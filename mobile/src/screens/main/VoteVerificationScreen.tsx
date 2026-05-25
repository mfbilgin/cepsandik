import React, { useEffect, useState, useLayoutEffect } from 'react';
import { View, Text, ScrollView, ActivityIndicator, TextInput, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRoute, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { AppHeader, Card, Button } from '../../components/ui';
import { getVoteReceipts, VoteReceipt } from '../../utils/voteReceipts';

/**
 * E2E-V oy doğrulama — KİMLİK KULLANMAZ. Eski sürüm /votes/my-proof
 * çağırıyordu; tam E2E-V modunda bu uçnokta gizlilik gereği reddediliyor
 * ("kullanıcı kimliğiyle oy kanıtı getirilemez"). Doğru model: seçmen oy
 * verirken aldığı TAKİP KODUNU herkese açık ilan panosunda (public
 * /audit-record bulletin chain) arar. Kimlik↔oy bağı kurulmaz; yalnız
 * "oyum panoya kaydedildi mi + zincir bütün mü" kanıtlanır.
 */
export const VoteVerificationScreen = () => {
    const route = useRoute<any>();
    const navigation = useNavigation<any>();
    const { electionId, trackingCode: presetCode } = route.params || {};
    const { t } = useI18n();

    const [election, setElection] = useState<any>(null);
    const [code, setCode] = useState<string>(presetCode || '');
    const [savedReceipts, setSavedReceipts] = useState<VoteReceipt[]>([]);
    const [checking, setChecking] = useState(false);
    const [result, setResult] = useState<
        | { state: 'found'; record: any; chain: { valid?: boolean; verifiedRecords?: number } }
        | { state: 'notfound' }
        | { state: 'error'; message: string }
        | null
    >(null);

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        (async () => {
            try {
                const res = await api.get(`/elections/${electionId}`);
                setElection(res.data?.data || null);
            } catch { /* election bilgisi opsiyonel */ }
        })();
    }, [electionId]);

    useEffect(() => {
        (async () => {
            const receipts = await getVoteReceipts(electionId);
            setSavedReceipts(receipts);
            if (!presetCode && !code && receipts[0]?.trackingCode) {
                setCode(receipts[0].trackingCode);
            }
        })();
    }, [electionId]);

    const norm = (s: string) => s.trim().toLowerCase().replace(/\s+/g, '');

    const handleVerify = async () => {
        const entered = norm(code);
        if (!entered) return;
        setChecking(true);
        setResult(null);
        try {
            const res = await api.get(`/elections/${electionId}/audit-record`);
            const body = res.data?.data ?? res.data;
            const bb = body?.bulletinBoard || {};
            const records: any[] = Array.isArray(bb.records) ? bb.records : [];
            const match = records.find((r) => {
                const tc = r?.trackingCode ? norm(String(r.trackingCode)) : '';
                // tam eşleşme veya kullanıcı sadece hex'i yapıştırdıysa içerme
                return tc && (tc === entered || tc.includes(entered) || entered.includes(tc));
            });
            if (match) {
                setResult({
                    state: 'found',
                    record: match,
                    chain: bb.chainIntegrity || {},
                });
            } else {
                setResult({ state: 'notfound' });
            }
        } catch (e: any) {
            setResult({
                state: 'error',
                message: e.response?.data?.message || t('verification.loadError'),
            });
        } finally {
            setChecking(false);
        }
    };

    const c = theme.colors;

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            <View style={{ paddingHorizontal: theme.spacing.md, paddingTop: theme.spacing.sm }}>
                <AppHeader title={t('verification.title')} />
            </View>

            <ScrollView
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 32, gap: 12 }}
                showsVerticalScrollIndicator={false}
                keyboardShouldPersistTaps="handled"
            >
                {/* Takip kodu girişi */}
                {savedReceipts.length > 0 && (
                    <Card>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                            <Ionicons name="bookmark-outline" size={20} color={c.primary} />
                            <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                                {t('verification.savedReceiptsTitle')}
                            </Text>
                        </View>
                        <View style={{ gap: 8 }}>
                            {savedReceipts.map((receipt) => (
                                <TouchableOpacity
                                    key={`${receipt.electionId}:${receipt.trackingCode}`}
                                    onPress={() => setCode(receipt.trackingCode)}
                                    style={{
                                        padding: 12,
                                        borderWidth: 1,
                                        borderColor: code === receipt.trackingCode ? c.primary : c.border,
                                        borderRadius: theme.borderRadius.md,
                                        backgroundColor: code === receipt.trackingCode ? c.primaryTint : c.surfaceAlt,
                                    }}
                                >
                                    <Text style={{ fontSize: 13, fontWeight: '700', color: c.text, marginBottom: 4 }}>
                                        {receipt.electionTitle || election?.title || `#${receipt.electionId}`}
                                    </Text>
                                    <Text style={{ fontSize: 12, fontWeight: '700', color: c.text }}>
                                        {receipt.trackingCode}
                                    </Text>
                                    <Text style={{ fontSize: 11, color: c.textSecondary, marginTop: 4 }}>
                                        {new Date(receipt.recordedAt).toLocaleString()}
                                    </Text>
                                </TouchableOpacity>
                            ))}
                        </View>
                    </Card>
                )}

                <Card>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                        <Ionicons name="finger-print-outline" size={20} color={c.primary} />
                        <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                            {t('verification.enterCodeTitle')}
                        </Text>
                    </View>
                    <Text style={{ fontSize: 13, color: c.textSecondary, lineHeight: 19, marginBottom: 12 }}>
                        {t('verification.enterCodeDesc')}
                    </Text>
                    <TextInput
                        value={code}
                        onChangeText={setCode}
                        placeholder={t('verification.codePlaceholder')}
                        placeholderTextColor={c.textTertiary}
                        autoCapitalize="none"
                        autoCorrect={false}
                        multiline
                        style={{
                            backgroundColor: c.surfaceAlt,
                            borderWidth: 1.5,
                            borderColor: c.borderStrong,
                            borderRadius: theme.borderRadius.md,
                            padding: 12,
                            fontSize: 13,
                            color: c.text,
                            minHeight: 56,
                            marginBottom: 12,
                        }}
                    />
                    <Button
                        title={checking ? t('verification.checking') : t('verification.verifyButton')}
                        icon="search-outline"
                        loading={checking}
                        disabled={checking || !code.trim()}
                        onPress={handleVerify}
                    />
                </Card>

                {/* Sonuç */}
                {result?.state === 'found' && (
                    <Card style={{ borderColor: c.success, backgroundColor: c.successTint }}>
                        <View style={{ alignItems: 'center', marginBottom: 8 }}>
                            <Ionicons name="shield-checkmark" size={40} color={c.success} />
                        </View>
                        <Text style={{ fontSize: 17, fontWeight: '700', color: c.text, textAlign: 'center' }}>
                            {t('verification.foundTitle')}
                        </Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, textAlign: 'center', marginTop: 6, lineHeight: 19 }}>
                            {t('verification.foundDesc')}
                        </Text>
                        <View style={{ height: 1, backgroundColor: c.border, marginVertical: 12 }} />
                        <Row label={t('verification.recordType')} value={String(result.record.recordType || '—')} />
                        {result.record.createdAt && (
                            <Row label={t('verification.recordedAt')} value={String(result.record.createdAt)} />
                        )}
                        {result.chain?.valid && (
                            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 8 }}>
                                <Ionicons name="link" size={14} color={c.success} />
                                <Text style={{ fontSize: 12, color: c.success, fontWeight: '600' }}>
                                    {t('verification.chainIntact', { count: result.chain.verifiedRecords ?? '—' })}
                                </Text>
                            </View>
                        )}
                    </Card>
                )}

                {result?.state === 'notfound' && (
                    <Card style={{ borderColor: c.warning, backgroundColor: c.warningTint }}>
                        <View style={{ alignItems: 'center', marginBottom: 8 }}>
                            <Ionicons name="alert-circle" size={40} color={c.warning} />
                        </View>
                        <Text style={{ fontSize: 17, fontWeight: '700', color: c.text, textAlign: 'center' }}>
                            {t('verification.notFoundTitle')}
                        </Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, textAlign: 'center', marginTop: 6, lineHeight: 19 }}>
                            {t('verification.notFoundDesc')}
                        </Text>
                    </Card>
                )}

                {result?.state === 'error' && (
                    <Card style={{ borderColor: c.danger, backgroundColor: c.dangerTint }}>
                        <Text style={{ fontSize: 14, color: c.danger, textAlign: 'center', fontWeight: '600' }}>
                            {result.message}
                        </Text>
                    </Card>
                )}

                {/* Seçim bilgisi */}
                {election && (
                    <Card>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                            <Ionicons name="information-circle-outline" size={20} color={c.textSecondary} />
                            <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                                {t('verification.electionInfo')}
                            </Text>
                        </View>
                        <Row label={t('verification.election')} value={election?.title || `#${electionId}`} />
                        <View style={{ height: 1, backgroundColor: c.border, marginVertical: 8 }} />
                        <Row label={t('verification.status')} value={election?.status || '—'} />
                    </Card>
                )}

                {/* Eğitim */}
                <Card>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                        <Ionicons name="help-circle-outline" size={20} color={c.textSecondary} />
                        <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }}>
                            {t('verification.whatIsThis')}
                        </Text>
                    </View>
                    <Text style={{ fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                        {t('verification.whatIsThisDesc')}
                    </Text>
                </Card>

                <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 4, opacity: 0.7 }}>
                    <Ionicons name="shield-checkmark" size={14} color={c.textTertiary} />
                    <Text style={{ fontSize: 12, color: c.textTertiary, fontWeight: '500' }}>
                        {t('verification.securedFooter')}
                    </Text>
                </View>
            </ScrollView>
        </SafeAreaView>
    );
};

const Row = ({ label, value }: { label: string; value: string }) => (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text style={{ fontSize: 13, color: theme.colors.textSecondary }}>{label}</Text>
        <Text
            style={{ fontSize: 13, fontWeight: '600', color: theme.colors.text, maxWidth: '60%', textAlign: 'right' }}
            numberOfLines={2}
        >
            {value}
        </Text>
    </View>
);
