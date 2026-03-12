import React from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import tw from 'twrnc';

export const AboutScreen = () => {
    const navigation = useNavigation<any>();

    return (
        <View style={tw`flex-1 bg-[#f6f7f8]`}>
            <View style={tw`bg-white border-b border-slate-200 pt-14 pb-3 px-5 shadow-sm z-30 flex-row items-center`}>
                <TouchableOpacity style={tw`w-10 h-10 items-center justify-center rounded-full bg-slate-50`} onPress={() => navigation.goBack()}>
                    <Ionicons name="chevron-back" size={24} color="#64748b" />
                </TouchableOpacity>
                <Text style={tw`text-xl font-bold tracking-tight text-slate-900 ml-4`}>Araç Hakkında</Text>
            </View>

            <ScrollView contentContainerStyle={tw`flex-grow p-6 flex-col gap-6`}>
                <View style={tw`items-center justify-center py-6`}>
                    <View style={tw`w-24 h-24 bg-[#1162d4] rounded-3xl items-center justify-center shadow-lg mb-4`}>
                        <Ionicons name="cube-outline" size={48} color="white" />
                    </View>
                    <Text style={tw`text-2xl font-bold text-slate-900 mb-1`}>CepSandık</Text>
                    <Text style={tw`text-base text-slate-500`}>Versiyon 1.0.4</Text>
                </View>

                <View style={tw`bg-white rounded-2xl shadow-sm border border-slate-100 p-6`}>
                    <Text style={tw`text-sm text-slate-600 leading-relaxed mb-4`}>
                        CepSandık, kurumsal ve topluluk bazlı oylamaları güvenli, şeffaf ve hızlı bir şekilde gerçekleştirmek amacıyla oluşturulmuş dijital seçim platformudur.
                    </Text>
                    <Text style={tw`text-sm text-slate-600 leading-relaxed`}>
                        Gelişmiş şifreleme ve veritabanı yapısıyla oylarınız tamamen anonimleştirilir ve yetkisiz erişimlere karşı korunur. Kullanıcı geri bildirimlerine dayalı olarak sürekli geliştirilmektedir.
                    </Text>
                </View>

                <View style={tw`items-center mt-8`}>
                    <Text style={tw`text-xs text-slate-400`}>© 2026 CepSandık. Tüm Hakları Saklıdır.</Text>
                </View>
            </ScrollView>
        </View>
    );
};
