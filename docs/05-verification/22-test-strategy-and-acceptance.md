# 22 — V1 Test Strategy, Canonical Acceptance Execution and Failure Evidence

> **PROPOSED test execution plan** grounded in [04 — 38 canonical AC scenarios](../01-discovery/04-use-cases.md), [06 — 19 CONS risk views](../01-discovery/06-business-rules.md), and [09 PRD §§9–11](../02-requirements/09-product-requirements.md). The canonical Given/When/Then in 04 remains normative. `T-*` is a test execution grouping, not new business acceptance criteria. **No banking acceptance, concurrency or performance tests are asserted passing.** Documentation checks are distinct from these runtime evidence gates.

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

- Architecture checks now exist on the Step 4E.2 working branch: ArchUnit scans production classes and applies the same candidate module/API, cycle and layer rules to segregated positive/negative fixtures. See the [module guide](../03-design/module-contracts-and-boundaries.md#automated-evidence-and-limits). No business modules exist yet; real dependency integration and caller/method restrictions for posting, Account mutation and GENESIS remain deferred. Naming a package `internal` is not sufficient evidence, and structural checks do not pass financial ACs.
- Module integration: Identity registration through Customer/Accounts creates all three records or none; Ledger through Accounts posts both sides or none; Transfers joins Ledger/Idempotency; Administration joins Ledger/Idempotency/audit or Accounts/status/audit. History remains read-only and authorized. Bootstrap tests are separately controlled, never exposed as runtime fault endpoints.
- Spring proxy/commit: invoke the real application bean through the proxy and the HTTP boundary. Inject failure during method body, JPA flush, required audit/outcome persistence and actual commit. Check checked/unchecked exception rollback policy and ensure the client never receives COMPLETED before confirmed commit (AC-010/011/019/033).
- Final rejection: validate insufficient funds before writes, commit rejection normally, add funds using a different authorized intent, then replay the first key and confirm its original rejection. Failure to persist that rejection must remain a technical/non-final result (AC-015/033).
- Independent transaction regression: detect accidental REQUIRES_NEW for any required effect; no committed audit, outcome, entry or balance survives a rolled-back parent. Verify restricted mutation APIs fail when called without an enclosing transaction (AC-010/019/022/023).

## PostgreSQL idempotency proof-of-concept (ARC-04)

**PROPOSED small experiment to implement before the financial engine; NOT RUN.** Run on an isolated real PostgreSQL dataset with the intended major version, READ COMMITTED and multiple independent connections. Use a minimal scope/outcome and paired-balance/posting fixture to exercise [16 §7](../03-design/16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04); do not install a production feature or public fault API. Record full-scope uniqueness, canonicalization/hash version, lock/timeouts, durability settings (`fsync`, `full_page_writes`, `synchronous_commit`, persistent storage) and every transaction's begin/lock/commit events. Synchronize schedules with barriers and observed database waits, not sleeps alone.

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

Also compare a minimal transactional unique-intent-row variant using exactly the same schedules; show no separately committed unfinished PROCESSING record. Test canonicalization vectors and fingerprint version upgrades without losing historical replay. Capture one final outcome at most, one movement at most, two entries for success, zero for rejection, matching balances, required funding audit and conserved supply after each schedule (AC-013/019/022/023). Approval of TD-003 depends on this evidence, not on the conceptual table alone.

## Additional PostgreSQL concurrency schedules (ARC-03)

Extend the existing harness with distinct-key Treasury allocations competing for remaining supply; verify all lock Treasury/recipient in Account ID order, no negative balance and correct rejection outcomes. Execute both freeze-first and funding-first schedules, including failure of freeze audit (AC-008/018/019/023/032).

Force a deadlock in a controlled test variant and a lock-timeout on an intentionally held participant/scope row. Confirm full rollback, bounded fresh-transaction same-key retry, eventual original outcome or honest non-final response, and released locks; no database exception becomes a permanent business rejection (AC-007/017/033). Verify Account ordering with IDs whose Java/default comparator ordering could differ from PostgreSQL UUID ordering. After every run reconcile on a consistent committed snapshot, checking missing/duplicate entries and required audits (AC-022/023). H2/in-memory tests cannot substitute for these PostgreSQL checks.

## History and counter-contention experiment (ARC-05)

Follow [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05). Hold an earlier posting before commit, start a second disjoint posting and fetch history. Prove the global counter prevents a later-position commit from overtaking the first, and rollback does not create visible financial facts. Traverse pages during new commits: stable initial bound, no missing/duplicate authorized movements, valid default/max size, immutable references, no rejected requests or other Customer's records (AC-025/024/031).

Run the prescribed benchmark below unchanged before separate diagnostic loads. For disjoint pairs, hot Account, duplicate scope and Treasury fan-in, collect offered/achieved throughput; p50/p95/p99; scope/Account/counter wait and hold time; retries/deadlocks/timeouts; database CPU/I/O/commit latency; and reconciliation. Use the documented 10/25/50/100 requests/s diagnostic sweep only within resource capacity. Record actual PostgreSQL version/settings, resource limits and observation overhead. Distinguish counter contention from Treasury or other account contention. TD-004/014 reconsideration requires measured impact and a replacement passing the same pagination/fault tests; no proven bottleneck or benchmark result is asserted.

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

## Documentation tooling checks (DOC-01/03)

The [documentation CI workflow](../../.github/workflows/docs.yml) validates the design OpenAPI file, all local `$ref` pointers, duplicate YAML keys, local Markdown paths/heading anchors and Compose configuration using pinned tools. Use the [README commands](../README.md#validate-documentation-locally-and-in-ci) locally. Swagger browser rendering and Mermaid rendering can be checked separately during review; these are documentation checks, not financial acceptance tests. No AC/CONS is marked PASS by a successful documentation workflow.
