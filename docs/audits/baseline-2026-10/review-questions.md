# Review Questions

Every item below is intentionally non-approved. See review-decisions.md for the required decision log.

| Question | Status | Evidence and impact |
|---|---|---|
| Who owns the other worktree's untracked Transfers, Idempotency, test, benchmark, evidence, and OpenSpec files, and what commit should contain them? | UNRESOLVED | They are absent from e3dc6ef in this worktree. Their provenance determines whether they are discarded local work, pending reviewed work, or something else. |
| Should the approved History module be implemented as a separate production root, or should an approved amendment place it in Ledger? | UNRESOLVED | The architecture has a History capability; tracked source has Ledger-local query services and no history production package. |
| What is the reviewed disposition of API-ERR-01 and any transport amendment? | UNRESOLVED | The frozen OpenAPI is the only tracked external contract. The prior local amendment file is not baseline evidence. |
| Who owns frontend delivery and which stack will implement the approved browser flows? | UNRESOLVED | No frontend package or browser E2E suite is tracked. Selecting a framework needs an owner decision. |
| Who provides EV-001/EV-002 evidence and authorizes the isolated EV-003 environment? | UNRESOLVED | The decision register keeps EV-001/002 pending and EV-003 awaiting measurement. No tracked benchmark harness exists. |
| Should formally migrated capabilities use OpenSpec as their maintained behavioral authority while durable documents retain their own authority? | PROPOSED | The proposed RD-07 policy requires explicit registry transfer and human approval, preserves operative legacy behavior before transfer, and prevents duplicate maintained behavioral specifications. |
| Should a first pilot be selected now? | PROPOSED | Recommended disposition: defer pilot selection until governance and OpenSpec initialization are reviewed. Customer-account-overview remains a candidate, not a selected or migrated pilot. |
| Should the decision register remain the sole maintained technical decision authority during initial migration? | PROPOSED | RD-09 recommends retaining the register and adding no ADR corpus or decisions.md without later explicit approval. |

The audit can establish tracked-file presence. It cannot resolve ownership, hosted CI state, external requirements, deployment behavior, or final release acceptance.
