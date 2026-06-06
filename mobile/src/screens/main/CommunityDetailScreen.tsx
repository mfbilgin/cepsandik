import React, { useLayoutEffect, useState, useEffect, useCallback } from 'react';
import {
    View, Text, TouchableOpacity, ScrollView,
    ActivityIndicator, RefreshControl, ImageBackground,
    StatusBar, Platform, Image, Modal, FlatList, TextInput,
    Share
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { tw } from '../../utils/tailwind';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';

export const CommunityDetailScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { id } = route.params || {};
    const { user } = useAuth();
    const { t, language } = useI18n();
    const { showDialog } = useUI();

    const [community, setCommunity] = useState<any>(null);
    const [elections, setElections] = useState<any[]>([]);
    const [draftElections, setDraftElections] = useState<any[]>([]);
    const [archivedElections, setArchivedElections] = useState<any[]>([]);
    const [members, setMembers] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);

    // Unsplash Integration States
    const [isGalleryVisible, setIsGalleryVisible] = useState(false);
    const [unsplashPhotos, setUnsplashPhotos] = useState<any[]>([]);
    const [isFetchingPhotos, setIsFetchingPhotos] = useState(false);
    const [searchQuery, setSearchQuery] = useState('community');

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const fetchData = async () => {
        try {
            // 5 endpoint paralel — sıralı bekleme yerine en yavaş çağrı kadar bekler.
            const [commRes, electionsRes, archRes, draftRes, memRes] = await Promise.all([
                api.get(`/communities/${id}`),
                api.get(`/elections/community/${id}?size=50`).catch(() => null),
                api.get(`/elections/communities/${id}/archived?size=50`).catch(() => null),
                api.get(`/elections/communities/${id}/drafts`).catch(() => null),
                api.get(`/communities/${id}/members?size=10`).catch(() => null),
            ]);

            setCommunity(commRes.data?.data || null);
            setElections(electionsRes?.data?.data?.content || electionsRes?.data?.data || []);
            setArchivedElections(archRes?.data?.data?.content || []);
            setDraftElections(draftRes?.data?.data || []);
            setMembers(memRes?.data?.data?.content || []);
        } catch (error) {
            setCommunity(null);
        } finally {
            setIsLoading(false);
            setRefreshing(false);
        }
    };

    useEffect(() => { fetchData(); }, [id]);

    const onRefresh = useCallback(() => {
        setRefreshing(true);
        fetchData();
    }, [id]);

    const fetchUnsplashPhotos = async (query = 'community') => {
        setIsFetchingPhotos(true);
        try {
            const res = await api.get(`/communities/unsplash?query=${query}`);
            const data = res.data?.data;
            setUnsplashPhotos(data?.results || []);
        } catch (error) {
            console.error("Failed to fetch Unsplash photos from backend", error);
        } finally {
            setIsFetchingPhotos(false);
        }
    };

    const handleOpenGallery = () => {
        setIsGalleryVisible(true);
        if (unsplashPhotos.length === 0) fetchUnsplashPhotos(searchQuery);
    };

    const handleSelectPhoto = async (photoUrl: string) => {
        setIsGalleryVisible(false);
        try {
            await api.put(`/communities/${id}`, { coverImageUrl: photoUrl });
            setCommunity((prev: any) => ({ ...prev, coverImageUrl: photoUrl }));
        } catch (error) {
            console.error("Failed to update cover image", error);
        }
    };

    const handleShareCommunity = async () => {
        if (!community) return;
        try {
            const shareUrl = `https://cepsandik.com/topluluk/${community.id}`;
            // Android Share.share `url` prop'unu yok sayar — bağlantıyı mesaja gömüyoruz.
            const message = `CepSandık'ta "${community.name}" topluluğuna seni davet ediyorum:\n\n${shareUrl}`;
            await Share.share({ message, url: shareUrl, title: community.name });
        } catch (error: any) {
            console.log("Failed to share", error);
        }
    };

    const handleJoinCommunity = async () => {
        try {
            const res = await api.post(`/communities/${id}/members/join`);
            if (res.data?.success) {
                showDialog({
                    title: t('common.success') || "Başarılı",
                    message: res.data?.message || "Katıldınız.",
                    type: 'success'
                });
                fetchData(); // Refresh UI to show member view
            }
        } catch (error: any) {
            const msg = error?.response?.data?.message || "Topluluğa katılırken hata oluştu";
            showDialog({
                title: t('common.error') || "Hata",
                message: msg,
                type: 'error'
            });
        }
    };

    const handleLeaveCommunity = () => {
        showDialog({
            title: t('communityDetail.leaveTitle') || 'Topluluktan Ayrıl',
            message: t('communityDetail.leaveBody') || 'Bu topluluktan ayrılmak istediğine emin misin?',
            type: 'confirm',
            confirmText: t('communityDetail.leaveConfirm') || 'Ayrıl',
            onConfirm: async () => {
                try {
                    await api.delete(`/communities/${id}/members/leave`);
                    showDialog({
                        title: t('common.success') || 'Başarılı',
                        message: t('communityDetail.leaveSuccess') || 'Topluluktan ayrıldınız.',
                        type: 'success',
                    });
                    fetchData();
                } catch (error: any) {
                    const msg = error?.response?.data?.message || 'Ayrılırken hata oluştu';
                    showDialog({ title: t('common.error') || 'Hata', message: msg, type: 'error' });
                }
            },
        });
    };

    const isOwner = community?.ownerId === user?.id;
    const isAuthorized = isOwner || community?.userRole === 'ADMIN' || community?.userRole === 'OWNER';
    const isPending = community?.userStatus === 'PENDING';
    // Sahip her zaman üye sayılır — backend userStatus alanını owner için doldurmuyor.
    const isApprovedMember = isOwner || (!!community?.userRole && community?.userStatus === 'APPROVED');

    const roleLabel = (role: string) => {
        if (role === 'OWNER') return t('communityDetail.role.owner') || 'Owner';
        if (role === 'ADMIN') return t('communityDetail.role.admin') || 'Admin';
        return t('communityDetail.role.member') || 'Member';
    };

    const statusLabel = (status: string) => {
        if (status === 'ACTIVE') return t('communityDetail.status.active') || 'ACTIVE';
        if (status === 'SCHEDULED') return t('communityDetail.status.scheduled') || 'SCHEDULED';
        if (status === 'CLOSED') return t('communityDetail.status.closed') || 'CLOSED';
        if (status === 'ARCHIVED') return t('communityDetail.status.archived') || 'ARCHIVED';
        if (status === 'CANCELLED') return t('communityDetail.status.cancelled') || 'CANCELLED';
        return status;
    };

    if (isLoading) {
        return (
            <SafeAreaView style={tw`flex-1 bg-background justify-center items-center`}>
                <ActivityIndicator size="large" color={tw.color('primary')} />
            </SafeAreaView>
        );
    }

    if (!community) {
        return (
            <SafeAreaView style={tw`flex-1 bg-background`}>
                <View style={tw`flex-row items-center px-4 py-4 bg-surface border-b border-borderDefault`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full`}>
                        <MaterialIcons name="arrow-back" size={24} color={tw.color('textDefault')} />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold text-textDefault ml-2`}>{t('communityDetail.notFoundTitle') || 'Not Found'}</Text>
                </View>
                <View style={tw`flex-1 items-center justify-center p-6`}>
                    <MaterialIcons name="error-outline" size={64} color={tw.color('textSecondary')} />
                    <Text style={tw`mt-4 text-lg text-textSecondary text-center`}>{t('communityDetail.notFoundBody') || 'Community not found or you do not have permission to view it.'}</Text>
                </View>
            </SafeAreaView>
        );
    }

    const now = new Date();
    const activeElectionsList = elections.filter((e: any) => {
        if (e.status !== 'ACTIVE') return false;
        if (e.endTime && new Date(e.endTime) <= now) return false;
        return true;
    });
    const scheduledElectionsList = elections.filter((e: any) => e.status === 'SCHEDULED');

    const adminsList = members.filter(m => m.role === 'ADMIN' || m.role === 'OWNER');
    const normalMembers = members.filter(m => m.role !== 'ADMIN' && m.role !== 'OWNER').slice(0, 9);
    const displayMembers = [...adminsList, ...normalMembers];

    return (
        <View style={tw`flex-1 bg-background relative`}>
            <StatusBar barStyle="dark-content" backgroundColor="transparent" translucent />

            {/* Top Navigation Bar Component */}
            <View style={[tw`absolute top-0 left-0 right-0 z-30 px-4 bg-surface/90 border-b border-borderDefault flex-row items-center justify-between pb-2`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight ?? 44 : 48 }]}>
                <View style={tw`flex-row items-center gap-3`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`items-center justify-center rounded-full active:opacity-60`}>
                        <MaterialIcons name="arrow-back" size={24} color={tw.color('primary')} />
                    </TouchableOpacity>
                    <Text style={tw`text-lg font-bold text-textDefault tracking-tight`}>{community.name}</Text>
                </View>
                <View style={tw`flex-row items-center gap-2`}>
                    <TouchableOpacity onPress={handleShareCommunity} style={tw`items-center justify-center rounded-full active:opacity-60`}>
                        <MaterialIcons name="share" size={24} color={tw.color('textSecondary')} />
                    </TouchableOpacity>
                </View>
            </View>

            <ScrollView
                contentContainerStyle={tw`pb-24`}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[tw.color('primary') || '#000']} progressViewOffset={90} />}
                showsVerticalScrollIndicator={false}
                scrollEventThrottle={16}
            >
                {/* Hero Section */}
                <View style={tw`relative pt-[70px]`}>
                    <View style={tw`w-full h-48 bg-surface overflow-hidden relative`}>
                        <ImageBackground
                            source={{ uri: community.coverImageUrl || 'https://images.unsplash.com/photo-1557683316-973673baf926?q=80&w=2029&auto=format&fit=crop' }}
                            style={tw`w-full h-full`}
                        >
                            <View style={tw`absolute inset-0 bg-primary/20`} />
                        </ImageBackground>

                        {/* Edit Button for Image */}
                        {isAuthorized && (
                            <TouchableOpacity
                                onPress={handleOpenGallery}
                                style={tw`absolute top-4 right-4 bg-background/80 w-10 h-10 rounded-full items-center justify-center shadow-lg`}
                            >
                                <MaterialIcons name="edit" size={20} color={tw.color('primary')} />
                            </TouchableOpacity>
                        )}
                    </View>

                    <View style={tw`px-6 -mt-12 relative flex-col gap-4 mb-4`}>
                        <View style={tw`flex-row items-end gap-4 justify-between`}>
                            <View style={tw`flex-row gap-4 items-end flex-1`}>
                                <View style={tw`relative`}>
                                    <View style={tw`w-24 h-24 rounded-full border-4 border-surface bg-background shadow-sm items-center justify-center overflow-hidden`}>
                                        <MaterialIcons name="groups" size={48} color={tw.color('primary')} />
                                    </View>
                                    <View style={tw`absolute bottom-1 right-1 bg-primary rounded-full p-1 border-2 border-surface items-center justify-center`}>
                                        <MaterialIcons name="verified" size={14} color="white" />
                                    </View>
                                </View>

                                <View style={tw`flex-1 pt-2`}>
                                    <Text style={tw`text-xl font-bold tracking-tight text-textDefault mb-1`} numberOfLines={2}>
                                        {community.name}
                                    </Text>
                                    <View style={tw`flex-row items-center gap-2 flex-wrap`}>
                                        <View style={tw`bg-primary/10 px-2 py-0.5 rounded-full`}>
                                            <Text style={tw`text-primary text-[10px] font-bold uppercase tracking-wider`}>
                                                {community.visibility === 'PUBLIC' ? (t('communityDetail.visibility.public') || 'Public') : (t('communityDetail.visibility.private') || 'Private')}
                                            </Text>
                                        </View>
                                        <Text style={tw`text-textSecondary text-xs font-medium`}>
                                            {t('communityDetail.memberCount', { count: community.memberCount ?? 0 }) || `${community.memberCount ?? 0} Members`}
                                        </Text>
                                    </View>
                                </View>
                            </View>
                        </View>

                        {/* Actions Row */}
                        <View style={tw`flex-row gap-2 mt-2 w-full`}>
                            {isPending ? (
                                <View style={tw`flex-1 bg-warningTint border border-borderDefault rounded-lg flex-row items-center justify-center py-2 h-11 gap-2`}>
                                    <MaterialIcons name="hourglass-top" size={18} color={tw.color('warning') || '#f59e0b'} />
                                    <Text style={tw`text-textDefault font-bold text-sm`}>{t('communityDetail.requestPending') || 'İstek Beklemede'}</Text>
                                </View>
                            ) : isApprovedMember ? (
                                isOwner ? (
                                    <View style={tw`flex-1 bg-surface border border-borderDefault shadow-sm rounded-lg flex-row items-center justify-center py-2 h-11 gap-2`}>
                                        <MaterialIcons name="check-circle" size={18} color={tw.color('success') || '#10b981'} />
                                        <Text style={tw`text-textDefault font-bold text-sm`}>{roleLabel(community.userRole)}</Text>
                                    </View>
                                ) : (
                                    <TouchableOpacity
                                        onPress={handleLeaveCommunity}
                                        activeOpacity={0.7}
                                        style={tw`flex-1 bg-surface border border-borderDefault shadow-sm rounded-lg flex-row items-center justify-center py-2 h-11 gap-2`}
                                    >
                                        <MaterialIcons name="check-circle" size={18} color={tw.color('success') || '#10b981'} />
                                        <Text style={tw`text-textDefault font-bold text-sm`}>{roleLabel(community.userRole)}</Text>
                                    </TouchableOpacity>
                                )
                            ) : (
                                <TouchableOpacity onPress={handleJoinCommunity} style={tw`flex-1 bg-primary rounded-lg flex-row items-center justify-center py-2 h-11 shadow-sm gap-2`}>
                                    <MaterialIcons name="group-add" size={18} color="white" />
                                    <Text style={tw`text-white font-bold text-sm`}>{t('communityDetail.joinCommunity') || 'Katılma İsteği Gönder'}</Text>
                                </TouchableOpacity>
                            )}

                            {isAuthorized && (
                                <TouchableOpacity
                                    onPress={() => navigation.navigate('CommunityManagement', { id })}
                                    style={tw`flex-1 bg-primary/10 rounded-lg flex-row items-center justify-center py-2 h-11 gap-2 border border-primary/20`}
                                >
                                    <MaterialIcons name="settings" size={18} color={tw.color('primary')} />
                                    <Text style={tw`text-primary font-bold text-sm`}>Yönet</Text>
                                </TouchableOpacity>
                            )}
                        </View>

                    </View>
                </View>

                {/* Bento Layout Content */}
                <View style={tw`px-6 mt-4 gap-6`}>

                    {/* About Section */}
                    {community.description ? (
                        <View style={tw`bg-surface rounded-xl p-5 shadow-sm border border-borderDefault`}>
                            <Text style={tw`text-base font-bold text-textDefault mb-3`}>{t('communityDetail.tab.about') || 'About this Community'}</Text>
                            <Text style={tw`text-textSecondary text-sm leading-relaxed mb-4`}>
                                {community.description}
                            </Text>
                            <View style={tw`pt-4 flex-row gap-4 border-t border-borderDefault/50`}>
                                <View style={tw`flex-row items-center gap-1.5`}>
                                    <MaterialIcons name="calendar-today" size={14} color={tw.color('primary')} />
                                    <Text style={tw`text-xs text-textSecondary font-medium`}>
                                        {community.createdAt ? new Date(community.createdAt).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR', { month: 'short', year: 'numeric' }) : '-'}
                                    </Text>
                                </View>
                                <View style={tw`flex-row items-center gap-1.5`}>
                                    <MaterialIcons name="public" size={14} color={tw.color('primary')} />
                                    <Text style={tw`text-xs text-textSecondary font-medium`}>
                                        {community.visibility === 'PUBLIC' ? (t('communityDetail.visibility.public') || 'Public') : (t('communityDetail.visibility.private') || 'Private')}
                                    </Text>
                                </View>
                            </View>
                        </View>
                    ) : null}

                    {/* Active Polls Section */}
                    <View style={tw`flex-col gap-3`}>
                        <View style={tw`flex-row items-center justify-between`}>
                            <Text style={tw`text-base font-bold text-textDefault`}>{t('communityDetail.activeHeader') || 'Aktif Seçimler'}</Text>
                            {activeElectionsList.length > 0 && (
                                <View style={tw`flex-row items-center gap-1 bg-surface px-2 py-0.5 rounded border border-borderDefault`}>
                                    <View style={tw`w-2 h-2 rounded-full bg-danger`} />
                                    <Text style={tw`text-danger text-xs font-bold uppercase tracking-tight`}>{activeElectionsList.length} LIVE</Text>
                                </View>
                            )}
                        </View>

                        {activeElectionsList.length === 0 ? (
                            <View style={tw`bg-surface p-6 rounded-xl border border-borderDefault items-center justify-center shadow-sm`}>
                                <MaterialIcons name="inventory-2" size={36} color={tw.color('textSecondary')} style={tw`opacity-40`} />
                                <Text style={tw`mt-2 text-textSecondary text-sm font-medium text-center`}>{t('communityDetail.noActive') || 'Şu an aktif seçim yok'}</Text>
                            </View>
                        ) : (
                            activeElectionsList.map((election: any) => (
                                <TouchableOpacity
                                    key={election.id}
                                    onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                    style={tw`bg-surface rounded-xl overflow-hidden shadow-sm border border-borderDefault p-5`}
                                    activeOpacity={0.7}
                                >
                                    <View style={tw`flex-row justify-between items-start mb-3`}>
                                        <View style={tw`bg-danger/10 px-2 py-1 rounded flex-row items-center gap-1`}>
                                            <View style={tw`w-1.5 h-1.5 rounded-full bg-danger`} />
                                            <Text style={tw`text-danger text-[10px] font-bold uppercase`}>CANLI</Text>
                                        </View>
                                        <View style={tw`flex-row items-center gap-1`}>
                                            <MaterialIcons name="timer" size={14} color={tw.color('textSecondary')} />
                                            <Text style={tw`text-textSecondary text-xs font-medium`}>
                                                {election.endTime ? new Date(election.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR', { day: '2-digit', month: 'short' }) : 'Süresiz'}
                                            </Text>
                                        </View>
                                    </View>
                                    <Text style={tw`text-base font-bold text-textDefault mb-1 leading-tight`}>{election.title}</Text>
                                    {election.description ? (
                                        <Text style={tw`text-textSecondary text-xs leading-snug mb-4`} numberOfLines={2}>{election.description}</Text>
                                    ) : <View style={tw`mb-2`} />}
                                    <View style={tw`mt-2 bg-primary rounded-lg py-2.5 items-center justify-center`}>
                                        <Text style={tw`text-white font-bold text-sm`}>{t('communityDetail.card.vote') || 'Oy Ver'}</Text>
                                    </View>
                                </TouchableOpacity>
                            ))
                        )}
                    </View>

                    {/* Scheduled Polls Section */}
                    {scheduledElectionsList.length > 0 && (
                        <View style={tw`flex-col gap-3`}>
                            <View style={tw`flex-row items-center justify-between`}>
                                <Text style={tw`text-base font-bold text-textDefault`}>Yaklaşan Seçimler</Text>
                                <View style={tw`flex-row items-center gap-1 bg-surface px-2 py-0.5 rounded border border-borderDefault`}>
                                    <Ionicons name="calendar-outline" size={12} color={tw.color('primary')} />
                                    <Text style={tw`text-primary text-xs font-bold uppercase tracking-tight`}>{scheduledElectionsList.length} PLANLANDI</Text>
                                </View>
                            </View>
                            {scheduledElectionsList.map((election: any) => (
                                <TouchableOpacity
                                    key={election.id}
                                    onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                    style={tw`bg-surface rounded-xl overflow-hidden shadow-sm border border-borderDefault p-5`}
                                    activeOpacity={0.7}
                                >
                                    <View style={tw`flex-row justify-between items-start mb-3`}>
                                        <View style={tw`bg-primary/10 px-2 py-1 rounded flex-row items-center gap-1`}>
                                            <Ionicons name="calendar-outline" size={11} color={tw.color('primary')} />
                                            <Text style={tw`text-primary text-[10px] font-bold uppercase`}>PLANLI</Text>
                                        </View>
                                        <View style={tw`flex-row items-center gap-1`}>
                                            <MaterialIcons name="event" size={14} color={tw.color('textSecondary')} />
                                            <Text style={tw`text-textSecondary text-xs font-medium`}>
                                                {election.startTime ? new Date(election.startTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR', { day: '2-digit', month: 'short' }) : 'Tarih belirsiz'}
                                            </Text>
                                        </View>
                                    </View>
                                    <Text style={tw`text-base font-bold text-textDefault mb-1 leading-tight`}>{election.title}</Text>
                                    {election.description ? (
                                        <Text style={tw`text-textSecondary text-xs leading-snug mb-4`} numberOfLines={2}>{election.description}</Text>
                                    ) : <View style={tw`mb-2`} />}
                                    <View style={tw`mt-2 bg-surface border border-primary rounded-lg py-2.5 items-center justify-center`}>
                                        <Text style={tw`text-primary font-bold text-sm`}>Detayları Gör</Text>
                                    </View>
                                </TouchableOpacity>
                            ))}
                        </View>
                    )}

                    {/* Admins & Past Results Grid Layout */}
                    <View style={tw`gap-6`}>
                        {/* Community Admins & Members */}
                        {displayMembers.length > 0 && (
                            <View style={tw`bg-surface rounded-xl p-5 shadow-sm border border-borderDefault`}>
                                <View style={tw`flex-row items-center justify-between mb-4`}>
                                    <Text style={tw`text-sm font-bold text-textDefault uppercase tracking-wider`}>{t('communityDetail.membersHeader') || 'Üyeler'}</Text>
                                    <TouchableOpacity onPress={() => navigation.navigate('CommunityMembers', { communityId: id, communityName: community.name, userRole: community.userRole })}>
                                        <Text style={tw`text-xs font-bold text-primary`}>{t('common.seeAll') || 'Tümünü Gör'}</Text>
                                    </TouchableOpacity>
                                </View>
                                <View style={tw`gap-4`}>
                                    {displayMembers.map((admin: any) => (
                                        <View key={admin.id} style={tw`flex-row items-center gap-3`}>
                                            {admin.profileImage ? (
                                                <Image source={{ uri: admin.profileImage }} style={tw`w-10 h-10 rounded-full`} />
                                            ) : (
                                                <View style={tw`w-10 h-10 bg-primary/10 rounded-full items-center justify-center`}>
                                                    <MaterialIcons name="person" size={22} color={tw.color('primary')} />
                                                </View>
                                            )}
                                            <View>
                                                <Text style={tw`text-sm font-bold text-textDefault`}>
                                                    {admin.firstName ? `${admin.firstName} ${admin.lastName}` : (admin.displayName || `#${String(admin.userId).slice(-8).toUpperCase()}`)}
                                                </Text>
                                                <Text style={tw`text-[11px] text-textSecondary font-medium`}>{roleLabel(admin.role)}</Text>
                                            </View>
                                        </View>
                                    ))}
                                </View>
                            </View>
                        )}

                        {/* Draft Elections — sadece admin/owner görür */}
                        {isAuthorized && (
                            <View style={tw`bg-surface rounded-xl p-5 shadow-sm border border-borderDefault`}>
                                <View style={tw`flex-row items-center gap-2 mb-4`}>
                                    <Ionicons name="document-outline" size={16} color={tw.color('primary')} />
                                    <Text style={tw`text-sm font-bold text-textDefault uppercase tracking-wider`}>
                                        Taslak Seçimler
                                    </Text>
                                    {draftElections.length > 0 && (
                                        <View style={tw`ml-auto bg-primary/10 px-2 py-0.5 rounded-full`}>
                                            <Text style={tw`text-[10px] font-bold text-primary`}>{draftElections.length}</Text>
                                        </View>
                                    )}
                                </View>
                                {draftElections.length === 0 ? (
                                    <Text style={tw`text-textSecondary text-xs font-medium`}>Taslak seçim yok.</Text>
                                ) : (
                                    <View style={tw`gap-3`}>
                                        {draftElections.map((election: any) => (
                                            <TouchableOpacity
                                                key={election.id}
                                                onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                                style={tw`bg-background p-3 rounded-lg border border-borderDefault flex-row items-center justify-between`}
                                            >
                                                <View style={tw`flex-1`}>
                                                    <Text style={tw`text-xs font-bold text-textDefault leading-tight`} numberOfLines={2}>{election.title}</Text>
                                                    <Text style={tw`text-[10px] text-textSecondary mt-1`}>
                                                        {election.startTime ? new Date(election.startTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR') : 'Tarih belirsiz'}
                                                    </Text>
                                                </View>
                                                <View style={tw`bg-amber-100 px-2 py-0.5 rounded ml-2`}>
                                                    <Text style={tw`text-[10px] text-amber-700 font-bold uppercase`}>TASLAK</Text>
                                                </View>
                                            </TouchableOpacity>
                                        ))}
                                    </View>
                                )}
                            </View>
                        )}

                        {/* Past Results / Archive */}
                        <View style={tw`bg-surface rounded-xl p-5 shadow-sm border border-borderDefault`}>
                            <View style={tw`flex-row items-center justify-between mb-4`}>
                                <Text style={tw`text-sm font-bold text-textDefault uppercase tracking-wider`}>{t('communityDetail.archiveHeader') || 'Past Results'}</Text>
                            </View>

                            {archivedElections.length === 0 ? (
                                <Text style={tw`text-textSecondary text-xs font-medium`}>{t('communityDetail.noArchive') || 'No past results found.'}</Text>
                            ) : (
                                <View style={tw`gap-3`}>
                                    {archivedElections.slice(0, 3).map((election: any) => (
                                        <TouchableOpacity
                                            key={election.id}
                                            onPress={() => navigation.navigate('ElectionDetail', { electionId: election.id })}
                                            style={tw`bg-background p-3 rounded-lg border border-borderDefault`}
                                        >
                                            <Text style={tw`text-xs font-bold text-textDefault leading-tight`} numberOfLines={2}>{election.title}</Text>
                                            <View style={tw`mt-2 flex-row items-center justify-between`}>
                                                <View style={tw`bg-success/15 px-1.5 py-0.5 rounded`}>
                                                    <Text style={tw`text-[10px] text-success font-bold uppercase`}>{statusLabel(election.status)}</Text>
                                                </View>
                                                <Text style={tw`text-[10px] text-textSecondary font-medium`}>
                                                    {election.endTime ? new Date(election.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR', { year: '2-digit', month: '2-digit', day: '2-digit' }) : ''}
                                                </Text>
                                            </View>
                                        </TouchableOpacity>
                                    ))}
                                </View>
                            )}
                        </View>

                        {/* Stats Summary */}
                        <View style={tw`flex-row gap-4`}>
                            <View style={tw`flex-1 bg-surface border border-borderDefault p-4 rounded-xl items-center`}>
                                <MaterialIcons name="groups" size={24} color={tw.color('primary')} style={tw`mb-1`} />
                                <Text style={tw`text-lg font-bold text-textDefault`}>{community.memberCount ?? 0}</Text>
                                <Text style={tw`text-[10px] text-textSecondary uppercase font-bold`}>{t('communityDetail.tab.members') || 'Members'}</Text>
                            </View>
                            <View style={tw`flex-1 bg-surface border border-borderDefault p-4 rounded-xl items-center`}>
                                <MaterialIcons name="how-to-vote" size={24} color={tw.color('primary')} style={tw`mb-1`} />
                                <Text style={tw`text-lg font-bold text-textDefault`}>{elections.length + archivedElections.length}</Text>
                                <Text style={tw`text-[10px] text-textSecondary uppercase font-bold`}>Total Polls</Text>
                            </View>
                        </View>
                    </View>

                </View>
            </ScrollView>

            {/* Unsplash Gallery Modal */}
            <Modal visible={isGalleryVisible} animationType="slide" presentationStyle="pageSheet">
                <SafeAreaView style={tw`flex-1 bg-background`}>
                    <View style={tw`flex-row items-center justify-between p-4 border-b border-borderDefault`}>
                        <Text style={tw`text-lg font-bold text-textDefault`}>Select Cover Image</Text>
                        <TouchableOpacity onPress={() => setIsGalleryVisible(false)} style={tw`p-2 bg-surface rounded-full`}>
                            <MaterialIcons name="close" size={20} color={tw.color('textDefault')} />
                        </TouchableOpacity>
                    </View>

                    <View style={tw`p-4 flex-row items-center gap-2`}>
                        <View style={tw`flex-1 bg-surface flex-row items-center px-4 py-2 border border-borderDefault rounded-lg`}>
                            <MaterialIcons name="search" size={20} color={tw.color('textSecondary')} />
                            <TextInput
                                value={searchQuery}
                                onChangeText={setSearchQuery}
                                onSubmitEditing={() => fetchUnsplashPhotos(searchQuery)}
                                placeholder="Search Unsplash..."
                                placeholderTextColor={tw.color('textSecondary')}
                                style={tw`flex-1 ml-2 text-textDefault`}
                            />
                        </View>
                        <TouchableOpacity onPress={() => fetchUnsplashPhotos(searchQuery)} style={tw`bg-primary py-2.5 px-4 rounded-lg`}>
                            <Text style={tw`text-white font-bold`}>Search</Text>
                        </TouchableOpacity>
                    </View>

                    {isFetchingPhotos ? (
                        <View style={tw`flex-1 items-center justify-center`}>
                            <ActivityIndicator size="large" color={tw.color('primary')} />
                        </View>
                    ) : (
                        <FlatList
                            data={unsplashPhotos}
                            keyExtractor={(item) => item.id}
                            numColumns={2}
                            contentContainerStyle={tw`p-2`}
                            renderItem={({ item }) => (
                                <TouchableOpacity
                                    onPress={() => handleSelectPhoto(item.urls.regular)}
                                    style={tw`flex-1 aspect-square m-2 rounded-xl overflow-hidden border border-borderDefault`}
                                >
                                    <Image source={{ uri: item.urls.small }} style={tw`w-full h-full`} />
                                </TouchableOpacity>
                            )}
                            ListEmptyComponent={() => (
                                <View style={tw`flex-1 items-center justify-center p-10`}>
                                    <MaterialIcons name="image-search" size={48} color={tw.color('textSecondary')} style={tw`opacity-40 mb-3`} />
                                    <Text style={tw`text-textSecondary text-center`}>No photos found. Try a different search term.</Text>
                                </View>
                            )}
                        />
                    )}
                </SafeAreaView>
            </Modal>
        </View>
    );
};
