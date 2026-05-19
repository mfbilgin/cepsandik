import React, { useState, useEffect } from 'react';
import { View, Text, TextInput, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import Toast from 'react-native-toast-message';
import { api } from '../../services/api';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { theme } from '../../utils/theme';
import { AppHeader, Card, Button } from '../../components/ui';

export const CommunityManagementScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { id } = route.params || {};
    const { t } = useI18n();
    const { showDialog } = useUI();
    const c = theme.colors;

    const [community, setCommunity] = useState<any>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');

    const fetchCommunity = async () => {
        try {
            const res = await api.get(`/communities/${id}`);
            const data = res.data?.data;
            setCommunity(data);
            setName(data?.name || '');
            setDescription(data?.description || '');
        } catch (e) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: t('communityManagement.loadError') });
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { fetchCommunity(); }, [id]);

    const handleSave = async () => {
        if (!name.trim()) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: t('communityManagement.nameRequired') });
            return;
        }
        setIsSaving(true);
        try {
            await api.put(`/communities/${id}`, { name: name.trim(), description: description.trim() });
            Toast.show({ type: 'success', text1: t('communityManagement.savedTitle'), text2: t('communityManagement.savedBody') });
            fetchCommunity();
        } catch (e: any) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: e.response?.data?.message || t('communityManagement.updateFail') });
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = () => {
        showDialog({
            title: t('communityManagement.deleteTitle'),
            message: t('communityManagement.deleteBody', { name: community?.name || '' }),
            type: 'confirm',
            confirmText: t('communityManagement.deleteConfirm'),
            onConfirm: async () => {
                try {
                    await api.delete(`/communities/${id}`);
                    Toast.show({ type: 'success', text1: t('communityManagement.deletedTitle'), text2: t('communityManagement.deletedBody') });
                    navigation.navigate('MainTab');
                } catch (e: any) {
                    Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: e.response?.data?.message || t('communityManagement.deleteFail') });
                }
            }
        });
    };

    const inputStyle = {
        backgroundColor: c.surface, borderWidth: 1.5, borderColor: c.borderStrong,
        borderRadius: theme.borderRadius.md, paddingHorizontal: 14, paddingVertical: 12,
        color: c.text, fontSize: 15,
    };
    const labelStyle = { fontSize: 12, fontWeight: '600' as const, color: c.textSecondary, textTransform: 'uppercase' as const, letterSpacing: 0.5, marginBottom: 6 };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader
                    title={t('communityManagement.title')}
                    onBack={() => navigation.goBack()}
                    right={
                        <TouchableOpacity
                            onPress={handleSave}
                            disabled={isSaving}
                            style={{
                                backgroundColor: isSaving ? c.border : c.primary, paddingHorizontal: 16,
                                height: 34, borderRadius: theme.borderRadius.round, alignItems: 'center', justifyContent: 'center',
                            }}
                        >
                            <Text style={{ color: isSaving ? c.textTertiary : c.onPrimary, fontSize: 13, fontWeight: '700' }}>
                                {isSaving ? '...' : t('notifications.saveChanges')}
                            </Text>
                        </TouchableOpacity>
                    }
                />
            </View>

            {isLoading ? (
                <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
                    <ActivityIndicator size="large" color={c.primary} />
                </View>
            ) : (
                <ScrollView contentContainerStyle={{ padding: theme.spacing.md, gap: theme.spacing.md, paddingBottom: 32 }} showsVerticalScrollIndicator={false}>
                    {community?.nameChangeCount > 0 && (
                        <View
                            style={{
                                backgroundColor: c.warningTint, borderWidth: 1, borderColor: c.warningTint,
                                borderRadius: theme.borderRadius.lg, padding: 12, flexDirection: 'row',
                                alignItems: 'center', gap: 8,
                            }}
                        >
                            <MaterialIcons name="info" size={18} color={c.warning} />
                            <Text style={{ color: c.warning, fontSize: 13, fontWeight: '500', flex: 1 }}>
                                {t('communityManagement.nameChanged', { count: community.nameChangeCount })}
                            </Text>
                        </View>
                    )}

                    <Card padding={theme.spacing.md} style={{ gap: theme.spacing.md }}>
                        <Text style={{ color: c.text, fontWeight: '700', fontSize: 15 }}>{t('communityManagement.infoTitle')}</Text>

                        <View>
                            <Text style={labelStyle}>{t('communityManagement.nameLabel')}</Text>
                            <TextInput
                                value={name}
                                onChangeText={setName}
                                style={inputStyle}
                                placeholder={t('communityManagement.namePlaceholder')}
                                placeholderTextColor={c.textTertiary}
                                maxLength={100}
                            />
                        </View>

                        <View>
                            <Text style={labelStyle}>{t('communityManagement.descriptionLabel')}</Text>
                            <TextInput
                                value={description}
                                onChangeText={setDescription}
                                style={[inputStyle, { minHeight: 100 }]}
                                placeholder={t('communityManagement.descriptionPlaceholder')}
                                placeholderTextColor={c.textTertiary}
                                multiline
                                textAlignVertical="top"
                                maxLength={2000}
                            />
                            <Text style={{ fontSize: 12, color: c.textTertiary, textAlign: 'right', marginTop: 4 }}>{description.length}/2000</Text>
                        </View>
                    </Card>

                    {/* Quick Actions */}
                    <Card padding={0}>
                        <Text style={{ color: c.text, fontWeight: '700', fontSize: 15, paddingHorizontal: 16, paddingTop: 16, paddingBottom: 8 }}>
                            {t('communityManagement.quickActions')}
                        </Text>
                        <TouchableOpacity
                            onPress={() => navigation.navigate('CreateElection', { communityId: id })}
                            style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 16, borderTopWidth: 1, borderTopColor: c.border }}
                        >
                            <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: c.primaryTint, alignItems: 'center', justifyContent: 'center' }}>
                                <MaterialIcons name="how-to-vote" size={20} color={c.primary} />
                            </View>
                            <View style={{ flex: 1 }}>
                                <Text style={{ color: c.text, fontWeight: '600', fontSize: 15 }}>{t('communityManagement.startElection')}</Text>
                                <Text style={{ color: c.textSecondary, fontSize: 12, marginTop: 2 }}>{t('communityManagement.startElectionSub')}</Text>
                            </View>
                            <MaterialIcons name="chevron-right" size={22} color={c.textTertiary} />
                        </TouchableOpacity>
                    </Card>

                    {/* Danger Zone */}
                    <Card padding={0} style={{ borderColor: c.dangerTint }}>
                        <Text style={{ color: c.danger, fontWeight: '700', fontSize: 15, paddingHorizontal: 16, paddingTop: 16, paddingBottom: 8 }}>
                            {t('communityManagement.danger')}
                        </Text>
                        <TouchableOpacity
                            onPress={handleDelete}
                            style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 16, borderTopWidth: 1, borderTopColor: c.dangerTint }}
                        >
                            <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: c.dangerTint, alignItems: 'center', justifyContent: 'center' }}>
                                <MaterialIcons name="delete-forever" size={20} color={c.danger} />
                            </View>
                            <View style={{ flex: 1 }}>
                                <Text style={{ color: c.danger, fontWeight: '600', fontSize: 15 }}>{t('communityManagement.deleteCommunity')}</Text>
                                <Text style={{ color: c.danger, opacity: 0.7, fontSize: 12, marginTop: 2 }}>{t('communityManagement.deleteCommunitySub')}</Text>
                            </View>
                            <MaterialIcons name="chevron-right" size={22} color={c.danger} />
                        </TouchableOpacity>
                    </Card>
                </ScrollView>
            )}
        </SafeAreaView>
    );
};
