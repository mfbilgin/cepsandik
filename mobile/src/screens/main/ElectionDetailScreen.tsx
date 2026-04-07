import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Alert, TextInput, Modal, Platform } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import tw from 'twrnc';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n/LanguageContext';

export const ElectionDetailScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const electionId = route.params?.electionId;

    const [election, setElection] = useState<any>(null);
    const [accessCode, setAccessCode] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isVerifying, setIsVerifying] = useState(false);
    const [isPublishing, setIsPublishing] = useState(false);
    const { user } = useAuth();

    // Edit Dates State
    const [isEditModalVisible, setIsEditModalVisible] = useState(false);
    const [editStartTime, setEditStartTime] = useState(new Date());
    const [editEndTime, setEditEndTime] = useState(new Date(Date.now() + 24 * 60 * 60 * 1000));
    const [showPicker, setShowPicker] = useState<'start' | 'end' | null>(null);
    const [pickerMode, setPickerMode] = useState<'date' | 'time'>('date');
    const [isUpdating, setIsUpdating] = useState(false);
    const { t, language } = useI18n();

    const formatDate = (date: Date) => date.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
    const formatTime = (date: Date) => date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        fetchDetail();
    }, []);

    const fetchDetail = async () => {
        try {
            const res = await api.get(`/elections/${electionId}`);
            setElection(res.data?.data || null);
        } catch (e) {
            console.error(e);
            Alert.alert(t('auth.twoFactor.errorTitle'), t('electionDetail.fetchError'));
            navigation.goBack();
        } finally {
            setIsLoading(false);
        }
    };

    const handleProceed = async () => {
        if (election.accessibility === 'PRIVATE') {
            if (!accessCode) {
                Alert.alert(t('auth.login.missingTitle'), t('electionDetail.privateCodeRequired'));
                return;
            }
            setIsVerifying(true);
            try {
                await api.post(`/elections/${electionId}/verify-access`, { code: accessCode });
            } catch (e) {
                Alert.alert(t('auth.twoFactor.errorTitle'), t('electionDetail.invalidCode'));
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
            Alert.alert(t('security.successTitle'), t('electionDetail.published'));
            fetchDetail(); // Yeniden yükle ki status SCHEDULED olsun
        } catch (e: any) {
            Alert.alert(t('auth.twoFactor.errorTitle'), e.response?.data?.message || t('electionDetail.publishFail'));
        } finally {
            setIsPublishing(false);
        }
    };

    const handleUpdateDates = async () => {
        if (editEndTime <= editStartTime) {
            Alert.alert(t('auth.twoFactor.errorTitle'), t('electionDetail.invalidDateRange'));
            return;
        }
        setIsUpdating(true);
        try {
            await api.put(`/elections/${electionId}`, {
                startTime: editStartTime.toISOString(),
                endTime: editEndTime.toISOString(),
            });
            Alert.alert(t('security.successTitle'), t('electionDetail.updateDatesSuccess'));
            setIsEditModalVisible(false);
            fetchDetail();
        } catch (e: any) {
            Alert.alert(t('auth.twoFactor.errorTitle'), e.response?.data?.message || t('electionDetail.updateDatesFail'));
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
            <View style={tw`flex-1 items-center justify-center bg-[#f6f7f8]`}>
                <ActivityIndicator size="large" color="#1162d4" />
            </View>
        );
    }

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]  max-w-md mx-auto w-full`}>
            {/* Top App Bar */}
            <View style={tw`flex-row items-center bg-white p-4 shadow-sm z-10 pt-10`}>
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
                <View style={tw`bg-white pb-6 px-4 pt-2 mb-2`}>
                    <View style={tw`flex-col items-center gap-4`}>
                        <View style={tw`w-24 h-24 items-center justify-center rounded-full bg-[#1162d4]/10`}>
                            <Ionicons name="checkbox-outline" size={48} color="#1162d4" />
                        </View>
                        <View style={tw`items-center gap-1`}>
                            <Text style={tw`text-2xl font-bold text-slate-900 text-center leading-tight`}>
                                {election.title}
                            </Text>
                            <View style={tw`flex-row items-center justify-center gap-2 mt-2`}>
                                <Ionicons name="shield-checkmark" size={16} color="#64748b" />
                                <Text style={tw`text-sm font-medium text-slate-500`}>{t('electionDetail.badgeText')}</Text>
                            </View>
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
                <View style={tw`mx-4 my-4 rounded-xl bg-white p-4 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center justify-between mb-4`}>
                        <View style={tw`flex-row items-center gap-2`}>
                            <Ionicons name="calendar-outline" size={20} color="#1162d4" />
                            <Text style={tw`text-base font-bold text-slate-900`}>{t('electionDetail.timeline')}</Text>
                        </View>
                        {election.status === 'DRAFT' && String(election.createdBy) === String(user?.id) && (
                            <TouchableOpacity onPress={openEditModal} style={tw`flex-row items-center gap-1 bg-[#1162d4]/10 px-3 py-1.5 rounded-full`}>
                                <Ionicons name="pencil" size={14} color="#1162d4" />
                                <Text style={tw`text-xs font-bold text-[#1162d4]`}>{t('electionDetail.edit')}</Text>
                            </TouchableOpacity>
                        )}
                    </View>
                    <View style={tw`flex-row justify-between pr-4`}>
                        <View style={tw`flex-col gap-1 border-l-4 border-[#1162d4] pl-3`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase`}>{t('electionDetail.startDate')}</Text>
                            <Text style={tw`text-sm font-medium text-slate-900`}>{new Date(election.startTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR')}</Text>
                            <Text style={tw`text-sm text-slate-500`}>{new Date(election.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text>
                        </View>
                        <View style={tw`flex-col gap-1 border-l-4 border-slate-200 pl-3`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase`}>{t('electionDetail.endDate')}</Text>
                            <Text style={tw`text-sm font-medium text-slate-900`}>{new Date(election.endTime).toLocaleDateString(language === 'en' ? 'en-US' : 'tr-TR')}</Text>
                            <Text style={tw`text-sm text-slate-500`}>{new Date(election.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text>
                        </View>
                    </View>
                </View>

                {/* Description */}
                <View style={tw`mx-4 mb-4 rounded-xl bg-white p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-3`}>
                        <Ionicons name="document-text-outline" size={20} color="#1162d4" />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('electionDetail.descriptionTitle')}</Text>
                    </View>
                    <Text style={tw`text-sm text-slate-600 leading-relaxed`}>
                        {election.description || t('electionDetail.defaultDescription')}
                    </Text>
                </View>

                {/* Rules & Compliance */}
                <View style={tw`mx-4 mb-4 rounded-xl bg-white p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-3`}>
                        <Ionicons name="hammer-outline" size={20} color="#1162d4" />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('electionDetail.rulesTitle')}</Text>
                    </View>
                    <View style={tw`flex-col gap-3`}>
                        <View style={tw`flex-row items-start gap-3`}>
                            <View style={tw`mt-0.5 w-5 h-5 rounded-full bg-[#1162d4]/10 items-center justify-center`}>
                                <Ionicons name="checkmark" size={14} color="#1162d4" />
                            </View>
                            <Text style={tw`flex-1 text-sm text-slate-600`}>{t('electionDetail.rule1')}</Text>
                        </View>
                        <View style={tw`flex-row items-start gap-3`}>
                            <View style={tw`mt-0.5 w-5 h-5 rounded-full bg-[#1162d4]/10 items-center justify-center`}>
                                <Ionicons name="checkmark" size={14} color="#1162d4" />
                            </View>
                            <Text style={tw`flex-1 text-sm text-slate-600`}>{t('electionDetail.rule2')}</Text>
                        </View>
                        <View style={tw`flex-row items-start gap-3`}>
                            <View style={tw`mt-0.5 w-5 h-5 rounded-full bg-[#1162d4]/10 items-center justify-center`}>
                                <Ionicons name="checkmark" size={14} color="#1162d4" />
                            </View>
                            <Text style={tw`flex-1 text-sm text-slate-600`}>{t('electionDetail.rule3')}</Text>
                        </View>
                    </View>
                </View>

                {/* Security Badge */}
                <View style={tw`mx-4 mb-6 items-center pt-2 opacity-70`}>
                    <View style={tw`flex-row items-center gap-2`}>
                        <Ionicons name="lock-closed" size={16} color="#1162d4" />
                        <Text style={tw`font-bold text-sm text-[#1162d4]`}>{t('electionDetail.securedBy')}</Text>
                    </View>
                    <Text style={tw`text-xs text-center text-slate-500 max-w-[250px] mt-2`}>
                        {t('electionDetail.securedDesc')}
                    </Text>
                </View>

                {election.accessibility === 'PRIVATE' && (
                    <View style={tw`mx-4 mb-4 bg-orange-50 p-4 rounded-xl border border-orange-200`}>
                        <Text style={tw`font-bold text-orange-700 mb-2`}>{t('electionDetail.privateAccessTitle')}</Text>
                        <TextInput
                            style={tw`bg-white border border-orange-300 rounded-lg p-3 text-center text-lg tracking-[8px] font-bold text-slate-800`}
                            placeholder={t('electionDetail.privateAccessPlaceholder')}
                            value={accessCode}
                            onChangeText={setAccessCode}
                            keyboardType="numeric"
                            maxLength={8}
                        />
                    </View>
                )}
            </ScrollView>

            {/* Sticky Footer */}
            <View style={tw`absolute bottom-0 w-full bg-white/95 p-4 border-t border-slate-200 shadow-md`}>
                {election.status === 'DRAFT' && String(election.createdBy) === String(user?.id) ? (
                    <TouchableOpacity
                        style={tw`flex-row items-center justify-center gap-2 rounded-lg bg-[#1162d4] py-3.5 shadow-sm`}
                        onPress={handlePublish}
                        disabled={isPublishing}
                    >
                        <Ionicons name="send" size={20} color="#fff" />
                        <Text style={tw`text-base font-bold text-white tracking-wide`}>
                            {isPublishing ? t('notifications.saving') : t('electionDetail.publish')}
                        </Text>
                    </TouchableOpacity>
                ) : (
                    <TouchableOpacity
                        style={tw`flex-row items-center justify-center gap-2 rounded-lg bg-[#1162d4] py-3.5 shadow-sm ${election.status !== 'ACTIVE' ? 'opacity-50' : ''}`}
                        onPress={handleProceed}
                        disabled={election.status !== 'ACTIVE' || isVerifying}
                    >
                        <Ionicons name="checkbox-outline" size={20} color="#fff" />
                        <Text style={tw`text-base font-bold text-white tracking-wide`}>
                            {isVerifying ? t('electionDetail.verify') : t('electionDetail.voteNow')}
                        </Text>
                    </TouchableOpacity>
                )}
            </View>

            {/* Edit Dates Modal */}
            <Modal visible={isEditModalVisible} transparent animationType="slide">
                <View style={tw`flex-1 bg-black/50 justify-center px-4`}>
                    <View style={tw`bg-white rounded-2xl p-6 shadow-xl`}>
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
                                        <Ionicons name="calendar-outline" size={18} color="#1162d4" />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(editStartTime)}</Text>
                                    </TouchableOpacity>
                                    <TouchableOpacity
                                        onPress={() => { setPickerMode('time'); setShowPicker('start'); }}
                                        style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                    >
                                        <Ionicons name="time-outline" size={18} color="#1162d4" />
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
                                        <Ionicons name="calendar-outline" size={18} color="#1162d4" />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(editEndTime)}</Text>
                                    </TouchableOpacity>
                                    <TouchableOpacity
                                        onPress={() => { setPickerMode('time'); setShowPicker('end'); }}
                                        style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                    >
                                        <Ionicons name="time-outline" size={18} color="#1162d4" />
                                        <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(editEndTime)}</Text>
                                    </TouchableOpacity>
                                </View>
                            </View>
                        </View>

                        <TouchableOpacity
                            style={tw`bg-[#1162d4] items-center justify-center p-4 rounded-xl flex-row gap-2`}
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
