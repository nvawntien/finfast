# BK-002 — Provisional cloud provider comparison

> **DRAFT research, accessed 2026-10-22 (Asia/Bangkok).** Complements the
> [feasibility preparation](bk-002-cloud-feasibility.md). Official public pages
> were read without credentials. No infrastructure was provisioned or deployed.
> BK-001 is pending; EV-002 remains unverified and TD-012 remains NOT_SELECTED.
> Prices are USD reference rates, not a quote or approved team budget.
> Review corrections and Render source recheck: **2026-10-23**. Other providers'
> research dates remain 2026-10-22; affordability is not yet established.

## Verified repository requirements

| Area | Source observation | Consequence for evaluation |
|---|---|---|
| Backend | [POM](../../backend/pom.xml): Java 21, Spring Boot 4.1.1, MVC, Actuator, Flyway/PostgreSQL dependencies. [Dockerfile](../../backend/Dockerfile): multi-stage Java 21 build/runtime, executable JAR, non-root UID/GID 10001. | Run the existing container with build context `backend/`. Build resources and runtime resources are different; 512 MB is not a verified Java capacity requirement. |
| Database | [Compose](../../compose.yaml): PostgreSQL 16.15 pinned by digest, persistent volume mounted at `/var/lib/postgresql/data`. | Preserve PostgreSQL 16 compatibility. A managed provider may control minor versions; verify its offered version rather than assume the exact local image is available. |
| Flyway | [Configuration](../../backend/src/main/resources/application.yml): startup migrations enabled, clean disabled. [Migration guide](../../backend/src/main/resources/db/migration/README.md): V1/V2, eight banking tables, functions/triggers and no banking seed rows. | Verify migration permissions and checksum validation on the selected database. No reset, bootstrap or sample-data creation is implied by deployment. |
| Network/port | Compose uses internal `postgres:5432`, loopback host publications, and DB health dependency. Backend reads `SERVER_PORT`, default 8080. | PaaS services need their own private DB hostname and JDBC URL. Do not copy Docker DNS or assume provider `PORT` automatically maps to `SERVER_PORT`; configure matching ports. Compose dependency ordering does not transfer automatically to PaaS. |
| TLS/secrets | Compose supplies `SPRING_DATASOURCE_URL`, username/password through environment; no TLS termination is configured there. | Configure HTTPS ingress and runtime secret delivery later. A `postgres://` provider URL is not automatically a JDBC URL. Confirm DB TLS/verification settings and proxy/session behavior separately. |
| Health | Actuator dependency and aggregate `/actuator/health` are documented in [README](../../README.md); `management.endpoint.health.probes.enabled` is explicitly `false` in the current application config. | Configure aggregate health as an initial deployment check. The separate probe examples are `/actuator/health/liveness` and `/actuator/health/readiness`; they are not enabled by the current configuration and their availability has not been runtime-verified. Design restart behavior carefully because aggregate DB failures need not mean a dead JVM. No runtime check was executed here. |
| Absent capabilities | README records missing frontend and banking provider implementations. Migration guide records no session tables. | No frontend framework, session persistence mechanism, Redis, broker, Kubernetes or additional service is assumed. Scaling/restarts require later Identity/session verification. |

Keep one deployable Core and one logical PostgreSQL database. All capacities below
are comparison configurations, not measurements or acceptance of EV-003.

## Comparable candidate configurations and costs

All examples assume one non-HA demo environment, no promotional credits, and
730 hours/month when hourly arithmetic is used. Regions, account eligibility,
six-person access rights and the actual budget are still unknown. These are
different operating models, not equivalent performance benchmarks.

| Provider / candidate | CPU/RAM and storage | Reference monthly cost | Cost limitations |
|---|---|---|---|
| AWS: Lightsail Linux VM, Core + self-hosted PostgreSQL | Published IPv4 bundle: 2 vCPU, 4 GB RAM, 80 GB SSD shared by both containers and OS. Smaller comparison: 2 vCPU, 2 GB, 60 GB. | $24 bundle; smaller bundle $12. [AWS pricing](https://aws.amazon.com/lightsail/pricing/) | Burst capacity is not sustained benchmark evidence. Add backup/snapshot, domain/TLS arrangement, overage and any separate secrets/monitoring services. Transfer allowance varies by region; stopping the VM does not stop bundle charges. [Billing](https://docs.aws.amazon.com/en_en/lightsail/latest/userguide/amazon-lightsail-frequently-asked-questions-faq-billing-and-account-management.html) |
| Google Cloud: Cloud Run + Cloud SQL PostgreSQL | Illustration: Core 1 vCPU/1 GiB; SQL Enterprise single-zone `db-g1-small`, shared CPU/1.7 GiB, assumed 10 GiB SSD. | Iowa reference: SQL `730 × $0.035` = $25.55; SSD `10 × 730 × $0.000232877` ≈ $1.70. Always-allocated instance-based Run: `2,628,000 × ($0.000018 + $0.000002)` = $52.56 before free allowances. Combined gross subtotal ≈ **$79.81**. [SQL pricing](https://cloud.google.com/sql/pricing?hl=en), [Run pricing](https://cloud.google.com/run/pricing) | Excludes networking/VPC, backup, secrets, registry/build/logging and tax. Request-based/scale-to-zero billing is a different scenario and requires a workload estimate. Shared-core SQL has no SLA. Confirm regional availability and rates; no Singapore price is inferred. |
| Render: paid Docker web service + paid Render Postgres | Candidate Core `1c-2g`: 1 CPU/2 GB. Candidate DB `0.5c-1g`: 0.5 CPU/1 GB; assume 10 GB DB storage for comparison. [Compute plans](https://render.com/docs/compute-plans) | **Exact total NOT VERIFIED.** Formula: web compute + DB compute + DB storage + applicable workspace/usage fees. [Pricing page](https://render.com/pricing) | Public price table was not exposed by the page reader; do not substitute third-party estimates or confuse Key Value prices with PostgreSQL. Capture a current official quote for these exact plan IDs, storage and team access before budget ranking. |
| Railway: Core container + PostgreSQL service/volume | Usage-based. Illustration assumes combined average 1.5 GB RAM, 0.1 vCPU consumed, 5 GB volume. These are assumed usage, not reserved capacity. Hobby advertises up to 5 GB volume; Pro up to 1 TB. | Service rates: RAM $0.00000386/GB-second, CPU $0.00000772/vCPU-second, volume $0.00000006/GB-second. Example `2,628,000 × (1.5 × RAM rate + 0.1 × CPU rate + 5 × volume rate)` ≈ **$18.03**, excluding egress. Hobby minimum $5; Pro minimum $20, including corresponding usage credit, not added again. [Pricing](https://railway.com/pricing) | Example Pro bill is at least $20; higher actual usage raises it. Egress $0.05/GB. Hobby is a single-developer workspace; Pro includes team seats. Verify account limits and backup/PITR costs. Limits are not guaranteed dedicated resources. |

For AWS the team runs PostgreSQL; for Google/Render the candidate uses a separate
managed database. Railway's current documentation describes a maintained single
node and a separate HA conversion path; do not label it fully managed HA by
default or rely on older blanket descriptions of all Railway databases as unmanaged.
[Railway PostgreSQL](https://docs.railway.com/databases/postgresql)

## Persistence, delivery and operations

| Area | AWS Lightsail VM | Google Cloud Run + SQL | Render paid web + Postgres | Railway Core + Postgres |
|---|---|---|---|---|
| PostgreSQL persistence | Proposed mount on persistent VM disk; app container replacement must retain it. Team owns patching, backup and restore. VM deletion/replacement requires a reviewed recovery procedure. | Store financial data in Cloud SQL. Cloud Run writable filesystem is in memory and disappears on instance stop; it is unsuitable for PostgreSQL data. [Runtime contract](https://docs.cloud.google.com/run/docs/container-contract) | Separate managed DB; paid tiers support PITR/logical exports. Storage can grow but cannot shrink; plan changes can interrupt DB availability. [Postgres](https://render.com/docs/postgresql-creating-connecting) | Explicit persistent volume mount required. Verify template major version and path, backup/PITR settings and actual restore behavior. [Volumes](https://docs.railway.com/volumes), [Postgres](https://docs.railway.com/databases/postgresql) |
| Networking | Proposed private container network; keep DB off public interfaces. Host firewall/ingress administration belongs to team. | Prefer private IP route to SQL using reviewed VPC connectivity; JDBC configuration and IAM/network costs need evaluation. [Run-to-SQL](https://docs.cloud.google.com/sql/docs/postgres/connect-run) | Same-region internal DB connection; explicitly restrict external access. [Connections](https://render.com/docs/postgresql-creating-connecting) | Internal DNS over isolated project/environment private networking; avoid exposing DB through a public TCP proxy. [Private network](https://docs.railway.com/networking/private-networking) |
| HTTPS | VM bundle alone is not automatic HTTPS. Team-managed termination is a proposal; alternatively price a separate supported Lightsail TLS resource. Certificates attach to load balancers/CDN/container services, not directly to this VM bundle. [TLS](https://docs.aws.amazon.com/lightsail/latest/userguide/understanding-tls-ssl-certificates-in-lightsail-https.html) | Platform terminates TLS; container receives proxied requests. [Runtime contract](https://docs.cloud.google.com/run/docs/container-contract) | Managed TLS for web services. [Web services](https://render.com/docs/web-services) | Provider/custom domains with automatic certificates. [Public networking](https://docs.railway.com/networking/public-networking) |
| Secrets | Proposed protected runtime environment delivery; no secret store or automatic rotation is included by this proposal. Select mechanism and price separately. | Secret Manager integration supports environment/file delivery; service account access must be configured. [Secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets) | Environment variables, secret files and environment groups. [Secrets](https://render.com/docs/configure-environment-variables) | Variables and sealed values; sealed values are not automatically copied into duplicated/PR environments. [Variables](https://docs.railway.com/variables) |
| Deployment | Proposed reuse of Dockerfile/Compose with separately reviewed cloud configuration. Team manages host startup, image pull and rollback. Existing Compose has no restart policy or TLS service. | Container revision deployment. Configure compatible port, SQL access and startup window for Flyway. The repo is not yet wired to this platform. | Git/Dockerfile or prebuilt image; proposed `backend/` root. Configure port, secrets and health; no Compose import assumed. [Deploy](https://render.com/docs/web-services) | Dockerfile build with `backend/` root/context, variables and independent DB service. [Dockerfiles](https://docs.railway.com/builds/dockerfiles) |
| Health/restart | Team must configure boot/restart supervision and monitoring; Dockerfile EXPOSE is not a health check. | Platform startup/liveness checks are configurable; map to existing endpoints deliberately. [Health checks](https://docs.cloud.google.com/run/docs/configuring/healthchecks) | HTTP health can gate deployment; continuing probes can restart unhealthy instances. [Health checks](https://render.com/docs/health-checks) | Health check gates deployment but is not monitored after it becomes live; ongoing monitoring and restart policy require separate review. Align `PORT` with 8080/`SERVER_PORT`. [Health checks](https://docs.railway.com/deployments/healthchecks) |
| Operating burden / limitations | Highest host/DB responsibility and a shared VM failure domain. Cheapest bundle does not establish adequate Java/build headroom. | More IAM/network/billing components; cold starts and instance replacement affect session design. Cloud SQL is a separate ongoing cost. | Lower DB/host burden; cost and team-plan quotation incomplete. Free DB expires after 30 days and is later deleted; free web can sleep. Exclude free tier from a durable dataset baseline. [Free limits](https://render.com/docs/free) | Variable bills require monitoring. Single-node DB is not automatic HA. Verify storage/backup ownership and team plan before selection. |

Managed persistence is not proof of FinFast invariants. For every candidate,
later tests must verify V1/V2 permissions, no reseeding, retention after restart,
outcome durability and session behavior. PostgreSQL 16 is listed by
[Cloud SQL version policy](https://docs.cloud.google.com/sql/docs/postgres/db-versions);
Render offers version selection in its creation flow. Exact offered minor versions
and migration compatibility remain untested. Railway's default template version
must be inspected before adoption; do not silently accept a different major.

## Illustrative budget scenarios

These ceilings are **assumptions for discussion**, not confirmed requirements.
Customer Web and Admin/Back-office are mandatory P0 requirements under
[PRD section 4.1 and UX-001..005](../02-requirements/09-product-requirements.md).
Only the hosting arrangement remains open: serve the frontend through Core or
host it separately. The examples above price Core/database resources only;
frontend hosting, build and bandwidth costs have **not been estimated or included**.
Serving assets through Core may share the quoted resources, but its additional
load and any required resizing must be evaluated; no zero incremental cost is
assumed. Separate hosting requires its own cost line. All totals also exclude
tax, exchange-rate effects and optional telemetry unless explicitly included.
An unpriced component is not zero cost, and these subtotals are not full V1 costs.

| Assumed ceiling / month | Interpretation |
|---|---|
| $20 | Lightsail $12 leaves $8 before extras but has only 2 GB shared RAM; adequacy untested. Railway example Hobby is about $18.03 before egress; it does not include six-person workspace access. Pro minimum already uses the ceiling. No complete solution is proven within this budget. |
| $50 | Lightsail $24 leaves $26 for known-to-be-needed extras, without asserting their prices. Railway Pro can be evaluated with measured usage. Render is a conditional candidate only after its full official quote fits. |
| $100 | Google example gross subtotal $79.81 leaves $20.19 before excluded costs; it is not a guaranteed all-in $100 solution. Other candidates have more possible headroom but still need evidence and team access costs. |

For each later quote record region, service/plan IDs, resource quantities, monthly
hours, disk/backup usage, bandwidth, workspace members, currency, pricing date and
credits separately. Exclude promotional credits from steady-state affordability.
No claim is made that any example meets the transfer latency/throughput target.

## Technical shortlist and decision gates

**Provisional technical shortlist: Render paid web + paid Postgres, and AWS Lightsail VM.
Affordability has not been sufficiently assessed.**
This is an engineering inference from the repository and provider documentation,
not a selected deployment architecture. Weighting assumes a small demo, a
six-person team, one Core, durable PostgreSQL and limited operational time.
Six team members does not establish how many provider workspace seats are needed.

1. **Render — operational simplicity candidate.** Managed DB, private connection,
   HTTPS and health-driven deployment fit the required shape without adding host
   administration. Use the 1 CPU/2 GB web and 0.5 CPU/1 GB DB as a quote starting
   point only. Main gates: exact recurring price including team workspace/storage,
   accepted provider/region, PostgreSQL version/permissions and startup capacity.
   Reject or resize if those gates fail; the shortlist does not establish budget fit.
2. **AWS Lightsail — control and configuration continuity candidate.** A 4 GB
   VM gives an inspectable bundle price and a path close to existing Compose.
   Main trade-off is team responsibility for Linux/PostgreSQL/TLS, restart
   supervision, backups and recovery. Use only if a named operator can own that
   work and the course accepts this topology. No HA claim; re-evaluate if managed
   DB or a prescribed AWS service is required.

Railway remains a close alternative if usage-based spending and its team plan are
acceptable; it could replace Render after quote comparison. Google Cloud is a
strong alternative if BK-001 prescribes GCP or the team already has IAM/VPC skills
and suitable credits/budget; this candidate has more configuration and cost items.
No provider is rejected as technically incapable solely because it is outside the
shortlist.

**Inputs still needed from BK-001:** mandated provider/services, acceptance of PaaS
or a single VM, database/region/network requirements, public URL/custom-domain
rules, required deployment/CI evidence, availability/demo duration, and any mandated
scaling or benchmark environment. Other team inputs: budget/currency, account
eligibility/credits, operator ownership, six-member access model and backup needs.
Record absent course requirements explicitly only when BK-001 confirms them.

## Research limitations and next action

All linked provider sources were consulted on **2026-10-22**. AWS/Run/Railway rates
were available from official pages. Cloud SQL's full pricing fetch exceeded the
reader size limit; its official indexed pricing results supplied shared-core and
SSD rates. Confirm region-specific SQL quotes before selection. Render's price
table remained unavailable through the reader on retry; no exact Render price is
claimed. Some auxiliary URLs failed; their contents are not used as evidence.

### Render source recheck — 2026-10-23

The official [pricing page](https://render.com/pricing) was accessible, but the
page reader again returned no usable compute, storage or workspace price table.
The official [compute plan reference](https://render.com/docs/compute-plans)
confirms web `1c-2g` = 1 CPU/2 GB and PostgreSQL `0.5c-1g` = 0.5 CPU/1 GB;
database storage is specified separately. This confirms candidate specifications,
not their prices. No Render dollar estimate or affordability conclusion is added.

To produce a complete quote, confirm these inputs:

| Input | Current status |
|---|---|
| Web/DB plans and instance count | The named plans and one instance each are comparison assumptions, not measured sizing or approved selections. |
| Database storage | 10 GB is an assumption; required capacity, growth and backup/PITR needs are unconfirmed. |
| Region and operating period | Region, demo duration and billable operating hours need confirmation. |
| Workspace and seats | Actual platform users/roles and workspace tier are unknown; do not equate the six-person project team with six paid seats. |
| Usage and frontend | Build minutes, outbound bandwidth, frontend hosting arrangement and associated usage remain unestimated. |
| Budget | Team ceiling/currency and applicable taxes are unconfirmed; credits must be listed separately. |

Capture official rates and applicable included allowances for those inputs before
comparing the complete recurring bill with the team's budget. The technical
shortlist must not be presented as a cost-ranked or budget-approved shortlist.

Next independent work: obtain the Render official quote and team budget/access
inputs, then update the candidate cost sheet. After BK-001, reconcile constraints
and review TD-012 before any authorized feasibility deployment. Documentation
research does not close EV-002; no runtime, migration, restart, TLS or performance
test has been executed in cloud.

## Local documentation validation

On 2026-10-22, `git diff --check` passed. A standard-library check verified 70
local link paths across this document, the preparation document and docs index,
checked their trailing whitespace, and reproduced the Railway/GCP example totals.
This limited check does not validate heading anchors or OpenAPI semantics.
`python -B scripts/check_docs.py` could not run because `markdown_it` is missing;
`python -B -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml`
could not run because `openapi_spec_validator` is missing. No dependencies were
installed. Re-run the repository's documented checks in its prepared docs
environment before merge. Application tests were not run for this docs-only change.
