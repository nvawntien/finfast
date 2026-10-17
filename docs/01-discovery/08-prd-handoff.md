# 08 — FinFast V1 Business Discovery: PRD Handoff

> **Product:** FinFast Core Banking Platform\
> **Release:** FinFast V1 Core Banking Foundation\
> **Status:** Discovery baseline ready for PRD authoring — not the PRD\
> **Decision authority:** Project owner, 2026-10-16\
> **Version:** 1.0 | **Last updated:** 2026-10-16

## 1. Purpose and approved baseline

This document packages the finalized discovery inputs for the next PRD phase. FinFast is an extensible Core Banking Platform independently developed from scratch. Its long-term vision includes an independently usable Core, Customer Web Banking, Mobile Banking, and Admin/Back-office applications using the Core's financial capabilities.

V1 is the **Core Banking Foundation**, limited to simulated funds, Customer/Account management, internal transfers, financial recording, consistency, and administrative operations. It does not provide real money movement, actual settlement, or licensed banking services. Approval comes from the project owner's supplied finalization decisions; no additional team approval or completed implementation is claimed.

Source documents remain available:

1. [01 — Product Vision and V1 Scope](./01-product-vision-and-scope.md)
2. [02 — Domain Glossary](./02-domain-glossary.md)
3. [03 — Actors and Stakeholders](./03-actors.md)
4. [04 — Use Case Catalogue and Acceptance Scenarios](./04-use-cases.md)
5. [05 — Business Process Flows](./05-business-processes.md)
6. [06 — Business Rules and Domain Invariants](./06-business-rules.md)
7. [07 — Assumptions, Questions, and Decision Log](./07-assumptions-and-questions.md)

The PRD must preserve these approved meanings, identifiers, and scope boundaries. Any policy change needs an explicit new project-owner decision rather than an inferred feature expansion.

## 2. Final V1 business capability inventory

| Mandatory capability | Approved behavior | Use-case coverage |
|---|---|---|
| Customer registration and identity | Display name/email/password only; normalized unique email; secure adaptive hashing; CUSTOMER privileges; separately provisioned Admin identities | UC-AUTH-01, UC-AUTH-02 |
| Authentication and authorization | Secure sessions, effective logout, server-side role and resource checks | UC-AUTH-02 and every protected use case |
| Customer Account provisioning and overview | Exactly one zero-balance VND Account per Customer; unique immutable number; owned account/status/balance access | UC-AUTH-01, UC-ACC-01 |
| Internal transfers | Positive integral VND between different ACTIVE Customer Accounts, within funds and safe numeric bounds; synchronous processing | UC-TRF-01 |
| Financial recording and consistent outcomes | Signed-entry Ledger, immutable references/entries, atomic financial effects, safe concurrency, lifetime idempotency outcomes and recovery | UC-TRF-01, UC-ADM-02; inspection through UC-TRF-02, UC-ADM-04 |
| Financial history and receipts | Owned committed movements only; stable newest-first pagination, 20 default/100 maximum; retained for dataset lifetime | UC-ACC-02, UC-TRF-02 |
| Administrative Account inspection | Necessary account/customer metadata only; no password/unnecessary sensitive data exposure | UC-ADM-01 |
| Demo funding | Authorized movement from finite Treasury Account with reason, two entries, atomic required success audit and final outcome | UC-ADM-02 |
| Freeze/unfreeze | Only ACTIVE/FROZEN; non-empty reason, atomic success audit; owner retains authentication/read access; repeat status returns UNCHANGED | UC-ADM-03 |
| Transaction/audit inspection | Authorized financial and operational views remain distinct; funding links to its audit record; records support reconciliation verification | UC-ADM-04 |
| Controlled dataset initialization | One Treasury Account and one auditable GENESIS; default 100,000,000 VND, configurable before initialization; no normal reset/mint function | Operational prerequisite of UC-ADM-02, verifiable through UC-ADM-04 |

**Presentation deliverable:** Minimal responsive Customer Web (registration/login, overview/balance, internal transfer, history/receipt) and Admin/Back-office (account listing, funding, freeze/unfreeze, transaction/audit inspection). One frontend is permitted with proper role separation. Core banking capabilities are independent of presentation channels; no additional banking feature is introduced to support the interfaces. AC-038 covers these journeys.

## 3. Approved actors and Account types

| Concept | Approved boundary |
|---|---|
| Customer / CUSTOMER | Registered Customer linked to a User identity; only their own Account and participating movements are private resources they may access |
| Bank Administrator / ADMIN | Separately provisioned identity authorized for the listed Admin operations; no unrestricted database or balance-editing privilege |
| CUSTOMER Account | Customer-owned; exactly one per Customer in V1; zero opening balance |
| TREASURY Account | One system-owned internal Account per dataset; authorized demo funding only after controlled GENESIS; no direct Customer access/management |

Only ACTIVE and FROZEN are lifecycle states. FROZEN blocks sending and receiving, including demo allocation, while preserving the owner's login and read-only access. There is no separate Auditor/Support application role. Evaluators and deployment responsibilities are stakeholders; controlled initialization is not a new banking actor or Admin self-service use case.

## 4. Approved business rules and financial invariants

The canonical rule definitions and per-rule traceability are in [06 — Mandatory Rule Coverage](./06-business-rules.md#mandatory-rule-coverage). All **38 rules are P0**:

| Rule family | Approved IDs | Count | Subject |
|---|---|---:|---|
| Identity | BR-ID-01–BR-ID-02 | 2 | Registration, secure credentials, authorization, effective logout |
| Account | BR-ACC-01–BR-ACC-06 | 6 | Ownership/provisioning, lifecycle, non-negative balances, VND, type/number separation |
| Transfer | BR-TRF-01–BR-TRF-12 | 12 | Eligibility, atomicity, idempotency, concurrency, deterministic recovery, safe bounds |
| Ledger | BR-LED-01–BR-LED-06 | 6 | Append-only records, reconciliation, references, balanced movements, GENESIS, rejection boundaries |
| History | BR-HIS-01–BR-HIS-04 | 4 | Visibility, stable pagination/retention, separation of rejected requests |
| Administration | BR-ADM-01–BR-ADM-06 | 6 | Funding, finite treasury, atomic audit, idempotency, access limits |
| Simulation | BR-SIM-01–BR-SIM-02 | 2 | Simulated funds and synthetic data; excluded capabilities |

The PRD must preserve these invariants:

- **No negative balances:** neither Customer nor Treasury can spend unavailable funds. Amounts and resulting balances remain within supported safe numeric bounds.
- **Positive amount versus signed delta:** an ordinary transaction for A > 0 creates exactly two entries, −A for source and +A for destination. Their sum is zero. BR-TRF-07 identifies the Customer transfer participants; BR-LED-04 supplies the common two-entry/zero-sum invariant for transfers and funding.
- **Atomic financial operation:** Financial Transaction, Ledger Entries, balance changes, and final idempotency outcome commit together. Funding includes its required complete success Admin Audit Event; a failure in any required element prevents partial success.
- **Atomic administrative status change:** freeze/unfreeze and its required success audit commit together, with a non-empty reason and before/after state. If required audit fails, status is unchanged.
- **Controlled GENESIS:** exactly one committed initialization per dataset, with one positive treasury entry and stable reference, amount, VND, timestamp, and verifiable record. It is the sole exception to ordinary zero-sum posting and does not repeat on restart.
- **Reconciliation:** each Account balance equals its committed signed deltas from a zero pre-initialization baseline, including the treasury GENESIS entry exactly once. Treasury balance plus all Customer balances always equals initial GENESIS supply after initialization.
- **One final outcome per intent:** initiator + operation type + key defines scope; concurrent identical intents cannot post twice, changed payload conflicts, and identical final business rejections replay even after conditions change. New attempts need new keys. Final outcomes last for the dataset lifetime.
- **Safe recovery:** timeout does not prove failure; recover with the original key or authorized lookup. Technical failure is not a permanently cached business rejection. COMPLETED is reported only after commit.
- **No postings on rejection:** a rejected Transfer Request/funding request has no Financial Transaction, Ledger Entry, or balance change. Request outcomes and security/Admin audit evidence are separate from financial history.
- **Frozen-state ordering and ownership:** new movements cannot commit after an effective freeze; previously committed movements remain valid/readable. Private Account/history access is authorized server-side.

The ledger is a **Signed-entry Ledger**, not a full double-entry General Ledger. Balance reconciliation is verification of these invariants, not an accounting-report product.

## 5. Use-case inventory

All ten existing use cases retain their identifiers and are P0. Full flows, alternatives, and rules are in [04](./04-use-cases.md).

| ID | Use case | Actor |
|---|---|---|
| UC-AUTH-01 | Register and provision an Account | Customer |
| UC-AUTH-02 | Sign in and sign out | Customer/Admin |
| UC-ACC-01 | View Account and balance | Customer |
| UC-ACC-02 | View Account transaction history | Customer |
| UC-TRF-01 | Execute an internal simulated transfer | Customer |
| UC-TRF-02 | View a transfer receipt | Participating Customer |
| UC-ADM-01 | List Customer Accounts | Admin |
| UC-ADM-02 | Allocate demo funds | Admin |
| UC-ADM-03 | Freeze/unfreeze a Customer Account | Admin |
| UC-ADM-04 | Review Financial Transactions and Audit Events | Admin |

Processes BP-01–BP-05 are retained; BP-06 documents controlled GENESIS. No normal application initialization/reset endpoint or new operator role is implied by the operational prerequisite.

## 6. Acceptance coverage and evidence boundary

[04 — Acceptance Scenarios](./04-use-cases.md#4-acceptance-scenario-matrix) contains **38 unique canonical scenarios**. AC-001–AC-013 are preserved and clarified; AC-014–AC-038 are added. [06](./06-business-rules.md#7-high-risk-consistency-scenarios) retains CONS-01–CONS-08 and adds CONS-09–CONS-19 as risk views pointing to AC scenarios, not duplicate tests.

| Coverage area | Canonical scenarios |
|---|---|
| Provisioning, identity, session termination, immutable Account Numbers | AC-001, AC-027, AC-028, AC-035 |
| Ordinary transfer/funding, insufficient funds, history/receipt facts | AC-003, AC-004, AC-008, AC-012, AC-031, AC-032, AC-036 |
| Retry, payload conflict, concurrent duplicate transfer/funding, final rejection replay, scope/lifetime | AC-005, AC-006, AC-013, AC-014, AC-015, AC-034 |
| Competing debits and opposite-direction transfers | AC-007, AC-017 |
| Ledger failure, lost response/timeout, technical recovery | AC-010, AC-011, AC-033 |
| Numeric validation and overflow | AC-016 |
| Freeze ordering, atomic audit failure, read-only frozen owners, reasons/UNCHANGED | AC-009, AC-018, AC-019, AC-026, AC-029 |
| Once-only GENESIS, restart, reconciliation, supply preservation | AC-020, AC-021, AC-022, AC-023 |
| Unauthorized Account/treasury/history/Admin access and data minimization | AC-002, AC-024, AC-030 |
| Stable pagination and committed-only visibility | AC-015, AC-025 |
| Scope and responsive presentation channels | AC-037, AC-038 |

Every mandatory BR maps to at least one use case and AC scenario. Acceptance/concurrency tests must report **zero financial-invariant violations**. This handoff defines coverage and required evidence; it does not assert that application, browser, security, concurrency, or performance tests have run.

## 7. Explicit V1 exclusions and optional work

- Real money, real bank rails, interbank payments, external gateways, clearing/settlement, redemption or customer cash-out.
- Savings/interest products, Lending, Cards, FX/multiple currencies, fees, overdrafts, standing orders, scheduled transfers, reversals/chargebacks.
- Full General Ledger, chart of accounts, accounting periods/reports, branch operations, multi-tenancy, product catalogues.
- Real KYC/AML, identity-document verification, fraud models, regulatory/tax compliance services, or real banking/payment credentials.
- Additional Customer-created Accounts, Account closure/additional lifecycle states, native mobile development, and production high availability/SLA claims.
- Normal treasury reset/replenishment/minting, direct balance overwrite, and complex persisted transfer state machines.

P1 only after P0 acceptance: notifications, advanced history filtering, basic metrics/account search improvements, and optional extended load/autoscaling demonstrations where justified. Notifications cannot block V1 and do not require a message broker. Baseline concurrency and the provisional performance workload are already required verification work, not optional P1 features.

## 8. Initial non-functional expectations and targets

These are approved discovery inputs for PRD elaboration, not a completed PRD or measured implementation results.

| Area | Initial expectation / target | Evidence needed later |
|---|---|---|
| Financial correctness | Zero invariant violations in acceptance/concurrency tests; atomic posting/audit, no duplicates/negative balances, conserved supply | AC/CONS test evidence, fault injection, per-account reconciliation and supply checks |
| Durability and recoverability | Committed financial history and final idempotency outcomes survive restart for dataset lifetime; required success audit is durable; unknown outcomes resolve safely | Restart, lost-response, ledger/audit failure, and retry scenarios |
| Security | Adaptive one-way password hashing, HTTPS, secure server-managed sessions with protected HttpOnly cookies, server-side role/resource authorization, effective logout, minimum necessary data access | Credential/session configuration review plus positive/negative authorization checks; framework-specific details in design |
| Performance | **Provisional transfer-request p95 <= 2 seconds at 10 requests/second for 5 minutes** on a documented demo environment | EV-003 benchmark; target awaits measurement and deployed-resource validation, not a production SLA |
| Presentation | Minimal responsive Customer and Admin Web journeys with role separation; Core independent of channels | AC-038 walkthrough at documented narrow/desktop viewports; no native mobile requirement |
| Deployment and reproducibility | Containerized application, persistent PostgreSQL, environment-based configuration, automated checks, reproducible cloud deployment | Documented deployment/resources, test records, and end-to-end walkthrough after EV-001/EV-002 |
| Traceability | Approved use cases → business rules → acceptance criteria → future PRD requirements and implementation tasks | Preserve identifiers and the canonical coverage matrix; record later policy changes explicitly |

Benchmark evidence should document resource sizing, environment configuration, dataset and initial balances, workload mix/concurrency, measurement boundaries, achieved request rate, p95, and errors/timeouts/non-final responses. At the target rate/duration there are 3,000 submitted requests. Report outcome distribution and verify financial invariants; quick rejections or non-final replies must not be misreported as completed transfers. The PRD/test plan will define the exact reproducible workload without claiming results in advance. No additional numeric availability SLA is approved here.

## 9. Dependencies and constraints for PRD authoring

**Approved initial architecture direction:** Java/Spring Boot, PostgreSQL, domain-oriented Modular Monolith, presentation-independent Core; secure server-managed browser sessions with protected HttpOnly cookies over HTTPS. No mandatory Kafka, Redis, Kubernetes, or microservices. These are constraints, not detailed banking rules.

**Settled policy:** A-001–A-014 are ACCEPTED; OQ-001–OQ-013 are CLOSED. DEC-001–DEC-008 in [07](./07-assumptions-and-questions.md#4-finalized-decision-log) distinguish business decisions, architecture direction, external evidence, and targets awaiting measurement.

| Follow-up | Status and gate | Effect on PRD |
|---|---|---|
| EV-001 — Actual external course/deployment requirements | PENDING VERIFICATION; before architecture/deployment plan finalization | Carry evidence dependency; do not invent platform/actor requirements or silently alter approved policies |
| EV-002 — Cloud resources, budget, storage, HTTPS, deployment feasibility | PENDING VERIFICATION; before resource selection/benchmark | Document constraints and selected environment when verified |
| EV-003 — Provisional performance target | AWAITING MEASUREMENT; before performance acceptance/demo-completion claims | Carry target and test methodology; record deviations for owner review |
| Exact numeric representation and bounds | Later design; before related contracts/tests are finalized | Enforce safe bounds without inventing a commercial transfer cap |
| Session/cookie configuration, concurrency coordination, stable pagination continuation | Later design within accepted direction | Define mechanisms without adding banking states, held-funds products, or new capabilities |

No external payment system is required for core V1. Source-code research does not impose architecture or traceability: Apache Fineract may be a general reference only. Do not create source-code mapping requirements or copy implementation. Schema, migrations, REST contracts, packages, Java classes, and authentication implementation remain outside this deliverable.

## 10. Future product domains, not committed to V1

The independently usable Core and broader Web Banking, Mobile Banking, and Back-office vision may later support new domains. Savings, Lending, FX, Cards, Interbank Transfers, and advanced General Ledger are possible future areas, **not approved V1 capabilities, scheduled releases, or commitments to implement**. Each needs separate discovery and owner prioritization. Native mobile belongs to the long-term channel vision only.

## 11. PRD authoring readiness checklist

- [x] Long-term platform vision is separated from the simulated-funds V1 foundation.
- [x] Mandatory capabilities, two roles, two Account types, and two lifecycle states are explicit.
- [x] Existing seven discovery documents are retained; terminology and rules are consistent across them.
- [x] All 38 P0 BR rules have use-case and canonical acceptance coverage; original UC/AC/CONS identifiers are preserved.
- [x] GENESIS, supply conservation, signed deltas, atomic funding/audit, concurrent idempotency, and uncertain outcomes are covered.
- [x] Financial history, rejected request outcomes, and Admin/security audit evidence are clearly distinct.
- [x] A-001–A-014 are ACCEPTED and OQ-001–OQ-013 are CLOSED with project-owner authority and date.
- [x] Initial architecture direction, reliability expectations, and provisional performance target are explicit; implementation choices remain deferred.
- [x] External verification and performance measurement have named tracking items and gates, without fabricated facts or results.
- [x] Scope exclusions and optional P1 work are explicit; no full GL or unapproved banking domain is added.
- [x] Relative Markdown links, identifier references/uniqueness, and coverage are checked as documentation quality gates.

**Readiness result:** PASS for starting PRD authoring. This is not PRD approval, implementation acceptance, deployment readiness, or proof of measured performance. EV-001–EV-003 and later design/test evidence remain required at their stated gates.

**Next phase:** Write the PRD from this approved discovery baseline; do not treat this handoff as the PRD itself.
