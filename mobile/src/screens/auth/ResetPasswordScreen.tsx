import React, { useState, useRef, useEffect } from 'react';
import { View, Text, TextInput, KeyboardAvoidingView, Platform, TouchableOpacity, ScrollView, Keyboard } from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useNavigation, useRoute } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { useI18n } from '../../i18n/LanguageContext';

export const ResetPasswordScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { token } = route.params || {};
    const { t } = useI18n();

    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [isPasswordVisible, setIsPasswordVisible] = useState(false);
    const [isConfirmPasswordVisible, setIsConfirmPasswordVisible] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);

    const confirmPasswordRef = useRef<TextInput>(null);

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
            keyboardDidShowListener.remove();
            keyboardDidHideListener.remove();
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
            setTimeout(() => {
                navigation.navigate('Login');
            }, 3000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.reset.failedTitle'), text2: error.response?.data?.message || t('auth.reset.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <KeyboardAvoidingView
            style={tw`flex-1 bg-white`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
            <ScrollView contentContainerStyle={tw`flex-grow px-6 ${isKeyboardVisible ? 'pb-100' : 'pb-8'} pt-12`} keyboardShouldPersistTaps="handled">
                <View style={tw`flex-row items-center py-2 mb-6`}>
                    <TouchableOpacity
                        onPress={() => navigation.goBack()}
                        style={tw`w-10 h-10 items-center justify-center rounded-full hover:bg-slate-100`}
                        activeOpacity={0.7}
                    >
                        <Ionicons name="chevron-back" size={28} color="#0f172a" />
                    </TouchableOpacity>
                </View>

                {!isSuccess ? (
                    <View style={tw`flex-1 flex-col`}>
                        <View style={tw`flex-col items-center text-center mt-4`}>
                            <View style={tw`bg-[#1162d4]/10 p-5 rounded-full mb-6 items-center justify-center`}>
                                <MaterialIcons name="password" size={40} color="#1162d4" />
                            </View>
                            <Text style={tw`text-3xl font-bold tracking-tight text-slate-900 mb-3`}>{t('auth.reset.title')}</Text>
                            <Text style={tw`text-slate-500 text-base text-center leading-relaxed max-w-[280px]`}>
                                {t('auth.reset.description')}
                            </Text>
                        </View>

                        <View style={tw`flex-1 mt-10 flex-col gap-4`}>
                            <View style={tw`flex-col w-full`}>
                                <Text style={tw`text-sm font-semibold text-slate-700 pb-2 ml-1`}>{t('auth.reset.newPassword')}</Text>
                                <View style={tw`relative`}>
                                    <TextInput
                                        style={tw`w-full rounded-xl border-2 border-slate-100 bg-slate-50 text-slate-900 focus:border-[#1162d4] h-14 pl-4 pr-12 text-base`}
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
                                        style={tw`absolute right-4 top-4`}
                                        onPress={() => setIsPasswordVisible(!isPasswordVisible)}
                                    >
                                        <Ionicons name={isPasswordVisible ? "eye-outline" : "eye-off-outline"} size={22} color="#94a3b8" />
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

                            <View style={tw`flex-col w-full`}>
                                <Text style={tw`text-sm font-semibold text-slate-700 pb-2 ml-1`}>{t('auth.reset.newPasswordAgain')}</Text>
                                <View style={tw`relative`}>
                                    <TextInput
                                        ref={confirmPasswordRef}
                                        style={tw`w-full rounded-xl border-2 border-slate-100 bg-slate-50 text-slate-900 focus:border-[#1162d4] h-14 pl-4 pr-12 text-base`}
                                        placeholder="••••••••"
                                        placeholderTextColor="#94a3b8"
                                        secureTextEntry={!isConfirmPasswordVisible}
                                        value={confirmPassword}
                                        onChangeText={setConfirmPassword}
                                        returnKeyType="done"
                                        onSubmitEditing={handleReset}
                                    />
                                    <TouchableOpacity
                                        style={tw`absolute right-4 top-4`}
                                        onPress={() => setIsConfirmPasswordVisible(!isConfirmPasswordVisible)}
                                    >
                                        <Ionicons name={isConfirmPasswordVisible ? "eye-outline" : "eye-off-outline"} size={22} color="#94a3b8" />
                                    </TouchableOpacity>
                                </View>
                            </View>
                        </View>

                        <View style={tw`mt-auto`}>
                            <TouchableOpacity
                                style={tw`w-full flex-row items-center justify-center bg-[#1162d4] py-4 rounded-xl shadow-lg border border-transparent ${isLoading ? 'opacity-50' : ''}`}
                                onPress={handleReset}
                                disabled={isLoading}
                                activeOpacity={0.8}
                            >
                                <Text style={tw`text-white font-bold text-base`}>{isLoading ? t('auth.reset.saving') : t('auth.reset.updatePassword')}</Text>
                            </TouchableOpacity>
                        </View>
                    </View>
                ) : (
                    <View style={tw`flex-1 flex-col items-center justify-center py-12`}>
                        <View style={tw`flex-1 flex-col items-center justify-center w-full`}>
                            <View style={tw`w-24 h-24 bg-green-100 rounded-full items-center justify-center mb-8`}>
                                <Ionicons name="checkmark-circle" size={48} color="#16a34a" />
                            </View>
                            <Text style={tw`text-2xl font-bold text-slate-900 mb-3 text-center`}>{t('auth.reset.successTitle')}</Text>
                            <Text style={tw`text-slate-500 text-base text-center leading-relaxed max-w-[280px]`}>
                                {t('auth.reset.successBody')}
                            </Text>
                        </View>
                    </View>
                )}
            </ScrollView>
        </KeyboardAvoidingView>
    );
};
