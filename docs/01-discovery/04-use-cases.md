# 04 — V1 Use Case Catalogue

> **Stage:** Phase 1 — Domain Discovery\
> **Status:** Approved V1 behavior and acceptance baseline — project owner decision\
> **Version:** 1.0 | **Last updated:** 2026-10-16

## 1. Conventions

- **Actor:** External role that initiates a case.
- **Preconditions:** Conditions already true before the use case starts.
- **Success guarantee:** Observable, persisted outcome on success.
- **Alternative / exception flow:** Valid rejection, or infrastructure/network failure.
- **Related rules:** IDs resolved in [06 — Business Rules](./06-business-rules.md).
- **P0:** Required for V1. **P1:** Optional; not part of the acceptance baseline.

A use case expresses **required behavior**; it does not prescribe REST endpoints, service decomposition, database tables, or UI framework.

All cases belong to **FinFast V1 Core Banking Foundation** and use simulated funds only. Customer Web and Admin/Back-office may share one responsive frontend with role separation. Controlled GENESIS is an operational prerequisite for funding, not a normal Admin action or an additional application actor.

## 2. Use case inventory

The inventory lists primary rules; each detailed case lists its full trace from the canonical rule coverage in [06](./06-business-rules.md#mandatory-rule-coverage).

| ID | Title | Primary actor | Priority | Business rules |
|---|---|---|---|---|
| UC-AUTH-01 | Register and provision an account | Customer | P0 | BR-ID-01, BR-ID-02, BR-ACC-01 |
| UC-AUTH-02 | Sign in and out | Customer/Admin | P0 | BR-ID-01, BR-ID-02 |
| UC-ACC-01 | View account and balance | Customer | P0 | BR-ACC-02, BR-HIS-02 |
| UC-ACC-02 | View account history | Customer | P0 | BR-HIS-01–BR-HIS-04 |
| UC-TRF-01 | Execute internal virtual transfer | Customer | P0 | BR-TRF-01–BR-TRF-12, BR-LED-04, BR-LED-06 |
| UC-TRF-02 | View transfer receipt | Customer | P0 | BR-HIS-01, BR-HIS-02 |
| UC-ADM-01 | List customer accounts | Admin | P0 | BR-ID-02, BR-ADM-06 |
| UC-ADM-02 | Allocate demo funds | Admin | P0 | BR-ADM-01–BR-ADM-05, BR-ACC-02, BR-LED-04–BR-LED-06 |
| UC-ADM-03 | Freeze/unfreeze customer account | Admin | P0 | BR-ACC-02, BR-ADM-03 |
| UC-ADM-04 | Review financial movements and audit events | Admin | P0 | BR-HIS-02, BR-HIS-04, BR-ADM-03, BR-ADM-06 |

## 3. Detailed use cases

### UC-AUTH-01 — Register and provision an account

**Actor:** Customer. **Trigger:** Visitor submits registration data.

**Preconditions:** The visitor is unauthenticated. Email uniqueness is checked during the case, including case-normalized concurrent duplicate registrations.

**Main success flow:**

1. Customer enters display name, email, and password only; no real identity documents or banking credentials are requested.
2. System validates required fields, format, case-normalized email uniqueness, and credential requirements.
3. System creates a User with `CUSTOMER` role and a linked Customer profile, storing the password using secure adaptive one-way hashing.
4. System provisions **one** ACTIVE VND Customer Account with balance `0` and a unique, immutable, system-generated Account Number separate from User/Customer IDs; the number is never reassigned.
5. System confirms registration without disclosing sensitive credential data.

**Alternatives:** Invalid fields or duplicate email → clear rejection; no duplicate accounts. Failure during provisioning → onboarding must not be reported successful with a missing account; use one atomic operation or equivalent recoverable guarantee.

**Postcondition:** Exactly one customer-owned account is available for an accepted registration.

**Rules:** `BR-ID-01`, `BR-ID-02`, `BR-ACC-01`, `BR-ACC-05`, `BR-ACC-06`, `BR-SIM-02`.

### UC-AUTH-02 — Sign in and sign out

**Actors:** Customer, Administrator.

1. User submits credentials.
2. System authenticates and establishes a secure server-managed browser session using protected HttpOnly cookies over HTTPS, following the approved architecture direction.
3. System enforces role and resource authorization server-side on protected operations.
4. User signs out or the session expires; the authenticated session is invalidated and cannot authorize further requests.

**Alternatives:** Wrong credentials → deny access; unauthorized role/resource → deny the requested operation. A frozen Customer Account does not prevent authentication or authorized read-only access. Signing out does not delete Financial Transactions or Account data. Admin identities must have been provisioned separately through a protected process.

**Rules:** `BR-ID-01`, `BR-ID-02`.

### UC-ACC-01 — View account and balance

**Actor:** Customer. **Preconditions:** Authenticated customer with provisioned account.

1. Customer opens account overview.
2. System resolves account ownership from the authenticated identity.
3. System returns immutable Account Number, account type, status, `VND`, and durable current balance, reconcilable to committed Ledger Entry Deltas.

**Alternatives:** Unauthorized Account reference, including Treasury Account details → deny; Account is FROZEN → Customer may still view their own details.

**Rules:** `BR-ACC-02`, `BR-ACC-05`, `BR-ACC-06`, `BR-LED-02`, `BR-HIS-02`.

### UC-ACC-02 — View account transaction history

**Actor:** Customer. **Preconditions:** Authenticated customer.

1. Customer requests their account history.
2. System retrieves only committed movements involving the owned Customer Account, retained for the dataset lifetime. Rejected requests, non-final processing outcomes, and GENESIS do not appear as Customer movements.
3. Customer sees timestamp, type, direction, positive Transfer Amount, meaningful counterparty or treasury label, and stable Transaction Reference, ordered newest first with a deterministic tie-break.
4. System provides stable pagination: default 20, maximum 100 records per page. Continuing a history traversal must not duplicate or omit eligible records because timestamps tie or newer transactions commit. Technical continuation semantics belong to design; advanced filtering remains P1.

**Alternatives:** No transactions → empty list; user requests another account → deny.

**Rules:** `BR-HIS-01`, `BR-HIS-02`, `BR-HIS-03`, `BR-HIS-04`.

### UC-TRF-01 — Execute an internal virtual transfer

**Actor:** Customer. **Trigger:** Customer submits recipient account number, amount, optional note, and idempotency key.

**Preconditions:** Authenticated Customer with an owned Customer Account. Account eligibility and available funds are validated for a new intent; replay of a previously recorded final outcome precedes re-evaluation of changing balances/statuses.

**Main success flow:**

1. System verifies authentication, role, and ownership of the Customer source Account.
2. System resolves the scope **initiator + operation type + Idempotency Key**, coordinating concurrent duplicates. An existing final outcome is replayed for identical payload; changed payload conflicts. A duplicate still processing may wait or receive a non-final response and retry safely.
3. For a new intent, System validates a strictly positive whole-VND Transfer Amount within supported numeric bounds, an existing different Customer recipient, and ACTIVE states for both Accounts. Treasury is not a Customer recipient/source.
4. In the same concurrency-safe consistency boundary used for commit, System checks current Account states, available funds, and safe resulting balances. No held-funds product or separate pending-transfer lifecycle is introduced.
5. In one atomic commit, System persists the Financial Transaction, exactly two entries (source delta −amount, destination delta +amount), resulting balances, **and the durable final idempotency outcome**.
6. Only after successful commit, System returns the unique immutable Transaction Reference and `COMPLETED` result.
7. Both Customers see the committed movement in their respective histories.

**Business alternatives:**

- Amount is zero, negative, fractional, invalid, unsupported, or would overflow an amount/resulting balance → reject; no financial effects.
- Recipient does not exist / is the same account → reject.
- Source or destination is FROZEN → reject.
- Available balance is insufficient (including after racing with another transfer) → reject.
- Same scoped key + identical request → return the recorded committed **or final business-rejected** result, even after funds/status change; do not execute twice. A genuinely new business attempt requires a new key.
- Same scoped key + different payload (amount, recipient, or other intent data) → conflict; do not execute.
- Final business rejection → retain the request outcome for the dataset lifetime without Financial Transactions, Ledger Entries, or balance changes.

**Technical exceptions:**

- Persistence fails before commit, including during ledger/result recording → roll back all financial effects; do not record a false completed outcome or cache the technical failure as permanent business rejection.
- Connection/response fails **after a commit** → client outcome is unknown; retry with the **same** scoped key or use an authorized result lookup. A reference may be used if known; recovery must also work when the lost response contained the reference.
- After transient infrastructure recovery, the original intent must resolve to a committed movement or final business rejection; clients do not replace uncertain requests with new keys.

**Success guarantee:** No half-posted transfer; committed entries sum to zero, accounts remain non-negative.

**Rules:** `BR-ACC-02`, `BR-ACC-03`, `BR-ACC-04`, `BR-TRF-01`, `BR-TRF-02`, `BR-TRF-03`, `BR-TRF-04`, `BR-TRF-05`, `BR-TRF-06`, `BR-TRF-07`, `BR-TRF-08`, `BR-TRF-09`, `BR-TRF-10`, `BR-TRF-11`, `BR-TRF-12`, `BR-LED-04`, `BR-LED-06`, `BR-SIM-01`.

### UC-TRF-02 — View a transfer receipt

**Actor:** Customer. **Preconditions:** Authenticated and involved as sender/receiver.

1. Customer selects a transaction reference in history.
2. System verifies the caller is a permitted participant.
3. System shows immutable Transaction Reference, type, currency, source/destination labels, positive amount, timestamp, and committed status. These facts remain stable across retries and status changes; associated Ledger Entries remain linked to the same reference.

**Alternatives:** Unknown reference → not found; unrelated transfer → deny (do not leak participant details).

**Rules:** `BR-LED-01`, `BR-LED-03`, `BR-HIS-01`, `BR-HIS-02`.

### UC-ADM-01 — List customer accounts

**Actor:** Administrator. **Preconditions:** Authenticated as admin.

1. Admin accesses the account management view.
2. System returns Account Number, Customer identifier, limited Customer display information, account status, and balance needed for authorized V1 operations. Passwords and unnecessary sensitive data are never exposed.

**Alternatives:** Customer tries to access admin view → deny. Search/pagination may be lightweight in V1.

**Rules:** `BR-ID-02`, `BR-ADM-06`, `BR-SIM-02`.

### UC-ADM-02 — Allocate demo funds

**Actor:** Administrator. **Trigger:** Admin supplies target account, positive amount, reason, and a request idempotency key.

**Operational prerequisite:** The dataset has exactly one Treasury Account initialized through controlled GENESIS, defaulting to 100,000,000 VND unless configured otherwise before first initialization. GENESIS records one positive treasury entry, immutable reference, amount, currency, timestamp, and verifiable initialization evidence. It is not repeated on restart or available as a normal Admin reset/replenishment operation.

1. System authenticates and authorizes Admin and resolves **initiator + DEMO_ALLOCATION + Idempotency Key**. Identical final outcomes replay, conflicting payloads reject, and concurrent duplicates wait or retry non-final processing safely.
2. For a new intent, System requires a positive integral VND amount within supported bounds and a non-empty reason.
3. Within the atomic consistency boundary, System verifies treasury and target Customer Account are ACTIVE, use VND, treasury has sufficient available funds, and resulting balances fit supported bounds. Account status and funds cannot be validated only before concurrent freeze/funding changes.
4. **One atomic financial operation** records the Financial Transaction, exactly two signed entries (treasury −amount; Customer +amount), both balances, immutable reference, durable final idempotency outcome, and required success Admin Audit Event containing administrator identity, action, target, reason, timestamp, outcome, and Transaction Reference.
5. Only after successful commit, Admin receives confirmation and Customer balance/history reflect the allocation. Total simulated supply is unchanged.

**Alternatives:** No Admin permission, invalid/overflowing amount, missing reason, frozen target, or insufficient treasury funds → reject without postings. Authorized final business rejections are retained/replayed for the dataset lifetime even after balances/statuses change; a new attempt needs a new key. Same scope with changed payload (including reason) conflicts. Required audit, ledger, balance, or final-outcome persistence failure aborts the entire success operation and remains technically recoverable. Lost responses are resolved with the original key or authorized lookup. Rejected/unauthorized Admin attempts should leave appropriate security/operational evidence, separate from financial records.

**Rules:** `BR-ACC-02`, `BR-ACC-03`, `BR-ACC-04`, `BR-ACC-05`, `BR-TRF-04`, `BR-TRF-05`, `BR-TRF-06`, `BR-TRF-10`, `BR-TRF-11`, `BR-TRF-12`, `BR-LED-04`, `BR-LED-05`, `BR-LED-06`, `BR-ADM-01`, `BR-ADM-02`, `BR-ADM-03`, `BR-ADM-04`, `BR-ADM-05`, `BR-SIM-01`.

### UC-ADM-03 — Freeze or unfreeze a customer account

**Actor:** Administrator.

1. Admin selects a Customer Account, intended ACTIVE/FROZEN status, and non-empty reason.
2. System authorizes the action; Treasury Account is excluded from this Customer Account control.
3. System changes status and writes the required success Admin Audit Event **atomically**, with administrator, action, target, reason, timestamp, outcome, and before/after states. The effective change is consistently ordered with concurrent transfer/funding commit.
4. Customer can continue to view account/history but cannot send or receive funds while FROZEN.

**Alternatives:** Unauthorized caller, nonexistent Account, or missing/empty reason → reject. A valid request for the existing status returns `UNCHANGED`, creates no financial movement, and may record the attempt in an audit trail. Required audit persistence failure leaves the original status unchanged. Rejected/unauthorized requests should produce appropriate security/operational evidence. A movement committed before the effective freeze remains valid; one ordered after it is rejected.

**Rules:** `BR-ACC-02`, `BR-TRF-06`, `BR-ADM-03`, `BR-ADM-05`.

### UC-ADM-04 — Review financial movements and audit events

**Actor:** Administrator.

1. Admin opens the operational review view and selects transaction movements or administrative audit history.
2. For Financial Transactions, System returns immutable reference, type, participating Accounts where applicable, currency, positive amount, timestamp, committed status, and associated signed Ledger Entries. GENESIS is explicitly labeled as initialization.
3. For Admin Audit Events, System returns time, administrator, action, target, reason, outcome, and relevant Transaction Reference or before/after state. Appropriate rejected/unauthorized operational evidence is separate from posted transactions.
4. Admin can correlate successful funding with its Financial Transaction and required audit record. Inspection is limited to authorized operational data; it never exposes passwords or unnecessary sensitive information.

**Verification support:** Persisted records must allow acceptance checks to reconcile every balance to signed entries (GENESIS included once) and total balances to the initial supply. This is record inspection and test evidence, not a new General Ledger report or treasury reset capability.

**Alternatives:** Unauthorized users receive no details; empty results yield an empty list.

**Rules:** `BR-LED-01`, `BR-LED-02`, `BR-LED-03`, `BR-LED-05`, `BR-HIS-02`, `BR-HIS-04`, `BR-ADM-02`, `BR-ADM-03`, `BR-ADM-06`.

## 4. Acceptance scenario matrix

These are required future acceptance scenarios, not test execution results. AC-001–AC-013 are preserved and clarified; variants share one ID where they verify the same outcome. CONS identifiers in [06](./06-business-rules.md#7-high-risk-consistency-scenarios) point here rather than defining duplicate tests. Initial GENESIS checks use the operational prerequisite of UC-ADM-02 and the records visible through UC-ADM-04.

| Scenario ID | Related use case | Given / When | Then |
|---|---|---|---|
| AC-001 | UC-AUTH-01 | New Customer registers | Exactly one owned CUSTOMER Account is ACTIVE with a zero VND opening balance |
| AC-002 | UC-ACC-01 | Alice requests Bob's account details | Alice is denied |
| AC-003 | UC-TRF-01 | Alice has 1,000,000 VND and transfers 200,000 to Bob | Transfer Amount remains +200,000; Alice delta −200,000 and resulting balance 800,000; Bob delta +200,000; exactly two balanced entries with one immutable reference |
| AC-004 | UC-TRF-01 | Alice attempts to transfer 1,000,001 from 1,000,000 | No transfer is committed; balances unchanged |
| AC-005 | UC-TRF-01 | Exact completed transfer request is retried with same key | Same result; no second debit/credit |
| AC-006 | UC-TRF-01, UC-ADM-02 | Same scoped key is reused with changed payload: amount/recipient or funding target/reason; exercise sequential and concurrent reuse | Conflict; no new movement and original final outcome unchanged |
| AC-007 | UC-TRF-01 | Alice has 100 VND; two distinct concurrent intents each send 80 to different recipients | At most one commits; the other receives final insufficient-funds rejection; no negative balance or partial entries |
| AC-008 | UC-ADM-02, UC-ADM-04 | Admin allocates 500,000 from treasury with a reason | Exactly two entries: treasury −500,000 and Customer +500,000; balances, transaction, required success audit, and final idempotency outcome commit together |
| AC-009 | UC-ADM-03 | Admin freezes Bob's Account | Bob can view details, but no new transfer/funding involving Bob may commit while FROZEN; previously committed movements remain valid |
| AC-010 | UC-TRF-01, UC-ADM-02 | Persistence fails before commit, including after the first Ledger Entry or before final outcome storage | No partial balances, transaction, entries, required success audit, or completed outcome; COMPLETED is not reported |
| AC-011 | UC-TRF-01, UC-ADM-02 | Transfer/funding commits but client times out or loses the response including its reference; client retries the original scoped key | Original committed outcome/reference is returned; one financial posting and, for funding, one required success audit; unknown response is not treated as proof of failure |
| AC-012 | UC-ACC-02 | Alice and Bob open history after a successful transfer | Alice sees outgoing and Bob incoming movement with correct positive amount, type, time, counterparty, and the same reference |
| AC-013 | UC-ADM-02 | Admin repeats identical funding sequentially and concurrently using the same scoped key | Exactly one allocation commits; duplicates receive the original result or a non-final processing response followed by safe resolution; treasury and Customer change once |
| AC-014 | UC-TRF-01 | Identical Transfer Requests arrive concurrently in the same scope | At most one Financial Transaction; duplicates wait or retry a non-final response; all ultimately resolve to the same final result/reference |
| AC-015 | UC-TRF-01, UC-ADM-02, UC-ACC-02 | Customer transfer is finally rejected for insufficient funds, then the source Customer receives an allocation; separately, funding is finally rejected for a frozen target, then the target is unfrozen; retry each original request/key | Original rejection replays with no postings/history movement; a new key permits a fresh attempt against current conditions; authorized request/audit evidence remains separate. This does not permit replenishing an exhausted treasury |
| AC-016 | UC-TRF-01, UC-ADM-02 | Submit zero, negative, fractional, malformed, unsupported/out-of-range amounts; exercise supported-range boundaries and resulting-balance overflow checks | Invalid/overflowing cases reject with no financial effects or numeric wrapping/truncation; valid supported values are not rejected by an invented commercial cap. Where overflow is unreachable under configured supply, verify the bound with isolated numeric validation evidence |
| AC-017 | UC-TRF-01 | Alice and Bob each have 100 VND and concurrently send 40 and 30 VND respectively to one another using distinct keys | Both valid intents ultimately commit, with Alice 90 and Bob 110; exactly two entries per transaction; no indefinite unresolved outcome after transient recovery |
| AC-018 | UC-TRF-01, UC-ADM-02, UC-ADM-03 | Freeze races with a transfer's source/destination or a funding target; exercise both effective orderings | Movement first: it remains committed, then freeze applies. Freeze first: new movement rejects. No posting uses a stale ACTIVE check; required status audit follows the same atomic change |
| AC-019 | UC-ADM-02, UC-ADM-03 | Required success Admin Audit Event cannot persist during funding or freeze/unfreeze | Funding has no posted transaction, entries, balance changes, or completed idempotency result; status change leaves prior state. Recovery may retry safely; no successful unaudited state change |
| AC-020 | UC-ADM-02, UC-ADM-04 | Initialize a fresh dataset at default supply or a valid preconfigured supply; repeat or concurrently invoke controlled initialization; inject interruption before initialization completes | Exactly one Treasury Account and one committed GENESIS with one positive treasury entry, stable reference, VND amount, timestamp, and verifiable record; no partial/duplicate supply; interrupted initialization recovers safely before ordinary operations |
| AC-021 | UC-ADM-02, UC-ADM-04, UC-ACC-02 | Restart an existing dataset, including with a changed initial-supply configuration and completed/rejected request keys already recorded | Existing treasury, balances, GENESIS amount/reference, financial history, and request outcomes remain; no reseeding or supply change; old keys replay original final outcomes |
| AC-022 | UC-ACC-01, UC-ADM-04 | Recompute each Account balance after GENESIS, funding, transfers, rejections, retries, and restarts | Current balance equals the sum of its committed signed entries from zero; GENESIS counted exactly once; no rejected-request entries |
| AC-023 | UC-ADM-02, UC-ADM-04 | Check total balances after each committed operation in a mixed sequential/concurrent workload | Treasury plus all Customer balances equals the initial GENESIS supply; ordinary transactions sum to zero; all balances remain non-negative |
| AC-024 | UC-ACC-01, UC-ACC-02, UC-TRF-01, UC-TRF-02, UC-ADM-01, UC-ADM-02, UC-ADM-03, UC-ADM-04 | Exercise unauthenticated access, another Customer's source/account/history/reference, Customer access to treasury details/transfers, Admin operations as Customer, and normal balance overwrite/reset/mint attempts | Server denies unauthorized access with no private disclosure or financial effect; treasury cannot be a Customer transfer source or destination; rejected Admin attempts produce appropriate separate security/operational evidence |
| AC-025 | UC-ACC-02, UC-ADM-04 | Browse history with more than 100 committed records, equal timestamps, new commits between pages, rejected attempts, and an empty Account | Default page has at most 20; requested pages never exceed 100; deterministic newest-first continuation does not duplicate/omit eligible records; only owned committed movements appear; empty history is empty; Admin audit evidence is separate |
| AC-026 | UC-AUTH-02, UC-ACC-01, UC-ACC-02, UC-TRF-01, UC-ADM-02 | Owner logs in while Account is FROZEN, reads account/history, and attempts send/receive/funding; then Admin unfreezes it | Login/read succeed; new money movements while frozen reject; read history is unchanged; new eligible intents after unfreeze may commit |
| AC-027 | UC-AUTH-01, UC-ADM-01 | Register with required/missing fields, email case variants, concurrent duplicate email, privilege escalation input, and an injected provisioning failure; inspect password persistence | Only display name/email/password are collected; normalized duplicate rejected; CUSTOMER only; adaptive one-way password hashing with no plaintext/reversible storage or exposure; no success with missing/duplicate Account |
| AC-028 | UC-AUTH-02 | Exercise valid/invalid login and reuse the authenticated session after logout/expiry; inspect browser session transport/protection | Invalid login denied; logout/expiry invalidates session server-side; protected HttpOnly cookies over HTTPS follow documented protections; no access using terminated session |
| AC-029 | UC-ADM-03, UC-ADM-04 | Freeze/unfreeze with missing/empty reason, valid reason, or already-current status | Missing reason rejected; valid change and complete before/after audit commit together; repeat returns UNCHANGED, no financial posting, optional attempt audit; only ACTIVE/FROZEN supported |
| AC-030 | UC-ADM-01, UC-ADM-04 | Authorized Admin inspects listing, transactions, success audit, and relevant rejected/unauthorized operational evidence | Only necessary metadata is visible; success audit has identity/action/target/reason/time/outcome/reference or before/after state; passwords/unnecessary sensitive data excluded; audit/request records remain distinct from Financial Transactions |
| AC-031 | UC-TRF-01, UC-TRF-02, UC-ADM-04 | Inspect receipts and entries across transfers, funding, GENESIS, retries, and attempts to edit/delete committed financial records | References unique and immutable; type/currency/positive amount/time/accounts/entries stable and retrievable by authorized roles; Ledger Entries append-only; no V1 operation edits/deletes them |
| AC-032 | UC-ADM-02 | Request allocation exceeding treasury funds or with missing reason; attempt normal replenishment/reset/direct balance overwrite | Reject without postings; treasury never negative; no ordinary mint/reset capability; dataset recreation is a separately controlled operation |
| AC-033 | UC-TRF-01, UC-ADM-02 | Transient technical failure before commit, followed by recovery and retry with the original key | Failure is not retained as permanent business rejection or false success; original intent ultimately resolves once to committed or final business-rejected outcome |
| AC-034 | UC-TRF-01, UC-ADM-02 | Reuse the same textual key in different initiator/operation scopes; replay old final outcomes late in the same dataset lifetime | Scopes do not collide or bypass authorization; within a scope final outcomes remain reserved/replayable with no automatic expiry/reuse; changing intent requires a new key |
| AC-035 | UC-AUTH-01, UC-ACC-01 | Create multiple Accounts and inspect numbers through freeze/unfreeze, restart, and attempted reassignment | Every Account Number is unique, system-generated, immutable, and never reassigned; User/Customer identifiers remain separate concepts |
| AC-036 | UC-TRF-01 | Request self-transfer, nonexistent recipient, treasury recipient, unsupported currency, or fee/overdraft behavior | Reject ineligible requests without postings; valid transfers use two different ACTIVE Customer Accounts in VND with no fee or overdraft |
| AC-037 | UC-AUTH-01, UC-TRF-01, UC-ADM-01, UC-ADM-02 | Review registration/demo data and all exposed V1 business paths | Simulated/synthetic data only; no real credentials/documents, real-money rails, Savings, Lending, FX, Cards, full GL, or other excluded capability; no additional source of simulated supply |
| AC-038 | UC-AUTH-01, UC-AUTH-02, UC-ACC-01, UC-ACC-02, UC-TRF-01, UC-TRF-02, UC-ADM-01, UC-ADM-02, UC-ADM-03, UC-ADM-04 | Walk through Customer and Admin interfaces at documented narrow and desktop viewport sizes | Required V1 journeys remain usable and responsive with proper role separation; only approved banking capabilities are exposed; Core behavior remains independent of presentation channel |

## 5. Resolved policies and remaining design work

- `OQ-003` — CLOSED: final business rejections return errors and may have operational/audit records, but no committed financial-history entry.
- `OQ-004` — CLOSED: final idempotency outcomes remain for the V1 dataset lifetime without automatic scoped-key reuse.
- `OQ-005` — CLOSED: stable recent-first financial-history pagination, default 20/max 100, dataset-lifetime retention; advanced filtering P1.

Architecture design will specify numeric bounds, safe concurrency mechanisms, session/cookie configuration, and pagination continuation semantics without reopening these policies. Performance measurement and external deployment checks are tracked in [07](./07-assumptions-and-questions.md); [08](./08-prd-handoff.md) is the PRD handoff, not an implementation specification.

**Related:** [03 — Actors](./03-actors.md) · [05 — Processes](./05-business-processes.md) · [06 — Business Rules](./06-business-rules.md)
