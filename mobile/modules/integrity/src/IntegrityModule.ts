import { Platform } from 'react-native';
import { NativeModule, requireOptionalNativeModule } from 'expo';

/**
 * Google Play Integrity API (Standard request) köprüsü.
 *
 * requestToken: cihazdan şifreli bir bütünlük token'ı alır. Token cihazda
 * ÇÖZÜLMEZ; user-service Google'ın decodeIntegrityToken uç noktasıyla çözer
 * ve kararı (verdict) sunucuda verir.
 *
 * @param cloudProjectNumber Play Console'a bağlı Google Cloud proje NUMARASI (string)
 * @param requestHash        Sunucunun verdiği tek kullanımlık challenge (≤ 500 karakter)
 */
declare class IntegrityNativeModule extends NativeModule {
    requestToken(cloudProjectNumber: string, requestHash: string): Promise<string>;
}

const nativeModule =
    Platform.OS === 'android'
        ? requireOptionalNativeModule<IntegrityNativeModule>('Integrity')
        : null;

export const isIntegrityModuleAvailable = (): boolean => nativeModule != null;

export default {
    requestToken(cloudProjectNumber: string, requestHash: string): Promise<string> {
        if (!nativeModule) {
            return Promise.reject(new Error('Integrity native module is not available'));
        }
        return nativeModule.requestToken(cloudProjectNumber, requestHash);
    },
};
