import axios from 'axios';
import { api, API_URL } from './api';
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

    // Biyometrik giriş için: kayıtlı refresh token ile yeni access token al.
    // Interceptor'ı bypass etmek için doğrudan axios kullanılır (sonsuz refresh döngüsünü önler).
    refreshWithToken: async (refreshToken: string) => {
        const response = await axios.post(`${API_URL}/auth/refresh`, { refreshToken });
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

    // "Oturumu bitir" — access token silinir, refresh_token + saved_email biyometrik için kalır.
    // Tam unutma için forgetDevice() çağrılmalı (Security ekranı aksiyonu).
    logout: async () => {
        await SecureStore.deleteItemAsync('access_token');
    },

    // Cihazdan tamamen çıkış: tüm oturum bilgileri silinir, biyometrik bir daha kullanılamaz.
    forgetDevice: async () => {
        await SecureStore.deleteItemAsync('access_token');
        await SecureStore.deleteItemAsync('refresh_token');
        await SecureStore.deleteItemAsync('saved_email');
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
