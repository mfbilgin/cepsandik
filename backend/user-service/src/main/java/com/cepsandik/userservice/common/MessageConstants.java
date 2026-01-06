package com.cepsandik.userservice.common;

public final class MessageConstants {

    private MessageConstants() {
    }

    // 🔐 Authentication
    public static final String REGISTER_SUCCESS = "Kayıt başarılı.";
    public static final String LOGIN_SUCCESS = "Giriş başarılı.";
    public static final String LOGOUT_SUCCESS = "Oturum kapatıldı.";
    public static final String REFRESH_SUCCESS = "Oturum yenilendi.";
    public static final String EMAIL_VERIFIED = "E-posta doğrulandı.";
    public static final String PASSWORD_RESET_SENT = "Parola sıfırlama bağlantısı e-posta adresinize gönderildi.";
    public static final String PASSWORD_RESET_SUCCESS = "Parola başarıyla sıfırlandı.";
    public static final String INVALID_CREDENTIALS = "Email veya parola hatalı";
    public static final String ACCOUNT_PASSIVE = "Hesap aktif değil.";
    public static final String ACCOUNT_ALREADY_ACTIVE = "Hesap zaten aktif. E-posta ve parolanızla giriş yapabilirsiniz.";
    public static final String EMAIL_NOT_VERIFIED = "Lütfen önce e-posta adresinizi doğrulayın.";
    public static final String INVALID_REFRESH_TOKEN = "Oturum yenileme başarısız.";
    public static final String ACCOUNT_LOCKED = "Çok fazla başarısız deneme. Lütfen %d dakika bekleyin.";
    public static final String PASSWORDS_SAME = "Yeni parola eski parolayla aynı olamaz.";
    public static final String INCORRECT_PASSWORD = "Eski parola hatalı.";
    public static final String UNAUTHENTICATED_REQUEST = "Bu işlemi gerçekleştirmek için lütfen giriş yapınız.";
    // 👤 User
    public static final String ACCOUNT_IS_SOFT_DELETED = "Bu e-posta daha önce bir hesapla ilişkilendirildi. Hesabınızı etkinleştirmeyi deneyebilirsiniz.";
    public static final String PROFILE_FETCHED = "Profil bilgileri getirildi.";
    public static final String PROFILE_UPDATED = "Profil başarıyla güncellendi.";
    public static final String PASSWORD_CHANGED = "Parola başarıyla değiştirildi.";
    public static final String EMAIL_EXISTS = "Email zaten kayıtlı.";
    public static final String EMAIL_NOT_EXISTS = "Email kayıtlı değil.";
    public static final String ACCOUNT_VERIFIED = "Hesap başarıyla doğrulandı";
    public static final String USER_NOT_FOUND = "Kullanıcı bulunamadı";
    public static final String INVALID_TOKEN = "Bağlantı geçersiz veya süresi dolmuş.";
    public static final String INVALID_RESET_TOKEN = "Sıfırlama bağlantısı geçersiz veya süresi dolmuş.";
    public static final String INVALID_2FA_TOKEN = "Oturum süresi doldu. Lütfen tekrar giriş yapın.";
    public static final String ACCOUNT_DELETED = "Hesap başarıyla silindi.";
    public static final String ACCOUNT_ACTIVATED = "Hesap başarıyla aktifleştirildi.";

    // ⚠️ Common errors
    public static final String ACCESS_DENIED = "Bu işlem için yetkiniz yok.";
    public static final String UNKNOWN_ERROR = "Beklenmeyen bir hata oluştu. EC: ";
    public static final String RATE_LIMIT_EXCEEDED = "Çok fazla istek gönderdiniz. Lütfen bekleyin.";
    public static final String SERVER_ERROR = "Sunucu hatası. Lütfen daha sonra tekrar deneyin.";

    // ✅ Validation messages (for reference, actual messages are in DTOs)
    public static final String FIELD_REQUIRED = "alanı zorunludur";
    public static final String INVALID_EMAIL = "Geçerli bir e-posta adresi giriniz";
    public static final String PASSWORD_LENGTH = "Parola 8 ile 128 karakter arasında olmalıdır";
    public static final String NAME_MAX_LENGTH = "en fazla 50 karakter olabilir";
}
