# CepSandık Production Deployment Guide

Bu guide, CepSandık microservices platformunu Digital Ocean Droplet'ında production ortamına deploy etmek için adım adım talimatlar içerir.

## Ön Gereksinimler

- [ ] Digital Ocean Droplet (minimum 4GB RAM önerilir)
- [ ] Domain: `cepsandik.com` ve `api.cepsandik.com` DNS kayıtları yapılandırılmış
- [ ] AWS hesabı (S3 ve SES için)
- [ ] SSH erişimi olan bir makine

## 1. DNS Konfigürasyonu

Digital Ocean veya domain sağlayıcınızın DNS panelinden aşağıdaki A kayıtlarını ekleyin:

```
A    api.cepsandik.com    →  YOUR_DROPLET_IP
A    cepsandik.com        →  YOUR_DROPLET_IP
A    www.cepsandik.com    →  YOUR_DROPLET_IP
```

DNS yayılmasını kontrol edin (5-30 dakika sürebilir):
```bash
nslookup api.cepsandik.com
nslookup cepsandik.com
```

## 2. Sunucu Hazırlığı

### 2.1. Sunucuya Bağlanın

```bash
ssh root@YOUR_DROPLET_IP
```

### 2.2. Sistem Güncellemesi

```bash
apt update && apt upgrade -y
```

### 2.3. Docker Kurulumu

```bash
# Docker kurulumu
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Docker Compose V2 kurulumu
apt install docker-compose-plugin -y

# Docker servisini başlat
systemctl start docker
systemctl enable docker

# Kurulumu doğrula
docker --version
docker compose version
```

### 2.4. Git Kurulumu

```bash
apt install git -y
```

## 3. Proje Kurulumu

### 3.1. Projeyi Klonlayın

```bash
cd /opt
git clone https://github.com/mfbilgin/cepsandik.git
cd cepsandik/infra
```

### 3.2. Environment Dosyası Oluşturun

```bash
cp .env.example .env
nano .env
```

`.env` dosyasında aşağıdaki değerleri güncelleyin:

#### Güvenlik (ZORUNLU)
- `DATABASE_PASSWORD`: Güçlü bir veritabanı şifresi
- `JWT_SECRET`: 64 karakter hexadecimal random string (örn: openssl rand -hex 32)
- `INTERNAL_JWT_SECRET`: Farklı 64 karakter hex string
- `RABBITMQ_PASSWORD`: Güçlü bir RabbitMQ şifresi

#### AWS Credentials (ZORUNLU)
- `AWS_ACCESS_KEY`: AWS IAM Access Key ID
- `AWS_SECRET_KEY`: AWS IAM Secret Access Key
- `AWS_BUCKET`: S3 bucket adı (örn: cepsandik-uploads)
- `AWS_REGION`: S3 region (örn: eu-north-1)

#### Email (AWS SES) (ZORUNLU)
- `MAIL_USERNAME`: AWS SES SMTP username
- `MAIL_PASSWORD`: AWS SES SMTP password
- `MAIL_HOST`: SMTP endpoint (örn: email-smtp.eu-north-1.amazonaws.com)
- `MAIL_FROM`: Gönderen email adresi (örn: noreply@cepsandik.com)
- `LETSENCRYPT_EMAIL`: SSL sertifika bildirimleri için email

#### Server Bilgileri
- `SERVER_IP`: Droplet IP adresiniz (referans için)

Örnek random secret oluşturma:
```bash
openssl rand -hex 32
```

Dosyayı kaydedin ve çıkın (Ctrl+X, Y, Enter).

### 3.3. Gerekli Dizinleri Oluşturun

```bash
mkdir -p certbot/conf certbot/www logs/nginx
chmod -R 755 certbot logs
```

## 4. SSL Sertifikası Oluşturma

### 4.1. Certbot ile İlk Sertifika

Domain'lerinizin Droplet IP'nize yönlendirildiğinden emin olun, sonra:

```bash
# Geçici nginx başlat (sadece ACME challenge için)
docker compose up -d gateway

# SSL sertifikası al
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email admin@cepsandik.com \
  --agree-tos \
  --no-eff-email \
  -d api.cepsandik.com

docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email admin@cepsandik.com \
  --agree-tos \
  --no-eff-email \
  -d cepsandik.com -d www.cepsandik.com

# Gateway'i durdur
docker compose down
```

**Not**: Eğer DNS henüz yayılmadıysa, certbot hata verecektir. DNS'in doğru yapılandırıldığından emin olun.

### 4.2. Alternatif: Staging Mode (Test için)

İlk denemede staging mode kullanabilirsiniz (Let's Encrypt rate limit'lerini aşmamak için):

```bash
docker compose run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email admin@cepsandik.com \
  --agree-tos \
  --no-eff-email \
  --staging \
  -d api.cepsandik.com
```

Test başarılıysa, `--staging` parametresini kaldırarak gerçek sertifikayı alın.

## 5. Deployment

### 5.1. Build ve Start

Tüm servisleri build edin ve başlatın:

```bash
cd /opt/cepsandik/infra
docker compose up -d --build
```

İlk build 10-15 dakika sürebilir (özellikle Spring Boot servisleri).

### 5.2. Logları İzleyin

```bash
# Tüm servislerin logları
docker compose logs -f

# Belirli bir servisin logları
docker compose logs -f gateway
docker compose logs -f user-service
docker compose logs -f community-service
docker compose logs -f election-service
```

### 5.3. Servis Durumunu Kontrol Edin

```bash
docker compose ps
```

Tüm servislerin `healthy` veya `running` durumunda olduğundan emin olun.

## 6. Doğrulama

### 6.1. Health Check Testleri

```bash
# User Service
curl https://api.cepsandik.com/user/actuator/health

# Community Service
curl https://api.cepsandik.com/community/actuator/health

# Election Service
curl https://api.cepsandik.com/election/actuator/health
```

Hepsi `{"status":"UP"}` döndürmelidir.

### 6.2. API Test

Postman veya curl ile authentication test edin:

```bash
# Register endpoint
curl -X POST https://api.cepsandik.com/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123!",
    "firstName": "Test",
    "lastName": "User"
  }'
```

### 6.3. Database Kontrolü

```bash
# PostgreSQL bağlantısı
docker compose exec postgres psql -U cepsandik_user -d userdb -c '\dt'

# Veritabanlarını listele
docker compose exec postgres psql -U cepsandik_user -c '\l'
```

## 7. Monitoring ve Bakım

### 7.1. Disk Kullanımı

```bash
# Docker volume'lerini kontrol et
docker system df

# Kullanılmayan image'leri temizle
docker image prune -a
```

### 7.2. Backup

Backup servisi otomatik olarak çalışır. Manuel backup için:

```bash
docker compose exec backup /backup.sh
```

Backup dosyaları `/opt/cepsandik/infra/backup_data` dizininde saklanır.

### 7.3. SSL Sertifika Yenileme

Certbot servisi otomatik olarak sertifikaları yeniler (her 12 saatte bir kontrol eder). Manuel yenileme:

```bash
docker compose run --rm certbot renew
docker compose restart gateway
```

### 7.4. Güncelleme

```bash
cd /opt/cepsandik
git pull
cd infra
docker compose down
docker compose up -d --build
```

## 8. Güvenlik Önerileri

### 8.1. Firewall (UFW)

```bash
# UFW kur ve yapılandır
apt install ufw -y
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
ufw status
```

### 8.2. SSH Güvenliği

```bash
# Root login'i devre dışı bırak
nano /etc/ssh/sshd_config
# PermitRootLogin no ekleyin

# SSH servisini yeniden başlat
systemctl restart sshd
```

### 8.3. Otomatik Güvenlik Güncellemeleri

```bash
apt install unattended-upgrades -y
dpkg-reconfigure --priority=low unattended-upgrades
```

## 9. Troubleshooting

### Servis Başlatılamıyor

```bash
# Detaylı log
docker compose logs <service-name>

# Container'ı yeniden başlat
docker compose restart <service-name>

# Container'ın içine gir
docker compose exec <service-name> sh
```

### Port Çakışması

```bash
# Port kullanımını kontrol et
netstat -tlnp | grep :80
netstat -tlnp | grep :443
```

### Database Bağlantı Hatası

```bash
# PostgreSQL logları
docker compose logs postgres

# Database'e manuel bağlan
docker compose exec postgres psql -U cepsandik_user -d userdb
```

### SSL Sertifika Hatası

```bash
# Sertifika yollarını kontrol et
docker compose exec gateway ls -la /etc/letsencrypt/live/

# Nginx konfigürasyonunu test et
docker compose exec gateway nginx -t
```

## 10. Yedekleme Stratejisi

### 10.1. Database Backup

```bash
# Manuel backup
docker compose exec postgres pg_dumpall -U cepsandik_user > backup_$(date +%Y%m%d).sql

# Backup'ı uzak sunucuya gönder
scp backup_*.sql user@remote-server:/backups/
```

### 10.2. Volume Backup

```bash
# Volume'leri tar ile yedekle
docker run --rm -v cepsandik-postgres-data:/data -v $(pwd):/backup ubuntu tar czf /backup/postgres-data-backup.tar.gz /data
```

## 11. Kaynaklar ve Limitler

Mevcut konfigürasyon yaklaşık **2.5GB RAM** kullanır:
- Gateway: 256MB
- User Service: 512MB
- Community Service: 400MB
- Election Service: 400MB
- PostgreSQL: 256MB
- Redis: 128MB
- RabbitMQ: 256MB
- Backup: 128MB

**4GB Droplet** rahatlıkla yeterlidir. Daha düşük RAM için servis limitlerini düşürebilirsiniz.

## 12. Ek Notlar

- **Frontend**: `cepsandik.com` için frontend dosyalarını `/usr/share/nginx/html` dizinine kopyalamanız gerekir.
- **Domain Mail Verification**: AWS SES kullanıyorsanız, domain'inizi SES'te verify etmeyi unutmayın (SPF, DKIM kayıtları).
- **S3 Bucket**: Bucket'ın public access'i kapalı olmalı, IAM user'a sadece gerekli policy'ler verilmeli.
- **Log Rotation**: Production'da log rotation için logrotate yapılandırması önerilir.

## Destek

Sorun yaşarsanız:
1. `docker compose logs` ile logları kontrol edin
2. Health check endpoint'lerini test edin
3. `.env` dosyasındaki konfigürasyonu doğrulayın
4. GitHub Issues açın: https://github.com/mfbilgin/cepsandik/issues

---

**Başarılı deployment!** 🚀

API Endpoint: https://api.cepsandik.com/v1/
Frontend: https://cepsandik.com
