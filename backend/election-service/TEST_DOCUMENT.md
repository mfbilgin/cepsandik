# Election Service – Test Dokümantasyonu

## Test Özeti

| Kategori | Endpoint Sayısı | Test Case Sayısı |
|----------|-----------------|------------------|
| Seçim CRUD | 4 | 16 |
| Durum Yönetimi | 4 | 14 |
| Aday Yönetimi | 3 | 10 |
| Erişim Kodu | 3 | 9 |
| Oy Verme | 5 | 22 |
| Kullanıcı Seçimleri | 2 | 6 |
| Topluluk Seçimleri | 1 | 3 |
| **TOPLAM** | **22** | **80** |

---

## 1. Seçim CRUD (16 test)

### POST /api/v1/elections – Seçim Oluşturma (5 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Geçerli bilgilerle seçim oluşturma | 201, DRAFT durumunda seçim |
| 2 | Başlık olmadan oluşturma | 400, validation hatası |
| 3 | Geçersiz seçim türü | 400 |
| 4 | Geçersiz tarihlerle (bitiş < başlangıç) | 400 |
| 5 | Topluluk belirtmeden bağımsız seçim | 201, communityId = null |

### GET /api/v1/elections/{id} – Seçim Getirme (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Mevcut seçimi getirme | 200, seçim detayları + adaylar |
| 2 | Mevcut olmayan ID | 404 |
| 3 | Silinmiş seçimi getirme | 404 |

### PUT /api/v1/elections/{id} – Güncelleme (4 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | DRAFT seçimi güncelleme | 200 |
| 2 | ACTIVE seçimi güncelleme | 400, sadece DRAFT düzenlenebilir |
| 3 | Başkasının seçimini güncelleme | 403 |
| 4 | Geçersiz alanlarla güncelleme | 400 |

### DELETE /api/v1/elections/{id} – Silme (4 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Kendi seçimini silme | 200, soft delete |
| 2 | Başkasının seçimini silme | 403 |
| 3 | Zaten silinmiş seçim | 404 |
| 4 | ADMIN rolüyle başkasının seçimini silme | 200 |

---

## 2. Durum Yönetimi (14 test)

### POST /api/v1/elections/{id}/publish (4 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | DRAFT → SCHEDULED | 200, status = SCHEDULED |
| 2 | ACTIVE seçimi publish etme | 400, geçersiz geçiş |
| 3 | Adaysız seçimi publish etme | 400, en az 2 aday gerekli |
| 4 | Tarihsiz seçimi publish etme | 400, başlangıç/bitiş zamanı gerekli |

### POST /api/v1/elections/{id}/start (4 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | SCHEDULED → ACTIVE | 200, status = ACTIVE |
| 2 | DRAFT seçimi başlatma | 400 |
| 3 | Başkasının seçimini başlatma | 403 |
| 4 | Zaten aktif seçimi başlatma | 400 |

### POST /api/v1/elections/{id}/end (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | ACTIVE → CLOSED | 200, status = CLOSED |
| 2 | DRAFT seçimi bitirme | 400 |
| 3 | Zaten kapanmış seçimi bitirme | 400 |

### POST /api/v1/elections/{id}/cancel (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Herhangi durumdaki seçimi iptal | 200, status = CANCELLED |
| 2 | Zaten iptal edilmiş seçim | 400 |
| 3 | Başkasının seçimini iptal etme | 403 |

---

## 3. Aday Yönetimi (10 test)

### POST /api/v1/elections/{id}/candidates (4 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | DRAFT seçime geçerli aday ekleme | 201 |
| 2 | ACTIVE seçime aday ekleme | 400 |
| 3 | İsimsiz aday ekleme | 400 |
| 4 | Aynı isimde ikinci aday | 400, duplicate |

### PUT /api/v1/elections/{id}/candidates/{candidateId} (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Aday bilgilerini güncelleme | 200 |
| 2 | Mevcut olmayan aday ID | 404 |
| 3 | ACTIVE seçimdeki adayı güncelleme | 400 |

### DELETE /api/v1/elections/{id}/candidates/{candidateId} (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Aday silme | 200, soft delete |
| 2 | Mevcut olmayan aday | 404 |
| 3 | ACTIVE seçimden aday silme | 400 |

---

## 4. Erişim Kodu (9 test)

### POST /api/v1/elections/{id}/access-codes (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Erişim kodu oluşturma | 201, 6 haneli kod |
| 2 | maxUses ve expiresAt ile | 201 |
| 3 | Başkasının seçimine kod oluşturma | 403 |

### GET /api/v1/elections/{id}/access-codes (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Aktif kodları listeleme | 200, kod listesi |
| 2 | Kodları olmayan seçim | 200, boş liste |
| 3 | Başkasının seçiminin kodları | 403 |

### DELETE /api/v1/elections/{id}/access-codes/{codeId} (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Erişim kodu silme | 200 |
| 2 | Mevcut olmayan kod | 404 |
| 3 | Başkasının kodunu silme | 403 |

---

## 5. Oy Verme (22 test)

### POST /api/v1/elections/{id}/votes/verify-access (5 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Geçerli erişim koduyla doğrulama | 200, seçim bilgileri + adaylar |
| 2 | Geçersiz erişim kodu | 404 |
| 3 | Süresi dolmuş erişim kodu | 400 |
| 4 | Kullanım limiti dolu erişim kodu | 400 |
| 5 | DRAFT seçim için erişim kodu doğrulama | 400, seçim aktif değil |

### POST /api/v1/elections/{id}/votes/token (5 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | İlk kez token alma | 201, UUID token |
| 2 | Aynı kullanıcı tekrar token isteme | 201, aynı token (idempotent) |
| 3 | DRAFT seçim için token alma | 400, seçim aktif değil |
| 4 | CLOSED seçim için token alma | 400 |
| 5 | Farklı kullanıcılar farklı token alır | 201, farklı UUID'ler |

### POST /api/v1/elections/{id}/votes (7 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Geçerli token ile oy kullanma | 201, oy kaydedildi |
| 2 | Aynı token ile tekrar oy (idempotent) | 200, mevcut oy döner |
| 3 | Geçersiz token ile oy kullanma | 404 |
| 4 | Başka seçimin token'ı ile oy | 400 |
| 5 | Geçersiz aday ID ile oy | 404 |
| 6 | Silinmiş adaya oy | 400 |
| 7 | CLOSED seçime oy kullanma | 400 |

### GET /api/v1/elections/{id}/votes/my-status (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Oy kullanmamış kullanıcı | 200, alreadyVoted = false |
| 2 | Oy kullanmış kullanıcı | 200, alreadyVoted = true |
| 3 | Anonim seçimde oy detayı gizli | 200, candidateId = null |

### GET /api/v1/elections/{id}/votes/stats (2 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | CLOSED seçim istatistikleri | 200, aday bazlı oy sayıları |
| 2 | ACTIVE seçim istatistikleri (sahip değil) | 403 |

---

## 6. Kullanıcı Seçimleri (6 test)

### GET /api/v1/elections/my/active (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Aktif seçimleri listeleme | 200, seçim listesi |
| 2 | Hiç seçimi olmayan kullanıcı | 200, boş liste |
| 3 | Oy kullanılmış seçimler de görünür | 200, hasVoted = true |

### GET /api/v1/elections/my/history (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Katılım geçmişini listeleme | 200, geçmiş seçimler |
| 2 | Hiç oy kullanmamış kullanıcı | 200, boş liste |
| 3 | totalVotes alanı doğru | 200, oy sayıları |

---

## 7. Topluluk Seçimleri (3 test)

### GET /api/v1/communities/{communityId}/elections (3 test)

| # | Test Senaryosu | Beklenen Sonuç |
|---|----------------|----------------|
| 1 | Topluluk seçimlerini sayfalama | 200, paginated |
| 2 | Mevcut olmayan topluluk | 200, boş liste |
| 3 | Silinmiş seçimler görünmüyor | 200, sadece aktif |

---

## Entegrasyon Test Senaryoları

### Senaryo 1: Tam Seçim Döngüsü
1. Seçim oluştur (DRAFT)
2. 3 aday ekle
3. Seçimi yayınla (SCHEDULED)
4. Seçimi başlat (ACTIVE)
5. 2 kullanıcı vote token alsın
6. Her ikisi de oy kullansın
7. Seçimi bitir (CLOSED)
8. İstatistikleri kontrol et

### Senaryo 2: İdempotent Oy Verme
1. Aktif seçimde token al
2. Oy kullan → 201
3. Aynı token ile tekrar oy → 200, alreadyVoted = true
4. Aynı kullanıcı tekrar token iste → mevcut (kullanılmış) token döner

### Senaryo 3: Erişim Kodu Akışı
1. Erişim kodu oluştur
2. Kodu doğrula → seçim bilgileri
3. Token al → oy kullan
4. Kodun currentUses'ı artmış mı kontrol et

---

## Test Ortamı Notları

- **Minimum 3 kullanıcı** gerekli (seçim sahibi, 2 oy kullanan)
- Testler **sıralı** çalıştırılmalı (durum geçişleri)
- Postman Collection'da `election-service` klasörü kullanılabilir
- Docker Compose ile `election-service` + `postgres` ayağa kaldırılmalı
