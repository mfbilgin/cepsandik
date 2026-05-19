import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import * as LegacyFileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { theme } from '../../utils/theme';
import { AppHeader, Button } from '../../components/ui';

export const RecoveryCodesScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { codes = [] } = route.params || {};
    const [confirmed, setConfirmed] = useState(false);
    const { t } = useI18n();
    const { showDialog } = useUI();
    const c = theme.colors;

    const copyAll = async () => {
        await Clipboard.setStringAsync(codes.join('\n'));
        Toast.show({ type: 'success', text1: t('recovery.copySuccessTitle'), text2: t('recovery.copySuccessBody') });
    };

    const downloadTxt = async () => {
        try {
            const content = `${t('recovery.fileHeader')}\n==============================\n\n${codes.join('\n')}\n\n${t('recovery.fileFooter')}`;

            if (Platform.OS === 'android') {
                const permissions = await LegacyFileSystem.StorageAccessFramework.requestDirectoryPermissionsAsync();
                if (permissions.granted) {
                    const fileUri = await LegacyFileSystem.StorageAccessFramework.createFileAsync(
                        permissions.directoryUri,
                        'cepsandik_recovery_codes.txt',
                        'text/plain'
                    );
                    await LegacyFileSystem.writeAsStringAsync(fileUri, content, { encoding: 'utf8' });
                    Toast.show({ type: 'success', text1: t('recovery.saveSuccessTitle'), text2: t('recovery.saveSuccessBody') });
                }
            } else {
                const fileUri = `${LegacyFileSystem.documentDirectory}cepsandik_recovery_codes.txt`;
                await LegacyFileSystem.writeAsStringAsync(fileUri, content, { encoding: 'utf8' });

                if (await Sharing.isAvailableAsync()) {
                    await Sharing.shareAsync(fileUri, {
                        mimeType: 'text/plain',
                        dialogTitle: t('recovery.shareDialogTitle'),
                        UTI: 'public.plain-text'
                    });
                }
            }
        } catch (e) {
            console.error('Download error:', e);
            Toast.show({ type: 'error', text1: t('recovery.saveErrorTitle'), text2: t('recovery.saveErrorBody') });
        }
    };

    const handleDone = () => {
        if (!confirmed) {
            showDialog({
                title: t('recovery.confirmTitle'),
                message: t('recovery.confirmBody'),
                type: 'confirm',
                confirmText: t('recovery.confirmAction'),
                onConfirm: () => navigation.reset({
                    index: 1,
                    routes: [{ name: 'MainTab' }, { name: 'SecuritySettings' }],
                })
            });
        } else {
            navigation.reset({
                index: 1,
                routes: [{ name: 'MainTab' }, { name: 'SecuritySettings' }],
            });
        }
    };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }} edges={['top', 'bottom']}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('recovery.title')} onBack={() => navigation.goBack()} />
            </View>

            <ScrollView contentContainerStyle={{ paddingBottom: 32 }} showsVerticalScrollIndicator={false}>
                <View style={{ alignItems: 'center', paddingTop: theme.spacing.lg, paddingBottom: theme.spacing.md, paddingHorizontal: theme.spacing.lg }}>
                    <View
                        style={{
                            width: 80, height: 80, borderRadius: 40, backgroundColor: c.primaryTint,
                            alignItems: 'center', justifyContent: 'center', marginBottom: theme.spacing.lg,
                        }}
                    >
                        <MaterialIcons name="shield" size={44} color={c.primary} />
                    </View>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text, textAlign: 'center', marginBottom: 10 }}>
                        {t('recovery.heroTitle')}
                    </Text>
                    <Text style={{ fontSize: 14, color: c.textSecondary, textAlign: 'center', lineHeight: 20 }}>
                        {t('recovery.heroBody')}
                    </Text>
                </View>

                <View style={{ paddingHorizontal: theme.spacing.lg, paddingBottom: theme.spacing.md }}>
                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
                        {codes.length > 0 ? codes.map((code: string, idx: number) => (
                            <View
                                key={idx}
                                style={{
                                    backgroundColor: c.surfaceAlt, borderWidth: 1, borderColor: c.border,
                                    borderRadius: theme.borderRadius.md, padding: 14, flexDirection: 'row',
                                    alignItems: 'center', gap: 8, width: '47.5%',
                                }}
                            >
                                <MaterialIcons name="verified-user" size={18} color={c.textTertiary} />
                                <Text style={{ fontFamily: 'monospace', fontWeight: '700', color: c.text, fontSize: 13, letterSpacing: 2 }}>{code}</Text>
                            </View>
                        )) : (
                            <View style={{ width: '100%', alignItems: 'center', padding: 24 }}>
                                <Text style={{ color: c.textTertiary, fontWeight: '500' }}>{t('recovery.empty')}</Text>
                            </View>
                        )}
                    </View>
                </View>

                <View style={{ paddingHorizontal: theme.spacing.lg, paddingVertical: theme.spacing.sm }}>
                    <View style={{ flexDirection: 'row', gap: 12 }}>
                        <TouchableOpacity
                            onPress={downloadTxt}
                            style={{ flex: 1, backgroundColor: c.primaryTint, height: 48, borderRadius: theme.borderRadius.lg, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                        >
                            <MaterialIcons name="download" size={20} color={c.primary} />
                            <Text style={{ color: c.primary, fontWeight: '700', fontSize: 14 }}>{t('recovery.downloadTxt')}</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            onPress={copyAll}
                            style={{ flex: 1, backgroundColor: c.primaryTint, height: 48, borderRadius: theme.borderRadius.lg, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 }}
                        >
                            <MaterialIcons name="content-copy" size={20} color={c.primary} />
                            <Text style={{ color: c.primary, fontWeight: '700', fontSize: 14 }}>{t('recovery.copyAll')}</Text>
                        </TouchableOpacity>
                    </View>
                </View>

                <View style={{ paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.md, borderTopWidth: 1, borderTopColor: c.border, marginTop: theme.spacing.lg }}>
                    <TouchableOpacity
                        onPress={() => setConfirmed(!confirmed)}
                        style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: theme.spacing.lg, marginTop: theme.spacing.md }}
                    >
                        <View
                            style={{
                                width: 22, height: 22, borderRadius: 6, borderWidth: 2,
                                backgroundColor: confirmed ? c.primary : c.surface,
                                borderColor: confirmed ? c.primary : c.borderStrong,
                                alignItems: 'center', justifyContent: 'center',
                            }}
                        >
                            {confirmed && <MaterialIcons name="check" size={14} color={c.onPrimary} />}
                        </View>
                        <Text style={{ fontSize: 14, fontWeight: '500', color: c.text, flex: 1, lineHeight: 19 }}>
                            {t('recovery.checkbox')}
                        </Text>
                    </TouchableOpacity>

                    <Button title={t('recovery.done')} size="lg" onPress={handleDone} />
                </View>
            </ScrollView>
        </SafeAreaView>
    );
};
