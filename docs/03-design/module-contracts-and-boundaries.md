# Module contracts and boundaries

> Consolidated baseline — 2026-10-22: [Product/UX/REST approval](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) now freezes PRD 09, derived specification 12, UX 14 and external API 17/YAML alongside the approved Java contracts and TD designs. Java APIs and HTTP contracts are separate authorities; no signatures/capabilities/edges change. Implement conforming assigned tasks without routine reapproval; use scoped amendments for actual contract/security/correctness conflicts and preserve recorded integration exceptions.

This is the canonical entry point for inter-module Java collaboration. BK-003 adds
real compile-time interfaces and immutable boundary values under
`backend/src/main/java/com/finfast/<provider>/api/`. The contracts and architecture
checks are **IMPLEMENTED in source**. On **2026-10-22**, Tiến (Tech Lead) explicitly
**APPROVED the current Java signatures, TD-015 ownership/transaction responsibilities
and TD-016 dependency/capability policy** in the BK-003 final-review instruction.
BK-003 PR #10 merged into `develop` on 2026-10-22 at `8a04ca0`; this is already the mandatory shared V1 integration baseline. No individual owner endorsement is inferred.
No provider
implementation, Spring Bean, banking workflow or new HTTP endpoint is supplied.
An interface permits compilation and mocking; it does not make runtime injection
or financial integration ready. The agreed direction remains one Java/Spring Boot
modular monolith, pragmatic Hexagonal Architecture, constructor injection and one
PostgreSQL database/transaction manager.

Approved [use cases](../01-discovery/04-use-cases.md) and
[business rules](../01-discovery/06-business-rules.md) determine required behavior.
[Architecture 15](15-system-architecture.md#32-allowed-module-dependencies-arc-06)
is the source of the approved nine-module/18-edge dependency graph. ARC-01/02/06
and DOC-02 refine that direction; the [decision register](../6-governance/25-design-decision-register.md#bk-003-approval-record-and-remaining-authority)
records the original approval scope and authority. The later [V1 freeze](../6-governance/25-design-decision-register.md#v1-engineering-design-freeze--2026-10-22) selects algorithms, isolation/locks/retries, canonicalization, HMAC cursor, concrete transaction guards and operator-driven setup under TD-001..014. Performance and runtime correctness remain unverified; pure API Javadocs retaining historical “proposal” wording do not override that later design authority.

## Ownership and approved dependencies

Only the following nine business modules are cycle nodes. An outgoing edge permits
dependencies on the provider's `api` package, never its `internal` implementation.
Internal implementations may access their own module's types; public `api`
contracts must not depend on their own internals. An empty cell means no outgoing business
dependency, not permission to add one. The table matches architecture 15 §3.2.

| Module | Owner from BK-003 request | Owns | Approved outgoing providers |
|---|---|---|---|
| `identity` | Hưng | Users, credentials, roles, sessions, registration orchestration | `customer` |
| `customer` | Hưng | Profiles and User–Customer linkage | `accounts` |
| `accounts` | Hải | Account identity/type/status/balance and invariants | None |
| `transfers` | Minh | Customer transfer intent, authorization, orchestration | `identity`, `accounts`, `ledger`, `idempotency` |
| `ledger` | Tiến | Financial transactions, immutable entries, controlled posting/reconciliation | `accounts` |
| `idempotency` | Minh | Scoped request identity, canonical intent, durable outcomes/recovery | None |
| `administration` | Tiến | Authorized allocations/status operations and Admin audits | `identity`, `customer`, `accounts`, `ledger`, `idempotency` |
| `history` | Hải | Authorized read-only history/receipt projections | `identity`, `accounts`, `ledger` |
| `bootstrap` | Trung | Restricted setup/Admin provisioning and GENESIS orchestration | `identity`, `accounts`, `ledger` |

Sĩ owns Customer/Admin frontend work and consumes REST/OpenAPI, never Java module
APIs. Trung also owns Admin read work within the reviewed module boundaries;
this does not transfer Ledger/Administration domain ownership. Tiến owns BK-003,
BK-010 and BK-013. Names here record the explicit task assignment, not evidence
that the owners individually endorsed the signatures; the approval is Tiến's.

`com.finfast.common` contains existing technical infrastructure and pure shared
contracts; it is not a business module and cannot depend on business modules.
`FinfastApplication` is the root composition/launch class, not the Bootstrap
business module. Framework libraries and test fixtures are not business modules.
New top-level production packages require explicit review/update of the scan guard.
There is no blanket cross-module internal-access exception for Bootstrap.

## Public API and private implementation

Every business-module class must be under `<module>.api` or `<module>.internal`,
including their subpackages. Classes at the module root or in `misc`, `service`,
`persistence`, `apihelper` or `internalized` directly below it are rejected.
`apihelper` is not `api`; checks match complete package segments.
Root `FinfastApplication` and `common` are not business modules and are excluded
from this package rule. No empty module is required.

Create only the subpackages needed by actual behavior:

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

Public `api` classes must not depend on their own module's `internal` types,
including implementation calls, fields and signatures. Implement reviewed public
contracts inside `internal`; internal application code may depend on its own public
DTOs/contracts and domain types. Pure shared error/money contracts remain allowed.
For example, the implemented `accounts.api.AccountQueries` returns immutable
`accounts.api.CustomerAccount`; returning an internal Account entity is forbidden.
Other modules still
need both an approved dependency edge and an approved provider `api` contract.

These imports now resolve; production implementations remain absent.

```java
import com.finfast.ledger.api.LedgerQueries;
// A History application use case can accept LedgerQueries in its constructor.

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

## Compatibility audit and review matrix

Historical pre-merge compatibility audit baseline (retained as evidence of that audit, not current branch status): `develop` / the BK-003 feature branch at
`a4eb9fff7523a5a51689176a85ed4a60514152b9`. Live read-only branch inspection found
`develop` there and `main` at `5af4b1c388d095dc971a2eca1e52be4b00754603`, with identical
trees. GitHub exposed only those two live branches and no open PRs. All available
local and retained remote-tracking refs had no production business-module Java
files before BK-003. Shared money/error types and 57 architecture tests existed.
Retained tracking refs are historical evidence, not proof a feature branch is live.
No existing interface/package is renamed, no member implementation is overwritten,
and no competing Java contract was found in visible work. Compatibility with
unpushed/local-only teammate code **cannot be verified**; absence of a PR is not
evidence that implementation has not started.

| Provider | Owner | Consumers | Existing Java API before BK-003? | Approved contract / deferred work | Compatibility / implementation follow-up |
|---|---|---|---|---|---|
| Accounts | Hải | Customer, Ledger, Transfers, Administration, History, Bootstrap | None found; documented proposals only | Queries, Customer/Treasury provisioning, guarded posting, status changes | Additive to visible baseline; implement approved signatures and selected TD-002 guards with Tiến/Trung; verify real proxies |
| Ledger | Tiến | Transfers, Administration, History, Bootstrap | None found | Transfer/allocation/GENESIS capabilities, pending results, Customer projections | Implement approved capability split with Minh/Hải/Trung; TD-004 cursor algorithm selected, implementation pending; Admin-wide read capability absent |
| Idempotency | Minh | Transfers, Administration | None found | Scoped inspect/record/find and typed final/non-final results | Implement selected TD-003 canonical v1/advisory coordination and existing response mapping with Tiến; proof tests pending |
| Identity | Hưng | Transfers, Administration, History, Bootstrap | None found | Current trusted actors, protected configured-Admin provisioning | Review actor resolution and credential-source/transaction contract with Trung |
| Customer | Hưng | Identity, Administration | None found | Registration provisioning and minimal profile reads | Review with Hải; registration projection intentionally avoids an Identity → Accounts dependency |
| Transfers | Minh | HTTP use cases | None found | No exported API; consumes providers above | Orchestration, retries and performance remain BK-012/022/023 work |
| Administration | Tiến | HTTP use cases; Trung supports Admin reads | None found | No exported API; consumes providers and owns audit | Funding/status/audit implementations remain separate work |
| History | Hải | HTTP use cases | None found | No exported API; consumes participant-scoped Ledger projections | Authorization and real stable traversal need implementation/tests |
| Bootstrap | Trung | Restricted operator/setup entry | None found | No exported API; consumes protected provider capabilities | TD-009 protected operator entry selected; private credential wiring and restart/race tests remain implementation work |

All additions are **APPROVED compile-time contracts**, without recorded individual
owner endorsements. Compare this matrix with each member's preserved local work.
For mismatches: preserve work → inspect the approved source → report exact package/
signature differences and migration options to Tiến → receive a Tech Lead
integration decision → adapt member-owned implementations without duplicate APIs
→ rerun relevant tests. Stop only the affected integration while awaiting that
decision. Compatibility where practical is preferred; unsafe casts and silent
renames are prohibited. A freeze permits reviewed bug fixes and amendments.

## Implemented Java API inventory

Signatures below are the source contracts, with DTOs nested in their owning
interface unless linked separately. UUID parameters have explicit User/Customer/
Account meanings; they are not interchangeable client authority. VND is implicit
in `VndAmount`; no generic business layer is added to `common`.
All reference arguments/components are non-null; `Optional` represents permitted
absence. Constructors enforce their stated value preconditions, not complete
business validation or authorization. Pure Java APIs have no Jakarta validation,
HTTP or Spring annotations. HTTP adapters validate raw requests; providers enforce
ownership/current-state invariants before writes. Actor IDs/records and query
snapshots are values, never authorization tokens. No signature or executable API
behavior was changed by the final approval review.

### Accounts — `com.finfast.accounts.api`

| Source | Exact methods / boundary values | Caller and semantics |
|---|---|---|
| [AccountQueries](../../backend/src/main/java/com/finfast/accounts/api/AccountQueries.java) | `Optional<CustomerAccount> findByCustomerId(UUID customerId)`; `Optional<CustomerAccount> findCustomerByNumber(AccountNumber number)` | Authorized readers on approved edges in 15; CUSTOMER-only snapshots, Treasury excluded; caller authorizes disclosure |
| [AccountProvisioning](../../backend/src/main/java/com/finfast/accounts/api/AccountProvisioning.java) | `CustomerAccount openCustomerAccount(UUID customerId)` | Customer only; join registration; generated immutable number, ACTIVE/VND/zero, one owner |
| [TreasuryProvisioning](../../backend/src/main/java/com/finfast/accounts/api/TreasuryProvisioning.java) | `UUID openTreasury()` | Bootstrap only; zero Treasury inside atomic setup, never independent usable initialization |
| [AccountStatusCommands](../../backend/src/main/java/com/finfast/accounts/api/AccountStatusCommands.java) | `Change changeCustomerStatus(UUID accountId, CustomerAccount.Status desiredStatus)`; `Change(accountId, before, after)`, `changed()` | Administration validates ADMIN/reason and owns outer status+audit transaction; Treasury excluded; equal states mean UNCHANGED |
| [PostingAccounts](../../backend/src/main/java/com/finfast/accounts/api/PostingAccounts.java) | `Decision transfer(UUID sourceAccountId, UUID expectedSourceCustomerId, AccountNumber recipientNumber, VndAmount amount)`; `Decision allocate(AccountNumber recipientNumber, VndAmount amount)`; `void initializeTreasury(UUID treasuryAccountId, VndAmount initialSupply)` | Ledger only; require active outer transaction, protect/revalidate all participants before writes; allocation selects Treasury internally; initialization callable only by the Ledger `GenesisPosting` implementation |
| [CustomerAccount](../../backend/src/main/java/com/finfast/accounts/api/CustomerAccount.java) | `(UUID accountId, UUID customerId, AccountNumber number, Status status, VndAmount balance)`; `Status = ACTIVE / FROZEN` | Immutable CUSTOMER snapshot; no lock/authorization token or authority to spend |
| [AccountNumber](../../backend/src/main/java/com/finfast/accounts/api/AccountNumber.java) | `(String value)` | Exact opaque nonblank number, at most the existing schema's 32 characters; no chosen generator/format or silent normalization |

`PostingAccounts.Decision` is `Applied(debitAccountId, creditAccountId)` or
`Rejected(ErrorCode reason)`. Applied is a pending paired change; Rejected means
no balance writes. All technical failures escape for rollback. Accounts owns
status, type, owner, funds and checked-arithmetic invariants; it never accepts
arbitrary deltas or exposes entities, repositories, SQL connections or lock handles.

### Ledger — `com.finfast.ledger.api`

| Source | Exact methods / boundary values | Caller and semantics |
|---|---|---|
| [FinancialPosting](../../backend/src/main/java/com/finfast/ledger/api/FinancialPosting.java) | `PostingResult postTransfer(Intent intent)`; `Intent(initiatorUserId, sourceCustomerId, sourceAccountId, recipientNumber, amount, Optional<String> note)` | Transfers only; trusted source/initiator, Customer→different Customer, two opposite entries; no allocation/GENESIS method |
| [AllocationPosting](../../backend/src/main/java/com/finfast/ledger/api/AllocationPosting.java) | `PostingResult postAllocation(Intent intent)`; `Intent(adminUserId, recipientNumber, amount, reason)` | Administration only; ADMIN and exact nonempty reason, selected Treasury→Customer, required audit owned by Administration |
| [GenesisPosting](../../backend/src/main/java/com/finfast/ledger/api/GenesisPosting.java) | `PostingResult.Pending initialize(UUID treasuryAccountId, VndAmount initialSupply)` | Bootstrap only; sole controlled one-entry supply initialization; verify established dataset, no reset/reseed |
| [PostingResult](../../backend/src/main/java/com/finfast/ledger/api/PostingResult.java) | Sealed `Pending(transactionId, reference, amount, recordedAt)` / `Rejected(ErrorCode reason)` | Neither proves commit. Pending amount is positive; time is recorded posting time, not physical PostgreSQL commit time |
| [LedgerQueries](../../backend/src/main/java/com/finfast/ledger/api/LedgerQueries.java) | `Page history(UUID participantAccountId, PageRequest request)`; `Optional<Receipt> receipt(UUID participantAccountId, String reference)` | History only; currently authorized participant; committed Customer movements, no GENESIS/rejected/non-final facts |

`LedgerQueries` owns `PageRequest(limit, Optional<String> cursor)`,
`Page(List<Movement> items, Optional<String> nextCursor)`,
`Movement(reference, Type, Direction, amount, counterpartyLabel, recordedAt)` and
`Receipt(reference, Type, amount, sourceLabel, destinationLabel, recordedAt)`.
Types are INTERNAL_TRANSFER/DEMO_ALLOCATION; direction INCOMING/OUTGOING;
amounts are positive VND. Page collections are copied and immutable; default 20,
valid limit 1..100. The opaque cursor representation commits to no counter/encoding
algorithm; owner/account/snapshot binding, integrity and stable ordering remain
provider obligations and TD-004 evidence work. Admin-wide transactions/entries,
reconciliation and dataset verification projections remain deferred to reviewed
concrete consumers rather than exposing an unrestricted repository-shaped API.

### Identity / Customer

| Package / source | Exact methods / records | Caller and semantics |
|---|---|---|
| `com.finfast.identity.api.`[CurrentIdentity](../../backend/src/main/java/com/finfast/identity/api/CurrentIdentity.java) | `CustomerActor requireCustomer()` / `AdminActor requireAdmin()`; actors `(userId, customerId)` / `(userId)` | Transfers/History use CUSTOMER, Administration ADMIN; resolve live server-managed identity, deny missing/expired/wrong role; actor construction alone grants nothing |
| `com.finfast.identity.api.`[AdminProvisioning](../../backend/src/main/java/com/finfast/identity/api/AdminProvisioning.java) | `UUID ensureConfiguredAdmin()` | Bootstrap only, protected setup transaction; Identity privately acquires/hashes credentials; no secret fields/config keys or credential reset through the interface |
| `com.finfast.customer.api.`[CustomerProvisioning](../../backend/src/main/java/com/finfast/customer/api/CustomerProvisioning.java) | `Registration provision(UUID userId, String displayName)`; `Registration(UUID customerId, String accountNumber)` | Identity only; CUSTOMER User/profile/one zero Account share registration transaction. Own projection supplies required registration response data without leaking an Accounts type into Identity |
| `com.finfast.customer.api.`[CustomerQueries](../../backend/src/main/java/com/finfast/customer/api/CustomerQueries.java) | `Optional<Profile> findByUserId(UUID userId)` / `findByCustomerId(UUID customerId)`; `Profile(customerId, userId, displayName)` | Identity resolves its authenticated User; Administration reads minimal authorized metadata; no credentials |

The no-argument Admin capability leaves credential-source/operator mechanism for
Hưng + Trung review. It does not implement secret loading, Admin creation or HTTP
authentication. Existing accounts are verified, never elevated/reset on restart.

### Idempotency — `com.finfast.idempotency.api`

[RequestOutcomes](../../backend/src/main/java/com/finfast/idempotency/api/RequestOutcomes.java)
declares `Decision inspect(Scope scope, CanonicalIntent intent)`,
`void record(Scope scope, CanonicalIntent intent, FinalOutcome outcome)` and
`Optional<FinalOutcome> find(Scope scope)`, for Transfers/Administration only.

- `Scope(UUID initiatorUserId, Operation operation, Key key)` retains the full
  identity. Operations are INTERNAL_TRANSFER/DEMO_ALLOCATION; `Key(String value)`
  preserves case/whitespace, 1..200 characters matching the existing schema/header.
- `CanonicalIntent(short version, String json)` is a versioned immutable canonical
  document, not an arbitrary payload map or hash. Its producer must validate JSON
  and preserve all intent fields, invalid numeric tokens, omitted/empty note and
  exact reason before constructing typed posting DTOs. The encoder, version-v1
  vectors, comparison implementation and fingerprint follow the selected TD-003/16 §7 protocol; tests remain required.
- `Decision` is Execute, Replay(FinalOutcome), Conflict or NotFinal
  (PROCESSING/UNKNOWN). Only a coordinated fresh authoritative inspection may
  authorize Execute. PROCESSING needs exact-scope evidence; unrelated contention
  is UNKNOWN. No non-final result can be recorded as FinalOutcome.
- `FinalOutcome` is Completion(transactionId, reference, amount, recordedAt) or
  Rejection(code, publicMessage). These stable typed fields form the V1 response
  snapshot with implicit VND; no generic HTTP response, current balance or secret
  is exported. A value supplied to record is pending until outer commit; only
  committed lookup/replay establishes durability.

Authorize every inspection, replay and lookup before revealing results. Inspect
and record join the same outer posting/audit transaction; coordinate the entire
scope until completion, compare full intent as well as any digest, retain final
results for dataset lifetime and never replace a reserved key. Lookup absence is
UNKNOWN, not permission to execute or proof funds did not move. Technical failure
escapes for rollback; never continue an aborted DB transaction. Retry outside the
proxy in a fresh transaction with the original scope. BK-003 itself selected no advisory algorithm/hash/retry budget; the later TD-002/003 freeze now selects them. A separately committed PROCESSING row remains excluded.

## Transactions and posting permissions

Follow [data design §5](16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02).
**Approved TD-015 responsibilities:** the initiating application owns the outer
transaction; restricted mutation collaborators must join an existing transaction.
The later TD-002 freeze selects concrete outer `REQUIRED` and restricted mutation `MANDATORY` guards; no annotations are added to these pure public APIs.
Do not independently commit balances, entries, final
outcomes or required success audits with `REQUIRES_NEW`. HTTP success follows actual
proxy commit confirmation, not a method-body return or an earlier SQL write.

BK-003 separates `FinancialPosting` (transfer), `AllocationPosting` (funding) and
`GenesisPosting` (setup), and separates Customer/Treasury provisioning. Approved
capability checks reject unauthorized consumers and foreign provider
implementations. Calls and method references to `PostingAccounts.initializeTreasury`
are restricted to a Ledger class implementing `GenesisPosting`; helper delegation
would require an explicit reviewed rule change. No new module edge or blanket
exception is introduced. These static checks do not implement runtime security.

| Outer use case / owner | Collaborators joining its transaction | Obligations |
|---|---|---|
| Identity registration / Hưng | CustomerProvisioning → AccountProvisioning | User + profile + one zero Account, all or none |
| Transfers / Minh | RequestOutcomes, FinancialPosting → PostingAccounts | Both balances, immutable facts and final outcome commit together |
| Administration allocation / Tiến | RequestOutcomes, AllocationPosting → PostingAccounts; internal audit writer | Include complete required success audit in the same commit |
| Administration status / Tiến + Hải | AccountStatusCommands; internal audit writer | Account protection shared with posting; status + audit or neither; same status UNCHANGED |
| Protected setup / Trung | AdminProvisioning; TreasuryProvisioning + GenesisPosting → PostingAccounts | Atomic Treasury/GENESIS/dataset linkage, exactly once; verify restart, no reseed |

The later TD-002 selected realization is outer concrete application `REQUIRED`, restricted
mutation/coordination collaborators guarded by an existing transaction (normally concrete
`MANDATORY`) on their concrete public implementation methods. **No transaction
annotations, proxies or Bean wiring are installed on these pure APIs.** Implement the selected READ COMMITTED and explicit checked-technical-failure rollback policy on the actual concrete boundary; real proxy tests must verify interception. Interface annotations alone or self-invocation cannot serve
as that evidence; see [Spring interception](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
and [rollback rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html).

Business rejections must occur before writes and return values normally so their
outcome can commit. Do not throw a generic ApplicationException out of the outer
transaction after recording a rejection: rollback could erase it. Provider-owned
immutable ErrorCode catalogs supply safe codes/messages; no new error catalog or
HTTP mapping is approved here. Technical/persistence/commit failures escape for
rollback and remain non-final. Pending posting/result values are never serialized
as final success before the outer proxy confirms commit. Raw invalid money and
recipient input must be captured before typed DTO construction, so final numeric
rejections can replay without lossy coercion. No direct balance setters or ordinary
mint/GENESIS APIs exist. Real concurrency/rollback/authorization evidence is deferred.

## Approval scope and remaining implementation decisions

| Item | Current authority / implementation obligation | Responsible contributors |
|---|---|---|
| R-01 / TD-002/015 | Ownership/capability split and concrete REQUIRED/MANDATORY guard design approved; implement/test real proxy and checked-failure rollback; helper-delegation capability amendments still require narrow review | Tiến + Hải + Minh + Trung |
| R-02 / TD-003 | Canonical v1, stable scope advisory encoding, final-only outcome protocol and existing HTTP result direction approved; golden vectors/PostgreSQL crash/replay tests pending | Minh + Tiến; Sĩ browser recovery |
| R-03 / TD-002 | READ COMMITTED, unsigned UUID sequential lock order and initial 3-attempt/25–75 ms/2 s policy approved; code and real contention evidence pending | Minh + Hải + Tiến |
| R-04 / TD-004 | Counter/high-water/keyset/HMAC-SHA256 cursor selected; Customer labels require a reviewed Account-ID metadata capability, Admin read projections remain absent | Tiến + Hải + Trung |
| R-05 / TD-009 | Explicit protected operator setup selected, no startup reseed; private credentials/command wiring, concurrent initialization and restart proof pending | Hưng + Trung + Tiến |
| R-06 / TD-016 | Existing nine-module/18-edge graph and enforcement remain approved; providers/runtime integration pending; new capabilities/edges need Tiến's amendment | Tiến + affected owners |
| R-07 / TD-008 | Existing OpenAPI preserved, stable protocol/final/rejection/non-final distinctions selected; endpoint-specific catalog/response mapping must fit that contract and pass real HTTP tests | Tiến + provider owners + Sĩ |

These choices are the active implementation baseline and do not await routine reapproval. Only a genuine incompatibility, security defect, financial correctness failure or necessary breaking API triggers amendment. Individual owner endorsement is not implied. [Explicit integration exceptions](../6-governance/25-design-decision-register.md#explicit-exceptions-and-implementation-findings) block only the affected projection paths. Unit fakes support independent work while providers are absent; they cannot establish PostgreSQL correctness.

For allocation audit, Administration can resolve the immutable recipient identity via existing `AccountQueries.findCustomerByNumber`; a missing target produces the appropriate rejection, and final eligibility is still revalidated under Accounts locks. Canonical allocation uses the trusted singleton `TREASURY` source marker, not an unavailable Treasury UUID query. Ledger alone controls the actual Treasury movement.

## Shared contracts and observability

Follow the actual [shared error/money guide](shared-error-money-contracts.md):

```java
VndAmount zero = new VndAmount(0);
VndAmount amount = VndAmount.parse("200000").requirePositive();
long debit = VndArithmetic.negateExact(amount.value());
// ModuleErrors is an illustrative module-owned enum implementing ErrorCode:
// throw new ApplicationException(ModuleErrors.RESOURCE_NOT_FOUND);
```

At the HTTP adapter boundary, money fields use `VndAmountJson`; required HTTP DTO
fields use `@NotNull` and `@Valid`. Pure inter-module APIs instead enforce value
preconditions without framework annotations. Monetary JSON is a decimal string,
Java is checked `long`, PostgreSQL
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
The eleven rules cover module package structure, public-API isolation from its own
internals, approved edges plus provider API access, business cycles, domain and
application isolation, controller JDBC/persistence isolation, clean business code
and the common-to-business dependency prohibition, plus pure public APIs and the
approved restricted-capability policy.

Production scans now exercise the actual public contracts. Internal domain,
application and adapter selections can still be empty; no provider exists yet.
This is not proof about future business implementations.
`RuleVerificationTests` explicitly imports test-only `finfast.architecture.fixtures`
outside Spring's application scan and applies the same rules using that root.
Positive evidence covers own internals, legal public APIs, pure shared contracts,
controller-to-use-case calls and application `@Transactional` usage. Negative
evidence covers foreign repositories/entities, non-API access, a reversed edge,
a two-module cycle, HTTP/Servlet/JDBC/Spring/adapter leakage, direct controller
persistence, domain/application observability and shared-to-business coupling.
Package fixtures also reject module-root classes and lookalike package prefixes.
API-leak fixtures exercise fields, return/parameter types, constructor parameters,
superclasses, implemented interfaces and `List<InternalType>` return/parameter
signatures. Positive fixtures verify API DTOs, internal-to-API/domain dependencies
and permitted shared contracts. This is evidence for those tested generic forms,
not complete enforcement of every generic signature or dynamically resolved type.
Assertions check the intended origin, target and rule violation; negative fixtures
do not enter production scans or production JARs.

`CapabilityVerificationTests` adds positive/negative proof for the real capability
policy and pure API rule: HTTP/JDBC/Jackson/transaction-annotation leaks are
rejected; legitimate Bootstrap/Administration consumers remain allowed; ordinary
Transfers cannot acquire GENESIS/allocation/Account mutation;
Ledger's GENESIS implementation may initialize Treasury through a call or method
reference; ordinary Ledger posting may not; a consumer cannot implement another
module's provider capability. `ModuleContractValueTests` checks opaque key/scope
semantics, absent/empty note preservation, immutable page collections, page bounds,
positive completion values and separate final/non-final type families. These are
contract/value/structural tests, not mocked or real financial posting evidence.

The Treasury rule recognizes a direct caller implementing `GenesisPosting`; it is
not a runtime privilege or proof of a secure GENESIS implementation. A legitimate
private helper arrangement needs a concrete, reviewed rule amendment rather than
a blanket internal-access exception. Other private implementation changes need no
new public contract when they preserve the approved boundaries.

Checks inspect bytecode dependencies. They cannot prove SQL ownership, reflective
calls, authorization, financial atomicity, locks, balanced entries/reconciliation,
idempotency recovery, mandatory audit persistence or successful durable commit.
Real module and PostgreSQL integration tests remain required; no financial AC is
marked passed by these structural checks.

From `backend/`, with Java 21:

```sh
./mvnw --batch-mode --no-transfer-progress -Dtest=ProductionArchitectureTests,RuleVerificationTests,CapabilityVerificationTests,ModuleContractValueTests test
./mvnw --batch-mode --no-transfer-progress verify
```

Architecture checks need no database; the full suite uses isolated PostgreSQL
Testcontainers and requires a compatible runtime. `make test` runs the full suite
from the repository root. No special Maven profile is needed for architecture checks.

## How to Start a Business Module

1. Read assigned use cases/business rules and their approval status.
2. Check ownership, the approved dependency matrix and the current engineering freeze; selected algorithms require implementation/proof rather than routine reapproval.
3. Review the relevant [OpenAPI](contracts/openapi-v1.yaml) and endpoint status.
4. Compare the approved source and compatibility matrix with preserved local work;
   use Tiến's mismatch/change-control process for any difference.
5. Reuse the source contracts above; do not duplicate interfaces/DTOs or move
   packages. Implement providers only under the owning module's `internal` tree.
6. Implement domain behavior in its owning module using pure shared contracts.
7. Add use-case orchestration, transactions and adapters with constructor injection.
8. Add meaningful unit/use-case tests, PostgreSQL and HTTP tests as behavior requires.
9. Run architecture checks, the relevant regressions and documentation checks.
10. Submit a small reviewable PR through the team's normal workflow after review.

If another module needs a capability, report the exact mismatch, consult its owner
and consumers, and obtain Tiến's approval before adding a public capability/edge or
making a breaking change. Update code/tests/docs together. Never import its
internals as a shortcut. Read [AGENTS.md](../../AGENTS.md), the
[backlog](../02-requirements/10-product-backlog.md) and
[test strategy](../05-verification/22-test-strategy-and-acceptance.md) before changing
scope. Tiến holds final cross-module architectural authority.

Use constructor injection for consumers. In unit tests, mock the real provider
interface or supply a test-only fake without component scanning it into the main
application. For example, with existing Mockito test dependencies and prepared
`customerId`/`account` values:

```java
AccountQueries queries = mock(AccountQueries.class);
when(queries.findByCustomerId(customerId)).thenReturn(Optional.of(account));
```

This supports consumer development without a completed provider. Full startup or
integration tests that inject a capability still require a real implementation or
a correctly scoped test replacement; never install a no-op production @Service.
Mocks prove consumer behavior only, not PostgreSQL atomicity or financial ACs.

The approved Java and documentation baselines are available on `develop`. Each owner updates their own feature branch from latest
`develop` using the team's agreed merge/rebase workflow, inspects these contracts,
adapts their implementation and scoped test doubles, runs `verify`/docs checks,
then requests review. This task does not update anyone's branch. For a contract
change, identify consumers and compatible options, consult provider/consumer
owners and obtain prior Tech Lead approval, then update code, this inventory, governance,
architecture/data notes and relevant tests together. On code/document mismatch,
stop the affected integration and report exact paths/signatures/statuses; do not
guess, ratify proposals or create an alternate API.
