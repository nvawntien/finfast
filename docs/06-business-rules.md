# 06 — Business Rules and Domain Invariants

> **Stage:** Phase 1 — Domain Discovery  
> **Status:** Working draft — proposed rules to be reviewed before PRD approval  
> **Version:** 0.1 | **Last updated:** 2026-10-14

## 1. Purpose and rule conventions

A **business rule** states a behavior or constraint the simulator must preserve, irrespective of its framework or deployment architecture. Each rule has a stable ID so the team can link it to use cases, PRD requirements, tests, and pull requests.

- **P0:** Mandatory for V1.
- **Invariant:** Condition that must never be violated in persisted financial state.
- **Reject:** Do not perform the requested money movement; return a meaningful error without leaking restricted details.
- **Audit:** Record an authorized or security-relevant action in a separate log where indicated.

These are **rules for our educational simulator**, not claims that all banks operate this way.

## 2. Identity and access rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-ID-01** | Customer email is unique (case-normalized); passwords are stored using a secure one-way password hash, never plaintext. | P0 | Duplicate registration rejected; password not recoverable from persistence |
| **BR-ID-02** | Customer registration yields `CUSTOMER` privileges only. Admin accounts are separately provisioned. All restricted operations enforce role and resource ownership on the server. | P0 | Customer cannot invoke admin action or access another customer's account |

## 3. Account rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-ACC-01** | Every successfully registered customer has exactly one VND customer account, created initially with balance `0`; account numbers are unique. | P0 | Registration creates one and only one zero-balance account |
| **BR-ACC-02** | Customer accounts have `ACTIVE` or `FROZEN` status. FROZEN blocks **both inbound and outbound money movements**, including admin allocations, but permits read-only access by the owner. | P0 | Every transfer involving frozen account rejected; history still visible |
| **BR-ACC-03** | No account, including internal treasury, may have a negative persisted balance or spend more than its available balance. | P0 invariant | Concurrent spending cannot take balance below zero |
| **BR-ACC-04** | All monetary amounts use VND as non-fractional integers; floating-point rounding may not determine ledger/balance values. | P0 | Decimal and non-integer transfer amounts rejected |

## 4. Internal transfer rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-TRF-01** | Transfer amount must be a strictly positive whole number of VND. | P0 | Zero/negative/fractional amount rejected |
| **BR-TRF-02** | Initiator must be authenticated and must own the customer source account. | P0 | Forged source account ID rejected |
| **BR-TRF-03** | Recipient must be an existing **different** customer account; both accounts must be ACTIVE and have the same currency. | P0 | Self-transfer, unknown or frozen recipient rejected |
| **BR-TRF-04** | A successful transfer changes debit-side balance, credit-side balance, movement record, and ledger entries in **one atomic consistency boundary**. | P0 invariant | Failure before commit yields no half-posted records |
| **BR-TRF-05** | For a given initiating user and idempotency key: **same request ⇒ same recorded outcome; different payload ⇒ conflict**. One intent must not produce two committed movements. Persist key/outcome durably, including processed business-rejection results, within a documented retention period. | P0 invariant | Repeat/restart test shows no second debit; payload mismatch conflicts |
| **BR-TRF-06** | Concurrent transfers must check funds with sufficient isolation/coordination that the final state is equivalent to some valid serial execution. | P0 invariant | Two concurrent excessive debits cannot both succeed |
| **BR-TRF-07** | Every committed internal transfer creates exactly two equal-and-opposite signed ledger entries, linked to one transaction reference; their sum is `0`. | P0 invariant | `sum(entry.amount) == 0` for each committed movement |
| **BR-TRF-08** | `COMPLETED` may only be reported after the database commit succeeds. A lost response **after** commit does not undo a transfer; the client must use idempotent retry/lookup to resolve the outcome. | P0 | Simulated lost response yields replayed committed reference |
| **BR-TRF-09** | Business-invalid transfer requests result in a deterministic rejection with **no balance or successful-ledger changes**; a completed request's ledger entries cannot be edited/deleted through V1 operations. | P0 | Insufficient funds returns error and leaves posted balance unchanged |

### Transfer invariant examples

| Operation | Source delta | Destination delta | Net movement |
|---|---:|---:|---:|
| Alice → Bob (200,000 VND) | −200,000 | +200,000 | 0 |
| Treasury → Alice (500,000 VND) | −500,000 | +500,000 | 0 |
| Rejected transfer | 0 | 0 | 0 |

**Important:** The **one-time genesis event** that establishes the initial treasury funds is an explicit simulation initialization event, **not an ordinary internal movement**. Its creation amount must be documented and audited; otherwise the total money supply would appear from nowhere.

## 5. Ledger and history rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-LED-01** | Committed ledger entries and their transaction references are append-only; no feature may silently overwrite or delete historical financial movements. | P0 | No endpoint or service path edits a committed entry |
| **BR-LED-02** | Any stored materialized account balance must remain reconcilable to opening/genesis balance plus committed signed entries for that account. | P0 invariant | Audit query recomputes account balances and matches materialized values |
| **BR-HIS-01** | Customer-facing history represents committed movements affecting that account with reference, timestamp, type, direction, amount, and meaningful counterparty label. | P0 | Alice sees outbound and Bob inbound record for same movement |
| **BR-HIS-02** | Customer may read only their own account and transfers in which they are a participant. Admin may inspect according to admin authorization. | P0 | Attempt to read unrelated account/reference is denied |

## 6. Administration and simulation rules

| ID | Rule | Priority | Suggested verification |
|---|---|---|---|
| **BR-ADM-01** | Demo allocation requires authenticated admin, positive whole-VND amount, ACTIVE target account, and non-empty operator reason. It is posted as a treasury-to-customer money movement, not a direct balance edit. | P0 | Funding created with two entries and recorded reason; direct override unavailable |
| **BR-ADM-02** | Simulator uses one finite internal VND treasury initialized by a **one-time audited genesis event**. Admin allocations cannot exceed its available balance. Ordinary actions do not mint new virtual funds. | P0 | Treasury stays non-negative; repeated application boot does not reseed |
| **BR-ADM-03** | Funding and account-freeze/unfreeze operations produce auditable records of actor, action, subject, timestamp, outcome, and linked transaction where applicable. | P0 | Admin can correlate an allocation to its audit event and ledger reference |
| **BR-ADM-04** | Admin funding requests must also be retry-safe: persist a stable request/idempotency key with the outcome to prevent duplicate allocations. | P0 | Retry of allocation does not debit treasury twice |
| **BR-SIM-01** | V1 does not represent real money, external transfers, customer cash-out, lending, FX, fees, interest, or overdraft. | P0 scope | No such UI endpoints or business flows are supported |
| **BR-SIM-02** | Demo users/data must be synthetic. The app does not ask for real banking credentials, payment credentials, or government IDs. | P0 scope | Registration/demo inspection confirms limited data collection |

## 7. High-risk consistency scenarios

| ID | Scenario | Rule(s) | Required outcome |
|---|---|---|---|
| CONS-01 | Alice has 100 VND; sends 80 twice concurrently to different recipients | BR-ACC-03, BR-TRF-06 | At most one 80 VND transfer commits |
| CONS-02 | Alice sends 50 VND, DB write fails before commit | BR-TRF-04, BR-TRF-08 | No balance or ledger partial effects |
| CONS-03 | Transfer commits, HTTP response is lost, client retries key | BR-TRF-05, BR-TRF-08 | Client receives original committed result |
| CONS-04 | Same key used with changed recipient | BR-TRF-05 | Conflict; no new movement |
| CONS-05 | Admin sends allocation twice due to UI double-click | BR-ADM-04 | Treasury debited once |
| CONS-06 | Admin freezes recipient while transfer is in progress | BR-ACC-02, BR-TRF-06 | Serialized outcome: transfer before freeze can commit; transfer after effective freeze rejects |
| CONS-07 | Service restarts with persistent DB and existing treasury | BR-ADM-02, BR-LED-02 | No repeated treasury genesis; balances preserved |
| CONS-08 | User requests unrelated transaction detail | BR-HIS-02 | Deny without disclosing private details |

## 8. Rule ownership and evidence

Recommended review owners:

- `BR-ID-*`: Identity/Authorization research owner.
- `BR-ACC-*`: Account domain owner.
- `BR-TRF-*` and `BR-LED-*`: Transfer/Ledger domain owner; require peer review.
- `BR-HIS-*`: Customer experience owner.
- `BR-ADM-*`: Admin/Operations domain owner.

Once requirements are approved, each P0 rule should have **at least one automated test** and a trace to one or more use cases. Rules about authorization, funds safety, and atomicity deserve negative-path and concurrency tests, not only happy-path tests.

## 9. Pending design decisions (not business rules)

Do not confuse these with approved domain invariants:

- PostgreSQL lock strategy, isolation level, and whether balances are computed or materialized.
- JWT versus server-side sessions; specific cloud provider or microservice topology.
- REST resource names/status codes and database schema.
- Exact retention policy for idempotency outcomes, subject to the V1 demo lifetime (`OQ-004`).

**Related:** [04 — Use Cases](./04-use-cases.md) · [05 — Processes](./05-business-processes.md) · [07 — Assumptions](./07-assumptions-and-questions.md)
