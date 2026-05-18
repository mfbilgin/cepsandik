package com.cepsandik.cryptoengine.service

import com.cepsandik.electionservice.grpc.SpoiledSelection
import com.cepsandik.electionservice.grpc.VerifySpoiledBallotRequest
import com.cepsandik.electionservice.grpc.VerifySpoiledBallotResponse
import electionguard.ballot.PlaintextBallot
import electionguard.core.GroupContext
import electionguard.core.UInt256
import electionguard.core.productionGroup
import electionguard.encrypt.Encryptor
import electionguard.json2.EncryptedBallotJson
import electionguard.json2.PlaintextBallotJson
import electionguard.json2.`import`
import electionguard.util.ErrorMessages
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.HexFormat

/**
 * Faz 1.4 — Benaloh challenge (cast-as-intended) — RE-ENCRYPTION ile TRUSTLESS
 * doğrulama.
 *
 * Seçmen bir ballot'u spoil edince cihaz şunları açar: şifreli ballot
 * (EncryptedBallot), ürettiği PlaintextBallot ve ballot primary nonce'ı.
 * Spoiled ballot ZATEN sayılmaz; plaintext ifşası tasarımın amacıdır.
 *
 * Sunucu cihaza GÜVENMEZ: açılan PlaintextBallot'u, açılan primary nonce ile
 * KMP'nin PROVEN public `Encryptor` API'siyle YENİDEN şifreler. ElGamal
 * şifreleme primary nonce verildiğinde deterministiktir; üretilen her
 * selection ciphertext'i (pad, data) submit edilen ciphertext'le BİREBİR
 * eşleşirse cihaz tam olarak o plaintext'i o nonce ile şifrelemiştir
 * (cast-as-intended kanıtlanır). Cihaz farklı bir oy şifrelediyse re-encrypt
 * sonucu tutmaz → verified=false.
 *
 * Not: selection ElGamal (pad,data) karşılaştırması baux/device/zaman
 * bağımsızdır; sadece (vote, primaryNonce, jointKey, manifest) belirler →
 * sağlam ve KMP `internal` API'ye bağımlı değil.
 */
@Service
class SpoiledBallotService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val group: GroupContext = productionGroup()
    private val json = ElectionGuardSerde.json

    fun verifySpoiled(request: VerifySpoiledBallotRequest): VerifySpoiledBallotResponse {
        val errs = ErrorMessages("verifySpoiled/${request.electionId}")
        try {
            // 1. Context + manifest → ElectionInitialized (joint key + extendedBaseHash)
            val manifestBytes = request.electionManifest.encodeToByteArray()
            val bundle = ElectionGuardSerde.parseElectionContext(request.electionGuardContext)
            val config = bundle.config.`import`(group.constants, manifestBytes, errs.nested("config"))
                ?: error("ElectionConfig import başarısız: $errs")
            val electionInit = bundle.init.`import`(group, config, errs.nested("init"))
                ?: error("ElectionInitialized import başarısız: $errs")
            val manifest = ElectionGuardSerde.parseManifest(request.electionManifest).`import`()

            // 2. Submit edilen spoiled EncryptedBallot
            val ebDto = json.decodeFromString<EncryptedBallotJson>(request.encryptedBallotJson)
            val submitted = ebDto.`import`(group, errs.nested("ballot"))
                ?: error("EncryptedBallot import başarısız: $errs")

            // 3. Cihazın açtığı PlaintextBallot (spoiled — gizli değil)
            val pbDto = json.decodeFromString<PlaintextBallotJson>(request.plaintextBallotJson)
            val plaintext: PlaintextBallot = pbDto.`import`()

            // 4. Cihazın açtığı primary nonce (hex → 32 byte → UInt256)
            val nonceHex = request.primaryNonceHex.trim().removePrefix("0x")
            val nonceBytes = HexFormat.of().parseHex(
                if (nonceHex.length % 2 == 0) nonceHex else "0$nonceHex")
            require(nonceBytes.size == 32) {
                "primary nonce 32 byte olmalı (UInt256), gelen=${nonceBytes.size}"
            }
            val primaryNonce = UInt256(nonceBytes)

            // 5. RE-ENCRYPT — PROVEN public Encryptor, AYNI primary nonce ile.
            //    ElGamal nonce verilince deterministik → cihaz dürüstse birebir
            //    aynı selection ciphertext'leri çıkar.
            val encryptor = Encryptor(
                group, manifest, electionInit.jointPublicKey(),
                electionInit.extendedBaseHash, "spoil-verify")
            val recomputed = encryptor.encrypt(
                plaintext,
                electionInit.config.configBaux0,
                errs.nested("re-encrypt"),
                primaryNonce,
                null)
                ?: error("re-encrypt başarısız: $errs")

            // 6. Selection ciphertext (pad,data) birebir karşılaştır.
            //    (selectionId bazında eşle; biri bile tutmazsa cihaz dürüst değil)
            val recMap = HashMap<String, electionguard.core.ElGamalCiphertext>()
            recomputed.contests.forEach { c ->
                c.selections.forEach { s ->
                    recMap[s.selectionId] = s.ciphertext
                }
            }
            var allMatch = true
            var compared = 0
            submitted.contests.forEach { c ->
                c.selections.forEach { s ->
                    val rec = recMap[s.selectionId]
                    // ElementModP structural eşitlik (ProductionElementModP.equals)
                    val ok = rec != null &&
                        rec.pad == s.encryptedVote.pad &&
                        rec.data == s.encryptedVote.data
                    if (!ok) allMatch = false
                    compared++
                }
            }
            if (compared == 0) allMatch = false

            // 7. Sonuç — eşleşen plaintext seçimleri (artık ciphertext'e
            //    kriptografik olarak bağlı; seçmene gösterilir)
            val sels = ArrayList<SpoiledSelection>()
            plaintext.contests.forEach { c ->
                c.selections.forEach { s ->
                    sels.add(SpoiledSelection.newBuilder()
                        .setSelectionId(s.selectionId)
                        .setVote(s.vote.toLong())
                        .build())
                }
            }

            return if (allMatch) {
                log.info("Spoiled ballot DOĞRULANDI (re-encryption trustless): " +
                    "election={}, ballot={}, selections={}",
                    request.electionId, plaintext.ballotId, compared)
                VerifySpoiledBallotResponse.newBuilder()
                    .setVerified(true)
                    .addAllSelections(sels)
                    .build()
            } else {
                log.warn("Spoiled ballot RE-ENCRYPT EŞLEŞMEDİ: election={}, ballot={} " +
                    "— cihaz açtığı plaintext'i bu nonce ile şifrelememiş (dürüst değil?)",
                    request.electionId, plaintext.ballotId)
                VerifySpoiledBallotResponse.newBuilder()
                    .setVerified(false)
                    .addAllSelections(sels)
                    .setError("Re-encryption submit edilen ciphertext ile eşleşmedi: " +
                        "cihaz açtığı oyu bu nonce ile şifrelememiş olabilir.")
                    .build()
            }
        } catch (t: Throwable) {
            log.error("verifySpoiled FAIL: election={}", request.electionId, t)
            return VerifySpoiledBallotResponse.newBuilder()
                .setVerified(false)
                .setError("verifySpoiled hata: ${t.message}")
                .build()
        }
    }
}
