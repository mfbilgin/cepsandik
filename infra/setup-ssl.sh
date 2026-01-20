#!/bin/bash

# ============================================
# CepSandık SSL Setup Script
# ============================================
# This script helps you obtain SSL certificates from Let's Encrypt
# Run this AFTER DNS is configured and docker compose is running

set -e

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

# Start gateway for ACME challenge
echo ""
echo "🚀 Starting gateway for ACME challenge..."
docker compose up -d gateway

sleep 5

# Obtain certificate for API domain
echo ""
echo "🔐 Obtaining SSL certificate for api.cepsandik.com..."
docker compose run --rm certbot certonly --webroot \
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
docker compose run --rm certbot certonly --webroot \
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
docker compose restart gateway

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
