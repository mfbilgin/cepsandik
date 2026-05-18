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
import java.util.concurrent.atomic.AtomicBoolean
import electionguard.ballot.ElectionInitialized

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

    /**
     * Faz 2.5 — Q-of-N "topla-sonra-çöz". DecryptorDoerre session başında
     * DEĞİL, ilk Q farklı guardian partial gönderince başlatılır. Böylece bir
     * guardian çevrimdışı olsa bile (N-Q kadar) tally tamamlanır; eksik
     * guardian'ların payı KMP DecryptorDoerre tarafından tüm N commitment'tan
     * Lagrange ile reconstruct edilir.
     */
    private class Session(
        val sessionId: String,
        val electionId: String,
        val encryptedTally: EncryptedTally,
        val electionInit: ElectionInitialized,
        val quorum: Int,
        val eligibleIds: Set<String>,
        val executor: ExecutorService,
        val decryptorFuture: CompletableFuture<DecryptedTallyOrBallot>,
        // Q'ya ulaşınca seçilen guardian'lar için lazy kurulan proxy'ler
        val proxies: ConcurrentHashMap<String, RemoteDecryptingTrustee> = ConcurrentHashMap(),
        // Henüz decryptor başlamadan toplanan partial'lar (guardianId → partials)
        val collectedPartials: ConcurrentHashMap<String, List<PartialDecryption>> = ConcurrentHashMap(),
        val decryptorStarted: AtomicBoolean = AtomicBoolean(false),
        @Volatile var state: State = State.WAIT_PARTIAL,
        // Faz 4.14 — TTL sweep için oluşturma zamanı.
        val createdAtMs: Long = System.currentTimeMillis(),
    )

    // Faz 4.14 — Bitmeyen/terk edilen session + executor thread sızıntısını
    // önle. TTL geçen session: future fail + executor shutdownNow + remove.
    // Proxy bekleme timeout'u env ile (prod-makul default; UAT override edebilir).
    private val sessionTtlMs: Long =
        (System.getenv("TALLY_SESSION_TTL_MS")?.toLongOrNull()) ?: 3_600_000L  // 60 dk
    private val proxyTimeoutMs: Long =
        (System.getenv("TALLY_PROXY_TIMEOUT_MS")?.toLongOrNull()) ?: 900_000L  // 15 dk
    private val cleaner: java.util.concurrent.ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "tally-session-cleaner").apply { isDaemon = true }
        }

    @jakarta.annotation.PostConstruct
    fun startCleaner() {
        cleaner.scheduleWithFixedDelay({
            try {
                val now = System.currentTimeMillis()
                sessions.entries.removeIf { (sid, s) ->
                    val stale = now - s.createdAtMs > sessionTtlMs
                    if (stale) {
                        log.warn("TTL sweep: stale tally session siliniyor sessionId={}, " +
                            "yaş={}ms (>TTL {}ms)", sid, now - s.createdAtMs, sessionTtlMs)
                        if (!s.decryptorFuture.isDone) {
                            s.decryptorFuture.completeExceptionally(
                                IllegalStateException("Session TTL aşıldı (terk edildi)"))
                        }
                        runCatching { s.executor.shutdownNow() }
                    }
                    stale
                }
            } catch (t: Throwable) {
                log.error("Tally session cleaner hata", t)
            }
        }, 5, 5, TimeUnit.MINUTES)
        log.info("Tally session cleaner başladı: TTL={}ms, proxyTimeout={}ms",
            sessionTtlMs, proxyTimeoutMs)
    }

    @jakarta.annotation.PreDestroy
    fun stopCleaner() {
        cleaner.shutdownNow()
    }

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

        // 3. Eligible guardian allow-list + quorum (config'ten — proto değişmeden).
        //    Q-of-N: DecryptorDoerre BURADA başlamaz; ilk Q farklı guardian
        //    partial gönderince submitPartialDecryption tetikler.
        val eligibleIds = request.participatingGuardianIdsList.toSet()
        val quorum = config.quorum
        require(quorum >= 1) { "quorum >= 1 olmalı (config.quorum=$quorum)" }
        val knownIds = electionInit.guardians.map { it.guardianId }.toSet()
        val unknown = eligibleIds - knownIds
        if (unknown.isNotEmpty()) error("eligible guardian ceremony'de yok: $unknown")
        if (eligibleIds.size < quorum) {
            error("eligible guardian (${eligibleIds.size}) < quorum ($quorum) — tally imkansız")
        }

        // 4. EncryptedTally JSON serialize (mobile'a yollanacak)
        val encryptedTallyJson = json.encodeToString(encryptedTally.publishJson())

        val sessionId = UUID.randomUUID().toString()
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "tally-session-${sessionId.take(8)}")
        }
        val decryptorFuture = CompletableFuture<DecryptedTallyOrBallot>()

        val session = Session(
            sessionId = sessionId,
            electionId = request.electionId,
            encryptedTally = encryptedTally,
            electionInit = electionInit,
            quorum = quorum,
            eligibleIds = eligibleIds,
            executor = executor,
            decryptorFuture = decryptorFuture,
        )
        sessions[sessionId] = session

        log.info("startSession OK: sessionId={}, eligible={}, quorum={} (Q-of-N: ilk {} partial DecryptorDoerre'ı başlatır), encryptedTallyJson={} bytes",
            sessionId, eligibleIds.size, quorum, quorum, encryptedTallyJson.length)

        return StartTallyDecryptionSessionResponse.newBuilder()
            .setSessionId(sessionId)
            .setEncryptedTallyJson(encryptedTallyJson)
            .build()
    }

    fun submitPartialDecryption(request: SubmitPartialDecryptionRequest): SessionAckResponse {
        val session = sessions[request.sessionId]
            ?: return ack(false, "UNKNOWN", "session bulunamadı: ${request.sessionId}")
        if (request.guardianId !in session.eligibleIds) {
            return ack(false, session.state.name,
                "guardian bu seçimde yetkili değil: ${request.guardianId}")
        }

        val errs = ErrorMessages("partial/${request.sessionId}/${request.guardianId}")
        val partials = try {
            decodePartialDecryptions(request.partialDecryptionsJson, errs)
        } catch (t: Throwable) {
            return ack(false, session.state.name, "partials parse FAIL: ${t.message}")
        }
        if (errs.hasErrors()) {
            return ack(false, session.state.name, "partials decode hataları: $errs")
        }

        // Decryptor zaten başladıysa: bu guardian seçilen Q'dan biriyse proxy'sine
        // ilet; değilse quorum doldu, partial gerekmiyor (geç gelen — zararsız).
        if (session.decryptorStarted.get()) {
            val proxy = session.proxies[request.guardianId]
            if (proxy != null) {
                proxy.submitPartialDecryption(partials)
                log.info("partial (seçili Q) iletildi: session={}, guardian={}, count={}",
                    request.sessionId, request.guardianId, partials.size)
            } else {
                log.info("partial geç geldi, quorum zaten doldu: session={}, guardian={}",
                    request.sessionId, request.guardianId)
            }
            return ack(true, session.state.name, "")
        }

        // Henüz Q'ya ulaşılmadı: topla. Aynı guardian tekrar gönderirse replace.
        session.collectedPartials[request.guardianId] = partials
        log.info("partial toplandı: session={}, guardian={}, count={}, toplam farklı={}/{}",
            request.sessionId, request.guardianId, partials.size,
            session.collectedPartials.size, session.quorum)

        maybeStartDecryptor(session)
        return ack(true, session.state.name, "")
    }

    /**
     * Q farklı guardian partial gönderdiyse DecryptorDoerre'ı başlatır.
     * Seçilen Q proxy'si önceden toplanan partial'larla BESLENİR (decrypt()
     * bloklamadan döner). Eksik N-Q guardian'ın payını DecryptorDoerre tüm N
     * commitment'tan Lagrange ile reconstruct eder. synchronized + AtomicBoolean
     * ile yalnızca bir kez başlar (eşzamanlı submit yarışı güvenli).
     */
    private fun maybeStartDecryptor(session: Session) {
        if (session.collectedPartials.size < session.quorum) return
        synchronized(session) {
            if (session.decryptorStarted.get()) return
            if (session.collectedPartials.size < session.quorum) return

            // İlk Q submitter'ı seç (deterministik: guardianId sıralı).
            val chosen = session.collectedPartials.keys.sorted().take(session.quorum)
            val guardianById = session.electionInit.guardians.associateBy { it.guardianId }

            chosen.forEach { gid ->
                val g = guardianById[gid] ?: error("seçilen guardian ceremony'de yok: $gid")
                val proxy = RemoteDecryptingTrustee(
                    guardianId = g.guardianId,
                    xCoord = g.xCoordinate,
                    publicKey = g.publicKey(),
                    timeoutMs = proxyTimeoutMs,  // Faz 4.14 — env-konfigüre, prod 15dk
                )
                // Önceden toplanan partial'ı ÖNCEDEN besle → decrypt() anında döner
                proxy.submitPartialDecryption(session.collectedPartials.getValue(gid))
                session.proxies[gid] = proxy
            }
            session.decryptorStarted.set(true)
            val proxyList = chosen.map { session.proxies.getValue(it) }

            log.info("Q-of-N: quorum doldu (Q={}/{} eligible), DecryptorDoerre başlıyor: " +
                "session={}, seçilen guardian'lar={}",
                session.quorum, session.eligibleIds.size, session.sessionId, chosen)

            session.executor.submit {
                try {
                    val decryptor = DecryptorDoerre(
                        group,
                        session.electionInit.extendedBaseHash,
                        session.electionInit.jointPublicKey(),
                        // TÜM N commitment — eksik guardian'lar Lagrange ile
                        Guardians(group, session.electionInit.guardians),
                        proxyList,
                    )
                    val decryptErrs = ErrorMessages("decrypt-${session.electionId}")
                    val decrypted = with(decryptor) {
                        session.encryptedTally.decrypt(decryptErrs)
                    } ?: throw IllegalStateException(
                        "DecryptorDoerre.decrypt returned null: $decryptErrs")
                    session.decryptorFuture.complete(decrypted)
                } catch (t: Throwable) {
                    log.error("DecryptorDoerre background thread FAIL session={}",
                        session.sessionId, t)
                    session.decryptorFuture.completeExceptionally(t)
                }
            }
        }
    }

    fun getChallenges(request: GetChallengesRequest): GetChallengesResponse {
        val session = sessions[request.sessionId]
            ?: error("session bulunamadı: ${request.sessionId}")
        if (request.guardianId !in session.eligibleIds) {
            error("guardian bu seçimde yetkili değil: ${request.guardianId}")
        }

        // Q-of-N long-poll: proxy yalnızca seçilen Q guardian için kurulur.
        // - proxy varsa + challenge hazırsa → döndür
        // - decryptor başladı ama bu guardian seçilmedi → quorum başkalarınca
        //   dolduruldu, bu guardian'a gerek yok (boş challengesJson → mobile
        //   nazikçe biter)
        // - henüz quorum dolmadı → bekle
        val deadlineMs = System.currentTimeMillis() + 30_000L
        var challenges: List<ChallengeRequest>? = null
        while (challenges == null && System.currentTimeMillis() < deadlineMs) {
            val proxy = session.proxies[request.guardianId]
            if (proxy != null) {
                challenges = proxy.pollChallengeInput()
            } else if (session.decryptorStarted.get()) {
                log.info("getChallenges: guardian seçilmedi (quorum doldu), gerek yok: " +
                    "session={}, guardian={}", request.sessionId, request.guardianId)
                return GetChallengesResponse.newBuilder().setChallengesJson("").build()
            }
            if (challenges == null) Thread.sleep(100)
        }
        if (challenges == null) {
            error("challenge timeout — quorum (Q=${session.quorum}) için yeterli " +
                "guardian partial göndermemiş olabilir")
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
        if (proxy == null) {
            // Seçilen Q dışında — quorum başkalarınca dolduruldu, gerek yok.
            return ack(true, session.state.name,
                "quorum başka guardian'larca dolduruldu, response gerekmiyor")
        }

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
        // Faz 1.3 — GERÇEK tally proof: tüm Chaum-Pedersen decryption proof'ları
        // içeren KMP DecryptedTallyOrBallotJson. Eski stub ({"contests":n}) 3.parti
        // ElectionGuard verifier ile doğrulanamıyordu; bu JSON election record'a
        // yazılır ve standart EG verifier ile bağımsız doğrulanabilir.
        responseBuilder.tallyProof = json.encodeToString(decryptedTally.publishJson())

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
