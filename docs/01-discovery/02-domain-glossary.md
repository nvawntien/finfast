# 02 — Domain Glossary

> **Stage:** Phase 1 — Domain Discovery\
> **Status:** Approved V1 terminology — project owner baseline, not an exhaustive banking dictionary\
> **Version:** 1.0 | **Last updated:** 2026-10-16

> **Source transition:** The maintained product vocabulary now lives in [docs/product/glossary.md](../product/glossary.md). This approved discovery glossary is retained as migration provenance, not as a second maintained glossary. Canonical business rules remain in [06](./06-business-rules.md).

## 1. Purpose

This glossary gives the team a **shared ubiquitous language** for discussions, PRD requirements, diagrams, future API contracts, tests, and implementation. **FinFast Core Banking Platform** is the long-term product; **FinFast V1 Core Banking Foundation** is its limited, simulated-funds first release. Words marked **SIMPLIFIED** have a narrower meaning in V1 than in real banking. These definitions express FinFast's own decisions.

## 2. Identity and access

| Term | Working definition in V1 | Important distinction |
|---|---|---|
| **User** | An identity that authenticates to the application | User is not itself the bank account |
| **Customer** | A simulated retail banking customer associated with a user | A customer has one virtual account (A-001) |
| **Bank Administrator** | Privileged application user managing demo operations | Not an external banker or customer |
| **Authentication** | Verifying the user is who they claim to be | Does not grant unrestricted account access |
| **Authorization** | Determining which actions/resources an authenticated user may access | Must be enforced on the server |
| **Ownership** | Relationship between a customer and their account | Required to authorize source-account transfers |
| **Audit Event / Admin Audit Event** | Append-only security or administrative record; a required success Admin Audit Event identifies administrator, action, target, reason, timestamp, outcome, and transaction reference or before/after state | Distinct from financial postings; required success audit commits atomically with funding/status changes |
| **Authenticated Session** | Server-managed authentication context for a User | Browser direction: protected HttpOnly cookies over HTTPS; logout invalidates the authenticated session |

## 3. Accounts and money

| Term | Working definition in V1 | Example / note |
|---|---|---|
| **Bank Account / Account** | Account holding simulated VND and participating in financial recording | V1 types are `CUSTOMER` and `TREASURY` |
| **Customer Account** | A `CUSTOMER` Account owned by a registered Customer | Exactly one per Customer; opening balance is zero |
| **Account Number** | Unique, immutable, system-generated account identifier; never reassigned | Separate from User IDs and Customer IDs; not a real bank account number or an authorization credential |
| **Account Status** | Account's operational state: ACTIVE or FROZEN | Frozen accounts may be viewed but cannot send/receive funds |
| **Currency** | Unit of value for the account | V1 uses VND exclusively (A-002) |
| **Transfer Amount** | Strictly positive integral VND value requested or committed for an internal transfer or demo allocation | Distinct from a signed delta; must fit supported safe numeric bounds |
| **Signed Ledger Entry Delta** | Positive or negative integral VND change to an Account balance | For amount A > 0, source delta = −A and destination delta = +A; no binary floating-point rounding |
| **Balance** | Current virtual amount attributed to an account | Cannot be negative under V1 rules |
| **Available Balance** | Amount currently spendable | **SIMPLIFIED:** equals balance in V1; no holds/pending settlements |
| **Treasury Account (demo treasury)** | One system-owned `TREASURY` Account per dataset, used exclusively for authorized demo funding after initialization | Customers cannot directly access/manage it or use it as a transfer source/recipient |
| **GENESIS** | One controlled, auditable initialization per V1 dataset; special Ledger Transaction with one positive treasury initialization entry | Establishes finite supply; default 100,000,000 VND configurable only before initialization; not an ordinary zero-sum movement |
| **Demo Allocation** | Admin-authorized movement from treasury into a customer account | Not a real deposit or cash-in |
| **Overdraft** | Ability to spend more than available funds | Out of scope; prohibited in V1 |
| **V1 Dataset** | One persisted set of FinFast demo identities, accounts, financial records, request outcomes, and audit records | Restart preserves the dataset; controlled recreation creates a new dataset, not extra supply in the existing one |

## 4. Movements, transactions, and records

| Term | Working definition in V1 | Important distinction |
|---|---|---|
| **Transfer Request** | Customer instruction to move simulated money between two different Customer Accounts | May commit or receive final business rejection; a request is not itself a Financial Transaction |
| **Transfer** | Customer business operation initiated by a Transfer Request | Use Financial Transaction when referring specifically to its committed financial result |
| **Internal Transfer** | Transfer between two different Customer Accounts within FinFast Core | Does not involve a payment network or direct Customer access to treasury |
| **Money Movement** | Executed ordinary movement of simulated value; type `INTERNAL_TRANSFER` or `DEMO_ALLOCATION` | GENESIS is a separate initialization category |
| **Financial Transaction** | A committed financial movement with a unique immutable Transaction Reference | Ordinary types are `INTERNAL_TRANSFER` and `DEMO_ALLOCATION`; `GENESIS` is the special initialization transaction, not a customer transfer |
| **Ledger Transaction** | Grouping of Ledger Entries for a committed Financial Transaction, including special GENESIS initialization | Ordinary movements have exactly two opposite entries; GENESIS has one positive treasury entry |
| **Ledger Entry** | Append-only signed delta assigned to an Account and linked to a Financial Transaction | Example: sender −200,000; receiver +200,000; negative delta does not mean negative Transfer Amount |
| **Signed-entry Ledger** | FinFast's immutable record of signed Account balance changes | Supports reconciliation and supply checks; not a full double-entry General Ledger |
| **Transaction History** | Customer-facing list of committed movements affecting an owned account | Derived from trustworthy persisted money records |
| **Transaction Reference** | Unique, immutable identifier for a committed Financial Transaction, including GENESIS | Identifies its type, VND currency, positive amount, timestamp, participating accounts where applicable, and associated Ledger Entries; does not prescribe an encoded reference format |
| **Idempotency Key** | Client-supplied token scoped by initiator + operation type + key, retained with its final outcome for the dataset lifetime | Same scope and payload replays the committed or final business-rejected outcome; changed payload conflicts; new attempt requires a new key |
| **Rejected Request** | Transfer request denied by business validation | Never changes balances or writes successful ledger entries |
| **Atomicity** | All effects of a committed movement happen together, or none do | No 'debit succeeded, credit failed' final state |
| **Concurrency Control** | Mechanism preventing simultaneous operations from violating balance constraints | Database locks or comparable strategy selected in technical design |
| **Conservation of Value** | Ordinary movements sum to zero; after GENESIS, Treasury balance plus all Customer balances equals initial supply | No later creation/destruction of simulated money |
| **Reconciliation** | Compare each balance to the sum of all committed signed entries for that Account, including its GENESIS entry exactly once where applicable | Zero pre-GENESIS baseline; do not add the genesis amount again as a separate opening balance; not full bank reconciliation |
| **Settlement** | Completion of obligations through financial infrastructure | Not applicable to V1 internal virtual movements |

## 5. Lifecycle and process vocabulary

| Term | Working definition |
|---|---|
| **Account Provisioning** | Creating a zero-balance customer account as part of successful onboarding |
| **Freeze / Unfreeze** | Reasoned administrative actions that block/restore Customer Account money movements and atomically record required success audit |
| **Synchronous Transfer** | A request that normally returns the committed result in the same interaction |
| **Network Retry** | A client resends a request because the previous response was delayed or lost |
| **Committed** | A database transaction's changes have been durably accepted by the database |
| **Rolled Back** | A failed database transaction left no partial money movement |
| **Business Rejection** | An expected denial such as insufficient funds, frozen account, invalid recipient |
| **Technical Failure** | Unexpected infrastructure/process/database/network problem |
| **Unknown Outcome** | Client did not receive a response and cannot infer whether a commit happened; it retries/queries safely using the same idempotency key |
| **Non-final Processing Response** | A concurrent duplicate may be told the original intent is still processing; it retries safely with the same key. Not a final business rejection or a required persisted transfer lifecycle state. |
| **UNCHANGED** | Result when an authorized request sets an Account to its existing status; no state change or financial posting; the attempted operation may be audited. |

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
- Use **Transfer Request** for an instruction, **Financial Transaction** for a committed result, and **Ledger Entry** for its signed financial delta. Use **Transfer Amount** for the strictly positive amount; never confuse it with a negative source delta.
- Use **demo allocation**, not cash deposit, for administrator-issued funds.
- Use **VND** and integer example amounts (e.g., `200000 VND`), not `float` or localized money strings in APIs.
- Use `ACTIVE` and `FROZEN` consistently in diagrams and rules.
- Use `GENESIS` for the special initialization transaction; use **Admin Audit Event** for the separate administrative evidence. Rejected request/idempotency outcomes are not financial postings.

## 8. Research references

Apache Fineract is a general research reference only, not an implementation dependency or source of FinFast-specific requirements:

- [Apache Fineract — main repository](https://github.com/apache/fineract)
- [Apache Fineract — platform documentation](https://fineract.apache.org/docs/stable/)

**Current product vocabulary:** [Product glossary](../product/glossary.md) · [Product vision](../product/vision.md)

**Operative behavior:** [06 - Business Rules](./06-business-rules.md) · [04 - Use Cases](./04-use-cases.md)
