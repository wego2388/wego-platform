# Sharm To Go execution plan

Exactly one packet is active at a time. Each phase below becomes an authorized
packet only after the prior phase has executable evidence and the owner approves
the business decisions it needs.

**See `TECHNICAL_EXECUTION_PLAN.md`** for the full engineering-detail version
of every phase below — real schema, real endpoints, real screens across
website/dashboard/mobile app, and the architecture decision to build a new
dedicated mobile app (`mobile/apps/sharm-to-go`) reusing Sharm Divers Club's
proven app architecture, not its content. Added 2026-08-30 at the owner's
request; this file stays the short overview, that file is where a session
picking up a phase should actually start reading.

## Phase 0 — composition and honest UI foundation

- Multi-product/client Foundry discovery and deterministic locks.
- Travel Marketplace product boundary and isolated Sharm To Go profile.
- Original ar/en public foundation and ar/en operations-readiness dashboard.
- Marketplace responsibility, locale and content governance documentation.
- Repo-owned design system, screen inventory and interactive booking/payment
  prototype with no business or gateway connection.
- Mobile app not started in Phase 0 (added to this plan 2026-08-30) — Phase 1
  is where it begins, as a new dedicated module, not a later add-on.

Exit: both current clients compose independently; all builds/tests pass; no
surface claims live inventory or deployment.

**Packet 0R (2026-09-02, self-verified, independent Tier 1 review
outstanding):** the two clients now compose independently as real, separate,
running backends (`platform/apps/sharm-to-go`, its own migrations, its own
permission catalog), not only as Foundry manifests — see
`TECHNICAL_EXECUTION_PLAN.md`'s own Packet 0R section and the WEGO-010-A
execution-board entry for the full evidence.

## Phase 1 — service catalog

- Enter the first real categories and services through one reusable model.
- Catalog/content/provider aggregates, Flyway schema and scoped permissions.
- Draft/review/publish workflow with source/media rights and locale status.
- Dashboard screens backed by real APIs; public list/detail backed only by
  published facts.
- New dedicated mobile app module (`mobile/apps/sharm-to-go`, real KMP
  jvm/android/iOS targets, own real design tokens): Home + Experiences list +
  Service Detail, published catalog shipped as bundled app data (no live
  network call needed yet — see `TECHNICAL_EXECUTION_PLAN.md` for why that's
  the right Phase 1 scope, not a shortcut).

Exit: one real approved service can be published and suspended with audit,
without yet accepting a booking; the mobile app's catalog screens render the
same real published services the website and dashboard show.

**Packet 1A (2026-09-02, self-verified; owner accepted that evidence):** the
backend half of this phase is done — catalog/provider
domain, schema, permissions, full staff CRUD + publish workflow, and the
public projection, all proven live against a real database. Packet 1B's real
staff dashboard and Packet 1C's public catalog list/detail are also implemented
and self-verified. Mobile (1D) has not started, and no real service has passed
the publication rehearsal (1E) — see
`TECHNICAL_EXECUTION_PLAN.md`'s Packet 1A section and the WEGO-010-A board
entry for the full evidence.

## Phase 2 — normal booking flow

- Calendar/slots, capacity, price snapshot, customer details, idempotency and
  simple `NEW → CONFIRMED → COMPLETED` lifecycle plus cancellation.
- Instant confirmation for controlled availability; staff confirmation for
  services that need a check. Provider workflow remains internal.
- Customer checkout/manage pages and staff booking screens.
- Mobile app's first real network integration: real checkout screens mirroring
  the website's step order, guest checkout only (no account system yet).
- Concurrency tests against real PostgreSQL and notification tasks without
  making an external channel the workflow authority.

Exit: a booking cannot become `CONFIRMED` without current capacity and an
immutable customer-visible price; no cross-client reads are possible; the
mobile app can complete the same real booking the website can.

## Phase 3 — payment, refund and settlement

- Paymob hosted checkout for enabled cards/wallets, Fawry reference-code option,
  CIB settlement-account reconciliation, and service-level cash-on-arrival.
- Money snapshots, provider callbacks, separate sensitive permissions, reasons,
  reconciliation and refunds. Provider statements follow only when needed.
- Mobile app gets the same hosted-checkout flow (provider-hosted payment page
  in an in-app browser tab, never a card form built in-app — see
  `PAYMENT_FOUNDATION.md`'s security boundary).

Exit: every money transition is idempotent, authorized, audited and reconciled;
no payout is inferred from public price.

## Phase 4 — planner, saved/compare, reviews and locale expansion

- Planner uses published catalog facts only.
- Saved/compare and booking-eligible verified reviews.
- Add public locales one at a time after parity and human approval.
- Currency display only after rate-source/rounding/expiry policy.
- Same features reach the mobile app once proven on web/dashboard first —
  never launched mobile-first, so lessons from the web version's real usage
  inform the mobile design instead of guessing twice.

## Phase 5 — operations and controlled launch

- Retention/consent, backups/restore, observability, support access, incident and
  provider outage runbooks.
- Accessibility, performance, SEO/schema metadata, content QA and launch smoke.
- Isolated Sharm To Go deployment; no change to Sharm Divers deployment.
- Real app-store listing (Google Play at minimum; App Store needs a Mac/CI
  path this repository's current dev box doesn't have — same real constraint
  already documented for the Sharm Divers Club mobile app).

## Immediate owner inputs

The next business packet needs one or more real service data sets using the
eight simple fields in `SERVICE_OWNERSHIP.md` (template in
`design/SERVICE_CONTENT_TEMPLATE.md`). Merchant sandbox credentials are
needed only when Phase 3 starts and must never be committed to this repository.

Also needed before Phase 1's mobile work starts (added 2026-08-30): the real
customer-facing app name/icon for the new dedicated mobile app, and a decision
on whether Sharm To Go's first dashboard staff accounts should be a role
scoped separately from `platform-admin` — see `TECHNICAL_EXECUTION_PLAN.md`'s
"What the owner supplies" section for the full list.
