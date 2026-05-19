import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, RefreshControl } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, EmptyState } from '../../components/ui';

export const NotificationInboxScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const [refreshing, setRefreshing] = useState(false);
    const c = theme.colors;

    const onRefresh = React.useCallback(async () => {
        setRefreshing(true);
        setTimeout(() => setRefreshing(false), 1000);
    }, []);

    const notifications = [
        { id: '1', title: t('inbox.mock1Title'), message: t('inbox.mock1Body'), time: t('inbox.mockTime1'), isRead: false },
        { id: '2', title: t('inbox.mock2Title'), message: t('inbox.mock2Body'), time: t('inbox.mockTime2'), isRead: true },
    ];

    return (
        <Screen scroll={false} padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('inbox.title')} onBack={() => navigation.goBack()} />
            </View>

            <ScrollView
                style={{ flex: 1 }}
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 24, gap: 10 }}
                showsVerticalScrollIndicator={false}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={c.primary} colors={[c.primary]} />}
            >
                {notifications.length === 0 ? (
                    <EmptyState
                        icon="notifications-off-outline"
                        title={t('inbox.emptyTitle')}
                        subtitle={t('inbox.emptyBody')}
                    />
                ) : (
                    notifications.map((item) => (
                        <TouchableOpacity
                            key={item.id}
                            activeOpacity={0.7}
                            style={{
                                flexDirection: 'row', padding: 14, borderRadius: theme.borderRadius.lg,
                                backgroundColor: c.surface, borderWidth: 1,
                                borderColor: item.isRead ? c.border : c.primary,
                                gap: 14, ...theme.shadows.card,
                            }}
                        >
                            <View
                                style={{
                                    width: 44, height: 44, borderRadius: 22,
                                    backgroundColor: item.isRead ? c.surfaceAlt : c.primaryTint,
                                    alignItems: 'center', justifyContent: 'center',
                                }}
                            >
                                <Ionicons name="notifications" size={22} color={item.isRead ? c.textTertiary : c.primary} />
                            </View>
                            <View style={{ flex: 1 }}>
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 3 }}>
                                    <Text style={{ fontSize: 15, fontWeight: '700', color: c.text, flex: 1 }}>
                                        {item.title}
                                    </Text>
                                    <Text style={{ fontSize: 12, color: c.textTertiary, marginLeft: 8 }}>{item.time}</Text>
                                </View>
                                <Text style={{ fontSize: 13, color: item.isRead ? c.textSecondary : c.text, lineHeight: 18 }}>
                                    {item.message}
                                </Text>
                            </View>
                        </TouchableOpacity>
                    ))
                )}
            </ScrollView>
        </Screen>
    );
};
