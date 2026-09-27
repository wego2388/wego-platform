# Safari Tours Sharm — client README

## Start here

هذا الملف يشرح حدود المنتج فقط. حالة التنفيذ الحالية ومهام أي Agent موجودة في:

1. `docs/execution/WEGO_EXECUTION_BOARD.md` — التفويض والـpacket الوحيد `ACTIVE`.
2. `clients/safari-tours-sharm/handoff/README.md` — نقطة دخول أي Agent.
3. `clients/safari-tours-sharm/handoff/SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`
   — النواقص والمراحل وعلامات `[x]` / `[ ]` / `[!]`.
4. `clients/safari-tours-sharm/content-research/README.md` — محتوى الموقع
   القديم وقواعد المراجعة قبل النقل والنشر.

لا تعتمد على عناوين المراحل التاريخية أدناه لتحديد الجاهزية التجارية. الحالة
الحالية `NO-GO`، و`WEGO-016-A` هو الـsub-packet الجاري حتى تُغلق بوابات الـbaseline.

## What this is

Safari Tours Sharm is a **single-operator tours company** based in Sharm El
Sheikh, Egypt. It owns and operates all its tours directly — no provider
onboarding or commission settlement is needed.

This client is deployed on the **`wego-tours-operator`** product — a new
product boundary separate from `wego-divers` (dive center) and
`wego-travel-marketplace` (multi-provider marketplace).

## Platform composition

```
Wego Platform
└── wego-tours-operator (product)
    └── safari-tours-sharm (isolated client deployment)
```

## Isolation rules

- Separate PostgreSQL database — no shared tables with any other client
- Separate Paymob account — no shared payment credentials
- Separate deployment instance — `ISOLATED_INSTANCE`
- Marketing data lives in `projects/clients/safari-tours-sharm/` — it is
  reference material only and is never imported directly into platform code
- Legacy WordPress content is preserved under `content-research/` as research
  evidence only; it is not a runtime dependency or automatic publication source

## Languages and locales

| Code | Language | Priority |
|------|----------|----------|
| en | English | 1 — default |
| ru | Russian | 2 — critical (35% of Sharm visitors) |
| ar | Arabic | 3 — important (RTL) |
| it | Italian | 4 — Phase 1 |

Arabic is the only RTL locale (`dir="rtl"` on `<html>` when `locale === "ar"`).

## Implementation phases

- Phase 1 — Foundation (this packet): manifests, design tokens, directory structure
- Phase 2 — Backend: domain model (Tour, Availability, Booking, Payment, Review, Notification)
- Phase 3 — Public website: Nuxt app in `web/apps/safari-tours-sharm-site`
- Phase 4 — Staff dashboard: ERP in `web/apps/safari-tours-sharm-erp`
- Phase 5 — Mobile app: Kotlin Multiplatform

## Reference files

```
Marketing data:
  projects/clients/safari-tours-sharm/data/approved-facts.json  ← tour content source of truth

Legacy WordPress snapshot:
  clients/safari-tours-sharm/content-research/legacy-wordpress-export.json
  clients/safari-tours-sharm/content-research/README.md

Execution truth:
  docs/execution/WEGO_EXECUTION_BOARD.md
  clients/safari-tours-sharm/handoff/SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md

Platform patterns:
  clients/sharm-divers-club/   ← single-operator reference (closest analogue)
  clients/sharm-to-go/         ← design doc structure reference

Engineering rules:
  docs/ENGINEERING_CONSTITUTION.md
  docs/architecture/BOUNDARIES.md
  docs/architecture/SECURITY_MODEL.md
```

## Current status

> الخلاصة التنفيذية هنا فقط؛ التفاصيل والأدلة في ملف التسليم. وجود UI أو API
> لا يساوي production-ready.

Foundation: **implemented; baseline gate still open**
- [x] `product.manifest.json` — `wego-tours-operator` registered
- [x] `client.manifest.json` — validated against foundry schema
- [x] `release.lock.json`
- [x] `design/tokens.json` — brand tokens (Ocean Deep + Gold + Sunset)
- [x] `design/BRAND_AND_ASSETS.md`
- [x] `design/SCREEN_CATALOG.md`
- [x] `design/BOOKING_AND_CHECKOUT.md`
- [x] `design/DASHBOARD.md`
- [ ] Full repository web check and WEGO-016-A review closure

Backend and contract: **in progress**
- [x] Flyway V14 foundation: Tour, TourSlot, Booking
- [x] Spring Modulith product module: `product.tours-operator`
- [x] Booking lifecycle baseline tests — 330 backend tests green
- [ ] OpenAPI contract + generated/contract types
- [ ] Production content model, Tour/Slot management, and approved import
- [ ] Payment aggregate, expiry worker, and reconciliation
- [ ] Paymob integration wiring

Public website: **UI baseline implemented; commercial flow pending**
- [x] `web/apps/safari-tours-sharm-site` — Nuxt app builds and its local tests pass
- [x] Legacy WordPress content captured under `content-research/`
- [ ] Live contract/catalog/availability/checkout integration
- [ ] Owner-approved logo, media rights, prices, policies, and translations

Staff dashboard: **UI baseline implemented; live operations pending**
- [x] `web/apps/safari-tours-sharm-erp/` created
- [x] `package.json`, `nuxt.config.ts`, `vitest.config.ts`
- [x] Brand tokens + CSS (`main.css`) — Ocean Deep / Gold / Sunset palette
- [x] `useAuthSession` composable (sessionStorage, permissions)
- [x] `useToursApi` composable — full API client for all tours-operator endpoints
- [x] `app/error.vue` — branded 404/403/500 error page
- [x] `pages/login.vue` — sign-in with permission-aware redirect
- [x] `pages/index.vue` — Overview: KPI cards, recent bookings table, full nav
- [x] `pages/bookings.vue` — list with status/date/tour filters, confirm/cancel/complete actions
- [x] `pages/bookings/[id].vue` — full booking detail + all action buttons
- [x] `pages/tours.vue` — tours table with "View slots →" link per row
- [x] `pages/tours/[id]/slots.vue` — weekly calendar grid, slot availability per time slot
- [x] `pages/finance.vue` — revenue KPIs, chart placeholder, revenue-by-tour table
- [x] `pages/customers.vue` — derived from bookings, searchable, filterable by nationality
- [x] `pages/reviews.vue` — Phase 5 placeholder with coming-soon state
- [x] `pages/notifications.vue` — notification center, unread badge, mark-as-read
- [x] `pages/settings.vue` — 5 tabs: Company, Paymob, WhatsApp templates, Cancellation Policy, Users
- [x] `test/erp.spec.ts` — 27 unit tests: auth, API error, booking transitions, customers, notifications, finance, calendar, reference format
- [x] Build ✅ · TypeCheck ✅ · Tests 27/27 ✅
- [ ] API wiring live (requires Phase 2 backend running on :8080)

Mobile app: **out of scope for first launch**
