"""
RSA anahtar yönetimi.
Anahtarlar Docker Volume'a persist edilir (/data/keys/).
Konteyner yeniden başlasa bile aynı keypair korunur.
"""

import os
import logging
from pathlib import Path
from typing import Optional

from Crypto.PublicKey import RSA

from app.config import get_config

logger = logging.getLogger(__name__)


class KeyManager:
    """RSA keypair yöneticisi — Volume-backed persistence."""

    def __init__(self, key_path: Optional[str] = None, key_size: Optional[int] = None):
        config = get_config()
        self._key_path = Path(key_path or config.key_path)
        self._key_size = key_size or config.rsa_key_size
        self._private_key_file = self._key_path / "rsa_private.pem"
        self._public_key_file = self._key_path / "rsa_public.pem"

        self._private_key: Optional[RSA.RsaKey] = None
        self._public_key: Optional[RSA.RsaKey] = None

        self._ensure_keys()

    def _ensure_keys(self) -> None:
        """Mevcut anahtarları yükle veya yoksa yeni üret."""
        self._key_path.mkdir(parents=True, exist_ok=True)

        if self._private_key_file.exists() and self._public_key_file.exists():
            self._load_keys()
        else:
            self._generate_keys()

    def _generate_keys(self) -> None:
        """Yeni RSA keypair üretir ve diske kaydeder."""
        logger.info(
            "RSA keypair üretiliyor (key_size=%d, path=%s)...",
            self._key_size,
            self._key_path,
        )

        key = RSA.generate(self._key_size)
        self._private_key = key
        self._public_key = key.publickey()

        # Private key dosyasını yaz (sadece owner okuyabilsin)
        with open(self._private_key_file, "wb") as f:
            f.write(key.export_key("PEM"))
        # Unix sistemlerde dosya izinleri
        try:
            os.chmod(self._private_key_file, 0o600)
        except OSError:
            pass  # Windows'ta izin değiştirme uyumsuzlukları

        # Public key dosyasını yaz
        with open(self._public_key_file, "wb") as f:
            f.write(key.publickey().export_key("PEM"))

        logger.info("RSA keypair başarıyla üretildi ve kaydedildi.")

    def _load_keys(self) -> None:
        """Mevcut anahtarları diskten yükler."""
        logger.info("RSA keypair diskten yükleniyor: %s", self._key_path)

        with open(self._private_key_file, "rb") as f:
            self._private_key = RSA.import_key(f.read())

        with open(self._public_key_file, "rb") as f:
            self._public_key = RSA.import_key(f.read())

        logger.info("RSA keypair başarıyla yüklendi.")

    @property
    def public_key_pem(self) -> str:
        """RSA public key'i PEM formatında döner."""
        assert self._public_key is not None, "Public key yüklenemedi"
        return self._public_key.export_key("PEM").decode("utf-8")

    def decrypt(self, ciphertext: bytes) -> bytes:
        """
        RSA-OAEP ile şifrelenmiş veriyi çözer.
        Mobil uygulamadan gelen transit şifreli oy verisi burada açılır.
        """
        from Crypto.Cipher import PKCS1_OAEP

        assert self._private_key is not None, "Private key yüklenemedi"

        cipher = PKCS1_OAEP.new(self._private_key)
        return cipher.decrypt(ciphertext)

    def decrypt_transit_payload(self, payload: bytes) -> bytes:
        """
        Transit payload: ya tek blok RSA-OAEP (kısa JSON, 256 bayt) ya da hibrit
        RSA-OAEP(32 bayt AES anahtarı) || IV(12) || AES-GCM şifre metni+tag(16).
        """
        rsa_ct_len = self._private_key.size_in_bytes() if self._private_key else 256
        if len(payload) == rsa_ct_len:
            return self.decrypt(payload)
        if len(payload) < rsa_ct_len + 12 + 16:
            raise ValueError(f"Geçersiz transit payload uzunluğu: {len(payload)}")

        from Crypto.Cipher import AES

        rsa_blob = payload[:rsa_ct_len]
        aes_key = self.decrypt(rsa_blob)
        if len(aes_key) != 32:
            raise ValueError(f"Beklenen 32 bayt AES anahtarı, gelen: {len(aes_key)}")

        iv = payload[rsa_ct_len : rsa_ct_len + 12]
        ct_and_tag = payload[rsa_ct_len + 12 :]
        tag = ct_and_tag[-16:]
        ct = ct_and_tag[:-16]

        cipher = AES.new(aes_key, AES.MODE_GCM, nonce=iv)
        return cipher.decrypt_and_verify(ct, tag)

    def encrypt_for_test(self, plaintext: bytes) -> bytes:
        """
        Test amaçlı: Public key ile RSA-OAEP şifreleme.
        Prodüksiyonda bu işlem mobil tarafta yapılır.
        """
        from Crypto.Cipher import PKCS1_OAEP

        assert self._public_key is not None, "Public key yüklenemedi"

        cipher = PKCS1_OAEP.new(self._public_key)
        return cipher.encrypt(plaintext)
