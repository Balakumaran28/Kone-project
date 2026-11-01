# LiftCare — Building Control-Room Dashboard Specification

## 1. Overview & Architecture

The **Building Control-Room Dashboard** (`/control-room`) provides a centralized, local-first operational command interface for facility staff, security personnel, maintenance managers, and technicians.

It is designed to operate on the local on-premise building server without internet dependencies, offering:
- Real-time aggregated incident and elevator fleet metrics.
- A prominent emergency banner for passenger entrapments and critical incidents.
- A triage queue with advanced filtering, sorting, and direct links to incident workflows.
- An elevator fleet safety overview distinguishing between software-recorded status and physical condition.
- Offline and local-network connectivity indicators with stale telemetry protection.
- Background polling (30s) that automatically suspends when the tab is hidden.

---

## 2. Routes & Navigation

| Route | Component | Access Control | Purpose |
| :--- | :--- | :--- | :--- |
| `/control-room` | [`ControlRoomPage.jsx`](file:///d:/KONE/liftcare/frontend/src/pages/ControlRoomPage.jsx) | Staff Roles Only | Live operational oversight, emergency alert monitor, fleet status, and incident triage queue |
| `/incidents/:id` | [`IncidentDetailPage.jsx`](file:///d:/KONE/liftcare/frontend/src/pages/IncidentDetailPage.jsx) | Role-Scoped | Full ticket details, chronological audit history, and staff triage actions |

---

## 3. API Endpoints

### Dedicated Telemetry Endpoint
- **`GET /api/dashboard/summary`**
  - **Access**: `@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'TECHNICIAN', 'COMPANY_SUPERVISOR')")`
  - **Query Params**:
    - `buildingId` *(optional, Long)*: Restricts counts and emergency tickets to a specific building complex on this local node.
  - **Response Payload (`DashboardSummaryResponse`)**:
    ```json
    {
      "totalActiveIncidents": 4,
      "criticalIncidents": 1,
      "highSeverityIncidents": 1,
      "awaitingAcknowledgementIncidents": 1,
      "assignedIncidents": 1,
      "inProgressIncidents": 1,
      "activeEntrapments": 1,
      "elevatorsOutOfService": 1,
      "elevatorsUnderMaintenance": 1,
      "elevatorsActive": 1,
      "totalElevators": 3,
      "totalBuildings": 2,
      "timestamp": "2026-10-31T04:42:00Z",
      "localNodeId": "building-local-node-01",
      "emergencyIncidents": [
        {
          "id": 1,
          "ticketNumber": "INC-20261031-C1DBDA",
          "buildingId": 1,
          "buildingName": "Tower Alpha",
          "elevatorId": 1,
          "elevatorCode": "LIFT-01",
          "severity": "CRITICAL",
          "status": "REPORTED",
          "entrapmentReported": true,
          "reportedAt": "2026-10-31T04:30:00Z"
        }
      ]
    }
    ```

### Incident & Fleet Endpoints Utilized
| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/incidents` | Staff / Admin | Lists incidents with filters (`status`, `severity`, `buildingId`, `elevatorId`, `entrapmentOnly`, `assignedToId`) |
| `GET` | `/api/elevators` | Authenticated | Lists elevators across buildings with optional `buildingId` and `status` filters |
| `PATCH` | `/api/elevators/{id}/status` | Admin / Mgr / Tech | Updates recorded operational status (`ACTIVE`, `MAINTENANCE`, `OUT_OF_SERVICE`) |
| `GET` | `/api/buildings` | Authenticated | Fetches active building complexes for selector dropdown |
| `GET` | `/api/users/technicians` | Staff / Admin | Fetches active maintenance technicians for assignment filter |
| `GET` | `/api/health` | Public | Telemetry ping to compute latency and local node health |

---

## 4. Definitions of Dashboard Counts

All telemetry counts are computed directly via database queries against real records without loading unnecessary domain trees into memory:

| Metric | Database Query / Calculation | Purpose |
| :--- | :--- | :--- |
| **Total Active Incidents** | `status IN ('REPORTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS')` | Open lifecycle tickets requiring resolution |
| **Critical Incidents** | `status IN ('REPORTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS') AND severity = 'CRITICAL'` | Extreme defects or safety risks |
| **High-Severity Incidents** | `status IN ('REPORTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS') AND severity = 'HIGH'` | Urgent mechanical or door malfunctions |
| **Awaiting Acknowledgement** | `status = 'REPORTED'` | Unacknowledged tickets awaiting first staff inspection |
| **Assigned to Techs** | `status = 'ASSIGNED'` | Tickets dispatched to a technician but work not yet initiated |
| **In Progress** | `status = 'IN_PROGRESS'` | Active repairs or inspections underway |
| **Active Entrapments** | `status IN ('REPORTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS') AND entrapment_reported = true` | Immediate passenger safety emergencies |
| **Lifts Out of Service** | `operational_status = 'OUT_OF_SERVICE'` | Units shutdown by safety interlock or staff override |
| **Lifts in Maintenance** | `operational_status = 'MAINTENANCE'` | Units undergoing scheduled technician maintenance |
| **Lifts Active** | `operational_status = 'ACTIVE'` | Units operational in service |
| **Total Elevators** | `count()` from `elevators` | Total fleet size registered on this node |
| **Total Buildings** | `countByActiveTrue()` from `buildings` | Active properties serviced by this local node |

---

## 5. Incident Queue Filtering & Sorting

### Available Filters
1. **Building Scope**: Filter entire queue and telemetry by specific building or all buildings on the local node.
2. **Severity**: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`.
3. **Status**: `REPORTED`, `ACKNOWLEDGED`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`.
4. **Entrapment Only**: Toggle to isolate passenger entrapment tickets.
5. **Assigned Technician**: Filter by technician account.
6. **Free-Text Search**: Live substring matching on ticket number, elevator code, building name, category, or description.

### Sorting Priority Modes
1. **🚨 Emergency & Critical First (`EMERGENCY_FIRST`)**:
   - Entrapment incidents (`entrapmentReported == true`) placed at top.
   - Ordered by severity descending (`CRITICAL` > `HIGH` > `MEDIUM` > `LOW`).
   - Secondary sort by `reportedAt` ascending (longest-waiting emergency first).
2. **⏳ Oldest Unacknowledged First (`OLDEST_UNACKNOWLEDGED`)**:
   - Unacknowledged tickets (`status == 'REPORTED'`) placed first.
   - Ordered by `reportedAt` ascending (longest unreviewed ticket first).
3. **⏱️ Most Recently Reported (`NEWEST_FIRST`)**:
   - Ordered by `reportedAt` descending.
4. **📅 Oldest Reported (`OLDEST_FIRST`)**:
   - Ordered by `reportedAt` ascending.

---

## 6. Elevator Fleet & Safety Visibility

### Operational Status Enforcement
The dashboard exposes an elevator status matrix tracking:
- `ACTIVE` (Green): Operational in service.
- `MAINTENANCE` (Amber): Scheduled technician servicing.
- `OUT_OF_SERVICE` (Red): Safety shutdown or mechanical defect.

### Physical vs. Software Status Notice
The dashboard presents a mandatory safety notice:
> **⚠️ Operational Safety Notice:** Recorded software statuses represent administrative records and incident triage interlocks. Software status does NOT verify physical or mechanical elevator conditions. Certified on-site physical inspection is required before restoring any unit to service.

Staff holding `ADMINISTRATOR`, `BUILDING_MANAGER`, or `TECHNICIAN` roles can update the recorded elevator status through a confirmation dialog utilizing `PATCH /api/elevators/{id}/status`.

---

## 7. Role-Based Access Control (RBAC)

The control room enforces strict server-side and client-side authorization:

| Role | Access to `/control-room` | Access to `GET /api/dashboard/summary` | Capabilities |
| :--- | :---: | :---: | :--- |
| **`RESIDENT`** | **BLOCKED (403)** | **FORBIDDEN (403)** | No access. Residents use `/incidents` for their own reported tickets. |
| **`SECURITY_STAFF`** | Granted | Granted (200 OK) | Operational monitoring, CCTV verification, acknowledgement of incoming reports. |
| **`BUILDING_MANAGER`** | Granted | Granted (200 OK) | Facility oversight, technician assignment, elevator status management. |
| **`TECHNICIAN`** | Granted | Granted (200 OK) | Work queue inspection, status progression (`IN_PROGRESS`, `RESOLVED`), status override. |
| **`COMPANY_SUPERVISOR`**| Granted | Granted (200 OK) | Operational cross-building oversight and performance tracking. |
| **`ADMINISTRATOR`** | Granted | Granted (200 OK) | Full administrative control, system telemetry, and manual overrides. |

---

## 8. Offline-First Resilience & Refresh Behavior

### Local LAN Autonomy
- LiftCare control-room operations execute completely over the local building network.
- No external cloud services, remote telemetry endpoints, or internet access are required.

### Connectivity Detection
- **Local Server Health**: Measured via `checkBackendHealth` against `/api/health`. Reports live round-trip latency in milliseconds.
- **Internet Indicator**: Checks browser network layer (`navigator.onLine`) to differentiate between loss of internet vs. loss of local LAN connection.
- **Stale Data Warning**: If the local server becomes unreachable, a prominent amber/red alert is displayed, signaling that displayed telemetry represents cached values.

### Background Refresh
- Refreshes every **30 seconds** when active.
- Integrated with the **Page Visibility API** (`document.addEventListener('visibilitychange')`):
  - Suspends timer when the browser tab is minimized or hidden.
  - Automatically triggers an immediate refresh and resumes timer when the user returns to the tab.
- Can be paused/resumed via the **Auto-refresh** toggle in the header.
- Includes a manual **Refresh Now** button.

---

## 9. Limitations & Next Steps

1. **Local Node Scope**:
   - In the current single-node deployment model, user accounts belong to the local server. While telemetry supports building-level filtering via `?buildingId=...`, granular user-to-building authorization assignments are not yet enforced.
2. **Next Module (Module 7: SLA Monitoring & Automatic Escalation)**:
   - SLA countdown timers based on severity (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`).
   - Automated background escalation worker (`@EnableScheduling`) for tier-1/2/3 breaches.
   - Escalation history audit events recorded in `incident_history`.
