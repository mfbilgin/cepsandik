import React, { useState, useCallback } from 'react';
import { View, Text, TouchableOpacity, FlatList, RefreshControl, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, EmptyState } from '../../components/ui';
import { api } from '../../services/api';

interface Notification {
    id: string;
    title: string;
    message: string;
    type: 'GUARDIAN_DUTY';
    isRead: boolean;
    electionId?: number | null;
    createdAt: string; // ISO Instant
}

interface PageResponse {
    content: Notification[];
    number: number;
    totalPages: number;
    totalElements: number;
    last: boolean;
}

const PAGE_SIZE = 20;

/**
 * Inbox ekranı — backend /v1/notifications. Pagination: scroll sonuna gelince
 * sonraki sayfayı çeker (append). Mark-as-read: bildirime dokunulduğunda
 * (okunmamışsa) PUT /read; başarısızsa UI'da geri alır. Mark-all-read header
 * sağında çoğul aksiyon olarak. Refresh control ile pull-to-refresh aktif.
 */
export const NotificationInboxScreen = () => {
    const navigation = useNavigation<any>();
    const { t, language } = useI18n();
    const c = theme.colors;

    const [items, setItems] = useState<Notification[]>([]);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fetchPage = useCallback(async (targetPage: number, mode: 'initial' | 'refresh' | 'more') => {
        try {
            if (mode === 'more') setLoadingMore(true);
            else if (mode === 'refresh') setRefreshing(true);
            else setLoading(true);
            setError(null);

            const res = await api.get<PageResponse>(`/notifications?page=${targetPage}&size=${PAGE_SIZE}`);
            const data = res.data;

            setItems(prev => (mode === 'more' ? [...prev, ...data.content] : data.content));
            setPage(data.number);
            setHasMore(!data.last);
        } catch (e: any) {
            const msg = e?.response?.data?.message || t('inbox.error');
            setError(msg);
            if (mode === 'refresh') {
                Toast.show({ type: 'error', text1: t('inbox.error'), text2: msg });
            }
        } finally {
            setLoading(false);
            setLoadingMore(false);
            setRefreshing(false);
        }
    }, [t]);

    // Ekran her odaklandığında ilk sayfayı yeniden çek — geri dönüldüğünde
    // yeni gelen bildirim varsa görünür olsun.
    useFocusEffect(useCallback(() => {
        fetchPage(0, 'initial');
    }, [fetchPage]));

    const onRefresh = useCallback(() => fetchPage(0, 'refresh'), [fetchPage]);

    const onEndReached = useCallback(() => {
        if (!loadingMore && !loading && hasMore) {
            fetchPage(page + 1, 'more');
        }
    }, [loadingMore, loading, hasMore, page, fetchPage]);

    const onPressItem = useCallback(async (item: Notification) => {
        // Optimistic update — başarısızsa geri al
        if (!item.isRead) {
            setItems(prev => prev.map(n => n.id === item.id ? { ...n, isRead: true } : n));
            try {
                await api.put(`/notifications/${item.id}/read`);
            } catch {
                setItems(prev => prev.map(n => n.id === item.id ? { ...n, isRead: false } : n));
            }
        }
        // İleride: type === GUARDIAN_DUTY ise GuardianTab'a yönlendirilebilir
    }, []);

    const onMarkAllRead = useCallback(async () => {
        const hadUnread = items.some(n => !n.isRead);
        if (!hadUnread) return;
        const snapshot = items;
        setItems(prev => prev.map(n => ({ ...n, isRead: true })));
        try {
            await api.put('/notifications/read-all');
            Toast.show({ type: 'success', text1: t('inbox.markAllAsReadSuccess') });
        } catch {
            setItems(snapshot);
            Toast.show({ type: 'error', text1: t('inbox.error') });
        }
    }, [items, t]);

    const renderItem = ({ item }: { item: Notification }) => (
        <TouchableOpacity
            activeOpacity={0.7}
            onPress={() => onPressItem(item)}
            style={{
                flexDirection: 'row',
                padding: 14,
                borderRadius: theme.borderRadius.lg,
                backgroundColor: c.surface,
                borderWidth: 1,
                borderColor: item.isRead ? c.border : c.primary,
                gap: 14,
                ...theme.shadows.card,
            }}
        >
            <View
                style={{
                    width: 44, height: 44, borderRadius: 22,
                    backgroundColor: item.isRead ? c.surfaceAlt : c.primaryTint,
                    alignItems: 'center', justifyContent: 'center',
                }}
            >
                <Ionicons
                    name={item.type === 'GUARDIAN_DUTY' ? 'shield-checkmark' : 'notifications'}
                    size={22}
                    color={item.isRead ? c.textTertiary : c.primary}
                />
            </View>
            <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 3 }}>
                    <Text style={{ fontSize: 15, fontWeight: '700', color: c.text, flex: 1 }} numberOfLines={2}>
                        {item.title}
                    </Text>
                    <Text style={{ fontSize: 11, color: c.textTertiary, marginLeft: 8 }}>
                        {formatRelative(item.createdAt, language, t)}
                    </Text>
                </View>
                <Text
                    style={{ fontSize: 13, color: item.isRead ? c.textSecondary : c.text, lineHeight: 18 }}
                    numberOfLines={3}
                >
                    {item.message}
                </Text>
            </View>
        </TouchableOpacity>
    );

    const headerRight = items.some(n => !n.isRead) ? (
        <TouchableOpacity onPress={onMarkAllRead} hitSlop={8} style={{ paddingHorizontal: 8 }}>
            <Text style={{ color: c.primary, fontWeight: '600', fontSize: 13 }}>
                {t('inbox.markAllAsRead')}
            </Text>
        </TouchableOpacity>
    ) : undefined;

    return (
        <Screen scroll={false} padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader
                    title={t('inbox.title')}
                    onBack={() => navigation.goBack()}
                    right={headerRight}
                />
            </View>

            {loading ? (
                <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
                    <ActivityIndicator size="large" color={c.primary} />
                </View>
            ) : error && items.length === 0 ? (
                <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: theme.spacing.xl }}>
                    <Ionicons name="cloud-offline-outline" size={48} color={c.textTertiary} />
                    <Text style={{ fontSize: 15, color: c.text, marginTop: 12, textAlign: 'center' }}>
                        {t('inbox.error')}
                    </Text>
                    <TouchableOpacity
                        onPress={() => fetchPage(0, 'initial')}
                        style={{
                            marginTop: 16, paddingHorizontal: 18, paddingVertical: 10,
                            borderRadius: theme.borderRadius.md, backgroundColor: c.primaryTint,
                            borderWidth: 1, borderColor: c.primary,
                        }}
                    >
                        <Text style={{ color: c.primary, fontWeight: '600' }}>{t('inbox.retry')}</Text>
                    </TouchableOpacity>
                </View>
            ) : (
                <FlatList
                    data={items}
                    keyExtractor={(i) => i.id}
                    renderItem={renderItem}
                    contentContainerStyle={{
                        paddingHorizontal: theme.spacing.md,
                        paddingBottom: 24,
                        gap: 10,
                        flexGrow: 1,
                    }}
                    refreshControl={
                        <RefreshControl
                            refreshing={refreshing}
                            onRefresh={onRefresh}
                            tintColor={c.primary}
                            colors={[c.primary]}
                        />
                    }
                    onEndReached={onEndReached}
                    onEndReachedThreshold={0.5}
                    ListEmptyComponent={
                        <EmptyState
                            icon="notifications-off-outline"
                            title={t('inbox.emptyTitle')}
                            subtitle={t('inbox.emptyBody')}
                        />
                    }
                    ListFooterComponent={
                        loadingMore ? (
                            <ActivityIndicator size="small" color={c.primary} style={{ marginVertical: 16 }} />
                        ) : null
                    }
                />
            )}
        </Screen>
    );
};

/** Basit relative time — "şimdi" / "Xd" / "Xs" / "Xg" / tarih. */
function formatRelative(iso: string, language: string, t: (k: string) => string): string {
    const ts = Date.parse(iso);
    if (Number.isNaN(ts)) return '';
    const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
    if (diffSec < 60) return language === 'en' ? 'now' : 'şimdi';
    const min = Math.floor(diffSec / 60);
    if (min < 60) return language === 'en' ? `${min}m` : `${min}d`;
    const hr = Math.floor(min / 60);
    if (hr < 24) return language === 'en' ? `${hr}h` : `${hr}s`;
    const day = Math.floor(hr / 24);
    if (day < 7) return language === 'en' ? `${day}d` : `${day}g`;
    // 1 haftadan eskiyse kısa tarih
    try {
        return new Date(ts).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR', { month: 'short', day: 'numeric' });
    } catch {
        return '';
    }
}
