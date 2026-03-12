import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ActivityIndicator, TouchableOpacityProps } from 'react-native';
import { theme } from '../utils/theme';

interface CustomButtonProps extends TouchableOpacityProps {
    title: string;
    variant?: 'primary' | 'secondary' | 'outline' | 'danger';
    isLoading?: boolean;
}

export const CustomButton: React.FC<CustomButtonProps> = ({
    title,
    variant = 'primary',
    isLoading = false,
    style,
    disabled,
    ...props
}) => {
    const isOutline = variant === 'outline';

    const getBackgroundColor = () => {
        if (disabled) return theme.colors.border;
        switch (variant) {
            case 'primary': return theme.colors.primary;
            case 'secondary': return theme.colors.secondary;
            case 'danger': return theme.colors.danger;
            case 'outline': return 'transparent';
            default: return theme.colors.primary;
        }
    };

    const getTextColor = () => {
        if (disabled) return theme.colors.textSecondary;
        if (isOutline) return theme.colors.primary;
        return theme.colors.surface;
    };

    return (
        <TouchableOpacity
            style={[
                styles.button,
                { backgroundColor: getBackgroundColor() },
                isOutline && styles.outlineButton,
                style
            ]}
            disabled={disabled || isLoading}
            {...props}
        >
            {isLoading ? (
                <ActivityIndicator color={getTextColor()} />
            ) : (
                <Text style={[styles.text, { color: getTextColor() }]}>{title}</Text>
            )}
        </TouchableOpacity>
    );
};

const styles = StyleSheet.create({
    button: {
        height: 48,
        borderRadius: theme.borderRadius.md,
        justifyContent: 'center',
        alignItems: 'center',
        paddingHorizontal: theme.spacing.lg,
        marginVertical: theme.spacing.sm,
    },
    outlineButton: {
        borderWidth: 1,
        borderColor: theme.colors.primary,
    },
    text: {
        fontSize: 16,
        fontWeight: 'bold',
    },
});
