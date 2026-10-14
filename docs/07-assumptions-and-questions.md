# 07 — Assumptions, Open Questions, and Decision Log

> **Stage:** Phase 1 — Domain Discovery  
> **Status:** Working draft — **no assumption below has been approved by the team yet**  
> **Version:** 0.1 | **Last updated:** 2026-10-14

## 1. Purpose

Separate **working assumptions** from **confirmed decisions**, assign owners to unanswered questions, and stop unresolved business ambiguity from silently becoming code. Research assumptions may be reasonable for a student V1 without being universal banking practices.

### Status definitions

- **PROPOSED:** Author recommendation; needs team review.
- **ACCEPTED:** Explicitly agreed by team (record date/decision maker).
- **REJECTED:** Team selected a different direction (record replacement).
- **OPEN:** Question has not been decided.
- **CLOSED:** Decision and rationale recorded.

## 2. Working assumptions register

| ID | Assumption | Status | Impact / rationale |
|---|---|---|---|
| **A-001** | One customer identity has exactly one virtual customer account. | PROPOSED | Simpler onboarding and ownership |
| **A-002** | V1 supports VND only; amounts are non-fractional integers. | PROPOSED | Removes FX/rounding issues |
| **A-003** | Customer accounts have only ACTIVE and FROZEN states. | PROPOSED | No application/closure lifecycle |
| **A-004** | Only internal customer-to-customer transfers; self-transfers prohibited. | PROPOSED | Small bounded transfer domain |
| **A-005** | No fees, interest, limits beyond available balance, or overdrafts. | PROPOSED | Smaller initial rule set |
| **A-006** | Admin funding moves existing simulated money from a finite internal treasury. | PROPOSED | Auditable source of demo funds |
| **A-007** | Treasury created at bootstrap by a once-only audited genesis event. | PROPOSED | Reproducible demo initialization |
| **A-008** | Frozen customer accounts cannot send **or receive** funds, including demo allocations. | PROPOSED | One explicit, testable rule |
| **A-009** | Transfer processing is synchronous in V1. | PROPOSED | Atomicity is easier within one banking consistency boundary |
| **A-010** | Only administrators provision administrator identities. | PROPOSED | Prevents privilege escalation through registration |
| **A-011** | Frozen account holders can still log in and read balances/history. | PROPOSED | Separates account operation status from user identity |
| **A-012** | History shows committed money movements; failed requests are errors and may appear in audit/operational logs, not customer transaction history. | PROPOSED | Avoids conflating attempted and settled simulator movements |
| **A-013** | We initially deploy a modular backend, without separate account and transfer services. | PROPOSED | Avoids distributed transaction costs at V1; revisit if course rubric requires microservices |
| **A-014** | Customer accounts open with 0 VND. The treasury starts with a configurable, finite test amount (example: 100,000,000 VND), not unlimited minting. | PROPOSED | Repeatable demos and conservation testing |

**Important:** No numeric treasury amount, chosen cloud vendor, or concrete database/schema configuration becomes final until approved in the PRD/technical design.

## 3. Open questions register

Owners are **roles**, not assigned students yet. Dates below are decision gates rather than fabricated calendar deadlines.

| ID | Question | Proposed default / options | Owner | Resolve before | Status |
|---|---|---|---|---|---|
| **OQ-001** | Which customer details can Admin view? | Account number, masked name/email, balance and status only | Product/Identity | PRD approval | OPEN |
| **OQ-002** | Does the rubric require an additional auditor/support actor? | No; Admin handles investigations in V1 | Product | PRD approval | OPEN |
| **OQ-003** | Are rejected transfer attempts visible to customers? | Not in financial history; return specific error and write operational/audit trace | Product/Transfers | PRD approval | OPEN |
| **OQ-004** | What retention period applies to idempotency keys/outcomes? | Keep for the entire V1 demo/project lifetime unless storage constraints require a defined TTL | Transfers | API contract | OPEN |
| **OQ-005** | What history pagination, filtering, and retention are needed? | Basic recent-first pagination; retain demo data for the course duration | Product/Frontend | PRD approval | OPEN |
| **OQ-006** | Does the course mandate Kubernetes, microservices, message brokers, or a particular cloud? | Prefer minimal viable deployment until rubric review | Cloud owner | Architecture decision | OPEN |
| **OQ-007** | Which authentication method and minimum credential standards are required? | Secure sessions or short-lived bearer tokens; safe password hashing and transport security | Identity/Cloud | Technical design | OPEN |
| **OQ-008** | What is the maximum single transfer and maximum balance? | Enforce implementation-safe integer bounds; any business cap is optional | Product/Transfers | PRD approval | OPEN |
| **OQ-009** | How should a client recover when a transfer response is lost? | Retry exact request with same idempotency key and/or query a stable reference | Transfers/Frontend | API contract | OPEN |
| **OQ-010** | What is the configured treasury starting amount, and who controls reset? | Once-only bootstrap seed; explicit manual reset in isolated demo environment, not an admin endpoint | Cloud/Admin | Initial deployment | OPEN |
| **OQ-011** | Which fields are collected during customer registration? | Display name, email, password only; no real documents | Product/Identity | PRD approval | OPEN |
| **OQ-012** | What measurable reliability, performance, and deployment targets apply? | Define from rubric, resource budget and test environment | Cloud/Product | PRD approval | OPEN |
| **OQ-013** | Are optional notifications important for the course's event-driven learning outcomes? | P1; implement only after secure transfer workflow passes | Product/Cloud | Sprint planning | OPEN |

## 4. Decision log template

Create an entry **whenever a meaningful assumption/question is accepted or changed**:

```markdown
### DEC-001 — <Decision title>
- Date: YYYY-MM-DD
- Participants: <team members>
- Related items: A-XXX, OQ-XXX, UC-XXX, BR-XXX
- Context: <what problem needs a decision?>
- Options considered: <option A / option B>
- Decision: <explicitly selected option>
- Rationale: <why it fits V1 and course constraints>
- Consequences: <new work, limits, risks>
- Follow-up documents: <files/sections to update>
```

**No final team decision has been recorded in this draft.** Do not change an assumption's status to ACCEPTED merely because it appears in a diagram or test example.

## 5. Phase 1 risks and mitigations

| Risk | Why it matters | Initial mitigation |
|---|---|---|
| V1 scope expands toward full Fineract | Delays end-to-end delivery | Preserve explicit out-of-scope list; P1 only after P0 |
| Members use different meanings for ledger/transaction | Conflicting code and tests | Review [02 — Glossary](./02-domain-glossary.md) before design |
| Account/transfer concurrency missed | Negative balances or incorrect movement history | Write acceptance tests for `CONS-01`–`CONS-07` before implementation |
| Course cloud requirement discovered late | Architecture must be reworked | Answer `OQ-006` in first kickoff |
| Demo data seeded on every restart | Supply increases unexpectedly | Genesis operation must be once-only and auditable |
| Agent-written spec diverges from implementation | Misleading source of truth | PR checklist requires updating affected use cases/rules/tests |
| Unsafe real personal/financial data used in a demo | Privacy and security exposure | Synthetic accounts and virtual money only |

## 6. Suggested first discovery meeting agenda (60–90 min)

1. **10 min:** Confirm problem, educational goals, and rubric constraints.
2. **15 min:** Agree or challenge P0/P1/out-of-scope proposal.
3. **20 min:** Walk through transfer and demo allocation flows, including failure cases.
4. **15 min:** Review assumptions `A-001`–`A-014` and choose which need more research.
5. **10 min:** Assign owners and decision gates to `OQ-001`–`OQ-013`.
6. **10 min:** Decide PRD authoring/review process and next checkpoint.

## 7. Discovery-to-PRD handoff checklist

- [ ] Every P0 feature maps to at least one `UC-*`.
- [ ] High-risk `BR-*` are reflected in the relevant user flows.
- [ ] All business-critical assumptions have been accepted or replaced.
- [ ] Each remaining question has an owner and a point by which it must be answered.
- [ ] The PRD uses the same vocabulary and scope as the discovery docs.
- [ ] Technical design follows the PRD, with architecture trade-offs documented separately.

## 8. Source of business examples and further research

External references help validate concepts, but **all V1 scope decisions remain our own design choices**:

- [Apache Fineract source repository](https://github.com/apache/fineract)
- [Fineract documentation](https://fineract.apache.org/docs/stable/)
- [Fineract consumer-facing architecture reference](https://github.com/apache/fineract-consumer-facing)

**Related:** [01 — Product Vision](./01-product-vision-and-scope.md) · [06 — Business Rules](./06-business-rules.md)
