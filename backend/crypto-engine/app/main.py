"""
Crypto-Engine gRPC Server başlatıcısı.
"""

import logging
import sys
import os
from concurrent import futures

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc

# Proto generated modüllerinin yolunu sys.path'e ekle
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), "generated"))

from generated import crypto_pb2_grpc
from app.config import get_config
from app.services.key_manager import KeyManager
from app.grpc_handlers.crypto_servicer import CryptoServicer


def configure_logging():
    """Logging yapılandırması."""
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)],
    )


def serve():
    """gRPC sunucusunu başlatır."""
    configure_logging()
    logger = logging.getLogger(__name__)

    config = get_config()

    logger.info("=" * 60)
    logger.info("CepSandık Crypto-Engine başlatılıyor...")
    logger.info("  gRPC Port: %d", config.grpc_port)
    logger.info("  RSA Key Size: %d", config.rsa_key_size)
    logger.info("  Key Path: %s", config.key_path)
    logger.info("  Max Workers: %d", config.grpc_max_workers)
    logger.info("=" * 60)

    # RSA KeyManager başlat (Volume'dan yükle veya üret)
    key_manager = KeyManager()
    logger.info("RSA KeyManager hazır.")

    # gRPC server oluştur
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=config.grpc_max_workers),
        options=[
            ("grpc.max_send_message_length", 50 * 1024 * 1024),  # 50MB
            ("grpc.max_receive_message_length", 50 * 1024 * 1024),  # 50MB
        ],
    )

    # CryptoService handler'ı kaydet
    crypto_servicer = CryptoServicer(key_manager)
    crypto_pb2_grpc.add_CryptoServiceServicer_to_server(crypto_servicer, server)

    # Health check servisi ekle
    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    health_servicer.set(
        "cepsandik.crypto.CryptoService",
        health_pb2.HealthCheckResponse.SERVING,
    )

    # Porta bağlan ve başlat
    listen_addr = f"[::]:{config.grpc_port}"
    server.add_insecure_port(listen_addr)
    server.start()

    logger.info("Crypto-Engine gRPC sunucusu çalışıyor: %s", listen_addr)

    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("Sunucu kapatılıyor...")
        server.stop(grace=5)
        logger.info("Sunucu kapatıldı.")


if __name__ == "__main__":
    serve()
