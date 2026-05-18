import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Image, Modal } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { randomHex, sha256Hex } from '../../utils/ballotEncrypt';
import { encryptBallotClientSide, spoilBallotClientSide, EncryptionParams } from '../../utils/electionGuardClient';

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
    const { showDialog } = useUI();

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
            showDialog({
                title: t('voting.loadErrorTitle'),
                message: t('voting.loadErrorBody'),
                type: 'error'
            });
        } finally {
            setIsLoading(false);
        }
    };

    const confirmVote = () => {
        if (!selectedOptionId) {
            showDialog({
                title: t('voting.selectRequiredTitle'),
                message: t('voting.selectRequiredBody'),
                type: 'warning'
            });
            return;
        }
        const option = options.find((o) => o.id === selectedOptionId);

        showDialog({
            title: t('voting.confirmTitle'),
            message: t('voting.confirmBody', { option: option?.name || option?.text || '' }),
            type: 'confirm',
            confirmText: t('voting.confirmAction'),
            cancelText: t('common.cancel'),
            onConfirm: handleCastVote
        });
    };

    const handleCastVote = async () => {
        setIsCasting(true);
        try {
            // E2E-V: önce client-side ElGamal şifreleme için kriptografik parametreleri çek.
            // Bu endpoint cache'li ve immutable; manifest/context/jointPublicKey içerir.
            const paramsRes = await api.get(`/elections/${electionId}/encryption-params`);
            const encryptionParams: EncryptionParams = paramsRes.data?.data;
            if (!encryptionParams?.jointPublicKey) {
                throw new Error(t('voting.encryptionRequired') || 'E2E-V parametreleri alınamadı.');
            }

            // Voter eligibility credential — ballot'ta saklanmaz.
            const tokenRes = await api.post(`/elections/${electionId}/votes/token`);
            const credential = tokenRes.data?.data?.token || tokenRes.data?.token;
            const selectedOption = selectedOptionId!;
            const ballotId = `ballot_${randomHex(16)}`;
            const nullifierHash = sha256Hex(`${credential}:${electionId}:nullifier`);

            const encrypted = await encryptBallotClientSide({
                encryptionParams,
                ballotId,
                options,
                selectedOptionId: selectedOption,
            });
            const castBody: {
                ballotId: string;
                credential: string;
                credentialSignature: string;
                nullifierHash: string;
                encryptedBallot: string;
                zkpProof: string;
                trackingCode: string;
            } = {
                ballotId,
                credential,
                credentialSignature: sha256Hex(`${credential}:${electionId}:signature`),
                nullifierHash,
                encryptedBallot: encrypted.encryptedBallot,
                zkpProof: encrypted.zkpProof,
                trackingCode: encrypted.trackingCode,
            };

            const voteRes = await api.post(`/elections/${electionId}/votes`, castBody);

            const payload = voteRes.data?.data;
            const alreadyVoted = payload?.alreadyVoted === true;
            const trackingCode: string | undefined = payload?.trackingCode;
            const trackingLabel = trackingCode?.trim()
                ? trackingCode
                : t('voting.trackingNone');

            showDialog({
                title: alreadyVoted ? t('voting.castIdempotentTitle') : t('voting.castSuccessTitle'),
                message: alreadyVoted
                    ? t('voting.castIdempotentBody', { trackingCode: trackingLabel })
                    : t('voting.castSuccessBody', { trackingCode: trackingLabel }),
                type: 'success',
                confirmText: t('voting.backHome'),
                onConfirm: () => navigation.navigate('MainTab')
            });
        } catch (error: any) {
            const msg =
                error.response?.data?.message ||
                (typeof error?.message === 'string' ? error.message : null) ||
                t('voting.castErrorBody');
            showDialog({
                title: t('voting.castErrorTitle'),
                message: msg,
                type: 'error'
            });
        } finally {
            setIsCasting(false);
        }
    };

    // Faz 1.4 — Benaloh challenge: ballot'u spoil et (cast etme). Cihaz primary
    // nonce'ı açar, sunucu DecryptWithNonce ile BAĞIMSIZ çözer. Çözülen seçim
    // seçmenin seçtiğiyle eşleşiyorsa cihaz dürüst şifrelemiş → cast-as-intended.
    const handleSpoilBallot = async () => {
        if (!selectedOptionId) {
            showDialog({
                title: t('voting.selectRequiredTitle'),
                message: t('voting.selectRequiredBody'),
                type: 'warning',
            });
            return;
        }
        setIsCasting(true);
        try {
            const paramsRes = await api.get(`/elections/${electionId}/encryption-params`);
            const encryptionParams: EncryptionParams = paramsRes.data?.data;
            if (!encryptionParams?.jointPublicKey) {
                throw new Error(t('voting.encryptionRequired') || 'E2E-V parametreleri alınamadı.');
            }
            const ballotId = `spoil_${randomHex(16)}`;
            const spoiled = await spoilBallotClientSide({
                encryptionParams,
                ballotId,
                options,
                selectedOptionId: selectedOptionId!,
            });
            const res = await api.post(`/elections/${electionId}/votes/spoil`, {
                ballotId,
                encryptedBallot: spoiled.encryptedBallot,
                primaryNonce: spoiled.primaryNonce,
                plaintextBallot: spoiled.plaintextBallot,
                trackingCode: spoiled.trackingCode,
            });
            const data = res.data?.data;
            const sels: Array<{ selectionId: string; vote: number }> =
                data?.decryptedSelections || [];
            const chosen = sels.find((s) => Number(s.vote) === 1);
            const chosenId = chosen
                ? Number(String(chosen.selectionId).replace('candidate_', ''))
                : null;
            const chosenOpt = options.find((o) => o.id === chosenId);
            const expectedOpt = options.find((o) => o.id === selectedOptionId);
            const honest =
                data?.verified === true &&
                chosenId === selectedOptionId;

            showDialog({
                title: honest
                    ? (t('voting.spoilHonestTitle') || 'Şifreleme Dürüst ✓')
                    : (t('voting.spoilFailTitle') || 'Uyarı: Şifreleme Doğrulanamadı'),
                message: honest
                    ? (t('voting.spoilHonestBody', {
                          option: chosenOpt?.name || chosenOpt?.text || '',
                      }) ||
                      `Sunucu bu cihazın şifrelediği oyu nonce ile bağımsız çözdü: ` +
                          `"${chosenOpt?.name || chosenOpt?.text || ''}". Tam olarak ` +
                          `seçtiğiniz seçenek. Cihaz dürüst şifreliyor. Bu test oyu ` +
                          `SAYILMAYACAK — gerçek oyunuzu vermek için "Oyu Onayla"ya basın.`)
                    : (t('voting.spoilFailBody') ||
                      `Sunucunun bağımsız çözdüğü seçim ("${chosenOpt?.name || '?'}") ` +
                          `beklenenle ("${expectedOpt?.name || '?'}") eşleşmedi veya ` +
                          `doğrulanamadı. Bu cihazla oy vermeyin, yetkiliye bildirin.`),
                type: honest ? 'success' : 'error',
            });
        } catch (error: any) {
            const msg =
                error.response?.data?.message ||
                (typeof error?.message === 'string' ? error.message : null) ||
                (t('voting.castErrorBody') || 'Spoil sırasında hata.');
            showDialog({ title: t('voting.castErrorTitle'), message: msg, type: 'error' });
        } finally {
            setIsCasting(false);
        }
    };

    if (isLoading || !election) {
        return (
            <View style={tw`flex-1 items-center justify-center bg-background`}>
                <ActivityIndicator size="large" color={tw.color('primary')} />
            </View>
        );
    }

    return (
        <View style={tw`flex-1 bg-background flex-col h-full`}>
            {/* Top Status Bar & Navigation */}
            <View style={tw`bg-surface shadow-sm z-20 pt-10 pb-2`}>
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
                        <Ionicons name="checkbox-outline" size={32} color={tw.color('primary')} />
                        <View style={tw`flex-1`}>
                            <Text style={tw`text-2xl font-bold leading-tight text-slate-900`}>{election?.title}</Text>
                            {election?.description && (
                                <Text style={tw`text-base text-slate-600 mt-2 leading-relaxed`}>{election?.description}</Text>
                            )}
                            <Text style={tw`text-sm text-slate-500 mt-2`}>{t('voting.community')}: {election?.communityId}</Text>
                        </View>
                    </View>
                    <View style={tw`mt-4 p-4 bg-primary/5 rounded-lg border border-primary/10 flex-row gap-3 items-start`}>
                        <Ionicons name="lock-closed" size={20} color={tw.color('primary')} style={tw`mt-0.5`} />
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
                                style={tw`flex-row items-center gap-4 rounded-xl border ${isSelected ? 'border-primary bg-primary/5' : 'border-slate-200 bg-surface'} p-4 shadow-sm`}
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
                                <View style={tw`relative h-6 w-6 rounded-full border-2 ${isSelected ? 'border-primary bg-primary' : 'border-slate-300 bg-transparent'} items-center justify-center`}>
                                    {isSelected && <View style={tw`w-2 h-2 bg-surface rounded-full`} />}
                                </View>
                            </TouchableOpacity>
                        );
                    })}
                </View>

                {/* Security Badge */}
                <View style={tw`flex-row items-center justify-center gap-1.5 opacity-60 pb-8 mt-2`}>
                    <Ionicons name="shield-checkmark" size={14} color="#64748b" />
                    <Text style={tw`text-xs font-medium text-slate-500`}>{t('home.securedBy')}</Text>
                </View>
            </ScrollView>

            {/* Sticky Footer */}
            <View style={tw`absolute bottom-0 left-0 right-0 z-30 p-4 pb-8 bg-surface/95 border-t border-slate-200`}>
                <TouchableOpacity
                    style={tw`w-full flex-row items-center justify-center gap-2 rounded-xl bg-primary py-3.5 shadow-md ${!selectedOptionId || isCasting ? 'opacity-50' : ''}`}
                    onPress={confirmVote}
                    disabled={!selectedOptionId || isCasting}
                >
                    <Text style={tw`text-base font-bold text-white tracking-wide`}>
                        {isCasting ? t('voting.encrypting') : t('voting.confirmSelection')}
                    </Text>
                    {!isCasting && <Ionicons name="arrow-forward" size={20} color="#fff" />}
                </TouchableOpacity>

                {/* Faz 1.4 — Benaloh challenge: cihazın dürüst şifrelediğini test et.
                    Bu ballot sayılmaz; doğrulama sonrası gerçek oy verilir. */}
                <TouchableOpacity
                    style={tw`w-full flex-row items-center justify-center gap-2 rounded-xl border border-primary py-3 mt-2 ${!selectedOptionId || isCasting ? 'opacity-50' : ''}`}
                    onPress={handleSpoilBallot}
                    disabled={!selectedOptionId || isCasting}
                >
                    <Ionicons name="shield-checkmark-outline" size={18} color={tw.color('primary')} />
                    <Text style={tw`text-sm font-semibold text-primary tracking-wide`}>
                        {t('voting.spoilButton') || 'Şifrelemeyi Doğrula (test — sayılmaz)'}
                    </Text>
                </TouchableOpacity>
            </View>

            {/* Encryption Overlay */}
            <Modal transparent={true} visible={isCasting} animationType="fade">
                <View style={tw`flex-1 items-center justify-center bg-black/60`}>
                    <View style={tw`bg-surface p-8 rounded-2xl items-center w-4/5 shadow-2xl`}>
                        <ActivityIndicator size="large" color={tw.color('primary')} style={tw`mb-4 scale-150`} />
                        <Text style={tw`text-lg font-bold text-slate-900 mb-2 text-center`}>
                            {t('voting.encrypting') || 'Oyunuz Şifreleniyor...'}
                        </Text>
                        <Text style={tw`text-sm text-slate-500 text-center leading-relaxed mt-2`}>
                            Matematiksel kanıtlar cihazınızda üretiliyor. Lütfen bekleyin.
                        </Text>
                    </View>
                </View>
            </Modal>
        </View>
    );
};
