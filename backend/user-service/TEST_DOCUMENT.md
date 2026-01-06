# User Service Test Dokümanı

Bu doküman, User Service'in tüm endpoint'lerini ve her endpoint için test edilmesi gereken senaryoları içermektedir.

**Base URL:** `http://localhost` (Gateway üzerinden)

---

## 📋 Test Özeti

| Endpoint | Toplam Test Case |
|----------|------------------|
| POST /api/v1/auth/register | 8 |
| POST /api/v1/auth/login | 6 |
| GET /api/v1/auth/verify/{token} | 4 |
| POST /api/v1/auth/refresh | 4 |
| POST /api/v1/auth/logout | 3 |
| POST /api/v1/auth/forgot-password | 4 |
| POST /api/v1/auth/reset-password | 5 |
| PUT /api/v1/auth/activate | 5 |
| GET /api/v1/users/me | 3 |
| PUT /api/v1/users/me | 5 |
| POST /api/v1/users/change-password | 6 |
| DELETE /api/v1/users/me | 3 |
| **TOPLAM** | **56** |

---

## 🔐 AUTH: Kullanıcı Kaydı

### `POST /api/v1/auth/register`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Başarılı kayıt | `{"firstName":"Test","lastName":"User","email":"newuser@test.com","password":"Test1234!"}` | 200 OK, `"Kayıt başarılı."` |
| 2 | ❌ Email boş | `{"firstName":"Test","lastName":"User","email":"","password":"Test1234!"}` | 400, `"email: E-posta alanı zorunludur"` |
| 3 | ❌ Email geçersiz format | `{"firstName":"Test","lastName":"User","email":"invalid-email","password":"Test1234!"}` | 400, `"email: Geçerli bir e-posta adresi giriniz"` |
| 4 | ❌ Email zaten kayıtlı | Mevcut email ile kayıt | 409, `"Email zaten kayıtlı."` |
| 5 | ❌ Parola çok kısa | `{"firstName":"Test","lastName":"User","email":"new@test.com","password":"123"}` | 400, `"password: Parola 8 ile 128 karakter arasında olmalıdır"` |
| 6 | ❌ Ad boş | `{"firstName":"","lastName":"User","email":"new@test.com","password":"Test1234!"}` | 400, `"firstName: Ad alanı zorunludur"` |
| 7 | ❌ Soyad boş | `{"firstName":"Test","lastName":"","email":"new@test.com","password":"Test1234!"}` | 400, `"lastName: Soyad alanı zorunludur"` |
| 8 | ❌ Ad 50 karakterden uzun | firstName: 51 karakter | 400, `"firstName: Ad en fazla 50 karakter olabilir"` |

---

## 🔐 AUTH: Kullanıcı Girişi

### `POST /api/v1/auth/login`

**Önkoşul:** Kayıtlı ve aktif kullanıcı olmalı

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Başarılı giriş | `{"email":"existing@test.com","password":"Test1234!"}` | 200 OK, accessToken + refreshToken döner |
| 2 | ❌ Yanlış parola | `{"email":"existing@test.com","password":"WrongPass"}` | 401, `"E-posta veya parola hatalı."` |
| 3 | ❌ Kayıtlı olmayan email | `{"email":"notexist@test.com","password":"Test1234!"}` | 401, `"E-posta veya parola hatalı."` |
| 4 | ❌ Email boş | `{"email":"","password":"Test1234!"}` | 400, `"email: E-posta alanı zorunludur"` |
| 5 | ❌ Pasif hesap ile giriş | Silinmiş hesabın email'i | 401, `"Hesap aktif değil."` |
| 6 | ❌ Doğrulanmamış hesap | Doğrulanmamış email | 401, `"Lütfen önce e-posta adresinizi doğrulayın."` |

---

## 🔐 AUTH: E-posta Doğrulama

### `GET /api/v1/auth/verify/{token}`

| # | Test Case | URL | Beklenen Sonuç |
|---|-----------|-----|----------------|
| 1 | ✅ Geçerli token | `/api/v1/auth/verify/valid-token-uuid` | 200 OK, `"E-posta doğrulandı."` |
| 2 | ❌ Geçersiz token | `/api/v1/auth/verify/invalid-token` | 400, Hata mesajı |
| 3 | ❌ Süresi dolmuş token | Expired token | 400, `"Sıfırlama bağlantısı geçersiz veya süresi dolmuş."` |
| 4 | ❌ Zaten doğrulanmış | Tekrar aynı token | 400, Hata mesajı |

---

## 🔐 AUTH: Token Yenileme

### `POST /api/v1/auth/refresh`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Geçerli refresh token | `{"refreshToken":"valid-uuid"}` | 200 OK, yeni accessToken döner |
| 2 | ❌ Geçersiz refresh token | `{"refreshToken":"invalid-token"}` | 401, `"Oturum yenileme başarısız."` |
| 3 | ❌ Refresh token boş | `{"refreshToken":""}` | 400, `"refreshToken: Yenileme token'ı zorunludur"` |
| 4 | ❌ Kullanılmış refresh token | Logout sonrası eski token | 401, `"Oturum yenileme başarısız."` |

---

## 🔐 AUTH: Çıkış

### `POST /api/v1/auth/logout`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Başarılı çıkış | `{"refreshToken":"valid-uuid"}` | 200 OK, `"Oturum kapatıldı."` |
| 2 | ❌ Geçersiz refresh token | `{"refreshToken":"invalid"}` | 200 OK (idempotent) |
| 3 | ❌ Refresh token boş | `{"refreshToken":""}` | 400, validation hatası |

---

## 🔐 AUTH: Parola Sıfırlama İsteği

### `POST /api/v1/auth/forgot-password`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Kayıtlı email | `{"email":"existing@test.com"}` | 200 OK, `"Parola sıfırlama bağlantısı..."` |
| 2 | ❌ Kayıtlı olmayan email | `{"email":"notexist@test.com"}` | 404, `"Email kayıtlı değil."` |
| 3 | ❌ Email boş | `{"email":""}` | 400, `"email: E-posta alanı zorunludur"` |
| 4 | ❌ Geçersiz email formatı | `{"email":"invalid"}` | 400, `"email: Geçerli bir e-posta adresi giriniz"` |

---

## 🔐 AUTH: Parola Sıfırlama

### `POST /api/v1/auth/reset-password`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Geçerli token ve parola | `{"resetToken":"valid","newPassword":"NewPass123!"}` | 200 OK, `"Parola başarıyla sıfırlandı."` |
| 2 | ❌ Geçersiz token | `{"resetToken":"invalid","newPassword":"NewPass123!"}` | 400, `"Sıfırlama bağlantısı geçersiz..."` |
| 3 | ❌ Süresi dolmuş token | Expired token | 400, `"Sıfırlama bağlantısı geçersiz..."` |
| 4 | ❌ Yeni parola çok kısa | `{"resetToken":"valid","newPassword":"123"}` | 400, `"newPassword: Yeni parola 8 ile 128 karakter..."` |
| 5 | ❌ Token boş | `{"resetToken":"","newPassword":"NewPass123!"}` | 400, `"resetToken: Sıfırlama token'ı zorunludur"` |

---

## 🔐 AUTH: Hesap Aktifleştirme

### `PUT /api/v1/auth/activate`

**Önkoşul:** Önceden silinmiş (soft-delete) hesap olmalı

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Silinmiş hesabı aktifleştir | `{"email":"deleted@test.com","password":"Test1234!"}` | 200 OK, `"Hesap başarıyla aktifleştirildi."` |
| 2 | ❌ Zaten aktif hesap | `{"email":"active@test.com","password":"Test1234!"}` | 400, `"Hesap zaten aktif..."` |
| 3 | ❌ Yanlış parola | `{"email":"deleted@test.com","password":"Wrong"}` | 401, `"E-posta veya parola hatalı."` |
| 4 | ❌ Kayıtlı olmayan email | `{"email":"notexist@test.com","password":"Test1234!"}` | 404, hata mesajı |
| 5 | ❌ Email boş | `{"email":"","password":"Test1234!"}` | 400, validation hatası |

---

## 👤 USER: Profil Getir

### `GET /api/v1/users/me`

**Header:** `Authorization: Bearer <accessToken>`

| # | Test Case | Headers | Beklenen Sonuç |
|---|-----------|---------|----------------|
| 1 | ✅ Geçerli token ile | Valid Bearer token | 200 OK, kullanıcı bilgileri döner |
| 2 | ❌ Token yok | Authorization header yok | 401, `"Bu işlemi gerçekleştirmek için..."` |
| 3 | ❌ Geçersiz token | `Authorization: Bearer invalid` | 401, Yetkisiz |

---

## 👤 USER: Profil Güncelle

### `PUT /api/v1/users/me`

**Header:** `Authorization: Bearer <accessToken>`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Ad güncelle | `{"firstName":"Yeni Ad"}` | 200 OK, güncellenen profil |
| 2 | ✅ Soyad güncelle | `{"lastName":"Yeni Soyad"}` | 200 OK, güncellenen profil |
| 3 | ✅ Profil resmi ekle | `{"profileImage":"https://..."}` | 200 OK, güncellenen profil |
| 4 | ❌ Ad 50 karakterden uzun | firstName: 51 karakter | 400, `"firstName: Ad en fazla 50 karakter olabilir"` |
| 5 | ❌ Token yok | Authorization header yok | 401, Yetkisiz |

---

## 👤 USER: Parola Değiştir

### `POST /api/v1/users/change-password`

**Header:** `Authorization: Bearer <accessToken>`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Başarılı değişiklik | `{"oldPassword":"Current","newPassword":"NewPass123!"}` | 200 OK, `"Parola başarıyla değiştirildi."` |
| 2 | ❌ Eski parola yanlış | `{"oldPassword":"Wrong","newPassword":"NewPass123!"}` | 400, `"Eski parola hatalı."` |
| 3 | ❌ Yeni parola aynı | `{"oldPassword":"Current","newPassword":"Current"}` | 400, `"Yeni parola eski parolayla aynı olamaz."` |
| 4 | ❌ Yeni parola çok kısa | `{"oldPassword":"Current","newPassword":"12"}` | 400, validation hatası |
| 5 | ❌ Eski parola boş | `{"oldPassword":"","newPassword":"NewPass123!"}` | 400, `"oldPassword: Mevcut parola alanı zorunludur"` |
| 6 | ❌ Token yok | Authorization header yok | 401, Yetkisiz |

---

## 👤 USER: Hesap Sil

### `DELETE /api/v1/users/me`

**Header:** `Authorization: Bearer <accessToken>`

| # | Test Case | Headers | Beklenen Sonuç |
|---|-----------|---------|----------------|
| 1 | ✅ Başarılı silme | Valid Bearer token | 200 OK, `"Hesap başarıyla silindi."` |
| 2 | ❌ Token yok | Authorization header yok | 401, Yetkisiz |
| 3 | ✅ Silme sonrası login | Silinen hesapla login | 401, `"Hesap aktif değil."` |

---

## 🔄 Entegrasyon Test Senaryoları

### Senaryo 1: Tam Kayıt ve Giriş Akışı
1. Register → 200 OK
2. Login → 401 (email doğrulanmamış - eğer email doğrulama aktifse)
3. Verify email → 200 OK
4. Login → 200 OK, token alınır
5. Me → 200 OK, profil görüntülenir

### Senaryo 2: Parola Sıfırlama Akışı
1. Forgot password → 200 OK
2. Reset password (geçersiz token) → 400
3. Reset password (geçerli token) → 200 OK
4. Login (eski parola) → 401
5. Login (yeni parola) → 200 OK

### Senaryo 3: Hesap Silme ve Aktifleştirme
1. Login → Token al
2. Delete me → 200 OK
3. Login → 401 (hesap pasif)
4. Activate → 200 OK
5. Login → 200 OK

### Senaryo 4: Token Yenileme
1. Login → accessToken + refreshToken al
2. Access token expired sonrası → 401
3. Refresh → Yeni accessToken al
4. Me (yeni token ile) → 200 OK

---

## ⚠️ Test Notları

1. **Sıralı Testler:** Bazı testler sıralı yapılmalı (örn: register → login → me)
2. **Temizlik:** Her test sonrası oluşturulan verileri temizleyin
3. **Rate Limiting:** Çok fazla istek yaparsanız 429 alabilirsiniz
4. **Token Süresi:** Access token 15 dakikada expire olur
5. **Email Doğrulama:** Prod ortamında email doğrulama aktif olabilir
