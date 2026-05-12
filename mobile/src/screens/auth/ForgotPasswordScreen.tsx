import React, { useState, useEffect } from 'react';
import { View, Text, TextInput, KeyboardAvoidingView, Platform, TouchableOpacity, ScrollView, Keyboard } from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const ForgotPasswordScreen = () => {
    const [email, setEmail] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);
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

    const handleReset = async () => {
        if (!email) {
            Toast.show({ type: 'error', text1: t('auth.forgot.errorTitle'), text2: t('auth.forgot.emailRequired') });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();
        try {
            await AuthService.forgotPassword(email);
            setIsSuccess(true);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.forgot.failedTitle'), text2: error.response?.data?.message || t('auth.forgot.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <KeyboardAvoidingView
            style={tw`flex-1 bg-surface`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
        >
            <ScrollView
                contentContainerStyle={tw`flex-grow px-6 pt-12 ${isKeyboardVisible ? 'pb-100' : 'pb-6'}`}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}
            >
                {/* Navigation Back */}
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
                        {/* Header Content */}
                        <View style={tw`flex-col items-center text-center mt-4`}>
                            <View style={tw`bg-primary/10 p-5 rounded-full mb-6 items-center justify-center`}>
                                <MaterialIcons name="lock-reset" size={40} color={tw.color('primary')} />
                            </View>
                            <Text style={tw`text-3xl font-bold tracking-tight text-slate-900 mb-3`}>{t('auth.forgot.title')}</Text>
                            <Text style={tw`text-slate-500 text-base text-center leading-relaxed max-w-[280px]`}>
                                {t('auth.forgot.description')}
                            </Text>
                        </View>

                        {/* Form */}
                        <View style={tw`flex-1 mt-10`}>
                            <View style={tw`mb-6`}>
                                <Text style={tw`text-sm font-semibold text-slate-700 mb-2 ml-1`}>E-Posta Adresi</Text>
                                <View style={tw`relative`}>
                                    <View style={tw`absolute inset-y-0 left-0 pl-4 flex-row items-center pointer-events-none z-10`}>
                                        <Ionicons name="mail" size={22} color="#94a3b8" />
                                    </View>
                                    <TextInput
                                        style={tw`w-full pl-12 pr-4 py-4 bg-slate-50 border-2 border-transparent rounded-xl text-base text-slate-900`}
                                        placeholder="ornek@mail.com"
                                        placeholderTextColor="#94a3b8"
                                        value={email}
                                        onChangeText={setEmail}
                                        keyboardType="email-address"
                                        autoCapitalize="none"
                                        returnKeyType="done"
                                        onSubmitEditing={handleReset}
                                    />
                                </View>
                            </View>
                        </View>

                        {/* Footer / Action */}
                        <View style={tw`mt-auto pb-6`}>
                            <TouchableOpacity
                                style={tw`w-full flex-row items-center justify-center bg-primary py-4 rounded-xl shadow-lg border border-transparent ${isLoading ? 'opacity-50' : ''}`}
                                onPress={handleReset}
                                disabled={isLoading}
                                activeOpacity={0.8}
                            >
                                <Text style={tw`text-white font-bold text-base`}>{isLoading ? t('auth.forgot.submitLoading') : t('auth.forgot.submit')}</Text>
                            </TouchableOpacity>
                        </View>
                        <View style={tw`flex-row items-center justify-center pb-4 opacity-60`}>
                            <Ionicons name="shield-checkmark" size={16} color="#475569" style={tw`mr-1`} />
                            <Text style={tw`text-xs font-semibold uppercase tracking-wider text-slate-500`}>SECURED BY CEPSANDIK</Text>
                        </View>
                    </View>
                ) : (
                    /* Success State */
                    <View style={tw`flex-1 flex-col items-center justify-center py-12`}>
                        <View style={tw`flex-1 flex-col items-center justify-center w-full`}>
                            <View style={tw`w-24 h-24 bg-green-100 rounded-full items-center justify-center mb-8`}>
                                <Ionicons name="checkmark-circle" size={48} color="#16a34a" />
                            </View>
                            <Text style={tw`text-2xl font-bold text-slate-900 mb-3 text-center`}>{t('auth.forgot.checkEmail')}</Text>
                            <Text style={tw`text-slate-500 text-base text-center leading-relaxed max-w-[280px]`}>
                                {t('auth.forgot.sentToEmail', { email })}
                            </Text>
                        </View>

                        <View style={tw`w-full mt-auto mb-8`}>
                            <TouchableOpacity
                                style={tw`w-full items-center justify-center bg-slate-100 py-4 rounded-xl`}
                                onPress={() => navigation.navigate('Login')}
                                activeOpacity={0.8}
                            >
                                <Text style={tw`text-slate-900 font-bold text-base`}>{t('auth.forgot.backToLogin')}</Text>
                            </TouchableOpacity>

                            <View style={tw`flex-row items-center justify-center mt-6 gap-2`}>
                                <Text style={tw`text-xs font-medium text-slate-500`}>
                                    {t('auth.forgot.notReceived')} <Text style={tw`text-primary font-semibold`} onPress={handleReset}>{t('auth.forgot.resend')}</Text>
                                </Text>
                            </View>
                        </View>
                    </View>
                )}
            </ScrollView>
        </KeyboardAvoidingView>
    );
};
