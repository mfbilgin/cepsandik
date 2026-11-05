package expo.modules.integrity

import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Cihaz bütünlüğü doğrulaması (Google Play Integrity API, Standard request).
 *
 * Akış: prepareIntegrityToken (bir kez, provider önbelleğe alınır) →
 * request(requestHash) → şifreli token JS'e döner. Kararı sunucu verir.
 *
 * Hata kodu JS'e "E_INTEGRITY_<kod>" olarak iletilir; JS tarafı geçici
 * hataları (ağ, sunucu yoğunluğu) kalıcı hatalardan (Play Store yok vb.) ayırır.
 */
class IntegrityModule : Module() {

  private var provider: StandardIntegrityTokenProvider? = null
  private var preparedFor: Long? = null

  override fun definition() = ModuleDefinition {
    Name("Integrity")

    AsyncFunction("requestToken") { cloudProjectNumber: String, requestHash: String, promise: Promise ->
      val context = appContext.reactContext
      if (context == null) {
        promise.reject("E_INTEGRITY_NO_CONTEXT", "React context is not available", null)
        return@AsyncFunction
      }
      val projectNumber = cloudProjectNumber.toLongOrNull()
      if (projectNumber == null) {
        promise.reject("E_INTEGRITY_BAD_PROJECT", "Invalid cloud project number", null)
        return@AsyncFunction
      }

      val cached = provider
      if (cached != null && preparedFor == projectNumber) {
        request(cached, requestHash, promise) { prepareAndRequest(context, projectNumber, requestHash, promise) }
      } else {
        prepareAndRequest(context, projectNumber, requestHash, promise)
      }
    }
  }

  private fun prepareAndRequest(
    context: android.content.Context,
    projectNumber: Long,
    requestHash: String,
    promise: Promise
  ) {
    IntegrityManagerFactory.createStandard(context)
      .prepareIntegrityToken(
        PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(projectNumber).build()
      )
      .addOnSuccessListener { p ->
        provider = p
        preparedFor = projectNumber
        request(p, requestHash, promise, onProviderInvalid = null)
      }
      .addOnFailureListener { reject(promise, "prepare", it) }
  }

  private fun request(
    p: StandardIntegrityTokenProvider,
    requestHash: String,
    promise: Promise,
    onProviderInvalid: (() -> Unit)?
  ) {
    p.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build())
      .addOnSuccessListener { response -> promise.resolve(response.token()) }
      .addOnFailureListener { error ->
        // Önbellekteki provider süresi dolmuş olabilir → bir kez yeniden hazırla.
        if (onProviderInvalid != null && error is StandardIntegrityException) {
          Log.w(TAG, "Cached provider failed (${error.errorCode}), re-preparing")
          provider = null
          preparedFor = null
          onProviderInvalid()
        } else {
          reject(promise, "request", error)
        }
      }
  }

  private fun reject(promise: Promise, stage: String, error: Exception) {
    val code = (error as? StandardIntegrityException)?.errorCode?.toString() ?: "UNKNOWN"
    Log.e(TAG, "Integrity $stage failed: code=$code ${error.message}")
    promise.reject("E_INTEGRITY_$code", error.message ?: "Integrity $stage failed", error)
  }

  companion object {
    private const val TAG = "Integrity"
  }
}
