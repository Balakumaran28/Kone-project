-- ==============================================================================
-- LiftCare Database Migration V6
-- Idempotency Records & Durable Server-Side Notification Outbox
-- ==============================================================================

-- 1. Idempotency Records Table
CREATE TABLE IF NOT EXISTS idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL,
    user_id BIGINT NOT NULL,
    endpoint VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_user_key ON idempotency_records(user_id, idempotency_key);

-- 2. Durable Notification Outbox Table
CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    notification_uid VARCHAR(64) NOT NULL UNIQUE,
    incident_id BIGINT,
    recipient_user_id BIGINT,
    recipient_role VARCHAR(50),
    recipient_contact VARCHAR(255),
    notification_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message_body VARCHAR(2000) NOT NULL,
    delivery_state VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    deduplication_key VARCHAR(160) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outbox_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE SET NULL,
    CONSTRAINT fk_outbox_user FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_delivery_state ON notification_outbox(delivery_state);
CREATE INDEX IF NOT EXISTS idx_outbox_incident ON notification_outbox(incident_id);
CREATE INDEX IF NOT EXISTS idx_outbox_recipient ON notification_outbox(recipient_user_id);
