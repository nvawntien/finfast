# 22 — V1 Test Strategy, Canonical Acceptance Execution and Failure Evidence

> Product/UX/REST approval update — 2026-10-22: [the later freeze](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) approves FR-001..013, UX-001..005, NFR-001..006, S01–S10, WEB-01..08, viewport/zoom/keyboard checks and the existing REST schemas/statuses as implementation/test specifications. No runtime result or checkbox changes. API-ERR-01 requires targeted adapter conformance/amendment work for existing generic 400/500 mappings; it is not a new approved HTTP response.

## Freeze verification obligations — 2026-10-22

The [decision register](../6-governance/25-design-decision-register.md#engineering-decisions-and-scoped-statuses) approves design, not tests. BK-003 is merged; banking providers were absent on the audited `develop` baseline. The separately authorized BK-010 FIX-001/Phase 2, Phase 3, Phase 4 and Phase 5 executions below verify domain/persistence infrastructure, Ledger GENESIS/ordinary posting contributions and test-only reconciliation on the feature branch, not complete banking workflows. No financial/security/cloud/performance acceptance is marked PASS by documentation synchronization.

| Evidence level | Required tests for the selected baseline | Current status / owner |
|---|---|---|
| Pure unit | Ledger pair/GENESIS cardinality/sign/magnitude/immutability/MAX_VALUE; FIX-001 `rejectsNulInReference()`; canonical v1 exact text/omitted-empty/raw-invalid/version vectors, scope hashing, UUID unsigned boundary vectors; cursor MAC tampering | 57 Ledger domain tests including FIX-001 executed successfully; canonical/scope/UUID/cursor vectors remain pending; Tiến/Minh/Hải |
| Real PostgreSQL repository | V1/V2 structure/guards (existing suites), exact VARCHAR/text/JSONB bounds incl. NUL/trailing spaces, cross-row persisted facts, dataset sequence rollback/serialization, numeric aggregate safety | Existing schema suites, 26 Phase 2 JDBC, 37 Phase 3 GENESIS and 63 Phase 4 ordinary integration cases executed successfully; Phase 5 adds 38 reconciliation integration invocations with exact numeric aggregates; Phase 3–5 use test-only Accounts, and real provider integration remains pending; Tiến/Hải/Minh |
| Spring transaction integration | Actual concrete proxy REQUIRED/MANDATORY, no standalone posting commit, checked technical rollback, failures after each balance/entry/outcome/audit write and at commit, no false Pending success | Phase 2 resource/proxy checks and Phase 3/4 MANDATORY/partial-write/outer rollback with test Accounts verified; real Accounts/Bootstrap, durable outcomes/required audits and commit acknowledgement workflows NOT RUN; Tiến/Minh/Hải/Trung |
| PostgreSQL concurrency/recovery | Scope wait + fresh statement, duplicate/hash-collision/conflicting payload, opposite-direction unsigned UUID lock order, Treasury/freeze races, 3 total attempts with nominal 25/75 ms backoff within 2 s/remaining lock budget, ambiguous commit resolution, counter late-commit ordering | 3 Phase 2 counter, 3 Phase 3 GENESIS and 7 Phase 4 ordinary race/timeout cases plus 5 Phase 5 committed-snapshot/mixed-workload cases executed successfully; real Accounts/Bootstrap and scope/freeze/restart/retry integration NOT RUN; Minh with owners. Require independent connections/barriers and SQLSTATE assertions, not H2/mocks |
| Read/API/security/browser | High-water/HMAC owner/account/page/version binding, changed cursor rejected, secret continuity across restart; session CSRF/expiry/logout/login rotation, role/ownership and BCrypt cost/72-byte Unicode boundary; existing OpenAPI status/schema assertions | NOT RUN for absent endpoints; Hưng/Hải/Trung/Sĩ; affected metadata/Admin projections await narrow capability amendment |
| Full financial acceptance | AC-003 ordinary movement, AC-008 allocation, AC-010 full rollback, AC-022 every Account balance, AC-023 supply, AC-031 immutable facts; rest of 38 AC/19 CONS crosswalk below; required audit and restart | NOT RUN for full workflows; Tiến/Minh/Sĩ coordinate owners. Phase 5 verifies synthetic fixtures through reusable consistent cross-table probes; real-provider AC-022/023 acceptance remains pending; no production boundary bypass |
| Capacity/deployment | Exact PRD §9 workload and outcome distribution; separate disjoint/hot/duplicate/Treasury diagnostics, scope/Account/counter waits and WAL; private durable DB/TLS/backups/recovery | EV-001/002 pending verification, EV-003 awaiting measurement; Minh/Trung/Sĩ/Hưng |

Use genuine isolated PostgreSQL 16/Testcontainers for locking, visibility, uniqueness, row guards, partial writes, commit/restart and aggregate SQL semantics. Unit fakes prove consumer logic only. Documentation tooling validates references/schema, not transaction behavior. Keep every historical execution record below tied to its original review/revision; do not present it as a new run.

## BK-010 FIX-001 and Phase 2 execution — 2026-10-22

**Executed locally on the authorized feature-branch working tree:** Java 21.0.12.1, Spring Boot 4.1.1 and the existing PostgreSQL 16.15 Testcontainers image `postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea`. Fresh isolated test databases apply unchanged V1/V2; no developer database or persistent volume is used.

From `backend/`, using Java 21 through the invocation environment (no build configuration changes):

```bash
./mvnw --batch-mode --no-transfer-progress -Dtest=LedgerTransactionTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=LedgerTransactionTests,LedgerPersistenceIntegrationTests,LedgerPostingPositionConcurrencyTests,FinancialTransactionAndLedgerTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Final result | Evidence boundary |
|---|---|---|
| FIX-001 checkpoint, before Phase 2 | PASS — 130 tests, zero failures/errors/skips | 57 pure Ledger tests and 73 architecture/contract checks; all three factories reject NUL and preserve valid exact/64-code-point references |
| Focused domain/JDBC/concurrency/schema/architecture run | PASS — 191 tests, zero failures/errors/skips | Includes 26 new JDBC and 3 new concurrency cases, plus 32 existing financial fact/schema tests |
| Full Maven `verify`, rerun after resuming an interrupted run | PASS — 568 tests across 29 suites, zero failures/errors/skips; executable JAR packaged | Includes the new tests and unchanged existing suites; local evidence, not hosted CI or banking acceptance |
| `python3 scripts/check_backend_jar.py` from repository root | PASS | Executable JAR contains no compiled test classes or test-only libraries |

Initial execution found and corrected two implementation/test-fixture defects: the JDBC guard compared a close-suppressing connection proxy instead of its transaction-bound target; a nested generic test configuration leaked into unrelated Spring contexts. The guard now unwraps through `DataSourceUtils.getTargetConnection`, and the fixture uses `@TestConfiguration`. The initial focused run failed (14 failures/9 errors); a JDBC rerun passed 28 cases before adding the nonfinite-timestamp regression, and the final focused run passed 191. The initial full run failed (4 failures/20 errors); a subsequent run was interrupted before completion and is not counted as PASS. The completed full rerun above includes both fixes. Existing tests were not weakened or excluded.

The new tests prove all three fact types round-trip, exact IDs/reference/currency/maximum amount, explicit microsecond timestamps, deterministic complete reconstruction, and safe rejection of incomplete/corrupt facts. Actual Spring proxies reject missing transactions; resource/isolation/read-only guards reject incompatible writes. SQLSTATE-checked duplicate/FK/second-entry failures and outer rollback remove headers, entries and counter increments, including when a caller catches an inner persistence failure. Missing/exhausted counters fail without setup or wraparound. Two independent PostgreSQL transactions expose their backend PIDs; `pg_blocking_pids` proves the second waits on the first. Commit releases the next position; rollback permits reuse; a bounded lock timeout remains a technical failure. Timestamps deliberately run opposite to position order.

This is partial infrastructure evidence toward AC-003/008/010/031 and CONS-02/11/17, not completion of their full scenarios. No Accounts balance mutation, real Treasury eligibility, idempotency outcome, required Admin audit, authorized history/receipt, restart or response-loss workflow is exercised by Phase 2. AC-022/023 reconciliation and supply acceptance remain pending. Concrete adapters are registered as real Spring beans in test contexts; future Ledger use-case wiring must register them behind transaction proxies in production. At that Phase 2 checkpoint, Phase 3–6 had not started. The later Phase 3 execution below does not mark BK-010 Done.

## BK-010 Phase 3 GENESIS contribution execution — 2026-10-22

**IMPLEMENTED AND VERIFIED — Ledger contribution with a test-only Accounts collaborator.** The existing Java 21.0.12.1 / Spring Boot 4.1.1 / PostgreSQL 16.15 Testcontainers baseline above was reused. Each test uses a fresh isolated database with unchanged V1/V2. Actual `GenesisPostingService`, fact repository and dataset repository execute through Spring transaction proxies on the same DataSource; only the missing Accounts provider is replaced with a test-scoped, row-locking zero-Treasury initializer. No production Accounts/Bootstrap implementation or configuration is added.

Commands executed from `backend/`, with Java 21 selected through `JAVA_HOME`/`PATH`:

```bash
./mvnw --batch-mode --no-transfer-progress -Dtest=GenesisPostingIntegrationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=GenesisPostingIntegrationTests,GenesisPostingConcurrencyTests,LedgerTransactionTests,LedgerPersistenceIntegrationTests,LedgerPostingPositionConcurrencyTests,FinancialTransactionAndLedgerTests,DatasetControlConstraintTests,SchemaIdentityAndCounterTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Observed result | Scope |
|---|---|---|
| New GENESIS integration suite | PASS — 37 tests, zero failures/errors/skips | Correct supply including BIGINT maximum, exact single-entry/linkage/position/reference/time, invalid input/eligibility, rollback, repeated setup and actual proxy checks |
| GENESIS + prior Ledger/schema/architecture suite | Initial FAIL — 264 tests, one architecture failure; corrected rerun PASS — 264, zero failures/errors/skips | Initial service dependency on concrete JDBC adapters violated application isolation. Two Ledger-internal outbound ports corrected the dependency; SQL and architecture rules were unchanged |
| Full Maven `verify` | PASS — 608 tests across 31 suites, zero failures/errors/skips | Includes 37 new GENESIS integration and 3 new concurrency cases, all prior tests, and executable JAR packaging |
| `python3 scripts/check_backend_jar.py` from repository root | PASS | No compiled test classes or test-only libraries in the executable JAR |

Rollback is injected after Treasury initialization, transaction insertion, entry insertion, dataset insertion and Pending return. Each point is tested with both a preexisting zero Treasury and Treasury provisioned inside the failed outer transaction. Post-transaction snapshots prove no additional balance, transaction, entry or singleton survives; a caught inner SQL failure still prevents outer commit. Collision tests exercise PostgreSQL reference uniqueness and orphan-GENESIS uniqueness failures without repairing/replacing preexisting data. Existing domain tests reject malformed GENESIS entries; dataset type/position/linkage checks run at their owning layer.

The three concurrency invocations use independent backend PIDs and observe actual `pg_blocking_pids` relationships with bounded waits. Two Ledger calls both observe an absent dataset, then serialize on the scoped Accounts Treasury lock: a committed winner prevents a second Pending/funding, while a rolled-back tentative winner permits the remaining transaction to initialize once. A separate test-only outer provisioning race loses on `uq_account_single_treasury` (SQLSTATE 23505) before reaching Ledger. Losing-transaction marker rows also roll back. No Java monitor substitutes for PostgreSQL locking, and no internal Ledger retry is added.

Repeated calls with the same/different amount or another Account are rejected before Accounts mutation. Recreating the Spring context against the same database still rejects reseeding; **this is not a process/deployment restart test**. Pending is observed before outer completion while independent reads still see the prior committed state. No standalone commit or automatic setup is performed.

This contributes partial evidence to AC-010/020/022/023/031 and CONS-02/14/15/16; no canonical scenario is marked complete. Real Accounts/Treasury providers, protected operator authorization, configured Admin provisioning, Bootstrap outer setup/race recovery, production composition and restart/no-reseed integration remain pending with Hải/Trung/Hưng and Tiến. At that Phase 3 checkpoint, Phase 4–6 remained NOT STARTED; the separately authorized Phase 4 execution follows below. BK-009/BK-010 remain incomplete.

## BK-010 Phase 4 ordinary posting execution — 2026-10-22

**IMPLEMENTED AND VERIFIED — Ledger contribution with test-only Accounts.** Java 21.0.12.1, Spring Boot 4.1.1, PostgreSQL 16.15 and the existing pinned Testcontainers image above are unchanged. The starting checkout was clean; a pre-change full `verify` re-established the 608-test baseline. All tests use disposable isolated databases and the actual Spring-managed Ledger services/adapters. Production Accounts composition, Transfers/Idempotency and Administration workflows remain absent from this evidence.

From `backend/`, with Java 21 selected through `JAVA_HOME`/`PATH`, executed commands were:

```bash
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Dtest=OrdinaryPostingIntegrationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=OrdinaryPostingIntegrationTests,OrdinaryPostingConcurrencyTests test
./mvnw --batch-mode --no-transfer-progress '-Dtest=OrdinaryPostingIntegrationTests#incompleteGenesisCannotPost' test
./mvnw --batch-mode --no-transfer-progress -Dtest=OrdinaryPostingIntegrationTests,OrdinaryPostingConcurrencyTests,LedgerTransactionTests,LedgerPersistenceIntegrationTests,LedgerPostingPositionConcurrencyTests,GenesisPostingIntegrationTests,GenesisPostingConcurrencyTests,FinancialTransactionAndLedgerTests,DatasetControlConstraintTests,SchemaIdentityAndCounterTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Actual result | Evidence boundary |
|---|---|---|
| Full baseline verification before Phase 4 | PASS — 608 tests, zero failures/errors/skips | Re-executed locally; historical test counts were not assumed |
| First ordinary integration run | PASS — 56 tests | Success/rejection, partial-write/outer rollback and transaction guards |
| First ordinary integration + concurrency run | PASS — 63 tests | Added 7 independent-transaction race/timeout cases |
| Incomplete-GENESIS precondition regression before correction | FAIL — 5 tests, 5 failures, zero errors/skips | Exposed that the previous counter primitive could reserve against incomplete GENESIS facts |
| Combined final Ledger/domain/JDBC/GENESIS/ordinary/schema/architecture run | PASS — 334 tests, zero failures/errors/skips | Includes all 70 Phase 4 cases after readiness enforcement and null-intent regressions |
| Final full Maven `verify` | PASS — 678 tests across 33 suites, zero failures/errors/skips | 63 new ordinary integration + 7 new concurrency cases, original suites and executable JAR packaging |
| `python3 scripts/check_backend_jar.py` from repository root | PASS | Executable JAR contains no compiled test classes or test-only libraries |

The missing precondition was corrected with a read-only, Ledger-table GENESIS completeness check before the unchanged counter UPDATE. Five negative fixtures now reject missing, wrong-Account, wrong-delta, wrong-supply and extra GENESIS entries/linkage without repairing existing data or committing pending balances. Missing/exhausted singleton tests retain technical rollback behavior. This adds no schema constraint or production reconciliation capability.

Successful transfers/allocations verify one ordinary transaction, two distinct opposite entries, exact UUIDs/amounts, unique references, increasing positions, reconstruction and microsecond Pending metadata. Amounts 1, 75 and BIGINT maximum execute with valid resulting balances. Notes preserve absent/empty/exact text; allocation reasons remain exact. No required Admin audit or durable outcome is fabricated. The Tech Lead explicitly approved the two Ledger eligibility codes/messages documented in [data design §13](../03-design/16-domain-model-and-data-design.md#13-phase-4-ordinary-posting-contribution); Accounts fixture reason identifiers are test-only, not Hải's production error catalog.

Rejected funds/frozen/type/recipient/self-transfer/ownership/amount/reason cases leave complete state snapshots and the counter unchanged. Expected Accounts reasons pass through by identity. A resulting-credit overflow fixture deliberately starts with inconsistent balances: in a valid conserved dataset bounded by BIGINT supply, that overflow is unreachable. It verifies pre-write numeric rejection only and makes no valid-supply claim for corrupt input.

Actual PostgreSQL failures after each balance write, counter write, header insert and first/second entry insert roll back both balances, position and all new facts. Outer failure after Pending does the same; catching an inner SQL failure still produces outer rollback. Real proxies enforce MANDATORY; actual JDBC guards reject incompatible isolation and PostgreSQL rejects read-only locking/writes. Independent reads see only prior committed state until the outer transaction completes.

The test-only Accounts provider uses individual sequential row locks in unsigned UUID order, revalidates the locked participant set and computes both checked resulting balances before writes. Boundary vectors compare its ordering with PostgreSQL UUID ordering. Seven concurrency invocations cover competing spends, opposite transfers, Treasury allocations, disjoint pairs waiting for the global counter, reservation reuse after rollback, and Account/counter lock timeouts. Distinct backend PIDs and `pg_blocking_pids` prove actual database blocking; disjoint waiters have already applied both pending balances before waiting on the counter. Later transactions observe committed balances, and equal recorded timestamps do not establish position order. Bounded waits and SQLSTATE 55P03 checks replace sleep-based assumptions; no Ledger retry is introduced.

This is partial evidence toward AC-003/004/007/008/010/016/017/022/023/031/032/036 and CONS-01/02/11/12/15/16, not completion of canonical scenarios. Consistent test-only probes validate the synthetic fixtures. At that Phase 4 checkpoint Phase 5 had not started; its separately authorized test-only execution follows below. No production reconciliation reader is implemented. Real Accounts providers, caller authorization, scoped idempotency/final outcomes, same-key recovery, required BK-013 audit, HTTP, freeze integration, restart and benchmark acceptance remain pending. Phase 6 is NOT STARTED; BK-010 is not Done.

## BK-010 Phase 5 reconciliation foundations execution — 2026-10-23

**IMPLEMENTED AND VERIFIED — test-scoped foundation:** starting branch `feat/BK-010-ledger-core`, HEAD `306ea3cd3a20faafcebf87647153484aa71671d1`, clean working tree. Phase 5 was explicitly authorized; no staging, commit, push, migration, public API or production code change is included. [Data design §14](../03-design/16-domain-model-and-data-design.md#14-phase-5-test-scoped-reconciliation-foundation) describes the helper and snapshot contract.

Executed from `backend/` using Temurin Java 21.0.12.1+1, the existing pinned PostgreSQL 16.15 Testcontainer and V1/V2 in disposable isolated databases:

```sh
env JAVA_HOME=/tmp/finfast-bk010-java21/opt/java/openjdk PATH=/tmp/finfast-bk010-java21/opt/java/openjdk/bin:$PATH ./mvnw --batch-mode --no-transfer-progress -Dtest=LedgerReconciliationIntegrationTests,LedgerReconciliationConcurrencyTests test

env JAVA_HOME=/tmp/finfast-bk010-java21/opt/java/openjdk PATH=/tmp/finfast-bk010-java21/opt/java/openjdk/bin:$PATH ./mvnw --batch-mode --no-transfer-progress -Dtest=LedgerTransactionTests,LedgerPersistenceIntegrationTests,LedgerPostingPositionConcurrencyTests,GenesisPostingIntegrationTests,GenesisPostingConcurrencyTests,OrdinaryPostingIntegrationTests,OrdinaryPostingConcurrencyTests,LedgerReconciliationIntegrationTests,LedgerReconciliationConcurrencyTests,FinancialTransactionAndLedgerTests,DatasetControlConstraintTests,SchemaIdentityAndCounterTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test

env JAVA_HOME=/tmp/finfast-bk010-java21/opt/java/openjdk PATH=/tmp/finfast-bk010-java21/opt/java/openjdk/bin:$PATH ./mvnw --batch-mode --no-transfer-progress verify
```

- Initial focused run: **40 PASS**, zero failures/errors/skips. Additional offsetting-drift, mixed transfer/allocation commit/rollback and actual read-only enforcement assertions were then added during review; the final focused run passed **43** (38 integration, 5 concurrency), zero failures/errors/skips. No failing or interrupted run is hidden.
- Combined Ledger domain, Phases 2–5, schema, architecture/capability and module-value suite: **377 PASS**, zero failures/errors/skips. The existing Phase 4 race/rollback tests now reuse the stronger verifier rather than maintaining a duplicate boolean SQL check.
- Full Maven `verify`: **721 PASS** across 35 suites, zero failures/errors/skips; the prior 678 tests and 43 new Phase 5 tests all executed. No test exclusion or dependency change was required.

Repository-root checks also passed: `python3 scripts/check_backend_jar.py` (executable JAR, no compiled test classes/test-only libraries), `/tmp/finfast-freeze-docs-venv/bin/python -B scripts/check_docs.py` (24 Markdown files, 315 local links, 88 OpenAPI references), and `git diff --check`. All 20 documentation dependency pins match that existing environment. New untracked Java files were separately checked for trailing whitespace/tabs/final newlines; no Git staging was used to inspect them.

The immutable findings distinguish Account balance mismatches, supply, GENESIS/linkage, ordinary entry cardinality/sign/magnitude/net, transaction metadata and posting positions/counter. Empty Customer Accounts are included and GENESIS is counted once from zero. NUMERIC-to-BigInteger conversion preserves corrupt total balances of `27670116110564327421` and signed sums of `18446744073709551614` / `-18446744073709551616`; valid one-VND and BIGINT-maximum postings also reconcile. Offsetting Account drift is detected even when total supply still matches.

Controlled sequential and concurrent workloads execute actual Ledger GENESIS, transfer and allocation use cases with the existing test-only Accounts provider. Expected transaction UUID sets, entry counts, positions and complete state snapshots detect missing/extra effects after business rejection and SQL/outer rollback. Existing Phase 4 concurrency tests cover competing debits, opposite/disjoint pairs, Treasury contention, counter serialization/reuse and bounded lock timeouts. New mixed transfer/allocation races verify both commit and allocation rollback.

Snapshot tests use distinct backend PIDs and bounded latches. While a writer is held after the first balance write or after complete Pending facts, independent reconciliation reads only the prior committed state. A held read-only REPEATABLE READ transaction retains that state across writer commit; a fresh verifier sees the new complete state. A deliberately naive two-statement READ COMMITTED probe observes old balance 60 and new signed sum 50, demonstrating why it is unsuitable. A verifier called inside the writer's transaction still uses a separate connection and excludes its tentative writes. PostgreSQL reports read-only/repeatable-read settings and rejects attempted writes with SQLSTATE 25006.

Committed incomplete ordinary/GENESIS rows allowed by V1/V2 produce deterministic diagnostics, without repair. Impossible negative balances, nonpositive amounts, invalid currency, duplicate positions/GENESIS/participants and invalid dataset links are rejected by the original constraints/triggers (23505/23514), without disabling guards. A denied SELECT propagates SQLSTATE 42501 rather than returning healthy/empty data.

This is partial evidence for BR-LED-02..06, BR-ADM-02, AC-003/008/010/022/023/031 and CONS-15/16; no canonical scenario is marked fully complete. Cross-table SQL remains strictly test-scoped under the existing production-read exception. Real Accounts/Treasury, protected Bootstrap/restart, durable Idempotency outcomes/replay, required Administration audit, authorized reads, HTTP/security and performance acceptance remain pending with their owners. A production-wide reader still needs a scoped capability amendment with Hải/Tiến. **Phase 6 is NOT STARTED; BK-010 is not Done.**

## BK-013 Phase 2 JDBC audit persistence execution — 2026-10-23

**IMPLEMENTED AND VERIFIED — isolated Administration persistence only.** Starting branch `feat/BK-013-admin-allocation`, HEAD `aa4e4ade6316e9c59090a66bca22c587d2deddea`, clean working tree. Phase 2 was explicitly authorized. The existing Phase 1 model, outbound port and 38 pure tests remain byte-for-byte unchanged. See [BK-013 tracking](../02-requirements/10-product-backlog.md#bk-013-phase-2--jdbc-admin-audit-persistence) for scope and remaining owners.

The valid pre-change baseline and subsequent tests used Temurin Java 21.0.13+9, Spring Boot 4.1.1 and the existing pinned PostgreSQL 16.15 Testcontainer. An initial invocation under default host Java 26 failed with 759 tests, zero failures, 438 errors and zero skips because the sandbox denied Docker/network socket access. This was environment failure, not accepted baseline evidence. Selecting Java 21 and executing with Docker/socket access resolved it before any source changes; no test, dependency or infrastructure configuration was weakened.

Executed from `backend/` with `JAVA_HOME=/tmp/finfast-jdk21` and that JDK's `bin` prepended to `PATH`:

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress -Dtest=AdminAuditPersistenceIntegrationTests,AdminAuditEventTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Actual result | Evidence boundary |
|---|---|---|
| Java 21 pre-change full baseline | PASS — 759 tests across 36 suites, zero failures/errors/skips | Re-established Phase 1 and all existing tests before source edits |
| Focused Phase 2/domain/architecture/contract run | PASS — 155 tests across 6 suites, zero failures/errors/skips | 44 new audit invocations, 38 unchanged domain cases and 73 existing architecture/contract checks |
| Final full Maven `verify` | PASS — 803 tests across 37 suites, zero failures/errors/skips | All existing and new tests, followed by executable JAR packaging |

[AdminAuditPersistenceIntegrationTests](../../backend/src/test/java/com/finfast/AdminAuditPersistenceIntegrationTests.java) and its [test support](../../backend/src/test/java/com/finfast/AdminAuditPersistenceTestSupport.java) reuse `PostgresTestSupport`, `IsolatedTestDatabase` and `DatabaseFixtures`. Each invocation applies unchanged V1/V2 in a fresh disposable database inside the existing container. Synthetic ADMIN, Customer Account and DEMO_ALLOCATION transaction prerequisites are table fixtures, not a completed funding workflow. No developer datasource, Compose volume, real dataset or live teammate implementation is used.

Eight complete-row cases verify supplied UUIDs, fixed action/outcome, NULL statuses, exact ordinary/whitespace/combining/supplementary Unicode reasons and 500-code-point boundaries, including trailing spaces. Five timestamp cases round-trip exact microseconds, sub-microsecond truncation without rounding, a pre-epoch instant and the finite driver lower boundary, with a non-UTC session zone. Inputs remain immutable. Three Java/driver-unrepresentable cases fail rather than becoming infinity; a PostgreSQL out-of-range timestamp propagates SQLSTATE 22008. The lower-bound guard prevents the pinned [pgJDBC encoder's negative-infinity substitution](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.13/pgjdbc/src/main/java/org/postgresql/jdbc/TimestampUtils.java). No current/database time is substituted.

Duplicate UUID/transaction references propagate 23505; all three missing FKs propagate 23503. Test-only SQL verifies four impossible allocation shapes (23514) and an oversized nonspace reason (22001), without bypassing production domain validation. Phase 1 already rejects oversized reasons including trailing spaces before SQL. Two additional cases stub only JdbcTemplate's affected-row count to verify explicit failure for zero/two rows; these are defensive-branch checks, not PostgreSQL transaction evidence.

Actual Spring proxies enforce MANDATORY before target invocation. The local resource guard separately rejects unproxied writes without a transaction, a thread transaction flag without a bound resource, a different DataSource, a bound autocommit connection, Spring/JDBC read-only modes and REPEATABLE READ/SERIALIZABLE writes. Successful writes run at actual READ COMMITTED on the caller's bound connection. A reader with a distinct PostgreSQL backend PID cannot see the inserted audit until outer commit. Explicit outer rollback, a configured checked `IOException`, and a later SQL division-by-zero fault remove the audit. Duplicate audit failure removes prior caller marker/audit writes; catching the proxied persistence error still produces `UnexpectedRollbackException` and no surviving writes. The adapter never starts or commits a transaction.

Repository-root checks passed: `python3 scripts/check_backend_jar.py` (executable JAR without compiled tests/test libraries), `/tmp/finfast-docs-venv/bin/python scripts/check_docs.py` (24 Markdown files, 325 local links, 88 OpenAPI references; no duplicate YAML keys), `/tmp/finfast-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml` and `git diff --check`. The isolated documentation environment uses the unchanged `scripts/requirements-docs.txt` pins. New untracked Java files were also checked directly for trailing whitespace, tabs and final newlines, without staging.

A separate second pass inspected every new/modified file and confirmed exact insert-only mapping, immutable input, failure propagation, transaction safeguards and unchanged module ownership. All pre-existing Java sources/tests, migrations, POM and OpenAPI remained byte-identical to HEAD. Changes remain unstaged and uncommitted; no forbidden Git operation was executed.

This contributes partial persistence evidence toward AC-008/010/019 and CONS-13 only. FKs prove existence, not authenticated Admin authority, ACTIVE Customer eligibility or a correct Treasury posting workflow. Real Accounts/Ledger/Idempotency orchestration, final durable outcomes, replay/recovery, HTTP/commit-confirmed success and protected setup remain unverified here. **BK-013 is not Done; at the Phase 2 checkpoint, Phase 3 and later were NOT STARTED and remained separately authorized work.** No full AC-008/010/013/019/032 acceptance is claimed.

## BK-013 Phase 3 allocation orchestration execution — 2026-10-23

**IMPLEMENTED AND VERIFIED — Administration application contribution with bounded verification.** Explicit Phase 3 authorization on clean `feat/BK-013-admin-allocation` HEAD `533f9b80da6afa0bb4dfa8c340905c840473df8f`. The merged TD-016 read amendment is active. [BK-013 implementation/source inventory](../02-requirements/10-product-backlog.md#bk-013-phase-3--administration-allocation-orchestration) describes the five new production types and four test files. Existing Phase 1/2, other modules, public APIs, architecture rules, V1/V2, dependencies and frozen OpenAPI are preserved.

Runs use Temurin 21.0.13+9, Maven Wrapper 3.9.11, Boot 4.1.1 and the existing PostgreSQL 16.15 Testcontainer with fresh isolated databases and unchanged Flyway migrations. No developer database or persistent Compose volume is used. From `backend/`, with `JAVA_HOME=/tmp/finfast-architecture-jdk21` and its `bin` prepended to `PATH`:

```sh
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocationCommandAndCanonicalIntentTests,AllocateDemoFundsServiceTests,ProductionArchitectureTests,ReadCapabilityVerificationTests,CapabilityVerificationTests,RuleVerificationTests,ModuleContractValueTests,ReadCapabilityContractTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AdminAllocationOrchestrationIntegrationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AdminAllocationOrchestrationIntegrationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocationCommandAndCanonicalIntentTests,AllocateDemoFundsServiceTests,AdminAllocationOrchestrationIntegrationTests,AdminAuditEventTests,AdminAuditPersistenceIntegrationTests,OrdinaryPostingIntegrationTests,OrdinaryPostingConcurrencyTests,LedgerPersistenceIntegrationTests,GenesisPostingIntegrationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ReadCapabilityVerificationTests,ModuleContractValueTests,ReadCapabilityContractTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Actual result | Evidence boundary |
|---|---|---|
| Pre-change full baseline | PASS — 904 tests / 40 suites, zero failures/errors/skips | Current merged baseline, not the older Phase 2 803-test count |
| Initial consumer/canonical/architecture run | PASS — 198 tests / 8 suites, zero failures/errors/skips | 47 new pure/mocked invocations and 151 existing architecture/contracts |
| Initial PostgreSQL orchestration run | FAIL — 27 tests, 1 failure, zero errors/skips | Known pre-commit TransactionSystemException exposed default manager cleanup behavior described below |
| PostgreSQL rerun with corrected composition and both pre-commit exception cases | PASS — 28 tests / 1 suite, zero failures/errors/skips | Real service/Ledger/audit proxies, test Accounts and non-durable outcome coordination |
| Combined new/existing Ledger/Admin/architecture regression | PASS — 441 tests / 15 suites, zero failures/errors/skips | Includes 38 unchanged audit-domain, 44 audit JDBC, 63 ordinary posting, 7 ordinary concurrency, 26 Ledger JDBC and 37 GENESIS tests |
| Final full verification after second-pass review | PASS — 979 tests / 43 suites, zero failures/errors/skips | All existing tests plus 28 consumer, 19 command/canonical and 28 PostgreSQL Phase 3 invocations; executable JAR packaged |

The initial failure is retained as evidence: the shared fixture used default `JdbcTransactionManager.rollbackOnCommitFailure=false`. A TransactionSystemException thrown by a beforeCommit callback takes Spring 7.0.9's TransactionException path without explicit rollback; JDBC cleanup restores autocommit, and the test observed persisted balances/facts/audit despite caller failure. The new fixture enables `rollbackOnCommitFailure` on the **same existing manager**, retains every rollback assertion and now tests both ordinary runtime and TransactionSystemException pre-commit faults. This is a composition safeguard, not an application catch/rejection or an independent transaction. Production composition is not installed in Phase 3 and must retain that setting; actual connection loss/uncertain commit and failures after commit still need original-key recovery. These tests do not prove rollback after an already successful commit.

The final 28 consumer cases use mocked provider APIs and explicit transaction metadata only: denied missing/expired/Customer identity before protected access, current trusted actor and exact scope, inspect-before-revalidation, no writes for replay/conflict/PROCESSING/UNKNOWN, precise posting/audit/outcome order, immutable target UUID, original completion snapshot, missing recipient, positive amount/reason rejection, stale snapshot independence, provider inconsistencies and escaping technical failures. The 19 command/canonical cases pin version/order/TREASURY/tags/decimal bounds, literal ASCII/Unicode/control JSON and UTF-8, exact composed/decomposed text, changed intent fields and structural storage validation. Cross-owner vectors, malformed HTTP tokens, absent required HTTP fields and version-upgrade readers remain unverified.

The 28 real PostgreSQL cases reuse existing Ledger posting/GENESIS/persistence/reconciliation fixtures plus the real audit adapter. All required participants join one READ COMMITTED connection, with backend PID/resource/autocommit checks. Actual proxies create REQUIRED and join MANDATORY; a surrounding REQUIRED transaction can still roll back the returned Pending. Tests cover one/two-balance failures, header/entry triggers, audit trigger, later SQL/outcome faults, a checked IOException thrown directly into the outer service target after audit, caught rollback-only SQL, both pre-commit faults, same-key retry after rollback, synthetic final replay/conflict, frozen/insufficient/absent/zero/blank rejection, read-only/isolation/unproxied guards and BIGINT maximum. An independent reader sees no tentative effects while a bounded latch holds the operation after audit; the external future cannot complete until the outer proxy does. Reconciliation probes verify committed synthetic balances, entries and conserved supply.

Repository-root checks passed: `python3 scripts/check_backend_jar.py`; `/tmp/finfast-capability-docs-venv/bin/python scripts/check_docs.py` (24 Markdown files, 342 local links, 88 OpenAPI references; no duplicate YAML keys); `/tmp/finfast-capability-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml`; `docker compose -f compose.docs.yaml config --quiet`; and `git diff --check`. The isolated Python environment matches all 20 checked-in dependency pins. Every new untracked Java file also passed a direct whitespace/final-newline check. A separate second pass reviewed all nine new Java files and both modified canonical documents; it refined the mocked identity cases to the three actual identity failures and added trusted-actor/replay-before-validation coverage before final verification. Exact file allowlist, unchanged pre-existing Java/schema/API/build source, empty staged diff and original branch/HEAD were verified. All work remains unstaged/uncommitted; no Git mutation was performed.

Identity, Account queries and the existing paired Accounts collaborator are test-only. The Idempotency fixture compares full typed scope/intent in memory and observes afterCommit; it does not implement advisory locks, hashing, SQL persistence or crash durability. It writes **zero `request_outcome` rows**, including on success. Therefore this is consumer orchestration plus real Ledger/audit/transaction evidence, not real outcome persistence or full AC-013 acceptance. Synthetic rejection replay after unfreeze and sequential duplicate checks do not prove concurrent scope safety. Production identity/session/CSRF, Accounts providers, Idempotency coordination/durable outcomes, protected setup, retries, uncertain commits, process restart, HTTP and Phase 4/5 remain pending. **BK-013 is not Done; no full canonical AC is marked accepted.**

## BK-013 Phase 3 interface-first correction execution — 2026-10-23

**Tech Lead-authorized internal architecture correction**, starting on `feat/BK-013-admin-allocation` HEAD `533f9b80da6afa0bb4dfa8c340905c840473df8f`. The starting working tree already contained nine untracked Phase 3 Java files and changes to backlog/test strategy; all were preserved before editing. This implements the inbound boundary portion of the [mandatory architecture convention](../03-design/15-system-architecture.md#33-java-project-and-module-structure-doc-02), without changing the earlier financial/public-contract approvals or implementing another phase.

The new Administration-local AllocateDemoFunds interface exposes the existing operation using DTOs in `application/dto`; its concrete implementation retains the transaction annotation and orchestration. The canonicalizer moves to `application/service`, with unchanged encoding body and literal golden expectations. The local error catalog remains package-private beside its use case; helpers/DTOs/domain types do not gain interfaces. [Current implementation/source links](../02-requirements/10-product-backlog.md#phase-3-architecture-correction--2026-10-23).

Using Temurin Java 21.0.13+9, Maven Wrapper 3.9.11, Boot 4.1.1 and isolated PostgreSQL 16.15 Testcontainers, from `backend/` with `JAVA_HOME=/tmp/finfast-architecture-jdk21` and its `bin` prepended to PATH:

```sh
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocateDemoFundsServiceTests,AllocationCommandAndCanonicalIntentTests,AdminAllocationOrchestrationIntegrationTests,AdminAuditEventTests,AdminAuditPersistenceIntegrationTests,ApplicationBoundaryVerificationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ReadCapabilityVerificationTests,ModuleContractValueTests,ReadCapabilityContractTests clean test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Actual result | Evidence boundary |
|---|---|---|
| Current working-tree baseline | PASS — 979 tests / 43 suites, zero failures/errors/skips | Re-ran the complete pre-correction Phase 3 contribution |
| Focused clean regression | PASS — 326 tests / 12 suites, zero failures/errors/skips | All 75 Phase 3 cases, 82 prior audit-domain/JDBC cases, 14 new boundary cases and 155 existing/extended architecture/contract cases |
| Final full verification | PASS — 997 tests / 44 suites, zero failures/errors/skips | Existing 979 cases plus 14 boundary cases and four production ArchUnit checks; executable JAR packaged |

Actual consumer fields and Spring bean lookup now use the inbound contract. The existing test configuration still has `proxyTargetClass=true`; strengthened assertions verify CGLIB and lookup identity through AllocateDemoFunds. All prior transaction, snapshot, checked-exception, no-write replay/conflict/non-final, audit/outcome-failure and pre-commit rollback assertions are retained. The same existing JdbcTransactionManager still enables `rollbackOnCommitFailure`, and no second manager or production composition is added.

The new generic production rules enforce inbound-port interfaces/purity, inbound-adapter dependency direction and DTO isolation. Fourteen JUnit cases prove legitimate port/DTO/implementation/adapter directions and concrete helpers, unchanged Ledger public inbound capabilities, BK-013 entry/DTO/annotation structure, and negative interface/concrete/generic/helper/HTTP/JDBC/persistence/transaction-annotation dependencies. Generic rules deliberately do not guess which application helpers are feature entries: explicit entry inventory and review remain required. These checks establish structural boundaries, not controller authorization or runtime financial behavior.

Repository-root checks passed: `python3 scripts/check_backend_jar.py`; `/tmp/finfast-capability-docs-venv/bin/python scripts/check_docs.py` (24 Markdown files, 355 local links, 88 OpenAPI references; no duplicate YAML keys); `/tmp/finfast-capability-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml`; `docker compose -f compose.docs.yaml config --quiet`; and `git diff --check`, plus direct untracked Java whitespace checks. All 20 isolated documentation dependency pins match. Explicit JAR inspection confirms the new inbound port, moved DTOs and canonical helper are present, with no obsolete usecase-package copies; no tests/test libraries are packaged. No test run in this correction failed or skipped integrations; the earlier Phase 3 failed attempt remains recorded in its original execution section.

The independent second pass checked all added/modified/moved files and compared them with the preserved starting working tree. Command/result changes are package-only; the canonical encoder body is byte-identical; the use-case implementation changes only imports, interface declaration and Override; every original consumer/PostgreSQL assertion is preserved, with stronger proxy assertions. Existing architecture-rule bodies, nine-module/eighteen-edge graph, capability permissions, historical governance records, frozen APIs, migrations and OpenAPI remain unchanged. No duplicate/forwarding DTOs or extra helper interfaces remain. AGENTS/architecture/module guidance share the mandatory architecture convention. The staged diff is empty, the branch/HEAD are unchanged, and no Git mutation occurred.

**BLOCKER for full production integration:** the corrected interface does not configure the production transaction manager. Wiring must demonstrate one correctly proxied DataSource/manager with the tested pre-commit safeguard before enabling the financial workflow. The known pre-commit TransactionSystemException finding remains recorded above; passing with the fixture's `rollbackOnCommitFailure` setting does not prove safe default production composition, rollback after successful commit or resolution of a failed/uncertain JDBC commit. Actual during-commit faults and response loss after a potentially successful commit require separately verified original-key durable recovery. Identity/Accounts/Idempotency collaborators remain test-only; there are still zero real final-outcome rows in these orchestration tests. Cross-owner canonical vectors, real providers, protected setup, HTTP, crash/concurrency/recovery and Phase 4/5 remain pending. No full AC or BK-013 Done claim follows.

> **Remaining financial workflow execution plan** grounded in [04 — 38 canonical AC scenarios](../01-discovery/04-use-cases.md), [06 — 19 CONS risk views](../01-discovery/06-business-rules.md), and [09 PRD §§9–11](../02-requirements/09-product-requirements.md). The canonical Given/When/Then in 04 remains normative. `T-*` is a test execution grouping, not new business acceptance criteria. **No complete banking acceptance or performance result is asserted by the scoped checks above.** Documentation checks are distinct from these runtime evidence gates.

## Complete architecture convention verification

The permanent [architecture convention](../03-design/15-system-architecture.md#33-java-project-and-module-structure-doc-02), [module guidance](../03-design/module-contracts-and-boundaries.md#public-api-and-private-implementation) and [AGENTS.md](../../AGENTS.md#architecture) cover all nine modules and implementation phases: module/API boundaries, domain/application/adapter/configuration responsibilities, inbound/outbound contracts, DTO/helpers, constructor injection, financial transaction ownership and verification. Governance points to the convention without defining it as a dated or feature-specific decision. Historical contract approvals and test results remain preserved.

Seven additional production architecture checks cover internal responsibility packages, outbound-port interfaces/purity, inbound-adapter use of application boundaries, inward outbound adapters, constructor injection and prohibition of inbound-adapter transaction ownership. Domain/application isolation is strengthened for framework/persistence dependencies. [HexagonalConventionVerificationTests](../../backend/src/test/java/com/finfast/architecture/HexagonalConventionVerificationTests.java) adds 23 fixture-based invocations, including 21 negative cases. Positive fixtures allow the responsibility tree, constructor injection and inward adapter dependencies; negative cases exercise misplaced code, concrete outbound ports, generic implementation leakage, adapter bypasses, transaction annotations, domain framework/configuration/application leaks and field/setter injection. The existing entry-contract, module, capability and financial assertions are retained.

Starting checkout: `feat/BK-013-admin-allocation` at `533f9b80da6afa0bb4dfa8c340905c840473df8f`, with eight tracked modifications and sixteen untracked Java files already present. All were preserved before editing. Verification uses Temurin Java 21.0.13+9, Maven Wrapper 3.9.11 and isolated PostgreSQL 16.15 Testcontainers. From `backend/`, with the same Java 21 environment as the preceding execution:

```sh
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Dtest=ApplicationBoundaryVerificationTests,HexagonalConventionVerificationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ReadCapabilityVerificationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocateDemoFundsServiceTests,AllocationCommandAndCanonicalIntentTests,AdminAllocationOrchestrationIntegrationTests,AdminAuditEventTests,AdminAuditPersistenceIntegrationTests,ApplicationBoundaryVerificationTests,HexagonalConventionVerificationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ReadCapabilityVerificationTests,ModuleContractValueTests,ReadCapabilityContractTests clean test
./mvnw --batch-mode --no-transfer-progress verify
```

| Executed check | Actual result |
|---|---|
| Starting full baseline | PASS — 997 tests / 44 suites, zero failures/errors/skips |
| Initial architecture-only verification | PASS — 152 tests / 6 suites, zero failures/errors/skips; before the additional inbound-transaction rule/case |
| Focused clean regression | PASS — 356 tests / 13 suites, zero failures/errors/skips; includes all 75 Phase 3 cases, 82 audit cases and 199 architecture/contract cases |
| Final full verification | PASS — 1,027 tests / 45 suites, zero failures/errors/skips; executable JAR packaged |

An executable-JAR check attempted during the final verification initially reported a missing artifact: the focused `clean test` had removed the JAR and packaging had not yet run. This was an artifact-ordering failure, not a Java/test failure. Re-running `python3 scripts/check_backend_jar.py` after the final Maven packaging passed: the JAR is executable and contains no compiled tests or test-only libraries. No JUnit run failed or skipped integrations during this change.

Repository-root documentation/OpenAPI checks pass in the existing isolated environment matching all 20 checked-in dependency pins: `/tmp/finfast-capability-docs-venv/bin/python scripts/check_docs.py` (24 Markdown files, 368 local links, 88 OpenAPI references; no duplicate YAML keys), and `/tmp/finfast-capability-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml`. `docker compose -f compose.docs.yaml config --quiet`, `git diff --check` and direct whitespace/final-newline checks on all 22 untracked Java files also pass.

Independent review compares the final files with a snapshot of the starting working tree. All six untracked production Phase 3 files and existing application/PostgreSQL tests remain byte-identical. The exact graph and module/public-API/restricted-capability policy remain unchanged; historical governance after the convention pointer is also unchanged. No public API, schema, migration, OpenAPI, build dependency or financial behavior is changed. All additions are documentation or test enforcement, with no new production providers or implementation phase. No file was deleted or moved. The branch/HEAD remain unchanged, the index is empty and all changes remain unstaged/uncommitted; no Git mutation was performed.

Static rules select all nine modules but cannot infer every feature entry/helper distinction, detect every reflective/service-locator shortcut, inspect SQL table ownership or prove runtime financial durability. Explicit entry inventory/review and real PostgreSQL/proxy/concurrency tests remain required. Current real Ledger/audit transaction tests retain their test-only Identity/Accounts/Idempotency boundary, zero real final-outcome rows and the documented transaction-manager/canonicalization interoperability limitations. Production composition and uncertain-commit recovery remain full-integration blockers; these structural changes do not close financial ACs or mark BK-013 Done.

## BK-013 Phase 4 idempotency concurrency and recovery verification

**Bounded Administration verification implemented; real-provider financial integration BLOCKED. BK-013 is not Done and Phase 5 is not started.** Starting branch `feat/BK-013-admin-allocation`, HEAD `104c8dc7c43b6cad8df87e7a865e3a8b4830aecf`, clean working tree/index and no Git operation in progress. Local source inspection finds only `api` contracts in Identity, Accounts and Idempotency. No production provider is inferred from those interfaces. Real Ledger posting/JDBC and Administration audit persistence participate; all missing collaborators remain test-only. Production source, frozen public Java/OpenAPI, migrations, architecture rules and prior tests remain unchanged.

### Canonical intent evidence

[AllocationCanonicalInteroperabilityTests](../../backend/src/test/java/com/finfast/administration/internal/application/service/AllocationCanonicalInteroperabilityTests.java) adds six independent local-vector invocations against the frozen typed v1 specification in [16 stable encodings](../03-design/16-domain-model-and-data-design.md#stable-encodings-and-boundary-validation): version 1; ordered initiatorUserId/operation/source/destinationAccountNumber/currency/amount/note/reason; lowercase trusted UUID; DEMO_ALLOCATION; fixed TREASURY; VND; maximum integral decimal text; absent note and exact present reason. Literal expectations cover control escaping, quotes/backslashes/slash, U+2028/U+2029, combining/precomposed characters, supplementary Unicode, whitespace and escaped exact Account Number. An independent hexadecimal UTF-8 tail pins `é`, decomposed `é` and 😀, without generating the expected bytes through the serializer. The exact key remains in scope, outside canonical JSON.

These are Administration-local vectors, not a second authoritative format or cross-owner interoperability approval. **BLOCKED shared verification with Minh:** publish matching versioned canonical JSON and UTF-8 bytes for the fixed actor/recipient/amount/reason cases, including exact escape spelling and tags; feed those same bytes into the real provider's version/fingerprint/full-intent comparison and replay/conflict tests. Scope/advisory encoding, fingerprints, collision handling and durable storage remain Idempotency-owned and untouched. Typed Long/VndAmount input does not prove raw malformed money-token handling, duplicate JSON properties, absent required HTTP fields or old-version recovery readers.

### Overlapping consumer schedules

[AdminAllocationConcurrencyIntegrationTests](../../backend/src/test/java/com/finfast/AdminAllocationConcurrencyIntegrationTests.java) contains nine invocations. Bounded latches hold the first call after real audit INSERT while another call reaches inspect. For identical intent, explicit PROCESSING and UNKNOWN decisions produce no additional posting/audit or record() invocation; after the first commit, repeated invocations replay the exact Completion snapshot. A reason differing by one trailing space yields UNKNOWN during the overlap and Conflict after finalization, leaving the original untouched. Two distinct exact keys (case-only or whitespace-only differences) and two trusted Admin contexts using the same key reach posting through a two-party barrier. Both scripted scopes commit the fixture’s Ledger/audit effects against the same recipient. An unrelated-operation sentinel verifies the service always chooses DEMO_ALLOCATION without overwriting INTERNAL_TRANSFER scope; no transfer workflow/canonical vector is implemented.

A failing first attempt is held after audit, a duplicate receives scripted PROCESSING, and the first transaction then fails at the simulated outcome boundary. After observed rollback, the same exact intent executes and replays without duplicate committed facts. Cancellation interrupts a held post-audit attempt; a separate unwound latch waits for proxy cleanup before asserting rollback and retrying. No arbitrary sleeps are used; all synchronization/future waits are bounded.

The [Phase 4 harness](../../backend/src/test/java/com/finfast/AdminAllocationPhase4TestSupport.java) supplies **scripted decisions**, delegates final observation to the existing afterCommit memory map, and adds no mutex, advisory lock, hashing or outcome SQL. It verifies actual CGLIB invocation, the same DataSource/manager and a fresh `finfast_it_` database distinct from the container's default database. The inherited SQL Accounts collaborator and synthetic trusted-context fixture are not production providers. These schedules prove Administration's reaction to supplied decisions, not exclusive execution, durable replay or concurrent financial correctness of the missing Idempotency/Accounts implementations.

### PostgreSQL commit and recovery investigation

[AdminAllocationCommitRecoveryIntegrationTests](../../backend/src/test/java/com/finfast/AdminAllocationCommitRecoveryIntegrationTests.java) contains ten invocations, using the [test-only JDBC observer](../../backend/src/test/java/com/finfast/AllocationJdbcCommitProbe.java) around the same DataSource for all participants. Fresh independent database connections observe committed Ledger/audit facts and complete test-fixture SQL snapshots. All ordinary committed allocation facts have exactly two opposite entries and exactly one linked required audit. Tests intentionally assert **zero real request_outcome rows**: no durable outcome provider participates. Account SQL state is the test collaborator's behavior, not production Account eligibility/conservation evidence.

| Injected boundary | JDBC observation and database result | Meaning |
|---|---|---|
| Before posting / after real Ledger posting but before audit (two cases) | No COMMIT; successful rollback; original committed snapshot unchanged; no audit/outcome; same-key fresh invocation can execute | Known rollback of available real database effects |
| beforeCommit TransactionSystemException, rollbackOnCommitFailure=false | No explicit JDBC COMMIT and no rollback; cleanup calls setAutoCommit(true); Ledger/audit writes become committed; Spring completion UNKNOWN | Reproduces the unsafe default composition; caller failure is not proof of rollback |
| Same callback, rollbackOnCommitFailure=true | No COMMIT; one successful rollback; unchanged database snapshot; completion ROLLED_BACK | Narrow known pre-commit safeguard, not a universal guarantee |
| beforeCommit error followed by synthetic rollback failure | Rollback attempted but delegate not called; rollback DataAccessException replaces the original TransactionSystemException; completion UNKNOWN; autocommit restoration commits on this deliberately still-usable connection | Diagnostic failure composition; a genuinely broken connection can instead remain uncertain |
| Synthetic SQLException before delegate COMMIT | COMMIT method entered but raw database COMMIT not called; subsequent rollback succeeds; no effects; same exact scope/intent can retry | Known pre-send failure only because the test controls the injection point |
| Real deferred PostgreSQL audit trigger failure at COMMIT | Audit INSERT succeeded tentatively; raw COMMIT fails with P0001; Spring attempts rollback; independent snapshot unchanged | Real server-side commit-time failure leaves no committed Ledger/audit effects |
| Synthetic acknowledgement failure after delegate COMMIT returned | One successful raw COMMIT, then technical exception; an additional successful rollback does not undo committed facts; Spring reports ROLLED_BACK to completion callbacks despite those committed facts | Caller must treat commit as uncertain; neither rollback callback status nor an empty fake outcome map proves failure |
| afterCommit callback failure | Raw COMMIT succeeded; no rollback; completion COMMITTED; caller receives the callback exception; later fake outcome callback never runs | Already committed database effects; afterCommit memory publication is not atomic durable outcome storage |
| Caller response loss after successful proxy completion | Real facts committed, fake outcome observed once; expired-context retry is denied before inspect; current-authorized original-key retry replays without additional writes | Simulated caller loss and consumer replay, not HTTP/network/crash recovery or durable outcome proof |

Spring 7.0.9's installed source archives were inspected: AbstractPlatformTransactionManager.processCommit distinguishes TransactionException from other runtime failures, doRollbackOnCommitException may replace the original failure and emits ROLLED_BACK after a rollback call returns; DataSourceTransactionManager cleanup restores autocommit. The tests observe actual calls/committed facts rather than deriving database finality from Spring status. No new production manager configuration is installed. A still-usable synthetic connection, deferred trigger rejection, real connection loss and a possibly successful COMMIT have different evidence; no rollback after successful commit is claimed.

In the acknowledgement-loss test, the fixture knows commit occurred through the delegate and independent observation; the caller receives only a technical exception. A scripted UNKNOWN decision blocks another posting, and a separately scripted original Completion replay verifies no new writes. **Real authorized committed lookup/coordination/recovery is BLOCKED.** The empty memory map is explicitly not permission to execute. Preserve original initiator/operation/exact key/canonical intent; authorize every retry; never invent a replacement key or durable technical rejection.

### Executed verification and remaining gates

Temurin Java 21.0.13+9, Maven Wrapper 3.9.11, Spring Boot 4.1.1/Spring 7.0.9 and PostgreSQL 16.15 Testcontainers are used. Every new PostgreSQL invocation creates a separate disposable database with Flyway V1/V2 validation, existing lock/statement/socket deadlines and no developer datasource/Compose volume. Commands from `backend/`, with `JAVA_HOME=/tmp/finfast-architecture-jdk21` and its `bin` prepended to PATH:

```sh
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocationCanonicalInteroperabilityTests,AdminAllocationConcurrencyIntegrationTests,AdminAllocationCommitRecoveryIntegrationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocationCanonicalInteroperabilityTests,AdminAllocationConcurrencyIntegrationTests,AdminAllocationCommitRecoveryIntegrationTests,AllocateDemoFundsServiceTests,AllocationCommandAndCanonicalIntentTests,AdminAllocationOrchestrationIntegrationTests,AdminAuditEventTests,AdminAuditPersistenceIntegrationTests,OrdinaryPostingIntegrationTests,OrdinaryPostingConcurrencyTests,LedgerPersistenceIntegrationTests,GenesisPostingIntegrationTests,ApplicationBoundaryVerificationTests,HexagonalConventionVerificationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ReadCapabilityVerificationTests,ModuleContractValueTests,ReadCapabilityContractTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AllocationCanonicalInteroperabilityTests,AdminAllocationConcurrencyIntegrationTests,AdminAllocationCommitRecoveryIntegrationTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Execution | Actual result |
|---|---|
| Current full baseline | PASS — 1,027 tests / 45 suites, zero failures/errors/skips |
| Initial new Phase 4 tests | PASS — 25 tests / 3 suites: six canonical and nineteen PostgreSQL cases, zero failures/errors/skips |
| Combined Admin/Ledger/architecture/contracts | PASS — 514 tests / 20 suites, zero failures/errors/skips |
| Reviewed Phase 4 rerun | PASS — 25 tests / 3 suites, zero failures/errors/skips; stronger DataSource/database isolation and misleading completion-status assertions |
| Final full verification | PASS — 1,052 tests / 48 suites, zero failures/errors/skips; executable JAR packaged |

Repository-root checks pass: `python3 scripts/check_backend_jar.py` (executable JAR, no compiled tests or test-only libraries); `/tmp/finfast-capability-docs-venv/bin/python scripts/check_docs.py` (24 Markdown files, 380 local links, 88 OpenAPI references; no duplicate YAML keys); `/tmp/finfast-capability-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml`; `docker compose -f compose.docs.yaml config --quiet`; `git diff --check`; and direct whitespace/final-newline checks on all five untracked Java files. All 20 documentation dependency pins match the isolated environment.

The independent second pass reviewed all five new test files and both modified documents, including decision-before-posting ordering, exact scope/canonical capture, trusted-context changes, actual proxy/DataSource identity, bounded synchronization, interruption cleanup and physical commit evidence. It strengthened database isolation and the acknowledgement-loss completion-status assertion before the reviewed rerun and full verify. A manifest comparison confirms every pre-existing production source/test, architecture instruction/rule, public API, migration, OpenAPI and build file is unchanged; only the two BK-013 evidence documents differ. No new provider, lock implementation, endpoint or financial implementation is introduced. Branch/HEAD are unchanged, index empty, all work unstaged/uncommitted; no Git mutation occurred.

The first `make help` attempt ran from backend/ and failed because its Makefile is at the repository root; rerunning at the root passed. Expected fault injection includes logged commit/rollback errors; no JUnit failure is hidden by those passing diagnostic assertions. Prior Phase 3 failed runs and the earlier architecture-check artifact-ordering failure remain recorded in their own execution sections.

| Remaining owner integration | Status / required evidence before acceptance |
|---|---|
| Minh: Idempotency | **BLOCKED** — real provider absent. Full-scope exclusivity, fresh authoritative reads after lock wait, insert-once durable completion/rejection, collisions, rollback, concurrent winner failure, original-key resolution after commit uncertainty and process restart |
| Minh/Tiến: canonical interoperability | **BLOCKED** — shared literal v1 bytes/provider vectors absent. Match exact encoding/tags and real provider version/fingerprint/full-intent/replay behavior; no Administration hash/lock implementation |
| Hải: Accounts | **BLOCKED** — production provider absent. Real sorted locks/current eligibility, Treasury competition, balances/conservation and same-database atomic participation; synthetic SQL fixtures do not close these obligations |
| Hưng: Identity | **BLOCKED** — current production identity/session provider absent. Live authorization/session expiry/role behavior before every inspect/recovery/replay |
| Tiến and provider owners: transaction composition | **BLOCKED** — production wiring absent. Same DataSource/manager and actual outer proxies, safe pre-commit/rollback-failure handling, during-commit classification and authoritative uncertain-commit recovery; rollbackOnCommitFailure alone is insufficient |
| Phase 5 and acceptance | **NOT STARTED** — depends on the above integrations and separately authorized HTTP/CSRF/raw-input/outcome mapping, restart/network-loss/E2E and full financial AC evidence |

## BK-016/BK-018 corrective read verification — 2026-10-25

**Independent Administration read contribution; real authentication/provider
integration and end-to-end acceptance remain pending.** Git preflight verified
clean `feat/BK-016-admin-read-apis`, local/remote/PR #35 HEAD
`5dfffc492a2642033ac8c2f72a77c497b971b355`, target `develop` at
`bb7b5ce911446b86c0d51583d22ab15f8d25cc05`, and `0 / 6` develop-only/branch-only
commits. No pull or develop merge was needed. GitHub CLI authentication was
unavailable; the connected GitHub metadata and remote Git refs agreed.

Verification uses Temurin Java 21.0.12.1+1 extracted into
`/tmp/finfast-pr35-java21` from the cached, repository-pinned JDK image; host Java
26 is not used. From `backend/`, with that JDK in `JAVA_HOME` and `PATH`:

```sh
./mvnw --batch-mode --no-transfer-progress '-Dtest=AdminInspectionControllerTest,AdminInspectionServiceTest,*ArchitectureTests,*VerificationTests' test
./mvnw --batch-mode --no-transfer-progress '-Dtest=AdminInspection*Test*,FinfastApplicationTests,GlobalExceptionHandlerTests,*ArchitectureTests,*VerificationTests' clean test
./mvnw --batch-mode --no-transfer-progress '-Dtest=AdminInspection*Test*,FinfastApplicationTests,GlobalExceptionHandlerTests,*ArchitectureTests,*VerificationTests' test
./mvnw --batch-mode --no-transfer-progress clean verify
```

The pre-edit baseline passed **170 tests**, zero failures/errors/skips. The final
focused run passed **260 tests / 11 suites**, zero failures/errors/skips: 28
application, 20 HTTP/Jackson, eight activation/dependency, four full-application
context, 42 shared-error and 158 architecture/capability/layer/negative-fixture
checks. Direct inbound invocation and the real controller/config/application chain
both authorize before private reads. Tests cover missing/wrong-role identity,
null actors, technical failures, Customer consistency, all three financial types,
signed MAX_VALUE decimal strings, exact timestamp/field sets and actual omission
of optional audit properties via `JsonNode.has`. Production scanning registers
neither use case nor controller/route by default; activation without any one of
the five required dependencies fails context startup.

Final full `clean verify` passed **1,310 tests / 61 suites**, zero
failures/errors/skips, in 3 minutes 29 seconds; Maven produced the executable JAR.
This includes existing PostgreSQL 16.15 Testcontainers allocation
orchestration/concurrency/commit-recovery, audit persistence/constraints, status
change, Ledger AM-04/history/posting/reconciliation and schema regressions in
isolated databases. Their passing results preserve their existing provider and
transaction evidence scope; they do not establish the new Admin HTTP read
pipeline with real Identity/Customer/audit readers.

Repository-root checks passed: `python3 scripts/check_backend_jar.py`, pinned
`openapi_spec_validator` and `scripts/check_docs.py`,
`docker compose -f compose.docs.yaml config --quiet`, `git diff --check`, and
whitespace/final-newline checks for both new Java files. JAR inspection confirms
the new configuration is packaged and removed Admin handler/DTO/exception classes
are absent. Public module APIs, Accounts/Ledger/Identity/Customer implementations,
V1/V2 migrations, frozen OpenAPI and build configuration are unchanged.

Initial attempts exposed a stale incremental nested-class artifact
(`PostingResult$Rejected.class`, resolved by clean compilation), an incorrectly
unstubbed Mockito null-result fixture and ambiguous MVC/Actuator mapping injection
in the new context test. The fixtures now explicitly return null and qualify the
MVC mapping. No existing financial implementation, architecture rule or assertion
was weakened to resolve these failures.

All new read-provider/security collaborators are test-only. They establish no
real session/logout/expiry/RBAC integration or PostgreSQL Admin read pipeline.
Production Identity/Customer/AM-03/audit readers and complete Ledger composition
remain pending; existing audit persistence stays insert-only. The new common
UNAUTHORIZED category supports a safe 401 convention without asserting a typed
Identity error contract. API-ERR-01 still governs exceptional statuses absent
from the frozen operation response lists. No OpenAPI/public module API/migration
change or BK-016/BK-018 acceptance completion is claimed.

## BK-017 Freeze/unfreeze verification — 2026-10-24

**PARTIAL IMPLEMENTATION EVIDENCE — Accounts status provider and Administration orchestration.** The focused PostgreSQL/Testcontainers suite uses isolated PostgreSQL 16 databases, the real JDBC transaction manager and actual Spring proxies. It verifies ACTIVE→FROZEN and FROZEN→ACTIVE, complete status audit action/outcome/before/after fields, same-state `UNCHANGED` without an audit row, Treasury exclusion, blank-reason rejection, audit-trigger rollback, and rejection of missing/read-only/unproxied caller transactions.

Executed from `backend/`:

```sh
./mvnw --batch-mode --no-transfer-progress -Dtest=AccountStatusIntegrationTests,AdminAuditEventTests,ApplicationBoundaryVerificationTests test
./mvnw --batch-mode --no-transfer-progress clean test-compile
```

The evidence covers the approved internal contracts and database transaction semantics only. There is no production `CurrentIdentity` implementation or complete runtime Bean composition for Accounts/Administration on this baseline, so no HTTP authentication, endpoint, deployment-secret or end-to-end acceptance is claimed. No OpenAPI, migration, BK-015 Receipts or public Java contract was changed. BK-017 remains incomplete until the responsible Identity/Accounts/runtime owners provide and integrate those real dependencies.

## BK-018 Ledger AM-04 verification — 2026-10-24

**Ledger provider implemented; real Accounts and Administration integration pending.** Started on clean `feat/BK-018-ledger-financial-queries` at `d0e75a8edb3191397398cb6d8f955b9a60df1e42`. Verification uses Temurin Java 21.0.12.1+1, the existing Maven Wrapper/Boot dependencies and pinned PostgreSQL 16.15 Testcontainers image, with unchanged Flyway V1/V2 in disposable isolated databases. The host default is Java 26; a cached Temurin JDK was extracted into `/tmp/finfast-am04-java21` for these invocations. No developer database or persistent Compose volume is used.

Commands executed from `backend/`, with `JAVA_HOME=/tmp/finfast-am04-java21` and its `bin` prepended to `PATH`:

```sh
./mvnw --batch-mode --no-transfer-progress -Dtest=LedgerPersistenceIntegrationTests,GenesisDatasetInspectionIntegrationTests,ProductionArchitectureTests,ReadCapabilityVerificationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=AdminFinancialTransactionQueriesIntegrationTests,AdminFinancialTransactionSnapshotIntegrationTests,ApplicationBoundaryVerificationTests,ProductionArchitectureTests test
./mvnw --batch-mode --no-transfer-progress '-Dtest=AdminFinancialTransaction*Tests,FinancialTransactionRowsTests,Ledger*Tests,Genesis*Tests,OrdinaryPosting*Tests,FinancialTransactionAndLedgerTests,DatasetControlConstraintTests,SchemaIdentityAndCounterTests,*ArchitectureTests,*VerificationTests,*Contract*Tests' test
./mvnw --batch-mode --no-transfer-progress clean verify
```

The pre-change focused baseline passed **110 tests / 4 suites**. The initial new focused run executed 101 tests with zero failures, one error and zero skips: its Unicode-blank reference fixture was rejected by the existing PostgreSQL nonblank constraint before the read. The test was corrected to assert that constraint, retaining schema enforcement; isolated reconstruction tests also cover invalid references. The subsequent combined AM-04/Ledger/architecture/contract regression passed **627 tests / 25 suites**, zero failures/errors/skips.

Final clean Maven verification and executable packaging passed **1,126 tests / 51 suites**, zero failures/errors/skips, including 74 new invocations (63 PostgreSQL, 10 isolated reconstruction, one entry-contract check). Existing Ledger Phases 1–5, AM-01, Administration, architecture/capability/layer/negative-fixture, contract and Spring application-context suites all passed. The executable-JAR isolation checker passed with no compiled test classes or test-only libraries packaged. This is local evidence; no hosted CI run, publication or release acceptance is claimed.

Root validation commands also passed, using the repository's unchanged pinned documentation dependencies in the temporary environment:

```sh
python3 scripts/check_backend_jar.py
/tmp/finfast-am04-docs-venv/bin/python scripts/check_docs.py
/tmp/finfast-am04-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml
docker compose -f compose.docs.yaml config --quiet
docker run --rm --network none -v /home/vantien/Projects/cloud/finfast:/repo:ro -w /repo rhysd/actionlint:1.7.7 -color
git diff --check
```

Final scope review confirms unchanged public APIs, financial domain/posting algorithms, Accounts/Administration implementation, Flyway migrations, OpenAPI and historical governance records. New Java files were included in whitespace review separately from the tracked diff. No commit, push, merge or PR was made.

[Financial projection integration tests](../../backend/src/test/java/com/finfast/AdminFinancialTransactionQueriesIntegrationTests.java) add 48 PostgreSQL invocations: empty datasets and separation from rejection/audit evidence; exact GENESIS/transfer/allocation references, timestamps, signed entries and MAX_VALUE; immutable results, repeated reads, position-based ordering and a 106-transaction complete listing; one identity lookup per distinct participant per request; incomplete cardinality/sign/magnitude facts, corrupt GENESIS/linkage/positions, nonfinite time, missing/inconsistent identities and propagating Ledger/Accounts SQL failures. Invalid type/reference/null timestamp/amount/currency/position, zero delta and duplicate participants are verified against unchanged PostgreSQL constraints.

[Snapshot integration tests](../../backend/src/test/java/com/finfast/AdminFinancialTransactionSnapshotIntegrationTests.java) add 15 PostgreSQL invocations using actual service/adapter Spring proxies: server-enforced read-only REPEATABLE READ, pending/rolled-back setup and ordinary posting invisibility, fresh snapshot visibility after commit, held-snapshot stability across concurrent commits, and identity SELECTs remaining in the same snapshot when a new Account and posting commit between fact retrieval and identity resolution. Bounded futures/latches coordinate schedules without sleeps. Write callers, wrong isolation, unproxied calls without a transaction, unbound/mismatched DataSources, autocommit and conflicting physical JDBC state are rejected. Attempting a write inside the read-only transaction yields PostgreSQL SQLSTATE 25006.

[Reconstruction tests](../../backend/src/test/java/com/finfast/ledger/internal/adapter/outbound/persistence/FinancialTransactionRowsTests.java) add 10 isolated invalid-header cases, with no PostgreSQL claim for mocked ResultSets. One explicit [entry-contract check](../../backend/src/test/java/com/finfast/architecture/ApplicationBoundaryVerificationTests.java) verifies the approved public API relationship and concrete REQUIRED/read-only/REPEATABLE READ/checked-failure rollback declaration. Existing graph, capability, layer and negative-fixture rules are unchanged.

The [test-only Accounts collaborator](../../backend/src/test/java/com/finfast/AdminFinancialQueriesTestSupport.java) performs identity SQL through the same bound DataSource and verifies Spring metadata, physical JDBC state, server transaction mode and snapshot IDs. It is not Hải's AM-02 implementation and establishes no real-provider correctness, Accounts balance/eligibility, ADMIN authorization, protected setup or durable outcome/audit workflow. Production Ledger configuration remains absent until real AM-02 participation is available and verified. Administration/BK-018 integration, HTTP/security/browser/end-to-end acceptance and BK-010 Phase 6 remain pending. Full-list memory/identity-call costs are documented in [data design](../03-design/16-domain-model-and-data-design.md#committed-admin-financial-inspection--td-016-am-04); no load measurement or complete BK-018 acceptance is claimed.

## BK-014 Ledger history verification — 2026-10-24

**IMPLEMENTED — Ledger-local history contribution; production composition/full BK-014 acceptance pending.** Started on clean `feat/BK-014-ledger-history-queries` at `1c8552ab3a0b91e4434215a92d8ec8b05697e756`, including merged AM-04. Executions use Temurin Java 21.0.12.1+1, existing Maven Wrapper/Boot dependencies and pinned PostgreSQL 16.15 Testcontainers, with unchanged V1/V2 in disposable isolated databases. Java 21 is selected through `JAVA_HOME=/tmp/finfast-am04-java21` and its `bin` prepended to `PATH`; no developer database or persistent Compose volume is used.

Commands executed from `backend/`:

```sh
./mvnw --batch-mode --no-transfer-progress '-Dtest=AdminFinancialTransaction*Tests,GenesisDatasetInspectionIntegrationTests,ProductionArchitectureTests,ApplicationBoundaryVerificationTests,ReadCapabilityContractTests' test
./mvnw --batch-mode --no-transfer-progress -Dtest=HistoryCursorCodecTests,ProductionArchitectureTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=LedgerHistoryIntegrationTests test
./mvnw --batch-mode --no-transfer-progress '-Dtest=LedgerHistory*Tests,HistoryCursorCodecTests,ApplicationBoundaryVerificationTests,ProductionArchitectureTests' test
./mvnw --batch-mode --no-transfer-progress '-Dtest=Ledger*Tests,HistoryCursorCodecTests,AdminFinancialTransaction*Tests,Genesis*Tests,OrdinaryPosting*Tests,FinancialTransactionAndLedgerTests,DatasetControlConstraintTests,SchemaIdentityAndCounterTests,*ArchitectureTests,*VerificationTests,*Contract*Tests' test
./mvnw --batch-mode --no-transfer-progress clean verify
```

Pre-change focused baseline passed **164 tests / 6 suites**. Initial cryptographic/architecture verification passed **64 tests / 2 suites**; initial functional PostgreSQL suite passed **42 tests / 1 suite**. The first combined focused command stopped during test compilation because the pinned AssertJ ClassAssert does not supply `isNotAssignableFrom`; no tests executed in that invocation. Replacing it with an assertion on Java's `Class.isAssignableFrom` preserved the intended check. The identical combined command then passed **150 tests / 6 suites**, zero failures/errors/skips, including all 112 new invocations: 41 cursor unit tests, 70 PostgreSQL-fixture invocations (66 exercise database/proxy behavior and four assert existing PageRequest limits), and one explicit entry-contract check.

Combined Ledger/AM-04/History/architecture/capability/contract regression passed **729 tests / 28 suites**, zero failures/errors/skips. Existing positive/negative structural rules and Ledger Phases 1–5/AM-04 behavior remain intact.

Final full `clean verify` passed **1,238 tests / 55 suites**, zero failures/errors/skips, including application-context checks, all existing PostgreSQL/schema/domain/AM-04/reconciliation/posting tests, cursor vectors and architecture/capability/contract suites. Maven produced the executable JAR; packaging isolation validation passed. Existing production files, public APIs, Accounts/History/Administration implementation, posting algorithms, V1/V2/OpenAPI and historical governance remain byte-identical to the starting commit. All eleven new files (six production, five test) and five modified files were reviewed for scope/whitespace; nothing was staged, committed, pushed, merged or published.

Root checks executed successfully with the existing pinned documentation environment/tools:

```sh
python3 scripts/check_backend_jar.py
/tmp/finfast-am04-docs-venv/bin/python scripts/check_docs.py
/tmp/finfast-am04-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml
docker compose -f compose.docs.yaml config --quiet
docker run --rm --network none -v /home/vantien/Projects/cloud/finfast:/repo:ro -w /repo rhysd/actionlint:1.7.7 -color
git diff --check
```

Documentation validation passes 27 Markdown files, 473 local links and 88 OpenAPI references with no duplicate YAML keys; the frozen OpenAPI validates. No hosted CI run, real Accounts integration, secret deployment, process restart, History HTTP/security/browser acceptance or performance benchmark was executed. Passing local tests is scoped evidence and does not complete BK-014/BK-015 or release acceptance.

- [Functional/pagination/integrity tests](../../backend/src/test/java/com/finfast/LedgerHistoryIntegrationTests.java): 42 invocations. Empty/initialized histories exclude GENESIS, rejections/audit and unrelated movements; exact signed direction/reference/timestamp/MAX_VALUE, immutable results and safe Treasury labels. Traversals across 205 eligible movements plus unrelated position gaps, tied timestamps and limits 1/2/20/100 verify exact ordered membership without duplicates, omissions or unnecessary final cursors. Tests verify lookahead corruption fails the page, missing/mismatched Account IDs, incompatible kinds, duplicate Number/Customer ownership, inconsistent posting bounds, maximum-position SQL bounds and propagating SQL failures. These are read fixtures, not production Account balance/eligibility evidence.
- [Cursor integration tests](../../backend/src/test/java/com/finfast/LedgerHistoryCursorIntegrationTests.java): 14 invocations. Malformed/tampered/wrong-key/unknown-version/invalid-bound tokens fail before Account or fact lookups. Account/limit mismatch also fails before lookups; authenticated owner mismatch fails before financial queries. Same-key service/proxy recreation continues the original cursor. The test context has no partial `LedgerQueries` provider and no receipt implementation. Recreation verifies key continuity, not a process/deployment restart.
- [Snapshot/concurrency tests](../../backend/src/test/java/com/finfast/LedgerHistorySnapshotIntegrationTests.java): 14 invocations. New transfers/allocations cannot enter an old traversal; a new traversal sees them. A commit between first bound and page retrieval leaves bound/rows/identities on the original snapshot. Partial/complete pending postings remain invisible and rollback remains absent. Real observed `pg_blocking_pids` counter contention prevents a disjoint later writer overtaking a held commit; commit/rollback schedules preserve old traversal membership and expose winners only to fresh traversals. Actual proxies and PostgreSQL server/JDBC/resource state verify read-only REPEATABLE READ and reject write/wrong-isolation/unproxied-without-transaction/unbound/mismatched/autocommit/conflicting physical resources. An attempted write fails with SQLSTATE 25006. All concurrency coordination has bounded waits and no sleeps.
- [Cursor unit vectors](../../backend/src/test/java/com/finfast/ledger/internal/application/service/HistoryCursorCodecTests.java): 41 invocations. Fixed canonical golden vector independently calculated with Python struct/HMAC, round-trip/MAX positions, defensive key copying, same/different-key behavior, modified fields/tags, invalid Base64url/padding/aliases, truncated/oversized input, authenticated unsupported versions and invalid position/page bounds. Production uses standard JCA HMAC-SHA256 and constant-time MAC comparison, not custom cryptographic primitives. Test-only deterministic key bytes establish no production secret provisioning.
- [Entry-contract verification](../../backend/src/test/java/com/finfast/architecture/ApplicationBoundaryVerificationTests.java): confirms the internal History inbound interface, existing public DTOs and concrete REQUIRED/read-only/REPEATABLE READ/checked-failure rollback declaration. Public `LedgerQueries` signatures, History-only capability permission, graph and existing positive/negative architecture rules are unchanged.

The [test support](../../backend/src/test/java/com/finfast/LedgerHistoryTestSupport.java) reuses existing isolated databases, Spring transaction composition, Ledger fact storage/position allocator and bounded test deadlines. Its Accounts identity SQL explicitly uses the same bound DataSource/snapshot and is strictly test-scoped. This cannot establish Hải's real AM-02 provider correctness or resource participation, current History authentication/authorization, production HMAC key provisioning, deployment restart, public provider/receipt composition or end-to-end acceptance. See [implementation/composition boundaries](../03-design/16-domain-model-and-data-design.md#bk-014-ledger-history-implementation). BK-014 is not Done, BK-015 is not implemented and BK-010 Phase 6 remains unchanged.

## Test levels and environments

| Level | Purpose | Examples / evidence |
|---|---|---|
| Domain unit tests | Pure validation and invariants | Amount parsing, overflow arithmetic, payload canonicalization, source ownership policy |
| Repository/DB integration | Genuine PostgreSQL constraints, locks and ACID semantics | Zero-sum postings, multi-threaded spend, freeze races, unique idempotency keys, audit failure |
| API contract tests | Valid/invalid request/response, privacy, cookie/CSRF/session behavior | OpenAPI conformance and unexpected-field/role checks |
| Fault/restart tests | Verify atomic commit, outcome uncertainty, GENESIS once | Inject SQL failure, kill process after commit, restart preserved dataset |
| Browser E2E tests | Complete CUSTOMER/ADMIN workflows and responsive UI | WEB-01..08; AC-038 |
| Performance test | Reproducible target workload and correctness under load | PRD §9 / EV-003 report plus reconciliation |
| Manual UAT | Owner evaluates required outcomes with realistic synthetic flows | Signoff matrix, video/screenshots/logs linked to AC IDs |

**Important:** a unit test of a mocked repository cannot prove a PostgreSQL concurrency or durability requirement. Run the stateful scenarios on an actual DB equivalent to the deployed engine.

## Acceptance execution groups (all 38 IDs, no replacement definitions)

| Test group | Canonical AC IDs | Method / verification focus |
|---|---|---|
| T-IDENTITY | AC-001, AC-027, AC-028, AC-035 | Browser/API+DB; atomic registration, email race, protected sessions/logout, immutable numbers |
| T-AUTHZ | AC-002, AC-024, AC-030 | API negative authorization; cross-owner, wrong-role, treasury, protected data minimization |
| T-TRANSFER | AC-003, AC-004, AC-016, AC-036 | DB/API; ordinary two-entry postings, safe amounts, ineligible recipient/currency |
| T-IDEMPOTENCY | AC-005, AC-006, AC-011, AC-013, AC-014, AC-015, AC-033, AC-034 | Concurrent API, persisted outcomes, replay, conflict, lost response, scope/lifetime |
| T-CONCURRENCY | AC-007, AC-017, AC-018 | Coordinated DB multi-connection tests; spend race, opposite-direction movement, freeze order |
| T-ADMIN | AC-008, AC-009, AC-019, AC-026, AC-029, AC-032 | Atomic allocation/audit, freeze/unfreeze, reason & insufficient treasury |
| T-GENESIS | AC-020, AC-021, AC-022, AC-023 | Controlled initialization/interrupt/restart + per-account/supply reconciliation |
| T-READS | AC-012, AC-025, AC-031 | Read history/receipt, stable cursor, immutable reference, append-only evidence |
| T-UI-SCOPE | AC-037, AC-038 | Scope exclusions, synthetic data, responsive customer/admin complete flows |
| T-FAULT | AC-010 | Inject persistence failure after partial write *before DB commit*, verify rollback; also referenced by T-ADMIN/T-IDEMPOTENCY |

The list above covers **AC-001 through AC-038 exactly once as primary grouped owners**. A scenario may additionally run in multiple levels; the original canonical ID stays unchanged.

## CONS crosswalk — risk lens, NOT additional acceptance IDs

| CONS IDs | Canonical AC targets | Specific risk |
|---|---|---|
| CONS-01..04 | AC-007, AC-010, AC-011, AC-006 | Double-spend, ledger failure, lost response, payload conflict |
| CONS-05..08 | AC-013, AC-018, AC-021, AC-024 | Duplicate funding, freeze race, restart, private lookup |
| CONS-09..12 | AC-014, AC-015, AC-016, AC-017 | Concurrent replay, rejected-outcome replay, numeric safety, opposite transfers |
| CONS-13..16 | AC-019, AC-020, AC-022, AC-023 | Atomic admin audit, GENESIS, reconciliation, supply conservation |
| CONS-17..19 | AC-025, AC-033, AC-034 | Stable pagination, technical retry, scope/lifetime |

## Reproducible financial test setup and assertions

**Fixture:** fresh GENESIS; protected Admin identity; synthetic Customers Alice/Bob/Charlie; controlled audited allocations establish explicit initial balances. Never write test balances directly except via separate isolated DB fixtures for low-level numeric unit tests.

**Pre/post-invariant probe on a consistent DB snapshot:**

1. Account balances are nonnegative and fit supported integer bounds.
2. Every Account balance equals sum of committed signed entries (Customer zero before allocations; Treasury includes GENESIS exactly once).
3. Exactly one Treasury and exactly one positive, one-entry GENESIS per dataset.
4. Every ordinary posted movement has exactly two signed entries, opposite and equal magnitude; no receipt/reference mutation.
5. `SUM(treasury + all Customer balances) == GENESIS initial supply`.
6. Funding success audit links to same reference and reason; freeze status effective change links to complete success audit.
7. Same scoped key has at most one final result and at most one ordinary Financial Transaction; business-rejected result has no postings.

## Fault-injection plan

| Injection point | Expected outcome | AC/CONS |
|---|---|---|
| After first Ledger Entry SQL execution but before commit | Entire transaction rolled back; no partial debit/credit/outcome | AC-010, CONS-02 |
| During `request_outcome` durable insert | No posted movement or false COMPLETED; safe later same-key retry | AC-010/033, CONS-18 |
| During required funding success Admin Audit insert | No funding postings/success; retry same key safely | AC-019, CONS-13 |
| During required freeze success audit insert | Account retains old state; no unaudited change | AC-019, CONS-13 |
| After DB commit but before HTTP response/reference delivery | Original committed reference recovered by original key; one movement | AC-011, CONS-03 |
| During GENESIS prior to commit | No usable partial supply; retried setup creates one full GENESIS | AC-020, CONS-14 |
| Restart with existing GENESIS and old rejected/committed keys | No reseed/supply change; same keys retain final outcomes | AC-021/034, CONS-07/19 |

**Control:** failure hooks must exist only in test configuration and require explicit enablement; no arbitrary production API to trigger partial financial writes. Capture DB snapshot, test revision, timestamps, injected point and assertion results.

## Concurrency harness

- Use multiple independent JDBC connections/threads with explicit barriers that release both requests simultaneously, not sleeps alone.
- Distinct keys competing to spend (100 VND; 80 + 80) → at most one commits and one final insufficient-funds rejection (AC-007).
- Two opposite transfers (Alice/Bob start 100 each; A→B 40 and B→A 30) → both complete, balances Alice 90/Bob 110, no indefinite deadlock (AC-017).
- Concurrent same-key requests → one committed effect, all clients resolve to identical final result; changed-payload key conflicts (AC-006/013/014).
- Freeze-first and movement-first schedules enforced with barriers; validate effective ordering and atomic audit (AC-018/019).
- Reconcile after all coordinated workloads on a consistent committed snapshot (AC-022/023).

## Transaction and module verification (ARC-01/02/06)

**Planned execution; no new canonical AC IDs.** Reuse existing T-* groups and AC/CONS mappings above. Review the [15 API allowlist](../03-design/15-system-architecture.md#32-allowed-module-dependencies-arc-06) and [16 transaction design](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02) before writing the application.

- Architecture checks scan the present approved BK-003 public contracts and apply TD-016 module package/API isolation, dependency, cycle, layer and restricted-capability rules to segregated positive/negative fixtures. See the [module guide](../03-design/module-contracts-and-boundaries.md#automated-evidence-and-limits). Provider implementations, runtime authorization/transaction guards and real dependency integration remain deferred. Naming a package `internal` is not sufficient evidence, and structural checks do not pass financial ACs.
- Module integration: Identity registration through Customer/Accounts creates all three records or none; Ledger through Accounts posts both sides or none; Transfers joins Ledger/Idempotency; Administration joins Ledger/Idempotency/audit or Accounts/status/audit. History remains read-only and authorized. Bootstrap tests are separately controlled, never exposed as runtime fault endpoints.
- Spring proxy/commit: invoke the real application bean through the proxy and the HTTP boundary. Inject failure during method body, JPA flush, required audit/outcome persistence and actual commit. Check checked/unchecked exception rollback policy and ensure the client never receives COMPLETED before confirmed commit (AC-010/011/019/033).
- Final rejection: validate insufficient funds before writes, commit rejection normally, add funds using a different authorized intent, then replay the first key and confirm its original rejection. Failure to persist that rejection must remain a technical/non-final result (AC-015/033).
- Independent transaction regression: detect accidental REQUIRES_NEW for any required effect; no committed audit, outcome, entry or balance survives a rolled-back parent. Verify restricted mutation APIs fail when called without an enclosing transaction (AC-010/019/022/023).

### TD-016 candidate read capability execution — 2026-10-23

**Historical pre-decision execution:** these runs verified the five candidate contracts before Tiến's final V1 decision. They remain evidence of that earlier working tree, not the final four-capability test count. Dedicated branch `arch/td-016-cross-module-capability-amendments`, initial clean HEAD/fetched develop `31d2425131766306ccf6a815ca057349bee77bff`. Temurin 21.0.13+9, Boot 4.1.1 and existing PostgreSQL 16.15 Testcontainers/Flyway V1/V2; disposable isolated databases only. No developer DB/Compose volume was used.

From `backend/`, with `JAVA_HOME=/tmp/finfast-architecture-jdk21` and its `bin` prepended to `PATH`, executed:

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress -Dtest=ReadCapabilityContractTests,ReadCapabilityVerificationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=GenesisDatasetInspectionIntegrationTests test
./mvnw --batch-mode --no-transfer-progress -Dtest=ReadCapabilityContractTests,ReadCapabilityVerificationTests,GenesisDatasetInspectionIntegrationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

| Execution | Actual result |
|---|---|
| Pre-source-change full baseline | PASS — 721 tests / 35 suites |
| Initial contract/architecture run | PASS — 157 tests / 6 suites |
| Initial PostgreSQL inspection run | PASS — 22 tests / 1 suite |
| Final focused run after Unicode/bound-autocommit review additions | PASS — 182 tests / 7 suites: 39 new contract, 47 new capability, 23 PostgreSQL and 73 existing architecture/contract cases |
| Final full Maven verify and executable packaging | PASS — 830 tests / 38 suites |

Every run above had zero failures/errors/skips. Contract cases cover immutable copied collections, nulls, exact/Unicode references and bounds, Customer/Treasury ownership shape, positive/MAX money and signed GENESIS/ordinary entry shapes. The 45-case nine-module/five-capability matrix tests interfaces and nested values; two additional cases reject foreign provider implementation and retain History-only LedgerQueries restrictions. Existing positive/negative architecture fixtures, graph/cycle/layer/pure-API protections remain unchanged.

Real Spring/JDBC inspection tests verify fresh absence, exact committed GENESIS/reference/MAX supply, repeated reads, context recreation against the same disposable DB, later ordinary posting, pending setup invisibility, commit/rollback, a held read-only REPEATABLE READ snapshot across concurrent commit, and fresh reads after failed setup. Orphan facts, missing/negative/wrong/extra GENESIS entries, wrong linkage/supply/position/counter/gaps and infinity fail technically without repair. Guards reject unproxied/unbound/mismatched/autocommit resources and read-write/wrong-isolation callers; PostgreSQL itself rejects an attempted write in the read-only snapshot (25006). Bounded futures coordinate independent transaction threads; no mocks prove visibility/rollback.

Root checks passed: `python3 scripts/check_backend_jar.py`, `/tmp/finfast-capability-docs-venv/bin/python scripts/check_docs.py`, `/tmp/finfast-capability-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml` and `git diff --check`. The temporary documentation environment uses unchanged pinned requirements. Untracked Java whitespace and a complete Markdown inventory were checked separately: zero newly added documentation files, with all original historical evidence retained. All existing production source/APIs, OpenAPI, migrations and build/configuration remain byte-identical to HEAD; only additive capability entries change existing test enforcement.

At that checkpoint, Accounts metadata/listing/Treasury and Admin financial providers were absent and signatures/permissions awaited review. Tiến's subsequent final decision approves AM-01..04 and does not select AM-05, as recorded below. Actual owner integration, role/ownership security, protected setup/recovery and process/deployment restart remain unverified. Context recreation is not process restart; Ledger inspection is not full Accounts/balance/ordinary-entry reconciliation. No BK-009/014/015/016/018 or full AC is Done; production wiring remains absent.

### TD-016 final V1 capability execution — 2026-10-23

**After Tiến's explicit final decision:** AM-01..04 are APPROVED — V1; AM-05 is NOT SELECTED — V1. Removed only its session-created interface/fixture and dedicated permission/test references. Retained the real GENESIS reader and its 23 PostgreSQL tests; reader logic and those tests are unchanged. Customer listings now copy and deterministically sort every row by exact Account Number, with input-preservation/order/immutability coverage. No provider stub or additional workflow was introduced.

Executed from `backend/` with the same Java 21 environment:

```sh
./mvnw --batch-mode --no-transfer-progress -Dtest=ReadCapabilityContractTests,ReadCapabilityVerificationTests,GenesisDatasetInspectionIntegrationTests,ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress clean verify
```

| Final-decision execution | Actual result |
|---|---|
| Focused contracts, architecture and PostgreSQL | PASS — 174 tests / 7 suites: 40 contract, 38 capability, 23 PostgreSQL and 73 existing architecture/contract cases |
| Full clean verification and executable packaging | PASS — 822 tests / 38 suites; zero failures, errors or skips |
| Capability permissions | Four capabilities × nine modules; interfaces and nested values checked. Only provider/exact consumer allowed; foreign implementation and History-only LedgerQueries misuse rejected |
| Artifact inspection | Four approved interface classes present; no TreasuryInspection in clean target or executable JAR; no packaged test classes/libraries |

The retained PostgreSQL 16.15 tests use disposable isolated databases and real Spring proxies/connections for committed visibility, rollback, stable read-only REPEATABLE READ snapshots and incompatible-resource rejection. They prove Ledger-owned GENESIS/linkage/counter consistency, not Accounts balances, complete reconciliation, process restart or actual Bootstrap recovery. Accounts AM-02/03 and Ledger AM-04 providers and their owner integrations remain pending; no related backlog item or full acceptance criterion is Done.

Root artifact, documentation, OpenAPI and whitespace checks passed using the same commands/environment recorded above. Original production APIs, financial writers, migrations, OpenAPI, Docker and CI remain unchanged. Existing canonical documentation and AGENTS.md were updated in place; zero new documentation files remain. The earlier 830-test execution is preserved as historical evidence, separate from this final 822-test result.

## PostgreSQL idempotency proof-of-concept (ARC-04)

**REQUIRED proof of the selected TD-003 design before financial integration acceptance; NOT RUN by this freeze.** Run on an isolated real PostgreSQL dataset with the intended major version, READ COMMITTED and multiple independent connections. Use a minimal scope/outcome and paired-balance/posting fixture to exercise [16 §7](../03-design/16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04); do not install a production feature or public fault API. Record full-scope uniqueness, canonicalization/hash version, lock/timeouts, durability settings (`fsync`, `full_page_writes`, `synchronous_commit`, persistent storage) and every transaction's begin/lock/commit events. Synchronize schedules with barriers and observed database waits, not sleeps alone.

| Schedule | Assertions / existing acceptance trace |
|---|---|
| Two identical full-scope requests; A holds scope lock before B begins | B waits or returns non-final; after A commits, B's **new statement** sees the final outcome; exactly one paired posting/reference (AC-005/013/014) |
| Same scope, changed recipient/amount/note/reason | B gets conflict after A finalizes; no second posting; exact canonical intent comparison wins over any hash match (AC-006) |
| Initially rejected, then later funding/unfreeze | Same key replays stored rejection; a new key reevaluates current eligibility (AC-015) |
| A crashes/connection is terminated before commit, including after first balance/entry write | All tentative effects roll back, lock releases, B with original key can safely execute (AC-010/033) |
| A commits, then response delivery is suppressed and client restarts | Original key recovers original reference/outcome; no new posting (AC-011/021/034) |
| Lookup sees no row while A is in flight, or cannot reach the primary | Absence/unavailability is non-final; coordinated retry waits and rereads; it never generates a new key automatically (AC-011/033) |
| Distinct full scopes forced to share one advisory-lock hash | They may wait unnecessarily but never replay/conflict with each other; both can finalize correctly (AC-034) |
| Deliberate full-key uniqueness race in an isolated defense-in-depth variant | Unique violation rolls back all loser effects; reload original result in a fresh transaction (AC-014/022/023) |
| Same text key across different initiators or operation types; unauthorized lookup | Scope isolation and role/ownership enforced; no private result leak (AC-024/034) |
| Reconnect/restart with prior success and rejection records | No key reuse/expiry or reseeding; both results retain original meaning (AC-021/034) |

The transactional unique-intent-row alternative is not selected; it is not a second implementation requirement. Prove no separately committed unfinished PROCESSING record in the selected advisory design. Test canonicalization vectors and fingerprint version upgrades without losing historical replay. Capture one final outcome at most, one movement at most, two entries for success, zero for rejection, matching balances, required funding audit and conserved supply after each schedule (AC-013/019/022/023). TD-003 design is approved; implementation acceptance depends on this executed evidence, not on the conceptual table alone. A failing implementation test normally calls for a code fix; only a genuine design defect warrants amendment.

## Additional PostgreSQL concurrency schedules (ARC-03)

Extend the existing harness with distinct-key Treasury allocations competing for remaining supply; verify all lock Treasury/recipient in Account ID order, no negative balance and correct rejection outcomes. Execute both freeze-first and funding-first schedules, including failure of freeze audit (AC-008/018/019/023/032).

Force a deadlock in a controlled test variant and a lock-timeout on an intentionally held participant/scope row. Confirm full rollback, bounded fresh-transaction same-key retry, eventual original outcome or honest non-final response, and released locks; no database exception becomes a permanent business rejection (AC-007/017/033). Verify Account ordering with IDs whose Java/default comparator ordering could differ from PostgreSQL UUID ordering. After every run reconcile on a consistent committed snapshot, checking missing/duplicate entries and required audits (AC-022/023). H2/in-memory tests cannot substitute for these PostgreSQL checks.

## History and counter-contention experiment (ARC-05)

Follow [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05). Hold an earlier posting before commit, start a second disjoint posting and fetch history. Prove the global counter prevents a later-position commit from overtaking the first, and rollback does not create visible financial facts. Traverse pages during new commits: stable initial bound, no missing/duplicate authorized movements, valid default/max size, immutable references, no rejected requests or other Customer's records (AC-025/024/031).

Run the prescribed benchmark below unchanged before separate diagnostic loads. For disjoint pairs, hot Account, duplicate scope and Treasury fan-in, collect offered/achieved throughput; p50/p95/p99; scope/Account/counter wait and hold time; retries/deadlocks/timeouts; database CPU/I/O/commit latency; and reconciliation. Use the documented 10/25/50/100 requests/s diagnostic sweep only within resource capacity. Record actual PostgreSQL version/settings, resource limits and observation overhead. Distinguish counter contention from Treasury or other account contention. TD-004/014 reconsideration requires measured impact and a replacement passing the same pagination/fault tests; no proven bottleneck or benchmark result is asserted.

## Approved UX/REST verification specifications — execution pending

- Execute WEB-01..08 from UX 14 with their unchanged canonical AC mappings; check S01–S10 states on 360×800, 768×1024 and 1440×900 CSS pixels plus desktop 200% zoom/keyboard. Record browsers and actual results; no certification is implied.
- Assert operation-specific response/status/security/schema sets against the frozen YAML, including generic versus financial 422, authorized stored rejection under resolve 200 and non-final 202/503. The API 17 inventory is a derived reference, not permission to add statuses.
- Before affected adapter conformance, resolve API-ERR-01 through its narrow amendment/compatible-mapping path. Test malformed financial JSON/raw-invalid intent, framework 400 and unexpected 500 handling explicitly; never erase the protocol/business/technical distinctions to make a schema check pass.
- Keep Java signature/ArchUnit checks and real provider/PostgreSQL/security/browser tests separate. Approved UX/API specifications do not mark any AC/CONS, deployment or benchmark PASS.

## PRD performance test (NFR-004; EV-003)

Use the **exact** fixture/schedule/clean-pass acceptance criteria in [09 PRD §9](../02-requirements/09-product-requirements.md). Dedicated 100-Customer, 50-pair dataset; 300 warm-up intents and 3,000 measured intents, every 100ms; report primary attempts separately from recovered outcomes. Zero financial-invariant violations. EV-003 remains AWAITING MEASUREMENT, not a passed gate.

## Evidence record template

```yaml
scenario_id: AC-007
risk_view: CONS-01
test_run_id: TODO
source_commit: TODO
environment: TODO
database_version: TODO
fixture_id: TODO
steps_or_test_command: TODO
expected_result: 'At most one transfer commits; no negative balance'
observed_result: TODO
invariant_report_path: TODO
ci_artifact_url: TODO
status: NOT_RUN  # NOT_RUN | PASS | FAIL | BLOCKED
reviewer: TODO
```

An AC is not considered passed until execution-specific evidence exists. The 38 ACs and 19 CONS lenses must be indexed in the trace matrix and release report.

## BK-003 contract verification and limits

The [canonical inventory and compatibility matrix](../03-design/module-contracts-and-boundaries.md)
records actual source signatures, Tiến's explicit 2026-10-22 approval of the Java
baseline/TD-015/TD-016, merge activation and the later selected mechanisms. Shared baseline
activation occurred with PR #10 merge into `develop` at `8a04ca0`. Evidence categories must remain
separate; approval does not supply a provider or pass a financial AC:

| Level | BK-003 evidence | What it does not establish |
|---|---|---|
| API compilation/value checks | Pure interfaces/records compile on Java 21; tests check key/scope identity, exact note semantics, immutable page copies, page limits, positive completion and final/non-final types | A provider Bean, authentication, persistence or successful posting |
| ArchUnit | Existing graph/isolation/layer/cycle rules retained; capability fixtures reject wrong consumers, foreign provider implementations and ordinary Ledger Treasury initialization, including method references | Runtime authorization, reflective access, actual mandatory transaction propagation or SQL ownership |
| Mocked consumers | Supported using test-scoped mocks/fakes of the real APIs; no mocked banking workflow is presented as BK-003 evidence | PostgreSQL locks, durable results, audit atomicity or AC acceptance |
| PostgreSQL regression | Full Maven `verify` passed with isolated PostgreSQL 16.15/Testcontainers, Flyway V1/V2 and existing schema/rollback/concurrency tests | Unimplemented Ledger/Transfers/Idempotency/GENESIS service behavior |
| Financial integration | NOT IMPLEMENTED / NOT RUN for the new providers; financial plans above remain required | No AC/CONS/EV-003 gate is closed by contract or structural tests |

**Historical BK-003 evidence, not rerun for this documentation freeze:** re-executed locally for the original final approval review on **2026-10-22**, against the
BK-003 worktree on base `a4eb9ff`, Temurin
21.0.12.1+1 and Docker Engine 29.7.2:

```sh
cd backend
./mvnw --batch-mode --no-transfer-progress -Dtest=ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

Focused checks: **73 passed** (12 production architecture, 47 boundary fixtures,
9 capability fixtures and 5 contract-value checks). Full `verify`: **482 passed**
(68 architecture, 5 contract-value, 235 existing PostgreSQL/container and 174 other
unit/context/HTTP checks), zero failures/errors/skips; executable JAR packaged.
No test tag is excluded. The JAR checker confirmed executable packaging and no
test-class/test-library leakage. Public declarations of all **48 API types**
(including nested types) matched the pre-review `javap -public` baseline after
normalizing method order; all 17 Java API source bodies/declarations were preserved.

Final documentation checks used isolated Python 3.12.12 with the pinned
requirements: OpenAPI validation and `scripts/check_docs.py` passed (24 Markdown
files, 251 local links, 88 OpenAPI references; no duplicate YAML keys).
`docker compose -f compose.docs.yaml config --quiet`, actionlint 1.7.7 and
`git diff --check` passed. The historical BK-003 consistency audit confirmed only TD-015/016 status
cells changed, with TD-001..014 retained; no external API, build, SQL migration,
CI or runtime configuration was modified.
The existing CI job names `backend-tests`, `backend-package`, `docs-validation`
were unchanged. At that earlier local-review snapshot no new BK-003 remote evidence was available. Current merged-baseline hosted evidence is recorded in the CI table below.

Before provider integration, implement the selected outer REQUIRED/concrete collaborator guards,
then test actual Spring proxies with full rollback, commit-acknowledgement loss,
rejection persistence, required audit failure, same-key recovery, concurrent
initialization and account/status races. Business-invalid values must reject before
writes; technical exceptions must not become final business rejections. Mocks or
direct unproxied class calls cannot substitute for this evidence.

## Documentation tooling checks (DOC-01/03)

The `docs-validation` job in the [CI workflow](../../.github/workflows/ci.yml)
validates the frozen OpenAPI file, all local `$ref` pointers, duplicate YAML keys,
root README/docs Markdown paths/heading anchors and Compose configuration using
the existing pinned tools. It runs on every PR into `develop`/`main`, pushes to
those branches and manual dispatch, including Java-only changes. Use the
[README commands](../README.md#validate-documentation-locally-and-in-ci) locally.
Swagger browser rendering and Mermaid rendering can be checked separately during
review; these are documentation checks, not financial acceptance tests. No AC/CONS
is marked PASS by a successful documentation job.

### Documentation freeze synchronization evidence — 2026-10-22

**Historical engineering-only review:** the commands/results below belong to the earlier documentation synchronization. The subsequent Product/UX/REST approval additionally changes OpenAPI approval metadata only; it does not turn earlier structural checks into runtime acceptance.

Local documentation validation ran in isolated `/tmp/finfast-freeze-docs-venv` with Python 3.14.7 and the unchanged `scripts/requirements-docs.txt` pins. The host interpreter initially lacked `markdown_it`; installing those existing pins in the temporary environment resolved that environment limitation. No project dependencies/configuration changed.

```sh
/tmp/finfast-freeze-docs-venv/bin/python -B scripts/check_docs.py
/tmp/finfast-freeze-docs-venv/bin/python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml
docker compose -f compose.docs.yaml config --quiet
git diff --check
```

The existing checker passed local Markdown/OpenAPI reference validation, the OpenAPI validator passed, and documentation Compose configuration validation passed. An additional temporary review script reused the existing parser across **all 29 tracked Markdown files** (including AGENTS, migration README and observability), checking local outgoing links/headings and table structure. It also checked unchanged canonical requirement/scenario content, exact PRD workload/thresholds, original BK-003 approval history, dependency graph, release checkboxes and source/configuration preservation. Git checkout line endings were accounted for by comparing non-Markdown bytes to the original checkout snapshot, including the CRLF Maven Windows wrapper. This script is a review artifact in `/tmp`, not new repository tooling or CI enforcement.

Only Markdown is changed by the freeze. No Maven, financial database, banking security/concurrency, browser, performance or Fineract test suite was executed for this documentation task. Existing/hosted test evidence above retains its own revision and scope. Swagger/Mermaid rendering and deployment verification were not executed by the documentation checks.

## CI foundation implementation and evidence

The [CI guide](../../README.md#continuous-integration) documents reproduction,
failure diagnosis, security and the future frontend job. `backend-tests` runs the
full Maven suite, including test-only ArchUnit rules/fixtures, Spring contexts and
real PostgreSQL/Flyway tests. Testcontainers needs Docker to create disposable
databases; it does not use a developer's persistent Compose database or H2.
`backend-package` depends on tests and fails its prerequisite gate unless they
succeeded, then packages with `-DskipTests` (test compilation enabled) and checks
the executable JAR for test-code/dependency leakage. `docs-validation` runs in
parallel with backend tests. No required job is filtered by changed files.

| Evidence category | Current status |
|---|---|
| Automation in source | Implemented as one `CI` workflow with stable job/check names `backend-tests`, `backend-package`, `docs-validation`; read-only token; no deployment |
| Local backend verification, 2026-10-21 | Temurin 21.0.12.1+1, Docker Engine 29.7.2, Testcontainers 2.0.5 and PostgreSQL 16.15: 466 tests, zero failures/errors/skips (235 PostgreSQL/container tests, 57 ArchUnit tests, 174 other unit/context/HTTP tests); Flyway V1/V2 applied to disposable databases. Subsequent `-DskipTests package` and executable JAR/isolation checks passed. |
| Local documentation / workflow validation, 2026-10-21 | Pinned dependencies installed in isolated Python 3.12.12; OpenAPI validation and README/docs link/anchor/reference checks passed (24 Markdown files, 223 local links, 88 refs, no duplicate YAML keys). Documentation Compose config, actionlint 1.7.7 and `git diff --check` passed. These are local checks, not a GitHub run. |
| GitHub baseline run (live verification, 2026-10-22) | [Merged develop run 39649338194](https://github.com/nvawntien/finfast/actions/runs/39649338194) at `8a04ca03505a140930b9bbc976dcdc1148db2693`: `backend-tests`, `backend-package` and `docs-validation` completed successfully. This hosted evidence applies to the linked BK-003/foundation revision; it does not establish banking-provider acceptance. Earlier [run 39601859299](https://github.com/nvawntien/finfast/actions/runs/39601859299) at `a4eb9ff` remains historical. |
| Required-check enforcement | Not verified or changed by this task; configure both branch rulesets only after successful remote checks exist |
| Banking acceptance / performance / CD | Not established by CI foundation; canonical AC/CONS and EV-003 evidence gates remain unchanged |

The intended required check names equal explicit job names, not the workflow name
`CI`. Confirm successful checks on the latest PR revision in GitHub before a
maintainer selects them under **Settings → Rules → Rulesets** for both `develop`
and `main`. Replace any retired `validate` requirement only after the new check is
available. PR approvals remain a separate requirement. Frontend dependency
installation from a lockfile, lint, type checks, unit tests and production build
will become a fourth check only after the real stack and successful runs exist.
