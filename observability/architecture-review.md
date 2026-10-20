# Observability architecture review

This is a local/demo foundation, not an externally deployable production topology.
Use [setup and log/trace queries](README.md), [metrics and alerts](metrics-and-alerts.md)
and the [logging/error/tracing contract](../docs/03-design/logging-and-error-handling.md)
for maintained commands and configuration. Banking posting services are not implemented.

## Boundaries

| Signal | Single intended path | Meaning and limitations |
|---|---|---|
| Logs | Logback stdout → Docker → Alloy → Loki | Diagnostic events; four stable labels, correlation IDs stay in content |
| Traces | Boot HTTP observation → Micrometer/one OTel SDK → batched OTLP → Alloy → Tempo | Sampled request execution; a trace ID does not emit a log or guarantee a stored trace |
| Metrics | One Prometheus registry → private scrape endpoint → Prometheus → Grafana | Aggregates; HTTP histogram estimates and scrape availability, not financial correctness |
| Alerts | Prometheus rules → Alertmanager `local-only` | Grouped/deduplicated state; no email/chat/person notification is configured |

Only the observed backend joins the telemetry network. PostgreSQL stays on the
application network. Normal mode needs no telemetry network or collector and exposes
only aggregate health. Grafana uses loopback **http://localhost:3300**, with configurable
`GRAFANA_PORT`; frontend port 3000 is reserved. Other telemetry APIs are not host-published.

Domain/application modules must remain independent of logging/tracing/metrics APIs.
Present production Java consists of the entry point and common technical infrastructure;
there are no implemented banking use cases to instrument or claim to have verified.
Logs, traces, metrics and notifications never substitute for committed ledger/audit facts.
An HTTP 500/503 does not establish rollback or a durable `REJECTED` outcome.

## Review corrections

- Error negotiation now honors an explicit JSON `q=0` over a less-specific positive
  wildcard. Tests cover exclusions, specific positive overrides and vendor JSON while
  preserving original error status/headers. There is no API schema change.
- The root test-image Docker context now excludes nested environment files, AWS/SSH
  directories, keystores and credential/settings files consistently with the backend
  context. A disposable COPY probe with synthetic credentials failed before the change
  and passed afterward; ordinary source remained included.
- Regression coverage now exercises a real container ERROR dispatch, checks the sole
  active non-composite metrics registry, and tests stale target disappearance and HTTP
  counter resets. No alert thresholds, production routes or migration SQL were changed.

## Targeted dependency security patches

The 2026-10-20 runtime inventory found Boot 4.1.1 managing Tomcat 11.0.24 and Jackson
3.1.5. Public version-based advisories matched Tomcat and Jackson core/databind.
The POM overrides only Boot's `tomcat.version` to **11.0.26** and
`jackson-bom.version` to **3.1.7**, preserving their existing release lines and coherent
dependency management. Remove the overrides once a reviewed Boot BOM includes equivalent
fixes; do not upgrade the framework or unrelated dependencies implicitly.

[Apache's advisory list](https://tomcat.apache.org/security-11.html) documents fixes
through 11.0.26, including HTTP/2 header mix-up CVE-2026-86350. The current HTTP/1.1
setup does not enable HTTP/2, AJP or container FORM/DIGEST authentication; a vulnerable
version match is not evidence of an exploited FinFast path. Jackson fixes include
[numeric-input ReDoS](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89)
and [forward-reference resolution DoS](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24).
The corresponding polymorphic/identity-based business deserializers are not implemented.
Release artifacts were verified on Maven Central; full HTTP/tracing/database regressions
and Docker builds exercise compatibility. This is not a complete container OS/SBOM scan
or a guarantee that no vulnerabilities exist.

A public OSV version lookup matched three coordinates in the original 106-package
runtime inventory. Repeating that lookup after the patches returned no matches for
the 106 resolved coordinates. Advisory coverage and reachability are separate concerns;
this point-in-time lookup is not a permanent security gate.

## Verified scope and evidence

The 2026-10-20 review uses new Compose project/network/volume names, temporary local
credentials and a PostgreSQL database comment as a persistence marker. It never connects
to or resets a developer database. Production migrations and the banking API contract
remain unchanged. Fault requests use the separate test-only image; the executable
production JAR contains no diagnostic endpoints.

Run the Java 21 checks with `cd backend && ./mvnw test && ./mvnw package`.
Both commands passed **330 tests, 0 failures/errors/skips**, including six added cases
over the 324-test baseline. The Docker production build also passed its 95
database-independent tests; database tests ran separately against Testcontainers.
The suite covers safe actual pretty/ECS encoders, strict duplicate-key parsing,
500/503 and framework client status/headers, committed/async responses, W3C propagation,
sampling, correlation cleanup, isolated PostgreSQL schema/rollback/concurrency and
bounded metric labels. The rule suite has **18 scenarios**; configuration and live
ingestion commands are in the linked runbooks. Browser link navigation is a separate
check from verifying the provisioned regex or datasource API.

Live verification also passed:

- Fresh ordinary/observed Compose startup with automatic datasource/dashboard provisioning;
  health `UP`, database `SELECT 1`, metrics 404 in ordinary mode and 200 when observed.
- Existing `verify_stack.py` and `verify_metrics.py`, before and after fault recovery:
  exact full-width IDs/timestamps, strict JSON, one event per tested boundary, private
  canaries absent, real counters/histograms/JVM/Hikari data and all 19 dashboard queries.
- Actual Chromium dashboard rendering (34 nonempty query frames, no query errors) and
  clicking the Loki derived link to the real Tempo server span.
- Three synthetic decoy containers excluded for wrong project/service/missing opt-in
  across five Docker discovery intervals; unrelated containers are not log sources.
- The unchanged 2m availability alert fired, reached one Alertmanager fingerprint/group
  at `local-only`, and resolved after restore (172s total drill). No external delivery.
- Each of six telemetry components stopped/restored independently; HTTP success/error
  semantics, real in-process tracing, database health and persistence remained intact.
- Restarting isolated PostgreSQL preserved its database comment and Flyway V1/V2 history;
  all eight banking tables remained empty. No financial posting behavior is claimed.
- Pinned-image configuration validators passed for Alloy, Loki, Tempo, Prometheus rules
  and Alertmanager; 18 rule scenarios passed. OpenAPI validation and documentation checks
  passed (21 Markdown files, 166 local links, 88 OpenAPI references).

Handled MVC 500 spans can retain OTel status `UNSET` with string attribute `status="500"`.
Use Prometheus HTTP status labels and Tempo `{span.status =~ "5[0-9][0-9]"}` rather
than relying only on OTel error status. No global span-status rewrite is installed.

## Accepted local limitations

- Alloy's read-only socket mount still grants powerful Docker API access. Three discovery
  filters select project, backend service and explicit ECS opt-in; this is collection
  selection, not an API privilege boundary. No Docker TCP listener or privileged service
  is added. Host log-file tailing is an alternative requiring host-specific configuration.
- Shared-network internal APIs are unauthenticated; Docker isolation is not zero-trust.
  Grafana requires locally chosen credentials. Never reuse development secrets publicly.
- Safe throwable rendering removes exception messages while retaining types, frames,
  causes and suppressed relationships. It does not sanitize arbitrary future log messages
  or data retained inside the SDK before the export privacy filter. MVC advice does not
  cover security filters/background jobs. Arbitrary executor MDC propagation is unsupported.
- No exactly-once telemetry delivery is promised. Outages, finite queues, stdout rotation,
  restarts and sampling can lose data; recovery can replay records. Missed metrics are
  not backfilled. Backend/database availability must not depend on telemetry readiness.
- Missing-target alerts require a running Prometheus. Leaving it running while deliberately
  stopping the backend or returning to ordinary mode intentionally causes an unavailable
  target alert. Stopping the optional stack stops alert evaluation entirely.
- Scrape `up`, JVM metrics and Hikari counts do not prove PostgreSQL accepts queries.
  Actuator retains its standard database health check; dedicated DB monitoring is absent.
- Loki/Tempo/Prometheus/Alertmanager retention is configured for 72h. Prometheus also
  limits retained blocks to about 1GB; head/WAL need extra disk. Compaction/deletion is
  asynchronous, not a hard volume quota. A full 72h expiry cycle has not been tested.

## Resource observation

Linux x86_64, 2026-10-20, after service restarts, light fixture HTTP traffic, dashboard
and log/trace queries. `docker stats --no-stream` measured the following snapshot:

| Component | RAM (MiB) |
|---|---:|
| Grafana | 289.70 |
| Alloy | 42.80 |
| Loki | 63.08 |
| Tempo | 49.43 |
| Prometheus | 53.37 |
| Alertmanager | 15.08 |
| **Telemetry total** | **513.46** |
| Backend test fixture with real PostgreSQL pool | 278.60 |
| PostgreSQL | 34.38 |

Read-only `du` of the seven isolated volumes measured about **117.6 MiB** allocated
telemetry data and **46.3 MiB** PostgreSQL data. Images, container layers and daemon
stdout storage are additional. Recently restarted caches explain why this snapshot
is lower than the earlier 1,051 MiB observation; it is not a controlled optimization
comparison, peak measurement, capacity test or memory limit. Allow several GiB of
headroom for queries, the backend and Docker/Maven builds. Telemetry remains optional.

## Deployment gates

| Classification | Required action |
|---|---|
| Current local/demo merge blockers | Failing regressions, leaked secrets, unsafe public binds, broken fresh setup or telemetry affecting HTTP/database behavior must be resolved before merge |
| Before external deployment | Review application authentication/authorization, TLS and management/telemetry access controls; replace privileged socket collection, inject managed secrets, scan dependencies/images and set patch ownership |
| Before external deployment | Define sensitive-data/access/retention policy, log integrity/access auditing, durable storage and tested backup/restore; configure approved external alert delivery and owners |
| Before external deployment | Validate sampling, alert thresholds, database-specific monitoring, capacity/disk budgets and recovery procedures on the actual deployment |
| Future scale/operations | Justify HA, object storage, long-term retention, disaster-recovery objectives and collector backpressure/load tests against project requirements |

These gates do not authorize implementation of production infrastructure, business
services or changes to approved financial behavior. The local review does not prove
future transfer atomicity, account-lock ordering or global-counter performance.
