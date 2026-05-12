package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.ContestResult
import com.cepsandik.electionservice.grpc.SelectionResult
import com.cepsandik.electionservice.grpc.TallyElectionRequest
import com.cepsandik.electionservice.grpc.TallyElectionResponse
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.decrypt.DecryptorDoerre
import electionguard.decrypt.Guardians
import electionguard.json2.EncryptedBallotJson
import electionguard.json2.TrusteeJson
import electionguard.json2.import
import electionguard.json2.importDecryptingTrustee
import electionguard.tally.AccumulateTally
import electionguard.util.ErrorMessages
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * KMP threshold tally decryption.
 *
 * Pipeline:
 *   1. Composite context parse → ElectionConfig + ElectionInitialized + Manifest
 *   2. ciphertext_ballots JSON → KMP EncryptedBallot listesi
 *   3. AccumulateTally ile homomorphic toplam (EncryptedTally)
 *   4. guardian_records JSON → DecryptingTrusteeDoerre listesi
 *   5. Guardians context + DecryptorDoerre kur, threshold decrypt
 *   6. DecryptedTallyOrBallot → proto ContestResult listesi
 */
@Service
class TallyDecryptionService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val group: GroupContext = productionGroup()
    private val json: Json = ElectionGuardSerde.json

    fun tally(request: TallyElectionRequest): TallyElectionResponse {
        log.info("tallyElection: election={}, ballots={}, guardians={}, quorum={}",
            request.electionId, request.ciphertextBallotsCount,
            request.guardianRecordsCount, request.quorum)

        val errs = ErrorMessages("tallyElection/${request.electionId}")

        // 1. Context (init + config) + Manifest
        val bundle = ElectionGuardSerde.parseElectionContext(request.electionGuardContext)
        val config = bundle.config.import(group.constants, request.electionManifest.encodeToByteArray(), errs.nested("config"))
            ?: error("ElectionConfig import basarisiz: $errs")
        val electionInit = bundle.init.import(group, config, errs.nested("init"))
            ?: error("ElectionInitialized import basarisiz: $errs")
        val manifest = ElectionGuardSerde.parseManifest(request.electionManifest).import()

        // 2. EncryptedBallot'ları parse
        val ballots = request.ciphertextBallotsList.mapIndexed { idx, jsonStr ->
            val dto = json.decodeFromString<EncryptedBallotJson>(jsonStr)
            dto.import(group, errs.nested("ballot-$idx"))
                ?: error("EncryptedBallot[$idx] import basarisiz: $errs")
        }
        log.info("  parsed {} encrypted ballots", ballots.size)

        // 3. Homomorphic toplam
        val accumulator = AccumulateTally(
            group, manifest, "tally-${request.electionId}",
            electionInit.extendedBaseHash, electionInit.jointPublicKey()
        )
        ballots.forEach { ballot ->
            val accErrs = ErrorMessages("accumulate-${ballot.ballotId}")
            accumulator.addCastBallot(ballot, accErrs)
            if (accErrs.hasErrors()) {
                log.warn("  ballot {} accumulate'e eklenemedi: {}", ballot.ballotId, accErrs)
            }
        }
        val encryptedTally = accumulator.build()
        log.info("  encryptedTally built: contests={}", encryptedTally.contests.size)

        // 4. guardian_records → DecryptingTrustee'ler
        val decryptingTrustees = request.guardianRecordsList.mapIndexed { idx, gr ->
            val dto = json.decodeFromString<TrusteeJson>(gr.serializedGuardian)
            dto.importDecryptingTrustee(group, errs.nested("trustee-${gr.guardianId}"))
                ?: error("Trustee[${gr.guardianId}] import basarisiz: $errs")
        }
        log.info("  decryptingTrustees: {}", decryptingTrustees.size)

        // 5. Decryptor + threshold decrypt
        val guardians = Guardians(group, electionInit.guardians)
        val decryptor = DecryptorDoerre(
            group,
            electionInit.extendedBaseHash,
            electionInit.jointPublicKey(),
            guardians,
            decryptingTrustees,
        )
        val decryptErrs = ErrorMessages("decrypt-${request.electionId}")
        val decryptedTally = with(decryptor) {
            encryptedTally.decrypt(decryptErrs)
        } ?: error("decryptTally basarisiz: $decryptErrs")
        log.info("  decryptedTally OK: contests={}", decryptedTally.contests.size)

        // 6. Proto response
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
        responseBuilder.tallyProof = "{\"contests\":${decryptedTally.contests.size}}" // TODO: tam JSON

        log.info("tallyElection OK: results={} contests", decryptedTally.contests.size)
        return responseBuilder.build()
    }
}
