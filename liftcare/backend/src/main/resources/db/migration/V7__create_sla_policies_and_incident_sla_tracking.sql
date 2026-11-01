-- ==============================================================================
-- LiftCare Database Migration V7
-- Configurable SLA Policies, Incident SLA Target Snapshots & Escalation Tracking
-- ==============================================================================

-- 1. Create SLA Policies Table
CREATE TABLE IF NOT EXISTS sla_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    building_id BIGINT,
    severity VARCHAR(20) NOT NULL,
    issue_category_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    response_target_minutes INT NOT NULL,
    resolution_target_minutes INT NOT NULL,
    warning_threshold_minutes INT NOT NULL,
    tier1_role VARCHAR(50) NOT NULL DEFAULT 'TECHNICIAN',
    tier2_role VARCHAR(50) NOT NULL DEFAULT 'BUILDING_MANAGER',
    tier3_role VARCHAR(50) NOT NULL DEFAULT 'COMPANY_SUPERVISOR',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sla_policy_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE CASCADE,
    CONSTRAINT fk_sla_policy_category FOREIGN KEY (issue_category_id) REFERENCES issue_categories(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_sla_policy_active ON sla_policies(active);
CREATE INDEX IF NOT EXISTS idx_sla_policy_lookup ON sla_policies(building_id, severity, issue_category_id, active);

-- 2. Seed Default Global SLA Policies (Covering All 4 Severity Levels)
INSERT INTO sla_policies (
    name, building_id, severity, issue_category_id, active,
    response_target_minutes, resolution_target_minutes, warning_threshold_minutes,
    tier1_role, tier2_role, tier3_role, version
) VALUES
('Default Critical Safety Policy', NULL, 'CRITICAL', NULL, TRUE, 15, 120, 5, 'SECURITY_STAFF', 'BUILDING_MANAGER', 'COMPANY_SUPERVISOR', 1),
('Default High Severity Policy', NULL, 'HIGH', NULL, TRUE, 60, 240, 15, 'TECHNICIAN', 'BUILDING_MANAGER', 'COMPANY_SUPERVISOR', 1),
('Default Medium Severity Policy', NULL, 'MEDIUM', NULL, TRUE, 180, 720, 30, 'TECHNICIAN', 'BUILDING_MANAGER', 'COMPANY_SUPERVISOR', 1),
('Default Low Severity Policy', NULL, 'LOW', NULL, TRUE, 720, 2880, 60, 'TECHNICIAN', 'BUILDING_MANAGER', 'COMPANY_SUPERVISOR', 1);

-- 3. Alter Incidents Table for SLA Clocks & Escalation State
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS sla_policy_id BIGINT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS response_deadline TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS resolution_deadline TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS sla_response_target_minutes INT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS sla_resolution_target_minutes INT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS sla_warning_threshold_minutes INT;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS escalation_level INT NOT NULL DEFAULT 0;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS last_escalated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS response_sla_breached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS resolution_sla_breached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS sla_warning_sent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE incidents ADD CONSTRAINT fk_incidents_sla_policy
    FOREIGN KEY (sla_policy_id) REFERENCES sla_policies(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_incidents_resp_deadline ON incidents(response_deadline);
CREATE INDEX IF NOT EXISTS idx_incidents_resol_deadline ON incidents(resolution_deadline);
CREATE INDEX IF NOT EXISTS idx_incidents_escalation ON incidents(escalation_level);
