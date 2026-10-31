# FinFast product vision

> **Authority:** Maintained authority for FinFast's product vision, durable V1 scope, priorities, users, and approved product decisions.

## Product

FinFast is an independently developed Core Banking Platform. Its long-term vision is a channel-independent financial core that can support Customer Web Banking, Mobile Banking, and administrative Back-office applications.

FinFast V1 Core Banking Foundation is the first limited release. It establishes Customer and Account management, internal transfers, financial recording, consistency, and administrative operations through minimal Customer Web and Admin interfaces. V1 is an educational demonstration, not a production bank or licensed banking service. It processes simulated funds only and uses synthetic customer data.

The product addresses the need to demonstrate understandable account and transfer experiences, traceable administrative operations, correct transaction-sensitive behavior, and reproducible delivery without connecting to real financial networks.

## Users and outcomes

| User or stakeholder | Intended outcome |
|---|---|
| Customer | Sign in, inspect their simulated account, transfer funds to another Customer, and review committed movements and receipts |
| Bank Administrator | Operate the demonstration by viewing necessary Customer account information, allocating demo funds, changing Customer account status, and inspecting financial and audit records |
| Course evaluator or demo observer | Reproduce demonstrations and review behavior, deployment, and test evidence; this stakeholder is not an application authorization role |
| Project and deployment team | Build, initialize, deploy, and verify the product through controlled development and operations procedures; these responsibilities do not add banking roles |

## V1 scope

The P0 release establishes:

- Customer registration, authentication, effective sign-out, and server-enforced role and resource authorization. Administrator identities are provisioned separately.
- Exactly one zero-balance VND Customer Account for each successful Customer registration, with a unique, immutable Account Number.
- Customer account overview and synchronous internal transfers between different eligible Customer Accounts.
- Reliable financial records, safe concurrent operation, durable intent outcomes, and recovery from uncertain responses.
- Customer history of committed movements and authorized receipts.
- One finite Treasury per dataset with controlled one-time GENESIS initialization, reasoned Admin allocations, reasoned freeze/unfreeze, and authorized inspection.
- Responsive Customer and Admin web experiences with role separation.
- Reproducible containerized deployment with persistent storage, automated checks, and required correctness, restart, concurrency, and performance evidence.

All detailed P0 behavior remains required. The summaries here do not replace the [product requirements](prd.md), [canonical use cases and acceptance scenarios](../01-discovery/04-use-cases.md), [business rules](../01-discovery/06-business-rules.md), [functional specifications](../02-requirements/12-functional-specifications.md), or [approved screen and browser journeys](../03-design/14-ux-user-flows-and-e2e.md).

### Follow-on scope

P1 work may be considered only after P0 acceptance:

- Optional asynchronous in-app notifications for completed transfers. Notification failure cannot affect a committed transfer and no message broker is mandatory.
- Advanced history filters, basic metrics, and improved account search.
- Extended load testing or an autoscaling demonstration if external course requirements justify them. The initial correctness and performance checks remain P0.

### Exclusions

V1 does not include:

- Real money, deposits or withdrawals through bank rails, interbank transfers, settlement, payment gateways, redemption, or cash-out.
- Savings, lending, cards, interest, fees, foreign exchange, multiple currencies, overdrafts, scheduled or standing orders, reversals, or chargebacks.
- A full General Ledger, chart of accounts, accounting periods or reports, branch operations, multi-tenancy, or a bank product catalogue.
- Real-world KYC or AML, government identity documents, fraud models, tax or regulatory services, or real banking credentials.
- Customer-created additional accounts, account closure, additional account states, native mobile applications, or production high-availability guarantees.
- Treasury replenishment, reset, or money minting through ordinary application operations, or a complex persisted transfer state machine.

Possible future domains and channels are directions for later discovery, not V1 commitments or dated release promises.

## Enduring product journeys

| Journey | Product outcome |
|---|---|
| Onboard | A Customer registers, signs in, and receives exactly one ACTIVE Customer Account with a zero VND balance |
| Fund and transfer | An Administrator allocates existing demo funds with a reason; a Customer transfers funds to another eligible Customer; both parties can inspect their committed movements |
| Resolve uncertainty | A Customer can resolve a delayed or lost response without creating a second movement |
| Freeze and resume | An Administrator changes a Customer Account's status with a reason; the owner retains permitted read access while new money movements follow the approved status rules |
| Inspect and verify | An Administrator reviews distinct financial and administrative records; the team can restart and reconcile the dataset |

The detailed actor permissions and process obligations remain in [actors](../01-discovery/03-actors.md), [business process flows](../01-discovery/05-business-processes.md), and the canonical behavioral sources linked above.

## Approved V1 decisions and provenance

The following product simplifications were accepted by the project owner on 2026-10-16. They describe FinFast V1 and are not universal banking rules.

| ID | Approved V1 decision |
|---|---|
| A-001 | Each Customer has one virtual Customer Account |
| A-002 | V1 uses VND and integral monetary amounts |
| A-003 | Accounts have only ACTIVE and FROZEN states |
| A-004 | Transfers occur internally between different Customer Accounts; self-transfers are prohibited |
| A-005 | No fees, interest, foreign exchange, negative balances, overdrafts, or independent commercial cap; available funds and safe numeric bounds still apply |
| A-006 | One finite system-owned Treasury per dataset funds normal Admin allocations |
| A-007 | One controlled, auditable GENESIS initializes finite Treasury supply per dataset |
| A-008 | FROZEN Accounts cannot send or receive funds, including allocations |
| A-009 | V1 transfers are synchronous; completion is reported only after commit |
| A-010 | Administrator identities are provisioned through protected procedures, not Customer registration |
| A-011 | Frozen Account owners retain authentication and read-only access to their own account and history |
| A-012 | Customer financial history contains committed movements only; request outcomes and operational evidence remain distinct |
| A-013 | The initial direction is Java/Spring Boot and PostgreSQL in a domain-oriented Modular Monolith, with Core capabilities independent of presentation channels |
| A-014 | Customer Accounts open at zero; initial Treasury supply defaults to 100,000,000 VND and can be configured only before GENESIS |

The [discovery assumptions and decision log](../01-discovery/07-assumptions-and-questions.md) is the provenance for A-001 through A-014, OQ-001 through OQ-013, DEC-001 through DEC-008, and EV-001 through EV-003. The [canonical decision register](../6-governance/25-design-decision-register.md) records technical decisions and the separate [2026-10-22 approval](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) of the existing V1 PRD, UX, and REST implementation baseline. These approvals do not establish runtime verification or release acceptance.

## Product success and evidence dependencies

V1 success requires a complete Customer and Administrator journey; zero financial-invariant violations; durable, attributable movement history; server-enforced resource access; traceability from requirements to acceptance and implementation work; and reproducible deployment, review, and demonstration evidence.

The approved provisional performance target is transfer-request p95 at or below 2 seconds under 10 new requests per second for five minutes on a documented demo environment. Performance acceptance requires measurement under that workload and validation of the environment. The target is not a production service level.

External evidence obligations remain separate from approved product scope:

| Evidence | Product or release obligation |
|---|---|
| EV-001: actual course or deployment rubric | Review the applicable rubric and explicitly reconcile any additional obligation before changing product or platform scope |
| EV-002: cloud provider, budget, resources, storage, HTTPS, and deployment feasibility | Establish feasibility before selecting deployment resources or the benchmark environment |
| EV-003: provisional performance target | Measure against the approved workload and a documented environment before making a performance acceptance claim |

The evidence definitions and approval provenance remain in [discovery](../01-discovery/07-assumptions-and-questions.md), and the benchmark method remains in the [verification strategy](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol).

