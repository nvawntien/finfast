# 25 — Engineering Decisions and Verification Dependencies

> This register separates **approved product/discovery facts** from **unapproved technical proposals** and **external measurements**. Do not reopen A-001..014 or OQ-001..013 by treating them as new questions. The project owner has decision authority for any policy change.

## Approved baseline to preserve

| IDs | Approved meaning / architectural constraint |
|---|---|
| A-001..A-012, A-014 | One Customer Account; VND integers; ACTIVE/FROZEN; finite Treasury GENESIS; safe synchronous transactions; controlled Admin operations; committed-only history |
| A-013 / DEC-006 | Java/Spring Boot + PostgreSQL + channel-independent Core + modular monolith initial direction; server-managed protected cookie sessions |
| DEC-001..DEC-005 | Business vision, ownership, signed ledger/GENESIS, retry/final outcomes, atomic Admin audit |
| DEC-007 | Zero financial-invariant violations; provisional benchmark target awaiting measurement |
| DEC-008 | External course/cloud/performance evidence gates remain separate from product approval |

## Candidate engineering decisions (not yet ratified)

| Tech decision | Proposed choice | Alternatives to assess | Must be decided before | Status |
|---|---|---|---|---|
| TD-001 Numeric representation | Checked Java `long` / PostgreSQL `BIGINT`, JSON decimal **strings** | BigInteger/Numeric(precision), JSON number safe subset | Final API, migrations, AC-016 | PROPOSED |
| TD-002 Concurrency | PostgreSQL row locks in deterministic Account order + transactional idempotency key coordination | Optimistic version+retry | Transfers/funding/freeze implementation | PROPOSED |
| TD-003 Idempotency identity | `(initiator, operation, key)` durable unique full fields + versioned canonical payload and stored final response | Request hash, separate processing table | API/UX-004, AC-005/006/015/034 | PROPOSED |
| TD-004 History ordering | Transactional global `commit_seq` + high-water cursor | Materialized snapshot, ordered event log | History API, AC-025, EV-003 | PROPOSED |
| TD-005 Materialized Account balance | Store checked balance with posted entries in same ACID transaction | Compute solely from entries | Data schema, reconciliation, benchmark | PROPOSED |
| TD-006 Browser session | Spring Security server sessions + protected HttpOnly/Secure/SameSite cookies + CSRF | Other server-side session configurations | FR-002/API security | PROPOSED within approved direction |
| TD-007 Password hash | Argon2id or configured BCrypt with benchmarked work factor | Other adaptive hash | Registration/login | PROPOSED |
| TD-008 Transaction/error contracts | `/api/v1`, decimal-string VND, `Idempotency-Key`, explicit FINAL vs UNKNOWN statuses | Alternate route/status contracts | Parallel frontend/backend | PROPOSED |
| TD-009 GENESIS/bootstrap implementation | Restricted migration/setup command with singleton uniqueness + atomic POSTGRES transaction | Dedicated one-shot deployment Job | Initial deployment/AC-020 | PROPOSED |
| TD-010 Append-only enforcement | No mutation APIs, restricted DB privileges, optional DB guard triggers | Application-only immutability | Migrations/AC-031 | PROPOSED |
| TD-011 Audit storage | Admin success events in same database tx; security failure evidence separate | Dedicated operational store | Admin implementation/AC-019 | PROPOSED |
| TD-012 Cloud topology | One Core container + frontend + private persistent PostgreSQL | Other cloud runtimes | EV-001, EV-002 | NOT_SELECTED |
| TD-013 Exact benchmark client/environment | Execute PRD §9 plan on verified demo resources | Adjust with owner-approved documented deviation only | EV-003 | AWAITING MEASUREMENT |
| TD-014 Lock/cursor throughput risk | Measure global commit-sequencer bottleneck; revisit ADR-003 if necessary | Snapshot materialization / ordered commit log | EV-003 | PROPOSED |

## External evidence tasks (status inherited from approved baseline)

- **EV-001 — PENDING VERIFICATION:** actual course rubric; any mandated cloud technology, autoscaling/load demonstration, acceptance format. Before architecture/deployment finalization.
- **EV-002 — PENDING VERIFICATION:** provider/budget/compute/DB storage/TLS and feasible persistence. Before resource allocation and performance test.
- **EV-003 — AWAITING MEASUREMENT:** PRD §9 deterministic 3,000-intent, 300 s workload; p95 target ≤2 s, reported honestly with correctness evidence.

## Design-review change protocol

1. Log proposed choice, rationale, affected `FR/UX/NFR`, `UC/BR/AC`, trade-offs, owner and decision date.
2. Verify proposal preserves approved business policy. If it *changes* policy, obtain explicit project owner decision and update 01–09 as needed **before** implementation.
3. Update ADR, API/data contracts, tests and backlog together; review with relevant domain and presentation owners.
4. Treat proposed benchmark/browser elaborations in PRD 09 as **awaiting review**, not pre-existing discovery approval.
5. Do not claim any application, cloud or performance outcome until linked executed evidence exists.
