"""
Crypto-Engine konfigürasyon modülü.
Tüm ayarlar environment variable'lardan okunur.
"""

from pydantic_settings import BaseSettings
from pydantic import Field


class CryptoConfig(BaseSettings):
    """Crypto-Engine konfigürasyonu (env-based)."""

    # gRPC Server
    grpc_port: int = Field(default=50051, alias="CRYPTO_GRPC_PORT")
    grpc_max_workers: int = Field(default=10, alias="CRYPTO_GRPC_MAX_WORKERS")

    # RSA Key Management
    rsa_key_size: int = Field(default=2048, alias="CRYPTO_RSA_KEY_SIZE")
    key_path: str = Field(default="/data/keys", alias="CRYPTO_KEY_PATH")

    # ElectionGuard Defaults
    default_guardian_count: int = Field(default=3, alias="CRYPTO_DEFAULT_GUARDIAN_COUNT")
    default_quorum: int = Field(default=2, alias="CRYPTO_DEFAULT_QUORUM")

    model_config = {
        "env_prefix": "",
        "case_sensitive": True,
    }


# Singleton
_config: CryptoConfig | None = None


def get_config() -> CryptoConfig:
    """Thread-safe config singleton."""
    global _config
    if _config is None:
        _config = CryptoConfig()
    return _config
