-- V8: Cloud Coordination and Multi-Building Synchronization Schema
-- Module 12: Cloud Coordination and Multi-Building Synchronization

-- 1. Registered Buildings on Cloud Coordination Server
CREATE TABLE cloud_registered_buildings (
    id BIGSERIAL PRIMARY KEY,
    building_code VARCHAR(50) NOT NULL UNIQUE,
    building_name VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    last_sync_status VARCHAR(20),
    last_sync_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cloud_reg_bld_status ON cloud_registered_buildings(status);
CREATE INDEX idx_cloud_reg_bld_code ON cloud_registered_buildings(building_code);

-- 2. Local Cloud Sync Outbox (stores outbound events queued on local building server)
CREATE TABLE cloud_sync_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    building_code VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_cloud_sync_outbox_status ON cloud_sync_outbox(status);
CREATE INDEX idx_cloud_sync_outbox_bld_status ON cloud_sync_outbox(building_code, status);
CREATE INDEX idx_cloud_sync_outbox_created ON cloud_sync_outbox(created_at);

-- 3. Local Cloud Sync Checkpoint & Connectivity Telemetry
CREATE TABLE cloud_sync_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    building_code VARCHAR(50) NOT NULL UNIQUE,
    last_successful_sync_at TIMESTAMP WITH TIME ZONE,
    last_sync_attempt_at TIMESTAMP WITH TIME ZONE,
    pending_events_count INT NOT NULL DEFAULT 0,
    failed_events_count INT NOT NULL DEFAULT 0,
    cloud_connection_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    last_sync_error TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Central Ingested Cloud Sync Events (Central deduplication & event log)
CREATE TABLE cloud_sync_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    building_code VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE
);

CREATE INDEX idx_cloud_sync_events_bld ON cloud_sync_events(building_code);
CREATE INDEX idx_cloud_sync_events_type ON cloud_sync_events(event_type);
CREATE INDEX idx_cloud_sync_events_status ON cloud_sync_events(status);

-- 5. Central Cloud Incident Replicas (Aggregated cross-building telemetry)
CREATE TABLE cloud_incident_replicas (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    building_code VARCHAR(50) NOT NULL,
    building_name VARCHAR(100),
    elevator_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    entrapment_reported BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT NOT NULL,
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    assigned_technician_name VARCHAR(100),
    escalation_level INT NOT NULL DEFAULT 0,
    response_sla_breached BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_sla_breached BOOLEAN NOT NULL DEFAULT FALSE,
    local_version INT NOT NULL DEFAULT 1,
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cloud_replica_bld ON cloud_incident_replicas(building_code);
CREATE INDEX idx_cloud_replica_status ON cloud_incident_replicas(status);
CREATE INDEX idx_cloud_replica_severity ON cloud_incident_replicas(severity);
CREATE INDEX idx_cloud_replica_reported ON cloud_incident_replicas(reported_at);

-- 6. Central Cloud Sync Conflicts
CREATE TABLE cloud_sync_conflicts (
    id BIGSERIAL PRIMARY KEY,
    conflict_id VARCHAR(64) NOT NULL UNIQUE,
    ticket_number VARCHAR(50) NOT NULL,
    building_code VARCHAR(50) NOT NULL,
    conflict_type VARCHAR(50) NOT NULL,
    local_state TEXT NOT NULL,
    cloud_state TEXT NOT NULL,
    resolution_action VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by VARCHAR(100)
);

CREATE INDEX idx_cloud_conflicts_status ON cloud_sync_conflicts(resolution_action);
CREATE INDEX idx_cloud_conflicts_bld ON cloud_sync_conflicts(building_code);
