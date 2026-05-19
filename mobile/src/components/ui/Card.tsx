import React from 'react';
import { View, TouchableOpacity, StyleProp, ViewStyle } from 'react-native';
import { theme } from '../../utils/theme';

interface CardProps {
  children: React.ReactNode;
  /** Dokunulabilir kart (liste öğesi gibi). */
  onPress?: () => void;
  /** İç boşluk. Varsayılan 16. */
  padding?: number;
  /** Yumuşak gölge (öne çıkan kart). Varsayılan true. */
  elevated?: boolean;
  style?: StyleProp<ViewStyle>;
}

/**
 * Beyaz yüzey + ince kenar + yumuşak gölge. Soft-gri zemin üzerinde içerik
 * grupları için standart konteyner. onPress verilirse dokunulabilir.
 */
export const Card: React.FC<CardProps> = ({
  children, onPress, padding = theme.spacing.md, elevated = true, style,
}) => {
  const base: ViewStyle = {
    backgroundColor: theme.colors.surface,
    borderRadius: theme.borderRadius.lg,
    borderWidth: 1,
    borderColor: theme.colors.border,
    padding,
    ...(elevated ? theme.shadows.card : {}),
  };

  if (onPress) {
    return (
      <TouchableOpacity activeOpacity={0.7} onPress={onPress} style={[base, style]}>
        {children}
      </TouchableOpacity>
    );
  }
  return <View style={[base, style]}>{children}</View>;
};
