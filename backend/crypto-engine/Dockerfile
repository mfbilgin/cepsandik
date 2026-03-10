# =============================================================
# Crypto-Engine Dockerfile
# Multi-stage build: proto derleme → runtime
# =============================================================

# ---------- Stage 1: Proto Derleme ----------
FROM python:3.11-slim AS proto-builder

WORKDIR /build

# grpcio-tools — protoc Python plugin
RUN pip install --no-cache-dir grpcio-tools==1.62.1

# Proto dosyalarını kopyala ve derle
COPY protos/ ./protos/
RUN mkdir -p generated && \
    python -m grpc_tools.protoc \
      --proto_path=protos \
      --python_out=generated \
      --grpc_python_out=generated \
      protos/crypto.proto && \
    touch generated/__init__.py

# ---------- Stage 2: Runtime ----------
FROM python:3.11-slim

# Sistem bağımlılıkları (ElectionGuard için GMP)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      libgmp-dev \
      libmpfr-dev \
      libmpc-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Python bağımlılıkları
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Proto generated dosyaları kopyala
COPY --from=proto-builder /build/generated/ ./generated/

# Uygulama kaynak kodu
COPY app/ ./app/

# Key dizini (Volume mount noktası)
RUN mkdir -p /data/keys

# gRPC portu
EXPOSE 50051

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=30s \
    CMD python -c "import grpc; ch=grpc.insecure_channel('localhost:50051'); grpc.channel_ready_future(ch).result(timeout=5)" || exit 1

# Başlat
CMD ["python", "-m", "app.main"]
