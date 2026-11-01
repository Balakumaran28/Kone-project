-- ==============================================================================
-- LiftCare Database Migration V1
-- Core relational tables, foreign keys, unique constraints, and indexes
-- ==============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30),
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('RESIDENT', 'SECURITY_STAFF', 'BUILDING_MANAGER', 'TECHNICIAN', 'COMPANY_SUPERVISOR', 'ADMINISTRATOR'))
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- 2. Buildings Table
CREATE TABLE IF NOT EXISTS buildings (
    id BIGSERIAL PRIMARY KEY,
    building_code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100),
    local_node_id VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_buildings_code UNIQUE (building_code)
);

CREATE INDEX IF NOT EXISTS idx_buildings_code ON buildings(building_code);
CREATE INDEX IF NOT EXISTS idx_buildings_node ON buildings(local_node_id);

-- 3. Elevators Table
CREATE TABLE IF NOT EXISTS elevators (
    id BIGSERIAL PRIMARY KEY,
    elevator_code VARCHAR(50) NOT NULL,
    building_id BIGINT NOT NULL,
    location_description VARCHAR(255),
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    service_provider VARCHAR(150),
    total_floors INTEGER,
    operational_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_elevator_building_code UNIQUE (building_id, elevator_code),
    CONSTRAINT fk_elevators_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE RESTRICT,
    CONSTRAINT chk_elevators_status CHECK (operational_status IN ('ACTIVE', 'MAINTENANCE', 'OUT_OF_SERVICE')),
    CONSTRAINT chk_elevators_floors CHECK (total_floors IS NULL OR total_floors > 0)
);

CREATE INDEX IF NOT EXISTS idx_elevators_building ON elevators(building_id);
CREATE INDEX IF NOT EXISTS idx_elevators_status ON elevators(operational_status);

-- 4. Issue Categories Table
CREATE TABLE IF NOT EXISTS issue_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    default_severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    requires_immediate_attention BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_issue_categories_code UNIQUE (code),
    CONSTRAINT chk_issue_categories_severity CHECK (default_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_issue_cat_code ON issue_categories(code);
CREATE INDEX IF NOT EXISTS idx_issue_cat_severity ON issue_categories(default_severity);

-- 5. Incidents Table
CREATE TABLE IF NOT EXISTS incidents (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(60) NOT NULL,
    building_id BIGINT NOT NULL,
    elevator_id BIGINT NOT NULL,
    reported_by_id BIGINT,
    issue_category_id BIGINT NOT NULL,
    description VARCHAR(2000) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'REPORTED',
    entrapment_reported BOOLEAN NOT NULL DEFAULT FALSE,
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_incidents_ticket UNIQUE (ticket_number),
    CONSTRAINT fk_incidents_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_elevator FOREIGN KEY (elevator_id) REFERENCES elevators(id) ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_user FOREIGN KEY (reported_by_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_category FOREIGN KEY (issue_category_id) REFERENCES issue_categories(id) ON DELETE RESTRICT,
    CONSTRAINT chk_incidents_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_incidents_status CHECK (status IN ('REPORTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_incidents_ticket ON incidents(ticket_number);
CREATE INDEX IF NOT EXISTS idx_incidents_bld_status ON incidents(building_id, status);
CREATE INDEX IF NOT EXISTS idx_incidents_elevator ON incidents(elevator_id);
CREATE INDEX IF NOT EXISTS idx_incidents_severity ON incidents(severity);
CREATE INDEX IF NOT EXISTS idx_incidents_reported_at ON incidents(reported_at);

-- 6. Incident History Table
CREATE TABLE IF NOT EXISTS incident_history (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    previous_value VARCHAR(1000),
    new_value VARCHAR(1000),
    changed_by_id BIGINT,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks VARCHAR(1000),
    CONSTRAINT fk_history_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE RESTRICT,
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_history_incident_date ON incident_history(incident_id, changed_at);
