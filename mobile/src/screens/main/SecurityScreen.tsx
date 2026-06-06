import React, { useState } from 'react';
import { View, Text, TouchableOpacity, Keyboard } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import { api } from '../../services/api';
import Toast from 'react-native-toast-message';
import * as SecureStore from 'expo-secure-store';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card, Input, Button, Badge } from '../../components/ui';
import { validatePasswordStrengthSync } from '../../utils/passwordStrength';

export const SecurityScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [verificationPassword, setVerificationPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [is2FAEnabled, setIs2FAEnabled] = useState(false);
    const [is2FALoading, setIs2FALoading] = useState(true);

    useFocusEffect(
        React.useCallback(() => {
            const check2FAStatus = async () => {
                setIs2FALoading(true);
                try {
                    const response = await api.get('/users/me/2fa/status');
                    setIs2FAEnabled(response.data?.data?.is2faEnabled || response.data?.data?.enabled || false);
                } catch (error) {
                    console.error('Failed to get 2FA status', error);
                } finally {
                    setIs2FALoading(false);
                }
            };
            check2FAStatus();
        }, [])
    );

    const strengthResult = newPassword.length > 0 ? validatePasswordStrengthSync(newPassword) : null;
    const strength = strengthResult?.score ?? 0;
    const strengthLevels = [
        t('auth.reset.passwordStrength.0'),
        t('auth.reset.passwordStrength.1'),
        t('auth.reset.passwordStrength.2'),
        t('auth.reset.passwordStrength.3'),
        t('auth.reset.passwordStrength.4'),
    ];
    const strengthColors = [c.danger, c.warning, '#CA8A04', '#65A30D', c.success];

    const handleChangePassword = async () => {
        if (!currentPassword || !newPassword || !confirmPassword) {
            Toast.show({ type: 'error', text1: t('security.missingTitle'), text2: t('security.missingBody') });
            return;
        }

        if (newPassword !== confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: t('security.passwordMismatch') });
            return;
        }
        const policy = validatePasswordStrengthSync(newPassword);
        if (policy.score < 3) {
            Toast.show({
                type: 'error',
                text1: t('auth.twoFactor.errorTitle'),
                text2: policy.feedback[0] || (t('password.tooWeak') || 'Parola çok zayıf'),
            });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();

        try {
            await api.put('/users/me/password', {
                oldPassword: currentPassword,
                newPassword,
                confirmPassword
            });
            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('security.successBody') });
            setTimeout(() => {
                navigation.goBack();
            }, 1000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: error.response?.data?.message || t('security.updateError') });
        } finally {
            setIsLoading(false);
        }
    };

    const handleDisable2FA = async () => {
        if (!verificationPassword) {
            Toast.show({ type: 'error', text1: t('security.passwordRequiredTitle'), text2: t('security.passwordRequiredBody') });
            return;
        }

        setIsLoading(true);
        try {
            await api.post('/users/me/2fa/disable', { password: verificationPassword });
            setIs2FAEnabled(false);
            setVerificationPassword('');
            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('security.disableSuccess') });
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: error.response?.data?.message || t('security.disableError') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Screen keyboardAvoiding scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('security.title')} onBack={() => navigation.goBack()} />
            </View>

            <View style={{ paddingHorizontal: theme.spacing.lg, gap: theme.spacing.md }}>
                <Card padding={theme.spacing.lg}>
                    <Text style={{ fontSize: 17, fontWeight: '700', color: c.text, marginBottom: theme.spacing.md }}>
                        {t('security.changePassword')}
                    </Text>

                    <Input
                        label={t('security.currentPassword')}
                        placeholder="••••••••"
                        password
                        value={currentPassword}
                        onChangeText={setCurrentPassword}
                    />
                    <Input
                        label={t('security.newPassword')}
                        placeholder="••••••••"
                        password
                        value={newPassword}
                        onChangeText={setNewPassword}
                    />
                    {newPassword.length > 0 && (
                        <View style={{ gap: 6, marginTop: -8, marginBottom: theme.spacing.md }}>
                            <View style={{ flexDirection: 'row', gap: 4, height: 4 }}>
                                {[1, 2, 3, 4].map(idx => (
                                    <View
                                        key={idx}
                                        style={{ flex: 1, borderRadius: 2, backgroundColor: strength >= idx ? strengthColors[strength] : c.border }}
                                    />
                                ))}
                            </View>
                            <Text style={{ fontSize: 12, textAlign: 'right', color: strengthColors[strength], fontWeight: '500' }}>
                                {strengthLevels[strength]}
                            </Text>
                            {strengthResult && strengthResult.feedback.length > 0 && strength < 3 && (
                                <View style={{ gap: 4, marginTop: 4 }}>
                                    {strengthResult.feedback.slice(0, 3).map((msg, idx) => (
                                        <Text key={idx} style={{ fontSize: 12, color: c.danger, lineHeight: 17 }}>
                                            • {msg}
                                        </Text>
                                    ))}
                                </View>
                            )}
                        </View>
                    )}

                    <Input
                        label={t('security.newPasswordAgain')}
                        placeholder="••••••••"
                        password
                        value={confirmPassword}
                        onChangeText={setConfirmPassword}
                    />

                    <Button
                        title={isLoading ? t('security.updating') : t('security.updatePassword')}
                        loading={isLoading}
                        onPress={handleChangePassword}
                        icon={!isLoading ? 'lock-closed' : undefined}
                        style={{ marginTop: 8 }}
                    />
                </Card>

                {/* 2FA Block */}
                <Card padding={theme.spacing.lg}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                            <Ionicons name="shield-checkmark" size={22} color={is2FAEnabled ? c.success : c.textSecondary} />
                            <Text style={{ fontSize: 17, fontWeight: '700', color: c.text }}>{t('security.2faTitle')}</Text>
                        </View>
                        {is2FAEnabled && <Badge label={t('security.active')} tone="success" dot />}
                    </View>
                    <Text style={{ fontSize: 14, color: c.textSecondary, marginTop: 8, lineHeight: 20 }}>
                        {is2FAEnabled ? t('security.2faEnabledDesc') : t('security.2faDisabledDesc')}
                    </Text>

                    {is2FALoading ? (
                        <View style={{ marginTop: theme.spacing.md, paddingVertical: 12 }}>
                            <Text style={{ color: c.textTertiary, textAlign: 'center' }}>{t('security.statusChecking')}</Text>
                        </View>
                    ) : is2FAEnabled ? (
                        <View style={{ gap: 12, marginTop: theme.spacing.md }}>
                            <Input
                                placeholder={t('security.disablePlaceholder')}
                                password
                                value={verificationPassword}
                                onChangeText={setVerificationPassword}
                                containerStyle={{ marginBottom: 0 }}
                            />
                            <Button
                                title={t('security.disable2fa')}
                                variant="danger"
                                loading={isLoading}
                                onPress={handleDisable2FA}
                                icon="trash-outline"
                                iconPosition="right"
                            />
                        </View>
                    ) : (
                        <TouchableOpacity
                            style={{
                                marginTop: theme.spacing.md, width: '100%', backgroundColor: c.surfaceAlt,
                                flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                                paddingVertical: 14, paddingHorizontal: 16, borderRadius: theme.borderRadius.lg,
                                borderWidth: 1, borderColor: c.border,
                            }}
                            onPress={() => navigation.navigate('TwoFactorSetupSelection')}
                        >
                            <Text style={{ color: c.text, fontWeight: '700', fontSize: 15 }}>{t('security.setup2fa')}</Text>
                            <Ionicons name="chevron-forward" size={20} color={c.textSecondary} />
                        </TouchableOpacity>
                    )}
                </Card>

                <View
                    style={{
                        flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14,
                        backgroundColor: c.warningTint, borderRadius: theme.borderRadius.lg,
                        borderWidth: 1, borderColor: c.warningTint,
                    }}
                >
                    <Ionicons name="warning" size={22} color={c.warning} />
                    <Text style={{ flex: 1, fontSize: 13, color: c.warning, lineHeight: 18 }}>
                        {t('security.warning')}
                    </Text>
                </View>
            </View>
        </Screen>
    );
};
