# Sharm To Go execution plan

Exactly one authorized packet is active at a time. Each phase below is split
into smaller packets in `TECHNICAL_EXECUTION_PLAN.md`; Claude must start there
for scope, acceptance tests, owner gates, and handoff requirements.

**Planning status, reviewed 2026-08-30:** WEGO-010-A remains `PAUSED` while
WEGO-011 is the sole `ACTIVE` packet. This file does not authorize a board
change, implementation, commit, push, deployment, or production access.

## Phase 0 — composition and honest UI foundation

Already built as planning/foundation output:

- Foundry discovery, client/product manifests, and deterministic release locks;
- the Travel Marketplace product boundary and isolated Sharm To Go profile;
- original Arabic/English public and operations-readiness foundations;
- product, responsibility, locale, content, design, booking, and payment docs;
- repo-owned design tokens, screen inventory, and a non-transactional booking
  prototype with no invented inventory or gateway connection.

The mobile app was intentionally not started.

### Packet 0R — required before Phase 1

Repository review found that both product source trees are still compiled into
one backend and all Flyway migrations still use one global location. Release
locks currently prove metadata composition, not executable runtime isolation.

0R must complete the outstanding Phase 0 Tier 1 review and make each client
lock select exactly its product code, routes, permissions, and migrations. A
fresh Sharm To Go deployment must contain no Divers table, permission, route,
or bean, and the inverse must also be proven. Wrong or stale artifact/lock
combinations must fail closed.

Exit: both client artifacts build reproducibly from their checked-in locks,
boot against real throwaway PostgreSQL, migrate only their selected product,
and pass Foundry/repository checks plus independent Tier 1 review.

## Phase 1 — service catalog

Phase 1 is not one large packet:

1. **1A catalog backend:** OpenAPI, catalog/content/provider domain, product
   migrations, least-privilege permissions, operations API, public projection,
   publication readiness, audit, and deterministic public export.
2. **1B operations dashboard:** documented secure browser session, login/route
   guards, catalog editors, locale/media/provider workflows, and authorization.
3. **1C public website:** SSR Arabic/English category and service list/detail
   using published facts only. Catalog prices are labelled "from" and never
   imply dated availability.
4. **1D dedicated mobile catalog:** new KMP/Android app and a versioned,
   generated public-catalog JSON snapshot. Published services are never copied
   manually into Kotlin source.
5. **1E publication rehearsal:** at least one real, owner-approved service moves
   through publish and suspension across dashboard, website, API, and regenerated
   mobile snapshot. Engineering uses synthetic fixtures until this packet.

Exit: a complete real service can be published and suspended with source,
translation, policy, provider, price, media-rights, audit, and cross-surface
evidence, without accepting a booking.

1E blocks real publication and an external pilot. If launch content is not ready,
the owner may still authorize booking engineering with synthetic fixtures after
1A–1D; no synthetic record may cross into a real environment.

## Phase 2 — availability and booking

Before any PII migration, Packet 2A fixes the privacy/retention policy, exact
confirmation modes, capacity holds/expiry, timezone, guest management
capability, and KMP networking/secure-storage design. Public booking reference
alone is never authorization, and guest checkout does not imply an account.

Then separate packets deliver:

- a capacity ledger/reservation model, immutable customer-visible snapshots,
  idempotent guest create, `NEW → CONFIRMED → COMPLETED` plus cancellation and
  expiry, and real PostgreSQL concurrency proof;
- least-privilege staff booking queue, calendar, detail, and lifecycle actions;
- equivalent website/mobile checkout and manage flows using a high-entropy,
  hashed management capability and the same API contract.

Exit: a booking cannot reserve or confirm more than current capacity, cannot
change its customer-visible snapshot, and cannot be read or changed using only
a guessable public reference. PII is minimized, access-controlled, retained by
an approved policy, and absent from logs/list projections.

## Phase 3 — payment, refund, and reconciliation

Payment is split into Tier 1 packets:

1. provider-neutral payment intent/attempt/refund and hosted-redirect contracts;
2. Paymob sandbox adapter;
3. optional Fawry reference-code adapter when commercial inputs are ready;
4. refund, service-level cash-on-arrival, and CIB settlement reconciliation.

The browser/provider return is never payment truth. Webhooks verify raw
signatures and amount/currency/merchant/order, deduplicate events, tolerate
replay/out-of-order delivery, query provider state, redact payloads, and commit
state plus outbox atomically. No card form is built in website or mobile.

Exit: each activated money transition is idempotent, authorized, audited,
reconciled, independently reviewed, and proven in sandbox. No payout or
commission is inferred from public price.

## Phase 4 — post-launch product increments

Planner, saved/compare, reviews, and every locale/currency expansion are
separate packets. The planner uses published facts and real availability only.
Reviews require an eligible completed booking. Locale expansion follows measured
Arabic/English parity and human approval; currency display requires a source,
rounding, refresh, and expiry policy.

## Phase 5 — isolated deployment and controlled launch

Separate packets cover isolated infrastructure, artifact/lock provenance,
backup/restore and migration recovery, observability/SLOs/alerts, support and
incident runbooks, privacy/retention enforcement, content/accessibility/SEO/
performance/security gates, and signed store builds. Sharm To Go never shares
the Sharm Divers deployment boundary by assumption.

A deployment, store submission, or launch needs explicit owner authorization
separate from implementation completion.

## Immediate owner inputs

- authorize resuming WEGO-010-A only after the current active packet closes;
- provide approved real service templates and rights-cleared assets for 1E;
- approve first staff users and least-privilege role matrix before 1B activation;
- approve stable application id and public app name before 1D;
- approve guest fields, consent, retention, booking confirmation/hold/cancel
  policy, timezone, pickup, and recovery channel before Phase 2 persistence;
- provide signed merchant terms and sandbox credentials only for each payment
  adapter being activated. Credentials never enter this repository.
