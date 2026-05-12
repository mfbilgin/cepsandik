import ExpoModulesCore

/**
 * iOS placeholder — Mac/Xcode/KMP iOS framework export'u henüz yok.
 * encryptBallot çağrılırsa anlamlı hata fırlatır.
 *
 * Faz 3.5: KMP iOS framework export edilip Swift bridge yazılacak.
 * Geçici çözüm: Android-only build, iOS testleri Faz 3.5'te.
 */
public class ElectionguardModule: Module {
  public func definition() -> ModuleDefinition {
    Name("Electionguard")

    AsyncFunction("encryptBallot") { (input: [String: Any?]) -> [String: String] in
      throw Exception(
        name: "ElectionGuardNotImplemented",
        description: "iOS native modülü henüz yerleştirilmedi. Faz 3.5'te KMP iOS framework eklenecek."
      )
    }
  }
}
