import React, { useState, useEffect, useCallback } from 'react';
import {
    View, Text, TextInput, TouchableOpacity, SafeAreaView,
    ScrollView, ActivityIndicator, RefreshControl, Platform,
    StatusBar, Alert, Modal, FlatList
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';

export const CommunityManagementScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { id } = route.params || {};
    const { user } = useAuth();

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
            Toast.show({ type: 'error', text1: 'Hata', text2: 'Topluluk bilgileri yüklenemedi.' });
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { fetchCommunity(); }, [id]);

    const handleSave = async () => {
        if (!name.trim()) {
            Toast.show({ type: 'error', text1: 'Hata', text2: 'Topluluk adı boş bırakılamaz.' });
            return;
        }
        setIsSaving(true);
        try {
            await api.put(`/communities/${id}`, { name: name.trim(), description: description.trim() });
            Toast.show({ type: 'success', text1: 'Kaydedildi', text2: 'Topluluk bilgileri güncellendi.' });
            fetchCommunity();
        } catch (e: any) {
            Toast.show({ type: 'error', text1: 'Hata', text2: e.response?.data?.message || 'Güncelleme başarısız.' });
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = () => {
        Alert.alert(
            'Topluluğu Sil',
            `"${community?.name}" topluluğunu silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
            [
                { text: 'İptal', style: 'cancel' },
                {
                    text: 'Evet, Sil',
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            await api.delete(`/communities/${id}`);
                            Toast.show({ type: 'success', text1: 'Silindi', text2: 'Topluluk başarıyla silindi.' });
                            navigation.navigate('MainTab');
                        } catch (e: any) {
                            Toast.show({ type: 'error', text1: 'Hata', text2: e.response?.data?.message || 'Silme işlemi başarısız.' });
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
                <Text style={tw`flex-1 text-lg font-bold text-center text-slate-900`}>Topluluğu Yönet</Text>
                <TouchableOpacity
                    onPress={handleSave}
                    disabled={isSaving}
                    style={tw`bg-[#1162d4] px-4 h-8 rounded-full items-center justify-center ${isSaving ? 'opacity-50' : ''}`}
                >
                    <Text style={tw`text-white text-sm font-bold`}>{isSaving ? '...' : 'Kaydet'}</Text>
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
                                Bu topluluğun adı <Text style={tw`font-bold`}>{community.nameChangeCount}</Text> defa değiştirilmiştir.
                            </Text>
                        </View>
                    )}

                    {/* Edit Fields */}
                    <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-4`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>Topluluk Bilgileri</Text>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>Topluluk Adı</Text>
                            <TextInput
                                value={name}
                                onChangeText={setName}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                placeholder="Topluluk adını girin..."
                                placeholderTextColor="#94a3b8"
                                maxLength={100}
                            />
                        </View>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>Açıklama</Text>
                            <TextInput
                                value={description}
                                onChangeText={setDescription}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium min-h-[100px]`}
                                placeholder="Topluluk hakkında bilgi verin..."
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
                        <Text style={tw`text-slate-900 font-bold text-base px-4 pt-4 pb-2`}>Hızlı İşlemler</Text>

                        <TouchableOpacity
                            onPress={() => navigation.navigate('CreateElection', { communityId: id })}
                            style={tw`flex-row items-center gap-3 px-4 py-4 border-t border-slate-100`}
                        >
                            <View style={tw`w-10 h-10 bg-[#1162d4]/10 rounded-full items-center justify-center`}>
                                <MaterialIcons name="how-to-vote" size={20} color="#1162d4" />
                            </View>
                            <View style={tw`flex-1`}>
                                <Text style={tw`text-slate-900 font-semibold`}>Yeni Seçim Başlat</Text>
                                <Text style={tw`text-slate-500 text-xs`}>Bu topluluk için seçim oluştur</Text>
                            </View>
                            <MaterialIcons name="chevron-right" size={22} color="#94a3b8" />
                        </TouchableOpacity>
                    </View>

                    {/* Danger Zone */}
                    <View style={tw`bg-white rounded-2xl border border-red-100 shadow-sm overflow-hidden mt-4`}>
                        <Text style={tw`text-red-600 font-bold text-base px-4 pt-4 pb-2`}>Tehlikeli Alan</Text>
                        <TouchableOpacity
                            onPress={handleDelete}
                            style={tw`flex-row items-center gap-3 px-4 py-4 border-t border-red-100`}
                        >
                            <View style={tw`w-10 h-10 bg-red-50 rounded-full items-center justify-center`}>
                                <MaterialIcons name="delete-forever" size={20} color="#ef4444" />
                            </View>
                            <View style={tw`flex-1`}>
                                <Text style={tw`text-red-600 font-semibold`}>Topluluğu Sil</Text>
                                <Text style={tw`text-red-400 text-xs`}>Bu işlem geri alınamaz</Text>
                            </View>
                            <MaterialIcons name="chevron-right" size={22} color="#ef4444" />
                        </TouchableOpacity>
                    </View>
                </ScrollView>
            )}
        </SafeAreaView>
    );
};
