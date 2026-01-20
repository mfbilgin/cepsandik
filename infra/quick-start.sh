#!/bin/bash

# ============================================
# CepSandık Quick Start Script
# ============================================
# This script helps you quickly deploy CepSandık on your server

set -e

echo "=========================================="
echo "🚀 CepSandık Quick Start"
echo "=========================================="
echo ""

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    echo "⚠️  Running as root. This is OK for initial setup."
    echo ""
fi

# Check if .env exists
if [ ! -f ".env" ]; then
    echo "📝 Creating .env file from .env.example..."
    cp .env.example .env
    echo ""
    echo "⚠️  IMPORTANT: Please edit .env file and update the following:"
    echo "   - DATABASE_PASSWORD"
    echo "   - JWT_SECRET (run: openssl rand -hex 32)"
    echo "   - INTERNAL_JWT_SECRET (run: openssl rand -hex 32)"
    echo "   - RABBITMQ_PASSWORD"
    echo "   - AWS credentials (AWS_ACCESS_KEY, AWS_SECRET_KEY)"
    echo "   - Email credentials (MAIL_USERNAME, MAIL_PASSWORD)"
    echo "   - LETSENCRYPT_EMAIL"
    echo ""
    read -p "Press Enter after you've updated .env file..." -r
fi

# Validate critical env variables
source .env

MISSING_VARS=()

if [[ "$DATABASE_PASSWORD" == "CHANGE_ME_STRONG_DB_PASSWORD_HERE" ]]; then
    MISSING_VARS+=("DATABASE_PASSWORD")
fi

if [[ "$JWT_SECRET" == "CHANGE_ME_TO_RANDOM_64_CHAR_HEX_STRING_FOR_JWT_SECRET" ]]; then
    MISSING_VARS+=("JWT_SECRET")
fi

if [[ "$INTERNAL_JWT_SECRET" == "CHANGE_ME_TO_DIFFERENT_RANDOM_64_CHAR_HEX_FOR_INTERNAL" ]]; then
    MISSING_VARS+=("INTERNAL_JWT_SECRET")
fi

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo "❌ ERROR: The following variables need to be configured in .env:"
    for var in "${MISSING_VARS[@]}"; do
        echo "   - $var"
    done
    echo ""
    echo "Please update .env file and run this script again."
    exit 1
fi

echo "✅ Environment configuration validated"
echo ""

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed!"
    echo "Please install Docker first:"
    echo "  curl -fsSL https://get.docker.com | sh"
    exit 1
fi

if ! command -v docker compose &> /dev/null; then
    echo "❌ Docker Compose is not installed!"
    echo "Please install Docker Compose plugin:"
    echo "  apt install docker-compose-plugin -y"
    exit 1
fi

echo "✅ Docker and Docker Compose are installed"
echo ""

# Create required directories
echo "📁 Creating required directories..."
mkdir -p certbot/conf certbot/www logs/nginx
chmod -R 755 certbot logs
echo "✅ Directories created"
echo ""

# Check if this is first time setup
if [ ! -d "certbot/conf/live" ]; then
    echo "🔐 This appears to be your first deployment."
    echo ""
    echo "SSL certificates will be obtained AFTER Docker services are running."
    echo "Make sure your DNS is configured:"
    echo "  - api.cepsandik.com → YOUR_SERVER_IP"
    echo "  - cepsandik.com → YOUR_SERVER_IP"
    echo ""
    read -p "Is DNS configured? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "Please configure DNS first, then run this script again."
        exit 1
    fi
    
    FIRST_RUN=true
else
    FIRST_RUN=false
fi

# Build and start services
echo ""
echo "🔨 Building Docker images (this may take 10-15 minutes)..."
docker compose build

echo ""
echo "🚀 Starting services..."
docker compose up -d

echo ""
echo "⏳ Waiting for services to be healthy (this may take 2-3 minutes)..."
sleep 30

# Check service health
echo ""
echo "🏥 Checking service health..."
docker compose ps

echo ""
echo "=========================================="

if [ "$FIRST_RUN" = true ]; then
    echo "⚠️  SSL CERTIFICATES NOT YET CONFIGURED"
    echo "=========================================="
    echo ""
    echo "Services are running, but SSL is not yet set up."
    echo "To obtain SSL certificates, run:"
    echo ""
    echo "  ./setup-ssl.sh"
    echo ""
    echo "After SSL setup, your services will be available at:"
    echo "  🔒 https://api.cepsandik.com/v1/"
    echo "  🔒 https://cepsandik.com"
else
    echo "✅ DEPLOYMENT COMPLETE"
    echo "=========================================="
    echo ""
    echo "Your services are running and accessible:"
    echo "  🔒 API: https://api.cepsandik.com/v1/"
    echo "  🔒 Frontend: https://cepsandik.com"
    echo ""
    echo "Test health endpoints:"
    echo "  curl https://api.cepsandik.com/user/actuator/health"
    echo "  curl https://api.cepsandik.com/community/actuator/health"
    echo "  curl https://api.cepsandik.com/election/actuator/health"
fi

echo ""
echo "📊 View logs:"
echo "  docker compose logs -f"
echo ""
echo "🔄 Restart services:"
echo "  docker compose restart"
echo ""
echo "🛑 Stop services:"
echo "  docker compose down"
echo ""
echo "=========================================="
