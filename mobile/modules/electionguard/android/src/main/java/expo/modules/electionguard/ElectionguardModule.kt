package expo.modules.electionguard

import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.io.File
import java.util.UUID

/**
 * Native module exposing KMP ElectionGuard encrypt to React Native JS.
 *
 * Tek async fonksiyon: encryptBallot.
 * Input: SetupElection response'undan composite context + manifest + selections.
 * Output: EncryptedBallot JSON (KMP 2.0 format) + trackingCode.
 *
 * Backend (crypto-engine-kotlin) aynı KMP versiyonunu kullandığı için cross-impl
 * uyum garanti — mobile burada şifreler, server validate eder, valid=true bekler.
 */
class ElectionguardModule : Module() {

  override fun definition() = ModuleDefinition {
    Name("Electionguard")

    AsyncFunction("encryptBallot") { input: EncryptBallotInput ->
      val t0 = System.currentTimeMillis()
      val cacheRoot = appContext.reactContext?.cacheDir
          ?: File(System.getProperty("java.io.tmpdir"), "electionguard")
      try {
        val result = ElectionGuardEncryptor.encrypt(input, cacheRoot)
        Log.i("Electionguard", "AsyncFunction encryptBallot returned in ${System.currentTimeMillis() - t0}ms")
        result
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction encryptBallot FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }
  }
}

class EncryptBallotInput : Record {
  @Field var electionId: Int = 0
  @Field var ballotId: String = ""
  @Field var electionManifest: String = ""
  @Field var electionGuardContext: String = ""
  @Field var jointPublicKey: String = ""
  @Field var specVersion: String = ""
  @Field var selections: List<SelectionRef> = emptyList()
  @Field var selectedOptionId: Int = 0
}

class SelectionRef : Record {
  @Field var id: Int = 0
  @Field var displayOrder: Int? = null
}

class EncryptBallotResult : Record {
  @Field var encryptedBallot: String = ""
  @Field var zkpProof: String = ""
  @Field var trackingCode: String = ""
}
