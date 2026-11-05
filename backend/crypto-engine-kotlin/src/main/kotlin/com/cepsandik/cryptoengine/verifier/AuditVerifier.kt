package com.cepsandik.cryptoengine.verifier

import com.cepsandik.cryptoengine.service.ElectionGuardSerde
import electionguard.core.productionGroup
import electionguard.json2.EncryptedBallotJson
import electionguard.json2.DecryptedTallyOrBallotJson
import electionguard.json2.import
import electionguard.util.ErrorMessages
import electionguard.util.Stats
import electionguard.verifier.VerifyEncryptedBallots
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Cep Sandık — Standalone Audit Record Verifier.
 *
 * Üçüncü-taraf bağımsız doğrulayıcı. Tek girdi: bir audit-record JSON'u
 * (URL veya dosya). Sunucuya/sisteme HİÇBİR GÜVEN gerekmez — doğrulamayı
 * kendi adımıyla yapar:
 *
 *   1. cryptoContext'ten manifest+context+jointKey kurulur (KMP ile aynı path)
 *   2. Tüm encryptedBallots → VerifyEncryptedBallots ile Chaum-Pedersen ZKP
 *      ve confirmation code doğrulanır
 *   3. tally.tallyProof → DecryptedTallyOrBallotJson olarak parse + structural
 *      olarak doğrulanır (yapı + her selection için Chaum-Pedersen proof varlığı)
 *   4. bulletinBoard.records → SHA-256 zinciri sıfırdan yeniden hesaplanır
 *
 * Çıkış kodu: 0 (tam başarılı) / 1 (en az bir bulgu).
 *
 * Kullanım:
 *   ./gradlew :runVerifier --args="--url http://localhost/api/v1/elections/18/audit-record"
 *   java -cp build/libs/audit-verifier.jar com.cepsandik.cryptoengine.verifier.AuditVerifierKt --file audit.json
 */
object AuditVerifier {

    private const val EXIT_OK = 0
    private const val EXIT_FAIL = 1

    fun run(args: Array<String>): Int {
        val parsed = parseArgs(args) ?: return EXIT_FAIL
        val rawJson = try {
            loadJson(parsed)
        } catch (t: Throwable) {
            err("audit-record yüklenemedi: ${t.message}")
            return EXIT_FAIL
        }

        out("┌─ Cep Sandık Audit Record Verifier")
        out("│  kaynak     : ${parsed.sourceLabel}")
        out("│  byte sayısı: ${rawJson.length}")

        val root: JsonObject = try {
            ElectionGuardSerde.json.parseToJsonElement(rawJson).jsonObject
        } catch (t: Throwable) {
            err("Geçersiz JSON: ${t.message}")
            return EXIT_FAIL
        }

        val electionId = root["electionId"]?.jsonPrimitive?.contentOrNull
            ?: root["electionId"]?.toString()
        val title = root["title"]?.jsonPrimitive?.contentOrNull
        val status = root["status"]?.jsonPrimitive?.contentOrNull
        val quorum = root["quorum"]?.jsonPrimitive?.contentOrNull
        out("│  electionId : $electionId")
        out("│  title      : $title")
        out("│  status     : $status")
        out("│  quorum     : $quorum")
        out("└─")
        out("")

        val findings = mutableListOf<String>()
        val ballotsOk = verifyBallots(root, findings)
        val tallyOk = verifyTallyStructure(root, findings)
        val chainOk = verifyChain(root, findings)

        out("")
        out("═══════════════════════════════════════════════════════════════")
        out("  SONUÇ ÖZETİ")
        out("═══════════════════════════════════════════════════════════════")
        out("  ${flag(ballotsOk)} Şifreli oylar (ZKP + manifest hash + extended base hash)")
        out("  ${flag(tallyOk)}  Tally proof yapısı (DecryptedTallyOrBallot + Chaum-Pedersen alan varlığı)")
        out("  ${flag(chainOk)}  Bulletin board hash-zinciri (SHA-256 sıfırdan yeniden hesap)")
        out("═══════════════════════════════════════════════════════════════")

        if (findings.isNotEmpty()) {
            out("")
            out("  BULGULAR:")
            findings.forEachIndexed { i, f -> out("    [${i + 1}] $f") }
        }

        val allOk = ballotsOk && tallyOk && chainOk
        return if (allOk) {
            out("")
            out("  ✅ AUDIT-RECORD GEÇERLİ — election=$electionId")
            EXIT_OK
        } else {
            out("")
            out("  ❌ AUDIT-RECORD REDDEDİLDİ — election=$electionId, bulgu=${findings.size}")
            EXIT_FAIL
        }
    }

    // ===== 1) Encrypted ballot verification (KMP VerifyEncryptedBallots) =====

    private fun verifyBallots(root: JsonObject, findings: MutableList<String>): Boolean {
        val crypto = root["cryptoContext"]?.jsonObject ?: run {
            findings += "cryptoContext alanı eksik — ballot doğrulanamıyor"
            return false
        }
        val manifestStr = crypto["electionManifest"]?.jsonPrimitive?.contentOrNull ?: run {
            findings += "cryptoContext.electionManifest eksik"
            return false
        }
        val contextStr = crypto["electionGuardContext"]?.jsonPrimitive?.contentOrNull ?: run {
            findings += "cryptoContext.electionGuardContext eksik"
            return false
        }

        val ballots = root["encryptedBallots"]?.jsonArray ?: JsonArray(emptyList())
        if (ballots.isEmpty()) {
            findings += "encryptedBallots boş — doğrulanacak şifreli oy yok"
            return false
        }

        out("[1/3] Şifreli oy doğrulaması — ${ballots.size} ballot …")

        val group = productionGroup()
        val errs = ErrorMessages("verifyBallots")
        val manifestBytes = manifestStr.encodeToByteArray()

        val bundle = try {
            ElectionGuardSerde.parseElectionContext(contextStr)
        } catch (t: Throwable) {
            findings += "electionGuardContext parse hatası: ${t.message}"
            return false
        }
        val config = bundle.config.import(group.constants, manifestBytes, errs.nested("config"))
            ?: run { findings += "ElectionConfig import başarısız: $errs"; return false }
        val electionInit = bundle.init.import(group, config, errs.nested("init"))
            ?: run { findings += "ElectionInitialized import başarısız: $errs"; return false }
        val manifestJson = ElectionGuardSerde.parseManifest(manifestStr)
        val manifest = manifestJson.import()

        val verifier = VerifyEncryptedBallots(
            group, manifest,
            electionInit.jointPublicKey(),
            electionInit.extendedBaseHash,
            config,
            nthreads = 1,
        )

        var pass = 0
        var fail = 0
        val verifyMs = measureTimeMillis {
            ballots.forEachIndexed { idx, jsonElement ->
                val ballotObj = jsonElement.jsonObject
                val ballotId = ballotObj["ballotId"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val cipherJson = ballotObj["encryptedBallot"]?.jsonPrimitive?.contentOrNull
                if (cipherJson.isNullOrBlank()) {
                    fail++; findings += "ballot[$idx] $ballotId: encryptedBallot alanı boş"
                    return@forEachIndexed
                }
                val expectedHashHex = ballotObj["ballotHash"]?.jsonPrimitive?.contentOrNull
                val actualHashHex = sha256Hex(cipherJson)
                if (!expectedHashHex.isNullOrBlank() && expectedHashHex != actualHashHex) {
                    fail++
                    findings += "ballot[$idx] $ballotId: ballotHash uyuşmuyor (kurcalama?)"
                    return@forEachIndexed
                }
                val ballotDto = try {
                    ElectionGuardSerde.json.decodeFromString<EncryptedBallotJson>(cipherJson)
                } catch (t: Throwable) {
                    fail++; findings += "ballot[$idx] $ballotId: JSON parse hatası: ${t.message}"
                    return@forEachIndexed
                }
                val ballot = ballotDto.import(group, errs.nested("b-$ballotId"))
                if (ballot == null) {
                    fail++; findings += "ballot[$idx] $ballotId: EncryptedBallot import başarısız"
                    return@forEachIndexed
                }
                val vErrs = ErrorMessages("verify-$ballotId")
                val ok = verifier.verifyEncryptedBallot(ballot, vErrs, Stats())
                if (ok) {
                    pass++
                } else {
                    fail++; findings += "ballot[$idx] $ballotId: ZKP doğrulanamadı — $vErrs"
                }
            }
        }
        out("       → $pass/${ballots.size} ballot geçti, $fail başarısız (${verifyMs} ms)")
        return fail == 0
    }

    // ===== 2) Tally proof structural validation =====

    private fun verifyTallyStructure(root: JsonObject, findings: MutableList<String>): Boolean {
        val tally = root["tally"]?.jsonObject ?: run {
            findings += "tally alanı eksik"
            return false
        }
        val proofStr = tally["tallyProof"]?.jsonPrimitive?.contentOrNull
        if (proofStr.isNullOrBlank()) {
            findings += "tally.tallyProof boş"
            return false
        }

        out("[2/3] Tally proof yapı doğrulaması …")

        try {
            ElectionGuardSerde.json.decodeFromString<DecryptedTallyOrBallotJson>(proofStr)
        } catch (t: Throwable) {
            findings += "tallyProof DecryptedTallyOrBallotJson olarak parse edilemedi: ${t.message}"
            return false
        }

        // Her selection için Chaum-Pedersen proof alanı dolu mu?
        var totalSelections = 0
        var missingProof = 0
        val contestCount: Int
        try {
            val proofTree = ElectionGuardSerde.json.parseToJsonElement(proofStr).jsonObject
            val contests = proofTree["contests"]?.jsonArray ?: JsonArray(emptyList())
            contestCount = contests.size
            contests.forEach { c ->
                val selections = c.jsonObject["selections"]?.jsonArray ?: JsonArray(emptyList())
                selections.forEach { s ->
                    totalSelections++
                    val sObj = s.jsonObject
                    // EGK proof field: "proof" — Chaum-Pedersen (challenge, response)
                    val proofPresent = sObj["proof"]?.let {
                        it is JsonObject && it.jsonObject.containsKey("challenge")
                                && it.jsonObject.containsKey("response")
                    } ?: false
                    if (!proofPresent) missingProof++
                }
            }
        } catch (t: Throwable) {
            findings += "tallyProof iç yapı taranamadı: ${t.message}"
            return false
        }

        out("       → contest=$contestCount, selection=$totalSelections, eksik proof=$missingProof")
        // Sanity check — proofObj contest sayısı != 0 olmalı
        if (totalSelections == 0) {
            findings += "tallyProof: contest/selection bulunamadı"
            return false
        }
        if (missingProof > 0) {
            findings += "tallyProof: $missingProof selection'da Chaum-Pedersen proof eksik"
            return false
        }
        return true
    }

    // ===== 3) Bulletin board chain re-verification (server-side hesabı tekrarla) =====

    private fun verifyChain(root: JsonObject, findings: MutableList<String>): Boolean {
        val electionId = root["electionId"]?.toString()?.trim('"') ?: ""
        val bb = root["bulletinBoard"]?.jsonObject ?: run {
            findings += "bulletinBoard alanı eksik"
            return false
        }
        val records = bb["records"]?.jsonArray ?: JsonArray(emptyList())
        if (records.isEmpty()) {
            findings += "bulletinBoard.records boş"
            return false
        }
        out("[3/3] Bulletin board hash-zinciri — ${records.size} kayıt …")

        var prevHash: String? = null
        for ((idx, recElem) in records.withIndex()) {
            val rec = recElem.jsonObject
            val recordType = rec["recordType"]?.jsonPrimitive?.contentOrNull ?: ""
            val trackingCode = rec["trackingCode"]?.jsonPrimitive?.contentOrNull ?: ""
            val ballotHash = rec["ballotHash"]?.jsonPrimitive?.contentOrNull ?: ""
            val payload = rec["payload"]?.jsonPrimitive?.contentOrNull ?: ""
            val actual = rec["recordHash"]?.jsonPrimitive?.contentOrNull ?: ""
            val linkedPrev = rec["previousHash"]?.jsonPrimitive?.contentOrNull ?: ""

            val expected = sha256Hex(
                listOf(electionId, recordType, trackingCode, ballotHash,
                    prevHash ?: "", payload).joinToString("|")
            )
            val hashOk = expected.equals(actual, ignoreCase = false)
            val linkOk = if (prevHash == null) linkedPrev.isEmpty()
                         else prevHash == linkedPrev
            if (!hashOk) {
                findings += "chain[$idx] recordType=$recordType: recordHash uyuşmuyor " +
                    "(beklenen=${expected.take(16)}…, gerçek=${actual.take(16)}…)"
                return false
            }
            if (!linkOk) {
                findings += "chain[$idx] recordType=$recordType: previousHash zincir bağlantısı kopuk"
                return false
            }
            prevHash = actual
        }
        out("       → ${records.size}/${records.size} kayıt zinciri tutarlı")
        return true
    }

    // ===== CLI / IO =====

    private data class Source(val sourceLabel: String, val url: String?, val file: String?)

    private fun parseArgs(args: Array<String>): Source? {
        var url: String? = null
        var file: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--url" -> { url = args.getOrNull(i + 1); i += 2 }
                "--file" -> { file = args.getOrNull(i + 1); i += 2 }
                "-h", "--help" -> { printUsage(); return null }
                else -> {
                    err("Bilinmeyen arg: ${args[i]}"); printUsage(); return null
                }
            }
        }
        return when {
            url != null -> Source("URL: $url", url, null)
            file != null -> Source("FILE: $file", null, file)
            else -> { printUsage(); null }
        }
    }

    private fun loadJson(s: Source): String = when {
        s.url != null -> {
            val conn = URI(s.url).toURL().openConnection()
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
        s.file != null -> Files.readString(Paths.get(s.file), StandardCharsets.UTF_8)
        else -> error("kaynak yok")
    }

    private fun printUsage() {
        out("Kullanım: AuditVerifier --url <audit-record-url> | --file <audit-record.json>")
        out("Örnek   : --url http://localhost/api/v1/elections/18/audit-record")
        out("        : --file /tmp/audit-18.json")
    }

    private fun sha256Hex(s: String): String {
        val d = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(d)
    }

    private fun flag(ok: Boolean) = if (ok) "✅" else "❌"
    private fun out(s: String) = println(s)
    private fun err(s: String) = System.err.println("[ERROR] $s")
}

fun main(args: Array<String>) {
    val code = try {
        AuditVerifier.run(args)
    } catch (t: Throwable) {
        System.err.println("[FATAL] doğrulayıcı çöktü: ${t.message}")
        t.printStackTrace(System.err)
        1
    }
    exitProcess(code)
}
