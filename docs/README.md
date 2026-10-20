# FinFast V1 — PRD-to-Engineering Handoff Pack

> **Artifact status:** Approved **discovery baseline (01–08)**, **draft PRD 09**, agreed architecture direction, and explicitly marked engineering **proposals**. Refined 2026-10-18. Architecture details still require review; this is not a completed banking application, signed implementation plan, successful banking test run or cloud deployment. No new actors/banking policies/features are approved by generating these files.

## How to use

1. Read [Discovery scope](01-discovery/01-product-vision-and-scope.md), [PRD handoff](01-discovery/08-prd-handoff.md) and [draft PRD](02-requirements/09-product-requirements.md) before changing scope. Canonical UC/BR/AC/CONS/A/OQ/DEC/BP/EV identifiers remain in their source documents.
2. Begin review with the [decision register](6-governance/25-design-decision-register.md). Separate the Tech Lead's agreed architecture direction from proposed mechanisms, and verify EV-001/002 before deployment decisions.
3. Use the [backlog](02-requirements/10-product-backlog.md) for planning; assign people/dates only through team planning. Review module APIs, atomicity, locking, idempotency and pagination before implementation.
4. Review [API contracts](03-design/17-api-contracts.md) with frontend/backend contributors using Swagger UI below. Freeze reviewed contracts before parallel implementation.
5. Track implementation in the [traceability matrix](02-requirements/11-requirements-traceability.md) and collect real runtime evidence through the [test strategy](05-verification/22-test-strategy-and-acceptance.md) and [release checklist](05-verification/23-uat-and-release-checklist.md).

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
| Optional local Grafana, Alloy, Loki and Tempo | [Observability setup and verification](../observability/README.md) |
| Prometheus metrics, dashboard and Alertmanager | [Metrics and alerting](../observability/metrics-and-alerts.md) |
| Observability review and deployment limitations | [Architecture review](../observability/architecture-review.md) |
| Quality and performance | [PRD §9](02-requirements/09-product-requirements.md), [22 verification](05-verification/22-test-strategy-and-acceptance.md) |
| Release and decision gates | [23 UAT/release checklist](05-verification/23-uat-and-release-checklist.md), [25 technical decisions](6-governance/25-design-decision-register.md) |

## Required V1 scope (no silent expansion)

- P0: CUSTOMER and ADMIN; one zero-balance VND CUSTOMER Account per registrant; one finite TREASURY initialized once with GENESIS; authenticated sessions; internal Customer transfers and demo allocations; signed-entry ledger, atomic postings/outcomes, immutable history/receipts, freeze/unfreeze with required atomic Admin audit, responsive Web and repeatable cloud delivery.
- P1 only **after** P0 acceptance: notifications, advanced filtering/metrics, separately justified extended scaling work. There is **no mandatory event broker** and no separate notification service in mandatory V1.
- Explicitly excluded: real funds/bank rails, full General Ledger, loans, cards, FX, additional account types/states, mint/reset/overwrites via normal APIs, real-world KYC/AML, native mobile, production HA guarantees.
- **Initial approved architecture direction** A-013 / DEC-006: Java/Spring Boot, PostgreSQL, domain-oriented modular monolith, secure server-managed sessions. The confirmed direction also includes DDD-inspired modules, pragmatic Hexagonal Architecture, Spring constructor DI, REST/OpenAPI/Swagger UI and in-process calls. Detailed mechanisms remain proposals.
- **Provisional target** EV-003: transfer-request p95 ≤2s at 10 new requests/s for five minutes, with **actual** environment and correctness evidence required; no measured outcome is claimed.

## Folder map

```text
finfast/
├── compose.docs.yaml                 # Local Swagger UI only
├── .github/workflows/docs.yml        # Documentation CI
├── scripts/
│   ├── check_docs.py                 # Local Markdown links and OpenAPI refs
│   └── requirements-docs.txt         # Pinned documentation dependencies
└── docs/
    ├── README.md
    ├── 01-discovery/                 # 01–08 approved business discovery/handoff
    ├── 02-requirements/              # 09 draft PRD; 10–12 engineering decomposition
    ├── 03-design/                    # 14–17 UX, architecture, data and API
    │   └── contracts/openapi-v1.yaml # Design-stage API source of truth
    ├── 05-verification/              # 22 test strategy; 23 release checklist
    └── 6-governance/                 # 25 decision register
```

**Planned, not present:** standalone 13 quality-attributes, 18 P1 event contracts, 19–21 ADRs (ADR-001..003), 24 cloud deployment runbook, and a machine-readable coverage report. Current quality/benchmark requirements are in PRD 09/22; boundary rationale is in 15; atomicity/idempotency/history proposals are in 16; statuses are in 25; coverage is in 11. No missing file is a completed deliverable. P1 event contracts remain optional. The backend/database and observability foundations now exist; see the [root README](../README.md) for implemented behavior. Banking business modules remain unimplemented.

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

The [CI workflow](../.github/workflows/docs.yml) runs schema validation, local Markdown path/heading checks, all local OpenAPI JSON-pointer checks (including unused components), duplicate YAML-key checks and Compose configuration validation on relevant pushes/PRs or manual dispatch. [Pinned tools](../scripts/requirements-docs.txt) include `openapi-spec-validator` and `markdown-it-py`; image/action releases are also version pinned. Upgrade pins through review and rerun the checks. The [checker](../scripts/check_docs.py) does not fetch remote links or prove runtime banking behavior. Browser rendering and Mermaid checks are separate review checks, not claimed as jobs in this workflow.

The OpenAPI YAML remains the design-stage source of truth. Future Springdoc integration must follow [17 — Spring Boot implementation guidance](03-design/17-api-contracts.md#future-spring-boot-integration); no Springdoc dependency or second maintained specification is introduced now.

## Review/approval gates

1. **Product:** project owner reviews PRD 09 (currently *draft*, not approved) and any proposed elaborations; baseline 01–08 records its own approved decisions.
2. **External feasibility:** EV-001 rubric and EV-002 cloud feasibility verified; if course requirements conflict, obtain explicit project owner disposition before final topology.
3. **Architecture:** Tech Lead reviews TD-001..016; standalone ADR-001..003 remain planned; API/OpenAPI contracts reviewed together by frontend/backend and test owners.
4. **Delivery:** execution evidence for all FR-001..013, UX-001..005, NFR-001..006, 38 canonical ACs and 19 CONS risk views collected; verify financial invariants under retries/failures.
5. **Performance/release:** EV-003 measured on recorded environment, deviations documented; project owner decides acceptance.

## Known implementation hazards (read before coding)

- Browser JSON VND **strings** and a global financial commit sequence are technical proposals. They do not alter approved integral-VND business semantics. Measure the sequence-lock's throughput against EV-003.
- History snapshots need true commit-order stability; naive `OFFSET` or `(timestamp,id)` alone may omit late-committing rows.
- A final business rejection is durable for the dataset lifetime, just like completed intent outcome. A technical failure is **not** a final rejection. Check authorization before replay or lookup.
- Funding success **audit**, final intent result, signed ledger entries, balance deltas and transaction reference must commit together. Effective freeze and its complete success audit must also commit together.
- Never expose ordinary GENESIS/replenishment/reset/mint APIs. Dataset recreation is a controlled maintenance operation, not a third banking role.

The full business requirements and canonical acceptance wording remain in `01-discovery/` and `02-requirements/09-product-requirements.md`. Engineering proposals do not replace them.
