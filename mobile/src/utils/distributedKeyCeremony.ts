/**
 * Sprint 5.A.distributed — gerçek dağıtık key ceremony orkestrasyonu.
 *
 * Her guardian KENDİ cihazında 1 trustee taşır. Sunucu opak relay yapar
 * (encrypted key share içeriğini göremez). 3 round + decline.
 *
 * Akış (GuardianScreen "Anahtar Yükle" → tek tıkla 3 round):
 *   info  : GET  /guardians/{id}/ceremony-info  → guardianId, xCoordinate, n, q
 *   R1    : native generateSingleGuardianKey → POST /guardians/{id}/public-key
 *   R2    : GET /peer-public-keys → native computeEncryptedKeyShares
 *           → POST /encrypted-key-shares
 *   R3    : GET /peer-public-keys + GET /my-encrypted-key-shares
 *           → native finalizeGuardianKey → POST /ceremony-complete
 *
 * Round'lar arası peer'lar hazır olmayabilir; her round çağrıldığında
 * sunucu eldeki public state'i döner. Eksikse guardian sonra tekrar dener
 * (mobile UI "Daha Sonra Hatırlat" / tekrar "Anahtar Yükle").
 */

import { api } from '../services/api';
import { guardianCrypto } from './guardianCrypto';

export type CeremonyInfo = {
    guardianId: string;
    xCoordinate: number;
    n: number;
    q: number;
    status: string;
    electionStatus: string;
    setupDeadline: string | null;
    substitutionCount: number;
};

export async function getCeremonyInfo(electionId: number): Promise<CeremonyInfo> {
    const r = await api.get(`/guardians/${electionId}/ceremony-info`);
    return r.data;
}

async function fetchPeerPublicKeys(electionId: number): Promise<string[]> {
    const r = await api.get(`/guardians/${electionId}/peer-public-keys`);
    return (r.data || []).map((p: { publicKeysJson: string }) => p.publicKeysJson);
}

/**
 * Tek tıkla tüm ceremony'i ilerletir. Guardian'ın mevcut status'üne göre
 * eksik round'ları tamamlar (idempotent — tekrar çağrılabilir).
 */
export async function runKeyCeremony(electionId: number): Promise<{
    finalStatus: string;
    completed: boolean;
    jointKeyGenerated: boolean;
}> {
    const info = await getCeremonyInfo(electionId);
    const { guardianId, xCoordinate, n, q } = info;

    // ---- Round 1: public key ----
    if (info.status === 'PENDING') {
        const publicKeysJson = await guardianCrypto.generateSingleGuardianKey(
            electionId, guardianId, xCoordinate, n, q,
        );
        await api.post(`/guardians/${electionId}/public-key`, { publicKeysJson });
    }

    // ---- Round 2: encrypted key shares ----
    let status = (await getCeremonyInfo(electionId)).status;
    if (status === 'KEY_UPLOADED') {
        const peers = await fetchPeerPublicKeys(electionId);
        if (peers.length < n - 1) {
            return { finalStatus: 'KEY_UPLOADED', completed: false, jointKeyGenerated: false };
        }
        const shares = await guardianCrypto.computeEncryptedKeyShares(
            electionId, guardianId, xCoordinate, n, q, peers,
        );
        await api.post(`/guardians/${electionId}/encrypted-key-shares`, { shares });
    }

    // ---- Round 3: finalize ----
    status = (await getCeremonyInfo(electionId)).status;
    if (status === 'KEYS_EXCHANGED') {
        const peers = await fetchPeerPublicKeys(electionId);
        const mineResp = await api.get(`/guardians/${electionId}/my-encrypted-key-shares`);
        const encryptedSharesForMe: string[] = (mineResp.data || []).map(
            (s: { encryptedKeyShareJson: string }) => s.encryptedKeyShareJson,
        );
        if (peers.length < n - 1 || encryptedSharesForMe.length < n - 1) {
            return { finalStatus: 'KEYS_EXCHANGED', completed: false, jointKeyGenerated: false };
        }
        await guardianCrypto.finalizeGuardianKey(
            electionId, guardianId, xCoordinate, n, q, peers, encryptedSharesForMe,
        );
        const ack = await api.post(`/guardians/${electionId}/ceremony-complete`);
        return {
            finalStatus: 'READY',
            completed: true,
            jointKeyGenerated: !!ack.data?.jointKeyGenerated,
        };
    }

    return {
        finalStatus: status,
        completed: status === 'READY',
        jointKeyGenerated: false,
    };
}

/** Guardian "bu seçime katılmıyorum" — cezasız, STATE_RESET tetikler. */
export async function declineCeremony(electionId: number): Promise<{
    cancelled: boolean;
    backupAvailable?: boolean;
    message?: string;
}> {
    await guardianCrypto.deleteDistributedState(electionId);
    const r = await api.post(`/guardians/${electionId}/decline`);
    return r.data;
}

/**
 * Distributed tally — guardian kendi cihazından partial decryption + challenge
 * response üretir. Gizli key share cihazdan ÇIKMAZ; sunucu sadece partial'ları
 * birleştirir (Q-of-N threshold). guardianId = "trustee{xCoordinate}" (ceremony
 * ile aynı; tally-task userId döner ama submit KMP guardianId ister).
 *
 * Q guardian partial+response gönderince sunucu otomatik finalize eder.
 */
export async function runDistributedTally(electionId: number): Promise<{
    submitted: boolean;
    finalized: boolean;
    tallyResults?: unknown;
}> {
    const info = await getCeremonyInfo(electionId);
    const guardianId = info.guardianId; // "trustee{x}"

    const task = await api.get(`/guardians/${electionId}/tally-task`);
    const { encryptedTallyJson, electionGuardContext, electionManifest } = task.data || {};
    if (!encryptedTallyJson || !electionGuardContext || !electionManifest) {
        throw new Error('Tally task eksik (session başlamamış olabilir)');
    }

    // Round 1: partial decryption (cihaz native, gizli key share lokal)
    const partialsJson = await guardianCrypto.computeDistributedPartialDecryption(
        electionId, encryptedTallyJson, electionGuardContext, electionManifest,
    );
    await api.post(`/guardians/${electionId}/partial-decryption`, { partialsJson, guardianId });

    // Round 2/3: challenges ancak TÜM katılan guardian'lar partial gönderince
    // üretilir. Sunucu getChallenges'ı ~30sn long-poll yapar; çok-cihaz manuel
    // tempoda erken cihaz beklemeli → retry-loop (her çağrı 30sn, ~10 tur).
    let challengesJson: string | undefined;
    for (let attempt = 0; attempt < 10 && !challengesJson; attempt++) {
        try {
            const ch = await api.get(`/guardians/${electionId}/challenges`, {
                params: { guardianId },
            });
            challengesJson = ch.data?.challengesJson;
        } catch {
            // 30sn long-poll timeout (diğer guardian'lar henüz partial vermedi)
            // — bir sonraki turda tekrar dene.
        }
    }
    if (!challengesJson) {
        return { submitted: true, finalized: false };
    }
    const responsesJson = await guardianCrypto.computeDistributedChallengeResponses(
        electionId, challengesJson,
    );
    const resp = await api.post(`/guardians/${electionId}/challenge-response`, {
        responsesJson, guardianId,
    });

    return {
        submitted: true,
        finalized: !!resp.data?.finalized,
        tallyResults: resp.data?.tallyResults,
    };
}
