import React from 'react';
import {
  View, ScrollView, KeyboardAvoidingView, Platform, RefreshControl,
  StyleProp, ViewStyle,
} from 'react-native';
import { SafeAreaView, Edge } from 'react-native-safe-area-context';
import { theme } from '../../utils/theme';

interface ScreenProps {
  children: React.ReactNode;
  /** İçerik kaydırılabilir mi (uzun form/liste). Varsayılan true. */
  scroll?: boolean;
  /** Yatay iç boşluk uygulansın mı (16px). Varsayılan true. */
  padded?: boolean;
  /** Klavye açılınca içerik itilsin (form ekranları). */
  keyboardAvoiding?: boolean;
  refreshing?: boolean;
  onRefresh?: () => void;
  /** Safe-area kenarları. Varsayılan üst+yan (alt tab bar'a bırakılır). */
  edges?: Edge[];
  backgroundColor?: string;
  contentStyle?: StyleProp<ViewStyle>;
}

/**
 * Tüm ekranların temel kabuğu: güvenli alan + tutarlı zemin + opsiyonel
 * scroll/klavye-itme/pull-to-refresh. Ekranlar artık View+ScrollView+
 * SafeArea boilerplate'ini tekrar etmez.
 */
export const Screen: React.FC<ScreenProps> = ({
  children,
  scroll = true,
  padded = true,
  keyboardAvoiding = false,
  refreshing,
  onRefresh,
  edges = ['top', 'left', 'right'],
  backgroundColor = theme.colors.background,
  contentStyle,
}) => {
  const pad = padded ? { paddingHorizontal: theme.spacing.md } : null;

  const body = scroll ? (
    <ScrollView
      style={{ flex: 1 }}
      contentContainerStyle={[
        { paddingVertical: theme.spacing.md, flexGrow: 1 },
        pad,
        contentStyle,
      ]}
      keyboardShouldPersistTaps="handled"
      showsVerticalScrollIndicator={false}
      refreshControl={
        onRefresh ? (
          <RefreshControl
            refreshing={!!refreshing}
            onRefresh={onRefresh}
            tintColor={theme.colors.primary}
            colors={[theme.colors.primary]}
          />
        ) : undefined
      }
    >
      {children}
    </ScrollView>
  ) : (
    <View style={[{ flex: 1, paddingVertical: theme.spacing.md }, pad, contentStyle]}>
      {children}
    </View>
  );

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor }} edges={edges}>
      {keyboardAvoiding ? (
        <KeyboardAvoidingView
          style={{ flex: 1 }}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
          {body}
        </KeyboardAvoidingView>
      ) : (
        body
      )}
    </SafeAreaView>
  );
};
