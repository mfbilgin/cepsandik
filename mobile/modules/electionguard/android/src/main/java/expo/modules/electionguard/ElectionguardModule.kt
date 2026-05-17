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

    // ===== Sprint 5.B — Guardian crypto (true E2E-V) =====

    AsyncFunction("generateAllGuardianKeys") { input: GenerateGuardianKeysInput ->
      val t0 = System.currentTimeMillis()
      try {
        val result = GuardianCryptoNative.generateAllGuardianKeys(input.electionId, input.n, input.q)
        Log.i("Electionguard", "AsyncFunction generateAllGuardianKeys returned in ${System.currentTimeMillis() - t0}ms")
        result
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction generateAllGuardianKeys FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }

    AsyncFunction("computePartialDecryption") { input: ComputePartialDecryptionInput ->
      val t0 = System.currentTimeMillis()
      try {
        val partialsJson = GuardianCryptoNative.computePartialDecryption(
            trusteeStateJson = input.trusteeStateJson,
            encryptedTallyJson = input.encryptedTallyJson,
            electionGuardContextJson = input.electionGuardContextJson,
            electionManifestJson = input.electionManifestJson,
        )
        Log.i("Electionguard", "AsyncFunction computePartialDecryption returned in ${System.currentTimeMillis() - t0}ms")
        partialsJson
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction computePartialDecryption FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }

    // ===== Sprint 5.A.distributed — gerçek dağıtık key ceremony (3 fonksiyon) =====

    AsyncFunction("generateSingleGuardianKey") { input: GenerateSingleGuardianKeyInput ->
      val t0 = System.currentTimeMillis()
      try {
        val result = GuardianCryptoNative.generateSingleGuardianKey(
            electionId = input.electionId,
            guardianId = input.guardianId,
            xCoordinate = input.xCoordinate,
            n = input.n,
            q = input.q,
        )
        Log.i("Electionguard", "AsyncFunction generateSingleGuardianKey returned in ${System.currentTimeMillis() - t0}ms")
        result
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction generateSingleGuardianKey FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }

    AsyncFunction("computeEncryptedKeyShares") { input: ComputeEncryptedKeySharesInput ->
      val t0 = System.currentTimeMillis()
      try {
        val result = GuardianCryptoNative.computeEncryptedKeyShares(
            guardianId = input.guardianId,
            xCoordinate = input.xCoordinate,
            n = input.n,
            q = input.q,
            secretPolynomialJson = input.secretPolynomialJson,
            peerPublicKeysJsons = input.peerPublicKeysJsons,
        )
        Log.i("Electionguard", "AsyncFunction computeEncryptedKeyShares returned in ${System.currentTimeMillis() - t0}ms")
        result
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction computeEncryptedKeyShares FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }

    AsyncFunction("finalizeGuardianKey") { input: FinalizeGuardianKeyInput ->
      val t0 = System.currentTimeMillis()
      try {
        val result = GuardianCryptoNative.finalizeGuardianKey(
            guardianId = input.guardianId,
            xCoordinate = input.xCoordinate,
            n = input.n,
            q = input.q,
            secretPolynomialJson = input.secretPolynomialJson,
            peerPublicKeysJsons = input.peerPublicKeysJsons,
            encryptedSharesForMeJsons = input.encryptedSharesForMeJsons,
        )
        Log.i("Electionguard", "AsyncFunction finalizeGuardianKey returned in ${System.currentTimeMillis() - t0}ms")
        result
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction finalizeGuardianKey FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }

    AsyncFunction("computeChallengeResponses") { input: ComputeChallengeResponsesInput ->
      val t0 = System.currentTimeMillis()
      try {
        val responsesJson = GuardianCryptoNative.computeChallengeResponses(
            trusteeStateJson = input.trusteeStateJson,
            challengesJson = input.challengesJson,
        )
        Log.i("Electionguard", "AsyncFunction computeChallengeResponses returned in ${System.currentTimeMillis() - t0}ms")
        responsesJson
      } catch (t: Throwable) {
        Log.e("Electionguard", "AsyncFunction computeChallengeResponses FAILED after ${System.currentTimeMillis() - t0}ms: ${t.message}")
        throw t
      }
    }
  }
}

class GenerateGuardianKeysInput : Record {
  @Field var electionId: String = ""
  @Field var n: Int = 0
  @Field var q: Int = 0
}

class ComputePartialDecryptionInput : Record {
  @Field var trusteeStateJson: String = ""
  @Field var encryptedTallyJson: String = ""
  @Field var electionGuardContextJson: String = ""
  @Field var electionManifestJson: String = ""
}

class ComputeChallengeResponsesInput : Record {
  @Field var trusteeStateJson: String = ""
  @Field var challengesJson: String = ""
}

class GenerateSingleGuardianKeyInput : Record {
  @Field var electionId: String = ""
  @Field var guardianId: String = ""
  @Field var xCoordinate: Int = 0
  @Field var n: Int = 0
  @Field var q: Int = 0
}

class ComputeEncryptedKeySharesInput : Record {
  @Field var guardianId: String = ""
  @Field var xCoordinate: Int = 0
  @Field var n: Int = 0
  @Field var q: Int = 0
  @Field var secretPolynomialJson: String = ""
  @Field var peerPublicKeysJsons: List<String> = emptyList()
}

class FinalizeGuardianKeyInput : Record {
  @Field var guardianId: String = ""
  @Field var xCoordinate: Int = 0
  @Field var n: Int = 0
  @Field var q: Int = 0
  @Field var secretPolynomialJson: String = ""
  @Field var peerPublicKeysJsons: List<String> = emptyList()
  @Field var encryptedSharesForMeJsons: List<String> = emptyList()
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
