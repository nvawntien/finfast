# FinFast Fresh Repository, Workflow and Harness Assessment

**Audit date:** 2026-10-30
**Repository:** nvawntien/finfast
**Checked revision:** ec33b07f0b59c6b058ada9f383cd15ddd1bd2311
**Checked branch:** chore/document-migration, tracking origin/develop
**Scope:** repository contents at the checked revision, current local Git metadata, readable GitHub rulesets and checks, and the current local OpenSpec CLI.

This is a new assessment based on current source, configuration, documentation and GitHub evidence. Prior audit reports and migration plans were not used. The current worktree already had deletions under docs/audits; those changes were preserved. Ignored backend/target build output was not treated as source or verification evidence. Other worktrees were inventoried by Git metadata only; their contents were not used.

## Findings

FinFast has an approved V1 product and engineering baseline, a substantial Java/PostgreSQL foundation, and a working backend and documentation CI pipeline. It does not have an accepted OpenSpec specification set, an active GitHub Issues backlog, or an integrated V1 customer journey. The repository contains parts of the approved system with different completion levels. Present source files and a passing test run must not be read as proof that the V1 capability is complete.

The largest migration risk is authority drift. Approved detailed behavior is spread across discovery, requirements, UX, functional specification and API documents. Several documents also contain dated implementation checkpoints and status claims. Some current source contradicts those claims. A clear transitional authority rule is needed before OpenSpec adopts any behavior, followed by incremental reconciliation of each bounded capability.

### Severity and order

| Priority | Finding | Consequence |
|---|---|---|
| P0 | No OpenSpec repository configuration or accepted specs. | The approved V1 behavior remains operative in its existing sources. Empty initialization is safe and does not transfer authority. |
| P0 | GitHub Issues is enabled but has zero open issues; docs/02-requirements/10-product-backlog.md is the only maintained task register in the checked repository. | Ownership, dependencies, acceptance and live task state are not discoverable through the intended team workflow. |
| P1 | V1 implementation is partial and production composition is incomplete. | Passing backend CI cannot establish full V1 behavior, browser acceptance, release readiness or financial workflow acceptance. |
| P1 | Current docs make stale or overbroad implementation claims and duplicate status tracking. | Agents may mistake a historical checkpoint for live state or treat interfaces and isolated services as complete features. |
| P1 | Exact REST and Java contract information is repeated in prose alongside the machine-readable and source authorities. | Contract edits can drift across OpenAPI, Java and documentation. |
| P2 | The local agent harness has no standard Issue-to-OpenSpec session procedure, OpenSpec version pin or OpenSpec CI validation. | New sessions must reconstruct the work boundary and cannot validate accepted OpenSpec artifacts reproducibly. |
| P2 | Documentation link validation omits some operative Markdown. | Links in AGENTS.md, migration guidance and observability docs can drift outside the current docs checker. |

## Evidence classes

| Evidence class | Current evidence | What it establishes |
|---|---|---|
| Approved V1 intent | Discovery 01-08; PRD 09; derived specification 12; UX 14; architecture/data/API documents 15-17; frozen OpenAPI; Java API source; decision register 25. Their approval scope is recorded in the documents and the current [decision register](../../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze). | The required behavior and selected design constraints. Approval does not establish implementation or release acceptance. |
| Source-observed implementation | Tracked production source, Spring configuration, Compose files, Maven configuration and Flyway V1/V2 migrations at the checked revision. | Which artifacts and wiring exist in this checkout. Class or interface presence alone does not establish a usable feature. |
| Test definition | Backend test sources, including architecture fixtures, PostgreSQL/Testcontainers tests and HTTP/security tests. | The behavior the current test suite attempts to exercise. Test names or historical reports are not run results. |
| Current execution evidence | GitHub Actions run 40878220995 at the exact checked SHA. | The current backend-tests, docs-validation and backend-package jobs completed successfully on 2026-10-29. This proves the checked-in suite and jobs passed on that revision, not that unimplemented requirements passed. |
| Accepted OpenSpec coverage | No openspec/ directory, config.yaml or accepted spec files exist in the checked worktree. | No behavior has transferred to OpenSpec yet. Existing approved V1 obligations remain operative. |

The GitHub run is the current hosted verification evidence. No backend tests, service startup, persistent database operation, browser journey, deployment or benchmark was run as part of this audit.

## Current repository baseline

- HEAD is ec33b07f0b59c6b058ada9f383cd15ddd1bd2311 on the local chore/document-migration branch, with no ahead/behind count against origin/develop. The checked source tree is the same revision as GitHub develop.
- Git reports three local worktrees. Only the current worktree contents were inspected. Before this audit, the current worktree had pre-existing audit-file deletions and no other untracked non-ignored files. This audit added the four dated reports listed in the roadmap. Ignored backend/target output exists.
- Production Java has seven approved business module packages: accounts, administration, bootstrap, customer, idempotency, identity and ledger. The approved module graph in architecture 15 and the module guide also includes transfers and history. Those packages are absent from production source; related fixture packages under backend/src/test are architecture tests, not implementations. common is technical infrastructure, outside the nine business-module graph.
- Backend database migrations V1 and V2 define eight banking tables and immutability guards. They insert no banking data, Treasury, GENESIS or sample users.
- No frontend files are tracked at this revision. The repository has one GitHub Actions workflow, .github/workflows/ci.yml. It validates backend tests, backend packaging and documentation.
- No repository OpenSpec files, OpenSpec setup instructions, Issue template, pull request template or CODEOWNERS file were found. The local OpenSpec CLI is 1.13.0; this version is not pinned by the repository. The CLI reports spec-driven as the default and only available schema, and supports Codex configuration.
- The global skill list for this session contains no OpenSpec skill. The repository-local .agents and .codex directories are empty and untracked.
- GitHub reports main as the default branch and develop as the development branch. There are three active repository rulesets for PR/CI checks on main and develop and history safety on both.

## Authority map

| Concern | Authority at this baseline | Target responsibility |
|---|---|---|
| Long-lived product purpose, scope and vocabulary | Discovery 01-03 and PRD 09, with approval provenance in 25. | Durable product documents; preserve approved scope and IDs. |
| Detailed behavior and acceptance obligations | Use cases and ACs in 04, rules and CONS in 06, derived behavior in 12, approved screen journeys in 14, and operation behavior in 17/OpenAPI. | OpenSpec accepted specs, adopted incrementally after each scope is reconciled. Until then, existing approved obligations remain operative. |
| External REST contract | docs/03-design/contracts/openapi-v1.yaml. | Keep the same machine-readable file authoritative; prose documents explain navigation and change policy. |
| Internal Java contract | Source under backend/src/main/java/com/finfast/<module>/api. | Keep signatures in source; documentation provides ownership, dependency and change guidance. |
| Database schema | Flyway migrations under backend/src/main/resources/db/migration. | Keep migration files authoritative; retain the colocated README for structural explanation after correcting stale implementation status. |
| Architecture and financial transaction design | Documents 15 and 16, module guide, and decisions in 25; checked-in source/configuration shows implementation. | Durable architecture documents distinguish approved target design from observed code. |
| Technical decisions | docs/6-governance/25-design-decision-register.md is the sole canonical register. | Retain it as the only technical decision authority; no parallel ADR or OpenSpec decision log. |
| Task assignment and live progress | Backlog 10 records BK planning IDs and assignments; GitHub currently has no open issues. | GitHub Issues owns team work state; OpenSpec tasks own change-local execution state. |
| Verification and release | Test strategy 22, release checklist 23, source tests and CI. | Verification docs define method and gates; executed tests and CI supply revision-specific evidence. |

## Approved V1 intent versus source and current verification

The frozen baseline includes FR-001..013, UX-001..005, NFR-001..006, 38 canonical acceptance scenarios, and 19 CONS risk views. Those remain requirements. The following comparison uses current source and current CI only; it does not promote historical test claims into current evidence.

| Capability | Approved intent | Source observed at ec33b07 | Current verification boundary |
|---|---|---|---|
| Registration and authentication | Register one Customer, create one zero-balance Account, establish and revoke secure sessions, enforce CSRF and role boundaries. | Identity has authentication/session configuration and login use cases. RegisterCustomer exists but its source comment describes explicit composition only until real providers and the HTTP boundary are ready. No registration controller exists. GET session/CSRF routes and a login filter exist under profile auth. | Current backend CI passed. The standard compose.yaml does not activate auth, creates an empty database, and has no sample users. No complete registration-to-account browser flow is present. |
| Account overview | Return only the authenticated Customer's Account, excluding Treasury, with approved technical-error mapping. | AccountQueryService, JDBC account adapters and GET /api/v1/accounts/me exist under profile auth. | Source and tests establish a partial route. The checked Compose path does not activate auth. Hosted CI is not browser acceptance. |
| Transfers, posting and outcome recovery | Synchronous internal transfers, signed entries, safe concurrency, durable idempotency, final outcome replay and atomic commits. | Accounts posting support and Ledger transfer/allocation/GENESIS service and JDBC classes exist. There is no production transfers package, no idempotency internal provider, no transfer HTTP adapter and no production transfer orchestration. Ledger services require explicit composition and caller-owned transaction proxies. | Current CI passes tests for present foundation code. No current endpoint or complete transfer/recovery workflow exists in production source. |
| Admin listing, funding, freeze and inspection | Admin reads, finite Treasury allocation, freeze/unfreeze and success audit committed atomically. | Administration has read DTOs/use cases/controllers and write use case classes. The Admin inspection controller and configuration are property-gated and require real query providers. No Admin allocation/status HTTP controller exists. | Current CI does not show full Admin workflows. Source includes parts that are unwired or provider-dependent. |
| Treasury setup | Protected operator-driven setup, one Admin, Treasury and GENESIS, one atomic initialization, no startup reseeding. | Bootstrap configuration conditionally exposes an InitializeDatasetUseCase when FINFAST_BOOTSTRAP_ENABLED is true. It has no HTTP/operator adapter. Compose does not set the property. Migrations seed no records. | Database and service tests do not establish a production bootstrap endpoint, actual operator authorization or restart acceptance. |
| History and receipts | Authorized committed history and receipts with stable pagination and protected cursors. | Ledger contains internal history/receipt query services and JDBC adapters. There is no production history module or history/receipt HTTP adapter. Production query composition and a stable configured HMAC secret remain incomplete. | Current CI may validate service contributions; no Customer UI or end-to-end History/Receipt route exists at this revision. |
| Customer/Admin Web and cloud release | Responsive browser journeys, persistent cloud deployment, external evidence and measured performance. | No frontend source is tracked. Compose supplies a local backend and PostgreSQL stack. | No frontend CI/browser suite or deployment/benchmark acceptance is present. EV-001/EV-002 are still recorded pending and EV-003 awaiting measurement in the canonical release/decision documents. |

Current hosted CI evidence is [run 40878220995](https://github.com/nvawntien/finfast/actions/runs/40878220995), at the checked SHA. backend-tests, docs-validation and backend-package all concluded success. The suite has no frontend job. A green run establishes successful execution of the checked-in tests; it does not complete the missing routes, wire production-only provider graphs, execute approved browser journeys, verify cloud delivery or grant V1 release acceptance.

## Documentation and authority problems

1. The documents have clear intended authorities, but index and policy text blurs them. docs/README.md carries product scope, approval history, status, hazards, setup and CI policy. AGENTS.md repeats detailed banking architecture and dated status facts. README.md and docs/README.md repeat setup and implementation summaries.
2. Status prose is duplicated in backlog 10, traceability 11, module guide, migration README, test strategy 22, decision register 25 and README files. These dated snapshots are not live task state.
3. Several current implementation statements are stale. The migration README says FIX-001 remains open and says no posting/counter-allocation algorithm is implemented. The decision register records the NUL fix as implemented, the source rejects NUL, and Ledger posting/counter classes exist. Full production workflows remain incomplete, so the corrected wording needs to distinguish code contributions from end-to-end providers.
4. docs/README.md describes provider implementations and functional HTTP APIs as not implemented. Source now includes authentication/session routes, account overview and conditional Admin read routes, alongside incomplete production wiring. The description should report partial code and runtime availability separately.
5. module-contracts-and-boundaries.md is the useful human entry point for owners, dependencies, capabilities and change policy. It also repeats exact Java method signatures and DTO shapes that live in source. Keep the policy inventory and link the source declarations.
6. API 17 correctly names the OpenAPI YAML as the REST authority, while also repeating endpoint and response inventories. Keep explanatory behavior/change context and the recorded pending exceptions; avoid maintaining a second schema table. The 2026-10-25 authentication-denial proposal, Receipt 403 disposition and API-ERR-01 remain pending and cannot be imported into accepted specs as decisions.
7. Traceability 11 calls its mapping generated, but no generator exists in scripts or build files. It is a manually maintained design crosswalk, not a generated or live status artifact.
8. Existing V1 requirements with incomplete implementation remain operative. They can move to OpenSpec only when their obligations and identifiers are explicitly reconciled and accepted.

## Decision boundary

The project owner has already approved the desired knowledge responsibilities, incremental OpenSpec adoption and RD-09's sole decision-register rule. Empty OpenSpec initialization needs no RD-07 approval. The practical decisions before execution are who owns/reviews each migration Issue, which live approved requirement is the first pilot, whether the ruleset bypass result is intended, and whether develop should require a branch-current status policy.

Existing product and release gates remain separate: verify EV-001 and EV-002 against actual evidence, measure EV-003 using the approved method, resolve only the recorded REST/security integration exceptions with their owners, and obtain the distinct project-owner V1 release decision after evidence exists.

## Checks performed for this assessment

- Inspected Git branch, HEAD, worktree metadata, tracked file list, untracked status and ignored file groups. Historical audit contents and sibling worktree contents were not inspected.
- Read current root instructions, README, Makefile, docs index, operative requirement/design/verification/decision documents, migrations and selected source/configuration/test inventories.
- Ran make help. It listed the existing commands and made no service changes.
- Ran openspec --version, openspec --help, openspec schemas and help for init. The installed CLI reports version 1.13.0 and default spec-driven schema. No initialization command was run.
- Queried GitHub repository metadata, branch refs, rulesets, current Actions run and Issue/PR lists using read-only access. Open Issues: 0. Open PRs: 0. Project-board inventory and labels were inaccessible through the connector and are not assumed.
- Confirmed hosted CI run 40878220995 matches ec33b07f0b59c6b058ada9f383cd15ddd1bd2311 and its three configured jobs succeeded.
- Did not run application tests or start services. This audit did not change application code, OpenAPI, Java APIs, migrations, GitHub settings or OpenSpec state.
