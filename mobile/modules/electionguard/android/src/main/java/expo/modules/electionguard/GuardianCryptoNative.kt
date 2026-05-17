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
import electionguard.json2.EncryptedKeyShareJson
import electionguard.json2.ElementModQJson
import electionguard.json2.PublicKeysJson
import electionguard.json2.TrusteeJson
import electionguard.json2.`import`
import electionguard.json2.importDecryptingTrustee
import electionguard.json2.publishJson
import electionguard.keyceremony.KeyCeremonyTrustee
import electionguard.keyceremony.keyCeremonyExchange
import electionguard.keyceremony.regeneratePolynomial
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
     * Tally decrypt(): partial + challenge response AYNI DecryptingTrusteeDoerre
     * instance'ı ister. KMP DecryptingTrusteeDoerre constructor'da
     * `randomConstantNonce`'ı RASTGELE üretir ve TrusteeJson'a SERIALIZE ETMEZ.
     * decrypt() commitment'ı `u_i + randomConstantNonce` blind'ler;
     * challenge() `getNonce() - randomConstantNonce` ile unblind eder. Mobil
     * native her AsyncFunction çağrısında trustee'yi JSON'dan yeniden kurarsa
     * iki round farklı nonce kullanır → tüm Chaum-Pedersen proof'ları fail
     * ("ai != ai'"). Bu yüzden decrypt()'te kurulan instance challenge()'a
     * kadar bellekte tutulur (SelfTest'teki tek `localDecryptor` ile aynı).
     * Anahtar = SHA-256(trusteeStateJson) — iki round aynı SecureStore
     * TrusteeJson'unu okur, eşleşir.
     */
    private val liveDecryptors =
        java.util.concurrent.ConcurrentHashMap<String, DecryptingTrusteeDoerre>()

    private fun decryptorCacheKey(trusteeStateJson: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(trusteeStateJson.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

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

    // ==================== Sprint 5.A.distributed — gerçek dağıtık ====================
    //
    // Her cihaz SADECE 1 trustee taşır. Trustee objesi adımlar arası saklanmaz
    // (app 24h ceremony penceresinde restart olabilir); tek kalıcı secret polinom
    // katsayılarıdır (secretPolynomialJson, SecureStore'da). Her çağrıda trustee
    // polinomdan yeniden kurulur (regeneratePolynomial) ve sunucudan gelen public
    // state replay edilir. Plaintext keyShareFor/receiveKeyShare round'u (KMP
    // steps 5-6) ATLANIR — sunucudan plaintext share geçemez; computeSecretKeyShare
    // ve publishJson sadece steps 1-4'e (myShareOfOthers) bağlıdır.

    /** Polinom katsayılarından (SecureStore) trustee'yi yeniden kurar. */
    private fun reconstructTrustee(
        guardianId: String,
        xCoordinate: Int,
        n: Int,
        q: Int,
        secretPolynomialJson: String,
    ): KeyCeremonyTrustee {
        val coeffs = json.decodeFromString<List<ElementModQJson>>(secretPolynomialJson)
            .map { it.`import`(group) ?: error("polinom katsayısı import FAIL ($guardianId)") }
        val poly = group.regeneratePolynomial(guardianId, q, coeffs)
        return KeyCeremonyTrustee(group, guardianId, xCoordinate, n, q, poly)
    }

    /**
     * Round 1 — Tek guardian key üretimi. Fresh KeyCeremonyTrustee yaratır;
     * gizli polinom katsayılarını (SecureStore için) ve sunucuya gidecek
     * PublicKeysJson'u döner. Trustee objesi burada atılır.
     */
    fun generateSingleGuardianKey(
        electionId: String,
        guardianId: String,
        xCoordinate: Int,
        n: Int,
        q: Int,
    ): Map<String, Any?> {
        require(n >= 1) { "n >= 1" }
        require(q in 1..n) { "q must be in 1..$n" }
        require(xCoordinate >= 1) { "xCoordinate >= 1 (polinom x, 1-indexed)" }

        lateinit var publicKeysJson: String
        lateinit var secretPolynomialJson: String
        val ms = measureTimeMillis {
            val t = KeyCeremonyTrustee(group, guardianId, xCoordinate, nguardians = n, quorum = q)
            secretPolynomialJson = json.encodeToString(
                t.polynomial.coefficients.map { it.publishJson() })
            publicKeysJson = json.encodeToString(t.publicKeys().unwrap().publishJson())
        }
        Log.i(TAG, "generateSingleGuardianKey: id=$guardianId x=$xCoordinate n=$n q=$q " +
            "in ${ms}ms, polyBytes=${secretPolynomialJson.length}, pkBytes=${publicKeysJson.length}")

        return mapOf(
            "electionId" to electionId,
            "guardianId" to guardianId,
            "xCoordinate" to xCoordinate,
            "publicKeysJson" to publicKeysJson,           // sunucuya (POST /keys)
            "secretPolynomialJson" to secretPolynomialJson, // SecureStore (SECRET)
            "elapsedMs" to ms,
        )
    }

    /**
     * Round 2 — Peer public key'leri al, her peer için encrypted key share üret.
     * Trustee polinomdan yeniden kurulur. Dönen şifreli share'ler sunucu üzerinden
     * (opak) ilgili peer'a forward edilir; sunucu içeriği göremez.
     */
    fun computeEncryptedKeyShares(
        guardianId: String,
        xCoordinate: Int,
        n: Int,
        q: Int,
        secretPolynomialJson: String,
        peerPublicKeysJsons: List<String>,
    ): List<Map<String, Any?>> {
        val result = ArrayList<Map<String, Any?>>()
        val ms = measureTimeMillis {
            val t = reconstructTrustee(guardianId, xCoordinate, n, q, secretPolynomialJson)
            val peerIds = ArrayList<String>()
            peerPublicKeysJsons.forEach { pkStr ->
                val errs = ErrorMessages("peer-pk")
                val pk = json.decodeFromString<PublicKeysJson>(pkStr).`import`(group, errs)
                    ?: error("peer PublicKeysJson import FAIL: $errs")
                if (pk.guardianId == guardianId) return@forEach // kendini atla
                val rr = t.receivePublicKeys(pk)
                if (rr is Err) error("receivePublicKeys(${pk.guardianId}) FAIL: ${rr.error}")
                peerIds.add(pk.guardianId)
            }
            peerIds.forEach { peerId ->
                val eks = t.encryptedKeyShareFor(peerId).unwrap()
                result.add(mapOf(
                    "toGuardianId" to peerId,
                    "encryptedKeyShareJson" to json.encodeToString(eks.publishJson()),
                ))
            }
        }
        Log.i(TAG, "computeEncryptedKeyShares: id=$guardianId peers=${result.size} in ${ms}ms")
        return result
    }

    /**
     * Round 3 — Bana gelen şifreli share'leri al, TrusteeJson (key_share içeren
     * tally secret'ı) üret. Plaintext receiveKeyShare ATLANIR (steps 5-6).
     * isComplete()==false beklenir ama publishJson computeSecretKeyShare'e
     * (sadece steps 1-4) bağlı olduğu için geçerli TrusteeJson üretilir.
     */
    fun finalizeGuardianKey(
        guardianId: String,
        xCoordinate: Int,
        n: Int,
        q: Int,
        secretPolynomialJson: String,
        peerPublicKeysJsons: List<String>,
        encryptedSharesForMeJsons: List<String>,
    ): Map<String, Any?> {
        lateinit var trusteeStateJson: String
        var isComplete = false
        val ms = measureTimeMillis {
            val t = reconstructTrustee(guardianId, xCoordinate, n, q, secretPolynomialJson)
            peerPublicKeysJsons.forEach { pkStr ->
                val errs = ErrorMessages("peer-pk2")
                val pk = json.decodeFromString<PublicKeysJson>(pkStr).`import`(group, errs)
                    ?: error("peer PublicKeysJson import FAIL: $errs")
                if (pk.guardianId == guardianId) return@forEach
                t.receivePublicKeys(pk)
            }
            encryptedSharesForMeJsons.forEach { eksStr ->
                val eks = json.decodeFromString<EncryptedKeyShareJson>(eksStr).`import`(group)
                    ?: error("EncryptedKeyShareJson import FAIL (to=$guardianId)")
                val rr = t.receiveEncryptedKeyShare(eks)
                if (rr is Err) error("receiveEncryptedKeyShare FAIL: ${rr.error}")
            }
            trusteeStateJson = json.encodeToString(t.publishJson())
            isComplete = t.isComplete()
        }
        Log.i(TAG, "finalizeGuardianKey: id=$guardianId in ${ms}ms, isComplete=$isComplete " +
            "(false beklenir, steps 5-6 skip), trusteeBytes=${trusteeStateJson.length}")

        return mapOf(
            "guardianId" to guardianId,
            "trusteeStateJson" to trusteeStateJson, // SecureStore (SECRET, tally için)
            "isComplete" to isComplete,
            "elapsedMs" to ms,
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
        // contextBundle = Pair(initDto: ElectionInitializedJson, configDto: ElectionConfigJson)
        // ElectionConfigJson.import(ElectionConstants, ByteArray, ErrorMessages)
        // ElectionInitializedJson.import(GroupContext, ElectionConfig, ErrorMessages)
        val config = contextBundle.second.`import`(group.constants, manifestBytes, errs.nested("config"))
            ?: error("ElectionConfig import FAIL: $errs")
        val electionInit = contextBundle.first.`import`(group, config, errs.nested("init"))
            ?: error("ElectionInitialized import FAIL: $errs")
        val encryptedTallyDto = json.decodeFromString<EncryptedTallyJson>(encryptedTallyJson)
        val encryptedTally = encryptedTallyDto.`import`(group, errs.nested("tally"))
            ?: error("EncryptedTally import FAIL: $errs")

        // 2. Trustee state — TrusteeJson → DecryptingTrusteeDoerre
        val trusteeDto = json.decodeFromString<TrusteeJson>(trusteeStateJson)
        val decryptingTrustee: DecryptingTrusteeDoerre = trusteeDto.importDecryptingTrustee(group, errs.nested("trustee"))
            ?: error("TrusteeJson.importDecryptingTrustee FAIL: $errs")
        // challenge() bu instance'ı (randomConstantNonce dahil) ister — cache'le.
        liveDecryptors[decryptorCacheKey(trusteeStateJson)] = decryptingTrustee

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

        // 1. Trustee state — decrypt()'te kurulan AYNI instance şart
        // (randomConstantNonce TrusteeJson'da yok; yeniden kurulursa unblind
        // yanlış nonce ile yapılır → tüm proof'lar "ai != ai'" fail).
        val cacheKey = decryptorCacheKey(trusteeStateJson)
        val decryptingTrustee = liveDecryptors[cacheKey]
            ?: error(
                "Partial decryption instance bulunamadı (randomConstantNonce kaybı). " +
                    "Aynı oturumda önce computePartialDecryption çağrılmalı; uygulama " +
                    "partial ile challenge arası kapanmış olabilir — tally'yi baştan başlatın."
            )

        // 2. Challenges parse
        val challengesDto = json.decodeFromString<ChallengeRequestsJson>(challengesJson)
        val challengesResult = challengesDto.`import`(group)
        if (challengesResult is Err) {
            error("ChallengeRequests import FAIL: ${challengesResult.error}")
        }
        // json2.ChallengeRequests.getChallenges(): List<decrypt.ChallengeRequest>
        val challengeRequests = challengesResult.unwrap().challenges

        // 3. Local challenge — tek-atışlık; instance kullanıldı, cache'i boşalt.
        val responses = decryptingTrustee.challenge(group, challengeRequests)
        liveDecryptors.remove(cacheKey)
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
