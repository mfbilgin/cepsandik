package expo.modules.electionguard

import android.util.Log
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.unwrap
import electionguard.ballot.makeElectionConfig
import electionguard.ballot.protocolVersion
import electionguard.core.GroupContext
import electionguard.core.productionGroup
import electionguard.decrypt.DecryptingTrusteeDoerre
import electionguard.json2.ChallengeRequestsJson
import electionguard.json2.ChallengeResponses
import electionguard.json2.ChallengeResponsesJson
import electionguard.json2.DecryptResponse
import electionguard.json2.DecryptResponseJson
import electionguard.json2.EncryptedTallyJson
import electionguard.json2.PublicKeysJson
import electionguard.json2.TrusteeJson
import electionguard.json2.`import`
import electionguard.json2.importDecryptingTrustee
import electionguard.json2.publishJson
import electionguard.keyceremony.KeyCeremonyTrustee
import electionguard.keyceremony.keyCeremonyExchange
import electionguard.util.ErrorMessages
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.measureTimeMillis

/**
 * KMP Guardian crypto — mobile cihazda lokal key ceremony + threshold
 * decryption parçaları. Private polynomial katsayıları VE key share'ler
 * SADECE bu cihazda kalır.
 *
 * Sprint 5.B (UAT scope — leader-mode):
 *   - generateAllGuardianKeys: tek cihaz N trustee yaratır, cross-trustee
 *     exchange yapar, N TrusteeJson (private) + manifest publicKeysJson döner.
 *     TS taraf bunları SecureStore'da saklar, sadece publicKeysJson'ı sunucuya
 *     yollar. Gerçek distributed (her cihaz 1 trustee + cihazlar arası
 *     encrypted forward) Sprint 5.A'da.
 *
 *   - computePartialDecryption: bir trusteeStateJson + sunucudan gelen
 *     encryptedTallyJson + electionGuardContext + manifest → KMP local decrypt
 *     → DecryptResponseJson string.
 *
 *   - computeChallengeResponses: trusteeStateJson + sunucudan gelen
 *     challengesJson + tally context → KMP local challenge() → ChallengeResponsesJson.
 *
 * Tüm performans logları "GuardianCrypto" tag'iyle çıkar.
 */
object GuardianCryptoNative {

    private const val TAG = "GuardianCrypto"

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val group: GroupContext by lazy {
        val t0 = System.currentTimeMillis()
        val g = productionGroup()
        Log.i(TAG, "productionGroup warm: ${System.currentTimeMillis() - t0}ms")
        g
    }

    /**
     * Leader-mode: tek mobile cihaz N trustee'in tümünü yaratır + cross-trustee
     * exchange yapar. UAT için yeterli. Production'da bu cihazlar arası yapılmalı.
     */
    fun generateAllGuardianKeys(electionId: String, n: Int, q: Int): Map<String, Any?> {
        require(n >= 1) { "n >= 1" }
        require(q in 1..n) { "q must be in 1..$n" }

        val total = measureTimeMillis {
            // no-op outer scope
        }

        val trustees: List<KeyCeremonyTrustee>
        val exchangeMs = measureTimeMillis {
            // KeyCeremonyTrustee constructor + cross-trustee exchange
            trustees = (0 until n).map { idx ->
                val seq = idx + 1
                KeyCeremonyTrustee(group, "trustee$seq", seq, nguardians = n, quorum = q)
            }
            val exchangeResult = keyCeremonyExchange(trustees)
            if (exchangeResult is Err) error("keyCeremonyExchange FAIL: ${exchangeResult.error}")
        }

        // Public part (sunucuya gidecek): N PublicKeysJson
        val publicKeysJsonList: List<String> = trustees.map { trustee ->
            val publicKeys = trustee.publicKeys().unwrap()
            val pkJson = publicKeys.publishJson()
            json.encodeToString(pkJson)
        }

        // Private part (cihazda kalacak): N TrusteeJson
        val trusteeStateJsonList: List<String> = trustees.map { trustee ->
            val trusteeJson = trustee.publishJson()
            json.encodeToString(trusteeJson)
        }

        Log.i(TAG, "generateAllGuardianKeys: n=$n, q=$q, exchange=${exchangeMs}ms, publicKeys total bytes=${publicKeysJsonList.sumOf { it.length }}, trusteeStates total bytes=${trusteeStateJsonList.sumOf { it.length }}")

        // Joint key sunucudan dönecek (CreateJointKey RPC sonrası); mobile'da
        // ayrıca hesaplamaya gerek yok. Yine de bir önizleme istersek:
        // KeyCeremonyResults(publicKeys).makeElectionInitialized(config, ...)
        // .jointPublicKey() yapılabilir ama config oluşturmak boş yere iş.
        return mapOf(
            "electionId" to electionId,
            "n" to n,
            "q" to q,
            "publicKeysJsons" to publicKeysJsonList,
            "trusteeStateJsons" to trusteeStateJsonList,
            "jointPublicKeyHex" to "", // sunucudan dönecek (CreateJointKey response)
            "exchangeMs" to exchangeMs,
        )
    }

    /**
     * Bir trustee state'ten lokal partial decryption üretir.
     *
     * trustee state JSON private — sadece bu cihaz görmeli. Sunucuya GİTMEZ.
     * encryptedTallyJson + electionGuardContext sunucudan gelir (public).
     */
    fun computePartialDecryption(
        trusteeStateJson: String,
        encryptedTallyJson: String,
        electionGuardContextJson: String,
        electionManifestJson: String,
    ): String {
        val errs = ErrorMessages("computePartialDecryption")

        // 1. Parse context + manifest + encrypted tally
        val manifestBytes = electionManifestJson.encodeToByteArray()
        val contextBundle = parseElectionContext(electionGuardContextJson)
        val config = contextBundle.first.`import`(group.constants, manifestBytes, errs.nested("config"))
            ?: error("ElectionConfig import FAIL: $errs")
        val electionInit = contextBundle.second.`import`(group, config, errs.nested("init"))
            ?: error("ElectionInitialized import FAIL: $errs")
        val encryptedTallyDto = json.decodeFromString<EncryptedTallyJson>(encryptedTallyJson)
        val encryptedTally = encryptedTallyDto.`import`(group, errs.nested("tally"))
            ?: error("EncryptedTally import FAIL: $errs")

        // 2. Trustee state — TrusteeJson → DecryptingTrusteeDoerre
        val trusteeDto = json.decodeFromString<TrusteeJson>(trusteeStateJson)
        val decryptingTrustee: DecryptingTrusteeDoerre = trusteeDto.importDecryptingTrustee(group, errs.nested("trustee"))
            ?: error("TrusteeJson.importDecryptingTrustee FAIL: $errs")

        // 3. listOfA — encrypted tally'nin pad bileşenleri (her selection için)
        val listOfA = encryptedTally.contests
            .flatMap { c -> c.selections.map { s -> s.encryptedVote.pad } }

        // 4. Local decrypt
        val partials = decryptingTrustee.decrypt(group, listOfA)
        Log.i(TAG, "computePartialDecryption: trustee=${decryptingTrustee.id()}, listOfA=${listOfA.size}, partials=${partials.size}")

        // 5. JSON serialize — KMP DecryptResponse format
        val response = DecryptResponse(partials)
        val responseJson: DecryptResponseJson = response.publishJson()
        return json.encodeToString(responseJson)
    }

    /**
     * Bir trustee state'ten challenge response'unu hesaplar.
     */
    fun computeChallengeResponses(
        trusteeStateJson: String,
        challengesJson: String,
    ): String {
        val errs = ErrorMessages("computeChallengeResponses")

        // 1. Trustee state
        val trusteeDto = json.decodeFromString<TrusteeJson>(trusteeStateJson)
        val decryptingTrustee = trusteeDto.importDecryptingTrustee(group, errs.nested("trustee"))
            ?: error("TrusteeJson.importDecryptingTrustee FAIL: $errs")

        // 2. Challenges parse
        val challengesDto = json.decodeFromString<ChallengeRequestsJson>(challengesJson)
        val challengesResult = challengesDto.`import`(group)
        if (challengesResult is Err) {
            error("ChallengeRequests import FAIL: ${challengesResult.error}")
        }
        val challengeRequests = challengesResult.unwrap().requests

        // 3. Local challenge
        val responses = decryptingTrustee.challenge(group, challengeRequests)
        Log.i(TAG, "computeChallengeResponses: trustee=${decryptingTrustee.id()}, requests=${challengeRequests.size}, responses=${responses.size}")

        // 4. JSON serialize
        val responsesWrapper = ChallengeResponses(responses)
        val responsesJson: ChallengeResponsesJson = responsesWrapper.publishJson()
        return json.encodeToString(responsesJson)
    }

    /** ElectionGuardSerde.parseElectionContext'un mobile karşılığı. */
    private fun parseElectionContext(jsonString: String): Pair<electionguard.json2.ElectionInitializedJson, electionguard.json2.ElectionConfigJson> {
        val root: kotlinx.serialization.json.JsonObject =
            json.parseToJsonElement(jsonString).let { it as kotlinx.serialization.json.JsonObject }
        val initElement = root["init"] ?: error("composite 'init' eksik")
        val configElement = root["config"] ?: error("composite 'config' eksik")
        val initDto = json.decodeFromString<electionguard.json2.ElectionInitializedJson>(
            json.encodeToString(initElement))
        val configDto = json.decodeFromString<electionguard.json2.ElectionConfigJson>(
            json.encodeToString(configElement))
        return initDto to configDto
    }
}
