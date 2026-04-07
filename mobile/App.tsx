import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { AuthProvider } from './src/context/AuthContext';
import { AppNavigator } from './src/navigation/AppNavigator';
import Toast from 'react-native-toast-message';
import { LanguageProvider } from './src/i18n/LanguageContext';

export default function App() {
  return (
    <>
            <LanguageProvider>
                <AuthProvider>
                    <AppNavigator />
                </AuthProvider>
            </LanguageProvider>
            <Toast />
            <StatusBar style="dark" />
    </>
  );
}
