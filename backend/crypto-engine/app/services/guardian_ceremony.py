"""
ElectionGuard Guardian Key Ceremony.
N guardian ile anahtar töreni yapılır,
Q (quorum) eşik değeriyle threshold şifreleme desteklenir.
"""

import json
import logging
from typing import Any, Optional

from electionguard.guardian import Guardian
from electionguard.key_ceremony import CeremonyDetails
from electionguard.key_ceremony_mediator import KeyCeremonyMediator
from electionguard.utils import get_optional

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
        self._mediator: Optional[KeyCeremonyMediator] = None

    def perform_ceremony(self) -> dict[str, Any]:
        """
        Tam key ceremony'yi çalıştırır.

        Returns:
            {
                "joint_key": ElGamal joint public key (hex string),
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

        # 2. Mediator ile key ceremony'yi yönet
        self._mediator = KeyCeremonyMediator(
            "key_ceremony_mediator",
            self._ceremony_details,
        )

        # Adım 1: Attendance — tüm guardian'ları kaydet
        for guardian in self._guardians:
            self._mediator.announce(guardian.share_key())

        # Adım 2: Key sharing — her guardian diğerleriyle anahtar paylaşır
        for guardian in self._guardians:
            announced_keys = self._mediator.share_announced()
            for key in get_optional(announced_keys):
                if key.owner_id != guardian.id:
                    guardian.save_guardian_key(key)

        # Adım 3: Her guardian partial key backup'larını paylaşır
        for guardian in self._guardians:
            for other_guardian in self._guardians:
                if guardian.id != other_guardian.id:
                    backup = guardian.share_key_backup(other_guardian.id)
                    if backup is not None:
                        self._mediator.receive_backups(backup)

        # Adım 4: Her guardian aldığı backup'ları doğrular
        for guardian in self._guardians:
            backups = self._mediator.share_backups(guardian.id)
            if backups is not None:
                for backup in backups:
                    verification = guardian.verify_key_backup(backup)
                    if verification is not None:
                        self._mediator.receive_backup_verifications(verification)

        # 5. Joint key'i üret
        joint_key = self._mediator.publish_joint_key()
        if joint_key is None:
            raise RuntimeError("Key ceremony başarısız — joint key üretilemedi.")

        logger.info("Key ceremony başarılı. Joint key üretildi.")

        # 6. Guardian state'lerini serileştir
        guardian_records = []
        for guardian in self._guardians:
            serialized = self._serialize_guardian(guardian)
            guardian_records.append({
                "guardian_id": guardian.id,
                "serialized_guardian": serialized,
            })

        return {
            "joint_key": str(joint_key.joint_public_key),
            "guardian_records": guardian_records,
            "ceremony_details": {"n": self._n, "q": self._q},
        }

    @property
    def guardians(self) -> list[Guardian]:
        """Oluşturulmuş guardian nesnelerini döner."""
        return self._guardians

    @staticmethod
    def _serialize_guardian(guardian: Guardian) -> str:
        """
        Guardian state'ini JSON string'e dönüştürür.
        Bu state, tally aşamasında guardian'ı yeniden oluşturmak için kullanılır.
        """
        try:
            # Guardian'ın export edilebilir state'ini al
            guardian_record = guardian.publish()
            return json.dumps({
                "guardian_id": guardian.id,
                "sequence_order": guardian.sequence_order,
                "number_of_guardians": guardian._ceremony_details.number_of_guardians,
                "quorum": guardian._ceremony_details.quorum,
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
            # Fallback: temel bilgileri kaydet
            return json.dumps({
                "guardian_id": guardian.id,
                "sequence_order": guardian.sequence_order,
                "number_of_guardians": guardian._ceremony_details.number_of_guardians,
                "quorum": guardian._ceremony_details.quorum,
            })
