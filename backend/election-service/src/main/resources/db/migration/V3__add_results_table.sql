-- V3__add_results_table.sql
-- Seçim sonuçları tablosu

CREATE TABLE election_results (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    candidate_id BIGINT NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    vote_count BIGINT NOT NULL DEFAULT 0,
    percentage DOUBLE PRECISION NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_election_results_election_id ON election_results(election_id);
CREATE INDEX idx_election_results_candidate_id ON election_results(candidate_id);
CREATE UNIQUE INDEX idx_election_results_election_candidate ON election_results(election_id, candidate_id);
