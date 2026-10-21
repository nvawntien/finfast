# 25 — Engineering Decisions and Verification Dependencies

> This register separates **approved product/discovery facts**, **approved BK-003 architecture/Java contracts**, **unapproved technical mechanisms** and **external measurements**. Do not reopen A-001..014 or OQ-001..013 by treating them as new questions. The project owner has decision authority for policy changes; Tiến (Tech Lead) has final authority over cross-module architectural contracts.

## Approved baseline to preserve

| IDs | Approved meaning / architectural constraint |
|---|---|
| A-001..A-012, A-014 | One Customer Account; VND integers; ACTIVE/FROZEN; finite Treasury GENESIS; safe synchronous transactions; controlled Admin operations; committed-only history |
| A-013 / DEC-006 | Java/Spring Boot + PostgreSQL + channel-independent Core + modular monolith initial direction; server-managed protected cookie sessions |
| DEC-001..DEC-005 | Business vision, ownership, signed ledger/GENESIS, retry/final outcomes, atomic Admin audit |
| DEC-007 | Zero financial-invariant violations; provisional benchmark target awaiting measurement |
| DEC-008 | External course/cloud/performance evidence gates remain separate from product approval |

## Confirmed architecture direction — Tech Lead agreement

The earlier refinement request records the Tech Lead's agreement on Java/Spring Boot, PostgreSQL, **one deployable Modular Monolith**, DDD-inspired domain modules, pragmatic Hexagonal Architecture, constructor-based Spring DI, REST/OpenAPI/Swagger UI, protected server-managed sessions, PostgreSQL ACID transactions, and in-process Java calls. These decisions are **AGREED**, not reopened by the table below. Future extraction is optional when justified; separate internal databases/network APIs, mandatory microservices/Kafka/Kubernetes/Redis, distributed transactions and event sourcing are not introduced.

That earlier agreement alone did not ratify particular Java signatures or mechanisms. The explicit 2026-10-22 BK-003 decision below now approves the current Java contracts, TD-015 and TD-016 only. It does not ratify lock algorithms, retry limits or performance claims. No additional individual owner endorsements are inferred. Source architecture: [15](../03-design/15-system-architecture.md).

## Engineering decisions and scoped statuses

| Tech decision | Choice / approval scope | Alternatives / remaining review | Implementation or evidence dependency | Status |
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
| TD-015 Financial posting ownership | Transfers owns transfer outer transaction; Administration owns allocation outer transaction/required audit; Ledger owns controlled paired posting, immutable facts/reconciliation through Accounts `PostingAccounts`; Accounts owns balance/status invariants; Idempotency owns scoped intent/final outcomes. Separate restricted GENESIS/Treasury capabilities; all required effects commit atomically, success only after durable commit | Concrete annotation placement/guards, SQL locking/isolation/retry and bootstrap mechanisms remain separate review items; no bypass or isolated debit/credit commit | BK-010/011/012/013; ARC-01/02; real transaction/fault tests still required | APPROVED |
| TD-016 Module enforcement | Current nine-module/18-edge graph, provider `api` only/private `internal`, no cycles/common-to-business dependency, pure APIs and current ArchUnit structural/capability enforcement | Additional public capabilities/directions or rule amendments need Tech Lead approval; no weaker enforcement, speculative modules or Spring Modulith dependency | Approved baseline applies on BK-003 merge into `develop`; real provider integration still required | APPROVED |

## Review items and implementation evidence gates

| Item | Refined source of truth | Review/evidence still required |
|---|---|---|
| DOC-01 | [17 workflow and future integration](../03-design/17-api-contracts.md#design-stage-api-documentation-workflow), [README run commands](../README.md#browse-the-api-locally-doc-01) | Tool/image upgrade review; actual Boot/Springdoc compatibility and runtime contract tests later |
| DOC-02 | [15 §3.3](../03-design/15-system-architecture.md#33-java-project-and-module-structure-doc-02) | Package/API details during implementation; no empty-package scaffolding required |
| DOC-03 | [README navigation](../README.md#navigation-requirements-to-engineering-documents) | Keep real paths/anchors valid in documentation CI; standalone missing docs remain planned |
| ARC-01 | [15 §3.1](../03-design/15-system-architecture.md#31-one-financial-posting-path-arc-01), TD-015 | Ownership/caller capabilities approved; implement controlled path and verify real authorization/transaction guards |
| ARC-02 | [16 §5](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02), TD-015 | Proxied transaction/rollback tests, rejection persistence, audit/commit failure injection |
| ARC-03 | [16 §6](../03-design/16-domain-model-and-data-design.md#6-locking-and-isolation-discipline-arc-03), TD-002 | Approve lock comparator and isolation premises; real PostgreSQL races, timeout/retry budgets |
| ARC-04 | [16 §7](../03-design/16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04), TD-003 | Canonicalization/API review; execute [22 PostgreSQL PoC](../05-verification/22-test-strategy-and-acceptance.md#postgresql-idempotency-proof-of-concept-arc-04) |
| ARC-05 | [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05), TD-004/014 | Late-commit pagination proof and EV-003/diagnostic contention measurements |
| ARC-06 | [15 §3.2/3.4](../03-design/15-system-architecture.md#34-enforcing-the-boundaries-arc-06), TD-016 | Policy/ArchUnit enforcement approved and fixtures implemented; real business-module integration remains pending |

**Step 4E.2 implementation evidence:** ArchUnit 1.5.1
(`archunit-junit6`, TEST scope) matches the checked-in Boot-managed Jupiter 6.0.3.
Production scans and segregated positive/negative fixtures implement the
module package/API isolation, dependency, cycle and basic layer rules; see the
[module guide](../03-design/module-contracts-and-boundaries.md#automated-evidence-and-limits).
Tooling alone did not approve TD-015/016 or §3.2's allowlist; approval comes from
Tiến's explicit decision below. Tooling does not mark ARC-01/02/06 runtime evidence
complete. BK-003 adds actual source contracts and approved capability/Treasury
initialization checks; proxied transaction/authorization integration and financial
provider behavior remain future work.

### BK-003 approval record and remaining authority

**Decision context/date:** on **2026-10-22**, the explicit *BK-003 — Final Architecture
Review, Tech Lead Approval & Module API Contract Freeze* instruction records
**Tiến's approval** of the current BK-003 Java interfaces/DTO signatures as the
authoritative V1 integration baseline, plus TD-015 and TD-016 in the scoped table
above. This records the supplied Tech Lead decision, not a formal meeting or
individual owner endorsements. No further owner approval is needed to record it.

**Effective integration milestone:** mandatory shared team baseline when the BK-003
PR merges into `develop`; merge remains pending. The 17 source files (14 capability
interfaces plus three standalone value/result types, including their nested types)
are IMPLEMENTED; providers/Beans/banking workflows are NOT IMPLEMENTED. Exact source
signatures, compatibility matrix and R-01..R-07 implementation follow-ups live in the
[canonical module guide](../03-design/module-contracts-and-boundaries.md).
Only TD-015/016 statuses change in this decision. TD-001..014 retain their prior
statuses; existing scoped schema/shared-contract approvals remain as recorded in
their foundation guides. Approved ownership/API shape does not approve a proposed
coordination algorithm, SQL mechanism, annotation placement or measured outcome.

| Decision | BK-003 approved contract / implementation outcome | Remaining mechanism or runtime evidence |
|---|---|---|
| TD-002 | Existing concurrency invariants retained; contracts require protected current-state validation, without selecting row/advisory ordering or retry budgets | Minh/Hải/Tiến review comparator/isolation/retry and real concurrent proxy behavior |
| TD-003 | APPROVED inspect/record/find signatures, scope and typed final/non-final values; no advisory SQL, digest mapping or canonical encoder | Minh/Tiến review encoding/version vectors, external response mapping, coordination protocol and PostgreSQL PoC |
| TD-004 | APPROVED participant-scoped Customer projection signatures and opaque continuation; no new order/counter algorithm | Tiến/Hải/Trung review cursor integrity/snapshot implementation, Admin projection and measured contention |
| TD-008 | Existing REST/OpenAPI/error/money contracts preserved byte-for-byte; internal pending values do not assert HTTP finality | Provider/frontend review stable codes and response mapping; Sĩ remains a REST consumer |
| TD-009 | APPROVED protected provisioning/GENESIS signatures; no setup entry, secret loading or initialization implementation | Hưng/Trung/Tiến review credential source, bootstrap mechanism, races and restart verification |
| TD-015 | APPROVED ownership, transaction responsibilities and capability split; source/pending values and caller checks IMPLEMENTED, structural checks VERIFIED locally | Concrete guard placement/helper-delegation amendments require review; real commit/audit/rollback/authorization tests still missing |
| TD-016 | APPROVED current pure API/capability policy and unchanged 18-edge graph; checks IMPLEMENTED and structurally VERIFIED locally | Owners integrate real providers and test runtime behavior; future capabilities/edges require Tiến's approval; no Spring Modulith dependency added |

No conflicting Java contract was found on the visible baseline, available local/
tracking refs, live branches or open PRs. This is **not** verified compatibility
with unpushed teammate work. Individual owner endorsements are not recorded;
Tiến's approved contracts become mandatory on merge. For a mismatch, preserve work,
compare exact packages/signatures, report to Tiến, obtain an integration decision,
adapt member-owned code without duplicate APIs and rerun tests. Mark only the
affected integration BLOCKED while awaiting that decision.

**Remaining decisions:** deployment-specific PostgreSQL durability, bounded retry/deadline values, cursor details and counter capacity. Checked-in build/configuration and foundation guides record implemented framework/test/database pins; those are not blanket approval of the remaining proposals. Technical proposals remain PROPOSED until explicitly reviewed; documentation or architecture checks alone do not approve them. Standalone ADR-001..003 do not yet exist; current rationale lives in 15/16 and statuses here. Structural fixtures are not financial application/concurrency/benchmark evidence.

## External evidence tasks (status inherited from approved baseline)

- **EV-001 — PENDING VERIFICATION:** actual course rubric; any mandated cloud technology, autoscaling/load demonstration, acceptance format. Before architecture/deployment finalization.
- **EV-002 — PENDING VERIFICATION:** provider/budget/compute/DB storage/TLS and feasible persistence. Before resource allocation and performance test.
- **EV-003 — AWAITING MEASUREMENT:** PRD §9 deterministic 3,000-intent, 300 s workload; p95 target ≤2 s, reported honestly with correctness evidence.

## Design-review change protocol

1. Log proposed choice, rationale, affected `FR/UX/NFR`, `UC/BR/AC`, trade-offs, owner and decision date.
2. Verify proposal preserves approved business policy. If it *changes* policy, obtain explicit project owner decision and update 01–09 as needed **before** implementation.
3. Update the decision register, architecture/data design, API contracts, tests and backlog together (and standalone ADRs if later created); review with relevant domain and presentation owners.
   For the approved Java baseline, obtain prior Tech Lead approval for breaking
   changes, new public capabilities or dependency directions. Preserve compatibility
   where practical; no silent renames, competing DTOs or boundary bypass. Consultation
   does not imply individual endorsements; Tiến has final architectural authority.
4. Treat proposed benchmark/browser elaborations in PRD 09 as **awaiting review**, not pre-existing discovery approval.
5. Do not claim any application, cloud or performance outcome until linked executed evidence exists.
