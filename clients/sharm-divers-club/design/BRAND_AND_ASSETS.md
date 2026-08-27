# Brand and asset register — Sharm Divers Club

## Source of truth

Brand voice, positioning, and personas come from the owner-maintained marketing
workspace at `/home/wego/projects/clients/sharm-divers-club/` (`ULTRA_START_HERE.md`,
`brand/BRAND_CORE.md`, `data/approved-facts.json`, `data/catalog.dive-core.v1.json`).
That workspace is not a code dependency — nothing in `web/apps/sharm-divers-club-site`
imports from it directly. Facts used here were copied in by hand, one at a time,
each traceable to a `status=approved` / `publishable=true` entry in
`data/approved-facts.json` at the time of writing. A fact whose `expiresAt` has
passed must be re-verified before reuse, not assumed still current.

## What is safe to publish today

Checked directly against `data/approved-facts.json` at time of writing — only
the rows with **both** `status: "approved"` and `publishable: true` qualify.
An earlier draft of this document listed founding year, payment methods, the
own-boats claim, women-owned, the 3:1 instructor ratio, and the 70m jetty as
safe; that was wrong — all six are `status: "pending"` or `"disputed"` with
`publishable: false` in the real file, so none of them may appear on the
site. The corrected, actually-safe list:

- `business.official_name` — "Sharm Divers Club" (use exactly this spelling).
- `business.positioning` — "PADI 5 Star Dive Center in Sharm El Sheikh" (no
  "Number 1," "safest," or "best" — the fact's own note forbids it).
- `accreditation.cdws_number` — "100601".
- `contact.phone_e164` / `contact.whatsapp_url` / `contact.email` /
  `contact.website`.
- `location.primary` — "Royal Grand Sharm Hotel, Hadabet Um Sid, Sharm El
  Sheikh, Egypt".
- `business.hours` — "Daily 08:00–20:00".
- `languages.padi_listed` — Arabic, English, Russian, German, Italian (a
  staffing-capability fact; it does not license shipping non-en/ar *site
  copy* — see below).
- The offering *categories* from `data/catalog.dive-core.v1.json` (shore
  diving, boat diving, multi-day packages, signature packages, world-class
  sites, PADI courses) — the category list itself is stable product
  structure, not a price or capacity claim.

## Pricing — approved 2026-08-26, diving items only (decision GOV-003)

`data/catalog.dive-core.v1.json` was updated on 2026-08-26: every diving offer
(`SD`, `BD`, `MP`, `HP`, `WC`, `PC` codes) now carries `status: "approved"` and
`publishable: true`, under `governance/DECISION_LOG.md#GOV-003` ("the current
workspace user is the pricing administrator; price changes do not require an
additional owner-approval gate"), sourced from the real internal 2026
pricelist. Real prices for these 12 offerings are now shown directly on
`/discover` and `/offering-preview` — no more "pending"/"illustrative"
framing for them.

**Explicitly out of scope for now, by the owner's own decision (2026-08-27):**
the same 2026-08-26 catalog update also added 24 new non-diving offers
(`WS`/`DS`/`EX`/`SN`/`TR` — water sports, desert safari, sightseeing,
snorkeling, transfers) across 5 new categories. The owner's instruction was
explicit: **"Diving only for now, expand later."** These new offers, their
categories, and the 8 newly `APPROVED` AI-generated cover images in
`assets/ASSET_REGISTER.md` (`GEN-CAT-01` through `GEN-CAT-08`) are
deliberately not used in this foundation yet — both because the scope
decision excludes them and because that register's "Approved uses" column
lists `Catalog, WhatsApp, Telegram, Meta organic content` without an explicit
"website" entry, which needs confirming before use here even for the three
diving-relevant covers (`GEN-CAT-01/02/03`).

`handoff/SHARM_DIVERS_EXECUTION_PLAN.md` in the marketing workspace is the
authoritative execution plan behind this: it explicitly scopes the *backend*
work (a real `ServiceDefinition`/price-version/public-catalog-projection
packet) as **not yet authorized** — gated behind WEGO-010-A completing and the
owner explicitly activating that dedicated packet. Nothing in this site build
does that backend work; prices are hand-copied into static site content, the
same methodology `BRAND_AND_ASSETS.md` already used for text facts, not a
live integration.

## What is NOT safe to publish yet

- Founding year (`business.founded_year` is `disputed` — internal pricelist
  says 2015, the PADI profile says 2016), payment methods, "we own and manage
  our daily boats," women-owned, the 3:1 max-divers-per-instructor ratio, and
  the 70m jetty length — all `pending`, none may be stated as fact until the
  owner resolves and approves them.
- Photography beyond the 8 newly-approved AI covers noted above (which this
  foundation still doesn't use — see the scope note). This foundation uses
  tokenized colour/gradient placeholders and small inline SVG icons, not
  stock or unlicensed images — same reasoning `clients/sharm-to-go/design/BRAND_AND_ASSETS.md`
  already documents: no asset is publishable merely because it is accessible
  online. The 174 legacy files under `SharmDiversClub pic/` remain completely
  unregistered and must not be used.
- Hebrew, Russian, German, and Italian *site copy*. The client manifest lists
  `ar`/`en`/`he` as supported locales and PADI lists five staff-service
  languages (which is a publishable fact, above), but this foundation only
  ships fully-reviewed `en`/`ar` UI copy — claiming translated site coverage
  without a native-reviewed translation would be the same "claims coverage
  that doesn't exist yet" mistake `clients/sharm-to-go/REFERENCE_STUDY.md`
  explicitly calls out avoiding.

## Booking capability — a real, current limitation, not a design choice

`products/divers` (WEGO-002) only allows an **authenticated staff session**
holding `booking:create` to create a booking — there is no anonymous/public
booking endpoint today, by explicit original design (WEGO-002's own recorded
scope: *"Any public/customer-facing booking UI... staff/ops-only per the
owner's explicit decision"*). This site therefore cannot honestly offer
self-service checkout yet. Every "book" action in this foundation routes to
the real, approved WhatsApp channel (`https://wa.me/201066461010`) instead of
a fake confirmation screen — matching how the marketing workspace's own
`ULTRA_START_HERE.md` already describes the real rollout order ("WhatsApp
Concierge manual first, then Cloud API, then AI"). A future packet that adds a
real public booking-request endpoint would change this; nothing here should
be read as claiming that capability exists already.

Copy was corrected on 2026-08-27 to say "send an inquiry" rather than "book"
for the WhatsApp CTAs, per `handoff/SHARM_DIVERS_EXECUTION_PLAN.md`'s own
remediation note ("A channel Lead creates an Inquiry, never a Booking") — a
WhatsApp message is real, but it becomes a booking only once staff confirm
it, and the site's language should say that precisely.
