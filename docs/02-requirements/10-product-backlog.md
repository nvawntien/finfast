# 10 — FinFast V1 Delivery Backlog

> Status: **PROPOSED engineering decomposition**, not assigned or approved. Derived from [PRD 09](09-product-requirements.md), canonical [use cases 04](../01-discovery/04-use-cases.md), and [business rules 06](../01-discovery/06-business-rules.md). All P0 requirements remain mandatory regardless of delivery order. P1 is explicitly excluded from V1 completion.

## Planning conventions

- **Epic** = customer/business outcome; **story** = independently verifiable thin slice; **task** = engineering work (implementation, design, verification). Issue IDs `BK-*` are new *planning IDs*, not substitutes for `FR-*`, `UC-*`, `BR-*`, or `AC-*`.
- Priority P0 means V1 release-blocking. Suggested size S/M/L describes relative implementation effort, **not hours**. The architecture is sized for a six-person team; individual owners, sprint dates and velocity are unassigned.
- A story is Done only with code, documentation/contract updates, relevant tests, review, CI success, deployable artifact, and evidence linked to its AC IDs; no financial feature is Done with unit tests alone.
- Backlog sequencing follows PRD section 11 slices; vertical delivery and technical spikes can run in parallel. A slice is not a requirement to merge unfinished work.

## Epic and story register

| Issue | Epic / story | P | Size | Requirement → acceptance trace | Dependencies |
|---|---|---|---|---|---|
| BK-001 | E0 — Verify course rubric and deployment requirements (EV-001) | P0 | S | NFR-005, EV-001 | None; gate for topology |
| BK-002 | E0 — Verify cloud/storage/TLS feasibility and budget (EV-002) | P0 | M | NFR-005, EV-002 | BK-001 |
| BK-003 | E0 — Review architecture decisions: boundaries, consistency/idempotency, pagination | P0 | M | NFR-001/002/003/006; FR-005/006/007 | BK-001 evidence before final approval |
| BK-004 | E0 — Repository baseline: CI, Docker, migrations, config, health checks | P0 | M | NFR-005/006 | BK-002 |
| BK-005 | E1 Identity — Customer registration with unique normalized email and atomic account provisioning | P0 | L | FR-001; UC-AUTH-01; AC-001/027/035/037 | BK-003/004 |
| BK-006 | E1 Identity — Protected Admin bootstrap (not self-registration) | P0 | M | FR-001/002; AC-024/027/030 | BK-004 |
| BK-007 | E1 Identity — Login, session lifecycle, logout and CSRF defenses | P0 | L | FR-002, NFR-003; UC-AUTH-02; AC-024/026/028 | BK-005/006 |
| BK-008 | E1 Account — Customer-owned overview and safe treasury exclusion | P0 | M | FR-003; UC-ACC-01; AC-002/022/024/026/035 | BK-005/007 |
| BK-009 | E1 Treasury — Controlled one-time GENESIS and restart safety | P0 | L | FR-010; BP-06; AC-020/021/022/023/032 | BK-003/004 |
| BK-010 | E2 Finance — Signed-entry ledger, atomic commit and reconciliation foundations | P0 | L | FR-005; AC-003/008/010/022/023/031 | BK-009 |
| BK-011 | E2 Finance — Durable dataset-lifetime idempotency and outcome recovery | P0 | L | FR-006; AC-005/006/011/013/014/015/021/033/034 | BK-010 |
| BK-012 | E2 Finance — Eligible synchronous Customer transfers incl. safe bounds and concurrency | P0 | L | FR-004/005/006; UC-TRF-01; AC-003/004/007/016/017/018/036 | BK-008/010/011 |
| BK-013 | E2 Admin — Treasury-to-Customer allocation with reason + atomic success audit | P0 | L | FR-011; UC-ADM-02; AC-006/008/010/011/013/015/018/019/032/033/034 | BK-006/009/010/011 |
| BK-014 | E3 History — Stable paginated Customer committed movements | P0 | L | FR-007; UC-ACC-02; AC-012/015/021/024/025/026 | BK-010/012/013 |
| BK-015 | E3 Receipts — Authorized immutable Customer receipts | P0 | M | FR-008; UC-TRF-02; AC-024/031 | BK-010/014 |
| BK-016 | E3 Admin — Minimum necessary Customer Account listing | P0 | M | FR-009; UC-ADM-01; AC-024/027/030/037 | BK-006/007/008 |
| BK-017 | E3 Admin — Freeze/unfreeze with reason, ordering, atomic audit, UNCHANGED | P0 | L | FR-012; UC-ADM-03; AC-009/018/019/026/029 | BK-010/012/013 |
| BK-018 | E3 Admin — Financial vs administrative evidence inspection | P0 | M | FR-013; UC-ADM-04; AC-020/022/023/024/025/030/031 | BK-009/010/013/017 |
| BK-019 | E3 Web — Customer screens, responsive/keyboard states and safe retry | P0 | L | UX-001/003/004/005; AC-005/011/013/014/015/033/038 | BK-007/008/012/014/015 |
| BK-020 | E3 Web — Admin screens and separation of permissioned operations | P0 | L | UX-002/003/005; AC-024/029/030/038 | BK-007/013/016/017/018 |
| BK-021 | E4 Testing — Canonical AC-001..AC-038 execution and CONS-01..19 validation | P0 | L | FR-001..013; UX-001..005; NFR-001..003/006 | All feature stories |
| BK-022 | E4 Testing — Failure injection, restart, concurrency, history, invariant evidence | P0 | L | NFR-001/002; AC-007/010/011/013/014/017..025/033/034 | BK-009..018 |
| BK-023 | E4 Performance — Execute prescribed 3,000-intent benchmark and EV-003 report | P0 | M | NFR-004; EV-003, AC-022/023 | BK-002/012/021 |
| BK-024 | E4 Cloud — Verified deploy/restart/TLS/secret/persistence/runbook and browser demo | P0 | L | NFR-005; UX-005; AC-021/028/038; EV-001/002 | BK-004, app ready |
| BK-025 | E4 Release — Traceability closure, evidence index, owner acceptance decision | P0 | M | All P0; AC-001..038; EV-001..003 | BK-021..024 |
| BK-P1-01 | Optional asynchronous completed-transfer in-app notifications | P1 | M | PRD §4.2; **not P0** | V1 acceptance |
| BK-P1-02 | Optional enhanced filtering/search/metrics | P1 | M | PRD §4.2; **not P0** | V1 acceptance |
| BK-P1-03 | Extended load/autoscaling, once externally justified and specified | P1* | L | PRD §4.2; EV-001 gate; separate acceptance | Verify course obligations |

`*` A verified course mandate can create a separately required milestone; it **must** be reconciled with scope by the owner, not silently claimed optional or imposed on the initial Core.

## Example issue template

```markdown
Title: [BK-012] Implement safe internal Customer transfers
Type: User Story | Priority: P0 | Owner: Unassigned | Status: Ready after design
Requirement: FR-004, FR-005, FR-006
Canonical use case: UC-TRF-01 | Process: BP-02
Rules: BR-TRF-01..12, BR-LED-04, BR-LED-06
Acceptance: AC-003/004/005/006/007/010/011/014/016/017/018/033/034/036
Depends on: BK-008, BK-010, BK-011
Tasks: finalize API contract; build atomic transfer workflow; implement errors;
       add authorization and concurrency tests; E2E Customer transfer demo.
Definition of Done: PR approved; mandatory tests green; invariant/replay evidence attached;
       docs/contracts updated; cloud-integrated test passes.
```

## Suggested iteration slicing (not staffing commitments)

1. **Design + runnable platform:** BK-001..004; no money movement yet.
2. **Identity/Account/GENESIS:** BK-005..009; prove restart doesn't reseed.
3. **Financial consistency vertical slice:** BK-010..013; prove both directions + failure/retry.
4. **UI/History/Admin completion:** BK-014..020; full Customer/Admin workflows.
5. **Acceptance/performance/release:** BK-021..025; attach *actual evidence*, never assumed passes.

## Parallel work coordination

- Product/experience: customer/admin flow specs, responsive UI, browser tests, acceptance evidence.
- Domain: Account/Transfer/Ledger rules, signed postings, concurrency/fault testing; financial PRs require domain peer review.
- Identity/admin: session design, authorization, controlled bootstrap, audits, freeze controls.
- Platform/quality: Docker/CI/cloud, test harness, benchmark, release evidence.
- Cross-team contracts are reviewed before implementation. Suggested ownership is **not** an assertion of actual team assignments.

## Technical review work within existing backlog items

These refinements do not add business capabilities, renumber acceptance criteria or mark implementation stories Done. The detailed design remains subject to [25 decision review](../6-governance/25-design-decision-register.md).

| Existing items | Required technical work before/alongside implementation | Source |
|---|---|---|
| BK-003/004 | Review agreed modular/hexagonal structure and proposed API allowlist. Backend build, PostgreSQL/Flyway and documentation Swagger UI/CI exist; branch-local shared contracts and ArchUnit candidate checks/negative fixtures are implemented. Business-module integration, posting caller rules and deployment CI remain future work; policy approval is still pending. | [15 §3](../03-design/15-system-architecture.md#3-module-ownership-and-the-controlled-posting-path), [module guide](../03-design/module-contracts-and-boundaries.md), [README workflow](../README.md#browse-the-api-locally-doc-01) |
| BK-010/012/013 | Implement one Ledger-owned posting path with Accounts-owned state; outer use-case transaction includes both balances, facts, final outcome and required allocation audit. | [16 §5](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02), TD-015 |
| BK-011/022 | Run small real-PostgreSQL idempotency PoC before adopting advisory coordination; test canonicalization, concurrency, missing outcome and crash recovery. | [22 PoC](../05-verification/22-test-strategy-and-acceptance.md#postgresql-idempotency-proof-of-concept-arc-04), TD-003 |
| BK-012/013/017/022 | Verify shared lock order for transfers, Treasury allocations and freeze; audit failure rollback, opposite-direction transfers and bounded same-key technical retries. | [16 §6](../03-design/16-domain-model-and-data-design.md#6-locking-and-isolation-discipline-arc-03), TD-002 |
| BK-014/021/023 | Validate late-commit pagination and benchmark the global counter separately from Account/Treasury contention; preserve PRD reference workload. | [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05), TD-004/014 |
| BK-021/022 | Maintain candidate module-boundary fixture checks; add real business-module integration and proxied transaction/fault tests as modules are implemented. Record canonical AC/CONS runtime evidence separately from structural checks. | [22 strategy](../05-verification/22-test-strategy-and-acceptance.md), TD-016 |
