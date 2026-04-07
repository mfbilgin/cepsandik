#!/bin/bash
# Entrypoint for backup container

echo "🚀 CepSandık Backup Service başlatılıyor..."
echo "⏰ Günlük yedekleme saati: 03:00 UTC"

# İlk yedeklemeyi başlangıçta yap (isteğe bağlı)
if [ "$BACKUP_ON_START" = "true" ]; then
    echo "📦 İlk yedekleme yapılıyor..."
    /usr/local/bin/backup.sh
fi

# Cron: exec ile PID 1 yap (dcron / bazı ortamlarda setpgid hatasını önler)
echo "✅ Cron daemon başlatılıyor (ön planda)"
exec /usr/sbin/crond -f
