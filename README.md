# FinFast

FinFast is a banking simulation project designed as one deployable modular
monolith, with DDD-inspired business modules and pragmatic Hexagonal Architecture.
The foundation includes PostgreSQL/Flyway, eight empty banking tables, an Actuator
health check, safe logging and HTTP tracing. Authentication, business operations and
the frontend are not implemented. The optional Grafana stack is described below.

See the [documentation index](docs/README.md) and
[system architecture](docs/03-design/15-system-architecture.md) for the agreed
direction and the proposals awaiting review.

## Backend baseline

| Technology | Pinned version |
|---|---|
| Java | 21 LTS (container: Eclipse Temurin 21.0.12.1+1) |
| Spring Boot | 4.1.1, using its parent POM for dependency management |
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

## Prerequisites

- Git to clone the repository.
- Docker with a running engine and Docker Compose v2 or later. Docker Desktop
  includes both. No local Java, Maven or PostgreSQL installation is required for
  the Docker workflow.
- Network access to Docker Hub and Maven Central for initial image/dependency
  downloads, and available local ports 8080 and 15432 (database port configurable).
- `curl` or an HTTP client to check health.

## Run with Docker (primary workflow)

From the repository root:

```bash
cp .env.example .env
docker compose up --build -d
```

Copy the example once when setting up a clone, then edit your local `.env` as
needed. `.env` is ignored by Git. **These credentials are for local development
only and must never be reused for a public deployment.**

The build uses the repository's Maven Wrapper, runs the database-independent tests
with Java 21 and copies only the executable JAR into the Java 21 JRE runtime
image. The application runs as a non-root user. PostgreSQL must pass its
`pg_isready` healthcheck before Compose starts the backend. The backend connects
to `jdbc:postgresql://postgres:5432/finfast` using the Compose service hostname and
the database name and credentials from `.env`.

Compose passes `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` to Spring Boot through its standard environment
binding. The application port is published at `127.0.0.1:8080`; PostgreSQL is
published at `127.0.0.1:15432` by default. Set `POSTGRES_PORT` in `.env` to change
only the host database port; containers always use port 5432. Communication
between services uses Compose's default private network.

After startup completes, check health:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

If the application is still starting, retry the health check. Actuator includes
the standard database connectivity check: a database outage produces health
`DOWN` and HTTP 503 while the backend is running. Connection failure during
Flyway startup prevents a successful backend startup. Public health responses
show aggregate status without database details or credentials.

Check service status and startup logs:

```bash
docker compose ps
docker compose logs -f backend postgres
```

Stop and remove this Compose application's containers and network:

```bash
docker compose down
```

This preserves the named `postgres_data` volume (normally
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

The image can also be built independently from `backend/`:

```bash
cd backend
docker build -t finfast-backend:local .
```

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

The two Step 2 tests still check the application context and health response
through MockMvc with database auto-configuration excluded only for those tests.
The PostgreSQL smoke test starts a separate pinned PostgreSQL 16.15 container,
checks `SELECT 1`, validates V1/V2 and confirms the banking tables start empty.
Schema, rollback, concurrency, logging and real HTTP tracing tests extend this smoke
test. Spring Boot service connections supply both datasource and Flyway
connection details from this test container, so it does not use the persistent
Compose database or a manually installed PostgreSQL server. The test database
and container are disposable and managed by Testcontainers.

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

Documentation preview and its existing validation workflow remain separate; see
[`docs/README.md`](docs/README.md) for `compose.docs.yaml` usage.

## Optional Grafana observability

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

Login with those local credentials. The ignored environment file is never overwritten
by setup. To override Grafana's host port, set `GRAFANA_PORT` in that file, or run
`GRAFANA_PORT=3330 make observability-up` and browse http://localhost:3330.

The backend uses a logging-only `observability` profile and exports traces to Alloy
over a shared Docker network. PostgreSQL stays on the application network. Loki and
Tempo datasources are provisioned automatically; use Grafana Explore to query
`{service_name="finfast-backend"}` and click **View trace in Tempo** on an expanded
log containing a Trace ID. This uses real tracing, not generated access-log events;
successful requests need not emit an application log.

Return to ordinary backend mode and stop telemetry safely:

```bash
make up
make observability-down
```

`observability-down` stops telemetry containers while preserving their volumes and
shared network. It never stops PostgreSQL. Do not use volume-deleting commands for
routine shutdown. **Alloy has privileged Docker socket access even though the socket
mount is read-only; this configuration is for trusted local development.**

See [observability setup and verification](observability/README.md) for security,
retention, log/trace queries, networking, resource use and repeatable end-to-end checks.
