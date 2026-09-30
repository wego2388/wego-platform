# Sharm To Go — agent handoff index

This directory is the single entry point for a new Codex, ChatGPT or Claude
session. It exists so account context is never treated as project memory.

## Current status

- **Mission:** WEGO-010 — Travel Marketplace / Sharm To Go.
- **State:** `ACTIVE` as of 2026-09-30 (owner reactivated it explicitly in this
  worktree; see `../ROADMAP_AR.md` Phase 0).
- **Note:** `2026-09-29_NEW-AGENTS_START-HERE.md` below describes the paused
  state at the moment it was written — read it for the preserved-checkout
  evidence, not for current permission. `../ROADMAP_AR.md` is now canonical
  for current status and phase order.
- **Scope of reactivation:** implementation in this worktree only. WEGO-016
  (Safari Tours Sharm) remains active in its own separate worktree — per
  `AGENTS.md`, the single-active-packet rule is scoped per worktree.

Start with [`../ROADMAP_AR.md`](../ROADMAP_AR.md), then
[`2026-09-29_NEW-AGENTS_START-HERE.md`](2026-09-29_NEW-AGENTS_START-HERE.md)
for the preserved checkout snapshot.

## Reading order

1. `AGENTS.md` at the repository root.
2. `docs/ENGINEERING_CONSTITUTION.md`.
3. `docs/operations/REVIEW_INTENSITY.md`.
4. `docs/operations/AGENT_COLLABORATION.md`.
5. This directory's dated start-here file.
6. [`CHATGPT_AND_CLAUDE_MULTI_ACCOUNT_WORKFLOW.md`](CHATGPT_AND_CLAUDE_MULTI_ACCOUNT_WORKFLOW.md).
7. [`../delivery/README.md`](../delivery/README.md) and
   [`../delivery/00_CURRENT_STATE.md`](../delivery/00_CURRENT_STATE.md).
8. [`../CLAUDE_HANDOFF.md`](../CLAUDE_HANDOFF.md) for technical history.
9. The WEGO-010-A section of `docs/execution/WEGO_EXECUTION_BOARD.md`.

## Document authority

| Need | Authoritative source |
|---|---|
| Current permission to work | Execution board plus an explicit owner instruction |
| Current checkout and divergence | Dated start-here file, then live Git inspection |
| Agent ownership and review roles | Multi-account workflow and repository collaboration policy |
| Ordered future delivery | `delivery/README.md` and phase files |
| Proven results | `delivery/07_ACCEPTANCE_AND_EVIDENCE.md` plus execution-board evidence |
| Product positioning | `BRAND_AND_GROWTH_STRATEGY.md` |
| Cross-surface request design | `CONVERSION_DELIVERY_PLAN.md` |
| Historical technical detail | `CLAUDE_HANDOFF.md`, `EXECUTION_PLAN.md`, `TECHNICAL_EXECUTION_PLAN.md` |

Older plans and research files are retained because they contain owner
decisions, commercial provenance and test evidence. They are reference material,
not permission to bypass the current board or the delivery workbook.

