import { Platform } from 'react-native';
import { api } from './api';
import Integrity, { isIntegrityModuleAvailable } from '../../modules/integrity';

/**
 * Cihaz bütünlüğü kontrolü (Google Play Integrity API).
 *
 * Akış: sunucudan tek kullanımlık challenge al → cihazdan bu challenge'a
 * bağlı şifreli token al → sunucu token'ı Google'da çözüp kararı verir.
 *
 * Karar politikası:
 *  - failed  → Sunucu cihazı açıkça reddetti (root, kilidi açık bootloader,
 *              değiştirilmiş uygulama…) ya da cihazda Play hizmetleri yok.
 *              Uygulama açılmaz.
 *  - skipped → Kontrol yapılamadı ama bu cihazın suçu değil: özellik bu
 *              ortamda kapalı, ağ/sunucu geçici hatası, zaman aşımı.
 *              Altyapı arızası tüm seçmenleri kilitlemesin diye uygulama açılır.
 */
export type IntegrityCheckResult =
    | { status: 'passed' }
    | { status: 'skipped'; reason: string }
    | { status: 'failed'; reasons: string[] };

const PROJECT_NUMBER = process.env.EXPO_PUBLIC_PLAY_INTEGRITY_PROJECT_NUMBER;
const TIMEOUT_MS = 15000;

// StandardIntegrityErrorCode: NETWORK_ERROR(-3), TOO_MANY_REQUESTS(-8),
// GOOGLE_SERVER_UNAVAILABLE(-12), CLIENT_TRANSIENT_ERROR(-18), INTERNAL_ERROR(-100)
const TRANSIENT_NATIVE_CODES = new Set(['-3', '-8', '-12', '-18', '-100']);

const skipped = (reason: string): IntegrityCheckResult => ({ status: 'skipped', reason });
const failed = (reasons: string[]): IntegrityCheckResult => ({ status: 'failed', reasons });

const isTransientHttpError = (error: any): boolean => {
    const status: number | undefined = error?.response?.status;
    return status === undefined || status === 429 || status >= 500;
};

async function runCheck(): Promise<IntegrityCheckResult> {
    if (Platform.OS !== 'android') return skipped('PLATFORM');
    if (!PROJECT_NUMBER) return skipped('NOT_CONFIGURED');
    if (!isIntegrityModuleAvailable()) return skipped('MODULE_MISSING');

    let nonce: string;
    try {
        const res = await api.post('/integrity/challenge');
        const challenge = res.data?.data;
        if (!challenge?.enabled || !challenge?.nonce) return skipped('DISABLED_ON_SERVER');
        nonce = challenge.nonce;
    } catch (error) {
        return skipped('CHALLENGE_UNAVAILABLE');
    }

    let token: string;
    try {
        token = await Integrity.requestToken(PROJECT_NUMBER, nonce);
    } catch (error: any) {
        const code = String(error?.code ?? '').replace('E_INTEGRITY_', '') || 'UNKNOWN';
        if (TRANSIENT_NATIVE_CODES.has(code)) return skipped(`TRANSIENT_${code}`);
        return failed([`TOKEN_UNAVAILABLE_${code}`]);
    }

    try {
        const res = await api.post('/integrity/verify', { nonce, token });
        const verdict = res.data?.data;
        if (verdict?.passed) return { status: 'passed' };
        return failed(verdict?.reasons?.length ? verdict.reasons : ['UNKNOWN']);
    } catch (error: any) {
        if (isTransientHttpError(error)) return skipped('VERIFY_UNAVAILABLE');
        return failed(['VERIFY_REJECTED']);
    }
}

export async function checkDeviceIntegrity(): Promise<IntegrityCheckResult> {
    const timeout = new Promise<IntegrityCheckResult>((resolve) =>
        setTimeout(() => resolve(skipped('TIMEOUT')), TIMEOUT_MS),
    );
    const result = await Promise.race([runCheck(), timeout]);
    if (__DEV__) {
        console.log('[Integrity]', JSON.stringify(result));
    }
    return result;
}
