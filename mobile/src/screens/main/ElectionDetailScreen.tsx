import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, TextInput, Modal, Platform, Alert } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { tw } from '../../utils/tailwind';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
// Faz 4.15b — distributedFlow (eski leader-mode) artık kullanılmıyor.

export const ElectionDetailScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const electionId = route.params?.electionId;

    const [election, setElection] = useState<any>(null);
    const [accessCode, setAccessCode] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isVerifying, setIsVerifying] = useState(false);
    const [isPublishing, setIsPublishing] = useState(false);
    const [guardianCandidates, setGuardianCandidates] = useState<any[]>([]);
    const [selectedGuardianIds, setSelectedGuardianIds] = useState<string[]>([]);
    const [isLoadingCandidates, setIsLoadingCandidates] = useState(false);
    const [isAssigningGuardians, setIsAssigningGuardians] = useState(false);
    const { user } = useAuth();

    // Edit Dates State
    const [isEditModalVisible, setIsEditModalVisible] = useState(false);
    const [editStartTime, setEditStartTime] = useState(new Date());
    const [editEndTime, setEditEndTime] = useState(new Date(Date.now() + 24 * 60 * 60 * 1000));
    const [showPicker, setShowPicker] = useState<'start' | 'end' | null>(null);
    const [pickerMode, setPickerMode] = useState<'date' | 'time'>('date');
    const [isUpdating, setIsUpdating] = useState(false);
    const { t, language } = useI18n();
    const { showDialog } = useUI();

    const formatDate = (date: Date) => date.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
    const formatTime = (date: Date) => date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        fetchDetail();
    }, []);

    const fetchGuardianCandidates = async () => {
        if (guardianCandidates.length > 0) return;
        setIsLoadingCandidates(true);
        try {
            const res = await api.get('/elections/guardian-candidates');
            setGuardianCandidates(res.data?.data || []);
        } catch (e) {
            console.error(e);
        } finally {
            setIsLoadingCandidates(false);
        }
    };

    const toggleGuardian = (id: string) => {
        setSelectedGuardianIds(prev =>
            prev.includes(id) ? prev.filter(g => g !== id) : [...prev, id]
        );
    };

    const fetchDetail = async () => {
        try {
            const res = await api.get(`/elections/${electionId}`);
            setElection(res.data?.data || null);
        } catch (e) {
            console.error(e);
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: t('electionDetail.fetchError'),
                type: 'error',
                onConfirm: () => navigation.goBack()
            });
        } finally {
            setIsLoading(false);
        }
    };

    const handleProceed = async () => {
        if (election.accessibility === 'PRIVATE') {
            if (!accessCode) {
                showDialog({
                    title: t('auth.login.missingTitle'),
                    message: t('electionDetail.privateCodeRequired'),
                    type: 'warning'
                });
                return;
            }
            setIsVerifying(true);
            try {
                await api.post(`/elections/${electionId}/verify-access`, { code: accessCode });
            } catch (e) {
                showDialog({
                    title: t('auth.twoFactor.errorTitle'),
                    message: t('electionDetail.invalidCode'),
                    type: 'error'
                });
                setIsVerifying(false);
                return;
            }
            setIsVerifying(false);
        }
        navigation.navigate('VotingBallot', { electionId, accessCode });
    };

    const handlePublish = async () => {
        setIsPublishing(true);
        try {
            await api.post(`/elections/${electionId}/publish`);
            showDialog({
                title: t('security.successTitle'),
                message: t('electionDetail.published'),
                type: 'success'
            });
            fetchDetail(); // Yeniden yükle ki status SCHEDULED olsun
        } catch (e: any) {
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: e.response?.data?.message || t('electionDetail.publishFail'),
                type: 'error'
            });
        } finally {
            setIsPublishing(false);
        }
    };

    const handleAssignManualGuardians = async () => {
        if (selectedGuardianIds.length < 2) {
            showDialog({
                title: 'Yetersiz seçim',
                message: 'En az 2 emanetçi seçmen gerekiyor.',
                type: 'warning'
            });
            return;
        }
        setIsAssigningGuardians(true);
        try {
            await api.post(`/elections/${electionId}/guardians/manual`, { guardianUserIds: selectedGuardianIds });
            showDialog({
                title: 'Emanetçiler atandı',
                message: 'Seçilen kullanıcılar bu seçimin emanetçisi olarak belirlendi.',
                type: 'success'
            });
        } catch (e: any) {
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: e.response?.data?.message || 'Emanetçiler atanamadı.',
                type: 'error'
            });
        } finally {
            setIsAssigningGuardians(false);
        }
    };

    // Faz 4.15b — handleDistributedSetup / handleDistributedTally KALDIRILDI
    // (eski leader-mode; threat-model bozuk). Doğru akış GuardianScreen'de
    // her emanetçinin kendi cihazında: runKeyCeremony + runDistributedTally.

    const handleUpdateDates = async () => {
        if (editEndTime <= editStartTime) {
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: t('electionDetail.invalidDateRange'),
                type: 'warning'
            });
            return;
        }
        setIsUpdating(true);
        try {
            await api.put(`/elections/${electionId}`, {
                startTime: editStartTime.toISOString(),
                endTime: editEndTime.toISOString(),
            });
            showDialog({
                title: t('security.successTitle'),
                message: t('electionDetail.updateDatesSuccess'),
                type: 'success'
            });
            setIsEditModalVisible(false);
            fetchDetail();
        } catch (e: any) {
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: e.response?.data?.message || t('electionDetail.updateDatesFail'),
                type: 'error'
            });
        } finally {
            setIsUpdating(false);
        }
    };

    const openEditModal = () => {
        setEditStartTime(new Date(election.startTime));
        setEditEndTime(new Date(election.endTime));
        setIsEditModalVisible(true);
    };

    if (isLoading || !election) {
        return (
            <View style={tw`flex-1 items-center justify-center bg-background`}>
                <ActivityIndicator size="large" color={tw.color('primary')} />
            </View>
        );
    }

    return (
        <View style={tw`flex-1 bg-background  max-w-md mx-auto w-full`}>
            {/* Top App Bar */}
            <View style={tw`flex-row items-center bg-surface p-4 shadow-sm z-10 pt-10`}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center rounded-full`}>
                    <Ionicons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <Text style={tw`flex-1 text-center text-lg font-bold text-slate-900`}>
                    {t('electionDetail.title')}
                </Text>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full`}>
                    <Ionicons name="information-circle-outline" size={24} color="#0f172a" />
                </TouchableOpacity>
            </View>

            {/* Scrollable Content */}
            <ScrollView contentContainerStyle={tw`pb-24`}>
                {/* Header Card & Status */}
                <View style={tw`bg-surface pb-6 px-4 pt-2 mb-2`}>
                    <View style={tw`flex-col items-center gap-4`}>
                        <View style={tw`w-24 h-24 items-center justify-center rounded-full bg-primary/10`}>
                            <Ionicons name="checkbox-outline" size={48} color={tw.color('primary')} />
                        </View>
                        <View style={tw`items-center gap-1`}>
                            <Text style={tw`text-2xl font-bold text-slate-900 text-center leading-tight`}>
                                {election.title}
                            </Text>
                        </View>
                        <View style={tw`flex-row items-center gap-2 rounded-full px-4 py-1.5 border ${election.status === 'ACTIVE' ? 'bg-green-100 border-green-200' : 'bg-slate-100 border-slate-200'}`}>
                            <Ionicons name={election.status === 'ACTIVE' ? "checkmark-circle" : "time"} size={16} color={election.status === 'ACTIVE' ? "#15803d" : "#475569"} />
                            <Text style={tw`text-sm font-bold uppercase tracking-wide ${election.status === 'ACTIVE' ? 'text-green-700' : 'text-slate-600'}`}>
                                {t('electionDetail.statusPrefix')}: {election.status}
                            </Text>
                        </View>
                    </View>
                </View>

                {/* Timeline Section */}
                <View style={tw`mx-4 my-4 rounded-xl bg-surface p-4 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center justify-between mb-4`}>
                        <View style={tw`flex-row items-center gap-2`}>
                            <Ionicons name="calendar-outline" size={20} color={tw.color('primary')} />
                            <Text style={tw`text-base font-bold text-slate-900`}>{t('electionDetail.timeline')}</Text>
                        </View>
                        {election.status === 'DRAFT' && String(election.createdBy) === String(user?.id) && (
                            <TouchableOpacity onPress={openEditModal} style={tw`flex-row items-center gap-1 bg-primary/10 px-3 py-1.5 rounded-full`}>
                                <Ionicons name="pencil" size={14} color={tw.color('primary')} />
                                <Text style={tw`text-xs font-bold text-primary`}>{t('electionDetail.edit')}</Text>
                            </TouchableOpacity>
                        )}
                    </View>
                    <View style={tw`flex-row justify-between pr-4`}>
                        <View style={tw`flex-col gap-1 border-l-4 border-primary pl-3`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase`}>{t('electionDetail.startDate')}</Text>
                            <Text style={tw`text-sm font-medium text-slate-900`}>{new Date(election.startTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR')}</Text>
                            <Text style={tw`text-sm text-slate-500`}>{new Date(election.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
                        </View>
                        <View style={tw`flex-col gap-1 border-l-4 border-slate-200 pl-3`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase`}>{t('electionDetail.endDate')}</Text>
                            <Text style={tw`text-sm font-medium text-slate-900`}>{new Date(election.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR')}</Text>
                            <Text style={tw`text-sm text-slate-500`}>{new Date(election.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
                        </View>
                    </View>
                </View>

                {/* Description */}
                <View style={tw`mx-4 mb-4 rounded-xl bg-surface p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-3`}>
                        <Ionicons name="document-text-outline" size={20} color={tw.color('primary')} />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('electionDetail.descriptionTitle')}</Text>
                    </View>
                    <Text style={tw`text-sm text-slate-600 leading-relaxed`}>
                        {election.description || t('electionDetail.defaultDescription')}
                    </Text>
                </View>

                {/* Rules & Compliance */}
                <View style={tw`mx-4 mb-4 rounded-xl bg-surface p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-3`}>
                        <Ionicons name="hammer-outline" size={20} color={tw.color('primary')} />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('electionDetail.rulesTitle')}</Text>
                    </View>
                    <View style={tw`flex-col gap-3`}>
                        <View style={tw`flex-row items-start gap-3`}>
                            <View style={tw`mt-0.5 w-5 h-5 rounded-full bg-primary/10 items-center justify-center`}>
                                <Ionicons name="checkmark" size={14} color={tw.color('primary')} />
                            </View>
                            <Text style={tw`flex-1 text-sm text-slate-600`}>{t('electionDetail.rule1')}</Text>
                        </View>
                        <View style={tw`flex-row items-start gap-3`}>
                            <View style={tw`mt-0.5 w-5 h-5 rounded-full bg-primary/10 items-center justify-center`}>
                                <Ionicons name="checkmark" size={14} color={tw.color('primary')} />
                            </View>
                            <Text style={tw`flex-1 text-sm text-slate-600`}>{t('electionDetail.rule2')}</Text>
                        </View>
                        <View style={tw`flex-row items-start gap-3`}>
                            <View style={tw`mt-0.5 w-5 h-5 rounded-full bg-primary/10 items-center justify-center`}>
                                <Ionicons name="checkmark" size={14} color={tw.color('primary')} />
                            </View>
                            <Text style={tw`flex-1 text-sm text-slate-600`}>{t('electionDetail.rule3')}</Text>
                        </View>
                    </View>
                </View>



                {/* Quick Actions */}
                {(election.status === 'ACTIVE' || election.status === 'CLOSED' || election.status === 'ARCHIVED') && (
                    <View style={tw`mx-4 mb-4 flex-row gap-3`}>
                        <TouchableOpacity
                            onPress={() => navigation.navigate('VoteVerification', { electionId })}
                            style={tw`flex-1 flex-row items-center justify-center gap-2 bg-surface border border-slate-200 rounded-xl py-3.5 shadow-sm`}
                        >
                            <Ionicons name="shield-checkmark-outline" size={18} color={tw.color('primary')} />
                            <Text style={tw`text-sm font-bold text-primary`}>{t('electionDetail.verifyVote')}</Text>
                        </TouchableOpacity>
                        {(election.status === 'CLOSED' || election.status === 'ARCHIVED') && (
                            <TouchableOpacity
                                onPress={() => navigation.navigate('ElectionResults', { electionId })}
                                style={tw`flex-1 flex-row items-center justify-center gap-2 bg-surface border border-slate-200 rounded-xl py-3.5 shadow-sm`}
                            >
                                <Ionicons name="bar-chart-outline" size={18} color={tw.color('primary')} />
                                <Text style={tw`text-sm font-bold text-primary`}>{t('electionDetail.viewResults')}</Text>
                            </TouchableOpacity>
                        )}
                    </View>
                )}

                {/*
                  Faz 4.15b — Eski leader-mode "Distributed Setup" + "Tally
                  İmzala + Aç" butonları KALDIRILDI. Bunlar Sprint 5.A
                  leader-mode'du (tüm N trustee tek owner cihazında):
                  threat-model bozuk (owner tüm payları görür, Q-of-N anlamsız)
                  ve gerçek dağıtık modda patlıyor. Doğru akış: her emanetçi
                  KENDİ cihazında GuardianScreen → "Anahtar Yükle" (ceremony)
                  ve "Hesaplamaya Katıl" (tally). UAT'ta kullanıcı yanlışlıkla
                  bu tuzağa bastı → kaldırıldı.
                */}

                {election.accessibility === 'PRIVATE' && (
                    <View style={tw`mx-4 mb-4 bg-orange-50 p-4 rounded-xl border border-orange-200`}>
                        <Text style={tw`font-bold text-orange-700 mb-2`}>{t('electionDetail.privateAccessTitle')}</Text>
                        <TextInput
                            style={tw`bg-surface border border-orange-300 rounded-lg p-3 text-center text-lg tracking-[8px] font-bold text-slate-800`}
                            placeholder={t('electionDetail.privateAccessPlaceholder')}
                            value={accessCode}
                            onChangeText={setAccessCode}
                            keyboardType="numeric"
                            maxLength={8}
                        />
                    </View>
                )}


                {/* Security Badge */}
                {election.status === 'DRAFT' && String(election.createdBy) === String(user?.id) && (
                    <View style={tw`mx-4 mb-4 rounded-xl bg-surface p-5 shadow-sm border border-slate-100`}>
                        <View style={tw`flex-row items-center justify-between mb-1`}>
                            <View style={tw`flex-row items-center gap-2`}>
                                <Ionicons name="people-outline" size={20} color={tw.color('primary')} />
                                <Text style={tw`text-base font-bold text-slate-900`}>Emanetçi Seçimi</Text>
                            </View>
                            {guardianCandidates.length === 0 && (
                                <TouchableOpacity
                                    onPress={fetchGuardianCandidates}
                                    style={tw`flex-row items-center gap-1 bg-primary/10 px-3 py-1.5 rounded-full`}
                                >
                                    {isLoadingCandidates
                                        ? <ActivityIndicator size="small" color={tw.color('primary')} />
                                        : <Ionicons name="refresh-outline" size={14} color={tw.color('primary')} />
                                    }
                                    <Text style={tw`text-xs font-bold text-primary`}>Listele</Text>
                                </TouchableOpacity>
                            )}
                        </View>
                        <Text style={tw`text-xs text-slate-400 mb-3`}>
                            Seçili: {selectedGuardianIds.length} kişi
                        </Text>

                        {guardianCandidates.length > 0 && (
                            <View style={tw`gap-2 mb-3`}>
                                {guardianCandidates.map((u: any) => {
                                    const selected = selectedGuardianIds.includes(String(u.id));
                                    return (
                                        <TouchableOpacity
                                            key={u.id}
                                            onPress={() => toggleGuardian(String(u.id))}
                                            style={tw`flex-row items-center gap-3 px-3 py-2.5 rounded-xl border ${selected ? 'bg-primary/10 border-primary/30' : 'bg-slate-50 border-slate-200'}`}
                                        >
                                            <View style={tw`w-8 h-8 rounded-full items-center justify-center ${selected ? 'bg-primary' : 'bg-slate-200'}`}>
                                                <Text style={tw`text-sm font-bold ${selected ? 'text-white' : 'text-slate-500'}`}>
                                                    {u.firstName?.[0]}{u.lastName?.[0]}
                                                </Text>
                                            </View>
                                            <View style={tw`flex-1`}>
                                                <Text style={tw`text-sm font-semibold text-slate-900`}>{u.firstName} {u.lastName}</Text>
                                                <Text style={tw`text-xs text-slate-400`}>{u.email}</Text>
                                            </View>
                                            {selected && <Ionicons name="checkmark-circle" size={20} color={tw.color('primary')} />}
                                        </TouchableOpacity>
                                    );
                                })}
                            </View>
                        )}

                        <TouchableOpacity
                            onPress={handleAssignManualGuardians}
                            disabled={isAssigningGuardians || selectedGuardianIds.length < 2}
                            style={tw`flex-row items-center justify-center gap-2 rounded-lg bg-primary/10 py-3 border border-primary/20 ${(isAssigningGuardians || selectedGuardianIds.length < 2) ? 'opacity-50' : ''}`}
                        >
                            {isAssigningGuardians ? (
                                <ActivityIndicator color={tw.color('primary')} />
                            ) : (
                                <Ionicons name="checkmark-circle-outline" size={18} color={tw.color('primary')} />
                            )}
                            <Text style={tw`text-sm font-bold text-primary`}>
                                Emanetçileri Ata ({selectedGuardianIds.length})
                            </Text>
                        </TouchableOpacity>
                    </View>
                )}

                <View style={tw`mx-4 mb-6 items-center pt-2 opacity-70`}>
                    <View style={tw`flex-row items-center gap-2`}>
                        <Ionicons name="lock-closed" size={16} color={tw.color('primary')} />
                        <Text style={tw`font-bold text-sm text-primary`}>{t('electionDetail.securedBy')}</Text>
                    </View>
                    <Text style={tw`text-xs text-center text-slate-500 max-w-[250px] mt-2`}>
                        {t('electionDetail.securedDesc')}
                    </Text>
                </View>
            </ScrollView>

            {/* Sticky Footer — bağlamsal: ölü/pasif "Vote Now" yerine duruma
                göre eylem. SCHEDULED/CANCELLED'da footer hiç gösterilmez. */}
            {(() => {
                const isDraftOwner = election.status === 'DRAFT'
                    && String(election.createdBy) === String(user?.id);
                const isActive = election.status === 'ACTIVE';
                const isClosedLike = election.status === 'CLOSED' || election.status === 'ARCHIVED';

                if (!isDraftOwner && !isActive && !isClosedLike) return null;

                return (
                    <View style={tw`absolute bottom-0 w-full bg-surface/95 p-4 border-t border-slate-200 shadow-md`}>
                        {isDraftOwner ? (
                            <TouchableOpacity
                                style={tw`flex-row items-center justify-center gap-2 rounded-lg bg-primary py-3.5 shadow-sm`}
                                onPress={handlePublish}
                                disabled={isPublishing}
                            >
                                <Ionicons name="send" size={20} color="#fff" />
                                <Text style={tw`text-base font-bold text-white tracking-wide`}>
                                    {isPublishing ? t('notifications.saving') : t('electionDetail.publish')}
                                </Text>
                            </TouchableOpacity>
                        ) : isActive ? (
                            <TouchableOpacity
                                style={tw`flex-row items-center justify-center gap-2 rounded-lg bg-primary py-3.5 shadow-sm ${isVerifying ? 'opacity-50' : ''}`}
                                onPress={handleProceed}
                                disabled={isVerifying}
                            >
                                <Ionicons name="checkbox-outline" size={20} color="#fff" />
                                <Text style={tw`text-base font-bold text-white tracking-wide`}>
                                    {isVerifying ? t('electionDetail.verify') : t('electionDetail.voteNow')}
                                </Text>
                            </TouchableOpacity>
                        ) : (
                            <TouchableOpacity
                                style={tw`flex-row items-center justify-center gap-2 rounded-lg bg-primary py-3.5 shadow-sm`}
                                onPress={() => navigation.navigate('ElectionResults', { electionId })}
                            >
                                <Ionicons name="bar-chart-outline" size={20} color="#fff" />
                                <Text style={tw`text-base font-bold text-white tracking-wide`}>
                                    {t('electionDetail.viewResults')}
                                </Text>
                            </TouchableOpacity>
                        )}
                    </View>
                );
            })()}

            {/* Edit Dates Modal */}
            <Modal visible={isEditModalVisible} transparent animationType="slide">
                <View style={tw`flex-1 bg-black/50 justify-center px-4`}>
                    <View style={tw`bg-surface rounded-2xl p-6 shadow-xl`}>
                        <View style={tw`flex-row items-center justify-between mb-6`}>
                            <Text style={tw`text-xl font-bold text-slate-900`}>{t('electionDetail.updateDatesTitle')}</Text>
                            <TouchableOpacity onPress={() => setIsEditModalVisible(false)} style={tw`p-2 bg-slate-100 rounded-full`}>
                                <Ionicons name="close" size={20} color="#64748b" />
                            </TouchableOpacity>
                        </View>

                        <View style={tw`gap-4 mb-6`}>
                            {/* Start Time */}
                            <View style={tw`gap-2`}>
                                <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('createElection.startLabel')}</Text>
                                <View style={tw`flex-row gap-2`}>
                                    <TouchableOpacity
                                        onPress={() => { setPickerMode('date'); setShowPicker('start'); }}
                                        style={tw`flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                    >
                                        <Ionicons name="calendar-outline" size={18} color={tw.color('primary')} />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(editStartTime)}</Text>
                                    </TouchableOpacity>
                                    <TouchableOpacity
                                        onPress={() => { setPickerMode('time'); setShowPicker('start'); }}
                                        style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                    >
                                        <Ionicons name="time-outline" size={18} color={tw.color('primary')} />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(editStartTime)}</Text>
                                    </TouchableOpacity>
                                </View>
                            </View>

                            {/* End Time */}
                            <View style={tw`gap-2`}>
                                <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('createElection.endLabel')}</Text>
                                <View style={tw`flex-row gap-2`}>
                                    <TouchableOpacity
                                        onPress={() => { setPickerMode('date'); setShowPicker('end'); }}
                                        style={tw`flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                    >
                                        <Ionicons name="calendar-outline" size={18} color={tw.color('primary')} />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(editEndTime)}</Text>
                                    </TouchableOpacity>
                                    <TouchableOpacity
                                        onPress={() => { setPickerMode('time'); setShowPicker('end'); }}
                                        style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                    >
                                        <Ionicons name="time-outline" size={18} color={tw.color('primary')} />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(editEndTime)}</Text>
                                    </TouchableOpacity>
                                </View>
                            </View>
                        </View>

                        <TouchableOpacity
                            style={tw`bg-primary items-center justify-center p-4 rounded-xl flex-row gap-2`}
                            onPress={handleUpdateDates}
                            disabled={isUpdating}
                        >
                            {isUpdating ? <ActivityIndicator color="#fff" size="small" /> : <Ionicons name="save" size={20} color="#fff" />}
                            <Text style={tw`text-white font-bold text-base`}>{isUpdating ? t('notifications.saving') : t('electionDetail.save')}</Text>
                        </TouchableOpacity>
                    </View>
                </View>

                {showPicker && (
                    <DateTimePicker
                        value={showPicker === 'start' ? editStartTime : editEndTime}
                        mode={pickerMode}
                        display="spinner"
                        is24Hour={true}
                        onChange={(event, date) => {
                            if (Platform.OS === 'android') setShowPicker(null);
                            if (date) {
                                if (showPicker === 'start') setEditStartTime(date);
                                else setEditEndTime(date);
                            }
                        }}
                    />
                )}
            </Modal>
        </View>
    );
};
