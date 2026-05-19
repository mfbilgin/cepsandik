import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl, Image, Modal } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Card, Badge, Button, EmptyState, SectionHeader } from '../../components/ui';
import { AccessCodeInput } from '../../components/AccessCodeInput';
import { NewElectionSheet } from '../../components/NewElectionSheet';
import { MaterialIcons } from '@expo/vector-icons';

export const HomeScreen = () => {
    const { user } = useAuth();
    const navigation = useNavigation<any>();
    const { t, language } = useI18n();
    const [activeElections, setActiveElections] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [recentResults, setRecentResults] = useState([]);
    const [showAccessCodeModal, setShowAccessCodeModal] = useState(false);
    const [showNewElectionSheet, setShowNewElectionSheet] = useState(false);

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

    const handleAccessCodeSuccess = useCallback((electionData: any) => {
        setShowAccessCodeModal(false);
        // Navigate to voting ballot with the election data
        navigation.navigate('VotingBallot', { electionId: electionData.id });
    }, [navigation]);

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
                {/* Hızlı Erişim — kod ile katıl + yeni seçim oluştur */}
                <View style={{ flexDirection: 'row', gap: 10, marginBottom: theme.spacing.md }}>
                    <TouchableOpacity
                        activeOpacity={0.85}
                        onPress={() => setShowAccessCodeModal(true)}
                        style={{
                            flex: 1, padding: 14, borderRadius: theme.borderRadius.lg,
                            backgroundColor: theme.colors.primaryTint, borderWidth: 1, borderColor: theme.colors.border,
                            gap: 10, minHeight: 110, justifyContent: 'space-between',
                        }}
                    >
                        <View style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: theme.colors.primary, alignItems: 'center', justifyContent: 'center' }}>
                            <Ionicons name="key" size={18} color={theme.colors.onPrimary} />
                        </View>
                        <View>
                            <Text style={{ fontSize: 14, fontWeight: '700', color: theme.colors.text }}>
                                {t('home.joinWithCode') || 'Kod ile Oy Ver'}
                            </Text>
                            <Text style={{ fontSize: 11, color: theme.colors.textSecondary, marginTop: 2, lineHeight: 15 }} numberOfLines={2}>
                                {t('home.joinWithCodeHelp') || '6 haneli kodu gir, seçim ekranına git'}
                            </Text>
                        </View>
                    </TouchableOpacity>

                    <TouchableOpacity
                        activeOpacity={0.85}
                        onPress={() => setShowNewElectionSheet(true)}
                        style={{
                            flex: 1, padding: 14, borderRadius: theme.borderRadius.lg,
                            backgroundColor: theme.colors.surface, borderWidth: 1, borderColor: theme.colors.border,
                            gap: 10, minHeight: 110, justifyContent: 'space-between',
                        }}
                    >
                        <View style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: theme.colors.surfaceAlt, alignItems: 'center', justifyContent: 'center' }}>
                            <MaterialIcons name="how-to-vote" size={20} color={theme.colors.primary} />
                        </View>
                        <View>
                            <Text style={{ fontSize: 14, fontWeight: '700', color: theme.colors.text }}>
                                {t('home.newElection') || 'Yeni Seçim Oluştur'}
                            </Text>
                            <Text style={{ fontSize: 11, color: theme.colors.textSecondary, marginTop: 2, lineHeight: 15 }} numberOfLines={2}>
                                {t('home.newElectionShort') || 'Topluluğun için veya bağımsız'}
                            </Text>
                        </View>
                    </TouchableOpacity>
                </View>

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

            {/* Yeni seçim oluşturma alt sayfası */}
            <NewElectionSheet
                visible={showNewElectionSheet}
                onClose={() => setShowNewElectionSheet(false)}
            />

            {/* Access Code Modal */}
            <Modal
                visible={showAccessCodeModal}
                transparent
                animationType="slide"
                onRequestClose={() => setShowAccessCodeModal(false)}
            >
                <SafeAreaView style={{ flex: 1, backgroundColor: 'rgba(0, 0, 0, 0.5)' }}>
                    <TouchableOpacity
                        style={{ flex: 1 }}
                        activeOpacity={1}
                        onPress={() => setShowAccessCodeModal(false)}
                    />
                    <View style={{
                        backgroundColor: theme.colors.background,
                        borderTopLeftRadius: theme.borderRadius.lg,
                        borderTopRightRadius: theme.borderRadius.lg,
                        paddingVertical: theme.spacing.lg,
                        paddingHorizontal: theme.spacing.md,
                        paddingBottom: theme.spacing.lg + 24,
                    }}>
                        {/* Handle bar */}
                        <View style={{
                            width: 40,
                            height: 4,
                            backgroundColor: theme.colors.border,
                            borderRadius: 2,
                            alignSelf: 'center',
                            marginBottom: theme.spacing.md,
                        }} />

                        {/* Content */}
                        <AccessCodeInput
                            onSuccess={handleAccessCodeSuccess}
                            onCancel={() => setShowAccessCodeModal(false)}
                        />
                    </View>
                </SafeAreaView>
            </Modal>
        </SafeAreaView>
    );
};
