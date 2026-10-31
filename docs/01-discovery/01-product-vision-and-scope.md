# 01 — Product Vision and V1 Scope

> **Project:** FinFast Core Banking Platform\
> **Stage:** Phase 1 — Domain Discovery\
> **Status:** Approved V1 business discovery baseline — project owner decision\
> **Version:** 1.0 | **Last updated:** 2026-10-16

> **Source transition:** The maintained product vision and durable V1 scope now live in [docs/product/vision.md](../product/vision.md). This approved discovery document is retained as migration provenance, not as a second maintained product authority. Its approved scope is represented in the successor; detailed behavioral obligations remain in their operative sources.

## 1. Product summary

**FinFast Core Banking Platform** is an extensible platform independently developed from scratch using Java and Spring Boot. Its long-term vision includes an independently usable Core Banking Platform, Customer-facing Web Banking, Mobile Banking, and an administrative Back-office application. All banking applications use the financial capabilities of FinFast Core, which remain independent of presentation channels.

**FinFast V1 Core Banking Foundation** is the first release, not the final platform feature set. It focuses on Customer management, Account management, internal transfers, financial recording, consistency, and administrative operations, with minimal Customer Web and Admin/Back-office interfaces.

**V1 processes simulated funds only. It does not store, move, settle, or redeem real money**, and provides no licensed banking services. The educational/demo release is not a production-ready bank. Real identity verification and regulatory controls are outside V1.

## 2. Problem and motivation

FinFast establishes an independently authored core that can support future banking channels and domains. The team also wants to translate business operations into testable requirements, implement transaction-sensitive functionality correctly, and deliver a working cloud application through a collaborative software development lifecycle (SDLC). Simulated funds allow ownership, balances, auditability, duplicate requests, and concurrency to be tested without integrating real financial networks.

## 3. Users and outcomes

| Persona | Primary goal | Successful outcome |
|---|---|---|
| Customer | Manage a simulated payment account | Can sign in, see current balance, transfer money, and review transactions |
| Bank Administrator | Operate the educational environment | Can view customer accounts, distribute demo funds, freeze/unfreeze accounts, and inspect audit records |
| Course evaluator / demo observer | Verify correct behavior and cloud delivery | Can reproduce happy paths, failures, and deployment/test evidence |

The course evaluator is a stakeholder, **not** an application authorization role.

## 4. V1 functional scope

### Must have (P0)

1. **Registration and authentication.** Display name, case-normalized unique email, and password only; secure password hashing, login/effective logout, and server-side role/resource authorization. Admin identities are provisioned separately through a protected process.
2. **Automatic virtual account provisioning.** Exactly one VND-denominated customer account created with each successful registration; opening balance is zero.
3. **Account overview.** Customer can see their own unique, immutable, system-generated account number, status, currency, and current balance. Account numbers are never reassigned and are separate from User IDs and Customer IDs.
4. **Internal transfers.** Customer can transfer a positive whole-VND amount to a different ACTIVE Customer Account within FinFast Core.
5. **Reliable financial recording.** A Signed-entry Ledger records Financial Transactions; all financial effects and final idempotency outcomes commit atomically. Concurrent spending cannot overdraw; concurrent duplicates cannot send twice; invalid or overflowing values are rejected safely. GENESIS is a distinct initialization event.
6. **Transaction history and receipts.** Customers can view only committed movements involving their own account, with date, amount, direction, counterparty, and stable Transaction Reference. Recent-first stable pagination is mandatory: default 20, maximum 100 records per page; history persists for the V1 dataset lifetime.
7. **Admin operations.** Admin can list necessary Customer Account metadata, allocate funds from the finite Treasury Account, freeze/unfreeze Customer Accounts with a non-empty reason, and inspect authorized transaction/audit records. Required success audit records commit atomically with funding or account status changes.
8. **Cloud deployability.** Containerized application, persistent database, environment-based configuration, automated checks, and reproducible deployment.
9. **V1 presentation channels.** A minimal responsive Customer Web interface provides registration/login, account overview/balance, internal transfer, history, and receipt. An Admin/Back-office interface provides account listing, demo funding, freeze/unfreeze, and transaction/audit inspection. They may share one frontend application with proper role separation; they add no further banking capabilities.

### Could have (P1, only after P0 passes acceptance)

- Asynchronous in-app notification of completed transfers.
- Filtered transaction history, basic metrics dashboard, or account search enhancements.
- Extended load testing and an autoscaling demonstration if external course requirements justify them (EV-001 verification remains pending). The initial performance and financial-invariant checks below are already part of the V1 acceptance baseline.

### Explicitly out of scope

- Real money, deposit/withdrawal via bank rails, interbank transfers, settlement, external payment gateways.
- Savings products, Lending, Cards, standing orders, fees, interest, FX/multiple currencies, overdrafts, scheduled transfers, reversals/chargebacks.
- Full General Ledger, chart of accounts, accounting periods/reports, branch operations, multi-tenancy, bank product catalogues.
- Real KYC, identity-document capture, AML screening, fraud models, tax or regulatory compliance.
- Mobile native apps, customer-created additional accounts, account closure, production high availability.
- Treasury reset/replenishment or money-minting through normal application operations; complex persisted transfer state machines.

## 5. Approved V1 simplifications

The following are **project-owner-approved V1 choices**, not universal banking requirements. All A-001–A-014 are ACCEPTED in [07 — Assumptions and Questions](./07-assumptions-and-questions.md), which also records the decision authority and date.

| ID | Approved simplification | Reason |
|---|---|---|
| A-001 | Customer has one virtual account | Reduces ownership/account-selection complexity |
| A-002 | VND only, integer monetary amounts | Avoids floating-point and FX ambiguity |
| A-003 | Only ACTIVE and FROZEN account states | Narrow account lifecycle |
| A-004 | Internal customer-to-customer transfers only; no self-transfers | Clear consistency boundary |
| A-005 | No fees, interest, FX, negative balances, overdrafts, or independent commercial cap; funds and safe numeric bounds apply | Focus on basic transfer invariants |
| A-006 | Admin funding comes from one finite virtual treasury | Every normal money movement has an auditable source |
| A-007 | One controlled, auditable GENESIS per dataset creates the finite treasury supply | Distinguishes initialization from ordinary zero-sum movements |
| A-008 | Frozen accounts can neither send nor receive funds (including allocations) | Simple deterministic rule |
| A-009 | Transfers are synchronous in V1 | Avoids premature distributed-transaction complexity |
| A-010 | Admin identities are provisioned through protected bootstrap/administrative procedures | Avoids admin self-registration risks |
| A-011 | Frozen account owners retain login and read-only account/history access | Separates banking status from authentication |
| A-012 | Financial history contains committed movements only | Separates postings from rejected request outcomes |
| A-013 | Domain-oriented Modular Monolith; Core independent of channels | Approved initial architecture direction, not a banking rule |
| A-014 | Customer opening balance is zero; finite treasury supply defaults to 100,000,000 VND, configurable before GENESIS only | Repeatable initialization and supply conservation |

## 6. Illustrative customer journey

1. Alice registers; the system creates her customer profile and zero-balance account.
2. Bob registers and receives a second zero-balance account.
3. An admin transfers 1,000,000 VND from the demo treasury into Alice's account, recording a reason.
4. Alice logs in, reads her account and available balance, and transfers 200,000 VND to Bob.
5. Both account balances, the Financial Transaction, Ledger Entries, and final idempotency outcome commit together.
6. Alice sees an outgoing movement of 200,000 VND; Bob sees an incoming movement of 200,000 VND.
7. A repeated or concurrent submission of Alice's same Transfer Request in the same idempotency scope returns the same final result without creating another Financial Transaction.

## 7. Product-level success criteria

V1 is successful when the team can demonstrate:

- A complete customer and administrator journey deployed on the selected cloud platform.
- Zero financial-invariant violations in acceptance and concurrency tests: no negative balances, partial postings, duplicated movements, or changes to the initial simulated supply after GENESIS.
- A durable, attributable history of completed transfers and demo allocations.
- Role-based access control preventing one customer from viewing or transferring from another customer's account.
- Documentation linking **use cases → business rules → acceptance tests → implementation tasks**.
- Pull requests, code reviews, automated tests, deployment records, and an end-to-end walkthrough.

The provisional performance target is **transfer-request p95 <= 2 seconds at 10 requests/second over 5 minutes** on a documented demo environment. This is a PRD/testing target awaiting measurement and validation against deployed resources, not a measured result or production SLA. External course/deployment requirements and cloud budget remain verification dependencies; see [07](./07-assumptions-and-questions.md) and [08 — PRD Handoff](./08-prd-handoff.md).

## 8. Boundaries and dependencies

- **External systems:** None are required for core V1 operations.
- **Trust boundary:** Browser/client requests are untrusted. The server verifies identity, ownership, and account state before changing money records.
- **Data:** Demo/synthetic customers only; never collect banking credentials or government identity documents from real people.
- **Approved initial architecture direction:** Java and Spring Boot, PostgreSQL, and a domain-oriented Modular Monolith with channel-independent Core capabilities. No mandatory Kafka, Redis, Kubernetes, or microservices. Browser authentication uses secure server-managed sessions with protected HttpOnly cookies over HTTPS; framework details belong to architecture design.
- **Infrastructure verification:** Choose the deployment provider/resources after verifying external assignment constraints and cost. Unverified external requirements do not silently replace approved product choices.
- **Treasury:** One system-owned Treasury Account per dataset; default initial supply 100,000,000 VND, configurable only before first GENESIS. Existing datasets cannot change initial supply through normal operations. Demo reset requires separately controlled dataset recreation.

## 9. Phase 1 exit criteria

- [x] Project owner has approved the product vision and P0/P1/out-of-scope separation; no other team approval is implied.
- [x] Glossary, actor map, use cases, flows, rules, and acceptance scenarios express the same V1 baseline.
- [x] A-001–A-014 are ACCEPTED and OQ-001–OQ-013 are CLOSED; remaining external checks and measurements have explicit gates.
- [x] [08 — PRD Handoff](./08-prd-handoff.md) provides the input and readiness checklist for PRD authoring, not a PRD or final technical design.

## 10. Research starting points

These are **references for understanding**, not specifications to copy or a requirement to use their implementation.

- [Apache Fineract — main repository](https://github.com/apache/fineract)
- [Apache Fineract — platform documentation](https://fineract.apache.org/docs/stable/)
- [Apache Fineract — consumer-facing project](https://github.com/apache/fineract-consumer-facing)

**Current product authority:** [Product vision](../product/vision.md) · [Product requirements](../product/prd.md) · [Domain glossary](../product/glossary.md)

**Provenance and behavior:** [07 - Assumptions and Questions](./07-assumptions-and-questions.md) · [04 - Use Cases](./04-use-cases.md) · [06 - Business Rules](./06-business-rules.md)
