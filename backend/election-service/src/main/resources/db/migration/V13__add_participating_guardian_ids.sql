-- Sprint 5.A leader-mode — election'ın CreateJointKey RPC'sine katılan
-- N adet guardian'ın ID listesi. Distributed tally sırasında
-- DistributedTallyService.startSessionForElection bu kolondan eligible
-- guardian'ları çıkarır (election_guardians tablosu sadece distributed-mode
-- multi-cihaz için kullanılır).

ALTER TABLE elections ADD COLUMN IF NOT EXISTS participating_guardian_ids TEXT;
