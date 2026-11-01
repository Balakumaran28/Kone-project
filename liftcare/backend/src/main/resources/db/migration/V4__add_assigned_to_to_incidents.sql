-- ==============================================================================
-- LiftCare Database Migration V4
-- Add assigned_to_id column to incidents table for technician triage assignment
-- ==============================================================================

ALTER TABLE incidents ADD COLUMN IF NOT EXISTS assigned_to_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_incidents_assigned_to'
    ) THEN
        ALTER TABLE incidents
        ADD CONSTRAINT fk_incidents_assigned_to
        FOREIGN KEY (assigned_to_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_incidents_assigned_to ON incidents(assigned_to_id);
