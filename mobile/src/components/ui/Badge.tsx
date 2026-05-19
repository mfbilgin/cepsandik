import React from 'react';
import { View, Text } from 'react-native';
import { theme } from '../../utils/theme';

type Tone = 'neutral' | 'primary' | 'success' | 'danger' | 'warning' | 'info';

interface BadgeProps {
  label: string;
  tone?: Tone;
  /** Solda küçük nokta (durum göstergesi). */
  dot?: boolean;
}

const MAP: Record<Tone, { bg: string; fg: string }> = {
  neutral: { bg: theme.colors.surfaceAlt,   fg: theme.colors.textSecondary },
  primary: { bg: theme.colors.primaryTint,  fg: theme.colors.primaryDark },
  success: { bg: theme.colors.successTint,  fg: theme.colors.success },
  danger:  { bg: theme.colors.dangerTint,   fg: theme.colors.danger },
  warning: { bg: theme.colors.warningTint,  fg: theme.colors.warning },
  info:    { bg: theme.colors.infoTint,     fg: theme.colors.info },
};

/** Durum rozeti — seçim durumu (Aktif/Kapandı/Taslak), rol vb. */
export const Badge: React.FC<BadgeProps> = ({ label, tone = 'neutral', dot = false }) => {
  const c = MAP[tone];
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        alignSelf: 'flex-start',
        backgroundColor: c.bg,
        paddingHorizontal: 10,
        paddingVertical: 4,
        borderRadius: theme.borderRadius.round,
        gap: 6,
      }}
    >
      {dot && (
        <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: c.fg }} />
      )}
      <Text style={{ color: c.fg, fontSize: 12, fontWeight: '600' }}>{label}</Text>
    </View>
  );
};
