import React, { useState } from 'react';
import { View, Text, Keyboard } from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Input, Button } from '../../components/ui';

export const ForgotPasswordScreen = () => {
    const [email, setEmail] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

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
        <Screen keyboardAvoiding scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('auth.forgot.title')} onBack={() => navigation.goBack()} />
            </View>

            {!isSuccess ? (
                <View style={{ paddingHorizontal: theme.spacing.lg }}>
                    <View style={{ alignItems: 'center', marginTop: 12, marginBottom: theme.spacing.xl }}>
                        <View
                            style={{
                                width: 80, height: 80, borderRadius: 40, backgroundColor: c.primaryTint,
                                alignItems: 'center', justifyContent: 'center', marginBottom: 16,
                            }}
                        >
                            <MaterialIcons name="lock-reset" size={38} color={c.primary} />
                        </View>
                        <Text style={{ fontSize: 24, fontWeight: '700', color: c.text, marginBottom: 8 }}>
                            {t('auth.forgot.title')}
                        </Text>
                        <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', lineHeight: 21, maxWidth: 280 }}>
                            {t('auth.forgot.description')}
                        </Text>
                    </View>

                    <Input
                        label="E-Posta Adresi"
                        icon="mail-outline"
                        placeholder="ornek@mail.com"
                        value={email}
                        onChangeText={setEmail}
                        keyboardType="email-address"
                        autoCapitalize="none"
                        returnKeyType="done"
                        onSubmitEditing={handleReset}
                    />

                    <Button
                        title={isLoading ? t('auth.forgot.submitLoading') : t('auth.forgot.submit')}
                        loading={isLoading}
                        onPress={handleReset}
                        style={{ marginTop: 8 }}
                    />

                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 24, opacity: 0.7 }}>
                        <Ionicons name="shield-checkmark" size={14} color={c.textTertiary} />
                        <Text style={{ fontSize: 11, fontWeight: '600', color: c.textTertiary, letterSpacing: 0.5 }}>
                            SECURED BY CEPSANDIK
                        </Text>
                    </View>
                </View>
            ) : (
                <View style={{ paddingHorizontal: theme.spacing.lg, alignItems: 'center', paddingTop: 48 }}>
                    <View
                        style={{
                            width: 92, height: 92, borderRadius: 46, backgroundColor: c.successTint,
                            alignItems: 'center', justifyContent: 'center', marginBottom: 24,
                        }}
                    >
                        <Ionicons name="checkmark-circle" size={46} color={c.success} />
                    </View>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text, marginBottom: 8, textAlign: 'center' }}>
                        {t('auth.forgot.checkEmail')}
                    </Text>
                    <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', lineHeight: 21, maxWidth: 280, marginBottom: 32 }}>
                        {t('auth.forgot.sentToEmail', { email })}
                    </Text>
                    <Button
                        title={t('auth.forgot.backToLogin')}
                        variant="secondary"
                        onPress={() => navigation.navigate('Login')}
                    />
                    <Text style={{ fontSize: 12, color: c.textSecondary, marginTop: 20 }}>
                        {t('auth.forgot.notReceived')}{' '}
                        <Text style={{ color: c.primary, fontWeight: '700' }} onPress={handleReset}>
                            {t('auth.forgot.resend')}
                        </Text>
                    </Text>
                </View>
            )}
        </Screen>
    );
};
