# FinFast Customer Web — BK-019 Phase 1

Frontend foundation only. All banking screens are explicit placeholders. There is
no authentication, API call, mock financial data, account balance or transaction
submission. Customer routes are public previews until session integration is implemented.
Only simulated funds are in scope.

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
No backend, database, credentials or `.env` is needed for Phase 1.

## Build and checks

```sh
npm run typecheck
npm run build
npm run preview
```

Build output is `dist/` (ignored). `preview` is a local build preview, not a production
deployment. Future static hosting must rewrite application routes to `index.html`
for deep links; no cloud or backend configuration is changed here.

For browser tests, install Playwright Chromium once if unavailable:

```sh
npx playwright install chromium
npm test
```

Alternatively use an existing Chrome installation in PowerShell:

```powershell
$env:PLAYWRIGHT_CHANNEL = 'chrome'
npm.cmd test
```

Stop any manually started server on port 3000 before tests. Playwright starts/stops
its own local dev server. `npm run check` runs build (including typecheck) and browser
tests. Tests cover direct routes, 404, root redirect, simulation notice, no API
requests, runtime exceptions, active navigation, keyboard skip link/route focus,
browser back, reduced motion, reflow and axe checks at 360×800, 768×1024 and 1440×900.
The 200% text-scaling check is not a substitute for manual browser 200% zoom.
Trace files for failures are in ignored `test-results/`.

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
  tsconfig.json / vite.config.ts / playwright.config.ts
  design-system/finfast/MASTER.md
  src/
    main.tsx
    app/
      router.tsx
      layouts/CustomerLayout.tsx + .module.css
    components/
      Navigation.tsx + .module.css
      SimulationNotice.tsx + .module.css
      PlaceholderPage.tsx + .module.css
      Icon.tsx
    styles/tokens.css + global.css
  tests/e2e/foundation.spec.ts
```

CSS Modules isolate components; CSS variables own design tokens. Native links and
React Router declarative routing provide keyboard navigation. Layout owns shared
landmarks and focus management. `PlaceholderPage` is intentionally shared until
actual feature behavior needs separate modules. Do not create speculative API wrappers
or unused folders. Future phases introduce `src/features/{auth,account,transfers,history,receipts}`,
`src/api`, `src/lib` and isolated test mocks as behavior is implemented.

| Route | Screen |
| --- | --- |
| `/` | Redirect to `/customer` |
| `/register` | S01 registration placeholder |
| `/login` | S02 login placeholder |
| `/customer` | S03 overview placeholder |
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
Phase 1 does not establish financial, authentication or end-to-end acceptance.

Source inspection on 2026-10-23 found no banking HTTP controllers in this checkout.
The approved `/api/v1` auth, account, transfer, outcome-resolution, history and
receipt contracts are integration specifications, not available endpoint evidence.
Later phases must verify provider implementations before connecting. Preserve
HttpOnly server sessions, CSRF, server authorization, decimal-string VND and original
idempotency key/payload recovery; never infer success from a placeholder or timeout.
Keep any later HTTP test fixtures separate from the real client and opt-in; no mock
adapter is installed now. Admin UI belongs to BK-020.

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
