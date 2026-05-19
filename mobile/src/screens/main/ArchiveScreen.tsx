import React, { useEffect, useState, useLayoutEffect, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, TextInput, RefreshControl } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Card, Badge, EmptyState } from '../../components/ui';

export const ArchiveScreen = () => {
    const [archived, setArchived] = useState<any[]>([]);
    const [filteredArchived, setFilteredArchived] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [sortAsc, setSortAsc] = useState(false); // false = newest first, true = oldest first
    const navigation = useNavigation<any>();
    const { t, language } = useI18n();
    const c = theme.colors;

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
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            {/* Header / Navigation */}
            <View
                style={{
                    backgroundColor: c.surface, borderBottomWidth: 1, borderBottomColor: c.border,
                    paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.sm, paddingBottom: theme.spacing.md,
                    ...theme.shadows.card,
                }}
            >
                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: theme.spacing.md }}>
                    <Text style={{ fontSize: 28, fontWeight: '700', color: c.text }}>{t('archive.title')}</Text>
                    <TouchableOpacity
                        style={{
                            padding: 8, borderRadius: theme.borderRadius.round,
                            backgroundColor: sortAsc ? c.primaryTint : c.surfaceAlt,
                        }}
                        onPress={() => setSortAsc(!sortAsc)}
                        activeOpacity={0.7}
                    >
                        <Ionicons name={sortAsc ? 'arrow-up' : 'arrow-down'} size={22} color={sortAsc ? c.primary : c.textSecondary} />
                    </TouchableOpacity>
                </View>

                {/* Search Bar */}
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                    <View style={{ position: 'absolute', left: 12, zIndex: 10 }}>
                        <Ionicons name="search" size={20} color={c.textTertiary} />
                    </View>
                    <TextInput
                        style={{
                            flex: 1, paddingLeft: 40, paddingRight: 40, paddingVertical: 12,
                            borderRadius: theme.borderRadius.md, backgroundColor: c.surfaceAlt, color: c.text,
                        }}
                        placeholder={t('archive.searchPlaceholder')}
                        placeholderTextColor={c.textTertiary}
                        value={searchQuery}
                        onChangeText={setSearchQuery}
                    />
                    {searchQuery.length > 0 && (
                        <TouchableOpacity style={{ position: 'absolute', right: 12, zIndex: 10 }} onPress={() => setSearchQuery('')}>
                            <Ionicons name="close-circle" size={20} color={c.textTertiary} />
                        </TouchableOpacity>
                    )}
                </View>
            </View>

            {/* Main Content: Election List */}
            <ScrollView
                style={{ flex: 1 }}
                contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.lg, paddingBottom: 96, gap: theme.spacing.md }}
                showsVerticalScrollIndicator={false}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={c.primary} colors={[c.primary]} />}
            >
                {isLoading ? (
                    <ActivityIndicator size="large" color={c.primary} style={{ marginTop: 40 }} />
                ) : filteredArchived.length === 0 ? (
                    <EmptyState
                        icon="documents-outline"
                        title={searchQuery ? t('archive.searchEmpty') : t('archive.empty')}
                    />
                ) : (
                    filteredArchived.map((item: any, index: number) => (
                        <Card
                            key={index}
                            padding={0}
                            onPress={() => navigation.navigate('ElectionDetail', { electionId: item.electionId })}
                        >
                            <View style={{ padding: theme.spacing.lg, flexDirection: 'row', gap: theme.spacing.md }}>
                                <View
                                    style={{
                                        width: 48, height: 48, borderRadius: 24, backgroundColor: c.surfaceAlt,
                                        alignItems: 'center', justifyContent: 'center',
                                    }}
                                >
                                    <Ionicons name="archive-outline" size={22} color={c.textSecondary} />
                                </View>
                                <View style={{ flex: 1 }}>
                                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                        <Text style={{ fontSize: 15, fontWeight: '700', color: c.text, flex: 1, paddingRight: 8, lineHeight: 20 }} numberOfLines={2}>
                                            {item.electionTitle}
                                        </Text>
                                        <Ionicons name="chevron-forward" size={18} color={c.textTertiary} />
                                    </View>
                                    <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4, marginBottom: 10 }}>
                                        <Ionicons name="time-outline" size={14} color={c.textSecondary} style={{ marginRight: 4 }} />
                                        <Text style={{ fontSize: 12, fontWeight: '500', color: c.textSecondary }}>
                                            {t('archive.endDate', { date: new Date(item.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR') })}
                                        </Text>
                                    </View>
                                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                                        <Badge label={t('archive.voted')} tone="success" dot />
                                        <Badge label={t('archive.verified')} tone="primary" dot />
                                    </View>
                                </View>
                            </View>
                        </Card>
                    ))
                )}

                {!isLoading && filteredArchived.length > 0 && (
                    <View style={{ paddingVertical: 32, alignItems: 'center', justifyContent: 'center', opacity: 0.6 }}>
                        <View
                            style={{
                                width: 48, height: 48, borderRadius: 24, backgroundColor: c.surfaceAlt,
                                alignItems: 'center', justifyContent: 'center', marginBottom: 12,
                            }}
                        >
                            <Ionicons name="checkmark-done" size={24} color={c.textSecondary} />
                        </View>
                        <Text style={{ fontSize: 13, fontWeight: '500', color: c.textSecondary }}>{t('archive.allLoaded')}</Text>
                    </View>
                )}
            </ScrollView>
        </SafeAreaView>
    );
};
