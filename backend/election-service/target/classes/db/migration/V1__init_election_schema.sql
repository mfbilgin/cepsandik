-- V1__init_election_schema.sql
-- Election Service veritabanı şeması

-- Elections tablosu
CREATE TABLE elections (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    community_id BIGINT,
    created_by VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_CHOICE',
    participant_type VARCHAR(20) NOT NULL DEFAULT 'ALL_MEMBERS',
    max_selections INTEGER,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    results_public BOOLEAN DEFAULT FALSE,
    anonymous_voting BOOLEAN DEFAULT TRUE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Candidates tablosu
CREATE TABLE candidates (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    display_order INTEGER DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Access codes tablosu
CREATE TABLE access_codes (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    code VARCHAR(6) NOT NULL UNIQUE,
    max_uses INTEGER,
    current_uses INTEGER DEFAULT 0,
    created_by VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_elections_community_id ON elections(community_id);
CREATE INDEX idx_elections_status ON elections(status);
CREATE INDEX idx_elections_created_by ON elections(created_by);
CREATE INDEX idx_elections_start_time ON elections(start_time);
CREATE INDEX idx_elections_end_time ON elections(end_time);

CREATE INDEX idx_candidates_election_id ON candidates(election_id);

CREATE INDEX idx_access_codes_election_id ON access_codes(election_id);
CREATE INDEX idx_access_codes_code ON access_codes(code);
