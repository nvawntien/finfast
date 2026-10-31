# 09 - FinFast V1 Product Requirements Document

> **APPROVED — V1 PRODUCT AND DELIVERY REQUIREMENTS BASELINE (2026-10-22):** Tiến (Tech Lead) explicitly approves the existing FR-001..013, UX-001..005 and NFR-001..006, the documented browser checks and unchanged §9 benchmark as the V1 implementation baseline. [Approval record](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze). This extends the earlier engineering-only freeze; it does not fabricate a separate project-owner signature or grant runtime/release acceptance. EV-001/002 remain PENDING VERIFICATION; EV-003 remains AWAITING MEASUREMENT.

> **Source transition:** The maintained high-level V1 product requirements now live in [docs/product/prd.md](../product/prd.md). This original approved PRD remains operative for its detailed FR clauses, UX/NFR acceptance content, and traceability until each obligation is explicitly reconciled into an accepted OpenSpec specification. The approved EV-003 benchmark method is maintained in the [verification strategy](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol). Preserve the identifiers and approval above; this source is not a second maintained high-level PRD.

> **Product:** FinFast Core Banking Platform\
> **Release:** FinFast V1 Core Banking Foundation\
> **Status:** APPROVED — V1 PRODUCT AND DELIVERY REQUIREMENTS BASELINE (2026-10-22)\
> **Version:** 0.1 | **Created:** 2026-10-17\
> **Implementation-baseline approval authority:** Tiến — Tech Lead, 2026-10-22; project-owner authority for future banking-policy changes and final release acceptance remains separate\
> **Source baseline:** Approved discovery decisions dated 2026-10-16, including the current working copies of documents 01-08

## 1. Purpose and source of truth

This PRD defines what FinFast V1 must deliver, how users experience it, and what evidence establishes acceptance. It turns the discovery baseline into identifiable functional, experience, and non-functional requirements. Tiến approves this documented V1 scope for implementation; that approval does not assert completed implementation, passing runtime tests or final release acceptance.

| Source | Requirements input |
|---|---|
| [Product vision and V1 scope](../product/vision.md) | Product outcomes, release boundaries, priorities, channels, and initial constraints |
| [Domain glossary](../product/glossary.md) | Canonical product terminology and distinctions between identities, accounts, requests, movements, and audit records |
| [Actor and role details](../01-discovery/03-actors.md) | Detailed CUSTOMER and ADMIN permissions and ownership boundaries, retained as operative behavior |
| [04 - Use Case Catalogue](../01-discovery/04-use-cases.md) | Ten use cases and 38 canonical acceptance scenarios |
| [05 - Business Process Flows](../01-discovery/05-business-processes.md) | BP-01 through BP-06, including controlled initialization and recovery |
| [06 - Business Rules](../01-discovery/06-business-rules.md) | All 38 mandatory business rules and CONS-01 through CONS-19 risk mappings |
| [07 - Assumptions, Questions, and Decision Log](../01-discovery/07-assumptions-and-questions.md) | A-001 through A-014, OQ-001 through OQ-013, DEC-001 through DEC-008, and EV-001 through EV-003 |
| [08 - PRD Handoff](../01-discovery/08-prd-handoff.md) | Consolidated discovery coverage, evidence boundaries, and readiness gates |

The existing UC, BR, AC, CONS, A, OQ, DEC, BP, and EV identifiers retain their meanings. The use cases, rules, and acceptance scenarios remain canonical in their linked documents. New FR, UX, and NFR identifiers organize delivery requirements without replacing those identifiers. A policy change requires an explicit project-owner decision and updates to affected source requirements.

The source documents settle the recorded banking-policy questions. The later 2026-10-22 Product/UX/REST freeze approves the existing V1 requirements and browser elaborations, including 360x800, 768x1024 and 1440x900 CSS pixels, desktop 200% zoom and keyboard checks. These are approved test specifications, not browser passes or WCAG certification. The benchmark method is maintained in the [verification strategy](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol) and remains approved under TD-013, with measurements pending. The later authorization does not retroactively enlarge the original discovery approval.

Historical provenance: the original 2026-10-17 PRD draft interpreted an omitted GENESIS term in vision A-007 and an unsupported parenthetical about course scaling. The engineering freeze corrected those source statements. Current meaning remains one controlled `GENESIS`; actual course/scaling obligations still require EV-001 evidence. OQ-006 names no mandated cloud. Neither correction adds a V1 technology or changes financial scope.

## 2. Product definition and goals

### 2.1 Product and problem

FinFast is an independently developed Core Banking Platform whose financial capabilities can support Customer Web Banking, Mobile Banking, and Back-office channels over time. V1 establishes the first usable foundation with Java/Spring Boot and PostgreSQL, rather than committing to the entire long-term banking suite.

The immediate problem is to implement and demonstrate reliable banking-like operations in a controlled educational environment. Users need understandable account and transfer experiences; operators need traceable funding and account controls; evaluators need reproducible evidence of financial correctness and cloud delivery.

**All V1 funds are simulated.** V1 cannot store, move, settle, redeem, or cash out real money and does not provide licensed banking services. Only synthetic customer data is used.

### 2.2 Outcomes and success measures

| Goal | Acceptance measure | Evidence |
|---|---|---|
| Complete the Customer journey | Registration, login, account overview, transfer, history, receipt, and logout work in the deployed responsive interface | Applicable AC scenarios and AC-038 browser walkthrough |
| Complete the Admin journey | Protected Admin identity can list accounts, allocate treasury funds, freeze/unfreeze, and inspect transactions/audits | UC-ADM-01 through UC-ADM-04 and AC-038 walkthrough |
| Preserve financial correctness | Zero negative balances, duplicate movements, partial postings, reconciliation mismatches, or supply changes after GENESIS in required tests | AC-003 through AC-023 as applicable, AC-031 through AC-034, and CONS coverage |
| Protect private resources | All required unauthenticated, wrong-role, wrong-owner, and terminated-session checks deny access without private disclosure or financial effects | AC-002, AC-024, AC-027, AC-028, AC-030 |
| Survive retries and restarts | Committed records and final outcomes remain durable; the same intent resolves without another posting | AC-005, AC-011, AC-013 through AC-015, AC-021, AC-033, AC-034 |
| Meet the provisional performance target | Transfer-request p95 <= 2 seconds at 10 requests/second for 5 minutes, subject to documented environment validation | EV-003 and section 9; no measured result is claimed |
| Deliver reproducibly | Containerized cloud application, persistent database, automated checks, deployment instructions, reviewed changes, and traceable acceptance evidence | EV-001/EV-002 evidence and release checklist |

There is no approved production availability SLA, commercial adoption target, or real-money readiness claim.

## 3. Users, permissions, and concepts

### 3.1 Personas and authorization

| Persona | Need | Permitted application behavior |
|---|---|---|
| Customer (`CUSTOMER`, ACT-01) | Manage one simulated payment account | Register; sign in/out; read own account and committed history; send from own account; read receipts for movements involving own account |
| Bank Administrator (`ADMIN`, ACT-02) | Operate and inspect the demo environment | Sign in/out using a separately provisioned identity; inspect necessary Customer Account metadata; allocate finite treasury funds; freeze/unfreeze Customer Accounts; inspect authorized financial and audit records |
| Evaluator/demo observer | Verify behavior and delivery evidence | Observe demonstrations or use provided Customer/Admin identities; no additional application role |
| Project/deployment team | Build, initialize, and deploy the system | SDLC and controlled setup responsibilities; no additional banking role or normal initialization/reset screen |

The server enforces role, ownership, and resource authorization for every protected operation, including replay and lookup. Hiding a control in the UI does not grant or enforce authorization. Admin privileges do not allow arbitrary balance edits or unrestricted credential access. There is no separate Auditor or Support role in V1.

### 3.2 Domain boundaries

| Concept | V1 meaning |
|---|---|
| User and Customer | User authenticates; Customer owns the one Customer Account. Their identifiers are separate from Account Numbers |
| Account types | `CUSTOMER` and `TREASURY`; exactly one system-owned Treasury Account per dataset |
| Account states | `ACTIVE` and `FROZEN`; frozen owners retain authentication and authorized read access |
| Money | VND integers only; positive Transfer Amount, signed Ledger Entry Deltas, non-negative balances; available balance equals current balance |
| Financial Transaction | Committed `INTERNAL_TRANSFER`, `DEMO_ALLOCATION`, or special initialization `GENESIS`, with a stable unique Transaction Reference |
| Request outcome | Final committed or business-rejected result associated with an idempotency scope; distinct from a financial posting |
| Admin Audit Event | Append-only operational evidence identifying who acted, why, on what, when, and with what outcome |
| Dataset lifetime | Lifetime of the persisted demo dataset, including restarts; controlled recreation establishes a new dataset |

The Signed-entry Ledger records account deltas and supports invariant verification. It is not a full General Ledger or an accounting-report product.

## 4. Release scope and priorities

### 4.1 Mandatory V1 scope (P0)

All FR-001 through FR-013, UX-001 through UX-005, and NFR-001 through NFR-006 are P0. They include:

- Customer registration, secure authentication, effective logout, and server authorization.
- Exactly one ACTIVE, zero-balance VND Customer Account on successful registration, with a unique immutable Account Number.
- Owned account overview, synchronous internal transfers, committed history, and receipts.
- Atomic signed-entry financial recording, concurrency safety, dataset-lifetime idempotency, and safe recovery from uncertainty.
- Controlled once-only treasury GENESIS, finite demo funding, reasoned freeze/unfreeze, and authorized transaction/audit inspection.
- Minimal responsive Customer Web and Admin/Back-office interfaces, which may share one frontend with role separation.
- Containerized reproducible cloud deployment, persistence, automated checks, baseline concurrency/fault/restart checks, and performance measurement against the provisional target.

### 4.2 Follow-on work (P1, after P0 acceptance)

| Candidate | Boundary |
|---|---|
| Asynchronous in-app completed-transfer notifications | Optional; failure cannot affect a committed transfer; no mandatory message broker |
| Advanced history filtering, basic metrics, improved account search | Add convenience after the required history, inspection, and pagination behavior works |
| Extended load testing and autoscaling demonstration | External justification remains unverified; capture the exact rubric, workload, topology, and evidence in EV-001 before scheduling the follow-on milestone |

Baseline financial-invariant, concurrency, and section 9 performance checks are P0. The placement of extended scaling work after P0 does not waive any verified course-completion requirement.

### 4.3 Exclusions

- Real funds, external bank/payment rails, interbank transfers, clearing/settlement, gateways, withdrawals/redemption, and cash-out.
- Savings, Lending, Cards, interest, fees, FX/multiple currencies, overdrafts, standing orders, scheduled transfers, and reversals/chargebacks.
- Full General Ledger, chart of accounts, accounting periods/reports, branches, multi-tenancy, and product catalogues.
- Real KYC/AML, government identity documents, fraud models, regulatory/tax services, and real banking/payment credentials.
- Additional Customer-created accounts, account closure, additional lifecycle states, native mobile applications, and production high availability guarantees.
- Normal treasury replenishment/reset/minting, direct balance overwrite, and complex persisted transfer state machines.

Future banking domains and native mobile remain possible directions requiring separate discovery and prioritization. No delivery date or later-release commitment is implied.

## 5. End-to-end journeys

| Journey | User-visible sequence | Required result |
|---|---|---|
| Onboard and inspect (BP-01) | Register with display name/email/password; sign in; open overview | Exactly one ACTIVE CUSTOMER Account at 0 VND; stable Account Number; no sensitive credential exposure |
| Fund and transfer (BP-03, BP-02) | Admin allocates 1,000,000 VND to Alice with a reason; Alice sends 200,000 to initially unfunded Bob; both inspect history/receipt | Alice 800,000; Bob 200,000; treasury reduced by 1,000,000; each ordinary movement has two entries and one reference; allocation has its required audit |
| Resolve a lost response (BP-02) | Alice submits; response is lost; interface resolves the original key | Original final outcome appears without a second debit, even if the lost response contained the reference |
| Freeze and resume (BP-04) | Admin freezes Bob with a reason; Bob logs in and reads history; new send/receive/funding attempts reject; Admin unfreezes with a reason | Status changes have atomic audits; committed history remains; new eligible intents after unfreeze may succeed |
| Inspect and verify (BP-05, BP-06) | Admin reviews funding and its audit; team restarts and reconciles the dataset | Stable financial facts; retained outcomes; one GENESIS; per-account reconciliation and total supply preserved |

The example assumes both Customers were initially unfunded and no other operations occur. The default initial treasury supply is 100,000,000 VND, so its balance after that allocation is 99,000,000 VND.

## 6. Functional requirements

### FR-001 - Register and provision a Customer Account

1. Registration collects display name, email, and password only. Validate required values and format; email uniqueness is case-normalized and holds under concurrent duplicate registration.
2. Store passwords using secure adaptive one-way hashing. Never store or return plaintext or reversibly encrypted passwords.
3. Self-registration grants `CUSTOMER` privileges only. Client-supplied privilege or ownership values cannot grant Admin access.
4. Every successful registration produces a User, linked Customer, and exactly one ACTIVE CUSTOMER Account in VND with opening balance 0. Onboarding failure must not be reported as success with a missing or duplicate account; provide an atomic or equivalently recoverable guarantee.
5. Every Account Number is system-generated, globally unique among Accounts, immutable, and never reassigned. It is distinct from User and Customer identifiers and is not an authorization credential.
6. Provision Admin identities through a protected bootstrap/administrative procedure outside Customer self-registration.

### FR-002 - Authenticate, authorize, and sign out

1. Authenticate Customer and Admin credentials; invalid credentials do not establish access.
2. Browser authentication uses secure server-managed sessions with protected HttpOnly cookies over HTTPS. Session expiration and logout invalidate the authenticated session on the server; reusing it cannot authorize a protected request.
3. Verify authentication, role, and resource permissions on the server before protected reads, mutations, final-outcome replay, or result lookup. Deny another Customer's private resources without disclosing their details.
4. A FROZEN Account does not prevent its owner signing in or reading authorized account/history/receipt data. Logout does not remove financial data.

### FR-003 - Show the owned Account and balance

1. Resolve ownership from the authenticated Customer; do not trust a client-selected account to establish ownership.
2. Show Account Number, type, ACTIVE/FROZEN status, VND, and durable current balance. Available balance equals current balance because there are no holds or pending settlements.
3. The displayed balance must reconcile to committed signed Ledger Entries, including after restart. Preserve read access while frozen.
4. Deny unrelated Account access and direct Customer access to Treasury Account details. A permitted treasury counterparty label on an allocation does not expose treasury balances or management access.

### FR-004 - Validate and execute internal transfers

1. Accept a recipient Account Number, positive whole-VND amount, optional note, and Idempotency Key. The source is the authenticated Customer's owned Account; a forged source must be denied.
2. A new eligible intent requires two existing, different, ACTIVE CUSTOMER Accounts in VND. Reject self-transfer, unknown recipient, treasury involvement, unsupported currency, fees, and overdrafts.
3. Validate amounts and signed deltas against the supported numeric range. Recheck current source funds, both account states, and safe resulting balances inside the concurrency-safe commit boundary.
4. Reject zero, negative, fractional, malformed, unsupported, or overflowing values without rounding, wrapping, truncating, or posting. There is no independent commercial cap beyond available funds and supported numeric bounds.
5. Apply FR-005 and FR-006. Return `COMPLETED` with the immutable reference only after commit. Business rejection leaves balances unchanged and creates no Financial Transaction or Ledger Entry.

### FR-005 - Record atomic financial effects and immutable facts

1. An ordinary movement of positive amount A produces exactly two entries linked to one Financial Transaction: source delta `-A` and destination delta `+A`. Their sum is zero. For Customer transfers, those entries belong to the sending and receiving Customer Accounts.
2. Commit the Financial Transaction, both entries, both balance effects, and final idempotency outcome atomically. Demo allocation additionally includes its complete required success Admin Audit Event in the same operation.
3. Any required persistence failure before commit prevents all partial financial/success-audit effects and a false completed outcome. Failure to durably retain a final rejection is a recoverable technical failure.
4. Coordinate concurrent operations so committed state is equivalent to a valid serial execution, including competing debits, opposite-direction transfers, allocation, and freeze/unfreeze. Stale prechecks cannot authorize a posting after an effective freeze.
5. Give every committed Financial Transaction a unique immutable reference linked to stable type, VND currency, positive amount, timestamp, applicable participating Accounts, and associated entries. Ledger Entries and references are append-only; no V1 operation edits or deletes them.
6. Enforce the invariants in section 7 for every posting. GENESIS is the separate one-entry exception defined in FR-010.

### FR-006 - Preserve one final outcome per intent and recover safely

1. Scope keys by **initiator + operation type + Idempotency Key**, for both `INTERNAL_TRANSFER` and `DEMO_ALLOCATION`. The client supplies a key for each intent; the interface retains it while resolving that intent.
2. After current authorization, resolve an existing scoped key before re-evaluating mutable balances or account states. Identical payload returns the original final committed or business-rejected outcome and, if committed, its original reference.
3. Changed intent data in the same scope conflicts without replacing its outcome or creating a movement. Compare all intent-bearing fields, including transfer note if supplied and funding target/reason. Exact canonicalization belongs to design.
4. Concurrent identical requests cannot produce multiple Financial Transactions. A duplicate may wait or receive a clearly non-final processing response; subsequent same-key resolution must return the same final result.
5. Persist committed and final business-rejected outcomes for the dataset lifetime, including across restarts. Do not expire/recycle a key in its scope. Changed funds/status do not turn an old final rejection into a new execution; a genuinely new attempt requires a new key.
6. A transient technical failure is neither a permanent business rejection nor proof of no commit. After recovery, the original intent must resolve to a committed movement or final business rejection.
7. Resolve timeout/lost-response uncertainty by retrying the original scoped key or authorized lookup. Recovery must work without a known Transaction Reference. Do not automatically create a new key to replace an uncertain intent.

### FR-007 - Present committed Account history

1. Customers see only committed movements involving their owned Account, with stable reference, timestamp, type, direction, positive amount, and meaningful counterparty/treasury label.
2. Include incoming/outgoing internal transfers and received demo allocations. Exclude GENESIS, rejected requests, technical failures, and non-final processing responses from Customer financial history.
3. Order newest first with a deterministic tie-break. Default to 20 records per page and enforce a maximum of 100. Continuing a traversal must not duplicate or omit eligible records because timestamps tie or newer movements commit. Define the eligible traversal set and continuation mechanism in design.
4. Retain history for the dataset lifetime, including restarts. Show an empty state for no movements. Advanced filters remain P1.

### FR-008 - Present authorized receipts

1. A participating Customer can select a movement reference from history and view immutable reference, type, currency, source/destination labels, positive amount, timestamp, and committed status.
2. Check participation server-side; unknown references produce a safe not-found response and unrelated references reveal no private facts. Admin transaction inspection follows FR-013.
3. Facts and linked entries remain stable across retries, restarts, or later account freezes. Receipt success must come from committed data.

### FR-009 - List Customer Accounts for Admin operations

1. An authorized Admin can list Account Number, Customer identifier, limited Customer display information, account status, and balance needed for V1 operations.
2. Exclude passwords and unnecessary sensitive information. Customer access to the Admin listing or actions is denied server-side.
3. Enable selecting a listed Customer Account for allocation or status management. Basic listing is sufficient; enhanced search is P1.

### FR-010 - Initialize the finite treasury once

1. Controlled dataset setup establishes one system-owned VND TREASURY Account and exactly one committed `GENESIS` Financial/Ledger Transaction with one positive treasury entry.
2. Initial supply defaults to **100,000,000 VND**. A finite positive integral VND supply within supported numeric bounds may be configured before first GENESIS only.
3. GENESIS has an immutable reference, amount, currency, timestamp, and verifiable initialization evidence. No usable partial supply or duplicate treasury may result from interruption, repeated invocation, or concurrent setup.
4. Existing datasets retain the original supply and reference on restart or initial-supply configuration change. GENESIS is included once in reconciliation from a zero baseline.
5. Make ordinary money movements available only after complete initialization. Treasury is used solely for authorized demo allocations thereafter.
6. Provide no normal Admin/Customer initialization, reset, replenishment, or money-minting capability. Demo reset is separately controlled dataset recreation, with the resulting dataset treated as new.

### FR-011 - Allocate demo funds with atomic audit

1. Accept an authorized Admin request containing target Customer Account, positive whole-VND amount, non-empty reason, and Idempotency Key. Resolve scoped outcomes under FR-006 before revalidating new-intent conditions.
2. Within the commit boundary, require ACTIVE treasury and target, VND, sufficient treasury funds, and supported amount/resulting-balance bounds. A FROZEN target cannot receive funds.
3. Move funds from treasury to Customer under FR-005: exactly two opposite entries, balances, reference, final outcome, and complete required success audit commit together. Do not directly overwrite a balance.
4. The success audit identifies administrator, action, target, non-empty reason, timestamp, outcome, and Transaction Reference. Required audit failure aborts the entire allocation.
5. Confirm only after commit; expose the allocation in the receiving Customer's history. Sequential/concurrent duplicate submissions allocate once. Changed target/amount/reason conflicts; final rejections replay; uncertain/technical outcomes recover with the original key.
6. Reject insufficient treasury funds, missing reason, invalid amount, or ineligible target without financial postings. No ordinary operation replenishes treasury or increases total supply. Capture appropriate rejected/unauthorized Admin evidence separately from financial records.

### FR-012 - Freeze and unfreeze with atomic audit

1. An authorized Admin may set a CUSTOMER Account to `ACTIVE` or `FROZEN` with a non-empty reason. Reject missing/empty reason, unknown Account, unauthorized caller, or an unsupported state. Treasury is excluded from this Customer control.
2. Commit each effective status change with its required success Admin Audit Event, including administrator, action, target, reason, timestamp, outcome, and before/after states. Required audit persistence failure preserves the previous status.
3. Consistently order status changes against transfer/allocation commit. A movement first may commit and remains valid; an effective freeze first rejects a new movement involving that Account. Unfreeze permits new eligible intents, while prior final outcomes still replay unchanged.
4. A valid request for the existing status returns `UNCHANGED`, creates no financial movement, and may audit the attempted operation. Rejected/unauthorized requests should have appropriate separate operational/security evidence.
5. Frozen owners retain authentication and authorized account/history/receipt reads; new inbound and outbound transfers and allocations are blocked.

### FR-013 - Inspect financial and administrative evidence

1. An authorized Admin can inspect Financial Transactions with reference, type, participating Accounts where applicable, currency, positive amount, timestamp, committed status, and associated signed entries. Label GENESIS explicitly as initialization.
2. Present Admin Audit Events separately, with timestamp, administrator, action, target, reason, outcome, and applicable reference or before/after state. Keep request/idempotency outcomes and rejected/unauthorized evidence distinct from financial postings.
3. Allow correlation of successful allocation with its Financial Transaction and required audit. Preserve append-only audit evidence and exclude passwords/unnecessary sensitive data.
4. Retained records must support acceptance verification of every Account balance and initial total supply. This does not add a General Ledger reporting feature or balance-correction operation.

## 7. Financial invariants and observable outcomes

### 7.1 Invariants

| Invariant | Required persisted condition |
|---|---|
| Non-negative funds | Every Customer and Treasury balance is >= 0; no operation spends unavailable funds |
| Safe arithmetic | Amounts, signed deltas, and resulting balances stay within documented supported integer bounds |
| Ordinary movement balance | Exactly two entries per transfer/allocation: `-A` and `+A`, summing to 0 for A > 0 |
| Atomic financial result | Transaction, entries, balances, final outcome, and allocation success audit either all commit or none commit |
| Atomic status result | Effective status and required success audit commit together |
| One-time supply | Exactly one GENESIS per dataset with one positive treasury entry; no subsequent normal creation/destruction |
| Per-account reconciliation | `balance(account) = sum(committed signed deltas for account)` from zero, including treasury GENESIS once |
| Supply conservation | `treasury balance + sum(all Customer balances) = initial GENESIS supply` after initialization |
| One result per intent | Same scoped intent has one immutable final outcome and at most one ordinary Financial Transaction |
| Append-only evidence | Committed financial entries, references, and required audit evidence remain stable and retrievable by authorized roles |
| Frozen ordering | No new movement involving a frozen Account commits after the effective freeze until unfreeze; earlier committed movements remain valid |

Rejection has zero financial effects; it does not create zero-valued Ledger Entries. Snapshot/reconciliation checks during concurrency must observe one consistent committed state rather than combine reads from different instants.

### 7.2 Client-visible outcome contract

These are semantic categories, not prescribed HTTP statuses or a new persisted transfer state machine.

| Outcome | Meaning | Client behavior |
|---|---|---|
| `COMPLETED` | Required financial commit succeeded | Show committed reference/receipt; retries replay it |
| Final business rejection | Authorized intent was durably rejected without posting | Show meaningful reason; same key replays it; deliberate fresh attempt uses a new key |
| Key conflict | Scope exists for different intent data | Explain conflict; preserve original intent and outcome |
| Non-final processing | Original identical request has not resolved yet | Retain the original key and resolve it safely |
| Technical/unknown outcome | Commit status is not established for the client | Explain uncertainty, retain original key, and resolve after recovery |
| Access denied | Authentication/role/resource check failed | Disclose no restricted details; no financial effect |
| `UNCHANGED` | Valid status request already matches current state | Confirm current status; no financial movement |

## 8. Experience and non-functional requirements

### 8.1 Required browser experience

| ID | Requirement | Acceptance evidence |
|---|---|---|
| UX-001 | Customer Web provides registration/login/logout, overview/balance, transfer, paginated history, and receipt navigation; required flows identify funds as simulated | AC-037, AC-038, and corresponding functional scenarios |
| UX-002 | Admin/Back-office provides Customer Account listing, reasoned allocation, reasoned freeze/unfreeze, and distinct transaction/audit inspection with clear role separation | AC-030, AC-038 and Admin scenarios |
| UX-003 | Forms provide labels, meaningful validation, keyboard operation, visible focus, and readable amounts/statuses. Loading, empty, denied, frozen, business-rejected, uncertain, and committed states have clear messages without private disclosure | Approved usability checks within AC-038; execution pending; authorization and rejection scenarios |
| UX-004 | Repeated submits and retry/recovery controls retain the same intent/key until resolved. Do not display success on submission alone or offer a replacement intent as the way to resolve an unknown outcome. Show committed reference after resolution and refresh affected owned views | AC-005, AC-011, AC-013, AC-014, AC-015, AC-033, AC-038 |
| UX-005 | Required journeys are usable on narrow and desktop viewports, without clipped controls, overlapping text, or loss of access to transaction/audit facts. Approved V1 checks: 360x800, 768x1024, and 1440x900 CSS pixels, plus a desktop 200% zoom/keyboard pass; document tested browsers | AC-038; approved verification specification, execution pending, no formal accessibility certification claimed |

### 8.2 Non-functional requirements

| ID | Requirement | Acceptance evidence |
|---|---|---|
| NFR-001 | Financial correctness has zero invariant violations in acceptance, concurrency, fault-injection, restart, and benchmark verification. Recovery must eventually resolve valid intents after transient failures | Section 7, canonical AC/CONS scenarios, reconciliation artifacts |
| NFR-002 | Persist Financial Transactions, entries, financial history, final idempotency outcomes, and required success audits durably across application restarts. Financial history/outcomes last for the dataset lifetime; do not invent an audit deletion period that breaks required evidence | AC-019 through AC-023, AC-031, AC-033, AC-034; retention design |
| NFR-003 | Use adaptive password hashing, HTTPS, protected HttpOnly session cookies, effective logout/expiry, server role/resource checks, and minimum necessary data access. Design must document cookie protections, session renewal/invalidation/timeouts, and protection against forged browser mutations. Keep credentials/session secrets out of client-visible errors and operational evidence | AC-024, AC-027, AC-028, AC-030; security configuration and negative-path review |
| NFR-004 | Measure transfer-request p95 <= 2 seconds at 10 requests/second over 5 minutes on the documented demo environment using section 9. Target remains provisional until EV-003; correctness cannot be traded for latency | Benchmark report, outcome distribution, invariant checks, EV-002/EV-003 |
| NFR-005 | Deliver a containerized application with persistent PostgreSQL, environment-based configuration, protected deployment secrets, automated checks, and reproducible cloud setup/restart instructions. Record resource sizing and application version; retain deployment and end-to-end evidence | EV-001/EV-002, pipeline/deployment records, restart and browser walkthrough |
| NFR-006 | Keep domain-oriented Core capabilities independent of presentation channels in a Java/Spring Boot Modular Monolith. Preserve requirement-to-test-to-task traceability and peer review for financial changes; optional notifications cannot determine posting success | Design review, automated check records, PR/task links, AC-038 |

Exact password/session parameters, numeric representation, concurrency mechanisms, pagination continuation, API contracts, schema/migrations, packages, Java classes, frontend framework, and hosting topology belong to technical design. Kafka, Redis, Kubernetes, and microservices are not mandatory V1 dependencies. No external payment system is required.

## 9. Performance acceptance protocol

The approved TD-013 EV-003 benchmark procedure was moved without changing its
workload or clean-pass criteria to the
[verification strategy](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol).
Its approval remains covered by the 2026-10-22 baseline approval above and the
[canonical decision record](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze).
This heading remains for existing links. No performance result is claimed.

## 10. Requirements traceability and acceptance

### 10.1 Functional requirement coverage

The [canonical acceptance matrix](../01-discovery/04-use-cases.md#4-acceptance-scenario-matrix) defines each AC. References below preserve all 10 use cases and all 38 scenarios without duplicating their Given/When/Then definitions. UX/NFR checks supplement that matrix as specified in section 8. CONS-01 through CONS-19 remain risk views of the same ACs in [06](../01-discovery/06-business-rules.md#7-high-risk-consistency-scenarios).

| Requirement | Use cases / process | Canonical acceptance scenarios |
|---|---|---|
| FR-001 | UC-AUTH-01; BP-01 | AC-001, AC-027, AC-035, AC-037 |
| FR-002 | UC-AUTH-02; every protected use case | AC-024, AC-026, AC-027, AC-028 |
| FR-003 | UC-ACC-01 | AC-002, AC-022, AC-024, AC-026, AC-035 |
| FR-004 | UC-TRF-01; BP-02 | AC-003, AC-004, AC-016, AC-018, AC-024, AC-026, AC-036, AC-037 |
| FR-005 | UC-TRF-01, UC-ADM-02, UC-ADM-04; BP-02/BP-03 | AC-003, AC-007, AC-008, AC-010, AC-017, AC-018, AC-019, AC-022, AC-023, AC-031 |
| FR-006 | UC-TRF-01, UC-ADM-02; BP-02/BP-03 | AC-005, AC-006, AC-011, AC-013, AC-014, AC-015, AC-021, AC-033, AC-034 |
| FR-007 | UC-ACC-02; BP-05 | AC-012, AC-015, AC-021, AC-024, AC-025, AC-026 |
| FR-008 | UC-TRF-02; BP-05 | AC-024, AC-031 |
| FR-009 | UC-ADM-01 | AC-024, AC-027, AC-030, AC-037 |
| FR-010 | UC-ADM-02 prerequisite; UC-ADM-04; BP-06 | AC-020, AC-021, AC-022, AC-023, AC-032 |
| FR-011 | UC-ADM-02, UC-ADM-04; BP-03 | AC-006, AC-008, AC-010, AC-011, AC-013, AC-015, AC-016, AC-018, AC-019, AC-026, AC-032, AC-033, AC-034 |
| FR-012 | UC-ADM-03, UC-ADM-04; BP-04 | AC-009, AC-018, AC-019, AC-026, AC-029 |
| FR-013 | UC-ADM-04; BP-05/BP-06 | AC-020, AC-022, AC-023, AC-024, AC-025, AC-030, AC-031 |
| UX-001, UX-002, UX-003, UX-004, UX-005 | All ten use cases through required Customer/Admin Web | AC-038 plus corresponding functional scenarios |

### 10.2 Mandatory business-rule coverage

This matrix extends the [canonical per-rule UC/AC coverage](../01-discovery/06-business-rules.md#mandatory-rule-coverage) with PRD requirement IDs. All 38 business rules are P0; the mapping is coverage, not a claim that tests passed.

| Business rule | PRD requirement coverage |
|---|---|
| BR-ID-01 | FR-001, FR-002, NFR-003 |
| BR-ID-02 | FR-001, FR-002, FR-009, NFR-003 |
| BR-ACC-01 | FR-001 |
| BR-ACC-02 | FR-003, FR-004, FR-011, FR-012 |
| BR-ACC-03 | FR-004, FR-005, FR-011, NFR-001 |
| BR-ACC-04 | FR-003, FR-004, FR-005, FR-010, FR-011 |
| BR-ACC-05 | FR-001, FR-003, FR-004, FR-010, FR-011 |
| BR-ACC-06 | FR-001, FR-003, FR-010 |
| BR-TRF-01 | FR-004 |
| BR-TRF-02 | FR-002, FR-004 |
| BR-TRF-03 | FR-004, FR-012 |
| BR-TRF-04 | FR-005, FR-011 |
| BR-TRF-05 | FR-006, FR-011, UX-004, NFR-002 |
| BR-TRF-06 | FR-004, FR-005, FR-011, FR-012 |
| BR-TRF-07 | FR-005 |
| BR-TRF-08 | FR-004, FR-005, FR-006, UX-004 |
| BR-TRF-09 | FR-004, FR-005 |
| BR-TRF-10 | FR-006, FR-011 |
| BR-TRF-11 | FR-006, UX-004, NFR-001 |
| BR-TRF-12 | FR-004, FR-005, FR-010, FR-011 |
| BR-LED-01 | FR-005, FR-008, FR-013 |
| BR-LED-02 | FR-003, FR-005, FR-010, FR-013 |
| BR-LED-03 | FR-005, FR-008, FR-010, FR-013 |
| BR-LED-04 | FR-005, FR-011 |
| BR-LED-05 | FR-010, FR-013, NFR-001 |
| BR-LED-06 | FR-004, FR-005, FR-006, FR-007, FR-011 |
| BR-HIS-01 | FR-007, FR-008 |
| BR-HIS-02 | FR-002, FR-003, FR-007, FR-008, FR-013 |
| BR-HIS-03 | FR-007, NFR-002 |
| BR-HIS-04 | FR-006, FR-007, FR-013 |
| BR-ADM-01 | FR-011 |
| BR-ADM-02 | FR-010, FR-011 |
| BR-ADM-03 | FR-011, FR-012, FR-013, NFR-002 |
| BR-ADM-04 | FR-005, FR-006, FR-011 |
| BR-ADM-05 | FR-002, FR-003, FR-010, FR-011, FR-012 |
| BR-ADM-06 | FR-009, FR-013, NFR-003 |
| BR-SIM-01 | FR-004, FR-010, FR-011, UX-001 |
| BR-SIM-02 | FR-001, FR-009, UX-001 |

Implementation tasks and PRs must cite applicable PRD IDs and their UC/BR/AC links. Record automated test identifiers, manual walkthrough evidence, and any evidence gaps against those IDs. Do not create duplicate canonical acceptance IDs for CONS risk views.

### 10.3 Verification approach

- Exercise browser journeys with separate synthetic Customer and Admin sessions, including wrong-owner/wrong-role requests and logout/expiry reuse. Inspect required narrow/desktop states under AC-038.
- Use coordinated concurrent requests to prove competing-debit safety, identical-key handling, payload conflicts, opposite-direction progress, and both effective freeze orderings. Include demo funding and treasury contention in applicable invariant checks.
- Inject failures at financial-entry, balance, final-outcome, and required-audit persistence boundaries. Simulate a committed operation whose response/reference is lost. Verify safe same-key resolution and no unaudited successful change.
- Repeat/concurrently attempt controlled initialization, interrupt setup, restart with changed supply configuration, and replay old committed/rejected keys. Verify no duplicate GENESIS, lost history, or changed supply.
- Verify history traversal beyond 100 records with equal timestamps and intervening commits. Confirm authorized visibility, deterministic continuation, and exclusion of rejected/non-final records.
- Test safe numeric bounds and malformed/fractional inputs. Where configured supply makes resulting-balance overflow unreachable end to end, supplement with isolated numeric validation evidence as AC-016 requires.
- Reconcile balances and supply on consistent committed snapshots after each acceptance workload and the benchmark. Store reports with environment/version and outcome counts. No runtime evidence exists merely because this PRD is written.

## 11. Delivery sequence and release gates

These are suggested delivery slices, not assigned people, calendar commitments, or an implementation design. Financial correctness checks are developed with each applicable slice.

| Slice | Deliverable | Exit evidence |
|---|---|---|
| 1. Design and environment readiness | Resolve EV-001/EV-002 and the section 12 design deliverables; confirm acceptance/benchmark plan | Recorded constraints, design decisions, selected resources, and traceable delivery tasks |
| 2. Identity and Account foundation | FR-001 through FR-003; Customer entry/overview; protected Admin bootstrap; controlled FR-010 initialization | Onboarding, ownership, sessions, Account Numbers, GENESIS, and restart checks |
| 3. Reliable financial Core | FR-004 through FR-006 and FR-011; signed-entry recording; recovery; required allocation audit | Applicable transfer/funding, concurrent request, failure, and reconciliation scenarios |
| 4. Complete presentation and operations | FR-007 through FR-009, FR-012, FR-013, all UX requirements | Committed history/receipts, status ordering/audit, privacy, and responsive Customer/Admin walkthroughs |
| 5. Cloud acceptance | All P0 requirements on the documented deployment | Full AC-001 through AC-038 coverage, CONS evidence, EV-003 measurement, deployment walkthrough, and project-owner acceptance decision |
| 6. Course-driven scaling and optional enhancements | Verified extended load/autoscaling deliverable and selected P1 work after P0 | Separately agreed evidence from EV-001 and preserved financial invariants |

Before claiming V1 acceptance, the release record must show:

- [ ] All P0 functional, experience, and non-functional requirements have linked passing evidence.
- [ ] All 38 canonical AC scenarios and applicable variants are covered; CONS risk views have no evidence gaps.
- [ ] Required workloads show zero financial-invariant violations, including concurrency, fault, recovery, initialization, and restart checks.
- [ ] Browser walkthroughs cover both roles, responsive layouts, empty/error/frozen/uncertain states, and authorization boundaries.
- [ ] EV-001 and EV-002 have evidence and any baseline conflicts have an explicit project-owner resolution.
- [ ] EV-003 has a documented benchmark, complete outcome distribution, invariant checks, and measured comparison with the provisional target. Any deviation has a recorded owner decision rather than a false pass claim.
- [ ] Reproducible container/cloud instructions, protected configuration, persistent storage, automated checks, code-review history, and deployment records are available.
- [ ] The release scope and any course-driven follow-on obligations are recorded; the project owner has made the release acceptance decision.

## 12. Dependencies, design deliverables, and risks

### 12.1 Evidence dependencies

The [backlog readiness matrix](10-product-backlog.md#member-implementation-readiness--2026-10-22) records named task assignments; the roles below describe evidence responsibility. Evidence status remains inherited from discovery; approval of this PRD does not assert completion.

| Item | Current status / remaining evidence | Responsibility | Gate |
|---|---|---|---|
| EV-001 - Course/deployment requirements | PENDING VERIFICATION of the actual rubric and exact acceptance obligations. No course mandate or provider selection has been verified; capture the rubric, any later technology expectations, and scope/phase implications | Project owner / delivery lead | Before architecture/deployment plan finalization and follow-on course milestone definition |
| EV-002 - Cloud feasibility and resources | PENDING VERIFICATION of provider choice, budget, resources, persistence, HTTPS, and reproducible deployment feasibility | Deployment responsibility | Before resource selection and benchmark execution |
| EV-003 - Performance evidence | AWAITING MEASUREMENT against the provisional target; agree/run section 9 and record results/deviations | Testing / deployment responsibilities | Before performance-acceptance or demo-completion claims |

If verified course requirements conflict with V1 scope or the initial architecture, the project owner must explicitly reconcile the affected milestone and documents. Justify each later infrastructure/technology introduction with a concrete requirement or measured need. Do not silently require Kubernetes, Kafka, Redis, or microservices in the initial Core release.

### 12.2 Required technical-design outputs

| Output | Constraint it must preserve | Needed before |
|---|---|---|
| Numeric representation, supported bounds, and validation | Integral VND, safe signed arithmetic, non-negative balances, no invented commercial cap, consistent browser/API/storage handling | Related contracts and AC-016 tests are finalized |
| Concurrency and atomicity strategy | Single financial commit with required final outcome/audit; valid serial results; no stale-state posting; recoverable opposite-direction contention | Transfer, funding, and status implementation |
| Idempotency payload and recovery contract | Initiator/operation/key scope, full intent comparison, lifetime outcomes, conflict isolation, authorized recovery without a known reference | Transfer/funding contracts and browser recovery implementation |
| Session/credential protections | Approved session direction, adaptive hashing, effective logout/expiry, protected cookies, safe browser mutations | Authentication and protected browser operations |
| History ordering/continuation contract | Default 20/max 100, deterministic ordering, defined eligible traversal set, no omissions/duplicates from ties/new commits | History contracts and AC-025 tests |
| Initialization, persistence, and audit lifecycle | Once-only controlled GENESIS, restart durability, dataset recreation boundary, required durable append-only auditability | Setup, deployment, and retention behavior |
| Deployment and test plan | Verified rubric/resources, Core/channel separation, documented workload/measurement boundary, reproducible evidence | Deployment finalization and release verification |

The corresponding TD-001..016 engineering choices are now selected in the [decision register](../6-governance/25-design-decision-register.md); implementation and proof remain pending within approved policy. Provider/resources still require EV-001/002, and existing V1 browser/product elaborations are now approved for implementation; their executed acceptance remains pending. A-001 through A-014 remain ACCEPTED; OQ-001 through OQ-013 remain CLOSED. This PRD does not reopen them or invent new banking actors/products.

### 12.3 Material risks

| Risk | Product impact | Mitigation / verification |
|---|---|---|
| Duplicate or partial posting under retries/failure | Incorrect balances and misleading receipts | FR-005/FR-006, atomic final outcomes, AC-005/AC-010/AC-011/AC-013/AC-014/AC-033 |
| Concurrent spend or stale ACTIVE check | Negative balances or movement after freeze | Commit-boundary funds/status coordination, AC-007/AC-017/AC-018, consistent snapshot reconciliation |
| Success audit written after the state change | Unattributable funding or status change | Required audit in the same atomic operation, AC-019/AC-029 |
| Restart reseeds treasury or forgets request outcomes | Extra supply or replayed spending | FR-010, lifetime persistence, AC-020/AC-021/AC-034 |
| History or receipt leaks another Customer's data | Broken ownership boundary | Server authorization on all views, replay, and lookup; AC-002/AC-024/AC-030 |
| UI encourages a new intent after timeout | Accidental repeated payment | Retain original key, distinguish uncertainty from rejection, UX-004 and AC-011 |
| Benchmark dominated by rejections or inadequate load | False performance acceptance | Funded deterministic workload, offered/achieved rates, separate outcome counts and completed latency, EV-003 |
| Course requirements arrive after design | Rework or incomplete course delivery | Verify EV-001 early, schedule justified scaling work, record explicit scope/architecture decisions |
| Scope or evidence claims exceed the demo | Unreviewed banking features or false readiness | Preserve exclusions, synthetic data, scoped approval/measurement status, and release evidence gates |

**Review outcome — 2026-10-22:** Tiến explicitly APPROVED this existing V1 product/delivery requirements baseline for implementation. No additional stakeholder signature is implied. Implementation, runtime/security/deployment verification and measured performance remain outstanding; final release acceptance is NOT YET GRANTED. Future business-policy changes and final release acceptance retain their distinct project-owner authority.
