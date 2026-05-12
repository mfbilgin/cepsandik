-- Push token alanı ekleme
ALTER TABLE users ADD COLUMN push_token VARCHAR(255);

-- is_guardian_eligible varsayılan değerini false yapma ve mevcutları sıfırlama (Güvenlik gereği)
ALTER TABLE users ALTER COLUMN is_guardian_eligible SET DEFAULT false;
UPDATE users SET is_guardian_eligible = false;

-- Bildirim tercihleri tablosu
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(30) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    UNIQUE(user_id, category, channel)
);
