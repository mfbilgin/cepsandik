-- Faz 2.6 — Kalıcı tally session (durable replay).
--
-- crypto-engine tally session'ı in-memory; restart/crash ile kaybolur. Bu
-- tablo her guardian'ın gönderdiği partial decryption ve challenge response
-- JSON'larını kalıcılaştırır. crypto-engine session'ı kaybolursa election-
-- service YENİ session açar ve persisted submission'ları SIRAYLA replay eder.
--
-- Replay neden güvenli: partial'lar session-bağımsız (sadece encrypted tally +
-- trustee'ye bağlı). Q-of-N seçimi crypto-engine'de deterministik
-- (guardianId sıralı ilk Q) → aynı Q seçilir → Fiat-Shamir challenge
-- deterministik olarak aynı üretilir → persisted challenge response'lar yeni
-- session'da da geçerli kalır. Guardian'ların (mobil) yeniden hesaplaması
-- GEREKMEZ (kırılgan nonce cache'e dokunulmaz).

CREATE TABLE IF NOT EXISTS tally_submissions (
    id           BIGSERIAL PRIMARY KEY,
    election_id  BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    guardian_id  VARCHAR(64) NOT NULL,        -- crypto-engine effectiveGuardianId
    round        VARCHAR(32) NOT NULL,        -- PARTIAL | CHALLENGE_RESPONSE
    payload_json TEXT NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tally_submission UNIQUE (election_id, guardian_id, round)
);

-- Replay: election bazında round'a göre id sırasıyla çek.
CREATE INDEX IF NOT EXISTS idx_tally_submissions_replay
    ON tally_submissions(election_id, round, id);
