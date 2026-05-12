-- V8__add_distributed_guardians.sql
-- Dağıtık Emanetçi (Guardian) sistemi için tablo ve kolon güncellemeleri

-- 1. Seçimlerdeki minimum emanetçi (Q) eşik değeri
ALTER TABLE elections ADD COLUMN min_guardians_threshold INTEGER DEFAULT 3;

-- 2. Eski simüle edilmiş (private key içeren) kolonun kaldırılması
ALTER TABLE elections DROP COLUMN guardian_records;

-- 3. Bağımsız Emanetçi Takip Tablosu
CREATE TABLE election_guardians (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    
    -- EG 2.1 Kamu Anahtarları ve Taahhütler (JSON)
    public_key TEXT, 
    commitments TEXT,
    
    -- Seremoni Transkript Parçaları (Diğer guardianlar için şifreli paylar)
    key_backups TEXT, 
    
    -- Deşifre Payı (Seçim bittiğinde doldurulur)
    decryption_share TEXT,
    
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, KEY_UPLOADED, SHARE_UPLOADED
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Hızlı erişim için indexler
CREATE INDEX idx_election_guardians_election_id ON election_guardians(election_id);
CREATE INDEX idx_election_guardians_user_id ON election_guardians(user_id);
