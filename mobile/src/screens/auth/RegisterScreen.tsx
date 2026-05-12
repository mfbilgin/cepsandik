import React, { useState, useRef, useEffect } from 'react';
import { View, Text, TextInput, KeyboardAvoidingView, Platform, TouchableOpacity, ScrollView, Keyboard, ImageBackground } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const RegisterScreen = () => {
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [isPasswordVisible, setIsPasswordVisible] = useState(false);
    const [isConfirmPasswordVisible, setIsConfirmPasswordVisible] = useState(false);
    const [kvkkAccepted, setKvkkAccepted] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);

    const lastNameRef = useRef<TextInput>(null);
    const emailRef = useRef<TextInput>(null);
    const passwordRef = useRef<TextInput>(null);
    const confirmPasswordRef = useRef<TextInput>(null);

    const navigation = useNavigation<any>();
    const { t } = useI18n();

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

    const getPasswordStrength = () => {
        let score = 0;
        if (password.length > 5) score += 1;
        if (password.length > 8) score += 1;
        if (/[A-Z]/.test(password)) score += 1;
        if (/[0-9]/.test(password)) score += 1;
        return score; // 0 to 4
    };

    const strengthScore = getPasswordStrength();
    const strengthColors = ['bg-transparent', 'bg-red-500', 'bg-yellow-500', 'bg-green-400', 'bg-green-600'];
    const strengthLabels = [
        t('auth.register.passwordStrength.0'),
        t('auth.register.passwordStrength.1'),
        t('auth.register.passwordStrength.2'),
        t('auth.register.passwordStrength.3'),
        t('auth.register.passwordStrength.4'),
    ];

    const handleRegister = async () => {
        if (!firstName || !lastName || !email || !password || !confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.register.missingTitle'), text2: t('auth.register.missingBody') });
            return;
        }
        if (password !== confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.register.errorTitle'), text2: t('auth.register.passwordMismatch') });
            return;
        }
        if (!kvkkAccepted) {
            Toast.show({ type: 'error', text1: t('auth.register.errorTitle'), text2: t('auth.register.kvkkRequired') });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();
        try {
            await AuthService.register({ firstName, lastName, email, password });
            Toast.show({ type: 'success', text1: t('auth.register.successTitle'), text2: t('auth.register.successBody') });
            // add a small delay so user can read the toast
            setTimeout(() => {
                navigation.navigate('Login');
            }, 1000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.register.failedTitle'), text2: error.response?.data?.message || t('auth.register.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <KeyboardAvoidingView
            style={tw`flex-1 bg-background`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 0}
        >
            <View style={tw`flex-row items-center bg-background p-4 pb-2 justify-between z-10 top-0 pt-10`}>
                <TouchableOpacity
                    onPress={() => navigation.goBack()}
                    style={tw`w-12 h-12 items-center justify-center rounded-full bg-slate-200/50`}
                >
                    <Ionicons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <Text style={tw`text-lg font-bold text-slate-900 flex-1 text-center pr-12`}>{t('auth.register.title')}</Text>
            </View>

            <ScrollView
                contentContainerStyle={tw`flex-grow ${isKeyboardVisible ? 'pb-100' : ''}`}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}
            >
                {/* Hero / Branding Section */}
                <View style={tw`px-4 py-2`}>
                    <ImageBackground
                        source={{ uri: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBAX2-poEMyGbMXK2iBzCjdXCAiRHAMz1wj4appX0z83DUkaSUhYim50orTciiI-liphMM1cZzjr5hByBZjWhMP5AIr8-ZHaNLOBjudErO2TUXFFRqRShDUwam9m1HhmQ6JXjRRaXd9LMxqsh2mkE-0Qy_K4E2XZglWZTbZ7x-rM0zvwQdubDGOAJBjgP2aEGaeMb_YtHnyrGPgBmrjqrO-Ix5eYi4Py4dX1lUBTbsFl98ntJeGJ1dOSBJ30ZhwOqGzWMuyIMBZ1Gfq' }}
                        style={tw`relative flex-col justify-end overflow-hidden rounded-xl min-h-[160px] shadow-sm`}
                        imageStyle={tw`opacity-80 bg-primary`}
                    >
                        <View style={tw`absolute inset-0 bg-primary/60`} />
                        <View style={tw`absolute top-4 right-4 opacity-50`}>
                            <Ionicons name="shield-checkmark" size={60} color="white" />
                        </View>
                        <View style={tw`flex-col p-4 z-10`}>
                            <View style={tw`flex-row items-center gap-2 mb-1`}>
                                <Ionicons name="stats-chart" size={14} color="white" />
                                <Text style={tw`text-xs font-semibold uppercase tracking-wider text-white`}>CEPSANDIK</Text>
                            </View>
                            <Text style={tw`text-white text-2xl font-bold leading-tight`}>Güvenli Kayıt</Text>
                            <Text style={tw`text-blue-100 text-sm mt-1`}>Şeffaf oylama ağına katılın.</Text>
                        </View>
                    </ImageBackground>
                </View>

                {/* Form Fields */}
                <View style={tw`flex-col gap-4 px-4 py-4`}>
                    {/* Name Row */}
                    <View style={tw`flex-row gap-4`}>
                        <View style={tw`flex-1 flex-col`}>
                            <Text style={tw`text-sm font-medium text-slate-700 pb-2`}>Ad</Text>
                            <TextInput
                                style={tw`w-full rounded-lg border border-slate-300 bg-surface text-slate-900 h-12 px-3 text-base`}
                                placeholder="Adınız"
                                placeholderTextColor="#94a3b8"
                                value={firstName}
                                onChangeText={setFirstName}
                                returnKeyType="next"
                                onSubmitEditing={() => lastNameRef.current?.focus()}
                                blurOnSubmit={false}
                            />
                        </View>
                        <View style={tw`flex-1 flex-col`}>
                            <Text style={tw`text-sm font-medium text-slate-700 pb-2`}>Soyad</Text>
                            <TextInput
                                ref={lastNameRef}
                                style={tw`w-full rounded-lg border border-slate-300 bg-surface text-slate-900 h-12 px-3 text-base`}
                                placeholder="Soyadınız"
                                placeholderTextColor="#94a3b8"
                                value={lastName}
                                onChangeText={setLastName}
                                returnKeyType="next"
                                onSubmitEditing={() => emailRef.current?.focus()}
                                blurOnSubmit={false}
                            />
                        </View>
                    </View>

                    {/* Email */}
                    <View style={tw`flex-col w-full`}>
                        <Text style={tw`text-sm font-medium text-slate-700 pb-2`}>E-Posta Adresi</Text>
                        <View style={tw`relative`}>
                            <TextInput
                                ref={emailRef}
                                style={tw`w-full rounded-lg border border-slate-300 bg-surface text-slate-900 h-12 pl-3 pr-10 text-base`}
                                placeholder="isim@ornek.com"
                                placeholderTextColor="#94a3b8"
                                value={email}
                                onChangeText={setEmail}
                                keyboardType="email-address"
                                autoCapitalize="none"
                                returnKeyType="next"
                                onSubmitEditing={() => passwordRef.current?.focus()}
                                blurOnSubmit={false}
                            />
                            <View style={tw`absolute right-3 top-3`}>
                                <Ionicons name="mail-outline" size={20} color="#94a3b8" />
                            </View>
                        </View>
                    </View>

                    {/* Password */}
                    <View style={tw`flex-col w-full`}>
                        <Text style={tw`text-sm font-medium text-slate-700 pb-2`}>Parola</Text>
                        <View style={tw`relative`}>
                            <TextInput
                                ref={passwordRef}
                                style={tw`w-full rounded-lg border border-slate-300 bg-surface text-slate-900 h-12 pl-3 pr-10 text-base`}
                                placeholder="••••••••"
                                placeholderTextColor="#94a3b8"
                                secureTextEntry={!isPasswordVisible}
                                value={password}
                                onChangeText={setPassword}
                                returnKeyType="next"
                                onSubmitEditing={() => confirmPasswordRef.current?.focus()}
                                blurOnSubmit={false}
                            />
                            <TouchableOpacity
                                style={tw`absolute right-3 top-3`}
                                onPress={() => setIsPasswordVisible(!isPasswordVisible)}
                            >
                                <Ionicons name={isPasswordVisible ? "eye-outline" : "eye-off-outline"} size={20} color="#94a3b8" />
                            </TouchableOpacity>
                        </View>

                        {/* Password Strength Indicator */}
                        <View style={tw`mt-2 flex-row items-center gap-2`}>
                            <View style={tw`flex-row h-1.5 flex-1 gap-1 overflow-hidden rounded-full bg-slate-100`}>
                                <View style={tw`h-full w-1/4 rounded-full ${strengthScore >= 1 ? strengthColors[strengthScore] : 'bg-transparent'}`} />
                                <View style={tw`h-full w-1/4 rounded-full ${strengthScore >= 2 ? strengthColors[strengthScore] : 'bg-transparent'}`} />
                                <View style={tw`h-full w-1/4 rounded-full ${strengthScore >= 3 ? strengthColors[strengthScore] : 'bg-transparent'}`} />
                                <View style={tw`h-full w-1/4 rounded-full ${strengthScore >= 4 ? strengthColors[strengthScore] : 'bg-transparent'}`} />
                            </View>
                            <Text style={tw`text-xs font-medium text-slate-500 w-16 text-right`}>
                                {password.length > 0 ? strengthLabels[strengthScore] : ''}
                            </Text>
                        </View>
                    </View>

                    {/* Confirm Password */}
                    <View style={tw`flex-col w-full`}>
                        <Text style={tw`text-sm font-medium text-slate-700 pb-2`}>Parola (Tekrar)</Text>
                        <View style={tw`relative`}>
                            <TextInput
                                ref={confirmPasswordRef}
                                style={tw`w-full rounded-lg border border-slate-300 bg-surface text-slate-900 h-12 pl-3 pr-10 text-base`}
                                placeholder="••••••••"
                                placeholderTextColor="#94a3b8"
                                secureTextEntry={!isConfirmPasswordVisible}
                                value={confirmPassword}
                                onChangeText={setConfirmPassword}
                                returnKeyType="done"
                                onSubmitEditing={Keyboard.dismiss}
                            />
                            <TouchableOpacity
                                style={tw`absolute right-3 top-3`}
                                onPress={() => setIsConfirmPasswordVisible(!isConfirmPasswordVisible)}
                            >
                                <Ionicons name={isConfirmPasswordVisible ? "eye-outline" : "eye-off-outline"} size={20} color="#94a3b8" />
                            </TouchableOpacity>
                        </View>
                    </View>

                    {/* KVKK Consent */}
                    <View style={tw`flex-col pt-2`}>
                        <TouchableOpacity
                            style={tw`flex-row items-start gap-3 mt-2`}
                            onPress={() => setKvkkAccepted(!kvkkAccepted)}
                            activeOpacity={0.8}
                        >
                            <View style={tw`w-5 h-5 rounded border ${kvkkAccepted ? 'border-primary bg-primary' : 'border-slate-300 bg-surface'} items-center justify-center mt-0.5`}>
                                {kvkkAccepted && <Ionicons name="checkmark" size={14} color="white" />}
                            </View>
                            <Text style={tw`flex-1 text-sm leading-tight text-slate-600`}>
                                <Text style={tw`font-medium text-primary underline`}>Gizlilik Politikası</Text> ve{' '}
                                <Text style={tw`font-medium text-primary underline`}>KVKK Metnini</Text>
                                {' '}okudum, onaylıyorum.
                            </Text>
                        </TouchableOpacity>
                    </View>

                    {/* Submit Button */}
                    <View style={tw`pt-4 pb-20`}>
                        <TouchableOpacity
                            style={tw`w-full bg-primary flex-row items-center justify-center gap-2 py-3.5 rounded-lg shadow-sm ${isLoading ? 'opacity-50' : ''}`}
                            onPress={handleRegister}
                            disabled={isLoading}
                        >
                            <Text style={tw`text-white font-bold text-base`}>{isLoading ? 'Lütfen Bekleyin...' : 'Kayıt Ol'}</Text>
                            {!isLoading && <Ionicons name="arrow-forward" size={20} color="white" />}
                        </TouchableOpacity>

                        {/* Footer Link */}
                        <View style={tw`text-center mt-6 flex-row items-center justify-center`}>
                            <Text style={tw`text-slate-500 text-sm`}>
                                Zaten hesabınız var mı?{' '}
                                <Text style={tw`font-semibold text-primary`} onPress={() => navigation.navigate('Login')}>
                                    Giriş Yap
                                </Text>
                            </Text>
                        </View>
                    </View>

                </View>
            </ScrollView>
        </KeyboardAvoidingView>
    );
};
