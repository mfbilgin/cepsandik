"""
ElectionGuard ile threshold decryption (tally).
Seçim kapandığında çağrılır — guardian key share'leri Java'dan gelir.
"""

import json
import logging
from typing import Any

from electionguard.ballot import CiphertextBallot, SubmittedBallot, BallotBoxState
from electionguard.election import CiphertextElectionContext
from electionguard.manifest import InternalManifest, Manifest
from electionguard.tally import CiphertextTally
from electionguard.decryption_mediator import DecryptionMediator
from electionguard.guardian import Guardian

logger = logging.getLogger(__name__)


class TallyDecryptor:
    """ElectionGuard ile threshold decryption yöneticisi."""

    @staticmethod
    def tally_election(
        ciphertext_ballots_json: list[str],
        guardian_records_json: list[dict[str, str]],
        context_json: str,
        manifest_json: str,
        quorum: int,
    ) -> dict[str, Any]:
        """
        Tüm şifreli oyları homomorfik olarak toplar ve
        guardian key share'leri ile sonuçları çözer.

        Args:
            ciphertext_ballots_json: Her biri CiphertextBallot JSON string'i
            guardian_records_json: [{"guardian_id": str, "serialized_guardian": str}]
            context_json: CiphertextElectionContext JSON
            manifest_json: Manifest JSON
            quorum: Eşik değer

        Returns:
            {
                "results": [
                    {
                        "contest_id": str,
                        "selections": [
                            {"selection_id": str, "tally": int}
                        ]
                    }
                ],
                "tally_proof": str (JSON)
            }
        """
        logger.info(
            "Tally çözümlemesi başlatılıyor: %d ballot, %d guardian, quorum=%d",
            len(ciphertext_ballots_json),
            len(guardian_records_json),
            quorum,
        )

        try:
            # 1. Context ve manifest'i deserialize et
            context = CiphertextElectionContext.from_json(context_json)
            manifest = Manifest.from_json(manifest_json)
            internal_manifest = InternalManifest(manifest)

            # 2. Ciphertext ballot'ları deserialize et
            submitted_ballots: list[SubmittedBallot] = []
            for ballot_json in ciphertext_ballots_json:
                ciphertext = CiphertextBallot.from_json(ballot_json)
                # CiphertextBallot'u SubmittedBallot'a dönüştür (CAST durumunda)
                submitted = SubmittedBallot(
                    object_id=ciphertext.object_id,
                    style_id=ciphertext.style_id,
                    manifest_hash=ciphertext.manifest_hash,
                    code_seed=ciphertext.code_seed,
                    contests=ciphertext.contests,
                    code=ciphertext.code,
                    timestamp=ciphertext.timestamp,
                    crypto_hash=ciphertext.crypto_hash,
                    nonce=ciphertext.nonce,
                    state=BallotBoxState.CAST,
                )
                submitted_ballots.append(submitted)

            # 3. CiphertextTally oluştur (homomorfik toplama)
            tally = CiphertextTally(
                "election_tally",
                internal_manifest,
                context,
            )

            for ballot in submitted_ballots:
                tally_result = tally.append(ballot)
                if not tally_result:
                    logger.warning(
                        "Ballot tally'ye eklenemedi: %s", ballot.object_id
                    )

            # 4. Guardian'ları yeniden oluştur
            guardians = TallyDecryptor._deserialize_guardians(
                guardian_records_json
            )

            if len(guardians) < quorum:
                raise RuntimeError(
                    f"Yeterli guardian yok: {len(guardians)} < {quorum} (quorum)"
                )

            # 5. DecryptionMediator ile threshold decryption
            mediator = DecryptionMediator(
                "decryption_mediator",
                context,
            )

            # Her guardian'ı mediator'a kaydet
            for guardian in guardians:
                mediator.announce(guardian)

            # Partial decryption share'ları topla
            decrypted_tally = mediator.get_plaintext_tally(tally, internal_manifest)

            if decrypted_tally is None:
                raise RuntimeError("Tally çözümlemesi başarısız — decrypted_tally None")

            # 6. Sonuçları formatla
            results = []
            for contest_id, contest in decrypted_tally.contests.items():
                selections = []
                for selection_id, selection in contest.selections.items():
                    selections.append({
                        "selection_id": selection_id,
                        "tally": selection.tally,
                    })
                results.append({
                    "contest_id": contest_id,
                    "selections": selections,
                })

            logger.info("Tally başarıyla çözüldü: %d contest", len(results))

            return {
                "results": results,
                "tally_proof": json.dumps({
                    "total_ballots": len(submitted_ballots),
                    "guardians_used": len(guardians),
                    "quorum": quorum,
                }),
            }

        except Exception as e:
            logger.error("Tally çözümleme hatası: %s", e, exc_info=True)
            raise

    @staticmethod
    def _deserialize_guardians(
        guardian_records: list[dict[str, str]],
    ) -> list[Guardian]:
        """
        Serileştirilmiş guardian state'lerinden Guardian nesneleri oluşturur.
        """
        guardians = []
        for record in guardian_records:
            try:
                state = json.loads(record["serialized_guardian"])
                guardian = Guardian.from_nonce(
                    id=state["guardian_id"],
                    sequence_order=state["sequence_order"],
                    number_of_guardians=state["number_of_guardians"],
                    quorum=state["quorum"],
                )
                guardians.append(guardian)
            except Exception as e:
                logger.error(
                    "Guardian deserialize hatası (%s): %s",
                    record.get("guardian_id", "unknown"),
                    e,
                )
        return guardians
