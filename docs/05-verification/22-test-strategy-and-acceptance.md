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

> **Remaining financial workflow execution plan** grounded in [04 — 38 canonical AC scenarios](../01-discovery/04-use-cases.md), [06 — 19 CONS risk views](../01-discovery/06-business-rules.md), and [09 PRD §§9–11](../02-requirements/09-product-requirements.md). The canonical Given/When/Then in 04 remains normative. `T-*` is a test execution grouping, not new business acceptance criteria. **No complete banking acceptance or performance result is asserted by the scoped checks above.** Documentation checks are distinct from these runtime evidence gates.

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
