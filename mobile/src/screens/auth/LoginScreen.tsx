import React, { useState, useEffect, useRef } from 'react';
import { View, Text, TextInput, KeyboardAvoidingView, Platform, TouchableOpacity, ScrollView, Keyboard } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useAuth } from '../../context/AuthContext';
import { useNavigation } from '@react-navigation/native';
import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const LoginScreen = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [isPasswordVisible, setIsPasswordVisible] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isBiometricSupported, setIsBiometricSupported] = useState(false);
    const [isBiometricReady, setIsBiometricReady] = useState(false);
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);
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
            const savedPassword = await SecureStore.getItemAsync('saved_password');
            if (savedEmail && savedPassword) {
                setIsBiometricReady(true);
            }
        })();
    }, []);
    useEffect(() => {
        const keyboardDidShowListener = Keyboard.addListener(
            Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
            () => setKeyboardVisible(true)
        );
        const keyboardDidHideListener = Keyboard.addListener(
            Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
            () => setKeyboardVisible(false)
        );

        return () => {
            keyboardDidHideListener.remove();
            keyboardDidShowListener.remove();
        };
    }, []);

    const performSignIn = async (mail: string, pass: string) => {
        setIsLoading(true);
        Keyboard.dismiss();
        try {
            const response = await AuthService.login(mail, pass);
            if (response.accessToken) {
                await SecureStore.setItemAsync('saved_email', mail);
                await SecureStore.setItemAsync('saved_password', pass);
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
                navigation.navigate('VerificationPending', { email, password });
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
            const savedPassword = await SecureStore.getItemAsync('saved_password');

            if (!savedEmail || !savedPassword) return;

            const biometricAuth = await LocalAuthentication.authenticateAsync({
                promptMessage: t('auth.login.biometricPrompt'),
                disableDeviceFallback: false,
            });

            if (biometricAuth.success) {
                performSignIn(savedEmail, savedPassword);
            }
        } catch (error) {
            Toast.show({ type: 'error', text1: t('auth.login.biometricErrorTitle'), text2: t('auth.login.biometricErrorBody') });
        }
    };

    return (

        <KeyboardAvoidingView
            style={tw`flex-1 bg-background`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
        >
            <ScrollView
                contentContainerStyle={tw`flex-grow justify-center items-center p-4 py-10 ${isKeyboardVisible ? 'pb-100' : 'pb-6'}`}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}
            >
                <View style={tw`w-full max-w-[400px] flex-col gap-6`}>
                    {/* Header Logo */}
                    <View style={tw`flex-col items-center gap-4 pt-8 pb-4`}>
                        <View style={tw`w-24 h-24 rounded-2xl bg-primary/10 items-center justify-center border border-primary/20`}>
                            <Ionicons name="checkbox" size={48} color={tw.color('primary')} />
                        </View>
                        <View style={tw`items-center`}>
                            <Text style={tw`text-3xl font-bold text-slate-900 mb-2`}>CepSandık</Text>
                            <Text style={tw`text-slate-500 text-base text-center max-w-[280px]`}>
                                {t('auth.login.description')}
                            </Text>
                        </View>
                    </View>

                    {/* Login Form */}
                    <View style={tw`flex-col gap-5 w-full bg-surface p-6 rounded-2xl shadow-sm border border-slate-100`}>

                        <View style={tw`flex-col gap-2`}>
                            <Text style={tw`text-sm font-semibold text-slate-700`}>{t('auth.login.emailLabel')}</Text>
                            <View style={tw`relative flex-row items-center`}>
                                <View style={tw`absolute left-3 z-10`}>
                                    <Ionicons name="mail-outline" size={20} color="#94a3b8" />
                                </View>
                                <TextInput
                                    style={tw`w-full rounded-lg border border-slate-200 bg-slate-50 text-slate-900 pl-10 py-3 text-base`}
                                    placeholder="example@mail.com"
                                    placeholderTextColor="#94a3b8"
                                    value={email}
                                    onChangeText={setEmail}
                                    keyboardType="email-address"
                                    autoCapitalize="none"
                                    returnKeyType="next"
                                    onSubmitEditing={() => passwordRef.current?.focus()}
                                    blurOnSubmit={false}
                                />
                            </View>
                        </View>

                        <View style={tw`flex-col gap-2`}>
                            <View style={tw`flex-row justify-between items-center`}>
                                <Text style={tw`text-sm font-semibold text-slate-700`}>{t('auth.login.passwordLabel')}</Text>
                                <TouchableOpacity onPress={() => navigation.navigate('ForgotPassword')}>
                                    <Text style={tw`text-sm font-medium text-primary`}>{t('auth.login.forgotPassword')}</Text>
                                </TouchableOpacity>
                            </View>
                            <View style={tw`relative flex-row items-center`}>
                                <View style={tw`absolute left-3 z-10`}>
                                    <Ionicons name="lock-closed-outline" size={20} color="#94a3b8" />
                                </View>
                                <TextInput
                                    ref={passwordRef}
                                    style={tw`w-full rounded-lg border border-slate-200 bg-slate-50 text-slate-900 pl-10 pr-10 py-3 text-base`}
                                    placeholder="••••••••"
                                    placeholderTextColor="#94a3b8"
                                    secureTextEntry={!isPasswordVisible}
                                    value={password}
                                    onChangeText={setPassword}
                                    returnKeyType="done"
                                    onSubmitEditing={handleLogin}
                                />
                                <TouchableOpacity
                                    style={tw`absolute right-3 z-10`}
                                    onPress={() => setIsPasswordVisible(!isPasswordVisible)}
                                >
                                    <Ionicons name={isPasswordVisible ? "eye-outline" : "eye-off-outline"} size={20} color="#94a3b8" />
                                </TouchableOpacity>
                            </View>
                        </View>

                        {/* Actions */}
                        <View style={tw`pt-2 flex-col gap-3`}>
                            <TouchableOpacity
                                style={tw`w-full bg-primary h-12 rounded-lg flex-row items-center justify-center gap-2 ${isLoading ? 'opacity-50' : ''}`}
                                onPress={handleLogin}
                                disabled={isLoading}
                            >
                                <Text style={tw`text-white font-semibold text-base`}>{isLoading ? t('auth.login.loading') : t('auth.login.submit')}</Text>
                                {!isLoading && <Ionicons name="log-in-outline" size={20} color="#ffffff" />}
                            </TouchableOpacity>

                            {isBiometricSupported && isBiometricReady && (
                                <TouchableOpacity
                                    style={tw`w-full bg-surface border border-slate-200 h-12 rounded-lg flex-row items-center justify-center gap-2`}
                                    onPress={handleBiometricLogin}
                                    disabled={isLoading}
                                >
                                    <Ionicons name="finger-print" size={20} color="#334155" />
                                    <Text style={tw`text-slate-700 font-medium`}>{t('auth.login.biometric')}</Text>
                                </TouchableOpacity>
                            )}
                        </View>
                    </View>

                    {/* Footer */}
                    <View style={tw`flex-col items-center gap-6 pb-8`}>
                        <Text style={tw`text-slate-600 text-sm`}>
                            {t('auth.login.noAccount')} <Text style={tw`text-primary font-semibold`} onPress={() => navigation.navigate('Register')}>{t('auth.login.register')}</Text>
                        </Text>

                        <View style={tw`flex-row items-center gap-2 px-4 py-2 bg-slate-100 rounded-full border border-slate-200 opacity-80`}>
                            <Ionicons name="shield-checkmark" size={16} color="#64748b" />
                            <Text style={tw`text-xs font-medium text-slate-500 tracking-wide uppercase`}>SECURED BY ELECTIONGUARD</Text>
                        </View>
                    </View>

                </View>
            </ScrollView>
        </KeyboardAvoidingView>
    );
};
