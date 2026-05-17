-- Sprint 5.A.distributed — gerçek dağıtık key ceremony desteği.
--
-- 1. guardian_key_shares: cross-trustee şifreli key share relay tablosu.
--    Sunucu içeriği GÖREMEZ (encrypted_share_json alıcının public key'iyle
--    HashedElGamal ile şifreli). Sunucu sadece from→to forward eder.
--
-- 2. election_guardians yeni kolonlar:
--    - x_coordinate: KMP polinom x (1-indexed). Guardian sırası yerine açık kolon.
--    - secret_polynomial_uploaded: guardian Round 1'i tamamladı mı (public key
--      yüklendi) — status zaten KEY_UPLOADED ile takip ediliyor, bu ek bilgi yok,
--      sadece coefficient_proofs reuse'undan kaçınmak için yeni state'ler eklendi.
--
-- 3. elections.substitution_count: STATE_RESET mekanizması — backup devreye
--    girince artırılır. K=2 cap'i aşınca election CANCELLED.
--
-- 4. elections.setup_deadline: key ceremony bitiş zamanı (adaptif yedek formülü).

ALTER TABLE elections ADD COLUMN IF NOT EXISTS substitution_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS setup_deadline TIMESTAMP WITH TIME ZONE;

ALTER TABLE election_guardians ADD COLUMN IF NOT EXISTS x_coordinate INTEGER;
ALTER TABLE election_guardians ADD COLUMN IF NOT EXISTS is_backup BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE election_guardians ADD COLUMN IF NOT EXISTS backup_order INTEGER;

CREATE TABLE IF NOT EXISTS guardian_key_shares (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    from_guardian_id VARCHAR(64) NOT NULL,
    to_guardian_id VARCHAR(64) NOT NULL,
    encrypted_share_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_key_share UNIQUE (election_id, from_guardian_id, to_guardian_id)
);

CREATE INDEX IF NOT EXISTS idx_guardian_key_shares_election ON guardian_key_shares(election_id);
CREATE INDEX IF NOT EXISTS idx_guardian_key_shares_to ON guardian_key_shares(election_id, to_guardian_id);
