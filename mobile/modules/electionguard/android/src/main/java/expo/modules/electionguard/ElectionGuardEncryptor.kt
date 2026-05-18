package expo.modules.electionguard

import android.util.Log
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import electionguard.ballot.PlaintextBallot
import electionguard.core.ElGamalPublicKey
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.encrypt.AddEncryptedBallot
import electionguard.json2.EncryptedBallotJson
import electionguard.json2.ElectionConfigJson
import electionguard.json2.ElectionInitializedJson
import electionguard.json2.ManifestJson
import electionguard.json2.import
import electionguard.json2.publishJson
import electionguard.publish.makeConsumer
import electionguard.publish.makePublisher
import electionguard.publish.readElectionRecord
import electionguard.util.ErrorMessages
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * KMP ElectionGuard ile bir PlaintextBallot'u şifreler.
 *
 * Pipeline:
 *   1. Composite context JSON'ı parse et (init + config — backend aynı format yolladı)
 *   2. Manifest JSON parse
 *   3. KMP API'leri: ElectionConfig, ElectionInitialized, Manifest import
 *   4. PlaintextBallot oluştur (input.selections'tan)
 *   5. AddEncryptedBallot ile encrypt + cast (geçici cache dizini)
 *   6. Cast edilmiş EncryptedBallot'u dosyadan oku, JSON'a serialize et
 *   7. Geçici dizini temizle
 *
 * Tüm alt adımlar ve toplam süre `Electionguard` tag'iyle logcat'e yazılır:
 *   adb logcat *:S Electionguard:V
 */
object ElectionGuardEncryptor {

    private const val TAG = "Electionguard"

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encrypt(input: EncryptBallotInput, cacheRoot: File): EncryptBallotResult {
        require(input.electionManifest.isNotBlank()) { "electionManifest bos" }
        require(input.electionGuardContext.isNotBlank()) { "electionGuardContext bos" }
        require(input.jointPublicKey.isNotBlank()) { "jointPublicKey bos" }
        require(input.selections.isNotEmpty()) { "selections bos" }

        val totalStart = System.currentTimeMillis()
        val workDir = File(cacheRoot, "eg-encrypt-${UUID.randomUUID()}").apply { mkdirs() }
        Log.i(TAG, "encrypt start: ballot=${input.ballotId}, selections=${input.selections.size}, ctx=${input.electionGuardContext.length}b, manifest=${input.electionManifest.length}b")

        try {
            lateinit var group: GroupContext
            val tGroup = measureTimeMillis { group = productionGroup() }
            Log.i(TAG, "  productionGroup: ${tGroup}ms")

            lateinit var initDto: ElectionInitializedJson
            lateinit var configDto: ElectionConfigJson
            val tParse = measureTimeMillis {
                val root: JsonObject = json.parseToJsonElement(input.electionGuardContext).jsonObject
                val initElement = root["init"] ?: error("electionGuardContext: init alani eksik")
                val configElement = root["config"] ?: error("electionGuardContext: config alani eksik")
                initDto = json.decodeFromString<ElectionInitializedJson>(json.encodeToString(initElement))
                configDto = json.decodeFromString<ElectionConfigJson>(json.encodeToString(configElement))
            }
            Log.i(TAG, "  context+manifest parse: ${tParse}ms")

            val errs = ErrorMessages("mobile-encrypt")
            val manifestBytes = input.electionManifest.encodeToByteArray()
            lateinit var config: electionguard.ballot.ElectionConfig
            lateinit var electionInit: electionguard.ballot.ElectionInitialized
            lateinit var manifest: electionguard.ballot.Manifest
            val tImport = measureTimeMillis {
                config = configDto.import(group.constants, manifestBytes, errs.nested("config"))
                    ?: error("ElectionConfig import basarisiz: $errs")
                electionInit = initDto.import(group, config, errs.nested("init"))
                    ?: error("ElectionInitialized import basarisiz: $errs")
                val manifestDto = json.decodeFromString<ManifestJson>(input.electionManifest)
                manifest = manifestDto.import()
            }
            Log.i(TAG, "  KMP import (config+init+manifest): ${tImport}ms")

            // Encryptor setup
            lateinit var encryptor: AddEncryptedBallot
            val tSetup = measureTimeMillis {
                val publisher = makePublisher(workDir.absolutePath, true, true)
                publisher.writeElectionInitialized(electionInit)

                val ballotStyleId = manifest.ballotStyles.firstOrNull()?.ballotStyleId ?: "ballot-style-1"
                val device = "mobile-${UUID.randomUUID().toString().take(8)}"
                val invalidDir = File(workDir, "invalid").apply { mkdirs() }.absolutePath

                encryptor = AddEncryptedBallot(
                    group, manifest,
                    electionInit.config.chainConfirmationCodes,
                    electionInit.config.configBaux0,
                    electionInit.jointPublicKey(),
                    electionInit.extendedBaseHash,
                    device,
                    outputDir = workDir.absolutePath,
                    invalidDir = invalidDir,
                    isJson = true,
                )
            }
            Log.i(TAG, "  encryptor setup (publisher + AddEncryptedBallot): ${tSetup}ms")

            // PlaintextBallot inşa
            val ballotStyleId = manifest.ballotStyles.firstOrNull()?.ballotStyleId ?: "ballot-style-1"
            val contestId = manifest.contests.firstOrNull()?.contestId
                ?: error("Manifest'te contest yok")
            val selections = input.selections.mapIndexed { idx, sel ->
                val vote = if (sel.id == input.selectedOptionId) 1 else 0
                val selectionId = "candidate_${sel.id}"
                PlaintextBallot.Selection(selectionId, idx, vote)
            }
            val plaintext = PlaintextBallot(
                input.ballotId,
                ballotStyleId,
                listOf(PlaintextBallot.Contest(contestId, 0, selections)),
            )

            // Esas KMP encrypt çağrısı — bu en pahalı kısım (4096-bit ElGamal + ZKP)
            val tEncrypt = measureTimeMillis {
                val encErrs = ErrorMessages("encrypt-${input.ballotId}")
                val cballot = encryptor.encrypt(plaintext, encErrs)
                    ?: error("encrypt failed: $encErrs")
                encryptor.cast(cballot.confirmationCode)
                encryptor.close()
            }
            Log.i(TAG, "  KMP encrypt+cast (4096-bit ElGamal + ZKP): ${tEncrypt}ms")

            // Cast sonrası EncryptedBallot'u disk'ten oku + JSON serialize
            lateinit var ciphertextJson: String
            lateinit var trackingCode: String
            val tSerialize = measureTimeMillis {
                val consumer = makeConsumer(group, workDir.absolutePath, false)
                val record = readElectionRecord(consumer)
                val ebs = record.encryptedAllBallots { it.ballotId == input.ballotId }
                val ballot = ebs.firstOrNull() ?: error("EncryptedBallot bulunamadi (cast sonrasi)")
                ciphertextJson = json.encodeToString(ballot.publishJson())
                trackingCode = ballot.confirmationCode.toString()
            }
            Log.i(TAG, "  read + serialize: ${tSerialize}ms (ciphertext=${ciphertextJson.length}b)")

            val totalMs = System.currentTimeMillis() - totalStart
            Log.i(TAG, "encrypt OK: ballot=${input.ballotId}, total=${totalMs}ms")

            return EncryptBallotResult().apply {
                encryptedBallot = ciphertextJson
                zkpProof = "embedded-in-ciphertext"
                this.trackingCode = trackingCode
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Faz 1.4 — Benaloh challenge / spoil. Ballot şifrelenir ama CAST EDİLMEZ;
     * `challenge` ile spoiled işaretlenir ve primary nonce AÇILIR. Sunucu
     * (crypto-engine DecryptWithNonce) ciphertext'i sadece bu nonce + joint key
     * ile bağımsız çözüp cihazın dürüst şifrelediğini doğrular. Spoiled ballot
     * ASLA tally'ye girmez; seçmen sonra gerçek oyu için yeni ballot şifreler.
     */
    fun spoil(input: EncryptBallotInput, cacheRoot: File): SpoilBallotResult {
        require(input.electionManifest.isNotBlank()) { "electionManifest bos" }
        require(input.electionGuardContext.isNotBlank()) { "electionGuardContext bos" }
        require(input.jointPublicKey.isNotBlank()) { "jointPublicKey bos" }
        require(input.selections.isNotEmpty()) { "selections bos" }

        val workDir = File(cacheRoot, "eg-spoil-${UUID.randomUUID()}").apply { mkdirs() }
        Log.i(TAG, "spoil start: ballot=${input.ballotId}, selections=${input.selections.size}")
        try {
            val group = productionGroup()
            val root: JsonObject = json.parseToJsonElement(input.electionGuardContext).jsonObject
            val initElement = root["init"] ?: error("electionGuardContext: init alani eksik")
            val configElement = root["config"] ?: error("electionGuardContext: config alani eksik")
            val initDto = json.decodeFromString<ElectionInitializedJson>(json.encodeToString(initElement))
            val configDto = json.decodeFromString<ElectionConfigJson>(json.encodeToString(configElement))

            val errs = ErrorMessages("mobile-spoil")
            val manifestBytes = input.electionManifest.encodeToByteArray()
            val config = configDto.import(group.constants, manifestBytes, errs.nested("config"))
                ?: error("ElectionConfig import basarisiz: $errs")
            val electionInit = initDto.import(group, config, errs.nested("init"))
                ?: error("ElectionInitialized import basarisiz: $errs")
            val manifestDto = json.decodeFromString<ManifestJson>(input.electionManifest)
            val manifest = manifestDto.import()

            val publisher = makePublisher(workDir.absolutePath, true, true)
            publisher.writeElectionInitialized(electionInit)
            val ballotStyleId = manifest.ballotStyles.firstOrNull()?.ballotStyleId ?: "ballot-style-1"
            val device = "mobile-${UUID.randomUUID().toString().take(8)}"
            val invalidDir = File(workDir, "invalid").apply { mkdirs() }.absolutePath
            val encryptor = AddEncryptedBallot(
                group, manifest,
                electionInit.config.chainConfirmationCodes,
                electionInit.config.configBaux0,
                electionInit.jointPublicKey(),
                electionInit.extendedBaseHash,
                device,
                outputDir = workDir.absolutePath,
                invalidDir = invalidDir,
                isJson = true,
            )

            val contestId = manifest.contests.firstOrNull()?.contestId
                ?: error("Manifest'te contest yok")
            val selections = input.selections.mapIndexed { idx, sel ->
                val vote = if (sel.id == input.selectedOptionId) 1 else 0
                PlaintextBallot.Selection("candidate_${sel.id}", idx, vote)
            }
            val plaintext = PlaintextBallot(
                input.ballotId, ballotStyleId,
                listOf(PlaintextBallot.Contest(contestId, 0, selections)),
            )

            val encErrs = ErrorMessages("spoil-${input.ballotId}")
            val cballot = encryptor.encrypt(plaintext, encErrs)
                ?: error("encrypt failed: $encErrs")
            // CAST ETME — challenge (spoil). Primary nonce AÇILIR.
            val primaryNonceHex = cballot.ballotNonce.toHex()
            val challenged = when (val r = encryptor.challenge(cballot.confirmationCode)) {
                is Ok -> r.value
                is Err -> error("challenge (spoil) failed: ${r.error}")
            }
            encryptor.close()

            val ciphertextJson = json.encodeToString(challenged.publishJson())
            val plaintextJson = json.encodeToString(plaintext.publishJson())
            val trackingCode = challenged.confirmationCode.toString()
            Log.i(TAG, "spoil OK: ballot=${input.ballotId}, ciphertext=${ciphertextJson.length}b, plaintext açıldı, nonce açıldı")

            return SpoilBallotResult().apply {
                encryptedBallot = ciphertextJson
                primaryNonce = primaryNonceHex
                plaintextBallot = plaintextJson
                this.trackingCode = trackingCode
            }
        } finally {
            workDir.deleteRecursively()
        }
    }
}
