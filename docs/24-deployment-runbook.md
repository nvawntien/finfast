# 24 — Deployment and recovery runbook (BK-024)

> **Owner:** Trung | **Review authority:** Tiến | **Status:** Corrective deployment contribution; review pending, not release approval.
> **Trace:** NFR-005, BK-024, EV-001/EV-002, TD-009/010/012. EV-001 and EV-002 remain **PENDING VERIFICATION**; no sign-off is inferred.

## 1. Scope and deployment gates

Follow [AGENTS](../AGENTS.md), [architecture](03-design/15-system-architecture.md),
[decisions](6-governance/25-design-decision-register.md), the
[release checklist](05-verification/23-uat-and-release-checklist.md) and the actual
[Flyway schema](../backend/src/main/resources/db/migration/README.md).
Only simulated funds and synthetic identities are permitted.

This is one Spring Boot monolith plus PostgreSQL 16, with persistent storage.
The reference Compose file is a **private deployment foundation**: backend HTTP
binds to `127.0.0.1`, PostgreSQL publishes no host port, and the Compose project
`finfast-prod` differs from development `finfast`. Never reuse development volumes.
Do not expose HTTP publicly or change the binding to `0.0.0.0` to bypass TLS.

| Gate | Status / evidence still needed |
|---|---|
| EV-001 course obligations | PENDING; Hưng/project owner must reconcile rubric and obtain the recorded decisions |
| EV-002 provider, region, SKU, budget, storage and encryption | PENDING; Sĩ/Trung must supply actual provider evidence; no sizing or price is verified here |
| Public ingress/TLS | PENDING; reviewed HTTPS termination, trusted proxy boundaries, certificate renewal, browser cookies/CSRF and security enforcement required |
| Application integration | PENDING; this branch's Java baseline is a foundation, not an authenticated production bank; `prod` enables logging, not authentication |
| Release/acceptance | PENDING; Tiến/project-owner decision and acceptance evidence remain separate from local Docker checks |

Do not publish this baseline until authentication/authorization and public ingress
are integrated and verified. No speculative proxy, cloud provider, Java changes,
OpenAPI changes or migration changes are introduced by BK-024.

## 2. Build and private environment

Use Docker Engine/Compose, Git and a POSIX shell for the commands below (for example
WSL). Java/Maven on the host are not required. `make help` is the repository command
index; production commands below use explicit Compose files rather than dev targets.

[Dockerfile.prod](../backend/Dockerfile.prod) builds from source using the same
pinned Java 21 JDK/JRE digests as the development Dockerfile. Maven Wrapper runs
database-independent tests and packages the executable JAR in the build stage.
Integration tests requiring a Docker daemon remain a separate CI job; they are not
silently replaced by the image build. `target/` stays excluded by `.dockerignore`.
The runtime runs as UID/GID 10001 and includes no compiler or test classes.

Create a dedicated secret file **outside the checkout** using a protected editor
and `umask 077`; do not copy development `.env` or reuse any local password.
Generate three independent passwords with `openssl rand -hex 32`. Use lowercase
letters, digits and underscores for the database name. Fill all fields
below with real deployment values. The password placeholders are not credentials.

```dotenv
PROD_DB_NAME=finfast_production
PROD_DB_ADMIN_PASSWORD=<new-random-admin-password>
PROD_DB_MIGRATION_PASSWORD=<new-random-migration-password>
PROD_DB_RUNTIME_PASSWORD=<new-random-runtime-password>
PROD_HTTP_PORT=18080
PROD_DEPLOYMENT_VERSION=<source-commit-or-release-tag>
```

```sh
export PROD_ENV=/secure/finfast/production.env
export PROD_PROJECT=finfast-prod
chmod 600 "$PROD_ENV"
# Run from the checkout root. Set a different project AND port for isolated drills.
dc() { docker compose --env-file "$PROD_ENV" -p "$PROD_PROJECT" -f compose.prod.yaml "$@"; }
dc config --quiet
dc build backend
```

All credential variables and the database name/version are required; `PROD_*`
names prevent accidental reuse of development `POSTGRES_*` values. Compose shell
environment variables override env-file values: unset stale `PROD_*` credential,
name and port variables before running. Never print expanded `docker compose config`
or container environment dumps into shared logs. `.env` values are not automatically
exported to the shell; later commands read the database container's environment.
Docker administrators can inspect container secrets: restrict host/Docker access.
TLS, encrypted volumes/backups and provider secret-store integration remain pending.

The merged bootstrap configuration honors `FINFAST_BOOTSTRAP_ENABLED=false`;
it also has no automatic startup setup invocation. Current migrations seed no rows.
Use only the approved protected operator setup when available; never reseed on boot.

## 3. Database roles, startup and privileges

Use a **new empty deployment volume** for initial setup. The PostgreSQL image uses
`finfast_admin` only for initialization/maintenance; it is a superuser and is never
the application DataSource identity. The non-superuser `finfast_migration` owns
schema objects, while `finfast_runtime` owns no database/schema/table and receives
explicit grants. Credentials/names initialize a fresh volume only: changing the env
file does not rotate existing passwords or rename an existing database. Plan and
verify explicit password rotation; never delete a volume to apply new credentials.

```sh
dc up -d --wait --wait-timeout 120 db
# One-time initialization only. Existing roles cause an error; do not mask it.
dc exec -T db sh -ec 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
\getenv migration_password MIGRATION_PASSWORD
\getenv runtime_password RUNTIME_PASSWORD
\getenv database_name POSTGRES_DB
CREATE ROLE finfast_migration LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'migration_password';
CREATE ROLE finfast_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'runtime_password';
REVOKE ALL ON DATABASE :"database_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database_name" TO finfast_migration, finfast_runtime;
ALTER SCHEMA public OWNER TO finfast_migration;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO finfast_runtime;
SQL

dc up -d --wait --wait-timeout 180 backend
```

Flyway uses its dedicated migration connection, not the runtime DataSource. Both
URLs derive from `PROD_DB_NAME`. Flyway clean remains disabled. After migrations,
apply these grants (also after restoring a backup). New tables/capabilities require
explicit review; there are no blanket default grants for future migrations.

```sh
dc exec -T db sh -ec 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
GRANT SELECT, INSERT ON public.app_user, public.customer, public.account,
  public.financial_transaction, public.ledger_entry, public.dataset_control,
  public.request_outcome, public.admin_audit_event TO finfast_runtime;
GRANT UPDATE (balance_vnd, status) ON public.account TO finfast_runtime;
GRANT UPDATE (last_commit_seq) ON public.dataset_control TO finfast_runtime;
REVOKE ALL ON public.flyway_schema_history FROM finfast_runtime;
SQL
```

These grants match V1/V2 persistence needs, retain immutable guards, and exclude
DELETE/TRUNCATE, schema creation and immutable financial UPDATE. They do not replace
application authorization/transaction invariants. **Limitation:** Flyway's owner
credential is still present in the backend container to run migrations at startup;
a fully compromised process could use it. DataSource role separation is verified
separately from process-level secret isolation. A separately reviewed migration
execution/secret-lifecycle design is required before claiming the latter.

```sh
dc ps
curl --fail --silent --show-error http://127.0.0.1:18080/actuator/health
# Use the configured PROD_HTTP_PORT if different from 18080.
dc exec -T db sh -ec 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
dc exec -T db sh -ec 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication
FROM pg_roles WHERE rolname IN ('finfast_runtime', 'finfast_migration');
SELECT has_schema_privilege('finfast_runtime', 'public', 'CREATE') AS runtime_can_create,
       has_table_privilege('finfast_runtime', 'public.ledger_entry', 'UPDATE') AS runtime_can_change_facts,
       has_table_privilege('finfast_runtime', 'public.account', 'TRUNCATE') AS runtime_can_truncate;
SHOW fsync;
SHOW full_page_writes;
SHOW synchronous_commit;
SQL
# Expected: Flyway 1/2 successful; both roles non-superuser; privileges false;
# durability settings on/on/on. Review safe ECS stdout; do not publish secret dumps.
dc logs --tail 50 backend
```

Health UP proves foundation/DB connectivity, not working banking endpoints or TLS.
For upgrades, preserve a backup and the previous source/image revision first, review
migration compatibility, and apply only approved additive migrations. Do not run an
old image against a new incompatible schema or auto-downgrade Flyway.

## 4. Backup, isolated restore and recovery

Use a restricted encrypted backup destination outside Git. A custom-format dump is
a consistent logical snapshot; roles/passwords are not included. Store role setup
and secrets through the protected operational process. File size or archive listing
alone is not recovery proof. Retain checksums, source revision, PostgreSQL version,
Flyway versions, dataset identity, counts and reconciliation output with the backup.
For a directly comparable drill, quiesce writers before collecting both the baseline
and dump. Production recovery needs an owner-approved outage/RPO/RTO plan.

```sh
# Set BACKUP_DIR to a protected directory outside the checkout.
export BACKUP_DIR=/secure/finfast/backups
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/finfast_$(date -u +%Y%m%dT%H%M%SZ).dump"
dc stop backend
# Write binary inside the container, then copy; avoids PowerShell stdout corruption.
dc exec -T db sh -ec 'umask 077; pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/finfast-backup.dump; pg_restore --list /tmp/finfast-backup.dump >/dev/null'
docker cp "$(dc ps -q db):/tmp/finfast-backup.dump" "$BACKUP_FILE"
chmod 600 "$BACKUP_FILE"
sha256sum "$BACKUP_FILE"
dc exec -T db rm /tmp/finfast-backup.dump
# Start only if this was a backup drill, not an ongoing recovery incident.
dc start backend
```

Restore first into a **different Compose project with a fresh volume** and dedicated
secret file/database name/port. Keep the original stack and backup intact. Record
its identity before changing `PROD_ENV`/`PROD_PROJECT`; never restore over the live
DB. Do not use `pg_restore --clean`, `dropdb`, `down -v` or reseeding as recovery.

1. Set `PROD_ENV` to the recovery env file and `PROD_PROJECT` to a unique name such
   as `finfast-recovery-20261026`; use a separate `PROD_DB_NAME` and HTTP port.
2. Start only `db` and run the one-time role setup in section 3. Verify the target
   has **no application tables**, and verify its volume is not the source volume.
3. Restore all schema/data/triggers/Flyway history in one transaction as the
   migration owner. Do not start the backend first (it would create the tables).

```sh
# dc now points ONLY to the isolated recovery project.
dc exec -T db sh -ec 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT count(*) AS existing_tables FROM pg_tables WHERE schemaname = '\''public'\'';"'
# Stop unless existing_tables = 0 and the project/volume are the recovery target.
docker cp "$BACKUP_FILE" "$(dc ps -q db):/tmp/finfast-restore.dump"
dc exec -T db sh -ec 'pg_restore --exit-on-error --single-transaction --no-owner --no-privileges --role=finfast_migration -U "$POSTGRES_USER" -d "$POSTGRES_DB" /tmp/finfast-restore.dump'
dc exec -T db rm /tmp/finfast-restore.dump
# Reapply explicit grants from section 3, then run ALL verification SQL below.
```

Run this SQL against both the quiesced source and restored database, using the
`dc exec ... psql` wrapper from section 3. Compare output against the same backup
snapshot. These statements use the checked-in Flyway names; GENESIS is a single
positive entry, so the entire ledger is **not** expected to sum to zero.

```sql
SELECT version, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT account_number, balance_vnd, status FROM public.account WHERE account_type = 'TREASURY';
SELECT * FROM public.dataset_control ORDER BY singleton_id;
SELECT count(*) AS audit_records FROM public.admin_audit_event;
SELECT count(*) AS final_outcomes FROM public.request_outcome;
-- Must return zero rows: wrong cardinality/net/magnitude for an ordinary movement.
SELECT t.reference FROM public.financial_transaction t
LEFT JOIN public.ledger_entry e ON e.transaction_id = t.id
WHERE t.type <> 'GENESIS'
GROUP BY t.id, t.reference, t.amount_vnd
HAVING count(e.id) <> 2 OR sum(e.delta_vnd::numeric) <> 0
   OR min(e.delta_vnd::numeric) <> -t.amount_vnd::numeric
   OR max(e.delta_vnd::numeric) <> t.amount_vnd::numeric;
-- Must return zero rows: GENESIS must have one positive Treasury entry.
SELECT t.reference FROM public.financial_transaction t
LEFT JOIN public.ledger_entry e ON e.transaction_id = t.id
LEFT JOIN public.account a ON a.id = e.account_id
WHERE t.type = 'GENESIS' GROUP BY t.id, t.reference, t.amount_vnd
HAVING count(e.id) <> 1 OR sum(e.delta_vnd::numeric) <> t.amount_vnd::numeric
   OR count(*) FILTER (WHERE a.account_type = 'TREASURY') <> 1;
-- Must return zero rows: every cached balance matches committed signed deltas.
SELECT a.account_number FROM public.account a
LEFT JOIN public.ledger_entry e ON e.account_id = a.id
GROUP BY a.id, a.account_number, a.balance_vnd
HAVING a.balance_vnd::numeric <> coalesce(sum(e.delta_vnd::numeric), 0);
SELECT (SELECT sum(balance_vnd::numeric) FROM public.account) AS total_balances,
       (SELECT sum(delta_vnd::numeric) FROM public.ledger_entry) AS net_ledger,
       initial_supply_vnd, last_commit_seq,
       (SELECT max(commit_seq) FROM public.financial_transaction) AS max_commit_seq
FROM public.dataset_control;
-- Initialized datasets: first three supply values equal, last two counters equal.
-- Fresh foundation: no dataset/GENESIS is expected; empty output is NOT banking acceptance.
-- Exact count/content fingerprints for ALL eight tables plus Flyway (small synthetic drills).
SELECT format('SELECT %L AS table_name, count(*) AS rows, md5(coalesce(string_agg(row_to_json(t)::text, E''\n'' ORDER BY row_to_json(t)::text), '''')) AS content_hash FROM public.%I t;', tablename, tablename)
FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
\gexec
```

Any restore error, missing dataset, mismatch, broken invariant or health failure
blocks promotion. Keep the original database unchanged for investigation. Following
successful restore/grants, start the recovery backend and verify health, unchanged
Flyway versions, restart and data persistence. Inspect that table owners are
`finfast_migration`, recheck runtime grants/denials and immutable guards. Record
results; only an approved cutover plan may promote recovery to the active dataset.
Fence old writers before switching, preserve the old data/image, and stop writes on
failure. Never run both as writable copies of the same logical dataset. Rollback
after new writes needs reconciliation and an explicit owner decision, not blind
switching to a stale backup. No actual production cutover is performed here.

## 5. Restart and persistence drill

Run on an isolated project with synthetic records only. Preserve reference outputs
from section 4 before each operation. The deployment verification fixture is not
production seed data and must never be installed on a developer/live database.

```sh
dc restart db backend
dc up -d --wait --wait-timeout 180
# Compare health, Flyway state, dataset, audit/outcomes and all table fingerprints.
dc down
# No -v: preserve the project's named database volume.
dc up -d --wait --wait-timeout 180
# Repeat the same comparisons after container recreation.
```

This checks container restart/recreation, not host power-loss durability, cloud disk
failure, browser acceptance or HA. Never delete persistent volumes during routine
stop, deployment or recovery.

## 6. BK-024 corrective verification record

Local execution on **2026-10-26 (Asia/Saigon)** used branch
`feat/BK-024-cloud-deployment`, original HEAD `42c5cdb`, fetched/merged `develop`
`ef2d520b2ab64070b2462d461984688e806a7df6`, plus the three uncommitted BK-024
corrections. The merge was subsequently completed as local commit `635284c`;
the three BK-024 corrections remain uncommitted. Java, POM,
OpenAPI, Flyway and architecture rules match that develop baseline; they are not
changed by the BK-024 corrections.

Environment: Windows/Docker Desktop Linux engine 29.8.0, Compose v5.5.1, pinned
Temurin Java 21.0.12.1+1 and PostgreSQL 16.15 images. The two isolated projects were
`finfast-bk024-check-20261026` and `finfast-bk024-restore-20261026`, using loopback
ports 28024/28025, distinct database names, random test-only passwords and new
volumes. No developer `.env`, DB or existing volume was used.

| Check actually executed | Result |
|---|---|
| Docker production build from `git archive origin/develop` plus corrected Dockerfile, with no host `target/` | PASS; multi-stage build, existing content-addressed build cache allowed; no host JAR input |
| Runtime identity and executable JAR | PASS; UID/GID 10001; repository JAR checker passed against the actual production image JAR and compiled test-class inventory |
| Compose startup and health | PASS; DB and backend healthy, HTTP health returned only `{"status":"UP"}`, Flyway V1/V2 successful |
| Network/database configuration | PASS; non-default DB name used by both JDBC URLs, HTTP only 127.0.0.1, DB has no published port, project/volume separate from dev |
| Runtime privileges | PASS; authenticated as `finfast_runtime`; schema CREATE, ledger UPDATE, account TRUNCATE, trigger disabling and Flyway-history SELECT all denied; both application roles non-superuser |
| PostgreSQL durability settings | PASS; fsync, full_page_writes and synchronous_commit all on |
| Nonempty synthetic dataset | 2 users, 1 customer, 2 accounts, 2 financial transactions (GENESIS + allocation), 3 ledger entries, 1 dataset row, 1 final outcome, 1 admin audit event |
| Restart and persistence | PASS; DB/backend restart and Compose down/up without volume deletion preserved all nine table count/content fingerprints, including Flyway |
| Immutable guards | PASS; owner attempts to UPDATE financial transactions and DELETE ledger entries rejected by append-only triggers |
| Binary custom-format dump and isolated restore | PASS; single-transaction restore into a different fresh project/volume, no clean/drop of source; all table counts/fingerprints and Flyway checksums matched |
| Recovery startup/restart | PASS; restored objects owned by migration role, explicit grants reapplied, backend healthy and snapshot unchanged after restart |
| Missing configuration / fixture safety | PASS; absent production DB configuration rejected; rerunning fixture on existing dataset rejected without changing data |
| Java regression suite after develop synchronization | PASS; Maven verify ran 1,404 tests across 66 suites, zero failures/errors/skips, including ArchUnit and real PostgreSQL integration tests; 12m57s |
| Documentation/static checks | PASS; 28 Markdown files, 496 local links, 88 OpenAPI references; YAML/OpenAPI validator, Compose config, LF shell syntax and whitespace checks passed |

Reconciliation returned no invalid ordinary/GENESIS postings or balance mismatches.
Total account balances, net ledger and initial GENESIS supply were each 100000 VND;
last_commit_seq and maximum committed sequence were both 2. This is synthetic
backup evidence, not end-to-end banking acceptance.

Production image config ID:
`sha256:c8bbf60a1856b6b8d8b48ee80b5e3904c50cf24717c2fa22f49389549d272402`.
Synthetic backup SHA-256:
`53504cf70b25ac9ad9a88d21df28fe05119970ceef07dfdff6bf9983993d4329`.
Evidence is retained locally in the Git-ignored `.bk024-review.tmp/` directory:
`latest-build.log`, `deployment.log`, `deployment-results.json`, `privileges.txt`,
`source-verification.txt`, `restore-verification.txt`, and `full-tests/`. The local
harness/env files and synthetic backup are not repository deliverables; do not
upload raw env files or Docker inspection output. Safe summarized results above
are the review record. Both drill stacks were brought down preserving test volumes.

The lead-reported 1,052 CI tests predate this synchronization and do not demonstrate
production Docker correctness. This local evidence is separate from hosted CI on
the future pushed commit. Public TLS/cloud/provider, EV-001/EV-002 sign-off,
process-level migration-secret isolation, browser security, actual host-crash/cloud
storage recovery and final BK-024/release acceptance remain **PENDING**. No approval
or production deployment is claimed.

### Reproducing the synthetic persistence fixture

After role/migration/grant setup on a new isolated drill project only, run the
following transaction through the section 3 maintenance psql wrapper. It requires
a database name starting with `finfast_bk024_` and an empty dataset. The rows
exercise backup/restart/reconciliation; direct fixture insertion is **not evidence
of application bootstrap, financial posting or registration correctness**. Keep
this fixture out of any developer, shared or production database.

```sql
BEGIN;
DO $$ BEGIN
  IF current_database() NOT LIKE 'finfast_bk024_%' THEN
    RAISE EXCEPTION 'Synthetic fixture requires an isolated finfast_bk024_ database';
  END IF;
  IF EXISTS (SELECT 1 FROM public.app_user) OR EXISTS (SELECT 1 FROM public.account) THEN
    RAISE EXCEPTION 'Synthetic fixture requires a fresh empty dataset';
  END IF;
END $$;
INSERT INTO public.app_user(id,email_normalized,password_hash,role) VALUES
('00000000-0000-0000-0000-000000000001','bk024-admin@example.invalid','synthetic-non-login-hash','ADMIN'),
('00000000-0000-0000-0000-000000000002','bk024-customer@example.invalid','synthetic-non-login-hash','CUSTOMER');
INSERT INTO public.customer(id,user_id,display_name) VALUES
('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','BK024 synthetic drill');
INSERT INTO public.account(id,account_number,customer_id,account_type,balance_vnd) VALUES
('00000000-0000-0000-0000-000000000004','BK024-TREASURY',NULL,'TREASURY',99000),
('00000000-0000-0000-0000-000000000005','BK024-CUSTOMER','00000000-0000-0000-0000-000000000003','CUSTOMER',1000);
INSERT INTO public.financial_transaction(id,reference,type,amount_vnd,currency,commit_seq,committed_at) VALUES
('00000000-0000-0000-0000-000000000006','BK024-GENESIS','GENESIS',100000,'VND',1,'2026-10-26T00:00:00Z'),
('00000000-0000-0000-0000-000000000007','BK024-ALLOCATION','DEMO_ALLOCATION',1000,'VND',2,'2026-10-26T00:00:01Z');
INSERT INTO public.ledger_entry(id,transaction_id,account_id,delta_vnd) VALUES
('00000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000004',100000),
('00000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000004',-1000),
('00000000-0000-0000-0000-000000000010','00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000005',1000);
INSERT INTO public.dataset_control VALUES(1,'00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000006',100000,1);
UPDATE public.dataset_control SET last_commit_seq=2 WHERE singleton_id=1;
INSERT INTO public.request_outcome(id,initiator_user_id,operation_type,idempotency_key,canonicalization_version,canonical_intent,payload_fingerprint,final_kind,response_snapshot,transaction_id,created_at) VALUES
('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001','DEMO_ALLOCATION','bk024-drill',1,'{}',repeat('a',64),'COMPLETED','{}','00000000-0000-0000-0000-000000000007','2026-10-26T00:00:01Z');
INSERT INTO public.admin_audit_event(id,admin_user_id,target_account_id,action,reason,outcome,transaction_id,created_at) VALUES
('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000005','DEMO_ALLOCATION','Isolated BK024 recovery drill','SUCCESS','00000000-0000-0000-0000-000000000007','2026-10-26T00:00:01Z');
COMMIT;
```
