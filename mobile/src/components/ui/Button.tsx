import React from 'react';
import {
  TouchableOpacity, Text, ActivityIndicator, View,
  StyleProp, ViewStyle, TouchableOpacityProps,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../utils/theme';

type Variant = 'primary' | 'secondary' | 'outline' | 'danger' | 'ghost';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends Omit<TouchableOpacityProps, 'style'> {
  title: string;
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: keyof typeof Ionicons.glyphMap;
  iconPosition?: 'left' | 'right';
  fullWidth?: boolean;
  style?: StyleProp<ViewStyle>;
}

const HEIGHT: Record<Size, number> = { sm: 38, md: 48, lg: 54 };
const FONT: Record<Size, number> = { sm: 14, md: 15, lg: 16 };

/**
 * Tek tutarlı buton. Tüm ekranlar bunu kullanır → renk/radius/yükseklik
 * tek yerden. variant=primary güven mavisi dolu; outline/ghost düşük vurgu;
 * danger yıkıcı eylem.
 */
export const Button: React.FC<ButtonProps> = ({
  title, variant = 'primary', size = 'md', loading = false,
  icon, iconPosition = 'left', fullWidth = true, disabled, style, ...rest
}) => {
  const isDisabled = disabled || loading;

  const palette = (): { bg: string; fg: string; border?: string } => {
    if (isDisabled) return { bg: theme.colors.border, fg: theme.colors.textTertiary };
    switch (variant) {
      case 'primary':   return { bg: theme.colors.primary, fg: theme.colors.onPrimary };
      case 'secondary': return { bg: theme.colors.surfaceAlt, fg: theme.colors.text, border: theme.colors.border };
      case 'outline':   return { bg: 'transparent', fg: theme.colors.primary, border: theme.colors.primary };
      case 'danger':    return { bg: theme.colors.danger, fg: '#FFFFFF' };
      case 'ghost':     return { bg: 'transparent', fg: theme.colors.primary };
      default:          return { bg: theme.colors.primary, fg: theme.colors.onPrimary };
    }
  };
  const { bg, fg, border } = palette();

  return (
    <TouchableOpacity
      activeOpacity={0.85}
      disabled={isDisabled}
      style={[
        {
          height: HEIGHT[size],
          backgroundColor: bg,
          borderRadius: theme.borderRadius.lg,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: theme.spacing.lg,
          alignSelf: fullWidth ? 'stretch' : 'flex-start',
          borderWidth: border ? 1.5 : 0,
          borderColor: border,
        },
        style,
      ]}
      {...rest}
    >
      {loading ? (
        <ActivityIndicator color={fg} />
      ) : (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          {icon && iconPosition === 'left' && (
            <Ionicons name={icon} size={FONT[size] + 3} color={fg} />
          )}
          <Text style={{ color: fg, fontSize: FONT[size], fontWeight: '600' }}>
            {title}
          </Text>
          {icon && iconPosition === 'right' && (
            <Ionicons name={icon} size={FONT[size] + 3} color={fg} />
          )}
        </View>
      )}
    </TouchableOpacity>
  );
};
