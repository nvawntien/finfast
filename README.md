# FinFast

FinFast is a banking simulation project designed as one deployable modular
monolith, with DDD-inspired business modules and pragmatic Hexagonal Architecture.
This Step 2 foundation provides a Spring Boot backend and an Actuator health check.
PostgreSQL integration, authentication, business modules and a frontend are future
steps; none are implemented here.

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

Both Docker base images are pinned to verified manifest digests in
[`backend/Dockerfile`](backend/Dockerfile). Maven distribution and Wrapper JAR
SHA-256 checksums are pinned in the Wrapper properties.

## Prerequisites

- Git to clone the repository.
- Docker with a running engine and Docker Compose v2 or later. Docker Desktop
  includes both. No local Java or Maven installation is required for Docker.
- Network access to Docker Hub and Maven Central for initial image/dependency
  downloads, and an available local port 8080.
- `curl` or an HTTP client to check health.

## Run with Docker (primary workflow)

From the repository root:

```bash
docker compose up --build -d
```

The build uses the repository's Maven Wrapper, runs tests with Java 21 and copies
only the executable JAR into the Java 21 JRE runtime image. The application runs as
a non-root user and needs no database or environment secrets. The published port
is bound to the local machine at `127.0.0.1:8080`.

After startup completes, check health:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

If the application is still starting, retry the health check. View startup logs:

```bash
docker compose logs -f backend
```

Stop and remove this Compose application's containers and network:

```bash
docker compose down
```

The image can also be built independently from `backend/`:

```bash
cd backend
docker build -t finfast-backend:local .
```

## Run natively

Install a Java 21 JDK and point `JAVA_HOME` at it, then run:

```bash
cd backend
./mvnw --version
./mvnw spring-boot:run
```

The Wrapper downloads and uses Maven 3.9.11; a separate Maven installation is not
needed. On Windows, use `mvnw.cmd` in place of `./mvnw`.

Check the same health URL from another terminal. Press **Ctrl+C** in the running
terminal to stop the application. For a different native port, set `SERVER_PORT`
(for example, `SERVER_PORT=8082 ./mvnw spring-boot:run`) and adjust the health URL.
The default Compose workflow keeps the container and published port at 8080.

## Tests and executable JAR

With Java 21, from `backend/`:

```bash
./mvnw test
./mvnw package
java -jar target/finfast-backend.jar
```

The tests load the application context without a database and verify that
`/actuator/health` responds with HTTP 200 and `{"status":"UP"}` through MockMvc.
Packaging runs these tests again and produces `target/finfast-backend.jar`.
Press **Ctrl+C** to stop the JAR process.

Documentation preview and its existing validation workflow remain separate; see
[`docs/README.md`](docs/README.md) for `compose.docs.yaml` usage.
