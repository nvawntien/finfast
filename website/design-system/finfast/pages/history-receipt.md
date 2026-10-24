# History and Receipt — BK-019 Phase 5

Extends [MASTER.md](../MASTER.md) without changing tokens, typography or navigation.
Implements PRD FR-007/008 and UX S05/S06 using the frozen OpenAPI fields only.

- Responsive semantic transaction list; amount, text direction, type, reference,
  counterparty and committed timestamp remain visible on narrow screens.
- Receipt has a labeled article and definition list; committed status uses explicit
  text. Source/destination labels are server facts, not account ownership inferred by UI.
- VND formatting uses the shared string helper; date display explicitly labels UTC+7.
- Pagination has labeled buttons with 44px targets; page completion moves focus to
  the history heading. Cursor tokens remain opaque and private to component memory.
- Loading replaces data with a status message; errors show safe text and manual GET
  retry. Empty history is distinct from a failed or malformed response.
- Revalidation removes protected content via the existing guard and invalidates reads.
  FROZEN users retain read access. Mock and simulation notices remain visible.
- Receipt 404 combines unknown and not-permitted states. Receipt 403 is not an approved
  operation status and remains a protocol error; no private body detail is displayed.

Automated checks include keyboard pagination, focus, axe, responsive layout and 200%
text scaling. Manual screen reader/browser zoom and Firefox/WebKit remain separate.
