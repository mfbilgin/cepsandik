import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../utils/theme';

interface ListItemProps {
  title: string;
  subtitle?: string;
  /** Sol ikon (daire zemin içinde). */
  icon?: keyof typeof Ionicons.glyphMap;
  iconTone?: 'primary' | 'success' | 'danger' | 'warning' | 'neutral';
  /** Sağ taraf: özel node; yoksa onPress varsa chevron. */
  right?: React.ReactNode;
  onPress?: () => void;
}

const TONE: Record<NonNullable<ListItemProps['iconTone']>, { bg: string; fg: string }> = {
  primary: { bg: theme.colors.primaryTint, fg: theme.colors.primary },
  success: { bg: theme.colors.successTint, fg: theme.colors.success },
  danger:  { bg: theme.colors.dangerTint,  fg: theme.colors.danger },
  warning: { bg: theme.colors.warningTint, fg: theme.colors.warning },
  neutral: { bg: theme.colors.surfaceAlt,  fg: theme.colors.textSecondary },
};

/** Ayar/menü/satır listesi öğesi — sol ikon + başlık/alt + sağ chevron. */
export const ListItem: React.FC<ListItemProps> = ({
  title, subtitle, icon, iconTone = 'neutral', right, onPress,
}) => {
  const t = TONE[iconTone];
  const Wrapper: any = onPress ? TouchableOpacity : View;
  return (
    <Wrapper
      {...(onPress ? { onPress, activeOpacity: 0.7 } : {})}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: 12,
        gap: 12,
      }}
    >
      {icon && (
        <View
          style={{
            width: 40, height: 40, borderRadius: theme.borderRadius.md,
            backgroundColor: t.bg, alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Ionicons name={icon} size={20} color={t.fg} />
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: 15, fontWeight: '600', color: theme.colors.text }}>
          {title}
        </Text>
        {subtitle && (
          <Text style={{ fontSize: 13, color: theme.colors.textSecondary, marginTop: 2 }}>
            {subtitle}
          </Text>
        )}
      </View>
      {right !== undefined
        ? right
        : onPress
          ? <Ionicons name="chevron-forward" size={18} color={theme.colors.textTertiary} />
          : null}
    </Wrapper>
  );
};
