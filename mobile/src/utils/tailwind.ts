import { create } from 'twrnc';
import { theme } from './theme';

// Create a configured twrnc instance with tokens from theme.ts
export const tw = create({
  theme: {
    extend: {
      colors: {
        primary: theme.colors.primary,
        secondary: theme.colors.secondary,
        success: theme.colors.success,
        danger: theme.colors.danger,
        warning: theme.colors.warning,
        background: theme.colors.background,
        surface: theme.colors.surface,
        textDefault: theme.colors.text,
        textSecondary: theme.colors.textSecondary,
        borderDefault: theme.colors.border,
      },
      // Note: In react native, border radius and spacing can also be extended if needed,
      // but keeping it simple for colors for now as requested.
    },
  },
});
