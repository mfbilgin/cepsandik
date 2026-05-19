import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { theme } from '../../utils/theme';

interface SectionHeaderProps {
  title: string;
  /** Sağ üst aksiyon metni (ör. "Tümü"). */
  actionLabel?: string;
  onAction?: () => void;
}

/** Bölüm başlığı + opsiyonel "Tümü" aksiyonu (Home/list ekranları). */
export const SectionHeader: React.FC<SectionHeaderProps> = ({
  title, actionLabel, onAction,
}) => (
  <View
    style={{
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      marginBottom: theme.spacing.sm,
      marginTop: theme.spacing.sm,
    }}
  >
    <Text style={{ fontSize: 17, fontWeight: '700', color: theme.colors.text }}>
      {title}
    </Text>
    {actionLabel && onAction && (
      <TouchableOpacity onPress={onAction} hitSlop={8}>
        <Text style={{ fontSize: 14, fontWeight: '600', color: theme.colors.primary }}>
          {actionLabel}
        </Text>
      </TouchableOpacity>
    )}
  </View>
);
