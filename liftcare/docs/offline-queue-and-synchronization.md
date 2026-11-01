# LiftCare — Module 9: Offline Notification Queue & Synchronization Foundation

## 1. Architectural Overview & Context

LiftCare is engineered for **Local Building Resiliency**. In elevator operations, physical environments such as concrete basements, steel-shielded machine rooms, and enclosed elevator shafts frequently experience intermittent or zero connectivity.

To guarantee zero data loss, LiftCare distinguishes between two distinct operational boundaries:

```
[Resident / Staff Device]
       │
       │ (Intermittent LAN / Wi-Fi inside elevator shaft / basement)
       ▼
┌──────────────────────────────────────────────┐
│  Browser-Side Offline Action Queue (IndexedDB)│
│  - Persistent storage in browser              │
│  - Client-generated UUID Idempotency Keys    │
│  - User-account scoping                      │
│  - Bounded exponential backoff retries        │
└──────────────────────┬───────────────────────┘
                       │ REST HTTP (Idempotency-Key Header)
                       ▼
┌──────────────────────────────────────────────┐
│  LiftCare Local Building Server (Spring Boot) │
│  - Server-side SHA-256 request deduplication │
│  - Idempotency records repository            │
│  - Cached HTTP responses (X-Idempotency-Cache)│
├──────────────────────────────────────────────┤
│  Durable Notification Outbox (PostgreSQL)    │
│  - Transactional event enqueueing             │
│  - In-app notification delivery (immediate)   │
│  - Outbox dispatcher state machine           │
└──────────────────────────────────────────────┘
                       │ (Intermittent WAN / Internet)
                       ▼
             [External World / Cloud]
```

### Key Distinction: Browser-to-Server vs. Server-to-Cloud
1. **Browser-to-Local-Server Offline State**: The resident's or technician's smartphone is disconnected from the building's local Wi-Fi or LAN (e.g., inside an elevator shaft). Actions are held locally in browser IndexedDB with unique UUID idempotency keys until the local building server is reachable.
2. **Local-Server-to-Internet Offline State**: The local building server and its PostgreSQL database are fully operational on the local network (LAN autonomy), but external internet access is offline. Local incident reporting, triage, and in-app notifications operate at 100% normal capability without internet.

---

## 2. Browser-Side Persistent Action Queue (IndexedDB)

The frontend action queue is implemented in `frontend/src/services/offlineQueue.js` using browser-native **IndexedDB** (`liftcare_offline_db`).

### Schema & Record Structure
- **Database Name**: `liftcare_offline_db` (Version 1)
- **Object Store**: `actions` (KeyPath: `id`)
- **Indexes**: `by_user`, `by_status`, `by_created`, `by_idempotency`

```javascript
{
  id: "act-1730372000000-abc12",
  idempotencyKey: "e4f8d29b-76c1-4b19-b7b5-0c7f2da51921", // Client UUID
  userEmail: "carlos@liftcare.local",                     // Scoped account
  actionType: "ADD_NOTE",                                 // Operation type
  endpoint: "/api/incidents/4/notes",                     // Backend endpoint
  method: "POST",                                         // HTTP method
  payload: { notes: "Motor vibration noted", noteType: "WORK_LOG" },
  description: "Work Note for Incident #4 (WORK_LOG)",
  status: "PENDING",                                      // State machine
  retryCount: 0,
  maxRetries: 5,
  createdAt: "2026-10-31T05:40:00.000Z",
  updatedAt: "2026-10-31T05:40:00.000Z",
  lastError: null,
  serverResult: null
}
```

### Action State Machine
1. `PENDING`: Enqueued in browser storage, awaiting synchronization.
2. `SYNCING`: Currently dispatching HTTP request to local server with `Idempotency-Key` header.
3. `SYNCED`: Successfully processed and acknowledged by local server (HTTP 2xx). Server response payload saved in `serverResult`.
4. `FAILED`: Terminal error (e.g., HTTP 400 validation error or max retries exceeded). Retries halted to prevent server overload.
5. `NEEDS_REVIEW`: Authentication expired (HTTP 401/403). Sync engine pauses to prompt user re-login rather than dropping data.

### User Account Scoping & Security Safeguards
- Each action is tagged with `userEmail`.
- Actions are isolated by authenticated account: switching users or logging out prevents another user from inspecting or replaying actions enqueued by a previous session.
- Client-generated tokens are refreshed upon retry: if the user's session expired, the sync engine pauses, flags `NEEDS_REVIEW`, and waits for valid authentication before replaying.

### Bounded Exponential Backoff Retries
When synchronizing:
- Network errors or 5xx server errors increment `retryCount` up to `maxRetries` (5).
- Retries follow exponential backoff: $T_{\text{wait}} = \min(60000, 1000 \times 2^{\text{retryCount}})\text{ ms}$.
- If `maxRetries` is reached, status transitions to `FAILED` with explicit error telemetry.

---

## 3. Server-Side Idempotency and Deduplication Engine

To guarantee that network timeouts, client retries, or replayed offline actions never generate duplicate incidents or repeated audit entries, the backend implements server-side idempotency in `IdempotencyService.java`.

### Database Schema (`idempotency_records`)
Added via Flyway migration `V6__create_idempotency_and_notification_outbox.sql`:

```sql
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
```

### Request Verification & Hashing Pipeline
1. Client sends HTTP request with `Idempotency-Key` header (e.g. `Idempotency-Key: e4f8d29b-76c1-4b19...`).
2. Server computes **SHA-256 hash** of the normalized JSON request body.
3. Server looks up `(user_id, idempotency_key)`:
   - **Case 1: Cache Miss**: Record does not exist. The request executes normally. On HTTP 2xx completion, the server persists the idempotency key, request hash, response status code, and JSON response body. Server attaches `X-Idempotency-Cache: MISS`.
   - **Case 2: Cache Hit (Safe Deduplication)**: Record exists and request hash matches. The server immediately returns the cached status and response body without re-executing business logic or persisting duplicate rows. Server attaches `X-Idempotency-Cache: HIT`.
   - **Case 3: Key Reuse Mismatch (Security Protection)**: Record exists but request hash differs (client attempted to reuse a previous key with different parameters). Server rejects with `400 Bad Request`: `"Idempotency key has already been used with a different request payload."`

### Protected Endpoints
- `POST /api/incidents`: Prevents duplicate incident ticket creation.
- `POST /api/incidents/{id}/notes`: Prevents duplicate work logs and audit history entries.

---

## 4. Durable Server-Side Notification Outbox

The local server tracks all system events via a persistent PostgreSQL outbox table (`notification_outbox`) implemented in `NotificationOutboxService.java`.

### Database Schema (`notification_outbox`)
```sql
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
    CONSTRAINT fk_outbox_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    CONSTRAINT fk_outbox_user FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### Outbox Delivery States
- `PENDING`: Outbox item enqueued, awaiting dispatch.
- `PROCESSING`: Currently picked up by local dispatcher.
- `SENT`: Delivered to recipient (for `IN_APP` notifications, delivery is immediate upon local database transaction commit).
- `RETRY_WAIT`: Temporary delivery failure; scheduled for retry.
- `FAILED`: Terminal failure after exceeding `max_attempts` (dead-letter status).

### Notification Types & Events
- `IN_APP`: In-app notification available via `GET /api/notifications/my` for residents and staff.
- `DISPATCH_NOTICE`: Targeted dispatch notice delivered to assigned technician when an incident is assigned.
- `SAFETY_ALERT`: Urgent safety notification enqueued upon passenger entrapment (`CRITICAL_ENTRAPMENT`) or emergency shutdowns.
- `STATUS_UPDATE`: Lifecycle progression updates delivered to reporting residents and building staff.

### Outbox Deduplication Keys
To prevent duplicate notifications from being enqueued by repeated incident status updates or concurrent actions, each outbox record utilizes a deterministic `deduplication_key`:
- Incident creation: `INC-CREATE-{incidentId}-{userId}`
- Entrapment safety alert: `INC-ENTRAPMENT-{incidentId}-{userId}`
- Technician assignment: `INC-ASSIGN-{incidentId}-{technicianId}`
- Status transition: `INC-STATUS-{incidentId}-{status}`

> [!IMPORTANT]
> **No Fake External Delivery**: LiftCare models `DISPATCH_NOTICE` and `SAFETY_ALERT` in its durable outbox queue with realistic delivery states. The system **never** falsely claims that real external SMS or email gateways were contacted unless real external telecom or SMTP credentials are configured. In-app notifications are stored and acknowledged directly within the local database.

---

## 5. Notification & Outbox REST APIs

| Endpoint | Method | Role | Description |
|---|---|---|---|
| `/api/notifications/my` | `GET` | Authenticated | Retrieve in-app notifications for authenticated user |
| `/api/notifications/{id}/read` | `PATCH` | Authenticated | Mark an in-app notification as read |
| `/api/notifications/outbox` | `GET` | Staff only | Summary metrics (total, pending, sent, failed) |
| `/api/notifications/outbox/items` | `GET` | Staff only | Paginated inspection of durable outbox entries |

---

## 6. Frontend Queue & Telemetry Experience

1. **Persistent Offline Queue Page (`/offline-queue`)**:
   - Filterable view of queued actions (`All`, `Pending`, `Synced`, `Failed`).
   - Action metadata: Type, Endpoint, Account Scope, Idempotency Key, Creation Date, Retry Count.
   - Operations: "Sync All Pending", "Sync Single Action", "Delete Action", "Clear Synced".
   - In-App Notifications Tab: Display and read-acknowledgment of user notifications.
   - Server Outbox Telemetry Tab (Staff): Real-time queue health counts and event log.
2. **Top Navigation (`Navbar.jsx`)**:
   - Live badge displaying pending offline action count (`📦 Queue (N)`).
   - Dynamically polls browser IndexedDB to reflect offline queue changes.
3. **Connectivity Status Card (`StatusIndicator.jsx`)**:
   - Reflects offline queue availability when local backend is unreachable.
4. **Form Integration (`ReportIncidentPage.jsx` & `IncidentDetailPage.jsx`)**:
   - When network connectivity is lost, forms offer direct fallback to the local browser queue.
   - Input values are preserved with zero data loss.

---

## 7. Verification & Automated Test Results

The Module 9 implementation was verified through extensive automated testing across both frontend and backend layers:

### Backend Integration Tests (`NotificationAndIdempotencyTest.java`)
- `testCreateIncidentWithIdempotencyKey_RepeatedRequestReturnsCachedResponse`: Verifies repeated POST with same key returns cached response with `X-Idempotency-Cache: HIT` without duplicate entity creation.
- `testCreateIncidentWithIdempotencyKey_ModifiedPayloadFailsWithBadRequest`: Verifies that key reuse with a modified payload produces HTTP 400 Bad Request.
- `testAddIncidentNoteWithIdempotencyKey_PreventsDuplicateNotes`: Verifies that repeated note submissions return cached audit notes without duplicate history entries.
- `testNotificationOutboxCreated_OnEntrapmentIncident`: Verifies that passenger entrapments trigger `SAFETY_ALERT` records in the durable outbox.
- `testNotificationOutboxCreated_OnTechnicianAssignment`: Verifies technician dispatch notices are recorded in the outbox.
- `testMyNotificationsAndMarkAsRead`: Verifies `GET /api/notifications/my` and `PATCH /api/notifications/{id}/read`.
- `testOutboxInspectionPermissions`: Verifies role-based access control (residents forbidden, staff permitted).

### Test Suite Execution
- **Backend**: **85 automated tests passed** (0 failures, 0 errors).
- **Frontend**: Production build verified via `npm run build` (0 bundle errors).
