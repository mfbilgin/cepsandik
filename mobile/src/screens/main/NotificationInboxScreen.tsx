import React, { useState } from 'react';
import { View, Text, TouchableOpacity, ScrollView, RefreshControl } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import tw from 'twrnc';

export const NotificationInboxScreen = () => {
    const navigation = useNavigation<any>();
    const [refreshing, setRefreshing] = useState(false);

    const onRefresh = React.useCallback(async () => {
        setRefreshing(true);
        // Simulate fetch
        setTimeout(() => setRefreshing(false), 1000);
    }, []);

    // Placeholder mock notifications
    const notifications = [
        { id: '1', title: 'Yeni Seçim Başladı!', message: 'Odanızda yeni bir seçim başladı. Hemen oyunuzu kullanın.', time: '2 saat önce', isRead: false },
        { id: '2', title: 'Hoş Geldiniz', message: 'CepSandık uygulamasına hoş geldiniz. İlk seçiminizi oluşturabilirsiniz.', time: '1 gün önce', isRead: true },
    ];

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            {/* Header */}
            <View style={tw`bg-white border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>Bildirimler</Text>
            </View>

            <ScrollView
                contentContainerStyle={tw`flex-grow p-4`}
                refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} colors={['#1162d4']} />}
            >
                {notifications.length === 0 ? (
                    <View style={tw`flex-1 items-center justify-center p-6 mt-10`}>
                        <View style={tw`w-24 h-24 bg-slate-100 rounded-full items-center justify-center mb-4`}>
                            <Ionicons name="notifications-off-outline" size={40} color="#94a3b8" />
                        </View>
                        <Text style={tw`text-lg font-bold text-slate-700 text-center mb-1`}>Bildirim Yok</Text>
                        <Text style={tw`text-sm text-slate-500 text-center`}>Şu an için yeni bir bildiriminiz bulunmuyor.</Text>
                    </View>
                ) : (
                    <View style={tw`flex-col gap-3`}>
                        {notifications.map((item) => (
                            <TouchableOpacity
                                key={item.id}
                                style={tw`flex-row p-4 rounded-xl bg-white border ${item.isRead ? 'border-slate-100' : 'border-[#1162d4]/30'} shadow-sm`}
                                activeOpacity={0.7}
                            >
                                <View style={tw`w-12 h-12 rounded-full ${item.isRead ? 'bg-slate-100' : 'bg-[#1162d4]/10'} items-center justify-center mr-4`}>
                                    <Ionicons name="notifications" size={24} color={item.isRead ? '#94a3b8' : '#1162d4'} />
                                </View>
                                <View style={tw`flex-1 flex-col`}>
                                    <View style={tw`flex-row justify-between items-start mb-1`}>
                                        <Text style={tw`text-base font-bold ${item.isRead ? 'text-slate-700' : 'text-slate-900'}`}>{item.title}</Text>
                                        <Text style={tw`text-xs text-slate-400`}>{item.time}</Text>
                                    </View>
                                    <Text style={tw`text-sm leading-snug ${item.isRead ? 'text-slate-500' : 'text-slate-700 font-medium'}`}>{item.message}</Text>
                                </View>
                            </TouchableOpacity>
                        ))}
                    </View>
                )}
            </ScrollView>
        </View>
    );
};
