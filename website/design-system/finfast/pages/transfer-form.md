# Transfer Form - BK-019 Phase 6A

Historical preview-only checkpoint. [Phase 6B](transfer-state.md) supersedes the
disabled-send notice and adds explicit submission without changing form validation.

Extends [MASTER.md](../MASTER.md), PRD FR-004 and approved UX S04. Reuses tokens,
semantic labels, 44px controls, focus outlines and CSS Modules without new dependencies.

- Account context on a navy panel; input/review on a white card. Single column on
  mobile/tablet; desktop uses two columns. Amounts wrap and retain exact string values.
- Required recipient and integer VND inputs; optional note is explicitly included or
  omitted. Its exact whitespace and empty string remain distinct from omission.
- Validation summary receives focus and links to invalid fields. Preview heading
  receives focus; returning to edit focuses recipient without changing entered values.
- A persistent notice identifies preview-only behavior. The send button is disabled
  with visible explanation; no success, transaction reference or financial outcome is shown.
- Reuse account GET/state store for loading/errors/retry/FROZEN. Frozen users keep
  read links; no form is shown. Prechecks use current account data but do not certify
  recipient existence, authorization or funds at commit time.
- Draft is local only and is discarded when the guard unmounts on revalidation,
  logout, expiry or role change, and when leaving the page.

Phase 6B boundary: `draft.ts` returns an immutable preview of only TransferRequest
fields. It creates no intent ID/key, request, callback, outcome or storage record.
Phase 6B must implement explicit submit, scoped payload/key ownership, cookie/CSRF
transport and unknown-outcome recovery before enabling send. No fake state machine
or financial API call is supplied in this phase.
