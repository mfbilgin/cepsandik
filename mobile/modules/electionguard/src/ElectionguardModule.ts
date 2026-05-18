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

// Faz 1.4 — Benaloh challenge / spoil. Cihaz primary nonce'ı AÇAR;
// sunucu DecryptWithNonce ile bağımsız doğrular. Spoiled ballot sayılmaz.
export type SpoilBallotResult = {
  encryptedBallot: string;
  primaryNonce: string;
  plaintextBallot: string;
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

// Sprint 5.A.distributed — gerçek dağıtık key ceremony (3 stateless fonksiyon)

export type GenerateSingleGuardianKeyInput = {
  electionId: string;
  guardianId: string;
  xCoordinate: number;
  n: number;
  q: number;
};

export type GenerateSingleGuardianKeyResult = {
  electionId: string;
  guardianId: string;
  xCoordinate: number;
  publicKeysJson: string;        // sunucuya (POST /public-key)
  secretPolynomialJson: string;  // SecureStore (SECRET)
  elapsedMs: number;
};

export type ComputeEncryptedKeySharesInput = {
  guardianId: string;
  xCoordinate: number;
  n: number;
  q: number;
  secretPolynomialJson: string;
  peerPublicKeysJsons: string[];
};

export type EncryptedKeyShareOut = {
  toGuardianId: string;
  encryptedKeyShareJson: string;
};

export type FinalizeGuardianKeyInput = {
  guardianId: string;
  xCoordinate: number;
  n: number;
  q: number;
  secretPolynomialJson: string;
  peerPublicKeysJsons: string[];
  encryptedSharesForMeJsons: string[];
};

export type FinalizeGuardianKeyResult = {
  guardianId: string;
  trusteeStateJson: string; // SecureStore (SECRET, tally için)
  isComplete: boolean;
  elapsedMs: number;
};

declare class ElectionguardModule extends NativeModule {
  encryptBallot(input: EncryptBallotInput): Promise<EncryptBallotResult>;

  // Faz 1.4 — Benaloh challenge: spoil et (cast etme), primary nonce aç
  spoilBallot(input: EncryptBallotInput): Promise<SpoilBallotResult>;

  // Sprint 5.B Guardian crypto (mobile-only private state)
  generateAllGuardianKeys(input: GenerateGuardianKeysInput): Promise<GenerateGuardianKeysResult>;
  computePartialDecryption(input: ComputePartialDecryptionInput): Promise<string>; // DecryptResponseJson
  computeChallengeResponses(input: ComputeChallengeResponsesInput): Promise<string>; // ChallengeResponsesJson

  // Sprint 5.A.distributed — gerçek dağıtık key ceremony
  generateSingleGuardianKey(input: GenerateSingleGuardianKeyInput): Promise<GenerateSingleGuardianKeyResult>;
  computeEncryptedKeyShares(input: ComputeEncryptedKeySharesInput): Promise<EncryptedKeyShareOut[]>;
  finalizeGuardianKey(input: FinalizeGuardianKeyInput): Promise<FinalizeGuardianKeyResult>;
}

// JSI-loaded native module. iOS placeholder şu an reject ediyor.
export default requireNativeModule<ElectionguardModule>('Electionguard');
