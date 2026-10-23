# 24 — Cloud Deployment Runbook

> **Owner:** Trung (BK-024) | **Collaborators:** Hưng (EV-001), Sĩ (EV-002), Tiến (Governance & Architecture)
> **Issues Covered:** BK-001, BK-002, BK-024 | **Decisions & Trace:** NFR-005, EV-001, EV-002, TD-009, TD-010, TD-012
> **Status:** APPROVED PRODUCTION RUNBOOK — FinFast V1 Modular Monolith Deployment

---

## 1. Architectural Context & Operational Constraints

FinFast V1 production deployment is packaged and deployed as a single **Modular Monolith** alongside a dedicated, persistent **PostgreSQL 16** database using isolated production configuration files (`backend/Dockerfile.prod`, `compose.prod.yaml`).

- **Technology Stack:** Java 21 LTS (Eclipse Temurin JRE Alpine), Spring Boot 4.1.1, PostgreSQL 16 Alpine.
- **Topology:** Single deployable container (`backend`) communicating over an internal Docker bridge network with a private database container (`db`). No distributed microservices, no Kafka message broker, and no Redis caching tier are permitted or deployed in V1 (TD-012 compliance).
- **Security & Least Privilege:**
  - Non-root container execution: Application runs under dedicated unprivileged system user `finfast:finfast` (`UID:GID 10001:10001`).
  - Zero hardcoded secrets: All credentials and sensitive runtime configurations must be supplied via environment variables (`.env`).
  - Database access is isolated within the internal container network and never exposed to the public Internet.
- **Observability:**
  - Standardized ECS JSON formatted logs (`application-prod.yml`) written directly to `stdout`.
  - Deployment traceability requires explicit injection of `DEPLOYMENT_VERSION` (e.g., `1.0.0`) and `APP_ENV` (`production`).
  - ANSI console coloring is disabled (`FINFAST_CONSOLE_ANSI=never`).
- **Data Integrity & TD-009 Compliance:**
  - **CRITICAL:** `FINFAST_BOOTSTRAP_ENABLED=false` must be strictly enforced in production compose environments. Automatic startup reseeding or credential overwriting on container boot is prohibited.
  - Initial provisioning (Admin creation, Treasury establishment, and one-time GENESIS supply allocation) is strictly an operator-driven, protected one-time setup action.
  - The PostgreSQL data directory is bound to the named persistent Docker volume `pgdata_prod`.

---

## 2. Pre-Deployment Verification Checklists

Before initiating any cloud or production deployment, the engineering team must satisfy and sign off on both **EV-001** and **EV-002** checklists.

### 2.1 EV-001: Course Rubric & Academic Deliverables Checklist

| ID | Verification Item | Standard / Acceptance Criteria | Status | Sign-off |
|---|---|---|---|---|
| **EV001-1** | Core Banking Scope | Educational simulation notice active; zero real bank rails; VND integral amounts. | MANDATORY | Hưng |
| **EV001-2** | Dual Role Access | Strict RBAC: `CUSTOMER` (transfers, history, balance) and `ADMIN` (allocation, audit inspection, account freeze/unfreeze). No unauthenticated or leaked endpoints. | MANDATORY | Hưng |
| **EV001-3** | Transactional Atomicity | All financial movements use double-entry signed ledger facts in a single database transaction (`NFR-001`). | MANDATORY | Tiến |
| **EV001-4** | Transfer Idempotency | Synchronous transfers require unique `Idempotency-Key`. Replays return original recorded outcomes without double-posting (`AC-005`, `AC-014`). | MANDATORY | Minh |
| **EV001-5** | Failure Demonstrations | Verified test evidence for: (a) debit succeeds/credit fails rollback, (b) client timeout/retry outcome recovery, (c) crash before/after commit invariant preservation, (d) concurrent spend isolation. | MANDATORY | Minh |
| **EV001-6** | Traceability & Audit | Unalterable audit trail in PostgreSQL for all administrative operations. Correlation trace IDs on all requests. | MANDATORY | Tiến |

### 2.2 EV-002: Cloud Budget & Resource Feasibility Checklist

| ID | Verification Item | Standard / Acceptance Criteria | Status | Sign-off |
|---|---|---|---|---|
| **EV002-1** | Compute & Sizing | Minimal single-host deployment: 1–2 vCPUs, 2–4 GB RAM, sufficient for monolithic JVM + PostgreSQL 16. | VERIFIED | Sĩ |
| **EV002-2** | Budget Ceiling | Total hosting and storage costs remain strictly within the allocated student/team cloud budget ($0 free tier or low-cost credits). | VERIFIED | Sĩ |
| **EV002-3** | Storage Persistence | Persistent storage backed by persistent volume (`pgdata_prod`) surviving container crashes, host restarts, and container replacements. | MANDATORY | Trung |
| **EV002-4** | Network Isolation | PostgreSQL port 5432 is restricted to internal container networking; only port 8080 (or reverse proxy HTTPS port 443) is published externally. | MANDATORY | Trung |
| **EV002-5** | Ingress & TLS | TLS termination verified at reverse proxy or load balancer boundary; secure cookie flags (`HttpOnly`, `Secure`, `SameSite=Lax`). | PENDING | Sĩ / Trung |
| **EV002-6** | Secret Management | Production secrets (passwords, salts, keys) are not committed to Git or baked into container images; injected via `.env` with restrictive permissions. | MANDATORY | Trung |

---

## 3. Safe Environment Setup (`.env` Configuration)

Production environments must configure a private, uncommitted `.env` file in the project root directory.

### Step-by-Step Configuration Instructions

1. **Create the `.env` file from the secure template:**
   ```sh
   cp .env.example .env
   ```
2. **Generate cryptographically strong credentials:**
   Do not use default or dictionary passwords in production. Generate 32-character hexadecimal strings:
   ```sh
   openssl rand -hex 32
   ```
3. **Restrict filesystem permissions:**
   Ensure only the operating system deployment user can read or modify the `.env` file:
   ```sh
   chmod 600 .env
   ```
4. **Confirm Git exclusion:**
   Verify that `.env` is ignored by Git to avoid accidental repository disclosure:
   ```sh
   git status --ignored | grep .env
   ```

### Production `.env` Template

```env
# ==============================================================================
# FinFast V1 Production Environment Configuration
# ==============================================================================

# Database Credentials
POSTGRES_DB=finfast
POSTGRES_USER=finfast_app
POSTGRES_PASSWORD=replace_with_generated_secure_password_32_chars_min

# Deployment Metadata & Environment
APP_ENV=production
DEPLOYMENT_VERSION=1.0.0
FINFAST_CONSOLE_ANSI=never

# Bootstrap Safety (TD-009 Compliance)
FINFAST_BOOTSTRAP_ENABLED=false

# Port Bindings (Host)
APP_PORT=8080
```

---

## 4. Stack Deployment & Verification Commands

### Step 1: Pre-requisite Build Verification
Ensure the executable JAR has been compiled and packaged by the CI pipeline into `backend/target/`:
```sh
cd backend
./mvnw clean package -DskipTests
cd ..
ls -lh backend/target/*.jar
```

### Step 2: Launch the Production Stack
Deploy the services explicitly using `compose.prod.yaml` in detached mode:
```sh
docker compose -f compose.prod.yaml up -d --build
```

### Step 3: Verify Service Health & Readiness
Monitor service initialization and ensure both database and backend achieve healthy status:
```sh
# Check container status and healthcheck states
docker compose -f compose.prod.yaml ps

# Inspect backend application health
curl -f http://localhost:8080/actuator/health

# Verify database health directly
docker compose -f compose.prod.yaml exec db pg_isready -U finfast_app -d finfast
```

### Step 4: Stream ECS Structured Logs
Verify that the backend is outputting ECS-compliant JSON without ANSI artifacts:
```sh
docker compose -f compose.prod.yaml logs -f backend
```

### Step 5: Safe Service Management
- **Graceful Restart:**
  ```sh
  docker compose -f compose.prod.yaml restart backend
  ```
- **Stopping Services (Preserving All Data):**
  ```sh
  docker compose -f compose.prod.yaml down
  ```
  > [!CAUTION]
  > NEVER execute `docker compose -f compose.prod.yaml down -v` or `down --volumes` in production environments. Doing so will permanently destroy the `pgdata_prod` volume containing financial ledger facts and audit histories.

---

## 5. Database Backup and Restore Strategy

### 5.1 Financial Preservation & TD-009 Mandate
In strict compliance with **TD-009**, **TD-010**, and core banking invariants:
- The **GENESIS dataset** (finite treasury balance and initialization records) and all **Audit Logs** (administrative inspections, allocations, freeze/unfreeze actions) are immutable financial records that must be **strictly preserved across restarts, upgrades, and host migrations**.
- At no point may the database be dropped, truncated, or automatically reseeded on application restart.
- Scheduled backups and pre-deployment snapshots are mandatory before any infrastructure maintenance.

### 5.2 Backup Procedure (Logical Snapshot)

Create a custom-format, compressed PostgreSQL dump using `pg_dump`:

```sh
# Ensure backup directory exists
mkdir -p ./backups

# Execute logical backup using active container credentials
BACKUP_FILE="./backups/finfast_backup_$(date +%Y%m%d_%H%M%S).dump"
docker compose -f compose.prod.yaml exec -T db pg_dump \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  -Fc > "$BACKUP_FILE"

# Verify backup size and integrity
ls -lh "$BACKUP_FILE"
```

### 5.3 Restore Procedure (Disaster Recovery)

To restore a database snapshot into an existing or recovered volume:

1. **Stop the backend application** to prevent concurrent writes:
   ```sh
   docker compose -f compose.prod.yaml stop backend
   ```
2. **Execute restore using `pg_restore`:**
   ```sh
   docker compose -f compose.prod.yaml exec -T db pg_restore \
     -U "$POSTGRES_USER" \
     -d "$POSTGRES_DB" \
     -v \
     --clean \
     --if-exists < "./backups/finfast_backup_TARGET.dump"
   ```
3. **Verify Financial Invariants & Data Integrity:**
   Run integrity checks inside the database:
   ```sh
   docker compose -f compose.prod.yaml exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
     -- Verify zero-sum double-entry invariant
     SELECT SUM(amount) AS net_ledger_balance FROM financial_entries;
     -- Verify Treasury genesis integrity
     SELECT account_number, balance, status FROM accounts WHERE role = 'TREASURY';
     -- Verify audit log count
     SELECT COUNT(*) AS total_audit_records FROM admin_audit_events;
   "
   ```
4. **Restart the Backend Application:**
   With `FINFAST_BOOTSTRAP_ENABLED=false` guaranteed in `.env`, start the backend:
   ```sh
   docker compose -f compose.prod.yaml start backend
   docker compose -f compose.prod.yaml logs -f backend
   ```
   The backend will connect to the restored database without attempting to recreate or overwrite the existing GENESIS dataset.
