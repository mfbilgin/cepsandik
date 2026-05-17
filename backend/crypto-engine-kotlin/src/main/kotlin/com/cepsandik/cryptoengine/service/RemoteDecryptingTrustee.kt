package com.cepsandik.cryptoengine.service

import electionguard.core.ElementModP
import electionguard.core.GroupContext
import electionguard.decrypt.ChallengeRequest
import electionguard.decrypt.ChallengeResponse
import electionguard.decrypt.DecryptingTrusteeIF
import electionguard.decrypt.PartialDecryption
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Gerçek E2E-V için blocking proxy: KMP `DecryptingTrusteeIF` implement eder
 * ama özel anahtar payı (`keyShare`, `polynomialCoefficients`) sunucuda HİÇ
 * bulunmaz. Sunucu sadece `id`, `xCoordinate`, `guardianPublicKey` tutar.
 *
 * `decrypt()` ve `challenge()` çağrıları bir CompletableFuture üzerinde
 * BLOCKING bekler — uzaktaki mobil cihazın HTTP üzerinden yanıt göndermesini
 * bekler. `submitPartialDecryption()` / `submitChallengeResponse()` dış
 * dünyadan (Java orchestrator veya HTTP endpoint) çağrılır ve future'ı
 * tamamlar. DecryptorDoerre kendisi senkron olduğu için bu blocking pattern
 * şart — KMP `DecryptingTrusteeIF` suspend değil.
 *
 * Pending task data — `decrypt()` çağrıldığında listOfA'yı dışarıdan
 * gözleyebilecek bir snapshot tutar; mobile cihaz bunu pull edip lokal
 * decrypt + challenge response'u hesaplar.
 *
 * Tek seferlik kullanım: aynı instance ile bir decrypt() ve sonra bir
 * challenge() bekler. Yeniden başlatma için yeni instance gerek.
 */
class RemoteDecryptingTrustee(
    private val guardianId: String,
    private val xCoord: Int,
    private val publicKey: ElementModP,
    // Manuel çok-cihaz UAT: guardian'ların login+tap senkronu kaçınılmaz
    // olarak 5 dk'yı aşar (3 kişi 3 cihaz). decrypt() ilk-partial bekleme
    // penceresi geniş tutulur; challenge tarafı zaten client retry-loop'lu.
    private val timeoutMs: Long = 1_800_000L,
) : DecryptingTrusteeIF {

    private val log = LoggerFactory.getLogger(javaClass)

    /** decrypt() çağrılınca buraya bir A listesi düşer; mobile pull edip görür. */
    private val pendingDecryptInput: AtomicReference<List<ElementModP>?> = AtomicReference(null)

    /** Mobile'dan beklenen partial decryption sonucu — submitPartialDecryption ile dolar. */
    private val partialDecryptionFuture: CompletableFuture<List<PartialDecryption>> = CompletableFuture()

    /** challenge() çağrılınca buraya istek listesi düşer; mobile pull edip görür. */
    private val pendingChallengeInput: AtomicReference<List<ChallengeRequest>?> = AtomicReference(null)

    /** Mobile'dan beklenen challenge response — submitChallengeResponse ile dolar. */
    private val challengeResponseFuture: CompletableFuture<List<ChallengeResponse>> = CompletableFuture()

    // ===== DecryptingTrusteeIF — DecryptorDoerre buradan çağırır (BLOCKING) =====

    override fun id(): String = guardianId
    override fun xCoordinate(): Int = xCoord
    override fun guardianPublicKey(): ElementModP = publicKey

    override fun decrypt(group: GroupContext, texts: List<ElementModP>): List<PartialDecryption> {
        log.info("RemoteDecryptingTrustee[{}].decrypt(): {} elements — waiting for mobile partial...",
            guardianId, texts.size)
        pendingDecryptInput.set(texts)
        try {
            return partialDecryptionFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: TimeoutException) {
            throw IllegalStateException("Trustee $guardianId partial decryption timeout (${timeoutMs}ms)", t)
        }
    }

    override fun challenge(group: GroupContext, challenges: List<ChallengeRequest>): List<ChallengeResponse> {
        log.info("RemoteDecryptingTrustee[{}].challenge(): {} requests — waiting for mobile response...",
            guardianId, challenges.size)
        pendingChallengeInput.set(challenges)
        try {
            return challengeResponseFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: TimeoutException) {
            throw IllegalStateException("Trustee $guardianId challenge response timeout (${timeoutMs}ms)", t)
        }
    }

    // ===== External — Java orchestrator / HTTP endpoint buradan çağırır =====

    /** Mobile cihazın decrypt için işlemesi gereken giriş; null ise henüz hazır değil. */
    fun pollDecryptInput(): List<ElementModP>? = pendingDecryptInput.get()

    /** Mobile cihazdan gelen partial decryption sonucunu submit eder; decrypt() bunu döndürür. */
    fun submitPartialDecryption(partials: List<PartialDecryption>) {
        log.info("RemoteDecryptingTrustee[{}].submitPartialDecryption: {} partials", guardianId, partials.size)
        if (!partialDecryptionFuture.complete(partials)) {
            log.warn("Trustee {} partial decryption was already submitted; ignoring duplicate", guardianId)
        }
    }

    /** Mobile cihazın challenge için işlemesi gereken giriş; null ise henüz hazır değil. */
    fun pollChallengeInput(): List<ChallengeRequest>? = pendingChallengeInput.get()

    /** Mobile cihazdan gelen challenge response'u submit eder; challenge() bunu döndürür. */
    fun submitChallengeResponse(responses: List<ChallengeResponse>) {
        log.info("RemoteDecryptingTrustee[{}].submitChallengeResponse: {} responses", guardianId, responses.size)
        if (!challengeResponseFuture.complete(responses)) {
            log.warn("Trustee {} challenge response was already submitted; ignoring duplicate", guardianId)
        }
    }

    /** Iptal — hata durumunda DecryptorDoerre'ı timeout'tan kurtarmak için. */
    fun fail(reason: String) {
        log.error("RemoteDecryptingTrustee[{}].fail: {}", guardianId, reason)
        val ex = IllegalStateException("Trustee $guardianId failed: $reason")
        partialDecryptionFuture.completeExceptionally(ex)
        challengeResponseFuture.completeExceptionally(ex)
    }
}
