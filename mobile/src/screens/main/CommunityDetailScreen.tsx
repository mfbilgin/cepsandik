import React, { useLayoutEffect, useState, useEffect, useCallback } from 'react';
import { View, Text, TouchableOpacity, SafeAreaView, ScrollView, ActivityIndicator, RefreshControl, ImageBackground, StyleSheet, StatusBar } from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import tw from 'twrnc';
import { api } from '../../services/api';

export const CommunityDetailScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { id } = route.params || {};

    const [community, setCommunity] = useState<any>(null);
    const [elections, setElections] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [activeTab, setActiveTab] = useState('Aktif Seçimler');

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const fetchData = async () => {
        try {
            const commRes = await api.get(`/communities/${id}`);
            setCommunity(commRes.data?.data || null);

            try {
                const electionsRes = await api.get(`/elections/community/${id}?size=50`);
                setElections(electionsRes.data?.data?.content || electionsRes.data?.data || []);
            } catch (electionError) {
                console.error('Elections fetch failed:', electionError);
                setElections([]);
            }
        } catch (error) {
            console.error('Failed to fetch community details:', error);
            setCommunity(null);
        } finally {
            setIsLoading(false);
            setRefreshing(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, [id]);

    const onRefresh = useCallback(() => {
        setRefreshing(true);
        fetchData();
    }, [id]);

    const tabs = ['Aktif Seçimler', 'Arşiv', 'Üyeler', 'Hakkında'];

    if (isLoading) {
        return (
            <SafeAreaView style={tw`flex-1 bg-[#f6f7f8] justify-center items-center`}>
                <ActivityIndicator size="large" color="#1162d4" />
                <Text style={tw`text-slate-500 mt-4 font-medium`}>Topluluk yükleniyor...</Text>
            </SafeAreaView>
        );
    }

    if (!community) {
        return (
            <SafeAreaView style={tw`flex-1 bg-[#f6f7f8]`}>
                <View style={tw`flex-row items-center px-4 py-4 bg-white border-b border-slate-200`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full hover:bg-slate-100`}>
                        <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold text-slate-900 ml-2`}>Topluluk Bulunamadı</Text>
                </View>
                <View style={tw`flex-1 items-center justify-center p-6`}>
                    <MaterialIcons name="error-outline" size={64} color="#334155" />
                    <Text style={tw`mt-4 text-lg text-slate-500 text-center`}>Bu topluluğa ulaşılamıyor veya silinmiş olabilir.</Text>
                </View>
            </SafeAreaView>
        );
    }

    return (
        <View style={tw`flex-1 bg-slate-50 relative`}>
            <StatusBar barStyle="dark-content" backgroundColor="#ffffff" translucent />

            {/* Top Navigation Bar - Sticky */}
            <View style={[tw`absolute top-0 left-0 right-0 z-30 px-4 bg-white/95 border-b border-slate-100 flex-row items-center justify-between`, { paddingTop: 48, paddingBottom: 8 }]}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full active:bg-slate-100`}>
                    <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <Text style={tw`text-slate-900 text-lg font-bold text-center flex-1`} numberOfLines={1}>Community</Text>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full active:bg-slate-100`}>
                    <MaterialIcons name="more-vert" size={24} color="#0f172a" />
                </TouchableOpacity>
            </View>

            <ScrollView
                contentContainerStyle={tw`pb-24 pt-[96px]`} // Padding top offsets the absolute header
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#1162d4']} />}
                showsVerticalScrollIndicator={false}
            >
                {/* Hero Section with Cover Image */}
                <ImageBackground
                    source={{ uri: 'https://images.unsplash.com/photo-1557683316-973673baf926?q=80&w=2029&auto=format&fit=crop' }}
                    style={tw`w-full h-40 bg-slate-200`}
                >
                    <View style={tw`absolute inset-0 bg-black/40`} />
                </ImageBackground>

                {/* Community Info Card */}
                <View style={tw`px-4 pb-4 -mt-12 mb-2 relative z-10`}>
                    <View style={tw`flex-col items-center`}>
                        {/* Avatar */}
                        <View style={tw`relative`}>
                            <ImageBackground
                                source={{ uri: 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?q=80&w=2070&auto=format&fit=crop' }}
                                style={tw`w-24 h-24 rounded-full border-4 border-white bg-white shadow-md items-center justify-center overflow-hidden`}
                                imageStyle={tw`w-full h-full rounded-full`}
                            >
                                {/* Fallback Icon if image fails or before load */}
                                <MaterialIcons name="groups" size={40} color="#cbd5e1" style={tw`opacity-50`} />
                            </ImageBackground>
                            <View style={tw`absolute bottom-1 right-1 bg-green-500 rounded-full p-1 border-2 border-white items-center justify-center`}>
                                <MaterialIcons name="check" size={14} color="white" style={tw`font-bold`} />
                            </View>
                        </View>

                        {/* Title & Stats */}
                        <View style={tw`mt-3 items-center w-full`}>
                            <Text style={tw`text-2xl font-bold text-slate-900 tracking-tight text-center`} numberOfLines={1}>{community.name}</Text>
                            <View style={tw`flex-row items-center justify-center gap-2 mt-1 text-slate-500`}>
                                <MaterialIcons name="group" size={16} color="#64748b" />
                                <Text style={tw`text-sm font-medium`}>{community.memberCount || '2,450'} Üye</Text>
                                <View style={tw`w-1 h-1 rounded-full bg-slate-300`} />
                                <Text style={tw`text-sm font-medium text-[#1162d4]`}>Onaylı</Text>
                            </View>
                        </View>

                        {/* Action Buttons */}
                        <View style={tw`flex-row gap-3 w-full mt-5`}>
                            <TouchableOpacity style={tw`flex-1 h-10 bg-[#1162d4] rounded-lg items-center justify-center flex-row gap-2 shadow-sm`}>
                                <MaterialIcons name="how-to-reg" size={18} color="white" />
                                <Text style={tw`text-white text-sm font-semibold`}>Topluluğa Katıl</Text>
                            </TouchableOpacity>
                            <TouchableOpacity style={tw`w-10 h-10 items-center justify-center border border-slate-200 rounded-lg bg-white`}>
                                <MaterialIcons name="notifications-none" size={20} color="#475569" />
                            </TouchableOpacity>
                            <TouchableOpacity style={tw`w-10 h-10 items-center justify-center border border-slate-200 rounded-lg bg-white`}>
                                <MaterialIcons name="share" size={20} color="#475569" />
                            </TouchableOpacity>
                        </View>
                    </View>
                </View>

                {/* Sticky Tab Navigation */}
                <View style={tw`bg-white border-b border-t border-slate-100 flex-row mb-4`}>
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={tw`flex-grow px-2`}>
                        {tabs.map((tab) => {
                            const isActive = activeTab === tab;
                            return (
                                <TouchableOpacity
                                    key={tab}
                                    onPress={() => setActiveTab(tab)}
                                    style={tw`items-center px-4 py-3 relative min-w-[80px]`}
                                >
                                    <Text style={tw`text-sm ${isActive ? 'font-bold text-[#1162d4]' : 'font-medium text-slate-500'}`}>{tab}</Text>
                                    {isActive && (
                                        <View style={tw`absolute bottom-0 h-0.5 w-[100%] bg-[#1162d4] rounded-t-full`} />
                                    )}
                                </TouchableOpacity>
                            );
                        })}
                    </ScrollView>
                </View>

                {/* Main Content Area */}
                <View style={tw`px-4`}>
                    {activeTab === 'Aktif Seçimler' ? (
                        <>
                            <View style={tw`flex-row items-center justify-between mb-4`}>
                                <Text style={tw`text-slate-900 text-lg font-bold`}>Seçimler</Text>
                                <View style={tw`bg-[#1162d4]/10 rounded-full px-2 py-1`}>
                                    <Text style={tw`text-xs font-medium text-[#1162d4]`}>{elections.length} Devam Ediyor</Text>
                                </View>
                            </View>

                            {elections.length === 0 ? (
                                <View style={tw`bg-white p-6 rounded-xl border border-slate-100 items-center justify-center shadow-sm`}>
                                    <MaterialIcons name="inventory-2" size={40} color="#cbd5e1" />
                                    <Text style={tw`mt-2 text-slate-500 text-sm font-medium`}>Bu toplulukta henüz seçim yok.</Text>
                                </View>
                            ) : (
                                elections.map((election: any) => {
                                    const isActive = election.status === 'ACTIVE';
                                    const isScheduled = election.status === 'SCHEDULED';

                                    return (
                                        <TouchableOpacity
                                            key={election.id}
                                            onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                            activeOpacity={0.9}
                                            style={tw`bg-white rounded-xl p-4 shadow-sm border border-slate-100 mb-4 opacity-${isScheduled ? '90' : '100'}`}
                                        >
                                            {/* Status Header */}
                                            <View style={tw`flex-row justify-between items-start mb-3`}>
                                                <View style={tw`flex-row items-center gap-2`}>
                                                    <View style={tw`w-2 h-2 rounded-full ${isActive ? 'bg-green-500' : isScheduled ? 'bg-amber-500' : 'bg-slate-500'}`} />
                                                    <Text style={tw`text-[11px] font-bold ${isActive ? 'text-green-600' : isScheduled ? 'text-amber-600' : 'text-slate-600'} uppercase tracking-wide`}>
                                                        {isActive ? 'Voting Open' : isScheduled ? 'Upcoming' : 'Closed'}
                                                    </Text>
                                                </View>
                                                <View style={tw`flex-row items-center bg-slate-100 px-2 py-0.5 rounded`}>
                                                    <MaterialIcons name="lock" size={14} color="#64748b" style={tw`mr-1`} />
                                                    <Text style={tw`text-[10px] text-slate-500 font-bold tracking-wide`}>ENCRYPTED</Text>
                                                </View>
                                            </View>

                                            {/* Content */}
                                            <Text style={tw`text-slate-900 font-bold text-lg mb-1 leading-tight`}>{election.title}</Text>
                                            <Text style={tw`text-slate-500 text-sm leading-snug mb-4`} numberOfLines={2}>
                                                {election.description || 'Katılmak ve oy kullanmak için detayları inceleyin.'}
                                            </Text>

                                            {/* Footer */}
                                            <View style={tw`flex-row items-center justify-between pt-3 border-t border-slate-100`}>
                                                <View style={tw`flex-col`}>
                                                    <Text style={tw`text-xs text-slate-400 font-medium`}>{isActive ? 'Bitiş:' : 'Başlangıç:'}</Text>
                                                    <Text style={tw`text-sm font-bold text-slate-900 font-mono`}>
                                                        {isActive ? new Date(election.endDate).toLocaleString('en-US', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : new Date(election.startDate).toLocaleString('en-US', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                                                    </Text>
                                                </View>
                                                <View style={tw`${isActive ? 'bg-[#1162d4]' : 'bg-slate-100'} px-4 py-2 rounded-lg`}>
                                                    <Text style={tw`${isActive ? 'text-white' : 'text-slate-500'} text-sm font-semibold`}>
                                                        {isActive ? 'Vote Now' : 'Coming Soon'}
                                                    </Text>
                                                </View>
                                            </View>
                                        </TouchableOpacity>
                                    );
                                })
                            )}

                            {/* ElectionGuard Security Badge */}
                            {elections.length > 0 && (
                                <View style={tw`flex-row items-center justify-center gap-2 mt-4 mb-8 opacity-60`}>
                                    <MaterialIcons name="security" size={18} color="#94a3b8" />
                                    <Text style={tw`text-xs text-slate-400 font-medium`}>Secured by ElectionGuard</Text>
                                </View>
                            )}
                        </>
                    ) : (
                        <View style={tw`bg-white p-6 rounded-xl border border-slate-100 items-center justify-center shadow-sm`}>
                            <MaterialIcons name="construction" size={40} color="#cbd5e1" />
                            <Text style={tw`mt-3 text-slate-500 text-sm font-medium text-center`}>{activeTab} bölümü yapım aşamasındadır.</Text>
                        </View>
                    )}
                </View>

            </ScrollView>
        </View>
    );
};
