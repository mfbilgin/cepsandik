import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
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
            {user ? <MainStack /> : <AuthStack />}
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
