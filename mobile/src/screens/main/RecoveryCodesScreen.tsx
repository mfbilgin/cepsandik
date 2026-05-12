import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, Platform, StatusBar } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import * as LegacyFileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';

export const RecoveryCodesScreen = () => {
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { codes = [] } = route.params || {};
    const [confirmed, setConfirmed] = useState(false);
    const { t } = useI18n();
    const { showDialog } = useUI();

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
        <SafeAreaView style={[tw`flex-1 bg-surface`, { paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 }]}>
            {/* Header */}
            <View style={tw`flex-row items-center bg-surface/80 px-4 py-3 border-b border-slate-200 justify-between`}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={tw`w-10 h-10 items-center justify-center`}>
                    <Ionicons name="arrow-back" size={24} color={tw.color('primary')} />
                </TouchableOpacity>
                <Text style={tw`text-lg font-bold text-slate-900 flex-1 text-center`}>{t('recovery.title')}</Text>
                <View style={tw`w-10`} />
            </View>

            <ScrollView contentContainerStyle={tw`pb-8`} showsVerticalScrollIndicator={false}>
                {/* Hero Icon */}
                <View style={tw`items-center pt-8 pb-4 px-6`}>
                    <View style={tw`bg-primary/10 p-4 rounded-full mb-6`}>
                        <MaterialIcons name="shield" size={48} color={tw.color('primary')} />
                    </View>
                    <Text style={tw`text-2xl font-bold text-slate-900 text-center tracking-tight mb-3`}>
                        {t('recovery.heroTitle')}
                    </Text>
                    <Text style={tw`text-sm text-slate-600 text-center font-medium leading-relaxed`}>
                        {t('recovery.heroBody')}
                    </Text>
                </View>

                {/* 2-Column Grid of Codes */}
                <View style={tw`px-6 pb-4`}>
                    <View style={tw`flex-row flex-wrap gap-3`}>
                        {codes.length > 0 ? codes.map((code: string, idx: number) => (
                            <View key={idx} style={[tw`bg-slate-50 border border-slate-200 rounded-lg p-4 flex-row items-center gap-2`, { width: '47.5%' }]}>
                                <MaterialIcons name="verified-user" size={18} color="#41431B80" />
                                <Text style={tw`font-mono font-bold text-slate-900 text-sm tracking-widest`}>{code}</Text>
                            </View>
                        )) : (
                            <View style={tw`w-full items-center p-6`}>
                                <Text style={tw`text-slate-400 font-medium`}>{t('recovery.empty')}</Text>
                            </View>
                        )}
                    </View>
                </View>

                {/* Utility Buttons */}
                <View style={tw`px-6 py-2 gap-3`}>
                    <View style={tw`flex-row gap-3`}>
                        <TouchableOpacity
                            onPress={downloadTxt}
                            style={tw`flex-1 bg-primary/10 h-12 rounded-xl flex-row items-center justify-center gap-2`}
                        >
                            <MaterialIcons name="download" size={20} color={tw.color('primary')} />
                            <Text style={tw`text-primary font-bold text-sm`}>{t('recovery.downloadTxt')}</Text>
                        </TouchableOpacity>
                        <TouchableOpacity
                            onPress={copyAll}
                            style={tw`flex-1 bg-primary/10 h-12 rounded-xl flex-row items-center justify-center gap-2`}
                        >
                            <MaterialIcons name="content-copy" size={20} color={tw.color('primary')} />
                            <Text style={tw`text-primary font-bold text-sm`}>{t('recovery.copyAll')}</Text>
                        </TouchableOpacity>
                    </View>
                </View>

                {/* Confirmation + Done */}
                <View style={tw`px-6 pt-4 border-t border-slate-100 mt-6`}>
                    <TouchableOpacity
                        onPress={() => setConfirmed(!confirmed)}
                        style={tw`flex-row items-center gap-3 mb-6`}
                    >
                        <View style={tw`w-5 h-5 rounded border-2 ${confirmed ? 'bg-primary border-primary' : 'border-slate-400 bg-surface'} items-center justify-center`}>
                            {confirmed && <MaterialIcons name="check" size={14} color="white" />}
                        </View>
                        <Text style={tw`text-sm font-semibold text-primary flex-1 leading-snug`}>
                            {t('recovery.checkbox')}
                        </Text>
                    </TouchableOpacity>

                    <TouchableOpacity
                        onPress={handleDone}
                        style={tw`w-full h-14 bg-primary rounded-xl items-center justify-center shadow-lg shadow-blue-500/30`}
                    >
                        <Text style={tw`text-white font-bold text-base tracking-wide`}>{t('recovery.done')}</Text>
                    </TouchableOpacity>

                    {/* iOS Home Bar */}
                    <View style={tw`w-32 h-1 bg-slate-300/60 rounded-full mx-auto mt-6`} />
                </View>
            </ScrollView>
        </SafeAreaView>
    );
};
