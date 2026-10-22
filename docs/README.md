# FinFast V1 — PRD-to-Engineering Handoff Pack

> **Artifact status — 2026-10-22:** Discovery 01–08, PRD 09, derived specification 12, UX 14, API 17/existing OpenAPI, BK-003 Java contracts and TD-001..016 are APPROVED implementation baselines within their recorded scopes. [Consolidated Product/UX/REST approval](6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze). Tiến authorizes implementation-baseline approval; future banking-policy changes and final release acceptance retain separate project-owner authority. The approved baseline is available on `develop`; runtime/integration/security/browser acceptance is not established, EV-001/002 remain pending and EV-003 awaits measurement.

## How to use

1. Read [Discovery scope](01-discovery/01-product-vision-and-scope.md), [PRD handoff](01-discovery/08-prd-handoff.md) and [approved PRD](02-requirements/09-product-requirements.md) before changing scope. Canonical UC/BR/AC/CONS/A/OQ/DEC/BP/EV identifiers remain in their source documents.
2. Begin review with the [decision register](6-governance/25-design-decision-register.md). Distinguish selected mechanisms from implementation/verification, and verify EV-001/002 before selecting actual deployment resources.
3. Use the [backlog](02-requirements/10-product-backlog.md) for planning; use the explicitly supplied six-person assignment and readiness matrix. Follow approved Java APIs/ownership and selected locking, idempotency and pagination designs; obtain amendments only for genuine incompatibilities.
4. Implement the [frozen REST API](03-design/17-api-contracts.md) and separate Java contracts without routine reapproval; use Swagger UI below to browse the YAML. Preserve the frozen operation/status/schema/security and Java capability contracts; incompatible changes require a scoped amendment.
5. Track implementation in the [traceability matrix](02-requirements/11-requirements-traceability.md) and collect real runtime evidence through the [test strategy](05-verification/22-test-strategy-and-acceptance.md) and [release checklist](05-verification/23-uat-and-release-checklist.md).

New contributor reading path: [root README](../README.md) → [AGENTS.md](../AGENTS.md)
→ approved use cases/rules below → architecture and the
[module collaboration guide](03-design/module-contracts-and-boundaries.md) →
[shared contracts](03-design/shared-error-money-contracts.md) → the relevant API/backlog items.
Shared error/VND contracts, architecture checks and BK-003 compile-time module
interfaces/immutable DTOs are implemented. The canonical module guide lists exact
source signatures, owners, compatibility and [change control](03-design/module-contracts-and-boundaries.md#how-to-start-a-business-module).
The current Java contracts and dependency/posting ownership policies are APPROVED;
provider implementations, runtime
banking workflows and functional HTTP APIs are not implemented.

## Navigation: requirements to engineering documents

| Topic | Existing documents |
|---|---|
| Business vocabulary, actors and behavior | [02 glossary](01-discovery/02-domain-glossary.md), [03 actors](01-discovery/03-actors.md), [04 use cases/ACs](01-discovery/04-use-cases.md), [05 processes](01-discovery/05-business-processes.md), [06 rules/CONS](01-discovery/06-business-rules.md), [07 decisions](01-discovery/07-assumptions-and-questions.md) |
| Scope, requirements and delivery | [09 PRD](02-requirements/09-product-requirements.md), [10 backlog](02-requirements/10-product-backlog.md), [11 traceability](02-requirements/11-requirements-traceability.md), [12 functional specifications](02-requirements/12-functional-specifications.md) |
| User flows | [14 screen inventory and browser E2E](03-design/14-ux-user-flows-and-e2e.md) |
| Architecture and module boundaries | [15 system architecture](03-design/15-system-architecture.md) |
| Data, atomicity, locking, idempotency and history ordering | [16 domain/data design](03-design/16-domain-model-and-data-design.md) |
| External API contract | [17 API workflow/contracts](03-design/17-api-contracts.md), [OpenAPI source](03-design/contracts/openapi-v1.yaml) |
| Implemented logging, HTTP errors and tracing | [Logging/error/tracing standard](03-design/logging-and-error-handling.md) |
| Implemented shared errors and integer VND | [Shared error/money guide](03-design/shared-error-money-contracts.md) |
| Approved Java contracts, implemented boundary checks and change control | [Canonical module collaboration guide](03-design/module-contracts-and-boundaries.md) |
| Optional six-service observability stack and local credentials | [Observability setup and verification](../observability/README.md) |
| Prometheus metrics, dashboard and Alertmanager | [Metrics and alerting](../observability/metrics-and-alerts.md) |
| Observability review and deployment limitations | [Architecture review](../observability/architecture-review.md) |
| Quality and performance | [PRD §9](02-requirements/09-product-requirements.md), [22 verification](05-verification/22-test-strategy-and-acceptance.md) |
| Release and decision gates | [23 UAT/release checklist](05-verification/23-uat-and-release-checklist.md), [25 technical decisions](6-governance/25-design-decision-register.md) |

## Required V1 scope (no silent expansion)

- P0: CUSTOMER and ADMIN; one zero-balance VND CUSTOMER Account per registrant; one finite TREASURY initialized once with GENESIS; authenticated sessions; internal Customer transfers and demo allocations; signed-entry ledger, atomic postings/outcomes, immutable history/receipts, freeze/unfreeze with required atomic Admin audit, responsive Web and repeatable cloud delivery.
- P1 only **after** P0 acceptance: notifications, advanced filtering/metrics, separately justified extended scaling work. There is **no mandatory event broker** and no separate notification service in mandatory V1.
- Explicitly excluded: real funds/bank rails, full General Ledger, loans, cards, FX, additional account types/states, mint/reset/overwrites via normal APIs, real-world KYC/AML, native mobile, production HA guarantees.
- **Initial approved architecture direction** A-013 / DEC-006: Java/Spring Boot, PostgreSQL, domain-oriented modular monolith, secure server-managed sessions. The confirmed direction also includes DDD-inspired modules, pragmatic Hexagonal Architecture, Spring constructor DI, REST/OpenAPI/Swagger UI and in-process calls. Detailed mechanisms now follow the scoped 2026-10-22 engineering freeze; runtime proof remains pending.
- **Provisional target** EV-003: transfer-request p95 ≤2s at 10 new requests/s for five minutes, with **actual** environment and correctness evidence required; no measured outcome is claimed.

## Folder map

This map covers documentation tooling and the handoff pack. See the
[root project map](../README.md#project-structure-and-documentation) for implemented
backend and observability paths.

```text
finfast/
├── compose.docs.yaml                 # Local Swagger UI only
├── .github/workflows/ci.yml          # Backend tests/package and documentation CI
├── scripts/
│   ├── check_docs.py                 # Local Markdown links and OpenAPI refs
│   └── requirements-docs.txt         # Pinned documentation dependencies
└── docs/
    ├── README.md
    ├── 01-discovery/                 # 01–08 approved business discovery/handoff
    ├── 02-requirements/              # 09 approved PRD; 10–12 delivery/derived specification
    ├── 03-design/                    # 14–17 UX, architecture, data and API
    │   └── contracts/openapi-v1.yaml # Frozen V1 external API source of truth
    ├── 05-verification/              # 22 test strategy; 23 release checklist
    └── 6-governance/                 # 25 decision register
```

**Planned, not present:** standalone 13 quality-attributes, 18 P1 event contracts, 19–21 ADRs (ADR-001..003), 24 cloud deployment runbook, and a machine-readable coverage report. Current quality/benchmark requirements are in PRD 09/22; boundary rationale is in 15; selected atomicity/idempotency/history implementation guidance is in 16; statuses are in 25; coverage is in 11. No missing file is a completed deliverable. P1 event contracts remain optional. Backend/database/observability foundations and BK-003 compile-time contracts exist; see the [root README](../README.md). Banking provider implementations remain unimplemented.

The [migration README](../backend/src/main/resources/db/migration/README.md) records
the concrete schema approvals from Step 4B.1. Historical proposal labels in design
documents do not override those scoped approvals or imply that posting algorithms,
financial APIs or the counter performance target have been implemented/validated.

## Browse the API locally (DOC-01)

Prerequisites: Docker Engine and the Compose plugin, an available localhost port 8081, and network access for the initial image pull. From the repository root:

```sh
docker compose -f compose.docs.yaml config --quiet
docker compose -f compose.docs.yaml up -d
```

Open **http://127.0.0.1:8081/**. The dedicated [Compose configuration](../compose.docs.yaml) pins the official `swaggerapi/swagger-ui:v5.32.15` image, binds only to `127.0.0.1`, and mounts the existing OpenAPI file read-only. It requires neither a banking backend nor PostgreSQL. Request execution is disabled for this design viewer; the server address in the contract remains an explicit placeholder. Remote validator calls are disabled; validation runs locally/CI.

After editing the YAML, restart the viewer (the image copies the mounted file into its served assets at startup), then refresh the browser:

```sh
docker compose -f compose.docs.yaml restart swagger-ui
```

Stop and remove this documentation container/network:

```sh
docker compose -f compose.docs.yaml down
```

If startup fails, inspect `docker compose -f compose.docs.yaml logs swagger-ui` and port availability. Do not expose the viewer publicly to resolve a local port conflict. Usage follows the official [Swagger UI Docker instructions](https://swagger.io/docs/open-source-tools/swagger-ui/usage/installation/) and [configuration options](https://swagger.io/docs/open-source-tools/swagger-ui/usage/configuration/).

## Validate documentation locally and in CI

Use Python 3.12+ and an isolated environment; from the repository root:

```sh
python3 -m venv /tmp/finfast-docs-venv
/tmp/finfast-docs-venv/bin/python -m pip install -r scripts/requirements-docs.txt
/tmp/finfast-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml
/tmp/finfast-docs-venv/bin/python scripts/check_docs.py
docker compose -f compose.docs.yaml config --quiet
git diff --check
```

The `docs-validation` job in the consolidated [CI workflow](../.github/workflows/ci.yml)
runs on every PR targeting `develop`/`main`, every push to those branches and manual
dispatch, with no path filters. It preserves schema validation, local Markdown
path/heading checks, all local OpenAPI JSON-pointer checks (including unused
components), duplicate YAML-key checks and Compose configuration validation.
[Pinned tools](../scripts/requirements-docs.txt) and Python 3.12 are unchanged;
official actions now use immutable SHA pins with upstream release comments.
Documentation errors fail the job. Upgrade pins through review and rerun the
checks. The [checker](../scripts/check_docs.py) does not fetch remote links or prove
runtime banking behavior. Browser rendering and Mermaid checks are separate
review checks, not claimed as jobs in this workflow.

The checker covers `README.md` and `docs/**/*.md`, including local links/anchors
and OpenAPI references. It does not scan AGENTS, the migration README or observability
Markdown as source documents; include those in broader repository reviews. Historical
audit coverage does not extend the checked-in CI checker's scope.
The previous docs-only workflow is retired without a duplicate validation job.
See the [CI guide](../README.md#continuous-integration) for backend reproduction,
failure diagnosis, exact proposed required checks and ruleset setup for both
branches. Select checks only after a successful GitHub run confirms their names;
source implementation, local verification, remote evidence and enforcement are
[tracked separately](05-verification/22-test-strategy-and-acceptance.md#ci-foundation-implementation-and-evidence).
Frontend validation waits for Sĩ's actual stack; no framework is assumed.

The approved frozen OpenAPI YAML is the single V1 external contract source of truth. Future Springdoc integration must follow [17 — Spring Boot implementation guidance](03-design/17-api-contracts.md#future-spring-boot-integration); no Springdoc dependency or second maintained specification is introduced now.

Historical review coverage is recorded in the [decision register](6-governance/25-design-decision-register.md#documentation-audit-coverage--2026-10-22).
Consult its [implementation exceptions](6-governance/25-design-decision-register.md#explicit-exceptions-and-implementation-findings)
before integrating the affected History, Admin or reconciliation capabilities.

## Review/approval gates

1. **Implementation baseline:** Tiến approves existing PRD 09 requirements, specification 12, UX 14 and REST 17/YAML, including browser checks and the unchanged benchmark method. No routine reapproval is needed. Future banking-policy changes and final release acceptance retain their separate project-owner authority.
2. **External feasibility:** verify EV-001 rubric and EV-002 cloud feasibility; if course requirements conflict, obtain explicit project owner disposition before final topology.
3. **Architecture:** TD-015/016 and current BK-003 Java signatures are explicitly approved by Tiến; BK-003 is already merged; follow the active engineering baseline and obtain prior Tech Lead approval for new capabilities/edges or breaking amendments. TD-001..014 have the freeze's scoped approvals; deployment resources, runtime proofs and benchmark results remain pending. Standalone ADR-001..003 remain planned; the Product/UX/REST freeze approves the exact existing YAML semantics, separately from Java contract approval; API-ERR-01 and missing read capabilities remain scoped integration exceptions.
4. **Delivery:** collect execution evidence for all FR-001..013, UX-001..005, NFR-001..006, 38 canonical ACs and 19 CONS risk views; verify financial invariants under retries/failures.
5. **Performance/release:** measure EV-003 on the recorded environment and document deviations; the project owner decides acceptance.

## Known implementation hazards (read before coding)

- Browser JSON VND **strings** and the transactional global financial commit sequence are approved V1 designs under TD-001/004. They do not alter approved integral-VND business semantics. Measure the sequence-lock's throughput against EV-003.
- History snapshots need true commit-order stability; naive `OFFSET` or `(timestamp,id)` alone may omit late-committing rows.
- A final business rejection is durable for the dataset lifetime, just like completed intent outcome. A technical failure is **not** a final rejection. Check authorization before replay or lookup.
- Funding success **audit**, final intent result, signed ledger entries, balance deltas and transaction reference must commit together. Effective freeze and its complete success audit must also commit together.
- Never expose ordinary GENESIS/replenishment/reset/mint APIs. Dataset recreation is a controlled maintenance operation, not a third banking role.

The full business requirements and canonical acceptance wording remain in `01-discovery/` and `02-requirements/09-product-requirements.md`. The consolidated freeze approves their implementation-facing scope while preserving canonical banking meanings; it does not establish passing runtime tests or final release acceptance.
