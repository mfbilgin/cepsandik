import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from './src/context/AuthContext';
import { AppNavigator } from './src/navigation/AppNavigator';
import Toast from 'react-native-toast-message';
import { LanguageProvider } from './src/i18n/LanguageContext';

import { UIProvider } from './src/context/UIContext';
import { ModernDialog } from './src/components/ModernDialog';

export default function App() {
  return (
    <SafeAreaProvider>
            <LanguageProvider>
                <UIProvider>
                    <AuthProvider>
                        <AppNavigator />
                        <ModernDialog />
                    </AuthProvider>
                </UIProvider>
            </LanguageProvider>
            <Toast />
            <StatusBar style="dark" />
    </SafeAreaProvider>
  );
}
