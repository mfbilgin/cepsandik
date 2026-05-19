import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, FlatList, ActivityIndicator, RefreshControl } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { runKeyCeremony, declineCeremony, runDistributedTally } from '../../utils/distributedKeyCeremony';
import { useUI } from '../../context/UIContext';
import { theme } from '../../utils/theme';
import { Card, Button, Badge, EmptyState } from '../../components/ui';

type GuardianStatus =
    | 'PENDING' | 'KEY_UPLOADED' | 'KEYS_EXCHANGED' | 'READY'
    | 'DECLINED' | 'TIMEOUT' | 'SHARE_UPLOADED';

type GuardianDuty = {
    electionId: number;
    electionTitle: string;
    communityName?: string;
    status: GuardianStatus;
    electionStatus: 'SCHEDULED' | 'ACTIVE' | 'CLOSED' | 'CLOSED_WAITING_DECRYPTION' | 'ARCHIVED' | 'CANCELLED';
};

type Tone = 'neutral' | 'primary' | 'success' | 'danger' | 'warning' | 'info';
const STATUS_LABEL: Record<GuardianStatus, { text: string; tone: Tone }> = {
    PENDING:        { text: 'ANAHTAR BEKLENİYOR', tone: 'warning' },
    KEY_UPLOADED:   { text: 'ANAHTAR YÜKLENDİ (devam)', tone: 'warning' },
    KEYS_EXCHANGED: { text: 'PAYLAR DEĞİŞTİRİLDİ (devam)', tone: 'warning' },
    READY:          { text: 'HAZIR ✓', tone: 'success' },
    DECLINED:       { text: 'KATILMADINIZ', tone: 'neutral' },
    TIMEOUT:        { text: 'SÜRE DOLDU', tone: 'danger' },
    SHARE_UPLOADED: { text: 'TALLY PAYI GÖNDERİLDİ', tone: 'success' },
};

export const GuardianScreen = () => {
    const [duties, setDuties] = useState<GuardianDuty[]>([]);
    const [loading, setLoading] = useState(true);
    const [busyId, setBusyId] = useState<number | null>(null);
    const { showDialog } = useUI();

    const fetchDuties = useCallback(async () => {
        try {
            setLoading(true);
            const response = await api.get('/guardians/my-duties');
            setDuties(response.data);
        } catch (error: any) {
            console.error('Görevler yüklenemedi:', error);
            showDialog({ title: 'Hata', message: 'Emanetçi görevleriniz yüklenemedi.', type: 'error' });
        } finally {
            setLoading(false);
        }
    }, [showDialog]);

    useEffect(() => { fetchDuties(); }, [fetchDuties]);

    // ---- Eylem 1: Anahtar Yükle (3 round tek tıkla, idempotent) ----
    const handleKeyCeremony = (duty: GuardianDuty) => {
        showDialog({
            title: 'Anahtar Üretimi',
            message: 'Cihazınızda kriptografik anahtarınız üretilecek ve diğer emanetçilerle '
                + 'şifreli pay değişimi yapılacak. Gizli anahtarınız cihazınızdan ÇIKMAZ. '
                + 'Tüm emanetçiler hazır olunca seçim aktifleşir. Devam edilsin mi?',
            type: 'confirm',
            confirmText: 'Üret ve Yükle',
            cancelText: 'Vazgeç',
            onConfirm: async () => {
                setBusyId(duty.electionId);
                try {
                    const r = await runKeyCeremony(duty.electionId);
                    if (r.completed) {
                        showDialog({
                            title: 'Hazır ✓',
                            message: r.jointKeyGenerated
                                ? 'Tüm emanetçiler tamamlandı, ortak anahtar üretildi ve seçim hazır.'
                                : 'Anahtar göreviniz tamamlandı. Diğer emanetçiler bekleniyor.',
                            type: 'success',
                        });
                    } else {
                        showDialog({
                            title: 'Devam ediliyor',
                            message: 'Bu adım tamamlandı ama diğer emanetçiler henüz hazır değil. '
                                + 'Lütfen bir süre sonra tekrar "Anahtar Yükle" deyin (kaldığınız yerden devam eder).',
                            type: 'info',
                        });
                    }
                    fetchDuties();
                } catch (err: any) {
                    showDialog({
                        title: 'Hata',
                        message: 'Anahtar adımı başarısız: ' + (err.response?.data?.message || err.message),
                        type: 'error',
                    });
                } finally {
                    setBusyId(null);
                }
            },
        });
    };

    // ---- Eylem 2: Bu seçime katılmıyorum (cezasız, STATE_RESET) ----
    const handleDecline = (duty: GuardianDuty) => {
        showDialog({
            title: 'Bu seçime katılmıyorum',
            message: 'Göreviniz yedek emanetçiye aktarılacak. Bu CEZASIZDIR — dürüst '
                + 'iletişim ödüllendirilir. Ceremony yeniden başlar (diğer emanetçiler '
                + 'anahtarlarını tekrar üretir). Onaylıyor musunuz?',
            type: 'confirm',
            confirmText: 'Katılmıyorum',
            cancelText: 'Vazgeç',
            onConfirm: async () => {
                setBusyId(duty.electionId);
                try {
                    const r = await declineCeremony(duty.electionId);
                    if (r.cancelled) {
                        showDialog({ title: 'Seçim İptal Edildi', message: 'Maksimum yedek değişim sayısına ulaşıldı, seçim iptal edildi.', type: 'warning' });
                    } else if (r.backupAvailable === false) {
                        showDialog({ title: 'Yedek Yok', message: r.message || 'Yedek havuzu tükendi, yönetici müdahalesi gerekli.', type: 'warning' });
                    } else {
                        showDialog({ title: 'Tamam', message: 'Göreviniz yedeğe aktarıldı. Teşekkürler.', type: 'success' });
                    }
                    fetchDuties();
                } catch (err: any) {
                    showDialog({ title: 'Hata', message: 'İşlem başarısız: ' + (err.response?.data?.message || err.message), type: 'error' });
                } finally {
                    setBusyId(null);
                }
            },
        });
    };

    // ---- Tally: distributed pay hesaplama (CLOSED) ----
    const handleTally = (duty: GuardianDuty) => {
        showDialog({
            title: 'Hesaplamaya Katıl',
            message: 'Cihazınızdaki gizli anahtar payınızla şifreli toplam üzerinde kısmi '
                + 'çözme yapılacak. Anahtarınız cihazdan ÇIKMAZ — sunucu sadece '
                + 'payları birleştirir (Q-of-N). Devam?',
            type: 'confirm',
            confirmText: 'Hesapla',
            cancelText: 'Vazgeç',
            onConfirm: async () => {
                setBusyId(duty.electionId);
                try {
                    const r = await runDistributedTally(duty.electionId);
                    if (r.finalized) {
                        showDialog({ title: 'Sonuç Açıklandı ✓', message: 'Yeterli pay toplandı, seçim sonucu hesaplandı.', type: 'success' });
                    } else if (r.submitted) {
                        showDialog({
                            title: 'Pay Gönderildi',
                            message: 'Hesaplama payınız gönderildi. Diğer emanetçiler bekleniyor — '
                                + 'yeterli sayıya ulaşınca sonuç açılır.',
                            type: 'info',
                        });
                    }
                    fetchDuties();
                } catch (err: any) {
                    showDialog({ title: 'Hata', message: 'Tally adımı başarısız: ' + (err.response?.data?.message || err.message), type: 'error' });
                } finally {
                    setBusyId(null);
                }
            },
        });
    };

    const handleRemindLater = () => {
        showDialog({
            title: 'Tamam',
            message: 'Görevi daha sonra tamamlayabilirsiniz. Süre dolmadan anahtarınızı '
                + 'yükleyin — aksi halde görev yedeğe geçer.',
            type: 'info',
        });
    };

    const isCeremonyPhase = (d: GuardianDuty) =>
        d.electionStatus === 'SCHEDULED' &&
        (d.status === 'PENDING' || d.status === 'KEY_UPLOADED' || d.status === 'KEYS_EXCHANGED');

    const c = theme.colors;

    const renderDuty = ({ item }: { item: GuardianDuty }) => {
        const label = STATUS_LABEL[item.status] ?? { text: item.status, tone: 'neutral' as Tone };
        const busy = busyId === item.electionId;
        const isClosed = item.electionStatus === 'CLOSED' || item.electionStatus === 'CLOSED_WAITING_DECRYPTION';
        return (
            <Card style={{ marginBottom: 14 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 }}>
                    <View style={{ flex: 1 }}>
                        <Text style={{ fontSize: 16, fontWeight: '700', color: c.text }} numberOfLines={2}>
                            {item.electionTitle}
                        </Text>
                        {item.communityName != null && String(item.communityName).trim() !== '' && (
                            <Text style={{ fontSize: 13, color: c.textSecondary, marginTop: 2 }}>
                                Topluluk: {item.communityName}
                            </Text>
                        )}
                    </View>
                    <Badge label="EMANETÇİ (SİZ)" tone="primary" />
                </View>

                <View style={{ marginTop: 12 }}>
                    <Text style={{ fontSize: 12, color: c.textSecondary, marginBottom: 4 }}>Durum:</Text>
                    <Badge label={label.text} tone={label.tone} dot />
                </View>

                {busy && (
                    <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 14, gap: 8 }}>
                        <ActivityIndicator color={c.primary} size="small" />
                        <Text style={{ fontSize: 12, color: c.textSecondary }}>İşleniyor — kapatmayın…</Text>
                    </View>
                )}

                {!busy && isCeremonyPhase(item) && (
                    <View style={{ marginTop: 16 }}>
                        <Button
                            title={item.status === 'PENDING' ? 'Anahtar Yükle' : 'Devam Et'}
                            icon="key-outline"
                            onPress={() => handleKeyCeremony(item)}
                        />
                        <View style={{ flexDirection: 'row', gap: 10, marginTop: 10 }}>
                            <View style={{ flex: 1 }}>
                                <Button
                                    title="Daha Sonra"
                                    variant="secondary"
                                    size="sm"
                                    onPress={handleRemindLater}
                                />
                            </View>
                            <View style={{ flex: 1 }}>
                                <Button
                                    title="Katılmıyorum"
                                    variant="danger"
                                    size="sm"
                                    onPress={() => handleDecline(item)}
                                />
                            </View>
                        </View>
                    </View>
                )}

                {!busy && item.status === 'READY' && item.electionStatus === 'SCHEDULED' && (
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 14 }}>
                        <Ionicons name="checkmark-circle" size={16} color={c.success} />
                        <Text style={{ fontSize: 12, color: c.success, flex: 1 }}>
                            Göreviniz tamam. Diğer emanetçiler hazır olunca seçim aktifleşir.
                        </Text>
                    </View>
                )}

                {!busy && isClosed && (
                    <View style={{ marginTop: 16 }}>
                        <Text style={{ fontSize: 12, color: c.textSecondary, marginBottom: 10, lineHeight: 18 }}>
                            Seçim kapandı. Sonucun açılması için pay hesaplamanıza ihtiyaç var.
                            Gizli anahtarınız cihazınızdan ÇIKMAZ.
                        </Text>
                        <Button
                            title="Hesaplamaya Katıl"
                            icon="calculator-outline"
                            onPress={() => handleTally(item)}
                        />
                    </View>
                )}
            </Card>
        );
    };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            <View style={{ paddingHorizontal: theme.spacing.md, paddingTop: theme.spacing.sm }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14, marginBottom: theme.spacing.md }}>
                    <View
                        style={{
                            width: 48, height: 48, borderRadius: theme.borderRadius.lg,
                            backgroundColor: c.primaryTint, alignItems: 'center', justifyContent: 'center',
                        }}
                    >
                        <Ionicons name="shield-checkmark" size={26} color={c.primary} />
                    </View>
                    <View style={{ flex: 1 }}>
                        <Text style={{ fontSize: 22, fontWeight: '700', color: c.text }}>Emanetçi Görevleri</Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, marginTop: 2 }}>
                            Güvenli seçimler için kriptografik onaylarınız
                        </Text>
                    </View>
                </View>
            </View>

            {loading ? (
                <ActivityIndicator color={c.primary} style={{ marginTop: 80 }} />
            ) : duties.length > 0 ? (
                <FlatList
                    data={duties}
                    keyExtractor={(item) => item.electionId.toString()}
                    renderItem={renderDuty}
                    contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 24 }}
                    showsVerticalScrollIndicator={false}
                    refreshControl={
                        <RefreshControl refreshing={loading} onRefresh={fetchDuties} tintColor={c.primary} colors={[c.primary]} />
                    }
                />
            ) : (
                <EmptyState
                    icon="documents-outline"
                    title="Henüz bir görev atanmadı"
                    subtitle="Bir seçimde emanetçi seçildiğinizde görev burada görünür."
                />
            )}
        </SafeAreaView>
    );
};
