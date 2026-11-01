# LiftCare — Local Server and Offline Operation

## 1. Architecture of the Local Building Server

LiftCare is architected as an **offline-first, building-local appliance**. Rather than relying on cloud microservices or internet-dependent APIs, every core workflow (incident reporting, entrapment alerting, status triage, elevator fleet management, and real-time dashboard telemetry) runs on a physical server or local workstation situated directly within the building complex.

```
+-----------------------------------------------------------------------------------+
|                            BUILDING LOCAL NETWORK (LAN)                           |
|                                                                                   |
|  [ Resident Phone ]      [ Security Desk Tablet ]     [ Manager Workstation ]     |
|   (Browser / Wi-Fi)       (Browser / Wi-Fi or Eth)     (Browser / Ethernet)       |
|          |                            |                          |                |
|          +----------------------------+--------------------------+                |
|                                       | (HTTP / LAN IP, e.g., 192.168.1.50:80)     |
|                                       v                                           |
|       +-------------------------------------------------------------------+       |
|       |                     LOCAL BUILDING SERVER HOST                     |       |
|       |                                                                   |       |
|       |   +-----------------------------------------------------------+   |       |
|       |   |               DOCKER / NGINX REVERSE PROXY                |   |       |
|       |   | - Serves React SPA static build bundle (assets, JS, CSS)  |   |       |
|       |   | - Routes /api/* to Spring Boot backend:8080               |   |       |
|       |   | - Zero external CDN dependencies (fonts, icons embedded)  |   |       |
|       |   +-----------------------------+-----------------------------+   |       |
|       |                                 | (Internal Network)              |       |
|       |                                 v                                 |       |
|       |   +-----------------------------------------------------------+   |       |
|       |   |                SPRING BOOT APPLICATION (Java 21)          |   |       |
|       |   | - Stateless JWT Authentication & Role-Based Access        |   |       |
|       |   | - Incident Lifecycle, Entrapment & Safety Shutdown Logic  |   |       |
|       |   | - Elevator Registry & Control-Room Dashboard Aggregation  |   |       |
|       |   | - Flyway Schema Migrations at Boot                        |   |       |
|       |   | - Masked Health & Diagnostic Probe (/api/health)          |   |       |
|       |   +-----------------------------+-----------------------------+   |       |
|       |                                 | (JDBC connection on port 5432)  |       |
|       |                                 v                                 |       |
|       |   +-----------------------------------------------------------+   |       |
|       |   |                 POSTGRESQL 16 DATABASE                    |   |       |
|       |   | - Relational schema: Users, Buildings, Elevators,         |   |       |
|       |   |   IssueCategories, Incidents, IncidentHistory             |   |       |
|       |   | - Persisted via Docker named volume (postgres_data)       |   |       |
|       |   +-----------------------------------------------------------+   |       |
|       +-------------------------------------------------------------------+       |
+-----------------------------------------------------------------------------------+
                                        | (Optional external uplink)
                                        X [INTERNET DISCONNECTED / SEVERED]
                                        (Core building operations unaffected)
```

### Key Architectural Tenets
1. **Network Autonomy**: All essential data processing, validation, and authentication happen locally. When internet connectivity is severed, residents, guards, and managers experience uninterrupted service across the building LAN.
2. **Reverse Proxy Topology**: In containerized production, Nginx handles client static asset requests and proxies `/api/*` endpoints to Spring Boot internally (`http://backend:8080/api`). The frontend communicates through relative paths (`/api/...`), completely avoiding cross-origin overhead and hardcoded IP addresses.
3. **Dynamic Dev Fallback**: In local development (`vite dev`), dynamic runtime hostname detection (`resolveApiBaseUrl`) allows external mobile/tablet devices on the LAN to query the backend on `${window.location.hostname}:8080` without manual build-time URL adjustments.
4. **Persistent State Storage**: All database state is isolated in dedicated persistent volumes (`postgres_data`), surviving container restarts and local server reboot cycles.

---

## 2. Required Software and Prerequisites

### Option A: Containerized Deployment (Recommended)
- **Docker Engine**: Version 24.0 or higher.
- **Docker Compose**: Version 2.20 or higher (or Docker Desktop on Windows/macOS).
- Available local ports: `80` (or `8080`), `5432` (database internal or mapped).
- Hardware minimums: 2 CPU cores, 2 GB RAM, 10 GB free disk space.

### Option B: Native Host Deployment
- **Java Development Kit (JDK)**: Java 21 or Java 25 LTS (Eclipse Temurin, OpenJDK).
- **Node.js**: v20 LTS or v24.x and npm v10+.
- **PostgreSQL**: Version 15 or 16 installed locally or reachable on the building LAN.
- **Operating System**: Windows 10/11, Ubuntu 22.04/24.04 LTS, Debian 12, or macOS.

---

## 3. Environment-Variable Configuration

LiftCare uses environment variables across both containerized and native deployments. A complete configuration template is provided in [.env.example](file:///d:/KONE/liftcare/.env.example).

### Core Configuration Parameters

| Variable | Description | Default / Example Value | Deployment Context |
|---|---|---|---|
| `SERVER_PORT` | HTTP port for the Spring Boot backend | `8080` | Backend / Docker |
| `DB_URL` | JDBC connection string to PostgreSQL | `jdbc:postgresql://postgres:5432/liftcare` | Backend / Compose |
| `DB_USERNAME` | PostgreSQL database user | `liftcare_app` | Backend / Postgres |
| `DB_PASSWORD` | PostgreSQL database user password | *Strong randomly generated secret* | Backend / Postgres |
| `POSTGRES_DB` | Name of initial PostgreSQL database | `liftcare` | Postgres Container |
| `POSTGRES_USER` | Initial administrative user | `liftcare_app` | Postgres Container |
| `POSTGRES_PASSWORD` | Password for PostgreSQL user | *Must match `DB_PASSWORD`* | Postgres Container |
| `JWT_SECRET` | HMAC-SHA256 signing secret (>= 256 bits) | *Minimum 64 hex characters or 32 bytes base64* | Backend |
| `JWT_EXPIRATION_MS` | JWT validity lifetime in milliseconds | `86400000` (24 hours) | Backend |
| `FRONTEND_PORT` | External host port for the frontend | `80` | Frontend / Compose |
| `CORS_ALLOWED_ORIGINS` | Permitted origins for API requests | `http://localhost,http://192.168.*:*` | Backend |
| `VITE_API_BASE_URL` | Explicit backend URL override (optional) | *Empty for relative `/api` routing* | Frontend |

> [!CAUTION]
> Never commit production secrets, real database credentials, or JWT signing keys to version control. Always copy `.env.example` to `.env` on the deployment host and supply unique credentials.

---

## 4. How to Start the Application Locally (Native Host)

If running directly on the host machine without Docker:

### Step 1: Ensure PostgreSQL is Running
Create a database named `liftcare`:
```bash
# PostgreSQL CLI
psql -U postgres
CREATE DATABASE liftcare;
CREATE USER liftcare_app WITH ENCRYPTED PASSWORD 'local_secure_password';
GRANT ALL PRIVILEGES ON DATABASE liftcare TO liftcare_app;
```

### Step 2: Configure Environment Variables
Set the required environment variables in your shell (PowerShell example):
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/liftcare"
$env:DB_USERNAME="liftcare_app"
$env:DB_PASSWORD="local_secure_password"
$env:JWT_SECRET="c6b12dfa52b84931a14a8b79e19d53c7a31b46ef028e46958d927c3a81e9f02c"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173,http://localhost:8080,http://192.168.*:*"
```

### Step 3: Start Backend
```bash
cd backend
./mvnw clean spring-boot:run
```
Spring Boot starts on port `8080`, validates database connectivity, and executes Flyway migrations `V1`, `V2`, and `V3`.

### Step 4: Start Frontend
In a separate terminal:
```bash
cd frontend
npm install
npm run dev -- --host
```
The `--host` flag ensures Vite listens on `0.0.0.0:5173`, enabling access from other devices on the LAN.

---

## 5. How to Start the Docker-Based Deployment

The production-ready container stack is defined in [docker-compose.yml](file:///d:/KONE/liftcare/docker-compose.yml).

### Step 1: Initialize Environment File
```bash
cp .env.example .env
# Edit .env with your favorite editor and configure secure DB_PASSWORD and JWT_SECRET
```

### Step 2: Build and Launch All Services
```bash
docker compose up -d --build
```

### Step 3: Verify Container Health
```bash
docker compose ps
```
All three services should transition to healthy:
- `liftcare-postgres`: PostgreSQL 16 Alpine, health check via `pg_isready`.
- `liftcare-backend`: Spring Boot 3 JRE Alpine, health check via `wget -qO- http://localhost:8080/api/health`.
- `liftcare-frontend`: Nginx Alpine serving React SPA, health check via `wget -qO- http://localhost/nginx-health`.

### Step 4: View Real-Time Logs
```bash
docker compose logs -f backend
```

---

## 6. Database Initialization and Flyway Migrations

LiftCare employs **Flyway** for deterministic database schema lifecycle management.

### Migration Sequence
1. `V1__init_schema.sql`:
   - Creates tables: `users`, `buildings`, `elevators`, `issue_categories`, `incidents`, `incident_history`.
   - Creates foreign keys, enum checks, and performance indexes (`idx_incidents_status`, `idx_elevators_building`).
2. `V2__seed_issue_categories.sql`:
   - Seeds baseline issue categories (`MECHANICAL`, `ELECTRICAL`, `DOOR_MALFUNCTION`, `ENTRAPMENT`, `SENSOR_FAILURE`, `OTHER`).
3. `V3__add_password_hash_to_users.sql`:
   - Adds `password_hash VARCHAR(255)` column to `users` and cleans up deprecated fields.

### Migration Execution Guarantee
- In both native and Dockerized environments, Flyway runs automatically on Spring Boot application startup before HTTP listeners are bound.
- In Docker Compose, the backend container includes a `depends_on` condition requiring `liftcare-postgres` to report healthy before backend initialization begins.
- Flyway maintains an immutable schema history table `flyway_schema_history` inside PostgreSQL. Migrations are strictly atomic and idempotent.

---

## 7. How to Access LiftCare from the Server Itself

From a browser running directly on the host building server:
- **Docker Production Deployment**: Navigate to `http://localhost` (or `http://localhost:80`).
- **Native Development Deployment**:
  - Frontend UI: `http://localhost:5173`
  - Backend API Health: `http://localhost:8080/api/health`

---

## 8. How to Access LiftCare from Another Device on the Same LAN

Residents, building security personnel, and technicians can access the application from smartphones, tablets, or laptops connected to the building's Wi-Fi or Ethernet switch.

### Step 1: Determine the Server's LAN IP Address
- **Windows**: Run `ipconfig` in Command Prompt or PowerShell. Look for the IPv4 Address under the active Wi-Fi or Ethernet adapter (e.g., `192.168.1.50`).
- **Linux**: Run `ip addr show` or `hostname -I` (e.g., `192.168.0.100`).
- **macOS**: Run `ipconfig getifaddr en0`.

### Step 2: Open Inbound Firewall Ports on the Server Host
- **Windows Firewall**:
  ```powershell
  # For Docker production deployment (port 80)
  New-NetFirewallRule -DisplayName "LiftCare HTTP" -Direction Inbound -LocalPort 80 -Protocol TCP -Action Allow

  # For Native development deployment (ports 5173 and 8080)
  New-NetFirewallRule -DisplayName "LiftCare Vite Dev" -Direction Inbound -LocalPort 5173 -Protocol TCP -Action Allow
  New-NetFirewallRule -DisplayName "LiftCare Backend Dev" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
  ```
- **Linux (ufw)**:
  ```bash
  sudo ufw allow 80/tcp
  sudo ufw allow 8080/tcp
  sudo ufw allow 5173/tcp
  ```

### Step 3: Connect from Client Devices
Open any mobile or desktop web browser on a device connected to the same LAN:
- **Docker Production**: `http://192.168.1.50` (substitute actual server IP).
- **Native Development**: `http://192.168.1.50:5173`.

The client browser loads the UI bundle directly from the server. The dynamic `resolveApiBaseUrl()` utility automatically routes API requests to the server's IP rather than the client device's local loopback.

---

## 9. How to Test Operation While Internet Access is Disconnected

To physically verify the offline-first resilience:

1. **Sever WAN Uplink**: Unplug the DSL/Fiber Ethernet WAN cable from the building's Wi-Fi router, or disconnect the server host from external broadband while keeping the local Wi-Fi / Ethernet LAN switch active.
2. **Open LiftCare Client**: On a connected smartphone or laptop, navigate to `http://192.168.1.50`.
3. **Verify Header Badge**: The top status indicator displays **Local LAN Autonomy** with an Amber badge (`Local LAN Autonomy (Offline-First)`).
4. **Test Operational Workflows**:
   - Log in using an existing resident or staff account (or register a new user).
   - Report an elevator issue with entrapment checked.
   - Verify that the ticket is immediately generated (`INC-...`), the elevator is transitioned to `OUT_OF_SERVICE`, and the history event is written to the local database.
   - Access the **Control-Room Dashboard** (`/control-room`) on a security desk tablet; verify the critical entrapment alert banner displays immediately with live triage controls.
   - Triage and acknowledge the ticket; verify state changes persist across browser refreshes.

---

## 10. How to Distinguish Internet, Backend, and Database Outages

LiftCare implements a **5-State System Health Model** (`evaluateSystemConnectivity` in `api.js`) to prevent diagnostic confusion:

```
+---------------------------+-------------------+--------------------+------------------------+
| State                     | UI Badge / Color  | Technical Cause    | User Guidance          |
+---------------------------+-------------------+--------------------+------------------------+
| LOCAL_ONLINE              | Green / "Online"  | Backend OK, DB OK, | All systems operating  |
|                           |                   | Internet OK        | nominally.             |
|                           |                   |                    |                        |
| LOCAL_LAN_AUTONOMY        | Amber / "Autonomy"| Backend OK, DB OK, | Normal building ops    |
|                           |                   | Internet OFFLINE   | active; external cloud |
|                           |                   |                    | sync suspended.        |
|                           |                   |                    |                        |
| BACKEND_UNAVAILABLE       | Red / "Offline"   | Backend down or LAN| Check server process   |
|                           |                   | disconnect         | & building Wi-Fi.      |
|                           |                   |                    |                        |
| DATABASE_UNAVAILABLE      | Red / "Degraded"  | Backend up, DB down| Server cannot persist  |
|                           |                   |                    | data; restart Postgres.|
|                           |                   |                    |                        |
| UNKNOWN                   | Gray / "Checking" | Health probe in-   | Diagnostics in         |
|                           |                   | flight or aborted  | progress.              |
+---------------------------+-------------------+--------------------+------------------------+
```

### Incident Submission Safeguard
If a resident attempts to submit an incident while the backend or database is unreachable:
1. The form submission fails gracefully without crashing the UI.
2. A prominent error alert notifies the user:
   > *"Connection failed: Your incident report was NOT saved to the database. Please verify your connection to the building Wi-Fi network and try again. Your input has been preserved."*
3. The user's form inputs (building, elevator, description, severity, entrapment) remain intact in the input fields, preventing user frustration and data loss.

---

## 11. How to Stop and Restart the System

### Docker Deployment
```bash
# Graceful stop
docker compose stop

# Restart services
docker compose start

# Full shutdown (preserves database data volume)
docker compose down

# Full restart with fresh image rebuild
docker compose down && docker compose up -d --build
```

### Native Deployment
- **Backend**: Press `Ctrl+C` in the backend terminal window. Restart with `./mvnw spring-boot:run`.
- **Frontend**: Press `Ctrl+C` in the frontend terminal window. Restart with `npm run dev -- --host`.

---

## 12. How Persistent Database Storage Works

When using Docker Compose:
- PostgreSQL stores its data inside the container directory `/var/lib/postgresql/data`.
- This container path is bound to a named Docker volume `postgres_data` declared in `docker-compose.yml`:
  ```yaml
  volumes:
    postgres_data:
      driver: local
  ```
- **Durability Guarantee**: Running `docker compose down` removes containers and virtual network interfaces but **never** deletes the `postgres_data` volume. When `docker compose up -d` is executed subsequently, the existing database state, user accounts, building registrations, and incident histories are mounted without data loss.
- **Wiping Data (Only if explicitly intended)**:
  `docker compose down -v` (removes named volumes; use with extreme caution).

---

## 13. How to Back Up and Restore the Local Database

Building management should perform periodic local backups (e.g., daily automated cron job or scheduled task).

### Backing Up the Database

#### Docker Deployment
```bash
# Generate SQL dump directly from the running postgres container
docker compose exec -T postgres pg_dump -U liftcare_app liftcare > backup_$(date +%Y%m%d_%H%M%S).sql
```

#### Native Host Deployment
```bash
pg_dump -U liftcare_app -h localhost -d liftcare -F c -b -v -f "liftcare_backup.dump"
```

### Restoring from Backup

#### Docker Deployment
```bash
# Stop backend to prevent concurrent writes during restore
docker compose stop backend

# Restore the SQL dump into the postgres container
docker compose exec -T postgres psql -U liftcare_app -d liftcare < backup_20261031_120000.sql

# Restart backend
docker compose start backend
```

#### Native Host Deployment
```bash
pg_restore -U liftcare_app -h localhost -d liftcare -v "liftcare_backup.dump"
```

---

## 14. Common Problems and Troubleshooting Steps

### Problem 1: Client device cannot open `http://<SERVER_IP>` over Wi-Fi
- **Check Server IP**: Verify that the server's LAN IP has not changed (routers may reassign IPs via DHCP; consider setting a DHCP reservation or static IP).
- **Check Client Network**: Ensure the mobile device is connected to the building's Wi-Fi network and not cellular mobile data.
- **Check Server Firewall**: Ensure port 80 (or 5173/8080) is permitted in the host operating system's firewall (see Section 8).
- **AP Isolation**: Some consumer Wi-Fi access points enable "Client Isolation" or "AP Isolation", preventing wireless devices from communicating with one another. Disable AP isolation in the router settings.

### Problem 2: Backend starts but displays `DATABASE_UNAVAILABLE`
- **Check PostgreSQL Service**: Run `docker compose ps` or `pg_isready -h localhost -p 5432`.
- **Check Credentials**: Verify `DB_USERNAME` and `DB_PASSWORD` in `.env` match the PostgreSQL credentials.
- **Connection Refused**: Confirm PostgreSQL is listening on all network interfaces (`listen_addresses = '*'`) if connecting across machines natively.

### Problem 3: CORS error when calling API from LAN client
- Verify that `CORS_ALLOWED_ORIGINS` in `.env` includes the building's IP subnet pattern (e.g., `http://192.168.*:*` or `http://10.*:*`). Both `CorsConfig.java` and `SecurityConfig.java` use Spring's `allowedOriginPatterns` to support wildcard subnets.

---

## 15. Security Recommendations for a Building-Local Deployment

1. **LAN Is Not a Trusted Perimeter**: Anyone on the building's guest Wi-Fi could attempt unauthorized access. Keep the LiftCare local server on an isolated management VLAN or password-protected staff/resident Wi-Fi network.
2. **Rotate Default Secrets**: Never deploy with example passwords. Generate a cryptographically secure 256-bit JWT secret (`openssl rand -hex 32`).
3. **Restrict PostgreSQL Port**: In `docker-compose.yml`, the database port `5432` is intentionally confined to the internal `liftcare-network` bridge and not published to external network adapters unless debugging is required.
4. **Credential Masking**: Verify that the `/api/health` probe endpoint masks sensitive database credentials (e.g. `jdbc:postgresql://***:***@postgres:5432/liftcare`).
5. **Enforce Role-Based Access**: Keep high-privilege credentials (`ADMINISTRATOR`, `BUILDING_MANAGER`) restricted to verified building personnel.

---

## 16. Current Limitations and Manual Recovery Procedures

### Current Scope Limitations
1. **No Client-Side Offline Outbox**: If a resident's smartphone completely loses Wi-Fi connection to the building router, reports cannot be submitted. The browser retains the filled form inputs for retry, but does not queue submissions into client-side IndexedDB for background sync.
2. **Single-Node Persistence**: All operational state resides in the local PostgreSQL instance. High availability / multi-master replication is not implemented in this module.
3. **No External SMS / Push Notification**: Escalations and notifications operate within the web interface and database audit trails. External carrier gateway dispatch is reserved for future modules.

### Manual Recovery Procedures
- **Unresponsive Server Host**: If the physical server freezes, reboot the machine and execute `docker compose up -d`. Flyway checks migration checksums, Spring Boot validates database integrity, and the application resumes normal operation without data loss.
- **Database Corruption / Volume Failure**: In the rare event of disk failure, follow the backup restore procedure detailed in Section 13 using the latest scheduled SQL dump.
