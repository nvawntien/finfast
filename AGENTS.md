# FinFast agent instructions

## Scope and worktree safety

- Start at the [documentation index](docs/README.md). Use the [root README](README.md), [Makefile](Makefile), and checked-in build/configuration files for setup, commands, and exact version pins.
- Follow the [development workflow](docs/development/workflow.md) for Issue, OpenSpec, and documentation updates.
- Before editing, inspect the branch, worktree, and `git status`. Work only within the assigned scope. Preserve unrelated staged, unstaged, untracked, and deleted files; stage only authorized paths. Do not commit or push unless explicitly requested.
- Implement only the requested scope within the approved V1 baseline. Do not infer intended behavior from incomplete source code or test definitions. Approved V1 obligations remain operative until explicitly reconciled and accepted in OpenSpec.
- Use simulated funds and synthetic data. Do not manually modify `CHANGELOG.md` or generated files, commit secrets, reset persistent database volumes, or run tests against a developer's persistent database.
- Prefer existing canonical documents and contracts. Create new documentation only for an approved, substantive need; do not create review reports or parallel requirements, decisions, contracts, task registries, or status files. Verify the actual frontend stack before assuming a framework or adding dependencies.

## Architecture

- Follow the [architecture overview](docs/architecture/overview.md) and [module boundaries](docs/architecture/boundaries.md). Cross-module calls use only approved provider APIs and allowed dependencies; never depend on another module's internals, repositories, or SQL.
- Do not weaken approved REST/Java contracts, schema, module boundaries, or financial constraints. No routine reapproval is needed within the approved baseline; fix ordinary implementation defects directly.
- Use the [decision register](docs/6-governance/25-design-decision-register.md) for approval provenance and scoped amendments to business rules, security/financial constraints, contracts, capabilities, dependencies, incompatible schema, or proven design adequacy. Report conflicts with their source paths and statuses, and stop only the affected integration.

## Financial safety

- Preserve approved business rules in [use cases](docs/01-discovery/04-use-cases.md), [business rules](docs/01-discovery/06-business-rules.md), and [data and transaction constraints](docs/architecture/data-and-transactions.md). Use integral VND; preserve ledger immutability and resource authorization.
- Keep required balances, ledger facts, final outcomes, and success audits in the caller-owned transaction. Report financial success only after confirmed commit; technical failure is not a final business rejection.
- Do not expose ordinary GENESIS, minting, direct balance overwrite, or dataset reset operations.
- Add schema changes through new Flyway migrations; never edit applied migrations or enable ORM schema auto-update. Preserve server-managed session and authorization boundaries.

## Verification and evidence

- Use existing project commands and checks, and add meaningful tests for behavior changes. Financial rollback, commit, and concurrency evidence requires isolated real PostgreSQL and actual transaction proxies; mocks and structural checks do not establish durable financial outcomes.
- Distinguish approved intent, observed implementation, and executed verification. Report only checks actually run, with their command, revision, environment, result, and limits. Test completion and final release acceptance are separate; see the [verification strategy](docs/verification/strategy.md) and [release checklist](docs/05-verification/23-uat-and-release-checklist.md).
- Protect credentials, sessions, and financial data. Preserve local configuration and persistent data; never log secrets or sensitive financial data.
- Summarize changed files, key decisions, checks run, failures or skips, and remaining risks at completion.
