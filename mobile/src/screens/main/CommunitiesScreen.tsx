import React, { useEffect, useState, useLayoutEffect, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, ImageBackground, TextInput, RefreshControl } from 'react-native';
import { api } from '../../services/api';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const CommunitiesScreen = () => {
    const [communities, setCommunities] = useState<any[]>([]);
    const [filteredCommunities, setFilteredCommunities] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [activeTab, setActiveTab] = useState<'memberships' | 'managed'>('memberships');
    const [isSearchOpen, setIsSearchOpen] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const navigation = useNavigation<any>();
    const { t } = useI18n();

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const fetchCommunities = async () => {
        try {
            // we will fetch all communities for now
            const res = await api.get('/communities');
            const data = res.data?.data?.content || res.data?.data || [];
            setCommunities(data);
            setFilteredCommunities(data);
        } catch (e) {
            console.error(e);
        }
    };

    const initialFetch = async () => {
        setIsLoading(true);
        await fetchCommunities();
        setIsLoading(false);
    };

    const onRefresh = useCallback(async () => {
        setRefreshing(true);
        await fetchCommunities();
        setSearchQuery('');
        setIsSearchOpen(false);
        setRefreshing(false);
    }, []);

    useEffect(() => {
        initialFetch();
    }, []);

    useEffect(() => {
        if (!searchQuery) {
            setFilteredCommunities(communities);
        } else {
            const lowerQuery = searchQuery.toLowerCase();
            const filtered = communities.filter(c =>
                c.name?.toLowerCase().includes(lowerQuery) ||
                c.description?.toLowerCase().includes(lowerQuery)
            );
            setFilteredCommunities(filtered);
        }
    }, [searchQuery, communities]);

    return (
        <View style={tw`flex-1 bg-background relative`}>
            {/* Header Section */}
            <View style={tw`bg-surface border-b border-background pt-14 pb-3 px-5 shadow-sm z-30`}>
                <View style={tw`flex-row items-center justify-between mb-3`}>
                    {!isSearchOpen ? (
                        <>
                            <Text style={tw`text-2xl font-bold tracking-tight text-primary`}>{t('communities.title')}</Text>
                            <View style={tw`flex-row items-center gap-2`}>
                                <TouchableOpacity style={tw`p-2 rounded-full bg-secondary/20`} onPress={() => setIsSearchOpen(true)}>
                                    <Ionicons name="search" size={22} color={tw.color('primary') as string} />
                                </TouchableOpacity>
                                <TouchableOpacity style={tw`p-2 rounded-full bg-secondary/20`} onPress={() => navigation.navigate('NotificationInbox')}>
                                    <Ionicons name="notifications-outline" size={22} color={tw.color('primary') as string} />
                                </TouchableOpacity>
                            </View>
                        </>
                    ) : (
                        <View style={tw`flex-row items-center flex-1 bg-background rounded-full px-4 py-2 mt-1 mb-1 border border-primary/10`}>
                            <Ionicons name="search" size={20} color={tw.color('secondary') as string} />
                            <TextInput
                                style={tw`flex-1 h-10 px-3 text-primary text-base`}
                                placeholder={t('communities.searchPlaceholder')}
                                placeholderTextColor={tw.color('secondary') as string}
                                value={searchQuery}
                                onChangeText={setSearchQuery}
                                autoFocus
                            />
                            <TouchableOpacity onPress={() => { setIsSearchOpen(false); setSearchQuery(''); }} style={tw`p-1`}>
                                <Ionicons name="close-circle" size={20} color={tw.color('secondary') as string} />
                            </TouchableOpacity>
                        </View>
                    )}
                </View>

                {/* Segmented Control Tabs */}
                <View style={tw`flex-row p-1 bg-background rounded-lg mt-1 border border-primary/10`}>
                    <TouchableOpacity
                        style={tw`flex-1 py-2 px-3 rounded ${activeTab === 'memberships' ? 'bg-surface shadow-sm' : 'bg-transparent'}`}
                        onPress={() => setActiveTab('memberships')}
                        activeOpacity={0.8}
                    >
                        <Text style={tw`text-center text-sm ${activeTab === 'memberships' ? 'font-bold text-primary' : 'font-semibold text-textSecondary'}`}>
                            {t('communities.tab.memberships')}
                        </Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                        style={tw`flex-1 py-2 px-3 rounded ${activeTab === 'managed' ? 'bg-surface shadow-sm' : 'bg-transparent'}`}
                        onPress={() => setActiveTab('managed')}
                        activeOpacity={0.8}
                    >
                        <Text style={tw`text-center text-sm ${activeTab === 'managed' ? 'font-bold text-primary' : 'font-semibold text-textSecondary'}`}>
                            {t('communities.tab.managed')}
                        </Text>
                    </TouchableOpacity>
                </View>
            </View>

            {/* Main Content: Community List */}
            <ScrollView
                style={tw`flex-1`}
                contentContainerStyle={tw`p-5 pb-24 flex-col gap-5`}
                refreshControl={
                    <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[tw.color('primary') as string]} />
                }
            >
                {isLoading ? (
                    <ActivityIndicator size="large" color={tw.color('primary')} style={tw`mt-10`} />
                ) : filteredCommunities.length === 0 ? (
                    <View style={tw`flex-col items-center justify-center p-8 mt-10 bg-surface rounded-2xl border border-primary/10 shadow-sm`}>
                        <Ionicons name="people-outline" size={64} color={tw.color('secondary') as string} />
                        <Text style={tw`text-textSecondary font-medium mt-4 text-center`}>
                            {searchQuery ? t('communities.searchEmpty') : t('communities.empty')}
                        </Text>
                    </View>
                ) : (
                    filteredCommunities.map((comm) => (
                        <TouchableOpacity key={comm.id} style={tw`flex-col overflow-hidden bg-surface rounded-2xl shadow-sm border border-primary/10`} activeOpacity={0.8} onPress={() => navigation.navigate('CommunityDetail', { id: comm.id })}>
                            {/* Banner Image */}
                            {comm.coverImageUrl ? (
                                <ImageBackground source={{ uri: comm.coverImageUrl }} style={tw`h-24 w-full bg-primary`}>
                                    <View style={tw`absolute inset-0 bg-primary/20`} />
                                </ImageBackground>
                            ) : (
                                <View style={tw`h-24 w-full bg-primary`} />
                            )}

                            <View style={tw`flex-col p-5 pt-0`}>
                                <View style={tw`flex-row justify-between items-start`}>
                                    <View style={tw`-mt-8 mb-3`}>
                                        <View style={tw`h-16 w-16 rounded-2xl border-4 border-surface bg-background overflow-hidden shadow-sm items-center justify-center`}>
                                            <Text style={tw`text-3xl font-black text-primary`}>{comm.name?.[0]}</Text>
                                        </View>
                                    </View>
                                </View>

                                <Text style={tw`text-xl font-bold text-primary leading-tight`}>{comm.name}</Text>
                                <Text style={tw`text-sm text-textSecondary mt-1.5 leading-relaxed`} numberOfLines={2}>
                                    {comm.description || t('communities.noDescription')}
                                </Text>

                                <View style={tw`mt-4 flex-row items-center justify-between border-t border-primary/10 pt-4`}>
                                    <View style={tw`flex-row items-center gap-2`}>
                                        <View style={tw`bg-primary/10 p-1.5 rounded-lg`}>
                                            <Ionicons name="people" size={16} color={tw.color('primary') as string} />
                                        </View>
                                        <Text style={tw`text-sm font-semibold text-textSecondary`}>{t('communities.member')}</Text>
                                    </View>
                                    <View style={tw`w-8 h-8 rounded-full bg-secondary/20 items-center justify-center`}>
                                        <Ionicons name="chevron-forward" size={18} color={tw.color('primary') as string} />
                                    </View>
                                </View>
                            </View>
                        </TouchableOpacity>
                    ))
                )}
            </ScrollView>

            <TouchableOpacity
                style={tw`absolute bottom-6 right-5 h-14 w-14 bg-primary rounded-full shadow-lg items-center justify-center z-40 border-2 border-surface`}
                onPress={() => navigation.navigate('CreateCommunity')}
                activeOpacity={0.8}
            >
                <Ionicons name="add" size={28} color="white" />
            </TouchableOpacity>
        </View >
    );
};
