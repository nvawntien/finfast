# 22 — V1 Test Strategy, Canonical Acceptance Execution and Failure Evidence

> **PROPOSED test execution plan** grounded in [04 — 38 canonical AC scenarios](../baseline/04-use-cases.md), [06 — 19 CONS risk views](../baseline/06-business-rules.md), and [09 PRD §§9–11](../baseline/09-product-requirements.md). The canonical Given/When/Then in 04 remains normative. `T-*` is a test execution grouping, not new business acceptance criteria. **No tests are asserted passing.**

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

## PRD performance test (NFR-004; EV-003)

Use the **exact** fixture/schedule/clean-pass acceptance criteria in [13 quality plan](13-quality-attributes.md) and [09 PRD §9](../baseline/09-product-requirements.md). Dedicated 100-Customer, 50-pair dataset; 300 warm-up intents and 3,000 measured intents, every 100ms; report primary attempts separately from recovered outcomes. Zero financial-invariant violations. EV-003 remains AWAITING MEASUREMENT, not a passed gate.

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
