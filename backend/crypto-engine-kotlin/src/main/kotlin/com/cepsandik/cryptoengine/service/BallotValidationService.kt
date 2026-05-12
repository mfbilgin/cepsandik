package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.ValidateBallotRequest
import com.cepsandik.electionservice.grpc.ValidateBallotResponse
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.json2.EncryptedBallotJson
import electionguard.json2.import
import electionguard.util.ErrorMessages
import electionguard.util.Stats
import electionguard.verifier.VerifyEncryptedBallots
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * KMP VerifyEncryptedBallots ile client-side şifrelenmiş ballot'u doğrular.
 *
 * Tüm Chaum-Pedersen contest/range proof'ları, manifest hash, extended base
 * hash, joint public key uyumu KMP tarafından kontrol edilir.
 *
 * E2E-V sıkılaştırması: tracking_code yalnızca EncryptedBallot.confirmationCode
 * üzerinden döner; client'in gönderdiği request.tracking_code authoritative
 * değil, sadece bilgi amaçlı log'lanır.
 */
@Service
class BallotValidationService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val group: GroupContext = productionGroup()

    fun validate(request: ValidateBallotRequest): ValidateBallotResponse {
        log.info("validateBallot: election={}, ballot={}, ciphertext_size={}",
            request.electionId, request.ballotId, request.ciphertextBallot.length)

        // 1. Bos input erken reject
        if (request.ciphertextBallot.isBlank()) {
            return invalid("ciphertext_ballot bos")
        }
        if (request.electionGuardContext.isBlank() || request.electionManifest.isBlank()) {
            return invalid("electionGuardContext veya electionManifest bos")
        }

        // 2. Ballot hash sanity (request bir hash gönderdiyse)
        val ballotHashHex = sha256Hex(request.ciphertextBallot)
        if (request.ballotHash.isNotBlank() && request.ballotHash != ballotHashHex) {
            return invalid("ballot_hash uyusmuyor", ballotHashHex)
        }

        // 3. Composite context'i parse et (init + config)
        val bundle = try {
            ElectionGuardSerde.parseElectionContext(request.electionGuardContext)
        } catch (t: Throwable) {
            return invalid("electionGuardContext parse hatasi: ${t.message}", ballotHashHex)
        }

        val errs = ErrorMessages("validateBallot/${request.ballotId}")
        val manifestBytes = request.electionManifest.encodeToByteArray()

        // 4. ElectionConfig import — group.constants + manifestBytes ister
        val config = bundle.config.import(group.constants, manifestBytes, errs.nested("config"))
            ?: return invalid("ElectionConfig import basarisiz: $errs", ballotHashHex)

        // 5. ElectionInitialized import
        val electionInit = bundle.init.import(group, config, errs.nested("init"))
            ?: return invalid("ElectionInitialized import basarisiz: $errs", ballotHashHex)

        // 6. Manifest import
        val manifestJson = ElectionGuardSerde.parseManifest(request.electionManifest)
        val manifest = manifestJson.import()

        // 7. EncryptedBallot import
        val ballotDto = try {
            ElectionGuardSerde.json.decodeFromString<EncryptedBallotJson>(request.ciphertextBallot)
        } catch (t: Throwable) {
            return invalid("ciphertext_ballot JSON parse hatasi: ${t.message}", ballotHashHex)
        }
        val ballot = ballotDto.import(group, errs.nested("ballot"))
            ?: return invalid("EncryptedBallot import basarisiz: $errs", ballotHashHex)

        if (request.ballotId.isNotBlank() && ballot.ballotId != request.ballotId) {
            return invalid(
                "ballot_id ciphertext ile uyusmuyor (request=${request.ballotId}, ciphertext=${ballot.ballotId})",
                ballotHashHex,
            )
        }

        // 8. Verifier kur, doğrula — jointPublicKey() zaten ElGamalPublicKey döndürür
        val verifier = VerifyEncryptedBallots(
            group, manifest,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            config,
            nthreads = 1,
        )
        val verifyErrs = ErrorMessages("verify/${ballot.ballotId}")
        val ok = verifier.verifyEncryptedBallot(ballot, verifyErrs, Stats())

        val authoritativeTrackingCode = ballot.confirmationCode.toString()

        if (!ok) {
            log.warn("validateBallot REJECTED: {}", verifyErrs)
            return ValidateBallotResponse.newBuilder()
                .setValid(false)
                .setBallotHash(ballotHashHex)
                .setTrackingCode(authoritativeTrackingCode)
                .setError("ElectionGuard verification failed: $verifyErrs")
                .build()
        }

        // Client'in tracking_code'u authoritative değil; mismatch warn'le geçilir
        if (request.trackingCode.isNotBlank() && request.trackingCode != authoritativeTrackingCode) {
            log.info(
                "tracking_code mismatch (authoritative kabul edilir): client={} server={}",
                request.trackingCode, authoritativeTrackingCode,
            )
        }

        return ValidateBallotResponse.newBuilder()
            .setValid(true)
            .setBallotHash(ballotHashHex)
            .setTrackingCode(authoritativeTrackingCode)
            .build()
    }

    private fun invalid(error: String, ballotHash: String = ""): ValidateBallotResponse =
        ValidateBallotResponse.newBuilder()
            .setValid(false)
            .setError(error)
            .also { if (ballotHash.isNotEmpty()) it.ballotHash = ballotHash }
            .build()

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
