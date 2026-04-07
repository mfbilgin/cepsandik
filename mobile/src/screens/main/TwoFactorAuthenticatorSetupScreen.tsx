import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, Platform, StatusBar, ActivityIndicator, Image, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import * as Linking from 'expo-linking';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/LanguageContext';

export const TwoFactorAuthenticatorSetupScreen = () => {
    const { user } = useAuth();
    const navigation = useNavigation<any>();
    const [isLoading, setIsLoading] = useState(true);
    const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
    const [manualKey, setManualKey] = useState<string | null>(null);
    const [backupCodes, setBackupCodes] = useState<string[]>([]);
    const [fetchError, setFetchError] = useState<string | null>(null);
    const { t } = useI18n();

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

    return (
        <SafeAreaView style={[tw`flex-1 bg-[#f6f7f8]`]}>
            <View style={tw`flex-1 w-full max-w-md mx-auto flex-col overflow-hidden`}>
                {/* Top Navigation */}
                <View style={tw`flex-row items-center px-4 py-4 justify-between bg-transparent`}>
                    <TouchableOpacity
                        onPress={() => navigation.goBack()}
                        style={tw`flex h-12 w-12 items-center justify-center shrink-0`}
                    >
                        <Ionicons name="arrow-back" size={28} color="#0f172a" />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold leading-tight tracking-tight text-slate-900 flex-1 text-center pr-12`}>
                        {t('twoFactor.setup.title')}
                    </Text>
                </View>

                {/* Progress Indicator */}
                <View style={tw`flex w-full flex-row items-center justify-center gap-3 py-4`}>
                    <View style={tw`h-2 w-2 rounded-full bg-[#1162d4]/20`} />
                    <View style={tw`h-2 w-8 rounded-full bg-[#1162d4]`} />
                    <View style={tw`h-2 w-2 rounded-full bg-[#1162d4]/20`} />
                </View>

                <ScrollView contentContainerStyle={tw`flex-grow px-6 pb-50`} showsVerticalScrollIndicator={false}>
                    {/* Header Content */}
                    <View style={tw`text-center mt-4`}>
                        <Text style={tw`text-2xl text-center font-bold leading-tight text-slate-900 mb-2`}>
                            {t('twoFactor.authSetup.title')}
                        </Text>
                        <Text style={tw`text-sm text-center font-normal leading-relaxed text-slate-600`}>
                            {t('twoFactor.authSetup.desc')}
                        </Text>
                    </View>

                    {isLoading ? (
                        <View style={tw`flex-1 items-center justify-center`}>
                            <ActivityIndicator size="large" color="#1162d4" />
                            <Text style={tw`mt-4 text-slate-500`}>{t('twoFactor.authSetup.loading')}</Text>
                        </View>
                    ) : fetchError ? (
                        <View style={tw`flex-1 items-center justify-center p-4`}>
                            <Ionicons name="warning-outline" size={64} color="#f43f5e" />
                            <Text style={tw`mt-4 text-xl font-bold text-center text-slate-800`}>{t('twoFactor.authSetup.failedTitle')}</Text>
                            <Text style={tw`mt-2 text-sm text-center text-slate-600`}>{fetchError}</Text>
                            <Text style={tw`mt-4 text-center text-xs text-slate-400 bg-slate-50 p-3 rounded-lg border border-slate-100`}>{t('twoFactor.authSetup.backendHint')}</Text>
                            <TouchableOpacity style={tw`mt-6 bg-[#1162d4]/10 p-3 px-6 rounded-xl`} onPress={() => navigation.goBack()}>
                                <Text style={tw`text-[#1162d4] font-bold`}>{t('twoFactor.authSetup.back')}</Text>
                            </TouchableOpacity>
                        </View>
                    ) : (
                        <>
                            {/* QR Code Container */}
                            {qrCodeUrl && (
                                <View style={tw`mt-8 flex-col items-center`}>
                                    <View style={tw`p-6 bg-white rounded-2xl shadow-sm border border-slate-200`}>
                                        <Image
                                            source={{ uri: qrCodeUrl.startsWith('data:image') ? qrCodeUrl : `data:image/png;base64,${qrCodeUrl}` }}
                                            style={tw`w-48 h-48`}
                                            resizeMode="contain"
                                        />
                                    </View>
                                </View>
                            )}

                            {/* Manual Entry Section */}
                            <View style={tw`mt-8 flex-col gap-3`}>
                                <Text style={tw`text-xs font-semibold uppercase tracking-wider text-center text-slate-500`}>{t('twoFactor.authSetup.cantScan')}</Text>
                                <View style={tw`flex-row items-center justify-between p-4 bg-[#1162d4]/5 rounded-xl border border-[#1162d4]/20`}>
                                    <View style={tw`flex-col`}>
                                        <Text style={tw`text-[10px] text-[#1162d4] font-bold uppercase`}>{t('twoFactor.authSetup.manualKey')}</Text>
                                        <Text style={tw`font-mono font-bold tracking-widest text-slate-900 mt-1`}>
                                            {manualKey || 'XXXX XXXX XXXX XXXX'}
                                        </Text>
                                    </View>
                                    <TouchableOpacity
                                        onPress={copyToClipboard}
                                        style={tw`flex items-center justify-center h-10 w-10 hover:bg-[#1162d4]/10 rounded-lg`}
                                    >
                                        <Ionicons name="copy-outline" size={24} color="#1162d4" />
                                    </TouchableOpacity>
                                </View>
                                <TouchableOpacity
                                    style={tw`mt-2 flex-row items-center justify-center gap-2 py-3.5 bg-slate-100 rounded-xl border border-slate-200`}
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
                                    <Ionicons name="open-outline" size={20} color="#475569" />
                                    <Text style={tw`text-slate-700 font-bold`}>{t('twoFactor.authSetup.openApp')}</Text>
                                </TouchableOpacity>
                            </View>

                            {/* Instructions List */}
                            <View style={tw`mt-8 flex-col gap-4`}>
                                <View style={tw`flex-row gap-4 items-start`}>
                                    <View style={tw`flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1162d4]`}>
                                        <Text style={tw`text-white text-xs font-bold`}>1</Text>
                                    </View>
                                    <Text style={tw`text-sm leading-snug text-slate-700 flex-1 mt-0.5`}>{t('twoFactor.authSetup.step1')}</Text>
                                </View>
                                <View style={tw`flex-row gap-4 items-start`}>
                                    <View style={tw`flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1162d4]`}>
                                        <Text style={tw`text-white text-xs font-bold`}>2</Text>
                                    </View>
                                    <Text style={tw`text-sm leading-snug text-slate-700 flex-1 mt-0.5`}>{t('twoFactor.authSetup.step2')}</Text>
                                </View>
                                <View style={tw`flex-row gap-4 items-start`}>
                                    <View style={tw`flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#1162d4]`}>
                                        <Text style={tw`text-white text-xs font-bold`}>3</Text>
                                    </View>
                                    <Text style={tw`text-sm leading-snug text-slate-700 flex-1 mt-0.5`}>{t('twoFactor.authSetup.step3')}</Text>
                                </View>
                            </View>
                        </>
                    )}
                </ScrollView>

                {/* Sticky Bottom Button */}
                {!fetchError && (
                    <View style={tw`absolute bottom-0 w-full p-6 bg-[#f6f7f8] border-t border-slate-200 z-10`}>
                        <TouchableOpacity
                            style={tw`w-full h-14 bg-[#1162d4] flex-row items-center justify-center gap-2 rounded-xl shadow-lg ${isLoading ? 'opacity-50' : ''}`}
                            onPress={() => navigation.navigate('TwoFactorVerification', { backupCodes })}
                            disabled={isLoading}
                            activeOpacity={0.8}
                        >
                            <Text style={tw`text-white font-bold text-base`}>{t('twoFactor.setup.continue')}</Text>
                            <Ionicons name="arrow-forward" size={20} color="white" />
                        </TouchableOpacity>
                        <Text style={tw`mt-4 text-center text-slate-400 text-[10px] uppercase font-bold tracking-[0.2em]`}>
                            {t('twoFactor.authSetup.secured')}
                        </Text>
                    </View>
                )}
            </View>
        </SafeAreaView>
    );
};
