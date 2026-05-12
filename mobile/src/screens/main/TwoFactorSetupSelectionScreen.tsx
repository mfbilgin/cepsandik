import React from 'react';
import { View, Text, TouchableOpacity, Platform, StatusBar } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const TwoFactorSetupSelectionScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();

    return (
        <SafeAreaView style={[tw`flex-1 bg-surface`]}>
            <View style={tw`flex-1 w-full flex-col shadow-2xl overflow-hidden`}>
                {/* Header */}
                <View style={tw`flex-row items-center px-4 py-4 border-b border-slate-100`}>
                    <TouchableOpacity
                        onPress={() => navigation.goBack()}
                        style={tw`p-1 rounded-full hover:bg-slate-100`}
                    >
                        <Ionicons name="chevron-back" size={28} color="#0f172a" />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold leading-tight tracking-tight text-slate-900 flex-1 text-center pr-8`}>
                        {t('twoFactor.setup.title')}
                    </Text>
                </View>

                <View style={tw`flex-col flex-1`}>
                    {/* Hero Illustration/Icon Section */}
                    <View style={tw`px-6 py-10 flex-col items-center`}>
                        <View style={tw`w-24 h-24 bg-primary/10 rounded-full flex items-center justify-center mb-6`}>
                            <MaterialIcons name="shield" size={56} color={tw.color('primary')} />
                        </View>
                        <Text style={tw`text-[24px] font-bold leading-tight text-center text-slate-900 mb-3`}>
                            {t('twoFactor.setup.heroTitle')}
                        </Text>
                        <Text style={tw`text-base font-normal leading-relaxed text-center text-slate-600 px-4`}>
                            {t('twoFactor.setup.heroBody')}
                        </Text>
                    </View>

                    {/* Selection Options */}
                    <View style={tw`flex-col gap-4 p-6`}>
                        {/* Option 1: Authenticator */}
                        <TouchableOpacity
                            style={tw`group relative flex-row items-start rounded-xl border-2 border-primary bg-primary/5 p-5`}
                            activeOpacity={0.8}
                            onPress={() => navigation.navigate('TwoFactorAuthenticatorSetup')} // Assuming this navigates to the next step
                        >
                            <View style={tw`flex-col grow`}>
                                <View style={tw`flex-row items-center gap-1 mb-1`}>
                                    <Text style={tw`text-base font-bold leading-normal text-slate-900`}>{t('twoFactor.setup.authApp')}</Text>
                                    <View style={tw`px-2 py-0.5 rounded-full bg-primary`}>
                                        <Text style={tw`text-white text-[10px] font-bold uppercase tracking-wider`}>{t('twoFactor.setup.recommended')}</Text>
                                    </View>
                                </View>
                                <Text style={tw`text-sm font-normal leading-normal text-slate-600 pr-4`}>
                                    {t('twoFactor.setup.authAppDesc')}
                                </Text>
                            </View>
                        </TouchableOpacity>

                        {/* Option 2: SMS/Email - Disabled for now as per design intention */}
                        <View style={tw`group relative flex-row items-start gap-4 rounded-xl border-2 border-slate-200 bg-transparent p-5 opacity-50`}>
                            <View style={tw`flex-col grow`}>
                                <View style={tw`flex-row items-center gap-2 mb-1`}>
                                    <Text style={tw`text-base font-bold leading-normal text-slate-900`}>{t('twoFactor.setup.emailCode')}</Text>
                                </View>
                                <Text style={tw`text-sm font-normal leading-normal text-slate-600 pr-4`}>
                                    {t('twoFactor.setup.emailCodeDesc')}
                                </Text>
                            </View>
                        </View>
                    </View>

                    {/* Footer / CTA */}
                    <View style={tw`mt-auto p-6`}>

                        <TouchableOpacity
                            style={tw`w-full bg-primary flex-row items-center justify-center gap-2 py-4 rounded-xl shadow-sm`}
                            onPress={() => navigation.navigate('TwoFactorAuthenticatorSetup')}
                            activeOpacity={0.8}
                        >
                            <Text style={tw`text-white font-bold text-base`}>{t('twoFactor.setup.continue')}</Text>
                            <Ionicons name="arrow-forward" size={20} color="white" />
                        </TouchableOpacity>
                    </View>
                </View>
            </View>
        </SafeAreaView>
    );
};
