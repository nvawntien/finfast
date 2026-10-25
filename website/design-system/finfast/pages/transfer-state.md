# Transfer state machine - Phase 6B

Historical 6B checkpoint. The recovery behavior below is superseded by Phase 6C
in the [website README](../../../README.md#phase-6c---transfer-recovery).

Extends [Transfer form](transfer-form.md) and [MASTER.md](../MASTER.md). Approved
FR-006 and UX uncertainty rules govern behavior. No new financial outcome enums.

| Response | Display/state |
| --- | --- |
| Awaiting request | UI `submitting`; no final outcome |
| 200 with validated COMPLETED and reference | COMPLETED, receipt/history links |
| 422 with validated FinancialRejectionResponse | REJECTED, text reason |
| 202 PROCESSING / UNKNOWN | Same non-final outcome; block replacement |
| 409 conflict | UNKNOWN plus conflict explanation; preserve original intent |
| Timeout/network/503/protocol/abort | UNKNOWN; no success/failure claim |

Confirmation is explicit. Payload/key are frozen together before the first await.
Store-level locking prevents double submit independently of disabled/removed controls.
Only an explicit new attempt after COMPLETED/REJECTED releases the form and generates
a fresh key. Completion re-reads account; History reads fresh data on navigation.

TransferProvider lives above routes and guards. It retains intents per server userId
in memory; no password/cookie/CSRF is stored. Scope change hides private UI and cancels
the request, retaining UNKNOWN for the original owner. Late responses cannot restore
old UI or expire another principal. Reauthentication of that owner shows the unresolved
intent, not a blank replacement form. Outcomes receive heading focus and status text.

Phase 6C boundary: no outcome lookup, resend, polling, durable browser persistence or
cross-tab coordination. UI warns against reload/close and beforeunload requests browser
confirmation while unresolved. This is not durable recovery: reload/crash can lose
the in-memory key. Actual backend atomicity/idempotency/session guarantees need integration.

## Phase 6C recovery UI

- UNKNOWN/PROCESSING exposes explicit "Tra cứu kết quả", never a transfer resend.
- While resolving, remove competing controls and focus/announce the pending heading.
- Reload reopens an uncertain intent only after principal verification and authenticated
  decryption; it never restores a final outcome from browser storage.
- Confirmed completion keeps receipt/history links; confirmed rejection permits a
  deliberately new intent. All unresolved/error states prohibit replacement.
- Cross-tab contention explains that another tab is working. Corrupt/unavailable
  storage blocks the form and offers a storage recheck, without deleting records.
- Keep the original request and browser data when unresolved. Do not infer failure
  from lookup errors, absent results, timeout or 422 outside the resolve contract.
