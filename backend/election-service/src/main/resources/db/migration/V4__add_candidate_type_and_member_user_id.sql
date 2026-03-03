-- V4__add_candidate_type_and_member_user_id.sql
-- Aday tiplerini desteklemek için candidates tablosuna yeni kolonlar ekleniyor
-- CandidateType: PERSON, TEXT_OPTION, IMAGE_OPTION

ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS candidate_type VARCHAR(20) NOT NULL DEFAULT 'PERSON',
    ADD COLUMN IF NOT EXISTS member_user_id VARCHAR(255);

COMMENT ON COLUMN candidates.candidate_type IS 'Aday tipi: PERSON, TEXT_OPTION, IMAGE_OPTION';
COMMENT ON COLUMN candidates.member_user_id IS 'PERSON tipinde topluluk üyesinin userId değeri';
