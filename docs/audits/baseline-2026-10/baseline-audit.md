# Repository Baseline Audit

**Audit date:** 2026-10-29
**Authoritative baseline:** Git-tracked content at e3dc6ef8deccc09946b115565171c4c3c0d2855a only
**Evidence rule:** Source and test definitions establish presence, not successful execution or production readiness.

**Evidence classes:** TRACKED_SOURCE, APPROVED_DOCUMENTED_INTENT, HISTORICAL_EVIDENCE, LOCAL_ONLY, INFERRED, CONFLICTING, UNKNOWN, and NOT_RUN. A class describes the evidence available for a claim, not the claim's importance or release status.

## Confirmed execution context

| Item | Observed value |
|---|---|
| Working directory | /home/bmngxn/Projects/bank-knowledge-audit |
| Git top-level directory | /home/bmngxn/Projects/bank-knowledge-audit |
| Branch | chore/repo-knowledge-audit |
| HEAD | e3dc6ef8deccc09946b115565171c4c3c0d2855a |
| Current working state at refinement | Audit reports are untracked; docs/README.md has a pre-existing modification that this audit preserves |
| Registered worktrees | This audit worktree and /home/bmngxn/Projects/finfast at the same commit on develop |

The audit is in the intended worktree. This audit changes only files in this audit directory. It does not attribute the unrelated docs/README.md modification to the baseline or alter it.

## Tracked baseline inventory

| Classification | Evidence at e3dc6ef | Assessment |
|---|---|---|
| Application source | 143 tracked Java files under backend/src/main/java | Source includes common, identity, customer, accounts, ledger, administration, and bootstrap code, plus the Idempotency public API. This does not establish complete provider composition. |
| Test definitions | 163 tracked Java files under backend/src/test/java | Unit, architecture, PostgreSQL integration, HTTP, transaction, concurrency, history, receipt, and administration test definitions are tracked. They were not run in this audit. |
| Build and configuration | Maven Wrapper, backend/pom.xml, compose.yaml, profile YAML, Flyway V1/V2 migrations, CI workflow, Makefile | These define a local and CI workflow. The default Compose service does not select the auth profile or the Administration inspection gate. |
| Requirements and approved contracts | 29 tracked Markdown files under docs, frozen OpenAPI YAML, module contract sources, decision register | These are the approved V1 product, API, architecture, and governance baselines. They are not runtime evidence. |
| OpenSpec and benchmark tooling | No tracked openspec directory, OpenSpec skills, or benchmark script | No baseline OpenSpec adoption, specification corpus, benchmark harness, or EV-003 result exists. |
| Historical execution evidence | Dated claims in the test strategy and decision register | Historical, revision-scoped documentation evidence only. No backend target Surefire evidence was present in this worktree and no test was rerun. |

## Tracked implementation and composition assessment

| Capability area | Tracked source present | Missing from the tracked production baseline |
|---|---|---|
| Identity and sessions | Security configuration, login filter, session/CSRF controller, credential and registration adapters, RegisterCustomer use case | No registration HTTP controller or tracked production composition of RegisterCustomer and its required providers. |
| Accounts | Account domain, JDBC adapters, query/provisioning/status/posting services, account-overview controller | No tracked production configuration that exposes the Accounts provider set needed for general banking composition. The controller source alone does not establish a startup-ready application. |
| Ledger | Immutable transaction and entry persistence, posting services, position allocator, history and receipt query services, public Ledger APIs | No tracked production configuration that composes real Accounts collaborators, stable cursor-secret provisioning, or public History/receipt adapters. |
| Administration | Allocation and status application services, audit persistence, conditional read-only inspection configuration/controller | No tracked write HTTP adapters or production composition for allocation, status, posting, outcomes, and audit collaborators. |
| Bootstrap | DatasetSetupWriter, DatasetInitializationService, conditional configuration | No tracked Admin/Treasury/Genesis provider composition or protected operator invocation. No HTTP endpoint is expected for GENESIS. |
| Idempotency | idempotency/api/RequestOutcomes.java and the request_outcome schema | No idempotency internal implementation, persistence provider, provider Bean, or Transfer consumer. |
| Transfers | ledger/internal/application/usecase/TransferPostingService.java implements Ledger's FinancialPosting collaborator | No transfers production package, transfer outer transaction/orchestration, HTTP adapter, request-outcome resolver, or transfer-specific tests. Ledger posting code is not a Transfers implementation. |
| History | Ledger-local history and receipt query services and tests | No history production package, production composition, authorization adapter, or history/receipt HTTP controller. |

The approved nine-module graph remains the architecture target. The baseline has seven named business source roots: Accounts, Administration, Bootstrap, Customer, Identity, Idempotency, and Ledger. Idempotency contains only its public API; Transfers and History have no production root. common is a shared foundation, not an eighth business module.

## HTTP surface at the baseline

The frozen OpenAPI contract defines 15 operations. The tracked application declares six MVC handler methods:

- GET /api/v1/auth/csrf and GET /api/v1/auth/session
- GET /api/v1/accounts/me
- GET /api/v1/admin/accounts, GET /api/v1/admin/financial-transactions, and GET /api/v1/admin/audit-events

The security configuration also declares filter/configuration handling for POST /api/v1/auth/login and POST /api/v1/auth/logout. This is source/configuration presence only. The MVC auth and account handlers require the auth profile; the three Administration reads require finfast.administration.inspection.enabled=true; the Compose file sets neither.

No tracked production route exists for registration, transfer submission, request-outcome resolution, customer history, receipt, allocation, or customer-status change. The missing HTTP adapters do not erase their tracked underlying application services where those services exist: registration, history, receipt, allocation, status change, and protected bootstrap are all separately represented in source. Transfer orchestration and Idempotency provision are not.

## Financial and concurrency evidence

Tracked source implements integral VND types, account mutation rules, deterministic account locking, Ledger posting facts, a commit-position allocator, V1/V2 database constraints, and append-only fact guards. The request_outcome table is schema support, not a persisted Idempotency implementation.

Tracked test definitions include PostgreSQL-oriented concurrency and rollback coverage for Accounts, ordinary Ledger posting, Ledger position allocation, GENESIS posting, Ledger reconciliation, and Administration allocation. The Administration test harness explicitly uses test-scoped or scripted collaborators for capabilities that lack production providers. There are no tracked transfer workflow, Idempotency provider persistence, Idempotency concurrency, or transfer HTTP tests at this commit.

Neither source inspection nor test definitions prove committed behavior, recovery after uncertain commit, complete financial wiring, browser acceptance, security deployment, or benchmark capacity. No tests, build, Compose startup, benchmark, Docker command, or persistent environment command was run for this correction.

## Historical local overlay note

**Separate from the authoritative baseline:** the earlier audit inspected /home/bmngxn/Projects/finfast on develop at e3dc6ef, where the user reported 48 pre-existing untracked paths. Its reports describe local-only Transfers and Idempotency source/tests, an API-error proposal, financial-evidence and benchmark documents, benchmark scripts, and OpenSpec scaffolding.

Those files are not tracked at e3dc6ef and are absent from this audit worktree. They cannot be credited as committed application implementation, test coverage, benchmark tooling, OpenSpec adoption, or execution evidence. Their owner, intended branch, review state, and provenance remain unresolved human questions. The observation is preserved here so the discovery is not lost, but it is excluded from all baseline completion statements.

## Verification performed and limits

This correction performed read-only Git context checks, make help, tracked-file inventory, and source/document/configuration inspection. It did not execute application tests or commands that create build artifacts, start services, access Docker, or touch persistent data.

The remaining limits are material:

- Historical test counts and pass claims in tracked documentation were not reproduced at this commit.
- No hosted CI status, branch protection, browser UI, external-course evidence, cloud environment, or deployment configuration was inspected.
- EV-001 and EV-002 remain pending and EV-003 remains awaiting measurement under the tracked decision register.
- The local overlay's ownership and intended integration status are not inferable from this worktree.
