# Community Service Test Dokümanı

Bu doküman, Community Service'in tüm endpoint'lerini ve her endpoint için test edilmesi gereken senaryoları içermektedir.

**Base URL:** `http://localhost` (Gateway üzerinden)  
**Önkoşul:** Tüm istekler için geçerli JWT token gereklidir (`Authorization: Bearer <token>`)

---

## 📋 Test Özeti

| Endpoint | Toplam Test Case |
|----------|------------------|
| POST /api/v1/communities | 6 |
| GET /api/v1/communities | 3 |
| GET /api/v1/communities/{id} | 4 |
| PUT /api/v1/communities/{id} | 5 |
| DELETE /api/v1/communities/{id} | 4 |
| GET /api/v1/communities/search | 3 |
| POST /api/v1/communities/{id}/invitations | 5 |
| POST /api/v1/communities/join | 7 |
| GET /api/v1/communities/{id}/invitations | 3 |
| DELETE /api/v1/communities/{id}/invitations/{invId} | 4 |
| GET /api/v1/communities/{id}/members | 3 |
| GET /api/v1/communities/{id}/members/pending | 3 |
| PUT /api/v1/communities/{id}/members/{mId}/role | 6 |
| PUT /api/v1/communities/{id}/members/{mId}/approve | 4 |
| PUT /api/v1/communities/{id}/members/{mId}/reject | 3 |
| DELETE /api/v1/communities/{id}/members/{mId} | 5 |
| DELETE /api/v1/communities/{id}/members/leave | 4 |
| GET /api/v1/communities/{id}/statistics | 3 |
| **TOPLAM** | **75** |

---

## 🏠 COMMUNITY: Topluluk Oluştur

### `POST /api/v1/communities`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Public topluluk oluştur | `{"name":"Test Topluluk","description":"Açıklama","visibility":"PUBLIC"}` | 201, topluluk bilgileri |
| 2 | ✅ Private topluluk oluştur | `{"name":"Özel Topluluk","description":"Açıklama","visibility":"PRIVATE"}` | 201, topluluk bilgileri |
| 3 | ❌ İsim boş | `{"name":"","visibility":"PUBLIC"}` | 400, `"name: Topluluk adı zorunludur"` |
| 4 | ❌ İsim çok kısa | `{"name":"AB","visibility":"PUBLIC"}` | 400, `"name: Topluluk adı 3 ile 100 karakter..."` |
| 5 | ❌ Visibility boş | `{"name":"Test","visibility":null}` | 400, `"visibility: Görünürlük alanı zorunludur"` |
| 6 | ❌ Aynı isimde topluluk | Mevcut isimle tekrar oluştur | 409, `"Bu isimde bir topluluğunuz zaten var"` |

---

## 🏠 COMMUNITY: Topluluklarımı Listele

### `GET /api/v1/communities?page=0&size=20`

| # | Test Case | Query Params | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ İlk sayfa | `?page=0&size=10` | 200, sayfalı liste |
| 2 | ✅ Boş liste | Üye olunmayan kullanıcı | 200, `content: []` |
| 3 | ❌ Token yok | Authorization header yok | 401 |

---

## 🏠 COMMUNITY: Topluluk Detayı

### `GET /api/v1/communities/{id}`

| # | Test Case | Path | Beklenen Sonuç |
|---|-----------|------|----------------|
| 1 | ✅ Üye olduğum topluluk | `/api/v1/communities/1` | 200, topluluk detayı |
| 2 | ❌ Üye olmadığım topluluk | Üye olunmayan id | 403, `"Bu topluluğun üyesi değilsiniz"` |
| 3 | ❌ Olmayan topluluk | `/api/v1/communities/99999` | 404, `"Topluluk bulunamadı"` |
| 4 | ❌ Token yok | Authorization header yok | 401 |

---

## 🏠 COMMUNITY: Topluluk Güncelle

### `PUT /api/v1/communities/{id}`

**Yetki:** OWNER veya ADMIN

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ İsim güncelle (Owner) | `{"name":"Yeni İsim"}` | 200, güncellenen topluluk |
| 2 | ✅ Açıklama güncelle (Admin) | `{"description":"Yeni açıklama"}` | 200, güncellenen topluluk |
| 3 | ✅ Visibility değiştir | `{"visibility":"PRIVATE"}` | 200, güncellenen topluluk |
| 4 | ❌ Normal üye güncelleme | MEMBER rolüyle istek | 403, `"Topluluk güncelleme yetkiniz yok"` |
| 5 | ❌ Olmayan topluluk | id=99999 | 404, `"Topluluk bulunamadı"` |

---

## 🏠 COMMUNITY: Topluluk Sil

### `DELETE /api/v1/communities/{id}`

**Yetki:** Sadece OWNER

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Owner silme | OWNER | 200, `"Topluluk başarıyla silindi"` |
| 2 | ❌ Admin silme | ADMIN | 403, `"Bu topluluğu silme yetkiniz yok"` |
| 3 | ❌ Member silme | MEMBER | 403, `"Bu topluluğu silme yetkiniz yok"` |
| 4 | ❌ Olmayan topluluk | id=99999 | 404, `"Topluluk bulunamadı"` |

---

## 🏠 COMMUNITY: Topluluk Ara

### `GET /api/v1/communities/search?query=test`

| # | Test Case | Query | Beklenen Sonuç |
|---|-----------|-------|----------------|
| 1 | ✅ Eşleşen sonuçlar | `?query=test` | 200, eşleşen topluluklar |
| 2 | ✅ Eşleşen yok | `?query=zzzzz` | 200, `content: []` |
| 3 | ❌ Query boş | `?query=` | 400 veya boş sonuç |

---

## 📩 INVITATION: Davet Oluştur

### `POST /api/v1/communities/{id}/invitations`

**Yetki:** OWNER veya ADMIN

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Sınırsız davet | `{}` | 201, davet kodu döner |
| 2 | ✅ Limitli davet | `{"maxUses":10,"expiresInHours":24}` | 201, davet bilgileri |
| 3 | ❌ Normal üye | MEMBER rolüyle | 403, `"Davet oluşturma yetkiniz yok"` |
| 4 | ❌ Olmayan topluluk | id=99999 | 404, `"Topluluk bulunamadı"` |
| 5 | ❌ Üye değil | Üye olunmayan topluluk | 403, `"Bu topluluğun üyesi değilsiniz"` |

---

## 📩 INVITATION: Davet Koduyla Katıl

### `POST /api/v1/communities/join`

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Public topluluğa katıl | `{"code":"VALIDCODE"}` | 200, status: APPROVED |
| 2 | ✅ Private topluluğa katıl | `{"code":"VALIDCODE"}` | 200, status: PENDING |
| 3 | ❌ Geçersiz kod | `{"code":"INVALID"}` | 404, `"Geçersiz davet kodu"` |
| 4 | ❌ Pasif davet | Deaktif edilmiş kod | 400, `"Bu davet artık aktif değil"` |
| 5 | ❌ Süresi dolmuş | Expired kod | 400, `"Bu davetin süresi dolmuş"` |
| 6 | ❌ Maksimum kullanım | Max kullanıma ulaşmış kod | 400, `"Bu davet maksimum kullanım sayısına ulaşmış"` |
| 7 | ❌ Zaten üye | Tekrar aynı topluluğa | 409, `"Bu topluluğun zaten üyesisiniz"` |

---

## 📩 INVITATION: Davetleri Listele

### `GET /api/v1/communities/{id}/invitations`

**Yetki:** OWNER veya ADMIN

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Owner/Admin | OWNER/ADMIN | 200, davet listesi |
| 2 | ❌ Normal üye | MEMBER | 403, `"Davetleri görme yetkiniz yok"` |
| 3 | ❌ Üye değil | Dış kullanıcı | 403 |

---

## 📩 INVITATION: Daveti İptal Et

### `DELETE /api/v1/communities/{id}/invitations/{invitationId}`

**Yetki:** OWNER veya ADMIN

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Başarılı iptal | OWNER/ADMIN | 200, `"Davet başarıyla iptal edildi"` |
| 2 | ❌ Normal üye | MEMBER | 403, `"Davet iptal etme yetkiniz yok"` |
| 3 | ❌ Olmayan davet | id=99999 | 404, `"Davet bulunamadı"` |
| 4 | ❌ Başka topluluğun daveti | Farklı topluluk | 400, `"Bu davet bu topluluğa ait değil"` |

---

## 👥 MEMBER: Üyeleri Listele

### `GET /api/v1/communities/{id}/members?page=0&size=20`

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Üye olarak görüntüle | MEMBER/ADMIN/OWNER | 200, üye listesi |
| 2 | ❌ Üye değil | Dış kullanıcı | 403, `"Bu topluluğun üyesi değilsiniz"` |
| 3 | ✅ Sayfalama | `?page=1&size=5` | 200, ikinci sayfa |

---

## 👥 MEMBER: Bekleyen Üyeleri Listele

### `GET /api/v1/communities/{id}/members/pending`

**Yetki:** OWNER veya ADMIN

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Owner/Admin | OWNER/ADMIN | 200, pending listesi |
| 2 | ❌ Normal üye | MEMBER | 403, `"Bekleyen üyeleri görme yetkiniz yok"` |
| 3 | ✅ Boş liste | Bekleyen yoksa | 200, `content: []` |

---

## 👥 MEMBER: Üye Rolü Değiştir

### `PUT /api/v1/communities/{id}/members/{memberId}/role`

**Yetki:** OWNER

| # | Test Case | Request Body | Beklenen Sonuç |
|---|-----------|--------------|----------------|
| 1 | ✅ Member → Admin | `{"role":"ADMIN"}` | 200, rol güncellendi |
| 2 | ✅ Admin → Member | `{"role":"MEMBER"}` | 200, rol güncellendi |
| 3 | ❌ Owner rolü değiştir | Owner'ın id'si | 403, `"Topluluk sahibinin rolü değiştirilemez"` |
| 4 | ❌ OWNER rolü ata | `{"role":"OWNER"}` | 400, `"OWNER rolü atanamaz"` |
| 5 | ❌ Admin değiştirmeye çalış | ADMIN olarak | 403, `"Rol değiştirme yetkiniz yok"` |
| 6 | ❌ Olmayan üye | memberId=99999 | 404, `"Üye bulunamadı"` |

---

## 👥 MEMBER: Üye Onayla

### `PUT /api/v1/communities/{id}/members/{memberId}/approve`

**Yetki:** OWNER veya ADMIN

| # | Test Case | Member Status | Beklenen Sonuç |
|---|-----------|---------------|----------------|
| 1 | ✅ Pending üyeyi onayla | PENDING | 200, status: APPROVED |
| 2 | ❌ Zaten onaylı | APPROVED | 400, `"Üye zaten onaylanmış veya reddedilmiş"` |
| 3 | ❌ Normal üye onaylama | MEMBER rolüyle | 403 |
| 4 | ❌ Olmayan üye | memberId=99999 | 404 |

---

## 👥 MEMBER: Üye Reddet

### `PUT /api/v1/communities/{id}/members/{memberId}/reject`

**Yetki:** OWNER veya ADMIN

| # | Test Case | Member Status | Beklenen Sonuç |
|---|-----------|---------------|----------------|
| 1 | ✅ Pending üyeyi reddet | PENDING | 200, `"Üye reddedildi"` |
| 2 | ❌ Zaten onaylı | APPROVED | 400 |
| 3 | ❌ Normal üye reddetme | MEMBER rolüyle | 403 |

---

## 👥 MEMBER: Üye Çıkar

### `DELETE /api/v1/communities/{id}/members/{memberId}`

**Yetki:** OWNER veya ADMIN

| # | Test Case | Target Role | Beklenen Sonuç |
|---|-----------|-------------|----------------|
| 1 | ✅ Owner üye çıkarır | MEMBER | 200, `"Üye topluluktan çıkarıldı"` |
| 2 | ✅ Owner admin çıkarır | ADMIN | 200 |
| 3 | ❌ Owner çıkarılamaz | OWNER | 403, `"Topluluk sahibi çıkarılamaz"` |
| 4 | ❌ Admin admin çıkarır | ADMIN (by ADMIN) | 403, `"Diğer yöneticileri çıkarma yetkiniz yok"` |
| 5 | ❌ Member çıkarma | MEMBER rolüyle | 403 |

---

## 👥 MEMBER: Topluluktan Ayrıl

### `DELETE /api/v1/communities/{id}/members/leave`

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Member ayrılır | MEMBER | 200, `"Topluluktan ayrıldınız"` |
| 2 | ✅ Admin ayrılır | ADMIN | 200 |
| 3 | ❌ Owner ayrılamaz | OWNER | 400, `"Topluluk sahibi olarak ayrılamazsınız..."` |
| 4 | ❌ Üye değil | Dış kullanıcı | 404/403 |

---

## 📊 STATISTICS: Topluluk İstatistikleri

### `GET /api/v1/communities/{id}/statistics`

**Yetki:** OWNER veya ADMIN

| # | Test Case | User Role | Beklenen Sonuç |
|---|-----------|-----------|----------------|
| 1 | ✅ Owner/Admin | OWNER/ADMIN | 200, istatistik bilgileri |
| 2 | ❌ Normal üye | MEMBER | 403, `"İstatistikleri görme yetkiniz yok"` |
| 3 | ❌ Üye değil | Dış kullanıcı | 403 |

**Beklenen Response Alanları:**
- totalMembers
- pendingMembers
- adminCount
- activeInvitations
- totalInvitationsUsed
- createdAt
- lastActivityAt

---

## 🔄 Entegrasyon Test Senaryoları

### Senaryo 1: Topluluk Oluşturma ve Üye Ekleme
1. Topluluk oluştur → 201, role: OWNER
2. Davet oluştur → 201, code döner
3. (Farklı kullanıcı) Davet koduyla katıl → 200
4. Üyeleri listele → 2 üye görünür

### Senaryo 2: Private Topluluk Akışı
1. Private topluluk oluştur → 201
2. Davet oluştur → 201
3. (Farklı kullanıcı) Katıl → status: PENDING
4. Bekleyen üyeleri listele → 1 bekleyen
5. Üyeyi onayla → status: APPROVED

### Senaryo 3: Yetki Kontrolleri
1. MEMBER olarak topluluk güncelle → 403
2. MEMBER olarak davet oluştur → 403
3. MEMBER olarak istatistik görüntüle → 403
4. ADMIN olarak update → 200

### Senaryo 4: Owner Koruma
1. Owner'ın rolünü değiştir → 403
2. Owner'ı çıkar → 403
3. Owner olarak ayrıl → 400

### Senaryo 5: Davet Limitleri
1. maxUses=1 ile davet oluştur
2. İlk kullanıcı katılır → 200
3. İkinci kullanıcı katılır → 400 (max kullanım)

---

## ⚠️ Test Notları

1. **Token Gerekli:** Her istek için `Authorization: Bearer <token>` header'ı şart
2. **Sıralı Testler:** Bazı testler sıralı yapılmalı (örn: create → update → delete)
3. **Farklı Kullanıcılar:** Bazı testler için 2+ farklı hesap gerekli
4. **Role Testleri:** OWNER/ADMIN/MEMBER rolleri için ayrı token'lar gerekli
5. **Cleanup:** Test sonrası oluşturulan toplulukları silin
6. **X-Platform-Role:** Gateway'den gelen platform rolü header'ı (USER/MODERATOR/ADMIN)
