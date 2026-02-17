# Election Service

CepSandık platformunun seçim yönetimi mikroservisi. Topluluklar içinde demokratik seçim süreçlerini yönetir.

## Özellikler

- **Seçim Yönetimi** – Seçim oluşturma, güncelleme, silme (CRUD)
- **Durum Yönetimi** – DRAFT → SCHEDULED → ACTIVE → CLOSED → ARCHIVED durum makinesi
- **Aday Yönetimi** – Aday ekleme, güncelleme, kaldırma
- **Erişim Kodu** – Bağımsız seçimler için 6 haneli alfanumerik kodlar
- **Oy Verme** – Vote token tabanlı anonim oylama
- **İdempotent İşlemler** – Ağ kesintilerinde güvenli tekrar deneme
- **Seçim İstatistikleri** – Aday bazlı oy sayıları ve yüzdeler
- **Katılım Takibi** – Aktif seçimlerim ve katılım geçmişi

## Teknoloji Stack

- Java 21
- Spring Boot 3.x
- PostgreSQL
- Flyway (veritabanı migration)
- Lombok
- Swagger/OpenAPI

## API Endpoint'leri

### Seçim Yönetimi
| Method | Endpoint | Açıklama |
|--------|----------|----------|
| POST | `/api/v1/elections` | Yeni seçim oluştur |
| GET | `/api/v1/elections/{id}` | Seçim detayını getir |
| PUT | `/api/v1/elections/{id}` | Seçimi güncelle |
| DELETE | `/api/v1/elections/{id}` | Seçimi sil |
| POST | `/api/v1/elections/{id}/publish` | Seçimi yayınla (DRAFT → SCHEDULED) |
| POST | `/api/v1/elections/{id}/start` | Seçimi başlat (SCHEDULED → ACTIVE) |
| POST | `/api/v1/elections/{id}/end` | Seçimi bitir (ACTIVE → CLOSED) |
| POST | `/api/v1/elections/{id}/cancel` | Seçimi iptal et |

### Aday Yönetimi
| Method | Endpoint | Açıklama |
|--------|----------|----------|
| POST | `/api/v1/elections/{id}/candidates` | Aday ekle |
| PUT | `/api/v1/elections/{id}/candidates/{candidateId}` | Adayı güncelle |
| DELETE | `/api/v1/elections/{id}/candidates/{candidateId}` | Adayı kaldır |

### Erişim Kodu
| Method | Endpoint | Açıklama |
|--------|----------|----------|
| POST | `/api/v1/elections/{id}/access-codes` | Erişim kodu oluştur |
| GET | `/api/v1/elections/{id}/access-codes` | Erişim kodlarını listele |
| DELETE | `/api/v1/elections/{id}/access-codes/{codeId}` | Erişim kodunu sil |

### Oy Verme
| Method | Endpoint | Açıklama |
|--------|----------|----------|
| POST | `/api/v1/elections/{id}/votes/verify-access` | Erişim kodunu doğrula |
| POST | `/api/v1/elections/{id}/votes/token` | Vote token al |
| POST | `/api/v1/elections/{id}/votes` | Oy kullan |
| GET | `/api/v1/elections/{id}/votes/my-status` | Oy durumumu kontrol et |
| GET | `/api/v1/elections/{id}/votes/stats` | Seçim istatistikleri |

### Kullanıcı Seçimleri
| Method | Endpoint | Açıklama |
|--------|----------|----------|
| GET | `/api/v1/elections/my/active` | Aktif seçimlerimi listele |
| GET | `/api/v1/elections/my/history` | Katılım geçmişim |

### Topluluk Seçimleri
| Method | Endpoint | Açıklama |
|--------|----------|----------|
| GET | `/api/v1/communities/{communityId}/elections` | Topluluk seçimlerini listele |

## Çalıştırma

### Docker ile (önerilen)
```bash
cd infra
docker compose up -d --build election-service
```

### Lokal geliştirme
```bash
# Java 21 gerekli
./mvnw spring-boot:run
```

### Ortam Değişkenleri

| Değişken | Açıklama | Varsayılan |
|----------|----------|------------|
| `SPRING_DATASOURCE_URL` | PostgreSQL bağlantı URL'i | `jdbc:postgresql://localhost:5432/election_db` |
| `SPRING_DATASOURCE_USERNAME` | Veritabanı kullanıcı adı | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Veritabanı şifresi | – |
| `JWT_SECRET` | Internal JWT doğrulama anahtarı | – |
| `SERVER_PORT` | Servis portu | `8083` |

## Swagger UI

Servis çalışırken: `http://localhost:8083/swagger-ui.html`
