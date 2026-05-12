import React from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const HelpScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();

    const faqs = [
        { q: t('help.faq.1.q'), a: t('help.faq.1.a') },
        { q: t('help.faq.2.q'), a: t('help.faq.2.a') },
        { q: t('help.faq.3.q'), a: t('help.faq.3.a') },
        { q: t('help.faq.4.q'), a: t('help.faq.4.a') },
    ];

    return (
        <View style={tw`flex-1 bg-background`}>
            <View style={tw`bg-surface border-b border-background pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-secondary/20`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color={tw.color('primary') as string} />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-primary ml-4`}>{t('help.title')}</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 flex-col gap-6`}>
                <View style={tw`bg-primary rounded-2xl p-6 shadow-md`}>
                    <Text style={tw`text-xl font-bold text-white mb-2`}>{t('help.heroTitle')}</Text>
                    <Text style={tw`text-surface text-sm leading-relaxed`}>{t('help.heroBody')}</Text>
                </View>

                <View style={tw`flex-col gap-4`}>
                    <Text style={tw`text-lg font-bold text-primary ml-1`}>{t('help.faqTitle')}</Text>
                    {faqs.map((faq, idx) => (
                        <View key={idx} style={tw`bg-surface p-5 rounded-2xl shadow-sm border border-primary/10`}>
                            <Text style={tw`font-bold text-primary text-base mb-2`}>{faq.q}</Text>
                            <Text style={tw`text-textSecondary text-sm leading-relaxed`}>{faq.a}</Text>
                        </View>
                    ))}
                </View>

                <TouchableOpacity style={tw`mt-4 flex-row items-center justify-center gap-2 py-4 rounded-xl border-2 border-secondary`}>
                    <Ionicons name="mail-outline" size={20} color={tw.color('primary') as string} />
                    <Text style={tw`text-primary font-bold text-base`}>{t('help.createSupportRequest')}</Text>
                </TouchableOpacity>
            </ScrollView>
        </View>
    );
};
