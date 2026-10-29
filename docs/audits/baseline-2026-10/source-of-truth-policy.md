# Source-of-Truth Transition Policy

**Status:** PROPOSED. No authority changes until RD-07 and RD-09 receive explicit human disposition.

## Policy rules

1. A capability remains governed by its existing approved behavioral sources until its migration record says AUTHORITY_TRANSFERRED and cites the approving review.
2. An approved OpenSpec main specification becomes the maintained behavioral authority only for that named capability. It cites rather than silently renumbers legacy identifiers.
3. Financial invariants are classified by concern: observable behavior, technical enforcement, external transport, and decision/approval. No one source type owns every invariant.
4. A conflict is reported with both source paths, identifiers, evidence class, and effect. It is not resolved by an agent or by overwriting one source.
5. Status is not behavior. Tracked source, provider composition, executed evidence, and release acceptance are separate fields in every migration record.

## Authority matrix

| Concern | Before formal migration | After capability migration | Supporting evidence, not the authority for this concern |
|---|---|---|---|
| Product scope | docs/01-discovery/01-product-vision-and-scope.md and docs/02-requirements/09-product-requirements.md | Durable product documents retain scope authority. | OpenSpec may reference scope limits. |
| Domain vocabulary | docs/01-discovery/02-domain-glossary.md | Curated docs/product glossary. | OpenSpec uses the terms without redefining them. |
| Behavioral specification | Use cases, rules, PRD, functional specifications, and UX sources for the capability. | Approved openspec/specs capability specification. | Legacy sources retain identifiers and provenance. |
| Proposed behavior change | Existing change control and decision register. | OpenSpec change proposal/specs/design/tasks, with existing approval authorities. | Backlog and PR descriptions. |
| REST/OpenAPI contract | docs/03-design/contracts/openapi-v1.yaml and docs/03-design/17-api-contracts.md | Unchanged unless separately amended through existing contract governance. | OpenSpec references operations, never becomes schema/status authority. |
| Java module contracts | provider api sources and docs/03-design/module-contracts-and-boundaries.md | Same durable authority. | OpenSpec can state dependency constraints relevant to behavior. |
| Architecture and data ownership | docs/03-design/15-system-architecture.md and docs/03-design/16-domain-model-and-data-design.md | Curated durable architecture pages, retaining target/as-built separation. | OpenSpec change design links to applicable constraints. |
| Financial transaction invariants | Observable rules are in approved behavioral sources; transaction ownership/enforcement is in architecture, module contracts, schema, and source; transport shape is in frozen OpenAPI; durable approval is in the decision register. | OpenSpec owns maintained observable behavior for a transferred capability. Architecture/module contracts retain enforcement ownership, OpenAPI retains transport constraints, and the register retains approvals. | Migrations, source, tests, and historical results are implementation/evidence only. |
| Technical decisions | docs/6-governance/25-design-decision-register.md | Same canonical register unless RD-09 explicitly changes that policy. | OpenSpec design explains impact but does not create a second record. |
| Implementation design | Existing design docs or an OpenSpec change design where the design is change-local. | Durable architecture docs for lasting cross-capability design; OpenSpec design for bounded change design. | Source comments and PR discussion. |
| Task completion | Backlog/team process and existing review practice. | OpenSpec tasks for that active change; broader planning stays in team tooling. | A task checklist is not release acceptance. |
| Implementation status | Tracked source plus explicitly dated status records. | Tracked source and migration registry status fields. | OpenSpec completion status. |
| Verification evidence | Test strategy, CI records, test artifacts, and documented environment. | Same durable verification authority. | OpenSpec links evidence but does not turn NOT_RUN into PASS. |
| Release acceptance | docs/05-verification/23-uat-and-release-checklist.md and project-owner authority. | Same authority. | Any OpenSpec artifact. |
| Agent execution state | AGENTS.md and current worktree/Git state. | Later harness local state plus worktree/Git state. | OpenSpec is not a scheduler. |
| Session handoff | Current PR/worktree/audit records. | Later harness handoff record, PR state, and migration registry where applicable. | Local JSON is advisory and not shared coordination. |

## Capability migration registry

Stage 3 may introduce docs/migration/capability-registry.yaml. It records only:

| Field | Purpose |
|---|---|
| capability_id | Stable, human-readable capability identifier. |
| legacy_references | Original UC, BR, FR, AC, CONS, UX, and document anchors. |
| openspec_location | Main spec and relevant active/archive change paths. |
| migration_status | NOT_STARTED, DRAFTED, REVIEWED, AUTHORITY_TRANSFERRED, ROLLED_BACK, or SUPERSEDED. |
| approval_reference | Existing decision-register, PR, or human-review reference. |
| source_status | TRACKED_SOURCE, LOCAL_ONLY, INFERRED, CONFLICTING, or UNKNOWN summary. |
| verification_limitations | Short links to NOT_RUN and historical-evidence constraints. |
| successor_links | Durable documents that retain authority for adjacent concerns. |

The registry contains references and state, never copied requirements, full scenarios, API schemas, or a duplicate decision log.

## Financial-invariant authority classification

| Invariant aspect | Authority | Evidence and limit |
|---|---|---|
| Observable behavioral invariant | Existing approved behavioral sources before transfer; approved OpenSpec main specification after formal transfer. | docs/01-discovery/06-business-rules.md identifies BR-ACC-03, BR-TRF-04..12, BR-LED-01..06, and BR-ADM-04. A spec transfer does not establish implementation or acceptance. |
| Technical enforcement mechanism | Durable architecture/data design, module contracts, approved dependency/transaction rules, and the owning module's implementation constraints. | docs/03-design/15-system-architecture.md, docs/03-design/16-domain-model-and-data-design.md, and docs/03-design/module-contracts-and-boundaries.md define ownership and transaction responsibilities. Migrations/source enforce technical constraints but cannot approve a behavior change. |
| External transport constraint | Frozen OpenAPI YAML and its API-contract explanation. | docs/03-design/contracts/openapi-v1.yaml and docs/03-design/17-api-contracts.md own operation, schema, status, and transport semantics. |
| Decision and approval record | Canonical decision register and the authority it records. | docs/6-governance/25-design-decision-register.md distinguishes APPROVED, IMPLEMENTED, and VERIFIED. It does not replace behavioral text or runtime evidence. |

An agent preserves every approved financial invariant regardless of the source that currently states it. It must not classify an implementation mechanism as a new behavioral requirement or treat an approved rule as proof of technical enforcement.

## Completion and reversal

A capability transfer completes only when all of the following are true:

1. The OpenSpec main specification has passed the installed CLI validation.
2. The registry names the OpenSpec path, all legacy references, approval reference, and limitations.
3. Required review explicitly approves the behavioral authority transfer.
4. The legacy source receives an explicit successor/provenance link when it is safe to edit, or the registry documents why it remains the active source pending a later compatibility update.
5. Documentation link validation passes and the PR records the review.

Rollback changes the registry status to ROLLED_BACK, restores the prior behavioral source as operative, and retains the rejected/draft OpenSpec material as traceable history. No legacy document is deleted during rollback.

## Conflict handling

When OpenSpec conflicts with a frozen YAML operation, module contract, financial rule, durable architecture constraint, or decision register entry:

1. Stop only the affected migration or change.
2. Record both exact paths, identifiers, evidence classes, and the compatibility impact.
3. Route the conflict to the existing authority named in the matrix.
4. Apply an explicit reviewed amendment or correct a factual status/documentation error.
5. Re-run relevant OpenSpec, document, contract, and implementation verification. A passing structural validator is not a substantive resolution.

This is a conflict-routing procedure, not a universal precedence order. An agent never selects the easiest interpretation, creates an unstated exception, or weakens an approved financial constraint.
