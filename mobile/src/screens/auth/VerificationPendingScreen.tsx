import React, { useState } from 'react';
import { View, Text, TouchableOpacity, SafeAreaView, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { AuthService } from '../../services/auth.service';
import Toast from 'react-native-toast-message';
import { tw } from '../../utils/tailwind';
import { useI18n } from '../../i18n/LanguageContext';

export const VerificationPendingScreen = () => {
    const [isLoading, setIsLoading] = useState(false);
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { t } = useI18n();
    const { email, password } = route.params || {};

    const handleResendEmail = async () => {
        if (!email || !password) {
            Toast.show({ type: 'error', text1: 'Hata', text2: 'Kullanıcı bilgileri bulunamadı, lütfen tekrar giriş yapın.' });
            navigation.navigate('Login');
            return;
        }

        setIsLoading(true);
        try {
            await AuthService.resendVerification(email, password);
            Toast.show({ 
                type: 'success', 
                text1: 'Başarılı', 
                text2: 'Doğrulama e-postası tekrar gönderildi. Lütfen gelen kutunuzu kontrol edin.' 
            });
        } catch (error: any) {
            Toast.show({ 
                type: 'error', 
                text1: 'Hata', 
                text2: error.response?.data?.message || 'E-posta gönderilemedi.' 
            });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <SafeAreaView style={tw`flex-1 bg-background`}>
            <View style={tw`flex-1 px-6 justify-center items-center`}>
                <View style={tw`w-20 h-20 rounded-full bg-primary/10 items-center justify-center mb-6`}>
                    <Ionicons name="mail-unread-outline" size={40} color={tw.color('primary')} />
                </View>

                <Text style={tw`text-2xl font-bold text-slate-900 mb-2 text-center`}>
                    E-posta Doğrulaması Bekleniyor
                </Text>
                
                <Text style={tw`text-slate-500 text-base text-center mb-8 px-4`}>
                    Hesabınız henüz doğrulanmamış. Lütfen <Text style={tw`font-bold text-slate-700`}>{email}</Text> adresine gönderdiğimiz bağlantıya tıklayın.
                </Text>

                <View style={tw`w-full gap-4`}>
                    <TouchableOpacity
                        style={tw`w-full bg-primary h-12 rounded-lg items-center justify-center flex-row gap-2 ${isLoading ? 'opacity-70' : ''}`}
                        onPress={handleResendEmail}
                        disabled={isLoading}
                    >
                        {isLoading ? (
                            <ActivityIndicator color="white" />
                        ) : (
                            <>
                                <Ionicons name="send-outline" size={18} color="white" />
                                <Text style={tw`text-white font-semibold text-base`}>Yeniden Gönder</Text>
                            </>
                        )}
                    </TouchableOpacity>

                    <TouchableOpacity
                        style={tw`w-full h-12 rounded-lg items-center justify-center`}
                        onPress={() => navigation.navigate('Login')}
                    >
                        <Text style={tw`text-primary font-medium`}>Giriş Ekranına Dön</Text>
                    </TouchableOpacity>
                </View>

                <View style={tw`mt-12 p-4 bg-slate-50 rounded-xl border border-slate-100`}>
                    <View style={tw`flex-row gap-3 items-start`}>
                        <Ionicons name="information-circle-outline" size={20} color="#64748b" />
                        <View style={tw`flex-1`}>
                            <Text style={tw`text-sm text-slate-600 leading-5`}>
                                E-posta gelmediyse Spam/Gereksiz klasörünü kontrol etmeyi unutmayın.
                            </Text>
                        </View>
                    </View>
                </View>
            </View>
        </SafeAreaView>
    );
};
