# FinFast Customer Web design system

BK-019 Phase 1 · 2026-10-23. This presentation baseline implements the approved
[UX inventory](../../../docs/03-design/14-ux-user-flows-and-e2e.md) without adding banking behavior.

## Skill evidence and adaptations

Used the installed UI UX Pro Max search script with:

- `"banking customer dashboard minimal" --design-system -p FinFast -f markdown`
- `"banking dashboard" --domain product -n 1`
- `"accessible" --stack react -n 2` (retry after no result for routing accessibility)

Accepted: Minimalism & Swiss Style, accessible financial dashboard, navy/blue,
semantic color pairs, generous spacing, visible focus, reduced motion and semantic test queries.
Rejected: generated sales landing pattern, certifications/social proof and conversion CTA;
these are unrelated to the approved simulator. This curated master preserves only applicable recommendations.
System fonts replace the suggested IBM Plex Sans to support Vietnamese without a runtime font request.

## Tokens and components

Canonical values live in `src/styles/tokens.css`; components use CSS Modules.

| Role | Value | Usage |
| --- | --- | --- |
| Primary / on-primary | #0f172a / #ffffff | Brand |
| Secondary | #1e3a8a | Links, active navigation, focus |
| Text / muted | #020617 / #475569 | Body / secondary text |
| Surface / background | #ffffff / #f8fafc | Cards / canvas |
| Selected | #e8eefb | Active navigation |
| Notice / text | #fffbeb / #854d0e | Persistent simulation notice |
| Danger / success | #b91c1c / #166534 | Reserved for future explicit text states |

- Spacing: 4, 8, 12, 16, 24, 32, 48px via rem tokens. Radii: 8/16px.
- Body: system sans, 16px/1.6. Headings: responsive 28–40px; no remote assets.
- Financial values in future phases use tabular figures and canonical decimal-string VND.
- Desktop >=64rem: sidebar; tablet: wrapping horizontal navigation; narrow mobile: three labeled links.
- Minimum interactive height 44px, visible focus, skip link, route-change main focus, semantic landmarks.
- No hiding essential text, forced horizontal scroll, auto-motion or color-only status.
- SimulationNotice persists on all routes. Placeholder text must not imply authenticated access,
  empty real history, a balance, a receipt result or a successful transaction.
- No forms, auth guard, account fixtures or financial actions in Phase 1.

## Verification scope

Automated browser checks target 360×800, 768×1024, 1440×900, keyboard routing,
axe WCAG A/AA checks, reduced motion and 200% text scaling. Text scaling is not
the same as browser 200% zoom. Manual browser zoom, screen reader and cross-browser
checks remain required before claiming the complete UX baseline; automated checks do not certify WCAG.
