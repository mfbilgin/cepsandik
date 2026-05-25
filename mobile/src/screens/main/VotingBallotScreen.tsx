import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Image, Modal } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { randomHex, sha256Hex } from '../../utils/ballotEncrypt';
import { encryptBallotClientSide, spoilBallotClientSide, EncryptionParams } from '../../utils/electionGuardClient';
import { theme } from '../../utils/theme';
import { Button } from '../../components/ui';
import { saveVoteReceipt } from '../../utils/voteReceipts';

export const VotingBallotScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { electionId } = route.params;

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

    useEffect(() => { fetchData(); }, []);

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
            showDialog({ title: t('voting.loadErrorTitle'), message: t('voting.loadErrorBody'), type: 'error' });
        } finally {
            setIsLoading(false);
        }
    };

    const confirmVote = () => {
        if (!selectedOptionId) {
            showDialog({ title: t('voting.selectRequiredTitle'), message: t('voting.selectRequiredBody'), type: 'warning' });
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
            const paramsRes = await api.get(`/elections/${electionId}/encryption-params`);
            const encryptionParams: EncryptionParams = paramsRes.data?.data;
            if (!encryptionParams?.jointPublicKey) {
                throw new Error(t('voting.encryptionRequired') || 'E2E-V parametreleri alınamadı.');
            }

            const tokenRes = await api.post(`/elections/${electionId}/votes/token`);
            const credential = tokenRes.data?.data?.token || tokenRes.data?.token;
            const selectedOption = selectedOptionId!;
            const ballotId = `ballot_${randomHex(16)}`;
            const nullifierHash = sha256Hex(`${credential}:${electionId}:nullifier`);

            const encrypted = await encryptBallotClientSide({
                encryptionParams, ballotId, options, selectedOptionId: selectedOption,
            });
            const castBody = {
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
            const trackingLabel = trackingCode?.trim() ? trackingCode : t('voting.trackingNone');
            if (trackingCode?.trim()) {
                await saveVoteReceipt({
                    electionId: String(electionId),
                    trackingCode,
                    electionTitle: election?.title,
                    recordedAt: new Date().toISOString(),
                });
            }

            showDialog({
                title: alreadyVoted ? t('voting.castIdempotentTitle') : t('voting.castSuccessTitle'),
                message: alreadyVoted
                    ? t('voting.castIdempotentBody', { trackingCode: trackingLabel })
                    : t('voting.receiptSavedBody') || 'Oyunuz kaydedildi. Takip kodunuz cihazınıza kaydedildi ve doğrulama ekranında gösterilecek.',
                type: 'success',
                confirmText: t('verification.verifyButton') || 'Doğrula',
                onConfirm: () => navigation.navigate('VoteVerification', { electionId, trackingCode })
            });
        } catch (error: any) {
            const msg =
                error.response?.data?.message ||
                (typeof error?.message === 'string' ? error.message : null) ||
                t('voting.castErrorBody');
            showDialog({ title: t('voting.castErrorTitle'), message: msg, type: 'error' });
        } finally {
            setIsCasting(false);
        }
    };

    // Faz 1.4 — Benaloh challenge: ballot'u spoil et (cast etme).
    const handleSpoilBallot = async () => {
        if (!selectedOptionId) {
            showDialog({ title: t('voting.selectRequiredTitle'), message: t('voting.selectRequiredBody'), type: 'warning' });
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
                encryptionParams, ballotId, options, selectedOptionId: selectedOptionId!,
            });
            const res = await api.post(`/elections/${electionId}/votes/spoil`, {
                ballotId,
                encryptedBallot: spoiled.encryptedBallot,
                primaryNonce: spoiled.primaryNonce,
                plaintextBallot: spoiled.plaintextBallot,
                trackingCode: spoiled.trackingCode,
            });
            const data = res.data?.data;
            const sels: Array<{ selectionId: string; vote: number }> = data?.decryptedSelections || [];
            const chosen = sels.find((s) => Number(s.vote) === 1);
            const chosenId = chosen ? Number(String(chosen.selectionId).replace('candidate_', '')) : null;
            const chosenOpt = options.find((o) => o.id === chosenId);
            const expectedOpt = options.find((o) => o.id === selectedOptionId);
            const honest = data?.verified === true && chosenId === selectedOptionId;

            showDialog({
                title: honest
                    ? (t('voting.spoilHonestTitle') || 'Şifreleme Dürüst ✓')
                    : (t('voting.spoilFailTitle') || 'Uyarı: Şifreleme Doğrulanamadı'),
                message: honest
                    ? (t('voting.spoilHonestBody', { option: chosenOpt?.name || chosenOpt?.text || '' }) ||
                      `Sunucu bu cihazın şifrelediği oyu nonce ile bağımsız çözdü: ` +
                          `"${chosenOpt?.name || chosenOpt?.text || ''}". Tam olarak seçtiğiniz seçenek. ` +
                          `Cihaz dürüst şifreliyor. Bu test oyu SAYILMAYACAK — gerçek oyunuzu vermek için "Oyu Onayla"ya basın.`)
                    : (t('voting.spoilFailBody') ||
                      `Sunucunun bağımsız çözdüğü seçim ("${chosenOpt?.name || '?'}") beklenenle ` +
                          `("${expectedOpt?.name || '?'}") eşleşmedi veya doğrulanamadı. Bu cihazla oy vermeyin, yetkiliye bildirin.`),
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
            <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: theme.colors.background }}>
                <ActivityIndicator size="large" color={theme.colors.primary} />
            </View>
        );
    }

    const c = theme.colors;

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            {/* Üst bar — başlık KOYU, kapat/info nötr */}
            <View
                style={{
                    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                    height: 52, paddingHorizontal: theme.spacing.md,
                    borderBottomWidth: 1, borderBottomColor: c.border, backgroundColor: c.surface,
                }}
            >
                <TouchableOpacity onPress={() => navigation.goBack()} hitSlop={10} style={{ width: 36 }}>
                    <Ionicons name="close" size={24} color={c.textSecondary} />
                </TouchableOpacity>
                <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>{t('voting.title')}</Text>
                <View style={{ width: 36, alignItems: 'flex-end' }}>
                    <Ionicons name="information-circle-outline" size={22} color={c.textSecondary} />
                </View>
            </View>

            <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: 170 }} showsVerticalScrollIndicator={false}>
                {/* Seçim başlığı */}
                <View style={{ paddingHorizontal: theme.spacing.md, paddingTop: theme.spacing.lg }}>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text, lineHeight: 28 }}>
                        {election?.title}
                    </Text>
                    {election?.description && (
                        <Text style={{ fontSize: 15, color: c.textSecondary, marginTop: 8, lineHeight: 21 }}>
                            {election?.description}
                        </Text>
                    )}
                    <Text style={{ fontSize: 13, color: c.textTertiary, marginTop: 8 }}>
                        {t('voting.community')}: {election?.communityId}
                    </Text>

                    {/* Gizlilik/E2E-V bilgi kutusu — primaryTint zemin (tek vurgu) */}
                    <View
                        style={{
                            marginTop: 16, padding: 14, borderRadius: theme.borderRadius.md,
                            backgroundColor: c.primaryTint, flexDirection: 'row', gap: 10, alignItems: 'flex-start',
                        }}
                    >
                        <Ionicons name="lock-closed" size={18} color={c.primary} style={{ marginTop: 1 }} />
                        <Text style={{ flex: 1, fontSize: 13, color: c.text, lineHeight: 19 }}>
                            {t('voting.instructions')}
                        </Text>
                    </View>
                </View>

                {/* Aday listesi — seçili kart mavi (doğru: aktif seçim) */}
                <View style={{ gap: 12, padding: theme.spacing.md }}>
                    {options.map((option) => {
                        const isSelected = selectedOptionId === option.id;
                        return (
                            <TouchableOpacity
                                key={option.id}
                                activeOpacity={0.8}
                                onPress={() => setSelectedOptionId(option.id)}
                                style={{
                                    flexDirection: 'row', alignItems: 'center', gap: 14,
                                    borderRadius: theme.borderRadius.lg,
                                    borderWidth: 1.5,
                                    borderColor: isSelected ? c.primary : c.border,
                                    backgroundColor: isSelected ? c.primaryTint : c.surface,
                                    padding: 16,
                                    ...(isSelected ? {} : theme.shadows.card),
                                }}
                            >
                                {election?.candidateType === 'PERSON' ? (
                                    <>
                                        <View
                                            style={{
                                                height: 52, width: 52, borderRadius: 26,
                                                backgroundColor: c.surfaceAlt,
                                                alignItems: 'center', justifyContent: 'center',
                                            }}
                                        >
                                            <Text style={{ fontSize: 18, fontWeight: '700', color: c.textSecondary }}>
                                                {option.name ? option.name[0] : option.text?.[0]}
                                            </Text>
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                                                {option.name || option.text}
                                            </Text>
                                            <Text style={{ fontSize: 13, color: c.textSecondary, marginTop: 2 }}>
                                                {t('voting.candidate')}
                                            </Text>
                                        </View>
                                    </>
                                ) : election?.candidateType === 'IMAGE_OPTION' ? (
                                    <>
                                        <View style={{ height: 52, width: 52, borderRadius: theme.borderRadius.md, backgroundColor: c.surfaceAlt, overflow: 'hidden' }}>
                                            {option.imageUrl ? (
                                                <Image source={{ uri: option.imageUrl }} style={{ width: '100%', height: '100%' }} resizeMode="cover" />
                                            ) : (
                                                <View style={{ width: '100%', height: '100%', alignItems: 'center', justifyContent: 'center' }}>
                                                    <Ionicons name="image-outline" size={24} color={c.textTertiary} />
                                                </View>
                                            )}
                                        </View>
                                        <View style={{ flex: 1 }}>
                                            <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                                                {option.name || option.text}
                                            </Text>
                                        </View>
                                    </>
                                ) : (
                                    <View style={{ flex: 1, paddingVertical: 4 }}>
                                        <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                                            {option.name || option.text}
                                        </Text>
                                    </View>
                                )}

                                {/* Radyo */}
                                <View
                                    style={{
                                        height: 24, width: 24, borderRadius: 12, borderWidth: 2,
                                        borderColor: isSelected ? c.primary : c.borderStrong,
                                        backgroundColor: isSelected ? c.primary : 'transparent',
                                        alignItems: 'center', justifyContent: 'center',
                                    }}
                                >
                                    {isSelected && <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: '#fff' }} />}
                                </View>
                            </TouchableOpacity>
                        );
                    })}
                </View>

                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingBottom: 24, marginTop: 4 }}>
                    <Ionicons name="shield-checkmark" size={14} color={c.textTertiary} />
                    <Text style={{ fontSize: 12, color: c.textTertiary, fontWeight: '500' }}>{t('home.securedBy')}</Text>
                </View>
            </ScrollView>

            {/* Sabit alt aksiyon */}
            <View
                style={{
                    position: 'absolute', bottom: 0, left: 0, right: 0,
                    padding: theme.spacing.md, paddingBottom: 28,
                    backgroundColor: c.surface, borderTopWidth: 1, borderTopColor: c.border,
                }}
            >
                <Button
                    title={isCasting ? t('voting.encrypting') : t('voting.confirmSelection')}
                    icon="arrow-forward"
                    iconPosition="right"
                    loading={isCasting}
                    disabled={!selectedOptionId || isCasting}
                    onPress={confirmVote}
                />
                <Button
                    title={t('voting.spoilButton') || 'Şifrelemeyi Doğrula (test — sayılmaz)'}
                    icon="shield-checkmark-outline"
                    variant="outline"
                    size="sm"
                    disabled={!selectedOptionId || isCasting}
                    onPress={handleSpoilBallot}
                    style={{ marginTop: 10 }}
                />
            </View>

            {/* Şifreleme overlay */}
            <Modal transparent visible={isCasting} animationType="fade">
                <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(15,23,42,0.6)' }}>
                    <View style={{ backgroundColor: c.surface, padding: 32, borderRadius: theme.borderRadius.xl, alignItems: 'center', width: '80%', ...theme.shadows.elevated }}>
                        <ActivityIndicator size="large" color={c.primary} style={{ marginBottom: 16 }} />
                        <Text style={{ fontSize: 17, fontWeight: '700', color: c.text, marginBottom: 6, textAlign: 'center' }}>
                            {t('voting.encrypting') || 'Oyunuz Şifreleniyor...'}
                        </Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, textAlign: 'center', lineHeight: 19 }}>
                            Matematiksel kanıtlar cihazınızda üretiliyor. Lütfen bekleyin.
                        </Text>
                    </View>
                </View>
            </Modal>
        </SafeAreaView>
    );
};
