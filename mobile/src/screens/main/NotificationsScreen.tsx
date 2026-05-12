import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, Platform, Switch, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';
import { api } from '../../services/api';

interface Preference {
    category: string;
    channel: string;
    enabled: boolean;
}

export const NotificationsScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();

    const [preferences, setPreferences] = useState<Preference[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        fetchPreferences();
    }, []);

    const fetchPreferences = async () => {
        try {
            const response = await api.get('/users/me/notification-preferences');
            setPreferences(response.data);
        } catch (error) {
            Toast.show({ type: 'error', text1: t('security.loadError'), text2: t('auth.forgot.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    const togglePreference = (category: string, channel: string) => {
        setPreferences(prev => prev.map(p => 
            (p.category === category && p.channel === channel) 
            ? { ...p, enabled: !p.enabled } 
            : p
        ));
    };

    const handleSave = async () => {
        setIsSaving(true);
        try {
            await api.put('/users/me/notification-preferences', preferences);
            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('notifications.saveSuccess') });
            navigation.goBack();
        } catch (error) {
            Toast.show({ type: 'error', text1: t('auth.forgot.failedTitle'), text2: t('auth.register.failedBody') });
        } finally {
            setIsSaving(false);
        }
    };

    const categories = ['GUARDIAN_DUTY', 'ELECTION_INFO', 'COMMUNITY_NOTICE'];
    const channels = ['PUSH', 'EMAIL'];

    if (isLoading) {
        return (
            <View style={tw`flex-1 bg-background items-center justify-center`}>
                <ActivityIndicator size="large" color={tw.color('primary') as string} />
            </View>
        );
    }

    return (
        <View style={tw`flex-1 bg-background`}>
            <View style={tw`bg-surface border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>{t('notifications.settingsTitle')}</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 flex-col gap-6`} showsVerticalScrollIndicator={false}>
                {categories.map((cat, idx) => (
                    <View key={cat} style={tw`flex-col bg-surface rounded-2xl shadow-sm border border-slate-100 overflow-hidden mb-4`}>
                        <View style={tw`bg-slate-50 p-4 border-b border-slate-100`}>
                            <Text style={tw`text-sm font-bold text-slate-700 uppercase tracking-wider`}>
                                {t(`notifications.category.${cat}`)}
                            </Text>
                        </View>
                        
                        {channels.map(channel => {
                            const pref = preferences.find(p => p.category === cat && p.channel === channel);
                            const isEnabled = pref ? pref.enabled : true;
                            
                            return (
                                <View key={channel} style={tw`flex-row items-center justify-between p-4 ${channel === 'PUSH' ? 'border-b border-slate-100' : ''}`}>
                                    <View style={tw`flex-1 mr-4`}>
                                        <Text style={tw`text-base font-semibold text-slate-900 mb-1`}>
                                            {t(`notifications.channel.${channel}`)}
                                        </Text>
                                        <Text style={tw`text-xs text-slate-500 leading-tight`}>
                                            {channel === 'PUSH' ? t('notifications.pushDesc') : t('notifications.emailDesc')}
                                        </Text>
                                    </View>
                                    <Switch
                                        trackColor={{ false: '#cbd5e1', true: tw.color('primary') as string }}
                                        thumbColor="#ffffff"
                                        ios_backgroundColor="#cbd5e1"
                                        onValueChange={() => togglePreference(cat, channel)}
                                        value={isEnabled}
                                    />
                                </View>
                            );
                        })}
                    </View>
                ))}

                <TouchableOpacity
                    style={tw`w-full bg-primary flex-row items-center justify-center gap-2 py-4 rounded-xl shadow-sm ${isSaving ? 'opacity-50' : ''}`}
                    onPress={handleSave}
                    disabled={isSaving}
                >
                    <Text style={tw`text-white font-bold text-base`}>{isSaving ? t('notifications.saving') : t('notifications.saveChanges')}</Text>
                    {isSaving ? <ActivityIndicator size="small" color="white" /> : <Ionicons name="checkmark" size={20} color="white" />}
                </TouchableOpacity>
            </ScrollView>
        </View>
    );
};
