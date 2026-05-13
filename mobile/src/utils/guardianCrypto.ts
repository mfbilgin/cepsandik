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
} from '../../modules/electionguard/src/ElectionguardModule';

const trusteeStateStorageKey = (electionId: number | string) => `guardian_trustee_states_${electionId}`;

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
