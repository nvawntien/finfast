# BK-007 — Independent authentication and session implementation

> Owner: Hưng. Implementation/evidence date: 2026-10-23.
> Branch: `feat/BK-007-authentication-sessions`, based on fetched `develop` at
> `31d2425131766306ccf6a815ca057349bee77bff`.
> Status: independent implementation and local verification complete; ready for
> review. This is not final integrated story/release acceptance.

## Scope and authority

This change follows [AGENTS.md](../../AGENTS.md), the
[BK-007 backlog entry](../02-requirements/10-product-backlog.md),
[FS-IDENTITY](../02-requirements/12-functional-specifications.md),
[use cases and acceptance criteria](../01-discovery/04-use-cases.md),
[business rules](../01-discovery/06-business-rules.md),
[module contracts](../03-design/module-contracts-and-boundaries.md),
[architecture](../03-design/15-system-architecture.md),
[data design](../03-design/16-domain-model-and-data-design.md),
[API design](../03-design/17-api-contracts.md),
[frozen OpenAPI](../03-design/contracts/openapi-v1.yaml), and the approved
TD-006/007/016 decisions in the [decision register](../6-governance/25-design-decision-register.md).
Trace: BK-007 → FR-002, NFR-003, UC-AUTH-02, AC-024/026/028.

The user requested an independent BK-007 branch while BK-005/006 integration is
pending. No changes from those feature branches were merged or cherry-picked.
The implementation covers login, server-managed sessions, logout, CSRF and the
existing `CurrentIdentity` contract. Customer supplies its existing read-only
`CustomerQueries` contract so Identity never queries Customer's table directly.
It does not implement registration, account provisioning, admin bootstrap,
transfers, ledger behavior or the frontend.

The course's **P1 — Business & Architecture** milestone is distinct from backlog
priority **P1**. BK-007 is a **P0** V1 backlog item. This record supplies scoped
implementation evidence; it does not declare the team's entire course P1 complete.
Existing approved Markdown, public Java APIs, OpenAPI and migrations are preserved.

## Implemented behavior

| Area | Behavior |
| --- | --- |
| Credentials | Identity-owned JDBC lookup of normalized email; Spring Security BCrypt, initial configurable strength 12; generic denial for wrong/unknown credentials |
| Password boundaries | Exact UTF-8 password input; reject more than 72 bytes, NUL and unpaired surrogates; no truncation or prehash; successful cost upgrades use a compare-and-set update |
| Principal | Immutable server-created user/role/customer identity; no password/hash/token in principal or response; CUSTOMER profile obtained through Customer API after credential verification |
| Login | JSON `POST /api/v1/auth/login`; strict duplicate/trailing/extra-field rejection, bounded 4096-byte body; caller cannot select role; success rotates session ID and clears old CSRF token |
| Session | `GET /api/v1/auth/session` returns only userId, role and optional displayName; default 30-minute inactivity timeout; cookie-only session tracking |
| Cookie | `FINFAST_SESSION`, HttpOnly, Secure and SameSite=Lax; no client-side bearer storage |
| CSRF | Public `GET /api/v1/auth/csrf`; framework session token with XOR masking; unsafe requests require `X-CSRF-TOKEN`; query/form-only tokens rejected |
| Logout | CSRF-protected POST invalidates server session, clears security context and deletes session cookie; GET does not log out |
| Authorization | `CurrentIdentity` rejects absent/invalid principals and wrong roles; HTTP namespaces require CUSTOMER/ADMIN as appropriate; unknown routes denied |
| Failures | 401 for invalid credentials/session, 403 for CSRF/role/transport denial, 503 for credential/profile backend failure; no credential/SQL details in response |
| Transport | HTTPS required by default for API requests; same-origin mutation policy; cross-origin mutations denied even with a valid token |

Spring Security config/web dependencies use the existing Spring Boot BOM. There
is no new service, token protocol or production fake provider. The custom JSON
filter uses the framework's session authentication strategy and explicit security
context repository, following the
[Spring Security session lifecycle](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html).

An account's FROZEN status does not block login. Account ownership and financial
permissions must still be enforced by the owning use case using `CurrentIdentity`;
namespace role checks do not establish transaction/account ownership.

## Activation and browser handoff

The explicit **`auth` Spring profile** activates all BK-007 providers, security
filters and routes. Set `SPRING_PROFILES_ACTIVE=auth` in the application's run
configuration, combining with other required profiles when integrating. The
existing default foundation profile is unchanged and has no BK-007 endpoints.
Business endpoint integration must activate security and must not treat the
default foundation profile as a protected banking deployment.

Use the existing datasource setup from the [root README](../../README.md).
In IntelliJ, select Java 21 and set the active profile in the FinfastApplication
run configuration. This change does not edit local `.env` or existing Compose
services. Docker-based verification commands are below. Real login requires
properly provisioned users from BK-005/006; this branch does not create demo users
or offer SQL seeding as an application feature.

For a browser deployment, use HTTPS and the same origin. After GET csrf, keep the
session cookie and pass the returned token in `X-CSRF-TOKEN` on login. After login,
fetch csrf again before the next mutation/logout because the old token is revoked.
The browser must accept the rotated cookie. A TLS-terminating proxy requires a
reviewed trusted-forwarding configuration; do not trust arbitrary forwarded headers.
Separate frontend origins and broader CORS settings remain deployment integration work.

The integration test alone sets `finfast.auth.require-https=false` on an isolated
random-port HTTP server and manually carries cookies. Secure remains enabled on
the cookie. This is not evidence of browser cookie handling over TLS. Production
uses the HTTPS requirement; disabling it is not a deployment recommendation.
Sessions are local to the servlet container, with no distributed session store;
restart invalidates those sessions and requires login again.

## Verification and reproducibility

Environment: pinned project Java 21 Docker build image, Spring Boot 4.1.1,
Docker Desktop Linux engine 29.8.0 and PostgreSQL 16.15 Testcontainers using the
project's pinned PostgreSQL image. Each integration context has an isolated
database with existing Flyway V1/V2 migrations. Only synthetic users are inserted
by tests. No developer database or persistent volume is reset.

| Executed check | Result |
| --- | --- |
| Existing Dockerfile build stage: `-DexcludedGroups=integration package` | PASS: 320 tests, zero failures/errors/skips |
| `AuthenticationHttpIntegrationTests` on real embedded Tomcat + PostgreSQL | PASS: 16 tests, zero failures/errors/skips |
| Full Maven `verify` | PASS: 753 tests across 37 reports, zero failures/errors/skips; 8m22s; container exit 0 |
| Executable JAR/test-class exclusion | PASS: existing `scripts/check_backend_jar.py` checked copied JAR and compiled test inventory |
| Documentation links/references | PASS: 25 Markdown files, 328 local links, 88 OpenAPI references; no duplicate YAML keys |
| OpenAPI schema / docs Compose configuration | PASS: existing YAML validator and `docker compose -f compose.docs.yaml config --quiet` |
| Whitespace | PASS: tracked diff plus explicit untracked BK-007 file checks |

Full verification completed at 2026-10-23 09:44:32 UTC (16:44:32 Asia/Saigon).
The total comprises 721 baseline cases plus 32 new BK-007 cases. Build and
targeted HTTP runs overlap the full run and must not be counted again.
Local evidence is retained outside the checkout under
`C:\Users\tangk\AppData\Local\Temp\finfast-bk007-31d2425-verification`
(Surefire XML, compiled test inventory, executable JAR and `summary.json`).
Build/full logs are `finfast-bk007-build.log` and `finfast-bk007-verify.log` in
the same Windows Temp parent directory. These are machine-local evidence,
not committed artifacts or remote CI evidence.

The 16 new unit cases cover credential failure, profile isolation, exact 72-byte
ASCII/multibyte/supplementary boundaries, invalid input, cost upgrades, trusted
principal/role handling and HTTPS/origin guard behavior. The 16 HTTP/JDBC cases
cover real session fixation protection, cookie attributes, CSRF rotation and
cross-session/query-token rejection, logout, real servlet expiration, failed
relogin, malformed input, CUSTOMER/ADMIN separation, FROZEN login, technical
failure and optimistic password-hash upgrade. Test-only endpoints/providers are
under test sources, not application routes.

Reproduce with Docker from the repository root (use unused container names):

```powershell
docker build --target build -t finfast-bk007-check:local ./backend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal finfast-bk007-check:local ./mvnw --batch-mode --no-transfer-progress verify
```

These commands require a compatible Docker daemon; the host override is for
Docker Desktop. On a host with Java 21, use the Maven Wrapper and the standard
[verification prerequisites](22-test-strategy-and-acceptance.md). `make help`
could not run here because host Make is unavailable; the checked-in Dockerfile
and Maven Wrapper were used. Initial compilation rejected inheritance from the
framework's final CSRF handler; composition fixed it before the passing build.

## Remaining integration and acceptance

1. **REST contract gap:** the frozen YAML omits 403 on login/logout/csrf/session
   even though CSRF and HTTPS/origin enforcement can deny requests. The existing
   API-ERR-01 exception already records incomplete adapter error coverage. This
   branch retains safe 403 responses and records this additional concrete case;
   it does not silently change OpenAPI or claim full frozen-contract conformance.
   Coordinate the scoped error-response amendment with Tiến before final adapter acceptance.
2. BK-005/006 must supply actual provisioned CUSTOMER/ADMIN records. End-to-end
   registration → login and protected admin setup are not verified by synthetic
   fixtures. Registration is currently denied as an unimplemented route; its
   future adapter must be explicitly permitted while retaining CSRF protection.
3. Sĩ's actual browser/frontend, HTTPS proxy/cookie/CORS topology, security review
   and resource-owner checks in each banking module still need integration tests.
4. Tests use BCrypt strength 4 for speed, plus strength 5 for upgrade behavior.
   Default strength 12 is configured, but deployment cost/load measurement and
   EV-003 are not established by these tests. No final release/security sign-off
   or closure of unrelated course/rubric gaps is claimed.
5. No commit, push or merge is performed by this implementation task.
