# 23 — UAT, Release Readiness and Project-Owner Signoff

> The [engineering freeze](../6-governance/25-design-decision-register.md#v1-engineering-design-freeze--2026-10-22) and subsequent [Product/UX/REST approval](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) approve implementation/test baselines, method and reference topology; they do not check any box below. Verify merged baseline, actual providers and all runtime/security/concurrency evidence. EV-001/002 provider/resources and EV-003 results remain outstanding.

> Status: **UNEXECUTED checklist** derived from [PRD §10–§12](../02-requirements/09-product-requirements.md). Completing this document requires linked runtime evidence; no boxes imply completed work today. Release acceptance authority belongs to the project owner, not the author of these outputs.

## A. Product requirements and coverage

- [ ] Implementation conforms to Tiến's approved PRD/functional/UX/Java/REST and engineering baseline (including scoped exceptions) and reviewed amendments. This compliance needs evidence; the separate project-owner final release decision remains below.
- [ ] FR-001..FR-013 P0 implemented with accepted functional evidence.
- [ ] UX-001..UX-005 P0 tested on Customer and Admin responsive Web journeys (AC-038).
- [ ] NFR-001..NFR-006 P0 verified with evidence or explicit disposition of any target deviation.
- [ ] Every canonical AC-001..AC-038 and applicable variants has an executed result and link; CONS-01..CONS-19 have supporting AC links, not duplicate tests.
- [ ] All 38 mandatory BR requirements remain traceable through FR/AC to implemented code, tests and reviewed PRs.
- [ ] No P1 feature or excluded banking function is misrepresented as required for V1.

## B. Financial correctness and adverse conditions

- [ ] One signed-entry ledger, exactly two entries per ordinary movement; GENESIS has exactly one positive treasury entry.
- [ ] Every balance reconciles to committed signed deltas, and all balances sum to original GENESIS supply.
- [ ] No negative Customer or Treasury balance under competing debits, funding, freeze races or benchmark workload.
- [ ] Transfer/allocations with same scope+payload replay original committed **and final business-rejected** outcomes, including across restart and changed Account state.
- [ ] Same scope + changed payload conflicts; distinct scopes do not interfere.
- [ ] Successful funding and status change have complete required success audit **in the same commit**.
- [ ] Failed ledger/audit/outcome persistence leaves no partial financial or status change.
- [ ] Timeout/lost response resolved using original key *without* Transaction Reference; no second movement.
- [ ] Repeated/concurrent/interrupted GENESIS never adds money or changes an existing dataset.
- [ ] Customer history excludes GENESIS and rejected/technical attempts; deterministic 20/default, ≤100/max high-water/keyset pagination with HMAC-SHA256 cursor survives late/new commits, tampering and restart.

## C. Security and Web experience

- [ ] Register only display name/email/password, normalized email uniqueness, BCrypt strength 12 default benchmarked, UTF-8 72-byte bound handled without truncation; separate protected operator-driven ADMIN/Treasury/GENESIS bootstrap.
- [ ] Expired/logged-out server session denied on reuse; CSRF, HTTPS and HttpOnly/Secure cookies verified.
- [ ] Unauthenticated, wrong-role, wrong-owner, treasury and receipt enumeration attempts denied without private disclosure; no balance overwrite/reset/mint routes.
- [ ] FROZEN owner retains login/reads but new send/receive/allocation rejected; after unfreeze only **new eligible intents** can proceed.
- [ ] Responsive 360×800, 768×1024, 1440×900 CSS pixel checks, 200% desktop zoom, keyboard focus and documented browsers (approved V1 test specification; execution evidence required).
- [ ] UI distinguishes `COMPLETED`, `REJECTED`, key `CONFLICT`, `PROCESSING`, `UNKNOWN`, `UNCHANGED` without false success.

## D. Cloud delivery & benchmark evidence

- [ ] EV-001 actual course rubric verified and archived; conflicts resolved by explicit owner decision.
- [ ] EV-002 provider/budget/resource/storage/HTTPS feasibility verified and archived.
- [ ] Docker/container artifact built with revision/digest; CI reproducible; deployed app and PostgreSQL persistent.
- [ ] TLS and secret configuration verified; database not publicly writable; migrations/setup documented.
- [ ] Deployment/restart walkthrough preserves GENESIS, ledger, audit, outcomes, sessions handled safely.
- [ ] EV-003 benchmark executed with [approved EV-003 offered-rate schedule](../verification/strategy.md#nfr-004--ev-003-benchmark-protocol); report includes complete outcome distribution, original attempts and recoveries, latency, environment and reconciliation.
- [ ] Provisional p95 ≤2 s/10 rps/5 min outcome recorded **honestly**, not assumed; deviations escalated with owner disposition.
- [ ] If actual rubric demands autoscaling/extended testing, separate evidence is provided or the gap explicitly owned, not silently discarded.

## E. Release record (to fill after tests)

```text
Release name: FinFast V1 Core Banking Foundation
PRD baseline version: __________
Git commit / tag / Docker image digest: __________
Cloud provider / region / resource sizes: __________
Database version / persistent storage: __________
Executed AC-001..AC-038 matrix link: __________
CONS crosswalk & reconciliation reports: __________
EV-001/002 evidence: __________
EV-003 benchmark report and deviations: __________
Known defects, mitigations, exclusions: __________
Product owner decision: APPROVE / REJECT / CONDITIONAL
Decision authority/date/evidence: __________
```

Unfilled signoff fields indicate **not yet accepted**. Engineering output generation is not application implementation or runtime verification.
