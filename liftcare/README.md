# LiftCare — Offline-First Elevator Issue Management and Automatic Escalation System

**LiftCare** is an offline-first software platform designed to manage elevator emergencies and maintenance issues in residential and commercial buildings. 

Each building operates a dedicated on-premise server and database that processes incident reports, role-based workflows, physical asset infrastructure, emergency triage, and escalations over the local area network (LAN / Wi-Fi) without requiring external internet connectivity or cloud identity providers.

---

## Current Status: Modules 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 & 12 Complete (All Modules Complete)

- **Module 1: Project Setup and Architecture**
  - Vite + React frontend, Spring Boot 3 REST Core, resilience-aware `GET /api/health` telemetry, CORS baseline, unified `ErrorResponse` model.
- **Module 2: Database Design and Entity Creation**
  - Relational JPA domain models (`User`, `Building`, `Elevator`, `IssueCategory`, `Incident`, `IncidentHistory`), string-backed enums, Flyway migrations (`V1`, `V2`), 6 Spring Data JPA repositories, scoped elevator uniqueness, and isolated H2 persistence tests.
- **Module 3: User Authentication and Role-Based Access Control (RBAC)**
  - Local-first identity and security engine with zero external cloud dependencies.
  - BCrypt password hashing (`BCryptPasswordEncoder`) with migration (`V3__add_password_hash_to_users.sql`).
  - Stateless JWT token authentication (HMAC-SHA256) via `io.jsonwebtoken:jjwt:0.12.6`.
  - Spring Security stateless filter chain (`JwtAuthenticationFilter`), custom entry point (401), and access denied handler (403).
  - Public registration defaults to `RESIDENT` role and strictly forbids self-assigned privileged roles (`ADMINISTRATOR`, `COMPANY_SUPERVISOR`).
  - Account status enforcement (inactive accounts cannot log in).
  - Protected endpoints with method security (`@EnableMethodSecurity`, `@PreAuthorize`).
- **Module 4: Building and Elevator Management**
  - Physical facility registry: create, list, retrieve, update, and soft-deactivate buildings.
  - Elevator shaft inventory: create elevators under buildings, enforce scoped uniqueness `(building_id, elevator_code)`, query by building and status.
  - Real-time operational status control: transition elevators between `ACTIVE`, `MAINTENANCE`, and `OUT_OF_SERVICE`.
  - Strict server-side RBAC: `ADMINISTRATOR` manages global buildings; `BUILDING_MANAGER` manages facilities and elevators; `TECHNICIAN` toggles status; `RESIDENT` and `SECURITY_STAFF` have read-only access.
  - Preserves historical integrity: soft deactivation via `active = false` (buildings) and `OUT_OF_SERVICE` (elevators); no destructive cascade deletes.
  - Frontend management interface: `BuildingsPage`, `BuildingDetailPage`, status controls, modals, and responsive layout.
- **Module 5: Incident Reporting and Local Triage**
  - Offline incident submission: `POST /api/incidents`, ticket number generation (`INC-YYYYMMDD-XXXXXX`), verified building & elevator relationships.
  - Reporter authentication verification: reporter identity derived from JWT token; client cannot forge reporter ID.
  - Severity & entrapment safety rules: passenger entrapment mandatorily ranked `CRITICAL`; silent downgrades to `LOW` or `MEDIUM` strictly rejected (HTTP 400).
  - Automatic elevator safety transition: qualifying safety incidents automatically transition the affected elevator to `OUT_OF_SERVICE` in the same transaction.
  - On-site triage workflow: acknowledge ticket, dispatch assignment to certified active technicians, status updates (`IN_PROGRESS`, `RESOLVED`, `CLOSED`), and resolution notes.
  - Role scoping: residents only access their own reported incidents; staff members triage all building tickets on this local node.
  - Immutable audit trail: chronological recording of every action, status change, technician assignment, and safety shutdown in `incident_history`.
  - Frontend triage experience: `ReportIncidentPage`, `IncidentsPage`, `IncidentDetailPage`, triage modals, and emergency entrapment indicators.
- **Module 6: Building Control-Room Dashboard**
  - Dedicated telemetry endpoint: `GET /api/dashboard/summary` providing high-performance database counts for active incidents, severity categories, entrapments, and elevator operational fleet status.
  - Emergency Section: prominent passenger entrapment banner with immediate triage links and cabin location indicators.
  - Prioritized Incident Queue: multi-dimensional filters (Severity, Status, Entrapment, Building, Assigned Technician, Free-text search) and deterministic sorting modes (`EMERGENCY_FIRST`, `OLDEST_UNACKNOWLEDGED`, `NEWEST_FIRST`, `OLDEST_FIRST`).
  - Elevator Fleet & Safety Matrix: operational status tracking (`ACTIVE`, `MAINTENANCE`, `OUT_OF_SERVICE`) distinguishing recorded software state from physical elevator condition, with staff status override dialogs.
  - Local-Network Resilience: functions 100% offline over building LAN; distinguishes local server health from internet availability; alerts operators on stale cached data.
  - Page-Visibility Aware Polling: 30-second background polling that pauses when the tab is inactive and resumes immediately when visible.
- **Module 7: Local Server and Offline Operation**
  - **Local Building Server Deployment**: Docker multi-stage containerization (`backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, and `docker-compose.yml`) deploying PostgreSQL 16, Spring Boot backend, and Nginx reverse proxy.
  - **Persistent Database Volume**: Docker named volume `postgres_data` providing database durability across container stop/reboot cycles.
  - **Zero Internet Dependency**: Purged external CDN assets (Google Fonts removed in favor of native system fonts); zero external runtime dependencies.
  - **Dynamic LAN Access & Routing**: Intelligent `resolveApiBaseUrl()` runtime routing supporting same-origin relative `/api` paths behind Nginx, and dynamic `window.location.hostname` preservation during local development for mobile/tablet LAN devices.
  - **5-State System Health Evaluator**: Real-time diagnostic state machine (`LOCAL_ONLINE`, `LOCAL_LAN_AUTONOMY`, `BACKEND_UNAVAILABLE`, `DATABASE_UNAVAILABLE`, `UNKNOWN`) in `StatusIndicator` and `LandingPage`.
  - **Fault-Tolerant Incident Reporting**: Clear failure indicators and form state preservation on backend/database disconnect to prevent data loss.
  - **Client-Side Exception Shield**: React `ErrorBoundary` preventing unexpected UI crashes from unhandled errors.
  - **CORS LAN Pattern Matching**: `allowedOriginPatterns` in `SecurityConfig` and `CorsConfig` allowing wildcard subnets (e.g. `http://192.168.*:*`).
  - **Comprehensive Operations Guide**: Added `docs/local-deployment-and-offline-operation.md` detailing architecture, container setup, native execution, LAN firewall configuration, persistent volumes, backup/restore routines, and disaster recovery.
- **Module 8: Incident History and Audit Trail**
  - **Relational Audit Data Model**: `incident_history` table extended with `actor_type` (`USER`, `SYSTEM`) and indexed on `(incident_id, action)` and `(incident_id, changed_by_id)` via Flyway `V5__enhance_incident_history.sql`.
  - **Comprehensive Event Coverage**: 10 lifecycle events tracked across incident progression (`INCIDENT_REPORTED`, `ACKNOWLEDGED`, `ASSIGNED`, `TECHNICIAN_REASSIGNED`, `STATUS_CHANGED`, `RESOLVED`, `CLOSED`, `ELEVATOR_OUT_OF_SERVICE`, `ELEVATOR_STATUS_CHANGED`, `WORK_NOTE_ADDED`, `RESOLUTION_NOTES_UPDATED`, `ADMINISTRATIVE_ACTION`).
  - **Server-Derived Identity & Timestamps**: Actors derived strictly from Spring Security JWT context (`@AuthenticationPrincipal`); timestamps generated by server UTC (`Instant.now()`, `@PrePersist`).
  - **Dedicated History Endpoint with Pagination**: `GET /api/incidents/{id}/history` supporting `page`, `size`, `sort` (`ASC`/`DESC`), `action`, `actorType`, and `actorId` filters, returning `IncidentHistoryPageResponse`.
  - **Append Audit Note API**: `POST /api/incidents/{id}/notes` allowing technicians, managers, and reporting residents to log progress and administrative observations.
  - **Frontend Timeline Experience**: Interactive chronological timeline in `IncidentDetailPage` with human-friendly event cards, role badges, state transition arrows, ascending/descending sorting, multi-dimensional category filters, and modal note-entry controls.
  - **Transactional Rollback Safeguards**: All audit mutations are atomic within `@Transactional` service methods, ensuring no phantom history persists if business operations fail.
  - **Automated Test Suite**: Added `IncidentHistoryTest` covering 12 audit test cases across lifecycle events, pagination, permissions, and transaction rollbacks.
  - **Documentation**: Comprehensive operations and design documentation in `docs/incident-history-and-audit-trail.md`.
- **Module 9: Offline Notification Queue and Synchronization Foundation**
  - **Browser-Side Action Queue (IndexedDB)**: `liftcare_offline_db` with `actions` store holding client actions with client-generated UUID `Idempotency-Key` headers, account-scoping (`userEmail`), and bounded exponential backoff retries.
  - **Server-Side Idempotency Engine**: Deduplication via `idempotency_records` table, SHA-256 payload hashing, duplicate detection, and cached HTTP responses (`X-Idempotency-Cache: HIT`/`MISS`) with payload mismatch rejection.
  - **Durable Server Notification Outbox**: PostgreSQL `notification_outbox` table with delivery state machine (`PENDING`, `PROCESSING`, `SENT`, `RETRY_WAIT`, `FAILED`), deduplication keys, and transactional event enqueueing on incident creation, passenger entrapment, technician assignment, and status updates.
  - **In-App Notification & Outbox APIs**: `GET /api/notifications/my`, `PATCH /api/notifications/{id}/read`, `GET /api/notifications/outbox`, and `GET /api/notifications/outbox/items`.
  - **Frontend Queue Management & Telemetry UI**: `OfflineQueuePage` (`/offline-queue`) with action filtering, sync runners, in-app notification reader, and staff outbox metrics. Navigation bar queue badge and form fallbacks in `ReportIncidentPage` and `IncidentDetailPage`.
  - **Documentation**: Comprehensive architecture guide in `docs/offline-queue-and-synchronization.md`.
  - **Automated Test Suite**: Added `NotificationAndIdempotencyTest` (7 tests), expanding backend suite to 85 passing tests.
- **Module 10: SLA Monitoring and Automatic Escalation Engine**
  - **Configurable SLA Policies & Precedence**: Relational `sla_policies` schema supporting building-level and category-level overrides down to global baselines, target durations, warning thresholds, escalation tier assignments, and versioning via Flyway `V7__create_sla_policies_and_incident_sla_tracking.sql`.
  - **Historical Snapshot Immutability**: Historical incidents snapshot target minutes and computed deadlines at creation time; administrative policy changes never retroactively mutate historical commitments.
  - **Dual Independent Clocks**: Response SLA and Resolution SLA tracked separately with states `ON_TRACK`, `APPROACHING_DEADLINE`, `BREACHED`, `MET`, `NOT_APPLICABLE`, and `POLICY_MISSING`. Acknowledgment satisfies response target without satisfying resolution target.
  - **Automated Multi-Tier Escalation Engine**: Background evaluation runner (`SlaEvaluationScheduler`, `SlaEvaluationService`) running locally on the building server, advancing incidents through Tier 1 (Technician), Tier 2 (Building Manager), and Tier 3 (Company Supervisor / Administrator) with missing recipient safety handling.
  - **Audited System Transitions & Deduplicated Outbox**: Records `actor_type: SYSTEM` audit logs in `incident_history` for `SLA_WARNING_TRIGGERED`, `RESPONSE_SLA_BREACHED`, `RESOLUTION_SLA_BREACHED`, `INCIDENT_ESCALATED`, and `ESCALATION_DELIVERY_FAILED`, alongside deduplicated durable outbox events.
  - **Control Room Telemetry & Filtering**: Live metric cards in `ControlRoomPage` for SLA warnings, response breaches, resolution breaches, and escalation tiers, alongside queue badges and multi-dimensional filters.
  - **Incident Detail Telemetry**: Real-time SLA tracking panel and timeline presentations in `IncidentDetailPage`.
  - **Documentation & Verification**: Comprehensive guide in `docs/sla-monitoring-and-escalation.md`, with 6 integration tests in `SlaAndEscalationTest` bringing the full backend suite to 91/91 passing tests.
- **Module 11: Integration Hardening, Reliability Testing & Deployment Validation**
  - **Comprehensive Integration Test Suite**: Implemented `Module11IntegrationHardeningTest` covering 11 critical integration flows: RBAC cross-resident isolation, full lifecycle state transitions, entrapment safety rules, offline idempotency replays/conflicts, validation rejections, dual SLA warning deduplication, automated multi-tier escalation, policy immutability, missing recipient resilience, isolated transactions, and system actor attribution.
  - **Incident Reopening & SLA Clock Reset**: Clean transition from `RESOLVED` back to `IN_PROGRESS`/`ASSIGNED`, clearing `resolvedAt`, resetting `slaWarningSent`, and logging `INCIDENT_REOPENED`.
  - **Dual SLA Warning Outbox Deduplication**: Differentiates pre-deadline response warnings (`SLA-WARN-RESPONSE-...`) and resolution warnings (`SLA-WARN-RESOLUTION-...`), preventing dropped warnings.
  - **Fault-Tolerant Isolated Evaluation**: `SlaEvaluationService` executes each incident evaluation inside an isolated programmatic transaction, preventing database errors on one ticket from rolling back the batch.
  - **Scheduler Overlap Mutex**: Added `AtomicBoolean isEvaluating` in `SlaEvaluationScheduler` to prevent concurrent evaluation runs during heavy load.
  - **System Actor Attribution**: Ensured automated safety shutdowns and background escalations record `ActorType.SYSTEM` with `null` user ID, strictly preserving dual actor audit trail integrity.
  - **Lazy Initialization Safeguards**: Ensured `IncidentRepository.findById` uses `@EntityGraph` and `evaluateIncident` reloads detached incident entities within active transactions.
  - **Full Test Suite Validation**: All 102 backend tests pass (`102/102`) and frontend production build succeeds in ~2s.
  - **Documentation**: Exhaustive verification and deployment analysis in `docs/integration-hardening-and-deployment-verification.md`.
- **Module 12: Cloud Coordination and Multi-Building Synchronization**
  - **Local-Server & Central-Cloud Architecture**: Complete operational autonomy for local building servers over LAN, with optional asynchronous cloud replication when internet is active.
  - **Relational Sync Schema**: Forward-only Flyway migration `V8__create_cloud_coordination_and_sync_tables.sql` providing `cloud_registered_buildings`, `cloud_sync_outbox`, `cloud_sync_checkpoints`, `cloud_sync_events`, `cloud_incident_replicas`, and `cloud_sync_conflicts`.
  - **Durable Local Outbox**: Transactionally enqueues outbound sync events whenever local incidents or elevator operational states change.
  - **Server-to-Server Authentication & Tenant Isolation**: Secure building registration with BCrypt-hashed server API keys (`lc_bld_...`); header vs. payload validation prevents cross-tenant data tampering.
  - **Idempotency & Deduplication Engine**: Replayed sync batches are identified via deterministic idempotency keys and acknowledged without duplicate records or side effects.
  - **Conflict Management**: Physical on-site actions are building-authoritative; conflicting status regressions on closed tickets are captured in `cloud_sync_conflicts` for central supervisor review and resolution.
  - **Multi-Building Telemetry UI**: Local status panel (`/cloud-sync`) and central multi-building dashboard (`/central-dashboard`) with building/severity filtering and conflict resolution controls.
  - **Automated Test Suite**: Added `Module12CloudSynchronizationTest` (7 tests), expanding backend suite to 109 passing tests (`109/109`) and verified frontend production build.
  - **Documentation**: Comprehensive architecture guide in `docs/cloud-coordination-and-synchronization.md`.

### Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Frontend** | React 18, Vite 6, JavaScript, React Router 6, Context API, Responsive CSS3 |
| **Backend** | Java 21 / 25, Spring Boot 3.3.4, Spring Web, Spring Data JPA, Spring Security 6, JJWT 0.12.6, Bean Validation |
| **Database** | PostgreSQL 16+ (Flyway migrations V1, V2, V3, V4, HikariCP connection pool, non-blocking startup fallback) |
| **Testing** | JUnit 5, Spring Boot Test, Spring Security Test, MockMvc, H2 Database (strictly in `test` scope) |
| **Build & Tooling** | Maven 3.9+ (includes self-contained Maven Wrapper `mvnw`), npm / Node.js 18+ |

---

## Directory Structure

```
liftcare/
├── frontend/                     # React + Vite client application
│   ├── src/
│   │   ├── components/           # Navbar, StatusIndicator, ArchitectureDiagram, SystemOverview,
│   │   │                         # Footer, ProtectedRoute, ErrorBoundary
│   │   ├── context/              # AuthContext (JWT session management, state, and hooks)
│   │   ├── pages/                # LandingPage, LoginPage, RegisterPage, DashboardPage,
│   │   │                         # ControlRoomPage, BuildingsPage, BuildingDetailPage,
│   │   │                         # ReportIncidentPage, IncidentsPage, IncidentDetailPage
│   │   ├── services/             # API client services (api.js with auth, assets, triage & 5-state connectivity)
│   │   ├── App.jsx               # React Router root configuration & ErrorBoundary wrapper
│   │   ├── App.css               # Design system & responsive styles
│   │   ├── index.css             # Base design tokens & CSS reset (native system fonts)
│   │   └── main.jsx              # DOM entry point
│   ├── .dockerignore             # Production Docker exclusions
│   ├── .env.example              # Frontend environment template
│   ├── Dockerfile                # Multi-stage production build (Node 20 Alpine -> Nginx Alpine)
│   ├── index.html                # HTML document (zero external CDN dependencies)
│   ├── nginx.conf                # Nginx SPA fallback, security headers & /api reverse proxy
│   ├── package.json              # NPM dependencies & scripts
│   └── vite.config.js            # Vite bundler configuration (Port 5173, host: true)
│
├── backend/                      # Spring Boot 3 on-premise local server
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/liftcare/
│   │   │   │   ├── config/       # SecurityConfig, CorsConfig (allowedOriginPatterns for LAN)
│   │   │   │   ├── controller/   # HealthController, AuthController, BuildingController, ElevatorController,
│   │   │   │   │                 # IncidentController, IssueCategoryController, UserController, DashboardController
│   │   │   │   ├── dto/          # AuthResponse, LoginRequest, RegisterRequest, UserResponse,
│   │   │   │   │                 # BuildingCreateRequest, BuildingUpdateRequest, BuildingResponse,
│   │   │   │   │                 # ElevatorCreateRequest, ElevatorUpdateRequest, ElevatorStatusUpdateRequest,
│   │   │   │   │                 # ElevatorResponse, IncidentCreateRequest, IncidentAssignRequest,
│   │   │   │   │                 # IncidentStatusUpdateRequest, IncidentResponse, IncidentDetailResponse,
│   │   │   │   │                 # IncidentHistoryResponse, IssueCategoryResponse, MessageResponse, HealthResponse, ErrorResponse,
│   │   │   │   │                 # DashboardSummaryResponse, IncidentSummaryDto, ElevatorSummaryDto
│   │   │   │   ├── exception/    # GlobalExceptionHandler, LiftCareException, ResourceNotFoundException, DuplicateResourceException
│   │   │   │   ├── model/        # User, Building, Elevator, IssueCategory, Incident, IncidentHistory
│   │   │   │   │                 # Enums: UserRole, ElevatorOperationalStatus, SeverityLevel, IncidentStatus
│   │   │   │   ├── repository/   # UserRepository, BuildingRepository, ElevatorRepository,
│   │   │   │   │                 # IssueCategoryRepository, IncidentRepository, IncidentHistoryRepository
│   │   │   │   ├── security/     # JwtProperties, JwtService, CustomUserDetails, CustomUserDetailsService,
│   │   │   │   │                 # JwtAuthenticationFilter, JwtAuthenticationEntryPoint, JwtAccessDeniedHandler
│   │   │   │   ├── service/      # AuthService, HealthService, BuildingService, ElevatorService, IncidentService, DashboardService
│   │   │   │   └── LiftCareApplication.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/ # Flyway SQL migrations (V1 schema, V2 seed categories, V3 password hash)
│   │   └── test/
│   │       ├── java/com/liftcare/
│   │       │   ├── DashboardControllerTest.java          # 12 Control-Room Dashboard & Telemetry tests
│   │       │   ├── IncidentControllerTest.java           # 17 Incident Reporting & Local Triage tests
│   │       │   ├── BuildingAndElevatorControllerTest.java # 15 Building & Elevator asset management tests
│   │       │   ├── AuthControllerTest.java               # 12 Authentication & RBAC integration tests
│   │       │   ├── HealthControllerTest.java             # 2 Health endpoint & capabilities tests
│   │       │   └── repository/EntityRepositoryTest.java  # 8 JPA persistence & constraint tests
│   │       └── resources/
│   │           └── application-test.yml                  # Isolated H2 in-memory test configuration
│   ├── .dockerignore             # Docker build exclusions
│   ├── Dockerfile                # Multi-stage production build (Eclipse Temurin 21 JDK -> JRE Alpine, non-root user)
│   ├── .mvn/wrapper/             # Maven wrapper configuration
│   ├── mvnw & mvnw.cmd           # Cross-platform Maven wrapper scripts
│   └── pom.xml                   # Maven dependencies and build plugins
│
├── docs/                         # Technical architecture documentation
│   ├── architecture.md           # Multi-tier topology and offline guarantees
│   ├── database-design.md        # Detailed ERD, table specs, and constraints
│   ├── authentication-and-rbac.md# Module 3 security spec, sequence diagrams, and endpoints
│   ├── building-and-elevator-management.md # Module 4 assets, scoped uniqueness & permissions
│   ├── incident-reporting-and-triage.md    # Module 5 incident triage, entrapment rules & audit trails
│   ├── control-room-dashboard.md # Module 6 telemetry aggregation, triage & elevator status matrix
│   ├── local-deployment-and-offline-operation.md # Module 7 local server, Docker, LAN access & disaster recovery
│   ├── incident-history-and-audit-trail.md       # Module 8 immutable audit trail, actor types & timeline
│   └── module-integration.md     # Roadmap and completed integration status
│
├── .env.example                  # Central environment template with safe placeholders
├── .gitignore                    # Git exclusions for Java, Node, IDE, Secrets
├── docker-compose.yml            # Module 7 containerized appliance stack (Postgres, Backend, Frontend)
└── README.md                     # Project documentation (this file)
```

---

## API Endpoints Reference

### Incident History & Audit Trail Endpoints (Module 8)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/incidents/{id}/history` | Authenticated | Retrieve paginated chronological audit trail for incident (supports `?page=`, `?size=`, `?sort=ASC/DESC`, `?action=`, `?actorType=`, `?actorId=`) |
| `POST`| `/api/incidents/{id}/notes` | Authenticated | Append work note, diagnostic note, or administrative observation to incident audit trail |

### Control-Room & Telemetry Endpoints (Module 6)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/dashboard/summary` | Security, Manager, Tech, Supervisor, Admin | Aggregated telemetry counts for active incidents, entrapments, and fleet status (supports `?buildingId=`) |
| `GET` | `/api/elevators` | Authenticated | List all elevators across buildings on the node (supports `?buildingId=` and `?status=`) |
| `PATCH`| `/api/elevators/{id}/status` | Admin, Manager, Tech | Fast update of elevator operational status with audit log |

### Incident Reporting & Local Triage (Module 5)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/incidents` | Authenticated | Create a new incident ticket (validates severity, entrapment, safety shutdown) |
| `GET` | `/api/incidents/{id}` | Authenticated | Retrieve incident details and chronological audit history (residents scoped to own tickets) |
| `GET` | `/api/incidents` | Authenticated | List incidents (supports `?status=`, `?severity=`, `?buildingId=`, `?entrapmentOnly=`) |
| `GET` | `/api/incidents/my` | Authenticated | List incidents reported by the authenticated user |
| `GET` | `/api/buildings/{id}/incidents` | Authenticated | List incidents for a specific building |
| `GET` | `/api/elevators/{id}/incidents` | Authenticated | List incidents for a specific elevator shaft |
| `PATCH`| `/api/incidents/{id}/acknowledge` | Security, Manager, Tech, Supervisor, Admin | Acknowledge ticket (`REPORTED` -> `ACKNOWLEDGED`) |
| `PATCH`| `/api/incidents/{id}/assign` | Manager, Security, Supervisor, Admin | Dispatch and assign ticket to certified active technician |
| `PATCH`| `/api/incidents/{id}/status` | Technician, Manager, Admin | Update status (`IN_PROGRESS`, `RESOLVED`, `CLOSED`) and record notes |
| `GET` | `/api/issue-categories` | Authenticated | List active issue categories with default severities |
| `GET` | `/api/users/technicians` | Staff Roles | List active technicians for assignment dropdown |

### Asset Management Endpoints (Module 4)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/buildings` | `ADMINISTRATOR` | Register a new building complex |
| `GET` | `/api/buildings` | Authenticated | List buildings (supports `?includeInactive=true` for privileged roles) |
| `GET` | `/api/buildings/{id}` | Authenticated | Retrieve building by ID (includes elevator count) |
| `PUT` | `/api/buildings/{id}` | `ADMINISTRATOR`, `BUILDING_MANAGER` | Update building name, address, city, node ID |
| `PATCH`| `/api/buildings/{id}/deactivate` | `ADMINISTRATOR` | Soft-deactivate building without deleting records |
| `POST` | `/api/buildings/{id}/elevators` | `ADMINISTRATOR`, `BUILDING_MANAGER` | Create elevator under building (enforces scoped code uniqueness) |
| `GET` | `/api/buildings/{id}/elevators` | Authenticated | List elevators for building (supports `?status=...`) |
| `GET` | `/api/elevators/{id}` | Authenticated | Retrieve elevator details by ID |
| `PUT` | `/api/elevators/{id}` | `ADMINISTRATOR`, `BUILDING_MANAGER` | Update elevator code, location, manufacturer, model, floors |
| `PATCH`| `/api/elevators/{id}/status` | `ADMINISTRATOR`, `BUILDING_MANAGER`, `TECHNICIAN` | Fast update of elevator operational status |
| `PATCH`| `/api/elevators/{id}/deactivate` | `ADMINISTRATOR`, `BUILDING_MANAGER` | Soft-deactivate elevator (sets `OUT_OF_SERVICE`) |

### Authentication & Core Endpoints (Modules 1 & 3)

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Register new resident account (email validation & BCrypt hashing) |
| `POST` | `/api/auth/login` | Public | Authenticate credentials and emit signed JWT token |
| `GET` | `/api/auth/me` | Authenticated | Retrieve active user profile from Bearer token |
| `POST` | `/api/auth/logout` | Authenticated | Acknowledge session logout |
| `GET` | `/api/health` | Public | Server telemetry, database status, and node ID |

---

## Environment Configuration

Copy the template configuration file:

```bash
cp .env.example .env
```

Available environment variables:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SERVER_PORT` | Port for Spring Boot backend | `8080` |
| `FRONTEND_ORIGIN` | Allowed origin for CORS | `http://localhost:5173` |
| `DB_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/liftcare_db` |
| `DB_USERNAME` | PostgreSQL user | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `FLYWAY_ENABLED` | Enable Flyway migrations on startup | `false` |
| `JPA_DDL_AUTO` | Hibernate DDL strategy | `none` |
| `JWT_SECRET` | 256-bit signing key for JWT HMAC-SHA256 | `liftcare-development-secret-key-must-be-at-least-256-bits-long-32-bytes` |
| `JWT_EXPIRATION_MS` | JWT token validity duration in milliseconds | `86400000` (24h) |
| `LIFTCARE_ENV` | Environment name | `development` |
| `LIFTCARE_NODE_ID` | Local building node identifier | `building-local-node-01` |
| `VITE_API_BASE_URL` | Frontend API target | `http://localhost:8080` |

---

## How to Set Up PostgreSQL & Run Migrations

When PostgreSQL is installed and running:

1. Create the database via `psql`:
   ```sql
   CREATE DATABASE liftcare_db;
   CREATE USER postgres WITH ENCRYPTED PASSWORD 'postgres';
   GRANT ALL PRIVILEGES ON DATABASE liftcare_db TO postgres;
   ```
2. Enable Flyway in your `.env`:
   ```env
   FLYWAY_ENABLED=true
   DB_URL=jdbc:postgresql://localhost:5432/liftcare_db
   DB_USERNAME=postgres
   DB_PASSWORD=postgres
   ```
3. Run the backend. Flyway will automatically execute:
   - `V1__create_core_tables.sql`: Base tables, foreign keys, constraints, and indexes.
   - `V2__insert_default_issue_categories.sql`: Seeds standard elevator defect categories.
   - `V3__add_password_hash_to_users.sql`: Adds `password_hash` column to the `users` table.
   - `V4__add_assigned_to_to_incidents.sql`: Adds `assigned_to_id` column to the `incidents` table.

> **Offline Resilience Note**: If PostgreSQL is not installed or running, the backend will still start in development mode. The `/api/health` endpoint reports `database.status: "DISCONNECTED"` with full diagnostic details without crashing.

---

## How to Run Tests

The test suite runs with an isolated H2 in-memory database using PostgreSQL compatibility mode (`scope: test`). No external PostgreSQL instance is needed to run automated tests.

Run all tests from the `backend/` directory:

### Windows:
```powershell
cd d:\KONE\liftcare\backend
.\mvnw.cmd test
```

### Linux / macOS:
```bash
cd liftcare/backend
./mvnw test
```

### Test Suite Summary (78 of 78 Tests Passed):
- **`IncidentHistoryTest` (12 tests)**:
  1. Incident creation produces history entry with `ActorType.USER` and server-generated timestamp.
  2. Full status transition lifecycle records previous and new values, timestamps, and actors (`REPORTED`, `ACKNOWLEDGED`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`).
  3. Technician reassignment records `TECHNICIAN_REASSIGNED` with previous and new technician values.
  4. Elevator safety shutdown (`ELEVATOR_OUT_OF_SERVICE`) and triage status restoration (`ELEVATOR_STATUS_CHANGED`).
  5. Adding work notes (`WORK_NOTE_ADDED`) and administrative notes (`ADMINISTRATIVE_ACTION`).
  6. Dedicated history endpoint `GET /api/incidents/{id}/history` supports pagination (`page`, `size`) and chronological ordering (`ASC`/`DESC`).
  7. Dedicated history endpoint action filtering returns only matching event types.
  8. Resident cannot view another resident's incident history (403 Forbidden).
  9. Operational staff roles (Security, Technician, Manager, Admin) can view authorized incident history.
  10. History entries cannot be modified (`PUT`) or deleted (`DELETE`) through application APIs (405 Method Not Allowed).
  11. Actor identity is derived strictly from authentication context rather than client payload.
  12. Failed transactions rollback completely without leaving phantom history entries.
- **`DashboardControllerTest` (12 tests)**:
  1. Rejection of unauthenticated dashboard summary requests (401 Unauthorized).
  2. Resident access rejected with 403 Forbidden.
  3. Security staff receives accurate real-time telemetry counts and emergency queue.
  4. Building manager authorized access (200 OK).
  5. Technician authorized access (200 OK).
  6. Company supervisor authorized access (200 OK).
  7. Administrator authorized access (200 OK).
  8. Building-scoped query filter computes counts for specific building complex.
  9. Non-existent building ID returns 404 Not Found.
  10. Listing all elevators across buildings (`GET /api/elevators`).
  11. Filtering elevators by operational status (`GET /api/elevators?status=OUT_OF_SERVICE`).
  12. Filtering incidents by assigned technician (`GET /api/incidents?assignedToId=...`).
- **`IncidentControllerTest` (17 tests)**:
  1. Successful incident creation by authenticated resident.
  2. Rejection of unauthenticated incident creation (401).
  3. Reporter identity derived from authentication rather than request input.
  4. Rejection of nonexistent building (404).
  5. Rejection of inactive building (400).
  6. Rejection of nonexistent elevator (404).
  7. Rejection when elevator does not belong to selected building (400).
  8. Rejection of missing required fields (400).
  9. Default severity inheritance from issue category and initial status `REPORTED`.
  10. Entrapment classified for immediate attention (`CRITICAL` severity).
  11. Entrapment cannot be silently downgraded to low/medium severity (400).
  12. Qualifying safety incident triggers automatic elevator status change to `OUT_OF_SERVICE`.
  13. Non-qualifying minor incident does not trigger unjustified status change.
  14. Incident triage workflow & audit trail (Acknowledge, Assign, Status Update, Resolve).
  15. Unauthorized users cannot update or assign incidents (403).
  16. Resident cannot view another resident's incident ticket (403 Access Denied).
  17. Scoped listing: residents only see their own reports; managers see all incidents.
- **`BuildingAndElevatorControllerTest` (15 tests)**:
  - Scoped elevator code uniqueness, building CRUD, operational status overrides, permission boundaries.
- **`AuthControllerTest` (12 tests)**:
  - Registration, BCrypt password hashing, email duplicates, role boundaries, and login tokens.
- **`HealthControllerTest` (2 tests)**:
  - System health report, node ID, database connection status, and 404 JSON error format.
- **`EntityRepositoryTest` (8 tests)**:
  - Persistence, string enums, scoped unique constraints, and audit trails.

---

## How to Run the Applications

### Option A: Containerized Appliance Deployment (Docker Compose)

For production-like local building server rollout:

1. Copy the environment configuration template:
   ```bash
   cp .env.example .env
   # Edit .env with secure database credentials and JWT signing secret
   ```

2. Launch all services with persistent storage:
   ```bash
   docker compose up -d --build
   ```

3. Access the application:
   - On the server host: `http://localhost` (Port 80)
   - On client devices across the LAN: `http://<SERVER_LAN_IP>` (e.g., `http://192.168.1.50`)
   - Backend health probe: `http://localhost/api/health`

4. Inspect services or view logs:
   ```bash
   docker compose ps
   docker compose logs -f backend
   ```

5. Stop or restart:
   ```bash
   docker compose stop   # pause containers
   docker compose start  # resume containers
   docker compose down   # clean stop (preserves postgres_data volume)
   ```

---

### Option B: Native Development Execution

If running directly on the host machine without Docker:

#### 1. Run the Backend Server
Set environment variables or ensure PostgreSQL is reachable at `localhost:5432`, then run:
```powershell
cd d:\KONE\liftcare\backend
.\mvnw.cmd spring-boot:run
```
Backend starts at `http://localhost:8080`.

#### 2. Run the Frontend Client
```powershell
cd d:\KONE\liftcare\frontend
npm install
npm run dev -- --host
```
Frontend starts at `http://localhost:5173`. The `--host` flag ensures the dev server binds to `0.0.0.0` so other devices on the LAN can reach it.

#### 3. Build the Frontend Production Bundle
```powershell
cd d:\KONE\liftcare\frontend
npm run build
```
Creates the optimized production bundle inside `frontend/dist`.

---

### Accessing LiftCare from Devices Across the Building LAN

1. **Find Server LAN IP**: Run `ipconfig` (Windows) or `ip addr show` (Linux) to find the server's local IPv4 address (e.g. `192.168.1.50`).
2. **Open Firewall Ports**:
   - For Docker: allow inbound TCP port `80`.
   - For Native: allow inbound TCP ports `5173` and `8080`.
3. **Connect from Client**: Open any mobile or desktop web browser on a device connected to the same Wi-Fi / LAN and navigate to `http://<SERVER_LAN_IP>` (or `:5173`).
4. **Offline Resilience**: If the external broadband connection is severed, the top status badge will indicate `Local LAN Autonomy (Offline-First)`. Residents and staff can continue reporting incidents, triaging tickets, and updating elevator fleet statuses normally.

---

## Recommended Next Step

**Module 9: SLA Monitoring and Automatic Escalation Engine**
- Implement scheduled background evaluations (`@EnableScheduling`) for open incident timers.
- Configure severity-driven response and resolution SLA countdowns.
- Automatic Tier-1/2/3 escalation triggers with audit logging in `incident_history` using `ActorType.SYSTEM`.
- Warning notices for incidents approaching breach thresholds on the control-room dashboard.
