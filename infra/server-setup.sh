#!/bin/bash
# CepSandık Production Deployment Script
# Run this on your Digital Ocean Droplet

set -e

echo "=========================================="
echo "🚀 CepSandık Production Deployment"
echo "=========================================="
echo ""

# Update system
echo "📦 Updating system packages..."
apt update && apt upgrade -y

# Install Docker
echo "🐳 Installing Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    rm get-docker.sh
    systemctl start docker
    systemctl enable docker
    echo "✅ Docker installed"
else
    echo "✅ Docker already installed"
fi

# Install Docker Compose
echo "🔧 Installing Docker Compose..."
if ! command -v docker compose &> /dev/null; then
    apt install -y docker-compose-plugin
    echo "✅ Docker Compose installed"
else
    echo "✅ Docker Compose already installed"
fi

# Install Git
echo "📥 Installing Git..."
if ! command -v git &> /dev/null; then
    apt install -y git
    echo "✅ Git installed"
else
    echo "✅ Git already installed"
fi

# Install dig for DNS checks
echo "🔍 Installing DNS tools..."
apt install -y dnsutils curl

# Clone repository
echo ""
echo "📂 Setting up project..."
cd /opt

if [ -d "cepsandik" ]; then
    echo "⚠️  Project directory already exists. Updating..."
    cd cepsandik
    git pull
else
    echo "📥 Cloning repository..."
    git clone https://github.com/mfbilgin/cepsandik.git
    cd cepsandik
fi

cd infra

# Create required directories
echo "📁 Creating directories..."
mkdir -p certbot/conf certbot/www logs/nginx
chmod -R 755 certbot logs

echo ""
echo "=========================================="
echo "✅ Server preparation complete!"
echo "=========================================="
echo ""
echo "📝 Next steps:"
echo "1. Copy your .env file to /opt/cepsandik/infra/"
echo "2. Run: cd /opt/cepsandik/infra && ./quick-start.sh"
echo ""
