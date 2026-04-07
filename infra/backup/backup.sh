#!/bin/bash
# ==========================================
# CepSandık PostgreSQL Backup Script
# ==========================================
# Bu script günlük otomatik yedekleme yapar
# Cron job ile çalıştırılır

set -e

# Konfigürasyon
BACKUP_DIR="/backups"
RETENTION_DAYS=7
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
DATABASES=("userdb" "communitydb" "electiondb")

# Log fonksiyonu
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

log "🚀 Yedekleme başlatılıyor..."

# Backup dizinini oluştur
mkdir -p "$BACKUP_DIR"

# Her veritabanı için yedekleme yap
for DB in "${DATABASES[@]}"; do
    BACKUP_FILE="$BACKUP_DIR/${DB}_${TIMESTAMP}.sql.gz"
    
    log "📦 $DB yedekleniyor..."
    
    pg_dump -h "$POSTGRES_HOST" -U "$POSTGRES_USER" -d "$DB" | gzip > "$BACKUP_FILE"
    
    if [ $? -eq 0 ]; then
        SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
        log "✅ $DB yedeklendi: $BACKUP_FILE ($SIZE)"
    else
        log "❌ $DB yedekleme hatası!"
        exit 1
    fi
done

# Eski yedekleri temizle
log "🧹 $RETENTION_DAYS günden eski yedekler temizleniyor..."
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +$RETENTION_DAYS -delete

# Kalan yedekleri listele
BACKUP_COUNT=$(ls -1 "$BACKUP_DIR"/*.sql.gz 2>/dev/null | wc -l)
TOTAL_SIZE=$(du -sh "$BACKUP_DIR" | cut -f1)

log "📊 Toplam yedek sayısı: $BACKUP_COUNT"
log "💾 Toplam boyut: $TOTAL_SIZE"
log "🎉 Yedekleme tamamlandı!"
