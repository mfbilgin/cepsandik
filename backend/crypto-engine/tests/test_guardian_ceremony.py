"""
Guardian Ceremony ve Encryption testleri (ElectionGuard).
Not: Bu testler ElectionGuard SDK'nın yüklü olmasını gerektirir.
"""

import json
import pytest

from app.services.guardian_ceremony import GuardianCeremony


class TestGuardianCeremony:
    """Guardian Key Ceremony testleri."""

    def test_basic_ceremony(self):
        """3 guardian, 2 quorum ile key ceremony."""
        ceremony = GuardianCeremony(number_of_guardians=3, quorum=2)
        result = ceremony.perform_ceremony()

        assert "joint_key" in result
        assert result["joint_key"]  # boş olmamalı
        assert len(result["guardian_records"]) == 3
        assert result["ceremony_details"]["n"] == 3
        assert result["ceremony_details"]["q"] == 2

    def test_minimum_ceremony(self):
        """1 guardian, 1 quorum ile en basit key ceremony."""
        ceremony = GuardianCeremony(number_of_guardians=1, quorum=1)
        result = ceremony.perform_ceremony()

        assert "joint_key" in result
        assert len(result["guardian_records"]) == 1

    def test_guardian_records_serializable(self):
        """Guardian record'larının JSON serileştirilebilir olduğunu doğrula."""
        ceremony = GuardianCeremony(number_of_guardians=2, quorum=2)
        result = ceremony.perform_ceremony()

        for record in result["guardian_records"]:
            assert "guardian_id" in record
            assert "serialized_guardian" in record
            # JSON parse edilebilmeli
            parsed = json.loads(record["serialized_guardian"])
            assert "guardian_id" in parsed

    def test_invalid_quorum_greater_than_guardians(self):
        """Quorum > N olduğunda ValueError fırlatılmalı."""
        with pytest.raises(ValueError, match="büyük olamaz"):
            GuardianCeremony(number_of_guardians=2, quorum=3)

    def test_invalid_quorum_zero(self):
        """Quorum=0 olduğunda ValueError fırlatılmalı."""
        with pytest.raises(ValueError, match="en az 1"):
            GuardianCeremony(number_of_guardians=2, quorum=0)
