# Verification strategy

Verification establishes evidence for a particular source revision and
environment. Approved requirements and technical decisions describe intent;
source shows what is present; only executed checks produce run evidence. Passing
tests do not by themselves establish final release acceptance.

The product-level outcomes and release expectations live in the
[product requirements](../product/prd.md). Detailed approved behavior remains
operative in its current source until covered and accepted through OpenSpec.
Final V1 signoff and evidence fields remain in the
[release checklist](../05-verification/23-uat-and-release-checklist.md).

## Test responsibilities

| Level | Responsibility | Suitable evidence |
|---|---|---|
| Unit | Pure domain rules, validation, value objects, arithmetic, and canonical intent | Focused deterministic tests; no claim of persistence or concurrency correctness |
| Integration | Persistence mappings, constraints, transactions, module-provider collaboration, and application behavior | Isolated PostgreSQL with real Spring transaction proxies and independent connections where needed |
| Architecture | Module ownership, allowed dependencies, API isolation, layers, and restricted capabilities | Existing architecture tests and negative fixtures; no proof of financial outcomes |
| Contract | REST request/response, status, security, and schema conformance; public Java APIs compile and preserve compatibility | Validate the frozen OpenAPI YAML and test real HTTP behavior against the application |
| Browser end-to-end | Complete Customer and Admin journeys, uncertainty states, responsive layouts, and accessibility interactions | Real browser checks at the approved viewports, desktop zoom, and keyboard path; record browsers and outcomes |
| Performance | Approved offered workload, completed-request latency, complete outcome distribution, and financial reconciliation | Run the approved EV-003 protocol below on a documented demo environment |
| Release acceptance | Required product outcomes, security, deployment, performance, and owner decision | Evidence-linked release checklist and explicit project-owner disposition |

## Financial correctness and durability

Financial correctness needs PostgreSQL and actual transaction-proxy interception.
Mocks, in-memory substitutes, SQL structure checks, and method-body returns do
not demonstrate durable financial outcomes. Tests must exercise the real caller
transaction and verify committed state independently.

Cover the approved obligations across these cases:

- Commit and rollback at each required persistence boundary. A failure after a
  partial write leaves no balance, ledger, final outcome, or required success
  audit effect committed alone.
- Competing debits, opposite-direction transfers, allocation/freeze races, and
  duplicate idempotency requests use multiple real PostgreSQL transactions.
  Verify lock ordering, bounded retry, no negative balances, and no duplicate
  posting.
- A successful result is exposed only after confirmed outer commit. Lost
  responses, timeouts, process failure, and uncertain commit are resolved with
  the original key and intent, not misreported as final rejection.
- Restart the application and, where the scenario requires it, reconnect to the
  same durable test database. Verify GENESIS, balances, ledger facts, final
  outcomes, audit records, and history remain consistent.
- Reconcile every Account to signed ledger entries and total supply on a
  consistent committed snapshot. Verify GENESIS cardinality and its Treasury
  entry, plus the required two entries per ordinary movement.

Use isolated PostgreSQL/Testcontainers for automated tests. Never point test
commands at a developer's persistent database or reset its volumes. The
[migration guide](../../backend/src/main/resources/db/migration/README.md)
describes schema-level tests and limits; schema constraints do not prove the
application invariants above. Actual test definitions live in the
[backend test source tree](../../backend/src/test/java/com/finfast/); inspect
the relevant implementation and run evidence before claiming a capability.

## Security, contracts, and user journeys

Test authentication and session lifecycle, CSRF, role checks, resource ownership,
privacy-preserving unavailable-resource behavior, and authorization before
lookup or replay. Verify financial mutation and audit requirements at the
application boundary, not only in controllers or repositories.

The exact REST source is the frozen
[OpenAPI V1 YAML](../03-design/contracts/openapi-v1.yaml). Structural validation
does not prove runtime conformance. Contract tests exercise the actual HTTP
application and preserve operation-specific differences among business
rejection, generic validation error, conflict, non-final state, and technical
failure. The exact Java APIs and schema remain source-owned.

Browser verification follows the approved journeys and acceptance IDs in the
[UX source](../03-design/14-ux-user-flows-and-e2e.md) and product requirements.
Exercise the approved 360x800, 768x1024, and 1440x900 CSS-pixel viewports,
desktop 200% zoom, keyboard interaction, and uncertainty states. Do not report a
browser pass without executing it against the actual frontend stack.

## NFR-004 / EV-003 benchmark protocol

This is the TD-013 approved method for EV-003 from the 2026-10-22 freeze. The
product requirement owns the provisional target: transfer-request p95 at or
below 2 seconds at 10 new requests per second for five minutes on a documented
demo environment. This is not a production service level and no measurement is
claimed here. Approval provenance remains in the
[decision register](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze).

### Environment and setup

1. Complete EV-001/EV-002 before selecting the benchmark environment. Record application revision, configuration, resource sizing, database/storage, deployment topology, TLS path, load-generator location, and relevant session/connection settings.
2. Use a dedicated synthetic benchmark dataset initialized once with the default 100,000,000 VND supply. Register 100 Customers and allocate exactly 1,000,000 VND to each through normal authorized, audited funding. Treasury then holds 0 VND; all Accounts remain ACTIVE. Setup is outside measurement.
3. Divide the Customers into 50 fixed pairs, with members A and B in each pair. Use authenticated browser-equivalent requests and a unique key for every new intent. Do not freeze, allocate, or introduce unrelated workload during the measured run.
4. Warm up for 30 seconds at the same scheduled rate using 300 intents and a separate key namespace. Use the schedule below; wait for warm-up outcomes and verify balances return to 1,000,000 VND per Customer before measurement.

### Measured workload

- Schedule exactly **3,000 new transfer intents over 300 seconds**, one every 100 milliseconds, without waiting for the preceding response to schedule the next request. Provide enough client concurrency to sustain the offered rate and report scheduling delay.
- For zero-based intent index `n`, select pair `n modulo 50`. Transfer 1,000 VND A-to-B when `floor(n / 50)` is even and B-to-A when it is odd. Assign a unique run-scoped key to each new intent.
- Every pair has 60 measured intents, 30 in each direction. All requests are eligible under the setup, there are no expected business rejections, and successful completion restores each Customer's initial benchmark balance.
- Measure elapsed request time from client request initiation to receipt of the final response, including network/application/database time. Record the client timeout and percentile method before the run. Record latency for all attempts and completed responses separately; retries are separate attempts linked to their original intent.
- If any response is lost, technical, or non-final, resolve the intent using the original key after submission. Report the original response and recovery separately. Recovery does not convert a failed or incomplete primary measurement into a clean success sample.

### Report and decision

The benchmark report must include offered/achieved request rate, submitted intents, request attempts, completed new movements, final rejections, conflicts, technical errors, timeouts, non-final replies, unresolved intents, latency distribution, p95, environment details, and recovery outcomes.

For the approved benchmark method, a clean performance pass requires sustaining the workload, all 3,000 eligible intents completing once, no unexpected rejected/technical/timeout/non-final primary responses, completed-request p95 <= 2 seconds, and zero invariant violations. Faster rejection responses cannot establish successful-transfer performance. Separately reconcile every Account, check two entries per ordinary movement, verify one GENESIS, and verify total supply after warm-up and the measured run.

Record deviations and their causes against EV-003 for owner review. A missed provisional latency target cannot be relabeled as achieved; an environment limit cannot justify a financial-invariant violation. Extended course-driven load/autoscaling evidence is a separate follow-on workload whose exact criteria depend on EV-001.

## Evidence and release gates

Classify evidence accurately:

- A source or configuration review reports what exists at a named revision.
- A structural check validates syntax, links, dependency rules, or declarations.
- An executed test reports its command, revision, environment, result, and limits.
- A hosted CI result applies only to the revision and jobs that actually ran.
- Release acceptance requires all applicable product gates, linked evidence, and
  the authorized project-owner decision.

Keep dated execution records in their scoped verification record. Do not copy
historical test results into this strategy or describe a planned test as run.
The [historical verification record](../05-verification/22-test-strategy-and-acceptance.md)
retains earlier detailed scenarios and dated run evidence with their original
revision and limits.

Use the existing [CI workflow](../../.github/workflows/ci.yml), [Makefile](../../Makefile),
and [root README](../../README.md) for commands. Existing documentation checks
are documented in the [docs index](../README.md#validate-documentation-locally-and-in-ci).
The local Markdown and OpenAPI-reference checker is
[`scripts/check_docs.py`](../../scripts/check_docs.py); its pinned dependencies
are in [`scripts/requirements-docs.txt`](../../scripts/requirements-docs.txt).
They validate documentation and contract structure, not financial behavior or
release acceptance.
