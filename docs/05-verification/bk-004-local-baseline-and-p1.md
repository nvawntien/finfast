# BK-004 — Local Repository Baseline and P1 Handoff

> Owner: Hưng. Review date: 2026-10-23. Branch: `feat/BK-004-repository-baseline`.
> Baseline: `31d2425131766306ccf6a815ca057349bee77bff` from updated `develop`.
> Scope: independently verifiable local foundations supporting course P1. Cloud feasibility, hosted PR checks and full BK-004 acceptance remain separate gates.

## 1. Scope and branch provenance

[BK-004](../02-requirements/10-product-backlog.md) covers CI, Docker, migrations, configuration and health checks, traces to NFR-005/006, and depends on BK-002. The user requested the local work needed to support course **P1 — Business & Architecture**, retaining existing documents and using the `feat/` branch prefix. Course lifecycle P1 is not the optional P1 priority in the backlog. CI/container implementation supports that initial architecture evidence; the lecturer's full DevOps deliverables belong to course P3 and are not all completed here.

`origin` was fetched, clean local `develop` was fast-forwarded by nine commits to `31d2425` (PR #16, Ledger contribution), and this branch was created from it. BK-001 commit `8a87877` remains on `feature/bk-001-course-requirements`; it was not an ancestor of this baseline at inspection. Its documents are not lost, copied into this branch or treated as merged. The earlier 482-test result at `24896ce` is not the current baseline's verification result.

Follow [AGENTS.md](../../AGENTS.md), [PRD](../02-requirements/09-product-requirements.md), [architecture](../03-design/15-system-architecture.md), [test strategy](22-test-strategy-and-acceptance.md) and [governance](../6-governance/25-design-decision-register.md). No existing Markdown, Java, schema, API, dependency, environment configuration or CI workflow is changed by this task. The addition is a Windows Docker test launcher plus this evidence/handoff record.

## 2. Existing baseline audit

| BK-004 area | Checked-in source and findings | Boundary |
|---|---|---|
| Build/runtime | [POM](../../backend/pom.xml), [Wrapper properties](../../backend/.mvn/wrapper/maven-wrapper.properties), [Dockerfile](../../backend/Dockerfile): Java 21, Boot 4.1.1, pinned Maven/Wrapper, Java 21 JDK build and non-root JRE runtime | Reuse existing pins; no dependency or image-version change |
| CI | [Workflow](../../.github/workflows/ci.yml): backend tests, dependent executable-JAR packaging, docs/OpenAPI/Compose validation; PRs to develop/main, pushes to those branches and manual runs | Source/configuration review; no new hosted run, required-check setting or PR approval is claimed |
| Docker/local isolation | [Compose](../../compose.yaml): one backend and private-network PostgreSQL 16.15, loopback host bindings, persistent named database volume, DB health dependency | Existing default backend port 8080 is occupied by another project on this machine; tests/smoke use separate names and dynamic ports, not the developer stack |
| Migration/configuration | [Application configuration](../../backend/src/main/resources/application.yml), [V1](../../backend/src/main/resources/db/migration/V1__create_banking_schema.sql), [V2](../../backend/src/main/resources/db/migration/V2__protect_financial_facts.sql): Flyway enabled, clean disabled, eight banking tables and immutable-fact guards, no banking seed rows | No applied migration edits, Hibernate auto-update, reset or schema expansion |
| Secrets/build context | [.gitignore](../../.gitignore), [Docker ignore rules](../../backend/.dockerignore): local secrets/build artifacts excluded; restricted source/build context | Actual `.env` contents are not loaded, printed or modified by the new launcher |
| Health | [Actuator tests](../../backend/src/test/java/com/finfast/FinfastApplicationTests.java), application configuration and datasource health | Aggregate health is not proof that banking/security workflows or all module integrations are complete |
| Test database | [PostgreSQL test setup](../../backend/src/test/java/com/finfast/PostgresIntegrationTests.java), [isolated database helper](../../backend/src/test/java/com/finfast/IsolatedTestDatabase.java) | Uses disposable PostgreSQL Testcontainers, not developer database volumes |

The latest Ledger providers/tests are already present on this baseline. Historical statements in the root README/migration README that all posting work or FIX-001 remains absent/open are superseded by the dated Ledger execution records in backlog 10, verification 22 and governance 25. Those older files are preserved per the user's instruction; this audit neither repeats their outdated implementation claim nor declares real-provider integration complete.

## 3. Added Windows test launcher

[scripts/test-backend-docker.ps1](../../scripts/test-backend-docker.ps1) closes the local tooling gap: this machine has no `make` command or Java 21 on PATH, while the existing host test command requires Java 21. The script reuses the repository's Dockerfile instead of adding a second dependency/runtime definition.

From the repository root in Windows PowerShell 5.1 or PowerShell 7 with Docker Desktop's **Linux engine** running:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-backend-docker.ps1
```

The execution-policy option applies only to this process; it does not change machine policy. If organizational policy blocks scripts, follow that policy. No host Java, Maven, Make, Python or persistent PostgreSQL is required for this launcher. Initial Docker/Maven downloads need network access. It uses Docker Desktop's `host.docker.internal` for Testcontainers; other daemon/network arrangements are outside the verified support scope.

The launcher:

1. Builds the existing pinned `build` stage, which packages and runs tests excluding the integration tag as already specified by the Dockerfile.
2. Starts a separate runner and executes **`./mvnw --batch-mode --no-transfer-progress verify`**, including PostgreSQL integration tests and packaging.
3. Retains build/verification logs, Surefire reports, the executable JAR and a JSON test-count summary in a newly created temporary directory. A supplied `-ReportDirectory` must not already exist; it will not overwrite earlier evidence.
4. Fails on a build/Maven/native command failure, missing reports, zero tests, failures, errors or skipped tests. It checks the runner's exit status, not just Docker attach success.
5. Uses a unique run ID and ownership labels to remove only its own runner and temporary image tag. Testcontainers handles its temporary PostgreSQL resources. Build cache and report files are retained; no developer volume, checkout, `.env` or other project's container is removed.

The runner mounts the Docker socket to create Testcontainers, which grants Docker-daemon control. Use it only for trusted repository code. It does not mount the host source tree, copy host environment files into the runner or configure any developer datasource. Normal completion/failure runs cleanup; killing the PowerShell process can bypass `finally`, so inspect this invocation's recorded resource names before manually cleaning a stranded runner.

## 4. P1 contribution and dependency handoff

| P1 output supported | Concrete contribution | Still separate |
|---|---|---|
| Initial Cloud Architecture | Verifiable Java/Core/PostgreSQL topology and runtime packaging; local health/migration checks | Provider, SKU, region, price, storage/TLS/backup choices and public deployment |
| NFRs with targets | Repeatable build/test evidence for the foundation; source trace to NFR-005/006 and financial fixture checks | Course acceptance, application security, complete banking behavior and deployed performance |
| Workload Analysis | Establishes a runnable foundation on which a later controlled benchmark can execute | The exact PRD §9 benchmark and EV-003 remain unmeasured; unit/integration test duration is not throughput capacity |
| Requirements/risks | Records current foundation capabilities, missing external evidence and accurate implementation limits | BK-001's detection, at-rest encryption and role-mapping decisions remain awaiting the group |

BK-002's remote draft was inspected at `origin/feature/BK-002`, commit `cd9c2c29f9c907f1b4bef7989e398875fc13091b`. Its feasibility/provider-comparison documents still say research/preparation only and no verified provider/resources; no changes were imported. Their historical labels that PRD is draft/TD-012 is not selected conflict with the approved baseline: PRD and the **reference topology class** are approved in governance 25, while actual provider/resources remain pending. Use the current baseline for authority and the BK-002 draft only within its stated research/evidence limits.

Sĩ owns BK-002/EV-002 feasibility; Trung owns BK-024 deployed runtime/TLS/private persistence/recovery evidence. Hưng can verify local foundations and continue separately authorized Identity/Customer work within approved contracts, but this record does not close EV-002 or choose infrastructure on their behalf. No BK-005/BK-007 functionality is implemented here.

## 5. Verification and completion status

The independently executable **local BK-004 foundation slice is complete** on the baseline above. Only the launcher and this new document are added. No commit, push, pull request or merge was performed. Static validation, isolated tests, runtime smoke checks, hosted CI and cloud acceptance are different evidence categories; this is not blanket completion of BK-004 or the team's entire course P1 deliverable.

### Executed checks — 2026-10-23, Asia/Saigon

| Check | Actual result and limits |
|---|---|
| Windows launcher | Executed with Windows PowerShell 5.1 and Docker Desktop Linux engine 29.8.0; Java 21 runs inside the pinned image. Maven `verify`: **721 tests, 0 failures, 0 errors, 0 skipped**, `BUILD SUCCESS`; Maven duration 5 minutes 39 seconds. Surefire XML totals independently read by the launcher; executable JAR retained. This is local evidence, not a hosted CI result. |
| Runtime image build | Existing Dockerfile built successfully. Its integration-excluding package step ran **304 tests**, all passed. This subset is not added to the 721-test total. |
| Live runtime/startup | Pinned PostgreSQL image on an isolated Docker network, no host DB port, database on temporary memory storage; application exposed on a dynamically assigned loopback port. `/actuator/health` returned HTTP **200** with only `{"status":"UP"}`; runtime UID **10001**. |
| Flyway and application restart | Database history was exactly **V1/V2 successful**. Sum of rows across the eight banking tables was **0** before and after application restart. Restart returned **200/UP** with unchanged migration history. This tests application restart against the same running database, not disk persistence across database recreation. |
| Database unavailable/recovered | Pausing the temporary database produced **503/DOWN**; unpausing restored **200/UP**. Both responses exposed aggregate status only. The smoke harness used test-only JDBC connect/socket timeouts of 3 seconds and Hikari connection/validation timeouts of 3/1 seconds; repository defaults were unchanged. This is a datasource-health check, not financial failure/recovery acceptance. |
| Launcher protection | Reusing the existing report directory failed with exit 1 before Docker resource creation; the existing summary hash was unchanged. PowerShell syntax parsing passed. Normal successful execution removed its labelled runner and image tag. |
| Documentation/contracts | `scripts/check_docs.py`: **25 Markdown files, 335 local links, 88 OpenAPI references**, no duplicate YAML keys. OpenAPI schema validator: **OK**. |
| Compose configuration | Both application and documentation Compose files passed `config --quiet`. Application configuration used a separate synthetic environment file, without loading the developer's `.env`. The persistent developer Compose stack was not started. |

Initial smoke-harness attempts failed because PowerShell returned Actuator's vendor JSON as bytes and because Docker reassigned the ephemeral host port on application restart. The harness was corrected to decode UTF-8 when needed and re-read `docker port` after restart; the complete startup/restart/unavailable/recovered sequence then passed. These were verification-harness issues; no application change was needed. `make help` could not run because Make is absent on this host; Docker, the Maven Wrapper in the container and the documented Python checks were used directly. Host Java 21 testing was not attempted.

Local evidence is retained outside the checkout in `C:\Users\tangk\AppData\Local\Temp\finfast-bk004-31d2425-verification` (`build.log`, `verify.log`, `summary.json`, Surefire reports and `finfast-backend.jar`), plus `finfast-bk004-runtime-build.log`, `finfast-bk004-runtime-smoke.log` and `finfast-bk004-smoke-results.json` in the same temporary parent directory. These are local execution artifacts, not committed shared evidence; temporary files can be removed by OS cleanup. The suite can be reproduced with the launcher in section 3. The smoke procedure is build the existing runtime image, start the isolated database/application, query health and Flyway/table counts, restart the application, re-read its assigned port, pause/unpause the database and query health again, then remove only the temporary containers/network.

### Remaining BK-004 acceptance gates

- BK-002's actual cloud feasibility evidence and relevant BK-001 course/baseline decisions remain pending. No provider, deployment resources or acceptance decision is inferred from local execution.
- Hosted CI execution, review and required-check enforcement need evidence from the eventual PR into `develop`; the local run does not prove GitHub repository settings. No PR was created because commit/push was not authorized.
- Full deployed runtime/TLS/storage/backup evidence belongs to the separately assigned deployment work. No other feature is implemented or marked complete by this BK-004 record.
