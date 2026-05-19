import React from 'react';
import { View, Text } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../utils/theme';
import { Button } from './Button';

interface EmptyStateProps {
  icon?: keyof typeof Ionicons.glyphMap;
  title: string;
  subtitle?: string;
  actionLabel?: string;
  onAction?: () => void;
}

/** Boş liste / sonuç yok durumları için tutarlı görsel. */
export const EmptyState: React.FC<EmptyStateProps> = ({
  icon = 'file-tray-outline', title, subtitle, actionLabel, onAction,
}) => (
  <View style={{ alignItems: 'center', paddingVertical: 48, paddingHorizontal: 24 }}>
    <View
      style={{
        width: 72, height: 72, borderRadius: 36,
        backgroundColor: theme.colors.surfaceAlt,
        alignItems: 'center', justifyContent: 'center', marginBottom: 16,
      }}
    >
      <Ionicons name={icon} size={32} color={theme.colors.textTertiary} />
    </View>
    <Text
      style={{
        fontSize: 17, fontWeight: '700', color: theme.colors.text,
        textAlign: 'center',
      }}
    >
      {title}
    </Text>
    {subtitle && (
      <Text
        style={{
          fontSize: 14, color: theme.colors.textSecondary,
          textAlign: 'center', marginTop: 6, lineHeight: 20,
        }}
      >
        {subtitle}
      </Text>
    )}
    {actionLabel && onAction && (
      <View style={{ marginTop: 20 }}>
        <Button title={actionLabel} onPress={onAction} fullWidth={false} size="sm" />
      </View>
    )}
  </View>
);
