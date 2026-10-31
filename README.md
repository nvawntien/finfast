# FinFast

FinFast is a banking simulator for simulated funds and synthetic customer data.
Its V1 scope covers Customer accounts, internal transfers, a signed-entry Ledger,
finite Treasury allocations, committed history/receipts and Admin account controls.
Customer and Admin Web interfaces are planned within that scope.

The backend is one deployable Java/Spring Boot modular monolith with DDD-inspired
modules, pragmatic Hexagonal Architecture and PostgreSQL/Flyway. The source
includes schema and shared error/VND foundations, public module APIs, architecture
checks, logging, HTTP tracing, optional metrics/dashboards/alerts, and selected
Ledger and banking-module contributions. These do not establish a complete,
production-wired V1 workflow. No browser frontend is present in this revision.
Check source, tests, and executed evidence for the current scope of any capability.

Start at the [documentation index](docs/README.md) for the current authority map,
including approved requirements, architecture, API/Java contracts, verification and
decisions. This README covers local setup, development commands and CI reproduction;
the index distinguishes approved intent from implementation and verification evidence.

## Backend baseline

| Technology | Pinned version |
|---|---|
| Java | 21 LTS (container: Eclipse Temurin 21.0.12.1+1) |
| Spring Boot | 4.1.1, using its parent POM for dependency management |
| Tomcat / Jackson 3 | 11.0.26 / 3.1.7, targeted security patch overrides |
| Maven runtime | 3.9.11 |
| Maven Wrapper | 3.3.4, with checked-in launcher scripts and JAR |
| Packaging | Executable JAR, `com.finfast:finfast-backend:0.0.1` |
| Docker build image | `eclipse-temurin:21.0.12.1_1-jdk-noble` |
| Docker runtime image | `eclipse-temurin:21.0.12.1_1-jre-noble` |
| Backend port | 8080 |
| PostgreSQL | 16.15, `postgres:16.15-alpine3.24` |
| Flyway | 12.4.0, managed by Spring Boot |
| PostgreSQL JDBC driver | 42.7.13, managed by Spring Boot |
| Testcontainers | 2.0.5, managed by Spring Boot; test scope only |
| ArchUnit | 1.5.1 (`archunit-junit6`), test scope; matches managed Jupiter 6.0.3 |

Both Docker base images are pinned to verified manifest digests in
[`backend/Dockerfile`](backend/Dockerfile). The PostgreSQL image is also pinned to
a verified manifest digest in [`compose.yaml`](compose.yaml). Maven distribution
and Wrapper JAR SHA-256 checksums are pinned in the Wrapper properties.
The [observability architecture review](observability/architecture-review.md)
records the security patch rationale and local-versus-production boundaries.

## Prerequisites

- Git to clone the repository.
- Docker with a running engine and Docker Compose v2 or later. Docker Desktop
  includes both. No local Java, Maven or PostgreSQL installation is required for
  the Docker workflow.
- GNU Make for the shortcuts below; `make help` lists every target. Docker Compose
  and Maven Wrapper commands remain available directly.
- Network access to Docker Hub and Maven Central for initial image/dependency
  downloads, and available local ports 8080 and 15432 (database port configurable).
- `curl` or an HTTP client to check health.

## Quick start with Docker

Clone this repository, enter its root directory, then run:

```bash
git clone https://github.com/nvawntien/finfast.git
cd finfast
make setup
make up
make health
make logs
```

`make setup` copies `.env.example` only if `.env` is missing; edit your local values
before startup if necessary. `.env` is ignored by Git. **Development credentials
must never be reused for a public deployment.** `make health` may need a retry while
the application starts. Expected response: `{"status":"UP"}`. Ctrl+C stops log
following without stopping services.

`make logs` follows backend stdout without Compose's container prefix. Normal Docker
mode enables INFO green, WARN yellow, ERROR red and cyan module labels. Rendering
depends on the viewing terminal. Set `FINFAST_CONSOLE_ANSI=never` in `.env` and rerun
`make up` to disable local colors; old stored records do not gain colors retroactively.
Observed/prod profiles keep ECS JSON ANSI-free.

Without Make, copy `.env.example` once without overwriting an existing `.env`, then
run `docker compose up --build -d`. The equivalent health check is
`curl --fail http://localhost:8080/actuator/health`.

| Shortcut | Behavior |
|---|---|
| `make ps` / `make logs` | Application container status / follow backend logs |
| `make stop` / `make start` | Stop/start existing containers; retain their current mode and configuration |
| `make restart` | Restart the existing backend; does not apply changed configuration |
| `make down` | Remove application containers/network; preserve database volumes |
| `make test` / `make build` | Host Maven Wrapper tests / executable JAR packaging; require Java 21 and Docker |

## Backend and PostgreSQL development

The Docker build uses Maven Wrapper/Java 21, runs database-independent tests and
uses a non-root Java 21 JRE runtime. PostgreSQL must pass `pg_isready` before the backend
starts. Compose supplies `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` from `.env`; the JDBC URL uses `postgres:5432` and
`POSTGRES_DB`. Host access is loopback 8080/15432. `POSTGRES_PORT` changes only the
database host port; container communication uses the application network and port 5432.

Actuator's standard database check means an outage produces health
`DOWN` and HTTP 503 while the backend is running. Connection failure during
Flyway startup prevents a successful backend startup. Public health responses
show aggregate status without database details or credentials.

`make down` preserves the named `postgres_data` volume (normally
`finfast_postgres_data`) and its database contents. Starting the stack again reuses
that data. **`docker compose down -v` removes associated volumes and can destroy
local development data.** Do not use it for routine shutdown.

Every developer runs an independent database and volume on their own Docker
engine. Share schema changes through Git and Flyway migrations, never through a
shared team database or by connecting to another developer's local instance. On
a shared Docker engine, use distinct Compose project names and non-conflicting
host ports to isolate environments.

`POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` initialize a new database
volume only. Editing them in `.env` does not rename an existing database/role or
change its stored password; make any required changes explicitly without
deleting existing data.

## Run natively

Install a Java 21 JDK and point `JAVA_HOME` at it. PostgreSQL still runs in Docker.
Use one backend mode at a time: stop the Docker backend with
`docker compose stop backend` before running a native backend on port 8080.

From the repository root, after creating `.env`, run these commands in a POSIX
shell:

```bash
# Export the local example's shell-compatible values.
set -a
. ./.env
set +a
docker compose up -d --wait postgres

export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-15432}/${POSTGRES_DB}"
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"

cd backend
./mvnw --version
./mvnw spring-boot:run
```

Compose loads `.env` for interpolation; a native Spring Boot process does not
load that file automatically. The exports above give it the same local database
credentials and the host-facing JDBC URL. On Windows, set the same three
`SPRING_DATASOURCE_*` variables in your shell before starting the backend.

The Wrapper downloads and uses Maven 3.9.11; a separate Maven installation is not
needed. On Windows, use `mvnw.cmd` in place of `./mvnw`.

Check the same health URL from another terminal. Press **Ctrl+C** in the running
terminal to stop the application. For a different native port, set `SERVER_PORT`
(for example, `SERVER_PORT=8082 ./mvnw spring-boot:run`) and adjust the health URL.
The default Compose workflow keeps the container and published port at 8080.
After stopping the native process, run `docker compose down` from the repository
root to stop PostgreSQL safely.

## Connect to PostgreSQL for debugging

No host PostgreSQL client is needed:

```bash
docker compose exec postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Useful read-only SQL inside `psql`:

```sql
SELECT 1;
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Use `\q` to exit. With an optional host `psql` client and the example settings:

```bash
psql -h 127.0.0.1 -p 15432 -U finfast -d finfast -W
```

Adjust the port, username and database if you changed `.env`.

## Flyway and future schema changes

Flyway runs automatically at backend startup and is the source of truth for
schema changes. Migrations live in
[`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration/README.md).
V1 creates the eight banking tables and V2 protects immutable financial facts.
Flyway validates and applies these migrations; they create no banking seed data,
Treasury, GENESIS or sessions. See the migration README for exact database and
application-level guarantees.

Use unique increasing versions for future migrations and coordinate them
across branches before merging. Restarting the backend applies pending migrations
and validates applied migration checksums. Do not modify an already applied
migration during normal development; add a new one instead.

Flyway clean is disabled. There is no Hibernate schema generation, automatic
data reset or placeholder migration. PostgreSQL retains its normal durability
settings (`fsync`, `full_page_writes` and `synchronous_commit` enabled).

## Tests and executable JAR

With Java 21 and a running Testcontainers-compatible Docker runtime, from
`backend/`:

```bash
./mvnw test
./mvnw package
```

Tests cover context/health, PostgreSQL schema and Flyway V1/V2, rollback/concurrency,
logging, real HTTP tracing/metrics, shared error/VND contracts and architecture
rules. Architecture checks run automatically, with test-only valid/invalid fixtures;
they validate the present API contracts without proving provider behavior or
financial correctness.
Run just those checks with `./mvnw -Dtest=ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests test`.
Database tests use isolated PostgreSQL 16.15
Testcontainers with Boot-supplied datasource/Flyway connections, never the developer's
persistent database. No local Grafana credentials or telemetry stack is required.

The full test suite requires a compatible Docker runtime; a missing runtime is a
test failure. Image builds explicitly exclude tests tagged `integration` because
a Docker build stage has no Docker daemon. They run database-independent tests. To
run just that limited group locally:

```bash
./mvnw -DexcludedGroups=integration test
```

Packaging runs the full suite and produces `target/finfast-backend.jar`. With the
native datasource environment variables configured above, start it using
`java -jar target/finfast-backend.jar`. Press **Ctrl+C** to stop it.

For documentation/OpenAPI checks, see [docs/README.md](docs/README.md#validate-documentation-locally-and-in-ci).

## Module contracts and collaboration

Use [module boundaries](docs/architecture/boundaries.md) for ownership,
permitted consumers, capability rules, and contract-change review. Exact public
Java declarations live in module source. Do not duplicate them, import another
module's internals, or silently break a public API. Follow the
[development workflow](docs/development/workflow.md) for Issue and change
coordination. Financial invariants and API-ERR-01 integration constraints remain
in their owning authorities; FIX-001 NUL validation is implemented in the Ledger
domain source and its tests.

## Continuous integration

The [CI workflow](.github/workflows/ci.yml), named **CI**, runs on every PR targeting
`develop` or `main`, pushes to either branch, and manual `workflow_dispatch`.
Documentation-only and future frontend-only PRs run the same checks. There are no
path filters. Superseded runs are cancelled within the same PR or branch/event;
separate PRs, push events and manual events have distinct concurrency groups.

| Stable job/check name | What it verifies |
|---|---|
| `backend-tests` | Temurin Java 21, executable Maven Wrapper, usable Docker; full JUnit suite including ArchUnit, context, HTTP, PostgreSQL/Flyway and Testcontainers tests; 30-minute timeout |
| `backend-package` | Requires successful `backend-tests`, compiles tests with `-DskipTests package`, then verifies the executable JAR, manifest, launcher and absence of compiled test classes/test-only libraries; 15-minute timeout |
| `docs-validation` | Python 3.12, pinned documentation dependencies, OpenAPI schema/references, duplicate YAML keys, README/docs links/anchors and documentation Compose config; 10-minute timeout |

All jobs use `ubuntu-24.04`. Java jobs cache Maven dependencies using `setup-java`
and hashes of the backend POM/Wrapper properties; documentation caches pip using
the pinned requirements file. Official actions are pinned to immutable SHAs with
release comments. Existing checkout v4.2.2, setup-python v5.6.0 and documentation
dependency versions are preserved; setup-java v6.1.0 supplies Java/caching.

Testcontainers uses the runner's Docker Engine to start disposable PostgreSQL
16.15 containers with synthetic test data and Boot `@ServiceConnection` wiring.
CI does not load `.env`, run the persistent application Compose stack, supply a
shared database or exclude integration tests. Missing Docker or failed image/
dependency downloads fail CI. Docker image builds remain outside this baseline.

The workflow uses normal `pull_request`, `contents: read` permissions and checkout
with credential persistence disabled. It needs no application/deployment secrets,
service containers, privileged PR context or Docker socket container mounts.
No artifacts are uploaded: Surefire XML can contain JVM properties and captured
output, so reports remain in the runner workspace rather than being published by
default. JARs are validated without release, registry or deployment publication.

### Reproduce and investigate checks

With Java 21 and Docker available, use a fresh shell without developer datasource
exports. From the repository root:

```bash
java -version
test -x backend/mvnw
docker version
cd backend
./mvnw --batch-mode --no-transfer-progress test
# Only after the full suite succeeds; test compilation is still enabled.
./mvnw --batch-mode --no-transfer-progress -DskipTests package
cd ..
python3 scripts/check_backend_jar.py
```

The [JAR checker](scripts/check_backend_jar.py) uses Python's standard library and
reads the artifact name/packaging from the checked-in POM. For documentation, run
the [same local validation commands as CI](docs/README.md#validate-documentation-locally-and-in-ci).
`make test` / `make build` remain available; `make build` reruns tests.

In GitHub, open **PR → Checks → failing job → Details**, or **Actions → CI → run →
job**, then inspect the first failing step. A package prerequisite failure points
back to `backend-tests`; packaging never starts after failed tests. Locally, inspect
`backend/target/surefire-reports/` for test failures. Do not publish raw reports,
logs or environment files containing private data. For a confirmed transient
Docker Hub/Maven Central outage, retain the failure evidence and rerun failed jobs
after recovery; do not suppress failures or disable tests.

### Required checks and review

Intended flow: `feature/* → PR → develop → reviewed release PR → main`. After the
new workflow has completed successfully on GitHub, a maintainer can configure
**Settings → Rules → Rulesets** for both branches:

1. Open/create the branch ruleset targeting `develop`, then repeat for `main`.
2. Enable **Require status checks to pass**, inspect the successful run's actual
   job names/conclusions on the latest PR commit, and select `backend-tests`,
   `backend-package` and `docs-validation` from GitHub Actions.
3. If the retired documentation job `validate` was required, replace it with
   `docs-validation` after confirming the new successful check exists.
4. Configure required PR review/approvals separately according to team policy,
   and verify the saved ruleset's enforcement on a real PR.

**CI** is the workflow name; the three explicit job `name` values above are the
intended required check names. Their current names have been confirmed in the merged-baseline run linked below; select them on the actual protected branches and verify enforcement separately. Path-filtered required workflows can leave checks pending
and block merges. Conditional jobs can report success when skipped without doing
the work. Here jobs are never skipped by changed files: `backend-package` uses
`!cancelled()` plus an explicit success gate, so failed tests produce a failing
package check while superseded runs can still cancel. Renaming a required job can require a ruleset
update. PR approval and successful CI are separate requirements; neither replaces
the other. See [GitHub's required-check troubleshooting](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).

Workflow automation is implemented in source; [local verification evidence](docs/05-verification/22-test-strategy-and-acceptance.md#ci-foundation-implementation-and-evidence)
is separate from GitHub execution. Live verification on 2026-10-22 confirmed the
[merged develop run](https://github.com/nvawntien/finfast/actions/runs/39649338194)
at `8a04ca0` completed `backend-tests`, `backend-package` and `docs-validation`
successfully, including the merged BK-003 baseline. This evidence applies to that
foundation revision and does not verify banking providers. Ruleset enforcement
requires separate verification. Workflow YAML alone does not enable branch protection. No deployment CD or banking-feature acceptance is provided by these foundation checks.

### Frontend readiness

No frontend application, package manager lockfile or build scripts exist yet.
When Sĩ establishes the actual stack, add a stable frontend check for dependency
installation from its lockfile, lint, type checking, unit tests and a production
build. Validate it on GitHub before requiring it in both rulesets. Until then,
there is no frontend tooling or placeholder required job.

## Swagger UI

Run from the repository root:

```bash
make docs-up
# Browse http://localhost:8081
make docs-down
```

This separate design-contract viewer requires no backend or database. Request
execution is disabled; these documented banking APIs are not implemented endpoints.
See the [API contract guide](docs/03-design/17-api-contracts.md) for the external API authority.

## Optional Grafana observability

Run the following commands from the repository root.

Grafana opens at **http://localhost:3300**. Host port **3000 stays reserved for the
frontend**; Grafana's container port remains 3000. Normal `make up` keeps pretty
console logs and needs no telemetry services.

```bash
make setup
make observability-setup
# Edit .env.observability: choose GRAFANA_ADMIN_USER and GRAFANA_ADMIN_PASSWORD.
make observability-up
make observed-up
make observability-health
```

Setup preserves an existing file; the example has blank credentials and startup
rejects them until you supply both values. Login with your chosen credentials.
Set `GRAFANA_PORT` in `.env.observability` to change that host port. Initialized
Grafana credentials must be changed through administration, not just environment edits.

The observed backend exposes metrics and exports traces to Alloy over an optional
network; PostgreSQL stays on the application network. Grafana provisions all three
datasources automatically. Select **FinFast Loki** in Explore to query
`{service_name="finfast-backend"}` and click **View trace in Tempo** on an expanded
log containing a Trace ID. This uses real tracing, not generated access-log events;
successful requests need not emit an application log.

Select **FinFast Tempo** for trace search, or **FinFast Prometheus** for metrics. Open
[FinFast — Backend Overview](http://localhost:3300/d/finfast-backend-overview) for
HTTP/JVM/Hikari metrics. [Metrics and alerts](observability/metrics-and-alerts.md)
documents histogram estimates, alert thresholds and the local receiver with no
external notification delivery. Scrape availability does not prove database health.

Return to ordinary backend mode and stop telemetry safely:

```bash
make up
make observability-down
```

`make up` reapplies the ordinary backend configuration; `make start`/`restart` do not
switch modes. `observability-down` stops telemetry containers and preserves containers,
volumes and the shared network. For full shutdown use `make down`,
`make observability-down` and, if used, `make docs-down`; no command deletes volumes.

See [observability setup and verification](observability/README.md) for security,
retention, log/trace queries, networking, resource use and repeatable end-to-end checks.

## Project structure and documentation

| Path | Purpose |
|---|---|
| `backend/` | One Spring Boot application, common infrastructure, Flyway migrations and isolated tests |
| `backend/src/test/java/com/finfast/architecture/` | Production architecture scans, approved boundary/capability rules and fixture verification |
| `compose.yaml`, `compose.docs.yaml` | Backend/PostgreSQL and separate Swagger UI stacks |
| `compose.observed.yaml`, `compose.observability.yaml` | Opt-in backend overlay and six telemetry services |
| `observability/` | Stack runbook, component configs, Grafana provisioning and verification helpers |
| `docs/` | Product intent, durable engineering guidance, operative detailed requirements, and scoped verification evidence |
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | Backend tests, executable JAR packaging and documentation validation |

Start at the [documentation index](docs/README.md). The
[logging/error/tracing guide](docs/03-design/logging-and-error-handling.md) defines
**Clean Business Code, Centralized Observability**; the
[architecture review](observability/architecture-review.md) records dated evidence
and deployment gates. Implemented schema decisions are explained in the
[migration README](backend/src/main/resources/db/migration/README.md); other design
mechanisms follow the scoped statuses in the current decision register; no foundation test establishes financial workflow correctness.

Before starting a business module, read [AGENTS.md](AGENTS.md), the approved use
cases/rules through the docs index, the [module boundaries](docs/architecture/boundaries.md)
and [shared error/VND guide](docs/03-design/shared-error-money-contracts.md).
Consult owning contributors and obtain Tiến's approval for new public capabilities,
dependency edges or breaking changes; do not
access another module's repositories/entities. This checkout contains selected
banking-module contributions and tests; their presence does not establish complete
workflows. Check current source and verification evidence, and do not add empty
module scaffolding.

## Security and deployment limits

All published services bind to loopback: backend 8080, database 15432 by default,
Swagger UI 8081 and Grafana 3300. Frontend host port 3000 is reserved. Other telemetry
APIs are internal Docker endpoints. This is trusted local/demo infrastructure.
**A read-only Docker socket mount does not make the Docker API read-only.**
Public deployment requires reviewed authentication/authorization, TLS, telemetry
access controls, secret/storage management and external alert delivery. Do not commit
local environment files or expose raw requests, credentials or financial data in logs.
Application telemetry does not replace durable ledger/audit records; privacy tests
do not make arbitrary future log messages safe.
