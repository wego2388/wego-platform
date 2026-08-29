# Sharm To Go execution plan

Exactly one packet is active at a time. Each phase below becomes an authorized
packet only after the prior phase has executable evidence and the owner approves
the business decisions it needs.

## Phase 0 — composition and honest UI foundation (current)

- Multi-product/client Foundry discovery and deterministic locks.
- Travel Marketplace product boundary and isolated Sharm To Go profile.
- Original ar/en public foundation and ar/en operations-readiness dashboard.
- Marketplace responsibility, locale and content governance documentation.
- Repo-owned design system, screen inventory and interactive booking/payment
  prototype with no business or gateway connection.

Exit: both current clients compose independently; all builds/tests pass; no
surface claims live inventory or deployment.

## Phase 1 — service catalog

- Enter the first real categories and services through one reusable model.
- Catalog/content/provider aggregates, Flyway schema and scoped permissions.
- Draft/review/publish workflow with source/media rights and locale status.
- Dashboard screens backed by real APIs; public list/detail backed only by
  published facts.

Exit: one real approved service can be published and suspended with audit,
without yet accepting a booking.

## Phase 2 — normal booking flow

- Calendar/slots, capacity, price snapshot, customer details, idempotency and
  simple `NEW → CONFIRMED → COMPLETED` lifecycle plus cancellation.
- Instant confirmation for controlled availability; staff confirmation for
  services that need a check. Provider workflow remains internal.
- Customer checkout/manage pages and staff booking screens.
- Concurrency tests against real PostgreSQL and notification tasks without
  making an external channel the workflow authority.

Exit: a booking cannot become `CONFIRMED` without current capacity and an
immutable customer-visible price; no cross-client reads are possible.

## Phase 3 — payment, refund and settlement

- Paymob hosted checkout for enabled cards/wallets, Fawry reference-code option,
  CIB settlement-account reconciliation, and service-level cash-on-arrival.
- Money snapshots, provider callbacks, separate sensitive permissions, reasons,
  reconciliation and refunds. Provider statements follow only when needed.

Exit: every money transition is idempotent, authorized, audited and reconciled;
no payout is inferred from public price.

## Phase 4 — planner, saved/compare, reviews and locale expansion

- Planner uses published catalog facts only.
- Saved/compare and booking-eligible verified reviews.
- Add public locales one at a time after parity and human approval.
- Currency display only after rate-source/rounding/expiry policy.

## Phase 5 — operations and controlled launch

- Retention/consent, backups/restore, observability, support access, incident and
  provider outage runbooks.
- Accessibility, performance, SEO/schema metadata, content QA and launch smoke.
- Isolated Sharm To Go deployment; no change to Sharm Divers deployment.

## Immediate owner inputs

The next business packet needs one or more real service data sets using the
eight simple fields in `SERVICE_OWNERSHIP.md`. Merchant sandbox credentials are
needed only when Phase 3 starts and must never be committed to this repository.
