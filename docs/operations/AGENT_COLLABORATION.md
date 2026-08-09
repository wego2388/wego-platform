# Agent Collaboration

This documents the current working agreement between the two agents the owner runs day to day: Claude Code (implementer/self-verifier) and OpenAI Codex CLI (independent reviewer for Tier 1 packets, per `docs/operations/REVIEW_INTENSITY.md`). The underlying protocol is implementer/reviewer roles, not tool-specific — if the reviewing tool changes, only the names in this paragraph change.

## Roles

The implementer (Claude Code today) builds the packet, writes and actually runs its tests, gathers real evidence, and keeps `docs/execution/WEGO_EXECUTION_BOARD.md` current. It never commits, pushes, or deploys while a Tier 1 blocking finding is open, and never without the owner's explicit authorization regardless of tier.

The reviewer (Codex CLI today, Tier 1 packets only) works from a fresh context against real code and real evidence — adversarial, not confirmatory. It reports findings in the format below and re-reviews after each fix until zero blocking findings remain.

For Tier 2 packets, one agent may hold both roles.

## Evidence standard

Evidence is an actual command run against real infrastructure with actual output, not a description of expected behavior. Real PostgreSQL or Redis through Testcontainers or Compose, real HTTP calls against a running service, real concurrent threads for race conditions, a controllable clock instead of sleeps for timing claims. Reading code is not evidence.

WEGO-001 set the standard this restates: the live Compose run that proved the 429 response actually carries the right JSON body and `Retry-After` header, the concurrency integration tests that proved the login-lockout race fix under real threads rather than by inspection, and the throwaway diagnostic that empirically disproved an assumption about Caffeine's eviction behavior before that assumption was allowed to shape the design.

## Finding format

One finding per item: file and line, severity, a one-sentence defect description, and the concrete triggering scenario where one applies.

- `BLOCKING` — no commit, push, or deploy until fixed and re-verified. This restates the authorization guardrail already in `AGENTS.md`; it is not a new rule.
- `NON-BLOCKING` — fixed now, or deferred with the reasoning recorded on the execution board.

## The execution board is shared memory across sessions

Both agents read the current packet's section of `docs/execution/WEGO_EXECUTION_BOARD.md` before starting, since context does not carry over between sessions. Each review round appends a new, dated subsection to that packet's evidence log — found/fixed, new tests added, files changed, tests run, evidence, risks, next packet — in the same shape WEGO-001's six rounds used. A prior round's entry is never edited; history is append-only. A reviewer starting fresh must be able to reconstruct full context from this log alone.

## Out of scope

CLI invocation mechanics for either tool. Which agent goes first on a Tier 2 packet — implementer's judgment. This is a record of the working agreement, not a prompt script.
