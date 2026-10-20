# Module contracts and boundaries

Step 4E.2 implements architecture checks and this collaboration guide on the
working branch. No business module/API below is implemented. The agreed direction
is one Java/Spring Boot modular monolith, pragmatic Hexagonal Architecture,
constructor injection and one PostgreSQL database/transaction manager.

Approved [use cases](../01-discovery/04-use-cases.md) and
[business rules](../01-discovery/06-business-rules.md) determine required behavior.
[Architecture 15](15-system-architecture.md#32-allowed-module-dependencies-arc-06)
is the source of the candidate dependency graph. ARC-01/02/06 and DOC-02 refine
that direction; [TD-015/016](../6-governance/25-design-decision-register.md) remain
**PROPOSED**. Implementing checks does not approve the candidate policy or Java APIs.

## Ownership and candidate dependencies

Only the following nine business modules are cycle nodes. An outgoing edge permits
dependencies on the provider's `api` package, never its `internal` implementation.
Each module may access its own internals. An empty cell means no outgoing business
dependency, not permission to add one. The table matches architecture 15 §3.2.

| Module | Owns | Candidate outgoing providers |
|---|---|---|
| `identity` | Users, credentials, roles, sessions, registration orchestration | `customer` |
| `customer` | Profiles and User–Customer linkage | `accounts` |
| `accounts` | Account identity/type/status/balance and invariants | None |
| `transfers` | Customer transfer intent, authorization, orchestration | `identity`, `accounts`, `ledger`, `idempotency` |
| `ledger` | Financial transactions, immutable entries, controlled posting/reconciliation | `accounts` |
| `idempotency` | Scoped request identity, canonical intent, durable outcomes/recovery | None |
| `administration` | Authorized allocations/status operations and Admin audits | `identity`, `customer`, `accounts`, `ledger`, `idempotency` |
| `history` | Authorized read-only history/receipt projections | `identity`, `accounts`, `ledger` |
| `bootstrap` | Restricted setup/Admin provisioning and GENESIS orchestration | `identity`, `accounts`, `ledger` |

`com.finfast.common` contains existing technical infrastructure and pure shared
contracts; it is not a business module and cannot depend on business modules.
`FinfastApplication` is the root composition/launch class, not the Bootstrap
business module. Framework libraries and test fixtures are not business modules.
New top-level production packages require explicit review/update of the scan guard.
There is no blanket cross-module internal-access exception for Bootstrap.

## Public API and private implementation

Create packages only when behavior needs them; this is a convention, not scaffolding:

```text
<module>/api/                              # Reviewed public contracts/value DTOs
<module>/internal/domain/                  # Pure domain behavior and invariants
<module>/internal/application/             # Use cases, transactions, necessary ports
<module>/internal/adapter/inbound/http/     # HTTP binding, auth context, result mapping
<module>/internal/adapter/outbound/persistence/ # Module-owned JDBC/repositories
<module>/internal/config/                  # Spring wiring
```

Use small immutable values, identifiers and purpose-specific results across module
APIs. Do not export internal entities, repositories, mutable aggregate graphs,
`Connection` objects or persistence DTOs. Do not move all DTOs into `common` or add
generic repositories/base services. Public Java visibility or the word `internal`
alone does not protect a boundary. Review signatures as well as dependencies.

Illustrative imports only: these business APIs do not yet exist.

```java
// History may depend on a reviewed query capability using constructor injection:
// import com.finfast.ledger.api.LedgerQueries;
// private final LedgerQueries ledgerQueries;

// Forbidden shortcut from another module:
// import com.finfast.ledger.internal.adapter.outbound.persistence.LedgerRepository;
```

Domain code uses Java and appropriate pure contracts, without Spring, HTTP/Servlet,
JDBC or application/adapter/config dependencies. Application code can use pragmatic
Spring transactions and its domain/ports, but not HTTP, JDBC or concrete adapters.
Controllers bind/validate requests, obtain trusted authorization context and call
use cases; checks prohibit direct JDBC/persistence access, not arbitrary method
lengths. Adapters implement module-owned ports where useful; avoid redundant
interfaces. Constructor injection and normal MVC binding need no Generic Handler.

## Collaboration inventory — future capabilities

All capability/API shapes below are **PROPOSED / TO REVIEW**, not signatures to
copy into production. Required business atomicity/authorization remains approved.
The provider owns its concepts; the initiating use case owns the outer transaction.

| Caller | Provider | Capability and ownership | Authorization responsibility | Transaction responsibility | Errors/outcomes | Status / source |
|---|---|---|---|---|---|---|
| Identity | Customer | Provision profile linked to User; Customer owns profile | Identity validates registration and CUSTOMER privileges | Identity encloses User/profile/one zero-balance Account, all or none | Safe expected registration errors; never partial provisioning | PROPOSED; DOC-02, 15 §3.2, BR-ID-01/02, BR-ACC-01 |
| Customer | Accounts | Open exactly one zero-balance Customer Account; Accounts owns identity/uniqueness | Trusted registration context, no caller-selected role or balance | Join registration transaction | No financial posting or new supply | PROPOSED; DOC-02, 15 §3.2, BR-ACC-01/06 |
| Transfers | Ledger | Submit valid internal-transfer intent; Ledger owns paired posting/facts | Transfers establishes initiator/source/recipient eligibility; collaborators recheck invariants | Transfers owns outer transaction; Ledger joins, never independently commits | Pending posting result is not confirmed success | PROPOSED; ARC-01/02, TD-015 |
| Ledger | Accounts | Restricted paired mutation under Account locks; Accounts owns balance/status invariants | Only reviewed mutation callers/capabilities | Require existing transaction; both sides/facts atomically commit | Numeric/status/funds checks under locks; no half debit/credit | PROPOSED; ARC-01/02, TD-015 |
| Transfers / Administration | Idempotency | Coordinate scoped intent and full canonical representation; Idempotency owns durable result/recovery | Initiator is server-established; callers reauthorize before replay/lookup | Final outcome joins posting and required audit; no independent unfinished row | COMPLETED/REJECTED final only after commit; PROCESSING/UNKNOWN non-final | PROPOSED mechanism; ARC-02/04, BR-TRF-05/08/11 |
| Administration | Ledger / Accounts | Treasury allocation through Ledger; status changes through Accounts; Administration owns audit | Establish ADMIN permissions and eligible Customer target; Treasury is not an ordinary Customer | Allocation + posting + outcome + success audit together; status + audit together | Audit failure rolls back affected operation; generic exception is not final rejection | PROPOSED APIs; ARC-01/02, BR-ADM-01..04 |
| History | Identity / Accounts / Ledger | Authorized read-only projections/receipts; no private repositories | History verifies current role/ownership before disclosure | Read committed facts under reviewed snapshot/pagination semantics | Privacy-safe unavailable-resource errors; no mutation | PROPOSED; 15 §3.2, ARC-05, TD-008 |
| Bootstrap | Identity / Accounts / Ledger | Protected Admin provisioning and finite Treasury/GENESIS initialization | Restricted operator/setup path, no normal public API | Atomic controlled initialization; verify existing dataset, never reseed | Setup failure is technical; no mint/reset endpoint | PROPOSED procedure; ARC-01/02, TD-009/015 |

Future consumers of Identity's authentication context are limited to the candidate
edges above. Authorization is not established by a Java import or database FK.
The exact reviewable capabilities and DTOs must be agreed with their owners before
parallel implementation; no speculative Java interface is created by this guide.

### Transactions and posting permissions

Follow [data design §5](16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02).
The candidate model uses an externally invoked application `REQUIRED` transaction;
restricted mutation collaborators join it and require an existing transaction
(for example `MANDATORY`). Do not independently commit balances, entries, final
outcomes or required success audits with `REQUIRES_NEW`. HTTP success follows actual
proxy commit confirmation, not a method-body return or an earlier SQL write.

Module edges alone cannot restrict individual methods. When real APIs appear, add
caller/method rules for transfer-only versus allocation posting, Ledger-only paired
Account mutation, Accounts status operations and Bootstrap-only GENESIS. Normal
Transfers cannot mint funds; Administration cannot bypass the controlled posting
path; Ledger cannot commit half a movement. No fake posting interfaces or synthetic
method-level financial permissions are installed now. TD-015 still needs review.

## Shared contracts and observability

Follow the actual [shared error/money guide](shared-error-money-contracts.md):

```java
VndAmount zero = new VndAmount(0);
VndAmount amount = VndAmount.parse("200000").requirePositive();
long debit = VndArithmetic.negateExact(amount.value());
// ModuleErrors is an illustrative module-owned enum implementing ErrorCode:
// throw new ApplicationException(ModuleErrors.RESOURCE_NOT_FOUND);
```

Money DTO fields use `VndAmountJson` automatically; required fields use `@NotNull`
and `@Valid`. Monetary JSON is a decimal string, Java is checked `long`, PostgreSQL
is BIGINT. No float/double or unchecked monetary arithmetic. Signed Ledger deltas
are distinct from nonnegative amounts. Accounts/Ledger keep their own invariants.

**Clean Business Code, Centralized Observability:** domain/application code has no
SLF4J, Logback, MDC, Micrometer/OpenTelemetry or technical logging dependencies.
Use module-owned `ErrorCode` catalogs and `ApplicationException` for expected errors;
technical failures propagate with causes to the appropriate terminal boundary.
Generic HTTP errors never add a financial `outcome`. Application logs do not replace
durable Ledger/Admin audit data. See the [logging standard](logging-and-error-handling.md).

## Automated evidence and limits

`archunit-junit6:1.5.1` is TEST scope only. Boot 4.1.1 actually manages Jupiter
6.0.3, so the matching engine is used instead of `archunit-junit5`; see
[ArchUnit installation](https://www.archunit.org/userguide/html/000_Index.html#_junit_5_6).
Java 21 and the existing Maven Wrapper remain unchanged. No Spring Modulith is added.

`ProductionArchitectureTests` imports compiled `com.finfast` production code with
`DoNotIncludeTests`, verifies the source location, and checks recognized packages.
The seven rules cover candidate edges plus provider API access, business cycles,
domain isolation, application isolation, controller JDBC/persistence isolation,
clean business code and the common-to-business dependency prohibition.

There are no production business modules yet, so business-only rules deliberately
allow empty selections. This is not proof about future business implementations.
`RuleVerificationTests` explicitly imports test-only `finfast.architecture.fixtures`
outside Spring's application scan and applies the same rules using that root.
Positive evidence covers own internals, legal public APIs, pure shared contracts,
controller-to-use-case calls and application `@Transactional` usage. Negative
evidence covers foreign repositories/entities, non-API access, a reversed edge,
a two-module cycle, HTTP/Servlet/JDBC/Spring/adapter leakage, direct controller
persistence, domain/application observability and shared-to-business coupling.
Assertions check the intended origin, target and rule violation; negative fixtures
do not enter production scans or production JARs.

Checks inspect bytecode dependencies. They cannot prove SQL ownership, reflective
calls, authorization, financial atomicity, locks, balanced entries/reconciliation,
idempotency recovery, mandatory audit persistence or successful durable commit.
Real module and PostgreSQL integration tests remain required; no financial AC is
marked passed by these structural checks.

From `backend/`, with Java 21:

```sh
./mvnw -Dtest=ProductionArchitectureTests,RuleVerificationTests test
./mvnw test
./mvnw package
```

Architecture checks need no database; the full suite uses isolated PostgreSQL
Testcontainers and requires a compatible runtime. `make test` runs the full suite
from the repository root. No special Maven profile is needed for architecture checks.

## How to Start a Business Module

1. Read assigned use cases/business rules and their approval status.
2. Check ownership and the candidate dependency matrix; resolve review gaps.
3. Review the relevant [OpenAPI](contracts/openapi-v1.yaml) and endpoint status.
4. Agree required public capabilities and consumers with the owning contributor.
5. Define only necessary immutable boundary types/interfaces; avoid empty scaffolding.
6. Implement domain behavior in its owning module using pure shared contracts.
7. Add use-case orchestration, transactions and adapters with constructor injection.
8. Add meaningful unit/use-case tests, PostgreSQL and HTTP tests as behavior requires.
9. Run architecture checks, the relevant regressions and documentation checks.
10. Submit a small reviewable PR through the team's normal workflow after review.

If another module needs a capability, discuss it with its owner, review the public
contract and any new edge, then update code/tests/docs together. Never import its
internals as a shortcut. Read [AGENTS.md](../../AGENTS.md), the
[backlog](../02-requirements/10-product-backlog.md) and
[test strategy](../05-verification/22-test-strategy-and-acceptance.md) before changing
scope. People/dates and final dependency/posting approvals remain team decisions.
