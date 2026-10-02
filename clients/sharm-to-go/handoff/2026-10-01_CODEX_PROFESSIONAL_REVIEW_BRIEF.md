# Codex professional-improvement review — ERP + public website

Prepared: 2026-10-01, by Claude, at the owner's (Mohamed) explicit request.

## Purpose of this review

The owner wants an **advisory, professional-improvement review** of the
current ERP and public website — not a rebuild, not new scope beyond what
is written here. The question is: *given everything that exists right now,
where is the current implementation weak, unprofessional, inconsistent, or
missing something a real production travel-booking product would need?*

This is advisory only. No commit, push, merge, deploy, or production action
should result directly from this review — findings go back to the owner and
to Claude (who will give an independent final opinion after reading this
review, not simply forward it).

## Read first, in this order

1. `AGENTS.md` (repo root) — platform/product/client boundaries, `AGENTS.md`'s
   own invariants.
2. `docs/ENGINEERING_CONSTITUTION.md`.
3. `clients/sharm-to-go/ROADMAP_AR.md` — the canonical, current, dated record
   of what phase is done and what evidence backs it (Arabic; phase headers
   and dates are legible even if you do not read Arabic fluently — ask if a
   section's content matters and is unclear).
4. `clients/sharm-to-go/delivery/01_REQUEST_AND_BOOKING.md`,
   `02_OPERATIONS_ERP.md`, `03_MARKETING_WEBSITE.md` — per-phase scope and
   **self-reported gaps** (sections marked `[ ]` or `[~]` with an explicit
   "real gap, not done" note are honest, not oversights to rediscover).
5. `docs/execution/WEGO_EXECUTION_BOARD.md`, the `WEGO-010-A` section —
   dated entries with real evidence (test counts, manual verification
   details) for every phase through 2026-10-01 (Phase 3A/3B/3C).

## What exists right now (as of 2026-10-01)

- **Backend:** `products/travel-marketplace/` — a real `TravelRequest`
  domain (state machine `NEW → IN_REVIEW → CONFIRMED → COMPLETED`, plus
  `CANCELLED`/`EXPIRED`), idempotent request creation (client-supplied
  `Idempotency-Key` + DB unique constraint), row-locking via jOOQ
  `.forUpdate()`, snapshotted price/policy at request creation, a public API
  (no customer PII) and a staff API (full record), permission-gated staff
  actions, an audit-event trail. Deployed per-client via
  `platform/apps/sharm-to-go/`. Flyway migrations, 37+ backend tests
  (domain, Testcontainers persistence/concurrency, HTTP).
- **ERP** (`web/apps/sharm-to-go-erp/`, Nuxt): provider/category/service
  catalog management with a draft→review→approve→publish→suspend→archive
  workflow; a requests queue (status filter, client-side search only — no
  server-side date/service/source filters); a request detail page with
  review/confirm/cancel/complete actions, audit timeline, a `wa.me` deep
  link pre-filled with a customer-safe summary.
- **Public website** (`web/apps/sharm-to-go-site/`, Nuxt, server-rendered):
  homepage, catalog list/detail, about/faq/contact/privacy/terms, a real
  4-step request flow (party/date → contact → review → result) that calls
  the backend through same-origin Nitro proxy routes, a track-by-reference
  page, a real search box on the homepage (category/date/party) that
  forwards into the catalog and pre-fills the request form, a shared
  `SiteFooter` and `useSiteLocale` composable (EN/AR, persisted via
  `localStorage`) used by every real page except the request flow itself
  (deliberately, to avoid an exit-link footer mid-checkout).
- **Mobile:** a separate Android catalog app exists (`mobile/`) with its own
  module; per `ROADMAP_AR.md` Phase 4, it is **not yet** wired to the real
  request API — still reads a locally-bundled catalog.
- **Not built at all, anywhere:** payment collection/refunds/reconciliation,
  availability slot/capacity pooling across requests (capacity today is
  checked per-request against a snapshotted `maxParticipants`, not a shared
  calendar), production hosting/domain/secrets, analytics, SEO structured
  data beyond a basic sitemap, an accessibility sweep, mobile navigation
  (hamburger menu) on the site, a skip-to-content link, a consent checkbox
  on the request form.
- **Never done, honestly recorded as a gap every phase:** a real graphical
  browser visual check (Claude-in-Chrome was not connected in any session
  that built this) — every "it renders correctly" claim so far is backed by
  component-level tests (real Vue mount/render, not screenshots) plus
  manual `curl`-level HTTP verification against real throwaway
  infrastructure, never an actual rendered-pixels check.

## The owner's broader product direction (future, explicitly not in scope now)

The owner's stated intent for where this product should go, **for your
context only — do not design or build any of this now, this review is
about improving what already exists**:

- The core idea stays a catalog of many trips/experiences, exactly as it is
  now ("رحلات كتيرة" — many trips) — this is not changing.
- On top of that, the owner wants the site to eventually also work as a
  **Sharm El Sheikh visitor's guide** — a resource covering the practical
  information a visitor actually needs while dealing with Sharm day to day.
  The owner explicitly included even **government/official services a
  tourist might need** (e.g. visa extension, port/customs formalities,
  embassy or consular contacts) as something to eventually point visitors
  toward or inform them about — but said explicitly this is a **future**
  direction, to be scoped properly later, not something to build now.
- Why this matters for *this* review: when you flag an architectural or
  content-model issue in the current implementation, note (only where it is
  genuinely relevant and cheap to mention) whether the current structure
  would make that kind of future content/informational expansion easy or
  hard — but do not spend review budget designing the guide itself, and do
  not treat "doesn't support the guide yet" as a finding on its own. The
  review's job is to make the current trips-catalog product better, not to
  plan the guide.

## What to actually review

Read the real code, not just the delivery docs' own claims — the docs
describe what was *intended* and *tested*; verify against the actual
implementation where it matters.

- `products/travel-marketplace/` (domain, application, infrastructure, API
  layers) and `platform/apps/sharm-to-go/` (migrations, wiring).
- `web/apps/sharm-to-go-site/` (all of `app/pages`, `app/components`,
  `app/composables`, `server/api`).
- `web/apps/sharm-to-go-erp/` (all of `app/pages`, `app/composables`).
- `platform/contracts/openapi/v1/sharm-to-go-api.yaml`.

Look for, and cite specific files/lines for:

1. **Correctness and robustness risks** in the booking/request flow
   (concurrency, idempotency, state-machine edge cases, error handling) —
   this is the money-adjacent core of the product.
2. **Code quality and architecture** — duplication, layering violations
   (domain code depending on Spring/HTTP/jOOQ — forbidden per
   `AGENTS.md`), inconsistent patterns between the ERP and site, anything
   that will make the next real feature (payments, availability pooling,
   the mobile app's real integration) harder than it needs to be.
3. **Production readiness gaps** beyond what is already self-documented
   above — security headers, rate limiting, logging/observability, input
   validation completeness, anything that would embarrass a real paying
   client if launched as-is.
4. **UX/content professionalism** on both the ERP and the public site —
   does it read and behave like a real commercial product, or like a
   student project? Be specific and concrete, not generic ("add more
   polish") — point at an actual screen/flow and say what is wrong with it
   and why.
5. **Honest gap validation** — spot-check a handful of the "done" claims in
   `ROADMAP_AR.md`/the delivery docs against the real code. If something
   claimed done is actually incomplete or wrong, say so explicitly; that is
   as valuable as finding a new issue.

## What NOT to do

- Do not write or modify any code. This is a read-only review.
- Do not propose or scope the "visitor's guide" feature — context only, see
  above.
- Do not assume production infrastructure exists (it does not — no deploy,
  no domain, no live secrets for this client yet).
- Do not treat this as a security-authorization gate or a merge review —
  it is a professional-improvement opinion for the owner and Claude to
  weigh, not an approval/rejection verdict.

## Output format

A structured report: a short overall assessment, then findings grouped by
the five categories above, each with a file path (and line numbers where
practical), a concrete description of the problem, and a concrete
suggested fix or direction — ranked roughly by how much it would matter to
a real paying client, most important first.
