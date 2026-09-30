# Safari Tours Sharm — Codex consultation brief

> **لمحمد:** ده الملف اللي تديه لـ Codex. افتح Codex جوه فولدر
> `/home/wego/wego-foundry-isolation` واكتب له:
> «اقرأ clients/safari-tours-sharm/CODEX_REVIEW_BRIEF.md ونفّذ المطلوب في آخره».
> هو هيقرأ ويطلّع تقرير بس، مش هيعدّل حاجة. التقرير ابعته لي، وأنا أراجعه وأنفّذ اللي يستاهل.

Written 2026-10-01 by Claude Code, the engineer who owns this project. It gives you
the full picture in one read. **Read-only review:** do not edit, commit,
push, deploy, or call external services. Produce a report (format at the end).

---

## 1. What this is

A direct-booking system for a real tour operator in Sharm El Sheikh, Egypt
(desert safaris, Red Sea boat trips, Cairo/Luxor trips, airport transfers).
It is 30 tours, prices in **EUR only**, card payment via **Paymob**. It replaces
the operator's old WordPress site. It has not launched yet: no production
server, no real Paymob keys, and no SMTP provider yet (phase H, waiting on the owner).

Three deliverables live in one monorepo (branch `wego-017-foundry-isolated-releases`):

| Part | Path | Stack |
|---|---|---|
| Backend (tours-operator product) | `products/tours-operator/src/main/kotlin/com/wego/toursoperator/` (domain/application/infrastructure/api) | Kotlin, Spring Boot 4, jOOQ, Flyway, PostgreSQL 18 |
| Safari app assembly + migrations | `platform/apps/safari-tours-sharm/`, `platform/application/src/main/resources/db/migration/` | Gradle |
| Public site | `web/apps/safari-tours-sharm-site/` | Nuxt 4 SSR, @nuxtjs/i18n (prefix `/en /ar /ru /it`, Arabic RTL), Tailwind 4, Reka UI |
| Staff ERP | `web/apps/safari-tours-sharm-erp/` | Nuxt 4 |
| Shared API types | `web/packages/api-contract/` (generated from OpenAPI) | TypeScript |
| Runtime | `infrastructure/compose/safari-tours-sharm.compose.yaml`, `infrastructure/nginx/nginx.conf` | Docker Compose + nginx edge |
| E2E | `e2e/tests/safari-checkout.spec.ts` | Playwright |

All backend file names below are under `products/tours-operator/src/main/kotlin/com/wego/toursoperator/` (note: `products/divers` has files with the same names — don't mix them up).

Governance and history: `docs/execution/WEGO_EXECUTION_BOARD.md` (WEGO-016 sections),
owner roadmap `clients/safari-tours-sharm/ROADMAP_AR.md`, frontend plan
`clients/safari-tours-sharm/design/FRONTEND_MASTER_PLAN_AR.md`, repo rules `AGENTS.md`,
`docs/ENGINEERING_CONSTITUTION.md`.

Gates (all green on CI run 36763813022, 2026-09-30):
`scripts/safari-tours-sharm-check.sh`, `scripts/repository-check.sh`,
`cd web && pnpm run check`, backend tests `./gradlew :platform:apps:safari-tours-sharm:test`.

## 2. Customer flow (end to end)

1. The site lists tours (SSR, `useCatalog`), and the tour page `/{locale}/tour/{slug}` shows published content and a booking card.
2. Availability comes from `GET /api/v1/tours-operator/tours/{id}/slots` and is loaded live in the browser.
3. `/{locale}/booking/{slotId}` collects customer details, then `POST /bookings` creates the booking (capacity is reserved).
4. `POST /payments/initiate` creates the Paymob intention, and the customer pays on Paymob.
5. Paymob calls back `POST /payments/paymob-callback`, which is HMAC-verified and idempotent. The payment is confirmed, the booking becomes CONFIRMED, and a notification is queued.
6. The customer lands on `/booking/payment-result`, which **polls the server** (it never trusts the query string), then `/booking/confirmation`.
7. Unpaid bookings expire (`ExpireOverduePaymentsService`, `BookingExpiryScheduler`) and release capacity.
8. Staff use the ERP: bookings, cancellations and refunds, the payment ledger, history, content publishing, and notifications.

## 3. Sensitive areas — please look hardest here

### 3.1 Money (highest priority)
- `domain/Money.kt`, `domain/BookingPricing.kt`: amounts are decimals with exactly 2 places and never floats. The API sends money as `{amount:"45.00", currencyCode}`.
- Server-side price recomputation in `CreateBookingService.kt`: the client total must never be trusted.
- `HandlePaymobWebhookService.kt`, `PaymobHttpClient.kt`, `MockPaymobClient.kt`: HMAC field order, idempotency on replays, amount/currency match, wrong-order or duplicate callbacks, and a payment arriving after expiry.
- `InitiatePaymentService.kt`, `ExpireOverduePaymentsService.kt`: the race between expiry and a late successful payment.
- Refunds and cancellation policy: `CancelBookingService.kt`, `domain/CancellationPolicy.kt` (STANDARD: ≥48h 100%, 24–48h 50%, <24h 0; FLEXIBLE; NON_REFUNDABLE). Timezone correctness (operator time is Africa/Cairo).
- Payment ledger and finance: V20 (`revenue_recognised_at`), V21 (`payment_audit_event`), `PaymentQueryService.kt`, and the ERP finance composable (net of refunds).

### 3.2 Capacity and concurrency
- `JooqTourSlotRepository.kt`, `JooqBookingRepository.kt`: overbooking under concurrent `POST /bookings`, and capacity release on cancel or expiry.
- `DispatchNotificationsService.kt`: `FOR UPDATE SKIP LOCKED` queue, retries, FAILED state, and a booking-state recheck before sending.

### 3.3 Customer data (PII) and security
- Customer name, phone, email and hotel. The public booking lookup `POST /bookings/lookup` (reference + phone) is rate-limited in nginx (`booking_lookup_rate` 10r/m). Check enumeration and timing leaks.
- PII must never appear in URLs, logs or analytics. CI greps compose logs for this (`.github/workflows/ci.yml`, step "Verify Safari checkout logs…"). After checkout, the booking confirmation is handed over in sessionStorage, not the URL. The tour→checkout handoff puts only non-personal data in the query (`adults, children, tourId, date, timeSlot`).
- Staff auth, roles and permissions (`platform/` identity, permissions such as `tours-operator.content:publish`, `notification:manage`). Look for IDOR on staff endpoints and for missing permission checks.
- nginx headers, CSP, and cookie flags. The site sets `sts_locale` and `sts_theme` cookies (non-sensitive).
- Email templates (`NotificationTemplates.kt`): header injection and HTML escaping of customer-provided fields.

### 3.4 Database migrations
- V20–V23 (`platform/application/src/main/resources/db/migration/`). They are registered in several places: both Gradle builds, `foundry/catalog/release-profiles.json`, and `ProductIsolationIntegrationTest`. Check that they are safe on a populated DB, that backfills are correct, and that constraints and indexes fit the hot queries (ledger keyset by id, slots by tour and date).

### 3.5 Content publishing (UX-0, `TourContent*.kt`, V23)
- DRAFT to PUBLISHED requires the reviewed `revision` fingerprint (409 `draft_changed`). Media rights: only APPROVED media is public, and approval is kept only when the revision is unchanged.
- Check that the public endpoint `GET /tours/by-slug/content` can never leak DRAFT content or unapproved media.

### 3.6 Public site (Nuxt SSR)
- `app/composables/useCatalog.ts`: SSR fetches the backend at `NUXT_API_INTERNAL_BASE` with a module-level 60 s cache keyed by URL. Check for cross-request leakage and for memory growth.
- HTML is not cached, because it carries a per-visitor theme and language.
- `server/middleware/locale-prefix.ts`, `server/utils/localeRedirect.ts`: redirects of language-less URLs. Check open-redirect and cache poisoning.
- `app/composables/useTourFilters.ts`: URL query parsing (whitelisted).
- New tour page and booking card (UX-3, in progress): `app/pages/tour/[slug].vue`, `app/components/tour/*`, `app/utils/availability.ts`. The calendar uses Africa/Cairo "today", and the checkout handoff passes `slotId` + query (adults, children, tourId, date, timeSlot). The server must re-validate everything.

## 4. Known open items (don't re-report these as new)
- Phase H not started: real Paymob keys, server, domain, SMTP provider, backups (owner inputs).
- The DB seed (V17) still has old prices. `clients/safari-tours-sharm/content-research/approved-catalog.json` `revisions` holds the owner-approved changes, and a data migration is pending.
- Tour content: EN drafts are approved but not imported. AR/RU/IT translations are pending, and RU/IT UI copy needs a native speaker.
- Real photos and the logo are not delivered yet, so branded placeholders are used. Old WordPress `imageUrl` thumbnails are intentionally not shown.
- Interactive map deferred until stop coordinates exist. Per-vehicle airport transfer pricing (sedan €15 / SUV €20 / Hiace €35) is not modelled yet (UX-4); until then the site shows transfers as request-on-WhatsApp, because checkout would charge per person.
- `resend` of a confirmation for a past tour, and resend history (G nits).

## 5. Constraints on recommendations
- Must stay independent: no shared SaaS tenancy, no dependency on other client apps in the repo.
- No new paid services unless clearly justified. Owner is non-technical; prefer simple, robust solutions.
- EUR only; four languages; Arabic RTL must stay first-class.

## 6. What we want from you

Review with **production launch in mind**. Output a single Markdown report:

1. **Critical / High findings first.** Each finding needs:
   - `file:line`
   - the concrete failure scenario (inputs or state → wrong result)
   - why it matters (money, PII, overbooking, data loss, security)
   - a minimal fix
2. **Medium / Low** findings in the same format, kept brief.
3. **Professional improvements** (max 10, ranked by value/effort). These cover architecture, testing gaps (especially concurrency and payment races), observability, operations and launch readiness.
4. **Test gaps:** specific test cases that should exist but don't.
5. One-line verdict: what must be fixed before launch.

Verify each claim against the code before reporting it: no speculative findings without a reproduction path. Skip style nits.
