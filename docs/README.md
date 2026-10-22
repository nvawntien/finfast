# FinFast V1 — PRD-to-Engineering Handoff Pack

> **Artifact status — 2026-10-22:** Discovery 01–08, PRD 09, derived specification 12, UX 14, API 17/existing OpenAPI, BK-003 Java contracts and TD-001..016 are APPROVED implementation baselines within their recorded scopes. [Consolidated Product/UX/REST approval](6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze). Tiến authorizes implementation-baseline approval; future banking-policy changes and final release acceptance retain separate project-owner authority. BK-003 is merged at `8a04ca0`; runtime/integration/security/browser acceptance is not established, EV-001/002 remain pending and EV-003 awaits measurement.

## How to use

1. Read [Discovery scope](01-discovery/01-product-vision-and-scope.md), [PRD handoff](01-discovery/08-prd-handoff.md) and [approved PRD](02-requirements/09-product-requirements.md) before changing scope. Canonical UC/BR/AC/CONS/A/OQ/DEC/BP/EV identifiers remain in their source documents.
2. Begin review with the [decision register](6-governance/25-design-decision-register.md). Distinguish selected mechanisms from implementation/verification, and verify EV-001/002 before selecting actual deployment resources.
3. Use the [backlog](02-requirements/10-product-backlog.md) for planning; use the explicitly supplied six-person assignment and readiness matrix. Follow approved Java APIs/ownership and selected locking, idempotency and pagination designs; obtain amendments only for genuine incompatibilities.
4. Implement the [frozen REST API](03-design/17-api-contracts.md) and separate Java contracts without routine reapproval; use Swagger UI below to browse the YAML. Only OpenAPI approval metadata changes; no operation/status/schema/security or Java capability changes are authorized.
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

The checker covers `README.md` and `docs/**/*.md`, retaining the existing link/
anchor and OpenAPI semantics. Including the root README also checks its new CI
source links. This freeze separately checked outgoing local links for all 29 tracked Markdown files using the existing parser; that broader one-off audit does not extend the checked-in CI checker's scope.
The previous docs-only workflow is retired without a duplicate validation job.
See the [CI guide](../README.md#continuous-integration) for backend reproduction,
failure diagnosis, exact proposed required checks and ruleset setup for both
branches. Select checks only after a successful GitHub run confirms their names;
source implementation, local verification, remote evidence and enforcement are
[tracked separately](05-verification/22-test-strategy-and-acceptance.md#ci-foundation-implementation-and-evidence).
Frontend validation waits for Sĩ's actual stack; no framework is assumed.

The approved frozen OpenAPI YAML is the single V1 external contract source of truth. Future Springdoc integration must follow [17 — Spring Boot implementation guidance](03-design/17-api-contracts.md#future-spring-boot-integration); no Springdoc dependency or second maintained specification is introduced now.

## Documentation audit coverage — 2026-10-22

**Historical engineering-review coverage:** this table records the earlier engineering-only synchronization. Its reference to then-draft product/UX scope is historical; the later consolidated approval above now freezes those existing artifacts. The present follow-up is targeted approval recording and validation, not a repeat of the comprehensive architecture audit.

The freeze review covered all **29 tracked Markdown files**, the existing OpenAPI, checked-in build/configuration, Java API/capability sources, tests and Flyway V1/V2; source/configuration is implementation evidence, not permission to edit it. There are no checked-in frontend implementation documents/stack to infer. Historical local test records retain their original date/scope.

| Area | Reviewed files | Synchronization outcome |
|---|---|---|
| Entry/contributor routing (3) | Root README, AGENTS, this index | Current BK-003 merge status, scoped freeze, member workflow and evidence boundaries |
| Approved discovery (8) | 01 vision, 02 glossary, 03 actors, 04 use cases, 05 processes, 06 rules, 07 decisions, 08 handoff | 02–05 unchanged; canonical UC/BR/AC/CONS and historical A/OQ/DEC unchanged. 01 restores the omitted GENESIS term and removes an unsupported parenthetical claim of verified course requirements; 06–08 receive current technical routing only |
| Requirements/delivery (4) | 09 PRD, 10 backlog, 11 traceability, 12 functional specifications | Scoped benchmark method approval, exact six-person assignment/readiness, BK-010 phases/FIX-001 and design-versus-execution traceability; no workload/threshold or canonical requirement change |
| Design/contracts (7) | 14 UX, 15 architecture, 16 data, 17 API, module guide, shared-error-money guide, logging guide | Selected mechanisms, unchanged Java/OpenAPI baseline, security/transaction/recovery guidance and explicit missing read-capability integrations; UX/product draft scope retained |
| Verification/governance (3) | 22 strategy, 23 release checklist, 25 decision register | Dated freeze/provenance, TD scopes/rationale/alternatives/owners/revisit criteria, Fineract evidence and required unexecuted runtime tests; historical BK-003 record retained |
| Persistence guide (1) | Backend migration README | Existing SQL guarantees versus cross-row/provider obligations, selected counter/advisory protocol and database text representability; migrations unchanged |
| Observability (3) | README, architecture-review, metrics-and-alerts | Existing local evidence/configuration retained; current pure API source status, topology approval versus deployment/benchmark proof clarified |

The decision register is the only approval authority; this inventory is coverage/routing. See its [explicit exceptions](6-governance/25-design-decision-register.md#explicit-exceptions-and-implementation-findings) before affected History/Admin integrations. No missing standalone ADR/runbook is claimed to exist, and no acceptance checkbox is completed by this audit.

## Review/approval gates

1. **Implementation baseline:** Tiến approves existing PRD 09 requirements, specification 12, UX 14 and REST 17/YAML, including browser checks and the unchanged benchmark method. No routine reapproval is needed. Future banking-policy changes and final release acceptance retain their separate project-owner authority.
2. **External feasibility:** EV-001 rubric and EV-002 cloud feasibility verified; if course requirements conflict, obtain explicit project owner disposition before final topology.
3. **Architecture:** TD-015/016 and current BK-003 Java signatures are explicitly approved by Tiến; BK-003 is already merged; follow the additional engineering freeze when it merges and obtain prior Tech Lead approval for new capabilities/edges or breaking amendments. TD-001..014 have the freeze's scoped approvals; deployment resources, runtime proofs and benchmark results remain pending. Standalone ADR-001..003 remain planned; the later Product/UX/REST freeze approves the exact existing YAML semantics, with metadata-only edits, separate from Java contract approval; API-ERR-01 and missing read capabilities remain scoped integration exceptions.
4. **Delivery:** execution evidence for all FR-001..013, UX-001..005, NFR-001..006, 38 canonical ACs and 19 CONS risk views collected; verify financial invariants under retries/failures.
5. **Performance/release:** EV-003 measured on recorded environment, deviations documented; project owner decides acceptance.

## Known implementation hazards (read before coding)

- Browser JSON VND **strings** and the transactional global financial commit sequence are approved V1 designs under TD-001/004. They do not alter approved integral-VND business semantics. Measure the sequence-lock's throughput against EV-003.
- History snapshots need true commit-order stability; naive `OFFSET` or `(timestamp,id)` alone may omit late-committing rows.
- A final business rejection is durable for the dataset lifetime, just like completed intent outcome. A technical failure is **not** a final rejection. Check authorization before replay or lookup.
- Funding success **audit**, final intent result, signed ledger entries, balance deltas and transaction reference must commit together. Effective freeze and its complete success audit must also commit together.
- Never expose ordinary GENESIS/replenishment/reset/mint APIs. Dataset recreation is a controlled maintenance operation, not a third banking role.

The full business requirements and canonical acceptance wording remain in `01-discovery/` and `02-requirements/09-product-requirements.md`. The consolidated freeze approves their implementation-facing scope while preserving canonical banking meanings; it does not establish passing runtime tests or final release acceptance.
