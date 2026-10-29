# Knowledge Migration Plan

**Status:** PROPOSED. This plan transfers authority incrementally. It does not approve a capability, initialize OpenSpec, move legacy files, or create a harness.

## Transition rules

- Before formal transfer, existing approved behavioral documents are the operative baseline.
- After explicit transfer, the reviewed OpenSpec main specification is the maintained behavioral authority for that capability.
- OpenAPI, Java contracts, architecture/data ownership, financial enforcement constraints, technical decisions, verification evidence, and release acceptance remain governed by their durable sources.
- The registry records references, status, approval, and limitations. It never copies full requirements or establishes approval automatically.
- Legacy material remains preserved and reachable until a successor/link transition is reviewed and reversible.

## Staged migration

| Stage | Goal, inputs, and intended outputs | Review, validation, and rollback |
|---|---|---|
| 0. Finalize audit and governance decisions | Inputs: this audit, tracked baseline evidence, RD-07/RD-09. Output: recorded human governance disposition. | Team/Tech Lead review. No migration changes. If not approved, stop after Session 1. |
| 1. Session 2A knowledge foundation | Inputs: approved RD-07/RD-09. Output: docs/INDEX.md, governance authority/workflow pages, migration registry, legacy-to-successor map, minimal routing. | Documentation checks and scope review. Revert one additive governance PR. No OpenSpec or capability content. |
| 2. Session 2B durable knowledge curation | Inputs: Session 2A foundation. Output: curated product, architecture, development, verification, contract-navigation, and governance pages. | Affected-owner review and source-status checks. Revert individual curation PRs; legacy paths remain. |
| 3. Session 2C OpenSpec adoption | Inputs: merged authority policy and RD-07 approval. Output: validated empty CLI-generated OpenSpec root/configuration and convention links. | Team review, installed CLI validation, documentation checks. Revert isolated initialization PR. No spec is seeded. |
| 4. Subsequent pilot selection | Inputs: Session 2C and an explicit capability decision. Output: reviewed pilot charter selecting baseline migration or normal change workflow. | Product/module/API owner review. No pilot artifact until the charter is accepted. |
| 5. Incremental OpenSpec expansion | Inputs: successful pilot evidence and per-capability transfer approval. Output: one capability transfer/change at a time with registry updates and provenance links. | OpenSpec/document validation plus change-specific verification. Roll back registry state and retain legacy source if needed. |
| 6. Formalize team documentation procedure | Inputs: observed migration/pilot lessons. Output: reviewed durable workflow refinements, not a duplicate decision log. | Governance review. Revert policy refinements if they create duplicate authority. |
| 7. Later harness implementation and recovery test | Inputs: merged knowledge system, real change to exercise it, separate harness approval. Output: only approved minimal harness additions and observed recovery evidence. | Dedicated harness PR and real-change verification. Revert harness independently. |
| 8. Team rollout | Inputs: reviewed migration/harness results. Output: documented onboarding, PR/CI adoption guidance, and measured improvements. | Team/GitHub process review. Do not replace branch protection or CI without separate authorization. |

## Deferred pilot candidate

Customer-account-overview remains a candidate, not a selected pilot. The approved sources include UC-ACC-01 and FR-003, the frozen contract has GET /api/v1/accounts/me, and source/test definitions exist. These are eligibility inputs only. They do not prove runtime composition, successful execution, release acceptance, or that baseline migration is preferable to a normal future change.

## Non-goals

- No mass conversion of legacy documents into OpenSpec.
- No canonicalization of implementation-only assumptions or historical test claims as business requirements.
- No OpenSpec capability seeding during Session 2A or 2C.
- No application implementation, frozen-contract change, CI replacement, or harness installation as part of the knowledge foundation.
