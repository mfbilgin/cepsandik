import React, { useState } from 'react';
import { View, Text, Keyboard } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { api } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import Toast from 'react-native-toast-message';
import { useI18n } from '../../i18n/LanguageContext';
import { theme } from '../../utils/theme';
import { Screen, AppHeader, Card, Input, Button } from '../../components/ui';

export const EditProfileScreen = () => {
    const { user, updateUser } = useAuth();
    const navigation = useNavigation<any>();
    const { t } = useI18n();
    const c = theme.colors;

    const [firstName, setFirstName] = useState(user?.firstName || '');
    const [lastName, setLastName] = useState(user?.lastName || '');
    const [isLoading, setIsLoading] = useState(false);

    const handleUpdate = async () => {
        if (!firstName.trim() || !lastName.trim()) {
            Toast.show({ type: 'error', text1: t('auth.login.missingTitle'), text2: t('editProfile.missingBody') });
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

            Toast.show({ type: 'success', text1: t('security.successTitle'), text2: t('editProfile.successBody') });
            setTimeout(() => {
                navigation.goBack();
            }, 1000);
        } catch (error: any) {
            Toast.show({ type: 'error', text1: t('auth.twoFactor.errorTitle'), text2: error.response?.data?.message || t('editProfile.errorBody') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Screen keyboardAvoiding scroll padded={false}>
            <View style={{ paddingHorizontal: theme.spacing.md }}>
                <AppHeader title={t('editProfile.title')} onBack={() => navigation.goBack()} />
            </View>

            <View style={{ paddingHorizontal: theme.spacing.lg, gap: theme.spacing.lg }}>
                <View style={{ alignItems: 'center', justifyContent: 'center', paddingVertical: theme.spacing.md }}>
                    <View
                        style={{
                            width: 96, height: 96, borderRadius: 48, backgroundColor: c.primary,
                            alignItems: 'center', justifyContent: 'center', ...theme.shadows.card,
                        }}
                    >
                        <Text style={{ fontSize: 40, fontWeight: '700', color: c.onPrimary }}>
                            {(firstName?.[0] || user?.firstName?.[0] || '').toUpperCase()}
                        </Text>
                    </View>
                </View>

                <Card padding={theme.spacing.lg}>
                    <Input
                        label={t('editProfile.name')}
                        placeholder={t('editProfile.namePlaceholder')}
                        value={firstName}
                        onChangeText={setFirstName}
                    />
                    <Input
                        label={t('editProfile.surname')}
                        placeholder={t('editProfile.surnamePlaceholder')}
                        value={lastName}
                        onChangeText={setLastName}
                    />
                    <View style={{ opacity: 0.6 }}>
                        <Input
                            label={t('editProfile.email')}
                            value={user?.email}
                            editable={false}
                        />
                        <Text style={{ fontSize: 12, color: c.textTertiary, marginTop: -8, marginBottom: 4 }}>
                            {t('editProfile.emailLocked')}
                        </Text>
                    </View>

                    <Button
                        title={isLoading ? t('notifications.saving') : t('notifications.saveChanges')}
                        loading={isLoading}
                        onPress={handleUpdate}
                        icon={!isLoading ? 'checkmark' : undefined}
                        style={{ marginTop: 8 }}
                    />
                </Card>
            </View>
        </Screen>
    );
};
