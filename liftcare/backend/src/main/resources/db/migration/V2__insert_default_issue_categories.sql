-- ==============================================================================
-- LiftCare Database Migration V2
-- Seed default issue categories (repeatable seed with ON CONFLICT DO NOTHING)
-- ==============================================================================

INSERT INTO issue_categories (code, name, description, default_severity, requires_immediate_attention, active, created_at)
VALUES
    ('PASSENGER_ENTRAPMENT', 'Passenger entrapment', 'One or more passengers are entrapped inside the elevator cabin. Requires immediate emergency dispatch.', 'CRITICAL', TRUE, TRUE, CURRENT_TIMESTAMP),
    ('ELEVATOR_NOT_OPERATING', 'Elevator not operating', 'Elevator is completely unresponsive to hall calls or cab commands.', 'HIGH', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('DOOR_MALFUNCTION', 'Door malfunction', 'Doors fail to open, close, or repeatedly bounce without mechanical obstruction.', 'HIGH', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('ELECTRICAL_CONTROL_PANEL', 'Electrical or control panel issue', 'Flickering indicators, unresponsive COP floor buttons, or display faults.', 'HIGH', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('UNUSUAL_NOISE_VIBRATION', 'Unusual noise or vibration', 'Audible grinding, rattling, squeaking, or excessive vertical/horizontal vibration.', 'MEDIUM', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('FLOOR_ALIGNMENT_PROBLEM', 'Floor alignment problem', 'Car stops noticeably unevenly with landing sill, creating a tripping hazard.', 'MEDIUM', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('ROUTINE_MAINTENANCE', 'Routine maintenance request', 'Scheduled inspection, minor cosmetic repair, or preventive lubrication.', 'LOW', FALSE, TRUE, CURRENT_TIMESTAMP),
    ('OTHER_ISSUE', 'Other issue', 'Uncategorized elevator defect or general facility observation.', 'LOW', FALSE, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;
