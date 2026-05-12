package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.GuardianRecord
import com.cepsandik.electionservice.grpc.SetupElectionRequest
import com.cepsandik.electionservice.grpc.SetupElectionResponse
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.unwrap
import electionguard.ballot.ElectionInitialized
import electionguard.ballot.makeElectionConfig
import electionguard.ballot.protocolVersion
import electionguard.core.GroupContext
import electionguard.core.getSystemDate
import electionguard.core.productionGroup
import electionguard.json2.TrusteeJson
import electionguard.json2.publishJson
import electionguard.keyceremony.KeyCeremonyTrustee
import electionguard.keyceremony.keyCeremonyExchange
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * KMP TrustedKeyCeremony tabanlı SetupElection.
 * Tek seferde N guardian üretir, key exchange yapar, ElectionInitialized oluşturur.
 * Distributed ceremony için ayrı bir CreateJointKeyService olacak (Faz 2 Sprint 4).
 */
@Service
class ElectionSetupService {

    private val log = LoggerFactory.getLogger(javaClass)

    fun setupElection(request: SetupElectionRequest): SetupElectionResponse {
        val n = request.numberOfGuardians
        val q = request.quorum
        require(n >= 1) { "numberOfGuardians >= 1 olmalı" }
        require(q in 1..n) { "quorum 1..$n aralığında olmalı" }

        log.info("setupElection: election={}, N={}, Q={}, contests={}",
            request.electionId, n, q, request.contestsCount)

        val group: GroupContext = productionGroup()

        // 1. Proto contest list'inden Manifest inşa (CreateJointKey ile paylaşılır)
        val manifest = ManifestBuildHelper.build(request.electionId, request.contestsList)
        val manifestBytes = ElectionGuardSerde.manifestToJson(manifest).encodeToByteArray()

        // 2. ElectionConfig
        val config = makeElectionConfig(
            protocolVersion,
            group.constants,
            n,
            q,
            manifestBytes,
            chainConfirmationCodes = false,
            baux0 = "cepsandik".encodeToByteArray(),
            metadata = mapOf(
                "CreatedBy" to "ElectionSetupService",
                "CreatedOn" to getSystemDate(),
                "ElectionId" to request.electionId,
            ),
        )

        // 3. Key ceremony (auto/trusted: tüm trustee'leri biz üretiyoruz)
        val trustees = List(n) { idx ->
            val seq = idx + 1
            KeyCeremonyTrustee(group, "trustee$seq", seq, nguardians = n, quorum = q)
        }

        val exchangeResult: Result<electionguard.keyceremony.KeyCeremonyResults, String> =
            keyCeremonyExchange(trustees)
        if (exchangeResult is Err) {
            error("KeyCeremony exchange başarısız: ${exchangeResult.error}")
        }
        val ceremonyResults = exchangeResult.unwrap()

        val electionInit: ElectionInitialized = ceremonyResults.makeElectionInitialized(
            config,
            mapOf(
                "CreatedBy" to "ElectionSetupService",
                "CreatedFrom" to "auto-ceremony",
            ),
        )

        // 4. JSON serialize + response
        // election_guard_context = composite { init, config } — ValidateBallot bunu parse eder
        val contextJson = ElectionGuardSerde.electionContextToJson(electionInit, config)
        val manifestJson = ElectionGuardSerde.manifestToJson(manifest)
        val jointPublicKeyHex = electionInit.jointPublicKey().toString()

        val responseBuilder = SetupElectionResponse.newBuilder()
            .setElectionGuardContext(contextJson)
            .setJointPublicKey(jointPublicKeyHex)
            .setElectionManifest(manifestJson)

        // Trustee private key'lerini guardian_records olarak gönder.
        // KMP `KeyCeremonyTrustee.publishJson() -> TrusteeJson` ile JSON'a serialize ederiz.
        // TallyElection ileride bu JSON'u `TrusteeJson.importDecryptingTrustee()` ile
        // DecryptingTrusteeDoerre'ye çevirip threshold decryption yapacak.
        trustees.forEach { trustee ->
            val trusteeJson = trustee.publishJson()
            val serialized = ElectionGuardSerde.json.encodeToString(TrusteeJson.serializer(), trusteeJson)
            responseBuilder.addGuardianRecords(
                GuardianRecord.newBuilder()
                    .setGuardianId(trustee.id)
                    .setSerializedGuardian(serialized)
                    .build()
            )
        }

        val firstTrusteeBytes = responseBuilder.getGuardianRecords(0).serializedGuardian.length
        log.info("setupElection OK: election={}, jointKey=${jointPublicKeyHex.take(32)}..., manifest={} bytes, context={} bytes, trustees={} (first={} bytes)",
            request.electionId, manifestJson.length, contextJson.length, trustees.size, firstTrusteeBytes)

        return responseBuilder.build()
    }

}
