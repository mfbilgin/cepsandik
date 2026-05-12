import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, Share, Clipboard } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';

export const VoteVerificationScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { electionId } = route.params;
    const { t } = useI18n();
    const { showDialog } = useUI();

    const [proof, setProof] = useState<any>(null);
    const [election, setElection] = useState<any>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [showTechnical, setShowTechnical] = useState(false);

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        try {
            const [proofRes, electionRes] = await Promise.all([
                api.get(`/elections/${electionId}/votes/my-proof`),
                api.get(`/elections/${electionId}`),
            ]);
            setProof(proofRes.data?.data || null);
            setElection(electionRes.data?.data || null);
        } catch (e: any) {
            const msg = e.response?.data?.message || t('results.loadError');
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: msg,
                type: 'error'
            });
        } finally {
            setIsLoading(false);
        }
    };

    const handleCopy = () => {
        if (proof?.trackingCode) {
            Clipboard.setString(proof.trackingCode);
            showDialog({
                title: '✓',
                message: t('verification.copySuccess'),
                type: 'success'
            });
        }
    };

    const handleShare = async () => {
        if (!proof?.trackingCode) return;
        try {
            await Share.share({
                message: `CepSandık ${t('verification.title')}\n${t('verification.election')}: ${election?.title || electionId}\n${t('verification.trackingCode')}: ${proof.trackingCode}`,
            });
        } catch {}
    };

    const isVerified = (() => {
        if (!proof?.zkpProof) return false;
        try { return JSON.parse(proof.zkpProof)?.is_valid === true; } catch { return false; }
    })();

    // ─── Loading ──────────────────────────────────────────
    if (isLoading) {
        return (
            <View style={tw`flex-1 items-center justify-center bg-background`}>
                <ActivityIndicator size="large" color={tw.color('primary')} />
            </View>
        );
    }

    // ─── No vote cast ─────────────────────────────────────
    if (!proof) {
        return (
            <View style={tw`flex-1 bg-background`}>
                <Header title={t('verification.title')} navigation={navigation} />
                <View style={tw`flex-1 items-center justify-center px-8`}>
                    <View style={tw`w-20 h-20 rounded-full bg-slate-100 items-center justify-center mb-4`}>
                        <Ionicons name="document-text-outline" size={40} color="#94a3b8" />
                    </View>
                    <Text style={tw`text-lg font-bold text-slate-700 text-center`}>{t('verification.noVoteTitle')}</Text>
                    <Text style={tw`text-sm text-slate-500 mt-2 text-center leading-relaxed`}>{t('verification.noVoteDesc')}</Text>
                </View>
            </View>
        );
    }

    // ─── Main content ─────────────────────────────────────
    return (
        <View style={tw`flex-1 bg-background`}>
            <Header title={t('verification.title')} navigation={navigation} onShare={handleShare} />

            <ScrollView contentContainerStyle={tw`pb-12`}>
                {/* ── Status Hero ── */}
                <View style={tw`mx-4 mt-4 rounded-2xl bg-surface p-6 shadow-sm border border-slate-100`}>
                    <View style={tw`items-center`}>
                        <View style={[
                            tw`w-20 h-20 rounded-full items-center justify-center mb-3`,
                            { backgroundColor: isVerified ? '#dcfce7' : '#f0f9ff' }
                        ]}>
                            <Ionicons
                                name={isVerified ? 'shield-checkmark' : 'checkmark-circle'}
                                size={40}
                                color={isVerified ? '#15803d' : '#0284c7'}
                            />
                        </View>
                        <Text style={tw`text-xl font-bold text-slate-900 text-center`}>
                            {isVerified ? t('verification.statusVerified') : t('verification.statusPending')}
                        </Text>
                        <Text style={tw`text-sm text-slate-500 mt-2 text-center px-2 leading-relaxed`}>
                            {isVerified ? t('verification.statusVerifiedDesc') : t('verification.statusPendingDesc')}
                        </Text>
                    </View>
                </View>

                {/* ── Tracking Code ── */}
                {proof.trackingCode && (
                    <View style={tw`mx-4 mt-3 rounded-2xl bg-surface p-5 shadow-sm border border-slate-100`}>
                        <View style={tw`flex-row items-center gap-2 mb-2`}>
                            <Ionicons name="finger-print-outline" size={20} color={tw.color('primary')} />
                            <Text style={tw`text-base font-bold text-slate-900`}>{t('verification.trackingCode')}</Text>
                        </View>
                        <Text style={tw`text-xs text-slate-500 mb-3 leading-relaxed`}>
                            {t('verification.trackingCodeDesc')}
                        </Text>
                        <TouchableOpacity
                            onPress={handleCopy}
                            activeOpacity={0.7}
                            style={tw`bg-primary/5 rounded-xl p-4 border border-primary/10 flex-row items-center`}
                        >
                            <Text style={tw`flex-1 text-sm font-mono font-bold text-primary leading-relaxed`} selectable>
                                {proof.trackingCode}
                            </Text>
                            <Ionicons name="copy-outline" size={18} color={tw.color('primary')} />
                        </TouchableOpacity>
                    </View>
                )}

                {/* ── Election Info ── */}
                <View style={tw`mx-4 mt-3 rounded-2xl bg-surface p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-3`}>
                        <Ionicons name="information-circle-outline" size={20} color={tw.color('primary')} />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('verification.electionInfo')}</Text>
                    </View>
                    <InfoRow label={t('verification.election')} value={election?.title || `#${electionId}`} />
                    <View style={tw`h-px bg-slate-100 my-2`} />
                    <InfoRow label={t('verification.status')} value={election?.status || '—'} />
                    <View style={tw`h-px bg-slate-100 my-2`} />
                    <View style={tw`flex-row justify-between items-center`}>
                        <Text style={tw`text-sm text-slate-500`}>{t('verification.encryptionStatus')}</Text>
                        <View style={tw`flex-row items-center gap-1.5`}>
                            <Ionicons
                                name={election?.encryptedVotingEnabled ? 'lock-closed' : 'lock-open'}
                                size={14}
                                color={election?.encryptedVotingEnabled ? '#15803d' : '#94a3b8'}
                            />
                            <Text style={tw`text-sm font-semibold ${election?.encryptedVotingEnabled ? 'text-green-700' : 'text-slate-400'}`}>
                                {election?.encryptedVotingEnabled ? t('verification.encryptionActive') : t('verification.encryptionInactive')}
                            </Text>
                        </View>
                    </View>
                </View>

                {/* ── "What is this?" explanation ── */}
                <View style={tw`mx-4 mt-3 rounded-2xl bg-surface p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-2`}>
                        <Ionicons name="help-circle-outline" size={20} color={tw.color('primary')} />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('verification.whatIsThis')}</Text>
                    </View>
                    <Text style={tw`text-sm text-slate-500 leading-relaxed`}>
                        {t('verification.whatIsThisDesc')}
                    </Text>
                </View>

                {/* ── "How to verify" explanation ── */}
                <View style={tw`mx-4 mt-3 rounded-2xl bg-surface p-5 shadow-sm border border-slate-100`}>
                    <View style={tw`flex-row items-center gap-2 mb-2`}>
                        <Ionicons name="search-outline" size={20} color={tw.color('primary')} />
                        <Text style={tw`text-base font-bold text-slate-900`}>{t('verification.howToVerifyTitle')}</Text>
                    </View>
                    <Text style={tw`text-sm text-slate-500 leading-relaxed`}>
                        {t('verification.howToVerifyDesc')}
                    </Text>
                </View>

                {/* ── Technical Details (collapsible) ── */}
                {(proof.zkpProof || proof.encryptedBallot) && (
                    <View style={tw`mx-4 mt-3 rounded-2xl bg-surface shadow-sm border border-slate-100 overflow-hidden`}>
                        <TouchableOpacity
                            onPress={() => setShowTechnical(!showTechnical)}
                            style={tw`p-5 flex-row items-center justify-between`}
                        >
                            <View style={tw`flex-row items-center gap-2`}>
                                <Ionicons name="code-slash-outline" size={20} color="#94a3b8" />
                                <Text style={tw`text-sm font-semibold text-slate-400`}>{t('verification.technicalTitle')}</Text>
                            </View>
                            <Ionicons name={showTechnical ? 'chevron-up' : 'chevron-down'} size={18} color="#94a3b8" />
                        </TouchableOpacity>

                        {showTechnical && (
                            <View style={tw`px-5 pb-5`}>
                                {proof.zkpProof && (() => {
                                    try {
                                        const zkp = JSON.parse(proof.zkpProof);
                                        return (
                                            <View style={tw`bg-slate-50 rounded-xl p-3 mb-3`}>
                                                <Text style={tw`text-xs font-bold text-slate-600 mb-2`}>ZKP (Zero-Knowledge Proof)</Text>
                                                <View style={tw`flex-row items-center gap-1.5 mb-1`}>
                                                    <Ionicons
                                                        name={zkp.is_valid ? 'checkmark-circle' : 'close-circle'}
                                                        size={14}
                                                        color={zkp.is_valid ? '#15803d' : '#dc2626'}
                                                    />
                                                    <Text style={tw`text-xs font-semibold ${zkp.is_valid ? 'text-green-700' : 'text-red-600'}`}>
                                                        {zkp.is_valid ? t('verification.proofValid') : t('verification.proofInvalid')}
                                                    </Text>
                                                </View>
                                                {zkp.manifest_hash && (
                                                    <>
                                                        <Text style={tw`text-xs text-slate-400 mt-1`}>Manifest Hash</Text>
                                                        <Text style={tw`text-xs font-mono text-slate-500`} selectable numberOfLines={1}>
                                                            {zkp.manifest_hash}
                                                        </Text>
                                                    </>
                                                )}
                                            </View>
                                        );
                                    } catch { return null; }
                                })()}

                                {proof.encryptedBallot && (
                                    <View style={tw`bg-slate-50 rounded-xl p-3`}>
                                        <Text style={tw`text-xs font-bold text-slate-600 mb-2`}>{t('verification.encryptedData')}</Text>
                                        <Text style={tw`text-xs font-mono text-slate-400 leading-relaxed`} selectable>
                                            {proof.encryptedBallot.slice(0, 200)}…
                                        </Text>
                                        <Text style={tw`text-xs text-slate-300 mt-1`}>
                                            {t('verification.charCount', { count: proof.encryptedBallot.length.toLocaleString() })}
                                        </Text>
                                    </View>
                                )}
                            </View>
                        )}
                    </View>
                )}

                {/* Footer */}
                <View style={tw`flex-row items-center justify-center gap-1.5 opacity-50 mt-6`}>
                    <Ionicons name="shield-checkmark" size={14} color="#64748b" />
                    <Text style={tw`text-xs font-medium text-slate-500`}>{t('verification.securedFooter')}</Text>
                </View>
            </ScrollView>
        </View>
    );
};

/* ─── Reusable sub-components ──────────────────────────── */

const Header = ({ title, navigation, onShare }: { title: string; navigation: any; onShare?: () => void }) => (
    <View style={tw`bg-surface shadow-sm z-20 pt-10 pb-2`}>
        <View style={tw`h-14 flex-row items-center px-4`}>
            <TouchableOpacity onPress={() => navigation.goBack()} style={tw`p-2 -ml-2 rounded-full`}>
                <Ionicons name="arrow-back" size={24} color="#0f172a" />
            </TouchableOpacity>
            <Text style={tw`flex-1 text-center text-lg font-bold text-slate-900`}>{title}</Text>
            {onShare ? (
                <TouchableOpacity onPress={onShare} style={tw`p-2 rounded-full`}>
                    <Ionicons name="share-outline" size={22} color="#0f172a" />
                </TouchableOpacity>
            ) : <View style={tw`w-10`} />}
        </View>
    </View>
);

const InfoRow = ({ label, value }: { label: string; value: string }) => (
    <View style={tw`flex-row justify-between items-center`}>
        <Text style={tw`text-sm text-slate-500`}>{label}</Text>
        <Text style={tw`text-sm font-semibold text-slate-800 max-w-[60%] text-right`}>{value}</Text>
    </View>
);
