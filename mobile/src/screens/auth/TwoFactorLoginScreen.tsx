import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useAuth } from '../../context/AuthContext';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { AppHeader, Button } from '../../components/ui';

type ParamList = {
    TwoFactorLogin: { tempToken: string };
};

export const TwoFactorLoginScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<RouteProp<ParamList, 'TwoFactorLogin'>>();
    const { tempToken } = route.params;
    const { signIn } = useAuth();
    const { t } = useI18n();
    const c = theme.colors;
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
            const active = code.length === i;
            boxes.push(
                <View
                    key={i}
                    style={{
                        width: isRecoveryMode ? 35 : 48,
                        height: 56,
                        marginHorizontal: isRecoveryMode ? 2 : 4,
                        backgroundColor: c.surface,
                        borderWidth: 2,
                        borderColor: active ? c.primary : c.border,
                        borderRadius: theme.borderRadius.lg,
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <Text style={{ fontSize: isRecoveryMode ? 20 : 24, fontWeight: '700', color: c.text }}>
                        {code[i] || (active ? '' : '·')}
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
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'bottom']}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('auth.twoFactor.title')} onBack={() => navigation.goBack()} />
            </View>

            <ScrollView
                contentContainerStyle={{ flexGrow: 1, alignItems: 'center', paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.lg, width: '100%', maxWidth: 420, alignSelf: 'center' }}
            >
                <View
                    style={{
                        marginBottom: theme.spacing.xl, width: 88, height: 88, borderRadius: 44,
                        backgroundColor: c.primaryTint, alignItems: 'center', justifyContent: 'center',
                    }}
                >
                    <MaterialIcons name="security" size={48} color={c.primary} />
                </View>

                <View style={{ alignItems: 'center', marginBottom: theme.spacing.xl }}>
                    <Text style={{ fontSize: 24, fontWeight: '700', marginBottom: 10, color: c.text, textAlign: 'center' }}>
                        {isRecoveryMode ? t('auth.twoFactor.recoveryCode') : t('auth.twoFactor.verificationCode')}
                    </Text>
                    <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', lineHeight: 21, maxWidth: 300 }}>
                        {isRecoveryMode
                            ? t('auth.twoFactor.recoveryDesc')
                            : t('auth.twoFactor.verificationDesc')}
                    </Text>
                </View>

                <View style={{ flexDirection: 'row', justifyContent: 'center', width: '100%', marginBottom: theme.spacing.xl }}>
                    {renderInputBoxes()}
                </View>

                <View style={{ width: '100%', gap: 16, marginTop: 'auto', marginBottom: theme.spacing.sm }}>
                    <Button
                        title={isLoading ? t('auth.twoFactor.waiting') : t('auth.twoFactor.signIn')}
                        loading={isLoading}
                        onPress={handleVerify}
                    />
                    <TouchableOpacity
                        onPress={() => {
                            setIsRecoveryMode(!isRecoveryMode);
                            setCode('');
                        }}
                        style={{ paddingVertical: 8 }}
                    >
                        <Text style={{ color: c.primary, textAlign: 'center', fontWeight: '600', fontSize: 14 }}>
                            {isRecoveryMode ? t('auth.twoFactor.useAppCode') : t('auth.twoFactor.useRecoveryCode')}
                        </Text>
                    </TouchableOpacity>
                </View>
            </ScrollView>

            <View
                style={{
                    backgroundColor: c.surfaceAlt, paddingTop: theme.spacing.md, paddingBottom: theme.spacing.xl,
                    paddingHorizontal: theme.spacing.md, borderTopWidth: 1, borderTopColor: c.border, width: '100%',
                }}
            >
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', maxWidth: 320, marginHorizontal: 'auto' }}>
                    {padKeys.map((key, index) => {
                        if (key.val === 'empty') return <View key={index} style={{ width: '30%', height: 48, marginBottom: 12 }} />;
                        if (key.val === 'backspace') {
                            return (
                                <TouchableOpacity
                                    key={index}
                                    onPress={() => handleKeyPress('backspace')}
                                    style={{ width: '30%', height: 48, alignItems: 'center', justifyContent: 'center', marginBottom: 12, borderRadius: theme.borderRadius.md }}
                                >
                                    <Ionicons name="backspace-outline" size={28} color={c.textSecondary} />
                                </TouchableOpacity>
                            );
                        }
                        return (
                            <TouchableOpacity
                                key={index}
                                onPress={() => handleKeyPress(key.val)}
                                style={{
                                    width: '30%', height: 56, alignItems: 'center', justifyContent: 'center',
                                    backgroundColor: c.surface, borderRadius: theme.borderRadius.lg, marginBottom: 12,
                                    ...theme.shadows.card,
                                }}
                            >
                                <Text style={{ fontSize: 24, fontWeight: '500', color: c.text }}>{key.val}</Text>
                                {key.letters ? (
                                    <Text style={{ fontSize: 10, letterSpacing: 2, color: c.textTertiary }}>{key.letters}</Text>
                                ) : null}
                            </TouchableOpacity>
                        );
                    })}
                </View>
                <View style={{ width: 128, height: 4, backgroundColor: c.borderStrong, borderRadius: 2, marginHorizontal: 'auto', marginTop: theme.spacing.md }} />
            </View>
        </SafeAreaView>
    );
};
