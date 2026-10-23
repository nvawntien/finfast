# BK-001 — Course Requirements Assessment

> **Owner:** Hưng | **Evidence received/reviewed:** 2026-10-22
> **Status:** Assessment documented; BK-001 closure awaits disposition of the course/baseline gaps below. EV-001 remains PENDING VERIFICATION in the canonical registers.
> **Trace:** NFR-005 → EV-001 → BK-001. This additive record preserves existing requirement IDs, approvals and files. It is not a new approved implementation baseline.

## 1. Scope and authority

The user requested BK-001 on `feature/bk-001-course-requirements`, then supplied two images identified as the lecturer's assignment. This assessment maps those images to the approved FinFast requirements, identifies conflicts and missing evidence, and prepares the handoff for resolution. Per the user's later instruction, existing Markdown files, code and configuration are unchanged; only this document and copies of the supplied evidence are added. No commit or push is authorized.

The intake baseline is `develop` at `24896ce1422ce216f6bd75d3666f2e0cf73260d5` (2026-10-22). After fetching `origin`, local `develop` and `origin/develop` matched; the requested feature branch was created from that revision. This identifies the reviewed baseline, not a permanent claim about a moving branch.

Apply [AGENTS.md](../../AGENTS.md), the approved [scope](../01-discovery/01-product-vision-and-scope.md), [PRD](09-product-requirements.md) and [decision register](../6-governance/25-design-decision-register.md). A newly evidenced course conflict must be recorded and reconciled by the appropriate authority; it must not be silently omitted from the course assessment or automatically implemented over the approved design.

## 2. Evidence register

| Source | Provenance and locator | Authority / limit |
|---|---|---|
| Lecturer assignment, functional scope | User-supplied image: [Project 4 — Business Problem & Functional Scope](evidence/bk-001-course-functional-scope.png), received 2026-10-22 | Direct evidence of the visible assignment content. Lecturer attribution comes from the user; no author name, issue date, scoring rubric or full slide deck is visible |
| Lecturer assignment, engineering requirements | User-supplied image: [Project 4 — Engineering Requirements — Consistency & Security](evidence/bk-001-course-engineering-requirements.png), received 2026-10-22 | Direct evidence of the visible critical requirements, failure scenarios and demonstrations. The circular “4” identifies the project; it is not treated as a page number |
| FinFast requirements and approvals | [Discovery 07](../01-discovery/07-assumptions-and-questions.md), [PRD 09](09-product-requirements.md), [traceability 11](11-requirements-traceability.md), [governance 25](../6-governance/25-design-decision-register.md) | Current approved internal baseline. Course intake does not itself amend it or establish runtime acceptance |
| Apache Fineract | [Repository supplied by the user](https://github.com/apache/fineract); tracking parameters removed | Technical reference. Neither assignment image requires a Fineract fork or deployment |
| Official Fineract documentation | [Published documentation](https://fineract.apache.org/docs/stable/), displayed version 1.15.0 when inspected on 2026-10-22 | Descriptive reference; `stable` is mutable and is not pinned source or runtime evidence |
| Prior Fineract source review | [Existing engineering review](../6-governance/25-design-decision-register.md#apache-fineract-reference-review--2026-10-22), upstream revision `71c39730e9178301b7ab949ba711238ce81fc66d` | Separate historical review retained unchanged. This pass does not claim to rerun those tests or equate that revision with the published documentation |

The original image bytes were copied without modification. SHA-256:

```text
functional-scope:        3745d4f754a36a0b8be0ba680e7f829e38a023e1c23938e75cee54b3a2183977
engineering-requirements: 1a49a1e8cc2d6238f7387621d0d88472b747e50706850f0e9c7246fbdbe6dea3
```

The supplied images establish the requirements they show. They do not establish a complete course-wide rubric, provider requirement, deadline or numerical load threshold. Those omissions are unknowns, not evidence that no additional obligation applies.

## 3. Functional scope comparison

“Mapped” below means covered in the approved requirements, not implemented or tested. Canonical acceptance wording remains in [use cases 04](../01-discovery/04-use-cases.md) and the [traceability matrix](11-requirements-traceability.md).

| Assignment item and image location | FinFast trace | Assessment |
|---|---|---|
| Functional image: Scenario and educational simulation notice | BR-SIM-01/02; PRD §§1–4 | Mapped: simulated account/transfer backend, synthetic data and no real financial transactions |
| Functional image: Customer in Core Users | [Actors 03](../01-discovery/03-actors.md); FR-001..008; OQ-002 | Mapped to CUSTOMER |
| Functional image: Bank Operator and Auditor / Admin in Core Users | Actors 03; FR-009/011/012/013; OQ-002 | Needs interpretation: ADMIN has operational and evidence-inspection capabilities, but the approved baseline has only CUSTOMER/ADMIN and explicitly no separate Auditor role. The image does not settle whether separate permissions/personas are required; see §6 |
| Functional image: Required Use Cases 1 — customer/account management | FR-001/009/012; UC-AUTH-01, UC-ADM-01/03; AC-001/009/027/029/030/035/037 | Mapped to registration, atomic Account provisioning, authorized listing and freeze/unfreeze. The broad course label does not automatically authorize extra profile/account lifecycle operations |
| Required Use Cases 2 — view account balance | FR-003; UC-ACC-01; AC-002/022/024/026/035 | Mapped to owned Account overview and balance |
| Required Use Cases 3 — internal money transfer | FR-004/005/006; UC-TRF-01; AC-003/004/005/007/010/013/014/016/017/018/033/034/036 | Mapped to synchronous internal Customer transfers with atomicity, authorization and idempotency |
| Required Use Cases 4 — transaction history | FR-007/008; UC-ACC-02, UC-TRF-02; AC-012/015/021/024/025/026/031 | Mapped to committed history and authorized receipts |
| Required Use Cases 5 — transaction status | FR-004/006/008; BR-TRF-08/11; UX-004; AC-011/013/015/033 | Mapped to confirmed completion, durable final rejection and original-intent recovery. A timeout remains uncertain, not failed. The course image does not specify extra persisted lifecycle states; preserve the frozen outcome/API model |
| Required Use Cases 6 — audit trail | FR-005/011/012/013; BR-ADM-03/04; AC-019/020/029/030/031 | Mapped to immutable financial facts and required administrative audits. Operational logging alone is not the business audit |
| Required Use Cases 7 — basic suspicious-transaction detection | No dedicated FR/AC/BK coverage found; scope 01 §4 and PRD §4.3 exclude fraud models | **Gap requiring scope disposition.** No approved basic detection behavior, rule, flag/review flow or acceptance criteria was located. Existing balance checks, authorization and logs are not evidence that this use case is met |
| Engineering image: Suggested AI / Data Feature — rule-based or ML-assisted anomaly/fraud flagging | Same detection gap | The suggested implementation choice does not cancel the detection item in Required Use Cases. The supplied wording does not require ML. A minimal rule-based simulator feature is a candidate for review, not authorized implementation |

## 4. Engineering requirements comparison

| Engineering image: Critical Requirements | FinFast trace | Assessment |
|---|---|---|
| Strong transactional consistency | FR-005/011/012; NFR-001; TD-002/011/015; AC-007/010/017/018/019/022/023 | Mapped to a PostgreSQL transaction containing all required financial effects/audit and current-state checks; runtime proof remains required |
| Idempotent transfer operations | FR-006; BR-TRF-05/10/11; TD-003; AC-005/006/011/013/014/033/034 | Mapped to full-scope durable outcomes and same-intent recovery; no automatic new key after uncertainty |
| Authentication and strong authorization | FR-002; NFR-003; BR-ID-01/02; AC-024/027/028/030 | Mapped to approved sessions, role/resource checks and security tests; role interpretation remains separate in §6 |
| Immutable/auditable history | FR-005/007/008/013; NFR-002; BR-LED-03; AC-019/021/031 | Mapped to immutable committed facts, owned history and audit evidence |
| Encryption in transit | NFR-003/005; [architecture 15](../03-design/15-system-architecture.md); BK-024 | HTTPS is specified; the actual deployment/TLS evidence remains pending EV-002/BK-024 |
| Encryption at rest | NFR-003/005 and deployment evidence requirements do not define or prove encrypted database/backup storage | **Gap requiring an explicit deployment requirement and evidence plan.** Password hashing, private database access and a Docker volume do not by themselves establish encryption at rest |
| High traceability | FR-008/013; NFR-006; AC-031; [traceability 11](11-requirements-traceability.md); [logging guide](../03-design/logging-and-error-handling.md) | Mapped to requirement/test/task links, transaction references, business audits and correlation evidence. Existing observability foundations do not replace durable financial evidence |

## 5. Failure scenarios and required demonstrations

These are mapped test obligations, **not passing tests for the course**. Follow [verification 22](../05-verification/22-test-strategy-and-acceptance.md); use an isolated synthetic database and record revision, environment, reproduction steps and actual results.

| Engineering image item | Existing trace / responsible work | Evidence required |
|---|---|---|
| Debit succeeds, credit fails | FR-005; AC-010/019/022/023; BK-022 | Inject failure between effects; show complete rollback, unchanged balances, no partial facts/final success outcome and no missing required audit |
| Client timeout, request retried | FR-006; AC-005/011/013/014/033/034; BK-022 | Lose/delay response, retry original key and recover original result with one movement; distinguish pre-commit and ambiguous/post-commit cases |
| Service crashes during transaction | NFR-001/002; AC-010/021/033/034; BK-022 | Controlled crash/restart before and after commit, durable outcomes and invariant reconciliation; no reseeding or duplicate posting |
| Concurrent transfer test | AC-007/017/018/022/023; BK-022 | Concurrent spends, competing directions and freeze/movement order; no negative balances, duplicates or supply change |
| Duplicate request protection | AC-005/006/013/014/034; BK-022 | Same-key replay/concurrency, changed-payload conflict and scope separation |
| Failure/recovery demonstration | The three failure rows above; BK-022 | Reproducible failure and recovery evidence, not just a normal successful request |
| Security and audit review | AC-019/024/027/028/029/030/031; BK-021/022/024 | Wrong role/owner, expired/logged-out sessions, CSRF, audit rollback and immutable facts; add the approved at-rest/role disposition when available |
| Load test with latency/error metrics | NFR-004; PRD [§9](09-product-requirements.md#9-performance-acceptance-protocol); BK-023; EV-003 | Approved 3,000-intent/300-second workload at 10/s, completed p95 ≤2 s target, outcome/error distribution and zero invariant violations. These numbers are FinFast's approved method, not numbers stated in the images; results remain unmeasured |

Neither supplied image mandates autoscaling, Kubernetes, Kafka, Redis, microservices or a particular cloud. Do not infer a course-wide exemption from these two images. OQ-006 in discovery 07 remains CLOSED for internal V1 policy; its historical assertions about later mandatory technology/no named provider are not independently established by this evidence.

## 6. Decisions required before closing BK-001

**User confirmation — 2026-10-22:** When asked whether the group/lecturer had resolved detection, at-rest encryption and role mapping, the user replied: “Chưa có, ghi rõ chờ nhóm chốt” (no decision yet; record as awaiting the group's decision). These items therefore remain **AWAITING TEAM DECISION**. This is an explicit pending disposition, not approval to implement or omit the course requirements.

| Gap / ambiguity | Concrete disposition to review | Authority and handoff | Status |
|---|---|---|---|
| Required suspicious-transaction detection lacks baseline coverage | Define a minimal rule-based synthetic-data detection slice: signal/rule, evaluation time, flag record and authorized inspection, with acceptance criteria. Decide whether it belongs in the assessed milestone, or obtain explicit lecturer acceptance of its omission. Do not choose numeric thresholds, block transfers or add ML/services implicitly | Project owner resolves scope/milestone; Tiến reviews any necessary capability/schema/API change; assign implementation ownership afterward | OPEN; proposal only |
| Required at-rest encryption lacks explicit coverage/evidence | Specify which database storage, backups and sensitive persisted artifacts must be encrypted, key/access responsibility and verifiable provider/host configuration. Assess managed storage or host encryption through BK-002; do not invent an application cipher or claim existing volumes are encrypted | Project owner/Tech Lead resolve requirement; Sĩ owns EV-002 feasibility, Trung owns BK-024 deployment proof | OPEN; proposal only |
| Bank Operator / Auditor / Admin interpretation | Confirm whether existing ADMIN capabilities may cover the course personas. If separate read-only audit/operating permissions are required, obtain a narrow authorization/actor amendment and acceptance criteria rather than silently adding a third role | Project owner; lecturer clarification where needed; Tiến and Hưng for contract/security implications | OPEN; mapping not confirmed |
| Course-wide deployment and submission constraints not visible | Obtain any additional rubric or explicit confirmation covering mandatory provider/topology, assessed artifacts, deadlines and load thresholds. Do not infer requirements from Fineract or invent dates | Hưng gathers evidence; Sĩ/Trung use confirmed deployment constraints; Tiến coordinates release traceability | PENDING; visible requirements above are already recorded |

The user asked to preserve AGENTS.md and existing docs; that preserves the baseline but does not waive visible course requirements. [DEC-008 / EV-001](../01-discovery/07-assumptions-and-questions.md) and [PRD §12.1](09-product-requirements.md#121-evidence-dependencies) require explicit owner reconciliation of conflicts. Canonical registers remain unchanged at the user's request; this new record carries the findings for review. A future approved amendment must be synchronized through the existing change protocol before conflicting functionality is implemented.

## 7. Fineract reference disposition

Fineract's [official documentation](https://fineract.apache.org/docs/stable/) describes user permissions, client/KYC and loan/deposit products, a General Ledger, API/domain organization and multi-tenancy. Its security chapter describes Basic/OAuth options; deployment chapters discuss JAR/HTTPS and Kubernetes, while Docker Compose is marked TBD. These observations provide context, not course mandates or tested FinFast behavior.

Retain FinFast's approved integral VND, zero-balance Customer Account, finite Treasury/once-only GENESIS, signed-entry Ledger, sessions/CSRF, one modular monolith/database and frozen Java/REST contracts. Do not import an upstream product suite, authentication scheme, schema, frontend or infrastructure. Reuse the [existing engineering review](../6-governance/25-design-decision-register.md#apache-fineract-reference-review--2026-10-22) for source-specific comparisons. Neither a reference nor its tests closes the course gaps in §6.

## 8. Completion checklist and verification record

- [x] Inspect AGENTS.md, the full checked-in docs pack and current requirement/decision statuses.
- [x] Fetch origin, verify develop and create the requested feature branch from the matched baseline.
- [x] Archive the two user-supplied assignment images with matching hashes and record their provenance/limits.
- [x] Map the visible functional, engineering, failure and demonstration obligations to the existing baseline; identify unmapped items.
- [x] Document Fineract's reference role and prepare concrete gap dispositions without changing approved files or code.
- [ ] Obtain the decisions/evidence in §6 and confirm applicable course-wide constraints.
- [ ] Hand off resolved obligations and record the authority/date; reconcile canonical EV-001/backlog status through an authorized update when appropriate.

**BK-001 closure:** the assessment work above is complete, but the issue cannot honestly be called Done while course/baseline dispositions remain open. No banking implementation, cloud acceptance or performance PASS follows from this document. Independently authorized work within the approved baseline can continue under the [readiness matrix](10-product-backlog.md#member-implementation-readiness--2026-10-22).

### Verification executed on 2026-10-22

Validation applies to the unchanged code baseline `24896ce` plus this additive documentation. Static documentation checks and application tests have different scopes; neither replaces the course demonstrations in §5.

| Check | Result / boundary |
|---|---|
| `python scripts/check_docs.py` with Python 3.12.14 and the existing pinned docs requirements in a temporary environment | PASS: 25 Markdown files, 307 local links, 88 OpenAPI references, no duplicate YAML keys |
| `python -m openapi_spec_validator docs/03-design/contracts/openapi-v1.yaml` | PASS; unchanged contract validates |
| `docker compose -f compose.docs.yaml config --quiet` | PASS; configuration validation only |
| `git diff --exit-code` and `git diff --cached --exit-code` | PASS; existing tracked files restored/unchanged and no staged changes. The new document and two evidence images remain untracked |
| Source/copy SHA-256 comparison | PASS for both assignment images; hashes recorded in §2 |
| `docker build --progress plain --target build --tag finfast-bk001-check:24896ce ./backend` | PASS; existing Dockerfile uses Java 21.0.12.1, packages the executable JAR and runs 247 tests with zero failures/errors/skips. It explicitly excludes the `integration` tag, so this result alone is not PostgreSQL test evidence |
| Full Maven suite in that Java 21 container, command below | PASS: **482 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Includes PostgreSQL 16.15 Testcontainers, Flyway V1/V2, database constraints/concurrency fixtures, architecture and platform HTTP/logging/metrics tests. These are tests of the existing foundation, not end-to-end banking feature acceptance |

Full-suite invocation (Docker Desktop 29.8.0; no developer `.env` or persistent database mounted):

```sh
docker run --rm --name finfast-bk001-tests-24896ce --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal finfast-bk001-check:24896ce ./mvnw --batch-mode --no-transfer-progress test
```

Environment notes: `make help` could not run because Make is unavailable. Host `java` is not on PATH and the discovered local JDK is 27, so validation used the repository's pinned Java 21 container rather than changing the host setup. Initial sandbox access to Docker/temporary Python setup failed; authorized escalation enabled those checks. Existing services already occupy port 8080; validation did not start the persistent project stack or use its database. Full banking workflows, the course demonstrations, deployment/TLS/at-rest proof and EV-003 benchmark were not run by this documentation task.
