# BK-005 — Independent Identity/Customer Registration Implementation

> Owner: Hưng. Date: 2026-10-23. Branch: `feat/BK-005-customer-registration`.
> Base: updated `develop`, `31d2425131766306ccf6a815ca057349bee77bff`.
> Status: independent Identity/Customer implementation verified locally. Full registration integration/HTTP acceptance remains pending.

## 1. Scope and authority

The user authorized BK-005 independently of the unmerged BK-004 branch. This branch starts from fetched `develop`; no BK-004 changes are copied or merged. Course P1 means Business & Architecture, not the optional P1 backlog priority. This contribution implements the approved registration foundation; it does not complete every course P1 deliverable.

Apply [AGENTS.md](../../AGENTS.md), [BK-005 and member readiness](../02-requirements/10-product-backlog.md), [UC-AUTH-01 and acceptance scenarios](../01-discovery/04-use-cases.md), [business rules](../01-discovery/06-business-rules.md), [PRD](../02-requirements/09-product-requirements.md), [functional specification](../02-requirements/12-functional-specifications.md), [module contracts](../03-design/module-contracts-and-boundaries.md), [architecture](../03-design/15-system-architecture.md), [data/transaction design](../03-design/16-domain-model-and-data-design.md) and [TD-007/governance](../6-governance/25-design-decision-register.md).

The existing Java interfaces and V1/V2 migrations remain unchanged. No production Accounts implementation, login/session workflow, Admin bootstrap, public module capability, cross-module edge, seed data or financial operation is introduced. Existing Markdown files are preserved; this is a new implementation/evidence record. No commit/push/merge is performed.

## 2. Implementation

| Area | Source | Behavior |
|---|---|---|
| Registration orchestration | [RegisterCustomer](../../backend/src/main/java/com/finfast/identity/internal/application/usecase/RegisterCustomer.java) | Validate input, hash outside the transaction, then create CUSTOMER User and call `CustomerProvisioning` in one owned READ COMMITTED transaction. Reject invocation inside an existing transaction; return only after successful commit. |
| Input | [RegistrationCommand](../../backend/src/main/java/com/finfast/identity/internal/application/usecase/RegistrationCommand.java) | Required display name/email/password; Jakarta email validation; email lowercase using `Locale.ROOT`; schema bounds measured in Unicode code points; reject NUL and malformed surrogate text. Preserve display name/password exactly; do not trim or normalize passwords. Diagnostic `toString` is redacted. No role, balance, account-number or identity-document field. |
| Credential hashing | [BcryptRegistrationPasswords](../../backend/src/main/java/com/finfast/identity/internal/adapter/outbound/security/BcryptRegistrationPasswords.java), [POM](../../backend/pom.xml) | Standard Spring Security BCrypt, default strength 12, explicit constructor tuning after measurement. Reject more than 72 UTF-8 password bytes before encoding; no truncation/prehash. Add only BOM-managed `spring-security-crypto` for approved TD-007, without enabling session/security auto-configuration. |
| User persistence | [JdbcRegistrationUsers](../../backend/src/main/java/com/finfast/identity/internal/adapter/outbound/persistence/JdbcRegistrationUsers.java) | Only writes `app_user`, fixes role to CUSTOMER, relies on PostgreSQL's unique normalized-email constraint with targeted `ON CONFLICT ... DO NOTHING`. Concurrent duplicate emails produce a safe module-owned conflict, not extra profiles/accounts. Other constraint/technical failures escape. |
| Customer provisioning | [ProvisionCustomer](../../backend/src/main/java/com/finfast/customer/internal/application/usecase/ProvisionCustomer.java) | Implements the unchanged `CustomerProvisioning` API, inserts one profile, calls only `AccountProvisioning`, checks returned owner/ACTIVE/zero balance and returns the existing Customer-owned projection. MANDATORY transaction contract; no Identity-to-Accounts edge. |
| Profile persistence | [JdbcCustomerProfiles](../../backend/src/main/java/com/finfast/customer/internal/adapter/outbound/persistence/JdbcCustomerProfiles.java) | Writes only `customer`; requires the same bound, writable READ COMMITTED JDBC transaction. It never queries Identity/Accounts private tables. |
| Expected errors | [RegistrationError](../../backend/src/main/java/com/finfast/identity/internal/application/usecase/RegistrationError.java) | Safe constant `INVALID_REGISTRATION` (UNPROCESSABLE) and `EMAIL_ALREADY_REGISTERED` (CONFLICT), using existing shared error contracts. No final financial outcome is created. |

Identity and Customer JDBC guards reject absent/read-only/wrong-resource/wrong-isolation transactions. User/profile/account changes roll back together when the collaborating Accounts provider correctly joins the transaction. No production fake, fallback or independent commit is supplied. The implementation follows the existing Ledger convention of explicit composition without component-scanned providers until real dependencies are ready; the ordinary application does not silently expose an unprotected route or fail startup for an absent Account provider.

Password inputs intentionally have no newly invented minimum length/complexity policy beyond required nonblank input and the approved representability/capacity constraints. No password hashes or plaintext are included in the returned registration projection. The default cost is 12; cost 4 is used only in repeated test fixtures to keep boundary/concurrency tests inexpensive. Default-cost behavior and salt are tested separately.

## 3. Verification scope

[RegistrationValidationTests](../../backend/src/test/java/com/finfast/identity/internal/RegistrationValidationTests.java) exercises invalid/missing fields, email syntax, PostgreSQL text constraints, Unicode code-point limits, host-locale-independent case normalization, redacted diagnostics, no hashing/persistence on invalid input, exact 72-byte ASCII/non-ASCII boundaries, salt and default BCrypt cost.

[RegistrationIntegrationTests](../../backend/src/test/java/com/finfast/RegistrationIntegrationTests.java) uses the existing pinned PostgreSQL Testcontainer and a freshly migrated database per test. It uses real Identity/Customer persistence and a real Spring JDBC transaction manager. The Accounts collaborator is a **test-only SQL fixture**, not Hải's provider and not production source. Scenarios cover:

- Complete committed CUSTOMER User/profile/one ACTIVE zero VND account; independent connection cannot see uncommitted onboarding.
- Sequential and concurrent case-variant duplicates, with exactly one complete registration.
- Failures after User/profile/account writes, actual profile constraint failure, invalid Account provider projections, precommit and injected commit failures; no success or partial rows, followed by a successful retry where applicable.
- Refusal to join a caller transaction, write without a bound transaction, use another JDBC resource or use read-only/incompatible isolation.

This provides PostgreSQL evidence for the implemented Identity/Customer code with the stated fixture; it does not prove the real Accounts generator, account-number immutability/reuse policy, browser security or a complete deployed registration workflow.

### Execution results

All results below were executed on 2026-10-23 (Asia/Saigon), using the existing pinned Dockerfile, Java 21/Maven Wrapper inside Docker and isolated PostgreSQL Testcontainers. No developer `.env` or persistent database was used.

| Check | Result |
|---|---|
| Docker build-stage package, integration tag excluded as in the existing Dockerfile | **328 tests, zero failures/errors/skips; BUILD SUCCESS** |
| Full `./mvnw --batch-mode --no-transfer-progress verify` with Docker/Testcontainers | **761 tests, zero failures/errors/skips; BUILD SUCCESS**; Maven duration 8 minutes 18 seconds; runner exit 0 |
| New BK-005 test contribution within the full count | **40 tests**: 24 input/BCrypt checks and 16 PostgreSQL registration scenarios; all passed. The other 721 tests are existing regression coverage. Do not add the build-stage subset to the full-suite count. |
| BCrypt default-cost observation | Two cost-12 encodes took **798 ms** in the full run (879 ms in the image build). Salt/verification tests passed. These are local samples, not an endpoint latency/capacity guarantee or password-login acceptance. |
| Executable JAR | Existing `scripts/check_backend_jar.py` passed against the copied artifact/compiled-test-class evidence: executable Spring Boot manifest/classes; no compiled test classes or checked test-only libraries packaged |
| Documentation/OpenAPI | **25 Markdown files, 337 local links, 88 OpenAPI references**, no duplicate YAML keys; OpenAPI schema validator **OK** |
| Change hygiene | Tracked diff and all 13 new files passed whitespace checks. Existing Markdown, migrations and public contracts are unchanged. No commit or push. |

Surefire XML totals were independently summed and matched 761/0/0/0. Local artifacts are retained at `C:\Users\tangk\AppData\Local\Temp\finfast-bk005-31d2425-verification` (summary JSON and `backend/target` containing reports, JAR and compiled test classes). Build/verification logs are `finfast-bk005-build.log` and `finfast-bk005-verify.log` under the same temporary parent. These local files are not committed shared evidence and may be removed by OS cleanup. Only the labelled BK-005 runner/image tag was removed; Testcontainers cleaned its temporary databases, and the other projects' containers remained running.

Host `make help` could not run because Make is not installed; host Java 21 testing was not used. The existing Dockerfile build stage and direct Maven Wrapper invocation above provided the fallback. The JAR check used the repository's existing checker with its backend path pointed to the copied evidence, without altering the checker or the checkout's build outputs. GitHub CI, a deployed registration endpoint, real Accounts integration and browser/security tests were not run or claimed.

## 4. Remaining integration and acceptance

| Requirement | Evidence available from this contribution | Still needed |
|---|---|---|
| FR-001 / AC-001 | Identity/Customer orchestration and database atomicity tests with the Accounts fixture | Wire and test Hải's real `AccountProvisioning`, including account generation and invariant enforcement |
| AC-027 | Input, normalized/concurrent duplicates, CUSTOMER-only persistence, adaptive hash and rollback scenarios | Public JSON schema rejection/privilege-injection tests, approved HTTP statuses, CSRF and real provider integration |
| AC-035 | Consumes Accounts' opaque number; Identity does not generate/reassign it | Real Accounts uniqueness, immutability, restart and never-reuse evidence owned by Accounts |
| AC-037 | Synthetic fixtures; no extra data fields, initial funds, ledger writes or excluded products | Full UI/exposed-path review at integration |

The frozen [API contract](../03-design/17-api-contracts.md) / [OpenAPI](../03-design/contracts/openapi-v1.yaml) requires `POST /api/v1/auth/register` with CSRF, the exact 201 response projection and operation-specific error handling. That route is **not exposed** by this independent contribution: existing `AccountProvisioning` has no production implementation on this baseline, and session/CSRF wiring belongs to the separately authorized BK-007 security work. The canonical API-ERR-01 protocol-error integration exception also remains unchanged. No HTTP 201, real-provider integration, security/browser acceptance, full BK-005 closure or entire-course-P1 completion is claimed.

Next integration must explicitly compose the real same-database providers, preserve the owned outer transaction, add the secured HTTP adapter against the frozen contract and rerun the corresponding acceptance tests. This does not authorize implementing another member's module or starting BK-006/BK-007 here.
