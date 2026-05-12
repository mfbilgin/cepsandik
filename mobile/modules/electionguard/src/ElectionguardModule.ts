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

declare class ElectionguardModule extends NativeModule {
  encryptBallot(input: EncryptBallotInput): Promise<EncryptBallotResult>;
}

// JSI-loaded native module. iOS placeholder şu an reject ediyor.
export default requireNativeModule<ElectionguardModule>('Electionguard');
