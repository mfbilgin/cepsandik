import React, { useState, useEffect, useCallback } from 'react';
import {
    View, Text, TextInput, TouchableOpacity, SafeAreaView,
    ScrollView, ActivityIndicator, Platform, StatusBar, Alert,
    Modal, FlatList
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import DateTimePicker from '@react-native-community/datetimepicker';
import Toast from 'react-native-toast-message';
import * as Clipboard from 'expo-clipboard';
import { tw } from '../../utils/tailwind';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';

type CandidateType = 'PERSON' | 'TEXT_OPTION' | 'IMAGE_OPTION';
type ElectionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE';

interface Candidate {
    name: string;
    description: string;
    imageUrl: string;
    candidateType: CandidateType;
    memberUserId: string;
}

export const CreateElectionScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { communityId } = route.params || {};
    const { t } = useI18n();
    const { showDialog } = useUI();

    // === Step ===
    const [step, setStep] = useState(1); // 1 = Basic info, 2 = Candidates

    // === Step 1: Basic Info ===
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [electionType, setElectionType] = useState<ElectionType>('SINGLE_CHOICE');
    const [candidateType, setCandidateType] = useState<CandidateType>('PERSON');
    const [startTime, setStartTime] = useState<Date>(() => {
        const d = new Date();
        d.setHours(d.getHours() + 1, 0, 0, 0);
        return d;
    });
    const [endTime, setEndTime] = useState<Date>(() => {
        const d = new Date();
        d.setHours(d.getHours() + 25, 0, 0, 0);
        return d;
    });
    const [showStartPicker, setShowStartPicker] = useState(false);
    const [showEndPicker, setShowEndPicker] = useState(false);
    const [startPickerMode, setStartPickerMode] = useState<'date' | 'time'>('date');
    const [endPickerMode, setEndPickerMode] = useState<'date' | 'time'>('date');

    // === Step 2: Candidates ===
    const [candidates, setCandidates] = useState<Candidate[]>([
        { name: '', description: '', imageUrl: '', candidateType: 'PERSON', memberUserId: '' },
        { name: '', description: '', imageUrl: '', candidateType: 'PERSON', memberUserId: '' },
    ]);
    const [communityMembers, setCommunityMembers] = useState<any[]>([]);
    const [membersLoading, setMembersLoading] = useState(false);
    const [memberPickerVisible, setMemberPickerVisible] = useState(false);
    const [pickerTargetIndex, setPickerTargetIndex] = useState(0);

    // === Creating State ===
    const [isCreating, setIsCreating] = useState(false);

    const fetchCommunityMembers = useCallback(async () => {
        if (!communityId) return;
        setMembersLoading(true);
        try {
            const res = await api.get(`/communities/${communityId}/members?size=100`);
            setCommunityMembers(res.data?.data?.content || []);
        } catch (e) {
            // silently fail
        } finally {
            setMembersLoading(false);
        }
    }, [communityId]);

    useEffect(() => {
        if (candidateType === 'PERSON') fetchCommunityMembers();
    }, [candidateType]);

    const formatDate = (d: Date) => d.toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const formatTime = (d: Date) => d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', hour12: false });

    const addCandidate = () => setCandidates(prev => [...prev, { name: '', description: '', imageUrl: '', candidateType, memberUserId: '' }]);
    const removeCandidate = (idx: number) => {
        if (candidates.length <= 2) {
            Toast.show({ type: 'info', text1: t('createElection.minCandidates') });
            return;
        }
        setCandidates(prev => prev.filter((_, i) => i !== idx));
    };
    const updateCandidate = (idx: number, field: keyof Candidate, value: string) => {
        setCandidates(prev => prev.map((c, i) => i === idx ? { ...c, [field]: value } : c));
    };

    const openMemberPicker = (idx: number) => {
        setPickerTargetIndex(idx);
        setMemberPickerVisible(true);
    };

    const selectMember = (member: any) => {
        const displayName = member.displayName || 
            (member.firstName && member.lastName ? `${member.firstName} ${member.lastName}` : null) ||
            member.userId || t('createElection.unknownMember');
        const avatarUrl = member.profileImage || member.avatarUrl || '';
        setCandidates(prev => prev.map((c, i) => i === pickerTargetIndex
            ? { ...c, name: displayName, imageUrl: avatarUrl, memberUserId: member.userId }
            : c
        ));
        setMemberPickerVisible(false);
    };

    const validateStep1 = () => {
        if (!title.trim()) { Toast.show({ type: 'error', text1: t('createElection.titleRequired') }); return false; }
        if (startTime <= new Date()) { Toast.show({ type: 'error', text1: t('createElection.startFuture') }); return false; }
        if (endTime <= startTime) { Toast.show({ type: 'error', text1: t('createElection.endAfterStart') }); return false; }
        return true;
    };

    const validateStep2 = () => {
        for (let i = 0; i < candidates.length; i++) {
            if (!candidates[i].name.trim()) { Toast.show({ type: 'error', text1: t('createElection.candidateNameRequired', { index: i + 1 }) }); return false; }
        }
        return true;
    };

    const handleCreate = async () => {
        if (!validateStep2()) return;
        setIsCreating(true);

        try {
            // 1. Create election
            const electionRes = await api.post('/elections', {
                title: title.trim(),
                description: description.trim() || null,
                communityId: communityId || null,
                type: electionType,
                participantType: 'PUBLIC',
                startTime: startTime.toISOString(),
                endTime: endTime.toISOString(),
                resultsPublic: true,
            });

            const electionId = electionRes.data?.data?.id;
            if (!electionId) throw new Error(t('createElection.createFailBody'));

            // 2. Add candidates
            for (let i = 0; i < candidates.length; i++) {
                const c = candidates[i];
                await api.post(`/elections/${electionId}/candidates`, {
                    name: c.name.trim(),
                    description: c.description.trim() || null,
                    imageUrl: c.imageUrl || null,
                    displayOrder: i,
                    candidateType: c.candidateType,
                    memberUserId: c.memberUserId || null,
                });
            }

            // 3. Topluluk bağımsız seçim → paylaşılabilir 6 haneli erişim kodu üret
            if (!communityId) {
                try {
                    const codeRes = await api.post(`/elections/${electionId}/access-codes`, {});
                    const accessCode = codeRes.data?.data?.code;
                    if (accessCode) {
                        showDialog({
                            title: t('createElection.codeReadyTitle') || 'Seçim Hazır',
                            message: (t('createElection.codeReadyBody') || 'Katılımcılar bu 6 haneli kodu ana sayfada girerek seçime erişebilir:\n\n{code}').replace('{code}', accessCode),
                            type: 'success',
                            confirmText: t('createElection.copyCode') || 'Kodu Kopyala',
                            cancelText: t('common.continue') || 'Devam',
                            onConfirm: async () => {
                                await Clipboard.setStringAsync(accessCode);
                                Toast.show({ type: 'success', text1: t('createElection.codeCopied') || 'Kod kopyalandı' });
                                navigation.navigate('ElectionDetail', { electionId });
                            },
                            onCancel: () => navigation.navigate('ElectionDetail', { electionId }),
                        });
                        return;
                    }
                } catch (codeErr) {
                    // Kod üretilemese de seçim oluşturuldu; sessiz geç
                    console.warn('Access code generation failed', codeErr);
                }
            }

            Toast.show({ type: 'success', text1: t('createElection.createSuccessTitle'), text2: t('createElection.createSuccessBody') });
            navigation.navigate('ElectionDetail', { electionId: electionId });
        } catch (e: any) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: e.response?.data?.message || t('createElection.createFailBody') });
        } finally {
            setIsCreating(false);
        }
    };

    const ELECTION_TYPES = [
        { key: 'SINGLE_CHOICE', label: t('createElection.stepType.singleLabel'), icon: 'radio-button-checked', desc: t('createElection.stepType.singleDesc') },
        { key: 'MULTIPLE_CHOICE', label: t('createElection.stepType.multiLabel'), icon: 'check-box', desc: t('createElection.stepType.multiDesc') },
    ] as const;

    const CANDIDATE_TYPES = [
        { key: 'PERSON', label: t('createElection.candidateType.personLabel'), icon: 'person', desc: t('createElection.candidateType.personDesc') },
        { key: 'TEXT_OPTION', label: t('createElection.candidateType.textLabel'), icon: 'text-fields', desc: t('createElection.candidateType.textDesc') },
        { key: 'IMAGE_OPTION', label: t('createElection.candidateType.imageLabel'), icon: 'image', desc: t('createElection.candidateType.imageDesc') },
    ] as const;

    return (
        <SafeAreaView style={[tw`flex-1 bg-background`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 }]}>
            {/* Header */}
            <View style={tw`flex-row items-center px-4 py-3 bg-surface border-b border-slate-200`}>
                <TouchableOpacity
                    onPress={() => step === 1 ? navigation.goBack() : setStep(1)}
                    style={tw`w-10 h-10 items-center justify-center rounded-full`}
                >
                    <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <Text style={tw`flex-1 text-lg font-bold text-center text-slate-900`}>
                    {step === 1 ? t('createElection.newElection') : t('createElection.setCandidates')}
                </Text>
                <View style={tw`flex-row gap-1`}>
                    {[1, 2].map(s => (
                        <View key={s} style={tw`w-2 h-2 rounded-full ${step === s ? 'bg-primary' : 'bg-slate-200'}`} />
                    ))}
                </View>
            </View>

            {step === 1 ? (
                <ScrollView contentContainerStyle={tw`p-4 gap-4 pb-32`} showsVerticalScrollIndicator={false}>
                    {/* Basic Info Card */}
                    <View style={tw`bg-surface rounded-2xl p-4 border border-slate-100 shadow-sm gap-4`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>{t('createElection.basicInfo')}</Text>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('createElection.titleLabel')}</Text>
                            <TextInput
                                value={title}
                                onChangeText={setTitle}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                placeholder={t('createElection.titlePlaceholder')}
                                placeholderTextColor="#94a3b8"
                                maxLength={200}
                            />
                        </View>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('createElection.descriptionLabel')}</Text>
                            <TextInput
                                value={description}
                                onChangeText={setDescription}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium min-h-[80px]`}
                                placeholder={t('createElection.descriptionPlaceholder')}
                                placeholderTextColor="#94a3b8"
                                multiline
                                textAlignVertical="top"
                                maxLength={2000}
                            />
                        </View>
                    </View>

                    {/* Election Type Card */}
                    <View style={tw`bg-surface rounded-2xl p-4 border border-slate-100 shadow-sm gap-3`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>{t('createElection.typeTitle')}</Text>
                        {ELECTION_TYPES.map(t => (
                            <TouchableOpacity
                                key={t.key}
                                onPress={() => setElectionType(t.key)}
                                style={tw`flex-row items-center gap-3 p-3 rounded-xl border ${electionType === t.key ? 'border-primary bg-primary/5' : 'border-slate-200 bg-slate-50'}`}
                            >
                                <MaterialIcons name={t.icon as any} size={22} color={electionType === t.key ? (tw.color('primary') as string) : '#94a3b8'} />
                                <View style={tw`flex-1`}>
                                    <Text style={tw`font-semibold ${electionType === t.key ? 'text-primary' : 'text-slate-700'}`}>{t.label}</Text>
                                    <Text style={tw`text-xs text-slate-400`}>{t.desc}</Text>
                                </View>
                                {electionType === t.key && <MaterialIcons name="check-circle" size={20} color={tw.color('primary')} />}
                            </TouchableOpacity>
                        ))}
                    </View>

                    {/* Candidate Type Card */}
                    <View style={tw`bg-surface rounded-2xl p-4 border border-slate-100 shadow-sm gap-3`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>{t('createElection.candidateTypeTitle')}</Text>
                        {CANDIDATE_TYPES.map(t => (
                            <TouchableOpacity
                                key={t.key}
                                onPress={() => {
                                    setCandidateType(t.key);
                                    setCandidates(prev => prev.map(c => ({ ...c, candidateType: t.key, name: '', memberUserId: '' })));
                                }}
                                style={tw`flex-row items-center gap-3 p-3 rounded-xl border ${candidateType === t.key ? 'border-primary bg-primary/5' : 'border-slate-200 bg-slate-50'}`}
                            >
                                <MaterialIcons name={t.icon as any} size={22} color={candidateType === t.key ? (tw.color('primary') as string) : '#94a3b8'} />
                                <View style={tw`flex-1`}>
                                    <Text style={tw`font-semibold ${candidateType === t.key ? 'text-primary' : 'text-slate-700'}`}>{t.label}</Text>
                                    <Text style={tw`text-xs text-slate-400`}>{t.desc}</Text>
                                </View>
                                {candidateType === t.key && <MaterialIcons name="check-circle" size={20} color={tw.color('primary')} />}
                            </TouchableOpacity>
                        ))}
                    </View>

                    {/* Date/Time Card */}
                    <View style={tw`bg-surface rounded-2xl p-4 border border-slate-100 shadow-sm gap-4`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>{t('createElection.timeRange')}</Text>

                        {/* Start Time */}
                        <View style={tw`gap-2`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('createElection.startLabel')}</Text>
                            <View style={tw`flex-row gap-2`}>
                                <TouchableOpacity
                                    onPress={() => { setStartPickerMode('date'); setShowStartPicker(true); }}
                                    style={tw`flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="calendar-today" size={18} color={tw.color('primary')} />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(startTime)}</Text>
                                </TouchableOpacity>
                                <TouchableOpacity
                                    onPress={() => { setStartPickerMode('time'); setShowStartPicker(true); }}
                                    style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="schedule" size={18} color={tw.color('primary')} />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(startTime)}</Text>
                                </TouchableOpacity>
                            </View>
                        </View>

                        {/* End Time */}
                        <View style={tw`gap-2`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>{t('createElection.endLabel')}</Text>
                            <View style={tw`flex-row gap-2`}>
                                <TouchableOpacity
                                    onPress={() => { setEndPickerMode('date'); setShowEndPicker(true); }}
                                    style={tw`flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="calendar-today" size={18} color={tw.color('primary')} />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(endTime)}</Text>
                                </TouchableOpacity>
                                <TouchableOpacity
                                    onPress={() => { setEndPickerMode('time'); setShowEndPicker(true); }}
                                    style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="schedule" size={18} color={tw.color('primary')} />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(endTime)}</Text>
                                </TouchableOpacity>
                            </View>
                        </View>

                        {showStartPicker && (
                            <DateTimePicker
                                value={startTime}
                                mode={startPickerMode}
                                is24Hour={true}
                                minimumDate={new Date()}
                                onChange={(_, date) => { setShowStartPicker(false); if (date) setStartTime(date); }}
                            />
                        )}
                        {showEndPicker && (
                            <DateTimePicker
                                value={endTime}
                                mode={endPickerMode}
                                is24Hour={true}
                                minimumDate={startTime}
                                onChange={(_, date) => { setShowEndPicker(false); if (date) setEndTime(date); }}
                            />
                        )}
                    </View>
                </ScrollView>
            ) : (
                <ScrollView contentContainerStyle={tw`p-4 gap-4 pb-32`} showsVerticalScrollIndicator={false}>
                    <View style={tw`bg-primary/5 border border-primary/20 rounded-xl p-3 flex-row items-center gap-2`}>
                        <MaterialIcons name="info" size={18} color={tw.color('primary')} />
                        <Text style={tw`text-primary text-sm font-medium flex-1`}>
                            {candidateType === 'PERSON' ? t('createElection.candidateInfoPerson') :
                                candidateType === 'TEXT_OPTION' ? t('createElection.candidateInfoText') :
                                    t('createElection.candidateInfoImage')}
                        </Text>
                    </View>

                    {candidates.map((candidate, idx) => (
                        <View key={idx} style={tw`bg-surface rounded-2xl p-4 border border-slate-100 shadow-sm gap-3`}>
                            <View style={tw`flex-row items-center justify-between`}>
                                <Text style={tw`text-slate-900 font-bold`}>{t('createElection.candidateN', { index: idx + 1 })}</Text>
                                <TouchableOpacity onPress={() => removeCandidate(idx)} style={tw`w-8 h-8 rounded-full bg-red-50 items-center justify-center`}>
                                    <MaterialIcons name="close" size={16} color="#ef4444" />
                                </TouchableOpacity>
                            </View>

                            {candidateType === 'PERSON' ? (
                                <TouchableOpacity
                                    onPress={() => openMemberPicker(idx)}
                                    style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 flex-row items-center gap-3`}
                                >
                                    <MaterialIcons name="person" size={22} color={tw.color('primary')} />
                                    <Text style={tw`flex-1 ${candidate.name ? 'text-slate-900 font-medium' : 'text-slate-400'}`}>
                                        {candidate.name || t('createElection.selectMember')}
                                    </Text>
                                    <MaterialIcons name="chevron-right" size={20} color="#94a3b8" />
                                </TouchableOpacity>
                            ) : (
                                <>
                                    <TextInput
                                        value={candidate.name}
                                        onChangeText={v => updateCandidate(idx, 'name', v)}
                                        style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                        placeholder={candidateType === 'IMAGE_OPTION' ? t('createElection.imageLabelPlaceholder') : t('createElection.optionNamePlaceholder')}
                                        placeholderTextColor="#94a3b8"
                                        maxLength={200}
                                    />
                                    {candidateType === 'IMAGE_OPTION' && (
                                        <TextInput
                                            value={candidate.imageUrl}
                                            onChangeText={v => updateCandidate(idx, 'imageUrl', v)}
                                            style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                            placeholder={t('createElection.imageUrlPlaceholder')}
                                            placeholderTextColor="#94a3b8"
                                            autoCapitalize="none"
                                            keyboardType="url"
                                        />
                                    )}
                                </>
                            )}
                        </View>
                    ))}

                    <TouchableOpacity
                        onPress={addCandidate}
                        style={tw`bg-surface border-2 border-dashed border-primary/40 rounded-2xl p-4 flex-row items-center justify-center gap-2`}
                    >
                        <MaterialIcons name="add" size={22} color={tw.color('primary')} />
                        <Text style={tw`text-primary font-semibold`}>{t('createElection.addCandidate')}</Text>
                    </TouchableOpacity>
                </ScrollView>
            )}

            {/* Bottom CTA */}
            <View style={tw`absolute bottom-0 left-0 right-0 bg-surface px-4 pt-3 pb-8 border-t border-slate-200`}>
                <TouchableOpacity
                    onPress={step === 1 ? () => { if (validateStep1()) setStep(2); } : handleCreate}
                    disabled={isCreating}
                    style={tw`w-full h-14 bg-primary rounded-2xl items-center justify-center shadow-lg shadow-blue-500/30 ${isCreating ? 'opacity-70' : ''}`}
                >
                    {isCreating ? (
                        <ActivityIndicator color="white" />
                    ) : (
                        <Text style={tw`text-white font-bold text-base tracking-wide`}>
                            {step === 1 ? t('createElection.continueCandidates') : t('createElection.createElection')}
                        </Text>
                    )}
                </TouchableOpacity>
            </View>

            {/* Member Picker Modal */}
            <Modal visible={memberPickerVisible} animationType="slide" transparent onRequestClose={() => setMemberPickerVisible(false)}>
                <View style={tw`flex-1 justify-end`}>
                    <TouchableOpacity style={tw`flex-1`} onPress={() => setMemberPickerVisible(false)} />
                    <View style={tw`bg-surface rounded-t-3xl max-h-[60%] border-t border-slate-100 shadow-2xl`}>
                        <View style={tw`flex-row items-center px-4 py-4 border-b border-slate-100`}>
                            <Text style={tw`flex-1 text-base font-bold text-slate-900`}>{t('createElection.pickMember')}</Text>
                            <TouchableOpacity onPress={() => setMemberPickerVisible(false)}>
                                <MaterialIcons name="close" size={24} color="#64748b" />
                            </TouchableOpacity>
                        </View>
                        {membersLoading ? (
                            <ActivityIndicator style={tw`p-8`} color={tw.color('primary')} />
                        ) : communityMembers.length === 0 ? (
                            <Text style={tw`text-center text-slate-400 p-8`}>{t('createElection.membersLoadFail')}</Text>
                        ) : (
                            <FlatList
                                data={communityMembers}
                                keyExtractor={item => item.userId || item.id?.toString()}
                                renderItem={({ item }) => (
                                    <TouchableOpacity
                                        onPress={() => selectMember(item)}
                                        style={tw`flex-row items-center gap-3 px-4 py-3 border-b border-slate-100`}
                                    >
                                        <View style={tw`w-10 h-10 bg-primary/10 rounded-full items-center justify-center`}>
                                            <MaterialIcons name="person" size={22} color={tw.color('primary')} />
                                        </View>
                                        <View>
                                            <Text style={tw`text-slate-900 font-semibold`}>
                                                {item.displayName || (item.firstName && item.lastName ? `${item.firstName} ${item.lastName}` : item.userId)}
                                            </Text>
                                            <Text style={tw`text-slate-400 text-xs`}>{item.role}</Text>
                                        </View>
                                    </TouchableOpacity>
                                )}
                                contentContainerStyle={tw`pb-4`}
                            />
                        )}
                    </View>
                </View>
            </Modal>
        </SafeAreaView>
    );
};
