# 11 — Requirement-to-Delivery Traceability Matrix

## Engineering authority and evidence update — 2026-10-22

[TD-001..016](../6-governance/25-design-decision-register.md#engineering-decisions-and-scoped-statuses) and the [assigned readiness matrix](10-product-backlog.md#member-implementation-readiness--2026-10-22) now guide implementation. The [Product/UX/REST approval](../6-governance/25-design-decision-register.md#2026-10-22--v1-product-ux-and-rest-contract-freeze) also approves PRD 09, derived specification 12, UX 14 and the existing external API. The tables below retain canonical mappings and NOT RUN evidence; approved specification is not executed acceptance. TD-001/002/005/010/015 support FR-005 (AC-003/008/010/022/023/031), TD-003 supports FR-006, TD-004/014 support FR-007/008, TD-006/007 support FR-001/002 and NFR-003, TD-009 supports FR-010, TD-011 supports FR-011/012, and TD-012/013 retain EV-001..003 evidence gates. BK-003 is merged; BK-010 Phase 1 is a separate branch with FIX-001 outstanding. Provider integration/read-capability exceptions remain explicit in the decision register; no row is marked passed by this synchronization.

> **Generated mapping** from [PRD §10](09-product-requirements.md), [04 canonical AC](../01-discovery/04-use-cases.md), [06 canonical BR](../01-discovery/06-business-rules.md), and [backlog 10](10-product-backlog.md). This is **design-time coverage**, not evidence of executed tests. FR/UC/AC/BR/CONS original identifiers remain unchanged.

## Functional requirement to use case/acceptance/issue

| Requirement | Canonical use cases / process | Canonical AC IDs | Suggested backlog issues |
|---|---|---|---|
| FR-001 | UC-AUTH-01; BP-01 | AC-001, AC-027, AC-035, AC-037 | BK-003, BK-005, BK-006, BK-021, BK-022 |
| FR-002 | UC-AUTH-02; every protected use case | AC-024, AC-026, AC-027, AC-028 | BK-007, BK-021 |
| FR-003 | UC-ACC-01 | AC-002, AC-022, AC-024, AC-026, AC-035 | BK-007, BK-008, BK-021 |
| FR-004 | UC-TRF-01; BP-02 | AC-003, AC-004, AC-016, AC-018, AC-024, AC-026, AC-036, AC-037 | BK-012, BK-021, BK-023 |
| FR-005 | UC-TRF-01, UC-ADM-02, UC-ADM-04; BP-02/BP-03 | AC-003, AC-007, AC-008, AC-010, AC-017, AC-018, AC-019, AC-022, AC-023, AC-031 | BK-001, BK-002, BK-003, BK-004, BK-010, BK-021, BK-024 |
| FR-006 | UC-TRF-01, UC-ADM-02; BP-02/BP-03 | AC-005, AC-006, AC-011, AC-013, AC-014, AC-015, AC-021, AC-033, AC-034 | BK-011, BK-021 |
| FR-007 | UC-ACC-02; BP-05 | AC-012, AC-015, AC-021, AC-024, AC-025, AC-026 | BK-014, BK-021 |
| FR-008 | UC-TRF-02; BP-05 | AC-024, AC-031 | BK-015, BK-021 |
| FR-009 | UC-ADM-01 | AC-024, AC-027, AC-030, AC-037 | BK-016, BK-021 |
| FR-010 | UC-ADM-02 prerequisite; UC-ADM-04; BP-06 | AC-020, AC-021, AC-022, AC-023, AC-032 | BK-009, BK-021 |
| FR-011 | UC-ADM-02, UC-ADM-04; BP-03 | AC-006, AC-008, AC-010, AC-011, AC-013, AC-015, AC-016, AC-018, AC-019, AC-026, AC-032, AC-033, AC-034 | BK-013, BK-021 |
| FR-012 | UC-ADM-03, UC-ADM-04; BP-04 | AC-009, AC-018, AC-019, AC-026, AC-029 | BK-017, BK-021 |
| FR-013 | UC-ADM-04; BP-05/BP-06 | AC-020, AC-022, AC-023, AC-024, AC-025, AC-030, AC-031 | BK-018, BK-021 |

## Experience/NFR requirements

| ID | Verification and implementation outputs |
|---|---|
| UX-001 | S01–S06 Customer screens, WEB-01..08, AC-038; BK-019 |
| UX-002 | S07–S10 Admin screens, WEB-02/05/06/08, AC-038; BK-020 |
| UX-003 | Validation/error/status/accessibility states, WEB-04/05/08, AC-038; BK-019/020 |
| UX-004 | UI same-key persistence and safe uncertainty resolution, AC-005/011/013/014/015/033; BK-019 |
| UX-005 | 360×800, 768×1024, 1440×900, 200% zoom, keyboard, AC-038; BK-019/020/024 |
| NFR-001 | CONS-01..19 + AC/reconciliation; BK-010..013/021..023 |
| NFR-002 | Persistent financial records and final outcomes, restart/fault tests; BK-009..018/022 |
| NFR-003 | Adaptive hash, HTTPS/session/CSRF, role/resource negative tests; BK-005..007/021 |
| NFR-004 | PRD §9 controlled 3,000 intents, EV-003 report, no invented pass; BK-023 |
| NFR-005 | CI, container, PostgreSQL persistence, TLS and deployment evidence; EV-001/002; BK-001/002/004/024 |
| NFR-006 | Modular boundaries, reviewed PRs, trace/mapping and AC-038; BK-003/021/025 |

## Canonical business-rule coverage (all P0)

| Business rule | PRD requirements | Acceptance IDs (canonical BR document) |
|---|---|---|
| BR-ACC-01 | FR-001 | AC-001, AC-027 |
| BR-ACC-02 | FR-003, FR-004, FR-011, FR-012 | AC-009, AC-018, AC-026 |
| BR-ACC-03 | FR-004, FR-005, FR-011, NFR-001 | AC-004, AC-007, AC-032 |
| BR-ACC-04 | FR-003, FR-004, FR-005, FR-010, FR-011 | AC-003, AC-016 |
| BR-ACC-05 | FR-001, FR-003, FR-004, FR-010, FR-011 | AC-001, AC-020, AC-024 |
| BR-ACC-06 | FR-001, FR-003, FR-010 | AC-035 |
| BR-ADM-01 | FR-011 | AC-008, AC-026, AC-032 |
| BR-ADM-02 | FR-010, FR-011 | AC-020, AC-021, AC-032 |
| BR-ADM-03 | FR-011, FR-012, FR-013, NFR-002 | AC-008, AC-019, AC-029, AC-030 |
| BR-ADM-04 | FR-005, FR-006, FR-011 | AC-006, AC-013, AC-015, AC-019, AC-034 |
| BR-ADM-05 | FR-002, FR-003, FR-010, FR-011, FR-012 | AC-024, AC-032 |
| BR-ADM-06 | FR-009, FR-013, NFR-003 | AC-024, AC-030 |
| BR-HIS-01 | FR-007, FR-008 | AC-012, AC-031 |
| BR-HIS-02 | FR-002, FR-003, FR-007, FR-008, FR-013 | AC-002, AC-024 |
| BR-HIS-03 | FR-007, NFR-002 | AC-021, AC-025 |
| BR-HIS-04 | FR-006, FR-007, FR-013 | AC-015, AC-025, AC-030 |
| BR-ID-01 | FR-001, FR-002, NFR-003 | AC-027, AC-028 |
| BR-ID-02 | FR-001, FR-002, FR-009, NFR-003 | AC-024, AC-027, AC-028 |
| BR-LED-01 | FR-005, FR-008, FR-013 | AC-031 |
| BR-LED-02 | FR-003, FR-005, FR-010, FR-013 | AC-022 |
| BR-LED-03 | FR-005, FR-008, FR-010, FR-013 | AC-020, AC-031 |
| BR-LED-04 | FR-005, FR-011 | AC-003, AC-008, AC-010 |
| BR-LED-05 | FR-010, FR-013, NFR-001 | AC-020, AC-021, AC-023 |
| BR-LED-06 | FR-004, FR-005, FR-006, FR-007, FR-011 | AC-004, AC-015, AC-032 |
| BR-SIM-01 | FR-004, FR-010, FR-011, UX-001 | AC-037 |
| BR-SIM-02 | FR-001, FR-009, UX-001 | AC-027, AC-037 |
| BR-TRF-01 | FR-004 | AC-016 |
| BR-TRF-02 | FR-002, FR-004 | AC-024 |
| BR-TRF-03 | FR-004, FR-012 | AC-018, AC-026, AC-036 |
| BR-TRF-04 | FR-005, FR-011 | AC-010, AC-019, AC-033 |
| BR-TRF-05 | FR-006, FR-011, NFR-002, UX-004 | AC-005, AC-006, AC-015, AC-033, AC-034 |
| BR-TRF-06 | FR-004, FR-005, FR-011, FR-012 | AC-007, AC-017, AC-018 |
| BR-TRF-07 | FR-005 | AC-003 |
| BR-TRF-08 | FR-004, FR-005, FR-006, UX-004 | AC-010, AC-011 |
| BR-TRF-09 | FR-004, FR-005 | AC-004, AC-015, AC-031 |
| BR-TRF-10 | FR-006, FR-011 | AC-013, AC-014 |
| BR-TRF-11 | FR-006, NFR-001, UX-004 | AC-011, AC-017, AC-033 |
| BR-TRF-12 | FR-004, FR-005, FR-010, FR-011 | AC-016 |

## Canonical acceptance-by-PRD reverse index

| AC ID | Use case (source 04) | FR IDs from PRD §10.1 | BR IDs from canonical rule coverage | Evidence / status |
|---|---|---|---|---|
| AC-001 | UC-AUTH-01 | FR-001 | BR-ACC-01, BR-ACC-05 | **NOT RUN** — add CI/report URL |
| AC-002 | UC-ACC-01 | FR-003 | BR-HIS-02 | **NOT RUN** — add CI/report URL |
| AC-003 | UC-TRF-01 | FR-004, FR-005 | BR-ACC-04, BR-LED-04, BR-TRF-07 | **NOT RUN** — add CI/report URL |
| AC-004 | UC-TRF-01 | FR-004 | BR-ACC-03, BR-LED-06, BR-TRF-09 | **NOT RUN** — add CI/report URL |
| AC-005 | UC-TRF-01 | FR-006 | BR-TRF-05 | **NOT RUN** — add CI/report URL |
| AC-006 | UC-TRF-01, UC-ADM-02 | FR-006, FR-011 | BR-ADM-04, BR-TRF-05 | **NOT RUN** — add CI/report URL |
| AC-007 | UC-TRF-01 | FR-005 | BR-ACC-03, BR-TRF-06 | **NOT RUN** — add CI/report URL |
| AC-008 | UC-ADM-02, UC-ADM-04 | FR-005, FR-011 | BR-ADM-01, BR-ADM-03, BR-LED-04 | **NOT RUN** — add CI/report URL |
| AC-009 | UC-ADM-03 | FR-012 | BR-ACC-02 | **NOT RUN** — add CI/report URL |
| AC-010 | UC-TRF-01, UC-ADM-02 | FR-005, FR-011 | BR-LED-04, BR-TRF-04, BR-TRF-08 | **NOT RUN** — add CI/report URL |
| AC-011 | UC-TRF-01, UC-ADM-02 | FR-006, FR-011 | BR-TRF-08, BR-TRF-11 | **NOT RUN** — add CI/report URL |
| AC-012 | UC-ACC-02 | FR-007 | BR-HIS-01 | **NOT RUN** — add CI/report URL |
| AC-013 | UC-ADM-02 | FR-006, FR-011 | BR-ADM-04, BR-TRF-10 | **NOT RUN** — add CI/report URL |
| AC-014 | UC-TRF-01 | FR-006 | BR-TRF-10 | **NOT RUN** — add CI/report URL |
| AC-015 | UC-TRF-01, UC-ADM-02, UC-ACC-02 | FR-006, FR-007, FR-011 | BR-ADM-04, BR-HIS-04, BR-LED-06, BR-TRF-05, BR-TRF-09 | **NOT RUN** — add CI/report URL |
| AC-016 | UC-TRF-01, UC-ADM-02 | FR-004, FR-011 | BR-ACC-04, BR-TRF-01, BR-TRF-12 | **NOT RUN** — add CI/report URL |
| AC-017 | UC-TRF-01 | FR-005 | BR-TRF-06, BR-TRF-11 | **NOT RUN** — add CI/report URL |
| AC-018 | UC-TRF-01, UC-ADM-02, UC-ADM-03 | FR-004, FR-005, FR-011, FR-012 | BR-ACC-02, BR-TRF-03, BR-TRF-06 | **NOT RUN** — add CI/report URL |
| AC-019 | UC-ADM-02, UC-ADM-03 | FR-005, FR-011, FR-012 | BR-ADM-03, BR-ADM-04, BR-TRF-04 | **NOT RUN** — add CI/report URL |
| AC-020 | UC-ADM-02, UC-ADM-04 | FR-010, FR-013 | BR-ACC-05, BR-ADM-02, BR-LED-03, BR-LED-05 | **NOT RUN** — add CI/report URL |
| AC-021 | UC-ADM-02, UC-ADM-04, UC-ACC-02 | FR-006, FR-007, FR-010 | BR-ADM-02, BR-HIS-03, BR-LED-05 | **NOT RUN** — add CI/report URL |
| AC-022 | UC-ACC-01, UC-ADM-04 | FR-003, FR-005, FR-010, FR-013 | BR-LED-02 | **NOT RUN** — add CI/report URL |
| AC-023 | UC-ADM-02, UC-ADM-04 | FR-005, FR-010, FR-013 | BR-LED-05 | **NOT RUN** — add CI/report URL |
| AC-024 | UC-ACC-01, UC-ACC-02, UC-TRF-01, UC-TRF-02, UC-ADM-01, UC-ADM-02, UC-ADM-03, UC-ADM-04 | FR-002, FR-003, FR-004, FR-007, FR-008, FR-009, FR-013 | BR-ACC-05, BR-ADM-05, BR-ADM-06, BR-HIS-02, BR-ID-02, BR-TRF-02 | **NOT RUN** — add CI/report URL |
| AC-025 | UC-ACC-02, UC-ADM-04 | FR-007, FR-013 | BR-HIS-03, BR-HIS-04 | **NOT RUN** — add CI/report URL |
| AC-026 | UC-AUTH-02, UC-ACC-01, UC-ACC-02, UC-TRF-01, UC-ADM-02 | FR-002, FR-003, FR-004, FR-007, FR-011, FR-012 | BR-ACC-02, BR-ADM-01, BR-TRF-03 | **NOT RUN** — add CI/report URL |
| AC-027 | UC-AUTH-01, UC-ADM-01 | FR-001, FR-002, FR-009 | BR-ACC-01, BR-ID-01, BR-ID-02, BR-SIM-02 | **NOT RUN** — add CI/report URL |
| AC-028 | UC-AUTH-02 | FR-002 | BR-ID-01, BR-ID-02 | **NOT RUN** — add CI/report URL |
| AC-029 | UC-ADM-03, UC-ADM-04 | FR-012 | BR-ADM-03 | **NOT RUN** — add CI/report URL |
| AC-030 | UC-ADM-01, UC-ADM-04 | FR-009, FR-013 | BR-ADM-03, BR-ADM-06, BR-HIS-04 | **NOT RUN** — add CI/report URL |
| AC-031 | UC-TRF-01, UC-TRF-02, UC-ADM-04 | FR-005, FR-008, FR-013 | BR-HIS-01, BR-LED-01, BR-LED-03, BR-TRF-09 | **NOT RUN** — add CI/report URL |
| AC-032 | UC-ADM-02 | FR-010, FR-011 | BR-ACC-03, BR-ADM-01, BR-ADM-02, BR-ADM-05, BR-LED-06 | **NOT RUN** — add CI/report URL |
| AC-033 | UC-TRF-01, UC-ADM-02 | FR-006, FR-011 | BR-TRF-04, BR-TRF-05, BR-TRF-11 | **NOT RUN** — add CI/report URL |
| AC-034 | UC-TRF-01, UC-ADM-02 | FR-006, FR-011 | BR-ADM-04, BR-TRF-05 | **NOT RUN** — add CI/report URL |
| AC-035 | UC-AUTH-01, UC-ACC-01 | FR-001, FR-003 | BR-ACC-06 | **NOT RUN** — add CI/report URL |
| AC-036 | UC-TRF-01 | FR-004 | BR-TRF-03 | **NOT RUN** — add CI/report URL |
| AC-037 | UC-AUTH-01, UC-TRF-01, UC-ADM-01, UC-ADM-02 | FR-001, FR-004, FR-009 | BR-SIM-01, BR-SIM-02 | **NOT RUN** — add CI/report URL |
| AC-038 | UC-AUTH-01, UC-AUTH-02, UC-ACC-01, UC-ACC-02, UC-TRF-01, UC-TRF-02, UC-ADM-01, UC-ADM-02, UC-ADM-03, UC-ADM-04 | UX-001..005 / cross-cutting | See cross-cutting scope requirements | **NOT RUN** — add CI/report URL |

## CONS risk lenses

| CONS | Canonical acceptance (no duplicate test case) |
|---|---|
| CONS-01 | AC-007 |
| CONS-02 | AC-010 |
| CONS-03 | AC-011 |
| CONS-04 | AC-006 |
| CONS-05 | AC-013 |
| CONS-06 | AC-018 |
| CONS-07 | AC-021 |
| CONS-08 | AC-024 |
| CONS-09 | AC-014 |
| CONS-10 | AC-015 |
| CONS-11 | AC-016 |
| CONS-12 | AC-017 |
| CONS-13 | AC-019 |
| CONS-14 | AC-020 |
| CONS-15 | AC-022 |
| CONS-16 | AC-023 |
| CONS-17 | AC-025 |
| CONS-18 | AC-033 |
| CONS-19 | AC-034 |

## Trace closure workflow

1. Add requirement IDs to each GitHub issue, PR and change/spec; connect to business rules and AC IDs as applicable.
2. Record actual automated test IDs and run evidence in the corresponding AC row; a passing test is not implied by a trace link.
3. During release verify every P0 FR/UX/NFR, 38 BRs, 38 ACs, and CONS risk links are covered; follow [release checklist](../05-verification/23-uat-and-release-checklist.md).
4. If a design change affects business meaning, return to the project owner for a policy change and update 01–09; a technical design choice alone is not authorization to change the V1 scope.
