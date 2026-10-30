# FinFast Agent-Driven Development Migration Roadmap

**Audit date:** 2026-10-30
**Basis:** current repository revision ec33b07f0b59c6b058ada9f383cd15ddd1bd2311
**Scope:** proposed small PRs only. No migration work was performed in this audit. This roadmap was corrected on 2026-10-31 to reflect confirmed team workflow conventions.

The sequence establishes authority routing, then allows product curation, one cohesive technical-documentation migration and empty OpenSpec initialization to proceed with their actual dependencies. A minimal workflow foundation must be in place before the first behavior pilot. The pilot implementation and OpenSpec archival are separate PRs. Existing approved requirements stay operative until reconciled. Source code remains implementation evidence. Preserve the existing GitHub rulesets, CI check names, Makefile and canonical contract paths unless a separately justified change is approved.

## Recommended dependency order

PR-1 index and authority routing
PR-2 product document curation
PR-3 architecture, contract and verification curation
PR-4 empty OpenSpec initialization
PR-5 minimal workflow foundation, before the behavior pilot
PR-6 one bounded pilot implementation PR, including unfinished-work recovery test
PR-7 separate OpenSpec archival PR after the implementation PR merges
Optional follow-ups: harness improvements justified by pilot evidence

PR-2, PR-3 and PR-4 may proceed in parallel after PR-1 when their actual content dependencies permit. PR-4 requires only the established authority map from PR-1; it does not wait for product, architecture or verification curation. PR-3 remains one cohesive PR and is not split by documentation category. PR-5 depends on PR-1 and PR-4 and must merge before PR-6 starts. PR-6 depends on a confirmed, bounded pilot and the minimal workflow foundation. PR-7 depends on PR-6 being reviewed, passing required CI and merged to the integration branch. No documentation migration PR requires application code, contract or schema changes.

## Worktree and existing-change safety

Before each migration PR, inspect `git status` and the selected worktree. The current worktree already has 15 pre-existing deletions under `docs/audits/baseline-2026-10/` and the untracked dated 2026-10-30 audit bundle. The deleted paths are:

- `baseline-audit.md`, `decision-inventory.md`, `documentation-disposition-matrix.md`, `documentation-inventory.md`, `documentation-lifecycle.md`
- `executive-summary.md`, `harness-gap-analysis.md`, `migration-plan.md`, `requirements-traceability.md`, `review-decisions.md`
- `review-questions.md`, `session-2-execution-plan.md`, `session-2-readiness.md`, `source-of-truth-policy.md`, `target-knowledge-architecture.md`

These historical-audit deletions and untracked audit files are unrelated local changes. Do not stage, restore, remove or include them in PR-1 through PR-7 without their owner's authorization. Use an isolated clean worktree for migration work, or explicitly stage only the assigned PR paths. Reconcile any uncommitted state with its owner before using that worktree.

## PR-1: Index and authority routing

**Goal:** Give a fresh session one short navigation map and an explicit authority hierarchy.

**Change boundary:** Curate existing docs/README.md as the index, update README.md and AGENTS.md references, and identify the existing source authorities. Keep current paths. Do not alter requirement wording or create target directories without content.

**Preconditions:** This audit's target authority map is adopted as implementation guidance. Assign an Issue owner/reviewers and inspect the chosen worktree for unrelated changes. No new design approval is needed.

**Deliverables:**

- Concise links by concern: product, current behavior, architecture/data, contracts, development, verification and decisions.
- A clear note that existing V1 obligations remain operative until OpenSpec reconciliation.
- A short statement separating approved intent, code observed in the checkout and executed verification.
- Existing path compatibility for README and AGENTS links.

**Review ownership:** Docs maintainer, Product Owner and Tech Lead.

**Verification:** Run scripts/check_docs.py, OpenAPI validation and git diff --check. Review every rewritten link and anchor. Confirm all canonical IDs and approval links still resolve.

**Rollback:** Revert the routing-only documentation commit. No code, CI or schema is touched.

**Parallelization:** Keep this PR single-owner because the index is a shared entry point. PR-2, PR-3 and PR-4 may start after PR-1 merges, with cross-links coordinated where their actual dependencies overlap.

## PR-2: Durable product document curation

**Goal:** Establish concise durable product sources without deleting behavior that has not reached OpenSpec.

**Change boundary:** Curate vision 01, glossary 02 and PRD 09 into the product responsibility. Keep 04, 05, 06, 07, 08 and 12 as explicitly transitional operative sources until their contents are reconciled. Do not rewrite canonical behavior or mark unfinished P0 as obsolete.

**Preconditions:** PR-1 routing is merged. Product Owner confirms content ownership and the issue-based review path.

**Deliverables:**

- Durable vision, V1 release scope, high-level PRD requirements and glossary with original FR/UX/NFR and domain identifiers preserved.
- Links from PRD to the still-operative detailed use cases, rules and functional specifications.
- External course evidence and EV-001/EV-002 kept distinct from approved product scope.
- Product document disposition recorded through this PR's Issue and review, not a new status register.

**Review ownership:** Product Owner, Tech Lead and owners of any touched acceptance mapping.

**Verification:** Compare every existing P0/P1 boundary and exclusion to source; verify all stable IDs, examples, links and release-gate language. Run documentation checks. No behavior acceptance is marked complete.

**Rollback:** Revert product-document changes if the crosswalk is incomplete. Keep the original operative requirement sources in place.

**Parallelization:** May proceed in parallel with PR-3 and PR-4 after PR-1 when the specific edits do not depend on one another. Coordinate shared index links and cross-references; do not edit the same shared file concurrently.

## PR-3: Architecture, contracts and verification curation

**Goal:** Make durable architecture and verification responsibilities distinct from source contracts, current implementation and historical execution notes.

**Change boundary:** Curate 15/16, module guide, 17, shared error/money, logging/error guidance, 22/23, migration README and BK-004 notes. Keep OpenAPI YAML, Java API source, Flyway SQL and decision register 25 at their existing paths.

**Preconditions:** PR-1 is merged. Use the current approved sources for operative behavior and authority boundaries. If a link depends on wording or a successor introduced by PR-2, coordinate that specific cross-reference; do not make PR-2 completion a blanket prerequisite.

**Deliverables:**

- Architecture docs describe approved target architecture separately from source-observed implementation.
- Module guide retains ownership, dependency/capability policy and change procedures; exact signatures link to Java source.
- API guide keeps navigation, amendment policy and exceptions; OpenAPI remains exact REST schema authority.
- Data design links migration guarantees to SQL and selected mechanisms to the sole decision register.
- Correct stale migration README statements: FIX-001 is implemented in current source; Ledger posting/counter classes exist, while complete production composition remains incomplete.
- Verification strategy states evidence classes and release gates. Dated execution snapshots remain dated and clearly scoped.
- Setup details remain in README or another substantive development guide. Retain existing paths until incoming links have been verified.

**Review ownership:** Tech Lead, verification owner, database owner, and affected module/API owners. Product Owner reviews any acceptance language.

**Verification:** Inspect source against each implementation claim; verify no contract signature/schema is edited; run scripts/check_docs.py, OpenAPI validation and git diff --check. Keep financial and release status claims tied to actual source or current run evidence.

**Rollback:** Revert PR-3 as a whole, independently of PR-2 and PR-4. Do not reverse implementation status by changing code or contract artifacts.

**Parallelization:** Keep this as one cohesive documentation migration PR covering the scope above. It may run alongside PR-2 and PR-4 after PR-1 when actual dependencies permit. Coordinate 17, module guide and 25 cross-links. Do not split PR-3 by category or edit the same shared file across branches.

## PR-4: Initialize OpenSpec

**Goal:** Initialize OpenSpec with its authority boundary and no imported behavior corpus.

**Change boundary:** Initialize the repository using the spec-driven schema and Codex integration. Add the minimal config context and generated Codex instructions. Do not create accepted feature specs, alter application files or move requirement documents.

**Preconditions:** PR-1 is merged and its authority map is sufficient to write accurate project context. A GitHub Issue is assigned to the initialization owner. PR-2 and PR-3 do not need to be complete; initialization may proceed in parallel with them.

**Deliverables:**

- openspec/config.yaml with concise project context and spec-driven schema.
- A pinned CLI version and reproducible local invocation. The audit environment had 1.13.0; pin the version reviewed by the team in repository tooling.
- Generated instruction changes reviewed without force cleanup. Use Codex only; avoid configuration for unrelated agents.
- Context rules: accepted specs own detailed behavior only within their coverage; unreconciled approved V1 sources remain operative; source does not define intent; OpenAPI, Java API, Flyway, architecture and decisions stay authoritative for their concerns.
- No blank or bulk-imported specs and no parallel decision log.

**Review ownership:** Tech Lead, Codex harness maintainer and Product Owner.

**Verification:** Use the pinned CLI to inspect config, list specs/changes, validate initialized state and confirm no behavior authority transferred. Review generated Codex instructions. Run documentation checks.

**Rollback:** Revert OpenSpec config and generated instructions as one change if the source boundaries are wrong. No implementation or external authority changes.

**Parallelization:** Source-of-truth review can happen in parallel, but only one initializer should edit OpenSpec configuration.

An empty initialization requires no RD-07 approval under the project owner's stated direction. This PR does not move detailed behavior authority or require completed durable-document curation.

## PR-5: Minimal workflow foundation before the first pilot

**Goal:** Give the team a reproducible Issue-to-OpenSpec workflow before starting a behavior pilot.

**Change boundary:** Add only the workflow foundation needed before the pilot: concise `AGENTS.md` operating instructions, deterministic session-start guidance, GitHub Issue and PR templates, and OpenSpec validation in the existing `docs-validation` job. Use the CLI version pinned in PR-4. Preserve the existing Makefile, GitHub rulesets, CI check names and canonical contract paths. Do not add global progress/state files, `feature_list.json`, or orchestration.

**Preconditions:** PR-1 and PR-4 are merged. The authority map is stable enough for the session guidance. Assign a GitHub Issue and reviewers for the workflow foundation.

**Deliverables:**

- Concise `AGENTS.md` with links to the docs index and canonical workflow, architecture, contract and verification sources.
- A deterministic session-start procedure: inspect status in the assigned worktree, read the Issue, locate only relevant sources, and use the existing Makefile/Maven Wrapper commands.
- Issue and PR templates that capture owner, dependencies, canonical acceptance IDs, OpenSpec link, durable-document impact and exact verification evidence without duplicating task status.
- The pinned OpenSpec CLI used reproducibly in local instructions and existing CI.
- `openspec validate` integrated into `docs-validation`; retain the current required check name and other job names.
- Clear Issue/PR language that accepted specs cover only reconciled behavior and passing CI or archival does not establish complete V1 acceptance.

**Review ownership:** CI maintainer, Tech Lead, docs maintainer and pilot participants.

**Verification:** Exercise the Issue/PR templates on a dry run or the pilot Issue. Run `openspec validate` with the pinned version and all existing `docs-validation` steps. Confirm the required GitHub check names and Makefile are unchanged.

**Rollback:** Revert the workflow instructions, templates and validation integration if they are inaccurate or unreliable. Keep the existing CI check names and avoid touching application or contract artifacts.

**Parallelization:** One owner should coordinate shared `AGENTS.md` and CI edits; templates may be prepared independently and integrated in this PR. This foundation precedes any pilot behavior implementation.

## PR-6: First bounded pilot implementation PR

**Goal:** Implement one confirmed, bounded behavior from an assigned GitHub Issue and exercise the workflow while the change is still unfinished.

**Change boundary:** The Admin Customer Account listing slice under FR-009 and approved AM-03 is a candidate only. Before selecting it, confirm its real provider dependencies and bounded acceptance scope. Preserve existing OpenAPI and Java signatures. Do not enable placeholder providers, broaden the contract, or mark all of BK-016 complete. If the candidate cannot remain bounded, select another approved behavior with resolved dependencies and no pending design decision.

**Preconditions:** PR-4 and PR-5 are merged. The candidate's provider dependencies, owner, acceptance IDs and dependencies are confirmed in the Issue. The pilot Issue is assigned and reviewers are identified.

**Deliverables:**

- A bounded OpenSpec change linked to the GitHub Issue, with proposal/spec/design/tasks only where useful and explicit references to the approved legacy requirements it reconciles.
- Implementation, tests and necessary durable-document updates for only the Issue scope.
- A fresh-session recovery test during the unfinished pilot: after some tasks are complete and others remain, a new Codex session with no conversation history recovers scope, current Git/worktree state, OpenSpec task state, constraints, dependencies and next actions from the Issue, repository and change artifacts.
- An implementation PR linking the Issue and OpenSpec change, including artifacts, implementation, tests, necessary durable-document changes and exact verification evidence.
- The implementation PR states that OpenSpec synchronization/archival is pending a separate follow-up PR. It must not claim the accepted spec has been synchronized or archived.
- After review and required CI pass, merge the implementation PR with the OpenSpec change artifacts retained in `openspec/changes/`.

**Review ownership:** The confirmed provider and consumer owners, Tech Lead for boundary compliance, verification owner for tests, and Product Owner for the acceptance scope.

**Verification:** Run the relevant Maven tests with supported isolated PostgreSQL/Testcontainers setup, architecture/capability checks, OpenAPI validation if relevant, pinned OpenSpec validation and all required GitHub CI jobs. Use actual transaction proxies where needed. Record what the pilot proves and does not prove. The fresh-session recovery test must happen before the implementation Issue is merged or closed.

**Rollback:** If implementation acceptance or CI fails, revise or revert the implementation PR and its unarchived change artifacts together. Leave unrelated providers and approved requirements intact. Do not archive a rejected or incomplete change.

**Parallelization:** Provider and consumer owners may collaborate through approved APIs. Avoid simultaneous changes to shared configuration or contract sources without coordination. Run the recovery test while the pilot is actively in progress, not as a post-merge drill.

## PR-7: Separate OpenSpec archival PR

**Goal:** Synchronize only the reviewed and merged behavior from the implementation PR into accepted OpenSpec specs.

**Change boundary:** Create a separate, narrowly scoped archival PR against the updated integration branch after PR-6 merges. Archive/synchronize the accepted change into `openspec/specs/`, preserving approved behavior and relevant legacy requirement references. Do not include implementation changes or unreviewed behavior changes.

**Preconditions:** PR-6 is reviewed, all required CI checks pass, and it is merged with its OpenSpec change artifacts retained. The related Issue stays open during the temporary implementation-merged/spec-sync-pending state. Link the archival PR from the Issue and record the state there; do not create another progress registry or describe synchronization as complete yet.

**Deliverables:**

- A separate PR containing only the OpenSpec archival/synchronization change and any required narrowly scoped references.
- OpenSpec validation and existing required CI checks on the updated integration branch.
- Review confirming archival preserves the behavior approved in the implementation PR and relevant legacy requirement IDs, without expanding acceptance scope.
- Issue completion recorded according to team policy, distinguishing implementation merged from specification synchronized. The default is to close the Issue after the archival PR merges; if team policy closes at implementation merge, the Issue must still explicitly track the linked archival PR as pending.

**Review ownership:** Tech Lead, Product Owner for behavior preservation, OpenSpec change owner and a reviewer independent of the implementation author.

**Verification:** Run the pinned OpenSpec validator and all required CI checks. Review the diff to confirm it contains no unreviewed behavior changes or unrelated implementation work. Do not treat validation, archival or green CI as complete V1 capability acceptance.

**Rollback:** Revert or revise the archival PR independently if synchronization is inaccurate. Keep the already merged implementation PR and its unarchived change artifacts intact until a corrected archival PR is ready.

**Parallelization:** Archival work follows the implementation merge for this change. Other members may work on independent Issues, each agent at WIP=1, while shared contract/database files retain explicit owners.

## Optional harness follow-ups after the pilot

**Goal:** Improve only the parts of the workflow that the pilot showed to be unreliable or costly.

**Change boundary:** Consider extending Markdown-link coverage to AGENTS.md, migration and observability docs; adding small startup aids; or adjusting templates only when the pilot provides concrete evidence. Do not add `make context`, global progress/state files, `feature_list.json`, a second task registry, or orchestration without a demonstrated need.

**Preconditions:** PR-6 and PR-7 have completed, and pilot feedback identifies a specific recurring failure. This is optional, not a prerequisite for OpenSpec adoption.

**Deliverables:** A small change limited to the demonstrated problem, with its own Issue and review.

**Review ownership:** Owner of the affected workflow/tooling area and pilot participants.

**Verification:** Show the specific pilot failure the change prevents and run the existing relevant checks. Preserve Makefile, CI check names and GitHub rulesets unless a separately justified change is approved.

**Rollback:** Revert the optional improvement if it adds process without resolving the documented problem.

## Decisions required before execution

- Assign a migration Issue owner and role-based reviewers for each phase. The repository currently has no open Issues and PRs.
- Confirm the actual owner and boundaries of the Admin list pilot, or select an equivalent read-only P0 behavior.
- Confirm that ruleset bypass reported by the GitHub API is intentional and whether develop should retain non-strict required status checks.
- Reconcile remaining remote branch refs with their owners before closing, deleting or reusing them.
- Preserve the already-recorded EV-001/EV-002 external evidence checks, EV-003 benchmark and pending REST/security decisions as separate product/release work. They do not block empty OpenSpec initialization.

No additional approval is required for the target authority map, RD-09's sole decision register or empty OpenSpec initialization. The selected workflow avoids a global status file, a second backlog, duplicate decision authority, a second test runner and automated agent loops.

## Recommended target deviations

- Keep docs/README.md as the docs index instead of adding docs/INDEX.md. The existing path is broadly linked; a rename provides no new authority and adds link churn.
- Keep docs/6-governance/25-design-decision-register.md as the sole decision register at its current path for this migration. A later one-file move can be separately proposed after link and anchor compatibility is proven.
- Keep OpenAPI and Java APIs at their current machine/source-consumed paths.
- Keep use cases and business rules operative until their obligations are accepted in OpenSpec. Move them to docs/legacy only after reconciliation, never because the code is incomplete.
- Do not create docs/contracts/README.md, docs/development/setup.md, docs/product/releases/v1.md or other target files unless curation establishes distinct content that existing README, API and PRD sources do not already own.
