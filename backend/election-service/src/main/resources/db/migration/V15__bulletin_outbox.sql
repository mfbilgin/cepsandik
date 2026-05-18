-- Faz 1.1 — Transactional outbox: bulletin board şeffaflık kayıtları artık
-- ceremony/tally state ile AYNI transaction'da atomik olarak bu tabloya yazılır.
-- Arka plan publisher (BulletinOutboxPublisher) bunları SIRAYLA, retry'lı,
-- idempotent (idempotency_key) şekilde bulletin-board-service'e teslim eder.
--
-- Garanti: ne sessiz kayıp (satır state ile atomik commit), ne @Transactional
-- rollback riski (dış çağrı tx dışında), hash-zinciri sırası korunur
-- (election_id bazında id sırasıyla publish, ilk hatada o election durur).
--
-- critical=true kayıtlar (PUBLIC_KEYS, JOINT_KEY_GENERATED, BALLOT_CAST,
-- TALLY_PUBLISHED) yayımlanmadan election ARŞİVLENEMEZ / sonuç publish edilemez.

CREATE TABLE IF NOT EXISTS bulletin_outbox (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    election_id     VARCHAR(64) NOT NULL,
    record_type     VARCHAR(64) NOT NULL,
    tracking_code   VARCHAR(128),
    ballot_hash     VARCHAR(256),
    payload         TEXT,
    critical        BOOLEAN NOT NULL DEFAULT FALSE,
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP WITH TIME ZONE
);

-- Publisher: yayımlanmamışları election bazında id sırasıyla çeker.
CREATE INDEX IF NOT EXISTS idx_bulletin_outbox_pending
    ON bulletin_outbox(election_id, id) WHERE published = FALSE;

-- "Bu election'da yayımlanmamış kritik kayıt var mı?" hızlı kontrolü.
CREATE INDEX IF NOT EXISTS idx_bulletin_outbox_unpublished_critical
    ON bulletin_outbox(election_id) WHERE published = FALSE AND critical = TRUE;
