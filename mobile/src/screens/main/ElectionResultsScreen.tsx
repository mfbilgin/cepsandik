import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, TouchableOpacity, ActivityIndicator, TextInput, Modal } from 'react-native';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { useAuth } from '../../context/AuthContext';
import * as LegacyFileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';

interface CandidateResult {
    selectionId: string;
    optionId: string;
    candidateName: string;
    candidateDescription?: string;
    candidateImageUrl?: string;
    voteCount: number;
    percentage: number;
    rank: number;
    isWinner: boolean;
}

interface ElectionResult {
    electionId: number;
    electionTitle: string;
    electionDescription?: string;
    status: string;
    type: string;
    startTime: string;
    endTime: string;
    totalVotes: number;
    totalEligibleVoters: number;
    turnoutPercentage: number;
    resultsCalculatedAt?: string;
    candidateResults: CandidateResult[];
}

export const ElectionResultsScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { electionId } = route.params;

    const [result, setResult] = useState<ElectionResult | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isCalculating, setIsCalculating] = useState(false);
    const [isVerifyModalVisible, setIsVerifyModalVisible] = useState(false);
    // Faz B0.4 — "Sonuçları Hesapla" yalnız seçim sahibine (organizatör).
    // Sıradan seçmen tetikleyememeli (leader-mode tuzağıyla aynı sınıf).
    const [isOwner, setIsOwner] = useState(false);
    const { t, language } = useI18n();
    const { showDialog } = useUI();
    const { user } = useAuth();

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        fetchResults();
        (async () => {
            try {
                const res = await api.get(`/elections/${electionId}`);
                const e = res.data?.data;
                setIsOwner(!!e && String(e.createdBy) === String(user?.id));
            } catch { /* sahiplik tespiti opsiyonel — varsayılan false */ }
        })();
    }, []);

    const fetchResults = async () => {
        try {
            const res = await api.get(`/elections/${electionId}/results`);
            setResult(res.data?.data || null);
        } catch (e: any) {
            const status = e.response?.status;
            if (status === 404 || status === 400) {
                setResult(null);
            } else {
                showDialog({
                    title: t('auth.twoFactor.errorTitle'),
                    message: e.response?.data?.message || t('results.loadError'),
                    type: 'error'
                });
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleCalculate = async () => {
        setIsCalculating(true);
        try {
            const res = await api.post(`/elections/${electionId}/results/calculate`);
            setResult(res.data?.data || null);
            showDialog({
                title: t('security.successTitle'),
                message: t('results.calculateSuccess'),
                type: 'success'
            });
        } catch (e: any) {
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: e.response?.data?.message || t('results.calculateError'),
                type: 'error'
            });
        } finally {
            setIsCalculating(false);
        }
    };

    const [isDownloading, setIsDownloading] = useState(false);
    const handleDownloadData = async () => {
        setIsDownloading(true);
        try {
            const res = await api.get(`/elections/${electionId}/proofs`);
            const proofs = res.data?.data;
            if (!proofs) throw new Error('No data');

            const fileName = `election_${electionId}_proofs.json`;
            const fileUri = (LegacyFileSystem.documentDirectory || '').endsWith('/')
                ? `${LegacyFileSystem.documentDirectory}${fileName}`
                : `${LegacyFileSystem.documentDirectory}/${fileName}`;

            await LegacyFileSystem.writeAsStringAsync(fileUri, JSON.stringify(proofs, null, 2), {
                encoding: 'utf8',
            });

            if (await Sharing.isAvailableAsync()) {
                await Sharing.shareAsync(fileUri);
            } else {
                showDialog({
                    title: t('results.downloadSuccess'),
                    message: fileUri,
                    type: 'success'
                });
            }
        } catch (e: any) {
            console.error('Download error:', e);
            const errorMsg = e.response?.data?.message || e.message || t('results.loadError');
            showDialog({
                title: t('auth.twoFactor.errorTitle'),
                message: errorMsg,
                type: 'error'
            });
        } finally {
            setIsDownloading(false);
        }
    };

    const maxVotes = result ? Math.max(...result.candidateResults.map(c => c.voteCount), 1) : 1;

    if (isLoading) {
        return (
            <View style={tw`flex-1 items-center justify-center bg-background`}>
                <ActivityIndicator size="large" color={tw.color('primary')} />
            </View>
        );
    }

    return (
        <View style={tw`flex-1 bg-background`}>
            {/* Header */}
            <View style={tw`bg-surface shadow-sm z-20 pt-10 pb-2`}>
                <View style={tw`h-14 flex-row items-center px-4`}>
                    <TouchableOpacity onPress={() => navigation.goBack()} style={tw`p-2 -ml-2 rounded-full`}>
                        <Ionicons name="arrow-back" size={24} color="#0f172a" />
                    </TouchableOpacity>
                    <Text style={tw`flex-1 text-center text-lg font-bold text-slate-900`}>{t('results.title')}</Text>
                    <TouchableOpacity 
                        onPress={handleDownloadData} 
                        disabled={isDownloading}
                        style={tw`p-2 -mr-2 rounded-full ${isDownloading ? 'opacity-50' : ''}`}
                    >
                        {isDownloading ? (
                            <ActivityIndicator size="small" color={tw.color('primary')} />
                        ) : (
                            <Ionicons name="download-outline" size={24} color={tw.color('primary')} />
                        )}
                    </TouchableOpacity>
                </View>
            </View>

            {!result ? (
                /* ── No results yet ── */
                <View style={tw`flex-1 items-center justify-center px-8`}>
                    <View style={tw`w-24 h-24 rounded-full bg-primary/10 items-center justify-center mb-6`}>
                        <Ionicons name="bar-chart-outline" size={48} color={tw.color('primary')} />
                    </View>
                    <Text style={tw`text-xl font-bold text-slate-900 text-center mb-2`}>{t('results.notReadyTitle')}</Text>
                    <Text style={tw`text-sm text-slate-500 text-center mb-8 leading-relaxed`}>{t('results.notReadyDesc')}</Text>
                    {isOwner && (
                        <TouchableOpacity
                            onPress={handleCalculate}
                            disabled={isCalculating}
                            style={tw`flex-row items-center gap-2 bg-primary px-8 py-3.5 rounded-xl shadow-sm ${isCalculating ? 'opacity-50' : ''}`}
                        >
                            {isCalculating
                                ? <ActivityIndicator size="small" color="#fff" />
                                : <Ionicons name="calculator-outline" size={20} color="#fff" />
                            }
                            <Text style={tw`text-base font-bold text-white`}>
                                {isCalculating ? t('results.calculating') : t('results.calculate')}
                            </Text>
                        </TouchableOpacity>
                    )}
                </View>
            ) : (
                <ScrollView contentContainerStyle={tw`pb-12`}>
                    {/* ── Summary Card ── */}
                    <View style={tw`mx-4 mt-4 rounded-2xl bg-surface p-6 shadow-sm border border-slate-100`}>
                        <Text style={tw`text-xl font-bold text-slate-900 text-center mb-1`}>{result.electionTitle}</Text>
                        {result.electionDescription ? (
                            <Text style={tw`text-sm text-slate-500 text-center mb-4`}>{result.electionDescription}</Text>
                        ) : null}

                        {/* Stats Row */}
                        <View style={tw`flex-row justify-around mt-2 pt-4 border-t border-slate-100`}>
                            <StatItem value={String(result.totalVotes)} label={t('results.totalVotes')} />
                            <View style={tw`w-px bg-slate-200`} />
                            <StatItem value={result.totalEligibleVoters ? String(result.totalEligibleVoters) : '—'} label={t('results.voters')} />
                            <View style={tw`w-px bg-slate-200`} />
                            <StatItem
                                value={result.turnoutPercentage != null ? t('home.percentValue', { value: result.turnoutPercentage.toFixed(0) }) : '—'}
                                label={t('results.turnout')}
                            />
                        </View>
                    </View>

                    {/* ── Winner Banner ── */}
                    {result.candidateResults?.some(c => c.isWinner) && (() => {
                        const winner = result.candidateResults.find(c => c.isWinner)!;
                        return (
                            <View style={[tw`mx-4 mt-3 rounded-2xl overflow-hidden`, { backgroundColor: '#f0fdf4', borderWidth: 1, borderColor: '#bbf7d0' }]}>
                                <View style={tw`p-5 flex-row items-center gap-4`}>
                                    <View style={tw`w-14 h-14 rounded-full bg-green-200 items-center justify-center`}>
                                        <Ionicons name="trophy" size={28} color="#15803d" />
                                    </View>
                                    <View style={tw`flex-1`}>
                                        <Text style={tw`text-xs font-semibold text-green-600 uppercase tracking-wide`}>{t('results.winner')}</Text>
                                        <Text style={tw`text-lg font-bold text-green-900`}>{winner.candidateName}</Text>
                                        <Text style={tw`text-sm text-green-700`}>
                                            {t('results.votes', { count: winner.voteCount })} — {t('home.percentValue', { value: winner.percentage?.toFixed(1) })}
                                        </Text>
                                    </View>
                                </View>
                            </View>
                        );
                    })()}

                    {/* ── Results List ── */}
                    <View style={tw`mx-4 mt-3 rounded-2xl bg-surface shadow-sm border border-slate-100`}>
                        <View style={tw`p-5 pb-3 flex-row items-center gap-2`}>
                            <Ionicons name="podium-outline" size={20} color={tw.color('primary')} />
                            <Text style={tw`text-base font-bold text-slate-900`}>{t('results.candidateResults')}</Text>
                        </View>

                        {result.candidateResults
                            ?.sort((a, b) => a.rank - b.rank)
                            .map((candidate, index) => (
                            <View key={candidate.selectionId || candidate.optionId} style={tw`px-5 pb-4`}>
                                <View style={tw`flex-row items-center gap-3 mb-2`}>
                                    <View style={[
                                        tw`w-8 h-8 rounded-full items-center justify-center`,
                                        { backgroundColor: candidate.isWinner ? '#dcfce7' : '#f1f5f9' }
                                    ]}>
                                        <Text style={tw`text-sm font-bold ${candidate.isWinner ? 'text-green-700' : 'text-slate-500'}`}>
                                            {candidate.rank}
                                        </Text>
                                    </View>
                                    <View style={tw`flex-1`}>
                                        <View style={tw`flex-row items-center gap-2`}>
                                            <Text style={tw`text-sm font-bold text-slate-900 flex-1`}>{candidate.candidateName}</Text>
                                            {candidate.isWinner && <Ionicons name="star" size={14} color="#f59e0b" />}
                                        </View>
                                        <View style={tw`flex-row items-center gap-2 mt-1`}>
                                            <Text style={tw`text-xs font-semibold text-slate-500`}>
                                                {t('results.votes', { count: candidate.voteCount })}
                                            </Text>
                                            <Text style={tw`text-xs text-slate-400`}>•</Text>
                                            <Text style={tw`text-xs font-semibold text-primary`}>{t('home.percentValue', { value: candidate.percentage?.toFixed(1) })}</Text>
                                        </View>
                                    </View>
                                </View>

                                {/* Bar */}
                                <View style={tw`h-3 bg-slate-100 rounded-full overflow-hidden`}>
                                    <View
                                        style={[
                                            tw`h-full rounded-full`,
                                            {
                                                width: `${Math.max((candidate.voteCount / maxVotes) * 100, 2)}%` as any,
                                                backgroundColor: candidate.isWinner ? '#22c55e' : tw.color('primary'),
                                            }
                                        ]}
                                    />
                                </View>

                                {index < result.candidateResults.length - 1 && <View style={tw`h-px bg-slate-100 mt-4`} />}
                            </View>
                        ))}
                    </View>

                    {/* ── Independent Audit Info ── */}
                    <View style={tw`mx-4 mt-3 rounded-2xl bg-primary/5 p-5 border border-primary/20`}>
                        <View style={tw`flex-row items-center gap-3 mb-3`}>
                            <View style={tw`w-10 h-10 rounded-full bg-primary/10 items-center justify-center`}>
                                <Ionicons name="shield-checkmark" size={22} color={tw.color('primary')} />
                            </View>
                            <View style={tw`flex-1`}>
                                <Text style={tw`text-base font-bold text-slate-900`}>{t('results.auditInfoTitle')}</Text>
                                <Text style={tw`text-xs font-bold text-primary`}>{t('results.auditVerified')}</Text>
                            </View>
                        </View>
                        
                        <Text style={tw`text-sm text-slate-600 leading-relaxed mb-4`}>
                            {t('results.auditInfoDesc')}
                        </Text>

                        <TouchableOpacity 
                            style={tw`flex-row items-center gap-2 bg-surface self-start px-4 py-2 rounded-lg border border-primary/20`}
                            onPress={() => setIsVerifyModalVisible(true)}
                        >
                            <Text style={tw`text-sm font-bold text-primary`}>{t('results.learnMore')}</Text>
                            <Ionicons name="chevron-forward" size={16} color={tw.color('primary')} />
                        </TouchableOpacity>
                    </View>


                    {/* ── Timeline ── */}
                    <View style={tw`mx-4 mt-3 rounded-2xl bg-surface p-5 shadow-sm border border-slate-100`}>
                        <View style={tw`flex-row items-center gap-2 mb-3`}>
                            <Ionicons name="time-outline" size={20} color={tw.color('primary')} />
                            <Text style={tw`text-base font-bold text-slate-900`}>{t('results.timeline')}</Text>
                        </View>
                        <View style={tw`flex-col gap-2`}>
                            <InfoRow label={t('results.start')} value={formatDate(result.startTime, language)} />
                            <View style={tw`h-px bg-slate-100`} />
                            <InfoRow label={t('results.end')} value={formatDate(result.endTime, language)} />
                            {result.resultsCalculatedAt && (
                                <>
                                    <View style={tw`h-px bg-slate-100`} />
                                    <InfoRow label={t('results.calculatedAt')} value={formatDate(result.resultsCalculatedAt, language)} />
                                </>
                            )}
                        </View>
                    </View>

                    {/* Footer */}
                    <View style={tw`flex-row items-center justify-center gap-1.5 opacity-50 mt-6`}>
                        <Ionicons name="shield-checkmark" size={14} color="#64748b" />
                        <Text style={tw`text-xs font-medium text-slate-500`}>{t('results.securedFooter')}</Text>
                    </View>
                </ScrollView>
            )}
            
            {/* ── How to Verify Modal ── */}
            <Modal
                visible={isVerifyModalVisible}
                transparent
                animationType="slide"
                onRequestClose={() => setIsVerifyModalVisible(false)}
            >
                <View style={tw`flex-1 bg-black/60 justify-end`}>
                    <View style={tw`bg-surface rounded-t-[32px] p-6 pb-12`}>
                        <View style={tw`flex-row items-center justify-between mb-8`}>
                            <Text style={tw`text-2xl font-bold text-slate-900`}>{t('results.verifyModal.title')}</Text>
                            <TouchableOpacity 
                                onPress={() => setIsVerifyModalVisible(false)}
                                style={tw`w-10 h-10 rounded-full bg-slate-100 items-center justify-center`}
                            >
                                <Ionicons name="close" size={24} color="#64748b" />
                            </TouchableOpacity>
                        </View>

                        <ScrollView showsVerticalScrollIndicator={false}>
                            <VerifyStep 
                                icon="download-outline" 
                                title={t('results.verifyModal.step1Title')} 
                                desc={t('results.verifyModal.step1Desc')} 
                            />
                            <VerifyStep 
                                icon="sync-outline" 
                                title={t('results.verifyModal.step2Title')} 
                                desc={t('results.verifyModal.step2Desc')} 
                            />
                            <VerifyStep 
                                icon="terminal-outline" 
                                title={t('results.verifyModal.step3Title')} 
                                desc={t('results.verifyModal.step3Desc')} 
                            />

                            <TouchableOpacity
                                onPress={() => setIsVerifyModalVisible(false)}
                                style={tw`bg-primary w-full py-4 rounded-xl mt-4 shadow-sm`}
                            >
                                <Text style={tw`text-center text-white font-bold text-base`}>
                                    {t('results.verifyModal.close')}
                                </Text>
                            </TouchableOpacity>
                        </ScrollView>
                    </View>
                </View>
            </Modal>
        </View>
    );
};

/* ─── Helpers ──────────────────────────────────────────── */

const VerifyStep = ({ icon, title, desc }: { icon: any; title: string, desc: string }) => (
    <View style={tw`flex-row gap-4 mb-6`}>
        <View style={tw`w-12 h-12 rounded-2xl bg-primary/10 items-center justify-center`}>
            <Ionicons name={icon} size={24} color={tw.color('primary')} />
        </View>
        <View style={tw`flex-1`}>
            <Text style={tw`text-base font-bold text-slate-900 mb-1`}>{title}</Text>
            <Text style={tw`text-sm text-slate-500 leading-relaxed`}>{desc}</Text>
        </View>
    </View>
);

const formatDate = (iso: string, lang: string) => {
    try {
        return new Date(iso).toLocaleString(lang === 'en' ? 'en-US' : 'tr-TR', {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit',
        });
    } catch { return iso; }
};

const StatItem = ({ value, label }: { value: string; label: string }) => (
    <View style={tw`items-center`}>
        <Text style={tw`text-2xl font-bold text-primary`}>{value}</Text>
        <Text style={tw`text-xs text-slate-500 mt-1`}>{label}</Text>
    </View>
);

const InfoRow = ({ label, value }: { label: string; value: string }) => (
    <View style={tw`flex-row justify-between`}>
        <Text style={tw`text-sm text-slate-500`}>{label}</Text>
        <Text style={tw`text-sm font-medium text-slate-800`}>{value}</Text>
    </View>
);
