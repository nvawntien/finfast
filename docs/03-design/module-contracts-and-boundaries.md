# Module contracts and boundaries

This path remains available for existing links. The maintained boundary guidance
is now in [docs/architecture/boundaries.md](../architecture/boundaries.md).
Exact public Java contracts live in their owning module's source `api` package.
This file no longer reproduces method signatures, DTO shapes, or an implementation
inventory.

## Ownership and approved dependencies

Use the [current module ownership and approved dependency map](../architecture/boundaries.md#module-boundaries)
for the nine-module target, its eighteen allowed edges, and responsibility rules.
GitHub Issues own current task owners and team progress; historical BK assignments
are not the source for current work assignment.

## Public API and private implementation

Public module APIs belong under the provider's `api` package. Provider-specific
implementation belongs under `internal`; cross-module callers use only approved
provider APIs. See [boundary policy](../architecture/boundaries.md#public-api-policy)
and the detailed [Java architecture convention](15-system-architecture.md).

## Approved read capabilities  TD-016-AM-01..04

The approved V1 read capabilities and their scope are summarized in
[Module boundaries](../architecture/boundaries.md#approved-posting-capabilities)
and approved in the [design decision register](../6-governance/25-design-decision-register.md#final-v1-capability-decision--2026-10-23).
AM-05 Treasury inspection was not selected. Capability approval does not prove
provider implementation, wiring, authorization, or feature acceptance.

## Transactions and posting permissions

The initiating application owns the outer financial transaction, and restricted
mutation collaborators join it. See [data and transactions](../architecture/data-and-transactions.md#transaction-ownership-and-commit-semantics)
for the durable constraints and the detailed [approved data design](16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02)
for selected mechanisms.

## Automated evidence and limits

Current dependency and capability rules are exercised by the
[architecture tests](../../backend/src/test/java/com/finfast/architecture/).
Structural checks do not prove runtime provider wiring, real authorization,
financial correctness, or release acceptance. See the
[verification strategy](../verification/strategy.md) for evidence requirements.

## How to Start a Business Module

Start with the assigned GitHub Issue, inspect current source contracts and
worktree state, then follow the [development workflow](../development/workflow.md).
Do not add empty module scaffolding, speculative APIs, or production no-op
providers.
