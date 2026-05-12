import { api } from './api';
import * as SecureStore from 'expo-secure-store';

export const AuthService = {
    login: async (email: string, password: string) => {
        const response = await api.post('/auth/login', { email, password });
        const authData = response.data?.data;
        if (authData?.accessToken) {
            await SecureStore.setItemAsync('access_token', authData.accessToken);
        }
        if (authData?.refreshToken) {
            await SecureStore.setItemAsync('refresh_token', authData.refreshToken);
        }
        return authData;
    },

    loginWith2FA: async (tempToken: string, code: string) => {
        const response = await api.post('/auth/login/2fa', { tempToken, code });
        const authData = response.data?.data;
        if (authData?.accessToken) {
            await SecureStore.setItemAsync('access_token', authData.accessToken);
        }
        if (authData?.refreshToken) {
            await SecureStore.setItemAsync('refresh_token', authData.refreshToken);
        }
        return authData;
    },

    register: async (userData: any) => {
        const response = await api.post('/auth/register', userData);
        return response.data?.data;
    },

    logout: async () => {
        await SecureStore.deleteItemAsync('access_token');
        await SecureStore.deleteItemAsync('refresh_token');
    },

    forgotPassword: async (email: string) => {
        const response = await api.post('/auth/forgot-password', { email });
        return response.data?.data;
    },

    resetPassword: async (token: string, newPassword: string) => {
        const response = await api.post('/auth/reset-password', { resetToken: token, newPassword });
        return response.data?.data;
    },

    getProfile: async () => {
        const response = await api.get('/users/me');
        return response.data?.data;
    },

    resendVerification: async (email: string, password: string) => {
        const response = await api.post('/auth/resend-verification', { email, password });
        return response.data;
    }
};
