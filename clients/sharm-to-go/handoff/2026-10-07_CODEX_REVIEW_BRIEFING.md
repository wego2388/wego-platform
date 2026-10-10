# Briefing for Codex (independent Tier 1 reviewer) — Sharm To Go, 2026-10-07

Read this first — it orients you fast instead of making you reconstruct
context from `docs/execution/WEGO_EXECUTION_BOARD.md`'s full WEGO-010-A
history (3000+ lines). Follow the links below only for what you actually
need to verify; don't re-read everything.

## What Sharm To Go is, in one paragraph

A travel-marketplace client inside this monorepo (`products/travel-
marketplace`, composed for the `clients/sharm-to-go` profile). The owner,
Mohamed, is an **intermediary, not an asset owner** — he doesn't own boats
or vehicles; he takes a customer request, arranges fulfillment himself, and
either confirms it instantly (`confirmationType=INSTANT`) or reviews it
first (`STAFF_REVIEW`) per service. That's a deliberate, owner-confirmed
business-model fact, not an assumption — an earlier session wrongly assumed
he owned fixed-capacity assets and proposed a slot/capacity system; he
corrected that directly, and it was retracted with no code change. Don't
rebuild that.

## Why this file exists right now

The earlier design/catalog round was Tier 2. The owner subsequently
authorized the Tier 1 sales-pause implementation. It is now implemented
and independently reviewed with no open findings; the evidence and
operating behavior are recorded in `2026-10-07_SALES_CONTROL_REVIEW.md`.
Changes are local and uncommitted; this is not a deployment record.

## Tier 1 sales-pause review — completed locally

**A sales-pause control** stops new WEBSITE/MOBILE requests while leaving
catalog browsing, tracking and existing staff operations available. V9
seeds the flag open, grants the dedicated `travel-sales:manage` permission
to platform-admin, and stores private change history. The request path
uses a shared database lock; manager changes use an exclusive lock and
expected-version conflict checks. Existing idempotent retries still work.
The public status exposes only `requestsOpen` with no-store caching.
Independent review reproduced and verified one UI response-ordering fix;
zero open BLOCKING or NON-BLOCKING findings remain. Read the review evidence
before any commit/deployment decision; owner authorization is still needed
for those actions under AGENTS.md.

## What changed this round (2026-10-06/07), if you need it for context

All Tier 2, all self-verified against a fresh throwaway Postgres+backend
stack per change (never shared/production), each with its own evidence
doc in `clients/sharm-to-go/handoff/` dated 2026-10-06:

- Catalog merged to 41 real trips (24 Safari-approved + 17 kept from the
  old 37), with a checkpointed import pipeline
  (`clients/sharm-to-go/scripts/import_catalog.py`) driving the real staff
  API. See `2026-10-06_CATALOG_IMPORT_PIPELINE.md` and the interruption-safety
  update `2026-10-07_CATALOG_RECOVERY.md`. State must be retained; a lost
  create response is reconciled explicitly rather than blindly retried.
- ERP given Sharm To Go's own brand identity (it was importing the generic
  shared platform palette — the literal cause of the owner's design
  complaint). See `2026-10-06_ERP_BRAND_IDENTITY.md`.
- `/find-my-trip` trip-finder page added to the site (the owner's "the
  site should be a tourist guide" request).
- `booking-preview.vue`/`design-system.vue` now 404 in production builds
  — closes a real public-crawler exposure gap.
- **The one finding worth your attention even though it's Tier 2:** a real
  axe-core WCAG 2.1 AA pass found 33 genuine color-contrast violations
  across the site (not noise — 5 distinct root causes, full sRGB math in
  `2026-10-06_ACCESSIBILITY_COLOR_CONTRAST.md`), fixed across 16 files,
  re-verified to 0 violations. Worth a skim if you're ever asked to review
  `web/apps/sharm-to-go-site/app/assets/css/main.css` or
  `content/categoryAccents.ts` — the comments there explain the exact
  contrast math behind every color choice.
- Optional GPG backup encryption added
  (`scripts/sharm-to-go-backup.sh`/`-restore-drill.sh`).

Full formal status (what's shipped vs. what's still pending, including
items only the owner can decide): `clients/sharm-to-go/ROADMAP_AR.md`,
Phase 3D specifically. That file is the single source of truth for
ordering and status — this briefing is orientation, not a replacement
for it.

**Publication evidence correction, 2026-10-07:** the importer deliberately
stops at APPROVED and never calls publish. Placeholder media does not itself
block the current backend publish operation: the check rejects an empty
media collection, not a non-empty placeholder or pending-rights record.
Real-photo/rights readiness remains an open owner/content decision. This
correction adds no publication gate and does not expand the sales-pause
review scope.

## Deliberately NOT done this round — don't flag these as missed

- **Real photo upload.** Sharm To Go still has no managed upload workflow;
  `ServiceMedia.assetReference` remains an opaque placeholder at this phase.
  **Reference correction, 2026-10-07:** the advanced Safari worktree at
  `/home/wego/wego-safari-hardening` does implement validated uploads,
  private filesystem storage, image variants and explicit rights approval
  (`MediaUploadService.kt`, `MediaController.kt`). Its design is useful
  reference material; it is not installed in this client's product and
  must be adapted rather than importing Safari's operating model. Actual
  owner-supplied photos and usage-rights evidence remain pending.
- **Russian/Italian UI translation.** Explicitly deferred by the owner
  (2026-10-06) over translation-quality risk — not started, not forgotten.
- **Lighthouse mobile performance.** LCP is 4.4s on Lighthouse's throttled
  mobile/slow-4G preset against the design spec's documented <1s target.
  Recorded as an open, honestly-flagged gap — no fix attempted yet, no
  root cause isolated (not one single render-blocking resource or
  oversized asset; likely needs deeper bundle/hydration work).
- **CI wiring for the new accessibility suite.** It runs for real locally
  (`e2e/tests/sharm-to-go-accessibility.spec.ts`) but isn't in
  `.github/workflows/ci.yml` yet — that job currently stands up a
  *different* app's compose stack entirely. Wiring Sharm To Go's own stack
  in needs care this round didn't attempt without a real CI run to verify
  against first.

## Standing project rules that apply to you too

- Exactly one execution packet `ACTIVE` per implementation worktree
  (`AGENTS.md`). This worktree is `wego-010a-0r-isolation` /
  `worktree-wego-010a-0r-isolation`, backed up to
  `origin/wego-010a-sharm-to-go` — never `main` directly.
- Flyway only for schema changes, jOOQ for persistence. Domain code stays
  free of Spring/HTTP/jOOQ-generated-records/Redis/provider-SDK
  dependencies.
- Never invent prices, ratings, operator names, or trip facts. Every fact
  in the catalog traces back to `content-research/from-safari/` (Safari's
  own approved data) or the owner's explicit decisions recorded in
  `content-research/SHARM_TO_GO_SERVICE_INTAKE_SHEETS.md`.
