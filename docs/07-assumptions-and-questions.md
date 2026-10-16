# 07 — Assumptions, Questions, and Decision Log

> **Stage:** Phase 1 — Domain Discovery\
> **Status:** Finalized V1 business discovery baseline — project owner decisions recorded\
> **Version:** 1.0 | **Last updated:** 2026-10-16

## 1. Purpose

Record FinFast's own approved product decisions, separate business policy from initial architecture direction, and carry external verification/measurement actions into PRD authoring without reopening settled banking rules. The long-term **FinFast Core Banking Platform** vision is distinct from the limited **FinFast V1 Core Banking Foundation** release with simulated funds.

**Decision authority:** Project owner. **Decision date:** 2026-10-16. **Evidence:** The project owner's V1 discovery finalization instructions supplied for this revision. No participant names, meeting attendance, or other team approvals are asserted.

### Status definitions

- **ACCEPTED:** Assumption explicitly approved by the project owner for this baseline.
- **CLOSED:** Product question resolved with a recorded decision.
- **PENDING VERIFICATION:** An external fact still needs evidence; it does not reopen an approved policy.
- **AWAITING MEASUREMENT:** An approved provisional target has not yet been measured on deployed resources.

No A-001–A-014 or OQ-001–OQ-013 item remains undecided. Technical design still selects mechanisms within these constraints.

## 2. Accepted assumptions register

Every row below has the project owner as decision authority, dated 2026-10-16. A-013 is an initial architecture direction; the other assumptions are business/scope decisions.

| ID | Assumption | Status | Impact / rationale |
|---|---|---|---|
| **A-001** | One Customer identity has exactly one Customer Account in V1. | ACCEPTED | Simple ownership and account provisioning |
| **A-002** | V1 supports VND only; monetary values are integral, with positive Transfer Amounts and signed Ledger Entry Deltas. | ACCEPTED | No FX or floating-point monetary ambiguity |
| **A-003** | Account lifecycle supports only ACTIVE and FROZEN. | ACCEPTED | No application/closure lifecycle added |
| **A-004** | Customer transfers are internal between different Customer Accounts; self-transfer is prohibited. | ACCEPTED | Clear transfer boundary; treasury is not a Customer recipient |
| **A-005** | No fees, interest, FX, overdrafts, or separate commercial transfer cap; available funds and safe numeric bounds still apply. | ACCEPTED | Avoids unapproved products while preventing overflow |
| **A-006** | Admin funding moves existing simulated money from one finite system-owned Treasury Account per dataset. | ACCEPTED | Auditable source; ordinary allocations preserve supply |
| **A-007** | One controlled, auditable GENESIS initializes treasury supply per dataset, with one positive treasury Ledger Entry. | ACCEPTED | Once-only creation, including across restarts; no normal mint/reset operation |
| **A-008** | FROZEN Accounts cannot send or receive money, including demo allocations. | ACCEPTED | One explicit eligibility rule for every new movement |
| **A-009** | Internal transfer processing is synchronous in V1; COMPLETED is reported only after commit. | ACCEPTED | No mandatory broker or complex persisted transfer state machine |
| **A-010** | Administrator identities are provisioned separately through protected bootstrap/administrative procedures. | ACCEPTED | Customer registration grants CUSTOMER privileges only |
| **A-011** | Frozen Account owners may authenticate and retain read-only access to their own details, balance, and history. | ACCEPTED | Account status is distinct from User authentication |
| **A-012** | Financial history contains committed money movements only; rejected requests return errors and may have separate operational/audit records, with final outcomes retained for idempotency. | ACCEPTED | Distinguishes Transfer Requests, Financial Transactions, and Audit Events |
| **A-013** | Initial direction is a domain-oriented Modular Monolith using Java/Spring Boot and PostgreSQL, with Core capabilities independent of presentation channels. | ACCEPTED | No mandatory Kafka, Redis, Kubernetes, or microservices; external requirements require evidence and explicit reconciliation |
| **A-014** | Customer Accounts open at 0 VND. Treasury initial supply defaults to 100,000,000 VND, configurable to a valid finite amount before first GENESIS only. | ACCEPTED | Initial supply is fixed within the existing dataset; reset requires controlled dataset recreation |

## 3. Resolved questions register

All product choices below are **CLOSED** by the project owner on 2026-10-16. Verification references point to separate evidence tasks, not unresolved banking policies.

| ID | Question | Approved decision | Status | Follow-through |
|---|---|---|---|---|
| **OQ-001** | Which Customer details can Admin view? | Only necessary account/customer metadata and authorized transaction/audit views. Listing may show Account Number, Customer identifier, limited display information, status, and balance; never passwords/unnecessary sensitive data. | CLOSED | BR-ADM-06; AC-030 |
| **OQ-002** | Is a separate Auditor/Support actor required in V1? | Only CUSTOMER and ADMIN application roles. No separate Auditor or Support actor. | CLOSED | Actor inventory in 03; any external rubric evidence goes through EV-001 |
| **OQ-003** | Are rejected Transfer Requests in financial history? | No. Return errors to the requester; relevant request/audit outcomes may persist separately. Final business rejections are retained for replay, not posted as Financial Transactions. | CLOSED | BR-LED-06, BR-HIS-04; AC-015 |
| **OQ-004** | How long are idempotency results retained? | For the V1 dataset lifetime; no automatic reuse of a key within initiator + operation type + key scope. | CLOSED | BR-TRF-05; AC-021, AC-034 |
| **OQ-005** | What history pagination, filtering, and retention apply? | Mandatory stable newest-first ordering/pagination; default 20, maximum 100 records per page. Financial history lasts for the dataset lifetime; advanced filtering is P1. | CLOSED | BR-HIS-03; AC-025; technical continuation semantics belong to design |
| **OQ-006** | Are Kubernetes, microservices, brokers, or a particular cloud mandated? | The baseline requires no Kubernetes, Kafka, Redis, or microservices. Actual external course/deployment requirements, if any, must be verified; none are invented here. | CLOSED | Initial architecture direction accepted; EV-001 and EV-002 remain evidence dependencies |
| **OQ-007** | What authentication/security direction is required? | Secure adaptive password hashing, HTTPS, session security, server-side role/resource authorization, and effective logout. Browser direction: server-managed sessions with protected HttpOnly cookies. | CLOSED | BR-ID-01, BR-ID-02; architecture specifies cookie/session protections; AC-027, AC-028 |
| **OQ-008** | Is there a separate transfer cap or balance ceiling? | No independent commercial transfer cap. Amounts and resulting balances must fit supported safe numeric bounds; invalid/overflowing values reject without financial effects. | CLOSED | BR-TRF-12; exact numeric representation/bounds documented in design; AC-016 |
| **OQ-009** | How does a client recover an unknown transfer outcome? | Resolve the original intent with the same Idempotency Key or authorized result lookup. Timeout does not prove failure; do not create a new intent to replace an uncertain one. | CLOSED | BR-TRF-08, BR-TRF-11; AC-011, AC-033 |
| **OQ-010** | What is the starting treasury supply and reset policy? | Default 100,000,000 VND, configurable before one-time GENESIS. No Admin API resets/replenishes it; existing supply cannot be changed through normal operations. Demo reset requires separately controlled dataset recreation. | CLOSED | BR-ADM-02, BR-LED-05; AC-020, AC-021, AC-032 |
| **OQ-011** | What does registration collect? | Display name, email, and password only. No real identity documents, banking credentials, or payment credentials. | CLOSED | BR-ID-01, BR-SIM-02; AC-027 |
| **OQ-012** | What initial reliability and performance targets apply? | Zero financial-invariant violations in acceptance/concurrency tests. Provisional transfer-request p95 <= 2 seconds at 10 requests/second over 5 minutes on a documented demo environment. This is a target for PRD/testing, not a measured result or production SLA. | CLOSED | EV-002 and EV-003; validate against deployed resources and document evidence |
| **OQ-013** | Are notifications or a message broker required? | Notifications remain optional P1 after P0; they cannot block V1 completion. No mandatory message broker. | CLOSED | Scope in 01; no added V1 banking capability |

## 4. Finalized decision log

All entries below record the **project owner** as authority on **2026-10-16**, based on the supplied finalization instructions. Categories distinguish approval of a policy from evidence of external facts or performance.

### DEC-001 — Product vision, release scope, and channels

- **Category:** Approved business/product decision.
- **Decision:** FinFast is an independently developed, extensible Core Banking Platform. Long-term channels include Web Banking, Mobile Banking, and Back-office. V1 is the Core Banking Foundation with simulated funds, minimal responsive Customer Web and Admin/Back-office interfaces, and only CUSTOMER/ADMIN roles. One shared frontend is permitted with role separation.
- **Related items:** OQ-002, OQ-013; BR-SIM-01, BR-SIM-02.
- **Rationale and consequences:** Preserve future extensibility without adding Savings, Lending, FX, Cards, Interbank Transfers, full General Ledger, or native mobile to V1. P1 notifications do not block delivery.
- **Documents:** 01, 02, 03, and 08.

### DEC-002 — Customer identity and Account lifecycle

- **Category:** Approved business decision.
- **Decision:** One zero-balance VND Customer Account per Customer; CUSTOMER/TREASURY type separation; immutable, never-reassigned Account Numbers; only ACTIVE/FROZEN. Frozen owners retain authentication/read access but cannot send or receive. Registration collects display name/email/password with case-normalized uniqueness and secure adaptive hashing; protected operations require server authorization and effective logout.
- **Related items:** A-001–A-005, A-008, A-010, A-011; OQ-007, OQ-011; BR-ID-01, BR-ID-02, BR-ACC-01–BR-ACC-06.
- **Rationale and consequences:** Separate identity from banking status and treasury ownership; bootstrap Admin identities through protected procedures. No extra lifecycle or identity-verification domain.
- **Documents:** 01–06 and 08.

### DEC-003 — Signed-entry Ledger, treasury, and GENESIS

- **Category:** Approved business decision.
- **Decision:** Signed-entry Ledger, not full double-entry General Ledger. Ordinary movements have exactly two opposite signed deltas and atomic effects. One GENESIS per dataset records a positive treasury entry and verifiable initialization; default supply 100,000,000 VND is configurable before initialization only. Total balances thereafter equal initial supply.
- **Related items:** A-006, A-007, A-014; OQ-010; BR-LED-01–BR-LED-06, BR-ADM-02, BR-ADM-05.
- **Rationale and consequences:** Reconcile balances including GENESIS exactly once. No additional minting, normal reset/replenishment, reversal workflow, or accounting-report domain; demo reset recreates a dataset under separate control.
- **Documents:** 02, 04–06, and 08.

### DEC-004 — Transfer safety, outcomes, and idempotency

- **Category:** Approved business decision.
- **Decision:** Synchronous transfers, no self-transfer/fee/overdraft/FX, ACTIVE participants, safe numeric bounds. Idempotency is initiator + operation type + key, retained for dataset lifetime. Concurrent identical intents cannot post twice; identical final rejections replay despite later business changes, and changed payload conflicts. Technical failure is not a permanent business rejection; uncertainty is resolved with the original key.
- **Related items:** A-009; OQ-003, OQ-004, OQ-008, OQ-009; BR-TRF-01–BR-TRF-12, BR-ADM-04.
- **Rationale and consequences:** Protect one intent from duplicate execution and eliminate partial financial effects without a complex persisted state machine. A genuinely new attempt needs a new key.
- **Documents:** 02, 04–06, and 08.

### DEC-005 — Administrative audit and financial-history boundaries

- **Category:** Approved business decision.
- **Decision:** Funding/status changes and their required complete success Admin Audit Events commit together. Freeze/unfreeze needs a reason; repeat status returns UNCHANGED without a posting. Admin sees necessary operational data only. Customer history contains owned committed movements, with stable recent-first pagination (20 default/100 maximum) and dataset-lifetime retention. Rejected requests and audit records remain separate.
- **Related items:** A-012; OQ-001, OQ-003, OQ-005; BR-ADM-01, BR-ADM-03, BR-ADM-04, BR-ADM-06, BR-HIS-01–BR-HIS-04.
- **Rationale and consequences:** No successful unaudited administrative state changes; rejected/unauthorized attempts should produce appropriate operational/security evidence without financial posting. Advanced filtering remains P1.
- **Documents:** 02–06 and 08.

### DEC-006 — Initial architecture and browser authentication direction

- **Category:** Approved initial architecture direction, not a detailed business rule.
- **Decision:** Java/Spring Boot, PostgreSQL, domain-oriented Modular Monolith, Core independent of presentation channels. Secure server-managed browser sessions use protected HttpOnly cookies over HTTPS. No mandatory Kafka, Redis, Kubernetes, or microservices.
- **Related items:** A-013; OQ-006, OQ-007.
- **Rationale and consequences:** Keep V1 deployment and consistency manageable. Later design specifies concurrency mechanisms, numeric representation, cookie/session protections, pagination continuation, and hosting. This discovery task does not design schemas, APIs, packages, or Java classes.
- **Documents:** 01, 06, and 08; EV-001/EV-002 track external facts.

### DEC-007 — Reliability expectation and provisional performance target

- **Category:** Approved reliability expectation; performance target AWAITING MEASUREMENT.
- **Decision:** Financial invariants have zero violations in acceptance/concurrency tests. Target transfer-request p95 <= 2 seconds at 10 requests/second for 5 minutes on a documented demo environment.
- **Related items:** OQ-012; EV-003; AC-007, AC-010–AC-023, AC-033, AC-034.
- **Rationale and consequences:** Provide a testable PRD starting point. No benchmark has been run and no production SLA/availability guarantee is asserted. Validate resources and workload, measure, and record deviations without weakening financial invariants.
- **Documents:** 01 and 08.

### DEC-008 — External evidence remains a separate dependency

- **Category:** Verification workflow; not approval of unverified external facts.
- **Decision:** Keep OQ-006/OQ-012 product policies CLOSED while verifying the actual course rubric, deployment constraints/budget, and observed performance. A discovered conflict requires explicit review by the project owner rather than silently expanding V1.
- **Related items:** EV-001–EV-003.
- **Rationale and consequences:** PRD authoring can start now; architecture/deployment acceptance must carry the evidence gates below. No course mandate, cloud choice, participant approval, or measured target is fabricated.
- **Documents:** 01 and 08.

## 5. Phase 1 risks and mitigations

| Risk | Why it matters | Mitigation |
|---|---|---|
| V1 expands toward a full banking suite | Delays foundation delivery | Preserve explicit exclusions; future domains and P1 need later prioritization |
| Ledger/transaction/request terms diverge | Conflicting requirements and tests | Use [02 — Glossary](./02-domain-glossary.md); positive Transfer Amount differs from signed delta |
| Concurrency or atomic audit is omitted | Negative balances, duplicates, or unaudited changes | Carry AC/CONS coverage and rule traceability into PRD/testing |
| External course/deployment constraint discovered late | Deployment direction may need review | Verify EV-001/EV-002; do not invent requirements |
| Restart repeats seed or changes supply | Money is created incorrectly | Once-only GENESIS; AC-020/AC-021 and conservation checks |
| Documents diverge from implementation | Misleading source of truth | Future PRs update affected use cases, rules, and acceptance evidence |
| Real sensitive data enters demo | Privacy/security exposure | Synthetic Customers and simulated money only |
| Performance target treated as achieved | False readiness or production claims | EV-003 measurement evidence is separate from documentation readiness |

## 6. Verification actions and PRD preparation gates

Suggested responsible roles are **unassigned responsibilities**, not named participants or completed approvals. No fabricated calendar deadlines are used.

| ID | Category / status | Action and evidence required | Suggested responsibility | Gate |
|---|---|---|---|---|
| **EV-001** | External requirement — PENDING VERIFICATION | Obtain the actual course/deployment rubric, if applicable; record any mandated platform, topology, or evidence. If none applies, record that fact. Escalate conflicts with the approved baseline to the project owner without silently reopening/changing banking policy. | Project owner / delivery lead | Before architecture and deployment plan are finalized |
| **EV-002** | External environment — PENDING VERIFICATION | Confirm cloud provider, budget, resource limits, persistent storage, HTTPS feasibility, and reproducible deployment evidence for the demo environment. | Deployment responsibility | Before resource selection and benchmark execution |
| **EV-003** | Performance target — AWAITING MEASUREMENT | Document the environment/workload, run the 10 requests/second, 5-minute transfer workload, measure p95 against 2 seconds, report outcomes/errors and invariant checks, and record resource-driven deviations for owner review. | Testing / deployment responsibilities | Before claiming performance acceptance or demo completion |

PRD authoring must also specify later design deliverables for supported numeric bounds, session/cookie protections, concurrency coordination, and deterministic history continuation. Those are implementation choices within settled policies, not additional open banking questions. Audit storage/retention implementation must preserve required durable auditability; no automatic deletion period is approved here.

## 7. Discovery-to-PRD handoff checklist

- [x] Every mandatory V1 capability is linked to existing UC identifiers or an explicitly controlled initialization prerequisite.
- [x] All 38 mandatory BR rules map to use cases and acceptance scenarios in [06](./06-business-rules.md#mandatory-rule-coverage).
- [x] A-001–A-014 are ACCEPTED and OQ-001–OQ-013 are CLOSED by the project owner.
- [x] Financial Transactions, request outcomes, and audit evidence are distinguished consistently.
- [x] Remaining external checks/measurements have explicit evidence actions and gates.
- [x] [08 — PRD Handoff](./08-prd-handoff.md) records the approved input and readiness checklist; the actual PRD and implementation remain future work.

These checks establish documentation readiness to **start writing** the PRD, not completion of runtime, security, deployment, or performance verification.

## 8. Source of business examples and further research

FinFast is independently authored. External references support general understanding; they are not normative business requirements, implementation dependencies, source-code mappings, or copied implementation plans:

- [Apache Fineract source repository](https://github.com/apache/fineract)
- [Fineract documentation](https://fineract.apache.org/docs/stable/)

**Related:** [01 — Product Vision](./01-product-vision-and-scope.md) · [06 — Business Rules](./06-business-rules.md) · [08 — PRD Handoff](./08-prd-handoff.md)
