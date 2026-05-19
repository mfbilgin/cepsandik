import React, { useState, useRef } from 'react';
import { View, Text, TextInput, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { AuthService } from '../../services/auth.service';
import { useNavigation } from '@react-navigation/native';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { Keyboard } from 'react-native';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Input, Button } from '../../components/ui';

export const RegisterScreen = () => {
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [kvkkAccepted, setKvkkAccepted] = useState(false);
    const [isLoading, setIsLoading] = useState(false);

    const lastNameRef = useRef<TextInput>(null);
    const emailRef = useRef<TextInput>(null);
    const passwordRef = useRef<TextInput>(null);
    const confirmPasswordRef = useRef<TextInput>(null);

    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    const getPasswordStrength = () => {
        let score = 0;
        if (password.length > 5) score += 1;
        if (password.length > 8) score += 1;
        if (/[A-Z]/.test(password)) score += 1;
        if (/[0-9]/.test(password)) score += 1;
        return score;
    };
    const strengthScore = getPasswordStrength();
    const strengthColors = ['transparent', c.danger, c.warning, '#65A30D', c.success];
    const strengthLabels = [
        t('auth.register.passwordStrength.0'),
        t('auth.register.passwordStrength.1'),
        t('auth.register.passwordStrength.2'),
        t('auth.register.passwordStrength.3'),
        t('auth.register.passwordStrength.4'),
    ];

    const handleRegister = async () => {
        if (!firstName || !lastName || !email || !password || !confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.register.missingTitle'), text2: t('auth.register.missingBody') });
            return;
        }
        if (password !== confirmPassword) {
            Toast.show({ type: 'error', text1: t('auth.register.errorTitle'), text2: t('auth.register.passwordMismatch') });
            return;
        }
        if (!kvkkAccepted) {
            Toast.show({ type: 'error', text1: t('auth.register.errorTitle'), text2: t('auth.register.kvkkRequired') });
            return;
        }
        setIsLoading(true);
        Keyboard.dismiss();
        try {
            await AuthService.register({ firstName, lastName, email, password });
            Toast.show({ type: 'success', text1: t('auth.register.successTitle'), text2: t('auth.register.successBody') });
            setTimeout(() => navigation.navigate('Login'), 1000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.register.failedTitle'), text2: error.response?.data?.message || t('auth.register.failedBody') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Screen keyboardAvoiding scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('auth.register.title')} />
            </View>

            <View style={{ paddingHorizontal: theme.spacing.lg, gap: 4 }}>
                {/* Marka başlığı (harici görsel kaldırıldı) */}
                <View style={{ alignItems: 'center', marginBottom: theme.spacing.md }}>
                    <View
                        style={{
                            width: 72, height: 72, borderRadius: theme.borderRadius.xl,
                            backgroundColor: c.primaryTint, alignItems: 'center', justifyContent: 'center',
                            marginBottom: 12,
                        }}
                    >
                        <Ionicons name="shield-checkmark" size={36} color={c.primary} />
                    </View>
                    <Text style={{ fontSize: 22, fontWeight: '700', color: c.text }}>Güvenli Kayıt</Text>
                    <Text style={{ fontSize: 14, color: c.textSecondary, marginTop: 4, textAlign: 'center' }}>
                        Şeffaf oylama ağına katılın.
                    </Text>
                </View>

                <View style={{ flexDirection: 'row', gap: 12 }}>
                    <View style={{ flex: 1 }}>
                        <Input
                            label="Ad"
                            placeholder="Adınız"
                            value={firstName}
                            onChangeText={setFirstName}
                            returnKeyType="next"
                            onSubmitEditing={() => lastNameRef.current?.focus()}
                            blurOnSubmit={false}
                        />
                    </View>
                    <View style={{ flex: 1 }}>
                        <Input
                            ref={lastNameRef}
                            label="Soyad"
                            placeholder="Soyadınız"
                            value={lastName}
                            onChangeText={setLastName}
                            returnKeyType="next"
                            onSubmitEditing={() => emailRef.current?.focus()}
                            blurOnSubmit={false}
                        />
                    </View>
                </View>

                <Input
                    ref={emailRef}
                    label="E-Posta Adresi"
                    icon="mail-outline"
                    placeholder="isim@ornek.com"
                    value={email}
                    onChangeText={setEmail}
                    keyboardType="email-address"
                    autoCapitalize="none"
                    returnKeyType="next"
                    onSubmitEditing={() => passwordRef.current?.focus()}
                    blurOnSubmit={false}
                />

                <Input
                    ref={passwordRef}
                    label="Parola"
                    icon="lock-closed-outline"
                    placeholder="••••••••"
                    password
                    value={password}
                    onChangeText={setPassword}
                    returnKeyType="next"
                    onSubmitEditing={() => confirmPasswordRef.current?.focus()}
                    blurOnSubmit={false}
                />
                {/* Parola gücü */}
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: -8, marginBottom: 12 }}>
                    <View style={{ flexDirection: 'row', flex: 1, height: 6, gap: 4, borderRadius: 3, overflow: 'hidden', backgroundColor: c.surfaceAlt }}>
                        {[1, 2, 3, 4].map((i) => (
                            <View
                                key={i}
                                style={{
                                    flex: 1, borderRadius: 3,
                                    backgroundColor: strengthScore >= i ? strengthColors[strengthScore] : 'transparent',
                                }}
                            />
                        ))}
                    </View>
                    <Text style={{ fontSize: 12, color: c.textSecondary, width: 64, textAlign: 'right' }}>
                        {password.length > 0 ? strengthLabels[strengthScore] : ''}
                    </Text>
                </View>

                <Input
                    ref={confirmPasswordRef}
                    label="Parola (Tekrar)"
                    icon="lock-closed-outline"
                    placeholder="••••••••"
                    password
                    value={confirmPassword}
                    onChangeText={setConfirmPassword}
                    returnKeyType="done"
                    onSubmitEditing={Keyboard.dismiss}
                />

                {/* KVKK onayı */}
                <TouchableOpacity
                    style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 12, marginTop: 4, marginBottom: 8 }}
                    onPress={() => setKvkkAccepted(!kvkkAccepted)}
                    activeOpacity={0.8}
                >
                    <View
                        style={{
                            width: 22, height: 22, borderRadius: 6, marginTop: 1,
                            borderWidth: 1.5,
                            borderColor: kvkkAccepted ? c.primary : c.borderStrong,
                            backgroundColor: kvkkAccepted ? c.primary : 'transparent',
                            alignItems: 'center', justifyContent: 'center',
                        }}
                    >
                        {kvkkAccepted && <Ionicons name="checkmark" size={15} color="#fff" />}
                    </View>
                    <Text style={{ flex: 1, fontSize: 13, color: c.textSecondary, lineHeight: 19 }}>
                        <Text style={{ fontWeight: '600', color: c.primary }}>Gizlilik Politikası</Text> ve{' '}
                        <Text style={{ fontWeight: '600', color: c.primary }}>KVKK Metnini</Text>
                        {' '}okudum, onaylıyorum.
                    </Text>
                </TouchableOpacity>

                <Button
                    title={isLoading ? 'Lütfen Bekleyin...' : 'Kayıt Ol'}
                    icon="arrow-forward"
                    iconPosition="right"
                    loading={isLoading}
                    onPress={handleRegister}
                    style={{ marginTop: 8 }}
                />

                <View style={{ alignItems: 'center', marginTop: 20, marginBottom: 24 }}>
                    <Text style={{ color: c.textSecondary, fontSize: 14 }}>
                        Zaten hesabınız var mı?{' '}
                        <Text style={{ fontWeight: '700', color: c.primary }} onPress={() => navigation.navigate('Login')}>
                            Giriş Yap
                        </Text>
                    </Text>
                </View>
            </View>
        </Screen>
    );
};
