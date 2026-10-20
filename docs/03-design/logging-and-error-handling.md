# Logging and HTTP error handling — Step 4D.1

Implemented technical foundation for Java 21 / Spring Boot 4.1.1. The generic HTTP
codes below are technical decisions for this step, not approval of new banking
behavior or implementation of the proposed financial endpoints in [API design](17-api-contracts.md).

## Clean business code, centralized observability

Domain models, domain services, application use cases, posting orchestration,
business validation and persistence business operations do not import logging APIs,
Logback or MDC. Propagate typed errors or explicit results; preserve the cause when
wrapping technical failures (`new SomeException(safeMessage, cause)`). No custom
Logger facade, logging service, business logging interface or method-wide AOP is needed.

Log at the terminal infrastructure boundary once. The MVC boundary logs unexpected
5xx failures once with the original Throwable. Lower layers propagate; they do not
log-and-rethrow. Future background/worker entry points need their own terminal boundary.
Retries should report the terminal failure rather than repeat the same stack at every layer.

## Formats and configuration

Use the existing Boot-managed SLF4J 2 / Logback dependencies. No encoder dependency
is added. Boot's ECS encoder writes production JSON. `logback-spring.xml` only
registers the module/trace/safe-exception converters and selects one stdout appender.

| Setting | Behavior |
|---|---|
| Default profile | Pretty console, UTC timestamps, colored levels/module labels when ANSI is supported |
| `SPRING_PROFILES_ACTIVE=prod` | ECS JSON stdout; ANSI disabled; banner disabled in both modes |
| `DEPLOYMENT_VERSION` | Release/build identifier supplied by deployment metadata; explicit fallback `dev` |
| `APP_ENV` | Environment metadata; default `local` for pretty mode, `production` for `prod` |
| `SPRING_OUTPUT_ANSI_ENABLED=always` / `never` | Explicit local ANSI preference |
| `LOGGING_LEVEL_ROOT` | Default INFO; change deliberately, without enabling sensitive framework diagnostics |

Supply `DEPLOYMENT_VERSION` and `APP_ENV` to the container/deployed process, not to
business code. A production profile alone does not manufacture a release version.
Both formatters read deployment metadata directly rather than relying on a JAR's artifact version.
No log collector or external service is required for startup.

The local pattern is UTC time, level, `[module]`, `service/version@environment`,
logger, trace context or `untraced`, and message. Forcing ANSI off keeps the same
readable fields. The application name remains `finfast-backend`; modules are labels
inside this one deployable, not separate services.

Production has one JSON event per stdout line, with embedded stack newlines escaped.
There is no second file sink or pretty sink to ingest twice. View JSON interactively
through an external renderer if desired; do not mix rendered text into ingestion.
JVM/process stderr diagnostics are separate from application JSON stdout.

## Logging contract

| Logical field | JSON representation / rule |
|---|---|
| UTC timestamp | `@timestamp`, ISO-8601 with UTC offset |
| Severity / logger | Boot ECS nested `log.level` / `log.logger` |
| Service / version / environment | Boot ECS nested `service.name` (`finfast-backend`), `service.version`, `service.environment` |
| Human message | `message`; describe the event without raw request/response data |
| Module | `module`, derived from explicit logger package boundaries |
| Trace availability | Literal root key `trace.state`: `untraced` or `context_present` |
| Correlation | Optional literal root keys `trace.id`, `span.id`, `request.id` |
| Event identity/result | Optional literal root keys `event.action`, `event.outcome` |
| Stable error code | Optional literal root key `error.code` |
| Technical diagnosis | Nested `error.type`, `error.stack_trace`; exception messages excluded |

The dotted correlation/event keys are literal JSON keys, using ECS dotted notation;
quote the entire key when extracting it. Boot's standard metadata/error objects remain
nested. This avoids adding a duplicate `error` object for `error.code`. The future
Alloy/Loki parser must follow this layout consistently, preserve full trace IDs and
avoid using request/trace IDs or financial values as stream labels.

Field ownership is explicit: Boot owns standard nested metadata and `error.type` /
`error.stack_trace`; `LoggingJsonCustomizer` owns the literal dotted keys above and
`module`. Boot normally exports all MDC and SLF4J fluent pairs automatically; FinFast
disables that path with `logging.structured.json.context.include=false`. The customizer
exports only allowlisted context and event keys, once each. Repeated fluent keys use
the first String value; blank optional values are omitted. Do not enable automatic
context export alongside this customizer: it would bypass allowlisting and could
collide with canonical fields. These are formatting extensions, not a business API.

`TraceLabelConverter` supplies the same trace mapping to pretty output and JSON.
Trusted Micrometer MDC keys `traceId` / `spanId` map to the canonical literal JSON
keys `trace.id` / `span.id`. Legacy dotted MDC pairs remain supported. If either
Micrometer key exists, that pair takes precedence over both legacy keys; never combine
IDs from different naming conventions. Both IDs must be nonblank, otherwise the event
is `untraced` and neither ID is exported. Nonblank IDs are preserved in full, without
trimming, normalization or synthetic replacements. `request.id` remains an independent,
optional trusted MDC key. Fluent event keys are `event.action`, `event.outcome`, `error.code`.

No tracing instrumentation, request-ID generator, correlation filter or inbound-header
trust is implemented. Startup/background events without trusted context are `untraced`
and omit IDs. `context_present` only reports supplied trusted MDC context, not proof of
an active/exported span verified by this formatter. Step 4D.2 must connect real
instrumentation through Micrometer's MDC convention and handle scope cleanup; application code
must not fabricate context or copy arbitrary client headers into it.

Logger names use the emitting infrastructure class's full package. Exact
`com.finfast.<module>.` boundaries map identity, customer, accounts, transfers, ledger,
idempotency, administration, history and bootstrap labels. Common infrastructure uses
`platform`, the entry point uses `startup`, and third-party loggers use `framework`.
No class-name substring guessing or business Logger is required to provide labels.

Currently emitted failure identity: `event.action=http.request.failed`,
`event.outcome=failure`, with an HTTP technical `error.code`. No synthetic banking
events are emitted. Future approved adapter events should use stable identifiers
such as `transfer.completed`; human messages are not event identifiers.

## Severity and error taxonomy

| Category | Ownership and policy |
|---|---|
| Lifecycle / operations | INFO for useful boundary events; DEBUG only for safe, deliberate diagnostics |
| Expected business failure | Future module-owned typed error/result, independent of HTTP and logging; INFO/WARN when useful, not an automatic ERROR |
| Input/protocol failure | Framework 4xx semantics; privacy-safe response, no custom ERROR report |
| Security failure | Future Spring Security entry point/access-denied handling; not every failure reaches MVC advice |
| Technical failure | Terminal ERROR, original cause preserved; HTTP 500 or infrastructure 503, no final financial outcome assertion |

Before introducing a module's expected failure, add its endpoint-specific mapping;
do not route known business rejections through the unexpected-error fallback. Do not
add a hierarchy of unused common banking exceptions. Pure domain errors carry no HTTP
status. Only a real financial command can return durable `REJECTED` after PostgreSQL
commit; this boundary never finalizes outcomes, persists rejections or infers that funds
did not move. Clients retain the original key and follow reviewed recovery semantics
after uncertain financial results. Logs do not replace ledger, outcomes or durable audits.

## HTTP error boundary

`GlobalExceptionHandler` extends Spring MVC's `ResponseEntityExceptionHandler`,
preserving framework status and headers while replacing detailed bodies with the
existing OpenAPI `ErrorResponse` shape: `code` and `message` only. No global success
wrapper or generic financial rejection mapping is introduced.

| Failure | Response |
|---|---|
| Malformed/missing body, validation, missing parameter, type mismatch | 400 `INVALID_REQUEST` |
| Missing resource / explicit framework not-found status | 404 `NOT_FOUND` |
| Unsupported method | 405 `METHOD_NOT_ALLOWED`, preserving `Allow` |
| No acceptable representation | 406 with negotiation headers and no body; avoid another serialization failure |
| Oversized upload / unsupported request media type | 413 `PAYLOAD_TOO_LARGE` / 415 `UNSUPPORTED_MEDIA_TYPE` |
| Database resource failure, query timeout, transaction creation failure | 503 `SERVICE_UNAVAILABLE` |
| Unexpected exception / framework server error | Original server status, safe `INTERNAL_ERROR` |
| Other framework client status | Original status and headers, safe `HTTP_<status>` code |

Bodies contain no exception type, SQL, path, stack, rejected input or cause message.
The boundary uses MVC's configured `ContentNegotiationManager`. If a different error
occurs but the client cannot accept JSON, preserve its original status and headers
with an empty body instead of triggering a second error during serialization. For
example, an unexpected failure with `Accept: application/xml` remains 500, logged once.
They omit `outcome`; neither `REJECTED`, `PROCESSING` nor `UNKNOWN` is inferred.
Already-committed responses and unusable async responses retain framework behavior.
Actuator health retains its own status/body. Failures outside MVC, including future
security filters, need their appropriate boundary rather than assumptions about advice.

## Sensitive data and diagnostics

Never log plaintext/hash passwords, cookies, session/CSRF tokens, Authorization headers,
database credentials, complete Account Numbers, raw idempotency keys, raw banking
bodies or responses. No blanket request/header/body logger is installed. Do not enable
Spring request-detail, SQL/bind-value DEBUG/TRACE logging in production; deployment
environment variables such as `DEBUG` can change framework logging levels.

The boundary uses constant messages/codes and retains the original exception object.
Because JDBC/library exception messages may contain SQL or sensitive values, both
renderers omit all Throwable messages while keeping exception types, full local frames,
causes and suppressed exceptions. This uses Boot's stack printer formatter, not regex
masking. Keep diagnostic stdout access-controlled; safe rendering is not authorization
or a guarantee that arbitrary future log messages are safe.

The two integrations are separate: the pretty pattern explicitly invokes `%safeEx`,
whose converter extends Logback's `ThrowableProxyConverter`, so Logback does not append
another default exception converter. ECS uses Boot's supported
`logging.structured.json.stacktrace.printer=...SafeThrowablePrinter` extension and excludes
`error.message`; a conversion-rule declaration alone would not configure it. Both paths
delegate to `StandardStackTracePrinter` with a type-only Throwable formatter, including
causes and suppressed exceptions. Local frames are retained without an arbitrary size
cutoff; Boot handles circular references. Remote serialized Throwable proxies are not
used and conservatively render the type only. This does not sanitize secrets deliberately
interpolated into a log's human message; use constant, safe boundary messages.

## Verification and later work

With Java 21 and Docker: `cd backend && ./mvnw test`, then `./mvnw package`.
`LoggingContractTests` verifies actual YAML configuration, ECS event fields/metadata,
severity, ANSI on/off, package labels, context allowlisting and safe cause rendering.
JSON assertions enable Jackson's strict duplicate-key detection. `LoggingOutputTests`
launches test-only child JVMs using the real `logback-spring.xml` in local and prod
modes, without a datasource or server. It injects a synthetic canary into root, cause
and suppressed exception messages, verifies their absence from actual encoder output,
checks retained types/frames, parses every production stdout record strictly, and
verifies Micrometer correlation and MDC cleanup. No diagnostic endpoint ships in the JAR.
`GlobalExceptionHandlerTests` uses test-only controllers for privacy, one-error-one-log,
original cause preservation, 400/404/405/406/415 behavior, 503 handling, required headers,
negotiation of error bodies, committed and unusable async responses. Existing health
and isolated PostgreSQL tests remain applicable.

Step 4D.2 tracing, exporters, Alloy, Loki, Tempo, Grafana, storage, dashboards and alerts
are not installed or operational. No financial module, audit publisher, migration or
observability Compose service is implemented here.

References: [Boot 4.1 logging](https://docs.spring.io/spring-boot/reference/features/logging.html),
[Boot MDC correlation conventions](https://docs.spring.io/spring-boot/reference/actuator/tracing.html#actuator.micrometer-tracing.logging),
[Spring MVC exception facilities](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
