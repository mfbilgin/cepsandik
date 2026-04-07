-- Seçim başlangıç/bitiş: anlık zaman (UTC) olarak sakla (timestamptz).
-- Eski TIMESTAMP değerleri, uygulamanın önceki semantiğiyle uyumlu olarak
-- kullanıcı yerel saati (Europe/Istanbul) gibi yorumlanır.
ALTER TABLE elections
    ALTER COLUMN start_time TYPE TIMESTAMP WITH TIME ZONE
        USING (CASE
                   WHEN start_time IS NULL THEN NULL
                   ELSE start_time AT TIME ZONE 'Europe/Istanbul' END),
    ALTER COLUMN end_time TYPE TIMESTAMP WITH TIME ZONE
        USING (CASE
                   WHEN end_time IS NULL THEN NULL
                   ELSE end_time AT TIME ZONE 'Europe/Istanbul' END);

-- Erişim kodu son kullanma: aynı duvar saati semantiği
ALTER TABLE access_codes
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE
        USING (CASE
                   WHEN expires_at IS NULL THEN NULL
                   ELSE expires_at AT TIME ZONE 'Europe/Istanbul' END);

-- Oy token kullanım anı: JVM (çoğunlukla UTC) ile yazılmış kabulü
ALTER TABLE vote_tokens
    ALTER COLUMN used_at TYPE TIMESTAMP WITH TIME ZONE
        USING (CASE
                   WHEN used_at IS NULL THEN NULL
                   ELSE used_at AT TIME ZONE 'UTC' END);
