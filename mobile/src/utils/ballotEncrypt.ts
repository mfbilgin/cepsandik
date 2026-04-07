import forge from 'node-forge';

export type BallotOptionRef = { id: number; displayOrder?: number | null };

/**
 * Crypto-engine EncryptBallot'un beklediği düz JSON (RSA/AES açıldıktan sonra).
 * contest_id ve selection_id, seçim başlatılırken backend'deki SetupElection ile aynı olmalı.
 */
export function buildElectionGuardPlaintextJson(
    electionId: number,
    options: BallotOptionRef[],
    selectedCandidateId: number
): string {
    const ordered = [...options].sort(
        (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
    );
    const contestId = `contest_${electionId}`;
    const selections = ordered.map((o) => ({
        selection_id: `candidate_${o.id}`,
        vote: o.id === selectedCandidateId ? 1 : 0,
    }));
    return JSON.stringify({
        contests: [{ contest_id: contestId, selections }],
    });
}

/**
 * Transit: RSA-OAEP(SHA1/MGF1-SHA1) ile 32 bayt AES anahtarı, ardından AES-GCM (12 bayt IV, 128 bit tag).
 * Çıktı: tüm blob'un base64'ü — Spring `byte[]` alanına JSON'da bu string verilir.
 */
export function encryptTransitPayloadBase64(rsaPublicKeyPem: string, plaintextUtf8: string): string {
    const publicKey = forge.pki.publicKeyFromPem(rsaPublicKeyPem);
    const aesKey = forge.random.getBytesSync(32);
    const iv = forge.random.getBytesSync(12);

    const cipher = forge.cipher.createCipher('AES-GCM', aesKey);
    cipher.start({
        iv: forge.util.createBuffer(iv),
        tagLength: 128,
    });
    cipher.update(forge.util.createBuffer(plaintextUtf8, 'utf8'));
    cipher.finish();
    const ciphertext = cipher.output.getBytes();
    const tag = cipher.mode.tag.getBytes();

    const encryptedAesKey = publicKey.encrypt(aesKey, 'RSA-OAEP', {
        md: forge.md.sha1.create(),
        mgf1: forge.mgf.mgf1.create(forge.md.sha1.create()),
    });

    // Crypto-engine varsayılanı 2048 bit RSA → şifre metni 256 bayt
    if (encryptedAesKey.length !== 256) {
        throw new Error(`RSA şifre çıktısı 256 bayt olmalı, gelen: ${encryptedAesKey.length}`);
    }

    const combined = encryptedAesKey + iv + ciphertext + tag;
    return forge.util.encode64(combined);
}
