# Sharm To Go — marketplace expansion plan (Activities → Dining → Accommodation → Car Rental → Automation)

**Written 2026-09-02**, at the owner's explicit request after reviewing seven
reference mockups (an Egyptra-style multi-vertical Sharm El Sheikh OTA: search,
activities, dining, accommodation, car rental, special offers, and a
WhatsApp-shareable booking-summary card) and approving the sequencing below
("موافق علي الترتيب المقترح (بدون قفز غير واقعي)"). This document is the
durable technical plan for that sequencing — `EXECUTION_PLAN.md` stays the
short phase overview, `TECHNICAL_EXECUTION_PLAN.md` stays the activities
vertical's own detailed plan (Phases 0–5), and this document is where the
*other* verticals and the cross-cutting WhatsApp/social booking mechanism get
designed before they become real packets.

**Status: expansion planning only.** Activities Packets 0R and 1A–1C now exist;
this document's Dining, Accommodation, Car Rental, and automation phases remain
planning only. Nothing here authorizes their implementation by itself — each
numbered phase below still needs its own board entry, review-tier
classification, and (where Tier 1) independent review before commit, per
`docs/ENGINEERING_CONSTITUTION.md` §2 and
`docs/operations/REVIEW_INTENSITY.md`.

## The real product shape, stated once

Sharm To Go is **one discovery-and-inquiry marketplace for everything a
visitor does in Sharm El Sheikh** — activities, dining, stays, transport —
in Arabic/English (more locales later), with **human-confirmed booking via
WhatsApp**, not a live payment checkout, until a real payment gateway is
separately authorized (`TECHNICAL_EXECUTION_PLAN.md`'s own Phase 3 already
gates that for Activities; the same gate applies to every other vertical).

This is not a new architectural direction — it is the same discipline
already proven for Activities (Packet 1A): a staff-managed catalog behind a
publication workflow, a narrow public projection, and (for the parts not yet
built) a website/dashboard/mobile surface reusing the same patterns.

**Correction after design consultation (2026-09-02):** the first draft of
this plan said every vertical "reuses `Category` as-is." That is not safe
without a change: `Category` (`domain/Category.kt`) carries no vertical
discriminator, and the existing public endpoint
(`PublicCatalogQueryService.listCategories()`) returns *every* `ACTIVE`
category regardless of which vertical created it. This is a real, live gap
in already-committed Packet 1A code — harmless today only because Activities
is the only category consumer that exists yet. The moment Dining creates its
first category ("Egyptian Restaurants"), it would appear on the Activities
public category list with nothing to stop it. Dining's implementation must
either add a scope/applicability field to `Category` (e.g. which product
vertical it belongs to) or give each vertical's public endpoint its own
scoped category query — decided in the Dining section below, not deferred.

## Why booking stays WhatsApp/social, not a new automation layer

The reference mockup's "booking summary" card is the right mental model:
the customer fills real details (their own info, dates, party size) on the
website/app, the system renders a real, correct summary from that data, and
the customer (or the system, on their behalf) sends it to Sharm To Go's
WhatsApp for staff confirmation. This is **the same honest, no-fake-checkout
discipline** already proven across this repository — Sharm Divers Club's
site/app WhatsApp-inquiry pattern, and Sharm To Go's own existing
`booking-preview.vue` prototype.

What this deliberately is **not**, and why: an automated WhatsApp-reply bot,
automatic lead capture from Meta/Instagram/TikTok DMs into a `Booking`, or
any AI-driven response to a customer message. That is a distinct, larger,
already-identified body of work (`WEGO_EXECUTION_BOARD.md`'s WEGO-003
reliable delivery, WEGO-004 communications/consent, WEGO-005 inquiry/lead
intake — all `NOT AUTHORIZED`, gated on reliable outbox delivery, consent
tracking, and audit that do not exist yet). Building it prematurely here
would repeat exactly the mistake this repo's own governance already guards
against for the Divers client. Point 5 of the approved order ("أتمتة
واتساب/السوشيال الحقيقية... مؤجل عمداً") stays deferred for the same reason,
across every vertical — a human confirms every real booking on WhatsApp
until that foundation is built and separately authorized.

## Sequencing (owner-approved 2026-09-02)

| # | Vertical | What "done" looks like | Depends on |
|---|---|---|---|
| 1 | **Activities/Experiences** | Packets 1A (backend), 1B (dashboard), and 1C (website) done. 1D (mobile) is next; 1E needs real approved content. | Nothing further for 1D; owner app identity required before release |
| 2 | **Dining** (restaurants, cafés) | A real directory: browse by cuisine/area, real photos with rights evidence, WhatsApp inquiry. No booking engine. | 1 substantially done (reuses its dashboard/website/mobile shell) |
| 3 | **Accommodation** | Partner-listing directory (Model A below), not live rate/availability integration | Real partner hotels under agreement — owner-supplied |
| 4 | **Car rental** | Same directory pattern as Dining/Accommodation | Real partner agencies under agreement — owner-supplied |
| 5 | **WhatsApp/social automation** | Deferred — see above | WEGO-003/004/005-equivalent foundation, not yet authorized |

Each row is additive, not a replacement — Activities' own booking/payment
maturity (`TECHNICAL_EXECUTION_PLAN.md` Phases 2–3) proceeds independently
and is not blocked by rows 2–4, since Dining/Accommodation/Car-rental are
directory-only by design and never need a booking/capacity engine to reach
their own "done." **Confirmed by design consultation**: there is no
*technical* dependency either way between Dining and Activities' booking
phases while Dining stays a read-only directory plus a client-generated
WhatsApp link — finishing Activities' 1C/1D before starting Dining is a
real, valid *owner sequencing choice* (proves the dashboard/website/mobile
patterns once before extending them to a second vertical), not a backend
architectural requirement. Stated as a dependency in the table only to
record the owner's actual chosen order, not to claim Dining is technically
blocked without it.

## Phase 2 — Dining (restaurants and cafés)

**Revised 2026-09-02 after an architecture design consultation (Codex).**
The first draft below was reviewed before any code was written, per the
owner's own instruction to have Codex help shape the build plan. Two of its
calls were corrected as a direct result — see the callouts inline. This is
exactly the point of consulting before implementing: the correction was
cheap here, and would have been expensive after `Venue`/`travel_venue`
shipped without a `providerId` or scoped categories.

### Domain decision: a new `Venue` aggregate, not an overloaded `Service`

`Service` (Packet 1A) models a *bookable, timed experience* with priced
options and a cancellation policy — real invariants that do not apply to a
restaurant or café listing (no duration, no per-person price tiers, no
"confirmation type," and cancellation wording means nothing for a walk-in
venue). Forcing dining into `Service` with empty/dummy options would satisfy
the `publish()` gate by lying to it, not by modeling reality — exactly what
`docs/ENGINEERING_CONSTITUTION.md`'s "Generic base services... require
proven repetition and an explicit invariant" warns against. Codex's review
confirmed this call: separate aggregate, separate tables.

**Correction: `Venue` DOES need a `providerId` — the first draft's "no
Provider link" call was wrong.** The original reasoning ("every dining venue
is external, so the DIRECT/PARTNER distinction disappears") conflated two
different things: `FulfilmentModel` (DIRECT vs. PARTNER) genuinely does not
apply to dining, since no venue is ever Sharm To Go operating something
itself — but `Provider` also carries the internal operator/contact record
staff actually need to act on an inquiry, and that need does not disappear
just because the answer is always "partner." A restaurant's legal
operator/brand is not always the same as the physical venue (a hotel
restaurant, a franchise), so `providerId` stays a real, useful field.
Corrected design: `Venue` requires a `providerId` (always set, never null —
unlike `Service`'s conditional one) and drops `FulfilmentModel` entirely
(it would always be `PARTNER`, so the field carries no information).

```kotlin
class Venue(
    val id: VenueId,
    val categoryId: CategoryId,       // staff-defined cuisine/grouping, e.g. "Egyptian Restaurants" — see the category-scoping fix below
    val providerId: ProviderId,       // always set — the real operator/brand staff contact for this venue
    val venueType: VenueType,         // RESTAURANT, CAFE
    val name: LocalizedText,
    val description: LocalizedText,
    val areaCode: String,             // a stable, staff-managed code (e.g. "naama-bay") — see area-as-code fix below
    val areaLabel: LocalizedText,     // the bilingual display text for areaCode
    val address: LocalizedText?,      // optional real street/landmark text, separate from areaCode
    val media: List<VenueMedia>,      // see media-ordering fix below — NOT a bare copy of ServiceMedia
    status: VenueStatus,              // DRAFT -> REVIEW -> APPROVED -> PUBLISHED (+SUSPENDED/ARCHIVED), same shape as Service for one staff mental model
    ...
    // Deliberately NOT included in this first cut, all per the consultation:
    // - priceRange (dropped entirely for now — see below)
    // - openingHours (needs timezone + exceptions + lastVerifiedAt to be honest, or omit and say "confirm via WhatsApp" — not attempted this phase)
    // - dietary/halal/beachfront/outdoor-seating tags (optional, evidence-backed, added only once a venue actually supplies real evidence for one — never inferred, never a bare boolean where false could mean "unknown")
)
```

**Correction: `Category` needs a scope, or Dining leaks into Activities'
navigation.** As flagged above, `PublicCatalogQueryService.listCategories()`
currently returns every `ACTIVE` category with no filter. Fix, decided now
rather than deferred: add a `appliesTo: CatalogVertical` field to `Category`
(`ACTIVITIES`, `DINING` to start — an extensible enum, not a free string),
and scope every category query (staff and public) by it. This is a real,
small migration against the already-committed `travel_category` table
(`ALTER TABLE ... ADD COLUMN applies_to ...` plus a `NOT NULL DEFAULT
'ACTIVITIES'` backfill for existing rows, then drop the default) — done as
part of Dining's own migration, not retroactively editing `V3`.

**Correction: `areaCode` replaces free-text `area`.** The first draft made
area a `LocalizedText`, which cannot reliably back a filter (`"Naama Bay"`
vs. `"naama bay"` vs. Arabic spelling variants) and made the "non-blank
area" publish check redundant (`LocalizedText` already rejects blanks).
Fixed: `areaCode` is a plain staff-managed string key (validated
lowercase-kebab-case, same convention as `Category.code`), with
`areaLabel: LocalizedText` carrying the actual bilingual display text —
the same code/display-text split `Category` already uses for its own `code`
vs. `name`.

**`priceRange` is dropped from this phase entirely**, not shipped as a
placeholder. It is a real customer-visible commercial claim and needs a
real, owner-decided scale/meaning/source before it exists at all — adding
an undecided enum to a migration just to have *something* there is exactly
the kind of invented fact this repository's discipline forbids. Revisit
once the owner actually wants to signal price tier.

**Publish gate, corrected.** The first draft's "one photo + non-blank area"
gate was too thin (area is no longer optional-feeling once required, and
there was no dining analogue to `Service`'s "does this have anything
purchasable" check). The real completeness gate for a venue: `providerId`
resolves to an active provider, `areaCode` resolves to a real staff-defined
area, `categoryId` resolves to an active `DINING`-scoped category, and at
least one rights-cleared `VenueMedia` exists. All four, checked fresh from
the loaded aggregate's own state by the application service, the same
discipline `PublishServiceService` already uses — never a caller-supplied
flag.

**A real gap inherited from `Service`, not repeated here.**
`UpdateServiceService.update()` lets a `PUBLISHED` service be edited into an
incomplete state without demoting it — an accepted, documented gap in 1A,
not something to copy. `UpdateVenueService` should not carry that ambiguity
forward: an update that would leave a `PUBLISHED` venue failing its own
publish gate either re-validates and rejects (safer, chosen here) or
explicitly demotes to `SUSPENDED`. Revisiting `Service`'s own version of
this gap is a separate, later decision — out of scope for Dining.

**Media needs presentation semantics `ServiceMedia` doesn't have.**
`VenueMedia` adds a `displayOrder: Int` (for deterministic gallery/cover
selection — the current `ServiceMedia`/`travel_service_media` has no
ordering column at all, a real gap worth fixing there too but not blocking
Dining) and `altText: LocalizedText` (accessibility/SEO, cheap to add now
rather than retrofit later).

**Public API**: `/api/v1/travel-marketplace/public/venues` (filter by
`venueType`/`categoryId`/`areaCode`), `/api/v1/travel-marketplace/public/venues/{id}`
— same narrow-shape discipline as `PublicServiceResponse` (no internal
fields, no `providerId` — only the provider's public name, mirroring
`operatedBy`). **The WhatsApp destination number and message template are
client configuration (`clients/sharm-to-go/client.manifest.json` or a
sibling config file), never a Travel Marketplace domain field or API
response field** — the product must not hardcode or own a specific client's
contact details. Also worth stating plainly: a `wa.me` link only reaches
Wego's own audit trail if the customer actually sends it — it is a
convenience, not a logged inquiry. Real inquiry auditability inside Wego
itself is Phase 5's job (deferred), not claimed here.

**Schema**: `travel_venue`, `travel_venue_media` (own tables, not reusing
`travel_service`/`travel_service_media`); `travel_category` gains
`applies_to`. `travel_marketplace_audit_event`'s existing named CHECK
constraint (`travel_marketplace_audit_event_aggregate_type_known`) cannot be
altered in place in PostgreSQL — the real forward-fix is `ALTER TABLE ...
DROP CONSTRAINT travel_marketplace_audit_event_aggregate_type_known` then
`ADD CONSTRAINT ... CHECK (aggregate_type IN ('PROVIDER', 'CATEGORY',
'SERVICE', 'VENUE'))` in the same `V4` migration — existing rows already
satisfy the widened list, so this validates immediately with no backfill.
`V3` itself is never edited.

**Correction: `venue:view`/`venue:manage` needs a real justification, not
an invented convention.** The first draft claimed "this repo's one-
permission-pair-per-resource-type convention" — false: `Category` has no
permission pair of its own and is gated by `service:*` today (a real,
already-committed design choice, not an oversight this plan should silently
paper over). `venue:view`/`venue:manage` as a distinct pair is still the
right call here, but on its own merits (dining is a distinct operational
area a dashboard role might reasonably be scoped to independently of
Activities staff), not because of a convention that does not actually
exist in this codebase.

**Real content gate, same as Activities**: no restaurant or café is
published from an invented name, cuisine, photo, or opening-hours claim. A
`VENUE_CONTENT_TEMPLATE.md` (mirroring `design/SERVICE_CONTENT_TEMPLATE.md`)
is needed before any real venue reaches `PUBLISHED`, and per the
consultation's own recommendation should capture **source/verification**
for each fact (operator identity, address, cuisine/features, hours if any,
photo rights) — not just the fact itself. "Unknown" stays valid while
drafting and blocks publication; it is never replaced with plausible Sharm
content, matching `SERVICE_CONTENT_TEMPLATE.md`'s own opening rule.

### Not yet decided — owner input needed before implementation

- Real dashboard role scoping for `venue:*` vs `service:*` permissions —
  `TECHNICAL_EXECUTION_PLAN.md`'s own Packet 1A section already flagged a
  general "real staff role" decision as outstanding; Dining inherits the
  same open question, not a new one.
- Whether `Category.appliesTo` should be a small closed enum (as sketched
  above) or something more extensible — closed enum is the safer default
  until a third vertical actually needs categories, per the same
  evidence-first-abstraction rule this whole plan keeps citing.

## Phase 3 — Accommodation (owner input required before design)

**Correction after design consultation:** the first draft of this section
treated a hotel as "just another `venueType`" on the same `Venue` aggregate
Dining uses. Flagged as the single biggest scope risk in the whole plan —
doing that would recreate, for `Venue`, exactly the premature-generic-
aggregate mistake this plan correctly avoided for `Service`. A hotel has
real structure Dining does not: a property/operator distinction that
matters even in a directory-only model (a hotel *chain* vs. a specific
*property*), amenities, check-in/check-out rules, and (if pricing is shown
at all) rate freshness — none of which a restaurant listing needs. Fixed:
Accommodation gets its own domain decision when it starts, informed by
Dining's pattern (code/display-text area keys, provider link, media
ordering, scoped categories) but **not** by literally adding `HOTEL` to
`VenueType`.

Two real fulfillment models still exist, and the choice changes the design
materially:

- **Model A — partner-listing directory** (recommended starting point: a
  `Venue`-shaped aggregate *of its own*, not a `Venue` variant — real
  partner-supplied description/photos/an indicative "from" price if the
  owner decides one, and WhatsApp inquiry, no live rooms/rates/availability).
  Buildable once real partner hotels exist under agreement.
- **Model B — live inventory integration** (a real channel-manager or OTA
  API integration for real-time rooms/rates/availability) — a materially
  larger, separate technical effort, and one that presupposes real
  commercial partnerships and API access this repository has no visibility
  into yet.

This plan does not choose between them — that is a real business decision
(which partner hotels, on what commercial terms) the owner needs to make
first, the same gate `TECHNICAL_EXECUTION_PLAN.md`'s "What the owner
supplies" section already applies to Activities' own real content.

## Phase 4 — Car rental

Same correction as Accommodation: not a `VenueType` on the shared `Venue`
aggregate — a real rental has its own structure (vehicles, deposits,
licence rules, rental terms) that a restaurant/café model has no reason to
carry. Its own domain decision, informed by Dining's pattern, once real
partner agencies exist under agreement. Listed here for sequencing
completeness only.

## Phase 5 — WhatsApp/social automation

Deliberately not designed here — see "Why booking stays WhatsApp/social,
not a new automation layer" above. When the owner is ready to invest in
this, it needs its own plan grounded in WEGO-003/004/005's already-stated
prerequisites (reliable outbox delivery, consent tracking, audit), not a
retrofit onto whatever ad hoc WhatsApp links exist by then.

## What happens next

Per the owner's approved order, the immediate next implementation step is
Activities Packet 1D (the dedicated mobile catalog), followed by Packet 1E's
real-content publication rehearsal — **before** Dining backend work starts,
not in parallel with it. Packets 1B and 1C are already implemented. This plan's
Dining section exists so its design is ready when Activities reaches the agreed
handoff point, not so implementation jumps ahead of that sequence.
