# Requirements Traceability and Documentation-to-Code Gaps

## Evidence classes

- **APPROVED REQUIREMENT:** canonical documented intent, contract, or decision.
- **TRACKED SOURCE:** source, migration, configuration, or test definition exists at e3dc6ef.
- **HISTORICAL EVIDENCE:** dated execution claim in tracked documentation, not rerun here.
- **NOT COMMITTED:** present only in the other worktree's local overlay.
- **NOT RUN:** no current execution evidence from this audit.

## Findings

| ID | Approved behavior | Tracked baseline evidence | Correct gap assessment |
|---|---|---|---|
| G-01 | Requirements and decisions are carried by existing canonical documents. | 29 tracked Markdown documents, frozen OpenAPI, public module contracts, and V1/V2 migrations. | Before a capability transfer, the existing documents remain authoritative. After explicit review, OpenSpec may become the maintained behavioral authority for that capability only; it must not replace contract, architecture, or decision authority. |
| G-02 | Integral VND, safe balances, immutable facts, and transactional posting are required. | VND types, Accounts and Ledger source, migrations, append-only guards, and relevant test definitions. | Partial source/test-definition coverage exists. Production composition and current execution are NOT RUN. |
| G-03 | Customer transfers require paired posting, scoped idempotency, final outcomes, and no false success. | Ledger has TransferPostingService; Idempotency has the public RequestOutcomes API and V1 schema support. | Transfer orchestration, Idempotency provider/persistence, routes, outcome resolver, and transfer tests are NOT COMMITTED. Do not count the local overlay. |
| G-04 | Administration allocation, status change, audit, and frozen transport semantics are required. | Allocation/status application services, audit repository, conditional inspection reads, and test definitions. | Write HTTP adapters and production provider composition are absent. Application-service presence must not be mistaken for REST availability. |
| G-05 | History and receipts require authorized, stable, immutable read behavior. | Ledger-local history/receipt query services, JDBC fact adapters, cursor codec, and test definitions. | No History module root, production composition, authorization adapter, history route, or receipt route is tracked. Owner decision on module placement remains open. |
| G-06 | Registration provisions a Customer and account atomically. | RegisterCustomer, registration persistence/password adapters, and registration test definitions. | No registration controller or tracked production composition is present. The permitted security matcher is not a route implementation. |
| G-07 | The frozen OpenAPI defines 15 operations. | Six MVC handler methods plus login/logout security configuration are tracked. | Registration, transfer, outcome resolution, history, receipt, allocation, and status routes are missing. auth and Administration gate activation are also unproven. |
| G-08 | UX/browser delivery and EV-003 performance acceptance are approved requirements. | UX and test-strategy documents, Compose support, and an approved benchmark method. | No frontend/browser E2E, benchmark harness, or EV-003 measurement is tracked. |
| G-09 | Banking APIs must be guarded by the auth chain. | Auth-profile security configuration and activation guard plus test definitions. | Security foundation is source evidence. Real provider configuration, HTTPS deployment, browser behavior, and release acceptance are NOT RUN. |
| G-10 | Traceability must retain canonical FR/UC/BR/AC/CONS identifiers and evidence. | Requirements traceability, test strategy, and decision register. | Historical runs are not a substitute for a revision-bound AC mapping. Keep acceptance open until a current run is captured. |
| G-11 | Bootstrap must be protected, one-time, and never exposed as ordinary GENESIS/minting. | Bootstrap use case, writer, conditional configuration, migration invariants, and test definitions. | No production provider/operator composition is tracked. The absence of a public bootstrap controller is aligned with the rule, not an HTTP gap. |

## Local-only provenance record

The prior reports referenced a Transfers tree, Idempotency provider, transfer and Idempotency tests, benchmark scripts, local evidence documents, an API amendment file, and OpenSpec files. They came from the user-described untracked overlay in /home/bmngxn/Projects/finfast, not from the tracked baseline. Their absence is a baseline fact; their owner and intended integration are unresolved.

## Verification limitations

No application test, Maven build, Docker/Compose startup, benchmark, browser flow, or persistent environment command was run during this correction. Historical pass counts in tracked documentation remain historical evidence and must not be reported as this audit's result.
