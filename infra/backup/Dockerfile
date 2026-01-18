FROM postgres:16-alpine

# Gerekli paketleri yükle
RUN apk add --no-cache bash dcron

# Backup scriptini kopyala
COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh

# Cron job tanımla - Her gün saat 03:00'te çalışır
RUN echo "0 3 * * * /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1" > /etc/crontabs/root

# Backup dizinini oluştur
RUN mkdir -p /backups

# Başlangıç scripti
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
