import React, { useState, useEffect } from 'react';
import { View, Text, TextInput, TouchableOpacity, KeyboardAvoidingView, ScrollView, Platform, Keyboard } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';

export const EditProfileScreen = () => {
    const { user, updateUser } = useAuth();
    const navigation = useNavigation<any>();

    const [firstName, setFirstName] = useState(user?.firstName || '');
    const [lastName, setLastName] = useState(user?.lastName || '');
    const [isLoading, setIsLoading] = useState(false);
    const [isKeyboardVisible, setKeyboardVisible] = useState(false);

    useEffect(() => {
        const keyboardDidShowListener = Keyboard.addListener(
            Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
            () => setKeyboardVisible(true)
        );
        const keyboardDidHideListener = Keyboard.addListener(
            Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
            () => setKeyboardVisible(false)
        );

        return () => {
            keyboardDidShowListener.remove();
            keyboardDidHideListener.remove();
        };
    }, []);

    const handleUpdate = async () => {
        if (!firstName.trim() || !lastName.trim()) {
            Toast.show({ type: 'error', text1: 'Eksik Bilgi', text2: 'Lütfen ad ve soyadınızı girin.' });
            return;
        }

        setIsLoading(true);
        Keyboard.dismiss();

        try {
            const res = await api.put('/users/me', {
                firstName: firstName.trim(),
                lastName: lastName.trim()
            });

            const updatedUser = res.data?.data || res.data;
            if (user && updatedUser) {
                updateUser({ ...user, firstName: updatedUser.firstName, lastName: updatedUser.lastName });
            }

            Toast.show({ type: 'success', text1: 'Başarılı', text2: 'Profiliniz başarıyla güncellendi.' });
            setTimeout(() => {
                navigation.goBack();
            }, 1000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: 'Hata', text2: error.response?.data?.message || 'Profil güncellenemedi.' });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <KeyboardAvoidingView
            style={tw`flex-1 bg-[#f6f7f8]`}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
        >
            <View style={tw`bg-white border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>Profili Düzenle</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 ${isKeyboardVisible ? 'pb-120' : 'pb-40'} flex-col gap-6`} keyboardShouldPersistTaps="handled">
                <View style={tw`items-center justify-center py-4`}>
                    <View style={tw`w-24 h-24 rounded-full bg-[#1162d4] items-center justify-center shadow-md mb-4`}>
                        <Text style={tw`text-5xl font-bold text-white`}>{firstName?.[0] || user?.firstName?.[0]}</Text>
                    </View>
                </View>

                <View style={tw`flex-col gap-5 bg-white p-6 rounded-2xl shadow-sm border border-slate-100`}>
                    <View style={tw`flex-col gap-2`}>
                        <Text style={tw`text-sm font-semibold text-slate-700 ml-1`}>Ad</Text>
                        <TextInput
                            style={tw`w-full rounded-xl border border-slate-200 bg-slate-50 text-slate-900 px-4 py-3.5 text-base`}
                            placeholder="Adınız"
                            placeholderTextColor="#94a3b8"
                            value={firstName}
                            onChangeText={setFirstName}
                        />
                    </View>

                    <View style={tw`flex-col gap-2`}>
                        <Text style={tw`text-sm font-semibold text-slate-700 ml-1`}>Soyad</Text>
                        <TextInput
                            style={tw`w-full rounded-xl border border-slate-200 bg-slate-50 text-slate-900 px-4 py-3.5 text-base`}
                            placeholder="Soyadınız"
                            placeholderTextColor="#94a3b8"
                            value={lastName}
                            onChangeText={setLastName}
                        />
                    </View>

                    <View style={tw`flex-col gap-2 opacity-50`}>
                        <Text style={tw`text-sm font-semibold text-slate-700 ml-1`}>E-Posta Adresi</Text>
                        <TextInput
                            style={tw`w-full rounded-xl border border-slate-200 bg-slate-100 text-slate-500 px-4 py-3.5 text-base`}
                            value={user?.email}
                            editable={false}
                        />
                        <Text style={tw`text-xs text-slate-400 ml-1`}>Devam eden seçimlerinizin güvenliği için E-posta adresi değiştirilemez.</Text>
                    </View>

                    <TouchableOpacity
                        style={tw`mt-4 w-full bg-[#1162d4] flex-row items-center justify-center gap-2 py-4 rounded-xl shadow-sm ${isLoading ? 'opacity-50' : ''}`}
                        onPress={handleUpdate}
                        disabled={isLoading}
                    >
                        <Text style={tw`text-white font-bold text-base`}>{isLoading ? 'Kaydediliyor...' : 'Değişiklikleri Kaydet'}</Text>
                        {!isLoading && <Ionicons name="checkmark" size={20} color="white" />}
                    </TouchableOpacity>
                </View>
            </ScrollView>
        </KeyboardAvoidingView>
    );
};
