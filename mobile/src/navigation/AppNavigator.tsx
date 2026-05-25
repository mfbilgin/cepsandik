import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { View, ActivityIndicator, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { AuthStack } from './AuthStack';
import { MainStack } from './MainStack';
import { theme } from '../utils/theme';

import * as Linking from 'expo-linking';

export const AppNavigator = () => {
    const { user, isLoading } = useAuth();

    const linking = {
        prefixes: [Linking.createURL('/'), 'https://cepsandik.com', 'cepsandik://'],
        config: {
            screens: {
                ResetPassword: 'auth/reset-password/:token',
                CommunityDetail: 'c/:id',
            }
        }
    };

    if (isLoading) {
        return (
            <View style={styles.loadingContainer}>
                <ActivityIndicator size="large" color={theme.colors.primary} />
            </View>
        );
    }

    return (
        <NavigationContainer linking={linking}>
            <KeyboardAvoidingView
                style={{ flex: 1 }}
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                keyboardVerticalOffset={0}
            >
                {user ? <MainStack /> : <AuthStack />}
            </KeyboardAvoidingView>
        </NavigationContainer>
    );
};

const styles = StyleSheet.create({
    loadingContainer: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
    },
});
