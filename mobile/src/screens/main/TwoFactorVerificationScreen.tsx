import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import Toast from 'react-native-toast-message';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { AppHeader, Button } from '../../components/ui';

export const TwoFactorVerificationScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { backupCodes = [] } = route.params || {};
    const { t } = useI18n();
    const c = theme.colors;
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
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: t('twoFactor.verify.codeLengthError') });
            return;
        }

        setIsLoading(true);
        try {
            await api.post('/users/me/2fa/enable', { code });
            Toast.show({ type: 'success', text1: t('auth.twoFactor.successTitle'), text2: t('twoFactor.verify.successBody') });
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
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: error.response?.data?.message || t('auth.twoFactor.errorVerify') });
        } finally {
            setIsLoading(false);
        }
    };

    const renderInputBoxes = () => {
        const boxes = [];
        for (let i = 0; i < 6; i++) {
            const active = code.length === i;
            boxes.push(
                <View
                    key={i}
                    style={{
                        width: 48, height: 56, marginHorizontal: 4, backgroundColor: c.surface,
                        borderWidth: 2, borderColor: active ? c.primary : c.border,
                        borderRadius: theme.borderRadius.lg, alignItems: 'center', justifyContent: 'center',
                    }}
                >
                    <Text style={{ fontSize: 24, fontWeight: '700', color: c.text }}>{code[i] || (active ? '' : '·')}</Text>
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
                <AppHeader title={t('twoFactor.verify.title')} onBack={() => navigation.goBack()} />
            </View>

            <ScrollView
                contentContainerStyle={{ flexGrow: 1, alignItems: 'center', paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.md, paddingBottom: theme.spacing.xl, width: '100%', maxWidth: 420, alignSelf: 'center' }}
            >
                <View
                    style={{
                        marginBottom: theme.spacing.lg, width: 80, height: 80, borderRadius: 40,
                        backgroundColor: c.primaryTint, alignItems: 'center', justifyContent: 'center',
                    }}
                >
                    <MaterialIcons name="security" size={42} color={c.primary} />
                </View>

                <View style={{ alignItems: 'center', marginBottom: theme.spacing.lg }}>
                    <Text style={{ fontSize: 24, fontWeight: '700', marginBottom: 10, color: c.text, textAlign: 'center' }}>
                        {t('twoFactor.verify.heading')}
                    </Text>
                    <Text style={{ fontSize: 14, color: c.textSecondary, textAlign: 'center', lineHeight: 20, maxWidth: 300 }}>
                        {t('twoFactor.verify.desc')}
                    </Text>
                </View>

                <View style={{ flexDirection: 'row', justifyContent: 'center', width: '100%', marginBottom: theme.spacing.lg }}>
                    {renderInputBoxes()}
                </View>

                <View style={{ width: '100%', gap: 16, marginTop: 'auto', marginBottom: theme.spacing.sm }}>
                    <Button
                        title={isLoading ? t('twoFactor.verify.loading') : t('twoFactor.verify.submit')}
                        loading={isLoading}
                        onPress={handleVerify}
                    />
                    <TouchableOpacity style={{ alignItems: 'center', paddingVertical: 8 }} onPress={() => navigation.goBack()}>
                        <Text style={{ color: c.primary, fontWeight: '600', fontSize: 14 }}>{t('twoFactor.verify.cancel')}</Text>
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
