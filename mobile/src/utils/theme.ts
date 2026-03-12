export const theme = {
  colors: {
    primary: '#1E3A8A', // Deep blue (trust, institutional)
    secondary: '#3B82F6', // Lighter blue
    success: '#10B981', // Emerald green
    danger: '#EF4444', // Red
    warning: '#F59E0B', // Amber
    background: '#F9FAFB', // Off-white/gray
    surface: '#FFFFFF', // White
    text: '#111827', // Dark gray
    textSecondary: '#6B7280', // Medium gray
    border: '#E5E7EB', // Light gray
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
    xxl: 48,
  },
  borderRadius: {
    sm: 4,
    md: 8,
    lg: 12,
    xl: 16,
    round: 9999,
  },
  typography: {
    h1: { fontSize: 32, fontWeight: 'bold' as const },
    h2: { fontSize: 24, fontWeight: 'bold' as const },
    h3: { fontSize: 20, fontWeight: '600' as const },
    body: { fontSize: 16, fontWeight: 'normal' as const },
    caption: { fontSize: 14, color: '#6B7280' },
  }
};
