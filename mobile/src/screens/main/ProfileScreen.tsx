import React, { useLayoutEffect, useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, RefreshControl, Image, Switch } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAuth } from '../../context/AuthContext';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { registerForPushNotificationsAsync } from '../../utils/notificationHandler';
import { api } from '../../services/api';
import { theme } from '../../utils/theme';
import { Card, ListItem } from '../../components/ui';

export const ProfileScreen = () => {
    const [refreshing, setRefreshing] = useState(false);
    const { user, signOut, refreshUser } = useAuth();
    const navigation = useNavigation<any>();
    const { t, language, setLanguage } = useI18n();
    const { showDialog } = useUI();
    const c = theme.colors;

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    const onRefresh = React.useCallback(async () => {
        setRefreshing(true);
        await refreshUser();
        setRefreshing(false);
    }, [refreshUser]);

    const handleLogout = () => {
        showDialog({
            title: t('profile.logoutTitle'),
            message: t('profile.logoutBody'),
            type: 'confirm',
            confirmText: t('profile.logout'),
            onConfirm: signOut,
        });
    };

    const handleImagePick = async () => {
        try {
            const ImagePicker = require('expo-image-picker');
            const result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                aspect: [1, 1],
                quality: 0.5,
            });
            if (!result.canceled && result.assets && result.assets.length > 0) {
                const asset = result.assets[0];
                const formData = new FormData();
                const localUri = asset.uri;
                const filename = localUri.split('/').pop();
                const match = /\.(\w+)$/.exec(filename || '');
                const type = match ? `image/${match[1]}` : `image`;
                formData.append('file', { uri: localUri, name: filename, type } as any);
                Toast.show({ type: 'info', text1: t('profile.uploadingTitle'), text2: t('profile.uploadingBody') });
                await api.post('/users/me/profile-image', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' },
                });
                await refreshUser();
                Toast.show({ type: 'success', text1: t('profile.uploadSuccessTitle'), text2: t('profile.uploadSuccessBody') });
            }
        } catch (error) {
            console.error('Profile image upload failed:', error);
            Toast.show({ type: 'error', text1: t('profile.uploadErrorTitle'), text2: t('profile.uploadErrorBody') });
        }
    };

    const toggleGuardianRole = async () => {
        if (!user) return;
        const currentEligible = (user as any).isGuardianEligible;
        if (!currentEligible) {
            const token = await registerForPushNotificationsAsync();
            try {
                await api.put('/users/me/push-token', { pushToken: token ?? '', guardianEligible: true });
                await refreshUser();
                Toast.show({
                    type: 'success',
                    text1: t('profile.guardianRole'),
                    text2: token ? t('profile.guardianEligibleSuccess') : t('profile.guardianEligibleSuccessNoPush'),
                });
            } catch (error) {
                Toast.show({ type: 'error', text1: t('security.loadError'), text2: t('auth.forgot.failedBody') });
            }
        } else {
            try {
                await api.put('/users/me/push-token', { pushToken: (user as any).pushToken ?? '', guardianEligible: false });
                await refreshUser();
                Toast.show({ type: 'info', text1: t('profile.guardianRole'), text2: t('profile.guardianEligibleDisabled') });
            } catch (error) {
                Toast.show({ type: 'error', text1: t('security.loadError'), text2: t('auth.forgot.failedBody') });
            }
        }
    };

    const LangBtn = ({ code, label }: { code: 'tr' | 'en'; label: string }) => {
        const active = language === code;
        return (
            <TouchableOpacity
                onPress={() => setLanguage(code)}
                activeOpacity={0.8}
                style={{
                    paddingHorizontal: 14, paddingVertical: 7, borderRadius: theme.borderRadius.md,
                    backgroundColor: active ? c.primary : 'transparent',
                }}
            >
                <Text style={{ fontSize: 12, fontWeight: '700', color: active ? c.onPrimary : c.textSecondary }}>
                    {label}
                </Text>
            </TouchableOpacity>
        );
    };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'left', 'right']}>
            <View style={{ paddingVertical: theme.spacing.md, alignItems: 'center', borderBottomWidth: 1, borderBottomColor: c.border, backgroundColor: c.surface }}>
                <Text style={{ fontSize: 18, fontWeight: '700', color: c.text }}>{t('profile.title')}</Text>
            </View>

            <ScrollView
                style={{ flex: 1 }}
                contentContainerStyle={{ paddingHorizontal: theme.spacing.md, paddingBottom: 32 }}
                showsVerticalScrollIndicator={false}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={c.primary} colors={[c.primary]} />}
            >
                {/* Profil kartı */}
                <View style={{ alignItems: 'center', paddingVertical: theme.spacing.lg }}>
                    <View style={{ marginBottom: 14 }}>
                        <View
                            style={{
                                width: 104, height: 104, borderRadius: 52, overflow: 'hidden',
                                backgroundColor: c.primary, borderWidth: 4, borderColor: c.surface,
                                alignItems: 'center', justifyContent: 'center', ...theme.shadows.card,
                            }}
                        >
                            {user?.profileImage ? (
                                <Image source={{ uri: user.profileImage }} style={{ width: '100%', height: '100%' }} />
                            ) : (
                                <Text style={{ fontSize: 40, fontWeight: '700', color: c.onPrimary }}>
                                    {user?.firstName?.[0]}{user?.lastName?.[0]}
                                </Text>
                            )}
                        </View>
                        <TouchableOpacity
                            onPress={handleImagePick}
                            activeOpacity={0.8}
                            style={{
                                position: 'absolute', bottom: 0, right: 0,
                                backgroundColor: c.primary, borderRadius: 18, padding: 9,
                                borderWidth: 2, borderColor: c.surface,
                            }}
                        >
                            <Ionicons name="camera" size={16} color="#fff" />
                        </TouchableOpacity>
                    </View>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text }}>
                        {user?.firstName} {user?.lastName}
                    </Text>
                    <Text style={{ fontSize: 14, color: c.textSecondary, marginTop: 2 }}>
                        {user?.email || t('profile.defaultEmail')}
                    </Text>
                    <View
                        style={{
                            marginTop: 14, flexDirection: 'row', alignItems: 'center', gap: 6,
                            paddingHorizontal: 14, paddingVertical: 6,
                            backgroundColor: c.successTint, borderRadius: theme.borderRadius.round,
                        }}
                    >
                        <Ionicons name="shield-checkmark" size={15} color={c.success} />
                        <Text style={{ fontSize: 12, fontWeight: '700', color: c.success }}>
                            {t('profile.verifiedVoter')}
                        </Text>
                    </View>
                </View>

                {/* Hesap */}
                <Text style={{ fontSize: 12, fontWeight: '700', color: c.textSecondary, marginBottom: 8, marginLeft: 4, letterSpacing: 0.5 }}>
                    {t('profile.section.account')}
                </Text>
                <Card padding={0} style={{ overflow: 'hidden' }}>
                    <View style={{ paddingHorizontal: theme.spacing.md }}>
                        <ListItem
                            icon="person-outline"
                            title={t('profile.editProfile')}
                            onPress={() => navigation.navigate('EditProfile')}
                        />
                        <Divider />
                        <ListItem
                            icon="lock-closed-outline"
                            title={t('profile.securityPassword')}
                            onPress={() => navigation.navigate('SecuritySettings')}
                        />
                        <Divider />
                        <ListItem
                            icon="notifications-outline"
                            title={t('profile.notificationSettings')}
                            onPress={() => navigation.navigate('NotificationSettings')}
                        />
                        <Divider />
                        <ListItem
                            icon="shield-outline"
                            iconTone="success"
                            title={t('profile.guardianRole')}
                            subtitle={t('profile.guardianRoleDesc')}
                            right={
                                <Switch
                                    value={(user as any)?.isGuardianEligible || false}
                                    onValueChange={toggleGuardianRole}
                                    trackColor={{ false: c.borderStrong, true: c.success }}
                                    thumbColor="#fff"
                                />
                            }
                        />
                        <Divider />
                        <ListItem
                            icon="language-outline"
                            title={t('profile.language')}
                            right={
                                <View
                                    style={{
                                        flexDirection: 'row', backgroundColor: c.surfaceAlt,
                                        borderRadius: theme.borderRadius.md, padding: 3,
                                        borderWidth: 1, borderColor: c.border,
                                    }}
                                >
                                    <LangBtn code="tr" label={t('profile.languageTurkish')} />
                                    <LangBtn code="en" label={t('profile.languageEnglish')} />
                                </View>
                            }
                        />
                    </View>
                </Card>

                {/* Destek */}
                <Text style={{ fontSize: 12, fontWeight: '700', color: c.textSecondary, marginBottom: 8, marginTop: theme.spacing.lg, marginLeft: 4, letterSpacing: 0.5 }}>
                    {t('profile.section.support')}
                </Text>
                <Card padding={0} style={{ overflow: 'hidden' }}>
                    <View style={{ paddingHorizontal: theme.spacing.md }}>
                        <ListItem
                            icon="information-circle-outline"
                            title={t('profile.about')}
                            onPress={() => navigation.navigate('About')}
                        />
                        <Divider />
                        <ListItem
                            icon="help-buoy-outline"
                            title={t('profile.helpCenter')}
                            onPress={() => navigation.navigate('Help')}
                        />
                    </View>
                </Card>

                {/* Çıkış */}
                <TouchableOpacity
                    onPress={handleLogout}
                    activeOpacity={0.8}
                    style={{
                        flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
                        padding: 16, borderRadius: theme.borderRadius.lg,
                        backgroundColor: c.dangerTint, marginTop: theme.spacing.lg,
                    }}
                >
                    <Ionicons name="log-out-outline" size={22} color={c.danger} />
                    <Text style={{ color: c.danger, fontWeight: '700', fontSize: 15 }}>{t('profile.logout')}</Text>
                </TouchableOpacity>

                <Text style={{ textAlign: 'center', fontSize: 12, color: c.textTertiary, marginTop: 14 }}>
                    {t('profile.version')}
                </Text>
            </ScrollView>
        </SafeAreaView>
    );
};

const Divider = () => (
    <View style={{ height: 1, backgroundColor: theme.colors.border }} />
);
