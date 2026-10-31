# Data and financial transactions

This guide states durable engineering constraints. Approved business outcomes
remain defined by the product requirements and detailed behavioral sources.
Exact columns, constraints, and database triggers remain defined by the existing
[Flyway migrations](../../backend/src/main/resources/db/migration/). Decision
rationale and approval scope remain in the
[design decision register](../6-governance/25-design-decision-register.md).

## Monetary and ledger integrity

- Store and calculate money as integral VND. Do not use binary floating point,
  implicit rounding, or a second currency representation for banking facts.
  Exact Java money declarations are in
  [`common.money`](../../backend/src/main/java/com/finfast/common/money/).
- Preserve signed ledger facts as immutable records. Balance changes must
  reconcile to committed ledger deltas, and dataset supply must reconcile to the
  one-time GENESIS amount. Schema constraints alone cannot establish all
  cross-row arithmetic or cardinality invariants.
- Do not add ordinary mint, GENESIS, direct balance overwrite, or dataset reset
  routes. Initial funding is a controlled setup operation with its own approved
  ownership and recovery rules.

## Transaction ownership and commit semantics

- The initiating application use case owns the outer transaction. Required
  balance changes, ledger facts, final idempotency outcome, and success audit
  join that transaction through the same database and transaction manager.
- Mutation collaborators join the caller-owned transaction. Do not independently
  commit required effects, use `REQUIRES_NEW` for financial writes, or perform
  required persistence asynchronously.
- Report financial success only after the outer transactional proxy confirms
  commit. A returned method value, pending result, SQL write, or adapter return
  does not establish commit. A technical or uncertain commit failure is not a
  final business rejection.
- Expected business rejection is distinct from a technical failure. A final
  rejection must be persisted atomically when the approved behavior requires a
  durable outcome; a failure to persist it remains technical and recoverable.

The selected Spring transaction guards, isolation, checked-failure rollback,
and detailed posting responsibilities are recorded under TD-002 and TD-015 in
the decision register and in the still-operative
[detailed data design](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02).
Use actual transaction proxies when verifying those guarantees.

## Concurrency, idempotency, and recovery

- Validate and lock the full set of affected resources under the approved
  transaction and isolation policy. Keep a stable lock order and bounded retry
  behavior. Mocks and structural checks do not establish database concurrency
  correctness.
- Scope idempotency to the trusted actor and operation. Preserve opaque keys
  exactly, compare the complete canonical intent, and persist only approved
  final outcomes. Same-key replay must not create a second posting; changed
  intent conflicts; processing, timeout, or uncertain commit remains non-final.
- Resolve uncertain outcomes with the original key and intent. Authorization
  and resource ownership checks still apply before lookup or replay. Do not
  create a new-key replacement because a response was lost.
- Verify account balance, ledger facts, outcome, and required audit together
  after concurrent, rollback, restart, and recovery scenarios using isolated
  PostgreSQL and real transaction proxies.

The approved lock, retry, idempotency, sequence, history, and cursor mechanisms
are detailed in [data design sections 6 through 9](../03-design/16-domain-model-and-data-design.md#6-locking-and-isolation-discipline-arc-03)
and the decision register. This concise summary does not replace those
still-operative approved protocols.

## Authorization and schema safety

- Derive actor identity from the trusted server session. Authorize each resource
  for the current actor before returning data, resolving an outcome, or applying
  a mutation. An identifier, cursor, or authenticated role alone does not prove
  resource ownership.
- Migrations own schema evolution. Add a new Flyway migration for a schema
  change; do not edit an applied migration, enable ORM schema auto-update, or
  use Flyway clean or persistent-volume reset to resolve a mismatch.
- Keep developer databases isolated. PostgreSQL/Testcontainers verification
  must use disposable test databases and synthetic data, not a developer's
  persistent local volume.

The [migration README](../../backend/src/main/resources/db/migration/README.md)
explains current migration guarantees and their limits. The
[verification strategy](../verification/strategy.md) defines required evidence.
