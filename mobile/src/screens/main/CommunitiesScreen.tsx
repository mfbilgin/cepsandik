import React, { useEffect, useState, useLayoutEffect, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, ImageBackground, TextInput, RefreshControl } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Card, EmptyState } from '../../components/ui';

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
    const c = theme.colors;

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const fetchCommunities = async () => {
        try {
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

    useEffect(() => { initialFetch(); }, []);

    useEffect(() => {
        let filtered = communities;

        // Filter by tab - managed tab should only show communities where user is OWNER or ADMIN
        if (activeTab === 'managed') {
            filtered = communities.filter((x) => x.userRole === 'OWNER' || x.userRole === 'ADMIN');
        } else {
            // memberships tab shows all communities where user is a member (any role including MEMBER)
            filtered = communities.filter((x) => x.userRole); // Has any role
        }

        // Apply search filter
        if (!searchQuery) {
            setFilteredCommunities(filtered);
        } else {
            const q = searchQuery.toLowerCase();
            setFilteredCommunities(
                filtered.filter((x) =>
                    x.name?.toLowerCase().includes(q) || x.description?.toLowerCase().includes(q)),
            );
        }
    }, [searchQuery, communities, activeTab]);

    const IconBtn = ({ name, onPress }: { name: any; onPress: () => void }) => (
        <TouchableOpacity
            onPress={onPress}
            style={{
                width: 38, height: 38, borderRadius: theme.borderRadius.md,
                backgroundColor: c.surface, borderWidth: 1, borderColor: c.border,
                alignItems: 'center', justifyContent: 'center',
            }}
        >
            <Ionicons name={name} size={20} color={c.text} />
        </TouchableOpacity>
    );

    const Tab = ({ id, label }: { id: 'memberships' | 'managed'; label: string }) => {
        const active = activeTab === id;
        return (
            <TouchableOpacity
                style={{
                    flex: 1, paddingVertical: 8, borderRadius: theme.borderRadius.sm,
                    backgroundColor: active ? c.surface : 'transparent',
                    ...(active ? theme.shadows.card : {}),
                }}
                onPress={() => setActiveTab(id)}
                activeOpacity={0.8}
            >
                <Text style={{ textAlign: 'center', fontSize: 14, fontWeight: active ? '700' : '600', color: active ? c.text : c.textSecondary }}>
                    {label}
                </Text>
            </TouchableOpacity>
        );
    };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            {/* Başlık */}
            <View style={{ paddingHorizontal: theme.spacing.md, paddingBottom: theme.spacing.sm }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12, minHeight: 38 }}>
                    {!isSearchOpen ? (
                        <>
                            <Text style={{ fontSize: 24, fontWeight: '700', color: c.text }}>{t('communities.title')}</Text>
                            <View style={{ flexDirection: 'row', gap: 8 }}>
                                <IconBtn name="search" onPress={() => setIsSearchOpen(true)} />
                                <IconBtn name="add-circle-outline" onPress={() => navigation.navigate('CreateElection')} />
                                <IconBtn name="notifications-outline" onPress={() => navigation.navigate('NotificationInbox')} />
                            </View>
                        </>
                    ) : (
                        <View
                            style={{
                                flexDirection: 'row', alignItems: 'center', flex: 1,
                                backgroundColor: c.surface, borderRadius: theme.borderRadius.md,
                                paddingHorizontal: 12, borderWidth: 1, borderColor: c.borderStrong, height: 44,
                            }}
                        >
                            <Ionicons name="search" size={18} color={c.textTertiary} />
                            <TextInput
                                style={{ flex: 1, paddingHorizontal: 10, color: c.text, fontSize: 15 }}
                                placeholder={t('communities.searchPlaceholder')}
                                placeholderTextColor={c.textTertiary}
                                value={searchQuery}
                                onChangeText={setSearchQuery}
                                autoFocus
                            />
                            <TouchableOpacity onPress={() => { setIsSearchOpen(false); setSearchQuery(''); }} hitSlop={8}>
                                <Ionicons name="close-circle" size={20} color={c.textTertiary} />
                            </TouchableOpacity>
                        </View>
                    )}
                </View>

                <View
                    style={{
                        flexDirection: 'row', padding: 3, backgroundColor: c.surfaceAlt,
                        borderRadius: theme.borderRadius.md, borderWidth: 1, borderColor: c.border,
                    }}
                >
                    <Tab id="memberships" label={t('communities.tab.memberships')} />
                    <Tab id="managed" label={t('communities.tab.managed')} />
                </View>
            </View>

            <ScrollView
                style={{ flex: 1 }}
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 90, gap: 14 }}
                showsVerticalScrollIndicator={false}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={c.primary} colors={[c.primary]} />}
            >
                {isLoading ? (
                    <ActivityIndicator size="large" color={c.primary} style={{ marginTop: 40 }} />
                ) : filteredCommunities.length === 0 ? (
                    <Card>
                        <EmptyState
                            icon="people-outline"
                            title={searchQuery ? t('communities.searchEmpty') : t('communities.empty')}
                        />
                    </Card>
                ) : (
                    filteredCommunities.map((comm) => (
                        <TouchableOpacity
                            key={comm.id}
                            activeOpacity={0.85}
                            onPress={() => navigation.navigate('CommunityDetail', { id: comm.id })}
                            style={{
                                backgroundColor: c.surface, borderRadius: theme.borderRadius.lg,
                                borderWidth: 1, borderColor: c.border, overflow: 'hidden', ...theme.shadows.card,
                            }}
                        >
                            {/* Yumuşak marka banner'ı (eski ağır düz mavi yerine primaryTint) */}
                            {comm.coverImageUrl ? (
                                <ImageBackground source={{ uri: comm.coverImageUrl }} style={{ height: 80, width: '100%' }} />
                            ) : (
                                <View style={{ height: 80, width: '100%', backgroundColor: c.primaryTint }} />
                            )}

                            <View style={{ paddingHorizontal: 16, paddingBottom: 16 }}>
                                <View
                                    style={{
                                        height: 56, width: 56, borderRadius: theme.borderRadius.lg,
                                        borderWidth: 3, borderColor: c.surface, backgroundColor: c.primaryTint,
                                        alignItems: 'center', justifyContent: 'center', marginTop: -28, marginBottom: 10,
                                    }}
                                >
                                    <Text style={{ fontSize: 24, fontWeight: '800', color: c.primary }}>
                                        {comm.name?.[0]}
                                    </Text>
                                </View>

                                <Text style={{ fontSize: 18, fontWeight: '700', color: c.text }}>{comm.name}</Text>
                                <Text style={{ fontSize: 13, color: c.textSecondary, marginTop: 4, lineHeight: 19 }} numberOfLines={2}>
                                    {comm.description || t('communities.noDescription')}
                                </Text>

                                <View
                                    style={{
                                        marginTop: 14, paddingTop: 14, borderTopWidth: 1, borderTopColor: c.border,
                                        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                                    }}
                                >
                                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                                        <Ionicons name="people-outline" size={16} color={c.textSecondary} />
                                        <Text style={{ fontSize: 13, fontWeight: '600', color: c.textSecondary }}>
                                            {t('communities.member')}
                                        </Text>
                                    </View>
                                    <Ionicons name="chevron-forward" size={18} color={c.textTertiary} />
                                </View>
                            </View>
                        </TouchableOpacity>
                    ))
                )}
            </ScrollView>

            <TouchableOpacity
                style={{
                    position: 'absolute', bottom: 24, right: theme.spacing.md,
                    height: 56, width: 56, borderRadius: 28, backgroundColor: c.primary,
                    alignItems: 'center', justifyContent: 'center', ...theme.shadows.elevated,
                }}
                onPress={() => navigation.navigate('CreateCommunity')}
                activeOpacity={0.85}
            >
                <Ionicons name="add" size={28} color="#fff" />
            </TouchableOpacity>
        </SafeAreaView>
    );
};
