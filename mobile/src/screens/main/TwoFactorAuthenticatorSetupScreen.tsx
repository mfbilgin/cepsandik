import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, Image, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import * as Linking from 'expo-linking';
import Toast from 'react-native-toast-message';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { AppHeader, Card, Button } from '../../components/ui';

export const TwoFactorAuthenticatorSetupScreen = () => {
    const { user } = useAuth();
    const navigation = useNavigation<any>();
    const [isLoading, setIsLoading] = useState(true);
    const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
    const [manualKey, setManualKey] = useState<string | null>(null);
    const [backupCodes, setBackupCodes] = useState<string[]>([]);
    const [fetchError, setFetchError] = useState<string | null>(null);
    const { t } = useI18n();
    const c = theme.colors;

    useEffect(() => {
        const generate2FA = async () => {
            try {
                const response = await api.post('/users/me/2fa/setup');
                if (response.data && response.data.data) {
                    setQrCodeUrl(response.data.data.qrCodeUri || response.data.data.qrCodeUrl || response.data.data.qrImage);
                    setManualKey(response.data.data.secretKey || response.data.data.secret || response.data.data.manualKey);
                    setBackupCodes(response.data.data.backupCodes || []);
                } else {
                    setFetchError(t('twoFactor.authSetup.unexpectedData'));
                }
            } catch (error: any) {
                console.error('Failed to generate 2FA', error);
                setFetchError(error.response?.data?.message || t('twoFactor.authSetup.fetchError'));
                Toast.show({ type: 'error', text1: t('twoFactor.authSetup.connectionErrorTitle'), text2: t('twoFactor.authSetup.connectionErrorBody') });
            } finally {
                setIsLoading(false);
            }
        };

        generate2FA();
    }, []);

    const copyToClipboard = async () => {
        if (manualKey) {
            await Clipboard.setStringAsync(manualKey);
            Toast.show({ type: 'success', text1: t('recovery.copySuccessTitle'), text2: t('recovery.copySuccessBody') });
        }
    };

    const StepRow = ({ n, label }: { n: number; label: string }) => (
        <View style={{ flexDirection: 'row', gap: 14, alignItems: 'flex-start' }}>
            <View style={{ width: 24, height: 24, borderRadius: 12, backgroundColor: c.primary, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: c.onPrimary, fontSize: 12, fontWeight: '700' }}>{n}</Text>
            </View>
            <Text style={{ fontSize: 14, lineHeight: 20, color: c.text, flex: 1, marginTop: 2 }}>{label}</Text>
        </View>
    );

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'bottom']}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('twoFactor.setup.title')} onBack={() => navigation.goBack()} />
            </View>

            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 12, paddingVertical: theme.spacing.md }}>
                <View style={{ height: 8, width: 8, borderRadius: 4, backgroundColor: c.primaryTint }} />
                <View style={{ height: 8, width: 32, borderRadius: 4, backgroundColor: c.primary }} />
                <View style={{ height: 8, width: 8, borderRadius: 4, backgroundColor: c.primaryTint }} />
            </View>

            <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingBottom: 180 }} showsVerticalScrollIndicator={false}>
                <View style={{ alignItems: 'center', marginTop: theme.spacing.sm }}>
                    <Text style={{ fontSize: 22, textAlign: 'center', fontWeight: '700', color: c.text, marginBottom: 8 }}>
                        {t('twoFactor.authSetup.title')}
                    </Text>
                    <Text style={{ fontSize: 14, textAlign: 'center', lineHeight: 20, color: c.textSecondary }}>
                        {t('twoFactor.authSetup.desc')}
                    </Text>
                </View>

                {isLoading ? (
                    <View style={{ alignItems: 'center', justifyContent: 'center', paddingVertical: 60 }}>
                        <ActivityIndicator size="large" color={c.primary} />
                        <Text style={{ marginTop: 16, color: c.textSecondary }}>{t('twoFactor.authSetup.loading')}</Text>
                    </View>
                ) : fetchError ? (
                    <View style={{ alignItems: 'center', justifyContent: 'center', padding: 16, paddingTop: 40 }}>
                        <Ionicons name="warning-outline" size={56} color={c.danger} />
                        <Text style={{ marginTop: 16, fontSize: 18, fontWeight: '700', textAlign: 'center', color: c.text }}>{t('twoFactor.authSetup.failedTitle')}</Text>
                        <Text style={{ marginTop: 8, fontSize: 14, textAlign: 'center', color: c.textSecondary }}>{fetchError}</Text>
                        <Text style={{ marginTop: 16, textAlign: 'center', fontSize: 12, color: c.textTertiary, backgroundColor: c.surfaceAlt, padding: 12, borderRadius: theme.borderRadius.md, borderWidth: 1, borderColor: c.border }}>
                            {t('twoFactor.authSetup.backendHint')}
                        </Text>
                        <View style={{ marginTop: 24, width: '100%' }}>
                            <Button title={t('twoFactor.authSetup.back')} variant="secondary" onPress={() => navigation.goBack()} />
                        </View>
                    </View>
                ) : (
                    <>
                        {qrCodeUrl && (
                            <View style={{ marginTop: theme.spacing.xl, alignItems: 'center' }}>
                                <View style={{ padding: 20, backgroundColor: c.surface, borderRadius: theme.borderRadius.xl, borderWidth: 1, borderColor: c.border, ...theme.shadows.card }}>
                                    <Image
                                        source={{ uri: qrCodeUrl.startsWith('data:image') ? qrCodeUrl : `data:image/png;base64,${qrCodeUrl}` }}
                                        style={{ width: 192, height: 192 }}
                                        resizeMode="contain"
                                    />
                                </View>
                            </View>
                        )}

                        <View style={{ marginTop: theme.spacing.xl, gap: 12 }}>
                            <Text style={{ fontSize: 12, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.5, textAlign: 'center', color: c.textSecondary }}>
                                {t('twoFactor.authSetup.cantScan')}
                            </Text>
                            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: 16, backgroundColor: c.primaryTint, borderRadius: theme.borderRadius.lg, borderWidth: 1, borderColor: c.border }}>
                                <View>
                                    <Text style={{ fontSize: 10, color: c.primaryDark, fontWeight: '700', textTransform: 'uppercase' }}>{t('twoFactor.authSetup.manualKey')}</Text>
                                    <Text style={{ fontFamily: 'monospace', fontWeight: '700', letterSpacing: 2, color: c.text, marginTop: 4 }}>
                                        {manualKey || 'XXXX XXXX XXXX XXXX'}
                                    </Text>
                                </View>
                                <TouchableOpacity onPress={copyToClipboard} style={{ alignItems: 'center', justifyContent: 'center', height: 40, width: 40 }}>
                                    <Ionicons name="copy-outline" size={22} color={c.primary} />
                                </TouchableOpacity>
                            </View>
                            <TouchableOpacity
                                style={{ marginTop: 4, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingVertical: 14, backgroundColor: c.surfaceAlt, borderRadius: theme.borderRadius.lg, borderWidth: 1, borderColor: c.border }}
                                onPress={() => {
                                    if (manualKey) {
                                        const emailParam = user?.email ? `:${user.email}` : '';
                                        const otpAuthUrl = `otpauth://totp/CepSandik${emailParam}?secret=${manualKey}&issuer=CepSandik`;
                                        Linking.openURL(otpAuthUrl).catch(() => {
                                            Toast.show({ type: 'error', text1: t('twoFactor.authSetup.noAppTitle'), text2: t('twoFactor.authSetup.noAppBody') });
                                        });
                                    }
                                }}
                            >
                                <Ionicons name="open-outline" size={20} color={c.textSecondary} />
                                <Text style={{ color: c.text, fontWeight: '700' }}>{t('twoFactor.authSetup.openApp')}</Text>
                            </TouchableOpacity>
                        </View>

                        <View style={{ marginTop: theme.spacing.xl, gap: 16 }}>
                            <StepRow n={1} label={t('twoFactor.authSetup.step1')} />
                            <StepRow n={2} label={t('twoFactor.authSetup.step2')} />
                            <StepRow n={3} label={t('twoFactor.authSetup.step3')} />
                        </View>
                    </>
                )}
            </ScrollView>

            {!fetchError && (
                <View style={{ position: 'absolute', bottom: 0, width: '100%', padding: theme.spacing.lg, backgroundColor: c.background, borderTopWidth: 1, borderTopColor: c.border }}>
                    <Button
                        title={t('twoFactor.setup.continue')}
                        icon="arrow-forward"
                        iconPosition="right"
                        size="lg"
                        disabled={isLoading}
                        onPress={() => navigation.navigate('TwoFactorVerification', { backupCodes })}
                    />
                    <Text style={{ marginTop: 16, textAlign: 'center', color: c.textTertiary, fontSize: 10, textTransform: 'uppercase', fontWeight: '700', letterSpacing: 2 }}>
                        {t('twoFactor.authSetup.secured')}
                    </Text>
                </View>
            )}
        </SafeAreaView>
    );
};
