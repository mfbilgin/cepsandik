import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, Platform, Switch } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import tw from 'twrnc';

export const NotificationsScreen = () => {
    const navigation = useNavigation<any>();

    const [pushEnabled, setPushEnabled] = useState(true);
    const [emailEnabled, setEmailEnabled] = useState(true);
    const [smsEnabled, setSmsEnabled] = useState(false);
    const [marketingEnabled, setMarketingEnabled] = useState(false);
    const [isLoading, setIsLoading] = useState(false);

    const handleSave = () => {
        setIsLoading(true);
        // Simulate API call
        setTimeout(() => {
            Toast.show({ type: 'success', text1: 'Başarılı', text2: 'Bildirim tercihleriniz kaydedildi.' });
            setIsLoading(false);
            navigation.goBack();
        }, 800);
    };

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            <View style={tw`bg-white border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>Bildirim Ayarları</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 flex-col gap-6`} showsVerticalScrollIndicator={false}>
                <View style={tw`flex-col gap-0 bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden`}>

                    {/* Push Notifications */}
                    <View style={tw`flex-row items-center justify-between p-4 border-b border-slate-100`}>
                        <View style={tw`flex-1 mr-4`}>
                            <Text style={tw`text-base font-semibold text-slate-900 mb-1`}>Anlık Bildirimler</Text>
                            <Text style={tw`text-xs text-slate-500 leading-tight`}>Uygulama arka plandayken telefonunuza gelen bildirimler.</Text>
                        </View>
                        <Switch
                            trackColor={{ false: '#cbd5e1', true: '#1162d4' }}
                            thumbColor={Platform.OS === 'ios' ? '#ffffff' : pushEnabled ? '#ffffff' : '#f8fafc'}
                            ios_backgroundColor="#cbd5e1"
                            onValueChange={setPushEnabled}
                            value={pushEnabled}
                        />
                    </View>

                    {/* Email Notifications */}
                    <View style={tw`flex-row items-center justify-between p-4 border-b border-slate-100`}>
                        <View style={tw`flex-1 mr-4`}>
                            <Text style={tw`text-base font-semibold text-slate-900 mb-1`}>E-Posta Bildirimleri</Text>
                            <Text style={tw`text-xs text-slate-500 leading-tight`}>Seçim başlangıcı, bitişi ve önemli güncellemeler.</Text>
                        </View>
                        <Switch
                            trackColor={{ false: '#cbd5e1', true: '#1162d4' }}
                            thumbColor={Platform.OS === 'ios' ? '#ffffff' : emailEnabled ? '#ffffff' : '#f8fafc'}
                            ios_backgroundColor="#cbd5e1"
                            onValueChange={setEmailEnabled}
                            value={emailEnabled}
                        />
                    </View>

                    {/* SMS Notifications */}
                    <View style={tw`flex-row items-center justify-between p-4 border-b border-slate-100`}>
                        <View style={tw`flex-1 mr-4`}>
                            <Text style={tw`text-base font-semibold text-slate-900 mb-1`}>SMS Bildirimleri</Text>
                            <Text style={tw`text-xs text-slate-500 leading-tight`}>Çok kritik durumlar ve güvenlik uyarıları için.</Text>
                        </View>
                        <Switch
                            trackColor={{ false: '#cbd5e1', true: '#1162d4' }}
                            thumbColor={Platform.OS === 'ios' ? '#ffffff' : smsEnabled ? '#ffffff' : '#f8fafc'}
                            ios_backgroundColor="#cbd5e1"
                            onValueChange={setSmsEnabled}
                            value={smsEnabled}
                        />
                    </View>

                    {/* Marketing Notifications */}
                    <View style={tw`flex-row items-center justify-between p-4`}>
                        <View style={tw`flex-1 mr-4`}>
                            <Text style={tw`text-base font-semibold text-slate-900 mb-1`}>Kampanya ve Bültenler</Text>
                            <Text style={tw`text-xs text-slate-500 leading-tight`}>Yeni özellikler, anketler ve topluluk haberleri.</Text>
                        </View>
                        <Switch
                            trackColor={{ false: '#cbd5e1', true: '#1162d4' }}
                            thumbColor={Platform.OS === 'ios' ? '#ffffff' : marketingEnabled ? '#ffffff' : '#f8fafc'}
                            ios_backgroundColor="#cbd5e1"
                            onValueChange={setMarketingEnabled}
                            value={marketingEnabled}
                        />
                    </View>

                </View>

                <TouchableOpacity
                    style={tw`w-full bg-[#1162d4] flex-row items-center justify-center gap-2 py-4 rounded-xl shadow-sm ${isLoading ? 'opacity-50' : ''}`}
                    onPress={handleSave}
                    disabled={isLoading}
                >
                    <Text style={tw`text-white font-bold text-base`}>{isLoading ? 'Kaydediliyor...' : 'Değişiklikleri Kaydet'}</Text>
                    {!isLoading && <Ionicons name="checkmark" size={20} color="white" />}
                </TouchableOpacity>

            </ScrollView>
        </View>
    );
};
