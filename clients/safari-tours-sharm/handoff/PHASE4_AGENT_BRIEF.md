# SAFARI TOURS SHARM — Phase 4 Agent Brief
# Staff Dashboard (ERP)
# ══════════════════════════════════════════════════════════════════
# اقرأ هذا الملف كاملاً قبل أي شيء آخر.
# يحتوي على كل ما يلزمك لتنفيذ Phase 4 من الصفر.
# ══════════════════════════════════════════════════════════════════

---

## 1. ما تم إنجازه (Phases 1–3)

### Phase 1 — Foundation ✅
```
/home/wego/wego-platform/
├── foundry/catalog/modules.json         ← أضيف product.tours-operator
├── products/tours-operator/
│   └── product.manifest.json
└── clients/safari-tours-sharm/
    ├── client.manifest.json             ← validated ✅
    ├── release.lock.json                ← generated ✅
    └── design/
        ├── tokens.json                  ← Ocean/Coral/Sand/Sunset/Palm
        ├── BRAND_AND_ASSETS.md
        ├── SCREEN_CATALOG.md
        ├── BOOKING_AND_CHECKOUT.md
        └── DASHBOARD.md
```

### Phase 2 — Backend Domain Model ✅
```
/home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/
├── domain/
│   ├── Tour.kt, TourId.kt, TourSlot.kt, TourSlotId.kt
│   ├── Booking.kt, BookingId.kt, BookingPricing.kt
│   ├── BookingStatus.kt      ← NEW → CONFIRMED/CANCELLED/COMPLETED/EXPIRED
│   ├── PaymentStatus.kt      ← PENDING → PAID/FAILED/REFUNDED
│   ├── PaymentMethod.kt      ← CARD/VODAFONE_CASH/FAWRY/APPLE_PAY/GOOGLE_PAY
│   ├── TourCategory.kt       ← DESERT/SEA/CULTURAL/SHOWS/TRANSFERS
│   ├── TimeSlot.kt           ← SUNRISE/MORNING/AFTERNOON/SUNSET
│   ├── CustomerContact.kt    ← fullName, phone, nationality (ISO-2), email?
│   └── Money.kt              ← EUR only, scale-2 enforced
├── application/
│   ├── TourRepository.kt, TourSlotRepository.kt, BookingRepository.kt
│   ├── BookingAuditRecorder.kt, TransactionRunner.kt
│   ├── CreateBookingService.kt    ← slot lock → capacity → STR-YYYY-N ref
│   ├── ConfirmBookingService.kt   ← idempotent (Paymob webhook safe)
│   ├── CancelBookingService.kt    ← releases slot seat
│   ├── CompleteBookingService.kt
│   ├── ExpireBookingService.kt    ← 30-min timeout, releases seat
│   └── *QueryService.kt
├── infrastructure/
│   ├── ToursOperatorBeanConfiguration.kt
│   ├── ToursOperatorSpringTransactionRunner.kt
│   └── JooqBookingAuditRecorder.kt
└── api/
    ├── TourController.kt         GET /api/v1/tours-operator/tours
    ├── TourSlotController.kt     GET /api/v1/tours-operator/tours/{id}/slots
    ├── BookingController.kt      POST/GET /api/v1/tours-operator/bookings
    └── ToursOperatorDtos.kt

Migration:
/home/wego/wego-platform/platform/application/src/main/resources/db/migration/V9__tours_operator_foundation.sql
Tables: tours_operator_tour, tours_operator_tour_slot,
        tours_operator_booking_reference_seq, tours_operator_booking,
        tours_operator_booking_audit_event
Permissions registered: tours-operator.booking:view/cancel/complete/payment-update
                        tours-operator.tour:manage/view, tours-operator.slot:manage
```

### Phase 3 — Public Website ✅
```
/home/wego/wego-platform/web/apps/safari-tours-sharm-site/
├── nuxt.config.ts         ← Nuxt 4, Tailwind v4, @wego/design-tokens
├── app/
│   ├── app.vue, error.vue
│   ├── assets/css/main.css  ← brand tokens (sts-ocean/coral/sand/sunset/palm)
│   ├── content/locales.ts   ← EN/RU/AR/IT complete + directionFor() + categoryMeta
│   ├── composables/
│   │   ├── useSiteLocale.ts  ← 4-lang, localStorage, syncs <html dir/lang>
│   │   ├── useScrollReveal.ts, useCountUp.ts, useScrolled.ts
│   ├── components/
│   │   ├── SiteHeader.vue  ← transparent→solid, 4-lang switcher, RTL, mobile menu
│   │   ├── SiteFooter.vue
│   │   └── WhatsAppFab.vue
│   └── pages/
│       ├── index.vue           ← Hero + Trust Bar + Categories + How + Cancellation
│       ├── tours.vue           ← /tours (5 categories)
│       ├── category/[slug].vue ← /category/desert|sea|cultural|shows|transfers
│       ├── contact.vue
│       ├── privacy.vue, terms.vue
├── test/locales.spec.ts    ← 5 tests PASSED ✅
Build: ✅ TypeCheck: ✅
```

---

## 2. مهمتك — Phase 4: Staff Dashboard

### الـ app الجديد
```
/home/wego/wego-platform/web/apps/safari-tours-sharm-erp/
```

اسم الـ package: `@wego/safari-tours-sharm-erp`

### الـ API base
```
/api/v1/tours-operator/
```

---

## 3. Reference implementations — اقرأها قبل تكتب سطر

### الـ ERP الموجود (wego-divers staff dashboard):
```
/home/wego/wego-platform/web/apps/erp/
```
هذا هو أقرب مثال. اقرأ:
- `app/composables/useAuthSession.ts` ← auth pattern (sessionStorage, permissions)
- `app/composables/useDiversApi.ts`   ← API client pattern
- `app/pages/bookings.vue`            ← bookings page pattern
- `app/pages/login.vue`               ← login flow
- `app/assets/css/main.css`           ← design tokens pattern
- `nuxt.config.ts`                    ← proxy config

### الـ UI library:
```
/home/wego/wego-platform/web/packages/ui/src/
  WegoButton.vue, WegoInput.vue, WegoAlert.vue, WegoFoundationCard.vue
```

### الـ design tokens:
```
/home/wego/wego-platform/web/packages/design-tokens/src/tokens.css
```

### Brand tokens للـ dashboard:
```
/home/wego/wego-platform/clients/safari-tours-sharm/design/tokens.json
```
الألوان: sts-ocean (#0A2342), sts-coral (#FF6B6B), sts-sand (#C8A97E)

---

## 4. API Endpoints المتاحة

### Tours
```
GET  /api/v1/tours-operator/tours                  ← list (activeOnly, category, page, size)
GET  /api/v1/tours-operator/tours/{id}
GET  /api/v1/tours-operator/tours/by-slug/{slug}
```

### Slots
```
GET  /api/v1/tours-operator/tours/{tourId}/slots   ← from, to (date range)
GET  /api/v1/tours-operator/tours/{tourId}/slots/by-date?date=
```

### Bookings
```
POST /api/v1/tours-operator/bookings               ← create (public, no auth)
POST /api/v1/tours-operator/bookings/{id}/confirm  ← payment-update permission
POST /api/v1/tours-operator/bookings/{id}/cancel   ← cancel permission
POST /api/v1/tours-operator/bookings/{id}/complete ← complete permission
GET  /api/v1/tours-operator/bookings               ← list (tourId, status, date, page, size)
GET  /api/v1/tours-operator/bookings/{id}
GET  /api/v1/tours-operator/bookings/lookup?reference=&phone=  ← public
```

### Auth (platform — same as divers ERP)
```
POST /api/v1/identity/sessions   ← login
DELETE /api/v1/identity/sessions ← logout
```

### Permissions used in dashboard
```
tours-operator.booking:view
tours-operator.booking:cancel
tours-operator.booking:complete
tours-operator.booking:payment-update
tours-operator.tour:manage
tours-operator.tour:view
tours-operator.slot:manage
```

---

## 5. Dashboard Pages to Build

### P0 — Required for launch

| Page | Route | Notes |
|------|-------|-------|
| Login | `/login` | Same pattern as erp/pages/login.vue |
| Overview | `/` | KPI cards: today bookings, today revenue, pending confirms, upcoming; recent bookings list |
| Bookings list | `/bookings` | Filter by status/date/tour; table with status badges; confirm/cancel/complete actions |
| Booking detail | `/bookings/[id]` | Full booking info; all actions; customer contact |
| Tours list | `/tours` | Table: name, category, price, capacity, status toggle |

### P1 — After launch

| Page | Route | Notes |
|------|-------|-------|
| Tour slots | `/tours/[id]/slots` | Calendar view of slot availability |
| Finance | `/finance` | Revenue summary, bookings by tour |
| Settings | `/settings` | Company info (read-only for now) |

---

## 6. Booking Status & Actions

```
Status: NEW | CONFIRMED | COMPLETED | CANCELLED | EXPIRED

Allowed transitions per status:
  NEW       → [Confirm] requires payment-update permission
              [Cancel]  requires cancel permission
  CONFIRMED → [Complete] requires complete permission
              [Cancel]   requires cancel permission
  COMPLETED → no actions
  CANCELLED → no actions
  EXPIRED   → no actions
```

Status badge colors (match brand):
```
NEW       → warning (amber)
CONFIRMED → success (green)
COMPLETED → info (blue)
CANCELLED → danger (red)
EXPIRED   → muted (gray)
```

---

## 7. Tech Stack (same as existing ERP)

```
Framework:    Nuxt 4 + Vue 3 + TypeScript
Styling:      Tailwind CSS v4 + @wego/design-tokens + brand tokens
Components:   @wego/ui (WegoButton, WegoInput, WegoAlert, WegoFoundationCard)
Icons:        inline SVG (same as erp/ — no icon library dependency)
Auth:         sessionStorage bearer token (readAuthSession / writeAuthSession)
API:          fetch() relative paths (/api/...) — Vite proxy to localhost:8080
Tests:        Vitest + happy-dom
```

---

## 8. File Structure to Create

```
web/apps/safari-tours-sharm-erp/
├── package.json            ← name: @wego/safari-tours-sharm-erp
├── nuxt.config.ts          ← proxy /api → localhost:8080
├── tsconfig.json           ← extends ./.nuxt/tsconfig.json
├── vitest.config.ts
├── public/
│   └── favicon.svg
├── test/
│   └── *.spec.ts
└── app/
    ├── app.vue
    ├── assets/css/main.css  ← brand tokens + erp layout
    ├── composables/
    │   ├── useAuthSession.ts   ← copy & adapt from erp/
    │   └── useToursApi.ts      ← API client for tours-operator endpoints
    └── pages/
        ├── login.vue
        ├── index.vue           ← overview
        ├── bookings.vue        ← bookings list
        ├── bookings/[id].vue   ← booking detail
        └── tours.vue           ← tours list
```

---

## 9. Add to Workspace

بعد إنشاء الـ app، أضفه لـ:
```
/home/wego/wego-platform/web/package.json
```
في scripts: prepare, typecheck, test, build — نفس pattern الـ safari-tours-sharm-site.

---

## 10. Validation Commands

```bash
# Foundry validation (يجب تمرير بدون أخطاء)
cd /home/wego/wego-platform/foundry && node scripts/validate-manifests.mjs

# Tests
cd /home/wego/wego-platform/web && pnpm --filter @wego/safari-tours-sharm-erp test

# Build
cd /home/wego/wego-platform/web && pnpm --filter @wego/safari-tours-sharm-erp build
```

---

## 11. Rules (من ENGINEERING_CONSTITUTION.md)

```
❌ لا تضع credentials أو secrets في الكود — استخدم env vars
❌ لا تستخدم GET للعمليات التي تغير state
❌ لا تعمل admin operations بدون فحص الـ permission أولاً
❌ لا تعرض booking IDs الداخلية (UUID) في الـ UI — استخدم reference (STR-YYYY-N)
✅ كل button/action يتحقق من hasPermission() قبل الظهور
✅ sessionStorage للـ token (ليس localStorage)
✅ نفس error handling pattern الموجود في erp/pages/bookings.vue
✅ TypeScript strict mode
✅ اكتب tests قبل تعلن الـ build ناجح
```

---

## 12. Reference Files — اقرأها بالترتيب

```bash
# 1. Auth pattern
cat /home/wego/wego-platform/web/apps/erp/app/composables/useAuthSession.ts

# 2. API client pattern
cat /home/wego/wego-platform/web/apps/erp/app/composables/useDiversApi.ts

# 3. Bookings page pattern (أهم ملف)
cat /home/wego/wego-platform/web/apps/erp/app/pages/bookings.vue

# 4. Login page pattern
cat /home/wego/wego-platform/web/apps/erp/app/pages/login.vue

# 5. CSS pattern
cat /home/wego/wego-platform/web/apps/erp/app/assets/css/main.css

# 6. nuxt.config pattern
cat /home/wego/wego-platform/web/apps/erp/nuxt.config.ts

# 7. package.json pattern
cat /home/wego/wego-platform/web/apps/safari-tours-sharm-site/package.json

# 8. Dashboard design spec
cat /home/wego/wego-platform/clients/safari-tours-sharm/design/DASHBOARD.md

# 9. Booking & checkout spec
cat /home/wego/wego-platform/clients/safari-tours-sharm/design/BOOKING_AND_CHECKOUT.md
```
