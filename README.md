# FinFast

FinFast is a banking simulation project designed as one deployable modular
monolith, with DDD-inspired business modules and pragmatic Hexagonal Architecture.
The Database Foundation includes PostgreSQL/Flyway and eight banking tables with no
automatic seed data. Observability Steps 4D.1–4D.5 provide safe logging, centralized
HTTP errors, real HTTP tracing, optional log/trace storage, metrics, dashboards and
local alerts. Identity/Customer APIs, authentication, transfers, Ledger posting,
Admin operations, controlled GENESIS bootstrap and the frontend are not implemented.

See the [documentation index](docs/README.md) and
[system architecture](docs/03-design/15-system-architecture.md) for the agreed
direction and the proposals awaiting review.

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
logging, real HTTP tracing and metrics. Database tests use isolated PostgreSQL 16.15
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

## Swagger UI

Run from the repository root:

```bash
make docs-up
# Browse http://localhost:8081
make docs-down
```

This separate design-contract viewer requires no backend or database. Request
execution is disabled; these documented banking APIs are not implemented endpoints.
See [documentation tooling](docs/README.md#browse-the-api-locally-doc-01).

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
| `compose.yaml`, `compose.docs.yaml` | Backend/PostgreSQL and separate Swagger UI stacks |
| `compose.observed.yaml`, `compose.observability.yaml` | Opt-in backend overlay and six telemetry services |
| `observability/` | Stack runbook, component configs, Grafana provisioning and verification helpers |
| `docs/` | Approved discovery, draft/proposed designs, contracts and verification guidance |

Start at the [documentation index](docs/README.md). The
[logging/error/tracing contract](docs/03-design/logging-and-error-handling.md) defines
**Clean Business Code, Centralized Observability**; the
[architecture review](observability/architecture-review.md) records dated evidence
and deployment gates. Implemented schema decisions are explained in the
[migration README](backend/src/main/resources/db/migration/README.md); other design
proposals retain their existing approval status.

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
