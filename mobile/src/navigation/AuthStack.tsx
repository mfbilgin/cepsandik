import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { LoginScreen } from '../screens/auth/LoginScreen';
import { RegisterScreen } from '../screens/auth/RegisterScreen';
import { ForgotPasswordScreen } from '../screens/auth/ForgotPasswordScreen';
import { ResetPasswordScreen } from '../screens/auth/ResetPasswordScreen';
import { TwoFactorLoginScreen } from '../screens/auth/TwoFactorLoginScreen';
import { VerificationPendingScreen } from '../screens/auth/VerificationPendingScreen';

const Stack = createNativeStackNavigator();

export const AuthStack = () => {
    return (
        <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Login" component={LoginScreen} />
            <Stack.Screen name="Register" component={RegisterScreen} />
            <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
            <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
            <Stack.Screen name="TwoFactorLogin" component={TwoFactorLoginScreen} />
            <Stack.Screen name="VerificationPending" component={VerificationPendingScreen} />
        </Stack.Navigator>
    );
};
