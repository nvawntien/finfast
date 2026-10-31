# OpenSpec workflow

OpenSpec is the planning workflow for non-trivial changes. Read [the documentation
index](../README.md) for the approved V1 authorities relevant to the change.
Active work lives under `openspec/changes/<change-name>/` with artifacts such as:

- `proposal.md` - why and what changes
- `design.md` - decisions and trade-offs, when needed
- `specs/` - delta requirements by capability
- `tasks.md` - implementation and verification checklist

## Branch and session setup

For each OpenSpec change, create a branch named `change/<change-name>` from the
current `develop` baseline before starting implementation. Keep its artifacts
and implementation on that branch. Resume an existing change on its own branch.
For example:

```sh
git switch -c change/complete-customer-onboarding develop
```

Inspect the branch, worktree and local changes first. Concurrent sessions use
separate branches and worktrees, with one active change per session. Record the
base commit and prerequisite changes in the change's artifacts. Verify that the
required dependency commits are present before relying on their implementation.

## Typical flow

1. **Propose** with `$openspec-propose`. The skill scaffolds the change through
   `openspec new change <change-name>` and follows the CLI artifact instructions.
   Never create a change directory by hand. Cite the approved requirement
   sources and distinguish current implementation from intended behavior.
2. **Review** the proposal, delta specs, design when present, and tasks before
   implementation. Resolve material ambiguity, prerequisites and ownership of
   shared providers or configuration. Validate with
   `openspec validate <change-name> --strict`; structural validation does not
   establish implementation correctness.
3. **Implement** with `$openspec-apply-change` after the user requests apply.
   Work against `tasks.md`. Use `$openspec-update-change` to reconcile artifacts
   when implementation reveals a necessary planning change.
4. **Verify** implementation against the artifacts using the
   [verification strategy](../verification/strategy.md). Record executed
   commands, revision, environment, results and limits in the change's Issue.
   Update task checkboxes only for work actually completed.
5. **Sync and archive** with `$openspec-archive-change`, which assesses delta
   specs and offers to sync them before moving the change to
   `openspec/changes/archive/YYYY-MM-DD-<change-name>/`. Use
   `$openspec-sync-specs` when a separate sync is needed before archive.
