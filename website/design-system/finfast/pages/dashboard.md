# Customer Dashboard — BK-019 Phase 4

Extends [MASTER.md](../MASTER.md), retaining its palette, system fonts, spacing,
breakpoints, focus and motion tokens. No master decisions are overwritten.

## UI UX Pro Max application

Read the installed skill and master; searched `dashboard loading empty error states`
in the UX domain and `async request cleanup race` in the React stack. Applied explicit
loading/error feedback and effect cleanup. Existing session/client architecture remains
the source for request handling; no new animation or component dependency.

## Layout and content

- One title/greeting plus a keyboard-accessible refresh button (44px minimum).
- Navy balance panel with tabular figures, white account metadata panel; stacked below
  48rem. Long account numbers and maximum VND values wrap instead of overflowing.
- Available balance is prominent; account balance is a separate labeled server value.
  Account Number, CUSTOMER type, ACTIVE/FROZEN and currency use only AccountOverview fields.
  Greeting comes from SessionInfo, not an invented account owner field.
- ACTIVE/FROZEN have explicit text. FROZEN retains read access and history navigation;
  the transfer shortcut is omitted. Existing sidebar routes remain previews, not permissions.
- Loading uses static skeletons and a status announcement, without fabricated amounts.
  Empty/malformed payloads are errors, never a successful account-less or zero-balance state.
- Refresh clears the old snapshot while loading. Errors expose a retry GET action and
  no previous account data. No transactions, charts, financial actions or invented receipts.
- SimulationNotice remains visible; the separate mock-mode banner remains unchanged.

## Verification

Browser suite covers 360×800, 768×1024, 1440×900; zero/funded/frozen states, axe,
keyboard, 200% text scaling, errors, expiry and pending requests across logout/principal
changes. Actual browser zoom and manual screen-reader acceptance remain separate.
