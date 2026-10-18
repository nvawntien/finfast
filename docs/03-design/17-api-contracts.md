# 17 — FinFast V1 REST API Contracts (candidate v0.1)

> **PROPOSED technical contract**, not approved/existing endpoints. Based on FR-001..013, UX-001..005. Companion machine-readable [OpenAPI](contracts/openapi-v1.yaml) represents the interface direction; teams must review and freeze versions before parallel frontend/backend work. Some optional fields and error codes below are design choices.

## Protocol rules

- All paths are under `/api/v1`; JSON/UTF-8, TLS required, cookie-based **server session**. `POST`, `PATCH` and `DELETE` require CSRF protection. `Origin` controls supplement CSRF; session scopes enforced server-side.
- Monetary `amountVnd`, balance and `deltaVnd` are **decimal strings encoding integral VND** in JSON (proposed safe-precision transport; backend uses checked signed 64-bit). No floating-point amounts, fractional VND or client-defined currency conversion.
- For `POST /transfers` and `POST /admin/allocations`, required `Idempotency-Key` HTTP header; key retained for the dataset lifetime scoped to `(authenticated initiator user ID, operation type, key)`. New user intent uses new key; retry/resolve original intent reuses the key.
- Source account is always derived server-side from authenticated Customer; do not accept a caller-controlled source account.
- `COMPLETED` exists only after durable commit. Semantics of final business rejection, key conflict, processing and technical-unknown are distinct; none may expose financial data to an unauthorized requester.
- Date-time fields ISO-8601 UTC; `reference` opaque and immutable. Account Numbers are not authentication credentials.

## Endpoint inventory

| Endpoint | Who | Intended behavior | FR trace |
|---|---|---|---|
| `POST /auth/register` | Public | Register Customer, create exactly one zero-balance Account | FR-001 |
| `POST /auth/login` | Public | Validate and establish server session | FR-002 |
| `POST /auth/logout` | Session | Revoke current server session | FR-002 |
| `GET /auth/session` | Session | Current role / minimal authenticated identity | FR-002 |
| `GET /auth/csrf` | Browser | Retrieve browser CSRF token (safe endpoint) | NFR-003 |
| `GET /accounts/me` | CUSTOMER | Read owned Account / balance/status | FR-003 |
| `POST /transfers` | CUSTOMER | Synchronous internal transfer; original-key retries | FR-004..006 |
| `POST /request-outcomes/resolve` | CUSTOMER/ADMIN | Resolve caller's original `(operation,key)` without reference | FR-006 |
| `GET /accounts/me/history?limit=&cursor=` | CUSTOMER | Stable newest-first committed history; default 20/max 100 | FR-007 |
| `GET /transactions/{reference}/receipt` | participating CUSTOMER | Read immutable receipt for participating movement | FR-008 |
| `GET /admin/accounts` | ADMIN | Necessary Customer Account metadata | FR-009 |
| `POST /admin/allocations` | ADMIN | Finite treasury funding and atomic success audit | FR-011 |
| `POST /admin/accounts/{accountNumber}/status` | ADMIN | Reasoned ACTIVE/FROZEN mutation, or UNCHANGED | FR-012 |
| `GET /admin/financial-transactions` | ADMIN | Financial facts, GENESIS labeled | FR-013 |
| `GET /admin/audit-events` | ADMIN | Separate Admin/security operational evidence, not financial history | FR-013 |

There is deliberately **no HTTP endpoint for GENESIS, Admin creation, treasury mint/replenish/reset, direct balance overwrite, editing/deleting ledger entries, external transfers, or P1 notifications**. Controlled bootstrap/GENESIS belongs in restricted deployment operations.

## Key request/response examples (schematic)

**Customer transfer**

```http
POST /api/v1/transfers
Cookie: FINFAST_SESSION=<opaque-secret>
X-CSRF-TOKEN: <session-specific-token>
Idempotency-Key: 1e0a-9ca8-demo
Content-Type: application/json

{"recipientAccountNumber":"FF-000002","amountVnd":"200000","note":"Demo"}
```

```json
{"outcome":"COMPLETED","reference":"FT-EXAMPLE-001","amountVnd":"200000","currency":"VND","committedAt":"2026-10-17T08:20:00Z"}
```

**Financial HTTP 422 — final business rejection:** `POST /api/v1/transfers` and `POST /api/v1/admin/allocations` return this only after the final rejection is durably committed, with zero financial postings. The specialized `FinancialRejectionResponse` requires all three fields: `outcome` fixed to `REJECTED`, a machine-readable `code`, and a safe human-readable `message`. The same request with the original idempotency key replays the final rejection even if funded later:

```json
{"outcome":"REJECTED","code":"INSUFFICIENT_FUNDS","message":"Insufficient available funds"}
```

**Uncertain outcome resolution**

```http
POST /api/v1/request-outcomes/resolve
X-CSRF-TOKEN: <session-specific-token>
Content-Type: application/json

{"operationType":"INTERNAL_TRANSFER","idempotencyKey":"1e0a-9ca8-demo"}
```

For ADMIN, only `DEMO_ALLOCATION` owned by *that Admin initiator* is resolvable. When a matching request is still running or cannot be confirmed, return a clearly **non-final** status (e.g. `PROCESSING` / `UNKNOWN`); never claim a missing result means funds did not move if a request is still in flight. On a confirmed final result, return the stored outcome and original reference if committed. Server must validate role/resource permissions **before** lookup.

**Admin allocation**

```json
{"targetAccountNumber":"FF-000001","amountVnd":"1000000","reason":"Seed customer demo"}
```

**Admin status**

```json
{"desiredStatus":"FROZEN","reason":"Demonstration of account control"}
```

**Paginated Customer history**

```json
{
  "items":[{"reference":"FT-EXAMPLE-001","type":"INTERNAL_TRANSFER","direction":"OUTGOING","amountVnd":"200000","currency":"VND","counterpartyLabel":"FF-000002","committedAt":"2026-10-17T08:20:00Z"}],
  "nextCursor":null
}
```

Cursor is an opaque signed/validated continuation token binding Customer Account, snapshot bound, last order position, and page constraints (see [16 §8 history ordering](16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05)). A malformed or cross-account token cannot reveal records.

## Outcome and error semantics (suggested mapping)

| Condition | Example status | Response / invariant |
|---|---|---|
| Completed transfer/allocation | 200 | `COMPLETED`, original reference, only after DB commit |
| Final business rejection on transfer/allocation commands | 422 | Required `outcome: REJECTED`, `code`, `message`; durably replayable, **zero postings** |
| Same scope, different canonical payload | 409 | `IDEMPOTENCY_CONFLICT`; original outcome unaffected |
| Same key still executing | 202 | `PROCESSING` when the exact intent is known active, otherwise `UNKNOWN`; non-final retry guidance |
| Technical uncertainty | 503 / timeout | Outcome **unknown**; client retains key and original payload, resolves/retries safely |
| Session absent/invalid | 401 | No protected access or disclosure |
| Wrong role or inaccessible resource | 403 or privacy-preserving 404 | No data disclosure or financial effect |
| Unknown permitted reference / missing outcome | 404 | Safe not found; missing outcome is not proof of financial failure, so retain original key |
| Current status requested | 200 | `UNCHANGED`, no new financial/audit-success movement |

HTTP status codes are proposals, not banking-policy decisions. **Business-rejected requests must be finalized and persisted before the server represents them as final; failure to persist means recoverable technical failure.**

Ordinary HTTP errors do not automatically represent finalized financial outcomes. Registration's HTTP 422 retains the generic `ErrorResponse`, whose `outcome` remains optional; it does not imply a durable financial intent or idempotency outcome.

## Required contract-test assertions

1. Auth/session cookie, logout, CSRF rejection, role/ownership and hidden-resource paths (AC-002/024/027/028/030).
2. Same-key committed response and final rejection replay, changed request conflict, concurrent duplicates, lifetime retention (AC-005/006/013..015/034).
3. Loss of response/reference and eventual result resolution using only operation + original key (AC-011/033).
4. Numeric VND range (string parsing), no floating-point rounding or unsupported currency (AC-016/036).
5. History cursor stable across ties and concurrent inserts, default 20 / max 100 (AC-025).
6. No public GENESIS/mint/replenishment; no direct financial modification routes (AC-024/032/037).

## Idempotency contract and recovery clarification

The [canonical intent/coordination proposal in 16 §7](16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04) defines exact field comparison, including transfer note and allocation reason, before interpreting a scoped key as replay or conflict. JSON property order is irrelevant; changed meaningful fields conflict. Client retries preserve the original intent and key. Current authorization is checked before replay, but mutable funds/status are not revalidated for an already-final result. Reauthentication does not require changing the key; frozen owners retain permitted read/recovery access.

`COMPLETED` and `REJECTED` are durable final outcomes only after commit. `PROCESSING` and `UNKNOWN` describe temporary knowledge, never a permanently cached business failure. A technical exception, a client timeout, or a missing result response cannot justify creating a new-key replacement transfer. Resolve using the authorized outcome operation or resend the original request with the same key. Lookup does not itself execute a transfer. Concurrent duplicates may wait or receive a non-final response, then resolve safely. A completed allocation implies its required Admin audit committed with balances, ledger facts and final result.

The table above uses the existing YAML statuses: transfer/allocation completion is 200, registration creation is 201, and `POST /api/v1/request-outcomes/resolve` may return HTTP 200 with an `OperationResult` whose outcome is `REJECTED`. This is a lookup result, not a new financial posting. Same-key changed-payload conflict is 409; final financial business rejection is 422 on the financial command. Specific business rejection codes and malformed-request validation are still TD-008 review items. This refinement does not add routes or change request schemas.

## Design-stage API documentation workflow

Use [README — Browse the API locally](../README.md#browse-the-api-locally-doc-01) for the official pinned Swagger UI container, read-only mount, localhost URL, restart and shutdown commands. Use [README — validation](../README.md#validate-documentation-locally-and-in-ci) to run the same pinned OpenAPI validator and reference checks as CI. The UI renders the checked-in YAML without a banking backend; a placeholder server remains a placeholder, and request execution is disabled in this viewer.

The existing [OpenAPI YAML](contracts/openapi-v1.yaml) is the **single design-stage API source of truth**. Review semantic changes together with this explanation, PRD traceability and the decision register. Schema/reference validation detects structural defects; it does not establish implemented behavior, concurrency safety, authorization or acceptance approval.

## Future Spring Boot integration

**Deferred until implementation:** select and record the actual Java/Spring Boot line first, then choose a compatible Springdoc-OpenAPI release using the official [Springdoc compatibility guidance](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot). No Springdoc dependency or build file is added now. Decide documentation endpoint exposure with the same session/role/security review as other development tooling.

Keep this YAML as the reviewed contract. Springdoc may expose an implementation-derived OpenAPI snapshot from running controllers for comparison; store that snapshot only as a generated test artifact, never as a second independently edited contract. A later CI step should normalize non-semantic generated metadata and compare paths/methods, required fields, types, response codes, security schemes and examples against the reviewed YAML. Any intentional difference requires contract review; do not automatically overwrite the source with generated output.

Add real HTTP integration/contract tests against the application with PostgreSQL for required headers, cookies/CSRF, authorization, decimal-string money, final/error outcomes and pagination. Generated-schema comparison alone cannot prove ownership checks, atomic audit or safe recovery. Reuse canonical AC IDs and the [22 test groups](../05-verification/22-test-strategy-and-acceptance.md); no new business acceptance criteria are introduced.
