# OpenSpec workflow

OpenSpec is the authoritative planning workflow for non-trivial changes. Active work lives under `openspec/changes/<change-name>/` with artifacts such as:

- `proposal.md` — why and what changes
- `design.md` — decisions and trade-offs
- `specs/` — delta requirements by capability
- `tasks.md` — implementation checklist

## Typical flow

1. **Propose** a change (new directory under `openspec/changes/`).
2. **Implement** against `tasks.md`, updating specs/design when reality diverges.
3. **Verify** implementation against artifacts before archive.
4. **Sync** delta specs to main: `/opsx-sync` (or manual merge to `openspec/specs/`).
5. **Archive** the change: `/opsx-archive` — moves to `openspec/changes/archive/YYYY-MM-DD-<change-name>/`.
