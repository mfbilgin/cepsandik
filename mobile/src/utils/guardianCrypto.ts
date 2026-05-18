/**
 * Sprint 5.B — KMP native modülü ile guardian crypto.
 *
 * Önceki node-forge JS implementasyonu KMP 2.0 spec ile uyumsuzdu
 * (commitment formatı + decryption share formatı yanlış). Bu yeniden
 * yazım native modülü çağırır; tüm crypto Android tarafında gerçek KMP
 * `KeyCeremonyTrustee` + `DecryptingTrusteeDoerre` ile yapılır.
 *
 * SecureStore'da:
 *   - guardian_trustee_states_{electionId} → JSON.stringify(string[])
 *     N adet TrusteeJson (private state — polynomial katsayıları + key share).
 *     SUNUCUYA ASLA GÖNDERİLMEZ.
 *
 * Sunucuya:
 *   - publicKeysJsons[]: N adet KMP PublicKeysJson (sadece Schnorr proofs).
 *   - partialDecryptionsJson: DecryptResponseJson (List<PartialDecryptionJson>).
 *   - challengeResponsesJson: ChallengeResponsesJson.
 */

import * as SecureStore from 'expo-secure-store';
import Electionguard, {
    GenerateGuardianKeysResult,
    EncryptedKeyShareOut,
} from '../../modules/electionguard/src/ElectionguardModule';

const trusteeStateStorageKey = (electionId: number | string) => `guardian_trustee_states_${electionId}`;

// Sprint 5.A.distributed — her cihaz 1 trustee. Tek kalıcı secret polinom;
// final TrusteeJson tally için. SecureStore Android 2KB/item limiti — polinom
// ve trustee state büyük olabilir, gerekirse parçalama eklenebilir (şimdilik
// N=1 trustee/cihaz olduğu için tek item yeterli, ~2-3KB).
const polyKey = (electionId: number | string) => `guardian_poly_${electionId}`;
const trusteeKey = (electionId: number | string) => `guardian_trustee_${electionId}`;
// Faz 2.7 — tally app-ölümü dayanıklılığı: hangi fazda kaldık (resume/idempotent).
const tallyCpKey = (electionId: number | string) => `guardian_tally_cp_${electionId}`;

export type TallyCheckpoint = {
    phase: 'PARTIAL_SUBMITTED' | 'CHALLENGE_SUBMITTED' | 'DONE';
};

export const guardianCrypto = {
    /**
     * Leader-mode setup: tek cihaz N trustee'in tümünü yaratır, cross-trustee
     * exchange yapar. Public part'ları döner (sunucuya yollanacak), private
     * part'ları SecureStore'da saklar.
     *
     * Sprint 5.A'da bu N=1 yapılıp gerçek distributed setup'a geçilecek.
     */
    async setupAllGuardians(
        electionId: number | string,
        n: number,
        q: number,
    ): Promise<{ publicKeysJsons: string[]; jointPublicKeyHex: string; exchangeMs: number }> {
        const result: GenerateGuardianKeysResult = await Electionguard.generateAllGuardianKeys({
            electionId: String(electionId),
            n,
            q,
        });
        // SecureStore expo-secure-store: 2048 byte limit on Android per item.
        // TrusteeJson büyük olabilir (~2-5KB N=3 için). Tek bir array string'ini
        // saklarsak limit aşılabilir; her trustee için ayrı entry yazıyoruz.
        for (let i = 0; i < result.trusteeStateJsons.length; i++) {
            await SecureStore.setItemAsync(
                `${trusteeStateStorageKey(electionId)}_${i}`,
                result.trusteeStateJsons[i],
            );
        }
        await SecureStore.setItemAsync(
            `${trusteeStateStorageKey(electionId)}_count`,
            String(result.trusteeStateJsons.length),
        );

        return {
            publicKeysJsons: result.publicKeysJsons,
            jointPublicKeyHex: result.jointPublicKeyHex,
            exchangeMs: result.exchangeMs,
        };
    },

    /**
     * Belirli bir trustee için lokal partial decryption hesaplar.
     *
     * @param trusteeIndex 0-indexed; setupAllGuardians sırasında üretilen
     *                     trusteeStateJsons'taki trustee'nin sırası.
     */
    async computePartialDecryption(
        electionId: number | string,
        trusteeIndex: number,
        encryptedTallyJson: string,
        electionGuardContextJson: string,
        electionManifestJson: string,
    ): Promise<string> {
        const trusteeStateJson = await SecureStore.getItemAsync(
            `${trusteeStateStorageKey(electionId)}_${trusteeIndex}`,
        );
        if (!trusteeStateJson) {
            throw new Error(`Trustee #${trusteeIndex} state bulunamadı (electionId=${electionId})`);
        }
        return Electionguard.computePartialDecryption({
            trusteeStateJson,
            encryptedTallyJson,
            electionGuardContextJson,
            electionManifestJson,
        });
    },

    /**
     * Belirli bir trustee için lokal challenge response hesaplar.
     */
    async computeChallengeResponses(
        electionId: number | string,
        trusteeIndex: number,
        challengesJson: string,
    ): Promise<string> {
        const trusteeStateJson = await SecureStore.getItemAsync(
            `${trusteeStateStorageKey(electionId)}_${trusteeIndex}`,
        );
        if (!trusteeStateJson) {
            throw new Error(`Trustee #${trusteeIndex} state bulunamadı (electionId=${electionId})`);
        }
        return Electionguard.computeChallengeResponses({
            trusteeStateJson,
            challengesJson,
        });
    },

    /** SecureStore'da kaç trustee var? */
    async getTrusteeCount(electionId: number | string): Promise<number> {
        const countStr = await SecureStore.getItemAsync(`${trusteeStateStorageKey(electionId)}_count`);
        return countStr ? parseInt(countStr, 10) : 0;
    },

    /** Tally tamamlandıktan sonra private state'i sil. */
    async deleteAll(electionId: number | string) {
        const count = await this.getTrusteeCount(electionId);
        for (let i = 0; i < count; i++) {
            await SecureStore.deleteItemAsync(`${trusteeStateStorageKey(electionId)}_${i}`);
        }
        await SecureStore.deleteItemAsync(`${trusteeStateStorageKey(electionId)}_count`);
    },

    // ============ Sprint 5.A.distributed — her cihaz 1 trustee ============

    /**
     * Round 1: Tek guardian key üret. Gizli polinomu SecureStore'a yazar,
     * sunucuya gidecek PublicKeysJson'u döner.
     */
    async generateSingleGuardianKey(
        electionId: number | string,
        guardianId: string,
        xCoordinate: number,
        n: number,
        q: number,
    ): Promise<string> {
        const res = await Electionguard.generateSingleGuardianKey({
            electionId: String(electionId),
            guardianId,
            xCoordinate,
            n,
            q,
        });
        await SecureStore.setItemAsync(polyKey(electionId), res.secretPolynomialJson);
        return res.publicKeysJson;
    },

    /**
     * Round 2: Peer public key'lerden her peer için şifreli key share üret.
     * Polinom SecureStore'dan okunur (trustee reconstruct edilir).
     */
    async computeEncryptedKeyShares(
        electionId: number | string,
        guardianId: string,
        xCoordinate: number,
        n: number,
        q: number,
        peerPublicKeysJsons: string[],
    ): Promise<EncryptedKeyShareOut[]> {
        const secretPolynomialJson = await SecureStore.getItemAsync(polyKey(electionId));
        if (!secretPolynomialJson) {
            throw new Error(`Polinom bulunamadı (electionId=${electionId}) — önce Round 1`);
        }
        return Electionguard.computeEncryptedKeyShares({
            guardianId,
            xCoordinate,
            n,
            q,
            secretPolynomialJson,
            peerPublicKeysJsons,
        });
    },

    /**
     * Round 3: Bana gelen şifreli share'lerle ceremony'i finalize et.
     * Final TrusteeJson (tally secret'ı) SecureStore'a yazılır.
     */
    async finalizeGuardianKey(
        electionId: number | string,
        guardianId: string,
        xCoordinate: number,
        n: number,
        q: number,
        peerPublicKeysJsons: string[],
        encryptedSharesForMeJsons: string[],
    ): Promise<boolean> {
        const secretPolynomialJson = await SecureStore.getItemAsync(polyKey(electionId));
        if (!secretPolynomialJson) {
            throw new Error(`Polinom bulunamadı (electionId=${electionId}) — önce Round 1`);
        }
        const res = await Electionguard.finalizeGuardianKey({
            guardianId,
            xCoordinate,
            n,
            q,
            secretPolynomialJson,
            peerPublicKeysJsons,
            encryptedSharesForMeJsons,
        });
        await SecureStore.setItemAsync(trusteeKey(electionId), res.trusteeStateJson);
        return res.isComplete;
    },

    /** Tally için tek-trustee state'i (Round 3'te yazılan). */
    async getDistributedTrusteeState(electionId: number | string): Promise<string | null> {
        return SecureStore.getItemAsync(trusteeKey(electionId));
    },

    /**
     * Distributed tally Round 1 — cihazın kendi TrusteeJson'u ile lokal partial
     * decryption. (Leader-mode computePartialDecryption index-bazlı; bu tek-trustee
     * distributed key — guardian_trustee_{electionId}.)
     */
    async computeDistributedPartialDecryption(
        electionId: number | string,
        encryptedTallyJson: string,
        electionGuardContextJson: string,
        electionManifestJson: string,
    ): Promise<string> {
        const trusteeStateJson = await SecureStore.getItemAsync(trusteeKey(electionId));
        if (!trusteeStateJson) {
            throw new Error(`Trustee state yok (electionId=${electionId}) — ceremony tamamlanmadı`);
        }
        return Electionguard.computePartialDecryption({
            trusteeStateJson,
            encryptedTallyJson,
            electionGuardContextJson,
            electionManifestJson,
        });
    },

    /** Distributed tally Round 2/3 — cihazın TrusteeJson'u ile challenge response. */
    async computeDistributedChallengeResponses(
        electionId: number | string,
        challengesJson: string,
    ): Promise<string> {
        const trusteeStateJson = await SecureStore.getItemAsync(trusteeKey(electionId));
        if (!trusteeStateJson) {
            throw new Error(`Trustee state yok (electionId=${electionId}) — ceremony tamamlanmadı`);
        }
        return Electionguard.computeChallengeResponses({ trusteeStateJson, challengesJson });
    },

    /** STATE_RESET: ceremony sıfırlandı — lokal polinom + trustee state sil. */
    async deleteDistributedState(electionId: number | string) {
        await SecureStore.deleteItemAsync(polyKey(electionId));
        await SecureStore.deleteItemAsync(trusteeKey(electionId));
        await SecureStore.deleteItemAsync(tallyCpKey(electionId));
    },

    // ============ Faz 2.7 — tally app-ölümü checkpoint ============

    /** Tally fazı checkpoint'i oku (resume/idempotent). null = hiç başlanmadı. */
    async getTallyCheckpoint(electionId: number | string): Promise<TallyCheckpoint | null> {
        const raw = await SecureStore.getItemAsync(tallyCpKey(electionId));
        if (!raw) return null;
        try {
            return JSON.parse(raw) as TallyCheckpoint;
        } catch {
            return null;
        }
    },

    /** Tally fazını kalıcılaştır (app ölse de resume bilinir). */
    async setTallyCheckpoint(electionId: number | string, cp: TallyCheckpoint) {
        await SecureStore.setItemAsync(tallyCpKey(electionId), JSON.stringify(cp));
    },

    // ============ Sprint 5.A öncesi backward-compat stub'lar ============
    // GuardianScreen.tsx eski API'yi çağırıyor; Sprint 5.A'da yeni native
    // akışa rewrite edilecek. Bu stub'lar build'in kırılmaması için.

    /** @deprecated Sprint 5.B'de kaldırıldı; setupAllGuardians kullan */
    async generateAndSaveKeyPair(_electionId: number) {
        throw new Error('Bu akış Sprint 5.B ile değişti — setupAllGuardians kullan');
    },

    /** @deprecated Sprint 5.B'de kaldırıldı; computePartialDecryption + computeChallengeResponses kullan */
    async generateDecryptionShare(_electionId: number, _encryptedTallyA: string): Promise<string> {
        throw new Error('Bu akış Sprint 5.B ile değişti — yeni 3-round protokolü kullan');
    },

    /** @deprecated Sprint 5.B'de kaldırıldı; deleteAll kullan */
    async deleteKey(electionId: number) {
        await this.deleteAll(electionId);
    },
};
