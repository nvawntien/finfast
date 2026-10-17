# FinFast V1 — PRD-to-Engineering Handoff Pack

> **Artifact status:** Working engineering **proposals** derived from the uploaded and approved **discovery baseline (01–08)** and **draft PRD 09**. Generated 2026-10-17. This pack is **not** a completed application, architecture approval, implementation plan signed by the team, successful test run or cloud deployment. No new actors/banking policies/features are approved by generating these files.

## How to use

1. **Read `baseline/01..08` and `baseline/09`** before changing scope. Canonical UC, BR, AC, CONS, A, OQ, DEC, BP and EV identifiers remain there.
2. Begin engineering review with `outputs/25-design-decision-register.md`, verify EV-001 and EV-002, and review ADRs. All technical detail beyond the source is labeled **PROPOSED**.
3. Use `outputs/10-product-backlog.md` to create issues/epics, but assign people/dates only after team planning and design approvals.
4. Review API/domain/cookie/locking/pagination choices in a short design workshop. Freeze versioned contracts for parallel frontend/backend work; do not implement unratified proposals as if approved policy.
5. Implement vertical slices with domain/DB integration tests and track requirements in `outputs/11-requirements-traceability.md`.
6. Use `outputs/22-test-strategy-and-acceptance.md` and `outputs/23-uat-and-release-checklist.md` to collect **real runtime evidence** before acceptance.

## Mapping: PRD input → engineering outputs

| PRD input | Resulting deliverable(s) |
|---|---|
| Scope and priorities | [10 — Product backlog, epics, user stories, issues and dependencies](outputs/10-product-backlog.md) |
| Functional requirements | [12 — Functional specifications](outputs/12-functional-specifications.md), [10 — backlog](outputs/10-product-backlog.md) |
| Business rules | [16 — Domain/data model](outputs/16-domain-model-and-data-design.md), [20 — atomicity/idempotency ADR](outputs/20-adr-financial-atomicity-and-idempotency.md), [22 — tests](outputs/22-test-strategy-and-acceptance.md) |
| Non-functional requirements | [13 — Quality attributes & benchmark](outputs/13-quality-attributes.md), [15 — architecture](outputs/15-system-architecture.md), [24 — cloud runbook](outputs/24-cloud-deployment-and-ci-runbook.md) |
| Journeys and UX | [14 — Screen inventory, wireframes and browser E2E](outputs/14-ux-user-flows-and-e2e.md) |
| Data and domain | [16 — Domain model, ERD, schema/index/locking design](outputs/16-domain-model-and-data-design.md) |
| Integration contracts | [17 — API contract](outputs/17-api-contracts.md), [OpenAPI 3.0 proposal](outputs/contracts/openapi-v1.yaml), [18 — P1-only event schema](outputs/18-event-contracts-p1.md) |
| Risks and constraints | [19 — service-boundary ADR](outputs/19-adr-service-boundaries.md), [20 — posting/idempotency ADR](outputs/20-adr-financial-atomicity-and-idempotency.md), [21 — history ADR](outputs/21-adr-history-continuation.md), [25 — decision register](outputs/25-design-decision-register.md) |
| Release acceptance | [22 — test plan](outputs/22-test-strategy-and-acceptance.md), [23 — UAT/release checklist](outputs/23-uat-and-release-checklist.md), [24 — deployment/CI runbook](outputs/24-cloud-deployment-and-ci-runbook.md) |
| Cross-cutting traceability | [11 — requirement/AC/BR matrix](outputs/11-requirements-traceability.md), [machine-readable coverage summary](outputs/contracts/coverage-report.json) |

## Required V1 scope (no silent expansion)

- P0: CUSTOMER and ADMIN; one zero-balance VND CUSTOMER Account per registrant; one finite TREASURY initialized once with GENESIS; authenticated sessions; internal Customer transfers and demo allocations; signed-entry ledger, atomic postings/outcomes, immutable history/receipts, freeze/unfreeze with required atomic Admin audit, responsive Web and repeatable cloud delivery.
- P1 only **after** P0 acceptance: notifications, advanced filtering/metrics, separately justified extended scaling work. There is **no mandatory event broker** and no separate notification service in mandatory V1.
- Explicitly excluded: real funds/bank rails, full General Ledger, loans, cards, FX, additional account types/states, mint/reset/overwrites via normal APIs, real-world KYC/AML, native mobile, production HA guarantees.
- **Initial approved architecture direction** A-013 / DEC-006: Java/Spring Boot, PostgreSQL, domain-oriented modular monolith, secure server-managed sessions. This pack proposes a concrete realization and leaves unapproved details for decision.
- **Provisional target** EV-003: transfer-request p95 ≤2s at 10 new requests/s for five minutes, with **actual** environment and correctness evidence required; no measured outcome is claimed.

## Folder map

```text
finfast-v1-engineering-handoff/
  baseline/            # Exact provided documents 01–09, normalized filenames; unchanged contents
  outputs/
    10-product-backlog.md
    11-requirements-traceability.md
    12-functional-specifications.md
    13-quality-attributes.md
    14-ux-user-flows-and-e2e.md
    15-system-architecture.md
    16-domain-model-and-data-design.md
    17-api-contracts.md
    18-event-contracts-p1.md
    19-adr-service-boundaries.md
    20-adr-financial-atomicity-and-idempotency.md
    21-adr-history-continuation.md
    22-test-strategy-and-acceptance.md
    23-uat-and-release-checklist.md
    24-cloud-deployment-and-ci-runbook.md
    25-design-decision-register.md
    contracts/
      openapi-v1.yaml
      coverage-report.json
```

## Review/approval gates

1. **Product:** project owner reviews PRD 09 (currently *draft*, not approved) and any proposed elaborations; baseline 01–08 records its own approved decisions.
2. **External feasibility:** EV-001 rubric and EV-002 cloud feasibility verified; if course requirements conflict, obtain explicit project owner disposition before final topology.
3. **Architecture:** team ratifies TD-001..014 + ADR-001..003; API/OpenAPI contracts reviewed together by frontend/backend and test owners.
4. **Delivery:** execution evidence for all FR-001..013, UX-001..005, NFR-001..006, 38 canonical ACs and 19 CONS risk views collected; verify financial invariants under retries/failures.
5. **Performance/release:** EV-003 measured on recorded environment, deviations documented; project owner decides acceptance.

## Known implementation hazards (read before coding)

- Browser JSON VND **strings** and a global financial commit sequence are technical proposals. They do not alter approved integral-VND business semantics. Measure the sequence-lock's throughput against EV-003.
- History snapshots need true commit-order stability; naive `OFFSET` or `(timestamp,id)` alone may omit late-committing rows.
- A final business rejection is durable for the dataset lifetime, just like completed intent outcome. A technical failure is **not** a final rejection. Check authorization before replay or lookup.
- Funding success **audit**, final intent result, signed ledger entries, balance deltas and transaction reference must commit together. Effective freeze and its complete success audit must also commit together.
- Never expose ordinary GENESIS/replenishment/reset/mint APIs. Dataset recreation is a controlled maintenance operation, not a third banking role.

The full business requirements and canonical acceptance scenario wording remain in `baseline/`. This pack does not replace them.
