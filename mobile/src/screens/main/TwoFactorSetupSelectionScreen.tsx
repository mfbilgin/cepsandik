import React from 'react';
import { View, Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card, Button, Badge } from '../../components/ui';

export const TwoFactorSetupSelectionScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    return (
        <Screen scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('twoFactor.setup.title')} onBack={() => navigation.goBack()} />
            </View>

            <View style={{ paddingHorizontal: theme.spacing.lg }}>
                <View style={{ alignItems: 'center', paddingVertical: theme.spacing.xl }}>
                    <View
                        style={{
                            width: 96, height: 96, borderRadius: 48, backgroundColor: c.primaryTint,
                            alignItems: 'center', justifyContent: 'center', marginBottom: theme.spacing.lg,
                        }}
                    >
                        <MaterialIcons name="shield" size={52} color={c.primary} />
                    </View>
                    <Text style={{ fontSize: 24, fontWeight: '700', textAlign: 'center', color: c.text, marginBottom: 10 }}>
                        {t('twoFactor.setup.heroTitle')}
                    </Text>
                    <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', lineHeight: 22, paddingHorizontal: 8 }}>
                        {t('twoFactor.setup.heroBody')}
                    </Text>
                </View>

                <View style={{ gap: theme.spacing.md }}>
                    <Card
                        onPress={() => navigation.navigate('TwoFactorAuthenticatorSetup')}
                        style={{ borderColor: c.primary, borderWidth: 2, backgroundColor: c.primaryTint }}
                    >
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                            <Text style={{ fontSize: 15, fontWeight: '700', color: c.text }}>{t('twoFactor.setup.authApp')}</Text>
                            <Badge label={t('twoFactor.setup.recommended')} tone="primary" />
                        </View>
                        <Text style={{ fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                            {t('twoFactor.setup.authAppDesc')}
                        </Text>
                    </Card>

                    <Card elevated={false} style={{ opacity: 0.5 }}>
                        <Text style={{ fontSize: 15, fontWeight: '700', color: c.text, marginBottom: 4 }}>
                            {t('twoFactor.setup.emailCode')}
                        </Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                            {t('twoFactor.setup.emailCodeDesc')}
                        </Text>
                    </Card>
                </View>

                <Button
                    title={t('twoFactor.setup.continue')}
                    icon="arrow-forward"
                    iconPosition="right"
                    onPress={() => navigation.navigate('TwoFactorAuthenticatorSetup')}
                    style={{ marginTop: theme.spacing.xl }}
                />
            </View>
        </Screen>
    );
};
