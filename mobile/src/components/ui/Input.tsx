import React, { useState, forwardRef } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, TextInputProps,
  StyleProp, ViewStyle,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../utils/theme';

interface InputProps extends Omit<TextInputProps, 'style'> {
  label?: string;
  icon?: keyof typeof Ionicons.glyphMap;
  error?: string;
  /** Şifre alanı — göster/gizle ikonu eklenir. */
  password?: boolean;
  containerStyle?: StyleProp<ViewStyle>;
}

/**
 * Modern form alanı: etiket + opsiyonel ikon + odak vurgusu + hata mesajı +
 * şifre göster/gizle. Tüm auth/form ekranları bunu kullanır.
 */
export const Input = forwardRef<TextInput, InputProps>(
  ({ label, icon, error, password, containerStyle, onFocus, onBlur, ...rest }, ref) => {
    const [focused, setFocused] = useState(false);
    const [hidden, setHidden] = useState(!!password);

    const borderColor = error
      ? theme.colors.danger
      : focused
        ? theme.colors.primary
        : theme.colors.borderStrong;

    return (
      <View style={[{ marginBottom: theme.spacing.md }, containerStyle]}>
        {label && (
          <Text
            style={{
              fontSize: 13,
              fontWeight: '600',
              color: theme.colors.textSecondary,
              marginBottom: 6,
            }}
          >
            {label}
          </Text>
        )}
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            backgroundColor: theme.colors.surface,
            borderWidth: 1.5,
            borderColor,
            borderRadius: theme.borderRadius.md,
            paddingHorizontal: 12,
            height: 50,
          }}
        >
          {icon && (
            <Ionicons
              name={icon}
              size={19}
              color={focused ? theme.colors.primary : theme.colors.textTertiary}
              style={{ marginRight: 8 }}
            />
          )}
          <TextInput
            ref={ref}
            style={{ flex: 1, fontSize: 15, color: theme.colors.text, paddingVertical: 0 }}
            placeholderTextColor={theme.colors.textTertiary}
            secureTextEntry={hidden}
            onFocus={(e) => { setFocused(true); onFocus?.(e); }}
            onBlur={(e) => { setFocused(false); onBlur?.(e); }}
            {...rest}
          />
          {password && (
            <TouchableOpacity onPress={() => setHidden((h) => !h)} hitSlop={10}>
              <Ionicons
                name={hidden ? 'eye-off-outline' : 'eye-outline'}
                size={19}
                color={theme.colors.textTertiary}
              />
            </TouchableOpacity>
          )}
        </View>
        {error && (
          <Text style={{ color: theme.colors.danger, fontSize: 12, marginTop: 4 }}>
            {error}
          </Text>
        )}
      </View>
    );
  },
);
