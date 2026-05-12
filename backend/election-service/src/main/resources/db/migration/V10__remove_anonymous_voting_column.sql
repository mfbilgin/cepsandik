-- V10__remove_anonymous_voting_column.sql
-- Remove anonymous_voting from elections since all elections are now anonymous by default.

ALTER TABLE elections DROP COLUMN anonymous_voting;
