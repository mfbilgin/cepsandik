import { NativeModule, requireNativeModule } from 'expo';

export type EncryptBallotInput = {
  electionId: number;
  ballotId: string;
  electionManifest: string;
  electionGuardContext: string;
  jointPublicKey: string;
  specVersion: string;
  selections: Array<{ id: number; displayOrder?: number | null }>;
  selectedOptionId: number;
};

export type EncryptBallotResult = {
  encryptedBallot: string;
  zkpProof: string;
  trackingCode: string;
};

// Sprint 5.B — Guardian crypto types

export type GenerateGuardianKeysInput = {
  electionId: string;
  n: number;
  q: number;
};

export type GenerateGuardianKeysResult = {
  electionId: string;
  n: number;
  q: number;
  publicKeysJsons: string[];      // N adet — sunucuya gönderilir (PublicKeysJson serialize)
  trusteeStateJsons: string[];    // N adet PRIVATE — SecureStore'da kalır
  jointPublicKeyHex: string;
  exchangeMs: number;
};

export type ComputePartialDecryptionInput = {
  trusteeStateJson: string;
  encryptedTallyJson: string;
  electionGuardContextJson: string;
  electionManifestJson: string;
};

export type ComputeChallengeResponsesInput = {
  trusteeStateJson: string;
  challengesJson: string;
};

declare class ElectionguardModule extends NativeModule {
  encryptBallot(input: EncryptBallotInput): Promise<EncryptBallotResult>;

  // Sprint 5.B Guardian crypto (mobile-only private state)
  generateAllGuardianKeys(input: GenerateGuardianKeysInput): Promise<GenerateGuardianKeysResult>;
  computePartialDecryption(input: ComputePartialDecryptionInput): Promise<string>; // DecryptResponseJson
  computeChallengeResponses(input: ComputeChallengeResponsesInput): Promise<string>; // ChallengeResponsesJson
}

// JSI-loaded native module. iOS placeholder şu an reject ediyor.
export default requireNativeModule<ElectionguardModule>('Electionguard');
