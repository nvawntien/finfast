# FinFast product glossary

> **Authority:** Maintained product glossary for FinFast V1. Definitions describe FinFast's vocabulary, not universal banking rules. Detailed business rules and scenarios remain in [06](../01-discovery/06-business-rules.md) and [04](../01-discovery/04-use-cases.md); exact API schemas and Java types remain in their contract sources.

## Identity and access

| Term | FinFast V1 meaning |
|---|---|
| User | An identity that authenticates to the application; it is not itself an Account |
| Customer | A simulated retail banking customer associated with a User and one Customer Account |
| Bank Administrator | A privileged application user who operates the demo environment through a separately provisioned identity |
| Authentication | Verifying who a User is; authentication alone does not grant unrestricted Account access |
| Authorization | Deciding which actions and resources an authenticated User may access; enforced by the server |
| Ownership | The relationship between a Customer and their Account, used to determine permitted access |
| Authenticated Session | A server-managed authentication context for a User; the approved browser direction uses protected HttpOnly cookies over HTTPS |

## Accounts and money

| Term | FinFast V1 meaning |
|---|---|
| Account | A container for simulated VND and participation in financial recording; V1 has Customer and Treasury Accounts |
| Customer Account | A Customer-owned Account; exactly one is provisioned for each Customer and it opens at zero balance |
| Account Number | A unique, immutable, system-generated Account identifier that is never reassigned; it is separate from User/Customer identifiers and is not an authorization credential |
| Account Status | An Account's operational state; V1 supports ACTIVE and FROZEN |
| Currency | The unit of value; V1 uses VND exclusively |
| Transfer Amount | A strictly positive integral VND amount requested or committed for an internal transfer or demo allocation |
| Balance | The current simulated value attributed to an Account; V1 balances cannot be negative |
| Available Balance | The amount currently spendable; simplified V1 behavior makes it equal to Balance because there are no holds or pending settlements |
| Treasury Account | One system-owned finite VND Account per dataset, used for authorized demo funding after initialization; Customers cannot directly access or manage it |
| GENESIS | The controlled, auditable one-time initialization of finite Treasury supply for a dataset; it is not an ordinary money movement |
| Demo Allocation | An Administrator-authorized movement of existing Treasury funds to a Customer Account; it is not a real deposit |
| V1 Dataset | A persisted set of demo identities, Accounts, financial records, request outcomes, and audit records; restart preserves it, while controlled recreation establishes a new dataset |
| Overdraft | Spending beyond available funds; prohibited in V1 |

## Financial movements and evidence

| Term | FinFast V1 meaning |
|---|---|
| Transfer Request | A Customer instruction to move simulated money between two different Customer Accounts; it may commit or receive a final business rejection |
| Transfer | A Customer business operation initiated by a Transfer Request; use Financial Transaction for its committed financial result |
| Internal Transfer | A Transfer between two different Customer Accounts within FinFast Core, without a payment network or Customer access to Treasury |
| Money Movement | An executed ordinary movement of simulated value, such as an internal transfer or demo allocation; GENESIS is a separate initialization |
| Financial Transaction | A committed financial movement with an immutable Transaction Reference; ordinary movements are internal transfers or demo allocations, and GENESIS is the special initialization transaction |
| Ledger Transaction | The grouping of Ledger Entries for a committed Financial Transaction, including the special GENESIS initialization |
| Ledger Entry | An append-only signed change attributed to an Account and linked to a Financial Transaction |
| Signed Ledger Entry Delta | A positive or negative integral VND change attributed to an Account; for an amount A greater than zero, a movement records a debit of A and a credit of A |
| Signed-entry Ledger | FinFast's immutable record of signed Account balance changes; it supports reconciliation and supply checks but is not a full General Ledger |
| Transaction History | A Customer-facing view of committed movements affecting an owned Account |
| Transaction Reference | A unique, immutable identifier for a committed Financial Transaction; it identifies the recorded movement but does not prescribe an encoded format |
| Admin Audit Event | Administrative or security evidence kept distinct from financial postings; a required success event records the actor, action, target, reason, time, outcome, and relevant transaction or before/after status |
| Idempotency Key | A client-supplied token associated with an initiator and operation; the same scoped intent resolves to its retained final outcome for the dataset lifetime |
| Rejected Request | An instruction denied by business validation; it does not create a successful financial posting |
| Atomicity | A committed ordinary movement has all required effects together, or none of them |
| Concurrency Control | A safeguard that prevents simultaneous operations from violating balance and eligibility rules |
| Conservation of Value | Ordinary movements preserve total simulated value; after GENESIS, Treasury and Customer balances equal the initial supply |
| Reconciliation | Comparing an Account balance with its committed signed Ledger changes from the zero baseline, including Treasury GENESIS once |
| Settlement | Completion of obligations through financial infrastructure; not applicable to V1's internal virtual movements |

## Lifecycle and outcome vocabulary

| Term | FinFast V1 meaning |
|---|---|
| Account Provisioning | Creating a zero-balance Customer Account as part of successful onboarding |
| Freeze / Unfreeze | Reasoned Administrator actions that block or restore Customer Account money movements and record required success audit evidence |
| Synchronous Transfer | A Transfer Request that normally returns its committed result in the same interaction |
| Network Retry | A client resending an instruction because the previous response was delayed or lost |
| Committed | A database transaction's changes have been durably accepted |
| Rolled Back | A failed database transaction left no partial money movement |
| Business Rejection | An expected denial such as insufficient funds, a frozen Account, or an invalid recipient |
| Technical Failure | An unexpected infrastructure, process, database, or network problem |
| Unknown Outcome | The client did not receive enough information to determine whether commit occurred; it resolves the original intent safely |
| Non-final Processing Response | A response indicating that an identical request is still resolving; it is not a final rejection |
| UNCHANGED | Result when an authorized status request matches the Account's existing status; no state change or financial posting occurs |

## Related concepts outside V1

Savings products, loans, KYC/AML programs, external payment and settlement, a full institutional General Ledger, and multiple currencies/foreign exchange are valid research concepts but are not V1 commitments.

