-- Faz 1.4 — Benaloh challenge / ballot spoiling (cast-as-intended).
--
-- Seçmen bir şifreli ballot'u "spoil" edebilir: cihaz o ballot'un primary
-- nonce'ını açar, sunucu DecryptWithNonce ile BAĞIMSIZ çözer (trustless).
-- Çözülen plaintext cihazın gösterdiğiyle eşleşirse cihaz dürüst şifrelemiş
-- demektir. Spoiled ballot ASLA tally'ye girmez (votes tablosuna yazılmaz).
--
-- KRİTİK gizlilik kuralı: nonce açıldığı için spoiled bir ciphertext bir daha
-- ASLA cast edilemez (aksi halde o oyun gizliliği kırılır). castVote bu tabloya
-- karşı kontrol eder.

CREATE TABLE IF NOT EXISTS spoiled_ballots (
    id            BIGSERIAL PRIMARY KEY,
    election_id   BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    ballot_id     VARCHAR(128),
    tracking_code VARCHAR(128),
    ballot_hash   VARCHAR(256),
    verified      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Aynı ciphertext'in tekrar spoil/cast edilmesini engellemek + hızlı kontrol.
CREATE INDEX IF NOT EXISTS idx_spoiled_ballots_election ON spoiled_ballots(election_id);
CREATE INDEX IF NOT EXISTS idx_spoiled_ballots_hash
    ON spoiled_ballots(election_id, ballot_hash);
CREATE INDEX IF NOT EXISTS idx_spoiled_ballots_tracking
    ON spoiled_ballots(election_id, tracking_code);
