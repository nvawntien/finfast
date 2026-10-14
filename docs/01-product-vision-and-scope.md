# 01 — Product Vision and V1 Scope

> **Project:** Digital Banking Simulator  
> **Stage:** Phase 1 — Domain Discovery  
> **Status:** Working draft — proposed assumptions, subject to team and course-rubric approval  
> **Version:** 0.1 | **Last updated:** 2026-10-14

## 1. Product summary

A **cloud-hosted educational digital banking simulator** where registered customers can hold a virtual account, inspect their balance, transfer simulated VND to other customers in the same system, and review a durable transaction history. Authorized administrators manage demo funding and account availability.

**The simulator does not store, move, settle, or redeem real money.** It is not a licensed or production-ready banking service. Real identity verification and regulatory controls are explicitly outside V1.

## 2. Problem and motivation

Our team wants to learn to translate unfamiliar business operations into testable software requirements, implement transaction-sensitive functionality correctly, and deliver a working cloud application through a collaborative software development lifecycle (SDLC). A focused banking simulation provides realistic domain invariants—ownership, balances, auditability, duplicate requests, and concurrency—without integrating real financial networks.

## 3. Users and outcomes

| Persona | Primary goal | Successful outcome |
|---|---|---|
| Customer | Manage a simulated payment account | Can sign in, see current balance, transfer money, and review transactions |
| Bank Administrator | Operate the educational environment | Can view customer accounts, distribute demo funds, freeze/unfreeze accounts, and inspect audit records |
| Course evaluator / demo observer | Verify correct behavior and cloud delivery | Can reproduce happy paths, failures, and deployment/test evidence |

The course evaluator is a stakeholder, **not** an application authorization role.

## 4. V1 functional scope

### Must have (P0)

1. **Registration and authentication.** Email/password registration for customers, login/logout, authorization by role. Admin accounts are provisioned separately, not by self-registration.
2. **Automatic virtual account provisioning.** Exactly one VND-denominated customer account created with each successful registration; opening balance is zero.
3. **Account overview.** Customer can see their own masked or full demo account number, status, currency, and current balance.
4. **Internal transfers.** Customer can transfer a positive whole-VND amount to a different active customer account within the simulator.
5. **Reliable transaction processing.** All balance/ledger effects commit atomically; concurrent spending cannot overdraw; idempotent retries do not send twice.
6. **Transfer history and detail.** Customers can view movements involving their account, including date, amount, direction, counterparty, and reference.
7. **Admin operations.** Admin can list accounts, allocate funds from a pre-seeded internal demo treasury, freeze/unfreeze accounts, and view transaction/audit records.
8. **Cloud deployability.** Containerized application, persistent database, environment-based configuration, automated checks, and reproducible deployment.

### Could have (P1, only after P0 passes acceptance)

- Asynchronous in-app notification of completed transfers.
- Filtered transaction history, basic metrics dashboard, or account search enhancements.
- Controlled load testing and autoscaling demonstration if the course expects it.

### Explicitly out of scope

- Real money, deposit/withdrawal via bank rails, interbank transfers, settlement, external payment gateways.
- Loans, savings interest, credit cards, standing orders, fees, FX/multiple currencies, overdrafts, scheduled transfers, reversals/chargebacks.
- Full accounting general ledger, branch operations, multi-tenancy, bank product catalogues.
- Real KYC, identity-document capture, AML screening, fraud models, tax or regulatory compliance.
- Mobile native apps, customer-created additional accounts, account closure, production high availability.

## 5. Proposed V1 simplifications

The following are **working assumptions, not verified industry requirements**; track them in [07 — Assumptions and Questions](./07-assumptions-and-questions.md).

| ID | Proposed simplification | Reason |
|---|---|---|
| A-001 | Customer has one virtual account | Reduces ownership/account-selection complexity |
| A-002 | VND only, integer monetary amounts | Avoids floating-point and FX ambiguity |
| A-003 | Only ACTIVE and FROZEN account states | Narrow account lifecycle |
| A-004 | Internal customer-to-customer transfers only; no self-transfers | Clear consistency boundary |
| A-005 | No fees, interest, negative balance, or overdraft | Focus on basic transfer invariants |
| A-006 | Admin funding comes from one finite virtual treasury | Every normal money movement has an auditable source |
| A-007 | Treasury is initialized once with a documented genesis event | Makes demo allocation reproducible and distinguishable from customer transfers |
| A-008 | Frozen accounts can neither send nor receive funds (including allocations) | Simple deterministic rule |
| A-009 | Transfers are synchronous in V1 | Avoids premature distributed-transaction complexity |
| A-010 | Admins are provisioned by deployment configuration/seed | Avoids admin self-registration risks |

## 6. Illustrative customer journey

1. Alice registers; the system creates her customer profile and zero-balance account.
2. Bob registers and receives a second zero-balance account.
3. An admin transfers 1,000,000 VND from the demo treasury into Alice's account, recording a reason.
4. Alice logs in, reads her account and available balance, and transfers 200,000 VND to Bob.
5. Both account balances and associated ledger records update together.
6. Alice sees an outgoing movement of 200,000 VND; Bob sees an incoming movement of 200,000 VND.
7. A repeated submission of Alice's same transfer request does not create an additional transfer.

## 7. Product-level success criteria

V1 is successful when the team can demonstrate:

- A complete customer and administrator journey deployed on the selected cloud platform.
- No negative balances or lost/duplicated funds under tested insufficient-funds, retry, crash-before-commit, and concurrent-transfer scenarios.
- A durable, attributable history of completed transfers and demo allocations.
- Role-based access control preventing one customer from viewing or transferring from another customer's account.
- Documentation linking **use cases → business rules → acceptance tests → implementation tasks**.
- Pull requests, code reviews, automated tests, deployment records, and an end-to-end walkthrough.

Quantified service-level targets (latency, availability, load) will be negotiated in the PRD according to the **actual course rubric and cloud budget**, rather than asserted here.

## 8. Boundaries and dependencies

- **External systems:** None are required for core V1 operations.
- **Trust boundary:** Browser/client requests are untrusted. The server verifies identity, ownership, and account state before changing money records.
- **Data:** Demo/synthetic customers only; never collect banking credentials or government identity documents from real people.
- **Infrastructure:** Choose deployment provider and architecture only after validating assignment constraints and practical deployment cost.

## 9. Phase 1 exit criteria

- [ ] Team has reviewed product vision and agreed to P0/P1/out-of-scope separation.
- [ ] Glossary, actor map, main use cases, flows, and rules contain no unresolved contradictions.
- [ ] High-impact open questions have owners and resolution dates.
- [ ] This discovery pack is accepted as the input to the V1 PRD, not mistaken for a final technical design.

## 10. Research starting points

These are **references for understanding**, not specifications to copy or a requirement to use their implementation.

- [Apache Fineract — main repository](https://github.com/apache/fineract)
- [Apache Fineract — platform documentation](https://fineract.apache.org/docs/stable/)
- [Apache Fineract — consumer-facing project](https://github.com/apache/fineract-consumer-facing)

**Next:** [02 — Domain Glossary](./02-domain-glossary.md) · [07 — Assumptions and Questions](./07-assumptions-and-questions.md)
