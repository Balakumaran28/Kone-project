# LiftCare — Incident History and Audit Trail

## 1. Overview and Design Principles

The **LiftCare Incident History and Audit Trail** module provides a consistent, immutable, and chronologically ordered audit log for all elevator incidents and triage operations on the local building appliance.

```
+--------------------------------------------------------------------------------------------------+
|                                    INCIDENT AUDIT LOG TOPOLOGY                                   |
|                                                                                                  |
|   Authenticated Actor (Resident, Security, Technician, Manager, Admin)                          |
|                                    │                                                             |
|                                    ▼                                                             |
|   [ Spring Security Context ] ───► Derived Actor ID, Full Name, Email, Role                      |
|                                    │                                                             |
|                                    ▼                                                             |
|   [ @Transactional Service ]  ───► Business Mutation + IncidentHistory Creation                  |
|                                    │ (Atomic commit: all succeed or all rollback)                |
|                                    ▼                                                             |
|   [ PostgreSQL Database ]     ───► INSERT INTO incident_history                                  |
|                                    - Immutable row (no UPDATE/DELETE endpoints)                  |
|                                    - Server-side UTC Instant.now()                               |
|                                    - ActorType: USER or SYSTEM                                   |
|                                    - previous_value, new_value, remarks                          |
+--------------------------------------------------------------------------------------------------+
```

### Core Design Tenets
1. **Server-Derived Identity**: The acting user is strictly derived from the Spring Security JWT session context. Request bodies cannot spoof the actor identity, email, or role.
2. **Server-Generated Timestamps**: Audit timestamps are created by the application server (`Instant.now()` via JPA `@PrePersist`) in UTC, preventing clients from manipulating event timing.
3. **Transactional Atomicity**: Business state changes and their audit records are saved within the same database transaction. If an operation fails, the transaction rolls back completely and leaves no misleading audit record.
4. **Application-Level Immutability**: No API endpoints exist to edit (`PUT`, `PATCH`) or delete (`DELETE`) audit entries. Audit records are read-only to all users.
5. **Forward-Compatibility for Automation**: The data model distinguishes between human actions (`ActorType.USER`) and automated background processes (`ActorType.SYSTEM`), preparing for upcoming SLA monitoring and escalation modules.

---

## 2. Audit Data Model

The audit trail is persisted in the `incident_history` table:

| Column Name | SQL Type | Nullable | Description |
|---|---|---|---|
| `id` | `BIGSERIAL` | No | Primary key, monotonically increasing |
| `incident_id` | `BIGINT` | No | Foreign key referencing `incidents(id)` |
| `action` | `VARCHAR(80)` | No | Machine-readable action code (e.g., `INCIDENT_REPORTED`, `ASSIGNED`, `RESOLVED`) |
| `previous_value`| `VARCHAR(1000)`| Yes | State or assignee before the event |
| `new_value` | `VARCHAR(1000)`| Yes | State or assignee after the event |
| `changed_by_id` | `BIGINT` | Yes | Foreign key referencing `users(id)`; null for system events |
| `actor_type` | `VARCHAR(20)` | No | Enum: `USER` (default) or `SYSTEM` |
| `changed_at` | `TIMESTAMP WITH TIME ZONE` | No | Server-generated timestamp in UTC |
| `remarks` | `VARCHAR(1000)`| Yes | Work notes, triage justification, or diagnostic details |

### Performance Indexes
- `idx_history_incident_date` on `(incident_id, changed_at)`: Optimizes chronological sorting.
- `idx_history_incident_action` on `(incident_id, action)`: Accelerates filtering by event type.
- `idx_history_incident_actor` on `(incident_id, changed_by_id)`: Enables rapid actor auditing.

---

## 3. Recorded Event Types

The system records 10 distinct event types across the incident lifecycle:

| Action Code | Description | Previous Value | New Value | Typical Actor |
|---|---|---|---|---|
| `INCIDENT_REPORTED` | Initial incident submission | `null` | Initial status (`REPORTED`) | Resident or Staff (`USER`) |
| `ACKNOWLEDGED` | Staff verification and intake | Previous status (`REPORTED`) | `ACKNOWLEDGED` | Security / Manager (`USER`) |
| `ASSIGNED` | Initial technician dispatch | `UNASSIGNED` | Technician Name & Email | Building Manager (`USER`) |
| `TECHNICIAN_REASSIGNED`| Reassignment to another tech | Previous Tech Name & Email | New Tech Name & Email | Building Manager (`USER`) |
| `STATUS_CHANGED` | Operational progress update | Previous status (`ASSIGNED`) | New status (`IN_PROGRESS`)| Technician / Staff (`USER`) |
| `RESOLVED` | Physical work completion | Previous status (`IN_PROGRESS`)| `RESOLVED` | Technician / Manager (`USER`) |
| `CLOSED` | Administrative closure | Previous status (`RESOLVED`) | `CLOSED` | Building Manager (`USER`) |
| `ELEVATOR_OUT_OF_SERVICE`| Automatic safety shutdown | Previous elevator status | `OUT_OF_SERVICE` | System / Reporter (`USER`) |
| `ELEVATOR_STATUS_CHANGED`| Elevator status transition | Previous elevator status | New elevator status | Technician / Manager (`USER`) |
| `WORK_NOTE_ADDED` | Progress or diagnostic log | `null` | Note content | Technician / Staff (`USER`) |
| `RESOLUTION_NOTES_UPDATED`| Resolution details update | Previous note | Updated resolution note | Technician / Manager (`USER`) |
| `ADMINISTRATIVE_ACTION`| Manager or Admin note/override | `null` | Administrative note | Administrator / Manager (`USER`)|

---

## 4. Representation of Actors and Timestamps

### Actors
- **Human Users (`ActorType.USER`)**:
  - `changedBy` points to the `User` entity who performed the action.
  - The API response surfaces `changedById`, `changedByName`, `changedByEmail`, and `changedByRole`.
  - If a user account is later deactivated, the historical foreign key remains valid.
- **System Processes (`ActorType.SYSTEM`)**:
  - `changedBy` is `null`.
  - `actorType` is explicitly set to `SYSTEM`.
  - The response automatically presents `changedByName: "System Automation"` and `changedByRole: "SYSTEM"`. No fake database user is created.

### Timestamps
- The `@PrePersist` hook on `IncidentHistory` guarantees that `changedAt` is populated with `Instant.now()`.
- The column is declared with `updatable = false` at the JPA layer and populated with `DEFAULT CURRENT_TIMESTAMP` at the SQL level.

---

## 5. Transaction Consistency and Rollback Safety

All incident mutation operations in `IncidentService` are marked with `@Transactional`:
- `createIncident(...)`
- `acknowledgeIncident(...)`
- `assignIncident(...)`
- `updateIncidentStatus(...)`
- `addIncidentNote(...)`

### Rollback Guarantee
If an error occurs at any point during request processing (e.g., validation failure, access violation, database constraint check, or transient connectivity issue), Spring's transaction manager rolls back the entire transaction. An audit log entry is **never** committed for an action that did not successfully take effect.

---

## 6. History Backend APIs and Authorization

### Dedicated Incident History Endpoint

`GET /api/incidents/{id}/history`

**Parameters:**
- `page` (optional integer, default `0`): Page index.
- `size` (optional integer, default `50`, max `100`): Page size.
- `sort` (optional string, `ASC` or `DESC`, default `ASC`): Chronological order.
- `action` (optional string): Filter by action code (e.g., `RESOLVED`, `ASSIGNED`).
- `actorType` (optional string): Filter by actor type (`USER` or `SYSTEM`).
- `actorId` (optional integer): Filter by user ID.

**Response Schema (`IncidentHistoryPageResponse`):**
```json
{
  "content": [
    {
      "id": 101,
      "incidentId": 42,
      "action": "ASSIGNED",
      "previousValue": "UNASSIGNED",
      "newValue": "Carlos Tech (carlos@liftcare.local)",
      "changedById": 5,
      "changedByName": "Frank Manager",
      "changedByEmail": "frank@liftcare.local",
      "changedByRole": "BUILDING_MANAGER",
      "actorType": "USER",
      "changedAt": "2026-10-31T10:45:00Z",
      "remarks": "Dispatched for urgent cable tension check"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

### Append Audit Note Endpoint

`POST /api/incidents/{id}/notes`

**Request Schema (`IncidentNoteRequest`):**
```json
{
  "notes": "Verified floor leveling on floor 4: tolerance within 3mm.",
  "noteType": "WORK_LOG"
}
```

### Authorization Rules
- **Residents (`RESIDENT`)**:
  - May view history **only** for incidents they reported (`reportedBy == currentUser`).
  - May append notes to their own open incidents.
  - Attempting to access history for other residents' incidents returns **HTTP 403 Forbidden**.
- **Operational Staff (`SECURITY_STAFF`, `TECHNICIAN`, `BUILDING_MANAGER`, `COMPANY_SUPERVISOR`, `ADMINISTRATOR`)**:
  - Authorized to inspect incident history on the local node.
  - Technicians and managers can append work notes and resolution notes.
  - Administrators and managers can record administrative action notes.

---

## 7. Frontend Incident Timeline

The incident detail view ([`IncidentDetailPage.jsx`](file:///d:/KONE/liftcare/frontend/src/pages/IncidentDetailPage.jsx)) presents a rich, human-readable timeline:

### Key Timeline Features
1. **Human-Friendly Event Cards**: Replaces raw enums with clear titles (e.g. "Staff Acknowledged", "Technician Assigned", "Elevator Safety Shutdown").
2. **Actor and Role Badges**: Displays actor names with stylized role chips (`BUILDING_MANAGER`, `TECHNICIAN`) or `🤖 System Automation` badges.
3. **State Transition Visualizations**: Shows previous and new values with directional indicators (`Previous: REPORTED → New: ACKNOWLEDGED`).
4. **Chronological Sorting Toggle**: Toggle between **Oldest First (Ascending)** for reconstructing incident progression and **Newest First (Descending)** for immediate access to recent actions.
5. **Multi-Dimensional Event Filtering**:
   - `All Event Types`
   - `Status Transitions`
   - `Technician Assignments`
   - `Elevator Status`
   - `Work & Audit Notes`
6. **Append Note Modal**: Allows authorized staff and reporting residents to log observations and progress notes directly from the timeline.
7. **Offline Stale Data Alerting**: When the local server is unreachable, the timeline displays an amber notice (`Offline Mode: Showing cached history`), preventing users from mistaking cached records for freshly retrieved data.

---

## 8. Historical Record Preservation and Immutability

- **Database Foreign Keys**:
  - `fk_history_incident`: Uses `ON DELETE RESTRICT`, preventing accidental cascade deletion of incidents that have recorded history.
  - `fk_history_user`: Uses `ON DELETE RESTRICT` (or `SET NULL`), preserving the audit trail even if a user account is removed.
- **No Modification Endpoints**:
  - `PUT /api/incidents/{id}/history` returns **405 Method Not Allowed**.
  - `DELETE /api/incidents/{id}/history` returns **405 Method Not Allowed**.
- **Spring Data Repository**: Does not expose batch delete or update operations to controller tiers.

---

## 9. Application-Level Audit Protection vs. Direct Database Modification

> [!IMPORTANT]
> The audit trail guarantees **application-level integrity**, preventing standard users, staff, and compromised API clients from editing or deleting past events.
> 
> However, the system does **not** claim to be tamper-proof against direct PostgreSQL database administrators (`postgres` superuser) or physical server host compromises. Direct database tampering protection requires hardware write-once-read-many (WORM) storage or cryptographic hash-chaining, which are outside the scope of this building appliance.

---

## 10. How Future Automated System Events Will Be Recorded

When Module 9 (SLA Monitoring & Automatic Escalation Engine) is implemented:
1. Scheduled background tasks (`@Scheduled`) will evaluate open incidents against SLA breach countdowns.
2. When an escalation tier triggers (e.g. Tier-1 notice, Tier-2 manager escalation, Tier-3 supervisor alert):
   - An `IncidentHistory` record will be inserted with:
     - `action: "SLA_ESCALATION_TIER_1"` (or `TIER_2`, `TIER_3`)
     - `actorType: ActorType.SYSTEM`
     - `changedBy: null`
     - `remarks: "Automated escalation triggered: Incident unacknowledged for > 30 minutes"`
3. The frontend timeline will automatically render this with the `🤖 System Automation` badge and escalation callout, maintaining seamless backward compatibility.

---

## 11. Known Limitations
1. **Local Node Scope**: History records are stored in the building's local PostgreSQL database. Multi-building cross-replication to a central cloud audit ledger is scheduled for Module 11.
2. **Granular User-to-Building Scoping**: All operational staff currently share node-level access. Granular assignment of security guards to specific building wings is documented for future RBAC refinement.
