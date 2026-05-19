import React from 'react';
import { View, Text, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card } from '../../components/ui';

export const AboutScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    return (
        <Screen scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('about.title')} onBack={() => navigation.goBack()} />
            </View>

            <ScrollView
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 32 }}
                showsVerticalScrollIndicator={false}
            >
                <View style={{ alignItems: 'center', paddingVertical: theme.spacing.lg }}>
                    <View
                        style={{
                            width: 92, height: 92, borderRadius: theme.borderRadius.xl,
                            backgroundColor: c.primary, alignItems: 'center', justifyContent: 'center',
                            marginBottom: 14, ...theme.shadows.card,
                        }}
                    >
                        <Ionicons name="cube-outline" size={44} color="#fff" />
                    </View>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text }}>CepSandık</Text>
                    <Text style={{ fontSize: 14, color: c.textSecondary, marginTop: 4 }}>
                        {t('about.version')}
                    </Text>
                </View>

                <Card>
                    <Text style={{ fontSize: 14, color: c.textSecondary, lineHeight: 21, marginBottom: 12 }}>
                        {t('about.description1')}
                    </Text>
                    <Text style={{ fontSize: 14, color: c.textSecondary, lineHeight: 21 }}>
                        {t('about.description2')}
                    </Text>
                </Card>

                <Text style={{ fontSize: 12, color: c.textTertiary, textAlign: 'center', marginTop: 32 }}>
                    {t('about.copyright')}
                </Text>
            </ScrollView>
        </Screen>
    );
};
