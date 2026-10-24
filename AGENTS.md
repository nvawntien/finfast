# FinFast — Agent Instructions

## Project and baseline

- FinFast is a banking simulator for a six-person team; use simulated funds and synthetic data.
- Implement only the requested scope within the approved V1 boundaries.
- Java 21, Spring Boot 4.1.1, Maven Wrapper; PostgreSQL 16 with Flyway.
- One deployable Spring Boot modular monolith and one logical database per environment.
- Each developer runs an independent local PostgreSQL instance through Docker Compose.
- Use root [README.md](README.md) and checked-in build/config files for setup and exact version pins.
- Verify the frontend stack before assuming a framework or adding dependencies.

## Documentation routing

Start at [docs/README.md](docs/README.md); read only documents relevant to the task.
Verify implementation against source/configuration: historical implementation notes may be outdated.

Default to editing existing canonical documents. Do not create new documentation files, review reports or amendment documents for ordinary implementation changes without explicit Tech Lead approval.

| Topic | Source |
|---|---|
| Approved V1 scope | [Vision and scope](docs/01-discovery/01-product-vision-and-scope.md) |
| Canonical behavior and rules | [Use cases](docs/01-discovery/04-use-cases.md), [business rules](docs/01-discovery/06-business-rules.md) |
| Requirements | [PRD](docs/02-requirements/09-product-requirements.md) |
| Task planning | [Backlog](docs/02-requirements/10-product-backlog.md) |
| Architecture and module boundaries | [Architecture](docs/03-design/15-system-architecture.md) |
| Canonical Java APIs, owners and collaboration | [Module contracts](docs/03-design/module-contracts-and-boundaries.md) |
| Domain, database, locking and transactions | [Domain/data design](docs/03-design/16-domain-model-and-data-design.md) |
| API behavior | [API design](docs/03-design/17-api-contracts.md) |
| OpenAPI contract | [OpenAPI YAML](docs/03-design/contracts/openapi-v1.yaml) |
| Testing and acceptance | [Test strategy](docs/05-verification/22-test-strategy-and-acceptance.md) |
| Decision authority and proposal status | [Decision register](docs/6-governance/25-design-decision-register.md) |

- Preserve approved discovery requirements and confirmed architecture decisions.
- **The V1 Product, UX, Java Contracts, Technical Designs and External REST Contracts are approved implementation baselines. Do not request routine design reapproval when implementing within them.** Tiến explicitly approved PRD 09, derived specification 12, UX 14 and API 17/existing OpenAPI on 2026-10-22; see the [consolidated approval](docs/6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze). This baseline is available on `develop`; implement assigned, authorized tasks/phases against it. Draft future/out-of-scope ideas are not blanket authorization. Implementation/test completion and final release acceptance remain separate.
- Existing APIs, dependency allowlists, V1/V2 schema, selected algorithms and enforcement are already approved within their recorded scopes. A scoped amendment is required for a business-rule conflict, financial/security defect, breaking Java/REST contract, new module capability/dependency, necessary incompatible schema change or proven design inadequacy. Fix ordinary implementation defects and test without reopening approval; stop only the affected integration.
- Report conflicts with source paths and statuses instead of guessing; preserve canonical requirement IDs.

## Architecture

**MANDATORY FINFAST ARCHITECTURE:** Apply the complete [architecture convention](docs/03-design/15-system-architecture.md#33-java-project-and-module-structure-doc-02) to every existing and future feature in all nine business modules. Check package responsibilities, contract ownership, dependency direction and transaction ownership before writing code. Implement only packages needed by real behavior; do not create empty folders, placeholder providers or generic frameworks.

- **Modular monolith:** one Spring Boot deployable and one logical PostgreSQL database per environment. Preserve the nine-module/eighteen-edge allowlist and capability restrictions. Cross-module calls use only approved provider `api` contracts; never foreign `internal` types, repositories or SQL. Public APIs are framework-free and cannot depend on their own internals. `common` cannot depend on business modules or become a home for business workflows.
- **Domain:** `internal/domain/{entity,valueobject,service,exception}` owns business concepts and local invariants. Keep it independent of application, adapters, configuration, Spring, HTTP, JDBC, JPA and persistence frameworks. Use immutable financial facts and checked integral-VND arithmetic. Introduce domain services/exceptions only for real domain responsibilities; distinguish expected rejections from technical failures.
- **Application:** every feature-facing use-case entry exposes an interface under `internal/application/port/inbound`, unless it implements an approved public module API serving that purpose. Concrete implementations belong under `internal/application/usecase` and implement that contract. Commands/results belong in `internal/application/dto` unless owned by a public API; application helpers belong in `internal/application/service`. Keep orchestration here, preserve domain invariants and use outbound ports or approved module APIs for dependencies. Application code must not depend on HTTP, SQL, persistence implementations or configuration.
- **Ports and DTOs:** inbound contracts never depend on concrete use cases, helpers, outbound ports or infrastructure. Outbound interfaces under `application/port/outbound` express application needs independently of adapters. DTOs carry data without depending on control/adapter/configuration classes. A required inbound contract is not an unnecessary abstraction; do not create redundant interfaces for helpers, DTOs, enums, entities or value objects.
- **Adapters:** inbound adapters, including thin HTTP controllers, translate/validate transport data and invoke inbound contracts through configured proxies. They must not depend on concrete use cases, access persistence directly or orchestrate domain/financial workflows. Outbound adapters implement module-owned outbound ports or approved provider APIs, own module-local SQL/mapping/locking and propagate technical failures. They must not invoke application entries or inbound adapters. Never duplicate domain invariants as alternative financial authority.
- **Configuration and injection:** `internal/config` composes concrete implementations, adapters and framework resources. Use constructor injection; no field/setter dependency injection or service-locator shortcuts. Keep composition out of domain/application code. Do not add infrastructure dependencies without checking the existing build.
- **Transactions:** retain concrete transactional entry annotations and actual Spring proxy interception. Outer financial orchestration uses REQUIRED, READ COMMITTED and rollback for checked/unchecked technical failures; restricted mutation collaborators require the same caller-owned transaction. All required balance, Ledger, final-outcome and success-audit effects share one DataSource/transaction manager and commit atomically. No self-invocation bypass, REQUIRES_NEW financial effects, manual/independent commits or asynchronous required persistence. Pending/method-body return is not commit confirmation; never report success before confirmed outer commit or convert technical/uncertain commit failure into final business rejection.
- **Verification:** preserve and run module/capability/layer architecture tests, including negative fixtures for added restrictions. Verify new entry-contract relationships explicitly. Use real PostgreSQL and actual transaction proxies for financial rollback/commit evidence; mocks and structural checks do not establish durable outcomes, concurrency safety or release acceptance. Keep documentation, source responsibilities and tests consistent without changing frozen contracts, schema or unrelated implementations.
- Do not add microservices, gateways, brokers, separate module databases or extra services without explicit authorization.

## Module API collaboration (BK-003)

- Start at [module contracts](docs/03-design/module-contracts-and-boundaries.md) for exact source signatures, provider/consumer ownership, compatibility and change control.
- Compile-time interfaces/immutable values live under `backend/src/main/java/com/finfast/<provider>/api/`; implementation belongs only to that provider's `internal` tree. No provider Beans or banking workflows are supplied by BK-003.
- Tiến explicitly APPROVED the current BK-003 signatures, TD-015 posting ownership/transaction responsibilities and TD-016 nine-module/18-edge boundary policy on 2026-10-22; BK-003 merged into `develop` at `8a04ca0` (PR #10) on 2026-10-22, so that shared baseline is active. No additional owner endorsement is inferred or required to record this decision. Approval is distinct from implementation and verification.
- Module owners implement their approved interfaces and consume only a provider `api` with an approved edge and capability permission. Never import foreign `internal`, entities or repositories, introduce cycles/common-to-business dependencies, or weaken ArchUnit to bypass ownership.
- Reuse existing interfaces/DTOs; no competing contracts, silent renames or unapproved breaking changes. Additional public capabilities/dependency directions require prior Tech Lead approval. Consult affected owners and preserve compatibility where practical; Tiến has final cross-module architectural authority. Unpushed teammate work remains unknown.
- Use constructor injection and test-scoped mocks/fakes while providers are absent. Do not create speculative/no-op production implementations to satisfy injection; compilation does not establish startup or integration readiness.
- Transfers uses `FinancialPosting`; Administration uses `AllocationPosting`; Bootstrap uses `GenesisPosting`. Only Ledger uses `PostingAccounts`; its Treasury initializer is restricted to the Ledger GenesisPosting implementation. Review helper delegation explicitly.
- Transfers owns the transfer outer transaction; Administration owns allocation/status orchestration and required audit. Ledger owns controlled paired posting/facts/reconciliation; Accounts owns balance/status invariants; Idempotency owns scope/canonical intent/outcomes. All required effects join the outer transaction; no isolated debit/credit commit. Pending values never confirm a commit.
- Pure APIs have no transaction annotations or provider Beans. The later 2026-10-22 [V1 engineering freeze](docs/6-governance/25-design-decision-register.md#v1-engineering-design-freeze--2026-10-22) selects concrete transaction guards, locking, retries, canonicalization, cursor and protected operator setup under TD-001..014. Their implementation/runtime evidence remains separate; TD-015/016 alone did not approve them.
- On a contract mismatch, preserve existing work, compare exact packages/signatures and report the mismatch/options to Tiến. Stop only the affected integration until a Tech Lead decision, then adapt member-owned code without duplicate APIs and rerun tests. Continue independent work; a freeze permits reviewed corrections.
- Synchronize public source, canonical inventory, governance, architecture/data notes and relevant tests in the same change. Do not alter REST/OpenAPI or migrations to accommodate an internal contract without separate reviewed authorization.

## Financial safety

- Consult approved use cases/rules and domain/data design before changing financial behavior.
- Use integral VND values, never floating-point money; reject unsafe numeric values.
- Keep balance/status invariants and immutable posting facts within their owning modules.
- Commit balances, posting facts, final idempotency outcomes and required success audits atomically.
- Financial operations use PostgreSQL transactions; never commit debit and credit independently.
- Preserve ledger immutability, resource authorization and scoped idempotency semantics.
- Report financial success only after commit confirmation; technical failure is not a final business rejection.
- Never expose GENESIS, minting, direct balance overwrites or dataset resets as ordinary APIs.

## Workflow

1. Inspect `git status`, relevant files and document statuses before editing.
2. Stay within the requested scope, preserve unrelated changes and follow existing conventions.
3. Add or update meaningful tests for changed behavior; use isolated test databases.
4. Run applicable checks and distinguish static validation from runtime evidence.
5. Never claim an unexecuted check passed or that unrelated work is complete.
6. Do not commit or push unless explicitly requested.

## Commands

Use [Makefile](Makefile) as the command index; run `make help` and use only existing targets.

| Command | Purpose |
|---|---|
| `make setup` | Create local `.env` only when missing |
| `make up` / `make down` | Build/start or stop the application stack; preserve database volumes |
| `make test` / `make build` | Run Maven tests or package the executable JAR |
| `make health` | Check `/actuator/health` |
| `make docs-up` / `make docs-down` | Manage the separate Swagger UI stack |
| `cd backend && ./mvnw test` | Direct Maven Wrapper testing |

Host Maven commands require Java 21; integration tests require a Testcontainers-compatible runtime.
The Docker Compose workflow requires no host Java, Maven or PostgreSQL installation.
Native datasource setup is in root README; documentation checks are in `docs/README.md`.

## Secrets and persistent data

- Never commit `.env`, credentials, session secrets or private keys; preserve existing local configuration.
- Preserve server-managed session and authorization boundaries; keep database access local/private.
- Never log passwords, session tokens or sensitive financial data.
- Version schema changes with Flyway; add new migrations rather than modifying applied migrations.
- Do not use Hibernate schema auto-update or implicitly delete volumes/reset persistent data.
- Never run tests against a developer's persistent database.

## Completion report

Summarize changed files, key decisions, checks actually run, failures/skipped checks and remaining risks.
