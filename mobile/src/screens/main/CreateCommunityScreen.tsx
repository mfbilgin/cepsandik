import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, Keyboard, Switch, Image, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import * as ImagePicker from 'expo-image-picker';
import Toast from 'react-native-toast-message';
import { api } from '../../services/api';
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
    const [logoUri, setLogoUri] = useState<string | null>(null);
    const [uploadingLogo, setUploadingLogo] = useState(false);

    const pickLogo = async () => {
        try {
            const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
            if (!permission.granted) {
                Toast.show({ type: 'error', text1: t('profile.permissionDeniedTitle') || 'İzin Gerekli', text2: 'Galeriye erişim izni gerekli' });
                return;
            }

            const result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ImagePicker.MediaTypeOptions.Images,
                allowsEditing: true,
                aspect: [1, 1],
                quality: 0.8,
            });

            if (!result.canceled && result.assets?.[0]) {
                const asset = result.assets[0];
                setLogoUri(asset.uri);
            }
        } catch (error) {
            Toast.show({ type: 'error', text1: t('common.error'), text2: 'Resim seçerken hata oluştu' });
        }
    };

    const handleCreate = async () => {
        if (!name.trim() || !description.trim()) {
            Toast.show({ type: 'error', text1: t('auth.login.missingTitle'), text2: t('createCommunity.missingBody') });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();

        try {
            // 1) Topluluğu oluştur (logo URL'siz — gerçek upload sonraki adımda)
            const response = await api.post('/communities', {
                name: name.trim(),
                description: description.trim(),
                visibility: isPrivate ? 'PRIVATE' : 'PUBLIC',
            });
            const newCommunityId = response.data?.data?.id;

            // 2) Logo seçildiyse multipart yükle (community-service S3 endpoint'i)
            if (newCommunityId && logoUri) {
                try {
                    setUploadingLogo(true);
                    const form = new FormData();
                    // RN FormData için { uri, name, type } üçlüsü zorunlu
                    const fileName = logoUri.split('/').pop() || 'logo.jpg';
                    const mime = fileName.toLowerCase().endsWith('.png') ? 'image/png'
                        : fileName.toLowerCase().endsWith('.webp') ? 'image/webp'
                        : fileName.toLowerCase().endsWith('.gif') ? 'image/gif'
                        : 'image/jpeg';
                    form.append('file', { uri: logoUri, name: fileName, type: mime } as any);
                    await api.post(`/communities/${newCommunityId}/logo`, form, {
                        headers: { 'Content-Type': 'multipart/form-data' },
                        transformRequest: (data) => data, // axios FormData'yı JSON'a çevirmesin
                    });
                } catch (uploadErr: any) {
                    // Topluluk yine de oluştu; logo eksik
                    Toast.show({
                        type: 'info',
                        text1: t('createCommunity.logoUploadFailedTitle') || 'Logo yüklenemedi',
                        text2: t('createCommunity.logoUploadFailedBody') || 'Topluluk oluştu ama logo yüklenemedi. Yönet ekranından tekrar deneyebilirsiniz.',
                    });
                } finally {
                    setUploadingLogo(false);
                }
            }

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
                        onPress={pickLogo}
                        disabled={uploadingLogo}
                    >
                        <View
                            style={{
                                width: 112, height: 112, borderRadius: 56, backgroundColor: logoUri ? c.primary : c.surfaceAlt,
                                alignItems: 'center', justifyContent: 'center', borderWidth: 2,
                                borderStyle: logoUri ? 'solid' : 'dashed', 
                                borderColor: logoUri ? c.primary : c.borderStrong, 
                                overflow: 'hidden',
                            }}
                        >
                            {logoUri ? (
                                <Image source={{ uri: logoUri }} style={{ width: '100%', height: '100%' }} />
                            ) : uploadingLogo ? (
                                <ActivityIndicator size="large" color={c.primary} />
                            ) : (
                                <Ionicons name="camera-outline" size={36} color={c.textTertiary} />
                            )}
                        </View>
                        <View
                            style={{
                                position: 'absolute', bottom: 0, right: 0, backgroundColor: c.primary,
                                borderRadius: 14, padding: 6, borderWidth: 2, borderColor: c.surface,
                                alignItems: 'center', justifyContent: 'center',
                            }}
                        >
                            <Ionicons name={logoUri ? 'checkmark' : 'add'} size={16} color={c.onPrimary} />
                        </View>
                    </TouchableOpacity>
                    <Text style={{ marginTop: 12, fontSize: 14, fontWeight: '600', color: c.primary }}>
                        {logoUri ? (t('createCommunity.logoSelected') || 'Logo Seçildi') : (t('createCommunity.uploadLogo') || 'Logo Yükle')}
                    </Text>
                    {logoUri && (
                        <TouchableOpacity onPress={() => setLogoUri(null)} style={{ marginTop: 8 }}>
                            <Text style={{ fontSize: 13, color: c.danger }}>Değiştir</Text>
                        </TouchableOpacity>
                    )}
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
