# Architecture overview

## Approved target

FinFast is an educational banking simulator using simulated funds and synthetic
data. Its approved V1 target is one Java/Spring Boot modular-monolith deployment
with one logical PostgreSQL database per environment. The architecture uses
domain-oriented modules and pragmatic Hexagonal Architecture. Deployment choices
remain subject to the approved external evidence and decision records.

Presentation channels remain outside the financial Core. Browser applications
are untrusted clients of the REST boundary; they do not call Java module APIs or
own financial rules. The Core owns identity, customer records, accounts,
transfers, ledger posting and reads, idempotency, administration, and controlled
bootstrap. Database access stays behind module-owned adapters and the database
is not exposed as a client interface.

| Capability | Architectural responsibility |
|---|---|
| Identity and Customer | Authenticate actors and own user credentials, roles, sessions, profiles, and User-Customer linkage |
| Accounts | Own account identity, type, status, balance invariants, and participant locking |
| Transfers | Authorize and coordinate Customer transfer intents and durable outcomes |
| Ledger | Own financial transactions, immutable signed entries, controlled posting, and committed financial reads |
| Idempotency | Coordinate scoped request identity, canonical intent, final outcomes, and recovery |
| Administration | Authorize allocations and account-status operations, including required audit records |
| History | Provide authorized read-only history and receipt projections |
| Bootstrap | Perform restricted operator setup and one-time GENESIS orchestration |
| Common | Hold technical infrastructure and pure shared contracts; it is not a banking workflow module |

These are approved ownership boundaries, not a claim that every capability is
implemented or wired into a complete runnable workflow. Source, tests, and
configuration must be checked at the revision being changed. A class or test
fixture alone does not establish production composition, behavior, or acceptance.

## Core collaboration

Cross-module calls use only the approved provider `api` contracts and the allowed
dependency graph in [module boundaries](boundaries.md). A provider owns its
private implementation, persistence, and data invariants. No caller reaches into
another module's `internal` packages, repositories, or SQL.

The initiating application capability owns the outer financial transaction.
Ledger owns controlled posting facts; Accounts owns account state and locking;
Idempotency owns request outcomes; Administration owns required administrative
audits; Bootstrap owns restricted setup orchestration. Required balance changes,
ledger facts, final outcomes, and success audits share the caller's transaction.
An operation is reported successful only after the outer transaction commits.
Ordinary application routes do not expose GENESIS, minting, direct balance
overwrite, or dataset reset.

The target supports Customer and Admin Web channels over the same REST boundary.
Exact REST shapes are owned by the frozen
[OpenAPI V1 contract](../03-design/contracts/openapi-v1.yaml). Exact Java
declarations are owned by their module API sources. Schema is owned by
[Flyway migrations](../../backend/src/main/resources/db/migration/). This overview
does not copy endpoint schemas, Java signatures, or table definitions.

## Decision provenance

The [design decision register](../6-governance/25-design-decision-register.md)
is the sole technical decision authority. It records the approved modular
monolith, module graph, posting ownership, transaction and financial design,
approved capabilities, scope, and scoped exceptions. Consult its source record
before proposing a change to an approved boundary or constraint. The detailed
[system architecture source](../03-design/15-system-architecture.md) preserves
the package, adapter, and enforcement convention; the detailed
[data design source](../03-design/16-domain-model-and-data-design.md) preserves
approved transaction, locking, idempotency, and history protocols while those
obligations remain operative.
