# Optional local observability

Six optional telemetry services support logs, traces, metrics and local alerts.
See [metrics, dashboards and alert validation](metrics-and-alerts.md) for the metrics
contract, operational thresholds and database-monitoring limitations.
The [architecture review](architecture-review.md) records verification evidence,
security patch exceptions and requirements before external deployment.

Grafana browser URL: **http://localhost:3300**. Host port 3000 is reserved for the
frontend; Grafana uses port 3000 internally. Override the host mapping with
`GRAFANA_PORT`; the mapping is `127.0.0.1:${GRAFANA_PORT:-3300}:3000`.

## Setup and lifecycle

```bash
make setup
make observability-setup
# Choose GRAFANA_ADMIN_USER and GRAFANA_ADMIN_PASSWORD in .env.observability.
make observability-up
make observed-up
make observability-health
```

Run these commands from the repository root with GNU Make, Docker/Compose and curl.
`make observability-setup` copies the committed `.env.observability.example` only
when the runtime file is missing; existing files and symlinks are left unchanged.
Choose both Grafana credentials manually before startup. Missing/empty values fail
Compose interpolation with `Set GRAFANA_ADMIN_USER` / `Set GRAFANA_ADMIN_PASSWORD`;
there is no generated or default admin password. The runtime file is Git-ignored
and excluded from both Docker build contexts. Restrict access to local secret files
(for example, `chmod 600 .env.observability` on POSIX systems).

`observed-up` also invokes the idempotent `observability-up`. The latter waits for
Grafana's healthcheck, which probes all six services using Grafana's verified curl
binary. Loki and Tempo images have no shell/HTTP client; they are not given fake
shell healthchecks. Their `/ready` endpoints and Alloy's `/-/ready` are checked over
Docker DNS. Grafana checks its `/api/health`. No fixed startup sleep is required.

Set `GRAFANA_PORT=3330` in `.env.observability` for http://localhost:3330. A one-command
environment override also works, but repeat it for every subsequent `observability-up`
or `observed-up` invocation to avoid reverting the port. Internal datasources use
`loki:3100`, `tempo:3200` and `prometheus:9090`; no internal caller uses Grafana's host port.

| Setting | Source and default |
|---|---|
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | Local runtime file; both required, initialize a fresh Grafana volume |
| `GRAFANA_PORT` | Local runtime file; host 3300 → container 3000, loopback only |
| `FINFAST_APP_PROJECT` | Local runtime file; `finfast`, exact application Compose project to collect |
| `FINFAST_OBSERVABILITY_NETWORK` | Local runtime file; `finfast_observability`, must match the backend overlay |
| `DOCKER_SOCKET_PATH` | Local runtime file; `/var/run/docker.sock`, rootless engines may differ |
| `OBSERVABILITY_ENV`, `OBSERVABILITY_PROJECT` | Make variables; `.env.observability` and `finfast-observability` |
| `APP_ENV`, `DEPLOYMENT_VERSION`, `TRACING_SAMPLING_PROBABILITY` | Observed backend interpolation; `local`, `dev`, `1.0` |

Arbitrary keys in an environment file are not automatically injected into containers.
The observed overlay explicitly enables trace export to `http://alloy:4318/v1/traces`;
native export and production sampling are described in the
[tracing contract](../docs/03-design/logging-and-error-handling.md#sampling-resources-and-optional-otlp-export).

Return to pretty-console mode with `make up`, then `make observability-down`.
The latter uses Compose **stop**, preserving containers, positions, network and
volumes, including when a running observed backend remains attached. It does not
manage the backend/PostgreSQL project. Do not use `down -v` for normal shutdown.
`make start`/`restart` retain the existing backend mode; use `make up` to reapply
ordinary configuration. For complete shutdown use `make down`,
`make observability-down` and, if running, `make docs-down`; named volumes persist.
Changing Grafana environment credentials after its persistent database is initialized
does not reset an existing account; change that account through Grafana administration.

`make logs` follows the backend without Compose's display prefix. Ordinary Docker
mode enables pretty ANSI output; the observed overlay explicitly disables it and
prod/observability profiles keep JSON uncolored. Colors require a capable viewing
terminal and do not change older stored records. See the
[console contract](../docs/03-design/logging-and-error-handling.md#formats-and-configuration)
for the local-only preference.

## Topology and versions

| Component | Responsibility | Pinned release | Local persistent storage |
|---|---|---|---|
| Grafana | Visualize logs, traces, metrics and dashboards | 13.2.4 | SQLite/settings in `grafana_data` |
| Alloy | Collect/forward Docker logs and OTLP traces | 1.20.1 | Docker tail positions in `alloy_data` |
| Loki | Store/query application logs | 3.7.8 | Filesystem/TSDB v13 in `loki_data` |
| Tempo | Store/query distributed traces | 2.10.8 | Local blocks/WAL in `tempo_data` |
| Prometheus | Scrape/query metrics and evaluate alert rules | 3.15.0 | TSDB in `prometheus_data`, 72h/1GB retention |
| Alertmanager | Group, deduplicate and route alerts | 0.34.1 | State in `alertmanager_data`, 72h retention |

Signal paths: Logback ECS → Docker stdout → Alloy → Loki → Grafana;
Micrometer/OpenTelemetry → OTLP → Alloy → Tempo → Grafana;
Actuator `/actuator/prometheus` → Prometheus → Grafana;
Prometheus alert rules → Alertmanager → `local-only`. Alloy does not collect metrics.

Verified image digests are pinned in [Compose](../compose.observability.yaml).
Tempo 2.10 supports this single-binary filesystem setup without Kafka or object storage.
The default projects are `finfast` and `finfast-observability`. Telemetry volumes are
separate from the application's `postgres_data`; no telemetry operation resets it.

The observability project creates `finfast_observability`. Only the opt-in
[backend overlay](../compose.observed.yaml) joins it, alongside the application's
normal network. PostgreSQL does not join it. Alloy receives backend OTLP/HTTP at
`http://alloy:4318/v1/traces` and forwards once to `http://tempo:4318`. No re-sampling
or second SDK/export path is added. Loki/Tempo/Alloy/Prometheus/Alertmanager ports are not published to the host.
Only Grafana is published, on loopback. Internal unauthenticated APIs are local Docker
development infrastructure, not a production security boundary.

For another application project, set `FINFAST_APP_PROJECT` in `.env.observability` to
its exact Compose project name. Both overlays must use the same
`FINFAST_OBSERVABILITY_NETWORK`. A missing network affects only the explicit observed
overlay; normal `make up` does not refer to it. Separate Docker engines give developers
independent storage; shared engines need distinct project/network names and host ports.

## Logs and traces in Explore

In Grafana **Explore**, select **FinFast Loki** (`finfast-loki`) and start with:

```logql
{service_name="finfast-backend"}
{service_name="finfast-backend",environment="local",level="ERROR"}
{service_name="finfast-backend",module="platform"}
```

Alloy discovers only the selected Compose project, `backend` service and explicit
`com.finfast.telemetry=ecs` opt-in label. One Docker source forwards into one Loki
pipeline. It parses ECS, applies the original UTC timestamp, retains the original JSON
line and indexes only `service_name`, `environment`, `level`, `module`.
Trace/span/request IDs remain content, never stream labels. Search a known ID with:

```logql
{service_name="finfast-backend"} |= "paste-real-trace-or-request-id"
```

Expand a correlated log row and select **View trace in Tempo**. The derived field
matches the literal dotted JSON key `"trace.id"` and preserves all 32 hex characters.
It uses an internal datasource link, not a hardcoded browser port. Tempo also has a
custom logs link back to Loki. Sampling, collector downtime and retention can leave
a log with no stored trace. Startup logs are correctly untraced; automatic HTTP tracing
and metrics do not emit an application log for every successful request.
Tempo 2.x search responses can omit leading zeroes in hexadecimal IDs. The verifier
normalizes only that search representation; ECS and stored trace-by-ID assertions
still require and compare the original full 128-bit identifier.

Select **FinFast Tempo** (`finfast-tempo`) to search traces, or
**FinFast Prometheus** (`finfast-prometheus`) for metrics. The provisioned
[FinFast — Backend Overview](http://localhost:3300/d/finfast-backend-overview) dashboard
shows HTTP/JVM/Hikari data; its [metrics guide](metrics-and-alerts.md#dashboard-and-latency)
explains No data states and quantile estimates.

### HTTP 5xx semantics

This Spring/Micrometer instrumentation exports legacy span attributes `method`,
`uri`, `status`, `outcome`, `exception`; `status` is a string. It does **not** emit
`http.response.status_code`. Handled MVC 500s may have OTel status `UNSET` while the
HTTP attribute is `status="500"`. Query HTTP failures, not only OTel error status:

```traceql
{ resource.service.name = "finfast-backend" && span.status =~ "5[0-9][0-9]" }
```

The end-to-end check exercises both a handled 500 and an SDK-recorded error. The
infrastructure preserves framework semantics; it does not invent exceptions or
financial rejection outcomes to make a dashboard look populated.

## Privacy, socket access and availability

The existing export privacy filter removes span events/status descriptions, and safe
exception rendering protects both stdout modes. Raw URLs, request bodies, credentials,
account numbers and idempotency keys are not added to telemetry. Alloy does not dump
headers or OTLP payloads. Debug endpoints exist only in the separate test fixture image.
Logs/traces never replace immutable ledger records or durable financial audits/outcomes.
Safe exception rendering and canary tests do not sanitize arbitrary developer-written
messages. Docker/stdout access and Grafana log/trace access can expose diagnostics;
restrict access and never deliberately emit sensitive values.

**Docker socket access is highly privileged. A read-only mount does not make the Docker
API read-only.** Direct socket discovery was selected for portable local Docker/Desktop
usage. Tailing daemon log files instead needs host-specific paths/driver assumptions;
it is a practical production alternative under a narrowly privileged host agent. Do not
deploy this socket mount to an untrusted environment. Production needs reviewed,
least-privilege collection and authenticated/TLS telemetry endpoints. No Docker TCP API
is exposed and no privileged container is used here.

The backend never depends on collector readiness. Its batched exporter has bounded
timeouts/queues; stopping telemetry leaves HTTP and database health available. Pending
spans/log batches can be lost during outages or restarts; no exactly-once durability is
claimed. Persistent Docker positions avoid ordinary replay, but duplicates remain an
operational possibility during failure recovery. `make up` disables export and returns
to pretty logs; `make observed-up` restores collection after the stack is ready.

Loki retention is 72h with TSDB v13, 24h indexes and an enabled filesystem compactor.
Deletion is asynchronous, with a 2h deletion delay and periodic compaction. Tempo
compaction retains trace blocks for 72h. Neither is a strict disk quota. Docker observed
backend stdout rotates at 3 × 10MB; telemetry volumes still need disk monitoring.
Allow a few GiB of free RAM/disk for the optional stack plus backend/builds; actual
consumption depends on traffic, queries and Go caches. Measure rather than assume:

The [review measurements](architecture-review.md#resource-observation) cover all six
telemetry services, backend/PostgreSQL separately and allocated volume sizes. These
light-workload snapshots are observations, not memory limits or capacity guarantees.
Retention is validated configuration, not proof of a complete 72h expiry cycle.

```bash
docker stats --no-stream
make observability-down
```

## Repeatable end-to-end verification

The normal production backend has no diagnostic API. Use a separate test image/project.
The following requires only Docker for the fixture and Python 3 for storage assertions.
Use a separate secrets file copied from the template and choose unused ports if another
observability stack is already running:

```bash
make observability-setup OBSERVABILITY_ENV=.env.observability.verify
# Set credentials, FINFAST_APP_PROJECT=finfast-observability-verify,
# and FINFAST_OBSERVABILITY_NETWORK=finfast_observability_verify in that file.
make observability-up OBSERVABILITY_ENV=.env.observability.verify OBSERVABILITY_PROJECT=finfast-observability-verify-stack
docker compose --env-file .env.observability.verify -f observability/compose.verify.yaml up --build -d
python3 observability/tests/verify_stack.py --environment-file .env.observability.verify
docker compose --env-file .env.observability.verify -f observability/compose.verify.yaml down
make observability-down OBSERVABILITY_ENV=.env.observability.verify OBSERVABILITY_PROJECT=finfast-observability-verify-stack
```

The example is committed; `.env.observability` is developer-owned; the ignored
`.env.observability.verify` is operator-owned test configuration with separate credentials.
Neither verification script creates/deletes credentials, starts/stops containers or
requires the normal developer secret file when `--environment-file` is supplied.
You perform setup and cleanup explicitly. Shutdown above preserves telemetry containers,
network and volumes; keep their configuration protected while retained. On reuse, keep
the initialized Grafana credentials or choose a fresh isolated project/network; editing
the file does not reset the stored password. No volume reset is part of this workflow.
For verifier files, use literal values without dollar interpolation or inline comments;
the Python reader is not a full Compose dotenv parser.

The script defaults to Grafana **http://localhost:3300** and probe localhost:18080;
override `--grafana-url` / `--probe-url` alongside the Compose host-port settings.
It starts no containers, accesses no database, compares real Micrometer/OTel/MDC IDs,
queries received Loki/Tempo data, rejects duplicate JSON keys/events, checks exact
timestamps and low-cardinality labels, tests the actual provisioned regex, searches
500 traces and verifies the sensitive canary is absent after ingestion. Browser
navigation is a separate check: open that Loki row and follow its Tempo link.

## Troubleshooting

- No logs: confirm the backend profile/opt-in label, `FINFAST_APP_PROJECT`, Docker
  socket path/permissions, and `make observability-health`; inspect `docker compose
  --env-file .env.observability -f compose.observability.yaml logs alloy`.
- No traces: check `OTLP_TRACES_ENABLED`, exporter errors, sampling, and shared network;
  `localhost:4318` inside the backend is not Alloy. Use `alloy:4318`.
- No derived link: expand the raw ECS row; IDs are literal dotted keys, not a nested
  `trace` object. Check retention/sampling if the link has no stored trace.
- Grafana unavailable: verify the chosen `GRAFANA_PORT` and credentials file. A 3300
  host listener is independent of frontend port 3000. `docker compose ... config
  --quiet` validates interpolation without printing passwords.
