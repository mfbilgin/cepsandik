import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import { AuthService } from '../../services/auth.service';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Button } from '../../components/ui';

export const VerificationPendingScreen = () => {
    const [isLoading, setIsLoading] = useState(false);
    const navigation = useNavigation<any>();
    const route = useRoute<any>();
    const { t } = useI18n();
    const { email, password } = route.params || {};
    const c = theme.colors;

    const handleResendEmail = async () => {
        if (!email || !password) {
            Toast.show({ type: 'error', text1: 'Hata', text2: 'Kullanıcı bilgileri bulunamadı, lütfen tekrar giriş yapın.' });
            navigation.navigate('Login');
            return;
        }
        setIsLoading(true);
        try {
            await AuthService.resendVerification(email, password);
            Toast.show({ type: 'success', text1: 'Başarılı', text2: 'Doğrulama e-postası tekrar gönderildi. Lütfen gelen kutunuzu kontrol edin.' });
        } catch (error: any) {
            Toast.show({ type: 'error', text1: 'Hata', text2: error.response?.data?.message || 'E-posta gönderilemedi.' });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
            <View style={{ flex: 1, paddingHorizontal: theme.spacing.lg, justifyContent: 'center', alignItems: 'center' }}>
                <View
                    style={{
                        width: 80, height: 80, borderRadius: 40, backgroundColor: c.primaryTint,
                        alignItems: 'center', justifyContent: 'center', marginBottom: 24,
                    }}
                >
                    <Ionicons name="mail-unread-outline" size={38} color={c.primary} />
                </View>

                <Text style={{ fontSize: 22, fontWeight: '700', color: c.text, marginBottom: 8, textAlign: 'center' }}>
                    E-posta Doğrulaması Bekleniyor
                </Text>
                <Text style={{ fontSize: 15, color: c.textSecondary, textAlign: 'center', marginBottom: 32, lineHeight: 21 }}>
                    Hesabınız henüz doğrulanmamış. Lütfen{' '}
                    <Text style={{ fontWeight: '700', color: c.text }}>{email}</Text>
                    {' '}adresine gönderdiğimiz bağlantıya tıklayın.
                </Text>

                <View style={{ width: '100%', gap: 12 }}>
                    <Button
                        title="Yeniden Gönder"
                        icon="send-outline"
                        loading={isLoading}
                        onPress={handleResendEmail}
                    />
                    <Button
                        title="Giriş Ekranına Dön"
                        variant="ghost"
                        onPress={() => navigation.navigate('Login')}
                    />
                </View>

                <View
                    style={{
                        marginTop: 40, padding: 14, backgroundColor: c.surfaceAlt,
                        borderRadius: theme.borderRadius.md, borderWidth: 1, borderColor: c.border,
                        flexDirection: 'row', gap: 10, alignItems: 'flex-start',
                    }}
                >
                    <Ionicons name="information-circle-outline" size={20} color={c.textSecondary} />
                    <Text style={{ flex: 1, fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                        E-posta gelmediyse Spam/Gereksiz klasörünü kontrol etmeyi unutmayın.
                    </Text>
                </View>
            </View>
        </SafeAreaView>
    );
};
