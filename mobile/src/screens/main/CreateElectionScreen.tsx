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
import tw from 'twrnc';
import { api } from '../../services/api';

type CandidateType = 'PERSON' | 'TEXT_OPTION' | 'IMAGE_OPTION';
type ElectionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RANKED_CHOICE';

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
    const formatTime = (d: Date) => d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });

    const addCandidate = () => setCandidates(prev => [...prev, { name: '', description: '', imageUrl: '', candidateType, memberUserId: '' }]);
    const removeCandidate = (idx: number) => {
        if (candidates.length <= 2) {
            Toast.show({ type: 'info', text1: 'Minimum 2 aday gerekli' });
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
        const displayName = member.displayName || member.userId || 'Bilinmeyen Üye';
        const avatarUrl = member.avatarUrl || '';
        setCandidates(prev => prev.map((c, i) => i === pickerTargetIndex
            ? { ...c, name: displayName, imageUrl: avatarUrl, memberUserId: member.userId }
            : c
        ));
        setMemberPickerVisible(false);
    };

    const validateStep1 = () => {
        if (!title.trim()) { Toast.show({ type: 'error', text1: 'Başlık zorunludur' }); return false; }
        if (startTime <= new Date()) { Toast.show({ type: 'error', text1: 'Başlangıç zamanı gelecekte olmalı' }); return false; }
        if (endTime <= startTime) { Toast.show({ type: 'error', text1: 'Bitiş zamanı başlangıçtan sonra olmalı' }); return false; }
        return true;
    };

    const validateStep2 = () => {
        for (let i = 0; i < candidates.length; i++) {
            if (!candidates[i].name.trim()) { Toast.show({ type: 'error', text1: `Aday ${i + 1} için ad zorunludur` }); return false; }
        }
        return true;
    };

    const formatLocalISO = (date: Date) => {
        const tzOffset = date.getTimezoneOffset() * 60000;
        return new Date(date.getTime() - tzOffset).toISOString().slice(0, 19);
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
                startTime: formatLocalISO(startTime),
                endTime: formatLocalISO(endTime),
                resultsPublic: true,
                anonymousVoting: true,
            });

            const electionId = electionRes.data?.data?.id;
            if (!electionId) throw new Error('Seçim oluşturulamadı');

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

            Toast.show({ type: 'success', text1: 'Seçim oluşturuldu!', text2: 'Adaylar başarıyla eklendi.' });
            navigation.navigate('ElectionDetail', { electionId: electionId });
        } catch (e: any) {
            Toast.show({ type: 'error', text1: 'Hata', text2: e.response?.data?.message || 'Seçim oluşturulamadı.' });
        } finally {
            setIsCreating(false);
        }
    };

    const ELECTION_TYPES = [
        { key: 'SINGLE_CHOICE', label: 'Tek Seçim', icon: 'radio-button-checked', desc: 'En fazla 1 aday' },
        { key: 'MULTIPLE_CHOICE', label: 'Çoklu Seçim', icon: 'check-box', desc: 'Birden fazla aday' },
        { key: 'RANKED_CHOICE', label: 'Sıralı Seçim', icon: 'format-list-numbered', desc: 'Tercih sıralaması' },
    ] as const;

    const CANDIDATE_TYPES = [
        { key: 'PERSON', label: 'Kişi', icon: 'person', desc: 'Topluluk üyesinden seç' },
        { key: 'TEXT_OPTION', label: 'Metin Seçeneği', icon: 'text-fields', desc: 'Serbest metin (Evet/Hayır vb.)' },
        { key: 'IMAGE_OPTION', label: 'Görsel Seçenek', icon: 'image', desc: 'Görselle temsil edilen seçenek' },
    ] as const;

    return (
        <SafeAreaView style={[tw`flex-1 bg-[#f6f7f8]`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 }]}>
            {/* Header */}
            <View style={tw`flex-row items-center px-4 py-3 bg-white border-b border-slate-200`}>
                <TouchableOpacity
                    onPress={() => step === 1 ? navigation.goBack() : setStep(1)}
                    style={tw`w-10 h-10 items-center justify-center rounded-full`}
                >
                    <MaterialIcons name="arrow-back" size={24} color="#0f172a" />
                </TouchableOpacity>
                <Text style={tw`flex-1 text-lg font-bold text-center text-slate-900`}>
                    {step === 1 ? 'Yeni Seçim' : 'Adayları Belirle'}
                </Text>
                <View style={tw`flex-row gap-1`}>
                    {[1, 2].map(s => (
                        <View key={s} style={tw`w-2 h-2 rounded-full ${step === s ? 'bg-[#1162d4]' : 'bg-slate-200'}`} />
                    ))}
                </View>
            </View>

            {step === 1 ? (
                <ScrollView contentContainerStyle={tw`p-4 gap-4 pb-32`} showsVerticalScrollIndicator={false}>
                    {/* Basic Info Card */}
                    <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-4`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>Seçim Bilgileri</Text>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>Başlık *</Text>
                            <TextInput
                                value={title}
                                onChangeText={setTitle}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                placeholder="Seçim başlığını girin..."
                                placeholderTextColor="#94a3b8"
                                maxLength={200}
                            />
                        </View>

                        <View style={tw`gap-1`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>Açıklama</Text>
                            <TextInput
                                value={description}
                                onChangeText={setDescription}
                                style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium min-h-[80px]`}
                                placeholder="Seçim hakkında bilgi verin..."
                                placeholderTextColor="#94a3b8"
                                multiline
                                textAlignVertical="top"
                                maxLength={2000}
                            />
                        </View>
                    </View>

                    {/* Election Type Card */}
                    <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-3`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>Seçim Türü</Text>
                        {ELECTION_TYPES.map(t => (
                            <TouchableOpacity
                                key={t.key}
                                onPress={() => setElectionType(t.key)}
                                style={tw`flex-row items-center gap-3 p-3 rounded-xl border ${electionType === t.key ? 'border-[#1162d4] bg-[#1162d4]/5' : 'border-slate-200 bg-slate-50'}`}
                            >
                                <MaterialIcons name={t.icon as any} size={22} color={electionType === t.key ? '#1162d4' : '#94a3b8'} />
                                <View style={tw`flex-1`}>
                                    <Text style={tw`font-semibold ${electionType === t.key ? 'text-[#1162d4]' : 'text-slate-700'}`}>{t.label}</Text>
                                    <Text style={tw`text-xs text-slate-400`}>{t.desc}</Text>
                                </View>
                                {electionType === t.key && <MaterialIcons name="check-circle" size={20} color="#1162d4" />}
                            </TouchableOpacity>
                        ))}
                    </View>

                    {/* Candidate Type Card */}
                    <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-3`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>Aday Tipi</Text>
                        {CANDIDATE_TYPES.map(t => (
                            <TouchableOpacity
                                key={t.key}
                                onPress={() => {
                                    setCandidateType(t.key);
                                    setCandidates(prev => prev.map(c => ({ ...c, candidateType: t.key, name: '', memberUserId: '' })));
                                }}
                                style={tw`flex-row items-center gap-3 p-3 rounded-xl border ${candidateType === t.key ? 'border-[#1162d4] bg-[#1162d4]/5' : 'border-slate-200 bg-slate-50'}`}
                            >
                                <MaterialIcons name={t.icon as any} size={22} color={candidateType === t.key ? '#1162d4' : '#94a3b8'} />
                                <View style={tw`flex-1`}>
                                    <Text style={tw`font-semibold ${candidateType === t.key ? 'text-[#1162d4]' : 'text-slate-700'}`}>{t.label}</Text>
                                    <Text style={tw`text-xs text-slate-400`}>{t.desc}</Text>
                                </View>
                                {candidateType === t.key && <MaterialIcons name="check-circle" size={20} color="#1162d4" />}
                            </TouchableOpacity>
                        ))}
                    </View>

                    {/* Date/Time Card */}
                    <View style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-4`}>
                        <Text style={tw`text-slate-900 font-bold text-base`}>Zaman Aralığı</Text>

                        {/* Start Time */}
                        <View style={tw`gap-2`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>Başlangıç *</Text>
                            <View style={tw`flex-row gap-2`}>
                                <TouchableOpacity
                                    onPress={() => { setStartPickerMode('date'); setShowStartPicker(true); }}
                                    style={tw`flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="calendar-today" size={18} color="#1162d4" />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(startTime)}</Text>
                                </TouchableOpacity>
                                <TouchableOpacity
                                    onPress={() => { setStartPickerMode('time'); setShowStartPicker(true); }}
                                    style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="schedule" size={18} color="#1162d4" />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(startTime)}</Text>
                                </TouchableOpacity>
                            </View>
                        </View>

                        {/* End Time */}
                        <View style={tw`gap-2`}>
                            <Text style={tw`text-xs font-semibold text-slate-500 uppercase tracking-wide`}>Bitiş *</Text>
                            <View style={tw`flex-row gap-2`}>
                                <TouchableOpacity
                                    onPress={() => { setEndPickerMode('date'); setShowEndPicker(true); }}
                                    style={tw`flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="calendar-today" size={18} color="#1162d4" />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatDate(endTime)}</Text>
                                </TouchableOpacity>
                                <TouchableOpacity
                                    onPress={() => { setEndPickerMode('time'); setShowEndPicker(true); }}
                                    style={tw`bg-slate-50 border border-slate-200 rounded-xl px-3 py-3 flex-row items-center gap-2`}
                                >
                                    <MaterialIcons name="schedule" size={18} color="#1162d4" />
                                    <Text style={tw`text-slate-900 font-medium text-sm`}>{formatTime(endTime)}</Text>
                                </TouchableOpacity>
                            </View>
                        </View>

                        {showStartPicker && (
                            <DateTimePicker
                                value={startTime}
                                mode={startPickerMode}
                                minimumDate={new Date()}
                                onChange={(_, date) => { setShowStartPicker(false); if (date) setStartTime(date); }}
                            />
                        )}
                        {showEndPicker && (
                            <DateTimePicker
                                value={endTime}
                                mode={endPickerMode}
                                minimumDate={startTime}
                                onChange={(_, date) => { setShowEndPicker(false); if (date) setEndTime(date); }}
                            />
                        )}
                    </View>
                </ScrollView>
            ) : (
                <ScrollView contentContainerStyle={tw`p-4 gap-4 pb-32`} showsVerticalScrollIndicator={false}>
                    <View style={tw`bg-[#1162d4]/5 border border-[#1162d4]/20 rounded-xl p-3 flex-row items-center gap-2`}>
                        <MaterialIcons name="info" size={18} color="#1162d4" />
                        <Text style={tw`text-[#1162d4] text-sm font-medium flex-1`}>
                            {candidateType === 'PERSON' ? 'Topluluk üyelerini aday olarak seçin.' :
                                candidateType === 'TEXT_OPTION' ? 'Oy pusulasında gösterilecek metin seçeneklerini girin.' :
                                    'Her seçenek için bir görsel URL ve etiket girin.'}
                        </Text>
                    </View>

                    {candidates.map((candidate, idx) => (
                        <View key={idx} style={tw`bg-white rounded-2xl p-4 border border-slate-100 shadow-sm gap-3`}>
                            <View style={tw`flex-row items-center justify-between`}>
                                <Text style={tw`text-slate-900 font-bold`}>Aday {idx + 1}</Text>
                                <TouchableOpacity onPress={() => removeCandidate(idx)} style={tw`w-8 h-8 rounded-full bg-red-50 items-center justify-center`}>
                                    <MaterialIcons name="close" size={16} color="#ef4444" />
                                </TouchableOpacity>
                            </View>

                            {candidateType === 'PERSON' ? (
                                <TouchableOpacity
                                    onPress={() => openMemberPicker(idx)}
                                    style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 flex-row items-center gap-3`}
                                >
                                    <MaterialIcons name="person" size={22} color="#1162d4" />
                                    <Text style={tw`flex-1 ${candidate.name ? 'text-slate-900 font-medium' : 'text-slate-400'}`}>
                                        {candidate.name || 'Üye seçin...'}
                                    </Text>
                                    <MaterialIcons name="chevron-right" size={20} color="#94a3b8" />
                                </TouchableOpacity>
                            ) : (
                                <>
                                    <TextInput
                                        value={candidate.name}
                                        onChangeText={v => updateCandidate(idx, 'name', v)}
                                        style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                        placeholder={candidateType === 'IMAGE_OPTION' ? 'Kısa etiket (örn. Logo A)...' : 'Seçenek adı (örn. Evet)...'}
                                        placeholderTextColor="#94a3b8"
                                        maxLength={200}
                                    />
                                    {candidateType === 'IMAGE_OPTION' && (
                                        <TextInput
                                            value={candidate.imageUrl}
                                            onChangeText={v => updateCandidate(idx, 'imageUrl', v)}
                                            style={tw`bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-slate-900 font-medium`}
                                            placeholder="Görsel URL'si..."
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
                        style={tw`bg-white border-2 border-dashed border-[#1162d4]/40 rounded-2xl p-4 flex-row items-center justify-center gap-2`}
                    >
                        <MaterialIcons name="add" size={22} color="#1162d4" />
                        <Text style={tw`text-[#1162d4] font-semibold`}>Aday Ekle</Text>
                    </TouchableOpacity>
                </ScrollView>
            )}

            {/* Bottom CTA */}
            <View style={tw`absolute bottom-0 left-0 right-0 bg-white px-4 pt-3 pb-8 border-t border-slate-200`}>
                <TouchableOpacity
                    onPress={step === 1 ? () => { if (validateStep1()) setStep(2); } : handleCreate}
                    disabled={isCreating}
                    style={tw`w-full h-14 bg-[#1162d4] rounded-2xl items-center justify-center shadow-lg shadow-blue-500/30 ${isCreating ? 'opacity-70' : ''}`}
                >
                    {isCreating ? (
                        <ActivityIndicator color="white" />
                    ) : (
                        <Text style={tw`text-white font-bold text-base tracking-wide`}>
                            {step === 1 ? 'Devam Et: Adaylar →' : 'Seçimi Oluştur'}
                        </Text>
                    )}
                </TouchableOpacity>
            </View>

            {/* Member Picker Modal */}
            <Modal visible={memberPickerVisible} animationType="slide" transparent onRequestClose={() => setMemberPickerVisible(false)}>
                <View style={tw`flex-1 justify-end`}>
                    <TouchableOpacity style={tw`flex-1`} onPress={() => setMemberPickerVisible(false)} />
                    <View style={tw`bg-white rounded-t-3xl max-h-[60%] border-t border-slate-100 shadow-2xl`}>
                        <View style={tw`flex-row items-center px-4 py-4 border-b border-slate-100`}>
                            <Text style={tw`flex-1 text-base font-bold text-slate-900`}>Üye Seç</Text>
                            <TouchableOpacity onPress={() => setMemberPickerVisible(false)}>
                                <MaterialIcons name="close" size={24} color="#64748b" />
                            </TouchableOpacity>
                        </View>
                        {membersLoading ? (
                            <ActivityIndicator style={tw`p-8`} color="#1162d4" />
                        ) : communityMembers.length === 0 ? (
                            <Text style={tw`text-center text-slate-400 p-8`}>Üye listesi yüklenemedi.</Text>
                        ) : (
                            <FlatList
                                data={communityMembers}
                                keyExtractor={item => item.userId || item.id?.toString()}
                                renderItem={({ item }) => (
                                    <TouchableOpacity
                                        onPress={() => selectMember(item)}
                                        style={tw`flex-row items-center gap-3 px-4 py-3 border-b border-slate-100`}
                                    >
                                        <View style={tw`w-10 h-10 bg-[#1162d4]/10 rounded-full items-center justify-center`}>
                                            <MaterialIcons name="person" size={22} color="#1162d4" />
                                        </View>
                                        <View>
                                            <Text style={tw`text-slate-900 font-semibold`}>{item.displayName || item.userId}</Text>
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
