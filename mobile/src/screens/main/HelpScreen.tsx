import React from 'react';
import { View, Text, ScrollView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card } from '../../components/ui';

export const HelpScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    const faqs = [
        { q: t('help.faq.1.q'), a: t('help.faq.1.a') },
        { q: t('help.faq.2.q'), a: t('help.faq.2.a') },
        { q: t('help.faq.3.q'), a: t('help.faq.3.a') },
        { q: t('help.faq.4.q'), a: t('help.faq.4.a') },
    ];

    return (
        <Screen scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('help.title')} onBack={() => navigation.goBack()} />
            </View>

            <ScrollView
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 32, gap: 14 }}
                showsVerticalScrollIndicator={false}
            >
                {/* Yumuşak marka kartı (eski ağır düz mavi yerine primaryTint) */}
                <View
                    style={{
                        backgroundColor: c.primaryTint, borderRadius: theme.borderRadius.lg,
                        padding: 20,
                    }}
                >
                    <Text style={{ fontSize: 18, fontWeight: '700', color: c.text, marginBottom: 6 }}>
                        {t('help.heroTitle')}
                    </Text>
                    <Text style={{ fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                        {t('help.heroBody')}
                    </Text>
                </View>

                <Text style={{ fontSize: 17, fontWeight: '700', color: c.text, marginTop: 4 }}>
                    {t('help.faqTitle')}
                </Text>
                {faqs.map((faq, idx) => (
                    <Card key={idx}>
                        <Text style={{ fontSize: 15, fontWeight: '700', color: c.text, marginBottom: 6 }}>
                            {faq.q}
                        </Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                            {faq.a}
                        </Text>
                    </Card>
                ))}
            </ScrollView>
        </Screen>
    );
};
