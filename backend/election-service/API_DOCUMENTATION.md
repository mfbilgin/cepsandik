# Election Service – API Dokümantasyonu

## Genel Bilgi

**Base URL:** `https://api.cepsandik.com/election` (production) veya `http://localhost:8083` (lokal)

**Kimlik Doğrulama:** Tüm istekler API Gateway üzerinden yönlendirilir. Gateway JWT doğrulaması yapar ve aşağıdaki header'ları ekler:
- `X-User-Id`: Kullanıcı UUID'si
- `X-Platform-Role`: Platform rolü (USER, MODERATOR, ADMIN)

---

## 1. Seçim Yönetimi

### 1.1 Seçim Oluşturma

```
POST /api/v1/elections
```

**Request Body:**
```json
{
  "title": "Yönetim Kurulu Seçimi 2026",
  "description": "Topluluk yönetim kurulu üyelerinin seçimi",
  "communityId": 1,
  "type": "SINGLE_CHOICE",
  "participantType": "ALL_MEMBERS",
  "maxSelections": 1,
  "startTime": "2026-03-01T09:00:00",
  "endTime": "2026-03-02T18:00:00",
  "resultsPublic": true,
  "anonymousVoting": true
}
```

**Başarılı Yanıt (201):**
```json
{
  "success": true,
  "message": "Seçim başarıyla oluşturuldu",
  "data": {
    "id": 1,
    "title": "Yönetim Kurulu Seçimi 2026",
    "status": "DRAFT",
    "type": "SINGLE_CHOICE",
    "participantType": "ALL_MEMBERS",
    "candidateCount": 0,
    "createdAt": "2026-02-17T10:00:00"
  }
}
```

### 1.2 Seçim Detayı

```
GET /api/v1/elections/{id}
```

**Başarılı Yanıt (200):** Yukarıdaki yapıda, `candidates` listesi dahil.

### 1.3 Seçim Güncelleme

```
PUT /api/v1/elections/{id}
```

> ⚠️ Sadece DRAFT durumundaki seçimler güncellenebilir.

**Request Body:** Seçim oluşturma ile aynı yapı.

### 1.4 Seçim Silme

```
DELETE /api/v1/elections/{id}
```

> ⚠️ Soft delete uygular. Sadece seçim sahibi silebilir.

### 1.5 Durum Geçişleri

| Endpoint | Geçiş | Açıklama |
|----------|-------|----------|
| `POST /api/v1/elections/{id}/publish` | DRAFT → SCHEDULED | Seçimi yayınla |
| `POST /api/v1/elections/{id}/start` | SCHEDULED → ACTIVE | Seçimi başlat |
| `POST /api/v1/elections/{id}/end` | ACTIVE → CLOSED | Seçimi bitir |
| `POST /api/v1/elections/{id}/cancel` | * → CANCELLED | Seçimi iptal et |

```
Durum Makinesi:
DRAFT → SCHEDULED → ACTIVE → CLOSED → ARCHIVED
                ↗                ↗
           CANCELLED        CANCELLED
```

---

## 2. Aday Yönetimi

### 2.1 Aday Ekleme

```
POST /api/v1/elections/{id}/candidates
```

> ⚠️ Sadece DRAFT durumundaki seçimlere aday eklenebilir.

**Request Body:**
```json
{
  "name": "Ahmet Yılmaz",
  "description": "10 yıllık topluluk üyesi",
  "imageUrl": "https://example.com/photo.jpg",
  "displayOrder": 1
}
```

### 2.2 Aday Güncelleme / Silme

```
PUT /api/v1/elections/{id}/candidates/{candidateId}
DELETE /api/v1/elections/{id}/candidates/{candidateId}
```

---

## 3. Erişim Kodu Yönetimi

### 3.1 Erişim Kodu Oluşturma

```
POST /api/v1/elections/{id}/access-codes
```

**Request Body:**
```json
{
  "maxUses": 100,
  "expiresAt": "2026-03-01T09:00:00"
}
```

**Yanıt:** 6 haneli alfanumerik kod (ör: `AB12CD`).

### 3.2 Erişim Kodlarını Listeleme / Silme

```
GET /api/v1/elections/{id}/access-codes
DELETE /api/v1/elections/{id}/access-codes/{codeId}
```

---

## 4. Oy Verme

### 4.1 Erişim Kodu Doğrulama

```
POST /api/v1/elections/{electionId}/votes/verify-access
```

**Request Body:**
```json
{
  "code": "AB12CD"
}
```

**Başarılı Yanıt (200):**
```json
{
  "success": true,
  "message": "Erişim kodu doğrulandı",
  "data": {
    "electionId": 1,
    "electionTitle": "Yönetim Kurulu Seçimi 2026",
    "status": "ACTIVE",
    "type": "SINGLE_CHOICE",
    "candidateCount": 3,
    "candidates": [
      {"id": 1, "name": "Ahmet Yılmaz", "description": "..."},
      {"id": 2, "name": "Ayşe Demir", "description": "..."}
    ]
  }
}
```

### 4.2 Vote Token Alma

```
POST /api/v1/elections/{electionId}/votes/token
Header: X-User-Id: {userId}
```

**Başarılı Yanıt (201):**
```json
{
  "success": true,
  "data": {
    "token": "550e8400-e29b-41d4-a716-446655440000",
    "electionId": 1,
    "electionTitle": "Yönetim Kurulu Seçimi 2026",
    "isUsed": false
  }
}
```

> ℹ️ İdempotent: Aynı kullanıcı tekrar isterse mevcut token döner.

### 4.3 Oy Kullanma

```
POST /api/v1/elections/{electionId}/votes
```

**Request Body:**
```json
{
  "voteToken": "550e8400-e29b-41d4-a716-446655440000",
  "candidateId": 2
}
```

**İlk Oy (201):**
```json
{
  "success": true,
  "message": "Oyunuz başarıyla kaydedildi",
  "data": {
    "electionId": 1,
    "candidateId": 2,
    "candidateName": "Ayşe Demir",
    "alreadyVoted": false,
    "votedAt": "2026-03-01T14:30:00"
  }
}
```

**Tekrar İstek (200):**
```json
{
  "success": true,
  "message": "Daha önce oy kullanılmış",
  "data": {
    "alreadyVoted": true,
    "votedAt": "2026-03-01T14:30:00"
  }
}
```

### 4.4 Oy Durumu Sorgulama

```
GET /api/v1/elections/{electionId}/votes/my-status
Header: X-User-Id: {userId}
```

### 4.5 Seçim İstatistikleri

```
GET /api/v1/elections/{electionId}/votes/stats
Header: X-User-Id: {userId}
```

> ⚠️ Sadece CLOSED/ARCHIVED seçimler veya seçim sahibi görüntüleyebilir.

**Başarılı Yanıt (200):**
```json
{
  "success": true,
  "data": {
    "electionId": 1,
    "electionTitle": "Yönetim Kurulu Seçimi 2026",
    "status": "CLOSED",
    "totalVotes": 150,
    "candidateStats": [
      {"candidateId": 2, "candidateName": "Ayşe Demir", "voteCount": 85, "percentage": 56.67},
      {"candidateId": 1, "candidateName": "Ahmet Yılmaz", "voteCount": 65, "percentage": 43.33}
    ]
  }
}
```

---

## 5. Kullanıcı Seçimleri

### 5.1 Aktif Seçimlerim

```
GET /api/v1/elections/my/active
Header: X-User-Id: {userId}
```

Kullanıcının oluşturduğu veya dahil olduğu aktif/scheduled seçimleri listeler.

### 5.2 Katılım Geçmişim

```
GET /api/v1/elections/my/history
Header: X-User-Id: {userId}
```

Kullanıcının geçmişte katıldığı kapanmış seçimleri listeler.

---

## 6. Topluluk Seçimleri

```
GET /api/v1/communities/{communityId}/elections?page=0&size=20
```

---

## Hata Yanıtları

Tüm hata yanıtları aşağıdaki formattadır:

```json
{
  "success": false,
  "message": "Hata açıklaması"
}
```

| HTTP Kodu | Açıklama |
|-----------|----------|
| 400 | Geçersiz istek (eksik alan, yanlış format) |
| 403 | Yetkisiz erişim |
| 404 | Kaynak bulunamadı |
| 409 | Çakışma (duplicate kayıt) |
| 500 | Sunucu hatası |

## Seçim Türleri

| Tür | Açıklama |
|-----|----------|
| `SINGLE_CHOICE` | Tek aday seçimi |
| `MULTIPLE_CHOICE` | Birden fazla aday seçimi |
| `RANKED_CHOICE` | Sıralı tercih oylaması |

## Katılımcı Türleri

| Tür | Açıklama |
|-----|----------|
| `ALL_MEMBERS` | Tüm topluluk üyeleri |
| `SELECTED_MEMBERS` | Seçilmiş üyeler |
| `ACCESS_CODE` | Erişim kodu ile |
| `PUBLIC` | Herkese açık |
