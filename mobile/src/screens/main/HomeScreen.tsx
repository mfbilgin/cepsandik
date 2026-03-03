import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl, Image } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import tw from 'twrnc';

export const HomeScreen = () => {
    const { user } = useAuth();
    const navigation = useNavigation<any>();
    const [activeElections, setActiveElections] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [recentResults, setRecentResults] = useState([]);

    const fetchDashboardData = async () => {
        try {
            const [activeRes, historyRes] = await Promise.all([
                api.get('/elections/my/active'),
                api.get('/elections/my/history')
            ]);

            // Filter out elections that have already ended based on client time
            const now = new Date();
            const filteredActive = (activeRes.data?.data || []).filter((poll: any) => {
                if (!poll.endTime) return true;
                return new Date(poll.endTime) > now;
            });

            setActiveElections(filteredActive);
            setRecentResults((historyRes.data?.data || []).slice(0, 3));
        } catch (e) {
            console.error(e);
        }
    };

    const initialFetch = async () => {
        setIsLoading(true);
        await fetchDashboardData();
        setIsLoading(false);
    };

    const onRefresh = useCallback(async () => {
        setRefreshing(true);
        await fetchDashboardData();
        setRefreshing(false);
    }, []);

    useEffect(() => {
        initialFetch();
    }, []);

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            {/* Header */}
            <View style={tw`flex-row items-center justify-between px-5 pt-14 pb-4 bg-white border-b border-slate-100 z-20`}>
                <View style={tw`flex-row items-center gap-3`}>
                    <View style={tw`relative`}>
                        <View style={tw`w-11 h-11 rounded-full bg-[#1162d4] overflow-hidden items-center justify-center shadow-sm`}>
                            {user?.profileImage ? (
                                <Image
                                    source={{ uri: user.profileImage }}
                                    style={tw`w-full h-full`}
                                    resizeMode="cover"
                                />
                            ) : (
                                <Text style={tw`text-white font-bold text-lg`}>{user?.firstName?.[0]}</Text>
                            )}
                        </View>
                        <View style={tw`absolute bottom-0 right-0 w-3.5 h-3.5 bg-green-500 border-2 border-white rounded-full`} />
                    </View>
                    <View>
                        <Text style={tw`text-xs font-medium text-slate-500`}>Tekrar hoş geldiniz,</Text>
                        <Text style={tw`text-lg font-bold text-slate-900 leading-tight`}>{user?.firstName} {user?.lastName}</Text>
                    </View>
                </View>
                <TouchableOpacity
                    style={tw`p-2 rounded-full relative bg-slate-50`}
                    activeOpacity={0.7}
                    onPress={() => navigation.navigate('NotificationInbox')}
                >
                    <Ionicons name="notifications-outline" size={24} color="#475569" />
                </TouchableOpacity>
            </View>

            <ScrollView
                contentContainerStyle={tw`flex-col gap-6 pb-6 pt-4`}
                refreshControl={
                    <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#1162d4']} />
                }
            >
                {/* Security Badge */}
                <View style={tw`px-5`}>
                    <View style={tw`flex-row items-center gap-2 bg-[#1162d4]/10 px-3.5 py-1.5 rounded-full self-start border border-[#1162d4]/20 shadow-sm`}>
                        <Ionicons name="shield-checkmark" size={16} color="#1162d4" />
                        <Text style={tw`text-xs font-semibold tracking-wide uppercase text-[#1162d4]`}>Secured by ElectionGuard</Text>
                    </View>
                </View>

                {/* Active Polls Section */}
                <View style={tw`flex-col gap-4`}>
                    <View style={tw`flex-row items-center justify-between px-5`}>
                        <Text style={tw`text-xl font-bold text-slate-900`}>Aktif Seçimler</Text>
                        <TouchableOpacity onPress={() => navigation.navigate('Communities')} activeOpacity={0.6}>
                            <Text style={tw`text-sm font-semibold text-[#1162d4]`}>Tümünü Gör</Text>
                        </TouchableOpacity>
                    </View>

                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={tw`px-5 pb-2 gap-4`}>
                        {isLoading ? (
                            <ActivityIndicator size="large" color="#1162d4" style={tw`ml-2 mt-5`} />
                        ) : activeElections.length === 0 ? (
                            <View style={tw`w-[280px] bg-white rounded-2xl p-5 shadow-sm border border-slate-100 flex-col gap-3 justify-center items-center h-44`}>
                                <Ionicons name="stats-chart-outline" size={40} color="#cbd5e1" />
                                <Text style={tw`text-slate-500 font-medium`}>Aktif seçim bulunmuyor.</Text>
                            </View>
                        ) : (
                            activeElections.map((poll: any) => (
                                <View key={poll.electionId} style={tw`w-[280px] bg-white rounded-2xl p-5 shadow-sm border border-slate-100 flex-col relative overflow-hidden h-44`}>
                                    <View style={tw`absolute top-0 left-0 w-1 h-full bg-[#1162d4]`} />

                                    <View style={tw`flex-row justify-between items-start mb-3`}>
                                        <View style={tw`flex-row items-center gap-1.5 px-2.5 py-1 rounded-md bg-green-100`}>
                                            <View style={tw`w-1.5 h-1.5 rounded-full bg-green-500`} />
                                            <Text style={tw`text-[10px] font-bold uppercase tracking-wider text-green-700`}>Canlı</Text>
                                        </View>
                                        <View style={tw`flex-row items-center gap-1 bg-slate-50 px-2 py-1 rounded-md`}>
                                            <Ionicons name="time-outline" size={12} color="#64748b" />
                                            <Text style={tw`text-[10px] font-semibold text-slate-500 uppercase tracking-wide`}>
                                                {poll.endTime ? new Date(poll.endTime).toLocaleDateString('tr-TR') : 'BİLİNMİYOR'}
                                            </Text>
                                        </View>
                                    </View>

                                    <View style={tw`flex-1`}>
                                        <Text style={tw`text-base font-bold text-slate-900 leading-tight mb-1`} numberOfLines={2}>{poll.electionTitle}</Text>
                                        <Text style={tw`text-xs text-slate-500 font-medium`} numberOfLines={1}>Topluluk: {poll.communityId}</Text>
                                    </View>

                                    <View style={tw`mt-auto`}>
                                        <TouchableOpacity
                                            style={tw`w-full py-2.5 bg-[#1162d4] rounded-lg flex-row items-center justify-center gap-2 shadow-sm`}
                                            activeOpacity={0.8}
                                            onPress={() => navigation.navigate('VotingBallot', { electionId: poll.electionId })}
                                        >
                                            <Ionicons name="checkbox" size={18} color="#fff" />
                                            <Text style={tw`text-white text-sm font-semibold`}>Oy Kullan</Text>
                                        </TouchableOpacity>
                                    </View>
                                </View>
                            ))
                        )}
                    </ScrollView>
                </View>

                {/* Recent Results Section */}
                <View style={tw`px-5 mt-2`}>
                    <Text style={tw`text-xl font-bold text-slate-900 mb-4`}>Sonuçlanan Seçimler</Text>
                    <View style={tw`flex-col gap-3`}>
                        {isLoading ? (
                            <ActivityIndicator size="small" color="#1162d4" />
                        ) : recentResults.length === 0 ? (
                            <View style={tw`bg-white rounded-2xl p-6 shadow-sm border border-slate-100 items-center justify-center gap-2`}>
                                <Ionicons name="archive-outline" size={32} color="#cbd5e1" />
                                <Text style={tw`text-slate-500 text-sm font-medium`}>Sonuçlanmış seçim bulunmuyor.</Text>
                            </View>
                        ) : (
                            recentResults.map((result: any) => (
                                <TouchableOpacity
                                    key={result.electionId}
                                    style={tw`flex-row items-center justify-between p-4 bg-white rounded-2xl border border-slate-100 shadow-sm`}
                                    activeOpacity={0.7}
                                    onPress={() => navigation.navigate('ElectionDetail', { electionId: result.electionId })}
                                >
                                    <View style={tw`flex-row items-center gap-4 flex-1`}>
                                        <View style={tw`w-12 h-12 rounded-full bg-[#1162d4]/10 items-center justify-center`}>
                                            <Ionicons name="podium" size={20} color="#1162d4" />
                                        </View>
                                        <View style={tw`flex-col flex-1 pr-2`}>
                                            <Text style={tw`text-base font-bold text-slate-900 mb-1`} numberOfLines={1}>{result.electionTitle}</Text>
                                            <View style={tw`flex-row items-center gap-2`}>
                                                <View style={tw`flex-row items-center gap-1`}>
                                                    <Ionicons name="people" size={12} color="#64748b" />
                                                    <Text style={tw`text-xs font-semibold text-slate-700`}>{result.totalVotes || 0} Oy</Text>
                                                </View>
                                                <Text style={tw`text-xs text-slate-400`}>•</Text>
                                                <Text style={tw`text-xs font-medium text-slate-500`}>{new Date(result.endTime).toLocaleDateString('tr-TR')}</Text>
                                            </View>
                                        </View>
                                    </View>
                                    <View style={tw`w-8 h-8 rounded-full bg-slate-50 items-center justify-center ml-2`}>
                                        <Ionicons name="chevron-forward" size={18} color="#94a3b8" />
                                    </View>
                                </TouchableOpacity>
                            ))
                        )}
                    </View>
                </View>
            </ScrollView>
        </View>
    );
};
