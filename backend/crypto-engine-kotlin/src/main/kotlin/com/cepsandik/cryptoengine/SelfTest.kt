package com.cepsandik.cryptoengine

import com.cepsandik.cryptoengine.service.BallotValidationService
import com.cepsandik.cryptoengine.service.CreateJointKeyService
import com.cepsandik.cryptoengine.service.ElectionGuardSerde
import com.cepsandik.cryptoengine.service.ElectionSetupService
import com.cepsandik.cryptoengine.service.RemoteDecryptingTrustee
import com.cepsandik.cryptoengine.service.TallyDecryptionService
import com.cepsandik.electionservice.grpc.ContestInfo
import com.cepsandik.electionservice.grpc.CreateJointKeyRequest
import com.cepsandik.electionservice.grpc.GuardianPublicKey
import com.cepsandik.electionservice.grpc.SetupElectionRequest
import com.cepsandik.electionservice.grpc.TallyElectionRequest
import com.cepsandik.electionservice.grpc.ValidateBallotRequest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import electionguard.core.ElGamalPublicKey
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.ballot.makeElectionConfig
import electionguard.ballot.protocolVersion
import electionguard.cli.ManifestBuilder
import electionguard.core.getSystemDate
import electionguard.decrypt.DecryptorDoerre
import electionguard.decrypt.Guardians
import electionguard.encrypt.AddEncryptedBallot
import electionguard.input.RandomBallotProvider
import electionguard.json2.import
import electionguard.json2.importDecryptingTrustee
import electionguard.json2.publishJson
import electionguard.json2.PublicKeysJson
import electionguard.json2.EncryptedKeyShareJson
import electionguard.json2.TrusteeJson
import electionguard.json2.ElementModQJson
import electionguard.keyceremony.KeyCeremonyTrustee
import electionguard.keyceremony.KeyCeremonyResults
import electionguard.keyceremony.keyCeremonyExchange
import electionguard.keyceremony.regeneratePolynomial
import electionguard.tally.AccumulateTally
import electionguard.publish.makeConsumer
import electionguard.publish.makePublisher
import electionguard.publish.readElectionRecord
import electionguard.util.ErrorMessages
import electionguard.verifier.VerifyEncryptedBallots
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
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
    private val createJointKeyService: CreateJointKeyService,
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

        val distMs = measureTimeMillis {
            try {
                runDistributedThresholdTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST dist] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST dist] total elapsed = {} ms", distMs)

        val cjkMs = measureTimeMillis {
            try {
                runCreateJointKeyRpcTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST cjk] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST cjk] total elapsed = {} ms", cjkMs)

        val proxyMs = measureTimeMillis {
            try {
                runRealDistributedProxyTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST proxy] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST proxy] total elapsed = {} ms", proxyMs)

        val dkrMs = measureTimeMillis {
            try {
                runDistributedReconstructionTest()
            } catch (t: Throwable) {
                log.error("[SELFTEST dkr] FAIL — exception: {}", t.message, t)
            }
        }
        log.info("[SELFTEST dkr] total elapsed = {} ms", dkrMs)
    }

    /**
     * Sprint 5.C.2.1 — Gerçek E2E-V threshold decryption proof-of-concept.
     *
     * Sunucu HİÇBİR private key share görmez. KMP DecryptorDoerre'i çalıştırırken
     * `DecryptingTrusteeIF` implementasyonu olarak `RemoteDecryptingTrustee`
     * proxy'lerini kullanırız. Proxy decrypt/challenge çağrılarında BLOCK'lanır,
     * dış dünyadan (bu testte simüle edilen "mobile thread") submit edilen
     * partial/response gelene kadar bekler.
     *
     * Real-world karşılığı:
     *   - Proxy = sunucu HTTP endpoint pair (partial submit + challenge response submit)
     *   - Submit eden "mobile thread" = gerçek bir Android cihazda local KMP
     *     DecryptingTrusteeDoerre çalıştırıp HTTP POST ile cevap gönderen kod
     *   - Bu testte private state'i SADECE mobile thread'in local DecryptingTrusteeDoerre
     *     instance'ı içerir; proxy o instance'ı asla görmez.
     */
    private fun runRealDistributedProxyTest() {
        log.info("[SELFTEST proxy] starting — true E2E-V via RemoteDecryptingTrustee proxies")
        val electionId = "selftest-proxy-1"
        val ballotStyleId = "ballot-style-1"
        val n = 3
        val q = 2

        val group = productionGroup()

        // 1. SETUP — Sprint 5.C.1 yolu: trustees lokal, server sadece publicKeys alır
        val trustees = List(n) { idx ->
            val seq = idx + 1
            electionguard.keyceremony.KeyCeremonyTrustee(group, "trustee$seq", seq, nguardians = n, quorum = q)
        }
        // Trustee'ler arası encrypted key exchange (gerçekte network — burada in-process)
        val exchangeResult = electionguard.keyceremony.keyCeremonyExchange(trustees)
        if (exchangeResult is Err) error("trustee exchange FAIL: ${exchangeResult.error}")

        // Server-side: sadece publicKeys'ten ElectionInitialized üret
        val publicKeysList = trustees.map { it.publicKeys().unwrap() }
        val manifest = com.cepsandik.cryptoengine.service.ManifestBuildHelper.build(electionId, listOf(
            com.cepsandik.electionservice.grpc.ContestInfo.newBuilder()
                .setContestId("contest-presidential")
                .addSelectionIds("candidate-alice")
                .addSelectionIds("candidate-bob")
                .setNumberElected(1)
                .setName("Presidential")
                .build()
        ))
        val manifestBytes = ElectionGuardSerde.manifestToJson(manifest).encodeToByteArray()
        val config = electionguard.ballot.makeElectionConfig(
            electionguard.ballot.protocolVersion, group.constants, n, q, manifestBytes,
            chainConfirmationCodes = false,
            baux0 = "cepsandik".encodeToByteArray(),
            metadata = mapOf("Flow" to "proxy-test"),
        )
        val electionInit = electionguard.keyceremony.KeyCeremonyResults(publicKeysList)
            .makeElectionInitialized(config, mapOf("CreatedBy" to "proxy-test"))

        // 2. ENCRYPT 3 ballot (Sprint 4 yolu)
        val outDir = "$outputDir/proxy-roundtrip"
        File(outDir).mkdirs()
        val publisher = makePublisher(outDir, true, true)
        publisher.writeElectionInitialized(electionInit)
        val encryptor = AddEncryptedBallot(
            group, manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "proxy-test-device",
            outputDir = outDir,
            invalidDir = "$outDir/invalid",
            isJson = true,
        )
        val votes = listOf("candidate-alice", "candidate-alice", "candidate-bob")
        votes.forEachIndexed { idx, choice ->
            val plaintext = electionguard.ballot.PlaintextBallot(
                "proxy-ballot-$idx", ballotStyleId,
                listOf(electionguard.ballot.PlaintextBallot.Contest(
                    "contest-presidential", 0,
                    listOf(
                        electionguard.ballot.PlaintextBallot.Selection("candidate-alice", 0,
                            if (choice == "candidate-alice") 1 else 0),
                        electionguard.ballot.PlaintextBallot.Selection("candidate-bob", 1,
                            if (choice == "candidate-bob") 1 else 0),
                    ),
                )),
            )
            val cb = encryptor.encrypt(plaintext, ErrorMessages("e-$idx"))
                ?: error("encrypt fail")
            encryptor.cast(cb.confirmationCode)
        }
        encryptor.close()

        // 3. SERVER-SIDE: AccumulateTally + RemoteDecryptingTrustee proxies
        val consumer = makeConsumer(group, outDir, false)
        val record = readElectionRecord(consumer)
        val accumulator = AccumulateTally(
            group, manifest, "tally-$electionId",
            electionInit.extendedBaseHash, electionInit.jointPublicKey(),
        )
        record.encryptedAllBallots { true }.forEach { ballot ->
            accumulator.addCastBallot(ballot, ErrorMessages("acc"))
        }
        val encryptedTally = accumulator.build()

        // Q seçilen trustee — mobile cihazda her birinin lokal KMP DecryptingTrusteeDoerre
        // instance'ı vardır. Burada simülasyon: KeyCeremonyTrustee'yi TrusteeJson üzerinden
        // DecryptingTrusteeDoerre'a dönüştürürüz. Bu private state SADECE mobile thread'in
        // local scope'unda kalır; proxy bunu görmez.
        val selectedTrustees = trustees.take(q)
        val localDecryptingTrustees = selectedTrustees.map { kt ->
            kt.publishJson().importDecryptingTrustee(group, ErrorMessages("import-${kt.id}"))
                ?: error("importDecryptingTrustee FAIL for ${kt.id}")
        }

        // Server-side: sadece public bilgi
        val proxies = selectedTrustees.map { trustee ->
            RemoteDecryptingTrustee(
                guardianId = trustee.id,
                xCoord = trustee.xCoordinate(),
                publicKey = trustee.guardianPublicKey(),
            )
        }
        log.info("[SELFTEST proxy] server-side proxies created: count={} (NO private state)", proxies.size)

        // 4. MOBILE THREAD SIMULATION
        //    Her proxy için ayrı bir thread: pollDecryptInput → local decrypt → submit
        //                                     pollChallengeInput → local challenge → submit
        //    Gerçek hayatta bu thread'lerin yerine HTTP POST geliyor.
        val executor = java.util.concurrent.Executors.newFixedThreadPool(q + 1)
        proxies.forEachIndexed { idx, proxy ->
            val localDecryptor = localDecryptingTrustees[idx]
            executor.submit {
                try {
                    // Polling loop: decrypt input gelmesini bekle
                    var decryptInput: List<electionguard.core.ElementModP>? = null
                    while (decryptInput == null) {
                        decryptInput = proxy.pollDecryptInput()
                        if (decryptInput == null) Thread.sleep(20)
                    }
                    log.info("[SELFTEST proxy/mobile-{}] decrypt input received: {} elements",
                        localDecryptor.id(), decryptInput.size)
                    // Local decrypt — KMP private key share LOKAL kalır
                    val partials = localDecryptor.decrypt(group, decryptInput)
                    proxy.submitPartialDecryption(partials)

                    // Polling loop: challenge input gelmesini bekle
                    var challengeInput: List<electionguard.decrypt.ChallengeRequest>? = null
                    while (challengeInput == null) {
                        challengeInput = proxy.pollChallengeInput()
                        if (challengeInput == null) Thread.sleep(20)
                    }
                    log.info("[SELFTEST proxy/mobile-{}] challenge input received: {} requests",
                        localDecryptor.id(), challengeInput.size)
                    val responses = localDecryptor.challenge(group, challengeInput)
                    proxy.submitChallengeResponse(responses)
                } catch (t: Throwable) {
                    log.error("[SELFTEST proxy/mobile-{}] FAIL: {}", localDecryptor.id(), t.message, t)
                    proxy.fail(t.message ?: "unknown")
                }
            }
        }

        // 5. SERVER-SIDE: DecryptorDoerre çağır — proxies blocking-wait yapar, mobile'lar submit
        val decryptorFuture = executor.submit<electionguard.ballot.DecryptedTallyOrBallot> {
            val decryptor = DecryptorDoerre(
                group, electionInit.extendedBaseHash, electionInit.jointPublicKey(),
                Guardians(group, electionInit.guardians), proxies,
            )
            with(decryptor) {
                encryptedTally.decrypt(ErrorMessages("proxy-decrypt"))
            } ?: error("decrypt returned null")
        }

        val decryptedTally = decryptorFuture.get(120, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdown()

        // 6. ASSERT
        val tallies = decryptedTally.contests.flatMap { it.selections }
            .associate { it.selectionId to it.tally }
        val alice = tallies["candidate-alice"] ?: -1
        val bob = tallies["candidate-bob"] ?: -1
        if (alice == 2 && bob == 1) {
            log.info("[SELFTEST proxy OK] alice={}, bob={} — TRUE E2E-V threshold decrypt via proxies",
                alice, bob)
        } else {
            log.error("[SELFTEST proxy FAIL] alice={}, bob={} (expected 2, 1)", alice, bob)
        }
    }

    /**
     * Sprint 5.C.1 — CreateJointKey RPC roundtrip.
     *
     * Simüle eder: 3 mobil cihaz (trustee) lokal olarak `KeyCeremonyTrustee`
     * üretir, `publishJson().coefficientProofs` JSON'unu sunucuya yollar.
     * Sunucu (`CreateJointKeyService`) hiçbir privat state görmeden joint key +
     * ElectionInitialized + Manifest üretir. Sonra Sprint 4 path'iyle aynı:
     * encrypt 3 ballot, AccumulateTally, threshold decrypt (Q=2/3), assert.
     *
     * NOT: bu test cjk yolunu RPC katmanı olmadan service'i doğrudan çağırır
     * (CryptoServiceImpl üzerinden gitmiyor — kotlin engine kendi içinde).
     */
    private fun runCreateJointKeyRpcTest() {
        log.info("[SELFTEST cjk] starting — distributed CreateJointKey + threshold decrypt")
        val electionId = "selftest-cjk-1"
        val ballotStyleId = "ballot-style-1"
        val n = 3
        val q = 2

        val group = productionGroup()

        // 1. Üç trustee mobil cihazlarda (yine de aynı JVM, ama izole state).
        //    Her trustee `publishJson()` ile public+private state'i çıkarır;
        //    biz SADECE coefficient_proofs'u sunucuya gönderiyoruz (private
        //    polynomial katsayıları cihazda kalır).
        val trustees = List(n) { idx ->
            val seq = idx + 1
            electionguard.keyceremony.KeyCeremonyTrustee(group, "trustee$seq", seq, nguardians = n, quorum = q)
        }

        // 2. Mobile-side: her trustee'den public-only JSON parçasını topla.
        //    KMP'de `KeyCeremonyTrustee.publishPublicKeys()` yok; ama trustee'nin
        //    `coefficientProofs`'ı zaten public bilgi (her Schnorr proof'un
        //    public_key alanı commitment'tır). Buradan SchnorrProofJson listesi
        //    çıkarmak için aracı path: trustee.publishJson() (TrusteeJson) →
        //    secretKeyShares dahil, AMA CreateJointKeyService'a sadece
        //    coefficient_proofs alanını gönderiyoruz.
        val publicKeysProto: List<GuardianPublicKey> = trustees.map { trustee ->
            // KMP public-only API: trustee.publicKeys() → PublicKeys (no privat state)
            val publicKeys = trustee.publicKeys().unwrap()
            val publicKeysJson = publicKeys.publishJson()  // WebappKeyCeremonyJsonKt extension
            val coefficientProofsJson = ElectionGuardSerde.json.encodeToString(publicKeysJson.coefficientProofs)
            GuardianPublicKey.newBuilder()
                .setGuardianId(trustee.id)
                .setPublicKey("") // KMP coefficient_proofs[0].public_key commitment'ı taşıyor
                .setCommitments("") // legacy proto field, KMP kullanmaz
                .setCoefficientProofs(coefficientProofsJson)
                .build()
        }
        log.info("[SELFTEST cjk] 3 trustee public_keys produced; coefficient_proofs JSON sizes={}",
            publicKeysProto.map { it.coefficientProofs.length })

        // 3. CreateJointKey RPC çağrısı (service'i direkt)
        val cjkReq = CreateJointKeyRequest.newBuilder()
            .setElectionId(electionId)
            .setNumberOfGuardians(n)
            .setQuorum(q)
            .addAllPublicKeys(publicKeysProto)
            .addContests(
                ContestInfo.newBuilder()
                    .setContestId("contest-presidential")
                    .addSelectionIds("candidate-alice")
                    .addSelectionIds("candidate-bob")
                    .setNumberElected(1)
                    .setName("Presidential")
            )
            .setStartDate("2026-05-13T00:00:00Z")
            .setEndDate("2026-05-14T00:00:00Z")
            .build()
        val cjkResp = createJointKeyService.createJointKey(cjkReq)
        log.info("[SELFTEST cjk] joint key produced via RPC: jointKey={}..., context={} bytes",
            cjkResp.jointPublicKey.take(40), cjkResp.electionGuardContext.length)

        // 4. Trustees diğer trustee'lerin public part'larını alır + key exchange
        //    yapar (bu encrypted key shares exchange — gerçekte network üzerinden).
        //    Burada keyCeremonyExchange ile single-JVM simülasyonu.
        val exchangeResult = electionguard.keyceremony.keyCeremonyExchange(trustees)
        if (exchangeResult is Err) error("trustee exchange FAIL: ${exchangeResult.error}")

        // 5. Sunucudan gelen context'i parse et, encrypt et
        val errs = ErrorMessages("cjk")
        val manifestBytes = cjkResp.electionManifest.encodeToByteArray()
        val bundle = ElectionGuardSerde.parseElectionContext(cjkResp.electionGuardContext)
        val config = bundle.config.import(group.constants, manifestBytes, errs.nested("config"))!!
        val electionInit = bundle.init.import(group, config, errs.nested("init"))!!
        val manifest = ElectionGuardSerde.parseManifest(cjkResp.electionManifest).import()

        val outDir = "$outputDir/cjk-roundtrip"
        File(outDir).mkdirs()
        val publisher = makePublisher(outDir, true, true)
        publisher.writeElectionInitialized(electionInit)
        val encryptor = AddEncryptedBallot(
            group, manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "cjk-test-device",
            outputDir = outDir,
            invalidDir = "$outDir/invalid",
            isJson = true,
        )
        val votes = listOf("candidate-alice", "candidate-alice", "candidate-bob")
        votes.forEachIndexed { idx, choice ->
            val plaintext = electionguard.ballot.PlaintextBallot(
                "cjk-ballot-$idx", ballotStyleId,
                listOf(electionguard.ballot.PlaintextBallot.Contest(
                    "contest-presidential", 0,
                    listOf(
                        electionguard.ballot.PlaintextBallot.Selection("candidate-alice", 0,
                            if (choice == "candidate-alice") 1 else 0),
                        electionguard.ballot.PlaintextBallot.Selection("candidate-bob", 1,
                            if (choice == "candidate-bob") 1 else 0),
                    ),
                )),
            )
            val cb = encryptor.encrypt(plaintext, ErrorMessages("e-$idx"))
                ?: error("encrypt fail")
            encryptor.cast(cb.confirmationCode)
        }
        encryptor.close()

        // 6. Threshold decrypt (Q=2/3) — Sprint 5.C.3 ile aynı pipeline
        val consumer = makeConsumer(group, outDir, false)
        val record = readElectionRecord(consumer)
        val accumulator = AccumulateTally(
            group, manifest, "tally-$electionId",
            electionInit.extendedBaseHash, electionInit.jointPublicKey(),
        )
        record.encryptedAllBallots { true }.forEach { ballot ->
            accumulator.addCastBallot(ballot, ErrorMessages("acc"))
        }
        val encryptedTally = accumulator.build()
        val decryptingTrustees = trustees.take(q).map {
            it.publishJson().importDecryptingTrustee(group, ErrorMessages("dt"))!!
        }
        val decryptor = DecryptorDoerre(
            group, electionInit.extendedBaseHash, electionInit.jointPublicKey(),
            Guardians(group, electionInit.guardians), decryptingTrustees,
        )
        val decryptedTally = with(decryptor) {
            encryptedTally.decrypt(ErrorMessages("dec"))
        } ?: error("decrypt FAIL")

        val tallies = decryptedTally.contests.flatMap { it.selections }
            .associate { it.selectionId to it.tally }
        val alice = tallies["candidate-alice"] ?: -1
        val bob = tallies["candidate-bob"] ?: -1
        if (alice == 2 && bob == 1) {
            log.info("[SELFTEST cjk OK] alice={}, bob={} — CreateJointKey RPC + threshold decrypt OK",
                alice, bob)
        } else {
            log.error("[SELFTEST cjk FAIL] alice={}, bob={} (expected 2, 1), all={}", alice, bob, tallies)
        }
    }

    /**
     * Sprint 5.C.3 — Distributed threshold roundtrip.
     *
     * Bu test mevcut ElectionSetupService/TallyDecryptionService'lere
     * GİRMEDEN, KMP'nin distributed key ceremony + threshold decrypt güzergahını manuel
     * olarak yürütür. Asıl kanıt: N=3 trustee key ceremony tamamlandıktan sonra sadece
     * Q=2 trustee ile decryption başarılı oluyor mu (gerçek Lagrange interpolation)?
     *
     * Akış:
     *   1. ManifestBuilder + makeElectionConfig (sunucu tarafı public)
     *   2. 3 KeyCeremonyTrustee (her birini izole, mobil cihazların simülasyonu)
     *   3. keyCeremonyExchange(trustees) — gerçekte trustee'ler arası network exchange
     *   4. KeyCeremonyResults.makeElectionInitialized — sunucu joint key + context üretir
     *   5. 3 ballot encrypt (2 Alice, 1 Bob)
     *   6. AccumulateTally → EncryptedTally
     *   7. SADECE 2 trustee'den DecryptingTrustee → DecryptorDoerre (threshold = quorum)
     *   8. Assert alice=2, bob=1
     */
    private fun runDistributedThresholdTest() {
        log.info("[SELFTEST dist] starting — N=3 trustees, Q=2 threshold decrypt")
        val electionId = "selftest-dist-1"
        val ballotStyleId = "ballot-style-1"
        val n = 3
        val q = 2

        val group = productionGroup()

        // 1. Manifest (sunucu/UI tarafı)
        val builder = ManifestBuilder("election-$electionId")
        builder.addStyle(ballotStyleId, "district-1")
        builder.addContest("contest-presidential")
            .setVoteVariationType(electionguard.ballot.Manifest.VoteVariationType.one_of_m, 1, 1)
            .setGpunit("district-1")
            .addSelection("candidate-alice", "candidate-alice")
            .addSelection("candidate-bob", "candidate-bob")
            .done()
        val manifest = builder.build()
        val manifestBytes = ElectionGuardSerde.manifestToJson(manifest).encodeToByteArray()

        // 2. ElectionConfig (sunucu)
        val config = makeElectionConfig(
            protocolVersion, group.constants, n, q, manifestBytes,
            chainConfirmationCodes = false,
            baux0 = "cepsandik".encodeToByteArray(),
            metadata = mapOf(
                "CreatedBy" to "SelfTest.runDistributedThresholdTest",
                "CreatedOn" to getSystemDate(),
                "ElectionId" to electionId,
            ),
        )

        // 3. N trustee — gerçekte ayrı cihazlarda, burada izole instance
        val trustees = List(n) { idx ->
            KeyCeremonyTrustee(group, "trustee${idx + 1}", idx + 1, nguardians = n, quorum = q)
        }

        // 4. Cross-trustee exchange (KMP single-process simülasyonu)
        val exchangeResult = keyCeremonyExchange(trustees)
        if (exchangeResult is Err) error("keyCeremonyExchange failed: ${exchangeResult.error}")
        val ceremonyResults = exchangeResult.unwrap()

        // 5. ElectionInitialized — sunucu joint key üretir
        val electionInit = ceremonyResults.makeElectionInitialized(
            config,
            mapOf("flow" to "distributed-selftest"),
        )
        log.info("[SELFTEST dist] joint key produced, n={}, q={}, jointKey={}...",
            n, q, electionInit.jointPublicKey().toString().take(40))

        // 6. Encrypt 3 ballots (2 Alice, 1 Bob)
        val outDir = "$outputDir/dist-roundtrip"
        File(outDir).mkdirs()
        val publisher = makePublisher(outDir, true, true)
        publisher.writeElectionInitialized(electionInit)

        val encryptor = AddEncryptedBallot(
            group, manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "dist-test-device",
            outputDir = outDir,
            invalidDir = "$outDir/invalid",
            isJson = true,
        )

        val votes = listOf("candidate-alice", "candidate-alice", "candidate-bob")
        votes.forEachIndexed { idx, choice ->
            val plaintext = electionguard.ballot.PlaintextBallot(
                "dist-ballot-$idx", ballotStyleId,
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

        // 7. EncryptedBallot'ları oku, AccumulateTally ile homomorphic toplam
        val consumer = makeConsumer(group, outDir, false)
        val record = readElectionRecord(consumer)
        val encryptedBallots = record.encryptedAllBallots { true }.toList()
        val accumulator = AccumulateTally(
            group, manifest, "tally-$electionId",
            electionInit.extendedBaseHash, electionInit.jointPublicKey(),
        )
        encryptedBallots.forEach { ballot ->
            val accErrs = ErrorMessages("acc-${ballot.ballotId}")
            accumulator.addCastBallot(ballot, accErrs)
        }
        val encryptedTally = accumulator.build()

        // 8. THRESHOLD DECRYPT — sadece Q=2 trustee'nin DecryptingTrustee'sini al
        //    Gerçek hayatta her trustee'nin lokal cihazından gelen JSON state olur;
        //    burada trustees[0..q-1]'i alıyoruz, trustees[2] hiç kullanılmıyor.
        val errs = ErrorMessages("dist")
        val decryptingTrustees = trustees.take(q).map { trustee ->
            val tJson = trustee.publishJson()
            tJson.importDecryptingTrustee(group, errs.nested("dt-${trustee.id}"))
                ?: error("trustee ${trustee.id} importDecryptingTrustee FAIL: $errs")
        }
        log.info("[SELFTEST dist] threshold decrypt with {} of {} trustees", decryptingTrustees.size, n)

        val guardians = Guardians(group, electionInit.guardians)
        val decryptor = DecryptorDoerre(
            group, electionInit.extendedBaseHash, electionInit.jointPublicKey(),
            guardians, decryptingTrustees,
        )
        val decryptErrs = ErrorMessages("decrypt-$electionId")
        val decryptedTally = with(decryptor) {
            encryptedTally.decrypt(decryptErrs)
        } ?: error("threshold decrypt FAIL: $decryptErrs")

        // 9. Assert
        val tallies = decryptedTally.contests.flatMap { it.selections }
            .associate { it.selectionId to it.tally }
        val alice = tallies["candidate-alice"] ?: -1
        val bob = tallies["candidate-bob"] ?: -1

        if (alice == 2 && bob == 1) {
            log.info("[SELFTEST dist OK] alice={}, bob={} — Q={} of N={} threshold decrypt OK",
                alice, bob, q, n)
        } else {
            log.error("[SELFTEST dist FAIL] alice={}, bob={} (expected 2, 1), all={}",
                alice, bob, tallies)
        }
    }

    /**
     * Sprint 5.A.distributed kanıtı — gerçek dağıtık key ceremony, stateless
     * reconstruction, SADECE steps 1-4 (plaintext keyShareFor/receiveKeyShare
     * round'u sunucudan geçemeyeceği için ATLANIR).
     *
     * Mobile akışın birebir simülasyonu:
     *   - "SecureStore" per device: SADECE polinom katsayıları kalıcı
     *   - "Server" opak relay: PublicKeysJson + EncryptedKeyShareJson (şifreli)
     *   - Her round'da trustee objesi atılır, polinomdan yeniden kurulur
     *     (regeneratePolynomial) — app 24h pencerede restart olabilir senaryosu
     *
     * Round 1 (generateSingleGuardianKey): fresh trustee → polinom SecureStore'a,
     *          PublicKeysJson server'a. Trustee atılır.
     * Round 2 (computeEncryptedKeyShares): reconstruct → receivePublicKeys(peers)
     *          → encryptedKeyShareFor(peer) → server'a (opak). Trustee atılır.
     * Round 3 (finalizeGuardianKey): reconstruct → receivePublicKeys(peers) +
     *          receiveEncryptedKeyShare(bana gelenler) → publishJson() → TrusteeJson
     *          SecureStore'a (tally secret). isComplete()==false beklenir (5-6 skip).
     * Server: KeyCeremonyResults(publicKeysList) → joint key + ElectionInitialized.
     * Tally: Q TrusteeJson → DecryptorDoerre → assert alice=2, bob=1.
     */
    private fun runDistributedReconstructionTest() {
        log.info("[SELFTEST dkr] starting — distributed ceremony, reconstruction, steps 1-4 only")
        val electionId = "selftest-dkr-1"
        val ballotStyleId = "ballot-style-1"
        val n = 3
        val q = 2
        val group = productionGroup()
        val json = ElectionGuardSerde.json

        // ---- Manifest + config (sunucu/UI tarafı public) ----
        val builder = ManifestBuilder("election-$electionId")
        builder.addStyle(ballotStyleId, "district-1")
        builder.addContest("contest-presidential")
            .setVoteVariationType(electionguard.ballot.Manifest.VoteVariationType.one_of_m, 1, 1)
            .setGpunit("district-1")
            .addSelection("candidate-alice", "candidate-alice")
            .addSelection("candidate-bob", "candidate-bob")
            .done()
        val manifest = builder.build()
        val manifestBytes = ElectionGuardSerde.manifestToJson(manifest).encodeToByteArray()
        val config = makeElectionConfig(
            protocolVersion, group.constants, n, q, manifestBytes,
            chainConfirmationCodes = false,
            baux0 = "cepsandik".encodeToByteArray(),
            metadata = mapOf(
                "CreatedBy" to "SelfTest.runDistributedReconstructionTest",
                "CreatedOn" to getSystemDate(),
                "ElectionId" to electionId,
            ),
        )

        val guardianIds = (1..n).map { "trustee$it" }

        // ---- Simüle stores ----
        val secureStorePoly = HashMap<String, String>()        // gid -> JSON List<ElementModQJson> (SECRET)
        val secureStoreTrustee = HashMap<String, String>()      // gid -> final TrusteeJson (SECRET)
        val serverPublicKeys = HashMap<String, String>()        // gid -> PublicKeysJson (public)
        val serverEncShares = ArrayList<Triple<String, String, String>>() // (from, to, EncryptedKeyShareJson) opak

        fun reconstruct(gid: String, xCoord: Int): KeyCeremonyTrustee {
            val coeffs = json.decodeFromString<List<ElementModQJson>>(secureStorePoly[gid]!!)
                .map { it.import(group) ?: error("coeff import FAIL for $gid") }
            val poly = group.regeneratePolynomial(gid, q, coeffs)
            return KeyCeremonyTrustee(group, gid, xCoord, n, q, poly)
        }

        // ===== ROUND 1: generateSingleGuardianKey (her cihaz) =====
        guardianIds.forEachIndexed { idx, gid ->
            val xCoord = idx + 1
            val t = KeyCeremonyTrustee(group, gid, xCoord, nguardians = n, quorum = q)
            secureStorePoly[gid] = json.encodeToString(t.polynomial.coefficients.map { it.publishJson() })
            serverPublicKeys[gid] = json.encodeToString(t.publicKeys().unwrap().publishJson())
            // cihaz trustee objesini ATAR (sadece polinom SecureStore'da)
        }
        log.info("[SELFTEST dkr] round 1 done — {} polinom persisted, public keys uploaded", n)

        // ===== ROUND 2: computeEncryptedKeyShares (reconstruct) =====
        guardianIds.forEachIndexed { idx, gid ->
            val xCoord = idx + 1
            val t = reconstruct(gid, xCoord)
            guardianIds.filter { it != gid }.forEach { peer ->
                val pk = json.decodeFromString<PublicKeysJson>(serverPublicKeys[peer]!!)
                    .import(group, ErrorMessages("pk-$peer")) ?: error("pk import FAIL $peer")
                val rr = t.receivePublicKeys(pk)
                if (rr is Err) error("receivePublicKeys($peer) FAIL: ${rr.error}")
            }
            guardianIds.filter { it != gid }.forEach { peer ->
                val eks = t.encryptedKeyShareFor(peer).unwrap()
                serverEncShares.add(Triple(gid, peer, json.encodeToString(eks.publishJson())))
            }
        }
        log.info("[SELFTEST dkr] round 2 done — {} encrypted shares relayed (opaque)", serverEncShares.size)

        // ===== ROUND 3: finalizeGuardianKey (reconstruct) =====
        guardianIds.forEachIndexed { idx, gid ->
            val xCoord = idx + 1
            val t = reconstruct(gid, xCoord)
            guardianIds.filter { it != gid }.forEach { peer ->
                val pk = json.decodeFromString<PublicKeysJson>(serverPublicKeys[peer]!!)
                    .import(group, ErrorMessages("pk2-$peer")) ?: error("pk import FAIL $peer")
                t.receivePublicKeys(pk)
            }
            serverEncShares.filter { it.second == gid }.forEach { (from, _, eksJson) ->
                val eks = json.decodeFromString<EncryptedKeyShareJson>(eksJson)
                    .import(group) ?: error("eks import FAIL from=$from to=$gid")
                val rr = t.receiveEncryptedKeyShare(eks)
                if (rr is Err) error("receiveEncryptedKeyShare(from=$from) FAIL: ${rr.error}")
            }
            // STEPS 5-6 ATLANIR (keyShareFor/receiveKeyShare plaintext — sunucudan geçmez)
            val tj = t.publishJson()
            secureStoreTrustee[gid] = json.encodeToString(tj)
            log.info("[SELFTEST dkr] {} finalized: isComplete={} (false beklenir, 5-6 skip), trusteeJson OK",
                gid, t.isComplete())
        }

        // ===== SERVER: public-only joint key (KeyCeremonyResults) =====
        val publicKeysList = guardianIds.map { gid ->
            json.decodeFromString<PublicKeysJson>(serverPublicKeys[gid]!!)
                .import(group, ErrorMessages("jk-$gid")) ?: error("jk pk import FAIL $gid")
        }
        val ceremonyResults = KeyCeremonyResults(publicKeysList)
        val electionInit = ceremonyResults.makeElectionInitialized(
            config, mapOf("flow" to "dkr-selftest"),
        )
        log.info("[SELFTEST dkr] joint key produced server-side, jointKey={}...",
            electionInit.jointPublicKey().toString().take(40))

        // ===== Encrypt 3 ballots (2 Alice, 1 Bob) → tally =====
        val outDir = "$outputDir/dkr-roundtrip"
        File(outDir).mkdirs()
        val publisher = makePublisher(outDir, true, true)
        publisher.writeElectionInitialized(electionInit)
        val encryptor = AddEncryptedBallot(
            group, manifest,
            electionInit.config.chainConfirmationCodes,
            electionInit.config.configBaux0,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            "dkr-test-device",
            outputDir = outDir,
            invalidDir = "$outDir/invalid",
            isJson = true,
        )
        val votes = listOf("candidate-alice", "candidate-alice", "candidate-bob")
        votes.forEachIndexed { idx, choice ->
            val plaintext = electionguard.ballot.PlaintextBallot(
                "dkr-ballot-$idx", ballotStyleId,
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

        val consumer = makeConsumer(group, outDir, false)
        val record = readElectionRecord(consumer)
        val encryptedBallots = record.encryptedAllBallots { true }.toList()
        val accumulator = AccumulateTally(
            group, manifest, "tally-$electionId",
            electionInit.extendedBaseHash, electionInit.jointPublicKey(),
        )
        encryptedBallots.forEach { ballot ->
            accumulator.addCastBallot(ballot, ErrorMessages("acc-${ballot.ballotId}"))
        }
        val encryptedTally = accumulator.build()

        // ===== THRESHOLD DECRYPT — Q TrusteeJson SecureStore'dan =====
        val errs = ErrorMessages("dkr")
        val decryptingTrustees = guardianIds.take(q).map { gid ->
            json.decodeFromString<TrusteeJson>(secureStoreTrustee[gid]!!)
                .importDecryptingTrustee(group, errs.nested("dt-$gid"))
                ?: error("trustee $gid importDecryptingTrustee FAIL: $errs")
        }
        val guardians = Guardians(group, electionInit.guardians)
        val decryptor = DecryptorDoerre(
            group, electionInit.extendedBaseHash, electionInit.jointPublicKey(),
            guardians, decryptingTrustees,
        )
        val decryptErrs = ErrorMessages("decrypt-$electionId")
        val decryptedTally = with(decryptor) {
            encryptedTally.decrypt(decryptErrs)
        } ?: error("threshold decrypt FAIL: $decryptErrs")

        val tallies = decryptedTally.contests.flatMap { it.selections }
            .associate { it.selectionId to it.tally }
        val alice = tallies["candidate-alice"] ?: -1
        val bob = tallies["candidate-bob"] ?: -1
        if (alice == 2 && bob == 1) {
            log.info("[SELFTEST dkr OK] alice={}, bob={} — distributed reconstruction (steps 1-4 only) " +
                "Q={} of N={} threshold decrypt OK", alice, bob, q, n)
        } else {
            log.error("[SELFTEST dkr FAIL] alice={}, bob={} (expected 2, 1), all={}",
                alice, bob, tallies)
        }
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
