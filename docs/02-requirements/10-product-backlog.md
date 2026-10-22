# 10 — FinFast V1 Delivery Backlog

> Status: Engineering decomposition with the explicitly authorized **2026-10-22 six-person assignment and scoped design baseline** below; sprint dates and completed delivery are not asserted. The later Product/UX/REST freeze approves existing PRD, derived behavior, screens/E2E and YAML semantics for implementation; runtime evidence remains separate. No story becomes Done through this documentation freeze. Derived from [PRD 09](09-product-requirements.md), canonical [use cases 04](../01-discovery/04-use-cases.md), and [business rules 06](../01-discovery/06-business-rules.md). All P0 requirements remain mandatory regardless of delivery order. P1 is explicitly excluded from V1 completion.

## Planning conventions

- **Epic** = customer/business outcome; **story** = independently verifiable thin slice; **task** = engineering work (implementation, design, verification). Issue IDs `BK-*` are new *planning IDs*, not substitutes for `FR-*`, `UC-*`, `BR-*`, or `AC-*`.
- Priority P0 means V1 release-blocking. Suggested size S/M/L describes relative implementation effort, **not hours**. The architecture is sized for a six-person team; sprint dates/velocity are not asserted here. The final engineering-freeze instruction supplies the six-person task assignment below; module responsibilities remain those in the [canonical module guide](../03-design/module-contracts-and-boundaries.md#ownership-and-approved-dependencies).
- A story is Done only with code, documentation/contract updates, relevant tests, review, CI success, deployable artifact, and evidence linked to its AC IDs; no financial feature is Done with unit tests alone.
- Backlog sequencing follows PRD section 11 slices; vertical delivery and technical spikes can run in parallel. A slice is not a requirement to merge unfinished work.

## Epic and story register

| Issue | Epic / story | P | Size | Requirement → acceptance trace | Dependencies |
|---|---|---|---|---|---|
| BK-001 | E0 — Verify course rubric and deployment requirements (EV-001) | P0 | S | NFR-005, EV-001 | None; gate for topology |
| BK-002 | E0 — Verify cloud/storage/TLS feasibility and budget (EV-002) | P0 | M | NFR-005, EV-002 | BK-001 |
| BK-003 | E0 — Review architecture decisions: boundaries, consistency/idempotency, pagination | P0 | M | NFR-001/002/003/006; FR-005/006/007 | Java baseline/TD-015/016 merged at 8a04ca0; later TD-001..014 selections recorded; BK-001/002 still gate actual deployment resources |
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

## BK-003 implementation and approval status

Tiến approved the Java contracts and TD-015/016 on 2026-10-22. PR #10 is **MERGED** into `develop` at `8a04ca03505a140930b9bbc976dcdc1148db2693`; the shared Java baseline is active. The later [V1 engineering freeze](../6-governance/25-design-decision-register.md#v1-engineering-design-freeze--2026-10-22) selects TD-001..014 with scoped design/method/topology approval. The subsequent [Product/UX/REST record](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) approves the existing implementation-facing PRD/functional/UX/external API baselines; it changes no task assignment, module ownership or evidence gate. Owner endorsements, provider implementations and financial acceptance do not follow from that approval. Unpushed teammate work remains unknown.

Additional public capabilities/edges or breaking changes require Tiến's prior amendment; independent provider code can proceed against approved contracts with test-scoped fakes. BK-010/011/012/013 are not complete because source contracts compile or architecture checks pass.

## Member implementation readiness — 2026-10-22

These task assignments come from the explicit final-freeze instruction. They do not transfer the BK-003 module ownership or grant extra API capabilities. In particular, Hải coordinates BK-017 delivery while Tiến retains Administration orchestration/audit ownership; Trung's BK-016/018 work remains in the relevant approved modules. Sĩ consumes REST only, not Java capabilities.

| Task owner / assigned items | Approved baseline / APIs to consume | Dependencies and safe independent implementation | Integration/test responsibility and outstanding evidence |
|---|---|---|---|
| **Tiến — BK-003/010/013/025** | TD-001..005/008..011/014..016; Ledger consumes `PostingAccounts`; Administration uses `AllocationPosting`, `RequestOutcomes`, `CurrentIdentity`, `AccountQueries` and `AccountStatusCommands` | Ledger/Administration remain Tiến-owned. Preserve Phase 1; later authorized persistence/domain work can use test fakes while Hải/Minh providers are absent. Allocation needs real Treasury/GENESIS, Accounts and Idempotency; no Phase 2 is started by this freeze | Ledger cardinality/signed facts/reconciliation, allocation audit rollback, cross-module capability review, final traceability/release evidence; real providers/commit/AC-003/008/010/019/022/023/031 still pending |
| **Minh — BK-011/012/022/023** | TD-001..005/008/013..016; Transfers consumes `CurrentIdentity`, `AccountQueries`, `FinancialPosting`, `RequestOutcomes` | Canonical v1 vectors and Idempotency PostgreSQL adapter/proof fixture can proceed; transfer orchestration tests use scoped fakes. Real transfer needs Hưng auth, Hải Accounts and Tiến Ledger; capacity run needs deployed resources | Same-key/conflict/raw-invalid replay, fresh transaction retry, ambiguous commit, restart/concurrency/fault harness and exact PRD §9 benchmark; no benchmark result yet |
| **Hưng — BK-001/004/005/007** | TD-001/006..009/012/015/016; Identity consumes `CustomerProvisioning`; Customer consumes `AccountProvisioning`; provides `CurrentIdentity`/`AdminProvisioning` | Registration/session/BCrypt implementation and pure tests within Identity/Customer; framework/config additions only in separately authorized implementation scope. Onboarding integration needs Hải provisioning; EV-001 needs real rubric | Atomic registration/duplicate email, password UTF-8 72-byte boundary/cost, CSRF/session rotation/logout/role tests and CI/runtime baseline evidence; no security pass implied |
| **Hải — BK-008/014/015/017** | TD-001/002/004/005/008/011/015/016; Accounts provides approved queries/provisioning/posting/status; History consumes `CurrentIdentity`, `AccountQueries`, `LedgerQueries` | Accounts locking/current-state invariants and History consumer tests can proceed. BK-017 Accounts status provider is Hải-owned; Administration outer transaction/audit stays Tiến-owned. History participant-label provider integration waits for narrow metadata capability amendment | Balance/status/provider races with Tiến/Minh; HMAC snapshot traversal/receipt authorization with Tiến; freeze-first/movement-first/audit failure with Administration. Real PostgreSQL and read-projection gaps remain |
| **Trung — BK-006/009/016/018/024** | TD-002/008..012/015/016; Bootstrap consumes `AdminProvisioning`, `TreasuryProvisioning`, `GenesisPosting`; approved Administration readers may consume `CurrentIdentity`, `AccountQueries` | Protected operator setup command/orchestration tests can use provider fakes; real initialization needs Hưng/Hải/Tiến. No direct `PostingAccounts`. BK-016/018 production read integration requires reviewed projections; no use of History-only LedgerQueries. Deployment runbook/reference topology can proceed, provider gated | Atomic setup/uniqueness race/restart/no reseed with owners; Admin read authorization; TLS/private persistent DB/secrets/backup/recovery evidence, EV-001/002 before actual deployment |
| **Sĩ — BK-002/019/020/021** | TD-001/003/004/006/008/012/013; existing OpenAPI REST contract only | Cloud feasibility and browser state/decimal-string/original-key recovery work can proceed with test-scoped HTTP fixtures; verify actual frontend stack before dependencies. End-to-end execution needs implemented/authenticated APIs and selected resources | EV-002 evidence, safe cookie/CSRF browser wiring, Customer/Admin permission/error/uncertainty E2E and canonical AC/CONS evidence coordination; existing flows/viewports are approved test specifications, not passing browser tests; styling remains Sĩ's choice within the baseline |

Once the consolidated baseline merges into `develop`, all six members may implement approved product/UX/Java/REST behavior and selected mechanisms without routine reapproval, within each assigned/authorized task phase. API-ERR-01 affects only adapter/contract conformance for undocumented foundation error statuses; preserve the YAML and obtain a narrow amendment rather than silently adding them. A failing code test normally requires a code fix. Escalate actual incompatible APIs/security/financial correctness defects for a scoped amendment and stop only the affected path; preserve work and never add duplicate contracts or no-op production providers.

## BK-010 continuation — preserve phase boundaries

Phase 1 is visible separately at `44dd3ed34c262c4de23a670af43fa0727054f9a0` (`feat/BK-010-ledger-core`), not in the audited develop baseline. Its immutable pure model does not perform persistence, lock Accounts, allocate a sequence or prove a database commit. Existing phase evidence is not rerun or expanded by this documentation work.

**FIX-001 — Transaction Reference NUL Validation remains open:** `ledger/internal/domain/FinancialTransaction.java` permits U+0000; PostgreSQL text/varchar cannot persist it. A later authorized implementation must reject `\u0000`, add `rejectsNulInReference()` in `ledger/internal/domain/LedgerTransactionTests.java`, and rerun the focused pure and architecture tests. Do not change Java during this freeze.

| Phase | Precise continuation / gate | Verification obligation |
|---|---|---|
| 1 — Immutable Ledger Domain Model | Preserve existing implementation; review/fix FIX-001 in its own authorized change | Pure invariants/immutability/MAX_VALUE plus NUL regression and existing architecture checks |
| 2 — Ledger Persistence and Posting Position | **NOT STARTED**. Requires explicit phase authorization, unchanged V1/V2 mapping, Phase 1 review/FIX-001 closure and TD-002/004 existing-transaction/counter protocol. Keep work in Ledger internals; no Accounts provider substitutes or new migrations | Isolated real PostgreSQL round-trip, bounds/FKs/immutability, counter serialization/rollback and proxy guard tests; test fixtures only, no banking service yet |
| 3 — GENESIS Contribution | Existing GenesisPosting only; coordinate real Treasury provider and Bootstrap outer setup | One positive Treasury entry and consistent dataset control; full rollback/concurrent setup/restart, no reseed |
| 4 — Ordinary Posting | Existing FinancialPosting/AllocationPosting only; real Accounts locking/balance invariants plus caller-owned transactions and Idempotency integration | Exactly two opposite entries, no writes on business rejection, partial SQL rollback and bounded same-key retries |
| 5 — Reconciliation Foundations | Consistent snapshot assertions, GENESIS exactly once, safe aggregation; no reporting/repair expansion | Every Account equals signed sum; full supply preserved; missing/duplicate/inconsistent entry detection after faults/concurrency |
| 6 — Integration and Acceptance | Real Transfers/Idempotency/Administration/Bootstrap/History providers and reviewed read capabilities | Canonical AC/CONS, audit/commit response loss, restart, security, concurrency and benchmark evidence with owners |

The freeze resolves design ambiguity, not unfinished implementation. Do not skip phases or automatically implement BK-009/011/012/013.

## Example issue template

```markdown
Title: [BK-012] Implement safe internal Customer transfers
Type: User Story | Priority: P0 | Owner: Minh | Status: Ready for authorized implementation scope
Requirement: FR-004, FR-005, FR-006
Canonical use case: UC-TRF-01 | Process: BP-02
Rules: BR-TRF-01..12, BR-LED-04, BR-LED-06
Acceptance: AC-003/004/005/006/007/010/011/014/016/017/018/033/034/036
Depends on: BK-008, BK-010, BK-011
Tasks: preserve approved API contract; build atomic transfer workflow; implement errors;
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
- Cross-team contracts are reviewed before implementation. Use the explicitly supplied assignment above; preserve documented module ownership and capability permissions.

## Technical review work within existing backlog items

These refinements do not add business capabilities, renumber acceptance criteria or mark implementation stories Done. The selected design and scoped evidence obligations are in [25 decision register](../6-governance/25-design-decision-register.md).

| Existing items | Required technical work before/alongside implementation | Source |
|---|---|---|
| BK-003/004 | Backend build, PostgreSQL/Flyway, Swagger UI/CI, shared contracts and ArchUnit checks exist. BK-003 Java APIs/DTOs, ownership/boundary policy and capability checks are explicitly approved; merged into `develop` at `8a04ca0`. Real providers, financial integration and deployment automation remain future work. Contract compilation does not mark implementation stories Done. | [15 §3](../03-design/15-system-architecture.md#3-module-ownership-and-the-controlled-posting-path), [module guide](../03-design/module-contracts-and-boundaries.md), [README workflow](../README.md#browse-the-api-locally-doc-01) |
| BK-010/012/013 | Implement one Ledger-owned posting path with Accounts-owned state; outer use-case transaction includes both balances, facts, final outcome and required allocation audit. | [16 §5](../03-design/16-domain-model-and-data-design.md#5-transaction-boundaries-and-outcomes-arc-02), TD-015 |
| BK-011/022 | Run the small real-PostgreSQL proof suite for the selected advisory coordination before financial integration acceptance; test canonicalization, concurrency, missing outcome and crash recovery. | [22 PoC](../05-verification/22-test-strategy-and-acceptance.md#postgresql-idempotency-proof-of-concept-arc-04), TD-003 |
| BK-012/013/017/022 | Verify shared lock order for transfers, Treasury allocations and freeze; audit failure rollback, opposite-direction transfers and bounded same-key technical retries. | [16 §6](../03-design/16-domain-model-and-data-design.md#6-locking-and-isolation-discipline-arc-03), TD-002 |
| BK-014/021/023 | Validate late-commit pagination and benchmark the global counter separately from Account/Treasury contention; preserve PRD reference workload. | [16 §8](../03-design/16-domain-model-and-data-design.md#8-history-ordering-and-global-sequence-risk-arc-05), TD-004/014 |
| BK-021/022 | Maintain approved module-boundary/capability fixture checks; add real business-module integration and proxied transaction/fault tests as modules are implemented. Record canonical AC/CONS runtime evidence separately from structural checks. | [22 strategy](../05-verification/22-test-strategy-and-acceptance.md), TD-016 |
