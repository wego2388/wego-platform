# Sharm To Go — technical execution plan (website, mobile app, dashboard)

This is the engineering-detail companion to `EXECUTION_PLAN.md`. That file
stays the short phase overview; this file is where each phase turns into real
schema, real endpoints, real screens, and real tests — the same level of
detail WEGO-011 (Sharm Divers Club's DiveOS work) was built at, so a session
picking this up later can execute directly instead of re-deriving it.

**Written 2026-08-30**, at the owner's explicit request, while WEGO-010-A
stays `PAUSED` on the execution board (WEGO-011 is the sole `ACTIVE` packet
right now, per this repository's single-active-packet rule). This document is
planning only — no code, schema, or commit happens against it until the owner
resumes WEGO-010-A and it becomes the board's `ACTIVE` packet again.

**Updated 2026-09-02:** the owner resumed WEGO-010-A (WEGO-011 closed
`COMPLETE` to free the board's single-`ACTIVE` slot) and asked to build this
client "بدون ما يأثر على مشروع شرم دايفرز كلوب" (without affecting Sharm
Divers Club). Before any of the phases below could start for real, that
required first proving the two clients are actually isolated as *running
backends*, not merely as Foundry manifests — see "Packet 0R" immediately
below, now done and self-verified. The owner then asked to continue with the
services ("كمل الخدمات") before that review ran — see "Packet 1A" below,
also now done and self-verified, backend-only (no dashboard/website/mobile
work yet). An independent Tier 1 review covering both packets together is
the next real step before 1B (dashboard).

## Packet 0R — executable client composition (done 2026-09-02, self-verified)

Before this packet, `products/divers` and `products/travel-marketplace` were
both compiled into the one existing Spring Boot application
(`:platform:application`), sharing one global Flyway migration location — so
"the two clients compose independently" was true only for Foundry's manifests
and locks, not for a runnable backend. A Sharm To Go deployment would have
booted with every Divers table, permission, and route silently present.

**What was built:** a second, separate, real Spring Boot application module,
`:platform:apps:sharm-to-go` (`platform/apps/sharm-to-go`), alongside the
existing one. Its Kotlin source set adds `platform/kernel/{security,events,
identity}` and `products/travel-marketplace` only — `products/divers` is not
on its compile classpath at all, the same way `:platform:application` already
adds product source directories directly rather than through a real Gradle
dependency graph. Its own migration folder physically contains only
`V1__platform_foundation.sql` and `V2__identity_foundation.sql` (copied from
`:platform:application`, not shared — see the board entry's accepted-risk
note on that duplication). Kernel's `SecurityConfiguration` no longer
hardcodes the Divers route; it now authorizes whatever `AuthenticatedApiPrefix`
beans a product actually contributes (`DiversBeanConfiguration` contributes
one for the Divers app; nothing contributes one here), and denies everything
else by default.

**Proven live**, not just by unit test: both apps' jars built and run for
real against separate fresh throwaway PostgreSQL containers. The Sharm To Go
app's real database ends up with zero `divers_*` tables and only
`identity:administer` in its permission catalog; its jar contains zero
`com.wego.divers.*` classes. The Divers app is unaffected — full regression
re-run green, same 17 `divers_*` tables, same 16 real permissions as before.
Full evidence is on the WEGO-010-A execution-board entry dated 2026-09-02, not
duplicated here.

**Still open:** an independent Tier 1 review (this changes an explicit
client-isolation/schema boundary, a Tier 1 trigger per
`docs/operations/REVIEW_INTENSITY.md`) has not yet run. Nothing has been
committed to `main`, pushed, or deployed — this was built in an isolated
worktree (`.claude/worktrees/wego-010a-0r-isolation`).

## Packet 1A — catalog contract, domain, schema, ops API, public API (done 2026-09-02, self-verified)

Backend only, per this document's own packet-map rule (1B dashboard/1C
website/1D mobile are separate rows, not started). Full evidence is on the
WEGO-010-A board entry dated 2026-09-02 — this section is the durable
technical summary, not a duplicate of it.

**What was built:** `Provider`/`Category`/`Service`/`ServiceOption`/
`ServiceMedia` domain types in `products/travel-marketplace`, mirroring
Divers' aggregate conventions exactly. `Service`'s lifecycle is `DRAFT ->
REVIEW -> APPROVED -> PUBLISHED`, with `SUSPENDED` reachable from
`PUBLISHED` (and re-publishable from there) and `ARCHIVED` terminal from any
non-archived state; `publish()` requires at least one option and one
rights-cleared media asset per `SERVICE_CONTENT_TEMPLATE.md`'s closing rule.
Migration `V3__travel_marketplace_catalog.sql` under
`platform/apps/sharm-to-go/src/main/resources/db/migration/` — see the
correction to the "Migration" bullet below, which originally pointed at the
Divers app before Packet 0R existed. New `service:view`/`service:manage`/
`provider:view`/`provider:manage` permissions granted to `platform-admin`,
inherently scoped to this client alone by Packet 0R's separate-database
architecture. Full staff CRUD + lifecycle-transition endpoints, plus an
unauthenticated `/api/v1/travel-marketplace/public/{categories,services}`
projection returning only `PUBLISHED`/`ACTIVE` records in the narrow
`SERVICE_OWNERSHIP.md` "Simple public model" shape. A new kernel
`PublicApiPrefix` type (sibling to Packet 0R's `AuthenticatedApiPrefix`)
lets a product mark part of its own route tree as unauthenticated without
kernel security code hardcoding it. A new, separate
`platform/contracts/openapi/v1/sharm-to-go-api.yaml` — this client's own
complete contract, not appended to the Divers app's `wego-api.yaml`.

**A second real isolation gap found while building this, not by luck:**
`:platform:application`'s build script still added
`products/travel-marketplace` to the Divers app's own source set — harmless
before this packet (empty product), a real compile break once the product
gained real code, and a real re-introduction of the exact composition
Packet 0R exists to prevent. Fixed by removing that line.

**Proven live**, not just by test: real `bootstrap-admin` staff account,
then a real `curl` walkthrough — create category → create DIRECT service
with one option and one rights-cleared media asset → confirm public 404
while `DRAFT` → submit-for-review → approve → publish → confirm real,
correct bilingual content on the unauthenticated public list/detail/
categories endpoints → confirm `/api/v1/divers/**` still 401s on this app.
34 new automated tests (domain + full HTTP lifecycle + permission
separation + public-shape assertions), all green; the Divers app re-verified
with zero regressions after the build-script fix.

**Accepted scope simplifications:** one generic audit table for all three
aggregates instead of Divers' one-per-aggregate convention; content fields
are a plain required `en`/`ar` pair, not `LOCALES_AND_CONTENT.md`'s full
per-field translation-lifecycle/staleness tracking (a real, separate
sub-system deferred to a future packet); `SERVICE_CONTENT_TEMPLATE.md`'s
short/full description split collapsed to one field for now. No real
service, category, price, provider, or photo was created outside test
fixtures and the disposable live-verification run above — none of
`SERVICE_OWNERSHIP.md`'s "Proposed launch categories" were seeded.

**Still open:** the same independent Tier 1 review noted for Packet 0R,
now covering both packets together. 1B (dashboard) is next per the packet
map, not started.

## The one rule that overrides everything else here

**No invented service, price, provider, photo, or policy — ever.** Every other
client in this repository (Sharm Divers Club, and this one) is held to the
same standard: real facts only, sourced and approved before publication. This
plan can build the entire structure — schema, screens, forms, templates — with
zero real content in it. It cannot build a single real service, price, or
provider record without the owner supplying it via
`design/SERVICE_CONTENT_TEMPLATE.md`. See "What the owner supplies" at the end
of this document.

## Architecture decisions

The owner asked explicitly to build on Sharm Divers Club's real, already-
proven website and mobile app foundations — reuse the *architecture*, not the
*content* (different client, different brand, different commercial model:
Sharm Divers Club is one business taking WhatsApp inquiries; Sharm To Go is a
multi-provider marketplace taking real bookings and payments).

### Website — reuse the Nuxt pattern, not the copy

New pages live in the already-scaffolded `web/apps/sharm-to-go-site`
(Nuxt 4, same workspace conventions as every other web app here). Concretely
reuse from `web/apps/sharm-divers-club-site`:

- `app/composables/useSiteLocale.ts` + `directionFor()` in `content/locales.ts`
  — the exact `useState`-backed locale ref with `localStorage` persistence and
  RTL/LTR direction helper. Copy the *pattern*, not the strings — Sharm To
  Go's own `app/content/locales.ts` (130 lines already) keeps its own real
  copy.
- The token-driven Tailwind setup (`@wego/design-tokens/tokens.css` +
  `app/assets/css/main.css`) — Sharm To Go already has its own real palette in
  `design/tokens.json` (sea/lagoon/sand/sun, distinct from Sharm Divers Club's
  turquoise/navy/sand). Port it into CSS custom properties the same way
  `tokens.css` does, not by copying Sharm Divers Club's own values.
  Dark-mode: port `@media (prefers-color-scheme: dark)` the same way,
  redefining role tokens (`--stg-color-surface-canvas` etc.), not raw hex.
- `ConditionsWidget.vue`'s discipline — not the widget itself (Sharm To Go has
  no live-conditions use case), but its *pattern*: a component that shows a
  real loading state, a real error/unavailable state, and never a fabricated
  value. Apply the same discipline anywhere this site shows anything
  server-dependent (availability, price-at-confirmation).
- `OfferingCard.vue` → the equivalent here is a `ServiceCard.vue`: photo (or
  the same honest gradient-placeholder pattern
  `SdcCard.kt`/`SdcMockPhoto` used on mobile until real photos are approved),
  category icon, price-from/basis label, `Operated by` badge when
  `fulfilmentModel == PARTNER` (a Sharm To Go-specific requirement Sharm
  Divers Club never needed — see `SERVICE_OWNERSHIP.md`).
- Skip-link + `id="main-content" tabindex="-1"` accessibility pattern from
  `app.vue` — apply from Phase 1 onward here, not bolted on later like it was
  for Sharm Divers Club.
- Test conventions: `test/setup.ts` stub pattern, `@vue/test-utils` +
  `happy-dom`, one spec file per page/component, real-content assertions (not
  placeholder strings) — already true of the 3 existing spec files here.

The existing scaffolding (`index.vue`, `experiences.vue`,
`booking-preview.vue`, `design-system.vue`) is Phase 0 output — real,
reviewed, kept. Phase 1+ extends it; it does not get rewritten.

### Mobile app — a new, dedicated app, not a generalized shell

**Decision, per the owner's explicit instruction:** a new mobile app module,
not a multi-tenant generalization of `mobile/apps/customer` (which stays
Sharm Divers Club-branded, per its own `SdcTokens`/`SdcTheme` — see
`project_wego_platform.md` memory on why that app is client-specific, not
product-neutral). Splitting them keeps both apps' release cadence, store
listing, and branding independent, and avoids a risky runtime-theming
refactor of an app that already works and is (per the owner's own plan) close
to a real device/store rollout.

New module: `mobile/apps/sharm-to-go` (Kotlin Multiplatform library,
`jvm` + `androidTarget` + `iosArm64`/`iosSimulatorArm64` — same target set
`mobile/apps/customer` already proved works, including the AGP-9/
`com.android.kotlin.multiplatform.library` plugin gotcha documented in memory)
plus `mobile/apps/sharm-to-go-android` (the installable `com.android.application`
module, mirroring `mobile/apps/customer-android`'s exact structure). Whatever
is genuinely product-neutral (not Sharm-Divers-Club-specific) already sitting
in `mobile/shared` — locale enum shape, `LocalizedText`, the KMP module/target
wiring itself — is reused directly, not re-invented. `mobile/shared` is the
right layer for anything a *second* client app needs identically; verify this
as each piece is built, don't assume in advance.

Concretely reuse *the pattern* from `mobile/apps/customer`:

- `design/SdcTokens.kt` → `design/StgTokens.kt`, ported from
  `design/tokens.json` the same disciplined way, with the real Sharm To Go
  palette.
- `design/SdcCard.kt`'s `SdcCard`/`SdcBadge`/`SdcMockPhoto` → an equivalent
  `StgCard.kt`. The honest-placeholder-photo discipline applies identically:
  no real photos exist yet, so every card gets the same gradient block until
  real, rights-cleared photos are approved (`SERVICE_CONTENT_TEMPLATE.md`'s
  own "Photo asset IDs and rights evidence" field is exactly this gate).
- `state/AppLocaleState.kt` + `locale/LocaleStore.kt`/`NoOpLocaleStore`/
  `AndroidLocaleStore` — the exact constructor-injection pattern for
  platform-specific `SharedPreferences` persistence without `expect`/`actual`.
- `nav/AppDestination.kt`'s sealed-route pattern, `WegoCustomerRoot.kt`'s
  `NavHost` wiring, bottom-nav-vs-secondary-CTA placement decisions (a launch
  screen catalog fits comfortably in a 5-tab bottom bar: Home, Experiences,
  Bookings, Saved (P1, can be a stub tab or omitted at P0), Account/Help).
- The `runComposeUiTest` JVM-target testing approach
  (`DiveSitesAndPackageBuilderTest.kt`'s structure) for every new screen.

**Where the pattern must NOT be reused — the real difference:** Sharm Divers
Club's mobile app never calls a backend API; every "book" action opens a
prefilled WhatsApp link (`WhatsAppInquiry.kt`). Sharm To Go's entire product
is a real transactional booking (per `PRODUCT_BLUEPRINT.md`'s state machine,
`NEW → CONFIRMED → COMPLETED`), so from Phase 2 onward the mobile app needs a
real HTTP client talking to `products/travel-marketplace`'s real API — this
repository's mobile layer has never done this before (confirmed while fixing
independent Tier 1 review's finding 17 on the website: no KMP HTTP client
exists in this codebase yet). Phase 1 (catalog is read-only, no checkout) can
still ship with zero network code by shipping the catalog as bundled data the
same way `DiveCatalog.kt` does — real published services, refreshed on each
app release, not live-fetched — deferring the "what HTTP client, what auth
token storage, what retry/offline story" decision to Phase 2, where it
actually becomes unavoidable and deserves its own real design pass rather
than a rushed choice now.

### Dashboard — reuse the ERP integration pattern

`web/apps/sharm-to-go-erp` already exists (Phase 0: readiness-only, no real
API). Reuse from `web/apps/erp`:

- The `useXApi.ts` composable shape (`listX`/`getX`/`createX`/`updateX`,
  typed request/response interfaces, a shared `request()` helper with a
  typed `XApiError`) — `useDiversApi.ts` is the real, current, largest example
  in this repo (over 500 lines, 6 resource types). Sharm To Go's dashboard
  gets its own `useTravelMarketplaceApi.ts` following the identical shape.
  Deliberately mirror the finding-12 remediation split too: any list endpoint
  serving customer PII (booking contact details) gets a roster projection,
  full record on single-GET — designed in from the start here, not retrofitted.
- Permission-gated UI: `hasPermission(session, 'x:manage')`-style checks
  hiding/disabling actions client-side, backed by real `@PreAuthorize` checks
  server-side — never trust the client-side gate alone, exactly as
  established across every Divers controller.
- Confirmation dialogs on destructive/financial actions (`window.confirm`),
  typed reason fields on cancel/refund actions — the `Bookings.vue` pattern.
- Vitest conventions identical to `Divers.spec.ts`/`Equipment.spec.ts`.

`app/content/dashboard.ts` (72 lines, Phase 0 readiness copy) is extended,
not replaced, as real work queues from `design/DASHBOARD.md` get real data
behind them.

## Phase 1 — Service catalog (read-only, no booking yet)

Matches `EXECUTION_PLAN.md`'s own Phase 1 scope, expanded to full technical
detail across all three surfaces.

### Backend (`products/travel-marketplace`)

- Domain: `Category`, `Service`, `ServiceOption` (duration/participant-band/
  price-basis variants of one service — e.g. "2-hour" vs "half-day" desert
  safari), `Provider` (name, fulfilment model, contact — no commission/legal
  fields yet, per `SERVICE_OWNERSHIP.md`'s "commission agreement... can be
  added when partner settlement is actually implemented"), `ServiceMedia`
  (asset id, rights evidence, locale). Publication lifecycle exactly as
  `LOCALES_AND_CONTENT.md` defines: `DRAFT → REVIEW → APPROVED → PUBLISHED`,
  plus `SUSPENDED`/`ARCHIVED`. Mirror the Diver/Equipment/BoatCharter
  aggregate conventions exactly: `@JvmInline value class` ids, `private set`
  mutable lifecycle fields, `init { require(...) }` invariants, a
  `companion object { fun create(...) }`, named lifecycle methods
  (`submitForReview()`, `publish()`, `suspend()`, `archive()`) that
  `require()`-guard valid transitions.
- Migration: **done as `V3__travel_marketplace_catalog.sql`, under
  `platform/apps/sharm-to-go/src/main/resources/db/migration/`** — this
  instruction originally said `platform/application/` (the Divers app), but
  that was written before Packet 0R existed and is now wrong: Sharm To Go
  has its own application and its own Flyway version sequence (which only
  had V1/V2 before this packet), completely separate from the Divers app's
  V3-V8. New
  `service:view`/`service:manage`, `provider:view`/`provider:manage`
  permissions, **done as granted to `platform-admin` directly** — the
  per-client role-scoping concern this bullet originally raised turned out
  to be resolved automatically by Packet 0R's architecture, not by a new
  feature: each client is now a separate application with its own separate
  identity database, so a Sharm To Go `platform-admin` account and a Divers
  `platform-admin` account are different rows in different databases from
  the start, sharing nothing.
- Application/infrastructure/api layers: same file-per-operation service
  pattern, jOOQ repositories, `@PreAuthorize`, OpenAPI paths/schemas, exactly
  as every WEGO-011 phase. `GET /api/v1/travel-marketplace/services` public
  (no auth) returns only `PUBLISHED` services; a separate authenticated
  dashboard-facing list/detail returns every status.
- **Real invariant this phase must enforce, not defer:** a service cannot
  reach `PUBLISHED` without price, capacity/duration, cancellation wording,
  fulfilment owner ("Operated by" value when `PARTNER`), and media rights all
  present — `SERVICE_CONTENT_TEMPLATE.md`'s own closing rule, enforced by a
  real domain `require()` on the `publish()` transition, not just a dashboard
  form hint.

### Dashboard

- Service list/editor (P0 per `SCREEN_CATALOG.md`): identity, category,
  fulfilment label, duration, inclusions/exclusions, pickup, cancellation
  policy, media rights, locale status, publication-state actions gated on
  `service:manage`.
- Content/locales screen (P0): missing/draft/reviewed/stale/published states
  per `LOCALES_AND_CONTENT.md`'s translation lifecycle — a real, queryable
  view, not decoration.
- Provider list (minimal P0 slice): name, fulfilment model, contact,
  assigned services — full provider-workspace/self-service login is P2,
  explicitly deferred per `PRODUCT_BLUEPRINT.md`.

### Website

- `/experiences` (already scaffolded as a static P0 shell) becomes real:
  category filter, published-service grid using `ServiceCard.vue`.
- New `/experiences/[slug].vue` (or `/services/[slug].vue` — name this to
  match whatever the final public URL taxonomy in
  `INFORMATION_ARCHITECTURE.md` settles on) — service detail, highlights,
  included/excluded, pickup, `Operated by` label, real price display, no
  booking action yet (Phase 2) — a clear "Contact us" or waitlist-style CTA
  is fine as a placeholder, never a fake "Book now" button that does nothing.
- `/booking-preview` stays the interaction-pattern prototype it already is;
  it does not become the real checkout (that's Phase 2's job, built as a
  real page consuming real APIs, informed by but not literally repurposed
  from the prototype's markup).

### Mobile

- Bundled-catalog approach (see Architecture decisions above): ship the
  same published services as static Kotlin data, refreshed per app release.
- Home, Experiences (list+filter), Service Detail screens — same structure as
  Wego Customer's Home/Discover/OfferingDetail, StoreLocale-driven ar/en.

### Tests and verification (every surface, every phase — not just Phase 1)

Same non-negotiable bar as WEGO-011: real Testcontainers-backed integration
tests for every service/repository, real HTTP tests for every endpoint
including negative-permission cases from day one (learn directly from
independent Tier 1 review's finding 11 — do not let permission coverage lag
behind endpoints the way it did in WEGO-011's first four phases), Vitest for
every web page/component, Compose UI tests for every mobile screen, a live
end-to-end run against a real throwaway Postgres + the real built jar before
every commit, `bash scripts/repository-check.sh` clean, and a proper
Tier 1 review before this phase's commit lands — WEGO-011 committed 6 phases
before its first review; that mistake is not repeated here. This phase adds
a real migration and a new authorization surface, so it is Tier 1 from the
moment it is scoped, not reclassified afterward.

## Phase 2 — Real booking flow

- Backend: `BookingRequest`/`Booking` aggregate, slot/capacity model, price
  snapshot (immutable at confirmation, mirroring `BookingPricing` from
  WEGO-002 exactly — that pattern is already proven correct and reviewed),
  idempotency-key handling (same `BookingFingerprint` + advisory-lock pattern
  WEGO-002 built and Tier 1-reviewed), the `NEW → CONFIRMED → COMPLETED` /
  `CANCELLED` / `EXPIRED` state machine from `PRODUCT_BLUEPRINT.md`.
- Website: real `/checkout` flow per `BOOKING_AND_CHECKOUT.md`'s exact step
  order (date/time → language/guests/add-ons → pickup/contact → price/policy
  review → payment method placeholder until Phase 3 → result), `/manage-
  booking` lookup page.
- Mobile: first real network integration — the HTTP-client decision deferred
  from Phase 1 gets made here, plus real checkout screens mirroring the
  website's step order, and secure token storage for a returning customer
  (a real design question: guest checkout only at first, per
  `BOOKING_AND_CHECKOUT.md`'s "minimum customer fields" list, defers a full
  account system to P1).
- Dashboard: booking queue, calendar/capacity editor, booking detail with
  immutable snapshot + internal notes.
- Concurrency proof required before commit: capacity-exceeded rejection under
  real concurrent requests, exactly like `BookingCapacityConcurrencyIntegrationTest`.

## Phase 3 — Payment, refund, settlement

Per `PAYMENT_FOUNDATION.md` exactly: Paymob hosted checkout, FawryPay
reference-code option, CIB settlement reconciliation, cash-on-arrival as a
service-level option. Payment intent/attempt/refund model as documented
there. This phase cannot start without real signed merchant terms and real
sandbox credentials from the owner — flagged there already, restated here
since it's the one phase in this whole plan that is blocked on something
only the owner can obtain, not on engineering time.

## Phase 4 — Planner, saved/compare, reviews, locale expansion

- Planner assembles an itinerary only from currently published catalog facts
  — no invented availability, ever (this is the one place in the whole
  product where a naive implementation would be tempted to fabricate, so
  call it out explicitly here too, not just in `PRODUCT_BLUEPRINT.md`).
  Whether this uses an AI-assisted ranking model at all — and if so, how
  narrowly scoped and reviewed that model's output is before it reaches a
  customer — is a real, separate decision the owner should make deliberately
  when this phase actually starts, given the standing caution around
  AI-driven customer-facing logic this whole DiveOS/Sharm-To-Go planning
  effort has already applied elsewhere (the diving-safety-scoring rejection).
- Reviews: only a completed, eligible booking can create one — enforced at
  the domain layer, not the UI.
- Locale expansion one language at a time (`ru` next, per
  `LOCALES_AND_CONTENT.md`), only after ar/en parity is real and measured.

## Phase 5 — Operations and controlled launch

Retention/consent decisions for booking PII (a real gap already flagged in
WEGO-002's own risk log for Divers bookings — Sharm To Go should not repeat
it undecided), backups/restore, observability, accessibility/performance/SEO
per `RESPONSIVE_AND_ACCESSIBILITY.md`/`SEO_AND_ANALYTICS.md`, isolated
deployment per the client manifest's `deploymentIsolation: ISOLATED_INSTANCE`
— a real, separate deploy target from both existing clients' current VPS
setup, not something to assume can share infrastructure without checking.

## What the owner supplies (nothing here gets invented)

1. **Real service data** — one filled `SERVICE_CONTENT_TEMPLATE.md` block per
   service for the first real launch catalog. `Unknown` is fine mid-draft; it
   blocks that service's `publish()` transition, it does not get guessed.
2. **A real Sharm To Go staff role decision** — who are the actual first
   dashboard users, and should their permissions be scoped separately from
   `platform-admin` (see Phase 1's backend note above)?
3. **The mobile-app-naming/branding decision** — confirm `mobile/apps/
   sharm-to-go` (module name) and the real customer-facing app name/icon
   before Phase 1's mobile work starts, so it is not built under a
   placeholder name and renamed later.
4. **Payment activation inputs**, only when Phase 3 actually starts — signed
   merchant contracts, legal merchant name, CIB details, Paymob/Fawry
   credentials (sandbox first), webhook signing method — never committed to
   this repository, per `PAYMENT_FOUNDATION.md`'s own closing line.
5. **Real photos with rights evidence**, whenever they become available —
   until then, every surface uses the same honest placeholder pattern already
   proven on both Sharm Divers Club's mobile app and (per this session's own
   remediation round) its website.

## Sequencing relative to the rest of this repository

WEGO-011 (DiveOS) is the current `ACTIVE` packet and must reach a genuinely
clean state (this remediation round's independent Tier 1 re-review returning
zero blocking findings) before WEGO-010-A can become `ACTIVE` again — the
board allows exactly one active packet, and swapping mid-remediation would
leave WEGO-011 in exactly the kind of unreviewed-but-committed state this
whole remediation round exists to correct. When the owner is ready to resume
Sharm To Go, the concrete next step is: flip WEGO-010-A back to `ACTIVE`,
open Phase 1 as its own dated board entry (this document's Phase 1 section is
the scope for that entry), and start with the backend catalog schema — the
one piece every other surface (dashboard, website, mobile) depends on.
