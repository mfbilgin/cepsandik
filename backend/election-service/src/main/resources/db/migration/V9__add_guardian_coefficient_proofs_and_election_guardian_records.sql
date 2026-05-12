-- V9__add_guardian_coefficient_proofs_and_election_guardian_records.sql
-- Entity sınıflarına eklenen eksik kolonların veritabanına yansıtılması

-- 1. election_guardians tablosuna coefficient_proofs kolonu
ALTER TABLE election_guardians ADD COLUMN coefficient_proofs TEXT;

-- 2. elections tablosuna guardian_records kolonu (dağıtık emanetçi kayıtları JSON)
ALTER TABLE elections ADD COLUMN guardian_records TEXT;
