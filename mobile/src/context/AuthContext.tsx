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
    updateUser: (userData: User) => void;
    refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextData>({} as AuthContextData);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        loadStorageData();

        const subscription = DeviceEventEmitter.addListener('auth_error_logout', () => {
            console.log('[AuthContext] Received auth_error_logout event, signing out...');
            signOut();
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

    async function signOut() {
        await SecureStore.deleteItemAsync('access_token');
        await SecureStore.deleteItemAsync('refresh_token');
        setUser(null);
    }

    function updateUser(userData: User) {
        setUser(userData);
    }

    async function refreshUser() {
        await loadStorageData();
    }

    return (
        <AuthContext.Provider value={{ user, isLoading, signIn, signOut, updateUser, refreshUser }}>
            {children}
        </AuthContext.Provider>
    );
};

export function useAuth() {
    const context = useContext(AuthContext);
    return context;
}
