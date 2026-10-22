# Shared error and VND contracts

> External-contract authority — 2026-10-22: [Product/UX/REST approval](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) freezes the existing YAML semantics. Generic foundation 400/500 mappings below remain implemented behavior, not automatically listed V1 operation responses; [API-ERR-01](17-api-contracts.md#protocol-error-integration-exception--api-err-01) requires a narrow integration disposition before affected adapter conformance. No handler, money type or dependency changes here.

> Current authority: [TD-001/003/008 freeze](../6-governance/25-design-decision-register.md#engineering-decisions-and-scoped-statuses). Shared code is implemented, financial providers remain absent on audited `develop`, and selected designs are not evidence of committed banking behavior. Existing runtime and historical test claims below retain their original scope.

Implemented in Step 4E.1: reusable error and numeric infrastructure, with no banking
services, endpoints, persistence adapters or durable financial outcomes.
The [business rules](../01-discovery/06-business-rules.md) remain authoritative
(BR-ACC-03/04, BR-TRF-01/12; numeric acceptance AC-016).
[TD-001 and TD-008](../6-governance/25-design-decision-register.md#engineering-decisions-and-scoped-statuses) are now approved within the freeze's explicit design/existing-contract scopes.
The original scoped implementation followed the then-current direction without approving all proposed routes/codes/mappings. Current TD-008 separately preserves the existing OpenAPI contract direction; it adds no endpoint implementation.

## Expected errors

Define immutable error catalogs under the owning module, normally as enums.
`ErrorCode` and `ApplicationException` depend only on Java. For example, a future
module can define the following catalog; it is illustrative, not a shipped API:

```java
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;

enum ModuleErrors implements ErrorCode {
    RESOURCE_NOT_FOUND;

    public String code() { return "RESOURCE_NOT_FOUND"; }
    public String publicMessage() { return "The requested resource was not found."; }
    public Category category() { return Category.NOT_FOUND; }
}

// At the use-case boundary, after applying the module's disclosure policy:
// throw new ApplicationException(ModuleErrors.RESOURCE_NOT_FOUND);
// If wrapping an expected failure, preserve its cause:
// throw new ApplicationException(ModuleErrors.RESOURCE_NOT_FOUND, cause);
```

Codes are stable machine identifiers. Public messages must be safe catalog
constants, never request values, SQL, credentials or infrastructure exception
messages. The exception snapshots the catalog fields and preserves the original
cause. There is no shared catalog of future business errors or mutable registry.
Do not relabel an unexpected technical failure as an expected application error.

Only `GlobalExceptionHandler` maps transport-independent categories to HTTP:

| Category | HTTP |
|---|---|
| `INVALID_REQUEST` | 400 |
| `NOT_FOUND` | 404 |
| `CONFLICT` | 409 |
| `FORBIDDEN` | 403 |
| `UNPROCESSABLE` | 422 |

These generic adapter conventions do not override the existing endpoint-specific OpenAPI. The later TD-008 freeze preserves that contract direction without inventing new codes/statuses.
The owning use case must choose safe disclosure and authorization semantics.
Controllers need no repetitive catch blocks or success envelope. Expected errors
produce only `{"code":"RESOURCE_NOT_FOUND","message":"The requested resource was not found."}`
and do not generate ERROR reports. Invalid catalog definitions fail fast.

Unexpected exceptions still produce safe `INTERNAL_ERROR`/500; selected existing
infrastructure failures produce `SERVICE_UNAVAILABLE`/503. Technical errors are
logged once at the terminal MVC boundary with their original cause, using the
existing [logging/error contract](logging-and-error-handling.md). Existing headers,
406 handling, vendor JSON, explicit `q=0`, committed-response and async behavior
remain in the existing Spring MVC handler.

Generic `ErrorResponse` remains **code + message only**. Neither a Java exception
nor HTTP 422/503 proves a committed financial rejection or rollback. Only a future
authorized financial use case may return the specialized `FinancialRejectionResponse`
after its final outcome commits. Generic errors never add `REJECTED`, `PROCESSING`
or `UNKNOWN`. The MVC boundary does not cover security filters or background jobs.

## Integer VND

`VndAmount` is a small immutable Java record holding `0..Long.MAX_VALUE`. A named
type prevents accidental non-money JSON serialization and negative amounts, while
avoiding a currency framework. It does not represent signed ledger deltas.

```java
import com.finfast.common.money.VndAmount;
import com.finfast.common.money.VndArithmetic;

VndAmount zero = new VndAmount(0);
VndAmount amount = VndAmount.parse("200000").requirePositive();
VndAmount total = amount.plus(new VndAmount(1));    // 200001
VndAmount remainder = total.minus(amount);         // 1
String decimal = amount.toDecimalString();         // "200000"
long debit = VndArithmetic.negateExact(amount.value()); // -200000
```

`parse` accepts only ASCII `^(0|[1-9][0-9]*)$` within signed 64-bit range. It rejects
null, empty, signs, leading zeros, whitespace, fractions, exponents, Unicode digits
and overflow. Inputs longer than 19 characters are rejected before scanning.
It never trims, rounds, parses through floating point or echoes rejected input.
Malformed values throw `IllegalArgumentException` with a fixed safe message.

`requirePositive()` rejects zero; zero remains valid for balances. `plus` and
`minus` return new nonnegative values. A negative result such as `0 - 1` throws
`IllegalArgumentException`. `VndArithmetic` exposes exact signed addition,
subtraction and negation via Java's `Math.*Exact`; overflow/underflow throws
`ArithmeticException`, including `Long.MAX_VALUE + 1` and negating `Long.MIN_VALUE`.
Signed arithmetic permits negative/zero results; Ledger must enforce nonzero
deltas and its posting rules. There is no silent wraparound or commercial cap.

## HTTP and database boundaries

The separate `common.money.json.VndAmountJson` adapter uses Spring Boot's existing
Jackson 3 `@JacksonComponent` support. DTO fields typed as `VndAmount` read/write
JSON strings, such as `{"amountVnd":"200000"}`. Numeric tokens (including integer
tokens), booleans, objects and arrays are rejected rather than coerced. Ordinary
`long` fields still serialize as numbers; no global numeric setting changes.
Use `@Valid` on request DTOs and `@NotNull` on required `VndAmount` fields to reject
missing/null values. Optional money fields may remain null.

Generic typed money decoding failures reach the existing safe `INVALID_REQUEST`/400 boundary. For transfer/allocation intent acceptance, TD-003 requires the owning financial adapter to retain distinguishable raw typed invalid monetary input before positive value/Intent construction; financially accepted numeric rejections use the existing durable REJECTED/422 contract. Unreadable JSON, ambiguous duplicate properties and unpersistable text remain protocol failures. Do not apply the generic typed decoder blindly to erase this distinction; no codec/handler or semantic OpenAPI change is made in this documentation task.
Positive-operation validation remains explicit in the owning module: validate
before writes and use its safe expected error catalog. Do not globally classify
all `IllegalArgumentException` or arithmetic failures as client errors; an
unexpected programming/numeric error remains a technical failure, never a final
financial rejection.

Persist `amount.value()` with JDBC as `BIGINT` and reconstruct with
`new VndAmount(storedValue)` when that field is nonnegative. No JDBC adapter or
schema change is introduced. Use the existing frozen `AdminFinancialTransaction.entries[].deltaVnd` signed decimal-string schema for Ledger transport; the nonnegative OpenAPI `MoneyVnd` and Java `VndAmount` remain unchanged. No new money type or routine contract approval is needed.

Accounts still owns persisted balance/status/ownership invariants and locking;
Transfers owns positive intent/authorization; Ledger owns balanced immutable
postings/reconciliation; Idempotency owns scoped durable outcomes. Numeric checks
alone prove none of these transaction-level guarantees.

## Verification

With Java 21, from `backend/`:

```sh
./mvnw -Dtest=ApplicationExceptionTests,GlobalExceptionHandlerTests,VndAmountTests,VndArithmeticTests,VndAmountJsonTests test
./mvnw test
./mvnw package
```

The full suite needs a Testcontainers-compatible runtime and uses isolated
PostgreSQL databases. Focused tests cover safe expected/technical HTTP errors,
negotiation/headers, exact numeric boundaries and actual MVC/Jackson money
round trips. They do not establish a posting service or financial final outcome.
