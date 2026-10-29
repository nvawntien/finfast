# FinFast Baseline Audit Reconciliation

**Authoritative baseline:** chore/repo-knowledge-audit at e3dc6ef8deccc09946b115565171c4c3c0d2855a.

## Outcome

The prior audit mixed a local, untracked overlay from /home/bmngxn/Projects/finfast with the committed baseline. This correction uses only tracked files from the intended worktree.

- Retained: approved discovery, PRD, functional specifications, frozen OpenAPI, module contracts, architecture, decision register, shared VND/error code, schema, partial application services, and tracked test definitions.
- Corrected: Transfers, an Idempotency provider, transfer HTTP routes, transfer/idempotency tests, benchmark tooling, OpenSpec scaffolding, and local evidence documents are not committed baseline assets.
- Clarified: application services can exist without a production HTTP adapter or provider composition. Registration, history, receipt, allocation, status change, and bootstrap have tracked source but remain incomplete end-user/runtime capabilities.

## Baseline gaps

The frozen OpenAPI defines 15 operations. The baseline declares six MVC handlers and login/logout security handling. It has no tracked routes for registration, transfer submission, request-outcome resolution, history, receipt, allocation, or status change.

The baseline includes financial source and concurrency test definitions for Accounts, Ledger, GENESIS, reconciliation, and Administration. It has no tracked Transfers application layer, Idempotency provider, durable outcome integration, or benchmark harness. No execution was performed for this correction, so historical test claims remain documentary evidence only.

The approved architecture expects nine business modules. Tracked production roots have no Transfers or History package, and Idempotency is public API only. This is a target-versus-as-built gap requiring owner review, not evidence that the local overlay is integrated.

## Migration readiness

OpenSpec is not tracked at this baseline. After an explicit capability transfer, its approved main specification becomes the maintained behavioral authority for that capability. Before transfer, the approved legacy behavioral documents remain authoritative. OpenSpec must not duplicate or supersede frozen OpenAPI semantics, architecture policy, data ownership, or decision authority.

Customer-account-overview remains a candidate for a later pilot because UC-ACC-01 and GET /api/v1/accounts/me form a bounded read path. Pilot selection is deliberately deferred until governance and OpenSpec initialization are reviewed. It is not implemented, verified, accepted, or migrated by this audit.

All required human decisions are listed as PROPOSED or UNRESOLVED in review-decisions.md. No decision is approved by this audit.
