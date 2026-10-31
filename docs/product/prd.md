# FinFast V1 product requirements

> **Authority:** Maintained high-level V1 product requirements and acceptance boundaries.
>
> **Approval:** The existing FR-001 through FR-013, UX-001 through UX-005, NFR-001 through NFR-006, browser checks, and section 9 benchmark method were approved as the implementation baseline by Tiến on 2026-10-22. This records Tech Lead approval and does not imply a separate project-owner signature, runtime acceptance, or release acceptance. See the [canonical approval record](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze).

## Product scope and authority

This PRD owns the high-level FR, UX, and NFR outcomes below. The [product vision](vision.md) owns product purpose, users, enduring journeys, P0/P1 scope, exclusions, and A-001 through A-014. The [approved PRD source 09](../02-requirements/09-product-requirements.md) remains operative for detailed requirement clauses and traceability; the exact approved EV-003 workload is maintained in the [verification strategy](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol). [Use cases](../01-discovery/04-use-cases.md), [business rules](../01-discovery/06-business-rules.md), [functional specifications](../02-requirements/12-functional-specifications.md), and [UX journeys](../03-design/14-ux-user-flows-and-e2e.md) retain their detailed behavioral obligations. Exact REST/Java contracts, technical architecture, schema, and technical decisions remain with their canonical sources.

## V1 priorities and boundaries

All FR-001 through FR-013, UX-001 through UX-005, and NFR-001 through NFR-006 are P0 release requirements. All are mandatory for V1 regardless of delivery order.

### Functional requirements

| ID | Product outcome |
|---|---|
| FR-001 | Register a Customer and provision exactly one owned Customer Account |
| FR-002 | Authenticate Customers and Administrators, enforce server authorization, and provide effective sign-out |
| FR-003 | Show an authenticated Customer their own Account and current balance |
| FR-004 | Validate and execute eligible internal Customer transfers |
| FR-005 | Record immutable financial facts and required effects atomically |
| FR-006 | Preserve one final outcome per intent and support safe retry and recovery |
| FR-007 | Present committed movements for an owned Account |
| FR-008 | Present receipts only to participating Customers |
| FR-009 | List the Customer Account information needed for authorized Admin operations |
| FR-010 | Initialize one finite Treasury supply through controlled, once-only GENESIS |
| FR-011 | Allocate existing demo funds with required administrative audit evidence |
| FR-012 | Freeze and unfreeze Customer Accounts with required administrative audit evidence |
| FR-013 | Let authorized Administrators inspect financial and administrative evidence |

The exact clauses and acceptance boundaries for these identifiers remain in [PRD source section 6](../02-requirements/09-product-requirements.md#6-functional-requirements) and the linked canonical behavior sources.

### Experience requirements

| ID | Product outcome |
|---|---|
| UX-001 | Provide the required Customer Web journeys and identify funds as simulated |
| UX-002 | Provide Admin/Back-office operations with clear role separation |
| UX-003 | Provide labeled forms, meaningful validation, visible focus, keyboard operation, readable amounts/statuses, and clear loading, empty, denied, frozen, rejected, uncertain, and committed states without private disclosure |
| UX-004 | Retain the same intent and key through repeat submission and recovery; report success only after the committed outcome is resolved |
| UX-005 | Keep required journeys usable at 360x800, 768x1024, and 1440x900 CSS pixels, with a desktop 200% zoom and keyboard pass; record tested browsers |

Detailed screen layouts, state scenarios, and browser journey scripts remain in [UX journeys](../03-design/14-ux-user-flows-and-e2e.md). AC-038 is the canonical browser acceptance scenario. The approved viewport bounds and UX acceptance criteria remain in [PRD source section 8](../02-requirements/09-product-requirements.md#8-experience-and-non-functional-requirements). These checks do not claim formal accessibility certification.

### Non-functional requirements

| ID | Product outcome |
|---|---|
| NFR-001 | Maintain zero financial-invariant violations across acceptance, concurrent operations, failures, restart, and performance workloads; recover valid intents after transient failures |
| NFR-002 | Persist financial records, final intent outcomes, history, and required success audits across restarts; retain financial history and final outcomes for the dataset lifetime |
| NFR-003 | Protect credentials and private information with adaptive password hashing, HTTPS, protected server-managed sessions, effective logout/expiry, server role/resource checks, and minimum necessary access |
| NFR-004 | Achieve transfer-request p95 at or below 2 seconds at 10 new requests per second for five minutes on a documented demo environment, without trading away correctness |
| NFR-005 | Deliver a containerized application with persistent PostgreSQL, environment-based configuration, protected deployment secrets, automated checks, and reproducible cloud setup and restart instructions |
| NFR-006 | Keep domain-oriented Core capabilities independent of presentation channels; preserve traceability and peer review for financial changes; notifications cannot determine posting success |

Detailed constraints and acceptance evidence remain in [PRD source section 8](../02-requirements/09-product-requirements.md#8-experience-and-non-functional-requirements), the canonical AC and CONS sources, and the technical authorities linked from the [documentation index](../README.md).

P1 candidates and V1 exclusions are owned by the [product vision](vision.md). Every FR, UX, and NFR listed here is a P0 release requirement.

## Business constraints

The following constraints apply across the P0 requirements:

- Use simulated funds and synthetic customer data only. V1 has no connection to external payment systems and does not handle real banking credentials or government identity documents.
- Each Customer has one VND Account, opened at zero, in ACTIVE or FROZEN status. Frozen owners retain authentication and authorized read access; frozen Accounts cannot send or receive movements.
- Customer transfers are synchronous and occur only between different eligible Customer Accounts. V1 has no fees, interest, foreign exchange, overdrafts, or independent commercial transfer cap; available funds and safe numeric bounds still apply.
- A dataset has one finite system-owned Treasury. Its default initial supply is 100,000,000 VND and may be configured only before one controlled GENESIS. Ordinary operations cannot mint, overwrite balances, replenish, reset, or recreate that dataset.
- Financial history contains committed movements only. Request outcomes and administrative audit evidence are distinct from financial postings. Required financial effects and success audits follow the approved atomicity obligations in the detailed sources.
- No external payment system is required. Course or deployment evidence may add a requirement only through explicit review and reconciliation.

The full approved product constraints and their A-001 through A-014 provenance are maintained in the [product vision](vision.md) and [discovery decision log](../01-discovery/07-assumptions-and-questions.md).

## Acceptance boundary

| Requirement set | Product-level acceptance boundary |
|---|---|
| FR-001 through FR-013 | Every P0 product capability in the functional requirements table must be covered |
| UX-001 through UX-005 | Both Customer and Admin journeys meet the experience outcomes above, including the explicit viewport and uncertainty requirements |
| NFR-001 through NFR-006 | Financial invariants, durability, security, performance, deployability, and channel separation meet the stated product constraints |
| UC and AC | Ten approved use cases remain canonical; release evidence covers all 38 AC scenarios and applicable variants |
| BR and CONS | All 38 business rules are P0; CONS-01 through CONS-19 remain risk views of canonical scenarios and must have no evidence gaps |
| EV-001 through EV-003 | External obligations and the provisional performance target have the evidence and owner decisions required below |

Detailed scenario wording and verification procedures remain in the [operative use cases](../01-discovery/04-use-cases.md), [business rules](../01-discovery/06-business-rules.md), [functional specifications](../02-requirements/12-functional-specifications.md), [UX journeys](../03-design/14-ux-user-flows-and-e2e.md), and original [PRD traceability](../02-requirements/09-product-requirements.md#10-requirements-traceability-and-acceptance).

The provisional benchmark target is transfer-request p95 at or below 2 seconds at 10 new requests per second for five minutes on a documented demo environment. Performance acceptance requires the approved workload, complete outcome reporting, and zero invariant violations. The approved workload and clean-pass conditions are in the [verification strategy](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol). This target is not a production service level.

## External evidence and release dependencies

| Evidence | Required evidence and gate |
|---|---|
| EV-001: actual course/deployment requirements | Review the applicable rubric and explicitly reconcile any additional obligation before changing product or platform scope |
| EV-002: cloud resources, budget, persistence, HTTPS, and deployment feasibility | Establish feasibility before selecting resources or the benchmark environment |
| EV-003: provisional performance target | Measure with the approved method and a documented environment before making a performance acceptance claim |

EV definitions and approval provenance remain in [discovery assumptions and questions](../01-discovery/07-assumptions-and-questions.md). The [course requirements](../02-requirements/bk-001-course-requirements.md) and [P1 business and architecture assessment](../02-requirements/bk-001-p1-business-and-architecture.md) remain source evidence. V1 release acceptance remains separate and requires linked evidence and the owner decision in the [release checklist](../05-verification/23-uat-and-release-checklist.md).

