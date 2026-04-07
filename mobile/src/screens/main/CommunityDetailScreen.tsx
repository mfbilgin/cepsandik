import React, { useLayoutEffect, useState, useEffect, useCallback } from 'react';
import {
    View, Text, TouchableOpacity, SafeAreaView, ScrollView,
    ActivityIndicator, RefreshControl, ImageBackground,
    StatusBar, Platform
} from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import tw from 'twrnc';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/LanguageContext';

export const CommunityDetailScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { id } = route.params || {};
    const { user } = useAuth();
    const { t, language } = useI18n();

    const [community, setCommunity] = useState<any>(null);
    const [elections, setElections] = useState<any[]>([]);
    const [archivedElections, setArchivedElections] = useState<any[]>([]);
    const [members, setMembers] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [activeTab, setActiveTab] = useState(t('communityDetail.tab.active'));
    const [membersLoading, setMembersLoading] = useState(false);
    const [archivedLoading, setArchivedLoading] = useState(false);

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const fetchData = async () => {
        try {
            const commRes = await api.get(`/communities/${id}`);
            setCommunity(commRes.data?.data || null);

            try {
                const electionsRes = await api.get(`/elections/community/${id}?size=50`);
                setElections(electionsRes.data?.data?.content || electionsRes.data?.data || []);
            } catch {
                setElections([]);
            }
        } catch (error) {
            setCommunity(null);
        } finally {
            setIsLoading(false);
            setRefreshing(false);
        }
    };

    const fetchArchivedElections = useCallback(async () => {
        if (archivedElections.length > 0) return;
        setArchivedLoading(true);
        try {
            const res = await api.get(`/elections/communities/${id}/archived?size=50`);
            setArchivedElections(res.data?.data?.content || []);
        } catch {
            setArchivedElections([]);
        } finally {
            setArchivedLoading(false);
        }
    }, [id]);

    const fetchMembers = useCallback(async () => {
        if (members.length > 0) return;
        setMembersLoading(true);
        try {
            const res = await api.get(`/communities/${id}/members?size=100`);
            setMembers(res.data?.data?.content || []);
        } catch {
            setMembers([]);
        } finally {
            setMembersLoading(false);
        }
    }, [id]);

    useEffect(() => { fetchData(); }, [id]);

    useEffect(() => {
        if (activeTab === t('communityDetail.tab.archive')) fetchArchivedElections();
        if (activeTab === t('communityDetail.tab.members')) fetchMembers();
    }, [activeTab]);

    const onRefresh = useCallback(() => {
        setRefreshing(true);
        setArchivedElections([]);
        setMembers([]);
        fetchData();
    }, [id]);

    const isOwner = community?.ownerId === user?.id;
    const tabs = [
        t('communityDetail.tab.active'),
        t('communityDetail.tab.archive'),
        ...(isOwner ? [t('communityDetail.tab.members')] : []),
        t('communityDetail.tab.about'),
    ];

    const statusColor = (status: string) => {
        if (status === 'ACTIVE') return 'text-green-600';
        if (status === 'SCHEDULED') return 'text-amber-600';
        return 'text-slate-500';
    };
    const statusBg = (status: string) => {
        if (status === 'ACTIVE') return 'bg-green-500';
        if (status === 'SCHEDULED') return 'bg-amber-500';
        return 'bg-slate-400';
    };
    const statusLabel = (status: string) => {
        if (status === 'ACTIVE') return t('communityDetail.status.active');
        if (status === 'SCHEDULED') return t('communityDetail.status.scheduled');
        if (status === 'CLOSED') return t('communityDetail.status.closed');
        if (status === 'ARCHIVED') return t('communityDetail.status.archived');
        if (status === 'CANCELLED') return t('communityDetail.status.cancelled');
        return status;
    };
    const roleLabel = (role: string) => {
        if (role === 'OWNER') return t('communityDetail.role.owner');
        if (role === 'ADMIN') return t('communityDetail.role.admin');
        return t('communityDetail.role.member');
    };

    const ElectionCard = ({ election, onPress }: { election: any; onPress: () => void }) => (
        <TouchableOpacity
            onPress={onPress}
            activeOpacity={0.9}
            style={tw`bg-white rounded-xl p-4 shadow-sm border border-slate-100 mb-4`}
        >
            <View style={tw`flex-row justify-between items-start mb-3`}>
                <View style={tw`flex-row items-center gap-2`}>
                    <View style={tw`w-2 h-2 rounded-full ${statusBg(election.status)}`} />
                    <Text style={tw`text-[11px] font-bold ${statusColor(election.status)} uppercase tracking-wide`}>
                        {statusLabel(election.status)}
                    </Text>
                </View>
                <View style={tw`flex-row items-center bg-slate-100 px-2 py-0.5 rounded`}>
                    <MaterialIcons name="lock" size={12} color="#64748b" />
                    <Text style={tw`text-[10px] text-slate-500 font-bold tracking-wide ml-1`}>{t('communityDetail.encrypted')}</Text>
                </View>
            </View>
            <Text style={tw`text-slate-900 font-bold text-base mb-1 leading-tight`}>{election.title}</Text>
            {election.description ? (
                <Text style={tw`text-slate-500 text-sm leading-snug mb-3`} numberOfLines={2}>{election.description}</Text>
            ) : null}
            <View style={tw`flex-row items-center justify-between pt-3 border-t border-slate-100`}>
                <View>
                    <Text style={tw`text-xs text-slate-400 font-medium`}>
                        {election.status === 'ACTIVE' ? t('communityDetail.ends') : t('communityDetail.starts')}
                    </Text>
                    <Text style={tw`text-sm font-bold text-slate-900`}>
                        {election.status === 'ACTIVE'
                            ? new Date(election.endTime).toLocaleString(language === 'en' ? 'en-US' : 'tr-TR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
                            : new Date(election.startTime).toLocaleString(language === 'en' ? 'en-US' : 'tr-TR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                    </Text>
                </View>
                <View style={tw`${election.status === 'ACTIVE' ? 'bg-[#1162d4]' : 'bg-slate-100'} px-4 py-2 rounded-lg`}>
                    <Text style={tw`${election.status === 'ACTIVE' ? 'text-white' : 'text-slate-500'} text-sm font-semibold`}>
                        {election.status === 'ACTIVE' ? t('communityDetail.card.vote') : t('communityDetail.card.review')}
                    </Text>
                </View>
            </View>
        </TouchableOpacity>
    );

    if (isLoading) {
        return (
            <SafeAreaView style={tw`flex-1 bg-[#f6f7f8] justify-center items-center`}>
                <ActivityIndicator size="large" color="#1162d4" />
                <Text style={tw`text-slate-500 mt-4 font-medium`}>{t('communityDetail.loading')}</Text>
            </SafeAreaView>
        );
    }

    if (!community) {
        return (
            <SafeAreaView style={tw`flex-1 bg-[#f6f7f8]`}>
                <View style={tw`flex-row items-center px-4 py-4 bg-white border-b border-slate-200`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full`}>
                        <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold text-slate-900 ml-2`}>{t('communityDetail.notFoundTitle')}</Text>
                </View>
                <View style={tw`flex-1 items-center justify-center p-6`}>
                    <MaterialIcons name="error-outline" size={64} color="#334155" />
                    <Text style={tw`mt-4 text-lg text-slate-500 text-center`}>{t('communityDetail.notFoundBody')}</Text>
                </View>
            </SafeAreaView>
        );
    }

    const now = new Date();
    const activeElections = elections.filter((e: any) => {
        // Basic status filter
        const isBasicActive = e.status === 'ACTIVE' || e.status === 'SCHEDULED' || (isOwner && e.status === 'DRAFT');
        if (!isBasicActive) return false;

        // Time filter: if it has an end time and that time has passed, it's NOT active
        if (e.endTime && new Date(e.endTime) <= now) {
            return false;
        }

        return true;
    });

    return (
        <View style={tw`flex-1 bg-slate-50 relative`}>
            <StatusBar barStyle="dark-content" backgroundColor="#ffffff" translucent />

            {/* Top Navigation Bar */}
            <View style={[tw`absolute top-0 left-0 right-0 z-30 px-4 bg-white/95 border-b border-slate-100 flex-row items-center justify-between`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 44 : 48, paddingBottom: 8 }]}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full active:bg-slate-100`}>
                    <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <View style={tw`flex-1`} />
                {isOwner && (
                    <TouchableOpacity
                        onPress={() => navigation.navigate('CommunityManagement', { id })}
                        style={tw`w-10 h-10 items-center justify-center rounded-full active:bg-slate-100`}
                    >
                        <MaterialIcons name="more-vert" size={24} color="#0f172a" />
                    </TouchableOpacity>
                )}
                {!isOwner && <View style={tw`w-10`} />}
            </View>

            <ScrollView
                contentContainerStyle={tw`pb-24 pt-[96px]`}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#1162d4']} progressViewOffset={96} />}
                showsVerticalScrollIndicator={false}
                scrollEventThrottle={16}
            >
                {/* Hero */}
                <ImageBackground
                    source={{ uri: 'https://images.unsplash.com/photo-1557683316-973673baf926?q=80&w=2029&auto=format&fit=crop' }}
                    style={tw`w-full h-40 bg-slate-200`}
                >
                    <View style={tw`absolute inset-0 bg-black/40`} />
                </ImageBackground>

                {/* Community Info */}
                <View style={tw`px-4 pb-4 -mt-12 mb-2 relative z-10`}>
                    <View style={tw`flex-col items-center`}>
                        <View style={tw`relative`}>
                            <View style={tw`w-24 h-24 rounded-full border-4 border-white bg-[#1162d4]/10 shadow-md items-center justify-center`}>
                                <MaterialIcons name="groups" size={40} color="#1162d4" />
                            </View>
                            <View style={tw`absolute bottom-1 right-1 bg-green-500 rounded-full p-1 border-2 border-white items-center justify-center`}>
                                <MaterialIcons name="check" size={14} color="white" />
                            </View>
                        </View>

                        <View style={tw`mt-3 items-center w-full`}>
                            <Text style={tw`text-2xl font-bold text-slate-900 tracking-tight text-center`} numberOfLines={1}>{community.name}</Text>
                            <View style={tw`flex-row items-center justify-center gap-2 mt-1`}>
                                <MaterialIcons name="group" size={16} color="#64748b" />
                                <Text style={tw`text-sm font-medium text-slate-600`}>{t('communityDetail.memberCount', { count: community.memberCount ?? 0 })}</Text>
                                <View style={tw`w-1 h-1 rounded-full bg-slate-300`} />
                                <Text style={tw`text-sm font-medium text-[#1162d4]`}>{community.visibility === 'PUBLIC' ? t('communityDetail.visibility.public') : t('communityDetail.visibility.private')}</Text>
                            </View>
                        </View>

                        {/* Action row */}
                        <View style={tw`flex-row gap-3 w-full mt-5`}>
                            {community.userRole ? (
                                <View style={tw`flex-1 h-10 bg-slate-100 rounded-lg items-center justify-center flex-row gap-2`}>
                                    <MaterialIcons name="check-circle" size={18} color="#64748b" />
                                    <Text style={tw`text-slate-600 text-sm font-semibold`}>{roleLabel(community.userRole)}</Text>
                                </View>
                            ) : (
                                <TouchableOpacity style={tw`flex-1 h-10 bg-[#1162d4] rounded-lg items-center justify-center flex-row gap-2 shadow-sm`}>
                                    <MaterialIcons name="how-to-reg" size={18} color="white" />
                                    <Text style={tw`text-white text-sm font-semibold`}>{t('communityDetail.joinCommunity')}</Text>
                                </TouchableOpacity>
                            )}
                            <TouchableOpacity style={tw`w-10 h-10 items-center justify-center border border-slate-200 rounded-lg bg-white`}>
                                <MaterialIcons name="share" size={20} color="#475569" />
                            </TouchableOpacity>
                        </View>
                    </View>
                </View>

                {/* Tab Bar */}
                <View style={tw`bg-white border-b border-t border-slate-100 flex-row mb-4`}>
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={tw`flex-grow px-2`}>
                        {tabs.map((tab) => {
                            const isActive = activeTab === tab;
                            return (
                                <TouchableOpacity
                                    key={tab}
                                    onPress={() => setActiveTab(tab)}
                                    style={tw`items-center px-4 py-3 relative min-w-[80px]`}
                                >
                                    <Text style={tw`text-sm ${isActive ? 'font-bold text-[#1162d4]' : 'font-medium text-slate-500'}`}>{tab}</Text>
                                    {isActive && <View style={tw`absolute bottom-0 h-0.5 w-[100%] bg-[#1162d4] rounded-t-full`} />}
                                </TouchableOpacity>
                            );
                        })}
                    </ScrollView>
                </View>

                {/* Tab Content */}
                <View style={tw`px-4`}>

                    {activeTab === t('communityDetail.tab.active') && (
                        <>
                            <View style={tw`flex-row items-center justify-between mb-4`}>
                                <Text style={tw`text-slate-900 text-lg font-bold`}>{t('communityDetail.activeHeader')}</Text>
                                <View style={tw`bg-[#1162d4]/10 rounded-full px-2 py-1`}>
                                    <Text style={tw`text-xs font-medium text-[#1162d4]`}>{t('communityDetail.activeCount', { count: activeElections.length })}</Text>
                                </View>
                            </View>
                            {activeElections.length === 0 ? (
                                <View style={tw`bg-white p-6 rounded-xl border border-slate-100 items-center justify-center shadow-sm`}>
                                    <MaterialIcons name="inventory-2" size={40} color="#cbd5e1" />
                                    <Text style={tw`mt-2 text-slate-500 text-sm font-medium text-center`}>{t('communityDetail.noActive')}</Text>
                                </View>
                            ) : (
                                activeElections.map((election: any) => (
                                    <ElectionCard
                                        key={election.id}
                                        election={election}
                                        onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                    />
                                ))
                            )}
                            {activeElections.length > 0 && (
                                <View style={tw`flex-row items-center justify-center gap-2 mt-4 mb-8 opacity-60`}>
                                    <MaterialIcons name="security" size={18} color="#94a3b8" />
                                    <Text style={tw`text-xs text-slate-400 font-medium`}>{t('communityDetail.secureFooter')}</Text>
                                </View>
                            )}
                        </>
                    )}

                    {activeTab === t('communityDetail.tab.archive') && (
                        <>
                            <View style={tw`flex-row items-center justify-between mb-4`}>
                                <Text style={tw`text-slate-900 text-lg font-bold`}>{t('communityDetail.archiveHeader')}</Text>
                            </View>
                            {archivedLoading ? (
                                <View style={tw`p-8 items-center`}><ActivityIndicator color="#1162d4" /></View>
                            ) : archivedElections.length === 0 ? (
                                <View style={tw`bg-white p-6 rounded-xl border border-slate-100 items-center justify-center shadow-sm`}>
                                    <MaterialIcons name="archive" size={40} color="#cbd5e1" />
                                    <Text style={tw`mt-2 text-slate-500 text-sm font-medium text-center`}>{t('communityDetail.noArchive')}</Text>
                                </View>
                            ) : (
                                archivedElections.map((election: any) => (
                                    <ElectionCard
                                        key={election.id}
                                        election={election}
                                        onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                    />
                                ))
                            )}
                        </>
                    )}

                    {activeTab === t('communityDetail.tab.members') && (
                        <>
                            <View style={tw`flex-row items-center justify-between mb-4`}>
                                <Text style={tw`text-slate-900 text-lg font-bold`}>{t('communityDetail.membersHeader')}</Text>
                                <Text style={tw`text-slate-500 text-sm`}>{t('communityDetail.membersCount', { count: community.memberCount ?? 0 })}</Text>
                            </View>
                            {membersLoading ? (
                                <View style={tw`p-8 items-center`}><ActivityIndicator color="#1162d4" /></View>
                            ) : members.length === 0 ? (
                                <View style={tw`bg-white p-6 rounded-xl border border-slate-100 items-center justify-center shadow-sm`}>
                                    <MaterialIcons name="group-off" size={40} color="#cbd5e1" />
                                    <Text style={tw`mt-2 text-slate-500 text-sm font-medium text-center`}>{t('communityDetail.membersLoadFail')}</Text>
                                </View>
                            ) : (
                                <View style={tw`bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden`}>
                                    {members.map((member: any, idx: number) => (
                                        <View
                                            key={member.id || idx}
                                            style={tw`flex-row items-center gap-3 px-4 py-3 ${idx < members.length - 1 ? 'border-b border-slate-100' : ''}`}
                                        >
                                            <View style={tw`w-10 h-10 bg-[#1162d4]/10 rounded-full items-center justify-center`}>
                                                <MaterialIcons name="person" size={22} color="#1162d4" />
                                            </View>
                                            <View style={tw`flex-1`}>
                                                <Text style={tw`text-slate-900 font-semibold`}>
                                                    {member.displayName || `#${String(member.userId).slice(-8).toUpperCase()}`}
                                                </Text>
                                                <Text style={tw`text-slate-400 text-xs`}>{t('communityDetail.joined', { date: member.joinedAt ? new Date(member.joinedAt).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR') : '-' })}</Text>
                                            </View>
                                            <View style={tw`px-2 py-0.5 rounded-full ${member.role === 'OWNER' ? 'bg-amber-100' : member.role === 'ADMIN' ? 'bg-blue-100' : 'bg-slate-100'}`}>
                                                <Text style={tw`text-xs font-bold ${member.role === 'OWNER' ? 'text-amber-700' : member.role === 'ADMIN' ? 'text-blue-700' : 'text-slate-500'}`}>
                                                    {roleLabel(member.role)}
                                                </Text>
                                            </View>
                                        </View>
                                    ))}
                                </View>
                            )}
                        </>
                    )}

                    {activeTab === t('communityDetail.tab.about') && (
                        <View style={tw`gap-4`}>
                            {community.description ? (
                                <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm`}>
                                    <Text style={tw`text-slate-500 text-xs font-semibold uppercase tracking-wide mb-2`}>{t('communityDetail.aboutDescription')}</Text>
                                    <Text style={tw`text-slate-700 leading-relaxed`}>{community.description}</Text>
                                </View>
                            ) : null}

                            <View style={tw`bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden`}>
                                {[
                                    { icon: 'group', label: t('communityDetail.aboutMemberCount'), value: t('communityDetail.aboutMemberCountValue', { count: community.memberCount ?? 0 }) },
                                    { icon: 'visibility', label: t('communityDetail.aboutVisibility'), value: community.visibility === 'PUBLIC' ? t('communityDetail.visibility.public') : t('communityDetail.visibility.private') },
                                    { icon: 'calendar-today', label: t('communityDetail.aboutCreatedAt'), value: community.createdAt ? new Date(community.createdAt).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR', { day: '2-digit', month: 'long', year: 'numeric' }) : '-' },
                                    { icon: 'edit', label: t('communityDetail.aboutNameChanges'), value: community.nameChangeCount != null ? t('communityDetail.aboutNameChangesValue', { count: community.nameChangeCount }) : '-' },
                                ].map((item, idx, arr) => (
                                    <View key={item.label} style={tw`flex-row items-center gap-3 px-4 py-3 ${idx < arr.length - 1 ? 'border-b border-slate-100' : ''}`}>
                                        <MaterialIcons name={item.icon as any} size={20} color="#1162d4" />
                                        <Text style={tw`text-slate-600 flex-1`}>{item.label}</Text>
                                        <Text style={tw`text-slate-900 font-semibold`}>{item.value}</Text>
                                    </View>
                                ))}
                            </View>
                        </View>
                    )}
                </View>
            </ScrollView>
        </View>
    );
};
