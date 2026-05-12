import React, { useLayoutEffect, useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, RefreshControl, Image, Keyboard } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';
import { useUI } from '../../context/UIContext';
import { registerForPushNotificationsAsync } from '../../utils/notificationHandler';
import { api } from '../../services/api';
import { Switch } from 'react-native';

export const ProfileScreen = () => {
    const [refreshing, setRefreshing] = useState(false);
    const { user, signOut, refreshUser } = useAuth();
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);

    const navigation = useNavigation<any>();
    const { t, language, setLanguage } = useI18n();
    const { showDialog } = useUI();

    useLayoutEffect(() => {
        navigation.setOptions({ headerShown: false });
    }, [navigation]);

    useEffect(() => {
        const showSubscription = Keyboard.addListener('keyboardDidShow', () => {
            setKeyboardVisible(true);
        });
        const hideSubscription = Keyboard.addListener('keyboardDidHide', () => {
            setKeyboardVisible(false);
        });

        return () => {
            showSubscription.remove();
            hideSubscription.remove();
        };
    }, []);

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
            onConfirm: signOut
        });
    };

    const handleNotImplemented = () => {
        Toast.show({
            type: 'info',
            text1: t('profile.comingSoonTitle'),
            text2: t('profile.comingSoonBody'),
            visibilityTime: 2000,
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

                // Formulate the file to upload
                const localUri = asset.uri;
                const filename = localUri.split('/').pop();
                const match = /\.(\w+)$/.exec(filename || '');
                const type = match ? `image/${match[1]}` : `image`;

                formData.append('file', {
                    uri: localUri,
                    name: filename,
                    type,
                } as any);

                Toast.show({ type: 'info', text1: t('profile.uploadingTitle'), text2: t('profile.uploadingBody') });

                const { api } = require('../../services/api');
                await api.post('/users/me/profile-image', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' },
                });

                await refreshUser();
                Toast.show({ type: 'success', text1: t('profile.uploadSuccessTitle'), text2: t('profile.uploadSuccessBody') });
            }
        } catch (error) {
            console.log(error);
            Toast.show({ type: 'error', text1: t('profile.uploadErrorTitle'), text2: t('profile.uploadErrorBody') });
        }
    };

    const toggleGuardianRole = async () => {
        if (!user) return;
        
        const currentEligible = (user as any).isGuardianEligible;
        
        if (!currentEligible) {
            // Aday olmak istiyor -> İzin al
            const token = await registerForPushNotificationsAsync();
            if (token) {
                try {
                    await api.put('/users/me/push-token', {
                        pushToken: token,
                        guardianEligible: true
                    });
                    await refreshUser();
                    Toast.show({ type: 'success', text1: t('profile.guardianRole'), text2: t('profile.guardianEligibleSuccess') });
                } catch (error) {
                    Toast.show({ type: 'error', text1: t('security.loadError'), text2: t('auth.forgot.failedBody') });
                }
            } else {
                Toast.show({ type: 'error', text1: t('profile.notificationPermissionRequired'), text2: t('profile.notificationSettings') });
            }
        } else {
            // Adaylıktan çıkmak istiyor
            try {
                await api.put('/users/me/push-token', {
                    pushToken: (user as any).pushToken,
                    guardianEligible: false
                });
                await refreshUser();
                Toast.show({ type: 'info', text1: t('profile.guardianRole'), text2: t('profile.guardianEligibleDisabled') });
            } catch (error) {
                Toast.show({ type: 'error', text1: t('security.loadError'), text2: t('auth.forgot.failedBody') });
            }
        }
    };

    const is2FA = (user as any)?.isMfaEnabled || (user as any)?.mfaEnabled;

    return (
        <View style={tw`flex-1 bg-background`}>
            {/* Header */}
            <View style={tw`items-center justify-center p-4 pt-14 pb-4 bg-surface border-b border-background z-10 shadow-sm`}>
                <Text style={tw`text-xl font-bold leading-tight tracking-tight text-center text-primary`}>
                    {t('profile.title')}
                </Text>
            </View>

            {/* Scrollable Content */}
            <ScrollView
                style={tw`flex-1`}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={[tw.color('primary') as string]} />}
            >
                {/* User Profile Card */}
                <View style={tw`p-6 flex-col items-center`}>
                    <View style={tw`relative mb-4`}>
                        <View style={tw`w-28 h-28 rounded-full bg-primary border-4 border-white shadow-lg items-center justify-center overflow-hidden`}>
                            {user?.profileImage ? (
                                <React.Fragment>
                                    {/* <Text style={tw`text-xs text-white`}>Resim Yüklendi</Text> */}
                                    {<Image source={{ uri: user.profileImage }} style={tw`w-full h-full`} />}
                                </React.Fragment>
                            ) : (
                                <Text style={tw`text-5xl font-bold text-white`}>{user?.firstName?.[0]}{user?.lastName?.[0]}</Text>
                            )}
                        </View>
                        <TouchableOpacity
                            style={tw`absolute bottom-0 right-0 bg-primary rounded-full p-2.5 border-2 border-white`}
                            onPress={handleImagePick}
                            activeOpacity={0.8}
                        >
                            <Ionicons name="camera" size={18} color="white" />
                        </TouchableOpacity>
                    </View>
                    <Text style={tw`text-2xl font-bold text-primary mb-1`}>{user?.firstName} {user?.lastName}</Text>
                    <Text style={tw`text-textSecondary text-sm font-medium`}>{user?.email || t('profile.defaultEmail')}</Text>
                    <View style={tw`mt-4 px-3.5 py-1.5 bg-success/10 items-center justify-center flex-row gap-1.5 rounded-full border border-success/20`}>
                        <Ionicons name="shield-checkmark" size={16} color={tw.color('success') as string} />
                        <Text style={tw`text-success text-xs font-bold uppercase tracking-wide`}>{t('profile.verifiedVoter')}</Text>
                    </View>
                </View>

                {/* Settings Groups */}
                <View style={tw`px-5 pb-8 flex-col gap-6 mt-2`}>
                    {/* Account Settings Group */}
                    <View>
                        <Text style={tw`px-2 mb-2 text-xs font-bold text-textSecondary uppercase tracking-wider`}>{t('profile.section.account')}</Text>
                        <View style={tw`bg-surface rounded-2xl overflow-hidden shadow-sm border border-slate-100`}>
                            {/* Edit Profile */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-surface border-b border-slate-100`} onPress={() => navigation.navigate('EditProfile')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-primary/10 items-center justify-center`}>
                                        <Ionicons name="person-outline" size={20} color={tw.color('primary') as string} />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-primary`}>{t('profile.editProfile')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color={tw.color('secondary') as string} />
                            </TouchableOpacity>

                            {/* Security */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-surface border-b border-slate-100`} onPress={() => navigation.navigate('SecuritySettings')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-secondary/20 items-center justify-center`}>
                                        <Ionicons name="lock-closed-outline" size={20} color={tw.color('primary') as string} />
                                    </View>
                                    <View style={tw`flex-col`}>
                                        <Text style={tw`text-base font-semibold text-primary`}>{t('profile.securityPassword')}</Text>

                                    </View>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color={tw.color('secondary') as string} />
                            </TouchableOpacity>

                            {/* Notifications */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-surface border-b border-slate-100`} onPress={() => navigation.navigate('NotificationSettings')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-primary/20 items-center justify-center`}>
                                        <Ionicons name="notifications-outline" size={20} color={tw.color('primary') as string} />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-primary`}>{t('profile.notificationSettings')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color={tw.color('secondary') as string} />
                            </TouchableOpacity>

                            {/* Guardian Role Toggle */}
                            <View style={tw`flex-row items-center justify-between p-4 bg-surface`}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-success/10 items-center justify-center`}>
                                        <Ionicons name="shield-outline" size={20} color={tw.color('success') as string} />
                                    </View>
                                    <View style={tw`flex-1 mr-4`}>
                                        <Text style={tw`text-base font-semibold text-primary`}>{t('profile.guardianRole')}</Text>
                                        <Text style={tw`text-xs text-textSecondary`}>{t('profile.guardianRoleDesc')}</Text>
                                    </View>
                                </View>
                                <Switch
                                    value={(user as any)?.isGuardianEligible || false}
                                    onValueChange={toggleGuardianRole}
                                    trackColor={{ false: '#cbd5e1', true: tw.color('success') as string }}
                                    thumbColor="white"
                                />
                            </View>

                            <View style={tw`p-4 bg-surface border-t border-slate-100`}>
                                <View style={tw`flex-row items-center justify-between`}>
                                    <View style={tw`flex-row items-center gap-4`}>
                                        <View style={tw`w-10 h-10 rounded-xl bg-secondary/20 items-center justify-center`}>
                                            <Ionicons name="language-outline" size={20} color={tw.color('primary') as string} />
                                        </View>
                                        <Text style={tw`text-base font-semibold text-primary`}>{t('profile.language')}</Text>
                                    </View>
                                    <View style={tw`flex-row rounded-xl bg-background p-1 border border-primary/10`}>
                                        <TouchableOpacity
                                            style={tw.style(`px-3 py-1.5 rounded-lg`, language === 'tr' ? 'bg-primary' : '')}
                                            onPress={() => setLanguage('tr')}
                                            activeOpacity={0.8}
                                        >
                                            <Text style={tw.style(`text-xs font-bold`, language === 'tr' ? 'text-surface' : 'text-textSecondary')}>
                                                {t('profile.languageTurkish')}
                                            </Text>
                                        </TouchableOpacity>
                                        <TouchableOpacity
                                            style={tw.style(`px-3 py-1.5 rounded-lg`, language === 'en' ? 'bg-primary' : '')}
                                            onPress={() => setLanguage('en')}
                                            activeOpacity={0.8}
                                        >
                                            <Text style={tw.style(`text-xs font-bold`, language === 'en' ? 'text-surface' : 'text-textSecondary')}>
                                                {t('profile.languageEnglish')}
                                            </Text>
                                        </TouchableOpacity>
                                    </View>
                                </View>
                            </View>
                        </View>
                    </View>

                    {/* Support Group */}
                    <View>
                        <Text style={tw`px-2 mb-2 text-xs font-bold text-textSecondary uppercase tracking-wider`}>{t('profile.section.support')}</Text>
                        <View style={tw`bg-surface rounded-2xl overflow-hidden shadow-sm border border-slate-100`}>
                            {/* About */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-surface border-b border-slate-100`} onPress={() => navigation.navigate('About')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-primary/10 items-center justify-center`}>
                                        <Ionicons name="information-circle-outline" size={22} color={tw.color('primary') as string} />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-primary`}>{t('profile.about')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color={tw.color('secondary') as string} />
                            </TouchableOpacity>

                            {/* Help */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-surface`} onPress={() => navigation.navigate('Help')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-secondary/10 items-center justify-center`}>
                                        <Ionicons name="help-buoy-outline" size={22} color={tw.color('primary') as string} />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-primary`}>{t('profile.helpCenter')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color={tw.color('secondary') as string} />
                            </TouchableOpacity>
                        </View>
                    </View>
                    {/* Security Badge */}
                    <View style={tw`px-5`}>
                        <View style={tw`flex-column items-center gap-2 bg-primary/10 py-1.5 rounded-full  border border-primary/20`}>
                            <Text style={tw`text-xs font-semibold tracking-wide uppercase text-primary`}>
                                {t('home.securedBy')}
                            </Text>
                        </View>
                    </View>
                    {/* Logout Button */}
                    <TouchableOpacity
                        style={tw`w-full flex-row items-center justify-center gap-2 p-4 rounded-2xl bg-danger/10 mt-2 border border-danger/20`}
                        onPress={handleLogout}
                        activeOpacity={0.8}
                    >
                        <Ionicons name="log-out-outline" size={24} color={tw.color('danger') as string} />
                        <Text style={tw`text-danger font-bold text-base`}>{t('profile.logout')}</Text>
                    </TouchableOpacity>


                    <Text style={tw`text-center text-xs text-textSecondary mt-2 font-medium`}>{t('profile.version')}</Text>
                </View>
            </ScrollView>
        </View>
    );
};
