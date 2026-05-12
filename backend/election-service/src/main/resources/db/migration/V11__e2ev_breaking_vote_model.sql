-- Breaking E2E-V migration.
-- Existing local votes/results are intentionally discarded: old records linked
-- plaintext candidate_id and vote_token, which is incompatible with E2E-V.

TRUNCATE TABLE election_results;
TRUNCATE TABLE votes;
TRUNCATE TABLE vote_tokens;

ALTER TABLE votes DROP CONSTRAINT IF EXISTS uk_vote_token;
ALTER TABLE votes DROP CONSTRAINT IF EXISTS uk_vote_election_token;
ALTER TABLE votes DROP CONSTRAINT IF EXISTS votes_candidate_id_fkey;

DROP INDEX IF EXISTS idx_votes_candidate_id;
DROP INDEX IF EXISTS idx_votes_vote_token;

ALTER TABLE votes DROP COLUMN IF EXISTS candidate_id;
ALTER TABLE votes DROP COLUMN IF EXISTS vote_token;
ALTER TABLE votes DROP COLUMN IF EXISTS created_at;
ALTER TABLE votes ADD COLUMN IF NOT EXISTS ballot_id VARCHAR(128) NOT NULL;
ALTER TABLE votes ALTER COLUMN encrypted_ballot SET NOT NULL;
ALTER TABLE votes ALTER COLUMN tracking_code SET NOT NULL;
ALTER TABLE votes ALTER COLUMN zkp_proof SET NOT NULL;
ALTER TABLE votes ADD COLUMN IF NOT EXISTS ballot_hash VARCHAR(64) NOT NULL;
ALTER TABLE votes ADD COLUMN IF NOT EXISTS cast_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE votes ADD CONSTRAINT uk_vote_election_ballot UNIQUE (election_id, ballot_id);
ALTER TABLE votes ADD CONSTRAINT uk_vote_election_tracking UNIQUE (election_id, tracking_code);
CREATE INDEX IF NOT EXISTS idx_votes_ballot_hash ON votes(ballot_hash);

CREATE TABLE IF NOT EXISTS vote_nullifiers (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    nullifier_hash VARCHAR(128) NOT NULL,
    ballot_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vote_nullifier_election_hash UNIQUE (election_id, nullifier_hash)
);

ALTER TABLE election_results DROP CONSTRAINT IF EXISTS idx_election_results_election_candidate;
ALTER TABLE election_results DROP CONSTRAINT IF EXISTS election_results_candidate_id_fkey;
DROP INDEX IF EXISTS idx_election_results_candidate_id;
ALTER TABLE election_results DROP COLUMN IF EXISTS candidate_id;
ALTER TABLE election_results ADD COLUMN IF NOT EXISTS selection_id VARCHAR(128) NOT NULL;
ALTER TABLE election_results ADD COLUMN IF NOT EXISTS option_label VARCHAR(200);
ALTER TABLE election_results ADD COLUMN IF NOT EXISTS option_description TEXT;
ALTER TABLE election_results ADD COLUMN IF NOT EXISTS option_image_url VARCHAR(500);
ALTER TABLE election_results ADD CONSTRAINT uk_election_result_selection UNIQUE (election_id, selection_id);

ALTER TABLE elections ADD COLUMN IF NOT EXISTS tally_results TEXT;
