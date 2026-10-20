# Logging, HTTP errors and tracing — Steps 4D.1–4D.2

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
| `SPRING_PROFILES_ACTIVE=observability` | ECS stdout and Prometheus metrics opt-in; preserves local sampling/datasource defaults |
| `DEPLOYMENT_VERSION` | Release/build identifier supplied by deployment metadata; explicit fallback `dev` |
| `APP_ENV` | Environment metadata; default `local` for pretty mode, `production` for `prod` |
| `SPRING_OUTPUT_ANSI_ENABLED=always` / `never` | Explicit local ANSI preference |
| `LOGGING_LEVEL_ROOT` | Default INFO; change deliberately, without enabling sensitive framework diagnostics |

Supply `DEPLOYMENT_VERSION` and `APP_ENV` to the container/deployed process, not to
business code. A production profile alone does not manufacture a release version.
Both formatters read deployment metadata directly rather than relying on a JAR's artifact version.
No log collector or external service is required for startup.

The local pattern is UTC time, level, `[module]`, `service/version@environment`,
logger, trace context or `untraced`, request ID or `none`, and message. Forcing ANSI off keeps the same
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

Boot-managed Micrometer/OpenTelemetry instrumentation now supplies real HTTP context
through Micrometer's MDC convention. Startup/background events outside a span remain
`untraced` and omit trace/span IDs. `context_present` means correlation context exists;
the formatter itself does not verify a span or guarantee that a backend stores it.
Application code must not fabricate context or copy arbitrary client headers into MDC.

Logger names use the emitting infrastructure class's full package. Exact
`com.finfast.<module>.` boundaries map identity, customer, accounts, transfers, ledger,
idempotency, administration, history and bootstrap labels. Common infrastructure uses
`platform`, the entry point uses `startup`, and third-party loggers use `framework`.
No class-name substring guessing or business Logger is required to provide labels.

Currently emitted failure identity: `event.action=http.request.failed`,
`event.outcome=failure`, with an HTTP technical `error.code`. No synthetic banking
events are emitted. Future approved adapter events should use stable identifiers
such as `transfer.completed`; human messages are not event identifiers.

## HTTP tracing and request correlation

One Boot-managed OpenTelemetry SDK bridges Micrometer Observation to tracing.
Spring's `ServerHttpObservationFilter` owns each HTTP server span and its lifecycle;
there is no custom server span, Java agent or method-wide AOP. Instrumentation creates
spans, not automatic application log lines. Normal successful/health requests need not
emit an application event. Domain/application code remains independent of these APIs.

| Identifier | Purpose / ownership |
|---|---|
| Trace ID | 32 hexadecimal characters identifying a distributed trace; supplied by OTel or a valid remote W3C parent |
| Span ID | 16 hexadecimal characters identifying the actual server span; remote parent span ID is not reused |
| Request ID | Independent, unpredictable server-generated UUID v4 per inbound request; response header `X-Request-ID` and MDC/JSON `request.id` |

Consume and produce only W3C `traceparent` / `tracestate`. Valid parents continue the
trace; invalid/absent parents create a new root. Propagation is correlation, not
authentication or authorization. Incoming `X-Request-ID` is ignored and never logged;
arbitrary trace headers are never copied into MDC. Baggage propagation is disabled.
No trace/span IDs are added to production response bodies or headers.

`RequestCorrelationFilter` runs at `HIGHEST_PRECEDENCE`, before Boot's observation
filter (`HIGHEST_PRECEDENCE + 1`). It stores its generated ID in a private request
attribute and binds MDC for REQUEST, ERROR and ASYNC dispatches. Redispatch reuses the
same ID, including nested errors. Each dispatch's `finally` restores an enclosing MDC
scope or removes the ID, leaving pooled threads clean. It emits no request log.

This supports servlet redispatch, not arbitrary executor/`@Async` propagation.
There is no automatic request-MDC propagation to worker tasks. Before introducing
asynchronous use cases, review task decorators/context propagation and lifecycle
semantics explicitly; do not assume thread locals travel with work.

Future outbound HTTP integrations must use Boot's injected `RestClient.Builder`,
`RestTemplateBuilder`, or (if introduced) `WebClient.Builder` to retain framework
instrumentation and propagation. Review client attributes for privacy before use;
manual client construction can lose tracing. No artificial service calls are added.

## Sampling, resources and optional OTLP export

Exact properties are in `application.yml` / `application-prod.yml`, using the Boot
4.1.1 starter and dependency management. `service.name=finfast-backend`,
`service.version=${DEPLOYMENT_VERSION:dev}` and
`deployment.environment.name=${APP_ENV:local}` identify OTel resources. The prod
environment fallback is `production`, matching ECS. Deployment supplies the version;
neither the logging nor tracing subsystem invents a production release number.

| Environment variable | Default / behavior |
|---|---|
| `DEPLOYMENT_VERSION` | `dev`; shared release metadata for logs and spans |
| `APP_ENV` | `local`, or `production` in prod; shared environment metadata |
| `TRACING_SAMPLING_PROBABILITY` | Local `1.0`, prod `0.1`; configurable 0–1 |
| `OTLP_TRACES_ENABLED` | `false`; opt in to remote trace export |
| `OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces`; full OTLP/HTTP endpoint |

The sampler is `parent-based-trace-id-ratio`: valid parents retain their sampling
decision; the configured probability applies to new roots. A valid trace/span ID may
exist on an unsampled request. Correlated logs therefore need not have a stored trace.
Sampling changes telemetry volume and never determines financial outcomes.

Enable export in the backend process's environment, for example:

```bash
OTLP_TRACES_ENABLED=true \
OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces \
./mvnw spring-boot:run
```

Run this command from `backend/` with the native datasource environment described in
the root README. Docker Compose does not automatically forward arbitrary `.env` keys:
these settings must reach the backend container environment when export is later wired
into infrastructure. Inside a container, `localhost` refers to that container; use the
future collector's reachable network hostname. No collector is created here.

The opt-in property is `management.tracing.export.otlp.enabled`, separate from the SDK.
`management.opentelemetry.tracing.export.otlp.transport=http` uses connect timeout 1s
and total request timeout 2s. Boot's standard `BatchSpanProcessor` exports asynchronously
with a bounded queue; its exporter wait is 3s. There is no application retry loop.
Collector failures may lose telemetry but do not alter request/commit results or
Actuator health. Export disabled means no OTLP trace connection attempts, with real
in-process context still available. OTLP metrics and log export are explicitly disabled
because the starter also brings a meter registry; application logs remain stdout only.

## Trace privacy

The default HTTP convention includes the raw path in `http.url`. FinFast replaces
only high-cardinality HTTP tags with an empty set, keeping framework method/status,
exception type and normalized route templates. Paths containing Account Numbers and
queries are not exported. No headers, bodies, customer IDs, SQL or custom baggage are
added as tags. Do not use correlation IDs as metric dimensions or Loki stream labels.

Micrometer's OTel bridge can capture raw exception messages, stack events and status
descriptions. `safeTraceDiagnostics` uses its supported export filter to remove events
and status descriptions before any configured exporter receives the span, preserving
status code, IDs, parent, resource and safe tags. The SDK may temporarily hold original
exception data internally; this is an export boundary, not global in-memory masking.
Safe types/frames/causes remain available through the existing terminal log boundary.
All span events are omitted in this phase; future event allowlisting needs review.
Never deliberately put secrets into span names, attributes or resource configuration.
Handled MVC errors retain their HTTP status/outcome tags; an HTTP 500 does not by
itself guarantee an OTel `ERROR` status in this instrumentation. The export filter
preserves the status supplied by the SDK rather than inventing financial outcomes.

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

`HttpTracingIntegrationTests` uses `@SpringBootTest(RANDOM_PORT)` and Boot 4.1.1's
`@AutoConfigureTracing` from `spring-boot-micrometer-tracing-test`, with an SDK in-memory
exporter and remote OTLP explicitly disabled. It compares Micrometer, OTel and MDC
context over real HTTP, checks completed server spans, W3C parents/sampling, privacy,
request overlap, async completion, safe errors and collector-independent health.
Sliced tests with `@AutoConfigureTracing` alone provide a no-op tracer and are not
evidence for this behavior. `RequestCorrelationFilterTests` checks cleanup, nested
errors and redispatch. `TracingConfigurationTests` binds the real YAML to Boot's
properties. `HttpTracingOutputTests` runs full HTTP child JVMs in local/prod modes,
strictly parses ECS and verifies opt-in OTLP against a loopback responder returning
503, including absence of the exception canary in actual protobuf request payloads.

Step 4D.3 adds an optional local Alloy → Loki/Tempo → Grafana stack; see the
[setup and end-to-end verification guide](../../observability/README.md). Grafana is
localhost:3300, leaving host port 3000 reserved for the frontend. The observed overlay
uses a shared Docker network and exports to Alloy; normal backend startup stays
collector-independent. Step 4D.4 adds optional Prometheus scraping, recording/alerting
rules, Alertmanager and a Grafana backend dashboard; see the
[metrics and alerting guide](../../observability/metrics-and-alerts.md). Only the
observability profile exposes the metrics endpoint; business code remains free of
meter/logging/tracing calls. No financial module, audit publisher or migration is
added. Logs/spans/metrics never replace durable Ledger Entries,
final Request Outcomes or mandatory Admin Audit Events.

References: [Boot 4.1 logging](https://docs.spring.io/spring-boot/reference/features/logging.html),
[Boot MDC correlation conventions](https://docs.spring.io/spring-boot/reference/actuator/tracing.html#actuator.micrometer-tracing.logging),
[Boot observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html),
[Boot tracing tests](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html#testing.spring-boot-applications.tracing),
[Spring MVC exception facilities](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
