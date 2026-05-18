-- Faz 2.8 — Stuck tally recovery + TALLY_FAILED.
--
-- tally_retry_count: scheduler stuck tally'i (CLOSED + session var + tally_proof
-- yok) durable-replay ile tekrar dener; her denemede artar. Cap aşılınca
-- election TALLY_FAILED'a geçer (sessizce asılı kalmaz, owner müdahale eder).

ALTER TABLE elections
    ADD COLUMN IF NOT EXISTS tally_retry_count INTEGER NOT NULL DEFAULT 0;
