-- ==============================================================================
-- LiftCare Database Migration V3
-- Add password_hash column to users table for Module 3 authentication
-- ==============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
