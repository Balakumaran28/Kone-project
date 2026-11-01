-- ==============================================================================
-- LiftCare Database Migration V5
-- Enhance incident_history table with actor_type and audit query indexes
-- ==============================================================================

ALTER TABLE incident_history ADD COLUMN IF NOT EXISTS actor_type VARCHAR(20) DEFAULT 'USER';

UPDATE incident_history SET actor_type = 'USER' WHERE actor_type IS NULL;

ALTER TABLE incident_history ALTER COLUMN actor_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_history_incident_action ON incident_history(incident_id, action);
CREATE INDEX IF NOT EXISTS idx_history_incident_actor ON incident_history(incident_id, changed_by_id);
