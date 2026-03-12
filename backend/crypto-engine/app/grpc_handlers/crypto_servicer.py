"""
gRPC CryptoService handler implementasyonu.
Proto tanımlarındaki tüm RPC'leri karşılar.

Not: electionguard PyPI paketi election_builder modülü içermez.
CiphertextElectionContext doğrudan oluşturulur.
"""

import json
import logging
from datetime import datetime, timezone
from typing import Optional

import grpc

# Generated proto imports — protoc çıktıları
from generated import crypto_pb2
from generated import crypto_pb2_grpc

from app.services.key_manager import KeyManager
from app.services.guardian_ceremony import GuardianCeremony
from app.services.encryption import BallotEncryptor
from app.services.decryption import TallyDecryptor

from electionguard.election import CiphertextElectionContext
from electionguard.manifest import (
    Manifest,
    InternalManifest,
    ContestDescription,
    SelectionDescription,
    BallotStyle,
    GeopoliticalUnit,
    ElectionType as EGElectionType,
    ReportingUnitType,
    VoteVariationType,
)
from electionguard.hash import hash_elems

logger = logging.getLogger(__name__)


class CryptoServicer(crypto_pb2_grpc.CryptoServiceServicer):
    """gRPC CryptoService implementasyonu."""

    def __init__(self, key_manager: KeyManager):
        self._key_manager = key_manager
        logger.info("CryptoServicer başlatıldı.")

    def GetPublicKey(self, request, context):
        """RSA public key döner (transit şifreleme için)."""
        logger.info("GetPublicKey çağrıldı.")
        try:
            pem = self._key_manager.public_key_pem
            return crypto_pb2.GetPublicKeyResponse(rsa_public_key_pem=pem)
        except Exception as e:
            logger.error("GetPublicKey hatası: %s", e, exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Public key alınamadı: {e}")
            return crypto_pb2.GetPublicKeyResponse()

    def SetupElection(self, request, context):
        """
        Seçim için ElectionGuard key ceremony yapar.
        Manifest, context ve guardian record'larını üretir.
        """
        logger.info(
            "SetupElection çağrıldı: election_id=%s, N=%d, Q=%d",
            request.election_id,
            request.number_of_guardians,
            request.quorum,
        )

        try:
            n = request.number_of_guardians
            q = request.quorum

            # Validasyon
            if n < 1:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("Guardian sayısı en az 1 olmalıdır.")
                return crypto_pb2.SetupElectionResponse()
            if q < 1 or q > n:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details(f"Quorum 1..{n} aralığında olmalıdır.")
                return crypto_pb2.SetupElectionResponse()

            # 1. ElectionGuard Manifest oluştur
            manifest = self._build_manifest(request)

            # 2. Guardian key ceremony
            ceremony = GuardianCeremony(n, q)
            ceremony_result = ceremony.perform_ceremony()

            # 3. CiphertextElectionContext'i doğrudan oluştur
            joint_public_key = ceremony_result["joint_public_key_obj"]
            commitment_hash = ceremony_result["commitment_hash"]
            manifest_hash = manifest.crypto_hash()

            # ElectionGuard spec'e göre hash zincirleri
            crypto_base_hash = hash_elems(n, q, manifest_hash)
            crypto_extended_base_hash = hash_elems(
                crypto_base_hash, commitment_hash
            )

            election_context = CiphertextElectionContext(
                number_of_guardians=n,
                quorum=q,
                elgamal_public_key=joint_public_key,
                commitment_hash=commitment_hash,
                manifest_hash=manifest_hash,
                crypto_base_hash=crypto_base_hash,
                crypto_extended_base_hash=crypto_extended_base_hash,
                extended_data=None,
            )

            # 4. Response hazırla
            guardian_records = [
                crypto_pb2.GuardianRecord(
                    guardian_id=gr["guardian_id"],
                    serialized_guardian=gr["serialized_guardian"],
                )
                for gr in ceremony_result["guardian_records"]
            ]

            response = crypto_pb2.SetupElectionResponse(
                election_guard_context=election_context.to_json(),
                joint_public_key=ceremony_result["joint_key"],
                election_manifest=manifest.to_json(),
                guardian_records=guardian_records,
            )

            logger.info(
                "SetupElection başarılı: election_id=%s, %d guardian",
                request.election_id,
                len(guardian_records),
            )

            return response

        except Exception as e:
            logger.error("SetupElection hatası: %s", e, exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Election setup hatası: {e}")
            return crypto_pb2.SetupElectionResponse()

    def EncryptBallot(self, request, context):
        """
        RSA şifreli oy verisini çözer ve ElGamal ile şifreler.
        Stateless: Context ve manifest request'ten gelir.
        """
        logger.info(
            "EncryptBallot çağrıldı: election_id=%s, ballot_id=%s",
            request.election_id,
            request.ballot_id,
        )

        try:
            # 1. RSA ile transit şifrelemeyi çöz
            if request.rsa_encrypted_payload:
                plaintext_bytes = self._key_manager.decrypt(
                    request.rsa_encrypted_payload
                )
                ballot_data = json.loads(plaintext_bytes.decode("utf-8"))
            else:
                logger.warning(
                    "RSA şifreli payload boş — geliştirme modunda çalışılıyor"
                )
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("rsa_encrypted_payload boş olamaz")
                return crypto_pb2.EncryptBallotResponse()

            # 2. ElectionGuard ile şifrele
            result = BallotEncryptor.encrypt_ballot(
                ballot_data=ballot_data,
                ballot_id=request.ballot_id,
                ballot_style_id=request.ballot_style_id or "ballot-style-1",
                context_json=request.election_guard_context,
                manifest_json=request.election_manifest,
            )

            # 3. Bellekten temizle (stateless prensibi)
            del plaintext_bytes
            del ballot_data

            return crypto_pb2.EncryptBallotResponse(
                ciphertext_ballot=result["ciphertext_ballot"],
                tracking_code=result["tracking_code"],
                zkp_proof=result["zkp_proof"],
            )

        except Exception as e:
            logger.error("EncryptBallot hatası: %s", e, exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Ballot şifreleme hatası: {e}")
            return crypto_pb2.EncryptBallotResponse()

    def TallyElection(self, request, context):
        """
        Tüm şifreli oyları homomorfik olarak toplar ve threshold decryption ile çözer.
        """
        logger.info(
            "TallyElection çağrıldı: election_id=%s, ballot_count=%d",
            request.election_id,
            len(request.ciphertext_ballots),
        )

        try:
            # Guardian record'larını dict formatına çevir
            guardian_records = [
                {
                    "guardian_id": gr.guardian_id,
                    "serialized_guardian": gr.serialized_guardian,
                }
                for gr in request.guardian_records
            ]

            # Tally çözümle
            result = TallyDecryptor.tally_election(
                ciphertext_ballots_json=list(request.ciphertext_ballots),
                guardian_records_json=guardian_records,
                context_json=request.election_guard_context,
                manifest_json=request.election_manifest,
                quorum=request.quorum,
            )

            # Response hazırla
            contest_results = []
            for contest in result["results"]:
                selections = [
                    crypto_pb2.SelectionResult(
                        selection_id=s["selection_id"],
                        tally=s["tally"],
                    )
                    for s in contest["selections"]
                ]
                contest_results.append(
                    crypto_pb2.ContestResult(
                        contest_id=contest["contest_id"],
                        selections=selections,
                    )
                )

            return crypto_pb2.TallyElectionResponse(
                results=contest_results,
                tally_proof=result["tally_proof"],
            )

        except Exception as e:
            logger.error("TallyElection hatası: %s", e, exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Tally hatası: {e}")
            return crypto_pb2.TallyElectionResponse()

    @staticmethod
    def _build_manifest(request) -> Manifest:
        """
        gRPC SetupElectionRequest'ten ElectionGuard Manifest oluşturur.
        """
        # GeopoliticalUnit (seçim kapsamı)
        gp_unit = GeopoliticalUnit(
            object_id=f"gp-unit-{request.election_id}",
            name=f"Election {request.election_id}",
            type=ReportingUnitType.other,
        )

        # Contest (yarışma) tanımları
        contests = []
        for i, contest_info in enumerate(request.contests):
            # Selection (aday/seçenek) tanımları
            selections = [
                SelectionDescription(
                    object_id=sel_id,
                    candidate_id=sel_id,
                    sequence_order=j,
                )
                for j, sel_id in enumerate(contest_info.selection_ids)
            ]

            contest = ContestDescription(
                object_id=contest_info.contest_id,
                electoral_district_id=gp_unit.object_id,
                sequence_order=i,
                vote_variation=VoteVariationType.one_of_m
                if contest_info.number_elected == 1
                else VoteVariationType.n_of_m,
                number_elected=contest_info.number_elected,
                votes_allowed=contest_info.number_elected,
                name=contest_info.name or f"Contest {contest_info.contest_id}",
                ballot_selections=selections,
            )
            contests.append(contest)

        # BallotStyle
        ballot_style = BallotStyle(
            object_id="ballot-style-1",
            geopolitical_unit_ids=[gp_unit.object_id],
        )

        # Manifest oluştur
        manifest = Manifest(
            election_scope_id=f"election-{request.election_id}",
            spec_version="v0.95",
            type=EGElectionType.general,
            start_date=datetime(2024, 1, 1, tzinfo=timezone.utc),
            end_date=datetime(2030, 12, 31, 23, 59, 59, tzinfo=timezone.utc),
            geopolitical_units=[gp_unit],
            contests=contests,
            ballot_styles=[ballot_style],
            parties=[],
            candidates=[],
            name=None,
            contact_information=None,
        )

        return manifest
