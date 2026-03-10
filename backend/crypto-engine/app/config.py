"""
Crypto-Engine konfigürasyon modülü.
Tüm ayarlar environment variable'lardan okunur.
Not: pydantic-settings kullanılamaz çünkü electionguard pydantic v1.9.0'a kilitli.
"""

import os
from dataclasses import dataclass


@dataclass
class CryptoConfig:
    """Crypto-Engine konfigürasyonu (env-based)."""

    # gRPC Server
    grpc_port: int = 50051
    grpc_max_workers: int = 10

    # RSA Key Management
    rsa_key_size: int = 2048
    key_path: str = "/data/keys"

    # ElectionGuard Defaults
    default_guardian_count: int = 3
    default_quorum: int = 2

    @classmethod
    def from_env(cls) -> "CryptoConfig":
        """Environment variable'lardan konfigürasyon oluşturur."""
        return cls(
            grpc_port=int(os.environ.get("CRYPTO_GRPC_PORT", "50051")),
            grpc_max_workers=int(os.environ.get("CRYPTO_GRPC_MAX_WORKERS", "10")),
            rsa_key_size=int(os.environ.get("CRYPTO_RSA_KEY_SIZE", "2048")),
            key_path=os.environ.get("CRYPTO_KEY_PATH", "/data/keys"),
            default_guardian_count=int(os.environ.get("CRYPTO_DEFAULT_GUARDIAN_COUNT", "3")),
            default_quorum=int(os.environ.get("CRYPTO_DEFAULT_QUORUM", "2")),
        )


# Singleton
_config: CryptoConfig | None = None


def get_config() -> CryptoConfig:
    """Thread-safe config singleton."""
    global _config
    if _config is None:
        _config = CryptoConfig.from_env()
    return _config
