# Session 2 Execution Plan

**Status:** PROPOSED. Session 2 starts only after human governance approves the applicable RD-07 and RD-09 policy decisions. It does not authorize application implementation, legacy mass relocation, OpenSpec capability seeding, decision-register edits, or harness installation.

## Session 2A - Knowledge governance foundation

| Item | Plan |
|---|---|
| Goal | Establish reviewed documentation authority, navigation, and migration tracking without transferring any capability. |
| Inputs and prerequisites | Final Session 1 audit, approved RD-07/RD-09 disposition, cleanly scoped audit PR, and owner direction for the unrelated docs/README.md modification. |
| Exact outputs | docs/INDEX.md; docs/governance/knowledge-authority.md; docs/governance/documentation-workflow.md; docs/migration/capability-registry.yaml; a legacy-to-successor reference map; minimal approved routing updates. |
| Allowed modifications | New/updated durable documentation and routing only. Existing legacy files stay in place except for narrow approved successor links. |
| Prohibited work | No openspec root, no main specification, no change proposal, no legacy mass relocation, no application code/test change, no decision-register edit, and no harness file. |
| Human gate | Team/Tech Lead approves the authority model and the decision-register policy before merge. |
| Validation | git status --short --untracked-files=all; git diff --check; make help; documentation link/OpenAPI checks using the existing pinned environment when available. |
| Exit criteria | Authority matrix reviewed; registry defaults to NOT_STARTED and creates no automatic authority transfer; legacy references are traceable; frozen contract paths remain valid; unsupported as-built claims are absent; validation results or limits are recorded. |
| Rollback | Revert this single governance PR. Legacy paths remain operative because none are moved or retired. |
| PR boundary | One dedicated documentation-governance PR. Do not combine with OpenSpec initialization or capability content. |

## Session 2B - Durable knowledge curation

| Item | Plan |
|---|---|
| Goal | Curate maintained product, architecture, development, verification, contract-navigation, and governance documentation. |
| Inputs and prerequisites | Session 2A merged; registry/reference map available; relevant source owners review each domain. |
| Intended outputs | Curated pages under docs/product, docs/architecture, docs/development, docs/verification, docs/contracts, and docs/governance only where content is ready. |
| Required preservation | Actual as-built versus approved target distinction; financial transaction and module-boundary rules; approval provenance and historical evidence; frozen OpenAPI and Java contracts at their authoritative locations. |
| Allowed modifications | Additive curated documents and narrowly approved routing/successor links. |
| Human gate | Product, architecture, API, verification, and governance review according to affected content. |
| Validation | Documentation link/OpenAPI checks; source-to-document comparison for as-built claims; no unintended OpenAPI, migration, Java-contract, or decision-register change. |
| Exit criteria | Curated pages point to authoritative sources and retain evidence classifications; no duplicate technical decision authority; legacy content remains reachable. |
| Rollback | Revert individual curation PRs. The legacy source remains available. |
| PR boundary | Separate product/development, architecture/data, and verification/contract navigation PRs when review ownership differs. |

## Session 2C - OpenSpec adoption

| Item | Plan |
|---|---|
| Goal | Initialize the approved OpenSpec project configuration and establish project conventions without seeding a capability. |
| Inputs and prerequisites | Session 2A authority policy merged; RD-07 approved; installed CLI and generated-file impact reviewed. |
| Exact outputs | Only CLI-generated OpenSpec root/configuration and narrowly required convention links approved in the initialization PR. |
| Allowed modifications | OpenSpec initialization output and approved integration instructions only. |
| Prohibited work | No capability main specification, no active behavior change, no implementation task, no legacy authority transfer, and no custom harness. |
| Human gate | Team review of generated diff and compatibility with AGENTS.md, docs routing, Makefile, and CI. |
| Validation | Installed CLI context/status/validate commands after initialization, plus documentation checks. Record their exact output and limitations. |
| Exit criteria | Validated empty OpenSpec project, explicit links to authority policy, and no implication that any capability has migrated. |
| Rollback | Revert the isolated OpenSpec initialization PR. No behavior source has changed. |
| PR boundary | One OpenSpec-only PR. |

## Subsequent pilot - First capability transfer or change

Pilot selection is a separate reviewed work item after Session 2C. It must decide between:

1. A one-time reviewed baseline capability migration, which transfers approved existing behavior to an OpenSpec main specification without implementation or verification claims.
2. A normal bounded OpenSpec change, which uses proposal, behavioral delta specs, design, tasks, implementation, verification, review, and finalization.

Customer-account-overview remains a candidate only. It is not selected, implemented, verified, accepted, or migrated by this plan. A pilot PR requires its own authority decision, registry entry, source/provenance map, affected-owner review, and rollback plan.

## Later harness iteration

After the knowledge system is reviewed and merged, evaluate harness-creator in assessment mode in a throwaway worktree. Preserve AGENTS.md, Makefile, Maven Wrapper, and CI unless a later approved harness change demonstrates a needed improvement. A real change must test cross-session recovery. Do not create harness files during Session 2A, 2B, or 2C.
