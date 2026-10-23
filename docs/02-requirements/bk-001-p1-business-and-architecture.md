# BK-001 — P1 Business and Architecture Handoff

> **Prepared:** 2026-10-23 | **Owner:** Hưng | **Scope:** course lifecycle P1 only.
> **Status:** P1 assessment and handoff prepared for team review. Course/baseline gaps remain awaiting team decisions; this document does not mark all of BK-001, EV-001 or P1 acceptance complete.
> **Baseline:** `24896ce1422ce216f6bd75d3666f2e0cf73260d5`, branch `feature/bk-001-course-requirements`. Code, configuration, canonical documents and the earlier assessment are unchanged.

## 1. Evidence, terminology and reading map

The user authorized the P1 portion of BK-001 on 2026-10-23 after supplying the lecturer's [Common Project Deliverables](evidence/bk-001-course-common-deliverables.png) image. Its **P1 — Business & Architecture** column lists six outputs, addressed in sections 2–7 below. Course lifecycle P1–P5 is different from the backlog's **P0/P1 priorities**; working on course P1 does not promote optional notifications/scaling into mandatory V1 scope.

The new image is archived byte-for-byte from `codex-clipboard-9a97dcaa-f329-4a2c-87ec-b8e6ef5668cd.png`, with SHA-256 `541e86a0d352239a1529e2e5754657c30569057e285d4438b1ad9f644362a17a`. The screenshot has no visible author/date, deadline, scoring weights, provider or instance sizing. Attribution to the lecturer is supplied by the user.

The [functional assignment](evidence/bk-001-course-functional-scope.png) and [engineering assignment](evidence/bk-001-course-engineering-requirements.png) still apply. Their provenance, detailed comparisons and earlier checks are in the [2026-10-22 assessment](bk-001-course-requirements.md). This P1 supplement adds the newly supplied deliverables evidence without rewriting that historical record. The common slide establishes the P1–P5 output categories; any additional course-specific deadlines, submission format and grading details remain unknown.

Follow [AGENTS.md](../../AGENTS.md) and the [approved decision register](../6-governance/25-design-decision-register.md). Existing FR/UC/BR/AC/NFR identifiers and approvals remain authoritative. New interpretations below are labelled as analysis or proposals; none grants implementation approval for a scope conflict.

| Required P1 output | Canonical source | Content in this handoff / review status |
|---|---|---|
| Business Problem Statement | [Vision 01 §§1–3](../01-discovery/01-product-vision-and-scope.md), [PRD 09 §2](09-product-requirements.md) | Section 2: documented from approved scope |
| Users / Stakeholders | [Actors 03](../01-discovery/03-actors.md), PRD §3 | Section 3: documented; course persona-to-role mapping awaits team decision |
| Functional Requirements | [Use cases 04](../01-discovery/04-use-cases.md), [rules 06](../01-discovery/06-business-rules.md), PRD §6 | Section 4: mapped; suspicious-transaction detection remains a course gap |
| NFRs with targets | PRD §§7–9, [verification 22](../05-verification/22-test-strategy-and-acceptance.md) | Section 5: targets and evidence defined; at-rest requirement needs scope/design disposition |
| Workload Analysis | PRD §9; TD-013/014 in governance 25 | Section 6: existing workload quantified and contention/storage implications analysed; no measurements or capacity claims |
| Initial Cloud Architecture | [Architecture 15 §§1–2](../03-design/15-system-architecture.md), TD-012 | Section 7: approved reference topology summarized; provider/resources/cost remain EV-002 work |

## 2. Business problem statement

FinFast is an educational digital banking simulator for a six-person team. Customers need to manage a simulated account, see balances, transfer funds internally and understand the result/history. Administrators need controlled demo funding, account status management and traceable evidence. The lecturer needs reproducible demonstrations that money is not duplicated, lost or exposed to an unauthorized user when requests overlap, time out or fail.

The project addresses both normal account operations and the correctness risks behind them: partial debit/credit, repeated submissions, concurrent spending, stale account status and false success after an uncertain response. The expected outcome is a demonstrable, independently developed Core with Customer/Admin Web access, reproducible deployment and testable financial/security guarantees. It does not handle real money or connect to external payment rails.

**Approved boundary:** simulated integral VND; one zero-balance Customer Account per Customer; ACTIVE/FROZEN states; finite Treasury supply initialized once; internal synchronous transfers; signed-entry financial facts; administrative audit. Real KYC/AML, lending, FX, fees/interest, full General Ledger, native mobile and production high availability remain outside the approved V1 baseline. The assignment's basic suspicious-detection item is explicitly tracked as a gap in section 8, not silently covered by these exclusions.

**Success definition:** implement the approved capabilities and collect their canonical acceptance evidence, preserving zero financial-invariant violations and safe authorization. The performance target and required deployment evidence are specified below; neither is asserted as achieved by writing this document. Sources: vision 01 and PRD §§2, 4, 7–9.

## 3. Users and stakeholders

| User / stakeholder | Intended outcome and access | Source / unresolved point |
|---|---|---|
| Customer (`CUSTOMER`) | Register, authenticate, read owned balance/history/receipts, transfer from owned Account; no Treasury management or another Customer's private records | ACT-01; FR-001..008; frozen owners retain authorized reads |
| Bank Administrator (`ADMIN`) | List necessary Customer metadata, allocate finite demo funds, freeze/unfreeze with reason/audit, inspect authorized financial/audit evidence | ACT-02; FR-009/011/012/013; no direct balance edits, normal mint/reset or self-registration |
| Bank Operator and Auditor/Admin in the assignment | Operational actions and evidence inspection conceptually overlap current ADMIN capabilities | Proposed mapping to ADMIN is **awaiting team decision**; no third role or separation-of-duties approval is inferred |
| Lecturer / evaluator | Review P1 rationale and later observe successful, concurrent, failed and recovered operations | Stakeholder, not a new application role; uses authorized demo access if needed |
| Six-person delivery team | Build, review, test, deploy and maintain evidence | Development/deployment access does not confer a banking role |

Hưng records course requirements through BK-001. Existing assignments put architecture/contract decisions with Tiến, cloud feasibility with Sĩ (BK-002), deployment with Trung (BK-024) and failure/performance work with Minh (BK-022/023), with other owners contributing to their modules. This is a handoff using the [existing assignment](10-product-backlog.md#member-implementation-readiness--2026-10-22), not a reassignment or a claim that they have accepted new tasks.

## 4. Functional requirements and course coverage

| Assignment capability | Existing FinFast behavior and canonical trace | P1 disposition |
|---|---|---|
| Customer / account management | Registration and one Account; protected Admin setup, necessary account listing, freeze/unfreeze. FR-001/002/009/012; UC-AUTH-01/02, UC-ADM-01/03; AC-001/009/024/027/028/029/030/035/037 | Approved requirements mapped; the broad course heading does not authorize extra account types or arbitrary profile/role editing |
| View account balance | Owned Account overview, integer VND and balance reconciliation. FR-003; UC-ACC-01; AC-002/022/024/026/035 | Approved requirements mapped |
| Internal money transfer | Positive valid amount between different ACTIVE Customer Accounts; atomic effects, no overdraft, scoped idempotency. FR-004/005/006; UC-TRF-01; AC-003..007/010/013/014/016/017/018/033/034/036 | Approved requirements mapped; preserve full canonical acceptance definitions |
| Transaction history | Owned committed movements, stable pagination and immutable authorized receipts. FR-007/008; UC-ACC-02, UC-TRF-02; AC-012/015/021/024/025/026/031 | Approved requirements mapped; rejected requests do not become financial postings |
| Transaction status | Report COMPLETED only after commit; retain final business outcomes; resolve uncertainty with the original intent/key. FR-004/006/008; UX-004; AC-011/013/015/033 | Existing outcome model mapped; no new persisted state machine is implied |
| Audit trail | Financial facts and separate Admin audits; allocation/status success and required audit are atomic. FR-005/011/012/013; UC-ADM-02/03/04; AC-019/020/029/030/031 | Approved requirements mapped; operational logs alone do not fulfill the business audit |
| Basic suspicious-transaction detection | No dedicated approved FR/AC found; scope 01 and PRD §4.3 exclude fraud models | **Unresolved course gap.** A proposed rule-based flagging slice needs scope, rule parameters, authorized inspection and acceptance criteria; it is not approved or implemented here |

FR-010's controlled GENESIS and FR-011's finite Treasury allocation support the simulation without introducing real funds. All FR-001..013 and UX-001..005 retain their approved P0 classification and the full mappings in [traceability 11](11-requirements-traceability.md). The selected rows above are a course reading map, not a replacement or reduced acceptance suite.

The common slide labels P5 AI Engineering **“if any”**. AI/ML is therefore conditional in that slide; this does not make the functional slide's required basic detection disappear. A non-ML implementation can be proposed for group review without adding model training, token-cost targets or an AI service to V1.

## 5. Non-functional requirements with targets

Targets below restate approved requirements or identify a gap. Binary criteria are testable targets; no availability percentage, RPO/RTO, cloud price or detection accuracy is invented.

| Requirement | Target / observable condition | Later evidence and source |
|---|---|---|
| NFR-001 — correctness | Zero financial-invariant violations: no negative balances, duplicate movements, partial required effects or supply drift; ordinary entries sum to zero | PRD §7; AC/CONS scenarios, concurrent/fault/recovery tests and reconciliation; BK-021/022 |
| NFR-002 — durability | Committed financial facts, final idempotency outcomes and required success audits survive application restart; history/outcomes retained for dataset lifetime; restart never repeats GENESIS | AC-019..023/031/033/034; PostgreSQL/restart evidence. An application restart guarantee is not an unapproved disaster-recovery SLA |
| NFR-003 — access and transport security | Every protected operation enforces role/resource authorization; logout/expiry invalidates the session; HTTPS and protected cookies/CSRF; no credentials/session secrets in responses or logs | AC-024/027/028/030; security checks and deployed TLS evidence |
| Course at-rest encryption | Visible course obligation acknowledged; storage/backup scope, key/access responsibility and verification method remain to be agreed | Group/Tech Lead disposition, then EV-002/BK-024 evidence. Hashing passwords or a private Docker volume does not prove encrypted storage |
| NFR-004 — performance | Existing target: completed-transfer p95 ≤2 s at 10 new intents/s for 300 s, using the exact approved PRD §9 method; all 3,000 eligible intents complete once with zero invariant violations and no unexpected primary errors | EV-003 / BK-023; full latency/outcome report. This is a project target, not a measured result or a number imposed by the images |
| NFR-005 — delivery | Containerized Core, persistent PostgreSQL, protected environment configuration, automated checks and reproducible cloud setup/restart evidence with recorded version/resources | EV-001/002; BK-004/024. Provider/SKU, budget and disaster-recovery targets remain unconfirmed |
| NFR-006 — maintainability and traceability | Domain-oriented Core independent of channels; preserve requirement-to-test/task links, approved module boundaries and peer review for financial changes | Architecture/contracts, automated checks and reviewed evidence; no mandatory broker or microservices |

Required failure scenarios remain debit/credit failure, timeout/retry and process crash, with concurrency, duplicate protection, security/audit and load demonstrations. P1 specifies their rationale and targets; execution belongs to later implementation/verification work. The common P4 SLI/SLO deliverable must later document indicators, objectives and measurement windows; it does not justify inventing a production SLA in P1.

## 6. Workload analysis

### Approved workload and explicit assumptions

The quantitative baseline is [PRD §9](09-product-requirements.md#9-performance-acceptance-protocol), not a forecast of real customers. It uses a dedicated synthetic dataset, 100 Customers in 50 fixed pairs, one 100,000,000 VND GENESIS and 100 audited allocations of 1,000,000 VND. Treasury is then zero; Customers are ACTIVE. Setup is outside the measured window.

| Phase | Existing workload | Interpretation for P1 |
|---|---|---|
| Warm-up | 300 intents over 30 s at 10/s, separate key namespace; await outcomes and verify restored Customer balances | Warm-up is excluded from reported measurement, but contributes persisted records |
| Measurement | 3,000 new intents over 300 s, one each 100 ms; 1,000 VND per intent; rotate across 50 pairs and alternate direction as specified in PRD | 60 intents per pair, 30 each direction; every request is eligible and final Customer balances return to 1,000,000 VND |
| Retry/recovery | Retain original intent/key; record attempts and final resolution separately | 3,000 intents does not necessarily mean only 3,000 HTTP attempts; faster rejections or recovery cannot hide primary failures |
| Reads and administration | Account/balance, paginated history/receipts and Admin operations are required, but excluded from the prescribed measured transfer workload | No traffic mix, read/write ratio, peak concurrent user count or Admin rate has been established; do not silently add them to the benchmark |

**Derived record volume, not measured disk usage:** the 3,000 successful measured transfers alone produce 3,000 Financial Transactions and 6,000 Ledger Entries. Including successful warm-up (300), allocations (100) and one GENESIS gives 3,401 Financial Transactions and 6,801 Ledger Entries. Registration/session data, durable request outcomes, Admin audits, indexes, WAL, logs and backups add storage; this count is not a byte estimate or a schema change. Dataset-lifetime retention means repeated runs accumulate records unless a separate controlled test dataset is used.

### Contention, capacity and measurement implications

- A transfer touches two Accounts, immutable posting facts and a scoped outcome in one transaction. Parallel spending on a hot Account and repeated same-key requests must serialize safely; they are correctness cases, not reasons to weaken atomicity.
- The approved commit-order mechanism is a potential shared write bottleneck; Treasury allocations have their own hot-account behavior. TD-014 requires measurement before substituting another mechanism. The paired workload does not prove every hot-account distribution is fast.
- History is a growing read workload with default 20/max 100 rows per page and approved stable traversal. No unbounded full-history response or invented retention purge is introduced.
- Offered rate is scheduled independently of response completion. Record client concurrency/scheduling delay, latency percentiles, throughput, error distribution and recovery. The 100 Accounts are test data, not proof of 100 concurrent sessions; p95 latency is not mean latency and does not determine a concurrency cap.
- Before selecting resources, BK-002 records compute/RAM/storage/network constraints and budget. BK-023 then measures the approved environment. Capacity and cost analysis for course P4 needs observations and provider data; this P1 analysis does not claim that a specific VM size or free tier will suffice.

## 7. Initial cloud architecture

Use the already approved topology class from [architecture 15 §§1–2](../03-design/15-system-architecture.md) and TD-012. This is the initial architecture requested by course P1, not proof of deployment or a new provider decision.

```text
Customer / Admin browser
          |
        HTTPS
          |
Public TLS entry + Web assets
          |
One Spring Boot Core runtime (domain modules, in-process APIs)
          |
Private connection
          |
One logical persistent PostgreSQL database
```

| Concern | Existing baseline / P1 treatment | Remaining handoff |
|---|---|---|
| Core runtime | Java 21, Spring Boot 4.1.1, Maven Wrapper, modular monolith; one deployment unit | Sĩ/Trung verify a suitable hosting option; no new service, broker or gateway is required by this document |
| Frontend | Responsive Customer/Admin channels; static-hosted or served with Core as permitted by architecture 15 | Sĩ selects the actual stack/hosting within the approved contracts; no framework inferred from Fineract |
| Data and transactions | PostgreSQL 16, Flyway, one logical financial database; one transaction for required posting/audit/outcome effects | Private access, persistent storage, deployment privileges and backup/recovery need real environment evidence |
| Public/private boundary | Browser is untrusted; HTTPS entry to Web/API, no public database endpoint; server validates role/resource access | TLS/secret configuration and the course at-rest encryption gap must be resolved before deployment acceptance |
| Build/deployment path | Existing repository CI and Docker foundations; versioned application artifact and environment configuration | Course P3 requires CI/CD, containerization, IaC and deploy/rollback deliverables; existing CI is not proof that cloud CD/IaC is complete |
| Operational evidence | Health and optional observability foundations exist | Deployed dashboards and course P4 reliability/load evidence remain future execution work; optional local telemetry is not a mandatory new cloud service |

Local Compose is a development setup with each developer's independent PostgreSQL; it is not the deployed cloud architecture. See the checked-in [build](../../backend/pom.xml), [Dockerfile](../../backend/Dockerfile) and [Compose configuration](../../compose.yaml) for actual pins and local setup. Region/provider, instance counts beyond the approved topology, SKU, disk capacity, budget, concrete backup policy and availability targets remain unselected/unverified under EV-002. No Kubernetes, Kafka, Redis, autoscaling or multi-region requirement appears in the supplied slides.

## 8. P1 review findings and later-phase handoff

The user has already confirmed **“Chưa có, ghi rõ chờ nhóm chốt”** for the three course gaps. Limiting this work to P1 does not approve or postpone recognition of them: they affect P1 actors, functional requirements and NFRs now, even though implementation happens later.

| Finding | Concrete next decision / evidence | Handoff and status |
|---|---|---|
| Detection coverage | Review a minimal rule-based flagging proposal; define signals/thresholds, who can inspect flags, acceptance examples and milestone. No automatic blocking/freezing is proposed as existing behavior | Project owner scope decision and Tiến technical review; implementation owner not yet assigned; **awaiting team decision** |
| At-rest encryption | Define database/backup scope and key/access responsibility, then select verifiable storage protection with the deployment environment | Group/Tech Lead requirement disposition; Sĩ BK-002 and Trung BK-024 feasibility/proof; **awaiting team decision** |
| Operator/Auditor mapping | Confirm whether ADMIN may cover both course personas or separate permissions are required | Project owner/course clarification as needed; Tiến/Hưng for any approved security amendment; **awaiting team decision** |
| Course-wide details | Archive any deadline, marking weights, submission format or provider mandate if supplied; distinguish missing evidence from confirmed absence | Hưng BK-001; **not provided**; no invented date, exemption or extra infrastructure |
| Concrete cloud feasibility | Record provider/resources/budget and storage/TLS/backup feasibility before resource selection and benchmark execution | Sĩ BK-002/EV-002 with Trung; **pending**; an initial P1 diagram can be reviewed without claiming this complete |

The Common Deliverables slide also names P2 component/API/data/integration decisions; P3 threat model, IAM, CI/CD, containerization, IaC and deploy/rollback; P4 performance/failure evidence, observability, SLI/SLO and capacity/cost; and P5 AI architecture/evaluation/guardrails/latency/token cost **if AI is used**. They are recorded here as later lifecycle handoff, not executed or declared covered by this P1 task. No new backlog IDs, owners for unassigned scope, deadline or approval is fabricated.

## 9. P1 review checklist and completion boundary

- [x] Archive the Common Deliverables source and distinguish course phase P1 from backlog priority P1.
- [x] Prepare all six P1 sections with canonical source references and distinguish approved requirements from course gaps/proposals.
- [x] Provide quantitative workload analysis, assumptions and limits without inventing measurements or changing PRD §9.
- [x] Present the approved initial cloud topology and explicitly identify unselected provider/resources.
- [x] Record pending decisions, responsible review/handoff roles and later-phase obligations.
- [ ] Team reviews the three gaps, records dispositions and resolves any affected P1 requirement/actor interpretation through the existing change protocol.
- [ ] Record any additional course constraints and the team's/evaluator's P1 acceptance when actually provided.

**Deliverable outcome:** this P1 documentation slice is prepared for review. Existing approved requirements do not need routine reapproval, and later P2–P5 implementation is not a prerequisite to preparing this slice. Team/evaluator acceptance of P1, overall BK-001 closure, EV-001/002 verification, EV-003 measurement and final release remain distinct; none is granted here.

## 10. Validation record

Only this Markdown file and the Common Deliverables image are added in the 2026-10-23 P1 task. The earlier assessment and its two images are retained unchanged. No backend tests, runtime stack, cloud deployment or benchmark are rerun for this documentation-only addition; the earlier assessment's 482-test result is historical evidence for its recorded baseline, not a new test run or proof of course acceptance.

Checks executed on 2026-10-23 with the existing temporary Python 3.12.14 documentation environment and repository-pinned validation dependencies:

| Check | Result |
|---|---|
| `python scripts/check_docs.py` | PASS: 26 Markdown files, 327 local links, 88 OpenAPI references; no duplicate YAML keys |
| `python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml` | PASS; existing contract unchanged |
| `docker compose -f compose.docs.yaml config --quiet` | PASS; static configuration validation, not service startup |
| `git diff --check`, `git diff --exit-code`, `git diff --cached --exit-code` | PASS; no tracked-file edits or staged changes |
| SHA-256 preservation checks | Earlier assessment and both earlier images unchanged; new image matches its supplied original |
| New Markdown whitespace and workload calculations | PASS; no trailing whitespace; measured-transfer and full setup/warm-up/measurement record counts recomputed |

No commit/push or canonical approval/status update was performed. The new P1 files remain untracked alongside the retained earlier BK-001 draft/evidence, ready for review.
