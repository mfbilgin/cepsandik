import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl, Image } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Card, Badge, Button, EmptyState, SectionHeader } from '../../components/ui';

export const HomeScreen = () => {
    const { user } = useAuth();
    const navigation = useNavigation<any>();
    const { t, language } = useI18n();
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

            const now = new Date();
            const filteredActive = (activeRes.data?.data || []).filter((poll: any) => {
                if (poll.status && String(poll.status).toUpperCase() !== 'ACTIVE') return false;
                if (poll.startTime && new Date(poll.startTime) > now) return false;
                if (poll.endTime && new Date(poll.endTime) <= now) return false;
                return true;
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

    useEffect(() => { initialFetch(); }, []);

    const dateFmt = (d: string) =>
        new Date(d).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR');

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: theme.colors.background }} edges={['top', 'left', 'right']}>
            {/* Dashboard başlığı — isim KOYU metin, mavi sadece marka avatarı */}
            <View
                style={{
                    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                    paddingHorizontal: theme.spacing.md, paddingBottom: theme.spacing.md,
                }}
            >
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                    <View>
                        <View
                            style={{
                                width: 44, height: 44, borderRadius: 22, overflow: 'hidden',
                                backgroundColor: theme.colors.primary,
                                alignItems: 'center', justifyContent: 'center',
                            }}
                        >
                            {user?.profileImage ? (
                                <Image source={{ uri: user.profileImage }} style={{ width: '100%', height: '100%' }} resizeMode="cover" />
                            ) : (
                                <Text style={{ color: theme.colors.onPrimary, fontWeight: '700', fontSize: 18 }}>
                                    {user?.firstName?.[0]}
                                </Text>
                            )}
                        </View>
                        <View
                            style={{
                                position: 'absolute', bottom: 0, right: 0, width: 13, height: 13,
                                borderRadius: 7, backgroundColor: theme.colors.success,
                                borderWidth: 2, borderColor: theme.colors.background,
                            }}
                        />
                    </View>
                    <View>
                        <Text style={{ fontSize: 12, fontWeight: '500', color: theme.colors.textSecondary }}>
                            {t('auth.login.welcomeBack')}
                        </Text>
                        <Text style={{ fontSize: 18, fontWeight: '700', color: theme.colors.text }}>
                            {user?.firstName} {user?.lastName}
                        </Text>
                    </View>
                </View>
                <TouchableOpacity
                    style={{
                        width: 40, height: 40, borderRadius: theme.borderRadius.md,
                        backgroundColor: theme.colors.surface, borderWidth: 1,
                        borderColor: theme.colors.border,
                        alignItems: 'center', justifyContent: 'center',
                    }}
                    activeOpacity={0.7}
                    onPress={() => navigation.navigate('NotificationInbox')}
                >
                    <Ionicons name="notifications-outline" size={22} color={theme.colors.text} />
                </TouchableOpacity>
            </View>

            <ScrollView
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 24 }}
                showsVerticalScrollIndicator={false}
                refreshControl={
                    <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.colors.primary} colors={[theme.colors.primary]} />
                }
            >
                {/* Aktif seçimler */}
                <SectionHeader
                    title={t('home.activeElections')}
                    actionLabel={t('home.seeAll')}
                    onAction={() => navigation.navigate('Communities')}
                />

                {isLoading ? (
                    <ActivityIndicator size="large" color={theme.colors.primary} style={{ marginVertical: 32 }} />
                ) : activeElections.length === 0 ? (
                    <Card>
                        <EmptyState icon="stats-chart-outline" title={t('home.noActive')} />
                    </Card>
                ) : (
                    <View style={{ gap: 12 }}>
                        {activeElections.map((poll: any) => (
                            <Card key={poll.electionId}>
                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                                    <Badge label={t('home.live')} tone="success" dot />
                                    <Text style={{ fontSize: 12, color: theme.colors.textSecondary, fontWeight: '500' }}>
                                        {poll.endTime ? dateFmt(poll.endTime) : t('common.unknownUpper')}
                                    </Text>
                                </View>
                                <Text style={{ fontSize: 16, fontWeight: '700', color: theme.colors.text, marginBottom: 2 }} numberOfLines={2}>
                                    {poll.electionTitle}
                                </Text>
                                <Text style={{ fontSize: 13, color: theme.colors.textSecondary, marginBottom: 14 }} numberOfLines={1}>
                                    {t('home.community')}: {poll.communityId}
                                </Text>
                                <Button
                                    title={t('home.vote')}
                                    icon="checkbox-outline"
                                    size="sm"
                                    onPress={() => navigation.navigate('VotingBallot', { electionId: poll.electionId })}
                                />
                            </Card>
                        ))}
                    </View>
                )}

                {/* Son sonuçlar */}
                <View style={{ marginTop: theme.spacing.lg }}>
                    <SectionHeader title={t('home.recentResults')} />
                    {isLoading ? (
                        <ActivityIndicator size="small" color={theme.colors.primary} />
                    ) : recentResults.length === 0 ? (
                        <Card>
                            <EmptyState icon="archive-outline" title={t('home.noRecentResults')} />
                        </Card>
                    ) : (
                        <View style={{ gap: 10 }}>
                            {recentResults.map((result: any) => (
                                <Card
                                    key={result.electionId}
                                    onPress={() => navigation.navigate('ElectionDetail', { electionId: result.electionId })}
                                >
                                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
                                        <View
                                            style={{
                                                width: 44, height: 44, borderRadius: theme.borderRadius.md,
                                                backgroundColor: theme.colors.surfaceAlt,
                                                alignItems: 'center', justifyContent: 'center',
                                            }}
                                        >
                                            <Ionicons name="podium-outline" size={20} color={theme.colors.textSecondary} />
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <Text style={{ fontSize: 15, fontWeight: '600', color: theme.colors.text, marginBottom: 3 }} numberOfLines={1}>
                                                {result.electionTitle}
                                            </Text>
                                            <Text style={{ fontSize: 12, color: theme.colors.textSecondary }}>
                                                {t('home.voteCount', { count: result.totalVotes || 0 })} · {dateFmt(result.endTime)}
                                            </Text>
                                        </View>
                                        <Ionicons name="chevron-forward" size={18} color={theme.colors.textTertiary} />
                                    </View>
                                </Card>
                            ))}
                        </View>
                    )}
                </View>
            </ScrollView>
        </SafeAreaView>
    );
};
