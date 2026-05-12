-- Sprint 5.C.2.5 — Distributed tally decryption session state.
-- Election kapandığında server crypto-engine'in `StartTallyDecryptionSession`
-- RPC'sini çağırır; dönen session_id + encrypted_tally JSON burada saklanır.
-- Mobile guardian'lar bu encrypted_tally üzerinden local KMP DecryptingTrustee
-- ile partial decryption + challenge response üretir.

ALTER TABLE elections ADD COLUMN IF NOT EXISTS tally_session_id VARCHAR(64);
ALTER TABLE elections ADD COLUMN IF NOT EXISTS encrypted_tally_json TEXT;

-- Guardian round-progress tracking. status'a yeni durumlar:
--   PARTIAL_SUBMITTED — Round 1 tamam (decrypt'in mobile sonucu submit edildi)
--   CHALLENGE_RESPONSE_SUBMITTED — Round 3 tamam
-- Mevcut status enum: PENDING, KEY_UPLOADED, SHARE_UPLOADED (eski tek-shot için).
-- Kolon zaten VARCHAR(20) — yeni değerler insert edilebilir; enum constraint yok.
