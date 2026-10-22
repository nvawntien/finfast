# 06 — Business Rules and Domain Invariants

> **Stage:** Phase 1 — Domain Discovery\
> **Status:** Approved V1 business rules — project owner baseline\
> **Version:** 1.0 | **Last updated:** 2026-10-16

## 1. Purpose and rule conventions

A **business rule** states a behavior or constraint FinFast V1 Core Banking Foundation must preserve, irrespective of its framework or deployment architecture. Each rule has a stable ID so the team can link it to use cases, PRD requirements, tests, and pull requests.

- **P0:** Mandatory for V1.
- **Invariant:** Condition that must never be violated in persisted financial state.
- **Reject:** Do not perform the requested money movement; return a meaningful error without leaking restricted details.
- **Audit:** Record an authorized or security-relevant action in a separate log where indicated.

These are **project-owner-approved FinFast V1 rules for simulated funds**, not claims that all banks operate this way. Transfer Amount is strictly positive; a Signed Ledger Entry Delta may be positive or negative. A Financial Transaction is committed, whereas a Transfer Request can be rejected. Architecture choices are recorded separately in [07](./07-assumptions-and-questions.md).

## 2. Identity and access rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-ID-01** | Registration collects display name, email, and password only. Email is unique after case normalization; passwords use secure adaptive one-way hashing, never plaintext or reversible storage. | P0 | Case-variant duplicate rejected; only approved fields collected; password storage inspected |
| **BR-ID-02** | Customer self-registration yields `CUSTOMER` privileges only. Admin identities are separately provisioned through protected bootstrap/administrative procedures. Protected operations enforce authentication, role, and resource authorization server-side. Logout invalidates the authenticated session; account freeze does not disable authentication. | P0 | Unauthorized role/resource access and reuse of a terminated session are denied |

## 3. Account rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-ACC-01** | Every successfully registered customer has exactly one VND customer account, created initially with balance `0`; account numbers are unique. | P0 | Registration creates one and only one zero-balance account |
| **BR-ACC-02** | Customer accounts have `ACTIVE` or `FROZEN` status. FROZEN blocks **both inbound and outbound money movements**, including admin allocations, but permits read-only access by the owner. | P0 | Every transfer involving frozen account rejected; history still visible |
| **BR-ACC-03** | No account, including internal treasury, may have a negative persisted balance or spend more than its available balance. | P0 invariant | Concurrent spending cannot take balance below zero |
| **BR-ACC-04** | All monetary values use VND as non-fractional integers; floating-point rounding may not determine Ledger Entry Deltas or balances. Transfer Amounts are positive, balances are non-negative, and Ledger Entry Deltas are signed. | P0 | Fractional amounts rejected; source delta remains negative for a valid positive Transfer Amount |
| **BR-ACC-05** | V1 supports `CUSTOMER` and `TREASURY` account types. Customer Accounts are owned by registered Customers. One system-owned Treasury Account per dataset is used exclusively for authorized demo funding after initialization; Customers cannot directly access or manage it. | P0 | Customer cannot read treasury details or use treasury as transfer source/recipient |
| **BR-ACC-06** | Every Account has a unique, system-generated, immutable Account Number, separate from User IDs and Customer IDs. Account Numbers must never be reassigned to different Accounts. | P0 | Number remains unchanged across status changes/restarts and is never reused |

## 4. Internal transfer rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-TRF-01** | Transfer amount must be a strictly positive whole number of VND. | P0 | Zero/negative/fractional amount rejected |
| **BR-TRF-02** | Initiator must be authenticated and must own the customer source account. | P0 | Forged source account ID rejected |
| **BR-TRF-03** | Recipient must be an existing **different** customer account; both accounts must be ACTIVE and have the same currency. | P0 | Self-transfer, unknown or frozen recipient rejected |
| **BR-TRF-04** | A successful transfer changes source balance, destination balance, Financial Transaction, Ledger Entries, and durable idempotency outcome in **one atomic consistency boundary**. Demo funding also includes the required success Admin Audit Event in that boundary. | P0 invariant | Failure before commit yields no partial financial records, success audit, or completed outcome |
| **BR-TRF-05** | Idempotency scope is **initiator + operation type + Idempotency Key**. Identical payload in that scope resolves to the same recorded final outcome; changed payload conflicts. Final business rejections replay even if balances/account states later change; a new business attempt needs a new key. Technical failures must not be cached as permanent business rejections. Outcomes persist for the V1 dataset lifetime, with no automatic key reuse in the same scope. | P0 invariant | Completed and rejected outcomes replay across retries/restarts; distinct scopes do not collide |
| **BR-TRF-06** | Concurrent money movements must coordinate funds and Account status so committed results are equivalent to a valid serial execution, including races with freeze/unfreeze. Status and funds are revalidated in the commit consistency boundary. | P0 invariant | Excessive concurrent debits cannot both succeed; an effective freeze blocks later postings |
| **BR-TRF-07** | For a committed internal Customer transfer of amount A, the source Customer Account receives delta −A and the destination Customer Account receives delta +A, linked to one Transaction Reference. The exactly-two-entry and zero-sum invariant is defined by BR-LED-04. | P0 invariant | Correct source/destination attribution, same reference, and zero net delta |
| **BR-TRF-08** | `COMPLETED` may only be reported after the database commit succeeds. A lost response **after** commit does not undo a transfer; the client must use idempotent retry/lookup to resolve the outcome. | P0 | Simulated lost response yields replayed committed reference |
| **BR-TRF-09** | Business-invalid transfer requests result in a deterministic rejection with **no balance or successful-ledger changes**; a completed request's ledger entries cannot be edited/deleted through V1 operations. | P0 | Insufficient funds returns error and leaves posted balance unchanged |
| **BR-TRF-10** | Concurrent requests with the same idempotency scope and identical intent never produce multiple committed Financial Transactions. A duplicate may wait for the original result or receive a non-final processing response and retry safely. | P0 invariant | Concurrent duplicates eventually resolve to one final outcome and at most one posting |
| **BR-TRF-11** | An accepted transfer intent must ultimately resolve to a committed movement or a final business rejection. Timeout, connection failure, or an unknown response does not prove failure. Clients resolve uncertainty using the original Idempotency Key or an authorized result lookup. | P0 | Recovery after transient failure resolves the original intent, without creating a new one |
| **BR-TRF-12** | Amounts, signed deltas, and resulting balances must remain within the supported safe numeric range. Invalid, overflowing, or unsupported values are rejected without financial effects. No independent commercial transfer cap is required beyond available funds and safe numeric bounds. | P0 invariant | Input and resulting-balance overflow rejected without wraparound, truncation, or partial posting |

**Processing policy:** synchronous internal transfers only; no self-transfer, overdraft, fee, FX, or interbank payment. Both Customer Accounts must be ACTIVE. `COMPLETED` is reported only after successful commit. No complex persisted transfer state machine is required; a non-final processing response is not an additional Account state. Technical failures remain recoverable, not final business outcomes.

### Transfer invariant examples

| Operation | Source delta | Destination delta | Net movement |
|---|---:|---:|---:|
| Alice → Bob (200,000 VND) | −200,000 | +200,000 | 0 |
| Treasury → Alice (500,000 VND) | −500,000 | +500,000 | 0 |
| Rejected transfer | 0 | 0 | 0 |

**Important:** Rejection deltas above are illustrative zero effects, not zero-valued posted entries. **GENESIS** establishes the finite initial simulated supply as a special transaction with one positive treasury entry; it is not an ordinary zero-sum movement.

## 5. Ledger and history rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-LED-01** | Committed ledger entries and their transaction references are append-only; no feature may silently overwrite or delete historical financial movements. | P0 | No endpoint or service path edits a committed entry |
| **BR-LED-02** | Each Account balance, including any materialized value, must reconcile to its committed signed entries from a zero pre-initialization baseline. Include the treasury GENESIS entry exactly once; do not also add it as a separate opening balance. | P0 invariant | Recompute each balance, including treasury, from committed deltas with no double-counting |
| **BR-LED-03** | Every committed Financial Transaction has a unique, immutable Transaction Reference identifying its type, currency, positive amount, timestamp, participating Accounts where applicable, and associated Ledger Entries. This includes GENESIS. | P0 | Reference retrieves stable complete transaction facts and linked entries |
| **BR-LED-04** | Every successful internal transfer or demo allocation creates exactly two equal-and-opposite Signed Ledger Entry Deltas whose sum is zero. All corresponding financial changes commit atomically. This rule excludes GENESIS. | P0 invariant | Two entries per ordinary transaction; zero sum; all-or-nothing effects |
| **BR-LED-05** | Each V1 dataset has one controlled, auditable GENESIS initialization establishing finite simulated supply in the Treasury Account. It is a special Ledger Transaction with one positive treasury entry, stable reference, amount, currency, timestamp, and verifiable record. Restart must not repeat GENESIS. Afterwards, Treasury balance plus all Customer balances equals the initial supply; V1 has no other creation/destruction operation. | P0 invariant | Repeated/concurrent initialization and restart do not create extra supply; total balances equal GENESIS amount |
| **BR-LED-06** | Business-rejected financial requests create no posted Financial Transactions or Ledger Entries and do not modify balances. Request outcomes may still be recorded for idempotency and operational auditing; required final rejection replay follows BR-TRF-05 and BR-ADM-04. | P0 | Rejection has a replayable outcome but no financial posting |
| **BR-HIS-01** | Customer-facing history represents committed movements affecting that account with reference, timestamp, type, direction, amount, and meaningful counterparty label. | P0 | Alice sees outbound and Bob inbound record for same movement |
| **BR-HIS-02** | Customer may read only their own account and transfers in which they are a participant. Admin may inspect according to admin authorization. | P0 | Attempt to read unrelated account/reference is denied |
| **BR-HIS-03** | Customer history contains only committed movements involving owned Accounts, newest first with deterministic ordering and stable Transaction References. Stable pagination is mandatory: default 20, maximum 100 records per page. Financial history is retained throughout the V1 dataset lifetime; advanced filtering is P1. | P0 | Equal timestamps and new commits do not destabilize continuation; no duplicate/omitted eligible records; page limits enforced |
| **BR-HIS-04** | Rejected requests must not appear as successful or committed movements in Customer history. Financial Transactions, request/idempotency outcomes, and Admin/security Audit Events serve distinct purposes. Authorized Admin operational views may show audit records separately. | P0 | Rejected attempts absent from financial history; authorized operational evidence remains separate |

## 6. Administration and simulation rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-ADM-01** | Demo allocation requires authenticated admin, positive whole-VND amount, ACTIVE target account, and non-empty operator reason. It is posted as a treasury-to-customer money movement, not a direct balance edit. | P0 | Funding created with two entries and recorded reason; direct override unavailable |
| **BR-ADM-02** | One finite VND Treasury Account per dataset is initialized by GENESIS. Initial supply defaults to **100,000,000 VND**, configurable before first initialization only; it cannot be changed in an existing dataset through normal operations. Allocations cannot exceed treasury available balance. No treasury reset/replenishment or money-minting API exists in V1; demo reset requires separately controlled dataset recreation. | P0 | Treasury never negative; restart/configuration change does not reseed or alter established supply |
| **BR-ADM-03** | Successful funding and Account status changes produce durable Admin Audit Events containing administrator identity, action, target, non-empty reason, timestamp, outcome, and relevant Transaction Reference or before/after Account state. State changes and required success audit commit atomically. Rejected/unauthorized Admin requests should produce appropriate security/operational evidence without financial postings. Freeze/unfreeze requires a non-empty reason; setting an existing status returns `UNCHANGED`, creates no financial movement, and may record the attempt. | P0 | Missing reason rejected; audit persistence failure aborts state change/funding; unchanged request does not post |
| **BR-ADM-04** | Demo funding follows BR-TRF-05 and BR-TRF-10 idempotency guarantees: identical concurrent/repeated requests cannot allocate twice, changed payload in the same scope conflicts, and final business rejections replay consistently. Successful funding, balances, entries, required success audit, and final idempotency outcome commit together. | P0 invariant | Sequential/concurrent duplicates debit treasury once; audit/result failure leaves no partial operation |
| **BR-ADM-05** | Only explicitly authorized operations may move funds from the system-owned Treasury Account. Customers cannot directly access or transact from it. Administrators cannot directly overwrite balances or mint additional simulated funds through normal operations. | P0 | Unauthorized treasury access, arbitrary balance edits, and normal reset/mint attempts denied |
| **BR-ADM-06** | Admins inspect only information needed for authorized V1 operations. Account listing may expose account number, customer identifier, limited Customer display information, status, and balance. Transaction/audit details require authorized Admin access; passwords and unnecessary sensitive data must never be exposed. | P0 | Listing/details contain only permitted metadata; no credential exposure |
| **BR-SIM-01** | V1 does not represent real money, external transfers, customer cash-out, lending, FX, fees, interest, or overdraft. | P0 scope | No such UI endpoints or business flows are supported |
| **BR-SIM-02** | Demo users/data must be synthetic. The app does not ask for real banking credentials, payment credentials, or government IDs. | P0 scope | Registration/demo inspection confirms limited data collection |

## 7. High-risk consistency scenarios

CONS identifiers are risk views of the canonical [AC scenarios](./04-use-cases.md#4-acceptance-scenario-matrix), not duplicate test requirements. Existing CONS-01–CONS-08 are preserved.

| ID | Scenario | Rule(s) | Required outcome / acceptance |
|---|---|---|---|
| CONS-01 | Alice has 100 VND; sends 80 twice concurrently to different recipients | BR-ACC-03, BR-TRF-06 | At most one commits; AC-007 |
| CONS-02 | Ledger posting fails before commit | BR-TRF-04, BR-TRF-08 | No partial financial effects; AC-010 |
| CONS-03 | Commit succeeds but response is lost | BR-TRF-05, BR-TRF-08, BR-TRF-11 | Same key resolves original result; AC-011 |
| CONS-04 | Same scoped key used with changed amount/recipient | BR-TRF-05 | Conflict; no new movement; AC-006 |
| CONS-05 | Admin double-submits funding, sequentially or concurrently | BR-ADM-04 | Treasury debited once; AC-013 |
| CONS-06 | Freeze races with transfer or funding | BR-ACC-02, BR-TRF-06 | Posting before effective freeze may commit; posting after it rejects; AC-018 |
| CONS-07 | Restart with existing treasury and request outcomes | BR-ADM-02, BR-LED-05, BR-TRF-05 | No repeated GENESIS or forgotten final outcome; AC-021 |
| CONS-08 | User requests unrelated transaction details | BR-HIS-02 | Deny without disclosure; AC-024 |
| CONS-09 | Identical concurrent Transfer Requests | BR-TRF-10 | One final outcome, at most one posting; AC-014 |
| CONS-10 | Retry final rejection after funds/status change | BR-TRF-05, BR-LED-06 | Replay rejection; new attempt needs new key; AC-015 |
| CONS-11 | Invalid amount or numeric overflow | BR-ACC-04, BR-TRF-12 | No financial effects or numeric wraparound; AC-016 |
| CONS-12 | Simultaneous opposite-direction transfers | BR-TRF-06, BR-TRF-11 | Valid serial result and eventual final outcomes; AC-017 |
| CONS-13 | Required Admin success audit fails | BR-ADM-03, BR-ADM-04 | No unaudited state change or funding; AC-019 |
| CONS-14 | Repeated/concurrent or interrupted GENESIS | BR-LED-05 | Exactly one committed initialization; AC-020 |
| CONS-15 | Recompute balances from signed entries | BR-LED-02 | Every balance matches; GENESIS counted once; AC-022 |
| CONS-16 | Mixed transfers, funding, rejections, and retries | BR-LED-04, BR-LED-05 | Initial supply preserved; AC-023 |
| CONS-17 | Equal-time history records and concurrent new commits | BR-HIS-03 | Stable pagination and permitted visibility; AC-025 |
| CONS-18 | Transient failure followed by same-key retry | BR-TRF-05, BR-TRF-11 | Failure not cached as permanent business rejection; AC-033 |
| CONS-19 | Same key value in distinct scopes or late retry | BR-TRF-05, BR-ADM-04 | Scope isolation and dataset-lifetime retention; AC-034 |

## 8. Rule ownership and evidence

Decision authority for all approved rules: **project owner, 2026-10-16**. Suggested future review responsibilities below are not assigned people or evidence of team approval:

- `BR-ID-*`: Identity/Authorization research owner.
- `BR-ACC-*`: Account domain owner.
- `BR-TRF-*` and `BR-LED-*`: Transfer/Ledger domain owner; require peer review.
- `BR-HIS-*`: Customer experience owner.
- `BR-ADM-*`: Admin/Operations domain owner.

Each P0 rule is mapped below to an existing use case and a canonical acceptance scenario. These are specifications for later verification, **not claims that application tests have run**. Authorization, funds safety, and atomicity require negative-path and concurrency coverage as applicable.

### Mandatory rule coverage

GENESIS is a controlled operational prerequisite of UC-ADM-02, with its recorded result inspectable through UC-ADM-04; this does not add a normal Admin initialization/reset function. Reconciliation is acceptance verification of financial records, not an added accounting-report product.

| Rule | Use case(s) | Acceptance scenario(s) |
|---|---|---|
| BR-ID-01 | UC-AUTH-01, UC-AUTH-02 | AC-027, AC-028 |
| BR-ID-02 | UC-AUTH-01, UC-AUTH-02, UC-ADM-01 | AC-024, AC-027, AC-028 |
| BR-ACC-01 | UC-AUTH-01 | AC-001, AC-027 |
| BR-ACC-02 | UC-ACC-01, UC-ADM-03, UC-TRF-01, UC-ADM-02 | AC-009, AC-018, AC-026 |
| BR-ACC-03 | UC-TRF-01, UC-ADM-02 | AC-004, AC-007, AC-032 |
| BR-ACC-04 | UC-TRF-01, UC-ADM-02 | AC-003, AC-016 |
| BR-ACC-05 | UC-AUTH-01, UC-ADM-02, UC-ACC-01 | AC-001, AC-020, AC-024 |
| BR-ACC-06 | UC-AUTH-01, UC-ACC-01 | AC-035 |
| BR-TRF-01 | UC-TRF-01 | AC-016 |
| BR-TRF-02 | UC-TRF-01 | AC-024 |
| BR-TRF-03 | UC-TRF-01 | AC-018, AC-026, AC-036 |
| BR-TRF-04 | UC-TRF-01, UC-ADM-02 | AC-010, AC-019, AC-033 |
| BR-TRF-05 | UC-TRF-01, UC-ADM-02 | AC-005, AC-006, AC-015, AC-033, AC-034 |
| BR-TRF-06 | UC-TRF-01, UC-ADM-02, UC-ADM-03 | AC-007, AC-017, AC-018 |
| BR-TRF-07 | UC-TRF-01 | AC-003 |
| BR-TRF-08 | UC-TRF-01 | AC-010, AC-011 |
| BR-TRF-09 | UC-TRF-01 | AC-004, AC-015, AC-031 |
| BR-TRF-10 | UC-TRF-01, UC-ADM-02 | AC-013, AC-014 |
| BR-TRF-11 | UC-TRF-01, UC-ADM-02 | AC-011, AC-017, AC-033 |
| BR-TRF-12 | UC-TRF-01, UC-ADM-02 | AC-016 |
| BR-LED-01 | UC-TRF-02, UC-ADM-04 | AC-031 |
| BR-LED-02 | UC-ACC-01, UC-ADM-04 | AC-022 |
| BR-LED-03 | UC-TRF-02, UC-ADM-04 | AC-020, AC-031 |
| BR-LED-04 | UC-TRF-01, UC-ADM-02 | AC-003, AC-008, AC-010 |
| BR-LED-05 | UC-ADM-02, UC-ADM-04 | AC-020, AC-021, AC-023 |
| BR-LED-06 | UC-TRF-01, UC-ADM-02 | AC-004, AC-015, AC-032 |
| BR-HIS-01 | UC-ACC-02, UC-TRF-02 | AC-012, AC-031 |
| BR-HIS-02 | UC-ACC-01, UC-ACC-02, UC-TRF-02, UC-ADM-04 | AC-002, AC-024 |
| BR-HIS-03 | UC-ACC-02 | AC-021, AC-025 |
| BR-HIS-04 | UC-ACC-02, UC-ADM-04 | AC-015, AC-025, AC-030 |
| BR-ADM-01 | UC-ADM-02 | AC-008, AC-026, AC-032 |
| BR-ADM-02 | UC-ADM-02, UC-ADM-04 | AC-020, AC-021, AC-032 |
| BR-ADM-03 | UC-ADM-02, UC-ADM-03, UC-ADM-04 | AC-008, AC-019, AC-029, AC-030 |
| BR-ADM-04 | UC-ADM-02 | AC-006, AC-013, AC-015, AC-019, AC-034 |
| BR-ADM-05 | UC-ADM-02, UC-ADM-03 | AC-024, AC-032 |
| BR-ADM-06 | UC-ADM-01, UC-ADM-04 | AC-024, AC-030 |
| BR-SIM-01 | UC-TRF-01, UC-ADM-02 | AC-037 |
| BR-SIM-02 | UC-AUTH-01, UC-ADM-01 | AC-027, AC-037 |

## 9. Engineering follow-through (not business rules)

The approved direction is Java/Spring Boot, PostgreSQL, a domain-oriented Modular Monolith, channel-independent Core, and secure server-managed browser sessions with protected HttpOnly cookies over HTTPS. No Kafka, Redis, Kubernetes, or microservices are mandatory. These are architecture constraints, not BR entries.

The following were pending when discovery was approved on 2026-10-16. The later [2026-10-22 engineering freeze](../6-governance/25-design-decision-register.md#v1-engineering-design-freeze--2026-10-22) now selects numeric, locking, balance, session and history mechanisms; implementation/evidence remain separate, and provider/resources remain externally gated. This dated routing update does not amend any BR/CONS or claim an original discovery approval of those mechanisms:

- PostgreSQL lock strategy, isolation level, and whether balances are computed or materialized.
- Exact safe numeric representation/bounds, credential/session configuration, and history continuation/tie-break mechanism.
- Specific cloud provider/resources and externally required deployment evidence, after verification.
- API contracts, database schemas, packages, and framework configuration belong to later design, not this discovery pack.

Idempotency/history retention is already settled for the dataset lifetime (`OQ-004`, `OQ-005`); authentication direction is settled (`OQ-007`). Exact technical parameters must be documented before the associated acceptance tests are finalized.

**Related:** [04 — Use Cases](./04-use-cases.md) · [05 — Processes](./05-business-processes.md) · [07 — Assumptions](./07-assumptions-and-questions.md)
