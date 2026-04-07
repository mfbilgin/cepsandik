import React, { useState, useEffect } from 'react';
import {
    View, Text, TextInput, TouchableOpacity, SafeAreaView,
    ScrollView, ActivityIndicator, Platform,
    StatusBar, Alert
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';

export const CommunityManagementScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { id } = route.params || {};
    const { t } = useI18n();

    const [community, setCommunity] = useState<any>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');

    const fetchCommunity = async () => {
        try {
            const res = await api.get(`/communities/${id}`);
            const data = res.data?.data;
            setCommunity(data);
            setName(data?.name || '');
            setDescription(data?.description || '');
        } catch (e) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: t('communityManagement.loadError') });
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { fetchCommunity(); }, [id]);

    const handleSave = async () => {
        if (!name.trim()) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: t('communityManagement.nameRequired') });
            return;
        }
        setIsSaving(true);
        try {
            await api.put(`/communities/${id}`, { name: name.trim(), description: description.trim() });
            Toast.show({ type: 'success', text1: t('communityManagement.savedTitle'), text2: t('communityManagement.savedBody') });
            fetchCommunity();
        } catch (e: any) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: e.response?.data?.message || t('communityManagement.updateFail') });
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = () => {
        Alert.alert(
            t('communityManagement.deleteTitle'),
            t('communityManagement.deleteBody', { name: community?.name || '' }),
            [
                { text: t('common.cancel'), style: 'cancel' },
                {
                    text: t('communityManagement.deleteConfirm'),
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            await api.delete(`/communities/${id}`);
                            Toast.show({ type: 'success', text1: t('communityManagement.deletedTitle'), text2: t('communityManagement.deletedBody') });
                            navigation.navigate('MainTab');
                        } catch (e: any) {
                            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: e.response?.data?.message || t('communityManagement.deleteFail') });
                        }
                    }
                }
            ]
        );
    };

    return (
        <SafeAreaView style={[tw`flex-1 bg-[#f6f7f8]`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 }]}>
            {/* Header */}
            <View style={tw`flex-row items-center px-4 py-3 bg-white border-b border-slate-200`}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full`}>
                    <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <Text style={tw`flex-1 text-lg font-bold text-center text-slate-900`}>{t('communityManagement.title')}</Text>
                <TouchableOpacity
                    onPress={handleSave}
                    disabled={isSaving}
                    style={tw`bg-[#1162d4] px-4 h-8 rounded-full items-center justify-center ${isSaving ? 'opacity-50' : ''}`}
                >
                    <Text style={tw`text-white text-sm font-bold`}>{isSaving ? '...' : t('notifications.saveChanges')}</Text>
                </TouchableOpacity>
            </View>

            {isLoading ? (
                <View style={tw`flex-1 items-center justify-center`}>
                    <ActivityIndicator size="large" color="#1162d4" />
                </View>
            ) : (
                <ScrollView contentContainerStyle={tw`p-4 gap-4 pb-16`} showsVerticalScrollIndicator={false}>
                    {/* Name Change Count Badge */}
                    {community?.nameChangeCount > 0 && (
                        <View style={tw`bg-amber-50 border border-amber-200 rounded-xl p-3 flex-row items-center gap-2`}>
                            <MaterialIcons name="info" size={18} color="#d97706" />
                            <Text style={tw`text-amber-700 text-sm font-medium flex-1`}>
                                {t('communityManagement.nameChanged', { count: community.nameChangeCount })}
                            </Text>
                        </View>
                    )}

                    {/* Edit Fields */}
                    <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-4`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>{t('communityManagement.infoTitle')}</Text>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('communityManagement.nameLabel')}</Text>
                            <TextInput
                                value={name}
                                onChangeText={setName}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                placeholder={t('communityManagement.namePlaceholder')}
                                placeholderTextColor="#94a3b8"
                                maxLength={100}
                            />
                        </View>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('communityManagement.descriptionLabel')}</Text>
                            <TextInput
                                value={description}
                                onChangeText={setDescription}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium min-h-[100px]`}
                                placeholder={t('communityManagement.descriptionPlaceholder')}
                                placeholderTextColor="#94a3b8"
                                multiline
                                textAlignVertical="top"
                                maxLength={2000}
                            />
                            <Text style={tw`text-xs text-slate-400 text-right`}>{description.length}/2000</Text>
                        </View>
                    </View>

                    {/* Quick Actions */}
                    <View style={tw`bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden`}>
                        <Text style={tw`text-slate-900 font-bold text-base px-4 pt-4 pb-2`}>{t('communityManagement.quickActions')}</Text>

                        <TouchableOpacity
                            onPress={() => navigation.navigate('CreateElection', { communityId: id })}
                            style={tw`flex-row items-center gap-3 px-4 py-4 border-t border-slate-100`}
                        >
                            <View style={tw`w-10 h-10 bg-[#1162d4]/10 rounded-full items-center justify-center`}>
                                <MaterialIcons name="how-to-vote" size={20} color="#1162d4" />
                            </View>
                            <View style={tw`flex-1`}>
                                <Text style={tw`text-slate-900 font-semibold`}>{t('communityManagement.startElection')}</Text>
                                <Text style={tw`text-slate-500 text-xs`}>{t('communityManagement.startElectionSub')}</Text>
                            </View>
                            <MaterialIcons name="chevron-right" size={22} color="#94a3b8" />
                        </TouchableOpacity>
                    </View>

                    {/* Danger Zone */}
                    <View style={tw`bg-white rounded-2xl border border-red-100 shadow-sm overflow-hidden mt-4`}>
                        <Text style={tw`text-red-600 font-bold text-base px-4 pt-4 pb-2`}>{t('communityManagement.danger')}</Text>
                        <TouchableOpacity
                            onPress={handleDelete}
                            style={tw`flex-row items-center gap-3 px-4 py-4 border-t border-red-100`}
                        >
                            <View style={tw`w-10 h-10 bg-red-50 rounded-full items-center justify-center`}>
                                <MaterialIcons name="delete-forever" size={20} color="#ef4444" />
                            </View>
                            <View style={tw`flex-1`}>
                                <Text style={tw`text-red-600 font-semibold`}>{t('communityManagement.deleteCommunity')}</Text>
                                <Text style={tw`text-red-400 text-xs`}>{t('communityManagement.deleteCommunitySub')}</Text>
                            </View>
                            <MaterialIcons name="chevron-right" size={22} color="#ef4444" />
                        </TouchableOpacity>
                    </View>
                </ScrollView>
            )}
        </SafeAreaView>
    );
};
