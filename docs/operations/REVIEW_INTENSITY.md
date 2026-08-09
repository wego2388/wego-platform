# Review Intensity

`docs/ENGINEERING_CONSTITUTION.md` §2 requires review effort proportionate to risk. This defines what that means mechanically: which packets need heavy adversarial review before commit, and which need one verified pass.

## Tiers

**Tier 1 — heavy adversarial review.** Triggered by any of: authentication, authorization, session, or permission logic; payments or money movement; a database migration (schema or data); a multi-tenant or client-isolation boundary; real client PII.

Process: an independent reviewing agent works from a fresh context against real, executable evidence — not a read-through of the diff. Findings use the format in `docs/operations/AGENT_COLLABORATION.md`. The implementer fixes and re-verifies; review repeats until zero blocking findings remain. No commit before that.

**Tier 2 — light, single-pass review.** The default for everything else. The implementer self-verifies with real evidence, one review pass checks correctness and regressions, and the packet commits once that pass has no blocking findings.

A Tier 2 packet is escalated to Tier 1 mid-review if it turns out to touch a Tier 1 category the original scope missed.

## Determining tier

Tier is decided when the packet is scoped, before implementation starts — by category match, not by size or perceived difficulty. A one-line permission check is Tier 1; a large, purely additive UI change is Tier 2. A packet spanning multiple categories takes the highest tier any part of its scope triggers. Ambiguous cases default to Tier 1.

## Tier does not replace existing tests

Regardless of tier, any change touching a file whose existing tests encode security or correctness assertions (for example `web/apps/erp/test/Login.spec.ts`) must still pass with those assertions unmodified. Tier controls how much *new* scrutiny a packet receives, not whether existing regression coverage runs.

## Worked example: WEGO-001 (Tier 1)

WEGO-001 (identity & access) is the canonical in-repo example. Six independent review rounds ran before commit and found, in order: a transaction-atomicity gap, a lost-update race under concurrent logins, a bootstrap-admin race, an incomplete rate-limit contract, a targeted-account-lockout design flaw, and a caching-library scan-resistance assumption that was empirically disproven before being relied on. Every fix is proven by a test that fails without it — Testcontainers-backed PostgreSQL, real concurrent threads, live Compose runs against the built image, controllable-clock timing proofs — not by re-reading the code. `docs/execution/WEGO_EXECUTION_BOARD.md`'s WEGO-001 evidence log is the literal record to imitate: each round is a new, dated, append-only subsection naming what was found, how it was fixed, and how the fix was verified.

## Out of scope

This document does not prescribe which tool plays the Tier 1 reviewer role — see `docs/operations/AGENT_COLLABORATION.md` for the current agents and their roles. It does not set a tier for pure documentation edits; that stays a judgment call. It does not retroactively re-tier packets already complete.
