# FinFast Customer Web — BK-019 Phases 1–4

Frontend foundation, shared HTTP client, opt-in Mock API, Identity UI and Customer Dashboard.
Registration/login and server-session checks are implemented. Customer routes require
a CUSTOMER session; the Dashboard reads the current account. Transfer, history and receipt screens remain placeholders.
Synthetic fixtures live only in the development/test mock module. Only simulated funds
are in scope; no Admin UI is included.

## Requirements and installation

- Node 24.13+ within Node 24; npm 11 (development environment: Node 24.13.0, npm 11.6.2).
- From repository root (`finfast/`), enter `website/`.
- Dependencies are pinned in `package.json`; use the committed lockfile for reproducible installs.

```sh
cd website
npm ci
npm run dev
```

Open http://127.0.0.1:3000. Port 3000 is reserved for frontend by the repository;
the server fails if occupied instead of silently choosing another port.
On Windows with PowerShell script restrictions, use `npm.cmd` instead of `npm`.
Use `npm run dev:mock` for Identity and Dashboard without a backend. Normal dev requires the approved
same-origin API; unavailable APIs show an explicit error and never enable mocks.

## Phase 2 — HTTP client and opt-in mocks

```sh
npm run dev:mock
```

This explicitly selects Vite's `mock` development mode. A visible Mock API notice
appears; Identity forms call synthetic endpoints. Stop the server before switching back
to `npm run dev`. Normal dev never enables mocks; production builds cannot enable
mocks, including `vite build --mode mock`. Worker startup must finish before render;
startup failure stops initialization rather than falling back to a real backend.

The official MSW worker is served directly from the installed dependency by a dev-only
Vite middleware. No worker file is copied into `public/` or `dist/`. Production removes
the conditional mock import. When leaving mock mode, initialization unregisters only
this origin's `/mockServiceWorker.js`, preserving unrelated service workers.

`src/api/index.ts` exports the browser client for same-origin `/api/v1`. No backend
host, proxy or CORS policy is guessed; connecting a separate Core host is deferred
until the actual topology/security configuration is confirmed.

```ts
import { api, ApiError } from './api';

try {
  const response = await api.request('/accounts/me');
  // response.data is unknown: feature adapters must validate their own DTOs.
  // The transport preserves status; 2xx alone is not financial completion.
} catch (error) {
  if (error instanceof ApiError) {
    // Inspect kind/status/code; never log credentials, tokens or request bodies.
  }
}
```

### Transport and financial boundaries

- `fetch` uses `credentials: include`, `cache: no-store`, `redirect: error`.
  The server owns `FINFAST_SESSION`; the client never reads/writes session cookies
  or stores credentials/CSRF in localStorage. TLS and HttpOnly/Secure/SameSite
  enforcement require the real backend/browser integration.
- Every POST retrieves a fresh `{token}` from `GET /auth/csrf` and sends
  `X-CSRF-TOKEN`, including public registration/login. No cached token can survive
  login/logout rotation. A CSRF failure never automatically replays the mutation.
- Request JSON, idempotency key and caller signal are snapshotted before awaiting
  CSRF. Validation/header construction use the captured key; abort subscription and
  cleanup use the same captured signal. There are no automatic retries,
  generated idempotency keys, redirects, auth navigation or financial recovery UI.
  `/transfers` requires the caller's original `idempotencyKey`. Preserve the original
  payload/key in later feature state; absence of `note` stays distinct from `""`.
- Timeout defaults to 15 seconds (frontend choice, **not a confirmed backend SLA**).
  Caller AbortSignal and timeout apply to CSRF and the main request. Cancellation
  is not proof the server rolled back. `requestMayHaveBeenSent` reports possible
  dispatch of the main request, not commit confirmation.
- Errors distinguish `http`, `protocol`, `network`, `timeout`, `aborted` and preserve
  documented HTTP status/code. Financial command 422 requires `outcome: REJECTED`;
  ordinary registration/history 422 is not a final financial result. Transfer 202
  stays PROCESSING/UNKNOWN; resolve 200 can contain REJECTED. Missing outcome 404,
  503, timeout and network failure do not authorize a new-key replacement.
- `API-ERR-01` remains unresolved: undocumented 400/500 are surfaced as protocol
  errors without altering the frozen specification. The frozen auth operation
  status lists also do not specify CSRF denial; mock auth CSRF failure deliberately
  produces a transport error, not a newly invented HTTP response contract.
- Response payloads remain `unknown`; this transport is not a complete DTO validator.
  It checks JSON, operation/status mapping, error envelopes, CSRF, financial
  status/outcome consistency and monetary fields. Feature DTO decoders remain for
  later phases. Server error messages must only be rendered as text, never HTML.
  Outcome enums require strings without coercion. Response-stream read failures are
  transport errors (or timeout/abort when applicable); a fully read body with invalid
  JSON or schema is a protocol error. Incomplete financial responses remain UNKNOWN.
- `src/lib/money.ts` validates canonical nonnegative VND strings through signed-64-bit
  maximum `9223372036854775807`. No Number/parseFloat money conversion. Zero balances
  are valid; `parsePositiveMoneyVnd` is available for future positive-amount inputs.
  Customer APIs are covered; Admin signed ledger deltas are outside BK-019 scope.

### Mock scope and limitations

- Synthetic login: `customer@example.test` / `mock-only-password`, **mock mode only**.
  Never enter real credentials into mocks. Mock state resets on reload/test reset.
- Handlers cover the ten Customer operations. Registration creates a distinct zero-balance
  fixture; it does not create a real account or a new login. Session state is in memory
  and CSRF rotates after mock login/logout; this does not prove cookie security.
- Account/history/receipt are principal-scoped synthetic fixtures; large VND values intentionally
  exercise precision. History has one page and no valid continuation cursor fixture.
- Default transfer/resolve return 202 UNKNOWN. They never post funds or manufacture
  a success. Unit tests inject deterministic completed/rejected/error responses.
  Mock data does not implement a ledger, durable idempotency, concurrent posting or
  server authorization; full financial behavior is deferred.
- Unhandled `/api` routes and other origins return network errors in mock mode;
  they cannot silently reach a real backend. Node tests use the same handlers and
  reset them and session state between cases. Browser request logging is disabled.

References checked 2026-10-23: [MSW browser integration](https://mswjs.io/guides/integrations/browser),
[MSW Node integration](https://mswjs.io/guides/integrations/node),
[Vitest guide](https://vitest.dev/guide/). Versions are pinned in `package.json`.

## Build and checks

```sh
npm run typecheck
npm run build
npm run check:production
npm run test:unit
npm run preview
```

Build output is `dist/` (ignored). `preview` is a local build preview, not a production
deployment. Future static hosting must rewrite application routes to `index.html`
for deep links; no cloud or backend configuration is changed here.

For browser tests, install Playwright Chromium once if unavailable:

```sh
npx playwright install chromium
npm test
npm run test:mock
```

Alternatively use an existing Chrome installation in PowerShell:

```powershell
$env:PLAYWRIGHT_CHANNEL = 'chrome'
npm.cmd test
```

Stop any manually started server on port 3000 before tests. Playwright starts/stops
its own local dev server. `test:mock` additionally needs free ports 3001/3002 and a
fresh `npm run build`; it checks mock startup and production preview separately.
`npm run check` runs build, production artifact checks, unit/integration tests and
both browser suites. Foundation tests cover direct routes, 404, root redirect, simulation notice,
only expected session/account API reads, runtime exceptions, active navigation, keyboard skip link/route focus,
browser back, reduced motion, reflow and axe checks at 360×800, 768×1024 and 1440×900.
The 200% text-scaling check is not a substitute for manual browser 200% zoom.
Trace files for failures are in ignored `test-results/`.
On Windows, use the same normal user for installs/dev/tests. If an old ignored
output is owned by another account, use a fresh output directory, for example
`npm.cmd test -- --output=.cache/my-test-results`; do not change permissions on source
or run all development commands as Administrator.

### Phase 2 execution record — 2026-10-23

- Typecheck: passed. Vitest: **63 tests passed** across client/CSRF/errors,
  money boundaries, environment guards and mock/OpenAPI schema/status checks.
- Normal build and `npm.cmd run build -- --mode mock`: passed. Both production
  bundles passed `check:production`; the conditional mock code/fixtures and worker
  are absent. At Phase 2 the transport was unused by placeholder screens and could
  also be tree-shaken until feature integration.
- Chrome mock/production tests: **3 passed**; rerun against the production build
  with `--mode mock` also passed. Phase 1 browser regression: **30 passed**.
- `git diff --check` plus whitespace checks on newly added files: passed.
- Dependency installation audit: 0 vulnerabilities reported. Full repository
  documentation checker still cannot run (`markdown_it` missing); basic local
  README links and fenced blocks checked separately.
- Initial unit execution exposed an early captured-fetch reference; fixed by
  resolving global fetch at request time. Initial build hit EPERM on old sandbox-owned
  `dist/`; only generated output was removed, then rebuilt as the Windows user.
  Browser output uses ignored `.cache/phase2-*` folders to avoid older ACL conflicts.
- No backend HTTP/security/financial E2E is claimed. Mock session state is not a
  substitute for server cookie security, durability, replay or concurrency tests.

### Phase 2 review fixes — 2026-10-23

All three review findings were addressed in `src/api/client.ts`, with 18 regression
cases in `tests/unit/client-review.test.ts`:

- P2 intent/key: replacing or deleting the caller's key while CSRF is pending cannot
  change the transmitted payload/key. Replacing the signal cannot redirect abort
  handling or listener cleanup; checked on completion and cancellation.
- P2 outcomes: array/object/number/null rejected as protocol errors for both
  OperationResult and ErrorResponse.
- P3 streams: interrupted body reads after HTTP 200/422/503 headers remain network
  failures with UNKNOWN financial outcome. Complete malformed JSON remains protocol;
  timeout/abort during body consumption preserve their classifications. No retry added.

Verification after fixes: typecheck, production build, production artifact check,
**81 unit/integration tests**, **3 Chrome mock/production tests**, **30 Phase 1 browser
regression tests**, and Git whitespace checks passed. Browser outputs are ignored
under `.cache/phase2-review-*`. Re-review found no remaining issue within these three
findings. Real backend security/financial integration remains outside this evidence.

### Phase 1 execution record — 2026-10-23

- `npm.cmd run typecheck` and `npm.cmd run build`: passed.
- Chrome: `npm.cmd test -- --output=.cache/playwright-results` with
  `PLAYWRIGHT_CHANNEL=chrome`: **30 passed**, exit 0; overview screenshots inspected
  at all three viewports. The initial sandbox run stalled at teardown; a subsequent
  run could not unlink `test-results/.last-run.json` (EPERM). The successful run used
  a fresh ignored output directory outside the sandbox. No application changes were
  needed for this environment issue.
- Dependency installation audit: 0 vulnerabilities reported at install time.
- Git whitespace check, local Markdown links and balanced fenced blocks: passed.
- Repository `python -B scripts/check_docs.py`: unavailable because `markdown_it`
  is not installed. The lightweight Markdown checks above are not that full checker.
- `make help`: unavailable because Make is not installed; used website scripts.

## Structure

```text
website/
  index.html
  package.json / package-lock.json
  tsconfig.json / vite.config.ts / vitest.config.ts
  playwright.config.ts / playwright.mock.config.ts
  scripts/check-production.mjs
  design-system/finfast/MASTER.md
  src/
    main.tsx
    app/
      router.tsx
      prepareEnvironment.ts
      layouts/CustomerLayout.tsx + .module.css
    components/
      Navigation.tsx + .module.css
      SimulationNotice.tsx + .module.css
      PlaceholderPage.tsx + .module.css
      Icon.tsx
    styles/tokens.css + global.css
    api/client.ts + contracts.ts + errors.ts + index.ts
    lib/money.ts
    features/auth/             # Identity forms, service, session store/provider and guard
    features/account/          # Dashboard, DTO decoder and session-scoped account store
    mocks/browser.ts + fixtures.ts + handlers.ts
  tests/e2e/                  # Foundation, Identity and Dashboard browser checks
  tests/unit/                 # Client, money, environment and OpenAPI fixture checks
  tests/mock/                 # Real browser MSW and production isolation checks
```

CSS Modules isolate components; CSS variables own design tokens. Native links and
React Router declarative routing provide keyboard navigation. Layout owns shared
landmarks and focus management. `PlaceholderPage` is intentionally shared until
actual feature behavior needs separate modules. Do not create speculative API wrappers
or unused folders. Future phases introduce `src/features/{transfers,history,receipts}`
as behavior is implemented.

| Route | Screen |
| --- | --- |
| `/` | Redirect to `/customer` |
| `/register` | S01 registration; success requires separate login |
| `/login` | S02 login and expired-session notice |
| `/customer` | S03 Customer Dashboard; server-owned account overview |
| `/customer/transfer` | S04 transfer placeholder |
| `/customer/history` | S05 history placeholder |
| `/customer/receipts/:reference` | S06 receipt placeholder; reference is not looked up |
| Other paths | 404 with navigation home |

## Baseline and integration boundaries

Read [AGENTS.md](../AGENTS.md), [BK-019](../docs/02-requirements/10-product-backlog.md),
[PRD](../docs/02-requirements/09-product-requirements.md),
[UX](../docs/03-design/14-ux-user-flows-and-e2e.md),
[architecture](../docs/03-design/15-system-architecture.md) and
[API contracts](../docs/03-design/17-api-contracts.md).
UX-001/003/004/005 and AC-005/011/013/014/015/033/038 remain the full BK-019 target;
Phases 3–4 verify frontend Identity and account reads against controlled APIs; this does not
establish real backend security, financial or full end-to-end acceptance.

Source inspection on 2026-10-24 found no banking HTTP controllers in this checkout.
The approved `/api/v1` auth, account, transfer, outcome-resolution, history and
receipt contracts are integration specifications, not available endpoint evidence.
Later phases must verify provider implementations before connecting. Preserve
HttpOnly server sessions, CSRF, server authorization, decimal-string VND and original
idempotency key/payload recovery; never infer success from a placeholder or timeout.
Phase 2 mocks remain separate from the real client and opt-in. Admin UI belongs to BK-020.

## Design and remaining verification

[Design master](design-system/finfast/MASTER.md) records the UI UX Pro Max queries,
accepted recommendations and product-specific adaptations. System fonts avoid a
runtime third-party font dependency. No remote images or analytics are loaded.

Manual browser 200% zoom, screen reader, Firefox/WebKit, real backend integration
and full banking E2E remain separate checks. Automated axe checks do not certify WCAG.
Root CI currently has no frontend job; this phase provides local scripts only.

Versions/requirements checked against npm registry and official
[Vite guide](https://vite.dev/guide/) and
[React Router installation](https://reactrouter.com/start/declarative/installation)
on 2026-10-23. No framework was mandated by the repository; these are the selected
Phase 1 implementation dependencies, not a change to backend technology.

## Phase 3 — Identity UI

- `src/features/auth/validation.ts`: required-field/email checks and TD-007's 72 UTF-8 byte password capacity; no additional complexity policy.
- `service.ts`: typed, runtime-validated Identity DTOs over the existing HTTP client.
- `sessionStore.ts`: in-memory state, stale-response protection, deduplicated refresh,
  mutation serialization, explicit unknown logout result.
- `SessionProvider.tsx`: refresh on startup, Customer navigation and window focus/visibility;
  Customer role guard, expired-session redirect and POST logout.
- `IdentityPage.tsx` / `.module.css`: accessible register/login forms and separate
  registration confirmation. Design decisions: [Identity](design-system/finfast/pages/identity.md).

No token or password is written to localStorage/sessionStorage. Form passwords are
cleared after submission; registration is aborted on unmount. A pending login is owned
by the session store and completes across navigation (aborting the browser request
would not prove the server did not create a session). No mutation is automatically retried.
Shared `signing-in` state survives route changes and prevents competing Identity forms.
Explicit logout during login suppresses that login's UI result, waits for it to settle,
then sends exactly one POST logout with fresh CSRF. Repeated logout calls share that
pending operation. If login ended with an uncertain transport/protocol result, the UI
keeps logout uncertainty even after a logout response: a client timeout alone cannot
prove that delayed server work cannot create a session. Real backend validation remains required.
The session DTO has no expiry timestamp: expiry is discovered through server 401 on
refresh or current-scope account reads, not an invented client TTL. Phase 4 account 401
expires only its originating confirmed session scope. Account responses from before
session revalidation are discarded, including late 401 responses.
Future financial adapters must integrate their own session-expiry handling.
A client guard is a UX boundary, not a substitute for server authorization.

### Synthetic development flow

Run `npm.cmd run dev:mock`, open `/register`, create a synthetic account, then sign in
separately. The existing fixture is `customer@example.test` / `mock-only-password`.
That address and duplicate registrations return 409. New mock registrations live only
in worker memory and are lost on reload/reset; they retain an ephemeral digest, not
plaintext passwords. SHA-256 here is fixture bookkeeping, never a production password
hashing recommendation. Real backend password storage remains the backend's concern.
MSW does not implement HttpOnly cookies, server session expiry or real authorization.
Each registration has distinct User/Customer/Account identifiers and a zero-balance
account matching the registration response. Session, account, history and receipt
fixtures follow the current principal. New customers have empty history and cannot
read the seeded customer's receipt (404); signed-out requests return 401.
Mock registration rejects passwords exceeding 72 UTF-8 bytes with 422 before creating
the fixture verifier. Login rejects them with 401, since its frozen contract has no 422.
Passwords are never truncated or normalized, and the fixture digest is never passed
into BCrypt as a custom prehash. Production BCrypt behavior is outside this mock.

### Verification commands (Windows)

```powershell
npm.cmd run build
npm.cmd run test:unit
npm.cmd run check:production
$env:PLAYWRIGHT_CHANNEL='chrome'
npm.cmd run test -- --output=.cache/phase3-browser
npm.cmd run test:mock -- --output=.cache/phase3-mock
```

Chrome must be installed for that channel override; otherwise use the browser installation
instructions above. Fresh `.cache/` output folders avoid collisions with Windows artifacts
owned by a different execution account. Do not change permissions on source files to fix
regenerable build/test caches.

Remaining integration gaps: no banking controllers in this branch; production same-origin
routing/TLS and cookie attributes need a real environment; frozen auth contracts do not
specify CSRF-filter denial responses. Do not invent response statuses or fallback behavior.
Manual screen-reader checks, actual browser zoom and Firefox/WebKit remain outstanding.

### Phase 3 verification record — 2026-10-23

| Check | Result |
| --- | --- |
| TypeScript / production build | Passed |
| Production artifact isolation | Passed; no mock worker, markers or fixture data |
| Unit/integration, including Phase 2 regression | 100 passed in 6 files |
| Chrome browser, including Phase 1 regression | 51 passed across mobile/tablet/desktop |
| Browser MSW / production isolation | 4 passed |
| `git diff --check` | Passed; Windows LF/CRLF notices only |

Final browser artifacts are ignored under `.cache/phase3-verified-browser/` and
`.cache/phase3-verified-mock/`. Desktop/mobile screenshots were visually inspected.
The initial default-browser run could not launch the bundled browser; verification
used installed Chrome through the existing channel setting. No dependencies were added.

### Review fixes verification — 2026-10-24

Preserved the interrupted changes and verified all three findings after resuming:

| Check | Result |
| --- | --- |
| TypeScript / production build / production artifact isolation | Passed |
| Unit/integration, including Phase 2 regression | 113 passed in 6 files |
| Chrome browser, including Phase 1 regression | 60 passed across mobile/tablet/desktop |
| Browser MSW / production isolation | 4 passed |
| `git diff --check` | Passed; Windows LF/CRLF notices only |

Added coverage includes pending login across route changes, competing login rejection,
queued/deduplicated logout, stale session completion, uncertain login failure, two-customer
ownership and receipt isolation, concurrent duplicate registration, ASCII/Unicode 72/73
byte boundaries and rejection without truncation. Browser outputs are ignored under
`.cache/phase3-review-fixes-browser/` and `.cache/phase3-review-fixes-mock/`.
These are frontend/mock checks; real server cookie rotation, delayed server completion,
CSRF-filter mapping, manual screen-reader checks and Firefox/WebKit remain unverified.

## Phase 4 — Customer Dashboard

`src/features/account/` contains the Dashboard, account DTO decoder and request store.
The existing HTTP client performs only `GET /api/v1/accounts/me`; no financial
mutation or automatic retry is introduced. Refresh/retry is an explicit GET.
The decoder validates the six approved fields and FR-003's Customer-only visibility
and equal current/available balances in V1. VND remains an integer decimal string;
the Phase 2 formatter preserves precision, including amounts above JavaScript's safe integer range.
The greeting comes from the authenticated session, not an invented account-owner field.

Loading clears previous amounts. Zero, funded and FROZEN states have explicit messages;
FROZEN retains account reads and the history placeholder link. The transfer shortcut
is omitted for FROZEN according to FR-004's ACTIVE requirement; server authorization
remains authoritative. Transfer/history/receipt functionality is not implemented.
An empty/null DTO is a protocol error, never a successful absent account or a fabricated
zero balance: this operation specifies only 200, 401 and 403. Network, timeout,
forbidden and invalid-response failures have retry feedback without fallback to mocks.

Requests are scoped to an in-memory session epoch and cancelled on cleanup, logout or
principal change. Both sequence and epoch checks discard late responses even if a
transport ignores abort. Data is hidden synchronously when its scope is no longer
current. Shared `revalidating` state removes the cached principal and advances scope
as soon as session verification starts. The guard hides Dashboard content; account
reads are aborted and cannot restart until a server session response confirms CUSTOMER.
Even confirmation of the same principal requires a fresh account read. Concurrent
refresh calls share one promise per authentication generation; old completion/cleanup
cannot replace a newer login or verification. Network failure shows a session problem,
not a claim that logout succeeded. A current account 401 expires the session; a stale
401 cannot expire a new one or override an in-progress session verification.
No account cache or credentials are persisted in browser storage.

Mock fixtures: `customer@example.test` has a large funded ACTIVE account;
`frozen@example.test` has a FROZEN account with `250000` VND. Both use the synthetic
password `mock-only-password`. A new registration has its own account number and zero
balance. Use these only with `npm run dev:mock`; the mock banner stays visible.
Dashboard design rules: [Dashboard](design-system/finfast/pages/dashboard.md).

### Phase 4 verification — 2026-10-24

Baseline: Phase 3 commit `7630091` on `feature/BK-019`.

| Check | Result |
| --- | --- |
| Typecheck / production build | Passed |
| Production artifact isolation | Passed; no mock worker, markers or fixture data |
| Unit/integration, including Phase 2–3 regression | 154 passed in 7 files; 41 new account cases |
| Chrome browser, including Phase 1–3 regression | 96 passed; 36 new Dashboard cases across mobile/tablet/desktop |
| Browser MSW / production isolation | 6 passed |

Dashboard checks cover exact DTOs/VND, zero/funded/frozen, errors, explicit retry,
session expiry, role guard, pending logout, principal changes and late-response
isolation. Browser checks include keyboard, axe and 200% text scaling; final desktop
and mobile screenshots were visually inspected. Artifacts are ignored under
`.cache/phase4-final-browser/` and `.cache/phase4-final-mock/`.
An initial empty-response browser fixture was corrected to send JSON `null`; the
final rerun passed. No dependency changes were required.

Real backend integration is still required for account ownership/authorization,
actual session cookies and expiry, CSRF/security-filter mapping, TLS and same-origin
routing. Manual screen-reader/200% browser zoom and Firefox/WebKit checks remain
outstanding. Automated frontend/mock tests do not establish those server guarantees.

### Phase 4 P2 revalidation fix — 2026-10-24

Re-reviewed the session A/account B race: starting session refresh now synchronously
publishes `revalidating`, removes the cached principal and advances the account scope.
The existing account store aborts old reads and rejects late responses; the guard
hides the Dashboard until server confirmation. Same-principal confirmation also
loads a fresh account. Refreshes deduplicate within an authentication generation;
old responses and cleanup cannot overwrite a later login/verification. Network
failure remains a session error, not successful logout.

Final verification: typecheck and production build passed; **161 unit/integration
tests**, **108 Chrome browser tests** (including Phase 1–3 regression across three
viewports), and **6 browser MSW/production isolation tests** passed. Production
artifact isolation and `git diff --check` passed. New coverage includes deferred
session verification for A/B/401/network, hiding rendered A, account responses before
session confirmation, late account 401, logout during verification, and overlapping
verification across login generations. Initial new browser assertions had damaged
Vietnamese encoding from shell input; corrected assertions passed the final full rerun.
Artifacts: `.cache/phase4-revalidation-final-browser/` and
`.cache/phase4-revalidation-mock/`. Real backend cookie/security integration and
manual accessibility/cross-browser checks remain outstanding as described above.
