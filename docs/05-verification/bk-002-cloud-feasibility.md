# BK-002 — Cloud feasibility preparation

> Status: **DRAFT preparation; repository inspection only.** Prepared 2026-10-22.
> This completes the independent requirements/evidence-planning step, not BK-002
> or EV-002 verification. BK-001 is in progress with another team member, per the
> task request; its official result is unavailable. EV-001/EV-002 remain pending
> and TD-012 remains NOT_SELECTED. No provider, budget or topology is approved here.

## Scope and authority

Step 2 research is available in the [provisional provider comparison](bk-002-provider-comparison.md):
AWS, Google Cloud, Render and Railway, official sources accessed 2026-10-22,
illustrative budgets and a conditional shortlist. Runtime feasibility remains unverified.

[BK-002](../02-requirements/10-product-backlog.md) asks for cloud, storage, TLS
and budget feasibility (EV-002), depends on BK-001, and traces to NFR-005.
The approved [discovery gates](../01-discovery/07-assumptions-and-questions.md)
require external evidence before final resource selection. The
[PRD](../02-requirements/09-product-requirements.md) remains draft; its NFRs are
planning inputs, not a new approval granted by this document. Preserve the agreed
[architecture](../03-design/15-system-architecture.md) and individual statuses in
the [decision register](../6-governance/25-design-decision-register.md).

The independent deliverable is a provider-neutral baseline, missing-input register
and evidence checklist. It requires no cloud resources or application changes.
Frontend implementation belongs to BK-019/020; application foundations to BK-004;
full cloud delivery/runbook and browser evidence to BK-024. No UI design work or
UI UX Pro Max workflow is needed for this step.

## Repository baseline and future verification criteria

The observations below establish source/configuration facts only. Every cloud
verification remains **NOT EXECUTED**; a local configuration is not cloud evidence.

| Area / trace | Observed baseline | Evidence needed for a candidate environment |
|---|---|---|
| Core runtime — NFR-005/006, A-013 / DEC-006 | One Spring Boot modular monolith. [Dockerfile](../../backend/Dockerfile) builds with Java 21 and runs the JAR as UID/GID 10001; image digests are pinned. | Supported container architecture/runtime, image revision/digest, startup logs without secrets, resource limits and successful health response. Preserve one deployable Core and in-process module calls. |
| Database/storage — NFR-002/005, EV-002 | [Compose](../../compose.yaml) pins PostgreSQL 16.15, uses `postgres_data` and a database health check. | PostgreSQL compatibility; storage type, capacity and lifecycle; documented retention across Core restart and platform redeploy; private connectivity. A named local Docker volume alone proves no cloud retention guarantee. |
| Schema lifecycle — NFR-005 | [Application configuration](../../backend/src/main/resources/application.yml) enables Flyway and disables clean; [migration guide](../../backend/src/main/resources/db/migration/README.md) records V1/V2 and no automatic banking seed data. | Migration/validation output on an isolated environment and evidence that ordinary restart does not reset data. No migration edits or implicit reseeding. |
| HTTPS/network — NFR-003/005, EV-002 | Compose publishes backend 8080 and database 15432 by default on loopback. It supplies no public TLS ingress. | HTTPS endpoint, certificate provisioning/renewal method, TLS termination and routing, backend reachability and private DB access. Record proxy/cookie implications for later Identity integration; do not claim session security from TLS alone. |
| Configuration/secrets — NFR-005 | Compose supplies datasource settings through environment variables. | Supported secret injection, environment separation and redacted startup/deploy instructions; no credentials in Git, client assets or evidence. |
| Web delivery — NFR-005, UX-005 | [README](../../README.md) records no frontend application. Architecture permits static hosting or serving web assets through Core. | Compare hosting and API-origin implications when choosing topology; no framework or new UI is needed for feasibility preparation. |
| Delivery — NFR-005 | README documents local Docker/health and CI foundations, but no deployment CD. | Reproducible candidate deployment steps, recorded configuration/version and restart evidence. Full release delivery remains BK-024. |
| Capacity/cost — EV-002/003, NFR-004/005 | No cloud sizing or budget is selected. The performance target remains provisional: p95 <= 2 seconds at 10 new transfer requests/second for five minutes. | Dated provider limits/pricing, CPU/RAM/storage/network estimates and total cost assumptions. Only later EV-003 measurements can establish performance; this document specifies no minimum sizing. |

Financial durability requirements affect Accounts, Ledger, Idempotency and
Administration; restricted initialization affects Bootstrap; HTTPS/session routing
affects Identity. No business module API or implementation change is required for
this preparation. Provider implementations and banking runtime flows remain future
work, so infrastructure checks cannot establish financial acceptance.

## Assumptions and missing inputs

These local labels organize this worksheet; they do not replace canonical A/OQ/EV IDs.

| Item | Status | Working treatment and resolution |
|---|---|---|
| Course/platform/topology constraints | UNKNOWN; BK-001 pending | Keep candidates provisional. Obtain the official EV-001 result before final topology/resource selection; do not assume no course mandate exists. |
| Evaluation target | WORKING ASSUMPTION | Prepare one demo environment for comparison, consistent with the project scope. Revisit if BK-001 requires other environments or topology. No production HA claim. |
| Budget and operating period | UNKNOWN | Budget cap, currency, demo duration and daily operating hours need team input. No zero-cost/free-tier assumption. |
| Provider/account/region | UNKNOWN | Credits, eligibility, quotas and region availability require dated confirmation. No account or provider has been selected. |
| Resource capacity | UNKNOWN | CPU, RAM, disk, connections and traffic must be estimated and later measured; do not infer them from the performance target. |
| Domain/TLS | UNKNOWN | Determine whether a provider hostname is acceptable and who controls any custom domain. Include certificate/domain costs where applicable. |
| Frontend hosting | OPEN DESIGN CHOICE | Compare static hosting and Core-served assets without selecting a frontend framework. Reconcile with the eventual frontend owner and session design. |
| Telemetry footprint | WORKING ASSUMPTION | Price the required application/database baseline separately from optional local observability services; do not assume all six services must run in cloud. Revisit against EV-001 and operational needs. |
| Backup/restore and availability objectives | UNKNOWN | Record candidate capabilities/costs and request team decisions where needed. Do not invent RPO/RTO, retention or an availability SLA. Preserve required dataset durability. |

## Work sequence and evidence record

1. **Completed here:** extract repository requirements, current configuration,
   assumptions, missing inputs and verification criteria without provisioning.
2. **Can proceed while BK-001 is pending:** collect budget/account inputs and
   compare provisional candidates against the matrix. Use dated official provider
   sources for pricing, storage lifecycle, runtime, HTTPS and quotas. Record unknowns
   and exclusions. Initial public-source research is recorded in the linked
   comparison; exact Render pricing and team budget/account inputs remain open.
3. **After the BK-001 result:** reconcile every course constraint with the candidates.
   Record conflicts for owner disposition and submit the proposed TD-012 topology
   through existing review. Do not silently override the agreed architecture.
4. **After environment/resource authorization:** perform an isolated feasibility
   exercise using synthetic data and the existing backend foundation. Capture
   health, migration, HTTPS, private connectivity and persistence evidence. Do not
   use a developer's persistent database or destructive volume-reset commands.
5. **Close EV-002 only with evidence:** record the selected resources, budget,
   verified capabilities and limitations; link the results from the
   [release checklist](23-uat-and-release-checklist.md). Hand off full delivery to
   BK-024 and performance measurement to BK-023/EV-003.

Use this proposed record for each later candidate; empty fields mean unverified:

| Field | Required content |
|---|---|
| Candidate and status | Provider/service/region; provisional, rejected or reviewed selection; reviewer/date when applicable |
| Evidence provenance | Official source URL, retrieval date, relevant quota/lifecycle details; distinguish documentation from an executed check |
| Cost estimate | Currency, pricing date, operating period/hours; compute + database + persistent storage + network/egress + required TLS/domain/registry/other costs; taxes/credits and exclusions explicit |
| Resources and lifecycle | CPU/RAM, storage, connection limits, sleep/idle behavior, restart/redeploy retention and network boundaries |
| Reproducibility | Commit/image digest, redacted configuration, exact steps, environment and timestamp |
| Verification result | Expected versus observed result, evidence location, failures and unresolved gaps; no secrets or private data |
| Dependencies | BK-001 constraints, unresolved decisions, later business/EV-003 evidence and next action |

## Completion boundary

This first step is complete when its requirements are traceable, assumptions are
explicit and the future evidence checklist is reviewable. It neither closes
BK-002 nor changes EV-001, EV-002 or TD-012 status. No cloud resources, runtime
checks, benchmark results or spending are claimed.
