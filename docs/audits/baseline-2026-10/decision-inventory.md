# Architectural Decision Inventory

**Authority:** docs/6-governance/25-design-decision-register.md remains the canonical decision record. A design approval is not implementation, composition, execution, or release acceptance.

| Decision | Tracked baseline evidence | Correct baseline assessment |
|---|---|---|
| DEC-001..008 | Discovery, PRD, use cases, business rules, and functional specifications are tracked and approved. | Approved product intent. No completion claim follows from the documents. |
| TD-001 | VndAmount, VndArithmetic, JSON support, V1 schema, and related test definitions are tracked. | Integral VND design has source and test-definition evidence. Tests were not run here. |
| TD-002 | PostingAccountsService, JdbcAccountRepository, and Accounts concurrency test definitions are tracked. | The locking and revalidation mechanism exists in source, but its production provider composition and current execution evidence are not established. |
| TD-003 | RequestOutcomes is a tracked public API and V1 has a request_outcome table. | The baseline has no Idempotency internal provider, advisory coordination, canonical-intent encoder, durable outcome implementation, or Transfer consumer. Local-overlay implementation must not be counted. |
| TD-004 | Ledger position allocator, history cursor codec, history query services, schema, and relevant test definitions are tracked. | Source implements selected Ledger mechanisms. Production History composition, secret provisioning, and capacity evidence remain absent. |
| TD-005 | Accounts posting code and test-only reconciliation support are tracked. | Accounts owns balance mutation in source. No production all-account/Treasury reconciliation capability is present. |
| TD-006/007 | Auth-profile security configuration, session and CSRF handlers, login filter, BCrypt support, and test definitions are tracked. | Security foundation has source coverage. Registration and full banking-provider composition are incomplete, and browser/deployment evidence is absent. |
| TD-008 | Frozen OpenAPI YAML and API design are tracked. | The contract is approved. Only eight of its 15 operations are declared by tracked MVC/security source, and source presence is not deployed conformance. |
| TD-009 | Bootstrap use case, writer, conditional configuration, and tests are tracked. | Protected setup is represented as application code, not a configured operator capability. No public GENESIS endpoint is present or expected. |
| TD-010/011 | V2 append-only guards, Administration application services, audit source, and test definitions are tracked. | Schema guards and partial application workflows exist. Real provider composition, write adapters, and end-to-end financial proof are missing. |
| TD-012/013/014 | Build, Compose, observability configuration, and approved benchmark method documents are tracked. | The repository has no tracked benchmark harness or result. No infrastructure/provider selection or throughput claim is established. |
| TD-015/016 | Public module contracts, architecture documents, and ArchUnit test definitions are tracked. | The approved nine-module/18-edge policy is a target and structural policy. Transfers and History roots are absent; Idempotency is API-only. Do not interpret fixtures or contracts as providers. |
| TD-016-AM-01..04 | Approved public capability interfaces and selected internal services are tracked. | Capability interfaces are source evidence only until each provider is composed and exercised. AM-05 remains unselected. |
| API-ERR-01 and related transport changes | The frozen OpenAPI is tracked. The prior report's financial-protocol-errors-amendment.md was local-only and is absent here. | No local proposal file can alter the baseline. Any compatible contract amendment remains a human review decision. |

## Decision boundary

The baseline already has a technical decision register. Do not create ADRs, repeat technical decisions in OpenSpec, or treat a new specification as approval. After an explicit capability transfer, OpenSpec may own maintained behavioral text for that capability, but durable technical decisions remain in the decision register. The remaining questions are recorded with non-approved statuses in review-decisions.md.
