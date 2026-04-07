import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, Alert, ActivityIndicator, Image } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import tw from 'twrnc';
import { useI18n } from '../../i18n/LanguageContext';
import { buildElectionGuardPlaintextJson, encryptTransitPayloadBase64 } from '../../utils/ballotEncrypt';

export const VotingBallotScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { electionId, accessCode } = route.params;

    const [options, setOptions] = useState<any[]>([]);
    const [election, setElection] = useState<any>(null);
    const [selectedOptionId, setSelectedOptionId] = useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isCasting, setIsCasting] = useState(false);
    const { t } = useI18n();

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
            Alert.alert(t('voting.loadErrorTitle'), t('voting.loadErrorBody'));
        } finally {
            setIsLoading(false);
        }
    };

    const confirmVote = () => {
        if (!selectedOptionId) {
            Alert.alert(t('voting.selectRequiredTitle'), t('voting.selectRequiredBody'));
            return;
        }
        const option = options.find((o) => o.id === selectedOptionId);

        Alert.alert(
            t('voting.confirmTitle'),
            t('voting.confirmBody', { option: option?.name || option?.text || '' }),
            [
                { text: t('common.cancel'), style: 'cancel' },
                { text: t('voting.confirmAction'), onPress: handleCastVote, style: 'default' }
            ]
        );
    };

    const handleCastVote = async () => {
        setIsCasting(true);
        try {
            // First get a vote token
            const tokenRes = await api.post(`/elections/${electionId}/votes/token`);
            const voteToken = tokenRes.data?.data?.token || tokenRes.data?.token;
            const candidateId = selectedOptionId!;
            const castBody: {
                voteToken: string;
                candidateId: number;
                rsaEncryptedPayload?: string;
            } = { voteToken, candidateId };

            if (election?.encryptedVotingEnabled) {
                const pemRes = await api.get('/elections/crypto/transit-rsa-public-key');
                const pem = pemRes.data?.data?.rsaPublicKeyPem as string | undefined;
                if (!pem?.trim()) {
                    throw new Error(t('voting.pemMissing'));
                }
                const plaintext = buildElectionGuardPlaintextJson(electionId, options, candidateId);
                castBody.rsaEncryptedPayload = encryptTransitPayloadBase64(pem, plaintext);
            }

            const voteRes = await api.post(`/elections/${electionId}/votes`, castBody);

            const payload = voteRes.data?.data;
            const alreadyVoted = payload?.alreadyVoted === true;
            const trackingCode: string | undefined = payload?.trackingCode;
            const trackingLabel = trackingCode?.trim()
                ? trackingCode
                : t('voting.trackingNone');

            Alert.alert(
                alreadyVoted ? t('voting.castIdempotentTitle') : t('voting.castSuccessTitle'),
                alreadyVoted
                    ? t('voting.castIdempotentBody', { trackingCode: trackingLabel })
                    : t('voting.castSuccessBody', { trackingCode: trackingLabel }),
                [{ text: t('voting.backHome'), onPress: () => navigation.navigate('MainTab') }]
            );
        } catch (error: any) {
            const msg =
                error.response?.data?.message ||
                (typeof error?.message === 'string' ? error.message : null) ||
                t('voting.castErrorBody');
            Alert.alert(t('voting.castErrorTitle'), msg);
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
                    <Text style={tw`text-base font-bold tracking-tight text-slate-900`}>{t('voting.title')}</Text>
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
                            <Text style={tw`text-sm text-slate-500 mt-2`}>{t('voting.community')}: {election?.communityId}</Text>
                        </View>
                    </View>
                    <View style={tw`mt-4 p-4 bg-[#1162d4]/5 rounded-lg border border-[#1162d4]/10 flex-row gap-3 items-start`}>
                        <Ionicons name="lock-closed" size={20} color="#1162d4" style={tw`mt-0.5`} />
                        <Text style={tw`text-sm text-slate-700 leading-relaxed pr-6`}>
                            {t('voting.instructions')}
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
                                            <Text style={tw`text-sm font-medium text-slate-500`}>{t('voting.candidate')}</Text>
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
                        {isCasting ? t('voting.encrypting') : t('voting.confirmSelection')}
                    </Text>
                    {!isCasting && <Ionicons name="arrow-forward" size={20} color="#fff" />}
                </TouchableOpacity>
            </View>
        </View>
    );
};
