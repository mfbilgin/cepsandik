#!/bin/sh
set -e

# nginx.conf iki alan adı için PEM bekliyor; klasör boşsa (ilk kurulum / yerel) geçici self-signed üret.
gen_selfsigned() {
    domain="$1"
    live="/etc/letsencrypt/live/$domain"
    if [ ! -f "$live/fullchain.pem" ] || [ ! -f "$live/privkey.pem" ]; then
        echo "cepsandik-gateway: sertifika yok ($domain) — geçici self-signed oluşturuluyor (production için Let's Encrypt kullanın)"
        mkdir -p "$live"
        openssl req -x509 -nodes -newkey rsa:2048 -days 30 \
            -keyout "$live/privkey.pem" \
            -out "$live/fullchain.pem" \
            -subj "/CN=$domain"
        chmod 644 "$live/fullchain.pem"
        chmod 600 "$live/privkey.pem"
    fi
}

gen_selfsigned "api.cepsandik.com"
gen_selfsigned "cepsandik.com"

exec "$@"
