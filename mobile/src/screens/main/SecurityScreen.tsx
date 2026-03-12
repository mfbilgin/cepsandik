import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, KeyboardAvoidingView, ScrollView, Platform, Keyboard } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import { api } from '../../services/api';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import * as SecureStore from 'expo-secure-store';

export const SecurityScreen = () => {
    const navigation = useNavigation<any>();

    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [verificationPassword, setVerificationPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [secureText, setSecureText] = useState({ current: true, newP: true, confirm: true, verification: true });
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);
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

    useFocusEffect(
        React.useCallback(() => {
            const keyboardDidShowListener = Keyboard.addListener(
                'keyboardDidShow',
                () => setKeyboardVisible(true)
            );
            const keyboardDidHideListener = Keyboard.addListener(
                'keyboardDidHide',
                () => setKeyboardVisible(false)
            );

            return () => {
                keyboardDidShowListener.remove();
                keyboardDidHideListener.remove();
            };
        }, [])
    );
    const toggleSecure = (field: 'current' | 'newP' | 'confirm' | 'verification') => {
        setSecureText(prev => ({ ...prev, [field]: !prev[field] }));
    };

    const calculateStrength = (pass: string) => {
        let score = 0;
        if (pass.length > 7) score += 1;
        if (/[A-Z]/.test(pass)) score += 1;
        if (/[0-9]/.test(pass)) score += 1;
        if (/[^A-Za-z0-9]/.test(pass)) score += 1;
        return score; // 0-4
    };

    const strength = calculateStrength(newPassword);
    const strengthLevels = ['Çok Zayıf', 'Zayıf', 'Orta', 'İyi', 'Güçlü'];
    const strengthColors = ['#f43f5e', '#f97316', '#eab308', '#84cc16', '#22c55e'];

    const handleChangePassword = async () => {
        if (!currentPassword || !newPassword || !confirmPassword) {
            Toast.show({ type: 'error', text1: 'Eksik Bilgi', text2: 'Lütfen tüm alanları doldurun.' });
            return;
        }

        if (newPassword !== confirmPassword) {
            Toast.show({ type: 'error', text1: 'Hata', text2: 'Yeni parolalar eşleşmiyor.' });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();

        try {
            await api.put('/users/me/password', {
                oldPassword: currentPassword, // Fix from currentPassword
                newPassword,
                confirmPassword
            });
            await SecureStore.deleteItemAsync('saved_password');
            Toast.show({ type: 'success', text1: 'Başarılı', text2: 'Parolanız başarıyla değiştirildi. Biyometrik giriş sıfırlandı.' });
            setTimeout(() => {
                navigation.goBack();
            }, 1000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: 'Hata', text2: error.response?.data?.message || 'Parola değiştirilemedi.' });
        } finally {
            setIsLoading(false);
        }
    };

    const handleDisable2FA = async () => {
        if (!verificationPassword) {
            Toast.show({ type: 'error', text1: 'Parola Gerekli', text2: '2FA özelliğini kapatmak için mevcut parolanızı girmeniz gereklidir.' });
            return;
        }

        setIsLoading(true);
        try {
            await api.post('/users/me/2fa/disable', { password: verificationPassword });
            setIs2FAEnabled(false);
            setVerificationPassword('');
            Toast.show({ type: 'success', text1: 'Başarılı', text2: 'İki Aşamalı Doğrulama devre dışı bırakıldı.' });
        } catch (error: any) {
            Toast.show({ type: 'error', text1: 'Hata', text2: error.response?.data?.message || '2FA devre dışı bırakılamadı. Parolanızı kontrol edin.' });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <KeyboardAvoidingView
            style={tw`flex-1 bg-[#f6f7f8]`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
        >
            <View style={tw`bg-white border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>Güvenlik ve Parola</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 flex-col gap-6 ${isKeyboardVisible ? 'pb-100' : ''}`} keyboardShouldPersistTaps="handled">
                <View style={tw`flex-col gap-5 bg-white p-6 rounded-2xl shadow-sm border border-slate-100`}>
                    <Text style={tw`text-lg font-bold text-slate-900 mb-1`}>Parola Değiştir</Text>

                    <View style={tw`flex-col gap-2`}>
                        <Text style={tw`text-sm font-semibold text-slate-700 ml-1`}>Mevcut Parola</Text>
                        <View style={tw`relative`}>
                            <TextInput
                                style={tw`w-full rounded-xl border border-slate-200 bg-slate-50 text-slate-900 px-4 py-3.5 pr-12 text-base`}
                                placeholder="••••••••"
                                placeholderTextColor="#94a3b8"
                                secureTextEntry={secureText.current}
                                value={currentPassword}
                                onChangeText={setCurrentPassword}
                            />
                            <TouchableOpacity style={tw`absolute right-3 top-3.5`} onPress={() => toggleSecure('current')}>
                                <Ionicons name={secureText.current ? "eye-off-outline" : "eye-outline"} size={20} color="#94a3b8" />
                            </TouchableOpacity>
                        </View>
                    </View>

                    <View style={tw`flex-col gap-2`}>
                        <Text style={tw`text-sm font-semibold text-slate-700 ml-1`}>Yeni Parola</Text>
                        <View style={tw`relative`}>
                            <TextInput
                                style={tw`w-full rounded-xl border border-slate-200 bg-slate-50 text-slate-900 px-4 py-3.5 pr-12 text-base`}
                                placeholder="••••••••"
                                placeholderTextColor="#94a3b8"
                                secureTextEntry={secureText.newP}
                                value={newPassword}
                                onChangeText={setNewPassword}
                            />
                            <TouchableOpacity style={tw`absolute right-3 top-3.5`} onPress={() => toggleSecure('newP')}>
                                <Ionicons name={secureText.newP ? "eye-off-outline" : "eye-outline"} size={20} color="#94a3b8" />
                            </TouchableOpacity>
                        </View>
                        {newPassword.length > 0 && (
                            <View style={tw`flex-col gap-1.5 mt-1`}>
                                <View style={tw`flex-row gap-1 h-1`}>
                                    {[1, 2, 3, 4].map(idx => (
                                        <View key={idx} style={tw`flex-1 rounded-full ${strength >= idx ? `bg-[${strengthColors[strength]}]` : 'bg-slate-200'}`} />
                                    ))}
                                </View>
                                <Text style={tw`text-xs text-right text-[${strengthColors[strength]}] font-medium`}>{strengthLevels[strength]}</Text>
                            </View>
                        )}
                    </View>

                    <View style={tw`flex-col gap-2`}>
                        <Text style={tw`text-sm font-semibold text-slate-700 ml-1`}>Yeni Parola (Tekrar)</Text>
                        <View style={tw`relative`}>
                            <TextInput
                                style={tw`w-full rounded-xl border border-slate-200 bg-slate-50 text-slate-900 px-4 py-3.5 pr-12 text-base`}
                                placeholder="••••••••"
                                placeholderTextColor="#94a3b8"
                                secureTextEntry={secureText.confirm}
                                value={confirmPassword}
                                onChangeText={setConfirmPassword}
                            />
                            <TouchableOpacity style={tw`absolute right-3 top-3.5`} onPress={() => toggleSecure('confirm')}>
                                <Ionicons name={secureText.confirm ? "eye-off-outline" : "eye-outline"} size={20} color="#94a3b8" />
                            </TouchableOpacity>
                        </View>
                    </View>

                    <TouchableOpacity
                        style={tw`mt-4 w-full bg-[#1162d4] flex-row items-center justify-center gap-2 py-4 rounded-xl shadow-sm ${isLoading ? 'opacity-50' : ''}`}
                        onPress={handleChangePassword}
                        disabled={isLoading}
                    >
                        <Text style={tw`text-white font-bold text-base`}>{isLoading ? 'Güncelleniyor...' : 'Parolayı Güncelle'}</Text>
                        {!isLoading && <Ionicons name="lock-closed" size={20} color="white" />}
                    </TouchableOpacity>
                </View>

                {/* 2FA Block */}
                <View style={tw`flex-col gap-2 bg-white p-6 rounded-2xl shadow-sm border border-slate-100 mt-2`}>
                    <View style={tw`flex-row items-center justify-between`}>
                        <View style={tw`flex-row items-center gap-2`}>
                            <Ionicons name="shield-checkmark" size={24} color={is2FAEnabled ? "#10b981" : "#1162d4"} />
                            <Text style={tw`text-lg font-bold text-slate-900`}>İki Aşamalı Doğrulama</Text>
                        </View>
                        {is2FAEnabled && (
                            <View style={tw`bg-emerald-100 px-2 py-1 rounded-md`}>
                                <Text style={tw`text-emerald-700 text-xs font-bold`}>AKTİF</Text>
                            </View>
                        )}
                    </View>
                    <Text style={tw`text-sm text-slate-500 mt-2 leading-relaxed`}>
                        {is2FAEnabled
                            ? 'Hesabınız Authenticator Uygulaması ile korunmaktadır. Devre dışı bırakmak için mevcut parolanızı doğrulamanız gereklidir.'
                            : 'Hesabınızı korumak için şimdilik Yalnızca Authenticator Uygulaması ile 2FA(Google Auth vb.) işlemleri desteklenmektedir.'
                        }
                    </Text>

                    {is2FALoading ? (
                        <View style={tw`mt-4 py-3.5`}>
                            <Text style={tw`text-slate-400 text-center`}>Durum kontrol ediliyor...</Text>
                        </View>
                    ) : is2FAEnabled ? (
                        <View style={tw`flex-col gap-3 mt-4`}>
                            <View style={tw`relative`}>
                                <TextInput
                                    style={tw`w-full rounded-xl border border-slate-200 bg-slate-50 text-slate-900 px-4 py-3 pr-12 text-sm max-h-12`}
                                    placeholder="Devre dışı bırakmak için parolanız"
                                    placeholderTextColor="#94a3b8"
                                    secureTextEntry={secureText.verification}
                                    value={verificationPassword}
                                    onChangeText={setVerificationPassword}
                                />
                                <TouchableOpacity style={tw`absolute right-3 top-2.5`} onPress={() => toggleSecure('verification')}>
                                    <Ionicons name={secureText.verification ? "eye-off-outline" : "eye-outline"} size={20} color="#94a3b8" />
                                </TouchableOpacity>
                            </View>

                            <TouchableOpacity
                                style={tw`w-full bg-red-50 flex-row items-center justify-between py-3.5 px-4 rounded-xl shadow-sm border border-red-100 ${isLoading ? 'opacity-50' : ''}`}
                                onPress={handleDisable2FA}
                                disabled={isLoading}
                            >
                                <Text style={tw`text-red-600 font-bold text-base`}>2FA Devre Dışı Bırak</Text>
                                <Ionicons name="trash-outline" size={20} color="#dc2626" />
                            </TouchableOpacity>
                        </View>
                    ) : (
                        <TouchableOpacity
                            style={tw`mt-4 w-full bg-slate-100 flex-row items-center justify-between py-3.5 px-4 rounded-xl shadow-sm`}
                            onPress={() => navigation.navigate('TwoFactorSetupSelection')}
                        >
                            <Text style={tw`text-slate-700 font-bold text-base`}>2FA Ayarla (Uygulama İle)</Text>
                            <Ionicons name="chevron-forward" size={20} color="#64748b" />
                        </TouchableOpacity>
                    )}
                </View>

                <View style={tw`flex-row items-center gap-3 p-4 bg-orange-50 rounded-xl border border-orange-100 mt-2`}>
                    <Ionicons name="warning" size={24} color="#ea580c" />
                    <Text style={tw`flex-1 text-sm text-orange-800 leading-tight`}>
                        Parolanızı unuttuysanız, çıkış yaptıktan sonra giriş ekranındaki "Parolamı Unuttum" adımını kullanabilirsiniz.
                    </Text>
                </View>
            </ScrollView>
        </KeyboardAvoidingView>
    );
};
