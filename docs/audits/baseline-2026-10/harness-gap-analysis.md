# Harness Readiness and Gap Analysis

| Subsystem | Tracked support | Baseline gap and classification | Recommended next action |
|---|---|---|---|
| Repository context | README, docs/README.md, architecture, module-contract guide, decision register, Makefile | Approved design and implementation status are intentionally separate. **SOURCE STATUS** | Keep revision, source presence, composition, and execution evidence distinct. |
| Worktree provenance | Intended audit worktree is at the specified commit. | The other worktree's 48-file overlay is not tracked here; ownership and intended integration are **UNRESOLVED**. | Obtain owner and branch provenance without copying it into the baseline. |
| Build and disposable test workflow | Maven Wrapper, Testcontainers dependencies, Compose, CI, and isolated test support are tracked. | No current run was authorized or performed. Hosted CI state is **UNKNOWN**. | Reproduce only in an approved disposable environment after this audit. |
| Provider composition | Application services, adapters, and public APIs are partly tracked. | No complete tracked provider graph for banking workflows; conditional components are inactive by default. **SOURCE STATUS** | Compose each capability through owner-controlled configurations before calling it production-ready. |
| HTTP/API conformance | Frozen 15-operation OpenAPI and six MVC handlers plus login/logout security handling. | Seven contract operations have no tracked route. Several declared routes require inactive profile/property gates. **SOURCE STATUS** | Implement approved adapters after provider composition. Do not amend frozen contracts to mask a gap. |
| Financial workflow | Accounts/Ledger/Administration source, V1/V2 guards, PostgreSQL-oriented tests. | Transfers orchestration, durable Idempotency, final outcome recovery, and end-to-end composition are absent. **SOURCE STATUS** | Treat the local overlay as a provenance question; implement or integrate only after review. |
| Concurrency evidence | Tracked test definitions cover Account locks, Ledger positions, GENESIS, reconciliation, and allocation harness schedules. | Definitions and historical records are not a current execution. Transfer/Idempotency concurrency coverage is absent. **NOT RUN** | Run approved tests against an isolated PostgreSQL environment once the actual providers exist. |
| UX and frontend | UX/browser acceptance criteria are approved. | No frontend package or browser E2E implementation is tracked. **NOT IMPLEMENTED / NOT RUN** | Confirm owner and stack before selecting technology or claiming UX acceptance. |
| Benchmark | EV-003 method and acceptance criteria are documented. | No tracked harness or measurement result. **AWAITING MEASUREMENT** | Preserve the approved workload and collect environment-bound evidence later. |
| OpenSpec workflow | The installed CLI provides the spec-driven proposal, specs, design, and tasks workflow, but no project root is tracked. | No config, specs, changes, or archive is part of this baseline. **NOT ADOPTED** | Agree the authority-transfer policy before initializing OpenSpec. Formally migrated capabilities can then use OpenSpec as behavioral authority without replacing contracts or decisions. |
| Release evidence | Test strategy and UAT/release checklist are tracked. | No current runtime, browser, deployment, cloud, or performance acceptance. **NOT RUN** | Keep all release gates open. |

This table measures available repository support and known gaps. It does not evaluate personal working trees or establish successful runtime behavior.

## Future harness boundary

| Boundary | FinFast baseline already has | Minimal Harness v1 should add later | Optional v2, not part of migration |
|---|---|---|---|
| Startup and scope | AGENTS.md, docs routing, Git worktrees, Makefile command index. | Read-only preflight for worktree, branch, HEAD, status, applicable instructions, allowed paths, and one declared active change. | Advisory worktree/PR lookup and policy reporting, never a distributed lock. |
| Verification | Maven Wrapper, Makefile, CI workflow, docs checker, OpenAPI validation, tracked tests. | A deterministic command plan that records commands actually run, environment limitations, and no-run reasons. | Normalized evidence summaries and CI-status retrieval. |
| Session continuity | Git history, PRs, audit records, and repository documents. | A short durable handoff format with scope, changes, checks, limits, and next owner. | Ignored local progress/state files with recovery checks. |
| OpenSpec relationship | No project OpenSpec root; installed CLI has native proposal/specs/design/tasks, validation, status, and archive. | Link an active change to the harness preflight only. Never duplicate specifications or change-local task authority. | Read-only status display in handoff tooling. |
| Team coordination | GitHub PR/CI model in .github/workflows/ci.yml. | Explicitly defer assignments, review, branch protection, and merge gates to GitHub/team process. | Integration with issue/PR metadata after team approval. |

Walking Labs harness-creator is a potential later audit/scaffolding aid, not a migration dependency. Its official repository describes scaffolding AGENTS.md, feature lists, init.sh, and verification workflows. Evaluate it only in a throwaway worktree after migration, compare every generated artifact with FinFast's AGENTS.md, Makefile, Maven Wrapper, and CI, and adopt only reviewed minimal deltas. It cannot approve scope, replace OpenSpec, or coordinate distributed work.
