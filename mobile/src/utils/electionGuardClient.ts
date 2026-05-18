// E2E-V client-side ElGamal şifrelemesi.
//
// Faz 3 (Yol 1a): mobile/modules/electionguard altındaki Expo Modules paketi
// Android Kotlin'de KMP fat JAR'ı çağırıyor. iOS placeholder — Mac/Xcode hazır
// olunca KMP iOS framework export'uyla doldurulacak.
//
// Backend tarafı (crypto-engine-kotlin) aynı KMP versiyonunu kullanıyor →
// cross-impl spec uyumu garanti.

import ElectionguardModule from '../../modules/electionguard';
import type { EncryptBallotResult, SpoilBallotResult } from '../../modules/electionguard';

export type ElectionGuardOptionRef = {
    id: number;
    displayOrder?: number | null;
};

export type ClientEncryptedBallot = EncryptBallotResult;

export type EncryptionParams = {
    electionId: number;
    specVersion: string;
    electionGuardContext: string;
    electionManifest: string;
    jointPublicKey: string;
};

const SUPPORTED_SPEC_VERSIONS = new Set(['v2.0', '2.0']);

function assertValidEncryptionParams(params: EncryptionParams): void {
    if (!params.electionGuardContext?.trim() || !params.electionManifest?.trim()) {
        throw new Error('ElectionGuard manifest/context bulunamadı.');
    }
    if (!params.jointPublicKey?.trim()) {
        throw new Error('Guardian joint public key gönderilmedi — şifreleme başlatılamaz.');
    }
    if (!SUPPORTED_SPEC_VERSIONS.has(params.specVersion)) {
        throw new Error(
            `Sunucu spec versiyonu (${params.specVersion}) bu uygulama tarafından desteklenmiyor.`
        );
    }
}

export async function encryptBallotClientSide(input: {
    encryptionParams: EncryptionParams;
    ballotId: string;
    options: ElectionGuardOptionRef[];
    selectedOptionId: number;
}): Promise<ClientEncryptedBallot> {
    assertValidEncryptionParams(input.encryptionParams);

    return ElectionguardModule.encryptBallot({
        electionId: input.encryptionParams.electionId,
        ballotId: input.ballotId,
        electionManifest: input.encryptionParams.electionManifest,
        electionGuardContext: input.encryptionParams.electionGuardContext,
        jointPublicKey: input.encryptionParams.jointPublicKey,
        specVersion: input.encryptionParams.specVersion,
        selections: input.options,
        selectedOptionId: input.selectedOptionId,
    });
}

/**
 * Faz 1.4 — Benaloh challenge. Ballot şifrelenir ama CAST EDİLMEZ; spoil
 * edilir ve primary nonce açılır. Sunucu DecryptWithNonce ile bağımsız
 * doğrular (cihaz dürüst mü). Spoiled ballot tally'ye girmez.
 */
export async function spoilBallotClientSide(input: {
    encryptionParams: EncryptionParams;
    ballotId: string;
    options: ElectionGuardOptionRef[];
    selectedOptionId: number;
}): Promise<SpoilBallotResult> {
    assertValidEncryptionParams(input.encryptionParams);

    return ElectionguardModule.spoilBallot({
        electionId: input.encryptionParams.electionId,
        ballotId: input.ballotId,
        electionManifest: input.encryptionParams.electionManifest,
        electionGuardContext: input.encryptionParams.electionGuardContext,
        jointPublicKey: input.encryptionParams.jointPublicKey,
        specVersion: input.encryptionParams.specVersion,
        selections: input.options,
        selectedOptionId: input.selectedOptionId,
    });
}
