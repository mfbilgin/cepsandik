-- V5__add_crypto_columns.sql
-- ElectionGuard kriptografik entegrasyon kolonları

-- elections tablosu — kriptografik context ve guardian bilgileri
ALTER TABLE elections ADD COLUMN election_guard_context TEXT;
ALTER TABLE elections ADD COLUMN election_manifest TEXT;
ALTER TABLE elections ADD COLUMN election_public_key TEXT;
ALTER TABLE elections ADD COLUMN guardian_records TEXT;

-- votes tablosu — şifreleme sonuç kolonları
ALTER TABLE votes ADD COLUMN tracking_code VARCHAR(128);
ALTER TABLE votes ADD COLUMN zkp_proof TEXT;

-- Tracking code index (doğrulama sorguları için)
CREATE INDEX idx_votes_tracking_code ON votes(tracking_code);
