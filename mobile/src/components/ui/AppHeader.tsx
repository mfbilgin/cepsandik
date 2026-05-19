import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { theme } from '../../utils/theme';

interface AppHeaderProps {
  title: string;
  subtitle?: string;
  /** Geri oku göster. Varsayılan true (navigation.goBack). */
  back?: boolean;
  onBack?: () => void;
  /** Sağ aksiyon (ikon buton / özel node). */
  right?: React.ReactNode;
}

/**
 * Tüm ekranlarda tutarlı başlık şeridi: geri oku + başlık/alt başlık +
 * opsiyonel sağ aksiyon. Ekranlar artık ad-hoc başlık dizmez.
 */
export const AppHeader: React.FC<AppHeaderProps> = ({
  title, subtitle, back = true, onBack, right,
}) => {
  const navigation = useNavigation<any>();
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingBottom: theme.spacing.md,
        gap: 12,
      }}
    >
      {back && (
        <TouchableOpacity
          onPress={onBack ?? (() => navigation.goBack())}
          hitSlop={12}
          style={{
            width: 38,
            height: 38,
            borderRadius: theme.borderRadius.md,
            backgroundColor: theme.colors.surface,
            borderWidth: 1,
            borderColor: theme.colors.border,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name="chevron-back" size={22} color={theme.colors.text} />
        </TouchableOpacity>
      )}
      <View style={{ flex: 1 }}>
        <Text
          numberOfLines={1}
          style={{ fontSize: 20, fontWeight: '700', color: theme.colors.text }}
        >
          {title}
        </Text>
        {subtitle && (
          <Text
            numberOfLines={1}
            style={{ fontSize: 13, color: theme.colors.textSecondary, marginTop: 2 }}
          >
            {subtitle}
          </Text>
        )}
      </View>
      {right ? <View>{right}</View> : null}
    </View>
  );
};
