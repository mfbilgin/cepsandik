import React, { createContext, useState, useEffect, useContext } from 'react';
import { DeviceEventEmitter } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { AuthService } from '../services/auth.service';

interface User {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    profileImage?: string;
    isMfaEnabled?: boolean;
    mfaEnabled?: boolean;
}

interface AuthContextData {
    user: User | null;
    isLoading: boolean;
    signIn: (accessToken: string, refreshToken: string | null, userData: User) => Promise<void>;
    signOut: () => Promise<void>;
    forgetDevice: () => Promise<void>;
    updateUser: (userData: User) => void;
    refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextData>({} as AuthContextData);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        loadStorageData();

        // Refresh token sunucu tarafından reddedildi (geçersiz/süresi dolmuş) → biyometriği de devre dışı bırak.
        const subscription = DeviceEventEmitter.addListener('auth_error_logout', () => {
            console.log('[AuthContext] Received auth_error_logout event, forgetting device...');
            forgetDevice();
        });

        return () => {
            subscription.remove();
        };
    }, []);

    async function loadStorageData() {
        try {
            const token = await SecureStore.getItemAsync('access_token');
            if (token) {
                const userData = await AuthService.getProfile();
                setUser(userData);
            }
        } catch (error) {
            console.log('Auth check failed:', error);
            await SecureStore.deleteItemAsync('access_token');
        } finally {
            setIsLoading(false);
        }
    }

    async function signIn(accessToken: string, refreshToken: string | null = null, userData: User) {
        if (accessToken) {
            await SecureStore.setItemAsync('access_token', accessToken);
        }
        if (refreshToken) {
            await SecureStore.setItemAsync('refresh_token', refreshToken);
        }
        setUser(userData);
    }

    // Oturumu bitir — refresh_token + saved_email cihazda kalır (biyometrik girişi mümkün kılar).
    async function signOut() {
        await SecureStore.deleteItemAsync('access_token');
        setUser(null);
    }

    // Cihazı unut — tüm oturum + biyometrik verilerini sil.
    async function forgetDevice() {
        await SecureStore.deleteItemAsync('access_token');
        await SecureStore.deleteItemAsync('refresh_token');
        await SecureStore.deleteItemAsync('saved_email');
        setUser(null);
    }

    function updateUser(userData: User) {
        setUser(userData);
    }

    async function refreshUser() {
        await loadStorageData();
    }

    return (
        <AuthContext.Provider value={{ user, isLoading, signIn, signOut, forgetDevice, updateUser, refreshUser }}>
            {children}
        </AuthContext.Provider>
    );
};

export function useAuth() {
    const context = useContext(AuthContext);
    return context;
}
