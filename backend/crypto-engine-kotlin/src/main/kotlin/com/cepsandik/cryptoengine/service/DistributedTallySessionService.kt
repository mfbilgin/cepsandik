package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.ContestResult
import com.cepsandik.electionservice.grpc.FinalizeTallyDecryptionRequest
import com.cepsandik.electionservice.grpc.GetChallengesRequest
import com.cepsandik.electionservice.grpc.GetChallengesResponse
import com.cepsandik.electionservice.grpc.SelectionResult
import com.cepsandik.electionservice.grpc.SessionAckResponse
import com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionRequest
import com.cepsandik.electionservice.grpc.StartTallyDecryptionSessionResponse
import com.cepsandik.electionservice.grpc.SubmitChallengeResponseRequest
import com.cepsandik.electionservice.grpc.SubmitPartialDecryptionRequest
import com.cepsandik.electionservice.grpc.TallyElectionResponse
import electionguard.ballot.DecryptedTallyOrBallot
import electionguard.ballot.EncryptedTally
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.decrypt.ChallengeRequest
import electionguard.decrypt.ChallengeResponse
import electionguard.decrypt.DecryptorDoerre
import electionguard.decrypt.Guardians
import electionguard.decrypt.PartialDecryption
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import electionguard.json2.ChallengeRequestsJson
import electionguard.json2.ChallengeResponsesJson
import electionguard.json2.DecryptResponseJson
import electionguard.json2.EncryptedBallotJson
import electionguard.json2.`import`
import electionguard.json2.publishJson
import electionguard.tally.AccumulateTally
import electionguard.util.ErrorMessages
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Sprint 5.C.2 — Stateful 3-round threshold decryption orchestrator.
 *
 * Her bir tally için bir [Session] yaratılır. Server `DecryptorDoerre`'i
 * arka plan thread'inde çalıştırır. KMP'nin senkron `DecryptingTrusteeIF`
 * çağrıları `RemoteDecryptingTrustee` proxy üzerinden BLOCKING bekler.
 * Mobile cihazlar lokal KMP `DecryptingTrusteeDoerre` ile partial + response
 * üretip RPC ile submit ettiklerinde proxy unblocks olur.
 *
 * Session in-memory tutulur (production'da Redis/state-store gerekebilir).
 * Crash/restart sonrası tally yeniden başlatılır.
 */
@Service
class DistributedTallySessionService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val group: GroupContext = productionGroup()
    private val json = ElectionGuardSerde.json
    private val sessions = ConcurrentHashMap<String, Session>()

    enum class State { WAIT_PARTIAL, WAIT_CHALLENGE_RESPONSE, FINAL, FAILED }

    private class Session(
        val sessionId: String,
        val electionId: String,
        val encryptedTally: EncryptedTally,
        val proxies: Map<String, RemoteDecryptingTrustee>,
        val executor: ExecutorService,
        val decryptorFuture: CompletableFuture<DecryptedTallyOrBallot>,
        @Volatile var state: State = State.WAIT_PARTIAL,
    )

    fun startSession(request: StartTallyDecryptionSessionRequest): StartTallyDecryptionSessionResponse {
        log.info("startSession: election={}, ballots={}, participating={}",
            request.electionId, request.ciphertextBallotsCount, request.participatingGuardianIdsCount)

        // 1. Parse context + manifest
        val errs = ErrorMessages("startSession/${request.electionId}")
        val manifestBytes = request.electionManifest.encodeToByteArray()
        val bundle = ElectionGuardSerde.parseElectionContext(request.electionGuardContext)
        val config = bundle.config.`import`(group.constants, manifestBytes, errs.nested("config"))
            ?: error("ElectionConfig import basarisiz: $errs")
        val electionInit = bundle.init.`import`(group, config, errs.nested("init"))
            ?: error("ElectionInitialized import basarisiz: $errs")
        val manifest = ElectionGuardSerde.parseManifest(request.electionManifest).`import`()

        // 2. EncryptedBallot'ları parse + AccumulateTally
        val ballots = request.ciphertextBallotsList.mapIndexed { idx, jsonStr ->
            val dto = json.decodeFromString<EncryptedBallotJson>(jsonStr)
            dto.`import`(group, errs.nested("ballot-$idx"))
                ?: error("EncryptedBallot[$idx] import basarisiz: $errs")
        }
        val accumulator = AccumulateTally(
            group, manifest, "tally-${request.electionId}",
            electionInit.extendedBaseHash, electionInit.jointPublicKey(),
        )
        ballots.forEach { ballot ->
            val accErrs = ErrorMessages("acc-${ballot.ballotId}")
            accumulator.addCastBallot(ballot, accErrs)
            if (accErrs.hasErrors()) {
                log.warn("ballot {} accumulate hata: {}", ballot.ballotId, accErrs)
            }
        }
        val encryptedTally = accumulator.build()

        // 3. participating_guardian_ids'den Guardian + xCoordinate + publicKey eşle
        //    electionInit.guardians = List<Guardian> (sadece public bilgi)
        val participatingIds = request.participatingGuardianIdsList.toSet()
        val proxies: Map<String, RemoteDecryptingTrustee> = electionInit.guardians
            .filter { it.guardianId in participatingIds }
            .associate { guardian ->
                guardian.guardianId to RemoteDecryptingTrustee(
                    guardianId = guardian.guardianId,
                    xCoord = guardian.xCoordinate,
                    publicKey = guardian.publicKey(),
                )
            }
        if (proxies.size != participatingIds.size) {
            val missing = participatingIds - proxies.keys
            error("participating guardian eksik: $missing")
        }

        // 4. EncryptedTally JSON serialize (mobile'a yollanacak)
        val encryptedTallyJson = json.encodeToString(encryptedTally.publishJson())

        // 5. DecryptorDoerre'ı background thread'de başlat
        val sessionId = UUID.randomUUID().toString()
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "tally-session-${sessionId.take(8)}")
        }
        val decryptorFuture = CompletableFuture<DecryptedTallyOrBallot>()
        val proxyList = proxies.values.toList()

        executor.submit {
            try {
                val decryptor = DecryptorDoerre(
                    group, electionInit.extendedBaseHash, electionInit.jointPublicKey(),
                    Guardians(group, electionInit.guardians), proxyList,
                )
                val decryptErrs = ErrorMessages("decrypt-${request.electionId}")
                val decrypted = with(decryptor) {
                    encryptedTally.decrypt(decryptErrs)
                } ?: throw IllegalStateException("DecryptorDoerre.decrypt returned null: $decryptErrs")
                decryptorFuture.complete(decrypted)
            } catch (t: Throwable) {
                log.error("DecryptorDoerre background thread FAIL session={}", sessionId, t)
                decryptorFuture.completeExceptionally(t)
            }
        }

        val session = Session(
            sessionId = sessionId,
            electionId = request.electionId,
            encryptedTally = encryptedTally,
            proxies = proxies,
            executor = executor,
            decryptorFuture = decryptorFuture,
        )
        sessions[sessionId] = session

        log.info("startSession OK: sessionId={}, proxies={}, encryptedTallyJson={} bytes",
            sessionId, proxies.size, encryptedTallyJson.length)

        return StartTallyDecryptionSessionResponse.newBuilder()
            .setSessionId(sessionId)
            .setEncryptedTallyJson(encryptedTallyJson)
            .build()
    }

    fun submitPartialDecryption(request: SubmitPartialDecryptionRequest): SessionAckResponse {
        val session = sessions[request.sessionId]
            ?: return ack(false, "UNKNOWN", "session bulunamadı: ${request.sessionId}")
        val proxy = session.proxies[request.guardianId]
            ?: return ack(false, session.state.name, "guardian session içinde değil: ${request.guardianId}")

        val errs = ErrorMessages("partial/${request.sessionId}/${request.guardianId}")
        val partials = try {
            decodePartialDecryptions(request.partialDecryptionsJson, errs)
        } catch (t: Throwable) {
            return ack(false, session.state.name, "partials parse FAIL: ${t.message}")
        }
        if (errs.hasErrors()) {
            return ack(false, session.state.name, "partials decode hataları: $errs")
        }

        proxy.submitPartialDecryption(partials)
        log.info("partial submitted: session={}, guardian={}, count={}",
            request.sessionId, request.guardianId, partials.size)

        return ack(true, session.state.name, "")
    }

    fun getChallenges(request: GetChallengesRequest): GetChallengesResponse {
        val session = sessions[request.sessionId]
            ?: error("session bulunamadı: ${request.sessionId}")
        val proxy = session.proxies[request.guardianId]
            ?: error("guardian session içinde değil: ${request.guardianId}")

        // Polling: DecryptorDoerre yeterli partial'ı topladığında challenge'ları
        // proxy'lere yerleştirir. Long-poll ~30sn.
        val deadlineMs = System.currentTimeMillis() + 30_000L
        var challenges: List<ChallengeRequest>? = null
        while (challenges == null && System.currentTimeMillis() < deadlineMs) {
            challenges = proxy.pollChallengeInput()
            if (challenges == null) Thread.sleep(100)
        }
        if (challenges == null) {
            error("challenge timeout — diğer guardian'lardan partial bekleniyor olabilir")
        }
        session.state = State.WAIT_CHALLENGE_RESPONSE

        // KMP ChallengeRequest → ChallengeRequestJson — extension syntax
        val challengesJsonStr = json.encodeToString(
            ChallengeRequestsJson(challenges.map { it.publishJson() })
        )
        return GetChallengesResponse.newBuilder()
            .setChallengesJson(challengesJsonStr)
            .build()
    }

    fun submitChallengeResponse(request: SubmitChallengeResponseRequest): SessionAckResponse {
        val session = sessions[request.sessionId]
            ?: return ack(false, "UNKNOWN", "session bulunamadı: ${request.sessionId}")
        val proxy = session.proxies[request.guardianId]
            ?: return ack(false, session.state.name, "guardian session içinde değil: ${request.guardianId}")

        val errs = ErrorMessages("challenge/${request.sessionId}/${request.guardianId}")
        val responses = try {
            decodeChallengeResponses(request.challengeResponsesJson, errs)
        } catch (t: Throwable) {
            return ack(false, session.state.name, "responses parse FAIL: ${t.message}")
        }
        if (errs.hasErrors()) {
            return ack(false, session.state.name, "responses decode hataları: $errs")
        }

        proxy.submitChallengeResponse(responses)
        log.info("challenge response submitted: session={}, guardian={}, count={}",
            request.sessionId, request.guardianId, responses.size)

        return ack(true, session.state.name, "")
    }

    fun finalizeTally(request: FinalizeTallyDecryptionRequest): TallyElectionResponse {
        val session = sessions[request.sessionId]
            ?: error("session bulunamadı: ${request.sessionId}")

        val decryptedTally = session.decryptorFuture.get(60, TimeUnit.SECONDS)
        session.state = State.FINAL

        // Build response
        val responseBuilder = TallyElectionResponse.newBuilder()
        decryptedTally.contests.forEach { dContest ->
            val contestBuilder = ContestResult.newBuilder().setContestId(dContest.contestId)
            dContest.selections.forEach { dSel ->
                contestBuilder.addSelections(
                    SelectionResult.newBuilder()
                        .setSelectionId(dSel.selectionId)
                        .setTally(dSel.tally.toLong())
                        .build()
                )
            }
            responseBuilder.addResults(contestBuilder.build())
        }
        responseBuilder.tallyProof = """{"contests":${decryptedTally.contests.size},"sessionId":"${session.sessionId}"}"""

        // Cleanup
        session.executor.shutdown()
        sessions.remove(session.sessionId)

        log.info("finalizeTally OK: session={}, contests={}", session.sessionId, decryptedTally.contests.size)
        return responseBuilder.build()
    }

    // ===== JSON encode/decode helpers (KMP types ↔ JSON) =====

    private fun decodePartialDecryptions(jsonStr: String, errs: ErrorMessages): List<PartialDecryption> {
        val responseJson = json.decodeFromString<DecryptResponseJson>(jsonStr)
        val result = responseJson.`import`(group)
        if (result is Err) {
            errs.add("DecryptResponse import FAIL: ${result.error}")
            return emptyList()
        }
        return result.unwrap().shares
    }

    private fun decodeChallengeResponses(jsonStr: String, errs: ErrorMessages): List<ChallengeResponse> {
        val responseJson = json.decodeFromString<ChallengeResponsesJson>(jsonStr)
        val result = responseJson.`import`(group)
        if (result is Err) {
            errs.add("ChallengeResponses import FAIL: ${result.error}")
            return emptyList()
        }
        return result.unwrap().responses
    }

    private fun ack(accepted: Boolean, state: String, errorMsg: String): SessionAckResponse =
        SessionAckResponse.newBuilder()
            .setAccepted(accepted)
            .setState(state)
            .setError(errorMsg)
            .build()
}
