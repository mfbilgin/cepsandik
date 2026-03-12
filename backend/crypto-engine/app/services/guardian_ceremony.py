"""
ElectionGuard Guardian Key Ceremony.
N guardian ile anahtar töreni yapılır,
Q (quorum) eşik değeriyle threshold şifreleme desteklenir.
"""

import json
import logging
from typing import Any, Optional

from electionguard.hash import hash_elems

from electionguard.guardian import Guardian
from electionguard.key_ceremony import CeremonyDetails

logger = logging.getLogger(__name__)


class GuardianCeremony:
    """ElectionGuard guardian anahtar töreni yöneticisi."""

    def __init__(self, number_of_guardians: int, quorum: int):
        """
        Args:
            number_of_guardians: Toplam mütevelli sayısı (N)
            quorum: Eşik değer — minimum kaç mütevelli gerekli (Q)
        """
        if quorum > number_of_guardians:
            raise ValueError(
                f"Quorum ({quorum}) guardian sayısından ({number_of_guardians}) "
                f"büyük olamaz."
            )
        if quorum < 1:
            raise ValueError("Quorum en az 1 olmalıdır.")

        self._n = number_of_guardians
        self._q = quorum
        self._guardians: list[Guardian] = []
        self._ceremony_details = CeremonyDetails(number_of_guardians, quorum)

    def perform_ceremony(self) -> dict[str, Any]:
        """
        Tam key ceremony'yi çalıştırır (guardian-to-guardian doğrudan iletişim).

        Returns:
            {
                "joint_key": ElGamal joint public key (hex string),
                "election_joint_key": ElectionJointKey nesnesi,
                "guardian_records": [
                    {"guardian_id": str, "serialized_guardian": str (JSON)},
                    ...
                ],
                "ceremony_details": {"n": int, "q": int}
            }
        """
        logger.info(
            "Guardian key ceremony başlatılıyor (N=%d, Q=%d)...",
            self._n, self._q,
        )

        # 1. Guardian'ları oluştur
        self._guardians = [
            Guardian.from_nonce(
                id=f"guardian_{i}",
                sequence_order=i,
                number_of_guardians=self._n,
                quorum=self._q,
            )
            for i in range(self._n)
        ]
        logger.info("%d guardian oluşturuldu.", len(self._guardians))

        # 2. Anahtar paylaşımı — her guardian diğerlerinin public key'ini kaydeder
        for guardian in self._guardians:
            for other in self._guardians:
                if guardian.id != other.id:
                    guardian.save_guardian_key(other.share_key())

        logger.info("Guardian anahtarları paylaşıldı.")

        # 3. Partial key backup oluşturma
        for guardian in self._guardians:
            guardian.generate_election_partial_key_backups()

        logger.info("Partial key backup'lar üretildi.")

        # 4. Backup paylaşımı — her guardian backup'ını diğerlerine gönderir
        for guardian in self._guardians:
            for other in self._guardians:
                if guardian.id != other.id:
                    backup = guardian.share_election_partial_key_backup(other.id)
                    if backup is not None:
                        other.save_election_partial_key_backup(backup)

        logger.info("Backup'lar paylaşıldı ve kaydedildi.")

        # 5. Backup doğrulama
        for guardian in self._guardians:
            for other in self._guardians:
                if guardian.id != other.id:
                    verification = guardian.verify_election_partial_key_backup(
                        other.id
                    )
                    if verification is not None:
                        other.save_election_partial_key_verification(verification)

        logger.info("Backup doğrulamaları tamamlandı.")

        # 6. Joint key üretimi
        # Guardian.publish_joint_key() doğrudan ElementModP döner
        joint_public_key = self._guardians[0].publish_joint_key()
        if joint_public_key is None:
            raise RuntimeError("Key ceremony başarısız — joint key üretilemedi.")

        # Commitment hash: tüm guardian'ların election commitment'larından hesaplanır
        all_commitments = []
        for guardian in self._guardians:
            record = guardian.publish()
            all_commitments.extend(record.election_commitments)
        commitment_hash = hash_elems(all_commitments)

        logger.info("Key ceremony başarılı. Joint key üretildi.")

        # 7. Guardian state'lerini serileştir
        guardian_records = []
        for guardian in self._guardians:
            serialized = self._serialize_guardian(guardian, self._n, self._q)
            guardian_records.append({
                "guardian_id": guardian.id,
                "serialized_guardian": serialized,
            })

        return {
            "joint_key": str(joint_public_key),
            "joint_public_key_obj": joint_public_key,
            "commitment_hash": commitment_hash,
            "guardian_records": guardian_records,
            "ceremony_details": {"n": self._n, "q": self._q},
        }

    @property
    def guardians(self) -> list[Guardian]:
        """Oluşturulmuş guardian nesnelerini döner."""
        return self._guardians

    @staticmethod
    def _serialize_guardian(guardian: Guardian, n: int, q: int) -> str:
        """
        Guardian state'ini JSON string'e dönüştürür.
        Bu state, tally aşamasında guardian'ı yeniden oluşturmak için kullanılır.
        """
        try:
            guardian_record = guardian.publish()
            return json.dumps({
                "guardian_id": guardian.id,
                "sequence_order": guardian.sequence_order,
                "number_of_guardians": n,
                "quorum": q,
                "guardian_record": {
                    "guardian_id": guardian_record.guardian_id,
                    "sequence_order": guardian_record.sequence_order,
                    "election_public_key": str(guardian_record.election_public_key),
                    "election_commitments": [
                        str(c) for c in guardian_record.election_commitments
                    ],
                },
            })
        except Exception as e:
            logger.error("Guardian serileştirme hatası: %s", e)
            return json.dumps({
                "guardian_id": guardian.id,
                "sequence_order": guardian.sequence_order,
                "number_of_guardians": n,
                "quorum": q,
            })
