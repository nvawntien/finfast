# Module boundaries

The approved target has nine business modules and eighteen directed dependency
edges. A listed edge permits access only to the provider's public `api` package.
It does not imply that the provider is complete or registered as a runtime bean.
The exact Java declarations remain in source code.

| Module | Owns | Approved outgoing providers |
|---|---|---|
| Identity | Users, credentials, roles, sessions, registration orchestration | Customer |
| Customer | Profiles and User-Customer linkage | Accounts |
| Accounts | Account identity, type, status, balance, and local invariants | None |
| Transfers | Customer transfer authorization and orchestration | Identity, Accounts, Ledger, Idempotency |
| Ledger | Financial transactions, immutable entries, controlled posting and reconciliation | Accounts |
| Idempotency | Scoped request identity, canonical intent, durable outcomes and recovery | None |
| Administration | Authorized allocations, account status operations, and Admin audits | Identity, Customer, Accounts, Ledger, Idempotency |
| History | Authorized read-only history and receipt projections | Identity, Accounts, Ledger |
| Bootstrap | Restricted setup, Admin provisioning, and GENESIS orchestration | Identity, Accounts, Ledger |

`common` contains technical infrastructure and pure shared contracts. It cannot
depend on a business module or own banking workflows. The root application class
composes the deployment; it is not a business module. No new dependency edge,
business module, or public capability is inferred from source presence.

## Public API policy

Each business module owns its public contracts under its `api` package and its
implementation under `internal`. APIs expose small immutable values and do not
expose internal entities, repositories, persistence DTOs, mutable aggregate
graphs, or framework objects. No Markdown inventory duplicates method
signatures or nested value shapes.

Exact declarations can be inspected in the
[Java module source tree](../../backend/src/main/java/com/finfast/). Key approved
posting contracts include
[FinancialPosting](../../backend/src/main/java/com/finfast/ledger/api/FinancialPosting.java),
[AllocationPosting](../../backend/src/main/java/com/finfast/ledger/api/AllocationPosting.java),
[GenesisPosting](../../backend/src/main/java/com/finfast/ledger/api/GenesisPosting.java),
and the Ledger-only
[PostingAccounts](../../backend/src/main/java/com/finfast/accounts/api/PostingAccounts.java).
Other source-owned declarations include
[CurrentIdentity](../../backend/src/main/java/com/finfast/identity/api/CurrentIdentity.java),
[AccountQueries](../../backend/src/main/java/com/finfast/accounts/api/AccountQueries.java),
[CustomerQueries](../../backend/src/main/java/com/finfast/customer/api/CustomerQueries.java),
[RequestOutcomes](../../backend/src/main/java/com/finfast/idempotency/api/RequestOutcomes.java),
and [LedgerQueries](../../backend/src/main/java/com/finfast/ledger/api/LedgerQueries.java).
Use the actual source tree for the complete current set.

The dependency and capability rules are enforced by the existing
[architecture tests](../../backend/src/test/java/com/finfast/architecture/).
Read the detailed [Java architecture convention](../03-design/15-system-architecture.md)
for package responsibilities, dependency direction, constructor injection, and
transaction-proxy requirements.

## Approved posting capabilities

The public capabilities keep distinct ownership:

| Capability | Permitted caller | Boundary |
|---|---|---|
| `FinancialPosting` | Transfers | Posts ordinary Customer transfers through Ledger |
| `AllocationPosting` | Administration | Posts authorized demo allocations through Ledger |
| `GenesisPosting` | Bootstrap | Performs controlled dataset initialization through Ledger |
| `PostingAccounts` | Ledger implementation | Applies account changes under Accounts ownership; Treasury initialization is restricted to the Ledger GENESIS implementation |

Approved TD-016-AM-01..04 read capabilities add only these existing graph edges:
Bootstrap to Ledger for committed GENESIS inspection; Ledger to Accounts for identity
metadata; Administration to Accounts for Customer listing; and Administration to
Ledger for committed financial inspection. These exact scopes are recorded in
the [sole design decision register](../6-governance/25-design-decision-register.md#final-v1-capability-decision--2026-10-23).
They do not grant the unselected Treasury inspection capability, change other
caller restrictions, or prove provider integration.

## Collaboration and amendments

Before changing a cross-module call, inspect the provider API, approved edge,
capability rules, and current source. Keep application orchestration with its
owning module, persistence and SQL with the data-owning module, and authorization
at the boundary that has trusted actor and resource context. Never bypass an
unavailable provider with foreign internals, direct SQL, a duplicate API, or a
no-op production implementation.

Propose a new edge, capability, breaking public API change, or change to an
approved ownership rule in a scoped GitHub Issue and review it with affected
owners and the Tech Lead. Once approved, update the owning Java source, boundary
tests, and only the durable documentation whose concern changed. Record technical
decision provenance in the existing decision register. Ordinary implementation
defects within the approved contract do not require routine reapproval.

The earlier [module guide](../03-design/module-contracts-and-boundaries.md)
remains as a compatibility entrypoint for existing references. Detailed
financial transaction responsibilities are summarized in
[data and transactions](data-and-transactions.md).
