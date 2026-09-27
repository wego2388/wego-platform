# Phase 0 — current state and baseline

Updated: 2026-09-27

## Repository and isolation

- [x] Sharm To Go has an isolated Spring Boot application.
- [x] Its migration path and database are separate from Sharm Divers Club.
- [x] Travel Marketplace code is absent from the Divers application composition.
- [x] Divers product code/tables/permissions are absent from Sharm To Go.
- [x] Foundry manifests and release lock describe the separate client/product.

## Catalog foundation

- [x] Provider, Category, Service, Option and Media domain models exist.
- [x] Draft/review/approve/publish/suspend/archive workflow exists.
- [x] Staff catalog APIs and permissions exist.
- [x] Narrow unauthenticated public catalog APIs exist.
- [x] OpenAPI contract exists for Sharm To Go.
- [x] ERP screens manage providers, categories and services.
- [x] Website list/detail routes read the public catalog.
- [x] Dedicated mobile catalog app and Android application module exist.
- [x] Research/intake catalog contains 37 structured service concepts.

## Brand and marketing baseline

- [x] Real logo is integrated into web, ERP and Android.
- [x] Public visual identity and dark mode exist.
- [x] Brand direction is recorded in `BRAND_AND_GROWTH_STRATEGY.md`.
- [x] Approved experience/scale story is represented carefully on the website.
- [x] About, FAQ, Contact, Privacy and Terms routes exist.
- [x] Real owner-supplied WhatsApp number and email are centralized.
- [x] Global WhatsApp action exists.
- [x] Sitemap includes public trust pages and published service detail routes.

## What is deliberately not complete

- [ ] Public request/booking persistence.
- [ ] Availability slots and capacity reservation.
- [ ] ERP request queue and lifecycle actions.
- [ ] Real request result/reference page.
- [ ] Mobile network integration and request tracking.
- [ ] Payment collection, refunds or reconciliation.
- [ ] Dining, car rental or accommodation domain implementations.
- [ ] Production hosting, domain, secrets or app-store submission.

## Working-copy safety

- [ ] Agent has run `git status --short --branch` and identified owner changes.
- [ ] Agent has confirmed the active worktree and has not edited the wrong
  checkout.
- [ ] Agent has read `CLAUDE_HANDOFF.md`, `BRAND_AND_GROWTH_STRATEGY.md` and
  `CONVERSION_DELIVERY_PLAN.md`.
- [ ] Agent has run the current quality gate before beginning a risky packet.

The four working-copy boxes are deliberately reset for every new agent/session.
They are session checks, not permanent project achievements.
