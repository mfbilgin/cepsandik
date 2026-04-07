import React, { useState } from 'react';
import { View, Text, TouchableOpacity, Platform, StatusBar, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useAuth } from '../../context/AuthContext';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { useI18n } from '../../i18n/LanguageContext';

type ParamList = {
    TwoFactorLogin: { tempToken: string };
};

export const TwoFactorLoginScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<RouteProp<ParamList, 'TwoFactorLogin'>>();
    const { tempToken } = route.params;
    const { signIn } = useAuth();
    const { t } = useI18n();
    const [code, setCode] = useState<string>('');
    const [isLoading, setIsLoading] = useState(false);
    const [isRecoveryMode, setIsRecoveryMode] = useState(false);

    const maxCodeLength = isRecoveryMode ? 8 : 6;

    const handleKeyPress = (key: string) => {
        if (key === 'backspace') {
            setCode(prev => prev.slice(0, -1));
        } else if (code.length < maxCodeLength) {
            setCode(prev => prev + key);
        }
    };

    const handleVerify = async () => {
        if (code.length !== maxCodeLength) {
            Toast.show({
                type: 'error',
                text1: t('auth.twoFactor.errorTitle'),
                text2: t('auth.twoFactor.errorFillCode', {
                    length: maxCodeLength,
                    codeType: isRecoveryMode ? t('auth.twoFactor.codeTypeRecovery') : t('auth.twoFactor.codeTypeVerification'),
                }),
            });
            return;
        }

        setIsLoading(true);
        try {
            const response = await AuthService.loginWith2FA(tempToken, code);

            if (response.accessToken) {
                const userData = await AuthService.getProfile();
                await signIn(response.accessToken, response.refreshToken || null, userData);
                Toast.show({ type: 'success', text1: t('auth.twoFactor.successTitle'), text2: t('auth.twoFactor.successBody') });
            }
        } catch (error: any) {
            console.log('Failed to verify 2FA code:', error?.response?.data || error.message);
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: error.response?.data?.message || t('auth.twoFactor.errorVerify') });
        } finally {
            setIsLoading(false);
        }
    };

    const renderInputBoxes = () => {
        const boxes = [];
        for (let i = 0; i < maxCodeLength; i++) {
            boxes.push(
                <View key={i} style={[
                    tw`bg-white border-2 rounded-xl flex items-center justify-center`,
                    { width: isRecoveryMode ? 35 : 48, height: 56, marginHorizontal: isRecoveryMode ? 2 : 4 },
                    code.length === i ? tw`border-[#1162d4]` : tw`border-slate-200`
                ]}>
                    <Text style={tw`${isRecoveryMode ? 'text-xl' : 'text-2xl'} font-bold text-slate-900`}>
                        {code[i] || (code.length === i ? '' : '·')}
                    </Text>
                </View>
            );
        }
        return boxes;
    };

    const padKeys = [
        { val: '1', letters: '' }, { val: '2', letters: 'ABC' }, { val: '3', letters: 'DEF' },
        { val: '4', letters: 'GHI' }, { val: '5', letters: 'JKL' }, { val: '6', letters: 'MNO' },
        { val: '7', letters: 'PQRS' }, { val: '8', letters: 'TUV' }, { val: '9', letters: 'WXYZ' },
        { val: 'empty', letters: '' }, { val: '0', letters: '' }, { val: 'backspace', letters: '' }
    ];

    return (
        <SafeAreaView style={[tw`flex-1 bg-[#f6f7f8]`]}>
            {/* Top Navigation Bar */}
            <View style={tw`flex-row items-center justify-between px-4 py-3 bg-[#f6f7f8] border-b border-[#1162d4]/10`}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`flex items-center justify-center h-10 w-10 rounded-full hover:bg-[#1162d4]/10`}>
                    <Ionicons name="chevron-back" size={24} color="#334155" />
                </TouchableOpacity>
                <Text style={tw`text-lg font-bold tracking-tight text-slate-900`}>{t('auth.twoFactor.title')}</Text>
                <View style={tw`h-10 w-10`} />
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow flex-col items-center px-6 pt-6 max-w-md w-full self-center`}>
                {/* Icon/Logo Placeholder */}
                <View style={tw`mb-8 p-4 bg-[#1162d4]/10 rounded-full`}>
                    <MaterialIcons name="security" size={56} color="#1162d4" />
                </View>

                {/* Heading & Instructions */}
                <View style={tw`text-center mb-8 flex-col items-center`}>
                    <Text style={tw`text-2xl font-bold mb-3 text-slate-900`}>
                        {isRecoveryMode ? t('auth.twoFactor.recoveryCode') : t('auth.twoFactor.verificationCode')}
                    </Text>
                    <Text style={tw`text-slate-600 text-base text-center`}>
                        {isRecoveryMode
                            ? t('auth.twoFactor.recoveryDesc')
                            : t('auth.twoFactor.verificationDesc')}
                    </Text>
                </View>

                {/* OTP Input Fields */}
                <View style={tw`flex-row justify-between w-full gap-2 mb-8`}>
                    {renderInputBoxes()}
                </View>

                {/* Action Buttons */}
                <View style={tw`w-full flex-col gap-4 mt-auto mb-2`}>
                    <TouchableOpacity
                        style={tw`w-full bg-[#1162d4] flex-row items-center justify-center py-4 rounded-xl shadow-lg border border-transparent ${isLoading ? 'opacity-50' : ''}`}
                        onPress={handleVerify}
                        disabled={isLoading}
                        activeOpacity={0.8}
                    >
                        <Text style={tw`text-white font-bold text-base`}>{isLoading ? t('auth.twoFactor.waiting') : t('auth.twoFactor.signIn')}</Text>
                    </TouchableOpacity>

                    <TouchableOpacity
                        onPress={() => {
                            setIsRecoveryMode(!isRecoveryMode);
                            setCode('');
                        }}
                        style={tw`py-2`}
                    >
                        <Text style={tw`text-[#1162d4] text-center font-medium`}>
                            {isRecoveryMode ? t('auth.twoFactor.useAppCode') : t('auth.twoFactor.useRecoveryCode')}
                        </Text>
                    </TouchableOpacity>
                </View>
            </ScrollView>

            {/* iOS Styled Numeric Keypad */}
            <View style={tw`bg-[#e2e8f0]/50 pt-4 pb-8 px-4 border-t border-slate-300 w-full`}>
                <View style={tw`flex-row flex-wrap justify-between max-w-xs mx-auto`}>
                    {padKeys.map((key, index) => {
                        if (key.val === 'empty') return <View key={index} style={tw`w-[30%] h-12 mb-3`} />;
                        if (key.val === 'backspace') {
                            return (
                                <TouchableOpacity key={index} onPress={() => handleKeyPress('backspace')} style={tw`w-[30%] h-12 flex items-center justify-center mb-3 rounded-lg active:bg-[#1162d4]/10`}>
                                    <Ionicons name="backspace-outline" size={28} color="#475569" />
                                </TouchableOpacity>
                            );
                        }
                        return (
                            <TouchableOpacity
                                key={index}
                                onPress={() => handleKeyPress(key.val)}
                                style={tw`w-[30%] h-14 flex flex-col items-center justify-center bg-white rounded-xl shadow-sm mb-3 active:bg-slate-200`}
                            >
                                <Text style={tw`text-2xl font-medium text-slate-900`}>{key.val}</Text>
                                {key.letters ? (
                                    <Text style={tw`text-[10px] tracking-widest text-slate-500`}>{key.letters}</Text>
                                ) : null}
                            </TouchableOpacity>
                        );
                    })}
                </View>
                {/* iOS Home Indicator */}
                <View style={tw`w-32 h-1 bg-slate-400/50 rounded-full mx-auto mt-4`} />
            </View>
        </SafeAreaView>
    );
};
