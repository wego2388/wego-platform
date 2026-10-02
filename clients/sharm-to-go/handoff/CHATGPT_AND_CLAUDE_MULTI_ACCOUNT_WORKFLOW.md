# Sharm To Go — Codex and Claude multi-account workflow

The repository, Git history, execution board and evidence ledger are shared
memory. Chat history is not. This workflow lets a new Claude account and Codex
work on the same product without duplicate implementation or self-review.

## Roles

| Role | Default holder | Responsibility |
|---|---|---|
| Owner/authority | Muhammad | Activates a mission; approves business facts, commit/push/deploy and production actions |
| Implementer | Claude new account, unless reassigned | Scopes one packet, writes code/tests/docs, runs real gates and records evidence |
| Independent reviewer | Codex, unless roles are swapped | Reviews Tier 1 work from fresh context and executable evidence; does not rubber-stamp |

Codex may implement a packet when the owner assigns it explicitly. If so,
Claude becomes the independent reviewer for Tier 1 work. The same agent/context
must not both implement and provide the independent approval.

## Worktree ownership

- Exactly one named agent owns writes to one active worktree at a time.
- The reviewer inspects from a separate clean worktree or a read-only view.
- Never let both accounts edit the same branch concurrently.
- Do not switch branches, pull, merge, rebase, reset, stash, clean or delete a
  worktree to solve an ownership conflict. Stop and reconcile first.
- Uncommitted files belong to their current owner until a written checkpoint
  assigns them. Unknown changes are preserved, never discarded.

## Packet protocol

1. Confirm an explicit owner instruction and exactly one active packet.
2. Record implementer, reviewer, worktree, branch, base commit, scope, tier,
   affected modules, acceptance criteria and test commands.
3. The implementer takes a baseline status and runs the proportionate pre-change
   gate. A failing baseline is evidence, not permission to delete or bypass it.
4. Implement only the named packet. New adjacent ideas go into the residual-risk
   or future-work list unless they are required for correctness.
5. Update phase boxes only when implementation and stated evidence both exist.
6. Append evidence; never rewrite prior review history.
7. For Tier 1, the independent reviewer reproduces critical behavior and reports
   each finding with file/line, severity, defect and trigger.
8. The implementer fixes and re-verifies. Review repeats until zero blocking
   findings remain.
9. Commit, push, merge, deploy and production work remain separate owner gates.

## Tier 1 triggers

Any authentication/authorization/permission change, payment or money movement,
database migration, client-isolation boundary or real customer PII is Tier 1.
Ambiguity defaults to Tier 1. Real PostgreSQL, real concurrency, real HTTP and
real browser evidence are required where the claim depends on them.

## Checkpoint required before handing accounts over

The implementing account writes a dated checkpoint containing:

```md
# YYYY-MM-DD — packet checkpoint

- Mission / packet / status:
- Implementer / reviewer:
- Worktree / branch / HEAD / base:
- Owner authorization received:
- Files changed:
- Behavior completed:
- Tests actually run and exact results:
- Blocking findings and disposition:
- Uncommitted or untracked owner changes to preserve:
- External state touched (normally none):
- Next exact unchecked gate:
- Forbidden next actions without new authorization:
```

The receiving account must verify the checkpoint against Git and the files. It
does not repeat completed work merely because its chat context is new.

## Current Sharm To Go assignment

- Mission status: `ACTIVE` as of 2026-09-30 (owner explicit reactivation; see
  `../ROADMAP_AR.md` Phase 0). This file's process/checkpoint format below
  still applies — only this status line was stale.
- Implementer: whichever Claude account is working in
  `worktree-wego-010a-0r-isolation`. Only that account writes to this
  worktree.
- No deploy is queued.

