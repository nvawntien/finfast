# Documentation Lifecycle

**Status:** PROPOSED. This workflow preserves FinFast's current approvals and does not require a new proposal, ADR, or specification for ordinary conforming implementation work.

| Workflow | Initiator and approval authority | Artifacts and review | Verification and Git/PR integration | Authority impact |
|---|---|---|---|---|
| A. Introduce a new capability | Product/module owner starts. Existing product/technical authority approves scope as required. | Product scope/requirements first; then an OpenSpec change uses proposal, specs, design, and tasks if the capability is approved for OpenSpec. | Review requirements, module/API/data impact, and tests; link work to issue/PR. Run applicable existing checks. | New behavioral authority is OpenSpec only after an explicit capability transfer. |
| B. Change existing behavior | Owning module/product owner starts. Existing authority decides whether it is a routine conforming fix or requires amendment. | For migrated behavior, create an OpenSpec change. For unmigrated behavior, use existing requirement/change control and preserve IDs. | Review behavioral delta, implementation, tests, evidence, and affected durable documents. | A change does not silently change frozen contracts or decisions. |
| C. Change a frozen REST contract | API owner and affected owners start. Existing API/Tech Lead authority approves. | Contract amendment, compatibility analysis, OpenAPI YAML change, API explanation, traceability, and decision-register update if a decision is required. | Validate YAML/reference checks and real adapter tests where applicable; review breaking/client impact in PR. | YAML remains the sole REST authority. OpenSpec change may reference, never replace, it. |
| D. Change architecture/module boundaries | Module owner starts. Tech Lead and affected owners approve an amendment. | Architecture/module guide, Java API/source, tests, data design, and decision register when durable rationale changes. | Run architecture/capability tests and relevant integration evidence; review all affected owners. | Durable architecture and contracts remain authoritative. |
| E. Record an architectural decision | Any owner proposes; designated existing authority approves. | Update the canonical decision register with context, choice, alternatives actually considered, scope, and status. | Link the approving PR/review and any required implementation/verification evidence. | No standalone ADR unless RD-09 authorizes that separate format. |
| F. Update as-built architecture | Implementing owner or documentation owner starts after source change. Tech Lead reviews material boundary claims. | Update maintained as-built page and any affected durable docs; preserve target architecture separately. | Cite tracked paths/symbols and label composition/runtime limits. Run architecture and documentation checks as relevant. | This corrects factual status, not approved target intent. |
| G. Record tests and acceptance evidence | Test owner records after execution. Release/project authority handles acceptance. | Verification evidence index with revision, exact command, environment, result, limitations, and linked AC/CONS where applicable. | Preserve CI URL or local evidence location; do not mark release gate complete without required authority. | Test results do not change behavioral, API, or decision authority. |
| H. Migrate a legacy capability | Documentation owner and capability owner start. RD-07 policy and capability review approve transfer. | OpenSpec main spec, migration registry entry, successor/provenance links, and narrowly needed durable navigation updates. | Run OpenSpec strict validation and documentation checks; review identifier mapping and authority transfer. | OpenSpec becomes behavioral authority for that capability only when registry status is AUTHORITY_TRANSFERRED. |
| I. Correct inaccurate documentation | Any contributor starts. Factual corrections use responsible owner review; requirement/contract changes use existing authority. | Correct current source/status statement, retain or annotate historical evidence, and update audit/traceability links where needed. | Cite tracked source or execution evidence and run documentation checks. | Do not silently reinterpret approved requirements or erase historical records. |
| J. Archive an OpenSpec change | Change owner starts after implementation/review. Existing review authority accepts the change. | Confirm proposal/specs/design/tasks status, update main specs and durable docs as required, then use the installed OpenSpec archive flow. | Run openspec validate and relevant project checks before archiving; archive in the reviewed PR. | Archive records completed change history. It is not a release-acceptance action. |

## Lightweight decision rule

No new OpenSpec change, ADR, or governance record is required for a code or documentation correction that already conforms to approved behavior and does not alter a durable authority. Create the smallest artifact set that expresses the real change:

- An ordinary implementation fix: source, focused tests, and any factual as-built/status correction.
- A behavioral delta for a migrated capability: one OpenSpec change.
- A frozen contract or module-boundary change: existing formal amendment path plus affected artifacts.
- A durable technical choice: the existing decision register.

## Review checklist for every lifecycle path

1. Identify current behavioral, contract, module, data, and decision authorities.
2. State whether the capability is unmigrated, in transfer, or AUTHORITY_TRANSFERRED.
3. Preserve legacy identifiers and identify source/composition/execution/release status separately.
4. Confirm local links, OpenSpec validation when used, and applicable project checks.
5. Use a PR for shared review; do not represent local state as a team-wide lock.
