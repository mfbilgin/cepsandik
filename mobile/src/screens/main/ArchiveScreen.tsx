import React, { useEffect, useState, useLayoutEffect, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, TextInput, RefreshControl } from 'react-native';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import tw from 'twrnc';

export const ArchiveScreen = () => {
    const [archived, setArchived] = useState<any[]>([]);
    const [filteredArchived, setFilteredArchived] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [sortAsc, setSortAsc] = useState(false); // false = newest first, true = oldest first
    const navigation = useNavigation<any>();

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const fetchArchive = async () => {
        try {
            const res = await api.get('/elections/my/history');
            const data = res.data?.data || [];
            setArchived(data);
            applyFilters(data, searchQuery, sortAsc);
        } catch (e) {
            console.error(e);
        }
    };

    const initialFetch = async () => {
        setIsLoading(true);
        await fetchArchive();
        setIsLoading(false);
    };

    const onRefresh = useCallback(async () => {
        setRefreshing(true);
        await fetchArchive();
        setRefreshing(false);
    }, [searchQuery, sortAsc]);

    useEffect(() => {
        initialFetch();
    }, []);

    const applyFilters = (data: any[], query: string, ascending: boolean) => {
        let result = [...data];
        if (query) {
            const lowerQuery = query.toLowerCase();
            result = result.filter(item => item.electionTitle?.toLowerCase().includes(lowerQuery));
        }

        result.sort((a, b) => {
            const dateA = new Date(a.endTime).getTime();
            const dateB = new Date(b.endTime).getTime();
            return ascending ? dateA - dateB : dateB - dateA;
        });

        setFilteredArchived(result);
    };

    useEffect(() => {
        applyFilters(archived, searchQuery, sortAsc);
    }, [searchQuery, sortAsc, archived]);

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            {/* Header / Navigation */}
            <View style={tw`bg-white border-b border-slate-200 px-5 pt-14 pb-4 shadow-sm z-30`}>
                <View style={tw`flex-row items-center justify-between mb-4`}>
                    <Text style={tw`text-3xl font-bold tracking-tight text-slate-900`}>Seçim Geçmişi</Text>
                    <TouchableOpacity
                        style={tw`p-2 rounded-full ${sortAsc ? 'bg-[#1162d4]/10' : 'bg-slate-50'}`}
                        onPress={() => setSortAsc(!sortAsc)}
                        activeOpacity={0.7}
                    >
                        <Ionicons name={sortAsc ? "arrow-up" : "arrow-down"} size={22} color={sortAsc ? "#1162d4" : "#475569"} />
                    </TouchableOpacity>
                </View>

                {/* Search Bar */}
                <View style={tw`relative flex-row items-center`}>
                    <View style={tw`absolute left-3 z-10`}>
                        <Ionicons name="search" size={20} color="#94a3b8" />
                    </View>
                    <TextInput
                        style={tw`flex-1 pl-10 pr-10 py-3 rounded-xl bg-slate-100 text-slate-900 border-none items-center shadow-sm`}
                        placeholder="Geçmiş seçimlerde ara..."
                        placeholderTextColor="#64748b"
                        value={searchQuery}
                        onChangeText={setSearchQuery}
                    />
                    {searchQuery.length > 0 && (
                        <TouchableOpacity style={tw`absolute right-3 z-10`} onPress={() => setSearchQuery('')}>
                            <Ionicons name="close-circle" size={20} color="#cbd5e1" />
                        </TouchableOpacity>
                    )}
                </View>
            </View>

            {/* Main Content: Election List */}
            <ScrollView
                style={tw`flex-1`}
                contentContainerStyle={tw`px-5 pt-6 pb-24 flex-col gap-4`}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#1162d4']} />}
            >
                {isLoading ? (
                    <ActivityIndicator size="large" color="#1162d4" style={tw`mt-10`} />
                ) : filteredArchived.length === 0 ? (
                    <View style={tw`flex-col items-center justify-center pt-20 pb-8`}>
                        <View style={tw`w-20 h-20 bg-white rounded-full items-center justify-center mb-4 shadow-sm border border-slate-100`}>
                            <Ionicons name="documents-outline" size={36} color="#94a3b8" />
                        </View>
                        <Text style={tw`text-base text-slate-500 font-medium`}>
                            {searchQuery ? 'Aradığınız kriterlere uygun seçim bulunamadı.' : 'Geçmiş seçim bulunamadı.'}
                        </Text>
                    </View>
                ) : (
                    filteredArchived.map((item: any, index: number) => (
                        <TouchableOpacity
                            key={index}
                            style={tw`bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden`}
                            onPress={() => navigation.navigate('ElectionDetail', { electionId: item.electionId })}
                            activeOpacity={0.8}
                        >
                            <View style={tw`p-5 flex-row gap-4`}>
                                <View style={tw`w-12 h-12 rounded-full bg-[#1162d4]/10 items-center justify-center`}>
                                    <Ionicons name="archive" size={24} color="#1162d4" />
                                </View>
                                <View style={tw`flex-1`}>
                                    <View style={tw`flex-row justify-between items-start`}>
                                        <Text style={tw`text-base font-bold text-slate-900 flex-1 pr-2 leading-tight`} numberOfLines={2}>
                                            {item.electionTitle}
                                        </Text>
                                        <Ionicons name="chevron-forward" size={18} color="#cbd5e1" />
                                    </View>
                                    <View style={tw`flex-row items-center mt-1 mb-3`}>
                                        <Ionicons name="time-outline" size={14} color="#64748b" style={tw`mr-1`} />
                                        <Text style={tw`text-xs font-medium text-slate-500`}>
                                            Bitiş: {new Date(item.endTime).toLocaleDateString('tr-TR')}
                                        </Text>
                                    </View>
                                    <View style={tw`flex-row items-center gap-2`}>
                                        <View style={tw`flex-row items-center gap-1.5 px-2.5 py-1 rounded-full bg-green-50 border border-green-200`}>
                                            <Ionicons name="checkmark-circle" size={14} color="#16a34a" />
                                            <Text style={tw`text-xs font-bold text-green-700`}>Oy Verildi</Text>
                                        </View>
                                        <View style={tw`flex-row items-center gap-1 px-2.5 py-1 rounded-full bg-blue-50 border border-blue-200`}>
                                            <Ionicons name="shield-checkmark" size={12} color="#1162d4" />
                                            <Text style={tw`text-[10px] font-bold text-[#1162d4] uppercase tracking-wide`}>Doğrulandı</Text>
                                        </View>
                                    </View>
                                </View>
                            </View>
                        </TouchableOpacity>
                    ))
                )}

                {!isLoading && filteredArchived.length > 0 && (
                    <View style={tw`py-8 flex-col items-center justify-center opacity-60`}>
                        <View style={tw`w-12 h-12 bg-slate-200 rounded-full flex-row items-center justify-center mb-3`}>
                            <Ionicons name="checkmark-done" size={24} color="#64748b" />
                        </View>
                        <Text style={tw`text-sm font-medium text-slate-500`}>Tüm geçmiş yüklendi</Text>
                    </View>
                )}
            </ScrollView>
        </View>
    );
};
