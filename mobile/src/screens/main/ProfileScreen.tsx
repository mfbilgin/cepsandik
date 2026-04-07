import React, { useLayoutEffect, useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, Alert, RefreshControl, Image, Keyboard } from 'react-native';
import { useAuth } from '../../context/AuthContext';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';
import { useI18n } from '../../i18n/LanguageContext';

export const ProfileScreen = () => {
    const [refreshing, setRefreshing] = useState(false);
    const { user, signOut, refreshUser } = useAuth();
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);

    const navigation = useNavigation<any>();
    const { t, language, setLanguage } = useI18n();

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
        Alert.alert(t('profile.logoutTitle'), t('profile.logoutBody'), [
            { text: t('common.cancel'), style: 'cancel' },
            { text: t('profile.logout'), onPress: signOut, style: 'destructive' }
        ]);
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

    const is2FA = (user as any)?.isMfaEnabled || (user as any)?.mfaEnabled;

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            {/* Header */}
            <View style={tw`items-center justify-center p-4 pt-14 pb-4 bg-white border-b border-slate-100 z-10 shadow-sm`}>
                <Text style={tw`text-xl font-bold leading-tight tracking-tight text-center text-slate-900`}>
                    {t('profile.title')}
                </Text>
            </View>

            {/* Scrollable Content */}
            <ScrollView
                style={tw`flex-1`}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#1162d4']} />}
            >
                {/* User Profile Card */}
                <View style={tw`p-6 flex-col items-center`}>
                    <View style={tw`relative mb-4`}>
                        <View style={tw`w-28 h-28 rounded-full bg-[#1162d4] border-4 border-white shadow-lg items-center justify-center overflow-hidden`}>
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
                            style={tw`absolute bottom-1 right-1 bg-[#1162d4] rounded-full p-2.5 shadow-md border-2 border-white`}
                            onPress={handleImagePick}
                            activeOpacity={0.8}
                        >
                            <Ionicons name="camera" size={18} color="white" />
                        </TouchableOpacity>
                    </View>
                    <Text style={tw`text-2xl font-bold text-slate-900 mb-1`}>{user?.firstName} {user?.lastName}</Text>
                    <Text style={tw`text-slate-500 text-sm font-medium`}>{user?.email || t('profile.defaultEmail')}</Text>
                    <View style={tw`mt-4 px-3.5 py-1.5 bg-green-50 items-center justify-center flex-row gap-1.5 rounded-full border border-green-200`}>
                        <Ionicons name="shield-checkmark" size={16} color="#16a34a" />
                        <Text style={tw`text-green-700 text-xs font-bold uppercase tracking-wide`}>{t('profile.verifiedVoter')}</Text>
                    </View>
                </View>

                {/* Settings Groups */}
                <View style={tw`px-5 pb-8 flex-col gap-6 mt-2`}>
                    {/* Account Settings Group */}
                    <View>
                        <Text style={tw`px-2 mb-2 text-xs font-bold text-slate-400 uppercase tracking-wider`}>{t('profile.section.account')}</Text>
                        <View style={tw`bg-white rounded-2xl overflow-hidden shadow-sm border border-slate-100`}>
                            {/* Edit Profile */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-white border-b border-slate-100`} onPress={() => navigation.navigate('EditProfile')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-[#1162d4]/10 items-center justify-center`}>
                                        <Ionicons name="person-outline" size={20} color="#1162d4" />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-slate-900`}>{t('profile.editProfile')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color="#cbd5e1" />
                            </TouchableOpacity>

                            {/* Security */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-white border-b border-slate-100`} onPress={() => navigation.navigate('SecuritySettings')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-orange-50 items-center justify-center`}>
                                        <Ionicons name="lock-closed-outline" size={20} color="#ea580c" />
                                    </View>
                                    <View style={tw`flex-col`}>
                                        <Text style={tw`text-base font-semibold text-slate-900`}>{t('profile.securityPassword')}</Text>

                                    </View>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color="#cbd5e1" />
                            </TouchableOpacity>

                            {/* Notifications */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-white`} onPress={() => navigation.navigate('Notifications')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-purple-50 items-center justify-center`}>
                                        <Ionicons name="notifications-outline" size={20} color="#9333ea" />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-slate-900`}>{t('profile.notificationSettings')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color="#cbd5e1" />
                            </TouchableOpacity>

                            <View style={tw`p-4 bg-white border-t border-slate-100`}>
                                <View style={tw`flex-row items-center justify-between`}>
                                    <View style={tw`flex-row items-center gap-4`}>
                                        <View style={tw`w-10 h-10 rounded-xl bg-indigo-50 items-center justify-center`}>
                                            <Ionicons name="language-outline" size={20} color="#4f46e5" />
                                        </View>
                                        <Text style={tw`text-base font-semibold text-slate-900`}>{t('profile.language')}</Text>
                                    </View>
                                    <View style={tw`flex-row rounded-xl bg-slate-100 p-1`}>
                                        <TouchableOpacity
                                            style={tw.style(`px-3 py-1.5 rounded-lg`, language === 'tr' ? 'bg-white' : '')}
                                            onPress={() => setLanguage('tr')}
                                            activeOpacity={0.8}
                                        >
                                            <Text style={tw.style(`text-xs font-bold`, language === 'tr' ? 'text-slate-900' : 'text-slate-500')}>
                                                {t('profile.languageTurkish')}
                                            </Text>
                                        </TouchableOpacity>
                                        <TouchableOpacity
                                            style={tw.style(`px-3 py-1.5 rounded-lg`, language === 'en' ? 'bg-white' : '')}
                                            onPress={() => setLanguage('en')}
                                            activeOpacity={0.8}
                                        >
                                            <Text style={tw.style(`text-xs font-bold`, language === 'en' ? 'text-slate-900' : 'text-slate-500')}>
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
                        <Text style={tw`px-2 mb-2 text-xs font-bold text-slate-400 uppercase tracking-wider`}>{t('profile.section.support')}</Text>
                        <View style={tw`bg-white rounded-2xl overflow-hidden shadow-sm border border-slate-100`}>
                            {/* About */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-white border-b border-slate-100`} onPress={() => navigation.navigate('About')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-slate-50 items-center justify-center`}>
                                        <Ionicons name="information-circle-outline" size={22} color="#475569" />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-slate-900`}>{t('profile.about')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color="#cbd5e1" />
                            </TouchableOpacity>

                            {/* Help */}
                            <TouchableOpacity style={tw`flex-row items-center justify-between p-4 bg-white`} onPress={() => navigation.navigate('Help')} activeOpacity={0.7}>
                                <View style={tw`flex-row items-center gap-4`}>
                                    <View style={tw`w-10 h-10 rounded-xl bg-slate-50 items-center justify-center`}>
                                        <Ionicons name="help-buoy-outline" size={22} color="#475569" />
                                    </View>
                                    <Text style={tw`text-base font-semibold text-slate-900`}>{t('profile.helpCenter')}</Text>
                                </View>
                                <Ionicons name="chevron-forward" size={20} color="#cbd5e1" />
                            </TouchableOpacity>
                        </View>
                    </View>

                    {/* Logout Button */}
                    <TouchableOpacity
                        style={tw`w-full flex-row items-center justify-center gap-2 p-4 rounded-2xl bg-red-50 mt-2 shadow-sm border border-red-100`}
                        onPress={handleLogout}
                        activeOpacity={0.8}
                    >
                        <Ionicons name="log-out-outline" size={24} color="#dc2626" />
                        <Text style={tw`text-red-600 font-bold text-base`}>{t('profile.logout')}</Text>
                    </TouchableOpacity>

                    <Text style={tw`text-center text-xs text-slate-400 mt-2 font-medium`}>{t('profile.version')}</Text>
                </View>
            </ScrollView>
        </View>
    );
};
