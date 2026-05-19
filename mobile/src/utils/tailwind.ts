import { create } from 'twrnc';
import { theme } from './theme';

// twrnc örneği — theme.ts token'larıyla beslenir. Ekranlar
// `tw\`bg-primary text-textSecondary rounded-lg\`` gibi token-temelli sınıf
// kullanır; palet tek yerden (theme.ts) yönetilir. Eski anahtarlar (primary,
// secondary, surface, background, success, danger, warning, textDefault,
// textSecondary, borderDefault) KORUNDU → mevcut ekranlar kırılmaz.
export const tw = create({
  theme: {
    extend: {
      colors: {
        primary: theme.colors.primary,
        primaryDark: theme.colors.primaryDark,
        primaryTint: theme.colors.primaryTint,
        onPrimary: theme.colors.onPrimary,
        secondary: theme.colors.secondary,
        background: theme.colors.background,
        surface: theme.colors.surface,
        surfaceAlt: theme.colors.surfaceAlt,
        success: theme.colors.success,
        successTint: theme.colors.successTint,
        danger: theme.colors.danger,
        dangerTint: theme.colors.dangerTint,
        warning: theme.colors.warning,
        warningTint: theme.colors.warningTint,
        info: theme.colors.info,
        infoTint: theme.colors.infoTint,
        textDefault: theme.colors.text,
        textSecondary: theme.colors.textSecondary,
        textTertiary: theme.colors.textTertiary,
        borderDefault: theme.colors.border,
        borderStrong: theme.colors.borderStrong,
      },
      borderRadius: {
        sm: `${theme.borderRadius.sm}px`,
        md: `${theme.borderRadius.md}px`,
        lg: `${theme.borderRadius.lg}px`,
        xl: `${theme.borderRadius.xl}px`,
        full: `${theme.borderRadius.round}px`,
      },
      fontSize: {
        display: '30px',
        h1: '24px',
        h2: '20px',
        h3: '17px',
        body: '15px',
        caption: '13px',
        label: '12px',
      },
    },
  },
});
