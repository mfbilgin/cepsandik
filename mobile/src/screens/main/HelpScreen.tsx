import React from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import tw from 'twrnc';
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
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            <View style={tw`bg-white border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>{t('help.title')}</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 flex-col gap-6`}>
                <View style={tw`bg-[#1162d4] rounded-2xl p-6 shadow-md`}>
                    <Text style={tw`text-xl font-bold text-white mb-2`}>{t('help.heroTitle')}</Text>
                    <Text style={tw`text-blue-100 text-sm leading-relaxed`}>{t('help.heroBody')}</Text>
                </View>

                <View style={tw`flex-col gap-4`}>
                    <Text style={tw`text-lg font-bold text-slate-900 ml-1`}>{t('help.faqTitle')}</Text>
                    {faqs.map((faq, idx) => (
                        <View key={idx} style={tw`bg-white p-5 rounded-2xl shadow-sm border border-slate-100`}>
                            <Text style={tw`font-bold text-slate-800 text-base mb-2`}>{faq.q}</Text>
                            <Text style={tw`text-slate-600 text-sm leading-relaxed`}>{faq.a}</Text>
                        </View>
                    ))}
                </View>

                <TouchableOpacity style={tw`mt-4 flex-row items-center justify-center gap-2 py-4 rounded-xl border-2 border-slate-200`}>
                    <Ionicons name="mail-outline" size={20} color="#64748b" />
                    <Text style={tw`text-slate-700 font-bold text-base`}>{t('help.createSupportRequest')}</Text>
                </TouchableOpacity>
            </ScrollView>
        </View>
    );
};
