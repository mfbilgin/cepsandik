import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import Constants from 'expo-constants';

/**
 * Generates a simple UUID for request tracing.
 */
const generateCorrelationId = () => {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
};

// Emülatör loopback'i (10.0.2.2) sadece AVD'den çalışır. Çok-cihaz UAT için
// EXPO_PUBLIC_API_URL ile host LAN IP'si verilir (emülatör + gerçek cihaz
// aynı ağda erişir). Tanımsızsa emülatör varsayılanına düşer.
export const API_URL =
    process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8088/v1';

export const api = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

api.interceptors.request.use(
    async (config) => {
        try {
            const token = await SecureStore.getItemAsync('access_token');
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            } else {
                console.log(`[API WARNING] No access token for ${config.url}`);
            }
        } catch (e) {
            console.error('SecureStore error:', e);
        }

        // Distributed Tracing & Context Headers
        config.headers['X-Correlation-ID'] = generateCorrelationId();
        config.headers['X-App-Version'] = Constants.expoConfig?.version || '1.0.0';
        config.headers['X-Platform'] = Platform.OS;

        try {
            let deviceId = await SecureStore.getItemAsync('device_id');
            if (!deviceId) {
                deviceId = generateCorrelationId();
                await SecureStore.setItemAsync('device_id', deviceId);
            }
            config.headers['X-Device-ID'] = deviceId;
        } catch (e) {
            config.headers['X-Device-ID'] = 'unknown';
        }

        const fullUrl = `${config.baseURL}${config.url}`;
        console.log(`[API REQUEST] [CID: ${config.headers['X-Correlation-ID']}] ${config.method?.toUpperCase()} ${fullUrl}`);
        return config;
    },
    (error) => Promise.reject(error)
);

let isRefreshing = false;
let failedQueue: Array<{ resolve: (value?: unknown) => void; reject: (reason?: any) => void }> = [];

const processQueue = (error: any, token: string | null = null) => {
    failedQueue.forEach(prom => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token);
        }
    });
    failedQueue = [];
};

api.interceptors.response.use(
    (response) => {
        const fullUrl = `${response.config.baseURL}${response.config.url}`;
        console.log(`[API SUCCESS] ${response.status} from ${fullUrl}`);
        return response;
    },
    async (error) => {
        const fullUrl = `${error.config?.baseURL}${error.config?.url}`;
        console.log(`[API ERROR] ${error.response?.status} for ${fullUrl}: ${JSON.stringify(error.response?.data || error.message)}`);

        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {

            // Avoid infinite loops if refresh token itself fails
            if (originalRequest.url?.includes('/auth/refresh') || originalRequest.url?.includes('/auth/login')) {
                return Promise.reject(error);
            }

            if (isRefreshing) {
                return new Promise(function (resolve, reject) {
                    failedQueue.push({ resolve, reject });
                })
                    .then(token => {
                        originalRequest.headers.Authorization = 'Bearer ' + token;
                        return api(originalRequest);
                    })
                    .catch(err => {
                        return Promise.reject(err);
                    });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const refreshToken = await SecureStore.getItemAsync('refresh_token');
                if (!refreshToken) {
                    throw new Error('No refresh token available in SecureStore. User needs to login again.');
                }

                // Call the token refresh API directly
                const response = await axios.post(`${API_URL}/auth/refresh`, { refreshToken });
                const newTokens = response.data?.data;

                if (newTokens?.accessToken) {
                    await SecureStore.setItemAsync('access_token', newTokens.accessToken);
                    if (newTokens.refreshToken) {
                        await SecureStore.setItemAsync('refresh_token', newTokens.refreshToken);
                    }

                    api.defaults.headers.common['Authorization'] = `Bearer ${newTokens.accessToken}`;
                    originalRequest.headers.Authorization = `Bearer ${newTokens.accessToken}`;

                    processQueue(null, newTokens.accessToken);
                    return api(originalRequest);
                } else {
                    throw new Error('Invalid refresh payload received from backend.');
                }
            } catch (refreshError: any) {
                console.error('[API REFRESH FAILED] Refresh process failed:', refreshError.message || refreshError);
                processQueue(refreshError, null);

                // On complete failure, clear tokens and emit event to force logout
                await SecureStore.deleteItemAsync('access_token');
                await SecureStore.deleteItemAsync('refresh_token');

                // We emit a custom event so that App.tsx or AuthContext can listen and call signOut()
                // Avoid using direct navigational dispatch here to prevent infinite loop risks.
                // You can listen to this event: DeviceEventEmitter.addListener('auth_error_logout', () => signOut())
                import('react-native').then(({ DeviceEventEmitter }) => {
                    DeviceEventEmitter.emit('auth_error_logout');
                });

                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        return Promise.reject(error);
    }
);
