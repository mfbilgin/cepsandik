import React, { useState, useRef } from 'react';
import { View, Text, TextInput, Keyboard } from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useNavigation, useRoute } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Input, Button } from '../../components/ui';

export const ResetPasswordScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { token } = route.params || {};
    const { t } = useI18n();
    const c = theme.colors;

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);
    const confirmPasswordRef = useRef<TextInput>(null);

    const getPasswordStrength = () => {
        let score = 0;
        if (password.length > 5) score += 1;
        if (password.length > 8) score += 1;
        if (/[A-Z]/.test(password)) score += 1;
        if (/[0-9]/.test(password)) score += 1;
        return score;
    };
    const strengthScore = getPasswordStrength();
    const strengthColors = ['transparent', c.danger, c.warning, '#65A30D', c.success];
    const strengthLabels = [
        t('auth.reset.passwordStrength.0'),
        t('auth.reset.passwordStrength.1'),
        t('auth.reset.passwordStrength.2'),
        t('auth.reset.passwordStrength.3'),
        t('auth.reset.passwordStrength.4'),
    ];

    const handleReset = async () => {
        if (!password || !confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.reset.missingTitle'), text2: t('auth.reset.missingBody') });
            return;
        }
        if (password !== confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.reset.errorTitle'), text2: t('auth.reset.passwordMismatch') });
            return;
        }
        if (!token) {
            Toast.show({ type: 'error', text1: t('auth.reset.errorTitle'), text2: t('auth.reset.invalidToken') });
            return;
        }
        setIsLoading(true);
        Keyboard.dismiss();
        try {
            await AuthService.resetPassword(token, password);
            setIsSuccess(true);
            setTimeout(() => navigation.navigate('Login'), 3000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.reset.failedTitle'), text2: error.response?.data?.message || t('auth.reset.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Screen keyboardAvoiding scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('auth.reset.title')} onBack={() => navigation.goBack()} />
            </View>

            {!isSuccess ? (
                <View style={{ paddingHorizontal: theme.spacing.lg }}>
                    <View style={{ alignItems: 'center', marginTop: 12, marginBottom: theme.spacing.xl }}>
                        <View
                            style={{
                                width: 80, height: 80, borderRadius: 40, backgroundColor: c.primaryTint,
                                alignItems: 'center', justifyContent: 'center', marginBottom: 16,
                            }}
                        >
                            <MaterialIcons name="password" size={38} color={c.primary} />
                        </View>
                        <Text style={{ fontSize: 24, fontWeight: '700', color: c.text, marginBottom: 8 }}>
                            {t('auth.reset.title')}
                        </Text>
                        <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', lineHeight: 21, maxWidth: 280 }}>
                            {t('auth.reset.description')}
                        </Text>
                    </View>

                    <Input
                        label={t('auth.reset.newPassword')}
                        icon="lock-closed-outline"
                        placeholder="••••••••"
                        password
                        value={password}
                        onChangeText={setPassword}
                        returnKeyType="next"
                        onSubmitEditing={() => confirmPasswordRef.current?.focus()}
                        blurOnSubmit={false}
                    />
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: -8, marginBottom: 12 }}>
                        <View style={{ flexDirection: 'row', flex: 1, height: 6, gap: 4, borderRadius: 3, overflow: 'hidden', backgroundColor: c.surfaceAlt }}>
                            {[1, 2, 3, 4].map((i) => (
                                <View
                                    key={i}
                                    style={{ flex: 1, borderRadius: 3, backgroundColor: strengthScore >= i ? strengthColors[strengthScore] : 'transparent' }}
                                />
                            ))}
                        </View>
                        <Text style={{ fontSize: 12, color: c.textSecondary, width: 64, textAlign: 'right' }}>
                            {password.length > 0 ? strengthLabels[strengthScore] : ''}
                        </Text>
                    </View>

                    <Input
                        ref={confirmPasswordRef}
                        label={t('auth.reset.newPasswordAgain')}
                        icon="lock-closed-outline"
                        placeholder="••••••••"
                        password
                        value={confirmPassword}
                        onChangeText={setConfirmPassword}
                        returnKeyType="done"
                        onSubmitEditing={handleReset}
                    />

                    <Button
                        title={isLoading ? t('auth.reset.saving') : t('auth.reset.updatePassword')}
                        loading={isLoading}
                        onPress={handleReset}
                        style={{ marginTop: 8 }}
                    />
                </View>
            ) : (
                <View style={{ paddingHorizontal: theme.spacing.lg, alignItems: 'center', paddingTop: 60 }}>
                    <View
                        style={{
                            width: 92, height: 92, borderRadius: 46, backgroundColor: c.successTint,
                            alignItems: 'center', justifyContent: 'center', marginBottom: 24,
                        }}
                    >
                        <Ionicons name="checkmark-circle" size={46} color={c.success} />
                    </View>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text, marginBottom: 8, textAlign: 'center' }}>
                        {t('auth.reset.successTitle')}
                    </Text>
                    <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', lineHeight: 21, maxWidth: 280 }}>
                        {t('auth.reset.successBody')}
                    </Text>
                </View>
            )}
        </Screen>
    );
};
