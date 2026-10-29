# Human Review Decisions

**Status convention:** Every entry is intentionally **PROPOSED** or **UNRESOLVED**. This file records questions for human authority; it grants no approval.

| ID | Status | Decision required | Baseline evidence | Required authority or evidence |
|---|---|---|---|---|
| RD-01 | UNRESOLVED | Establish owner, branch, review status, and intended disposition of the original local overlay. | The overlay was reported in /home/bmngxn/Projects/finfast; its files are absent from tracked e3dc6ef. | Named owners and intended commit or explicit discard decision. |
| RD-02 | UNRESOLVED | Decide whether Transfers and Idempotency will be implemented from the approved baseline or proposed for integration from reviewed local work. | No tracked Transfers module or Idempotency provider exists. | Module-owner and Tech Lead review of provenance, contracts, transaction safety, tests, and migrations. |
| RD-03 | UNRESOLVED | Resolve the approved History module boundary versus Ledger-local history/receipt services. | Architecture expects History; production source has no history production package. | Tech Lead/module-owner decision or scoped amendment. |
| RD-04 | UNRESOLVED | Decide any API-ERR-01 or transport amendment before changing the frozen OpenAPI or adapters. | The tracked YAML is frozen; the earlier amendment file is local-only. | API contract authority and updated contract-test plan. |
| RD-05 | UNRESOLVED | Assign frontend ownership and select a stack for approved UX/browser flows. | No frontend or browser E2E implementation is tracked. | Product/technical owner decision and browser evidence plan. |
| RD-06 | UNRESOLVED | Supply EV-001/EV-002 evidence and approve the isolated environment for EV-003. | The decision register marks all three evidence gates open. | Project owner and measurable environment/evidence record. |
| RD-07 | APPROVED | Approve the authority-transition policy: OpenSpec becomes maintained behavioral authority only after explicit per-capability transfer; unmigrated approved behavioral documents remain operative; durable product, architecture, API, Java contract, decision, and verification authorities remain separate; registry and human approval are mandatory; no competing maintained behavioral specification is created. | Existing approved documents, frozen OpenAPI, module guide, decision register, and installed OpenSpec spec-driven workflow. | Team and Tech Lead governance approval. |
| RD-08 | DEFERRED | Recommended disposition: defer pilot selection until knowledge governance and OpenSpec initialization are reviewed. Customer-account-overview remains a candidate only. | UC-ACC-01, FR-003, frozen GET /api/v1/accounts/me, tracked controller, and test definitions; provider composition and execution remain limited. | Product, Accounts, API, and documentation owners when a later pilot is selected. |
| RD-09 | APPROVED | Keep docs/6-governance/25-design-decision-register.md as the sole maintained technical decision authority during the initial migration. Do not create an ADR corpus or decisions.md. | The register is canonical; ADRs are planned but absent. | Tech Lead governance approval. |

## Session 2 gate

RD-07 and RD-09 require explicit human governance disposition before Session 2A changes documentation authority or creates migration tracking. RD-08 requires a later explicit decision before any capability pilot. RD-01 through RD-06 remain capability-specific or evidence-specific constraints and do not block Session 2A/2B/2C when their subject matter is untouched.

Do not seed OpenSpec, create ADRs, change canonical documents, integrate local code, or claim operational completion in Session 1.
