# 02 — Domain Glossary

> **Stage:** Phase 1 — Domain Discovery  
> **Status:** Working draft — V1 terminology, not an exhaustive banking dictionary  
> **Version:** 0.1 | **Last updated:** 2026-10-14

## 1. Purpose

This glossary gives the team a **shared ubiquitous language** for discussions, PRD requirements, diagrams, API contracts, tests, and implementation. Words marked **SIMPLIFIED** have a narrower meaning in our V1 than in a real bank or Apache Fineract.

## 2. Identity and access

| Term | Working definition in V1 | Important distinction |
|---|---|---|
| **User** | An identity that authenticates to the application | User is not itself the bank account |
| **Customer** | A simulated retail banking customer associated with a user | A customer has one virtual account (A-001) |
| **Bank Administrator** | Privileged application user managing demo operations | Not an external banker or customer |
| **Authentication** | Verifying the user is who they claim to be | Does not grant unrestricted account access |
| **Authorization** | Determining which actions/resources an authenticated user may access | Must be enforced on the server |
| **Ownership** | Relationship between a customer and their account | Required to authorize source-account transfers |
| **Audit Event** | Append-only record of a security-relevant or administrative action | Not a substitute for financial ledger entries |

## 3. Accounts and money

| Term | Working definition in V1 | Example / note |
|---|---|---|
| **Bank Account** | Virtual account that holds a VND balance and participates in movements | May be a CUSTOMER account or internal TREASURY account |
| **Account Number** | Unique, simulator-generated public identifier for transfers | Not a real bank account number |
| **Account Status** | Account's operational state: ACTIVE or FROZEN | Frozen accounts may be viewed but cannot send/receive funds |
| **Currency** | Unit of value for the account | V1 uses VND exclusively (A-002) |
| **Amount** | Non-negative integer quantity of VND for storage; transfers require > 0 | Never use binary floating-point for money |
| **Balance** | Current virtual amount attributed to an account | Cannot be negative under V1 rules |
| **Available Balance** | Amount currently spendable | **SIMPLIFIED:** equals balance in V1; no holds/pending settlements |
| **Demo Treasury** | Special internal account holding the initial pool of simulated funds | Allocations move money from treasury to customer accounts |
| **Genesis Event** | Explicit, one-time creation of the initial demo treasury pool | Exception to ordinary zero-sum internal movements; recorded and auditable |
| **Demo Allocation** | Admin-authorized movement from treasury into a customer account | Not a real deposit or cash-in |
| **Overdraft** | Ability to spend more than available funds | Out of scope; prohibited in V1 |

## 4. Movements, transactions, and records

| Term | Working definition in V1 | Important distinction |
|---|---|---|
| **Transfer** | Customer-initiated instruction to move virtual money between two different customer accounts | Business operation, not necessarily one DB row |
| **Internal Transfer** | Transfer in which source and target both exist in our simulator | Does not involve a payment network |
| **Money Movement** | Executed movement of virtual value; type INTERNAL_TRANSFER or DEMO_ALLOCATION | Can be represented by a shared movement/transaction identifier |
| **Ledger Transaction** | Grouping of ledger entries for one committed money movement | Successful internal movement contains two opposite entries |
| **Ledger Entry** | Append-only signed change allocated to an account | Example: sender −200,000; receiver +200,000 |
| **Transaction History** | Customer-facing list of committed movements affecting an owned account | Derived from trustworthy persisted money records |
| **Transaction Reference** | Stable identifier for an executed transfer/allocation | Enables support, correlation, and idempotent response replay |
| **Idempotency Key** | Client-generated token used to identify one intended request | Same initiator + key + same payload ⇒ one outcome |
| **Rejected Request** | Transfer request denied by business validation | Never changes balances or writes successful ledger entries |
| **Atomicity** | All effects of a committed movement happen together, or none do | No 'debit succeeded, credit failed' final state |
| **Concurrency Control** | Mechanism preventing simultaneous operations from violating balance constraints | Database locks or comparable strategy selected in technical design |
| **Conservation of Value** | For ordinary internal movements, the sum of signed ledger entries is zero | Treasury genesis explicitly increases initial simulated supply |
| **Reconciliation** | Comparing balances and transaction records to detect inconsistencies | Simple verification in V1, not full bank reconciliation |
| **Settlement** | Completion of obligations through financial infrastructure | Not applicable to V1 internal virtual movements |

## 5. Lifecycle and process vocabulary

| Term | Working definition |
|---|---|
| **Account Provisioning** | Creating a zero-balance customer account as part of successful onboarding |
| **Freeze / Unfreeze** | Administrative actions that block/restore money movements on an account |
| **Synchronous Transfer** | A request that normally returns the committed result in the same interaction |
| **Network Retry** | A client resends a request because the previous response was delayed or lost |
| **Committed** | A database transaction's changes have been durably accepted by the database |
| **Rolled Back** | A failed database transaction left no partial money movement |
| **Business Rejection** | An expected denial such as insufficient funds, frozen account, invalid recipient |
| **Technical Failure** | Unexpected infrastructure/process/database/network problem |
| **Unknown Outcome** | Client did not receive a response and cannot infer whether a commit happened; it retries/queries safely using the same idempotency key |

## 6. Related but excluded banking concepts

These are valid research terms, but **not** scope commitments:

- **Savings Product / Interest:** Interest-bearing products and accrual rules.
- **Loan / Repayment:** Borrowing, amortization, interest schedules, and collections.
- **KYC / AML:** Real-world identity verification and anti-money-laundering controls.
- **External Payment / Clearing / Settlement:** Movement between institutions or payment rails.
- **General Ledger (GL):** Full institutional accounting, including chart of accounts, reconciliation, and financial reporting.
- **Multi-currency / FX:** Currency conversion, exchange rates, and associated accounting.

Our simplified **signed-entry ledger** is an auditable transfer record, **not a claim of full bank-grade double-entry accounting**.

## 7. Naming conventions for documentation

- Use **customer** for the retail banking participant; **user** when discussing authentication.
- Use **account** for the container of simulated value; **user balance** is not a separate source of truth.
- Use **transfer** for the customer action; **ledger transaction / ledger entry** for its accounting record.
- Use **demo allocation**, not cash deposit, for administrator-issued funds.
- Use **VND** and integer example amounts (e.g., `200000 VND`), not `float` or localized money strings in APIs.
- Use `ACTIVE` and `FROZEN` consistently in diagrams and rules.

## 8. Research references

- [Fineract — portfolio/client](https://github.com/apache/fineract/tree/develop/fineract-provider/src/main/java/org/apache/fineract/portfolio/client)
- [Fineract — portfolio/savings](https://github.com/apache/fineract/tree/develop/fineract-provider/src/main/java/org/apache/fineract/portfolio/savings)
- [Fineract — portfolio/transfer](https://github.com/apache/fineract/tree/develop/fineract-provider/src/main/java/org/apache/fineract/portfolio/transfer)
- [Fineract — accounting/journalentry](https://github.com/apache/fineract/tree/develop/fineract-provider/src/main/java/org/apache/fineract/accounting/journalentry)

**Related:** [01 — Vision & Scope](./01-product-vision-and-scope.md) · [06 — Business Rules](./06-business-rules.md)
