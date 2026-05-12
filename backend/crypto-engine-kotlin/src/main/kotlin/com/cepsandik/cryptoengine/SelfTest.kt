package com.cepsandik.cryptoengine

import com.cepsandik.cryptoengine.service.BallotValidationService
import com.cepsandik.cryptoengine.service.ElectionGuardSerde
import com.cepsandik.cryptoengine.service.ElectionSetupService
import com.cepsandik.cryptoengine.service.TallyDecryptionService
import com.cepsandik.electionservice.grpc.ContestInfo
import com.cepsandik.electionservice.grpc.SetupElectionRequest
import com.cepsandik.electionservice.grpc.TallyElectionRequest
import com.cepsandik.electionservice.grpc.ValidateBallotRequest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import electionguard.core.ElGamalPublicKey
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.encrypt.AddEncryptedBallot
import electionguard.input.RandomBallotProvider
import electionguard.json2.import
import electionguard.json2.publishJson
import electionguard.publish.makeConsumer
import electionguard.publish.makePublisher
import electionguard.publish.readElectionRecord
import electionguard.util.ErrorMessages
import electionguard.verifier.VerifyEncryptedBallots
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * POC v2 — KMP intra-impl roundtrip self-test.
 *
 * Spring Boot ayağa kalktığında otomatik çalışır. Pipeline:
 *   1. EGK test data'sını oku (önceden hazırlanmış election initialized + manifest)
 *   2. AddEncryptedBallot ile bir test ballot şifrele
 *   3. VerifyEncryptedBallots ile aynı ballot'u doğrula
 *   4. [SELFTEST OK / FAIL] log'la
 *
 * Bu KMP'nin Spring Boot/Docker/JVM'de uçtan uca kullanılabildiğini kanıtlar.
 * Cross-impl (Python) uyumu beklenmiyor — Faz 4'te DB migration ile çözülecek.
 *
 * Devre dışı bırakmak için: SPRING_PROFILES_ACTIVE=production
 */
@Component
@Profile("!production")
class SelfTest(
    @org.springframework.beans.factory.annotation.Value("\${selftest.data-dir:/app/egk-test-data/workflow/allAvailableJson}")
    private val dataDir: String,
    @org.springframework.beans.factory.annotation.Value("\${selftest.output-dir:/tmp/selftest-output}")
    private val outputDir: String,
    private val electionSetupService: ElectionSetupService,
    private val ballotValidationService: BallotValidationService,
    private val tallyDecryptionService: TallyDecryptionService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        log.info("[SELFTEST] starting — data={}", dataDir)

        if (!File(dataDir).exists()) {
            log.error("[SELFTEST] FAIL — test data directory not found: {}", dataDir)
        } else {
            val verifyMs = measureTimeMillis {
                try {
                    runIntraImplRoundtrip()
                } catch (t: Throwable) {
                    log.error("[SELFTEST verify] FAIL — exception: {}", t.message, t)
                }
            }
            log.info("[SELFTEST verify] total elapsed = {} ms", verifyMs)
        }

        val setupMs = measureTimeMillis {
            try {
                runSetupElectionTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST setup] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST setup] total elapsed = {} ms", setupMs)

        val rtMs = measureTimeMillis {
            try {
                runFullRoundtripTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST roundtrip] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST roundtrip] total elapsed = {} ms", rtMs)

        val tallyMs = measureTimeMillis {
            try {
                runTallyRoundtripTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST tally] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST tally] total elapsed = {} ms", tallyMs)
    }

    /**
     * Sprint 4 mühür testi: SetupElection → 3 ballot encrypt (2 Alice, 1 Bob) →
     * TallyElection → assert Alice=2 Bob=1.
     */
    private fun runTallyRoundtripTest() {
        log.info("[SELFTEST tally] starting — SetupElection -> 3xEncrypt -> Tally -> assert counts")
        val electionId = "selftest-tally-1"
        val ballotStyleId = "ballot-style-1"

        // 1. SetupElection (3 guardian, 2 quorum)
        val setupReq = SetupElectionRequest.newBuilder()
            .setElectionId(electionId)
            .setNumberOfGuardians(3)
            .setQuorum(2)
            .addContests(
                ContestInfo.newBuilder()
                    .setContestId("contest-presidential")
                    .addSelectionIds("candidate-alice")
                    .addSelectionIds("candidate-bob")
                    .setNumberElected(1)
                    .setName("Presidential")
            )
            .setStartDate("2026-05-10T00:00:00Z")
            .setEndDate("2026-05-11T00:00:00Z")
            .build()
        val setupResp = electionSetupService.setupElection(setupReq)

        // 2. KMP context parse
        val group = productionGroup()
        val errs = ErrorMessages("tally-rt")
        val manifestBytes = setupResp.electionManifest.encodeToByteArray()
        val bundle = ElectionGuardSerde.parseElectionContext(setupResp.electionGuardContext)
        val config = bundle.config.import(group.constants, manifestBytes, errs.nested("config"))!!
        val electionInit = bundle.init.import(group, config, errs.nested("init"))!!
        val manifest = ElectionGuardSerde.parseManifest(setupResp.electionManifest).import()

        // 3. 3 ballot şifrele: 2 Alice, 1 Bob
        val outDir = "$outputDir/tally-roundtrip"
        File(outDir).mkdirs()
        val publisher = makePublisher(outDir, true, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            group, manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "tally-test-device",
            outputDir = outDir,
            invalidDir = "$outDir/invalid",
            isJson = true,
        )

        val votes = listOf("candidate-alice", "candidate-alice", "candidate-bob")
        votes.forEachIndexed { idx, choice ->
            val plaintext = electionguard.ballot.PlaintextBallot(
                "tally-ballot-$idx", ballotStyleId,
                listOf(electionguard.ballot.PlaintextBallot.Contest(
                    "contest-presidential", 0,
                    listOf(
                        electionguard.ballot.PlaintextBallot.Selection(
                            "candidate-alice", 0, if (choice == "candidate-alice") 1 else 0),
                        electionguard.ballot.PlaintextBallot.Selection(
                            "candidate-bob", 1, if (choice == "candidate-bob") 1 else 0),
                    ),
                )),
            )
            val ebErrs = ErrorMessages("encrypt-$idx")
            val cb = encryptor.encrypt(plaintext, ebErrs) ?: error("encrypt fail: $ebErrs")
            encryptor.cast(cb.confirmationCode)
        }
        encryptor.close()

        // 4. EncryptedBallot'ları disk'ten oku, JSON'a serialize et
        val consumer = makeConsumer(group, outDir, false)
        val record = readElectionRecord(consumer)
        val encryptedJsons = record.encryptedAllBallots { true }.map { ballot ->
            ElectionGuardSerde.json.encodeToString(
                electionguard.json2.EncryptedBallotJson.serializer(),
                ballot.publishJson(),
            )
        }.toList()

        // 5. TallyElection RPC çağrısı
        val tallyReq = TallyElectionRequest.newBuilder()
            .setElectionId(electionId)
            .setElectionGuardContext(setupResp.electionGuardContext)
            .setElectionManifest(setupResp.electionManifest)
            .addAllCiphertextBallots(encryptedJsons)
            .addAllGuardianRecords(setupResp.guardianRecordsList)
            .setQuorum(2)
            .build()

        val tallyResp = tallyDecryptionService.tally(tallyReq)

        // 6. Sonuçları doğrula
        val tallies = tallyResp.resultsList.flatMap { it.selectionsList }
            .associate { it.selectionId to it.tally }
        val alice = tallies["candidate-alice"] ?: -1L
        val bob = tallies["candidate-bob"] ?: -1L

        if (alice == 2L && bob == 1L) {
            log.info("[SELFTEST tally OK] alice={}, bob={} — expected 2, 1", alice, bob)
        } else {
            log.error("[SELFTEST tally FAIL] alice={}, bob={} (expected 2, 1), all={}",
                alice, bob, tallies)
        }
    }

    /** Sprint 1 sanity check: ElectionSetupService.setupElection() çalışıyor mu? */
    private fun runSetupElectionTest() {
        log.info("[SELFTEST setup] starting — programatik SetupElectionRequest")

        val request = SetupElectionRequest.newBuilder()
            .setElectionId("selftest-election-1")
            .setNumberOfGuardians(3)
            .setQuorum(2)
            .addContests(
                ContestInfo.newBuilder()
                    .setContestId("contest-presidential")
                    .addSelectionIds("candidate-alice")
                    .addSelectionIds("candidate-bob")
                    .setNumberElected(1)
                    .setName("Presidential")
            )
            .setStartDate("2026-05-08T00:00:00Z")
            .setEndDate("2026-05-09T00:00:00Z")
            .build()

        val response = electionSetupService.setupElection(request)

        val ctxLen = response.electionGuardContext.length
        val manifestLen = response.electionManifest.length
        val keyPrefix = response.jointPublicKey.take(40)
        val nGuardianRecords = response.guardianRecordsCount

        log.info("[SELFTEST setup OK] electionGuardContext={} bytes, electionManifest={} bytes, jointPublicKey={}..., guardianRecords={}",
            ctxLen, manifestLen, keyPrefix, nGuardianRecords)
    }

    /**
     * Sprint 2 tam doğrulama: SetupElection → ElectionGuard composite parse →
     * AddEncryptedBallot ile bir ballot şifrele → ValidateBallot RPC ile doğrula.
     * Bu tüm Sprint 2 zincirinin uçtan uca kanıtı.
     */
    private fun runFullRoundtripTest() {
        log.info("[SELFTEST roundtrip] starting — SetupElection -> Encrypt -> ValidateBallot")
        val electionId = "selftest-roundtrip-1"
        val ballotStyleId = "ballot-style-1"
        val ballotId = "rt-ballot-1"

        // 1. SetupElection (3 guardian, 2 quorum, 1 contest)
        val setupReq = SetupElectionRequest.newBuilder()
            .setElectionId(electionId)
            .setNumberOfGuardians(3)
            .setQuorum(2)
            .addContests(
                ContestInfo.newBuilder()
                    .setContestId("contest-presidential")
                    .addSelectionIds("candidate-alice")
                    .addSelectionIds("candidate-bob")
                    .setNumberElected(1)
                    .setName("Presidential")
            )
            .setStartDate("2026-05-08T00:00:00Z")
            .setEndDate("2026-05-09T00:00:00Z")
            .build()
        val setupResp = electionSetupService.setupElection(setupReq)
        log.info("[SELFTEST roundtrip] setup OK; ctx={} bytes", setupResp.electionGuardContext.length)

        // 2. Composite parse + AddEncryptedBallot için pipeline
        val group: GroupContext = productionGroup()
        val errs = ErrorMessages("rt")
        val manifestBytes = setupResp.electionManifest.encodeToByteArray()
        val bundle = ElectionGuardSerde.parseElectionContext(setupResp.electionGuardContext)
        val config = bundle.config.import(group.constants, manifestBytes, errs.nested("config"))
            ?: error("config import failed: $errs")
        val electionInit = bundle.init.import(group, config, errs.nested("init"))
            ?: error("init import failed: $errs")
        val manifestDto = ElectionGuardSerde.parseManifest(setupResp.electionManifest)
        val manifest = manifestDto.import()

        val rtOutDir = "$outputDir/roundtrip"
        File(rtOutDir).mkdirs()
        val publisher = makePublisher(rtOutDir, true, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            group, manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "selftest-rt-device",
            outputDir = rtOutDir,
            invalidDir = "$rtOutDir/invalid",
            isJson = true,
        )

        val plaintext = RandomBallotProvider(manifest).getFakeBallot(manifest, ballotStyleId, ballotId)
        val encErrs = ErrorMessages("encrypt-rt")
        val cballot = encryptor.encrypt(plaintext, encErrs)
            ?: error("encrypt failed: $encErrs")
        encryptor.cast(cballot.confirmationCode)
        encryptor.close()

        // 3. encryptor.encrypt() CiphertextBallot döner; finalize edilmiş EncryptedBallot'u
        // outputDir'dan oku (cast sonrası persist olur).
        val rtConsumer = makeConsumer(group, rtOutDir, false)
        val rtRecord = readElectionRecord(rtConsumer)
        val encryptedBallot = rtRecord.encryptedAllBallots { it.ballotId == ballotId }.firstOrNull()
            ?: error("EncryptedBallot bulunamadi (cast edildikten sonra okunmadi)")

        val ciphertextJson = ElectionGuardSerde.json.encodeToString(encryptedBallot.publishJson())
        log.info("[SELFTEST roundtrip] encrypted ballot {} bytes; sending to ValidateBallot", ciphertextJson.length)

        // 4. ValidateBallot RPC handler'ını çağır
        val validateReq = ValidateBallotRequest.newBuilder()
            .setElectionId(electionId)
            .setElectionGuardContext(setupResp.electionGuardContext)
            .setElectionManifest(setupResp.electionManifest)
            .setBallotId(ballotId)
            .setCiphertextBallot(ciphertextJson)
            .build()
        val validateResp = ballotValidationService.validate(validateReq)

        if (validateResp.valid) {
            log.info("[SELFTEST roundtrip OK] valid=true, tracking={}, hash={}",
                validateResp.trackingCode.take(40), validateResp.ballotHash.take(40))
        } else {
            log.error("[SELFTEST roundtrip FAIL] valid=false, error={}", validateResp.error)
        }
    }

    private fun runIntraImplRoundtrip() {
        val group: GroupContext = productionGroup()
        log.info("[SELFTEST] productionGroup OK; group class={}", group::class.simpleName)

        val consumer = makeConsumer(group, dataDir)
        val initResult = consumer.readElectionInitialized()
        if (initResult is Err) {
            log.error("[SELFTEST] FAIL — readElectionInitialized: {}", initResult.error)
            return
        }
        val electionInit = initResult.unwrap()
        val manifest = consumer.makeManifest(electionInit.config.manifestBytes)
        log.info(
            "[SELFTEST] electionInit OK; nGuardians={}, quorum={}, contests={}",
            electionInit.config.numberOfGuardians,
            electionInit.config.quorum,
            manifest.contests.size,
        )

        File(outputDir).mkdirs()

        // Election record'u outputDir'a yaz — verifier sonrasında okuyacak
        val publisher = makePublisher(outputDir, true, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            group,
            manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "selftest-device",
            outputDir = outputDir,
            invalidDir = "$outputDir/invalid",
            isJson = true,
        )

        val nballots = 3
        val provider = RandomBallotProvider(manifest)
        var encryptedCount = 0
        val encryptMs = measureTimeMillis {
            repeat(nballots) { i ->
                val plaintext = provider.getFakeBallot(manifest, "ballotStyle", "selftest-ballot-$i")
                val errs = ErrorMessages("encrypt-$i")
                val ciphertext = encryptor.encrypt(plaintext, errs)
                if (ciphertext == null) {
                    log.error("[SELFTEST] encrypt #{} FAILED: {}", i, errs)
                } else {
                    encryptor.cast(ciphertext.confirmationCode)
                    encryptedCount++
                }
            }
            encryptor.close()
        }
        log.info("[SELFTEST] encrypted {}/{} ballots in {} ms ({} ms/ballot)",
            encryptedCount, nballots, encryptMs, encryptMs / nballots)

        val verifyMs = measureTimeMillis {
            val verifyConsumer = makeConsumer(group, outputDir, false)
            val record = readElectionRecord(verifyConsumer)
            val verifier = VerifyEncryptedBallots(
                group, record.manifest(),
                ElGamalPublicKey(record.jointPublicKey()!!),
                record.extendedBaseHash()!!,
                record.config(), 1,
            )
            val errs = ErrorMessages("verifyBallots")
            val ok = verifier.verifyBallots(record.encryptedAllBallots { true }, errs)
            if (ok) {
                log.info("[SELFTEST OK] all {} ballots verified", encryptedCount)
            } else {
                log.error("[SELFTEST FAIL] verification errors: {}", errs)
            }
        }
        log.info("[SELFTEST] verify done in {} ms", verifyMs)
    }
}
