# CepSandık Infrastructure

Bu dizin CepSandık microservices platformunun production deployment altyapısını içerir.

## 📋 İçerik

- **docker-compose.yaml** - Production Docker Compose yapılandırması
- **.env.example** - Environment değişkenleri şablonu
- **openresty/** - API Gateway (Nginx + Lua) konfigürasyonu
- **backup/** - Otomatik veritabanı yedekleme servisi
- **DEPLOYMENT.md** - Detaylı deployment talimatları
- **quick-start.sh** - Hızlı başlangıç scripti
- **setup-ssl.sh** - SSL sertifika kurulum scripti

## 🚀 Hızlı Başlangıç

### 1. Environment Dosyasını Hazırlayın

```bash
cp .env.example .env
nano .env
```

Aşağıdaki değerleri güncelleyin:
- `DATABASE_PASSWORD` - Güçlü bir veritabanı şifresi
- `JWT_SECRET` - 64 karakter random hex string (`openssl rand -hex 32`)
- `INTERNAL_JWT_SECRET` - Farklı bir 64 karakter hex string
- `RABBITMQ_PASSWORD` - RabbitMQ şifresi
- AWS credentials (S3 için)
- Email credentials (AWS SES için)
- `LETSENCRYPT_EMAIL` - SSL sertifika bildirimleri için

### 2. DNS Ayarlarını Yapın

Domain sağlayıcınızdan aşağıdaki A kayıtlarını ekleyin:

```
api.cepsandik.com  →  YOUR_DROPLET_IP
cepsandik.com      →  YOUR_DROPLET_IP
```

### 3. Servisleri Başlatın

```bash
# Quick start script ile (önerilir)
chmod +x quick-start.sh
./quick-start.sh

# Veya manuel olarak
docker compose up -d --build
```

### 4. SSL Sertifikalarını Alın

```bash
chmod +x setup-ssl.sh
./setup-ssl.sh
```

## 🏗️ Mimari

```
                    ┌─────────────────┐
                    │   Internet      │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   Certbot/SSL   │
                    │  Let's Encrypt  │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼────┐         ┌────▼────┐       ┌─────▼─────┐
    │  Port   │         │  Port   │       │  Gateway  │
    │   80    │────────▶│  443    │──────▶│ OpenResty │
    │  HTTP   │         │  HTTPS  │       │           │
    └─────────┘         └─────────┘       └─────┬─────┘
         │                                       │
         └───────────── HTTP redirect ──────────┘
                                                 │
                    JWT Auth & Rate Limiting     │
                                                 │
         ┌───────────────────┬──────────────────┼──────────────────┐
         │                   │                  │                  │
    ┌────▼────┐        ┌─────▼──────┐    ┌─────▼──────┐    ┌─────▼──────┐
    │  User   │        │ Community  │    │  Election  │    │  Frontend  │
    │ Service │        │  Service   │    │  Service   │    │   (SPA)    │
    │  :8080  │        │   :8083    │    │   :8082    │    │            │
    └────┬────┘        └─────┬──────┘    └─────┬──────┘    └────────────┘
         │                   │                  │
         └───────────────────┴──────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼────┐        ┌─────▼──────┐      ┌────▼─────┐
    │  Postgres│       │   Redis    │      │ RabbitMQ │
    │  :5432  │        │   :6379    │      │  :5672   │
    └─────────┘        └────────────┘      └──────────┘
```

## 🔌 API Endpoints

**Base URL**: `https://api.cepsandik.com/v1`

### Public Endpoints (No Auth Required)
- `POST /auth/register` - Kullanıcı kaydı
- `POST /auth/login` - Giriş
- `POST /auth/refresh` - Token yenileme
- `GET /users/confirm-email-change` - Email değişikliği onayı

### Protected Endpoints (JWT Required)
- `GET /users/me` - Kullanıcı profili
- `PUT /users/me` - Profil güncelleme
- `POST /communities` - Topluluk oluşturma
- `GET /communities/{id}` - Topluluk detayı
- `POST /elections` - Seçim oluşturma
- `GET /elections/{id}` - Seçim detayı

### Admin Endpoints (Admin Role Required)
- `GET /admin/users` - Tüm kullanıcılar
- `GET /admin/stats` - Sistem istatistikleri

## 🛠️ Yönetim Komutları

### Servis Yönetimi

```bash
# Tüm servisleri başlat
docker compose up -d

# Belirli bir servisi yeniden başlat
docker compose restart user-service

# Logları izle
docker compose logs -f

# Belirli bir servisin logları
docker compose logs -f gateway

# Servis durumunu kontrol et
docker compose ps

# Servisleri durdur
docker compose down

# Servisleri durdur ve volume'leri sil
docker compose down -v
```

### Database Yönetimi

```bash
# PostgreSQL'e bağlan
docker compose exec postgres psql -U cepsandik_user -d userdb

# Database listesi
docker compose exec postgres psql -U cepsandik_user -c '\l'

# Manuel backup
docker compose exec postgres pg_dump -U cepsandik_user userdb > backup.sql

# Backup restore
docker compose exec -T postgres psql -U cepsandik_user userdb < backup.sql
```

### SSL Sertifika Yenileme

```bash
# Manuel yenileme
docker compose run --rm certbot renew

# Gateway'i yeniden başlat
docker compose restart gateway
```

## 📊 Monitoring

### Health Checks

```bash
# User Service
curl https://api.cepsandik.com/user/actuator/health

# Community Service
curl https://api.cepsandik.com/community/actuator/health

# Election Service
curl https://api.cepsandik.com/election/actuator/health
```

### Metrics

Prometheus metrics şu endpoint'lerden alınabilir:
- `https://api.cepsandik.com/user/actuator/metrics`
- `https://api.cepsandik.com/community/actuator/metrics`
- `https://api.cepsandik.com/election/actuator/metrics`

## 🔒 Güvenlik

- **JWT Authentication**: API Gateway seviyesinde JWT doğrulaması
- **Rate Limiting**: Brute-force koruması için rate limiting
- **SSL/TLS**: Let's Encrypt ile otomatik SSL sertifikaları
- **Internal JWT**: Servisler arası güvenli iletişim için internal token
- **CORS**: Frontend domain'i için CORS yapılandırması
- **Security Headers**: HSTS, X-Frame-Options, CSP vb.

## 📦 Kaynaklar

Toplam bellek kullanımı: **~2.5GB**
- Gateway: 256MB
- User Service: 512MB
- Community Service: 400MB
- Election Service: 400MB
- PostgreSQL: 256MB
- Redis: 128MB
- RabbitMQ: 256MB
- Backup: 128MB

**Öneri**: Minimum 4GB RAM'li Droplet

## 🔧 Troubleshooting

### Servis başlatılamıyor

```bash
# Detaylı log
docker compose logs <service-name>

# Container'ı temiz başlat
docker compose down
docker compose up -d --build
```

### Database bağlantı hatası

```bash
# PostgreSQL logları
docker compose logs postgres

# Database health check
docker compose exec postgres pg_isready -U cepsandik_user
```

### SSL sertifika hatası

```bash
# Sertifika yollarını kontrol et
docker compose exec gateway ls -la /etc/letsencrypt/live/

# Nginx konfigürasyonunu test et
docker compose exec gateway nginx -t
```

## 📚 Daha Fazla Bilgi

Detaylı deployment talimatları için [DEPLOYMENT.md](./DEPLOYMENT.md) dosyasına bakın.

## 📞 Destek

- GitHub Issues: https://github.com/mfbilgin/cepsandik/issues
- Email: admin@cepsandik.com

---

**CepSandık Platform** - Demokratik Seçim Sistemi 🗳️
