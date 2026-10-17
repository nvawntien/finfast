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

**Replayed final business rejection** (the same key + same request returns same final rejection even if funded later):

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

Cursor is an opaque signed/validated continuation token binding Customer Account, snapshot bound, last order position, and page constraints (see [ADR-003](21-adr-history-continuation.md)). A malformed or cross-account token cannot reveal records.

## Outcome and error semantics (suggested mapping)

| Condition | Example status | Response / invariant |
|---|---|---|
| Completed transfer/allocation | 200 or 201 | `COMPLETED`, original reference, only after DB commit |
| Final business rejection | 422 | `REJECTED` + reason code, durably replayable, **zero postings** |
| Same scope, different canonical payload | 409 | `IDEMPOTENCY_CONFLICT`; original outcome unaffected |
| Same key still executing | 202 | `PROCESSING` + retry guidance; not a final result |
| Technical uncertainty | 503 / timeout | Outcome **unknown**; client retains key, resolves/retries safely |
| Session absent/invalid | 401 | No protected access or disclosure |
| Wrong role or inaccessible resource | 403 or privacy-preserving 404 | No data disclosure or financial effect |
| Unknown permitted reference | 404 | Safe not found |
| Current status requested | 200 | `UNCHANGED`, no new financial/audit-success movement |

HTTP status codes are proposals, not banking-policy decisions. **Business-rejected requests must be finalized and persisted before the server represents them as final; failure to persist means recoverable technical failure.**

## Required contract-test assertions

1. Auth/session cookie, logout, CSRF rejection, role/ownership and hidden-resource paths (AC-002/024/027/028/030).
2. Same-key committed response and final rejection replay, changed request conflict, concurrent duplicates, lifetime retention (AC-005/006/013..015/034).
3. Loss of response/reference and eventual result resolution using only operation + original key (AC-011/033).
4. Numeric VND range (string parsing), no floating-point rounding or unsupported currency (AC-016/036).
5. History cursor stable across ties and concurrent inserts, default 20 / max 100 (AC-025).
6. No public GENESIS/mint/replenishment; no direct financial modification routes (AC-024/032/037).
