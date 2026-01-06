# Community Service API Dokümantasyonu

Bu doküman, Community Service'in tüm endpoint'lerini ve bunları nasıl test edeceğinizi içermektedir. Tüm istekler **Gateway (http://localhost)** üzerinden atılmalıdır.

---

## 🔐 Kimlik Doğrulama (Önkoşul)

Tüm community endpoint'leri JWT token gerektirir. Önce login olup token almanız gerekiyor.

### 1. Kullanıcı Kaydı (Opsiyonel - yeni kullanıcı için)
```
POST http://localhost/api/v1/auth/register
Content-Type: application/json

{
  "firstName": "Test",
  "lastName": "User",
  "email": "test@example.com",
  "password": "Test123!",
  "phoneNumber": "5551234567"
}
```

### 2. Login ve Token Alma
```
POST http://localhost/api/v1/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Test123!"
}
```

**Yanıt:**
```json
{
  "success": true,
  "message": "Giriş başarılı",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "...",
    "accessTokenExpireDate": "2025-12-30T02:00:00Z"
  }
}
```

> ⚠️ **ÖNEMLİ:** `accessToken` değerini kopyalayın ve aşağıdaki tüm isteklerde kullanın.

---

## 📋 Header Ayarları (Tüm İstekler İçin)

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

---

## 🏠 Topluluk İşlemleri

### 1. Topluluk Oluştur
```
POST http://localhost/api/v1/communities
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "name": "Test Topluluk",
  "description": "Bu bir test topluluğudur",
  "visibility": "PUBLIC"
}
```

**Visibility Değerleri:** `PUBLIC` veya `PRIVATE`

**Beklenen Yanıt (201 Created):**
```json
{
  "success": true,
  "message": "Topluluk başarıyla oluşturuldu",
  "data": {
    "id": 1,
    "name": "Test Topluluk",
    "description": "Bu bir test topluluğudur",
    "visibility": "PUBLIC",
    "ownerId": "user-uuid",
    "memberCount": 1,
    "userRole": "OWNER",
    "createdAt": "2025-12-30T01:00:00",
    "updatedAt": "2025-12-30T01:00:00"
  }
}
```

---

### 2. Topluluklarımı Listele (Sayfalı)
```
GET http://localhost/api/v1/communities?page=0&size=20
Authorization: Bearer <accessToken>
```

**Query Parametreleri:**
| Parametre | Zorunlu | Varsayılan | Açıklama |
|-----------|---------|------------|----------|
| page | Hayır | 0 | Sayfa numarası |
| size | Hayır | 20 | Sayfa başına kayıt |

**Beklenen Yanıt (200 OK):**
```json
{
  "success": true,
  "message": "Başarılı",
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

---

### 3. Topluluk Detayı Getir
```
GET http://localhost/api/v1/communities/{id}
Authorization: Bearer <accessToken>
```

**Örnek:** `GET http://localhost/api/v1/communities/1`

---

### 4. Topluluk Güncelle
```
PUT http://localhost/api/v1/communities/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "name": "Güncellenmiş Topluluk Adı",
  "description": "Güncellenmiş açıklama",
  "visibility": "PRIVATE"
}
```

> **Not:** Sadece OWNER veya ADMIN rolündeki kullanıcılar güncelleme yapabilir.

---

### 5. Topluluk Sil
```
DELETE http://localhost/api/v1/communities/{id}
Authorization: Bearer <accessToken>
```

> **Not:** Sadece OWNER silme yapabilir. Soft delete uygulanır.

---

### 6. Topluluk Ara
```
GET http://localhost/api/v1/communities/search?query=test&page=0&size=20
Authorization: Bearer <accessToken>
```

**Query Parametreleri:**
| Parametre | Zorunlu | Varsayılan | Açıklama |
|-----------|---------|------------|----------|
| query | Evet | - | Arama terimi |
| page | Hayır | 0 | Sayfa numarası |
| size | Hayır | 20 | Sayfa başına kayıt |

---

## 📩 Davet İşlemleri

### 1. Davet Oluştur
```
POST http://localhost/api/v1/communities/{communityId}/invitations
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "maxUses": 10,
  "expiresInHours": 24
}
```

**İstek Gövdesi:**
| Alan | Zorunlu | Açıklama |
|------|---------|----------|
| maxUses | Hayır | Maksimum kullanım sayısı (null = sınırsız) |
| expiresInHours | Hayır | Geçerlilik süresi (saat) (null = süresiz) |

**Beklenen Yanıt (201 Created):**
```json
{
  "success": true,
  "message": "Davet başarıyla oluşturuldu",
  "data": {
    "id": 1,
    "code": "ABC12345",
    "maxUses": 10,
    "currentUses": 0,
    "expiresAt": "2025-12-31T01:00:00",
    "createdBy": "user-uuid",
    "createdAt": "2025-12-30T01:00:00",
    "isActive": true
  }
}
```

---

### 2. Davet Koduyla Topluluğa Katıl
```
POST http://localhost/api/v1/communities/join
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "code": "ABC12345"
}
```

> **Not:** 
> - PUBLIC topluluklar için otomatik onay verilir
> - PRIVATE topluluklar için PENDING statüsü verilir

---

### 3. Topluluk Davetlerini Listele
```
GET http://localhost/api/v1/communities/{communityId}/invitations
Authorization: Bearer <accessToken>
```

> **Not:** Sadece OWNER veya ADMIN görebilir.

---

### 4. Daveti İptal Et
```
DELETE http://localhost/api/v1/communities/{communityId}/invitations/{invitationId}
Authorization: Bearer <accessToken>
```

---

## 👥 Üye Yönetimi

### 1. Üyeleri Listele (Sayfalı)
```
GET http://localhost/api/v1/communities/{communityId}/members?page=0&size=20
Authorization: Bearer <accessToken>
```

**Beklenen Yanıt:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "communityId": 1,
        "userId": "user-uuid",
        "role": "OWNER",
        "status": "APPROVED",
        "joinedAt": "2025-12-30T01:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 2. Bekleyen Üyeleri Listele
```
GET http://localhost/api/v1/communities/{communityId}/members/pending?page=0&size=20
Authorization: Bearer <accessToken>
```

> **Not:** Sadece OWNER veya ADMIN görebilir.

---

### 3. Üye Rolü Değiştir
```
PUT http://localhost/api/v1/communities/{communityId}/members/{memberId}/role
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "role": "ADMIN"
}
```

**Role Değerleri:** `ADMIN` veya `MEMBER`

> **Not:** 
> - Sadece OWNER rol değişikliği yapabilir
> - OWNER rolü atanamaz veya değiştirilemez

---

### 4. Üye Onayla (PENDING → APPROVED)
```
PUT http://localhost/api/v1/communities/{communityId}/members/{memberId}/approve
Authorization: Bearer <accessToken>
```

---

### 5. Üye Reddet
```
PUT http://localhost/api/v1/communities/{communityId}/members/{memberId}/reject
Authorization: Bearer <accessToken>
```

---

### 6. Üye Çıkar
```
DELETE http://localhost/api/v1/communities/{communityId}/members/{memberId}
Authorization: Bearer <accessToken>
```

> **Not:**
> - OWNER veya ADMIN çıkarabilir
> - OWNER çıkarılamaz
> - ADMIN başka ADMIN'i çıkaramaz

---

### 7. Topluluktan Ayrıl
```
DELETE http://localhost/api/v1/communities/{communityId}/members/leave
Authorization: Bearer <accessToken>
```

> **Not:** OWNER ayrılamaz (önce topluluğu silmeli veya sahipliği devretmeli).

---

## 📊 İstatistikler

### 1. Topluluk İstatistiklerini Getir
```
GET http://localhost/api/v1/communities/{communityId}/statistics
Authorization: Bearer <accessToken>
```

> **Not:** Sadece OWNER veya ADMIN görebilir.

**Beklenen Yanıt:**
```json
{
  "success": true,
  "data": {
    "communityId": 1,
    "communityName": "Test Topluluk",
    "totalMembers": 10,
    "pendingMembers": 2,
    "adminCount": 3,
    "activeInvitations": 5,
    "totalInvitationsUsed": 25,
    "createdAt": "2025-12-30T01:00:00",
    "lastActivityAt": "2025-12-30T12:00:00"
  }
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
  "timestamp": "2025-12-30T01:00:00"
}
```

### Yaygın Hata Kodları

| Kod | Açıklama |
|-----|----------|
| 400 | Geçersiz istek (validation hatası) |
| 401 | Yetkisiz (token eksik veya geçersiz) |
| 403 | Erişim reddedildi (yetki yok) |
| 404 | Kaynak bulunamadı |
| 409 | Çakışma (duplicate kayıt) |
| 500 | Sunucu hatası |

### Yaygın Hata Mesajları (Türkçe)

- `"Bu topluluğun üyesi değilsiniz"`
- `"Topluluk bulunamadı"`
- `"Bu isimde bir topluluğunuz zaten var"`
- `"Topluluk güncelleme yetkiniz yok"`
- `"Bu topluluğu silme yetkiniz yok"`
- `"Geçersiz davet kodu"`
- `"Bu davet artık aktif değil"`
- `"Bu davetin süresi dolmuş"`
- `"Bu topluluğun zaten üyesisiniz"`
- `"Üye bulunamadı"`
- `"Topluluk sahibinin rolü değiştirilemez"`
- `"OWNER rolü atanamaz"`
- `"Topluluk sahibi çıkarılamaz"`
- `"Topluluk sahibi olarak ayrılamazsınız"`

---

## 🔄 Test Senaryosu (Önerilen Sıra)

1. **Login** → Token al
2. **Topluluk Oluştur** → id=1 döner
3. **Davet Oluştur** → code döner
4. **(Farklı kullanıcı ile)** Davet Koduyla Katıl
5. **Üyeleri Listele**
6. **Üye Rolü Değiştir** → ADMIN yap
7. **İstatistikleri Görüntüle**
8. **Topluluk Güncelle**
9. **Topluluktan Ayrıl** (normal üye ile)
10. **Topluluk Sil** (owner ile)

---

## 📦 Postman Collection İpuçları

### Environment Variables Oluşturun
```
BASE_URL = http://localhost
ACCESS_TOKEN = (login sonrası otomatik set edilebilir)
COMMUNITY_ID = (oluşturma sonrası set edilebilir)
```

### Pre-request Script (Authorization Header)
```javascript
pm.request.headers.add({
    key: "Authorization",
    value: "Bearer " + pm.environment.get("ACCESS_TOKEN")
});
```

### Test Script (Token'ı Kaydet)
```javascript
// Login response'ından token'ı kaydet
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("ACCESS_TOKEN", jsonData.data.accessToken);
}
```
