# Community Service

CepSandık platformu için Topluluk Yönetimi mikroservisi.

## Özellikler

- ✅ Topluluk Oluşturma/Güncelleme/Silme
- ✅ Açık ve Özel topluluklar
- ✅ Davet kodu sistemi
- ✅ Üye yönetimi (rol değiştirme, çıkarma, onaylama)
- ✅ Rol tabanlı erişim (Sahip, Yönetici, Üye)
- ✅ Sayfalama (Pagination) desteği
- ✅ Topluluk arama
- ✅ İstatistik endpoint'leri
- ✅ Otomatik davet temizleme (scheduled task)

## Teknoloji Stack

- Java 21
- Spring Boot 3.5.5
- PostgreSQL
- Maven

## API Endpoint'leri

### Topluluklar
- `POST /api/v1/communities` - Topluluk oluştur
- `GET /api/v1/communities` - Topluluklarımı listele (sayfalı)
- `GET /api/v1/communities/{id}` - Topluluk detayları
- `PUT /api/v1/communities/{id}` - Topluluk güncelle
- `DELETE /api/v1/communities/{id}` - Topluluk sil
- `GET /api/v1/communities/search?query=...` - Topluluk ara

### Davetler
- `POST /api/v1/communities/{id}/invitations` - Davet oluştur
- `POST /api/v1/communities/join` - Davet koduyla katıl
- `GET /api/v1/communities/{id}/invitations` - Davetleri listele
- `DELETE /api/v1/communities/{id}/invitations/{invitationId}` - Davet iptal et

### Üyeler
- `GET /api/v1/communities/{id}/members` - Üyeleri listele (sayfalı)
- `GET /api/v1/communities/{id}/members/pending` - Bekleyen üyeleri listele
- `PUT /api/v1/communities/{id}/members/{memberId}/role` - Üye rolü değiştir
- `PUT /api/v1/communities/{id}/members/{memberId}/approve` - Üye onayla
- `PUT /api/v1/communities/{id}/members/{memberId}/reject` - Üye reddet
- `DELETE /api/v1/communities/{id}/members/{memberId}` - Üye çıkar
- `DELETE /api/v1/communities/{id}/members/leave` - Topluluktan ayrıl

### İstatistikler
- `GET /api/v1/communities/{id}/statistics` - Topluluk istatistikleri

## Çalıştırma

```bash
mvn spring-boot:run
```

## Docker

```bash
mvn clean package
docker build -t community-service .
docker run -p 8083:8083 community-service
```

## Dökümantasyon

Swagger UI: http://localhost:8083/swagger-ui.html

## Yapılandırma

| Özellik | Varsayılan | Açıklama |
|---------|------------|----------|
| `server.port` | 8083 | Servis portu |
| `spring.datasource.url` | jdbc:postgresql://localhost:5434/communitydb | Veritabanı URL |
| `jwt.internal.secret` | ... | Internal JWT secret |
