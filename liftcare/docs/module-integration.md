# LiftCare — Module Integration Roadmap

This document outlines how upcoming modules seamlessly integrate into the architectural foundation established in **Module 1 (Project Setup & Architecture)**.

---

## Foundation Status & Completed Modules

### Module 1: Project Setup & Architecture (Complete)
- **Decoupled Client & Server**: Vite + React frontend communicating via REST over `VITE_API_BASE_URL`.
- **Spring Boot 3 REST Core**: Modular package structure (`config`, `controller`, `dto`, `exception`, `service`).
- **Configurable Database Layer**: PostgreSQL Spring Data JPA configuration driven entirely by environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
- **Resilience-Aware Health Endpoint**: `GET /api/health` providing live telemetry of server runtime and persistence status without brittle startup failures.
- **Unified Error Model**: Standardized JSON response format (`timestamp`, `status`, `error`, `message`, `path`, `validationErrors`) enforced via `GlobalExceptionHandler`.
- **CORS Baseline**: Configured via `SecurityConfig` and `CorsConfig`.

### Module 2: Database Design & Entity Creation (Complete)
- **Domain Entities**: `User`, `Building`, `Elevator`, `IssueCategory`, `Incident`, `IncidentHistory`.
- **Enums**: `UserRole`, `ElevatorOperationalStatus`, `SeverityLevel`, `IncidentStatus`.
- **Flyway Migrations**: `V1__init_schema.sql` (relational tables, indices, foreign keys) and `V2__seed_issue_categories.sql` (default categories).
- **Repositories**: 6 Spring Data JPA repositories with query methods.
- **Test Suite**: Isolated H2 integration tests (`EntityRepositoryTest`).

### Module 3: User Authentication & Role-Based Access Control (Complete)
- **Local Identity Architecture**: Zero reliance on external cloud OAuth/OIDC providers; runs 100% on local building appliance.
- **Password Security**: BCrypt hashing via `BCryptPasswordEncoder`, `password_hash` column added via `V3__add_password_hash_to_users.sql`, zero password exposure in DTOs or logs.
- **Stateless JWT**: HMAC-SHA256 tokens (`JwtService`, `JwtAuthenticationFilter`, configurable secret & expiration).
- **Security Perimeter**: `SecurityConfig` with stateless session management, unified 401/403 handlers, method-level security (`@EnableMethodSecurity`).
- **Auth Endpoints**: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`, `GET /api/auth/admin-check`.
- **Frontend Auth Integration**: React `AuthContext`, token persistence in `localStorage`, `ProtectedRoute` routing, Login/Register/Dashboard views.

### Module 4: Building and Elevator Management (Complete)
- **Physical Asset Registry**: Building complex provisioning and elevator shaft inventory.
- **Scoped Code Uniqueness**: Enforces global uniqueness for building codes, and scoped uniqueness for elevator codes within each containing building (`building_id, elevator_code`).
- **Real-Time Operational Status Controls**: `ACTIVE`, `MAINTENANCE`, `OUT_OF_SERVICE` lifecycle transitions.
- **Role Permission Boundary**: `ADMINISTRATOR` for global building creation/deactivation; `BUILDING_MANAGER` for property and shaft maintenance; `TECHNICIAN` for status overrides; `RESIDENT` and `SECURITY_STAFF` for read-only status visibility.
- **Frontend UI & Workflows**: `BuildingsPage`, `BuildingDetailPage`, status controls, empty states, and permission gating.
- **API Endpoints**: `POST/GET/PUT/PATCH /api/buildings` and `POST/GET/PUT/PATCH /api/elevators`.

### Module 5: Incident Reporting and Local Triage (Complete)
- **Incident Reporting API**: `POST /api/incidents`, `GET /api/incidents/{id}`, `GET /api/incidents`, `GET /api/incidents/my`, `GET /api/buildings/{id}/incidents`, `GET /api/elevators/{id}/incidents`.
- **Reporter Identity Verification**: Derived server-side from Spring Security JWT authentication context; clients cannot forge reporter identity.
- **Severity & Entrapment Rules**: Enforces mandatory `CRITICAL` severity for passenger entrapment; strictly forbids silent downgrades to `LOW` or `MEDIUM`.
- **Automatic Elevator Safety Shutdown**: Qualifying emergency incidents automatically transition the affected elevator to `OUT_OF_SERVICE` within the same transaction.
- **Triage & Assignment Workflow**: Staff acknowledgment, technician dispatch assignment, status transitions (`IN_PROGRESS`, `RESOLVED`, `CLOSED`), and resolution notes.
- **Immutable Audit Trail**: Chronological event logging in `incident_history` for creation, safety status transitions, acknowledgment, assignments, and resolutions.
- **Frontend Views**: `ReportIncidentPage`, `IncidentsPage`, `IncidentDetailPage`, status and assignment modals, role-specific triage controls.

### Module 6: Building Control-Room Dashboard (Complete)
- **Dedicated Telemetry API**: `GET /api/dashboard/summary` computes real-time incident lifecycle metrics, severity breakdown, and elevator operational counts directly via indexed database queries.
- **Emergency Entrapment Banner**: Prominent dispatch alert for active passenger entrapments and critical emergencies with immediate triage links.
- **Incident Queue with Advanced Triage Controls**: Real-time ticket queue with multi-dimensional filtering (Severity, Status, Entrapment, Building, Technician, Free-text search) and deterministic sorting modes (`EMERGENCY_FIRST`, `OLDEST_UNACKNOWLEDGED`, `NEWEST_FIRST`, `OLDEST_FIRST`).
- **Elevator Fleet & Safety Visibility**: Operational status matrix (`ACTIVE`, `MAINTENANCE`, `OUT_OF_SERVICE`) distinguishing recorded software state from physical reality, with staff status override capabilities.
- **Offline-First Resilience**: Full operational autonomy over local LAN without cloud dependencies, active health latency monitoring, and stale telemetry warnings.
- **Page Visibility Aware Refresh**: 30-second background polling that pauses when the tab is hidden and resumes immediately when visible.
- **Frontend Views**: `ControlRoomPage`, quick navigation cards in `Navbar` and `DashboardPage`.

### Module 7: Local Server and Offline Operation (Complete)
- **Local Building Server Deployment**: Docker multi-stage builds (`backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, and `docker-compose.yml`) for containerized appliance rollout.
- **Zero Internet Dependency**: Complete purge of external CDN dependencies (Google Fonts removed in favor of native system fonts); all assets and libraries served locally.
- **Dynamic LAN Access Support**: Intelligent `resolveApiBaseUrl()` runtime routing supporting same-origin relative `/api` paths behind Nginx, and dynamic `window.location.hostname` preservation during local development for mobile/tablet LAN devices.
- **5-State System Health Evaluator**: Real-time diagnostic state machine (`LOCAL_ONLINE`, `LOCAL_LAN_AUTONOMY`, `BACKEND_UNAVAILABLE`, `DATABASE_UNAVAILABLE`, `UNKNOWN`) in `StatusIndicator` and `LandingPage`.
- **Fault-Tolerant Incident Reporting**: Clear failure indicators and form state preservation on backend/database disconnect to prevent data loss.
- **Client-Side Exception Shield**: React `ErrorBoundary` preventing unexpected UI crashes from unhandled errors.
- **CORS LAN Pattern Matching**: `allowedOriginPatterns` in `SecurityConfig` and `CorsConfig` allowing wildcard subnets (e.g. `http://192.168.*:*`).
- **Comprehensive Operations Guide**: Added `docs/local-deployment-and-offline-operation.md` detailing architecture, container setup, native execution, LAN firewall configuration, persistent volumes, backup/restore routines, and disaster recovery.

### Module 8: Incident History and Audit Trail (Complete)
- **Immutable Relational Audit Trail**: `incident_history` table extended with `actor_type` (`USER`, `SYSTEM`) and indexed on `(incident_id, action)` and `(incident_id, changed_by_id)` via Flyway `V5__enhance_incident_history.sql`.
- **Comprehensive Lifecycle Event Coverage**: 10 event types tracked across incident progression (`INCIDENT_REPORTED`, `ACKNOWLEDGED`, `ASSIGNED`, `TECHNICIAN_REASSIGNED`, `STATUS_CHANGED`, `RESOLVED`, `CLOSED`, `ELEVATOR_OUT_OF_SERVICE`, `ELEVATOR_STATUS_CHANGED`, `WORK_NOTE_ADDED`, `RESOLUTION_NOTES_UPDATED`, `ADMINISTRATIVE_ACTION`).
- **Strict Server-Derived Identity & Timestamps**: Actors derived strictly from Spring Security JWT context (`@AuthenticationPrincipal`); timestamps generated by server UTC (`Instant.now()`, `@PrePersist`).
- **Dedicated History Endpoint with Pagination**: `GET /api/incidents/{id}/history` supporting `page`, `size`, `sort` (`ASC`/`DESC`), `action`, `actorType`, and `actorId` filters, returning `IncidentHistoryPageResponse`.
- **Append Audit Note API**: `POST /api/incidents/{id}/notes` allowing technicians, managers, and reporting residents to log progress and administrative observations.
- **Frontend Timeline Experience**: Interactive chronological timeline in `IncidentDetailPage` with human-friendly event cards, role badges, state transition arrows, ascending/descending sorting, multi-dimensional category filters, and modal note-entry controls.
- **Transactional Rollback Safeguards**: All audit mutations are atomic within `@Transactional` service methods, ensuring no phantom history persists if business operations fail.
- **Automated Test Suite**: Added `IncidentHistoryTest` covering 12 audit test cases across lifecycle events, pagination, permissions, and transaction rollbacks.

### Module 9: Offline Notification Queue and Synchronization Foundation (Complete)
- **Browser-Side Offline Action Queue**: Persistent IndexedDB storage (`liftcare_offline_db`) holding queued client actions with client-generated UUID idempotency keys.
- **User-Account Scoping**: Actions bound to user email, preventing cross-user queue pollution or unauthorized replay.
- **State Machine & Bounded Exponential Backoff**: Tracks `PENDING`, `SYNCING`, `SYNCED`, `FAILED`, and `NEEDS_REVIEW` states with exponential backoff and auth failure pausing.
- **Server-Side Idempotency Engine**: Deduplication via `idempotency_records` table, SHA-256 payload hashing, cache responses (`X-Idempotency-Cache: HIT`/`MISS`), and mismatch protection.
- **Durable Notification Outbox**: PostgreSQL `notification_outbox` table with delivery state machine (`PENDING`, `PROCESSING`, `SENT`, `RETRY_WAIT`, `FAILED`), deterministic deduplication keys, and transactional event enqueueing for incident creation, critical entrapments, technician assignments, and status updates.
- **In-App Notification & Outbox APIs**: `GET /api/notifications/my`, `PATCH /api/notifications/{id}/read`, and staff inspection endpoints `GET /api/notifications/outbox` and `GET /api/notifications/outbox/items`.
- **Frontend Telemetry & Queue Management UI**: `OfflineQueuePage` with action management, sync controls, in-app notifications view, and staff outbox metrics. Navigation bar queue badge and form fallbacks in `ReportIncidentPage` and `IncidentDetailPage`.
- **Documentation**: Comprehensive architecture guide in `docs/offline-queue-and-synchronization.md`.
- **Automated Test Suite**: Added `NotificationAndIdempotencyTest` (7 tests), expanding backend suite to 85 passing tests.

### Module 10: SLA Monitoring & Automatic Escalation Engine (Complete)
- **Configurable SLA Policies & Precedence**: Relational `sla_policies` schema supporting building-level and category-level overrides down to global baselines, target durations, warning thresholds, escalation tier assignments, and versioning via Flyway `V7__create_sla_policies_and_incident_sla_tracking.sql`.
- **Historical Snapshot Immutability**: Historical incidents snapshot target minutes and computed deadlines at creation time; administrative policy changes never retroactively mutate historical commitments.
- **Dual Independent Clocks**: Response SLA and Resolution SLA tracked separately with states `ON_TRACK`, `APPROACHING_DEADLINE`, `BREACHED`, `MET`, `NOT_APPLICABLE`, and `POLICY_MISSING`. Acknowledgment satisfies response target without satisfying resolution target.
- **Automated Multi-Tier Escalation Engine**: Background evaluation runner (`SlaEvaluationScheduler`, `SlaEvaluationService`) running locally on the building server, advancing incidents through Tier 1 (Technician), Tier 2 (Building Manager), and Tier 3 (Company Supervisor / Administrator) with missing recipient safety handling.
- **Audited System Transitions & Deduplicated Outbox**: Records `actor_type: SYSTEM` audit logs in `incident_history` for `SLA_WARNING_TRIGGERED`, `RESPONSE_SLA_BREACHED`, `RESOLUTION_SLA_BREACHED`, `INCIDENT_ESCALATED`, and `ESCALATION_DELIVERY_FAILED`, alongside deduplicated durable outbox events.
- **Control Room Telemetry & Filtering**: Live metric cards in `ControlRoomPage` for SLA warnings, response breaches, resolution breaches, and escalation tiers, alongside queue badges and multi-dimensional filters.
- **Incident Detail Telemetry**: Real-time SLA tracking panel and timeline presentations in `IncidentDetailPage`.
- **Documentation & Verification**: Comprehensive guide in `docs/sla-monitoring-and-escalation.md`, with 6 integration tests in `SlaAndEscalationTest` bringing the full backend suite to 91/91 passing tests.

### Module 11: Integration Hardening, Reliability Testing & Deployment Validation (Complete)
- **Comprehensive Integration Test Suite**: Added `Module11IntegrationHardeningTest` covering 11 critical integration flows spanning RBAC, full lifecycle state transitions, entrapment safety rules, offline idempotency, SLA dual-warning deduplication, multi-tier escalations, isolated transactions, and system actor attribution.
- **Incident Reopening & SLA Clock Reset**: Proper lifecycle transition from `RESOLVED` back to `IN_PROGRESS`/`ASSIGNED`, clearing `resolvedAt`, resetting `slaWarningSent`, and logging `INCIDENT_REOPENED`.
- **Dual SLA Warning Outbox Deduplication**: Differentiates pre-deadline response warnings (`SLA-WARN-RESPONSE-...`) and resolution warnings (`SLA-WARN-RESOLUTION-...`), preventing dropped warnings.
- **Fault-Tolerant Isolated Evaluation**: `SlaEvaluationService` executes each incident evaluation inside an isolated programmatic transaction, preventing database errors on one ticket from rolling back the batch.
- **Scheduler Overlap Mutex**: Added `AtomicBoolean isEvaluating` in `SlaEvaluationScheduler` to prevent concurrent evaluation runs during heavy load.
- **System Actor Attribution**: Ensured automated safety shutdowns and background escalations record `ActorType.SYSTEM` with `null` user ID, strictly preserving dual actor audit trail integrity.
- **Lazy Initialization Safeguards**: Ensured `IncidentRepository.findById` uses `@EntityGraph` and `evaluateIncident` reloads detached incident entities within active transactions.
- **Test Suite Growth**: Full backend automated test suite expanded to 102 passing tests with 0 failures and 0 errors.

### Module 12: Cloud Coordination & Multi-Building Synchronization (Complete)
- **Local-Server & Central-Cloud Architecture**: Clear architectural separation between autonomous building-local servers and the central coordination platform.
- **Relational Sync Foundation**: Dedicated forward-only migration `V8__create_cloud_coordination_and_sync_tables.sql` introducing `cloud_registered_buildings`, `cloud_sync_outbox`, `cloud_sync_checkpoints`, `cloud_sync_events`, `cloud_incident_replicas`, and `cloud_sync_conflicts`.
- **Durable Local Outbox**: Local mutations (incident creation, status transitions, technician assignments, elevator status changes) automatically enqueue durable sync events in `cloud_sync_outbox` in the same database transaction.
- **Server-to-Server Authentication & Tenant Isolation**: Secure building registration with BCrypt-hashed API keys (`lc_bld_...`); strict validation prevents cross-building submission or data spoofing.
- **Idempotency & Deduplication Engine**: Replayed sync batches are identified via deterministic idempotency keys and acknowledged without creating duplicate records or side effects.
- **Explicit Conflict Management**: Building-authoritative precedence for physical status and triage; conflicting status changes on closed tickets are captured in `cloud_sync_conflicts` for supervisor review and resolution.
- **Offline-First Resilience**: Local operations (reporting, triage, notes, SLA evaluation) never depend on cloud API availability. Disconnection gracefully pauses sync without blocking local users.
- **Multi-Building Telemetry UI**: Local status page (`/cloud-sync`) and central cross-building overview (`/central-dashboard`) with building/severity filtering and conflict resolution controls.
- **Test Suite Completion**: Added `Module12CloudSynchronizationTest` (7 tests), expanding the full backend test suite to 109 passing tests with 0 failures and 0 errors.

---

## Architectural Completion Status

With the completion of **Module 12**, all core planned capabilities across the offline-first building server, local triage, control room, immutable audit trail, offline action queue, SLA escalation engine, integration hardening, and multi-building cloud coordination are fully operational and verified.
