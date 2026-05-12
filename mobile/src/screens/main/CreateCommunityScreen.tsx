import React, { useState, useEffect } from 'react';
import { View, Text, TextInput, TouchableOpacity, KeyboardAvoidingView, ScrollView, Platform, Keyboard, Switch } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { api } from '../../services/api';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const CreateCommunityScreen = () => {
    const navigation = useNavigation();
    const { t } = useI18n();
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [isPrivate, setIsPrivate] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);

    useEffect(() => {
        const keyboardDidShowListener = Keyboard.addListener(
            Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
            () => setKeyboardVisible(true)
        );
        const keyboardDidHideListener = Keyboard.addListener(
            Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
            () => setKeyboardVisible(false)
        );

        return () => {
            keyboardDidShowListener.remove();
            keyboardDidHideListener.remove();
        };
    }, []);

    const handleCreate = async () => {
        if (!name.trim() || !description.trim()) {
            Toast.show({ type: 'error', text1: t('auth.login.missingTitle'), text2: t('createCommunity.missingBody') });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();

        try {
            await api.post('/communities', {
                name: name.trim(),
                description: description.trim(),
                visibility: isPrivate ? 'PRIVATE' : 'PUBLIC'
            });
            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('createCommunity.successBody') });
            setTimeout(() => {
                navigation.goBack();
            }, 1000);
        } catch (error: any) {
            Toast.show({
                type: 'error',
                text1: t('createCommunity.failedTitle'),
                text2: error.response?.data?.message || t('auth.register.failedBody')
            });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <KeyboardAvoidingView
            style={tw`flex-1 bg-background`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
        >
            <View style={tw`w-full bg-surface flex-1 flex-col relative`}>
                {/* Top App Bar (iOS Style) */}
                <View style={tw`flex-row items-center px-4 pt-14 pb-2 bg-surface border-b border-slate-100 z-10`}>
                    <TouchableOpacity
                        style={tw`w-10 h-10 -ml-2 items-center justify-center rounded-full`}
                        onPress={() => navigation.goBack()}
                    >
                        <Ionicons name="chevron-back" size={28} color={tw.color('primary')} />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold flex-1 text-center pr-8`}>{t('createCommunity.title')}</Text>
                </View>

                {/* Main Content (Scrollable) */}
                <ScrollView
                    contentContainerStyle={tw`flex-grow p-6 flex-col ${isKeyboardVisible ? 'pb-120' : 'pb-40'}`}
                    keyboardShouldPersistTaps="handled"
                    showsVerticalScrollIndicator={false}
                >
                    {/* Logo Uploader */}
                    <View style={tw`flex-col items-center mb-8`}>
                        <TouchableOpacity style={tw`relative`} activeOpacity={0.8} onPress={() => Toast.show({ type: 'info', text1: t('profile.comingSoonTitle'), text2: t('createCommunity.logoSoon') })}>
                            <View style={tw`w-28 h-28 rounded-full bg-slate-100 items-center justify-center border-2 border-dashed border-slate-300 overflow-hidden relative`}>
                                <Ionicons name="camera-outline" size={36} color="#94a3b8" />
                            </View>
                            <View style={tw`absolute bottom-0 right-0 bg-primary rounded-full p-1.5 shadow-md border-2 border-white items-center justify-center`}>
                                <Ionicons name="add" size={16} color="white" />
                            </View>
                        </TouchableOpacity>
                        <Text style={tw`mt-3 text-sm font-medium text-primary`}>{t('createCommunity.uploadLogo')}</Text>
                    </View>

                    {/* Form Fields */}
                    <View style={tw`flex-col gap-6`}>
                        {/* Community Name */}
                        <View style={tw`flex-col gap-1.5`}>
                            <Text style={tw`text-sm font-semibold text-slate-700`}>
                                {t('createCommunity.nameLabel')} <Text style={tw`text-red-500`}>*</Text>
                            </Text>
                            <TextInput
                                style={tw`w-full px-4 py-3.5 rounded-lg bg-slate-50 border border-slate-200 text-slate-900 text-base`}
                                placeholder={t('createCommunity.namePlaceholder')}
                                placeholderTextColor="#94a3b8"
                                value={name}
                                onChangeText={setName}
                                maxLength={50}
                            />
                        </View>

                        {/* Description */}
                        <View style={tw`flex-col gap-1.5`}>
                            <View style={tw`flex-row justify-between items-baseline`}>
                                <Text style={tw`text-sm font-semibold text-slate-700`}>{t('createCommunity.descriptionLabel')}</Text>
                                <Text style={tw`text-xs text-slate-400`}>{description.length}/300</Text>
                            </View>
                            <TextInput
                                style={tw`w-full px-4 py-3 rounded-lg bg-slate-50 border border-slate-200 text-slate-900 text-base h-28`}
                                placeholder={t('createCommunity.descriptionPlaceholder')}
                                placeholderTextColor="#94a3b8"
                                value={description}
                                onChangeText={setDescription}
                                multiline
                                textAlignVertical="top"
                                maxLength={300}
                            />
                        </View>

                        {/* Privacy Toggle Section */}
                        <View style={tw`pt-2`}>
                            <View style={tw`p-4 rounded-xl border border-slate-200 bg-surface shadow-sm`}>
                                <View style={tw`flex-row items-center justify-between mb-2`}>
                                    <View style={tw`flex-row items-center gap-3`}>
                                        <View style={tw`bg-primary/10 p-2 rounded-full`}>
                                            <Ionicons name="lock-closed" size={20} color={tw.color('primary')} />
                                        </View>
                                        <Text style={tw`font-semibold text-slate-900`}>{t('createCommunity.privateTitle')}</Text>
                                    </View>
                                    <Switch
                                        trackColor={{ false: '#cbd5e1', true: '#41431B' }}
                                        thumbColor={Platform.OS === 'ios' ? '#ffffff' : isPrivate ? '#ffffff' : '#f8fafc'}
                                        ios_backgroundColor="#cbd5e1"
                                        onValueChange={setIsPrivate}
                                        value={isPrivate}
                                    />
                                </View>
                                <Text style={tw`text-sm text-slate-500 pl-[52px] leading-relaxed`}>
                                    {t('createCommunity.privateDesc')}
                                </Text>
                            </View>
                        </View>
                    </View>
                </ScrollView>

                {/* Sticky Footer Button */}
                <View style={tw`absolute bottom-0 left-0 w-full p-4 bg-surface border-t border-slate-100 pb-8`}>
                    <TouchableOpacity
                        style={tw`w-full bg-primary flex-row items-center justify-center gap-2 py-4 rounded-xl shadow-md ${isLoading ? 'opacity-50' : ''}`}
                        onPress={handleCreate}
                        disabled={isLoading}
                        activeOpacity={0.8}
                    >
                        <Text style={tw`text-white font-bold text-base`}>{isLoading ? t('createCommunity.creating') : t('createCommunity.submit')}</Text>
                        {!isLoading && <Ionicons name="arrow-forward" size={20} color="white" />}
                    </TouchableOpacity>
                </View>
            </View>
        </KeyboardAvoidingView>
    );
};
