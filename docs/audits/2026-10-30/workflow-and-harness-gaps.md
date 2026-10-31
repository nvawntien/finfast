# Workflow and Harness Gaps

**Audit date:** 2026-10-30
**Checked revision:** ec33b07f0b59c6b058ada9f383cd15ddd1bd2311

## Current GitHub and Git workflow

| Area | Observed evidence | Gap and minimal action |
|---|---|---|
| GitHub task system | Repository Issues are enabled. Read-only API returned zero open Issues. Backlog 10 uses BK-001..025 as planning IDs and contains assignments/dependencies/status checkpoints. | Create a real GitHub Issue for each active bounded task and assign an owner. Preserve legacy BK IDs only as cross-reference during transition. Do not maintain live status in backlog 10 after Issue migration. |
| GitHub Projects | Repository metadata says the Projects feature is enabled. Project-board inventory was not accessible through the connector. | UNKNOWN whether a board exists or is in use. Issues are sufficient for the proposed workflow; do not create or assume a Project board. |
| Branches and worktrees | Feature PR heads use several conventions, including feat/BK-*, feature/BK-* and mixed casing. Three local worktrees exist. No worktree convention was found in repository instructions. | Adopt one branch per GitHub Issue and one isolated worktree per active coding agent. WIP=1 per agent; team members can work concurrently on independent Issues. Keep branch names short and include the GitHub Issue number. |
| Pull requests | Recent merged PRs target develop. Sampled PR descriptions vary: PR #31 has scope, review, verification and pending-integration sections; PR #42 and #43 have empty bodies. Sampled PRs received approvals; PR #31 also shows a changes-requested cycle. | Add one PR template requiring Issue, OpenSpec change, impacted durable docs/contracts, exact verification command/result/revision, and remaining integration dependencies. Link PR to Issue; do not repeat the task list from OpenSpec. |
| Issue templates and assignment | No .github/ISSUE_TEMPLATE or pull request template exists. Sampled PR metadata had no assignees. No open Issues exist today. | Add a small Issue form for goal, owner, dependencies, acceptance criteria with canonical IDs, and OpenSpec link. Use GitHub Issue number as live task identity. Confirm module reviewer from current owners rather than adding CODEOWNERS without owner/team validation. |
| CI and merge gates | .github/workflows/ci.yml has backend-tests, backend-package and docs-validation. GitHub run 40878220995 for the checked SHA succeeded in all three jobs. | Preserve these jobs. Add OpenSpec validation inside docs-validation after CLI pinning and initial spec creation so existing required check names stay stable. |
| Rulesets | Active develop and main PR/CI rulesets require one approval, last-push approval, resolved review threads, and all three named checks. main requires strict/up-to-date checks; develop does not. Separate history rules prohibit deletion and non-fast-forward updates on both. | Existing gates are strong. Confirm whether non-strict develop checks are intentional under concurrent work. The ruleset API reports current_user_can_bypass as always; confirm intended bypass actors. The branch-protection endpoint itself returned 403, so other protection settings are unknown. |
| Review ownership | Module owner/dependency guidance exists in module-contracts-and-boundaries.md. No CODEOWNERS file or required code-owner review is configured. | Select reviewers in each Issue/PR using the existing ownership guide. Add CODEOWNERS only if team accounts and ownership are verified and the rule provides value. |
| OpenSpec relationship | No OpenSpec files exist. No current Issue or PR is open. | Issue owns team task and acceptance; OpenSpec change owns proposed behavior, design and local tasks; PR and Git own integration; CI and executed tests own evidence. Link all four without introducing another global status file. |
| Current in-flight work visibility | GitHub lists remote feature branches, including feature/BK-019, while the open PR and Issue lists are empty. The branch contents are outside the checked develop revision. | Reconcile unmerged branches with their owners before cleanup or reuse. Their presence does not prove current implementation or completion. |

GitHub evidence: [repository](https://github.com/nvawntien/finfast), [latest CI run](https://github.com/nvawntien/finfast/actions/runs/40878220995), [PR #31](https://github.com/nvawntien/finfast/pull/31), [PR #42](https://github.com/nvawntien/finfast/pull/42), [PR #43](https://github.com/nvawntien/finfast/pull/43).

## OpenSpec readiness

- The repository is not initialized: no openspec/config.yaml, specs or changes exist.
- The current local CLI is OpenSpec 1.13.0. This is environment state, not a repository pin or team guarantee.
- The CLI reports spec-driven as the default schema with proposal, specs, design and tasks artifacts. It supports Codex setup with the --tools option. The session skill catalog has no OpenSpec-specific skill.
- AGENTS.md and docs/README.md do not define OpenSpec authority, change naming, validation, archival or links to GitHub Issues.
- The current approval record already establishes the V1 behavior and design baselines. A new change must state which existing requirements it implements and reconcile the affected obligations. It must not treat current source as intended behavior.
- The OpenSpec project context should say that accepted specs own detailed behavior only within their coverage; existing approved requirements remain operative until reconciled; source does not define intent; OpenAPI YAML, Java API source, Flyway migrations, architecture documents and decision register remain separate authorities.
- Proposed specs must not duplicate schemas, Java method signatures, database DDL, design-decision rationale or implementation enforcement mechanisms. Link those sources.
- The team convention is to archive in a separate, narrowly scoped follow-up PR after the implementation PR is reviewed, passes required CI and merges with the OpenSpec change artifacts retained. The archival PR targets the updated integration branch and synchronizes only the approved behavior into `openspec/specs/`, preserving relevant legacy requirement references. It must not introduce unreviewed behavior or unrelated implementation work.
- During the interval between implementation merge and archival merge, the implementation PR must say specification synchronization is pending. Keep the Issue open by default, link the archival PR and record the pending state there. If team completion policy closes the Issue at implementation merge, the Issue must still explicitly track the linked archive PR as pending. Close according to that policy only after distinguishing implementation completion from specification synchronization.
- OpenSpec validation, archival and passing CI do not establish complete V1 capability acceptance.
- Empty initialization is approved by the stated target and requires no RD-07 approval. Do not bulk import 04/06/12/14 into specs. Start with one reviewed behavioral change.

## Harness readiness against fresh-session questions

| Agent question | Existing answer | Reliability gap | Smallest useful improvement |
|---|---|---|---|
| What system am I working on? | README.md explains simulated banking scope and backend technology. | Summary says several areas need implementation without consistently identifying partial code versus complete, runnable workflows. | Keep a short system statement and evidence-class distinction in README; link product PRD and current Issue. |
| How is it organized? | AGENTS.md, docs/README.md, architecture 15, module guide and data design 16. | Root instructions are 115 lines and repeat detailed rules/status. The 165-line docs README combines navigation, approvals, setup and current status. Module guide duplicates API signatures. | Keep the docs index as navigation; keep AGENTS as a concise operating contract; keep detailed boundaries and financial constraints in their canonical docs. |
| How do I run it? | README.md and Makefile cover local Docker, native backend, database, tests, health and optional observability. make help lists current targets. | The default Compose stack uses an empty database and does not activate the auth profile or Admin/bootstrap gates. It starts an infrastructure foundation, not a V1 demo. | Document the default runtime accurately. Add a development profile/demo path only when the needed production behavior exists; do not create fixture banking data in ordinary startup. |
| How do I verify changes? | Maven Wrapper, Testcontainers, ArchUnit and CI provide backend checks. docs/README.md gives OpenAPI and Markdown validation commands. | OpenSpec validation is absent. The docs checker scans README.md and docs/**/*.md but omits AGENTS.md, migration README and observability Markdown. No frontend files or browser checks exist at this revision. | Extend the existing checker inputs and run pinned OpenSpec validate in existing docs-validation after initialization. Keep real PostgreSQL/proxy tests for financial evidence. |
| What is the current relevant work state? | Git provides branch, HEAD, worktree and dirty state. GitHub exposes Issues, PRs, branches and CI. | There are no open Issues/PRs and no OpenSpec change. Backlog 10 and dated documents contain non-live work state. | Fresh start begins from the assigned Issue, branch/worktree, its OpenSpec change, and latest CI. Do not create STATUS.md, PROGRESS.md, feature_list.json or global handoff. |

## Minimal team procedure

1. Start from an assigned GitHub Issue. Confirm owner, dependencies, acceptance IDs and whether the work changes behavior.
2. Create a branch and isolated worktree from develop. Each coding agent keeps one active Issue at a time; other team members may work concurrently.
3. Initialize the session: read AGENTS.md and the docs index, run git status and make help, then inspect the Issue and only its linked product, architecture, contract and accepted spec sources.
4. For a behavior change, use OpenSpec explore when scope is unclear, then create a bounded proposal/spec/design/tasks change. For implementation-only work with no behavior change, record the reason on the Issue and use the existing sources.
5. Implement within approved APIs, architecture, schema and business rules. Ask for a scoped decision only for the conflict types already defined in AGENTS.md and decision register.
6. Run the relevant existing Maven and documentation commands. Financial proof uses isolated real PostgreSQL and actual transaction proxies. Record command, revision, environment and result.
7. Open the implementation PR linked to the Issue and OpenSpec change. Include the change artifacts, implementation, tests, necessary durable-document updates, exact verification evidence, limits and unfinished dependencies. State that specification synchronization is pending a separate archival PR. CI and review gate merge.
8. After review and required CI pass, merge the implementation PR while retaining its change artifacts. During the temporary merged-but-not-archived state, keep the Issue open by default, link the follow-up archival PR and record that synchronization is pending. Do not claim accepted-spec synchronization is complete.
9. Open a separate, narrowly scoped archival PR against the updated integration branch. Archive/synchronize the approved behavior into `openspec/specs/`, preserve relevant legacy requirement references, run OpenSpec validation and required CI, and review that no behavior scope has changed.
10. After the archival PR merges, close the related Issue according to team policy, explicitly distinguishing implementation completion from specification synchronization. If policy closes it at implementation merge, its pending archive PR must remain visibly tracked on the Issue.

Passing OpenSpec validation, merging an archival PR or passing CI does not establish complete V1 capability acceptance. Keep the existing GitHub rulesets, CI check names, Makefile and canonical contract paths unless a separately justified change is approved.

This uses GitHub Issues for team coordination, OpenSpec tasks for change-local work, Git/PRs for history, and executed CI/tests for evidence. It creates no second task, status or decision authority.

## Minimal additions versus optional work

### Minimal workflow foundation required before the first pilot

- Concise `AGENTS.md` and deterministic session-start guidance that route agents to the Issue, worktree state and relevant authorities.
- Issue and PR templates for owner, dependencies, canonical acceptance IDs, OpenSpec link and verification evidence.
- Repository-pinned OpenSpec CLI and authority context.
- OpenSpec validation integrated into the existing `docs-validation` job, preserving its required check name.
- A clean assigned worktree or explicit path staging so unrelated local changes stay out of the pilot. Reconcile remote branch state with owners before reusing or removing branches.

### Optional harness improvements after the pilot

- Broader existing Markdown link validation for agent instructions, migration and observability docs, if the pilot exposes meaningful link drift.
- `make context`: current `git status`, `make help` and OpenSpec already expose local state. The GitHub Issue and remote CI remain in GitHub. A local script cannot reliably replace those sources. Revisit only if pilot sessions repeatedly fail to find needed context.
- GitHub Projects dashboards: the feature is enabled, but no project inventory is accessible or needed for the proposed Issue workflow.
- CODEOWNERS, merge queues and new branch policies: current review/CI rules are substantial. Verify maintainers and value before adding more enforcement.
- Global STATUS.md, PROGRESS.md, session-handoff.md, feature_list.json, custom orchestration loops, parallel agent runners or a second build/test command system.
- New empty docs/product, docs/architecture, docs/development or docs/verification directories. Create them only with substantive successor content and verified link updates.

## Pilot candidate

The read-only Customer Account listing slice for an Admin is a candidate only, associated with FR-009, UC-ADM-01, AC-024/027/030/037 and approved AM-03 AdminAccountQueries. Before selecting it, confirm its real provider dependencies and bounded acceptance scope in the Issue. Do not activate placeholder providers or change the public contract. If the candidate cannot remain bounded, choose another approved behavior with resolved dependencies and no pending contract or architecture decision.

Run the recovery test while the pilot change is unfinished: a fresh Codex session with no conversation history must recover its scope, Git/worktree state, OpenSpec tasks, constraints, dependencies and next actions from the Issue, repository and change artifacts before the implementation PR merges or the Issue closes. The pilot passes when that recovery succeeds, the implementation is reviewed and passes current checks, and the separate archival PR synchronizes only the reviewed behavior. Do not mark BK-016, all FR-009 acceptance, or V1 complete from a partial pilot.
