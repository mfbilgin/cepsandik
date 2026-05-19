import React, { useState, useRef } from 'react';
import { 
    View, 
    TextInput, 
    TouchableOpacity, 
    ActivityIndicator, 
    Text, 
    Alert, 
    Platform,
    Clipboard
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Toast from 'react-native-toast-message';
import { theme } from '../utils/theme';
import { api } from '../services/api';
import { useI18n } from '../i18n/LanguageContext';

interface AccessCodeInputProps {
    onSuccess: (electionData: any) => void;
    onCancel: () => void;
}

/**
 * AccessCodeInput Component
 * 
 * Allows users to enter a 6-digit access code to join independent elections
 * Features:
 * - Formatted input (ABC 123 style)
 * - Paste support from clipboard
 * - Real-time validation
 * - Error handling
 */
export const AccessCodeInput: React.FC<AccessCodeInputProps> = ({ onSuccess, onCancel }) => {
    const { t } = useI18n();
    const [code, setCode] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const inputRef = useRef<TextInput>(null);

    const formatCode = (text: string): string => {
        // Remove all non-alphanumeric characters
        const cleaned = text.toUpperCase().replace(/[^A-Z0-9]/g, '');
        
        // Limit to 6 characters
        const trimmed = cleaned.slice(0, 6);
        
        // Format as ABC 123 (3 chars, space, 3 chars)
        if (trimmed.length <= 3) {
            return trimmed;
        }
        return `${trimmed.slice(0, 3)} ${trimmed.slice(3, 6)}`;
    };

    const handleCodeChange = (text: string) => {
        const formatted = formatCode(text);
        setCode(formatted);
        setError(null);
    };

    const handlePaste = async () => {
        try {
            const pastedText = await Clipboard.getString();
            if (pastedText) {
                const formatted = formatCode(pastedText);
                setCode(formatted);
                setError(null);
            }
        } catch (err) {
            console.error('Paste error:', err);
        }
    };

    const getCodeWithoutSpace = (): string => {
        return code.replace(/\s+/g, '');
    };

    const handleSubmit = async () => {
        const cleanCode = getCodeWithoutSpace();

        // Validate code format
        if (cleanCode.length !== 6) {
            setError(t('accessCode.invalidFormat') || 'Erişim kodu 6 karakter olmalıdır');
            return;
        }

        if (!/^[A-Z0-9]{6}$/.test(cleanCode)) {
            setError(t('accessCode.invalidFormat') || 'Erişim kodu alfanumerik olmalıdır');
            return;
        }

        setIsLoading(true);
        setError(null);

        try {
            // First verify the code
            const verifyResponse = await api.post('/elections/by-code/verify', {
                code: cleanCode,
            });

            if (!verifyResponse.data?.data) {
                throw new Error('Invalid response');
            }

            const electionData = verifyResponse.data.data;

            // Show success toast
            Toast.show({
                type: 'success',
                text1: t('accessCode.success') || 'Seçim bulundu!',
                text2: electionData.title,
                visibilityTime: 2000,
            });

            // Call success callback
            onSuccess(electionData);
        } catch (err: any) {
            const errorMessage = err.response?.data?.message 
                || err.message 
                || t('accessCode.invalidCode') 
                || 'Geçersiz erişim kodu';
            
            setError(errorMessage);
            
            Toast.show({
                type: 'error',
                text1: t('accessCode.error') || 'Hata',
                text2: errorMessage,
                visibilityTime: 3000,
            });

            console.error('Access code verification failed:', err);
        } finally {
            setIsLoading(false);
        }
    };

    const isCodeComplete = getCodeWithoutSpace().length === 6;

    return (
        <View style={{ gap: 16 }}>
            {/* Title */}
            <View>
                <Text style={{
                    fontSize: 18,
                    fontWeight: '700',
                    color: theme.colors.text,
                    marginBottom: 6,
                }}>
                    {t('accessCode.enterCode') || 'Erişim Kodunu Gir'}
                </Text>
                <Text style={{
                    fontSize: 13,
                    color: theme.colors.textSecondary,
                    lineHeight: 18,
                }}>
                    {t('accessCode.description') || '6 haneli erişim kodunu girerek topluluk bağımsız seçime katıl'}
                </Text>
            </View>

            {/* Code Input Field */}
            <View style={{
                borderWidth: 1.5,
                borderColor: error ? theme.colors.danger : theme.colors.border,
                borderRadius: theme.borderRadius.md,
                paddingHorizontal: 14,
                paddingVertical: 12,
                backgroundColor: theme.colors.surface,
                flexDirection: 'row',
                alignItems: 'center',
                gap: 10,
            }}>
                <Ionicons 
                    name="key-outline" 
                    size={20} 
                    color={error ? theme.colors.danger : theme.colors.textSecondary} 
                />
                <TextInput
                    ref={inputRef}
                    value={code}
                    onChangeText={handleCodeChange}
                    placeholder="ABC 123"
                    placeholderTextColor={theme.colors.textTertiary}
                    maxLength={7} // 6 chars + 1 space
                    autoCapitalize="characters"
                    selectionColor={theme.colors.primary}
                    editable={!isLoading}
                    style={{
                        flex: 1,
                        fontSize: 18,
                        fontWeight: '600',
                        color: theme.colors.text,
                        letterSpacing: 1,
                        fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
                    }}
                />
                {code.length > 0 && (
                    <TouchableOpacity
                        onPress={() => {
                            setCode('');
                            setError(null);
                        }}
                        disabled={isLoading}
                    >
                        <Ionicons 
                            name="close-circle" 
                            size={20} 
                            color={theme.colors.textSecondary} 
                        />
                    </TouchableOpacity>
                )}
            </View>

            {/* Error Message */}
            {error && (
                <View style={{
                    flexDirection: 'row',
                    gap: 8,
                    paddingHorizontal: 12,
                    paddingVertical: 10,
                    backgroundColor: theme.colors.dangerTint,
                    borderRadius: theme.borderRadius.sm,
                    alignItems: 'flex-start',
                }}>
                    <Ionicons 
                        name="alert-circle" 
                        size={18} 
                        color={theme.colors.danger} 
                        style={{ marginTop: 2 }}
                    />
                    <Text style={{
                        flex: 1,
                        fontSize: 12,
                        color: theme.colors.danger,
                        lineHeight: 16,
                    }}>
                        {error}
                    </Text>
                </View>
            )}

            {/* Help Text */}
            <Text style={{
                fontSize: 12,
                color: theme.colors.textTertiary,
                fontStyle: 'italic',
            }}>
                {t('accessCode.example') || 'Örnek: A3X7K9'}
            </Text>

            {/* Action Buttons */}
            <View style={{ gap: 10 }}>
                {/* Main Submit Button */}
                <TouchableOpacity
                    onPress={handleSubmit}
                    disabled={!isCodeComplete || isLoading}
                    activeOpacity={0.7}
                    style={{
                        backgroundColor: !isCodeComplete ? theme.colors.surfaceAlt : theme.colors.primary,
                        paddingVertical: 14,
                        paddingHorizontal: 16,
                        borderRadius: theme.borderRadius.md,
                        flexDirection: 'row',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 8,
                        opacity: !isCodeComplete ? 0.6 : 1,
                    }}
                >
                    {isLoading ? (
                        <ActivityIndicator size="small" color={theme.colors.onPrimary} />
                    ) : (
                        <Ionicons name="log-in-outline" size={20} color={theme.colors.onPrimary} />
                    )}
                    <Text style={{
                        fontSize: 15,
                        fontWeight: '600',
                        color: theme.colors.onPrimary,
                    }}>
                        {isLoading 
                            ? t('accessCode.verifying') || 'Doğrulanıyor...'
                            : t('accessCode.join') || 'Seçime Katıl'
                        }
                    </Text>
                </TouchableOpacity>

                {/* Helper Buttons Row */}
                <View style={{ flexDirection: 'row', gap: 10 }}>
                    {/* Paste Button */}
                    <TouchableOpacity
                        onPress={handlePaste}
                        disabled={isLoading}
                        style={{
                            flex: 1,
                            paddingVertical: 12,
                            paddingHorizontal: 12,
                            borderRadius: theme.borderRadius.md,
                            borderWidth: 1,
                            borderColor: theme.colors.border,
                            backgroundColor: theme.colors.surface,
                            flexDirection: 'row',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: 6,
                        }}
                    >
                        <Ionicons name="copy-outline" size={16} color={theme.colors.text} />
                        <Text style={{
                            fontSize: 13,
                            fontWeight: '500',
                            color: theme.colors.text,
                        }}>
                            {t('accessCode.paste') || 'Yapıştır'}
                        </Text>
                    </TouchableOpacity>

                    {/* Cancel Button */}
                    <TouchableOpacity
                        onPress={onCancel}
                        disabled={isLoading}
                        style={{
                            flex: 1,
                            paddingVertical: 12,
                            paddingHorizontal: 12,
                            borderRadius: theme.borderRadius.md,
                            borderWidth: 1,
                            borderColor: theme.colors.border,
                            backgroundColor: theme.colors.surface,
                            flexDirection: 'row',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: 6,
                        }}
                    >
                        <Ionicons name="close-outline" size={16} color={theme.colors.textSecondary} />
                        <Text style={{
                            fontSize: 13,
                            fontWeight: '500',
                            color: theme.colors.textSecondary,
                        }}>
                            {t('common.cancel') || 'İptal'}
                        </Text>
                    </TouchableOpacity>
                </View>
            </View>
        </View>
    );
};
