# 17 - FinFast V1 REST contract guidance

The frozen [OpenAPI V1 YAML](contracts/openapi-v1.yaml) is the exact external
REST contract. This guide explains how to inspect it, preserve compatibility,
and review scoped exceptions. It intentionally contains no duplicate endpoint,
request, response, or status inventory.

## Protocol rules

- Read operation paths, request and response schemas, security requirements, and
  status codes directly from the OpenAPI YAML.
- Preserve the approved distinctions among authentication or authorization
  denial, protocol validation, business rejection, idempotency conflict,
  non-final outcome, and technical failure.
- Authorize the current actor and resource before lookup, replay, or mutation.
- A schema validation pass establishes structural validity only. It does not
  establish application conformance, security, transaction correctness, or
  release acceptance.

Detailed behavior and canonical acceptance IDs remain in the approved use
cases, business rules, and operative functional specifications. Exact
Java-side error and money contracts live in source and the
[shared error and money guide](shared-error-money-contracts.md).

## Endpoint inventory

The [OpenAPI YAML](contracts/openapi-v1.yaml) is the sole operation and schema
inventory. Do not maintain a second endpoint table or copy JSON schema into
Markdown. Use the root README's Swagger UI instructions to inspect the local
read-only viewer.

## Outcome and error semantics

Use the operation-specific responses in the YAML. Do not infer a generic
response that is not listed for that operation. In particular, a timeout or
technical failure does not prove a financial rejection, and a successful method
return does not prove the outer transaction committed.

## BK-008 Account Overview technical errors  approved 2026-10-26

The approved scoped amendment adds generic technical 500 and temporary
unavailability 503 responses only to `GET /api/v1/accounts/me`. The exact
responses are in the YAML and the [decision register](../6-governance/25-design-decision-register.md#2026-10-26--bk-008-account-overview-technical-error-response-amendment).
No other endpoint inherits this amendment.

## Protocol-error integration exception  API-ERR-01

The shared exception handler maps invalid request and framework parsing failures
to 400, but no operation defines a 400 response in the frozen contract. Generic
500 responses are also absent except for the approved Account Overview amendment
above. Keep this as a scoped integration exception. Do not add undocumented
statuses, map malformed financial commands to a durable rejection, or make
technical failure look like an empty successful result. Affected owners and the
Tech Lead must review the smallest compatible disposition before claiming conformance. The
[decision register](../6-governance/25-design-decision-register.md) records
approval provenance; no global contract change is implied here.

## BK-007 security-denial amendment proposal  pending Tech Lead review

The proposed per-operation 403 response change remains pending and has not
changed the frozen YAML. It covers five authentication operations; the separate
Receipt filter gap needs its own owner disposition. The affected operations are
recorded in the
[decision register](../6-governance/25-design-decision-register.md#bk-007-security-denial-contract-review--2026-10-25).
Keep secure denial behavior in place while the scoped decision is pending. Do
not infer approval, add a blanket response, or claim full conformance for an
unresolved operation.

## Required contract-test assertions

Use canonical acceptance IDs and test the actual HTTP application for requests,
responses, session/CSRF behavior, ownership, error distinctions, and uncertainty
recovery. The [verification strategy](../verification/strategy.md) sets test and
evidence expectations; detailed behavior remains in its approved source.

## Idempotency contract and recovery clarification

Use the approved intent, key-scope, replay, and recovery rules in the
[use cases](../01-discovery/04-use-cases.md), [business rules](../01-discovery/06-business-rules.md),
and [data design](16-domain-model-and-data-design.md#7-idempotency-and-recovery-arc-04).
The HTTP contract defines the exact operation and response shape. A response
loss does not authorize a replacement request with a new key.

## Frozen API documentation workflow

Inspect the YAML at its existing path, review affected owners, and run the
existing OpenAPI schema and reference checks described in the
[docs index](../README.md#validate-documentation-locally-and-in-ci). Contract
changes require a scoped proposal, compatibility analysis, affected-owner
review, and the approval recorded by the decision authority. Update only the
approved YAML and relevant source/tests in the same reviewed implementation
change. Routine implementation within the frozen contract needs no reapproval.

## Future Spring Boot integration

Any future generated OpenAPI snapshot is a comparison artifact, never a second
independently edited contract and never an automatic replacement for the
approved YAML. Review security and compatibility before exposing a generated
endpoint. This guidance does not add a dependency or authorize a contract change.
