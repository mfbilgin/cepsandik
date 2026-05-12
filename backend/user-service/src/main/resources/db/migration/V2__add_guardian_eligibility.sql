-- V2__add_guardian_eligibility.sql
-- Kullanıcıların emanetçi (guardian) olabilme tercihini tutan kolon

ALTER TABLE users ADD COLUMN is_guardian_eligible BOOLEAN DEFAULT TRUE;
