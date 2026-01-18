#!/bin/bash
# Entrypoint for backup container

echo "🚀 CepSandık Backup Service başlatılıyor..."
echo "⏰ Günlük yedekleme saati: 03:00 UTC"

# İlk yedeklemeyi başlangıçta yap (isteğe bağlı)
if [ "$BACKUP_ON_START" = "true" ]; then
    echo "📦 İlk yedekleme yapılıyor..."
    /usr/local/bin/backup.sh
fi

# Cron daemon'ı başlat
echo "✅ Cron daemon başlatıldı"
crond -f -l 2
