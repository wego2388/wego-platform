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

Nothing in this round needs your review today — everything built was Tier
2 (no migration, no new permission, no auth/payment logic). This file
exists so you have fast orientation **the next time** you're asked in,
which the owner expects will be when the one deliberately-deferred Tier 1
item below gets built.

## The one Tier 1 item waiting on you (not built yet)

**A sales-pause "kill switch"** — a staff-toggleable flag that stops new
customer requests platform-wide, with an ERP banner. This needs a new
Flyway migration and a new permission check, which `docs/operations/
REVIEW_INTENSITY.md` classifies Tier 1 on both counts. It was **not**
built this round specifically because no independent reviewer was
available to pair with this session — correctly left undone, not quietly
merged solo. When it's built, that's your review: the migration, the
permission gate, and the public request-creation endpoint's enforcement of
it are the parts that matter most.

## What changed this round (2026-10-06/07), if you need it for context

All Tier 2, all self-verified against a fresh throwaway Postgres+backend
stack per change (never shared/production), each with its own evidence
doc in `clients/sharm-to-go/handoff/` dated 2026-10-06:

- Catalog merged to 41 real trips (24 Safari-approved + 17 kept from the
  old 37), with an idempotent import pipeline
  (`clients/sharm-to-go/scripts/import_catalog.py`) driving the real staff
  API. See `2026-10-06_CATALOG_IMPORT_PIPELINE.md`.
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

## Deliberately NOT done this round — don't flag these as missed

- **Real photo upload.** Neither Safari Tours Sharm nor Sharm To Go has a
  working file-upload/storage mechanism anywhere in this monorepo
  (confirmed by a dedicated research pass — no S3/MinIO/Cloudinary
  integration exists in any product). `ServiceMedia.assetReference` is an
  opaque placeholder string by design at this phase. The owner was told
  this honestly and chose not to decide yet between "paste externally-
  hosted links" and "build real upload storage" (net-new work, not a
  reuse of anything that already exists).
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
