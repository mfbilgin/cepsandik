import React, { useState, useEffect } from 'react';
import { View, Text, Switch, ActivityIndicator } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { api } from '../../services/api';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card, Button } from '../../components/ui';

interface Preference {
    category: string;
    channel: string;
    enabled: boolean;
}

export const NotificationsScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    const [preferences, setPreferences] = useState<Preference[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        fetchPreferences();
    }, []);

    const fetchPreferences = async () => {
        try {
            const response = await api.get('/users/me/notification-preferences');
            setPreferences(response.data);
        } catch (error) {
            Toast.show({ type: 'error', text1: t('security.loadError'), text2: t('auth.forgot.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    const togglePreference = (category: string, channel: string) => {
        setPreferences(prev => prev.map(p =>
            (p.category === category && p.channel === channel)
            ? { ...p, enabled: !p.enabled }
            : p
        ));
    };

    const handleSave = async () => {
        setIsSaving(true);
        try {
            await api.put('/users/me/notification-preferences', preferences);
            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('notifications.saveSuccess') });
            navigation.goBack();
        } catch (error) {
            Toast.show({ type: 'error', text1: t('auth.forgot.failedTitle'), text2: t('auth.register.failedBody') });
        } finally {
            setIsSaving(false);
        }
    };

    const categories = ['GUARDIAN_DUTY', 'ELECTION_INFO', 'COMMUNITY_NOTICE'];
    const channels = ['PUSH', 'EMAIL'];

    if (isLoading) {
        return (
            <View style={{ flex: 1, backgroundColor: c.background, alignItems: 'center', justifyContent: 'center' }}>
                <ActivityIndicator size="large" color={c.primary} />
            </View>
        );
    }

    return (
        <Screen scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('notifications.settingsTitle')} onBack={() => navigation.goBack()} />
            </View>

            <View style={{ paddingHorizontal: theme.spacing.lg, gap: theme.spacing.md }}>
                {categories.map((cat) => (
                    <Card key={cat} padding={0}>
                        <View style={{ padding: 14, borderBottomWidth: 1, borderBottomColor: c.border }}>
                            <Text style={{ fontSize: 13, fontWeight: '700', color: c.text, letterSpacing: 0.5, textTransform: 'uppercase' }}>
                                {t(`notifications.category.${cat}`)}
                            </Text>
                        </View>

                        {channels.map((channel, ci) => {
                            const pref = preferences.find(p => p.category === cat && p.channel === channel);
                            const isEnabled = pref ? pref.enabled : true;

                            return (
                                <View
                                    key={channel}
                                    style={{
                                        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
                                        padding: 14,
                                        borderBottomWidth: ci < channels.length - 1 ? 1 : 0,
                                        borderBottomColor: c.border,
                                    }}
                                >
                                    <View style={{ flex: 1, marginRight: 16 }}>
                                        <Text style={{ fontSize: 15, fontWeight: '600', color: c.text, marginBottom: 2 }}>
                                            {t(`notifications.channel.${channel}`)}
                                        </Text>
                                        <Text style={{ fontSize: 12, color: c.textSecondary, lineHeight: 17 }}>
                                            {channel === 'PUSH' ? t('notifications.pushDesc') : t('notifications.emailDesc')}
                                        </Text>
                                    </View>
                                    <Switch
                                        trackColor={{ false: c.borderStrong, true: c.primary }}
                                        thumbColor={c.surface}
                                        ios_backgroundColor={c.borderStrong}
                                        onValueChange={() => togglePreference(cat, channel)}
                                        value={isEnabled}
                                    />
                                </View>
                            );
                        })}
                    </Card>
                ))}

                <Button
                    title={isSaving ? t('notifications.saving') : t('notifications.saveChanges')}
                    loading={isSaving}
                    onPress={handleSave}
                    icon={!isSaving ? 'checkmark' : undefined}
                    style={{ marginTop: 4 }}
                />
            </View>
        </Screen>
    );
};
