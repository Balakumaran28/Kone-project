# LiftCare — Module 11: Integration Hardening, Reliability Testing & Deployment Validation

## 1. Overview & Objectives

LiftCare is an offline-first elevator issue management and automatic escalation system designed to run on a local building server over an isolated local area network (LAN). Following the implementation of Modules 1–10, **Module 11** focuses on end-to-end integration hardening, cross-module reliability verification, edge-case remediation, and deployment validation.

### Core Objectives
1. **End-to-End System Integration**: Verify seamless coordination across Authentication (Module 3), Physical Asset Registry (Module 4), Incident Lifecycle & Triage (Module 5), Control Room Dashboard (Module 6), Local LAN Architecture (Module 7), Audit Trail (Module 8), Offline Queue & Idempotency Engine (Module 9), and SLA Escalation Engine (Module 10).
2. **Defect Remediation & Hardening**: Fix verified edge cases in incident reopening, dual SLA warning deduplication, isolated evaluation transactions, scheduler concurrency, and system actor attribution.
3. **Automated Integration Test Suite**: Establish a repeatable, end-to-end integration test suite (`Module11IntegrationHardeningTest`) that verifies critical production flows without relying on mocks.
4. **Transparent Deployment Validation**: Validate Flyway database migrations (V1–V7), Docker Compose orchestration, and document the real state of the local host environment.

---

## 2. Environment Status & Test Methodology

### 2.1 Development Host Reality Check
- **Host Operating System**: Windows (AMD64)
- **Java Runtime**: OpenJDK 25.0.2 / 21
- **Node.js Runtime**: Node.js v24, Vite 6.4.5
- **Docker Daemon**: **NOT installed / NOT running** on the host operating system. The `docker` and `docker compose` CLI commands are unavailable.
- **PostgreSQL Daemon**: **NOT installed / NOT running** locally as a host service. The `psql` CLI is unavailable.

### 2.2 Testing Strategy: H2 in PostgreSQL Compatibility Mode
Because PostgreSQL and Docker are not installed on the dev machine, all automated test suites run using an embedded H2 database configured with:
```properties
spring.datasource.url=jdbc:h2:mem:liftcare_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULLS_LAST=TRUE
```
- **Schema Migration Execution**: Flyway applies all migrations (`V1` through `V7`) sequentially against H2 during test bootstrapping, verifying schema syntax validity and constraint definitions.
- **PostgreSQL Dialect Compatibility**: All 7 Flyway migration scripts adhere strictly to ANSI SQL and PostgreSQL standard DDL:
  - `BIGSERIAL` primary keys with automatic sequence generation.
  - `TIMESTAMP WITH TIME ZONE` for UTC temporal precision.
  - Standard table constraints (`UNIQUE`, `FOREIGN KEY ... ON DELETE CASCADE/SET NULL`, `CHECK`).
  - Standard B-Tree indexes for fast filtering and deduplication lookups.

### 2.3 Containerized Deployment Architecture
In production deployment on a physical building appliance, the stack is orchestrated via `docker-compose.yml`:
- **Database**: `postgres:16-alpine` with healthcheck (`pg_isready -U postgres -d liftcare`). Persistent storage is mounted to volume `liftcare_postgres_data`.
- **Backend Service**: Multi-stage Docker container (`eclipse-temurin:21-jre-alpine`) depending on healthy Postgres (`condition: service_healthy`), with HTTP healthcheck (`wget -qO- http://localhost:8080/api/health`).
- **Frontend Service**: Multi-stage Docker container (`nginx:1.27-alpine`) serving compiled React assets, reverse-proxying `/api` requests to `backend:8080`, with healthcheck (`wget -qO- http://localhost:80/`).

---

## 3. Confirmed Defects and Hardening Fixes

### 3.1 Incident Reopening Lifecycle and SLA Clock Reset
- **Issue**: When an incident transitioned from `RESOLVED` back to `IN_PROGRESS` or `ASSIGNED` (e.g. resident or manager noted issue recurred), `resolvedAt` remained set, preventing proper resolution SLA tracking, and `slaWarningSent` remained true, suppressing subsequent resolution warnings.
- **Fix**: In `IncidentService.updateIncidentStatus`:
  - When transitioning from `RESOLVED` back to `IN_PROGRESS` or `ASSIGNED`, `resolvedAt` is reset to `null`.
  - `slaWarningSent` is reset to `false` to enable resolution warning triggers.
  - An immutable audit trail entry with action `INCIDENT_REOPENED` is appended to `incident_history`.
  - Frontend `IncidentDetailPage.jsx` now presents `INCIDENT_REOPENED` with dedicated visual styling.

### 3.2 Dual SLA Warning Deduplication Scopes
- **Issue**: An incident could receive a pre-deadline warning for response, and later a warning for resolution. However, the notification outbox deduplication key previously did not distinguish between response and resolution warning scopes, leading to the resolution warning being dropped by deduplication.
- **Fix**: In `NotificationOutboxService`, added an overloaded method `queueSlaWarning(Incident incident, String warningScope, String warningDetails)` that generates distinct deduplication keys:
  - Response warning: `SLA-WARN-RESPONSE-<ticketNumber>`
  - Resolution warning: `SLA-WARN-RESOLUTION-<ticketNumber>`
- Backward compatibility is preserved via a delegate method.

### 3.3 Isolated Transaction Boundaries for SLA Evaluation
- **Issue**: If `SlaEvaluationService.evaluateSlaPoliciesAndEscalate()` processed 20 active incidents in a single transaction, a database constraint or runtime error on one incident could roll back the entire batch, preventing warnings or escalations for all other valid incidents.
- **Fix**: Injected `PlatformTransactionManager` into `SlaEvaluationService` and used a `TransactionTemplate` to isolate each incident evaluation within its own atomic transaction boundary (`executeWithoutResult`). An unhandled exception on ticket A is logged and skipped, allowing tickets B through Z to complete successfully.

### 3.4 Scheduler Overlap Prevention
- **Issue**: Under heavy database load or high incident counts, a scheduled execution of `SlaEvaluationScheduler.evaluateSlaPolicies()` could take longer than the 60-second fixed delay, leading to overlapping execution threads.
- **Fix**: Introduced `AtomicBoolean isEvaluating` in `SlaEvaluationScheduler`. If an evaluation cycle is currently active when the timer fires, the cycle is safely skipped and logged.

### 3.5 System Actor Attribution for Automated Safety Actions
- **Issue**: When an entrapment report triggered an automated safety transition of an elevator to `OUT_OF_SERVICE`, the audit history previously recorded the reporting user with `ActorType.USER`.
- **Fix**: In `IncidentService.createIncident`, automated safety transitions now record `actorUser = null` and `actorType = ActorType.SYSTEM`, maintaining a strict separation between human actions and automated system actions.

### 3.6 LazyInitializationException Prevention
- **Issue**: Passing detached incidents to `SlaEvaluationService.evaluateIncident` caused Hibernate `LazyInitializationException` when accessing lazy relationships (`slaPolicy`, `elevator`, `building`).
- **Fix**:
  - Overrode `findById` in `IncidentRepository` with `@EntityGraph(attributePaths = {"building", "elevator", "slaPolicy", "issueCategory", "assignedTo"})`.
  - Updated `evaluateIncident` to always re-fetch the incident entity within its own transaction context.

---

## 4. Module 11 Integration Test Suite Summary

The automated integration test suite is implemented in [`Module11IntegrationHardeningTest.java`](file:///d:/KONE/liftcare/backend/src/test/java/com/liftcare/Module11IntegrationHardeningTest.java). It contains 11 tests covering the critical integration points across all modules:

| Test Name | Test Area | Verification Criteria | Status |
|:---|:---|:---|:---:|
| `testRoleBasedAccessControlAndCrossResidentPrivacy` | Auth & Security (M3) | Verifies resident cannot view other residents' reports (403 Forbidden). Technicians and Managers can view reports. | **PASSED** |
| `testFullIncidentLifecycleTransitionMatrix` | Incident Lifecycle (M5, M8) | Reported $\to$ Acknowledged $\to$ Assigned $\to$ In Progress $\to$ Resolved $\to$ Reopened $\to$ Resolved $\to$ Closed. Reopening clears resolved timestamp. Closed tickets reject further mutations. | **PASSED** |
| `testEntrapmentSafetyAndStatusAssertion` | Safety Shutdown (M4, M5) | Entrapment cannot be downgraded to LOW (400 Bad Request). Entrapment defaults to CRITICAL and automatically shuts down elevator to `OUT_OF_SERVICE` with `ActorType.SYSTEM` audit. | **PASSED** |
| `testOfflineIdempotentIncidentSubmissionAndConflictRejection` | Offline & Idempotency (M9) | First submission with idempotency key returns 201 (`X-Idempotency-Cache: MISS`). Duplicate identical submission returns 200 (`X-Idempotency-Cache: HIT`). Conflicting payload with reused key returns 409 Conflict. | **PASSED** |
| `testValidationFailureRejectionAndNoSideEffects` | Validation & Robustness (M1) | Submitting an incident with empty description, null elevator, or non-existent building returns 400 Bad Request and creates zero database records. | **PASSED** |
| `testResponseAndResolutionWarnings` | SLA Warnings (M10, M11) | Response warning fires 5m before response deadline (`SLA-WARN-RESPONSE`). Acknowledging resets warning flag. Resolution warning fires 5m before resolution deadline (`SLA-WARN-RESOLUTION`). | **PASSED** |
| `testLateBreachesAndAutomatedEscalationTiers` | SLA Escalation (M10) | Evaluator advances incident across Tier 1 (Technician), Tier 2 (Building Manager), and Tier 3 (Administrator) as deadlines are breached. | **PASSED** |
| `testMissingPolicySnapshotPreservation` | SLA Immutability (M10) | Incidents snapshot SLA parameters at creation time. Deactivating or modifying the policy does not mutate the historical incident's deadlines. | **PASSED** |
| `testEscalationRecipientAbsenceResilience` | Escalation Safety (M10) | When no active user holds the escalation target role, system logs `ESCALATION_DELIVERY_FAILED` and enqueues outbox failure without crashing or blocking. | **PASSED** |
| `testIsolatedTransactionFaultTolerance` | Concurrency & Fault Isolation (M11) | Verifies that SLA evaluation runs each ticket in an isolated transaction boundary, preventing rollbacks from cascading across the batch. | **PASSED** |
| `testSystemActorAndOutboxDeliveryIntegrity` | Audit & Outbox (M8, M9) | Verifies human user actions record `ActorType.USER` with user ID, and background breaches record `ActorType.SYSTEM` with `null` user ID. Outbox items transition to `PENDING`. | **PASSED** |

### Execution Results
```
[INFO] Running com.liftcare.Module11IntegrationHardeningTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 26.45 s
[INFO] 
[INFO] Results:
[INFO] Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 5. Offline Recovery and Client Synchronization Foundation

### 5.1 Browser-Side Offline Queue Simulation
The client-side offline queue operates inside the browser via IndexedDB (`liftcare_offline_db`):
- **User-Account Scoping**: Actions queued offline are tied to the authenticated user's ID/email. Switching accounts on a shared terminal does not allow User B to replay or leak User A's un-synced incident reports.
- **Idempotency Keys**: Every queued action generates a unique UUID `idempotencyKey` client-side.
- **Bounded Exponential Backoff**: When the local LAN is unreachable, the client backs off retries:
  $$\text{delay} = \min(60\,\text{s}, 1.5^{\text{retryCount}} \times 2\,\text{s}) \pm 10\% \text{ jitter}$$
- **Auth Failure Interruption**: If the server returns a 401 Unauthorized during synchronization, queue processing immediately pauses with status `AUTH_REQUIRED` until the user re-authenticates.

### 5.2 Server-Side Deduplication & Replay Verification
1. Replaying an identical queued request returns the cached JSON response and ticket number (`X-Idempotency-Cache: HIT`).
2. If client tampering or a race condition alters the payload for an existing idempotency key, the server rejects the request with HTTP 409 Conflict (`X-Idempotency-Cache: MISMATCH`) without mutating the database.

---

## 6. Single-Instance Local Server Operational Assumptions

1. **Single Node / Single Appliance**: The local building server runs as a standalone Docker Compose instance or native service on the building's local network. There is no multi-node cluster or distributed consensus engine.
2. **Scheduled Task Concurrency**: `SlaEvaluationScheduler` uses an `AtomicBoolean` mutex to ensure that only one evaluation cycle executes at any given time on the JVM.
3. **Clock Monotonicity & Time Zone**: The server clock is assumed to be synchronized via local NTP or hardware RTC. All timestamps are persisted as UTC `Instant` (`TIMESTAMP WITH TIME ZONE`).

---

## 7. Explicit Operational Boundaries and Non-Goals

To maintain strict college project boundaries and real-world safety transparency:
- **No Cloud Synchronization**: There is no cross-building cloud coordination, master database replication, or cloud relay implemented.
- **No External SMS / Email Gateways**: Notifications are persisted in the local relational outbox (`notification_outbox`) and exposed via in-app API endpoints (`/api/notifications/my`). No external carrier SMS or SMTP services are connected.
- **No Elevator Hardware Sensors**: Incident severity, status, and elevator operational states are managed via software records and user reports. The system does not interface with physical elevator PLCs, CAN buses, or IoT accelerometers.
- **No Life-Safety Certification**: LiftCare is a software incident logging and escalation tool. It does not replace physical elevator interlocks, fire alarm integration, or certified emergency dispatch services. Emergency entrapments require immediate physical verification by building staff.
