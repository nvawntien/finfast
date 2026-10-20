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

## Confirmed architecture direction — Tech Lead agreement

The current refinement request records the Tech Lead's agreement on Java/Spring Boot, PostgreSQL, **one deployable Modular Monolith**, DDD-inspired domain modules, pragmatic Hexagonal Architecture, constructor-based Spring DI, REST/OpenAPI/Swagger UI, protected server-managed sessions, PostgreSQL ACID transactions, and in-process Java calls. These decisions are **AGREED**, not reopened by the candidate table below. Future extraction is optional when justified; separate internal databases/network APIs, mandatory microservices/Kafka/Kubernetes/Redis, distributed transactions and event sourcing are not introduced.

This agreement does not ratify particular API names, lock algorithms, schema fields, dependency-tool versions, retry limits or performance claims. No individual participant names or additional approvals are inferred. Source architecture: [15](../03-design/15-system-architecture.md).

## Candidate engineering decisions (not yet ratified)

| Tech decision | Proposed choice | Alternatives to assess | Must be decided before | Status |
|---|---|---|---|---|
| TD-001 Numeric representation | Checked Java `long` / PostgreSQL `BIGINT`, JSON decimal **strings** | BigInteger/Numeric(precision), JSON number safe subset | Final API, migrations, AC-016 | PROPOSED |
| TD-002 Concurrency | READ COMMITTED with full-scope coordination → ascending stable Account-ID locks → global counter; recheck locked status/funds/bounds; fresh-transaction bounded retry | Optimistic version+retry | Transfers/funding/freeze implementation | PROPOSED |
| TD-003 Idempotency identity | NOT NULL/UNIQUE full `(initiator_user_id, operation_type, idempotency_key)` + transaction-scoped advisory coordination; versioned canonical bytes/fingerprint and durable final response | Transactional unique-intent-row coordination; no separately committed PROCESSING lifecycle | API/UX-004, AC-005/006/015/034 | PROPOSED |
| TD-004 History ordering | Ledger-owned transactional global `last_commit_seq` + committed high-water/keyset cursor; nextval alone is not commit order | Per-Account positions with separate Admin design; materialized snapshot; commit-ordered projection | History API, AC-025, EV-003 | PROPOSED |
| TD-005 Materialized Account balance | Store checked balance with posted entries in same ACID transaction | Compute solely from entries | Data schema, reconciliation, benchmark | PROPOSED |
| TD-006 Browser session | Spring Security server sessions + protected HttpOnly/Secure/SameSite cookies + CSRF | Other server-side session configurations | FR-002/API security | PROPOSED within approved direction |
| TD-007 Password hash | Argon2id or configured BCrypt with benchmarked work factor | Other adaptive hash | Registration/login | PROPOSED |
| TD-008 Transaction/error contracts | `/api/v1`, decimal-string VND, `Idempotency-Key`, explicit FINAL vs UNKNOWN statuses | Alternate route/status contracts | Parallel frontend/backend | PROPOSED |
| TD-009 GENESIS/bootstrap implementation | Restricted migration/setup command with singleton uniqueness + atomic POSTGRES transaction | Dedicated one-shot deployment Job | Initial deployment/AC-020 | PROPOSED |
| TD-010 Append-only enforcement | No mutation APIs, restricted DB privileges, optional DB guard triggers | Application-only immutability | Migrations/AC-031 | PROPOSED |
| TD-011 Audit storage | Admin success events in same database tx; security failure evidence separate | Dedicated operational store | Admin implementation/AC-019 | PROPOSED |
| TD-012 Cloud topology | One Core container + frontend + private persistent PostgreSQL | Other cloud runtimes | EV-001, EV-002 | NOT_SELECTED |
| TD-013 Exact benchmark client/environment | Execute PRD §9 plan on verified demo resources | Adjust with owner-approved documented deviation only | EV-003 | AWAITING MEASUREMENT |
| TD-014 Lock/cursor throughput risk | Measure potential counter contention separately from Account/Treasury waits; revisit TD-004 only with correctness-preserving alternative | See 16 §8 and 22: baseline then disjoint/hot-account/Treasury diagnostics, lock waits/holds and p95/throughput | EV-003 | PROPOSED |
| TD-015 Financial posting ownership | Restricted Ledger `FinancialPosting` → Accounts `PostingAccounts`; Transfers/Administration own outer REQUIRED transaction; posting requires existing transaction; final outcome and funding audit join it | Different facade owner only with the same single controlled path and acyclic ownership | BK-010/012/013; ARC-01/02; real transaction/fault tests | PROPOSED |
| TD-016 Module enforcement | Explicit named `api` interfaces and dependency allowlist; Spring Modulith verification plus ArchUnit caller/layer rules after compatibility check | Equivalent build-time architecture checks; package naming alone is insufficient | Module scaffolding and BK-003/004 | PROPOSED |

## Review items and implementation evidence gates

| Item | Refined source of truth | Review/evidence still required |
|---|---|---|
| DOC-01 | [17 workflow and future integration](../03-design/17-api-contracts.md#design-stage-api-documentation-workflow), [README run commands](../README.md#browse-the-api-locally-doc-01) | Tool/image upgrade review; actual Boot/Springdoc compatibility and runtime contract tests later |
| DOC-02 | [15 §3.3](../03-design/15-system-architecture.md#33-java-project-and-module-structure-doc-02) | Package/API details during implementation; no empty-package scaffolding required |
| DOC-03 | [README navigation](../README.md#navigation-requirements-to-engineering-documents) | Keep real paths/anchors valid in documentation CI; standalone missing docs remain planned |
| ARC-01 | [15 §3.1](../03-design/15-system-architecture.md#31-one-financial-posting-path-arc-01), TD-015 | Approve posting owner and permitted caller/method pairs |
| ARC-02 | [16 §5](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02), TD-015 | Proxied transaction/rollback tests, rejection persistence, audit/commit failure injection |
| ARC-03 | [16 §6](../03-design/16-domain-model-and-data-design.md#6-locking-and-isolation-discipline-arc-03), TD-002 | Approve lock comparator and isolation premises; real PostgreSQL races, timeout/retry budgets |
| ARC-04 | [16 §7](../03-design/16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04), TD-003 | Canonicalization/API review; execute [22 PostgreSQL PoC](../05-verification/22-test-strategy-and-acceptance.md#postgresql-idempotency-proof-of-concept-arc-04) |
| ARC-05 | [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05), TD-004/014 | Late-commit pagination proof and EV-003/diagnostic contention measurements |
| ARC-06 | [15 §3.2/3.4](../03-design/15-system-architecture.md#34-enforcing-the-boundaries-arc-06), TD-016 | Branch-local ArchUnit mechanism and negative fixtures implemented; candidate policy approval and real business-module integration remain pending |

**Step 4E.2 implementation evidence (working branch, not merged):** ArchUnit 1.5.1
(`archunit-junit6`, TEST scope) matches the checked-in Boot-managed Jupiter 6.0.3.
Production scans and segregated positive/negative fixtures implement the candidate
module/API, cycle and basic layer rules; see the
[module guide](../03-design/module-contracts-and-boundaries.md#automated-evidence-and-limits).
This does not approve TD-015/016 or §3.2's allowlist, establish method-level posting
permissions or mark ARC-01/02/06 acceptance complete. Business modules, proxied
transaction/authorization integration and posting-caller rules remain future work.

**Remaining decisions:** deployment-specific PostgreSQL durability, bounded retry/deadline values, cursor details and counter capacity. Checked-in build/configuration and foundation guides record implemented framework/test/database pins; those are not blanket approval of the remaining proposals. Technical proposals remain PROPOSED until explicitly reviewed; documentation or architecture checks alone do not approve them. Standalone ADR-001..003 do not yet exist; current rationale lives in 15/16 and statuses here. Structural fixtures are not financial application/concurrency/benchmark evidence.

## External evidence tasks (status inherited from approved baseline)

- **EV-001 — PENDING VERIFICATION:** actual course rubric; any mandated cloud technology, autoscaling/load demonstration, acceptance format. Before architecture/deployment finalization.
- **EV-002 — PENDING VERIFICATION:** provider/budget/compute/DB storage/TLS and feasible persistence. Before resource allocation and performance test.
- **EV-003 — AWAITING MEASUREMENT:** PRD §9 deterministic 3,000-intent, 300 s workload; p95 target ≤2 s, reported honestly with correctness evidence.

## Design-review change protocol

1. Log proposed choice, rationale, affected `FR/UX/NFR`, `UC/BR/AC`, trade-offs, owner and decision date.
2. Verify proposal preserves approved business policy. If it *changes* policy, obtain explicit project owner decision and update 01–09 as needed **before** implementation.
3. Update the decision register, architecture/data design, API contracts, tests and backlog together (and standalone ADRs if later created); review with relevant domain and presentation owners.
4. Treat proposed benchmark/browser elaborations in PRD 09 as **awaiting review**, not pre-existing discovery approval.
5. Do not claim any application, cloud or performance outcome until linked executed evidence exists.
