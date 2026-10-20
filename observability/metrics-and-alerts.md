# Metrics, dashboard and operational alerts

Use the existing [setup workflow](README.md). Grafana stays at
**http://localhost:3300**; frontend host port 3000 is reserved and Swagger UI uses 8081.
`make observability-up` starts all six telemetry services. `make observed-up` enables
ECS logs, tracing and metrics; `make observability-health` checks all six readiness
endpoints. Return to ordinary mode with `make up`, then `make observability-down`.
No routine command removes PostgreSQL or telemetry volumes.

## One metrics pipeline

Prometheus pulls `http://backend:8080/actuator/prometheus` on the shared optional
network every 15s (5s timeout), evaluates rules every 15s and sends alerts to
`alertmanager:9093`. It also scrapes itself and Alertmanager. Alloy still handles
only logs/traces; OTLP metrics remain disabled. No exporter or monitoring process is
required for the backend to serve requests.

Prometheus 3.15.0 and Alertmanager 0.34.1 are digest-pinned in
[Compose](../compose.observability.yaml). Neither publishes host ports. Grafana
provisions `finfast-prometheus` at `http://prometheus:9090`; Loki/Tempo UIDs and
log-to-trace links remain unchanged. Internal unauthenticated APIs and Docker socket
access are trusted local development choices, not production network isolation.

Only the `observability` Spring profile enables the Prometheus registry and exposes
`health,prometheus`. Ordinary and `prod` profiles do not expose it. The observed
backend's existing localhost:8080 listener can access metrics too; public production
deployment needs reviewed network/access controls or a private management listener.
Do not expose arbitrary Actuator endpoints or publish Prometheus/Alertmanager APIs.

## Actual exported framework metrics

| Metric family | Meaning |
|---|---|
| `http_server_requests_seconds_count`, `_sum`, `_bucket` | Completed request count, total duration and cumulative latency buckets |
| `jvm_memory_used_bytes`, `jvm_memory_max_bytes` | Heap/non-heap usage and pool maximums; some maxima are undefined (`-1`) |
| `jvm_gc_pause_seconds_count`, `_sum` | GC events and pause time; series appear after a GC event |
| `jvm_threads_live_threads`, `process_cpu_usage`, `process_uptime_seconds` | Live threads, process CPU fraction and uptime |
| `hikaricp_connections_active`, `_idle`, `_max`, `_pending` | Pool occupancy, capacity and waiting requests |
| `hikaricp_connections_acquire_seconds_count`, `_sum` | Connection acquisition count/time |
| `hikaricp_connections_timeout_total` | Connection acquisition timeouts |
| `jdbc_connections_max`, `_min` | Generic JDBC pool bounds; active/idle generic JDBC series were not present in validation |

Common metric labels are `application="finfast-backend"` and configurable
`environment` from `APP_ENV`. HTTP labels use method, normalized route, status and
outcome; unknown resource paths collapse to a bounded framework label (`/**` or
`NOT_FOUND`), never the raw path/query. Pool names are controlled configuration.
Request/trace/span/account/customer/idempotency identifiers are not metric labels.
`DEPLOYMENT_VERSION` remains in logs and trace resources rather than becoming a
dimension on every metric. No financial values, bodies, credentials or headers are
recorded. There is no business-layer instrumentation.

**Scrape UP is not database UP.** Hikari counts also do not prove PostgreSQL accepts
queries. There is no PostgreSQL exporter, synthetic DB poller or database-up metric.
Use existing Actuator database health for diagnosis; dedicated DB monitoring is later work.

| Signal | What it establishes |
|---|---|
| Scrape failure (`up=0` or expected target absent) | Prometheus cannot collect metrics; the backend may still serve other HTTP endpoints |
| Backend HTTP failure (5xx) | A request failed; metrics scraping can remain UP and no financial result is implied |
| PostgreSQL health failure | While the backend runs, Actuator's database check fails and aggregate health returns DOWN/503 even if metrics remain scrapeable |
| Connection pool pressure | Active/max or pending acquisition indicates contention; it does not by itself prove DB unavailability |

## Dashboard and latency

Open **Dashboards → FinFast → FinFast — Backend Overview**, or
http://localhost:3300/d/finfast-backend-overview. The provisioned
[dashboard JSON](grafana/dashboards/backend-overview.json) contains the exact PromQL.
Its 19 panels in five sections cover scrape/uptime, HTTP status classes/rate/counts, latency,
JVM/CPU and Hikari occupancy/acquisition/timeouts. The environment selector filters
application metrics; the single configured scrape target is shown independently.
Missing series remain **No data**. Rates need at least two scrape samples; no traffic
can produce NaN/No data for ratios and latency. GC panels can be empty before the first GC.

HTTP histograms have 11 finite boundaries: 5, 10, 25, 50, 100, 250, 500ms and
1, 2, 5, 10s, plus `+Inf`. These explicit buckets bound series count. Quantiles are
interpolated estimates; observations beyond the last finite boundary cannot yield
precise tail estimates. No client-side quantiles or exemplar integration is added.
With few requests, p95/p99 can be unstable; dashboard quantiles are not a workload
benchmark or evidence that the proposed banking performance target has been met.

Examples in Grafana Explore, using the Prometheus datasource:

```promql
sum(rate(http_server_requests_seconds_count{job="finfast-backend",uri!~"/actuator.*"}[5m]))
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="finfast-backend",uri!~"/actuator.*"}[5m])))
hikaricp_connections_pending{job="finfast-backend"}
```

Recording rules aggregate across routes while preserving target/application/environment.
Actuator scrapes/health calls are excluded from traffic and latency rules. The error-rate
fallback produces zero only when the request-rate series exists; missing traffic does
not manufacture a healthy series. Heap utilization divides total heap usage by the sum
of positive heap maxima; inspect the JVM's actual pools before applying it to another GC.

## Alerts and routing

| Alert | Condition | Sustained duration |
|---|---|---|
| `BackendUnavailable` | Failed scrape or missing expected target | 2m, critical |
| `ElevatedHttp5xxRate` | 5xx ratio >5%, at least 1 request/s over 5m | 3m, warning |
| `HighHttpLatency` | p95 >500ms, at least 1 request/s over 5m | 5m, warning |
| `HikariPoolNearExhaustion` | Active/max ≥90%; positive maximum required | 3m, warning |

These are initial operational thresholds, not banking SLOs. Minimum traffic avoids
alerting on one failed/slow request; 4xx responses are not server errors. Pool occupancy
does not indicate a financial outcome or database availability. Validate thresholds
under a real workload before production use. Each alert has actionable annotations.

Prometheus evaluates the [rules](prometheus/rules/finfast.yaml); Grafana does not create
duplicate managed alerts. Alertmanager groups by alert name/job/environment, waits
15s for a group, updates groups at 1m and repeats at 4h, with persistent deduplication
state. The default receiver is `local-only`, with **no external notification delivery**.
Firing alerts and resolution can be inspected through its internal API. A dashboard
is not proof that a notification reached a person.

Absence detection runs only while Prometheus is running. Stopping the entire optional
stack stops evaluation; it cannot emit an alert while offline. If Prometheus remains
running while the backend is deliberately stopped or returned to ordinary mode, the
expected target is unavailable and the alert is intentional. Stop the optional stack
too when monitoring is not wanted. Missing-target alerts have a job label but cannot
recover instance/environment labels from a series that no longer exists.

Add reviewed webhook/email receivers and secret injection through Alertmanager
configuration in a future task. Never put real tokens in Git or send test notifications
to external recipients without explicit authorization.

## Repeatable checks

After setup/startup, use pinned tools already inside the containers:

```bash
docker compose --env-file .env.observability -f compose.observability.yaml exec -T prometheus promtool check config /etc/prometheus/prometheus.yaml
docker compose --env-file .env.observability -f compose.observability.yaml exec -T prometheus promtool check rules /etc/prometheus/rules/finfast.yaml
docker compose --env-file .env.observability -f compose.observability.yaml exec -T -w /etc/prometheus prometheus promtool test rules rules.test.yaml
docker compose --env-file .env.observability -f compose.observability.yaml exec -T alertmanager amtool check-config /etc/alertmanager/alertmanager.yaml
```

Rule fixtures cover healthy/down/missing targets, a previously healthy target becoming
stale, HTTP counter resets, transient failures, low traffic,
sustained 5xx/latency, normal latency, saturated pools and missing/zero maximums.
Java tests use isolated PostgreSQL 16 Testcontainers for HTTP metrics, bounded routes,
privacy, pool metrics, histogram buckets and ordinary-mode endpoint exclusion.

For real ingestion checks, create separate application/telemetry projects, credentials,
network and PostgreSQL volume. Use the [test-only overlay](compose.metrics-verify.yaml)
with `compose.yaml` and `compose.observed.yaml`; it replaces only the backend image
with existing HTTP fixtures and keeps the real isolated PostgreSQL pool. Set
`FINFAST_APP_PROJECT` to that application's project name. Never use a persistent
developer database for fault tests. Only one backend should own the `backend` DNS
alias on that telemetry network. With host ports 8080/3300 available:

```bash
cp -n .env.example .env.metrics.verify
make observability-setup OBSERVABILITY_ENV=.env.observability.metrics.verify
# Choose Grafana credentials, FINFAST_APP_PROJECT=finfast-metrics-verify and
# FINFAST_OBSERVABILITY_NETWORK=finfast_metrics_verify in .env.observability.metrics.verify.
# Choose a separate DB name/password and an unused POSTGRES_PORT in .env.metrics.verify.
make observability-up OBSERVABILITY_ENV=.env.observability.metrics.verify OBSERVABILITY_PROJECT=finfast-metrics-verify-stack
docker compose --env-file .env.metrics.verify --env-file .env.observability.metrics.verify -p finfast-metrics-verify -f compose.yaml -f compose.observed.yaml -f observability/compose.metrics-verify.yaml up --build -d
python3 observability/tests/verify_metrics.py --environment-file .env.observability.metrics.verify
python3 observability/tests/verify_stack.py --environment-file .env.observability.metrics.verify --probe-url http://localhost:8080
docker compose --env-file .env.metrics.verify --env-file .env.observability.metrics.verify -p finfast-metrics-verify -f compose.yaml -f compose.observed.yaml -f observability/compose.metrics-verify.yaml down
make observability-down OBSERVABILITY_ENV=.env.observability.metrics.verify OBSERVABILITY_PROJECT=finfast-metrics-verify-stack
```

These ignored files are operator-owned test settings, separate from the developer's
`.env` and `.env.observability` and the trace-only verification example. Setup preserves
existing files; neither script generates/deletes credentials or needs developer secrets
when given `--environment-file`. Use literal values without dollar interpolation or
inline comments; the Python reader is not a full Compose dotenv parser. Protect retained
configuration and reuse the initialized Grafana credentials on subsequent runs;
shutdown preserves all volumes.

The metrics script checks actual stored metrics, counter increments, p95/p99,
dashboard queries and rule/Alertmanager discovery. It does not stop containers.
For an isolated availability-alert drill, stop only that test backend, wait through
the unchanged 2m rule and grouping delay, inspect Prometheus `/api/v1/alerts` and
Alertmanager `/api/v2/alerts` plus `/api/v2/alerts/groups`, then start it and verify
scrape recovery and alert disappearance. Use Grafana's Prometheus datasource proxy
or `exec grafana curl http://alertmanager:9093/api/v2/alerts` for private API access.

## Storage, resources and troubleshooting

Prometheus keeps TSDB data for 72h or approximately 1GB, whichever retention policy
removes blocks first; WAL/head data need additional disk. Alertmanager retains state
for 72h. These named volumes are independent of PostgreSQL. Retention configuration
is validated; a complete 72h expiry cycle has not been exercised.

Use `docker stats` interactively or `docker stats --no-stream` for a snapshot.
The [resource observation](architecture-review.md#resource-observation) is the single
location for dated RAM/storage measurements, including backend/PostgreSQL separately.
Usage depends on traffic, active series, query workload, caches and retention; allow
headroom for the application and image builds. Telemetry is optional. Fault checks
verify backend HTTP/database availability, not unimplemented financial services or
posting atomicity. Samples missed while Prometheus is stopped are not backfilled.

- Target down: verify observed profile, shared network/DNS and target scrape errors;
  normal backend mode deliberately does not expose Prometheus.
- Missing latency: generate traffic, wait for two scrapes and rule evaluation; inspect
  `_bucket` output. Do not substitute zero for missing data.
- Missing Hikari: initialize a real datasource/pool; the database-free trace fixture
  intentionally has no pool. Connection counts still are not a DB health check.
- No alert: inspect rule health, minimum traffic, rolling window and `for` duration;
  inspect Alertmanager discovery/grouping. The default receiver sends nothing externally.
