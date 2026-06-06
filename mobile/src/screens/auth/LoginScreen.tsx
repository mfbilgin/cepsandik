import React, { useState, useEffect, useRef } from 'react';
import { View, Text, TextInput, TouchableOpacity, Keyboard } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useAuth } from '../../context/AuthContext';
import { useNavigation } from '@react-navigation/native';
import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, Input, Button } from '../../components/ui';

export const LoginScreen = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isBiometricSupported, setIsBiometricSupported] = useState(false);
    const [isBiometricReady, setIsBiometricReady] = useState(false);
    const { signIn } = useAuth();
    const { t } = useI18n();
    const navigation = useNavigation<any>();
    const passwordRef = useRef<TextInput>(null);

    useEffect(() => {
        (async () => {
            const compatible = await LocalAuthentication.hasHardwareAsync();
            const enrolled = await LocalAuthentication.isEnrolledAsync();
            setIsBiometricSupported(compatible && enrolled);

            const savedEmail = await SecureStore.getItemAsync('saved_email');
            const refreshToken = await SecureStore.getItemAsync('refresh_token');
            if (savedEmail && refreshToken) {
                setIsBiometricReady(true);
            }
        })();
    }, []);

    const performSignIn = async (mail: string, pass: string) => {
        setIsLoading(true);
        Keyboard.dismiss();
        try {
            const response = await AuthService.login(mail, pass);
            if (response.accessToken) {
                await SecureStore.setItemAsync('saved_email', mail);
                setIsBiometricReady(true);

                const userData = await AuthService.getProfile();
                await signIn(response.accessToken, response.refreshToken || null, userData);
                Toast.show({ type: 'success', text1: t('auth.login.successTitle'), text2: t('auth.login.successBody') });
            } else if (response.requires2FA && response.tempToken) {
                navigation.navigate('TwoFactorLogin', { tempToken: response.tempToken });
            }
        } catch (error: any) {
            const errorMsg = error.response?.data?.message;
            const status = error.response?.status;

            // 403 Forbidden ve doğrulama hatası mesajı varsa (Türkçe ve İngilizce kontrolü)
            const isVerificationError = status === 403 && (
                errorMsg?.toLowerCase().includes('verify') ||
                errorMsg?.toLowerCase().includes('aktif') ||
                errorMsg?.toLowerCase().includes('doğrula')
            );

            if (isVerificationError) {
                navigation.navigate('VerificationPending', { email });
            } else {
                Toast.show({
                    type: 'error',
                    text1: t('auth.login.errorTitle'),
                    text2: errorMsg || t('auth.login.errorInvalidCredentials')
                });
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleLogin = () => {
        if (!email || !password) {
            Toast.show({ type: 'error', text1: t('auth.login.missingTitle'), text2: t('auth.login.missingBody') });
            return;
        }
        performSignIn(email, password);
    };

    const handleBiometricLogin = async () => {
        if (!isBiometricReady) return;
        try {
            const savedEmail = await SecureStore.getItemAsync('saved_email');
            const refreshToken = await SecureStore.getItemAsync('refresh_token');
            if (!savedEmail || !refreshToken) {
                setIsBiometricReady(false);
                return;
            }

            const biometricAuth = await LocalAuthentication.authenticateAsync({
                promptMessage: t('auth.login.biometricPrompt'),
                disableDeviceFallback: false,
            });
            if (!biometricAuth.success) return;

            setIsLoading(true);
            try {
                const authData = await AuthService.refreshWithToken(refreshToken);
                if (authData?.accessToken) {
                    const userData = await AuthService.getProfile();
                    await signIn(authData.accessToken, authData.refreshToken || refreshToken, userData);
                    Toast.show({ type: 'success', text1: t('auth.login.successTitle'), text2: t('auth.login.successBody') });
                } else {
                    throw new Error('No access token in refresh response');
                }
            } catch (refreshErr) {
                // Refresh token süresi doldu / geçersiz → biyometrik durumu temizle, klasik girişe yönlendir.
                await SecureStore.deleteItemAsync('refresh_token');
                setIsBiometricReady(false);
                Toast.show({
                    type: 'error',
                    text1: t('auth.login.biometricErrorTitle'),
                    text2: t('auth.login.errorInvalidCredentials')
                });
            } finally {
                setIsLoading(false);
            }
        } catch (error) {
            Toast.show({ type: 'error', text1: t('auth.login.biometricErrorTitle'), text2: t('auth.login.biometricErrorBody') });
        }
    };

    return (
        <Screen keyboardAvoiding scroll padded={false} contentStyle={{ justifyContent: 'center' }}>
            <View style={{ paddingHorizontal: theme.spacing.lg, gap: theme.spacing.lg }}>
                {/* Marka başlığı */}
                <View style={{ alignItems: 'center', gap: 14, paddingTop: 16 }}>
                    <View
                        style={{
                            width: 84, height: 84, borderRadius: theme.borderRadius.xl,
                            backgroundColor: theme.colors.primaryTint,
                            alignItems: 'center', justifyContent: 'center',
                        }}
                    >
                        <Ionicons name="shield-checkmark" size={42} color={theme.colors.primary} />
                    </View>
                    <View style={{ alignItems: 'center' }}>
                        <Text style={{ fontSize: 28, fontWeight: '700', color: theme.colors.text }}>
                            CepSandık
                        </Text>
                        <Text
                            style={{
                                color: theme.colors.textSecondary, fontSize: 15,
                                textAlign: 'center', marginTop: 6, maxWidth: 280, lineHeight: 21,
                            }}
                        >
                            {t('auth.login.description')}
                        </Text>
                    </View>
                </View>

                {/* Giriş formu */}
                <View
                    style={{
                        backgroundColor: theme.colors.surface,
                        borderRadius: theme.borderRadius.xl,
                        borderWidth: 1,
                        borderColor: theme.colors.border,
                        padding: theme.spacing.lg,
                        ...theme.shadows.card,
                    }}
                >
                    <Input
                        label={t('auth.login.emailLabel')}
                        icon="mail-outline"
                        placeholder="example@mail.com"
                        value={email}
                        onChangeText={setEmail}
                        keyboardType="email-address"
                        autoCapitalize="none"
                        returnKeyType="next"
                        onSubmitEditing={() => passwordRef.current?.focus()}
                        blurOnSubmit={false}
                    />

                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <Text style={{ fontSize: 13, fontWeight: '600', color: theme.colors.textSecondary }}>
                            {t('auth.login.passwordLabel')}
                        </Text>
                        <TouchableOpacity onPress={() => navigation.navigate('ForgotPassword')} hitSlop={8}>
                            <Text style={{ fontSize: 13, fontWeight: '600', color: theme.colors.primary }}>
                                {t('auth.login.forgotPassword')}
                            </Text>
                        </TouchableOpacity>
                    </View>
                    <Input
                        ref={passwordRef}
                        icon="lock-closed-outline"
                        placeholder="••••••••"
                        password
                        value={password}
                        onChangeText={setPassword}
                        returnKeyType="done"
                        onSubmitEditing={handleLogin}
                    />

                    <Button
                        title={isLoading ? t('auth.login.loading') : t('auth.login.submit')}
                        icon="log-in-outline"
                        iconPosition="right"
                        loading={isLoading}
                        onPress={handleLogin}
                        style={{ marginTop: 4 }}
                    />

                    {isBiometricSupported && isBiometricReady && (
                        <Button
                            title={t('auth.login.biometric')}
                            icon="finger-print"
                            variant="secondary"
                            onPress={handleBiometricLogin}
                            disabled={isLoading}
                            style={{ marginTop: 12 }}
                        />
                    )}
                </View>

                {/* Alt bilgi */}
                <View style={{ alignItems: 'center', gap: 18, paddingBottom: 16 }}>
                    <Text style={{ color: theme.colors.textSecondary, fontSize: 14 }}>
                        {t('auth.login.noAccount')}{' '}
                        <Text
                            style={{ color: theme.colors.primary, fontWeight: '700' }}
                            onPress={() => navigation.navigate('Register')}
                        >
                            {t('auth.login.register')}
                        </Text>
                    </Text>

                    <View
                        style={{
                            flexDirection: 'row', alignItems: 'center', gap: 6,
                            paddingHorizontal: 14, paddingVertical: 7,
                            backgroundColor: theme.colors.surfaceAlt,
                            borderRadius: theme.borderRadius.round,
                            borderWidth: 1, borderColor: theme.colors.border,
                        }}
                    >
                        <Ionicons name="shield-checkmark" size={14} color={theme.colors.textSecondary} />
                        <Text
                            style={{
                                fontSize: 11, fontWeight: '600',
                                color: theme.colors.textSecondary, letterSpacing: 0.5,
                            }}
                        >
                            SECURED BY ELECTIONGUARD
                        </Text>
                    </View>
                </View>
            </View>
        </Screen>
    );
};
