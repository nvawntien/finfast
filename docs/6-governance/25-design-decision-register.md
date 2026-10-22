# 25 — Engineering Decisions and Verification Dependencies

This is the canonical V1 engineering decision register. Approved discovery (A-001..014, closed OQ-001..013, DEC-001..008) and financial policy remain unchanged. **APPROVED describes authority and scope; IMPLEMENTED describes visible code; VERIFIED requires executed evidence.** An approved algorithm is not a passing financial workflow. The later 2026-10-22 Product/UX/REST record below approves the existing PRD/browser/REST implementation baselines; implementation evidence, external gates and final release acceptance remain separate.

The separately authorized [BK-010 implementation evidence](#bk-010-fix-001-and-phase-2-implementation-evidence--2026-10-22) closes FIX-001 and records Phase 2 infrastructure verification. Earlier dated freeze findings below remain historical; they do not override this later scoped status or imply completion of banking workflows.

## Approved baseline to preserve

- One Customer Account, integral VND, ACTIVE/FROZEN, finite Treasury GENESIS, synchronous atomic movements, controlled Admin actions and committed-only history.
- Java 21 / Spring Boot 4.1.1 / Maven Wrapper / PostgreSQL 16 / Flyway; one deployable modular monolith, pragmatic Hexagonal Architecture, constructor injection and one logical database per environment.
- Existing BK-003 public source signatures, provider ownership, nine-module/18-edge graph and capability checks. No foreign `internal` access, new API, dependency or schema is authorized by this documentation change.

## Confirmed architecture direction — Tech Lead agreement

The initial Java/Spring Boot/PostgreSQL modular monolith, in-process APIs, server-managed protected sessions and PostgreSQL ACID direction remains agreed. Historical BK-003 approval selected Java contracts and TD-015/016 only. The later freeze below explicitly selects the other mechanisms; it does not retrospectively enlarge the earlier approval.

## Engineering decisions and scoped statuses

Previous status means the status immediately before the dated freeze, not implementation readiness. All APPROVED scopes below were selected in the supplied architecture-review baseline under Tiến's authorization; no member endorsement is inferred.

| Decision | Previous status | Selected V1 baseline | Current approval scope |
|---|---|---|---|
| TD-001 | PROPOSED | Checked Java `long`, existing `VndAmount`/`VndArithmetic`, PostgreSQL BIGINT, JSON decimal strings; positive amounts, nonnegative balances, signed deltas | APPROVED — DESIGN |
| TD-002 | PROPOSED | READ COMMITTED; scope coordination → unsigned UUID-ordered sequential Account row locks → locked validation → dataset counter → atomic writes; concrete REQUIRED/MANDATORY; bounded fresh retries | APPROVED — DESIGN; concurrency/transaction evidence pending |
| TD-003 | PROPOSED | Full scoped identity, transaction advisory lock, canonical v1 UTF-8 JSON + SHA-256, full-intent comparison, atomic final-only outcomes and authorized recovery | APPROVED — DESIGN; PostgreSQL proof pending |
| TD-004 | PROPOSED | Transactional `dataset_control.last_commit_seq`; committed high-water descending keyset; HMAC-SHA256 protected cursor; 20/default, 100/max | APPROVED — DESIGN; correctness/capacity evidence pending |
| TD-005 | PROPOSED | Accounts-owned materialized balance, updated atomically with Ledger facts, reconciled against committed signed entries | APPROVED — DESIGN |
| TD-006 | PROPOSED within agreed direction | Spring Security server sessions; HttpOnly/Secure under HTTPS, explicit SameSite (initial Lax where topology permits), expiry/logout/rotation, roles and CSRF | APPROVED — DESIGN; security evidence pending |
| TD-007 | PROPOSED | Standard Spring Security BCrypt, initial strength 12, configurable and benchmarked; per-password salt, no secret logging | APPROVED — DESIGN; security/cost measurements pending |
| TD-008 | PROPOSED | Preserve existing `/api/v1` OpenAPI, decimal strings, Idempotency-Key and distinct final/rejected/conflict/non-final/protocol results | APPROVED — EXISTING IMPLEMENTATION DIRECTION; endpoints not supplied |
| TD-009 | PROPOSED | Explicit protected operator-driven setup, one outer transaction for configured Admin + Treasury + GENESIS + positive entry + dataset control; verify existing dataset, never automatic startup reseed | APPROVED — DESIGN; initialization/restart evidence pending |
| TD-010 | PROPOSED (V1/V2 already implemented) | Retain V2 UPDATE/DELETE/TRUNCATE guards and immutable facts/migrations; no repair API; restrict runtime privileges where deployment supports | APPROVED — EXISTING IMPLEMENTATION DIRECTION |
| TD-011 | PROPOSED | PostgreSQL Admin success audit in the same operation transaction; audit failure rolls back allocation/status effects; security-denial logs separate | APPROVED — DESIGN |
| TD-012 | NOT_SELECTED | One Core deployment + frontend + private persistent PostgreSQL 16, TLS/secrets/backups/restart; no added distributed services | APPROVED — REFERENCE TOPOLOGY / PROVIDER PENDING |
| TD-013 | AWAITING MEASUREMENT | Exact PRD §9 reproducible benchmark and reporting method, with unchanged workload/thresholds | APPROVED — METHOD / EVIDENCE PENDING |
| TD-014 | PROPOSED | Keep counter; measure disjoint/hot Account/duplicate scope/Treasury waits and commit/WAL; revisit only on measured impact plus equivalent correctness proof | APPROVED — DIAGNOSTIC AND REVISIT POLICY; measurements pending |
| TD-015 | APPROVED | Transfers outer transfer transaction; Administration allocation/status + audit; Ledger posting/facts/reconciliation; Accounts state; Idempotency scoped outcomes; Bootstrap initialization | APPROVED — PREVIOUSLY RATIFIED |
| TD-016 | APPROVED | Existing nine modules/18 edges, pure APIs/private internals and current structural/capability enforcement | APPROVED — PREVIOUSLY RATIFIED |

## Rationale, obligations and revisit conditions

| TD | Rationale / alternative considered | Affected owners and implementation obligations | Verification / revisit condition |
|---|---|---|---|
| 001 | Exact finite VND with existing shared values; reject floating point, unnecessary multicurrency BigDecimal or new money abstractions | All; check every addition/subtraction/negation, bounds before writes and JSON parsing | Numeric boundary/overflow tests incl. Long.MAX_VALUE; widen only for an explicitly changed monetary requirement |
| 002 | Short serialized current-state decisions avoid stale spending; optimistic retries not selected | Minh/Hải/Tiến/Trung; all mutations share lock order and one transaction manager; 3 total attempts, nominal 25/75 ms backoff+jitter within initial 2 s deadline, bounded waits; recognized 40P01/40001/55P03 only; resolve ambiguous commit first | Real PostgreSQL/proxied rollback, opposite direction, freeze races, Treasury competition and retry exhaustion; tune defaults from evidence without changing correctness |
| 003 | Coordinate before absent-row lookup; no persisted unfinished lifecycle; unique-intent-row alternative not selected | Minh with callers; exact `(initiator_user_id, operation_type, idempotency_key)` (1–200 exact characters); stable namespaced SHA-256-derived 64-bit lock; fresh SQL read after wait; canonical version/intent/fingerprint/response | Duplicate/crash/lost-response/hash-collision/version vectors; amendment only if proof exposes a correctness/security incompatibility |
| 004 | Counter lock serializes commit tail; sequence allocation, OFFSET and timestamp-only ordering cannot prove snapshot completeness | Tiến/Hải; increment ordinary success once after Account validation, rollback increment, GENESIS initializes; none for rejection/replay/freeze; owner/account/bounds/version-bound HMAC cursor, current auth each page | Late commit/page tampering/restart tests and counter capacity; replace only with measured need and equivalent ordering proof |
| 005 | Reads remain cheap while Accounts retains balance authority; historical SUM alone is not available balance | Hải/Tiến; balance + facts same transaction; consistent-snapshot reconciliation, including Treasury/GENESIS once | Per-Account/dataset conservation after failures/concurrency/restart; no balance repair or arbitrary setter |
| 006 | Existing browser session architecture; no localStorage bearer, bespoke auth/OIDC or Redis added | Hưng/Sĩ; framework CSRF token integration, session rotation/expiry/logout, resource/role checks, topology-aware cookie/CORS settings | Real security/browser tests; revisit topology-specific cookie settings when actual hosting is known |
| 007 | Existing Spring Security ecosystem avoids a new Argon2 dependency/strategy layer | Hưng; BCrypt strength 12 default, measured cost, salt, private credentials; reject passwords exceeding BCrypt's 72 UTF-8 byte capacity before encoding, never truncate or add custom prehash | Non-ASCII/72-byte boundary/login/rehash tests and hash-cost measurement; tuning is not a latency guarantee or a new password API |
| 008 | Stable frontend integration; Java Pending cannot certify HTTP success | Provider owners/Sĩ; bind existing YAML statuses/schemas exactly, finalize only after commit, technical uncertainty stays non-final | Endpoint contract and browser recovery tests; actual incompatible contract requires a narrow reviewed amendment |
| 009 | Explicit operator action protects finite supply; startup reseeding/ordinary GENESIS endpoint rejected | Trung with Hưng/Hải/Tiến; Bootstrap orchestrates Identity provisioning, Accounts Treasury and Ledger GENESIS, private credentials, immediate-FK insert order and uniqueness-race recovery | Concurrent first setup, partial failure, existing-dataset verification, restart; no later automatic credential reset or re-mint |
| 010 | Existing DB guards add defense beyond immutable Java; application-only protection insufficient | Tiến/platform; retain V1/V2, no UPDATE/DELETE/TRUNCATE financial API; separate migration/runtime roles where feasible | Real PostgreSQL guard tests and deployment privilege inspection; DB owner/superuser can bypass controls and remains a deployment limitation |
| 011 | A successful Admin operation without its evidence violates approved policy; asynchronous audit rejected | Tiến with Hải/Minh; allocation all effects + success audit; freeze/unfreeze state + complete audit in one transaction; UNCHANGED is not a movement | Audit failure injection, unchanged status, no partial commit; operational security logging is not a substitute |
| 012 | Minimal topology meets current monolith constraints; no Kubernetes/Kafka/Redis/microservices | Trung/Sĩ/Hưng; verify course/provider, private DB, persistent volume, TLS, secrets, backups and recovery | EV-001/002 still pending; amend if verified external obligations genuinely conflict; provider/SKU/region/budget not selected |
| 013 | Reproducible honest comparison; no easier workload/fast-rejection substitute | Minh/Sĩ with Trung; PRD §9 environment, 100 Customers/50 pairs, 100M supply/100 allocations, warm-up 300 intents/30 s, measured 3,000/300 s at 10/s, alternating directions | All 3,000 eligible intents complete once, completed p95 ≤2 s, zero invariant violations, full failure distribution; EV-003 awaiting measurement |
| 014 | Isolate counter cost from hot Account/Treasury serialization; no speculative optimization | Minh/Tiến/Trung; collect waits/holds, throughput/latencies, retries, DB CPU/I/O/WAL; diagnostic 10/25/50/100 requests/s only within capacity | Measured material counter bottleneck plus replacement passing the same correctness tests; diagnostics do not change acceptance thresholds |
| 015 | Preserve module authority and atomicity; no isolated debit/credit or REQUIRES_NEW effects | All owners; consume only approved capabilities; implement concrete guards selected separately by TD-002 | Real provider/proxy/commit/audit/rollback/authorization evidence remains missing |
| 016 | Shared compile-time baseline prevents boundary drift without speculative frameworks | All; retain approved graph/signatures and ArchUnit; fakes only in tests while providers absent | New capability/direction/breaking API requires Tiến's prior amendment; green structural checks do not prove finance |

Detailed protocols and stable encoding obligations: [16 §§5–9](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02). Owners/tasks/readiness and BK-010 phases: [10](../02-requirements/10-product-backlog.md#member-implementation-readiness--2026-10-22). Runtime evidence gates: [22](../05-verification/22-test-strategy-and-acceptance.md).

## Review items and implementation evidence gates

| Item | Authority / implementation location | Remaining work (not routine design reapproval) |
|---|---|---|
| DOC-01/02/03 | [docs index](../README.md), [15](../03-design/15-system-architecture.md), [17](../03-design/17-api-contracts.md) | Actual runtime OpenAPI/framework compatibility, docs links and source synchronization |
| ARC-01/02 | TD-002/015, [16 §5](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02) | Real guards/proxies, checked-failure rollback, partial SQL/audit/commit failure tests |
| ARC-03 | TD-002, [16 §6](../03-design/16-domain-model-and-data-design.md#6-locking-and-isolation-discipline-arc-03) | Unsigned comparator equivalence, bounded retry/deadline, real PostgreSQL races |
| ARC-04 | TD-003, [16 §7](../03-design/16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04) | Canonical/advisory encoding golden vectors and [PostgreSQL proof tests](../05-verification/22-test-strategy-and-acceptance.md#postgresql-idempotency-proof-of-concept-arc-04) |
| ARC-05 | TD-004/014, [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05) | Late commit, cursor integrity, EV-003 and separate contention diagnostics |
| ARC-06 | TD-016, [module guide](../03-design/module-contracts-and-boundaries.md#automated-evidence-and-limits) | Current ArchUnit checks implemented; actual providers and runtime integration unverified |

## V1 Engineering Design Freeze — 2026-10-22

**Authority/provenance:** Tiến explicitly authorized the final V1 engineering design freeze and supplied the architecture reviewer's selected Section 5 baseline. Codex synchronizes that selection into this register and the existing documentation. This is not an invented meeting, approval by each member, or evidence that mechanisms work. TD-015/016 retain their prior ratification. TD-001..014 acquire only the scopes above under this later authorization.

**Verified repository context:** BK-003 PR #10 merged into `develop` at `8a04ca03505a140930b9bbc976dcdc1148db2693` on 2026-10-22 (01:03:49 +07). Live `develop`, local `develop` and `origin/develop` matched that revision during this audit. Pure module contracts and V1/V2 schema exist; business providers remain absent on that baseline. Unpushed teammate work is unknown. BK-010 Phase 1 exists separately at `44dd3ed34c262c4de23a670af43fa0727054f9a0` on `feat/BK-010-ledger-core`; it is not in this `develop` snapshot and is not a persisted or committed banking workflow.

**Effective team baseline:** BK-003 already applies. Once this documentation freeze merges into `develop`, every member/agent implements against these selected designs within their assigned/authorized implementation phase. No routine approval is needed again for selected algorithms. A failing implementation test normally requires a code fix. A proven incompatibility, security defect, financial correctness failure or necessary breaking API requires a scoped amendment; stop only the affected path while independent work continues. This documentation change does not authorize starting BK-010 Phase 2 or mark any financial story Done.

### Explicit exceptions and implementation findings

| Finding | Evidence / affected path | Required next action |
|---|---|---|
| FIX-001 — reference NUL | Feature-branch `ledger/internal/domain/FinancialTransaction.java` validates blank/64 characters but permits U+0000; PostgreSQL text/varchar rejects it | Later authorized Java change rejects `\u0000`, adds `rejectsNulInReference()`, reruns pure/architecture checks; no Java change in this freeze |
| Customer history/receipt metadata | `accounts/api/AccountQueries.java` has Customer-ID/number queries but no Account-ID-to-number/counterparty projection; `ledger/api/LedgerQueries.java` requires labels | Narrow read-capability amendment before affected BK-014/015 provider integration; preserve current signatures, no private Account SQL join or fabricated label |
| Admin read projections | Current AccountQueries has no Admin paginated listing; LedgerQueries is History-only and excludes GENESIS | BK-016/018 require concrete reviewed public read capabilities within the existing graph; do not grant Administration/Bootstrap a History-only capability |
| Reconciliation snapshot | No production all-balances/Treasury read API exists | AC-022/023 can use test-scoped consistent cross-table probes. A future production reader needs a reviewed capability; no production boundary bypass |
| Treasury identity in canonical allocation | AllocationPosting.Intent has recipient/amount/reason; Administration cannot read Treasury UUID | Canonicalize trusted source as fixed `TREASURY` marker in the singleton dataset; Ledger selects/verifies real Treasury. Resolve target UUID via existing AccountQueries for audit, then revalidate under Accounts locks |
| Financial invalid-input binding | Generic `VndAmountJson` decoding rejects invalid input before a typed positive Intent can exist | Financial adapter captures distinguishable raw typed invalid money before construction so accepted financial rejection can be durable; malformed/ambiguous/unpersistable JSON remains protocol failure; no global handler/YAML change |
| Database text / BCrypt input bounds | PostgreSQL disallows NUL; BCrypt supports at most 72 UTF-8 password bytes | Reject unsupported/unpersistable input before accepting intent/hash; never normalize/truncate. Existing OpenAPI is unchanged; any necessary external-contract extension is an explicit amendment |

Current deployment privilege limitation: `compose.yaml` supplies the same `POSTGRES_USER` to PostgreSQL initialization and the application DataSource, and `backend/src/main/resources/application.yml` supplies no separate Flyway credentials. Thus the local baseline does not establish runtime/migration role separation; a database owner/superuser can defeat append-only defenses. Retain V2 and verify least privileges during the separately authorized deployment implementation; no role/configuration is changed here.

These are concrete integration/validation obligations, not permission to introduce speculative provider Beans, new public interfaces or migrations. None demonstrates that the selected transaction/idempotency/counter protocol is unsafe; the affected projection integrations remain blocked until their capability amendments exist.

## Apache Fineract reference review — 2026-10-22

Examined actual source at **`71c39730e9178301b7ab949ba711238ce81fc66d`** in Apache Fineract. Structure includes `fineract-accounting`, `fineract-core`, `fineract-provider` and test modules. Source is engineering reference, never FinFast authority; no source/dependency/schema is copied. Tests were read, not executed.

| Actual source (pinned revision) | Observed pattern / FinFast comparison | Decision |
|---|---|---|
| [JournalEntry.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-accounting/src/main/java/org/apache/fineract/accounting/journalentry/domain/JournalEntry.java), [JournalEntryType.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-core/src/main/java/org/apache/fineract/accounting/journalentry/domain/JournalEntryType.java) | Mutable JPA GL entries associate an account, type, magnitude, shared transaction ID and reversal fields. GL DEBIT/CREDIT is a category whose balance effect depends on account class; FinFast delta is directly negative/positive on a participant Account | ADAPT explicit grouping/participants; REJECT GL hierarchy/reversals and treating GL debit as universally negative |
| [JournalEntryWritePlatformServiceImpl.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-provider/src/main/java/org/apache/fineract/accounting/journalentry/service/JournalEntryWritePlatformServiceImpl.java), [JournalEntryCommand.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-accounting/src/main/java/org/apache/fineract/accounting/journalentry/command/JournalEntryCommand.java) | `createJournalEntry`, `checkDebitAndCreditAmounts`, `validateBusinessRulesForJournalEntries` validate grouping and equal total magnitudes; framework transaction wraps writes. General journal cardinality/zero allowances differ | ADOPT validate before facts; ADAPT to exactly two opposite entries or one positive GENESIS, strictly positive amounts, FinFast-owned outer transaction |
| [Money.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-core/src/main/java/org/apache/fineract/organisation/monetary/domain/Money.java), [MoneyTest.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-core/src/test/java/org/apache/fineract/organisation/monetary/domain/MoneyTest.java) | Value-based arithmetic and tests; BigDecimal, currency/rounding and convenience conversions support a much larger domain | ADAPT exact-value validation/tests; REJECT multicurrency/rounding/double/null-as-zero patterns for integral VND |
| [AccountTransfersWritePlatformServiceImpl.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-provider/src/main/java/org/apache/fineract/portfolio/account/service/AccountTransfersWritePlatformServiceImpl.java), [AccountTransferTransactionTest.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-provider/src/test/java/org/apache/fineract/portfolio/account/domain/AccountTransferTransactionTest.java) | Transactional `create`/`transferFunds`, linked withdrawal/deposit; factory association tests. Loan/savings/interest/reversal workflows exceed V1 | ADAPT atomic grouping and association tests; REJECT extra products/strategy framework. These tests do not prove FinFast rollback/concurrency |
| [CommandSourceService.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-core/src/main/java/org/apache/fineract/commands/service/CommandSourceService.java), [SynchronousCommandProcessingService.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-core/src/main/java/org/apache/fineract/commands/service/SynchronousCommandProcessingService.java) | `saveInitial`/`saveResult` and `processCommandAndSaveResult` support durable commands/results; initial command may be saved separately, with under-processing/error lifecycle and different action/entity/key scope | ADAPT durable original result and duplicate handling; REJECT copying separate processing/error commits. FinFast full scope and final-only atomic protocol require their own proof |
| [JournalEntryWritePlatformServiceImplTest.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/fineract-provider/src/test/java/org/apache/fineract/accounting/journalentry/service/JournalEntryWritePlatformServiceImplTest.java) | Examined empty-reversal-list regression; not an end-to-end reconciliation, isolation or exactly-once proof | ADAPT focused regressions; no inferred universal correctness |

Additional actual integration sources: [IdempotencyTest.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/integration-tests/src/test/java/org/apache/fineract/integrationtests/IdempotencyTest.java) checks cached repeat success/error responses for business-step configuration; [AccountTransferTest.java](https://github.com/apache/fineract/blob/71c39730e9178301b7ab949ba711238ce81fc66d/integration-tests/src/test/java/org/apache/fineract/integrationtests/AccountTransferTest.java) checks paired savings balances, associated GL entries and negative/insufficient-balance cases. **ADAPT** assertions over both participant balances, grouped facts and original error replay; **REJECT** copying product-specific HTTP codes, GL account behavior or treating these read tests as a concurrent crash/idempotency proof. These sources were inspected, not run. No generic Fineract per-Account/supply reconciliation guarantee was established by this review.

Implementation constraint sources: PostgreSQL 16 [locking](https://www.postgresql.org/docs/16/explicit-locking.html), [READ COMMITTED statement snapshots](https://www.postgresql.org/docs/16/transaction-iso.html), [character bounds/NUL](https://www.postgresql.org/docs/16/datatype-character.html) and [JSONB storage](https://www.postgresql.org/docs/16/datatype-json.html); Spring [transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html), [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) and [BCrypt UTF-8/72-byte validation source](https://github.com/spring-projects/spring-security/blob/7.0.0/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java). These support constraints and test obligations; they are not proof that FinFast already implements them or a new dependency/version selection.

[FINERACT-2485](https://issues.apache.org/jira/browse/FINERACT-2485) was **In Progress / Unresolved** when inspected on 2026-10-22; its proposed hardening is opt-in/phased and calls for network-failure/retry tests. It is not proof that all Fineract operations provide FinFast's durable-idempotency guarantees. No inaccessible source or unexecuted Fineract test is claimed as verified. FinFast reconciliation remains its own signed-balance/supply assertions, not a copied GL trial-balance feature.

## External evidence tasks (status inherited from approved baseline)

- **EV-001 — PENDING VERIFICATION:** actual course rubric, mandated deployment/load/acceptance evidence. Reference topology approval does not invent course requirements.
- **EV-002 — PENDING VERIFICATION:** actual provider, region/SKU/budget, compute/storage/TLS, persistent private DB, secrets and backup feasibility. No provider selected.
- **EV-003 — AWAITING MEASUREMENT:** exact PRD §9 3,000-intent/300 s benchmark at 10/s, p95 ≤2 s, all eligible intents completed once and zero invariant violations. Method is approved; result is not.

## Design-review change protocol

1. Follow this selected V1 baseline and canonical UC/BR/AC/CONS identifiers. No routine reapproval of selected mechanisms after the freeze merges.
2. Preserve work and report exact signatures/source/status when a real incompatibility, security issue, financial failure or breaking change appears. Isolate the affected path; continue independent work.
3. Tiến approves cross-module capability/edge/breaking amendments; project owner explicitly decides business-policy changes. Update affected source, canonical inventory, governance and tests together only in an authorized implementation change.
4. Keep implementation and evidence separate. Record commands, revision, environment, observations and limitations; no financial/cloud/performance PASS without actual evidence.


> **Historical snapshot — original BK-003 final-review record, before PR #10 merged.** The original text below is retained for provenance. Its “merge pending”, mechanism-review and remaining-decisions statements describe that earlier review only. The dated freeze above supersedes them for current design status; it does not rewrite the original approval. Historical local test results belong to that review, not this documentation task.

## BK-003 approval record and remaining authority

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

## 2026-10-22 — V1 Product, UX and REST Contract Freeze

**Authority and provenance:** Tiến (Tech Lead) explicitly authorizes and approves the existing V1 implementation-facing product/delivery requirements, derived functional specification, UX/E2E design and external REST contract in the supplied *FINAL V1 Product, UX & REST API Contract Approval* instruction. Codex records/synchronizes that authorization; it does not act as an additional approving stakeholder. No separate project-owner, member or course-evaluator signature is fabricated. Future business-policy changes and final release acceptance retain their distinct project-owner authority.

This is a later, additive approval. PRD 09 was originally created as a draft on 2026-10-17; the earlier engineering freeze approved mechanisms and the benchmark method without approving all product/browser elaborations. This record closes those remaining implementation-baseline approval gaps without retrospectively changing that history, any TD decision or banking policy.

| Artifact / evidence | Final disposition | Implementation / verification boundary |
|---|---|---|
| Discovery 01–08 | Preserve previously approved | Canonical business meaning and IDs unchanged |
| [PRD 09](../02-requirements/09-product-requirements.md) | APPROVED V1 REQUIREMENTS BASELINE | FR-001..013, UX-001..005, NFR-001..006 and existing P0 scope; implementation not complete |
| [Functional Specifications 12](../02-requirements/12-functional-specifications.md) | APPROVED DERIVED IMPLEMENTATION SPECIFICATION | Existing behaviors/mappings approved; runtime acceptance not established |
| [UX 14](../03-design/14-ux-user-flows-and-e2e.md) | APPROVED V1 UX/E2E DESIGN BASELINE | Information architecture, S01–S10, required states, WEB-01..08 and existing viewport/zoom/keyboard checks approved; execution not verified |
| [API 17](../03-design/17-api-contracts.md) | APPROVED V1 REST CONTRACT | Exact operation-specific YAML mapping; endpoint implementation/conformance pending |
| [OpenAPI YAML](../03-design/contracts/openapi-v1.yaml) | APPROVED FROZEN V1 EXTERNAL API | Only info title/version/description approval metadata changes; paths, methods, fields, schemas, statuses and security unchanged |
| TD-001–016 | Preserve previously selected/approved decisions | No algorithm, invariant, ownership or evidence gate changed |
| BK-003 Java contracts | Preserve approved/frozen | Pure source signatures and capability permissions unchanged; providers/integration still required |
| EV-001 | PENDING VERIFICATION | Actual course rubric remains unverified |
| EV-002 | PENDING VERIFICATION | Actual cloud provider/resources/budget/persistence/TLS remain unselected/unverified |
| EV-003 | AWAITING MEASUREMENT | Exact approved PRD §9 method; no achieved-performance claim |
| Runtime acceptance | NOT YET VERIFIED | No acceptance/security/concurrency/browser test passes are created by this approval |
| Final release acceptance | NOT YET GRANTED | Separate project-owner release decision still required |

**Approved elaborations:** 360×800, 768×1024 and 1440×900 CSS pixels plus desktop 200% zoom and keyboard checks are now test specifications, not WCAG certification or passing browser evidence. ASCII wireframes remain low-fidelity references; Sĩ may choose presentation/components without routine architecture reapproval while preserving required capabilities/states, accessibility, safe recovery and frozen REST semantics. No frontend framework/dependency is mandated.

**Benchmark unchanged:** PRD §9 retains 100 Customers/50 pairs, existing GENESIS/allocation fixture, 300 warm-up intents/30 s, 3,000 measured intents/300 s at 10/s, p95 ≤2 s and zero financial-invariant violations. All eligible intents must complete once and failures/non-final/recovery outcomes must be reported honestly. Method approval does not close EV-003.

**REST authority:** the existing YAML is the sole external HTTP contract; BK-003 is the separate internal Java contract. Operation response sets were inspected: command financial 422 is durable REJECTED, generic validation 422 is not a financial outcome, permitted resolve 200 can replay a rejection, and applicable 202/503 remain non-final. Info version `0.1.0` removes the historical `-proposal` status suffix only; `/api/v1` and all operation semantics remain fixed. API-ERR-01 below is explicit, not a silently adopted amendment.

### Retained exceptions, owners and affected paths

| Exception / obligation | Owner / coordination | Impact and required disposition |
|---|---|---|
| Account metadata for History/Receipts | Hải (Accounts/History), Tiến (Ledger) | BK-014/015 labels and owner-bound cursor provider integration need a narrow reviewed read capability; no private Account join, duplicate API or invented labels |
| Admin read projections | Trung with Tiến/Accounts owner Hải | BK-016/018 integration needs approved Account listing/financial projections; no bypass of History-only LedgerQueries or changed ownership |
| Production reconciliation access | Tiến with Hải; Minh verification | Existing APIs lack an all-Accounts/Treasury snapshot. Test-scoped consistent probes can verify AC-022/023; a production read capability needs a scoped amendment |
| BK-010 FIX-001 | Tiến | Later authorized Java fix rejects reference U+0000 and adds `rejectsNulInReference()`; Phase 1 unchanged here and Phase 2 not started |
| API-ERR-01 — framework protocol/error mapping | Tiến + affected backend owners, with Sĩ | Current common handler returns 400/500; no frozen YAML operation lists those statuses. Before affected adapter conformance, review the smallest per-operation generic ErrorResponse amendment or prove a compatible mapping. No semantic YAML change authorized/performed here; no false financial rejection |
| Missing real provider/transaction/concurrency/recovery evidence | Respective module owners; Minh BK-022 | Test fakes permit independent code; real PostgreSQL/proxy/failure/restart evidence required before financial acceptance |
| Security/browser evidence | Hưng/Sĩ with read/API owners | Sessions/CSRF/authorization/BCrypt/cursor and approved browser journeys remain unverified |
| External course/cloud/deployment evidence | Hưng EV-001; Sĩ EV-002; Trung deployment | Verify rubric/resources, private durable DB/TLS/secrets/privileges/backups/restart before deployment acceptance |
| Actual performance | Minh BK-023 with Trung/Sĩ | Execute unchanged PRD §9 and separate diagnostics; EV-003 remains awaiting measurement |

### Contributor implementation and amendment policy

Once this documentation baseline merges into `develop`, all six members may implement their assigned, authorized tasks/phases without another routine product/UX/API/architecture approval: Tiến Ledger/Administration/architecture/release; Minh Idempotency/Transfers/concurrency/recovery; Hưng Identity/Customer/authentication/security; Hải Accounts/History/Receipts/Account status behavior; Trung Bootstrap/GENESIS orchestration/Admin reads/deployment; Sĩ frontend/browser UX/E2E. Preserve the exact backlog assignment and module ownership; Hải's BK-017 assignment does not transfer Administration outer-transaction/audit ownership from Tiến.

Follow approved Java and REST contracts, frozen invariants and normal code review/CI; add meaningful tests and report actual failures. Do not invent a provider/API because another member is unfinished, select conflicting algorithms or bypass missing capabilities. This documentation task implements no banking provider or BK-010 phase.

A scoped amendment is required only for an approved business-rule conflict, financial/security defect, breaking public Java/REST change, new cross-module capability/dependency, necessary incompatible schema change or proven inadequacy in the frozen design. Ordinary implementation defects are fixed/tested within the baseline. Stop only the affected integration while independent work continues. Future business-policy changes require the separate project-owner decision; final release acceptance is not implied.

## Design freeze merge activation — 2026-10-22

[PR #12](https://github.com/nvawntien/finfast/pull/12) merged the engineering and
Product/UX/REST documentation baselines into `develop` at
`291fd5ae47250182cf2c291557dd5345f55d6282`. The conditional merge wording in the
original dated approval records above is historical; the selected implementation
baseline is active. This records integration status, not a new design approval,
authorization to start another BK-010 phase, closure of the retained exceptions,
runtime verification or final release acceptance.

## Documentation audit coverage — 2026-10-22

**Historical engineering-review coverage:** this table records the earlier engineering-only synchronization. Its reference to then-draft product/UX scope is historical; the consolidated approval in this register now freezes those existing artifacts. The subsequent follow-up recorded Product/UX/REST approval; this inventory describes the earlier engineering review only.

The freeze review covered all **29 tracked Markdown files**, the existing OpenAPI, checked-in build/configuration, Java API/capability sources, tests and Flyway V1/V2; source/configuration is implementation evidence, not permission to edit it. There are no checked-in frontend implementation documents/stack to infer. Historical local test records retain their original date/scope.

| Area | Reviewed files | Synchronization outcome |
|---|---|---|
| Entry/contributor routing (3) | Root README, AGENTS, docs index | Current BK-003 merge status, scoped freeze, member workflow and evidence boundaries |
| Approved discovery (8) | 01 vision, 02 glossary, 03 actors, 04 use cases, 05 processes, 06 rules, 07 decisions, 08 handoff | 02–05 unchanged; canonical UC/BR/AC/CONS and historical A/OQ/DEC unchanged. 01 restores the omitted GENESIS term and removes an unsupported parenthetical claim of verified course requirements; 06–08 receive current technical routing only |
| Requirements/delivery (4) | 09 PRD, 10 backlog, 11 traceability, 12 functional specifications | Scoped benchmark method approval, exact six-person assignment/readiness, BK-010 phases/FIX-001 and design-versus-execution traceability; no workload/threshold or canonical requirement change |
| Design/contracts (7) | 14 UX, 15 architecture, 16 data, 17 API, module guide, shared-error-money guide, logging guide | Selected mechanisms, unchanged Java/OpenAPI baseline, security/transaction/recovery guidance and explicit missing read-capability integrations; UX/product draft scope retained |
| Verification/governance (3) | 22 strategy, 23 release checklist, 25 decision register | Dated freeze/provenance, TD scopes/rationale/alternatives/owners/revisit criteria, Fineract evidence and required unexecuted runtime tests; historical BK-003 record retained |
| Persistence guide (1) | Backend migration README | Existing SQL guarantees versus cross-row/provider obligations, selected counter/advisory protocol and database text representability; migrations unchanged |
| Observability (3) | README, architecture-review, metrics-and-alerts | Existing local evidence/configuration retained; current pure API source status, topology approval versus deployment/benchmark proof clarified |

This register records the approval authority; the inventory above records historical coverage only. See its [explicit exceptions](#explicit-exceptions-and-implementation-findings) before affected History/Admin integrations. No missing standalone ADR/runbook is claimed to exist, and no acceptance checkbox is completed by this audit.

## BK-010 FIX-001 and Phase 2 implementation evidence — 2026-10-22

Tiến separately authorized FIX-001 and Phase 2 implementation after the documentation freeze. This records execution against existing TD-002/004/015/016, not a new design decision or an amendment to any approved Java/REST contract, dependency edge or V1/V2 migration.

**FIX-001 — IMPLEMENTED AND VERIFIED:** the immutable Ledger domain now rejects reference U+0000; all three construction paths have NUL regressions and exact valid/64-code-point boundary coverage. The pre-Phase-2 pure/architecture/contract checkpoint passed 130 tests. The earlier exception rows record the finding at freeze time; FIX-001 is no longer outstanding in this working tree.

**Phase 2 — IMPLEMENTED AND VERIFIED, infrastructure scope only:** Ledger-owned JDBC storage/reconstruction and transactional singleton posting-position allocation join an existing outer transaction. Actual Spring proxies, partial-write rollback, counter bounds, lock waiting, commit serialization and rollback reuse were verified on isolated PostgreSQL 16.15. The completed full Maven `verify` passed 568 tests with zero failures/errors/skips. See [test strategy execution evidence](../05-verification/22-test-strategy-and-acceptance.md#bk-010-fix-001-and-phase-2-execution--2026-10-22) for exact commands, initial failures/fixes and evidence limits, and [data design implementation notes](../03-design/16-domain-model-and-data-design.md#11-phase-2-internal-jdbc-infrastructure) for adapter responsibilities and future Spring wiring.

These primitives are not public posting providers or commit acknowledgements. No Accounts balances, Idempotency outcomes, Admin audit or production GENESIS workflow were implemented. Phase 3–6 remain NOT STARTED. Tiến must coordinate future GenesisPosting wiring with Hải's real Treasury/Accounts capability and Trung's Bootstrap outer setup; Minh's durable outcomes/Transfers and Administration success audit remain later integration obligations. Missing History/Receipt metadata, Admin projections, production reconciliation access, API-ERR-01 and external/security/deployment/performance evidence retain their existing owners and impacts. No full AC/CONS, BK-010 completion or final release acceptance is granted by this implementation record.
