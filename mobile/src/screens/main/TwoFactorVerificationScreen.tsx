import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, Platform, StatusBar, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { api } from '../../services/api';

export const TwoFactorVerificationScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { backupCodes = [] } = route.params || {};
    const [code, setCode] = useState<string>('');
    const [isLoading, setIsLoading] = useState(false);

    const handleKeyPress = (key: string) => {
        if (key === 'backspace') {
            setCode(prev => prev.slice(0, -1));
        } else if (code.length < 6) {
            setCode(prev => prev + key);
        }
    };

    const handleVerify = async () => {
        if (code.length !== 6) {
            Toast.show({ type: 'error', text1: 'Hata', text2: 'Lütfen 6 haneli doğrulama kodunu eksiksiz girin.' });
            return;
        }

        setIsLoading(true);
        try {
            await api.post('/users/me/2fa/enable', { code });
            Toast.show({ type: 'success', text1: 'Başarılı', text2: '2FA kurulumu başarıyla tamamlandı.' });
            setTimeout(() => {
                if (backupCodes && backupCodes.length > 0) {
                    navigation.navigate('RecoveryCodes', { codes: backupCodes });
                } else {
                    navigation.reset({
                        index: 1,
                        routes: [{ name: 'MainTab' }, { name: 'SecuritySettings' }],
                    });
                }
            }, 800);
        } catch (error: any) {
            console.error('Failed to verify 2FA code', error);
            Toast.show({ type: 'error', text1: 'Hata', text2: error.response?.data?.message || 'Kod doğrulanamadı. Lütfen tekrar deneyin.' });
        } finally {
            setIsLoading(false);
        }
    };

    const renderInputBoxes = () => {
        const boxes = [];
        for (let i = 0; i < 6; i++) {
            boxes.push(
                <View key={i} style={tw`w-12 h-14 bg-white border-2 rounded-xl flex items-center justify-center ${code.length === i ? 'border-[#1162d4]' : 'border-slate-200'}`}>
                    <Text style={tw`text-2xl font-bold text-slate-900`}>{code[i] || (code.length === i ? '' : '·')}</Text>
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
        <SafeAreaView style={[tw`flex-1 bg-[#f6f7f8]`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 }]}>
            {/* Top Navigation Bar */}
            <View style={tw`flex-row items-center justify-between px-4 py-3 bg-[#f6f7f8] border-b border-[#1162d4]/10`}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`flex items-center justify-center h-10 w-10 rounded-full hover:bg-[#1162d4]/10`}>
                    <Ionicons name="chevron-back" size={24} color="#334155" />
                </TouchableOpacity>
                <Text style={tw`text-lg font-bold tracking-tight text-slate-900`}>Doğrulama</Text>
                <View style={tw`h-10 w-10`} />
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow flex-col items-center px-6 pt-4 pb-8 max-w-md w-full self-center`}>
                {/* Icon/Logo Placeholder */}
                <View style={tw`mb-6 p-4 bg-[#1162d4]/10 rounded-full`}>
                    <MaterialIcons name="security" size={48} color="#1162d4" />
                </View>

                {/* Heading & Instructions */}
                <View style={tw`text-center mb-6 flex-col items-center`}>
                    <Text style={tw`text-2xl font-bold mb-3 text-slate-900`}>2FA Doğrulaması</Text>
                    <Text style={tw`text-slate-600 text-sm text-center`}>
                        Oy hesabınızı güvenceye almak için kimlik doğrulayıcı uygulamanızdan aldığınız 6 haneli kodu girin.
                    </Text>
                </View>

                {/* OTP Input Fields */}
                <View style={tw`flex-row justify-between w-full gap-2 mb-6`}>
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
                        <Text style={tw`text-white font-bold text-base`}>{isLoading ? 'Kod Doğrulanıyor...' : 'Kimliği Doğrula'}</Text>
                    </TouchableOpacity>

                    <TouchableOpacity style={tw`items-center`} onPress={() => navigation.goBack()}>
                        <Text style={tw`text-[#1162d4] font-medium text-sm`}>Kurulumu İptal Et</Text>
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
