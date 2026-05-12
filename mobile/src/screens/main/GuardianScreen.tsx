import React, { useState, useEffect } from 'react';
import { View, Text, FlatList, TouchableOpacity, Alert, ActivityIndicator } from 'react-native';
import { tw } from '../../utils/tailwind';
import { theme } from '../../utils/theme';
import { Ionicons } from '@expo/vector-icons';
import { guardianCrypto } from '../../utils/guardianCrypto';
import axios from 'axios';

type GuardianDuty = {
    electionId: number;
    electionTitle: string;
    communityName?: string;
    status: 'PENDING' | 'KEY_UPLOADED' | 'SHARE_UPLOADED';
    electionStatus: 'SCHEDULED' | 'ACTIVE' | 'CLOSED' | 'CLOSED_WAITING_DECRYPTION' | 'ARCHIVED';
    encryptedTallyChallenge?: string | null;
};

export const GuardianScreen = () => {
    const [duties, setDuties] = useState<GuardianDuty[]>([]);
    const [loading, setLoading] = useState(true);

    const fetchDuties = async () => {
        try {
            setLoading(true);
            const response = await axios.get('/api/v1/guardians/my-duties');
            setDuties(response.data);
        } catch (error) {
            console.error("Görevler yüklenemedi:", error);
            Alert.alert("Hata", "Emanetçi görevleriniz yüklenemedi.");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchDuties();
    }, []);

    const handleKeySubmission = async (duty: GuardianDuty) => {
        try {
            Alert.alert(
                "Anahtar Üretimi",
                "Bu seçim için kriptografik anahtarlarınız üretilip sisteme yüklenecektir. Devam edilsin mi?",
                [
                    { text: "Vazgeç", style: "cancel" },
                    { text: "Üret ve Yükle", onPress: async () => {
                        try {
                            const { publicKey, commitments, coefficientProofs } = await guardianCrypto.generateAndSaveKeyPair(duty.electionId);
                            
                            await axios.post(`/api/v1/guardians/${duty.electionId}/keys`, { 
                                publicKey, 
                                commitments,
                                coefficientProofs 
                            });
                            
                            Alert.alert("Başarılı", "Anahtarlarınız ve Schnorr kanıtlarınız başarıyla yüklendi.");
                            fetchDuties();
                        } catch (err: any) {
                            Alert.alert("Hata", "Anahtarlar yüklenemedi: " + (err.response?.data?.message || err.message));
                        }
                    }}
                ]
            );
        } catch (error) {
            Alert.alert("Hata", "Anahtar üretimi sırasında bir sorun oluştu.");
        }
    };

    const handleDecryption = async (duty: GuardianDuty) => {
        try {
            Alert.alert(
                "Hesaplamayı Başlat",
                "Seçim sonuçlarını doğrulamak için deşifre payınız gönderilecektir.",
                [
                    { text: "Vazgeç", style: "cancel" },
                    { text: "Onayla", onPress: async () => {
                        try {
                            // Backendden election detaylarını alıp encrypted_tally'yi çekmek gerekir
                            // Şimdilik sadece API çağrısını hazırla
                            if (!duty.encryptedTallyChallenge) {
                                throw new Error("Desifre edilecek encrypted tally challenge bulunamadi.");
                            }
                            const share = await guardianCrypto.generateDecryptionShare(duty.electionId, duty.encryptedTallyChallenge);
                            
                            await axios.post(`/api/v1/guardians/${duty.electionId}/decrypt`, { shareData: share });

                            Alert.alert("Başarılı", "Hesaplama onayı gönderildi.");
                            fetchDuties();
                        } catch (err: any) {
                            Alert.alert("Hata", "Deşifre payı gönderilemedi.");
                        }
                    }}
                ]
            );
        } catch (error) {
            Alert.alert("Hata", "Hesaplama onayı gönderilemedi.");
        }
    };

    const renderDuty = ({ item }: { item: GuardianDuty }) => (
        <View style={tw`bg-surface p-4 rounded-xl mb-4 border border-slate-100 shadow-sm`}>
            <View style={tw`flex-row justify-between items-start`}>
                <View style={tw`flex-1`}>
                    <Text style={tw`text-primary font-bold text-lg`}>{item.electionTitle}</Text>
                    {item.communityName && <Text style={tw`text-secondary text-sm`}>{item.communityName}</Text>}
                </View>
                <View style={tw`bg-primary/10 px-2 py-1 rounded`}>
                    <Text style={tw`text-primary text-[10px] font-bold`}>EMANETÇİ #1 (SİZ)</Text>
                </View>
            </View>

            <View style={tw`mt-4 flex-row items-center justify-between`}>
                <View>
                    <Text style={tw`text-xs text-secondary mb-1`}>Durum:</Text>
                    <Text style={tw`text-sm font-semibold ${item.status === 'PENDING' ? 'text-orange-500' : 'text-green-500'}`}>
                        {item.status === 'PENDING' ? 'ANAHTAR BEKLENİYOR' : 'HAZIR'}
                    </Text>
                </View>
                
                {item.electionStatus === 'SCHEDULED' && item.status === 'PENDING' && (
                    <TouchableOpacity 
                        onPress={() => handleKeySubmission(item)}
                        style={tw`bg-primary px-4 py-2 rounded-lg`}
                    >
                        <Text style={tw`text-white font-bold`}>Anahtar Yükle</Text>
                    </TouchableOpacity>
                )}

                {item.electionStatus === 'CLOSED' && item.status !== 'SHARE_UPLOADED' && (
                    <TouchableOpacity 
                        onPress={() => handleDecryption(item)}
                        style={tw`bg-accent-blue px-4 py-2 rounded-lg`}
                    >
                        <Text style={tw`text-white font-bold`}>Hesaplat</Text>
                    </TouchableOpacity>
                )}
            </View>
        </View>
    );

    return (
        <View style={tw`flex-1 bg-background p-4 pt-12`}>
            <View style={tw`flex-row items-center mb-6`}>
                <View style={tw`bg-primary/20 p-3 rounded-2xl mr-4`}>
                    <Ionicons name="shield-checkmark" size={32} color={theme.colors.primary} />
                </View>
                <View>
                    <Text style={tw`text-primary text-2xl font-bold`}>Emanetçi Görevleri</Text>
                    <Text style={tw`text-secondary text-sm`}>Güvenli seçimler için kriptografik onaylarınız</Text>
                </View>
            </View>

            {loading ? (
                <ActivityIndicator color={theme.colors.primary} style={tw`mt-20`} />
            ) : duties.length > 0 ? (
                <FlatList 
                    data={duties}
                    keyExtractor={(item) => item.electionId.toString()}
                    renderItem={renderDuty}
                    contentContainerStyle={tw`pb-20`}
                />
            ) : (
                <View style={tw`flex-1 justify-center items-center opacity-40`}>
                    <Ionicons name="documents-outline" size={80} color={theme.colors.secondary} />
                    <Text style={tw`text-secondary font-medium mt-4`}>Henüz bir görev atanmadı.</Text>
                </View>
            )}
        </View>
    );
};
