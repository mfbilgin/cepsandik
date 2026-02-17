-- V2__add_voting_tables.sql
-- Oy verme sistemi tabloları

-- Vote tokens tablosu (her kullanıcıya seçim başına tek token)
CREATE TABLE vote_tokens (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    token VARCHAR(36) NOT NULL UNIQUE,
    is_used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP,
    CONSTRAINT uk_vote_token_election_user UNIQUE (election_id, user_id)
);

-- Votes tablosu (anonim oylar)
CREATE TABLE votes (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    candidate_id BIGINT NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    vote_token VARCHAR(36) NOT NULL,
    encrypted_ballot TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vote_token UNIQUE (vote_token),
    CONSTRAINT uk_vote_election_token UNIQUE (election_id, vote_token)
);

-- Indexes
CREATE INDEX idx_vote_tokens_election_id ON vote_tokens(election_id);
CREATE INDEX idx_vote_tokens_user_id ON vote_tokens(user_id);
CREATE INDEX idx_vote_tokens_token ON vote_tokens(token);

CREATE INDEX idx_votes_election_id ON votes(election_id);
CREATE INDEX idx_votes_candidate_id ON votes(candidate_id);
CREATE INDEX idx_votes_vote_token ON votes(vote_token);
