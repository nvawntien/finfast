# Documentation Inventory

**Classification key:**
**APPROVED BASELINE** means the document is an approved source of product, contract, architecture, or decision intent.
**HISTORICAL EVIDENCE** means it records a dated claim that was not rerun in this audit.
**SOURCE STATUS** means the audit compared it with tracked files at e3dc6ef; it is not runtime proof.

The complete file-by-file transition proposal, including future authority, dependencies, and link risks, is in documentation-disposition-matrix.md. This inventory retains the original audit evidence summary.

## Long-term disposition vocabulary

The proposed matrix uses LEGACY_ARCHIVE, MIGRATE_TO_OPENSPEC, MIGRATE_TO_DURABLE_DOCS, RETAIN_AS_EXTERNAL_CONTRACT, RETAIN_AS_HISTORICAL_EVIDENCE, and REQUIRES_HUMAN_DECISION. These are planning dispositions, not approvals or move instructions. Mixed documents are split by concern: behavioral content may migrate incrementally, while approval provenance, historical evidence, external contracts, and durable architecture remain in their respective authorities.

## Tracked documentation and authority

| Document group | Classification | Baseline role and reconciliation result |
|---|---|---|
| docs/01-discovery/01-08 | APPROVED BASELINE | Product scope, actors, use cases, business rules, assumptions, and handoff history. Preserve the canonical UC, BR, AC, and CONS identifiers. |
| docs/02-requirements/09-12 | APPROVED BASELINE | PRD, backlog, traceability, and functional specifications. They remain requirements and delivery authorities, not proof of implementation. |
| docs/02-requirements/bk-001-* and evidence images | HISTORICAL / EXTERNAL | Course context and images remain outside implementation and OpenSpec authority pending EV-001. |
| docs/03-design/14-17 and contracts/openapi-v1.yaml | APPROVED BASELINE | UX, architecture, data design, API workflow, and frozen external REST contract. OpenAPI remains the single V1 REST source. |
| module-contracts-and-boundaries.md and shared-error-money-contracts.md | APPROVED BASELINE / SOURCE STATUS | Approved module contracts and shared contracts. Their statements that providers and banking workflows are not implemented align with the tracked baseline. |
| logging-and-error-handling.md and observability documents | SOURCE STATUS | Technical foundation and operations guidance. They do not prove deployed telemetry or banking workflow behavior. |
| docs/05-verification/22-test-strategy-and-acceptance.md | APPROVED BASELINE / HISTORICAL EVIDENCE | Test obligations plus dated execution records. The records are revision-scoped and were not reproduced in this audit. |
| docs/05-verification/23-uat-and-release-checklist.md | APPROVED BASELINE | Explicit unexecuted release gate. It correctly keeps provider, runtime, and EV evidence open. |
| docs/05-verification/bk-004-local-baseline-and-p1.md | HISTORICAL EVIDENCE | Dated delivery and verification context. It must not be elevated to current runtime proof. |
| docs/6-governance/25-design-decision-register.md | APPROVED BASELINE / HISTORICAL EVIDENCE | Canonical decision authority. Its dated tests and implementation checkpoints retain their own scope and do not make absent providers present. |
| Root README.md, docs/README.md, migration README, AGENTS.md | SOURCE STATUS | Onboarding, routing, schema, and policy. The statements that banking provider composition and functional banking APIs remain unimplemented are consistent with this commit. |

## Documentation-to-source alignment

The approved documents intentionally separate selected designs from the later work needed to compose providers and expose banking operations. The following sources are present without making the corresponding end-user feature complete:

- Registration has RegisterCustomer and supporting adapters, but no registration HTTP adapter or production composition.
- History and receipt query services are Ledger-local source, but no History module root, production composition, or HTTP adapters.
- Allocation and status-change services are tracked, but no write HTTP adapters or production provider composition.
- Bootstrap source exists behind an opt-in configuration, but no protected operator/provider integration.
- TransferPostingService is a Ledger collaborator, not a Transfer application feature.

This is not a conflict between the documents and the tracked baseline. It is the intended distinction between approved architecture/application code and missing provider/runtime integration.

## Local-only artifacts excluded from this inventory

The following artifacts were named in the prior reports because they existed in the other worktree's untracked overlay. They are absent from the tracked baseline and are not documentation or implementation evidence here:

- docs/03-design/contracts/financial-protocol-errors-amendment.md
- docs/05-verification/financial-workflow-implementation-evidence.md
- docs/05-verification/transfer-benchmark-runbook.md
- scripts/benchmark_finfast.py and related benchmark tests
- openspec/ and .agents/ OpenSpec skills
- Transfers and Idempotency implementation/test trees

Their provenance, intended integration, and ownership are unresolved. Preserve the observation as a separate local-overlay note, not as a baseline document status.
