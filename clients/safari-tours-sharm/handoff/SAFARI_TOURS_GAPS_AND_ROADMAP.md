# Safari Tours Sharm — ملف النواقص والمهام الكاملة
# ══════════════════════════════════════════════════════════════════════
# آخر تحديث: 2026-09-27
# الحالة الحالية: Phase 2 Backend مكتملة — Phase 3/4/5 ناقصة
# ══════════════════════════════════════════════════════════════════════

> **سجل تاريخي — 27 سبتمبر، وليس حالة التنفيذ الحالية.** لا تعتمد نسب
> الإنجاز أو أرقام migrations أو قائمة النواقص القديمة هنا. المرجع الحي:
> `2026-10-05_CATALOG_MEDIA_SAFE_CHECKPOINT_AR.md` و`../ROADMAP_AR.md` وBoard.
> MEDIA هي النشطة الوحيدة؛ الحجز/المالية/ENQUIRY وERP EN/AR سبقتها محليًا.

---

## الحالة الراهنة — ما تم بناؤه

| الطبقة | الحالة | التفاصيل |
|--------|---------|----------|
| **Backend API** | ✅ مكتمل | Tours، TourSlots، Bookings (CRUD + lifecycle) |
| **Database** | ✅ مكتمل | V9 migration — 5 tables + permissions |
| **ERP Dashboard** | ✅ 90% مكتمل | كل الصفحات موجودة — API wiring ينتظر backend live |
| **الموقع العام** | ⚠️ 30% مكتمل | Homepage + Categories + Tours list فقط |
| **Mobile App** | ❌ لم يبدأ | Scaffold فقط — Phase 5 |

---

## القسم الأول — الموقع العام (safari-tours-sharm-site)

### ما هو موجود ✅
- `pages/index.vue` — Homepage كاملة (Hero, Trust Bar, Categories, How It Works, Cancellation)
- `pages/tours.vue` — قائمة الكاتيجوريز
- `pages/category/[slug].vue` — صفحة الكاتيجوري (placeholder بدون API data)
- `pages/contact.vue` — صفحة التواصل
- `pages/terms.vue` + `pages/privacy.vue`
- `components/SiteHeader.vue` + `SiteFooter.vue` + `WhatsAppFab.vue`
- `content/locales.ts` — i18n (en, ru, ar) — **it ناقص**
- CSS tokens + design system

### ما هو ناقص ❌

#### P0 — بدونه مافيش مبيعات أبداً

| الرقم | المهمة | الملف المطلوب |
|-------|---------|---------------|
| S-01 | **صفحة تفاصيل الرحلة** `/tour/:slug` — gallery + description + booking card sticky | `pages/tour/[slug].vue` |
| S-02 | **Booking Flow — Step 1** `/book/:slotId` — بيانات العميل (Name, Phone, Nationality, Hotel) | `pages/book/[slotId].vue` |
| S-03 | **Booking Flow — Step 2** — Review & Confirm مع pricing breakdown | داخل نفس flow أو `pages/book/[slotId]/confirm.vue` |
| S-04 | **Booking Flow — Step 3** — Paymob redirect / payment | داخل flow |
| S-05 | **Booking Confirmation** `/booking/[reference]/confirmation` — "تم الحجز ✅" + تفاصيل | `pages/booking/[reference]/confirmation.vue` |
| S-06 | **Public Booking Lookup** `/my-booking` — عميل يشوف حجزه بـ reference + phone | `pages/my-booking.vue` |

#### P1 — مهم للتجربة الكاملة

| الرقم | المهمة | الملف المطلوب |
|-------|---------|---------------|
| S-07 | **API wiring في صفحة الكاتيجوري** — تحميل الرحلات الحقيقية من `/api/v1/tours-operator/tours?category=X` | تعديل `category/[slug].vue` |
| S-08 | **API wiring في صفحة tours** — تحميل كل الرحلات مع filter | تعديل `tours.vue` |
| S-09 | **Availability calendar في صفحة الرحلة** — عرض slots المتاحة للتاريخ المختار | داخل `tour/[slug].vue` |
| S-10 | **Composable للـ public API** — `usePublicToursApi.ts` بدون token (public endpoints) | `composables/usePublicToursApi.ts` |
| S-11 | **Italian locale** — i18n للإيطالي في `locales.ts` | تعديل `content/locales.ts` |
| S-12 | **صفحة About** — من نحن، فريق، لماذا Safari Tours | `pages/about.vue` |

#### P2 — تحسينات SEO والأداء

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| S-13 | **Sitemap XML** — لكل الرحلات و categories | `server/routes/sitemap.xml.ts` |
| S-14 | **Schema.org لكل رحلة** — `TouristAttraction` structured data | داخل `tour/[slug].vue` |
| S-15 | **OG image per tour** — صورة social sharing لكل رحلة | إما static أو `og-image/[slug].ts` |
| S-16 | **Hero video** — حالياً CSS gradient بدل فيديو حقيقي | ينتظر assets من العميل |
| S-17 | **Featured Tours section** في Homepage — حالياً غير موجودة | تعديل `index.vue` |
| S-18 | **Testimonials section** في Homepage — حالياً غير موجودة | تعديل `index.vue` |
| S-19 | **Tour card component** — مكون مشترك يُستخدم في كل صفحة | `components/TourCard.vue` |

---

## القسم الثاني — الداشبورد (safari-tours-sharm-erp)

### ما هو موجود ✅
- كل الصفحات مبنية بـ mock data:
  - `pages/index.vue` — Overview + KPIs + Recent Bookings
  - `pages/bookings.vue` + `pages/bookings/[id].vue`
  - `pages/tours.vue` + `pages/tours/[id]/slots.vue`
  - `pages/finance.vue`
  - `pages/customers.vue`
  - `pages/notifications.vue`
  - `pages/settings.vue` (5 tabs)
  - `pages/reviews.vue` (placeholder)
  - `pages/login.vue`
- `composables/useToursApi.ts` — API client كامل (list/get/confirm/cancel/complete)
- `composables/useAuthSession.ts`
- 27 unit tests

### ما هو ناقص ❌

#### P0 — API wiring (ينتظر backend live على :8080)

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| E-01 | **API wiring لصفحة Overview** | الكود موجود — يحتاج backend فعلي يرد |
| E-02 | **API wiring لصفحة Bookings** | الكود موجود — نفس السبب |
| E-03 | **API wiring لصفحة Tours** | يحتاج `GET /api/v1/tours-operator/tours` backend |
| E-04 | **API wiring لصفحة Slots Calendar** | يحتاج `GET /api/v1/tours-operator/tours/:id/slots` |
| E-05 | **API wiring لصفحة Finance** | يحتاج bookings data حقيقية |
| E-06 | **API wiring لصفحة Customers** | مشتق من bookings — نفس E-02 |
| E-07 | **nuxt.config.ts — proxy setup** | ضبط dev proxy من `:3001` → `:8080` |

#### P1 — ميزات مبنية في الـ brief لكن غير موجودة

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| E-08 | **Tours CRUD — Create Tour** `POST /api/v1/tours-operator/tours` | Form لإنشاء رحلة جديدة — الـ API غير موجود بعد (out of scope WEGO-012) |
| E-09 | **Tours CRUD — Edit Tour** `PUT /api/v1/tours-operator/tours/:id` | نفس الـ API |
| E-10 | **Slot Management — Create Slot** | إنشاء slot ليوم وtime معينين |
| E-11 | **Slot Blocking** `PUT .../slots/:id/block` | تعطيل slot |
| E-12 | **Paymob Webhook Handler** — تأكيد الدفع تلقائياً | Backend — WEGO-012-D |
| E-13 | **ExpireBookingService scheduler** — إلغاء bookings منتهية الـ 30 دقيقة | Backend — WEGO-012-C |
| E-14 | **WhatsApp notification** على كل booking event | Backend integration |
| E-15 | **Export Bookings to Excel** | Frontend + backend |
| E-16 | **Print Voucher** لكل booking | HTML template → print |
| E-17 | **Reviews Management** — حالياً placeholder | Phase 5 |

#### P2 — تحسينات UX

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| E-18 | **Real-time notifications** — WebSocket أو Server-Sent Events | يحتاج backend support |
| E-19 | **Charts حقيقية** في Finance — chart library integration | حالياً placeholder |
| E-20 | **Pagination** في كل القوائم — حالياً `size=50` بدون UI | تعديل Bookings + Tours + Customers |
| E-21 | **Dark mode** | تعديل CSS tokens |
| E-22 | **RTL support** في ERP — لو المستخدم عربي | إضافة `dir` handling |

---

## القسم الثالث — البيكند (platform/products/tours-operator)

### ما هو موجود ✅
- Domain model كامل (Tour, TourSlot, Booking, BookingPricing, Money, CustomerContact)
- Application services كاملة (Create, Confirm, Cancel, Complete, Expire)
- Infrastructure repositories كاملة (JooqTourRepository, JooqTourSlotRepository, JooqBookingRepository)
- API controllers (TourController, TourSlotController, BookingController)
- ExceptionHandler, BeanConfiguration
- V9 Flyway migration
- 37 unit tests + 10 integration tests

### ما هو ناقص ❌

#### P0 — لازم قبل أي deployment

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| B-01 | **Tier 1 Review** — independent review للـ repositories + migration | قبل أي commit |
| B-02 | **`./gradlew build` نظيف** | يحتاج Java 25 environment |
| B-03 | **Live bootRun verification** | تأكيد الـ backend يشتغل على :8080 |
| B-04 | **WEGO_EXECUTION_BOARD.md entry** — تسجيل WEGO-012 رسمياً | قبل commit |

#### P1 — Packets لاحقة مخططة

| الرقم | الـ Packet | المحتوى |
|-------|-----------|---------|
| B-05 | **WEGO-012-B** | Tours CRUD API (POST/PUT) + Slot management |
| B-06 | **WEGO-012-C** | ExpireBookingService scheduler |
| B-07 | **WEGO-012-D** | Paymob webhook integration |
| B-08 | **WEGO-012-E** | Tour localization fields (name, description per locale) |
| B-09 | **WEGO-013** | Public website API — endpoints للموقع العام (no auth) |

#### P2 — مستقبل

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| B-10 | **Payment domain** — Payment entity + Paymob webhook | WEGO-012-D |
| B-11 | **Review domain** — قراءة reviews من sources خارجية | Phase 5 |
| B-12 | **WhatsApp notifications** — trigger على booking events | WEGO-004 equivalent |
| B-13 | **OpenAPI schema** | بعد live verification |

---

## القسم الرابع — Mobile App (KMP)

### ما هو موجود ✅
- `mobile/apps/customer/` — KMP scaffold (Android + iOS + JVM)
- `mobile/apps/customer-android/` — Android scaffold
- `mobile/shared/` — shared module
- Build config مضبوط (Compose Multiplatform, Material3)

### ما هو ناقص ❌ — Phase 5 كاملة

| الرقم | المهمة | الأولوية |
|-------|---------|----------|
| M-01 | **Browse Tours screen** — قائمة الرحلات بالـ categories | P0 |
| M-02 | **Tour Detail screen** — صورة + تفاصيل + booking button | P0 |
| M-03 | **Booking Flow** — 4 steps مثل الموقع | P0 |
| M-04 | **My Booking screen** — lookup بـ reference + phone | P0 |
| M-05 | **API client** — Ktor أو Retrofit للـ backend | P0 |
| M-06 | **Push Notifications** — Firebase للـ Android, APNs للـ iOS | P1 |
| M-07 | **RTL support** | P1 |
| M-08 | **Multilingual** — en, ru, ar | P1 |
| M-09 | **iOS app** — Xcode project + signing | P2 |

---

## القسم الخامس — Infrastructure

### ما هو موجود ✅
- `infrastructure/compose/compose.yaml` — PostgreSQL + Redis + Nginx + web
- `infrastructure/nginx/nginx.conf`
- Docker build configs

### ما هو ناقص ❌

| الرقم | المهمة | التفاصيل |
|-------|---------|----------|
| I-01 | **compose.yaml entry للـ safari-tours-sharm-site** | إضافة الموقع العام للـ compose |
| I-02 | **compose.yaml entry للـ safari-tours-sharm-erp** | إضافة الداشبورد |
| I-03 | **nginx routing** لـ safaritourssharm.com | تعديل nginx.conf |
| I-04 | **CI/CD pipeline** للـ safari-tours-sharm | GitHub Actions workflow |
| I-05 | **Production secrets** — Paymob API key، DB password | ENV vars setup |
| I-06 | **SSL/TLS** لـ safaritourssharm.com | Certbot أو Cloudflare |

---

## القسم السادس — Content وAssets

### ما هو ناقص — ينتظر من العميل

| الرقم | المطلوب من العميل | الاستخدام |
|-------|-------------------|-----------|
| C-01 | **Logo SVG + PNG** عالي الجودة | Header + Favicon + OG image |
| C-02 | **صور كل رحلة** (min 5 صور per tour) | صفحة الرحلة + cards |
| C-03 | **فيديو Hero** (30-60 ثانية، desert + sea) | Homepage background video |
| C-04 | **الألوان الرسمية** (Hex codes) | تأكيد design tokens |
| C-05 | **أسعار كل الرحلات** (27 رحلة بدون سعر) | DB seed + عرض على الموقع |
| C-06 | **الترجمة الروسية** الكاملة لكل رحلة | i18n backend + frontend |
| C-07 | **الترجمة العربية** الكاملة | i18n |
| C-08 | **الترجمة الإيطالية** | i18n |
| C-09 | **Reviews حقيقية** من Google/TripAdvisor | Reviews section |
| C-10 | **Paymob credentials** للـ production | Payment integration |

---

## الأولويات المقترحة — التسلسل الصح

### المرحلة الحالية (قبل أي شيء)
```
1. Owner: ./gradlew build + check + bootRun (Java 25 environment)
2. Tier 1 Review للـ backend
3. Commit WEGO-012
```

### بعدها مباشرة — ما يفتح الباب للمبيعات

```
Phase 3A — الموقع العام (أعلى أولوية للـ ROI):
  S-10: usePublicToursApi composable
  S-07: API wiring صفحة category
  S-08: API wiring صفحة tours
  S-01: صفحة الرحلة /tour/:slug
  S-02→S-05: Booking Flow كامل
  S-06: My Booking lookup

Phase 3B — تكتمل التجربة:
  S-12: About page
  S-11: Italian locale
  S-17: Featured Tours في Homepage
  S-18: Testimonials في Homepage
```

### بعد الموقع — يفتح كفاءة التشغيل

```
Phase 4 Completion — ERP API wiring:
  E-07: nuxt proxy config
  E-01→E-06: API wiring لكل الصفحات
  B-05 (WEGO-012-B): Tours CRUD API
  B-06 (WEGO-012-C): Expire scheduler
  B-07 (WEGO-012-D): Paymob webhook
```

### آخراً

```
Phase 5 — Mobile:
  M-01→M-08: كل شاشات الـ mobile app
```

---

## ملاحظات حرجة للـ Agent القادم

```
1. الـ useToursApi في الـ ERP يستخدم token (staff only)
   الموقع العام يحتاج usePublicToursApi بدون token
   Endpoints العامة: GET /tours، GET /tours/:id، GET /tours/:id/slots (available)
   POST /bookings، GET /bookings/lookup — كلها public في BookingController

2. priceAdultCents في DB هو Long cents
   لعرضه على الموقع: (priceAdultCents / 100).toFixed(0) + "€"
   مثال: 3500 cents = 35€

3. approved-facts.json في /home/wego/projects/clients/safari-tours-sharm/data/
   هو مصدر بيانات الرحلات للـ seed — لا تستخدمه مباشرة في الكود

4. 27 رحلة من أصل 30 بدون سعر محدد
   قرار مطلوب من Owner: هل تظهر بـ "Contact for Price"؟ أم مخفية حتى يُحدد سعرها؟

5. الـ ERP يعمل على :3001 في dev
   الموقع العام يعمل على :3000 في dev
   Backend على :8080

6. Italian locale (it) موجود في client.manifest وفي DB schema
   لكن locales.ts في الموقع ليس فيها it content بعد
```

---

## ملخص الأرقام

| القسم | مكتمل | ناقص |
|-------|-------|------|
| Backend API | ✅ 100% | 0 (P0 done) |
| ERP Dashboard (بنية) | ✅ 90% | API wiring + CRUD |
| الموقع العام | ⚠️ 30% | Booking flow كامل |
| Mobile | ❌ 0% | كل شيء |
| Infrastructure | ⚠️ 50% | safari-tours compose + nginx |
| Content/Assets | ⚠️ 20% | ينتظر العميل |

**الأهم الآن:** S-01 + S-02 + S-03 + S-04 + S-05 (الـ booking flow) — هذا ما يحوّل الزيارة لفلوس.

---

*آخر تحديث: 2026-09-27 — Safari Tours Sharm Full Gap Analysis*
