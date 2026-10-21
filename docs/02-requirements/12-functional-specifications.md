# 12 — Functional Specifications (behavioral contracts)

> Status: **DERIVED specification for engineering review**. The source of approved business meaning remains [04 use cases](../01-discovery/04-use-cases.md), [06 rules](../01-discovery/06-business-rules.md), and [09 PRD](09-product-requirements.md). External endpoint/HTTP DTO proposals and unresolved technical mechanisms retain their separate statuses; they are not new banking rules. The current internal Java signatures/DTOs and TD-015/016 ownership/boundaries are explicitly approved by Tiến for BK-003, effective as the shared baseline on merge into `develop` (pending); see the [canonical Java contracts](../03-design/module-contracts-and-boundaries.md). This approval does not implement the behaviors below.

## Universal conventions

- Roles: `CUSTOMER`, `ADMIN`. Every protected operation validates current session, role, ownership and resource permission *on the server*. Do not infer rights from hidden UI controls, submitted Account Number, or transaction reference.
- Currencies: VND only; positive integral amount for requests; signed integral `LedgerEntry.delta`; no real funds or identity documents.
- Source-of-truth distinction: **Transfer Request / final request outcome** is not **Financial Transaction**; **Financial Transaction** is not **Admin Audit Event**.
- Financial state is durable across restarts and releases; re-create a dataset only using a separately controlled maintenance process. No ordinary GENESIS/reset/mint API.
- `COMPLETED` means durably committed. A technical/unknown response does not mean rejected; retry/resolve the *original* key. A business rejection has no posted transaction or ledger entries.

## FS-IDENTITY — Registration, sessions, and ownership

**Source:** FR-001/002; UC-AUTH-01/02; BR-ID-01/02, BR-ACC-01/06; AC-001/024/027/028/035.

1. Registration accepts only display name, email and password. Normalize email case and enforce uniqueness transactionally, including concurrent requests.
2. A successful registration creates `User(CUSTOMER)`, a linked `Customer`, one ACTIVE CUSTOMER Account in VND, balance=0, and a never-reused unique Account Number. Any failure cannot leave successful partial onboarding.
3. Passwords use adaptive one-way hashing. Self-registration never provisions ADMIN; bootstrap identities by protected offline procedure.
4. Sign-in creates a secure server-managed browser session. Logout and expiration revoke it on the server. Cookies protected over HTTPS; cross-site mutation defenses documented in design.
5. All reads/writes enforce least privilege. FROZEN is an **Account** state, not a ban on login/read history. Session/role checks happen before idempotency replay or status lookups.

**Important rejection states:** invalid/duplicate email, bad credentials, expired session, wrong actor or owner, repeated provisioning failure. Neither password hash nor credential clues appear in response fields.

## FS-ACCOUNT — Account overview and lifecycle

**Source:** FR-003/009/012; UC-ACC-01, UC-ADM-01/03; BR-ACC-01..06, BR-ADM-03/05/06; AC-002/009/018/019/026/029/035.

- A Customer reads only its one owned Account Number, type, `ACTIVE|FROZEN` state, `VND`, and non-negative durable balance; available equals current.
- A Customer cannot access the TREASURY Account. An Admin can list required Customer Account metadata but cannot overwrite balances or perform controlled setup from the normal UI.
- Reasoned Admin status updates require a nonempty reason, target type CUSTOMER, valid desired state; effective change and complete success Audit Event commit atomically.
- Reapply current state → `UNCHANGED`, no monetary effect (attempt audit optional). Frozen Account can read; no new inbound or outbound transfer/allocation may commit.
- Status changes and financial movements must share a concurrency order. The status check that authorizes a commit must not be stale.

## FS-TREASURY — Controlled initialization

**Source:** FR-010; BP-06; BR-LED-05, BR-ADM-02; AC-020/021/022/023/032.

- Outside ordinary Customer/Admin actions, initialize one system-owned TREASURY Account and one immutable `GENESIS` Financial Transaction with exactly one positive treasury Ledger Entry.
- Default supply 100,000,000 VND, with a valid positive finite integer configurable only **before first successful GENESIS**.
- Concurrent/repeated/partially interrupted invocation is safe: at most one committed GENESIS and one treasury, never a usable half-initialized monetary dataset.
- Once initialized, restart or config changes cannot mint further value; retain existing `GENESIS` reference and supply. Subsequent operations are zero-sum.
- Controlled dataset recreation for demos is a maintenance responsibility, **not** an Admin API or another role.

## FS-FINANCE — Transfer, ledger, idempotency, recovery

**Source:** FR-004/005/006; UC-TRF-01; BP-02; BR-TRF-01..12, BR-LED-01..06; AC-003..007/010/011/014..018/022/023/033/034/036.

**New intent:** authenticate CUSTOMER; resolve source Account via session; validate supplied recipient Number, integral positive VND amount, optional note, and Idempotency Key. Recipient differs from source, both are CUSTOMER and ACTIVE; no treasury, fee, self-transfer, FX or overdraft.

**Idempotency order:** check current authorization **first**; then inspect `(initiator, operation=INTERNAL_TRANSFER, key)`. If canonical payload matches final outcome, replay original committed or final rejection regardless of changed funds/status. Different payload → conflict; concurrent identical request waits or returns resolvable non-final signal. For a new intent, re-check mutable eligibility during atomic commit.

**Financial commit:** within a concurrency-safe operation, lock/revalidate both Accounts and safe bounds, ensure sufficient funds, atomically persist one immutable Financial Transaction, two opposite signed entries (`-A`, `+A`), balance updates and final successful outcome. Only after DB commit return `COMPLETED` and original Transaction Reference. A valid business rejection is durable for the lifetime of the dataset, creates no financial postings, and replays unchanged with that key.

**Uncertain failure:** crash/network/lost response may happen before or after commit; client retains same scoped key and resolves without a known reference. Never automatically submit fresh key after a timeout. A technical failure is not a permanent business rejection.

**Ledger checks:** every ordinary transfer/allocation is zero-sum with exactly two entries; GENESIS has one positive entry and is never repeated. Each Account balance equals sum of all its signed entries; aggregate Account balances remain equal to GENESIS initial supply.

## FS-ADMIN — Funding and audit inspection

**Source:** FR-011/013; UC-ADM-02/04; BP-03/05; BR-ADM-01..06; AC-008/010/011/013/015/018/019/030/031/032/033/034.

- Only ADMIN may allocate from finite TREASURY to ACTIVE CUSTOMER Account. Validate whole-VND positive amount, safe bounds, sufficient treasury funds and **nonempty reason**.
- Same lifetime idempotency policy as transfers, scope `(admin initiator, DEMO_ALLOCATION, key)`; reason and target are part of intent fingerprint. A prior final business rejection replays unchanged even if account subsequently unfreezes.
- Committed allocation atomically persists treasury debit, Customer credit, Financial Transaction, two entries, final request outcome **and full required success Admin Audit Event** containing admin, action, target, reason, timestamp, outcome, reference. Failure of the audit means the allocation **does not** commit.
- Authorized Admin inspection shows committed Financial Transactions and their entries distinctly from Admin Audit Events, final idempotency outcomes and rejected/security evidence. Do not return password hashes or unrelated sensitive metadata.

## FS-HISTORY — History, receipts, and authorization

**Source:** FR-007/008/013; UC-ACC-02, UC-TRF-02, UC-ADM-04; BR-HIS-01..04; AC-012/015/021/024/025/030/031.

- Customer financial history includes only owned, committed ordinary `INTERNAL_TRANSFER` or incoming `DEMO_ALLOCATION`, not GENESIS, final rejected intents or audit events.
- Each row exposes immutable reference, type, timestamp, direction, positive amount, meaningful counterparty/treasury label. Newest-first; default 20, max 100. Pagination must be stable with timestamp ties and concurrent new postings.
- Receipt lookup requires a participating Customer and shows immutable committed facts. Unknown/unauthorized references must not expose other Customers' details. Admin inspects transaction and audit records under its own permissions.
- Durability through restart and retention through the dataset lifetime are mandatory.

## Functional cross-cutting decisions still to be ratified

- API field schemas/error codes and numeric range: [17 API contracts](../03-design/17-api-contracts.md), [OpenAPI proposal](../03-design/contracts/openapi-v1.yaml).
- Synchronization/locking/commit sequence: [16 transaction/idempotency design](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02), [16 history ordering](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05).
- Session TTL, CSRF mechanism, explicit privacy of not-found vs forbidden: [15 architecture](../03-design/15-system-architecture.md), [25 decision register](../6-governance/25-design-decision-register.md).
- No feature or service in this document is authorized to extend beyond PRD V1 exclusions.
