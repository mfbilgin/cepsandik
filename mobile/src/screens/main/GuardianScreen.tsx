import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, FlatList, TouchableOpacity, Alert, ActivityIndicator, RefreshControl } from 'react-native';
import { tw } from '../../utils/tailwind';
import { theme } from '../../utils/theme';
import { Ionicons } from '@expo/vector-icons';
import { api } from '../../services/api';
import { runKeyCeremony, declineCeremony, runDistributedTally } from '../../utils/distributedKeyCeremony';

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

const STATUS_LABEL: Record<GuardianStatus, { text: string; color: string }> = {
    PENDING:        { text: 'ANAHTAR BEKLENİYOR', color: 'text-orange-500' },
    KEY_UPLOADED:   { text: 'ANAHTAR YÜKLENDİ (devam)', color: 'text-amber-500' },
    KEYS_EXCHANGED: { text: 'PAYLAR DEĞİŞTİRİLDİ (devam)', color: 'text-amber-500' },
    READY:          { text: 'HAZIR ✓', color: 'text-green-600' },
    DECLINED:       { text: 'KATILMADINIZ', color: 'text-slate-400' },
    TIMEOUT:        { text: 'SÜRE DOLDU', color: 'text-red-500' },
    SHARE_UPLOADED: { text: 'TALLY PAYI GÖNDERİLDİ', color: 'text-green-600' },
};

export const GuardianScreen = () => {
    const [duties, setDuties] = useState<GuardianDuty[]>([]);
    const [loading, setLoading] = useState(true);
    const [busyId, setBusyId] = useState<number | null>(null);

    const fetchDuties = useCallback(async () => {
        try {
            setLoading(true);
            const response = await api.get('/guardians/my-duties');
            setDuties(response.data);
        } catch (error: any) {
            console.error('Görevler yüklenemedi:', error);
            Alert.alert('Hata', 'Emanetçi görevleriniz yüklenemedi.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchDuties(); }, [fetchDuties]);

    // ---- Eylem 1: Anahtar Yükle (3 round tek tıkla, idempotent) ----
    const handleKeyCeremony = (duty: GuardianDuty) => {
        Alert.alert(
            'Anahtar Üretimi',
            'Cihazınızda kriptografik anahtarınız üretilecek ve diğer emanetçilerle ' +
            'şifreli pay değişimi yapılacak. Gizli anahtarınız cihazınızdan ÇIKMAZ. ' +
            'Tüm emanetçiler hazır olunca seçim aktifleşir. Devam edilsin mi?',
            [
                { text: 'Vazgeç', style: 'cancel' },
                {
                    text: 'Üret ve Yükle',
                    onPress: async () => {
                        setBusyId(duty.electionId);
                        try {
                            const r = await runKeyCeremony(duty.electionId);
                            if (r.completed) {
                                Alert.alert(
                                    'Hazır ✓',
                                    r.jointKeyGenerated
                                        ? 'Tüm emanetçiler tamamlandı, ortak anahtar üretildi ve seçim hazır.'
                                        : 'Anahtar göreviniz tamamlandı. Diğer emanetçiler bekleniyor.',
                                );
                            } else {
                                Alert.alert(
                                    'Devam ediliyor',
                                    'Bu adım tamamlandı ama diğer emanetçiler henüz hazır değil. ' +
                                    'Lütfen bir süre sonra tekrar "Anahtar Yükle" deyin (kaldığınız yerden devam eder).',
                                );
                            }
                            fetchDuties();
                        } catch (err: any) {
                            Alert.alert('Hata', 'Anahtar adımı başarısız: ' +
                                (err.response?.data?.message || err.message));
                        } finally {
                            setBusyId(null);
                        }
                    },
                },
            ],
        );
    };

    // ---- Eylem 2: Bu seçime katılmıyorum (cezasız, STATE_RESET) ----
    const handleDecline = (duty: GuardianDuty) => {
        Alert.alert(
            'Bu seçime katılmıyorum',
            'Göreviniz yedek emanetçiye aktarılacak. Bu CEZASIZDIR — dürüst ' +
            'iletişim ödüllendirilir. Ceremony yeniden başlar (diğer emanetçiler ' +
            'anahtarlarını tekrar üretir). Onaylıyor musunuz?',
            [
                { text: 'Vazgeç', style: 'cancel' },
                {
                    text: 'Katılmıyorum',
                    style: 'destructive',
                    onPress: async () => {
                        setBusyId(duty.electionId);
                        try {
                            const r = await declineCeremony(duty.electionId);
                            if (r.cancelled) {
                                Alert.alert('Seçim İptal Edildi',
                                    'Maksimum yedek değişim sayısına ulaşıldı, seçim iptal edildi.');
                            } else if (r.backupAvailable === false) {
                                Alert.alert('Yedek Yok',
                                    r.message || 'Yedek havuzu tükendi, yönetici müdahalesi gerekli.');
                            } else {
                                Alert.alert('Tamam', 'Göreviniz yedeğe aktarıldı. Teşekkürler.');
                            }
                            fetchDuties();
                        } catch (err: any) {
                            Alert.alert('Hata', 'İşlem başarısız: ' +
                                (err.response?.data?.message || err.message));
                        } finally {
                            setBusyId(null);
                        }
                    },
                },
            ],
        );
    };

    // ---- Tally: distributed pay hesaplama (CLOSED) ----
    const handleTally = (duty: GuardianDuty) => {
        Alert.alert(
            'Hesaplamaya Katıl',
            'Cihazınızdaki gizli anahtar payınızla şifreli toplam üzerinde kısmi ' +
            'çözme yapılacak. Anahtarınız cihazdan ÇIKMAZ — sunucu sadece ' +
            'payları birleştirir (Q-of-N). Devam?',
            [
                { text: 'Vazgeç', style: 'cancel' },
                {
                    text: 'Hesapla',
                    onPress: async () => {
                        setBusyId(duty.electionId);
                        try {
                            const r = await runDistributedTally(duty.electionId);
                            if (r.finalized) {
                                Alert.alert('Sonuç Açıklandı ✓',
                                    'Yeterli pay toplandı, seçim sonucu hesaplandı.');
                            } else if (r.submitted) {
                                Alert.alert('Pay Gönderildi',
                                    'Hesaplama payınız gönderildi. Diğer emanetçiler ' +
                                    'bekleniyor — yeterli sayıya ulaşınca sonuç açılır.');
                            }
                            fetchDuties();
                        } catch (err: any) {
                            Alert.alert('Hata', 'Tally adımı başarısız: ' +
                                (err.response?.data?.message || err.message));
                        } finally {
                            setBusyId(null);
                        }
                    },
                },
            ],
        );
    };

    // ---- Eylem 3: Daha Sonra Hatırlat ----
    const handleRemindLater = () => {
        Alert.alert('Tamam', 'Görevi daha sonra tamamlayabilirsiniz. Süre dolmadan ' +
            'anahtarınızı yükleyin — aksi halde görev yedeğe geçer.');
    };

    const isCeremonyPhase = (d: GuardianDuty) =>
        d.electionStatus === 'SCHEDULED' &&
        (d.status === 'PENDING' || d.status === 'KEY_UPLOADED' || d.status === 'KEYS_EXCHANGED');

    const renderDuty = ({ item }: { item: GuardianDuty }) => {
        const label = STATUS_LABEL[item.status] ?? { text: item.status, color: 'text-secondary' };
        const busy = busyId === item.electionId;
        return (
            <View style={tw`bg-surface p-4 rounded-xl mb-4 border border-slate-100 shadow-sm`}>
                <View style={tw`flex-row justify-between items-start`}>
                    <View style={tw`flex-1`}>
                        <Text style={tw`text-primary font-bold text-lg`}>{item.electionTitle}</Text>
                        {item.communityName && (
                            <Text style={tw`text-secondary text-sm`}>{item.communityName}</Text>
                        )}
                    </View>
                    <View style={tw`bg-primary/10 px-2 py-1 rounded`}>
                        <Text style={tw`text-primary text-[10px] font-bold`}>EMANETÇİ (SİZ)</Text>
                    </View>
                </View>

                <View style={tw`mt-3`}>
                    <Text style={tw`text-xs text-secondary mb-1`}>Durum:</Text>
                    <Text style={tw`text-sm font-semibold ${label.color}`}>{label.text}</Text>
                </View>

                {busy && (
                    <View style={tw`mt-3 flex-row items-center`}>
                        <ActivityIndicator color={theme.colors.primary} size="small" />
                        <Text style={tw`text-secondary text-xs ml-2`}>İşleniyor — kapatmayın…</Text>
                    </View>
                )}

                {!busy && isCeremonyPhase(item) && (
                    <View style={tw`mt-4`}>
                        <TouchableOpacity
                            onPress={() => handleKeyCeremony(item)}
                            style={tw`bg-primary px-4 py-3 rounded-lg mb-2`}
                        >
                            <Text style={tw`text-white font-bold text-center`}>
                                {item.status === 'PENDING' ? 'Anahtar Yükle' : 'Devam Et'}
                            </Text>
                        </TouchableOpacity>
                        <View style={tw`flex-row`}>
                            <TouchableOpacity
                                onPress={() => handleRemindLater()}
                                style={tw`flex-1 bg-slate-100 px-3 py-2 rounded-lg mr-2`}
                            >
                                <Text style={tw`text-secondary font-semibold text-center text-xs`}>
                                    Daha Sonra Hatırlat
                                </Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                onPress={() => handleDecline(item)}
                                style={tw`flex-1 bg-red-50 px-3 py-2 rounded-lg`}
                            >
                                <Text style={tw`text-red-500 font-semibold text-center text-xs`}>
                                    Katılmıyorum
                                </Text>
                            </TouchableOpacity>
                        </View>
                    </View>
                )}

                {!busy && item.status === 'READY' && item.electionStatus === 'SCHEDULED' && (
                    <Text style={tw`mt-3 text-green-600 text-xs`}>
                        ✓ Göreviniz tamam. Diğer emanetçiler hazır olunca seçim aktifleşir.
                    </Text>
                )}

                {!busy && (item.electionStatus === 'CLOSED'
                        || item.electionStatus === 'CLOSED_WAITING_DECRYPTION') && (
                    <View style={tw`mt-4`}>
                        <Text style={tw`text-xs text-secondary mb-2`}>
                            Seçim kapandı. Sonucun açılması için pay hesaplamanıza ihtiyaç var.
                            Gizli anahtarınız cihazınızdan ÇIKMAZ.
                        </Text>
                        <TouchableOpacity
                            onPress={() => handleTally(item)}
                            style={tw`bg-accent-blue px-4 py-3 rounded-lg`}
                        >
                            <Text style={tw`text-white font-bold text-center`}>Hesaplamaya Katıl</Text>
                        </TouchableOpacity>
                    </View>
                )}
            </View>
        );
    };

    return (
        <View style={tw`flex-1 bg-background p-4 pt-12`}>
            <View style={tw`flex-row items-center mb-6`}>
                <View style={tw`bg-primary/20 p-3 rounded-2xl mr-4`}>
                    <Ionicons name="shield-checkmark" size={32} color={theme.colors.primary} />
                </View>
                <View>
                    <Text style={tw`text-primary text-2xl font-bold`}>Emanetçi Görevleri</Text>
                    <Text style={tw`text-secondary text-sm`}>Güvenli seçimler için kriptografik onaylarınız</Text>
                </View>
            </View>

            {loading ? (
                <ActivityIndicator color={theme.colors.primary} style={tw`mt-20`} />
            ) : duties.length > 0 ? (
                <FlatList
                    data={duties}
                    keyExtractor={(item) => item.electionId.toString()}
                    renderItem={renderDuty}
                    contentContainerStyle={tw`pb-20`}
                    refreshControl={
                        <RefreshControl refreshing={loading} onRefresh={fetchDuties}
                            tintColor={theme.colors.primary} />
                    }
                />
            ) : (
                <View style={tw`flex-1 justify-center items-center opacity-40`}>
                    <Ionicons name="documents-outline" size={80} color={theme.colors.secondary} />
                    <Text style={tw`text-secondary font-medium mt-4`}>Henüz bir görev atanmadı.</Text>
                </View>
            )}
        </View>
    );
};
