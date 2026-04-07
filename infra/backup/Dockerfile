FROM postgres:16-alpine

# Gerekli paketleri yükle
RUN apk add --no-cache bash dcron

# Backup scriptini kopyala (Windows CRLF → container içinde sed ile temizle)
COPY backup.sh /usr/local/bin/backup.sh
RUN sed -i 's/\r$//' /usr/local/bin/backup.sh && chmod +x /usr/local/bin/backup.sh

# Cron job tanımla - Her gün saat 03:00'te çalışır
RUN echo "0 3 * * * /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1" > /etc/crontabs/root

# Backup dizinini oluştur
RUN mkdir -p /backups

# Başlangıç scripti — doğrudan exec /entrypoint.sh CRLF ile "No such file" verir; bash + sed
COPY entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh

ENTRYPOINT ["/bin/bash", "/entrypoint.sh"]
