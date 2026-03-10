"""
ElectionGuard ile oy şifreleme (EncryptionMediator).
Stateless: Her çağrıda context ve manifest dışarıdan gelir.
"""

import json
import logging
from typing import Any

from electionguard.ballot import PlaintextBallot, PlaintextBallotContest, PlaintextBallotSelection
from electionguard.election import CiphertextElectionContext
from electionguard.encrypt import EncryptionDevice, EncryptionMediator
from electionguard.manifest import InternalManifest, Manifest

logger = logging.getLogger(__name__)

# Sabit EncryptionDevice — bu servis tek bir "cihaz" olarak tanımlanır
_DEVICE = EncryptionDevice("crypto-engine-device", 1, 1, "crypto-engine-location")


class BallotEncryptor:
    """ElectionGuard ile oy şifreleme işlemlerini yönetir."""

    @staticmethod
    def encrypt_ballot(
        ballot_data: dict[str, Any],
        ballot_id: str,
        ballot_style_id: str,
        context_json: str,
        manifest_json: str,
    ) -> dict[str, Any]:
        """
        Düz metin oy verisini ElGamal ile şifreler ve ZKP üretir.

        Args:
            ballot_data: Düz metin oy verisi (RSA çözüldükten sonra)
                {
                    "contests": [
                        {
                            "contest_id": "contest_1",
                            "selections": [
                                {"selection_id": "candidate_1", "vote": 1},
                                {"selection_id": "candidate_2", "vote": 0},
                            ]
                        }
                    ]
                }
            ballot_id: Benzersiz ballot kimliği (vote token)
            ballot_style_id: Ballot style ID
            context_json: CiphertextElectionContext (JSON string)
            manifest_json: Manifest (JSON string)

        Returns:
            {
                "ciphertext_ballot": str (JSON),
                "tracking_code": str,
                "zkp_proof": str (JSON),
            }
        """
        logger.info("Ballot şifreleniyor: ballot_id=%s", ballot_id)

        try:
            # 1. Context ve manifest'i deserialize et
            context = CiphertextElectionContext.from_json(context_json)
            manifest = Manifest.from_json(manifest_json)
            internal_manifest = InternalManifest(manifest)

            # 2. PlaintextBallot oluştur
            plaintext_ballot = BallotEncryptor._build_plaintext_ballot(
                ballot_data, ballot_id, ballot_style_id
            )

            # 3. EncryptionMediator ile şifrele
            mediator = EncryptionMediator(internal_manifest, context, _DEVICE)
            ciphertext_ballot = mediator.encrypt(plaintext_ballot)

            if ciphertext_ballot is None:
                raise RuntimeError("Ballot şifreleme başarısız — None döndü")

            # 4. Sonuçları hazırla
            result = {
                "ciphertext_ballot": ciphertext_ballot.to_json(),
                "tracking_code": str(ciphertext_ballot.tracking_hash) if hasattr(ciphertext_ballot, 'tracking_hash') else str(ciphertext_ballot.code),
                "zkp_proof": json.dumps({
                    "object_id": ciphertext_ballot.object_id,
                    "is_valid": ciphertext_ballot.is_valid_encryption(
                        context.manifest_hash,
                        context.elgamal_public_key,
                        context.crypto_extended_base_hash,
                    ),
                }),
            }

            logger.info(
                "Ballot başarıyla şifrelendi: ballot_id=%s, tracking_code=%s",
                ballot_id,
                result["tracking_code"][:16] + "...",
            )

            return result

        except Exception as e:
            logger.error("Ballot şifreleme hatası: %s", e, exc_info=True)
            raise

    @staticmethod
    def _build_plaintext_ballot(
        ballot_data: dict[str, Any],
        ballot_id: str,
        ballot_style_id: str,
    ) -> PlaintextBallot:
        """Dict formatındaki oy verisini ElectionGuard PlaintextBallot'a dönüştürür."""
        contests = []
        for contest_data in ballot_data.get("contests", []):
            selections = []
            for sel_data in contest_data.get("selections", []):
                selections.append(
                    PlaintextBallotSelection(
                        object_id=str(sel_data["selection_id"]),
                        vote=int(sel_data["vote"]),
                        is_placeholder_selection=False,
                    )
                )
            contests.append(
                PlaintextBallotContest(
                    object_id=str(contest_data["contest_id"]),
                    ballot_selections=selections,
                )
            )

        return PlaintextBallot(
            object_id=ballot_id,
            style_id=ballot_style_id,
            contests=contests,
        )
