# User Service API Dokümantasyonu

Bu doküman, User Service'in tüm endpoint'lerini ve bunları nasıl test edeceğinizi içermektedir. Tüm    istekler **Gateway (http://localhost)** üzerinden atılmalıdır.

---

## 📋 Endpoint Özeti

| Kategori | Endpoint | Metod | Auth Gerekli |
|----------|----------|-------|--------------|
| **Kimlik Doğrulama** | | | |
| Kayıt | `/api/v1/auth/register` | POST | ❌ |
| Login | `/api/v1/auth/login` | POST | ❌ |
| E-posta Doğrula | `/api/v1/auth/verify/{token}` | GET | ❌ |
| Token Yenile | `/api/v1/auth/refresh` | POST | ❌ |
| Çıkış | `/api/v1/auth/logout` | POST | ❌ |
| Parola Sıfırlama İste | `/api/v1/auth/forgot-password` | POST | ❌ |
| Parola Sıfırla | `/api/v1/auth/reset-password` | POST | ❌ |
| Hesap Aktifleştir | `/api/v1/auth/activate` | PUT | ❌ |
| **Kullanıcı** | | | |
| Profil Getir | `/api/v1/users/me` | GET | ✅ |
| Profil Güncelle | `/api/v1/users/me` | PUT | ✅ |
| Parola Değiştir | `/api/v1/users/change-password` | POST | ✅ |
| Hesap Sil | `/api/v1/users/me` | DELETE | ✅ |

---

## 🎭 Platform Rolleri

Her kullanıcı bir platform rolüne sahiptir. Bu rol topluluk ve seçim rollerinden bağımsızdır.

| Rol | Açıklama |
|-----|----------|
| `USER` | Normal kullanıcı (varsayılan) |
| `MODERATOR` | İçerik denetimi yapabilir |
| `ADMIN` | Platform yöneticisi - tam yetki |

### JWT Token'da Rol Bilgisi
Login sonrası dönen `accessToken` içinde `platformRole` claim'i bulunur:

```json
{
  "sub": "user-uuid",
  "email": "test@example.com",
  "platformRole": "USER",
  "exp": 1735520400
}
```

### Gateway Headers
Gateway, backend servislere şu header'ı iletir:
```
X-Platform-Role: USER
```

> **Not:** Rol değişikliği şu an için sadece veritabanından manuel yapılabilir. İleride admin paneli eklenecektir.

---

## 🔐 Kimlik Doğrulama (Authentication)

### 1. Kullanıcı Kaydı
```
POST http://localhost/api/v1/auth/register
Content-Type: application/json

{
  "firstName": "Test",
  "lastName": "User",
  "email": "test@example.com",
  "password": "Test123!"
}
```

**İstek Gövdesi:**
| Alan | Zorunlu | Min | Max | Açıklama |
|------|---------|-----|-----|----------|
| firstName | ✅ | - | 50 | Kullanıcı adı |
| lastName | ✅ | - | 50 | Kullanıcı soyadı |
| email | ✅ | - | 255 | E-posta adresi |
| password | ✅ | 8 | 128 | Parola |

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Kayıt başarılı.",
  "data": {
    "id": "e9514fc1-48b4-46fd-9b48-2e71059863aa",
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com",
    "verified": false,
    "profileImage": null
  },
  "timestamp": "2025-12-30T01:00:00Z"
}
```

---

### 2. Kullanıcı Girişi (Login)
```
POST http://localhost/api/v1/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Test123!"
}
```

**İstek Gövdesi:**
| Alan | Zorunlu | Açıklama |
|------|---------|----------|
| email | ✅ | Kayıtlı e-posta adresi |
| password | ✅ | Parola |

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Giriş başarılı.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "tokenType": "Bearer",
    "accessTokenExpireDate": 1735520400000
  },
  "timestamp": "2025-12-30T01:00:00Z"
}
```

> ⚠️ **ÖNEMLİ:** `accessToken` değerini kopyalayın ve korumalı endpoint'lerde kullanın.

---

### 3. E-posta Doğrulama
```
GET http://localhost/api/v1/auth/verify/{token}
```

**Path Parametreleri:**
| Parametre | Açıklama |
|-----------|----------|
| token | E-posta ile gönderilen doğrulama token'ı |

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "E-posta doğrulandı.",
  "data": "Success"
}
```

---

### 4. Token Yenileme
```
POST http://localhost/api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Oturum yenilendi.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "new-refresh-token-uuid",
    "tokenType": "Bearer",
    "accessTokenExpireDate": 1735524000000
  }
}
```

---

### 5. Çıkış (Logout)
```
POST http://localhost/api/v1/auth/logout
Content-Type: application/json

{
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Oturum kapatıldı.",
  "data": null
}
```

---

### 6. Parola Sıfırlama İsteği
```
POST http://localhost/api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "test@example.com"
}
```

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Parola sıfırlama bağlantısı e-posta adresinize gönderildi.",
  "data": null
}
```

---

### 7. Parola Sıfırlama (Yeni Parola Belirleme)
```
POST http://localhost/api/v1/auth/reset-password
Content-Type: application/json

{
  "resetToken": "reset-token-from-email",
  "newPassword": "NewSecurePass123!"
}
```

**İstek Gövdesi:**
| Alan | Zorunlu | Min | Max | Açıklama |
|------|---------|-----|-----|----------|
| resetToken | ✅ | - | - | E-posta ile gönderilen sıfırlama token'ı |
| newPassword | ✅ | 8 | 128 | Yeni parola |

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Parola başarıyla sıfırlandı.",
  "data": null
}
```

---

### 8. Hesap Aktifleştirme
```
PUT http://localhost/api/v1/auth/activate
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Test123!"
}
```

> **Not:** Soft-delete edilmiş hesapları yeniden aktifleştirmek için kullanılır.

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Hesap başarıyla aktifleştirildi.",
  "data": null
}
```

---

## 👤 Kullanıcı İşlemleri (User)

> ⚠️ Bu endpoint'ler için `Authorization` header'ı gereklidir.

### Header Ayarları
```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

---

### 1. Profil Bilgilerini Getir
```
GET http://localhost/api/v1/users/me
Authorization: Bearer <accessToken>
```

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Profil bilgileri getirildi.",
  "data": {
    "id": "e9514fc1-48b4-46fd-9b48-2e71059863aa",
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com",
    "verified": true,
    "profileImage": null
  }
}
```

---

### 2. Profil Güncelle
```
PUT http://localhost/api/v1/users/me
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "firstName": "Güncel",
  "lastName": "İsim",
  "profileImage": "https://example.com/avatar.jpg"
}
```

**İstek Gövdesi:**
| Alan | Zorunlu | Max | Açıklama |
|------|---------|-----|----------|
| firstName | ❌ | 50 | Yeni ad |
| lastName | ❌ | 50 | Yeni soyad |
| profileImage | ❌ | 255 | Profil resmi URL'i |

> **Not:** Sadece göndermek istediğiniz alanları ekleyin.

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Profil başarıyla güncellendi.",
  "data": {
    "id": "e9514fc1-48b4-46fd-9b48-2e71059863aa",
    "firstName": "Güncel",
    "lastName": "İsim",
    "email": "test@example.com",
    "verified": true,
    "profileImage": "https://example.com/avatar.jpg"
  }
}
```

---

### 3. Parola Değiştir
```
POST http://localhost/api/v1/users/change-password
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "oldPassword": "Test123!",
  "newPassword": "NewSecure456!"
}
```

**İstek Gövdesi:**
| Alan | Zorunlu | Min | Max | Açıklama |
|------|---------|-----|-----|----------|
| oldPassword | ✅ | - | - | Mevcut parola |
| newPassword | ✅ | 8 | 128 | Yeni parola |

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Parola başarıyla değiştirildi.",
  "data": null
}
```

---

### 4. Hesap Sil
```
DELETE http://localhost/api/v1/users/me
Authorization: Bearer <accessToken>
```

> **Not:** Soft delete uygulanır. Hesap daha sonra `/api/v1/auth/activate` ile tekrar aktifleştirilebilir.

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Hesap başarıyla silindi.",
  "data": null
}
```

---

## ❌ Hata Yanıtları

Tüm hata yanıtları şu formatta döner:

```json
{
  "success": false,
  "message": "Hata mesajı burada",
  "data": null,
  "timestamp": "2025-12-30T01:00:00Z"
}
```

### Yaygın Hata Kodları

| Kod | Açıklama |
|-----|----------|
| 400 | Geçersiz istek (validation hatası) |
| 401 | Yetkisiz (token eksik veya geçersiz) |
| 403 | Erişim reddedildi |
| 404 | Kaynak bulunamadı |
| 409 | Çakışma (duplicate kayıt) |
| 429 | Çok fazla istek |
| 500 | Sunucu hatası |

### Yaygın Hata Mesajları (Türkçe)

| Mesaj | Açıklama |
|-------|----------|
| `"Email veya parola hatalı"` | Login başarısız |
| `"Email zaten kayıtlı."` | Kayıt sırasında duplicate |
| `"Email kayıtlı değil."` | Parola sıfırlama için email bulunamadı |
| `"Hesap aktif değil."` | Silinen hesapla giriş denemesi |
| `"Lütfen önce e-posta adresinizi doğrulayın."` | Doğrulanmamış hesapla giriş |
| `"Oturum yenileme başarısız."` | Geçersiz refresh token |
| `"Yeni parola eski parolayla aynı olamaz."` | Parola değişikliği |
| `"Eski parola hatalı."` | Yanlış mevcut parola |
| `"Kullanıcı bulunamadı"` | User not found |
| `"Sıfırlama bağlantısı geçersiz veya süresi dolmuş."` | Reset token invalid |
| `"Hesap zaten aktif."` | Aktif hesabı aktifleştirme denemesi |
| `"Bu işlemi gerçekleştirmek için lütfen giriş yapınız."` | Yetkisiz erişim |
| `"Bu işlem için yetkiniz yok."` | Access denied |
| `"Çok fazla istek gönderdiniz. Lütfen bekleyin."` | Rate limit aşıldı |

---

## 🔄 Test Senaryosu (Önerilen Sıra)

### Temel Akış
1. **Register** → Yeni kullanıcı oluştur
2. **Login** → Token al
3. **Me (GET)** → Profil bilgilerini getir
4. **Me (PUT)** → Profil güncelle
5. **Change Password** → Parola değiştir
6. **Logout** → Çıkış yap
7. **Login** → Yeni parolayla tekrar giriş

### Parola Sıfırlama Akışı
1. **Forgot Password** → Sıfırlama e-postası iste
2. E-postadaki token'ı al
3. **Reset Password** → Yeni parola belirle
4. **Login** → Yeni parolayla giriş

### Hesap Silme/Aktifleştirme Akışı
1. **Login** → Token al
2. **Delete Me** → Hesabı sil
3. **Login** → "Hesap aktif değil" hatası alırsınız
4. **Activate** → Hesabı tekrar aktifleştir
5. **Login** → Başarılı giriş

---

## 📦 Postman Collection İpuçları

### Environment Variables Oluşturun
```
BASE_URL = http://localhost
ACCESS_TOKEN = (login sonrası otomatik set)
REFRESH_TOKEN = (login sonrası otomatik set)
USER_EMAIL = test@example.com
USER_PASSWORD = Test123!
```

### Pre-request Script (Korumalı Endpoint'ler İçin)
```javascript
pm.request.headers.add({
    key: "Authorization",
    value: "Bearer " + pm.environment.get("ACCESS_TOKEN")
});
```

### Test Script (Login Response'ından Token Kaydet)
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("ACCESS_TOKEN", jsonData.data.accessToken);
    pm.environment.set("REFRESH_TOKEN", jsonData.data.refreshToken);
}
```

### Test Script (Register Sonrası)
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("USER_ID", jsonData.data.id);
}
```
