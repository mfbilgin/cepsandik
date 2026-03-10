"""
RSA KeyManager testleri.
- Anahtar üretimi ve persist testi
- Şifreleme/çözme roundtrip testi
"""

import os
import tempfile
import pytest
from pathlib import Path

from app.services.key_manager import KeyManager


class TestKeyManager:
    """KeyManager birim testleri."""

    def test_key_generation(self, tmp_path):
        """Yeni keypair üretilip diske kaydedildiğini doğrula."""
        km = KeyManager(key_path=str(tmp_path), key_size=2048)

        assert (tmp_path / "rsa_private.pem").exists()
        assert (tmp_path / "rsa_public.pem").exists()
        assert km.public_key_pem.startswith("-----BEGIN PUBLIC KEY-----")

    def test_key_persistence(self, tmp_path):
        """Anahtar disk persistansını doğrula — restart simülasyonu."""
        # İlk oluşturma
        km1 = KeyManager(key_path=str(tmp_path), key_size=2048)
        pem1 = km1.public_key_pem

        # İkinci oluşturma (diskten yükleme)
        km2 = KeyManager(key_path=str(tmp_path), key_size=2048)
        pem2 = km2.public_key_pem

        # Aynı anahtar olmalı
        assert pem1 == pem2

    def test_encrypt_decrypt_roundtrip(self, tmp_path):
        """RSA-OAEP şifreleme/çözme roundtrip testi."""
        km = KeyManager(key_path=str(tmp_path), key_size=2048)

        original = b'{"contest_id": "c1", "selection_id": "s1", "vote": 1}'
        encrypted = km.encrypt_for_test(original)
        decrypted = km.decrypt(encrypted)

        assert decrypted == original

    def test_encrypt_decrypt_turkish_characters(self, tmp_path):
        """Türkçe karakter içeren veri ile roundtrip testi."""
        km = KeyManager(key_path=str(tmp_path), key_size=2048)

        original = 'Seçim başladı — Oy kullanınız! İğneleyici ışık.'.encode("utf-8")
        encrypted = km.encrypt_for_test(original)
        decrypted = km.decrypt(encrypted)

        assert decrypted == original

    def test_different_key_sizes(self, tmp_path):
        """Farklı RSA key boyutları için çalıştığını doğrula."""
        for key_size in [2048, 4096]:
            key_path = tmp_path / f"keys_{key_size}"
            km = KeyManager(key_path=str(key_path), key_size=key_size)

            original = b"test data"
            encrypted = km.encrypt_for_test(original)
            decrypted = km.decrypt(encrypted)

            assert decrypted == original

    def test_public_key_pem_format(self, tmp_path):
        """PEM formatının doğruluğunu kontrol et."""
        km = KeyManager(key_path=str(tmp_path), key_size=2048)
        pem = km.public_key_pem

        assert "-----BEGIN PUBLIC KEY-----" in pem
        assert "-----END PUBLIC KEY-----" in pem
