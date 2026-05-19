// =============================================================
// CepSandık tasarım sistemi — Modern güven/sivil palet (UI/UX Faz A)
//
// Eski "toprak/vintage" palet (bej/arduvaz/adaçayı) düşük kontrastlı ve
// modern bir oylama uygulaması için soluk duruyordu. Yeni yön: temiz beyaz
// yüzey, güven veren mavi primary, yüksek kontrast metin, net durum renkleri.
//
// ÖNEMLİ: Token ANAHTARLARI korundu (primary/secondary/surface/background/
// success/danger/warning/text/textSecondary/border) → mevcut 30 ekran
// kırılmadan anında yeni görünüme geçer. Yeni token'lar (primaryDark,
// *Tint, textTertiary, info, onPrimary, borderStrong, shadows) ADDITIVE.
// =============================================================
export const theme = {
  colors: {
    // Aksiyon / marka — güven mavisi
    primary: '#2563EB',       // blue-600  ana aksiyon, vurgu
    primaryDark: '#1D4ED8',   // blue-700  basılı/aktif durum
    primaryTint: '#EFF6FF',   // blue-50   seçili/aktif arka plan, soft rozet
    onPrimary: '#FFFFFF',     // primary üstü metin/ikon

    secondary: '#475569',     // slate-600 ikincil metin/buton

    // Yüzeyler
    background: '#F4F6F8',    // uygulama zemini (soft gri → kartlar öne çıkar)
    surface: '#FFFFFF',       // kart / sheet / input zemini
    surfaceAlt: '#F8FAFC',    // hafif vurgulu satır/zebra

    // Durum renkleri + soft tint'leri (rozet/uyarı kutusu zemini)
    success: '#059669', successTint: '#ECFDF5',
    danger:  '#DC2626', dangerTint:  '#FEF2F2',
    warning: '#D97706', warningTint: '#FFFBEB',
    info:    '#0EA5E9', infoTint:    '#F0F9FF',

    // Metin hiyerarşisi
    text: '#0F172A',          // slate-900 başlık/gövde
    textSecondary: '#64748B', // slate-500 ikincil
    textTertiary: '#94A3B8',  // slate-400 placeholder/ipucu

    // Kenarlık
    border: '#E2E8F0',        // slate-200 ince ayraç/kart kenarı
    borderStrong: '#CBD5E1',  // slate-300 input kenarı/odak-dışı
  },

  spacing: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32, xxl: 48 },

  // Modern his için biraz daha yumuşak köşeler
  borderRadius: { sm: 6, md: 10, lg: 14, xl: 20, round: 9999 },

  typography: {
    display:    { fontSize: 30, fontWeight: '700' as const },
    h1:         { fontSize: 24, fontWeight: '700' as const },
    h2:         { fontSize: 20, fontWeight: '700' as const },
    h3:         { fontSize: 17, fontWeight: '600' as const },
    body:       { fontSize: 15, fontWeight: '400' as const },
    bodyStrong: { fontSize: 15, fontWeight: '600' as const },
    caption:    { fontSize: 13, fontWeight: '400' as const, color: '#64748B' },
    label:      { fontSize: 12, fontWeight: '600' as const }, // rozet/etiket
  },

  // iOS gölge + Android elevation birlikte (component'lerde spread edilir)
  shadows: {
    card: {
      shadowColor: '#0F172A',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.06,
      shadowRadius: 8,
      elevation: 2,
    },
    elevated: {
      shadowColor: '#0F172A',
      shadowOffset: { width: 0, height: 8 },
      shadowOpacity: 0.12,
      shadowRadius: 20,
      elevation: 8,
    },
  },
};
