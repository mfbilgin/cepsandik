import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, Keyboard, Switch } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { api } from '../../services/api';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card, Input, Button } from '../../components/ui';

export const CreateCommunityScreen = () => {
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [isPrivate, setIsPrivate] = useState(false);
    const [isLoading, setIsLoading] = useState(false);

    const handleCreate = async () => {
        if (!name.trim() || !description.trim()) {
            Toast.show({ type: 'error', text1: t('auth.login.missingTitle'), text2: t('createCommunity.missingBody') });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();

        try {
            await api.post('/communities', {
                name: name.trim(),
                description: description.trim(),
                visibility: isPrivate ? 'PRIVATE' : 'PUBLIC'
            });
            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('createCommunity.successBody') });
            setTimeout(() => {
                navigation.goBack();
            }, 1000);
        } catch (error: any) {
            Toast.show({
                type: 'error',
                text1: t('createCommunity.failedTitle'),
                text2: error.response?.data?.message || t('auth.register.failedBody')
            });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Screen keyboardAvoiding scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('createCommunity.title')} onBack={() => navigation.goBack()} />
            </View>

            <View style={{ paddingHorizontal: theme.spacing.lg }}>
                {/* Logo Uploader */}
                <View style={{ alignItems: 'center', marginBottom: theme.spacing.xl, marginTop: theme.spacing.sm }}>
                    <TouchableOpacity
                        activeOpacity={0.8}
                        onPress={() => Toast.show({ type: 'info', text1: t('profile.comingSoonTitle'), text2: t('createCommunity.logoSoon') })}
                    >
                        <View
                            style={{
                                width: 112, height: 112, borderRadius: 56, backgroundColor: c.surfaceAlt,
                                alignItems: 'center', justifyContent: 'center', borderWidth: 2,
                                borderStyle: 'dashed', borderColor: c.borderStrong, overflow: 'hidden',
                            }}
                        >
                            <Ionicons name="camera-outline" size={36} color={c.textTertiary} />
                        </View>
                        <View
                            style={{
                                position: 'absolute', bottom: 0, right: 0, backgroundColor: c.primary,
                                borderRadius: 14, padding: 6, borderWidth: 2, borderColor: c.surface,
                                alignItems: 'center', justifyContent: 'center',
                            }}
                        >
                            <Ionicons name="add" size={16} color={c.onPrimary} />
                        </View>
                    </TouchableOpacity>
                    <Text style={{ marginTop: 12, fontSize: 14, fontWeight: '600', color: c.primary }}>
                        {t('createCommunity.uploadLogo')}
                    </Text>
                </View>

                <Input
                    label={`${t('createCommunity.nameLabel')} *`}
                    placeholder={t('createCommunity.namePlaceholder')}
                    value={name}
                    onChangeText={setName}
                    maxLength={50}
                />

                <View style={{ marginBottom: theme.spacing.md }}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
                        <Text style={{ fontSize: 13, fontWeight: '600', color: c.textSecondary }}>
                            {t('createCommunity.descriptionLabel')}
                        </Text>
                        <Text style={{ fontSize: 12, color: c.textTertiary }}>{description.length}/300</Text>
                    </View>
                    <TextInput
                        style={{
                            width: '100%', paddingHorizontal: 12, paddingVertical: 12,
                            borderRadius: theme.borderRadius.md, backgroundColor: c.surface,
                            borderWidth: 1.5, borderColor: c.borderStrong, color: c.text,
                            fontSize: 15, height: 112,
                        }}
                        placeholder={t('createCommunity.descriptionPlaceholder')}
                        placeholderTextColor={c.textTertiary}
                        value={description}
                        onChangeText={setDescription}
                        multiline
                        textAlignVertical="top"
                        maxLength={300}
                    />
                </View>

                {/* Privacy Toggle */}
                <Card padding={theme.spacing.md} style={{ marginBottom: theme.spacing.lg }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                            <View style={{ backgroundColor: c.primaryTint, padding: 8, borderRadius: theme.borderRadius.round }}>
                                <Ionicons name="lock-closed" size={20} color={c.primary} />
                            </View>
                            <Text style={{ fontWeight: '600', color: c.text, fontSize: 15 }}>{t('createCommunity.privateTitle')}</Text>
                        </View>
                        <Switch
                            trackColor={{ false: c.borderStrong, true: c.primary }}
                            thumbColor={c.surface}
                            ios_backgroundColor={c.borderStrong}
                            onValueChange={setIsPrivate}
                            value={isPrivate}
                        />
                    </View>
                    <Text style={{ fontSize: 13, color: c.textSecondary, paddingLeft: 52, lineHeight: 19 }}>
                        {t('createCommunity.privateDesc')}
                    </Text>
                </Card>

                <Button
                    title={isLoading ? t('createCommunity.creating') : t('createCommunity.submit')}
                    loading={isLoading}
                    onPress={handleCreate}
                    icon={!isLoading ? 'arrow-forward' : undefined}
                    iconPosition="right"
                />
            </View>
        </Screen>
    );
};
