import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, Alert, ActivityIndicator, Image } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import tw from 'twrnc';

export const VotingBallotScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { electionId, accessCode } = route.params;

    const [options, setOptions] = useState<any[]>([]);
    const [election, setElection] = useState<any>(null);
    const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isCasting, setIsCasting] = useState(false);

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        try {
            const [optRes, electRes] = await Promise.all([
                api.get(`/elections/${electionId}/candidates`),
                api.get(`/elections/${electionId}`)
            ]);
            setOptions(optRes.data?.data || []);
            setElection(electRes.data?.data || null);
        } catch (e) {
            console.error(e);
            Alert.alert('Hata', 'Adaylar/Seçenekler yüklenemedi.');
        } finally {
            setIsLoading(false);
        }
    };

    const confirmVote = () => {
        if (!selectedOptionId) {
            Alert.alert('Hata', 'Lütfen bir seçenek işaretleyin.');
            return;
        }
        const option = options.find((o) => o.id === selectedOptionId);

        Alert.alert(
            'Güvenli Oy Onayı',
            `"${option?.name || option?.text}" seçeneğine oy veriyorsunuz. Bu işlem geri alınamaz ve oyunuz kriptografik olarak şifrelenecektir. Onaylıyor musunuz?`,
            [
                { text: 'İptal', style: 'cancel' },
                { text: 'Onayla ve Şifrele', onPress: handleCastVote, style: 'default' }
            ]
        );
    };

    const handleCastVote = async () => {
        setIsCasting(true);
        try {
            // First get a vote token
            const tokenRes = await api.post(`/elections/${electionId}/votes/token`);
            const voteToken = tokenRes.data?.data?.token || tokenRes.data?.token;

            // Then cast the vote
            const voteRes = await api.post(`/elections/${electionId}/votes`, {
                voteToken,
                selectedOptionId,
                idempotencyKey: `vote-${electionId}-${Date.now()}`
            });

            const trackingCode = voteRes.data?.data?.trackingCode || voteRes.data?.trackingCode;

            Alert.alert(
                'Oy Kullanıldı 🎉',
                'Oyunuz başarıyla şifrelendi ve sandığa atıldı! Takip Kodu (Özet): ' + trackingCode,
                [{ text: 'Ana Sayfaya Dön', onPress: () => navigation.navigate('MainTab') }]
            );
        } catch (error: any) {
            Alert.alert('Oy Gönderilemedi', error.response?.data?.message || 'Lütfen tekrar deneyin.');
        } finally {
            setIsCasting(false);
        }
    };

    if (isLoading || !election) {
        return (
            <View style={tw`flex-1 items-center justify-center bg-[#f6f7f8]`}>
                <ActivityIndicator size="large" color="#1162d4" />
            </View>
        );
    }

    return (
        <View style={tw`flex-1 bg-[#f6f7f8] flex-col h-full`}>
            {/* Top Status Bar & Navigation */}
            <View style={tw`bg-white shadow-sm z-20 pt-10 pb-2`}>
                <View style={tw`h-14 flex-row items-center justify-between px-4`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`p-2 -ml-2 rounded-full`}>
                        <Ionicons name="close" size={24} color="#64748b" />
                    </TouchableOpacity>
                    <Text style={tw`text-base font-bold tracking-tight text-slate-900`}>Ballot</Text>
                    <TouchableOpacity style={tw`w-10 h-10 items-center justify-center`}>
                        <Ionicons name="information-circle-outline" size={24} color="#64748b" />
                    </TouchableOpacity>
                </View>

            </View>

            {/* Main Content Area */}
            <ScrollView style={tw`flex-1`} contentContainerStyle={tw`pb-32`}>
                <View style={tw`px-5 pt-6 pb-2`}>
                    <View style={tw`flex-row items-start gap-3 mb-2`}>
                        <Ionicons name="checkbox-outline" size={32} color="#1162d4" />
                        <View style={tw`flex-1`}>
                            <Text style={tw`text-2xl font-bold leading-tight text-slate-900`}>{election?.title}</Text>
                            {election?.description && (
                                <Text style={tw`text-base text-slate-600 mt-2 leading-relaxed`}>{election?.description}</Text>
                            )}
                            <Text style={tw`text-sm text-slate-500 mt-2`}>Topluluk: {election?.communityId}</Text>
                        </View>
                    </View>
                    <View style={tw`mt-4 p-4 bg-[#1162d4]/5 rounded-lg border border-[#1162d4]/10 flex-row gap-3 items-start`}>
                        <Ionicons name="lock-closed" size={20} color="#1162d4" style={tw`mt-0.5`} />
                        <Text style={tw`text-sm text-slate-700 leading-relaxed pr-6`}>
                            Lütfen <Text style={tw`text-[#1162d4] font-semibold`}>bir seçenek</Text> işaretleyin. Seçiminiz ElectionGuard ile uçtan uca şifrelenecek ve sandığa güvenle gönderilecektir.
                        </Text>
                    </View>
                </View>

                {/* Candidate List */}
                <View style={tw`flex-col gap-4 p-5`}>
                    {options.map((option) => {
                        const isSelected = selectedOptionId === option.id;
                        return (
                            <TouchableOpacity
                                key={option.id}
                                onPress={() => setSelectedOptionId(option.id)}
                                style={tw`flex-row items-center gap-4 rounded-xl border ${isSelected ? 'border-[#1162d4] bg-[#1162d4]/5' : 'border-slate-200 bg-white'} p-4 shadow-sm`}
                            >
                                {/* Candidate content based on candidateType */}
                                {election?.candidateType === 'PERSON' ? (
                                    <>
                                        {/* Avatar placeholder */}
                                        <View style={tw`h-14 w-14 rounded-full border border-slate-100 bg-slate-100 items-center justify-center`}>
                                            <Text style={tw`text-lg font-bold text-slate-500`}>{option.name ? option.name[0] : option.text?.[0]}</Text>
                                        </View>
                                        <View style={tw`flex-1 justify-center`}>
                                            <Text style={tw`text-base font-bold text-slate-900`}>{option.name || option.text}</Text>
                                            <Text style={tw`text-sm font-medium text-slate-500`}>Aday</Text>
                                        </View>
                                    </>
                                ) : election?.candidateType === 'IMAGE_OPTION' ? (
                                    <>
                                        <View style={tw`h-14 w-14 rounded-lg bg-slate-100 overflow-hidden`}>
                                            {option.imageUrl ? (
                                                <Image source={{ uri: option.imageUrl }} style={tw`w-full h-full`} resizeMode="cover" />
                                            ) : (
                                                <View style={tw`w-full h-full items-center justify-center`}>
                                                    <Ionicons name="image-outline" size={24} color="#94a3b8" />
                                                </View>
                                            )}
                                        </View>
                                        <View style={tw`flex-1 justify-center`}>
                                            <Text style={tw`text-base font-bold text-slate-900`}>{option.name || option.text}</Text>
                                        </View>
                                    </>
                                ) : (
                                    <View style={tw`flex-1 justify-center py-2`}>
                                        <Text style={tw`text-base font-bold text-slate-900`}>{option.name || option.text}</Text>
                                    </View>
                                )}

                                {/* Radio Outline */}
                                <View style={tw`relative h-6 w-6 rounded-full border-2 ${isSelected ? 'border-[#1162d4] bg-[#1162d4]' : 'border-slate-300 bg-transparent'} items-center justify-center`}>
                                    {isSelected && <View style={tw`w-2 h-2 bg-white rounded-full`} />}
                                </View>
                            </TouchableOpacity>
                        );
                    })}
                </View>

                {/* Security Badge */}
                <View style={tw`flex-row items-center justify-center gap-1.5 opacity-60 pb-8 mt-2`}>
                    <Ionicons name="shield-checkmark" size={14} color="#64748b" />
                    <Text style={tw`text-xs font-medium text-slate-500`}>Secured by ElectionGuard</Text>
                </View>
            </ScrollView>

            {/* Sticky Footer */}
            <View style={tw`absolute bottom-0 left-0 right-0 z-30 p-4 pb-8 bg-white/95 border-t border-slate-200`}>
                <TouchableOpacity
                    style={tw`w-full flex-row items-center justify-center gap-2 rounded-xl bg-[#1162d4] py-3.5 shadow-md ${!selectedOptionId || isCasting ? 'opacity-50' : ''}`}
                    onPress={confirmVote}
                    disabled={!selectedOptionId || isCasting}
                >
                    <Text style={tw`text-base font-bold text-white tracking-wide`}>
                        {isCasting ? 'Şifreleniyor...' : 'Confirm Selection'}
                    </Text>
                    {!isCasting && <Ionicons name="arrow-forward" size={20} color="#fff" />}
                </TouchableOpacity>
            </View>
        </View>
    );
};
