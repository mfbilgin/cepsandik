#!/bin/bash

# ============================================
# CepSandık SSL Setup Script
# ============================================
# This script helps you obtain SSL certificates from Let's Encrypt
# Run this AFTER DNS is configured and docker compose is running

set -e

if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo "ERROR: Docker Compose is not installed."
    exit 1
fi

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.prod.yaml -f docker-compose.demo.yaml}"

echo "=========================================="
echo "CepSandık SSL Certificate Setup"
echo "=========================================="
echo ""

# Check if .env exists
if [ ! -f ".env" ]; then
    echo "ERROR: .env file not found!"
    echo "Please copy .env.example to .env and configure it first."
    exit 1
fi

# Load environment variables
source .env

# Check required variables
if [ -z "$LETSENCRYPT_EMAIL" ]; then
    echo "ERROR: LETSENCRYPT_EMAIL not set in .env"
    exit 1
fi

echo "📧 Email for certificates: $LETSENCRYPT_EMAIL"
echo ""

# Check DNS
echo "🔍 Checking DNS configuration..."
echo ""

API_IP=$(dig +short api.cepsandik.com | tail -n1)
FRONTEND_IP=$(dig +short cepsandik.com | tail -n1)

echo "api.cepsandik.com → $API_IP"
echo "cepsandik.com → $FRONTEND_IP"
echo ""

if [ -z "$API_IP" ] || [ -z "$FRONTEND_IP" ]; then
    echo "⚠️  WARNING: DNS not fully configured or not yet propagated"
    echo "Please ensure your DNS A records are set correctly:"
    echo "  api.cepsandik.com → YOUR_DROPLET_IP"
    echo "  cepsandik.com → YOUR_DROPLET_IP"
    echo ""
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Create directories
echo "📁 Creating certificate directories..."
mkdir -p certbot/conf certbot/www
chmod -R 755 certbot

cleanup_selfsigned_lineage() {
  domain="$1"
  if [ -f "certbot/conf/live/$domain/fullchain.pem" ] && \
     ! openssl x509 -in "certbot/conf/live/$domain/fullchain.pem" -issuer -noout 2>/dev/null | grep -qi "Let's Encrypt"; then
    echo "Removing temporary self-signed certificate for $domain..."
    rm -rf "certbot/conf/live/$domain" "certbot/conf/archive/$domain" "certbot/conf/renewal/$domain.conf"
  fi
}

cleanup_selfsigned_lineage "api.cepsandik.com"
cleanup_selfsigned_lineage "cepsandik.com"

# Start gateway for ACME challenge
echo ""
echo "🚀 Starting gateway for ACME challenge..."
$COMPOSE $COMPOSE_FILES up -d gateway

sleep 5

# Obtain certificate for API domain
echo ""
echo "🔐 Obtaining SSL certificate for api.cepsandik.com..."
$COMPOSE $COMPOSE_FILES run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email "$LETSENCRYPT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d api.cepsandik.com

if [ $? -eq 0 ]; then
    echo "✅ Certificate for api.cepsandik.com obtained successfully!"
else
    echo "❌ Failed to obtain certificate for api.cepsandik.com"
    echo "Please check DNS configuration and try again."
    exit 1
fi

# Obtain certificate for frontend domain
echo ""
echo "🔐 Obtaining SSL certificate for cepsandik.com..."
$COMPOSE $COMPOSE_FILES run --rm certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email "$LETSENCRYPT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d cepsandik.com -d www.cepsandik.com

if [ $? -eq 0 ]; then
    echo "✅ Certificate for cepsandik.com obtained successfully!"
else
    echo "❌ Failed to obtain certificate for cepsandik.com"
    echo "Note: You can still use the API with api.cepsandik.com"
    echo "Frontend certificate can be obtained later."
fi

# Restart gateway to load certificates
echo ""
echo "🔄 Restarting gateway to load SSL certificates..."
$COMPOSE $COMPOSE_FILES restart gateway

sleep 5

# Test HTTPS endpoints
echo ""
echo "🧪 Testing HTTPS endpoints..."
echo ""

echo "Testing https://api.cepsandik.com/user/actuator/health"
curl -s -o /dev/null -w "Status: %{http_code}\n" https://api.cepsandik.com/user/actuator/health || echo "❌ Failed"

echo ""
echo "Testing https://cepsandik.com"
curl -s -o /dev/null -w "Status: %{http_code}\n" https://cepsandik.com || echo "❌ Failed (Frontend may not be deployed yet)"

echo ""
echo "=========================================="
echo "✅ SSL Setup Complete!"
echo "=========================================="
echo ""
echo "Your services are now accessible via HTTPS:"
echo "  🔒 API: https://api.cepsandik.com/v1/"
echo "  🔒 Frontend: https://cepsandik.com"
echo ""
echo "Certificates will auto-renew every 60 days."
echo ""
