-- Add tally_proof column to elections table to store ElectionGuard tally proofs
ALTER TABLE elections ADD COLUMN IF NOT EXISTS tally_proof TEXT;
