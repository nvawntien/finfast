# 04 — V1 Use Case Catalogue

> **Stage:** Phase 1 — Domain Discovery  
> **Status:** Working draft — use-case behavior proposed for team validation  
> **Version:** 0.1 | **Last updated:** 2026-10-14

## 1. Conventions

- **Actor:** External role that initiates a case.
- **Preconditions:** Conditions already true before the use case starts.
- **Success guarantee:** Observable, persisted outcome on success.
- **Alternative / exception flow:** Valid rejection, or infrastructure/network failure.
- **Related rules:** IDs resolved in [06 — Business Rules](./06-business-rules.md).
- **P0:** Required for V1. **P1:** Optional; not part of the acceptance baseline.

A use case expresses **required behavior**; it does not prescribe REST endpoints, service decomposition, database tables, or UI framework.

## 2. Use case inventory

| ID | Title | Primary actor | Priority | Business rules |
|---|---|---|---|---|
| UC-AUTH-01 | Register and provision an account | Customer | P0 | BR-ID-01, BR-ID-02, BR-ACC-01 |
| UC-AUTH-02 | Sign in and out | Customer/Admin | P0 | BR-ID-01, BR-ID-02 |
| UC-ACC-01 | View account and balance | Customer | P0 | BR-ACC-02, BR-HIS-02 |
| UC-ACC-02 | View account history | Customer | P0 | BR-HIS-01, BR-HIS-02 |
| UC-TRF-01 | Execute internal virtual transfer | Customer | P0 | BR-TRF-01 … BR-TRF-09 |
| UC-TRF-02 | View transfer receipt | Customer | P0 | BR-HIS-01, BR-HIS-02 |
| UC-ADM-01 | List customer accounts | Admin | P0 | BR-ID-02, BR-ADM-03 |
| UC-ADM-02 | Allocate demo funds | Admin | P0 | BR-ADM-01, BR-ADM-02, BR-ACC-02 |
| UC-ADM-03 | Freeze/unfreeze customer account | Admin | P0 | BR-ACC-02, BR-ADM-03 |
| UC-ADM-04 | Review financial movements and audit events | Admin | P0 | BR-HIS-02, BR-ADM-03 |

## 3. Detailed use cases

### UC-AUTH-01 — Register and provision an account

**Actor:** Customer. **Trigger:** Visitor submits registration data.

**Preconditions:** Email is not already registered; the visitor is unauthenticated.

**Main success flow:**

1. Customer enters the required demo identity fields and password.
2. System validates format, uniqueness, and minimum credential requirements.
3. System creates a user with `CUSTOMER` role and a linked customer profile.
4. System provisions **one** ACTIVE virtual VND account with balance `0`.
5. System confirms registration without disclosing sensitive credential data.

**Alternatives:** Invalid fields or duplicate email → clear rejection; no duplicate accounts. Failure during provisioning → onboarding must not be reported successful with a missing account; use one atomic operation or equivalent recoverable guarantee.

**Postcondition:** Exactly one customer-owned account is available for an accepted registration.

**Rules:** `BR-ID-01`, `BR-ID-02`, `BR-ACC-01`.

### UC-AUTH-02 — Sign in and sign out

**Actors:** Customer, Administrator.

1. User submits credentials.
2. System authenticates and establishes a session or issues an access credential.
3. System enforces role restrictions on protected operations.
4. User signs out or the session expires.

**Alternatives:** Wrong credentials → deny access; unauthorized role → deny requested operation. Signing out does not delete transactions or account data.

**Rules:** `BR-ID-01`, `BR-ID-02`.

### UC-ACC-01 — View account and balance

**Actor:** Customer. **Preconditions:** Authenticated customer with provisioned account.

1. Customer opens account overview.
2. System resolves account ownership from the authenticated identity.
3. System returns account number, status, `VND`, and durable current balance.

**Alternatives:** Unauthorized account reference → deny; account is FROZEN → customer may still view the details.

**Rules:** `BR-ACC-02`, `BR-HIS-02`.

### UC-ACC-02 — View account transaction history

**Actor:** Customer. **Preconditions:** Authenticated customer.

1. Customer requests their account history.
2. System retrieves committed movements involving the owned account.
3. Customer sees time, signed direction, amount, counterparty or treasury label, and reference, ordered newest first.

**Alternatives:** No transactions → empty list; user requests another account → deny.

**Rules:** `BR-HIS-01`, `BR-HIS-02`.

### UC-TRF-01 — Execute an internal virtual transfer

**Actor:** Customer. **Trigger:** Customer submits recipient account number, amount, optional note, and idempotency key.

**Preconditions:** Authenticated customer; an owned ACTIVE source account exists; recipient account is in the same simulator.

**Main success flow:**

1. System verifies the caller owns the source account.
2. System checks amount is a strictly positive whole-VND integer.
3. System resolves recipient account; prevents self-transfer; validates ACTIVE states.
4. System checks spendable balance and performs concurrency-safe reservation/check.
5. In one atomic commit, system persists the movement, a negative source entry, a positive destination entry, the resulting balance state, **and the durable idempotency key-to-result association**. Account state and available funds must be rechecked inside that consistency boundary to prevent races with freezes or concurrent transfers.
6. System returns a stable transaction reference and `COMPLETED` result.
7. Both customers can see the movement in their respective histories.

**Business alternatives:**

- Amount is zero, negative, fractional, or invalid → reject; no money movement.
- Recipient does not exist / is the same account → reject.
- Source or destination is FROZEN → reject.
- Available balance is insufficient (including after racing with another transfer) → reject.
- Same idempotency key + same request replayed → return previously recorded completed **or business-rejected** result; do not execute twice.
- Same idempotency key + different request → conflict; do not execute.

**Technical exceptions:**

- Database transaction fails before commit → rollback money effects.
- Connection/response fails **after a commit** → client outcome is unknown; retry with the **same** idempotency key or query by reference, rather than submitting as a new transfer.

**Success guarantee:** No half-posted transfer; committed entries sum to zero, accounts remain non-negative.

**Rules:** `BR-ACC-02`, `BR-ACC-03`, `BR-TRF-01` to `BR-TRF-09`.

### UC-TRF-02 — View a transfer receipt

**Actor:** Customer. **Preconditions:** Authenticated and involved as sender/receiver.

1. Customer selects a transaction reference in history.
2. System verifies the caller is a permitted participant.
3. System shows reference, source/destination labels, amount, date/time, and committed status.

**Alternatives:** Unknown reference → not found; unrelated transfer → deny (do not leak participant details).

**Rules:** `BR-HIS-01`, `BR-HIS-02`.

### UC-ADM-01 — List customer accounts

**Actor:** Administrator. **Preconditions:** Authenticated as admin.

1. Admin accesses the account management view.
2. System returns customer account identifier, account number, status, and balance (subject to agreed privacy fields).

**Alternatives:** Customer tries to access admin view → deny. Search/pagination may be lightweight in V1.

**Rules:** `BR-ID-02`, `BR-ADM-03`.

### UC-ADM-02 — Allocate demo funds

**Actor:** Administrator. **Trigger:** Admin supplies target account, positive amount, reason, and a request idempotency key.

1. System authenticates and authorizes admin.
2. System verifies that treasury and target customer account are ACTIVE and have the same currency.
3. System checks treasury available balance and rejects excessive allocations.
4. In one atomic operation, system moves virtual money from treasury to customer and records two ledger entries, identity of admin, reason, reference, and durable idempotency outcome.
5. Admin receives confirmation; customer balance/history reflect the allocation.

**Alternatives:** No admin permission, invalid amount, frozen target, insufficient treasury funds → reject with no partial movement. Repeated identical request key → replay original outcome without a second allocation; same key with changed payload → conflict.

**Rules:** `BR-ACC-02`, `BR-ACC-03`, `BR-ADM-01`, `BR-ADM-02`, `BR-ADM-03`, `BR-TRF-04`, `BR-TRF-06`, `BR-TRF-07`, `BR-ADM-04`.

### UC-ADM-03 — Freeze or unfreeze a customer account

**Actor:** Administrator.

1. Admin selects an account and intended new status.
2. System authorizes action; treasury is excluded from this customer-facing control.
3. System changes status to ACTIVE or FROZEN and writes an audit event.
4. Customer can continue to view account/history but cannot send or receive funds while FROZEN.

**Alternatives:** Customer lacks rights; account does not exist; repeat freeze/unfreeze returns unchanged status without creating financial movements.

**Rules:** `BR-ACC-02`, `BR-ADM-03`.

### UC-ADM-04 — Review financial movements and audit events

**Actor:** Administrator.

1. Admin opens the operational review view and selects transaction movements or administrative audit history.
2. For financial movements, system returns the persisted movement reference, type, accounts, amount, timestamp and status.
3. For administrative actions, system returns time, actor, action, subject, and outcome of funding/freeze operations.
4. Admin can correlate successful funding events with ledger transaction references.

**Alternatives:** Unauthorized users receive no details; empty results yield an empty list.

**Rules:** `BR-HIS-02`, `BR-ADM-03`.

## 4. Acceptance scenario matrix (draft)

| Scenario ID | Related use case | Given / When | Then |
|---|---|---|---|
| AC-001 | UC-AUTH-01 | New customer registers | One ACTIVE VND account has a zero opening balance |
| AC-002 | UC-ACC-01 | Alice requests Bob's account details | Alice is denied |
| AC-003 | UC-TRF-01 | Alice has 1,000,000 VND and transfers 200,000 to Bob | Alice −200,000; Bob +200,000; two balanced ledger entries |
| AC-004 | UC-TRF-01 | Alice attempts to transfer 1,000,001 from 1,000,000 | No transfer is committed; balances unchanged |
| AC-005 | UC-TRF-01 | Exact completed transfer request is retried with same key | Same result; no second debit/credit |
| AC-006 | UC-TRF-01 | Same key is reused with a different amount | Conflict, no new movement |
| AC-007 | UC-TRF-01 | Two concurrent transfers compete for insufficient combined funds | At most allowable transfers commit; no negative balance |
| AC-008 | UC-ADM-02 | Admin allocates 500,000 from treasury | Treasury −500,000; customer +500,000; reason/audit recorded |
| AC-009 | UC-ADM-03 | Admin freezes Bob's account | Bob can view details but no transfers involving Bob may commit |
| AC-010 | UC-TRF-01 | DB failure before commit | No partial movement is persisted |
| AC-011 | UC-TRF-01 | Server commits but client loses response; client retries same key | Client learns original committed result without a duplicate transfer |
| AC-012 | UC-ACC-02 | Alice opens history after a successful transfer | Outgoing record displays correct amount and reference |
| AC-013 | UC-ADM-02 | Admin repeats same demo allocation request key | Only one allocation commits, and original outcome is returned |

## 5. Open points that affect detailed design

- Should failed business requests be shown to customers, or only returned as errors and captured in audit/technical logs? (`OQ-003`)
- How long are idempotency results retained? (`OQ-004`)
- Exact retention/pagination requirements for transaction/audit history (`OQ-005`).

These questions should not prevent drafting the PRD, but must be resolved before the associated API contract and tests are finalized.

**Related:** [03 — Actors](./03-actors.md) · [05 — Processes](./05-business-processes.md) · [06 — Business Rules](./06-business-rules.md)
