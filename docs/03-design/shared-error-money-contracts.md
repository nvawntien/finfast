# Shared error and money guidance

This guide owns cross-cutting error and VND semantics. Exact Java declarations
are maintained in [common error source](../../backend/src/main/java/com/finfast/common/error/)
and [common money source](../../backend/src/main/java/com/finfast/common/money/).
The [OpenAPI V1 YAML](contracts/openapi-v1.yaml) owns external schemas and
operation responses. This guide does not define banking workflows or final
financial outcomes.

The 2026-10-22 Product/UX/REST approval freezes the existing YAML semantics.
Generic framework mappings do not add operation responses. The scoped
[API-ERR-01 integration exception](17-api-contracts.md#protocol-error-integration-exception--api-err-01)
remains subject to its recorded decision and owner review.

## Expected errors

- The owning module defines stable machine codes and safe public messages for
  its expected errors. Messages are fixed catalog text, not request values, SQL,
  credentials, or infrastructure exception details.
- Preserve technical causes when propagating failures, but do not expose raw
  exception messages or turn an unexpected technical failure into an expected
  business error.
- Keep domain and application rules independent of HTTP. The HTTP boundary maps
  errors only where the exact OpenAPI operation permits that response.
- A generic error response does not imply `REJECTED`, `PROCESSING`, `UNKNOWN`,
  rollback, or a committed financial outcome. Financial finality belongs to the
  owning use case and caller transaction.

The exact exception, error-code, and transport implementations live in
[`common.error`](../../backend/src/main/java/com/finfast/common/error/) and the
[HTTP error handler source](../../backend/src/main/java/com/finfast/common/error/GlobalExceptionHandler.java).
Use [API guidance](17-api-contracts.md) for contract inspection and the pending
operation-specific exception disposition.

## Integral VND

- Represent VND monetary values with integral arithmetic. Do not convert money
  through binary floating point, round values, or silently overflow.
- Keep nonnegative monetary amounts distinct from signed Ledger deltas. The
  owning Ledger rules validate nonzero entries and balanced postings.
- At the REST boundary, use the exact string representation and bounds in the
  OpenAPI contract. Do not maintain another JSON schema or copy parser details
  into Markdown.
- Validate before persistence. Database `BIGINT` fields store integral VND;
  schema bounds alone do not establish balanced postings, reconciled balances,
  or total-supply correctness.

Exact declarations and serializers are in
[`VndAmount`](../../backend/src/main/java/com/finfast/common/money/VndAmount.java),
[`VndArithmetic`](../../backend/src/main/java/com/finfast/common/money/VndArithmetic.java),
and the [money JSON adapter](../../backend/src/main/java/com/finfast/common/money/json/VndAmountJson.java).
The [migration README](../../backend/src/main/resources/db/migration/README.md)
describes schema constraints; the
[data and transaction guide](../architecture/data-and-transactions.md) describes
financial invariants and commit requirements.

## HTTP and database boundaries

Keep protocol errors distinct from accepted business rejections. Malformed JSON,
unreadable values, or invalid wire encodings follow the framework and exact
operation contract. Only an authorized use case can create a durable financial
rejection, and the required outcome must commit with the operation. A technical
or uncertain commit failure remains non-final. See the operative
[use cases](../01-discovery/04-use-cases.md) and
[business rules](../01-discovery/06-business-rules.md) for behavior.

Generic exception mappings and typed money conversion are implementation
mechanisms, not permission to add REST statuses, coerce input, or bypass module
ownership. Exact response differences and unresolved integration exceptions
remain in [API guidance](17-api-contracts.md) and the decision register.

## Verification

Run the relevant existing Java and PostgreSQL tests through the commands in the
[root README](../../README.md) and [Makefile](../../Makefile). Actual test sources
live under [`backend/src/test/java/com/finfast`](../../backend/src/test/java/com/finfast/).
The [verification strategy](../verification/strategy.md) explains the evidence
needed for real HTTP, transaction, and financial outcomes. Structural or unit
tests alone do not prove durable financial behavior.
