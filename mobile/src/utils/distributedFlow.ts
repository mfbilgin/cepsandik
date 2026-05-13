/**
 * Sprint 5.A leader-mode owner akışı.
 *
 * Election owner cihazında tüm guardian rollerini taşır:
 *   - Setup: N trustee lokal üret → publicKeysJsons sunucuya → CreateJointKey
 *   - Tally: tally_session_id pull → her trustee için partial + challenge →
 *     finalize
 *
 * Tüm private state mobile SecureStore'da kalır; sunucuya HİÇBİR private key
 * share veya polynomial katsayı GİTMEZ.
 */

import { api } from '../services/api';
import { guardianCrypto } from './guardianCrypto';

/**
 * Owner için distributed setup. N trustee yarat, public part'ları sunucuya yolla.
 */
export async function ownerDistributedSetup(electionId: number, n: number = 3, q: number = 2) {
    const t0 = Date.now();
    const { publicKeysJsons, exchangeMs } = await guardianCrypto.setupAllGuardians(electionId, n, q);
    const guardianIds = Array.from({ length: n }, (_, i) => `trustee${i + 1}`);

    const response = await api.post(`/elections/${electionId}/distributed-setup`, {
        publicKeysJsons,
        guardianIds,
    });
    const elapsed = Date.now() - t0;
    return {
        success: true,
        elapsed,
        exchangeMs,
        n,
        q,
        jointPublicKey: response.data?.data?.electionPublicKey || '',
    };
}

/**
 * Owner için tally akışı: Q trustee için partial + challenge + finalize.
 */
export async function ownerDistributedTally(electionId: number, q: number = 2) {
    const t0 = Date.now();

    // 1. Task pull — tüm trustee'ler için aynı encrypted_tally + context + manifest
    const taskResp = await api.get(`/guardians/${electionId}/tally-task`);
    const { sessionId, encryptedTallyJson, electionGuardContext, electionManifest } = taskResp.data;

    if (!encryptedTallyJson) {
        throw new Error('encryptedTallyJson sunucudan gelmedi (tally session başlatılmadı?)');
    }

    const submittedTrustees: string[] = [];

    // 2. Q trustee için Round 1 (partial decryption)
    for (let i = 0; i < q; i++) {
        const guardianId = `trustee${i + 1}`;
        const partialsJson = await guardianCrypto.computePartialDecryption(
            electionId,
            i,
            encryptedTallyJson,
            electionGuardContext,
            electionManifest,
        );
        await api.post(`/guardians/${electionId}/partial-decryption`, {
            partialsJson,
            guardianId,
        });
        submittedTrustees.push(guardianId);
    }

    // 3. Q trustee için Round 2+3 (challenges + response)
    for (let i = 0; i < q; i++) {
        const guardianId = `trustee${i + 1}`;
        const challengesResp = await api.get(`/guardians/${electionId}/challenges`, {
            params: { guardianId },
        });
        const challengesJson = challengesResp.data?.challengesJson;
        if (!challengesJson) {
            throw new Error(`Trustee ${guardianId} için challenges null geldi`);
        }
        const responsesJson = await guardianCrypto.computeChallengeResponses(
            electionId,
            i,
            challengesJson,
        );
        await api.post(`/guardians/${electionId}/challenge-response`, {
            responsesJson,
            guardianId,
        });
    }

    // 4. Owner explicit finalize
    const finalizeResp = await api.post(`/guardians/${electionId}/finalize-tally`);
    const tallyResults = finalizeResp.data?.tallyResults;

    return {
        success: true,
        elapsed: Date.now() - t0,
        sessionId,
        trusteesUsed: submittedTrustees,
        tallyResults,
    };
}
