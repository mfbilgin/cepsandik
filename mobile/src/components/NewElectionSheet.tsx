import React, { useEffect, useState } from 'react';
import {
    View, Text, TouchableOpacity, ScrollView, ActivityIndicator, Modal,
} from 'react-native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { api } from '../services/api';
import { useI18n } from '../i18n/LanguageContext';
import { theme } from '../utils/theme';

interface Props {
    visible: boolean;
    onClose: () => void;
}

/**
 * Yeni Seçim Oluştur alt sayfası.
 * - "Bağımsız Seçim (kod ile)" → CreateElection (communityId yok → seçim
 *   bittiğinde 6 haneli erişim kodu üretilip paylaşılır).
 * - Kullanıcının OWNER/ADMIN olduğu toplulukların listesi → o topluluk
 *   bağlamında seçim.
 */
export const NewElectionSheet: React.FC<Props> = ({ visible, onClose }) => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;
    const [communities, setCommunities] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!visible) return;
        let active = true;
        (async () => {
            setLoading(true);
            try {
                const res = await api.get('/communities');
                const list = res.data?.data?.content || res.data?.data || [];
                if (active) {
                    setCommunities(list.filter((x: any) => x.userRole === 'OWNER' || x.userRole === 'ADMIN'));
                }
            } catch {
                if (active) setCommunities([]);
            } finally {
                if (active) setLoading(false);
            }
        })();
        return () => { active = false; };
    }, [visible]);

    const goTo = (communityId?: number) => {
        onClose();
        if (communityId) {
            navigation.navigate('CreateElection', { communityId });
        } else {
            navigation.navigate('CreateElection');
        }
    };

    return (
        <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
            <TouchableOpacity
                activeOpacity={1}
                onPress={onClose}
                style={{ flex: 1, backgroundColor: 'rgba(15,23,42,0.45)', justifyContent: 'flex-end' }}
            >
                <TouchableOpacity
                    activeOpacity={1}
                    style={{
                        backgroundColor: c.surface, borderTopLeftRadius: theme.borderRadius.xl,
                        borderTopRightRadius: theme.borderRadius.xl, paddingBottom: theme.spacing.xl,
                        maxHeight: '85%',
                    }}
                >
                    {/* Sheet handle + title */}
                    <View style={{ alignItems: 'center', paddingTop: theme.spacing.md, paddingBottom: theme.spacing.sm }}>
                        <View style={{ width: 40, height: 4, borderRadius: 2, backgroundColor: c.borderStrong, marginBottom: theme.spacing.md }} />
                        <Text style={{ fontSize: 18, fontWeight: '700', color: c.text }}>
                            {t('home.newElectionTitle') || 'Yeni Seçim Oluştur'}
                        </Text>
                        <Text style={{ fontSize: 13, color: c.textSecondary, marginTop: 4, textAlign: 'center', paddingHorizontal: theme.spacing.lg }}>
                            {t('home.newElectionHelp') || 'Bir topluluk seç veya kod ile bağımsız bir seçim başlat.'}
                        </Text>
                    </View>

                    <ScrollView contentContainerStyle={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.md, gap: 10 }} showsVerticalScrollIndicator={false}>
                        {/* Independent option — primary */}
                        <TouchableOpacity
                            onPress={() => goTo()}
                            activeOpacity={0.7}
                            style={{
                                flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16,
                                backgroundColor: c.primaryTint, borderRadius: theme.borderRadius.lg,
                                borderWidth: 1.5, borderColor: c.primary,
                            }}
                        >
                            <View style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: c.primary, alignItems: 'center', justifyContent: 'center' }}>
                                <Ionicons name="key" size={22} color={c.onPrimary} />
                            </View>
                            <View style={{ flex: 1 }}>
                                <Text style={{ fontSize: 15, fontWeight: '700', color: c.text }}>
                                    {t('home.independentElection') || 'Bağımsız Seçim (kod ile)'}
                                </Text>
                                <Text style={{ fontSize: 12, color: c.textSecondary, marginTop: 2, lineHeight: 17 }}>
                                    {t('home.independentElectionHelp') || 'Topluluksuz, 6 haneli kod üretilir; paylaştığın herkes katılabilir.'}
                                </Text>
                            </View>
                            <Ionicons name="chevron-forward" size={20} color={c.primary} />
                        </TouchableOpacity>

                        {/* Communities header */}
                        <Text style={{ fontSize: 12, fontWeight: '700', color: c.textSecondary, marginTop: theme.spacing.md, marginBottom: 4, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                            {t('home.pickCommunity') || 'Topluluğun için'}
                        </Text>

                        {loading ? (
                            <ActivityIndicator size="small" color={c.primary} style={{ marginVertical: 16 }} />
                        ) : communities.length === 0 ? (
                            <View style={{ padding: 16, borderRadius: theme.borderRadius.lg, backgroundColor: c.surfaceAlt, borderWidth: 1, borderColor: c.border }}>
                                <Text style={{ fontSize: 13, color: c.textSecondary, textAlign: 'center', lineHeight: 18 }}>
                                    {t('home.noManagedCommunities') || 'Yönettiğin topluluk yok. Bir topluluk oluşturup yönetici olabilir veya yukarıdan bağımsız bir seçim başlatabilirsin.'}
                                </Text>
                            </View>
                        ) : (
                            communities.map((comm) => (
                                <TouchableOpacity
                                    key={comm.id}
                                    onPress={() => goTo(comm.id)}
                                    activeOpacity={0.7}
                                    style={{
                                        flexDirection: 'row', alignItems: 'center', gap: 14, padding: 14,
                                        backgroundColor: c.surface, borderRadius: theme.borderRadius.lg,
                                        borderWidth: 1, borderColor: c.border,
                                    }}
                                >
                                    <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: c.primaryTint, alignItems: 'center', justifyContent: 'center' }}>
                                        <Text style={{ fontSize: 18, fontWeight: '700', color: c.primary }}>
                                            {comm.name?.[0]?.toUpperCase()}
                                        </Text>
                                    </View>
                                    <View style={{ flex: 1 }}>
                                        <Text style={{ fontSize: 14, fontWeight: '600', color: c.text }} numberOfLines={1}>
                                            {comm.name}
                                        </Text>
                                        <Text style={{ fontSize: 11, color: c.textSecondary, marginTop: 2 }}>
                                            {comm.userRole === 'OWNER' ? (t('communityDetail.role.owner') || 'Sahip') : (t('communityDetail.role.admin') || 'Yönetici')}
                                        </Text>
                                    </View>
                                    <Ionicons name="chevron-forward" size={18} color={c.textTertiary} />
                                </TouchableOpacity>
                            ))
                        )}

                        <TouchableOpacity
                            onPress={onClose}
                            style={{
                                marginTop: theme.spacing.md, paddingVertical: 14, borderRadius: theme.borderRadius.lg,
                                backgroundColor: c.surfaceAlt, alignItems: 'center',
                            }}
                        >
                            <Text style={{ fontSize: 14, fontWeight: '600', color: c.textSecondary }}>
                                {t('common.cancel') || 'İptal'}
                            </Text>
                        </TouchableOpacity>

                        {/* Discreet footer note */}
                        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8, marginTop: theme.spacing.md, paddingHorizontal: 4 }}>
                            <MaterialIcons name="info-outline" size={14} color={c.textTertiary} />
                            <Text style={{ flex: 1, fontSize: 11, color: c.textTertiary, lineHeight: 16 }}>
                                {t('home.electionHint') || 'Topluluğun seçiminde sadece üyeler oy verebilir; bağımsız seçimde kodu olan herkes katılabilir.'}
                            </Text>
                        </View>
                    </ScrollView>
                </TouchableOpacity>
            </TouchableOpacity>
        </Modal>
    );
};
