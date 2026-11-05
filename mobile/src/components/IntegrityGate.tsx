import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../utils/theme';
import { useI18n } from '../i18n/LanguageContext';
import { Button } from './ui';
import { checkDeviceIntegrity } from '../services/integrity.service';

type GateState = 'checking' | 'allowed' | 'blocked';

/**
 * Uygulama açılışında cihaz bütünlüğünü doğrular (Google Play Integrity).
 * Doğrulama açıkça başarısız olursa uygulama içeriği hiç gösterilmez;
 * kullanıcı, değiştirilmemiş bir cihaz kullanması gerektiği konusunda
 * bilgilendirilir. Tehdit modeli: makale/tez Bölüm 3.4.
 */
export const IntegrityGate: React.FC<React.PropsWithChildren> = ({ children }) => {
    const { t } = useI18n();
    const [state, setState] = useState<GateState>('checking');
    const [codes, setCodes] = useState<string[]>([]);

    const run = useCallback(async () => {
        setState('checking');
        const result = await checkDeviceIntegrity();
        if (result.status === 'failed') {
            setCodes(result.reasons);
            setState('blocked');
        } else {
            setState('allowed');
        }
    }, []);

    useEffect(() => {
        run();
    }, [run]);

    if (state === 'allowed') {
        return <>{children}</>;
    }

    if (state === 'checking') {
        return (
            <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: theme.colors.background }}>
                <ActivityIndicator size="large" color={theme.colors.primary} />
                <Text style={{ marginTop: 16, color: theme.colors.textSecondary }}>{t('integrity.checking')}</Text>
            </View>
        );
    }

    return (
        <View
            style={{
                flex: 1, alignItems: 'center', justifyContent: 'center',
                paddingHorizontal: 32, backgroundColor: theme.colors.background,
            }}
        >
            <View
                style={{
                    width: 80, height: 80, borderRadius: 40, marginBottom: 24,
                    backgroundColor: theme.colors.dangerTint, alignItems: 'center', justifyContent: 'center',
                }}
            >
                <Ionicons name="shield-outline" size={40} color={theme.colors.danger} />
            </View>
            <Text style={{ fontSize: 20, fontWeight: '700', color: theme.colors.text, textAlign: 'center', marginBottom: 12 }}>
                {t('integrity.blocked.title')}
            </Text>
            <Text style={{ fontSize: 15, lineHeight: 22, color: theme.colors.textSecondary, textAlign: 'center', marginBottom: 24 }}>
                {t('integrity.blocked.message')}
            </Text>
            <Button title={t('integrity.blocked.retry')} onPress={run} />
            <Text style={{ marginTop: 24, fontSize: 12, color: theme.colors.textSecondary, textAlign: 'center' }}>
                {t('integrity.blocked.code', { codes: codes.join(', ') })}
            </Text>
        </View>
    );
};
