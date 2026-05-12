import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, RefreshControl, Image } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

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
                if (poll.status && String(poll.status).toUpperCase() !== 'ACTIVE') {
                    return false;
                }
                if (poll.startTime && new Date(poll.startTime) > now) {
                    return false;
                }
                if (poll.endTime && new Date(poll.endTime) <= now) {
                    return false;
                }
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

    useEffect(() => {
        initialFetch();
    }, []);

    return (
        <View style={tw`flex-1 bg-background`}>
            {/* Header */}
            <View style={tw`flex-row items-center justify-between px-5 pt-14 pb-4 bg-surface border-b border-background z-20`}>
                <View style={tw`flex-row items-center gap-3`}>
                    <View style={tw`relative`}>
                        <View style={tw`w-11 h-11 rounded-full bg-primary overflow-hidden items-center justify-center shadow-sm`}>
                            {user?.profileImage ? (
                                <Image
                                    source={{ uri: user.profileImage }}
                                    style={tw`w-full h-full`}
                                    resizeMode="cover"
                                />
                            ) : (
                                <Text style={tw`text-surface font-bold text-lg`}>{user?.firstName?.[0]}</Text>
                            )}
                        </View>
                        <View style={tw`absolute bottom-0 right-0 w-3.5 h-3.5 bg-success border-2 border-surface rounded-full`} />
                    </View>
                    <View>
                        <Text style={tw`text-xs font-medium text-textSecondary`}>{t('auth.login.welcomeBack')}</Text>
                        <Text style={tw`text-lg font-bold text-primary leading-tight`}>{user?.firstName} {user?.lastName}</Text>
                    </View>
                </View>
                <TouchableOpacity
                    style={tw`p-2 rounded-full relative bg-secondary/20`}
                    activeOpacity={0.7}
                    onPress={() => navigation.navigate('NotificationInbox')}
                >
                    <Ionicons name="notifications-outline" size={24} color={tw.color('primary') as string} />
                </TouchableOpacity>
            </View>

            <ScrollView
                contentContainerStyle={tw`flex-col gap-6 pb-6 pt-4`}
                refreshControl={
                    <RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[tw.color('primary') as string]} />
                }
            >

                {/* Active Polls Section */}
                <View style={tw`flex-col gap-4`}>
                    <View style={tw`flex-row items-center justify-between px-5`}>
                        <Text style={tw`text-xl font-bold text-primary`}>{t('home.activeElections')}</Text>
                        <TouchableOpacity onPress={() => navigation.navigate('Communities')} activeOpacity={0.6}>
                            <Text style={tw`text-sm font-semibold text-primary`}>{t('home.seeAll')}</Text>
                        </TouchableOpacity>
                    </View>

                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={tw`px-5 pb-2 gap-4`}>
                        {isLoading ? (
                            <ActivityIndicator size="large" color={tw.color('primary')} style={tw`ml-2 mt-5`} />
                        ) : activeElections.length === 0 ? (
                            <View style={tw`w-[280px] bg-surface rounded-2xl p-5 shadow-sm border border-primary/10 flex-col gap-3 justify-center items-center h-44`}>
                                <Ionicons name="stats-chart-outline" size={40} color={tw.color('secondary') as string} />
                                <Text style={tw`text-textSecondary font-medium`}>{t('home.noActive')}</Text>
                            </View>
                        ) : (
                            activeElections.map((poll: any) => (
                                <View key={poll.electionId} style={tw`w-[280px] bg-surface rounded-2xl p-5 shadow-sm border border-primary/10 flex-col relative overflow-hidden h-44`}>
                                    <View style={tw`absolute top-0 left-0 w-1 h-full bg-primary`} />

                                    <View style={tw`flex-row justify-between items-start mb-3`}>
                                        <View style={tw`flex-row items-center gap-1.5 px-2.5 py-1 rounded-md bg-success/10`}>
                                            <View style={tw`w-1.5 h-1.5 rounded-full bg-success`} />
                                            <Text style={tw`text-[10px] font-bold uppercase tracking-wider text-success`}>{t('home.live')}</Text>
                                        </View>
                                        <View style={tw`flex-row items-center gap-1 bg-secondary/10 px-2 py-1 rounded-md`}>
                                            <Ionicons name="time-outline" size={12} color={tw.color('primary') as string} />
                                            <Text style={tw`text-[10px] font-semibold text-textSecondary uppercase tracking-wide`}>
                                                {poll.endTime ? new Date(poll.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR') : t('common.unknownUpper')}
                                            </Text>
                                        </View>
                                    </View>

                                    <View style={tw`flex-1`}>
                                        <Text style={tw`text-base font-bold text-primary leading-tight mb-1`} numberOfLines={2}>{poll.electionTitle}</Text>
                                        <Text style={tw`text-xs text-textSecondary font-medium`} numberOfLines={1}>{t('home.community')}: {poll.communityId}</Text>
                                    </View>

                                    <View style={tw`mt-auto`}>
                                        <TouchableOpacity
                                            style={tw`w-full py-2.5 bg-primary rounded-lg flex-row items-center justify-center gap-2 shadow-sm`}
                                            activeOpacity={0.8}
                                            onPress={() => navigation.navigate('VotingBallot', { electionId: poll.electionId })}
                                        >
                                            <Ionicons name="checkbox" size={18} color={tw.color('surface') as string} />
                                            <Text style={tw`text-surface text-sm font-semibold`}>{t('home.vote')}</Text>
                                        </TouchableOpacity>
                                    </View>
                                </View>
                            ))
                        )}
                    </ScrollView>
                </View>

                {/* Recent Results Section */}
                <View style={tw`px-5 mt-2`}>
                    <Text style={tw`text-xl font-bold text-primary mb-4`}>{t('home.recentResults')}</Text>
                    <View style={tw`flex-col gap-3`}>
                        {isLoading ? (
                            <ActivityIndicator size="small" color={tw.color('primary')} />
                        ) : recentResults.length === 0 ? (
                            <View style={tw`bg-surface rounded-2xl p-6 shadow-sm border border-primary/10 items-center justify-center gap-2`}>
                                <Ionicons name="archive-outline" size={32} color={tw.color('secondary') as string} />
                                <Text style={tw`text-textSecondary text-sm font-medium`}>{t('home.noRecentResults')}</Text>
                            </View>
                        ) : (
                            recentResults.map((result: any) => (
                                <TouchableOpacity
                                    key={result.electionId}
                                    style={tw`flex-row items-center justify-between p-4 bg-surface rounded-2xl border border-primary/10 shadow-sm`}
                                    activeOpacity={0.7}
                                    onPress={() => navigation.navigate('ElectionDetail', { electionId: result.electionId })}
                                >
                                    <View style={tw`flex-row items-center gap-4 flex-1`}>
                                        <View style={tw`w-12 h-12 rounded-full bg-primary/10 items-center justify-center`}>
                                            <Ionicons name="podium" size={20} color={tw.color('primary') as string} />
                                        </View>
                                        <View style={tw`flex-col flex-1 pr-2`}>
                                            <Text style={tw`text-base font-bold text-primary mb-1`} numberOfLines={1}>{result.electionTitle}</Text>
                                            <View style={tw`flex-row items-center gap-2`}>
                                                <View style={tw`flex-row items-center gap-1`}>
                                                    <Ionicons name="people" size={12} color={tw.color('primary') as string} />
                                                    <Text style={tw`text-xs font-semibold text-primary`}>{t('home.voteCount', { count: result.totalVotes || 0 })}</Text>
                                                </View>
                                                <Text style={tw`text-xs text-textSecondary`}>•</Text>
                                                <Text style={tw`text-xs font-medium text-textSecondary`}>{new Date(result.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR')}</Text>
                                            </View>
                                        </View>
                                    </View>
                                    <View style={tw`w-8 h-8 rounded-full bg-secondary/20 items-center justify-center ml-2`}>
                                        <Ionicons name="chevron-forward" size={18} color={tw.color('primary') as string} />
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
