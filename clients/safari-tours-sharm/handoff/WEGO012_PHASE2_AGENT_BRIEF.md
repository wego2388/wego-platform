# SAFARI TOURS SHARM — WEGO-012
# Phase 2 Backend: Infrastructure Wiring
# ══════════════════════════════════════════════════════════════════════════════
# اقرأ هذا الملف كاملاً قبل أي شيء آخر.
# يحتوي على الصورة الكاملة، كل المهام بالترتيب، وقواعد التنفيذ.
# ══════════════════════════════════════════════════════════════════════════════

---

## 0. السياق — اقرأه أولاً

### ما هذا المشروع؟

Safari Tours Sharm هو عميل على منتج `wego-tours-operator` — شركة جولات سياحية في
شرم الشيخ تمتلك وتدير كل جولاتها مباشرة (لا مزودين خارجيين، لا settlement).

### الموقع في المنصة

```
wego-platform/
├── platform/application/          ← Spring Boot monolith (الـ runtime الوحيد)
├── platform/kernel/
│   ├── identity/                  ← Auth + RBAC (WEGO-001)
│   ├── events/                    ← Outbox pattern
│   └── security/                  ← Permission codes
├── products/
│   ├── divers/                    ← المنتج الأقرب — اقرأه كـ reference
│   └── tours-operator/            ← هذا المنتج — هدف هذا الـ packet
└── clients/safari-tours-sharm/    ← manifests + design tokens
```

### الحالة قبل هذا الـ packet

| Layer | الملفات | الحالة |
|-------|---------|--------|
| Domain | `Tour`, `TourSlot`, `Booking`, `Money`, `BookingStatus`, `PaymentMethod`, `CustomerContact`, `TimeSlot`, `TourCategory` | ✅ كامل |
| Application interfaces | `TourRepository`, `TourSlotRepository`, `BookingRepository`, `BookingAuditRecorder`, `TransactionRunner` | ✅ كامل |
| Application services | `CreateBookingService`, `ConfirmBookingService`, `CancelBookingService`, `CompleteBookingService`, `ExpireBookingService`, `TourQueryService`, `TourSlotQueryService`, `BookingQueryService` | ✅ كامل |
| API controllers | `TourController`, `TourSlotController`, `BookingController`, `ToursOperatorDtos.kt` | ✅ كامل |
| Infrastructure | `ToursOperatorBeanConfiguration.kt`, `ToursOperatorSpringTransactionRunner.kt`, `JooqBookingAuditRecorder.kt` | ✅ جزئي |
| Infrastructure | `JooqTourRepository`, `JooqTourSlotRepository`, `JooqBookingRepository` | ❌ ناقص |
| Infrastructure | `ToursOperatorExceptionHandler` | ❌ ناقص |
| Build wiring | `build.gradle.kts` sourceSets | ❌ ناقص (tours-operator غير مضاف) |
| Flyway | `V9__tours_operator_foundation.sql` | ✅ كامل |
| jOOQ generated | `ToursOperatorTour`, `ToursOperatorTourSlot`, `ToursOperatorBooking`, etc. | ❌ لم تُولَّد بعد |
| Tests | Unit + Integration | ❌ ناقص |
| e2e seed | tours-operator permissions | ❌ ناقص |
| Frontend | `web/apps/safari-tours-sharm-erp/` | ✅ كامل (ينتظر backend) |

**النتيجة:** `./gradlew :platform:application:build` يفشل لأن `products/tours-operator`
غير مدرج في sourceSets ولأن الـ jOOQ generated types للـ V9 tables غير موجودة.

---

## 1. القراءة الإلزامية قبل التنفيذ

```bash
# 1. الـ execution board — الشكل الإلزامي لكل packet
cat /home/wego/wego-platform/docs/execution/WEGO_EXECUTION_BOARD.md

# 2. Review tier rules — هذا الـ packet Tier 1 (migration + permissions)
cat /home/wego/wego-platform/docs/operations/REVIEW_INTENSITY.md

# 3. Engineering constitution
cat /home/wego/wego-platform/docs/ENGINEERING_CONSTITUTION.md

# 4. Reference implementation — اقرأ كل ملف من هذه قبل تكتب سطر
cat /home/wego/wego-platform/products/divers/src/main/kotlin/com/wego/divers/infrastructure/JooqBookingRepository.kt
cat /home/wego/wego-platform/products/divers/src/main/kotlin/com/wego/divers/infrastructure/JooqOfferingRepository.kt
cat /home/wego/wego-platform/products/divers/src/main/kotlin/com/wego/divers/api/DiversExceptionHandler.kt
cat /home/wego/wego-platform/products/divers/src/main/kotlin/com/wego/divers/infrastructure/DiversBeanConfiguration.kt

# 5. Domain models المطلوب تنفيذها
cat /home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/domain/Tour.kt
cat /home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/domain/TourSlot.kt
cat /home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/domain/Booking.kt
cat /home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/BookingRepository.kt
cat /home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/TourRepository.kt
cat /home/wego/wego-platform/products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/TourSlotRepository.kt

# 6. الـ schema
cat /home/wego/wego-platform/platform/application/src/main/resources/db/migration/V9__tours_operator_foundation.sql

# 7. الـ build config
cat /home/wego/wego-platform/platform/application/build.gradle.kts

# 8. الـ e2e seed (للفهم قبل تعديله)
cat /home/wego/wego-platform/e2e/seed.mjs
```

---

## 1.5 — Progress Tracking (اقرأ أولاً إذا كنت وكيلاً يكمل عمل سابق)

**الملف الوحيد الذي يُحدَّث بعد كل step:**
```
/home/wego/wego-platform/clients/safari-tours-sharm/handoff/WEGO012_PROGRESS.md
```

**القاعدة:** بعد إنهاء كل step بنجاح، حدِّث هذا الملف قبل أي شيء آخر.
وكيل جديد يبدأ من هذا الملف — لا من هذا الـ brief.

**شكل كل تحديث:**
```markdown
## Step N — [اسم الـ step] ✅
- **منتهي في:** 2026-XX-XX HH:MM
- **الدليل:** [الـ command الذي أثبت النجاح + نتيجته في سطر]
- **ملاحظات:** [أي اختيار تقني غير مباشر، أو gap وجدته]
- **التالي:** Step N+1 — [اسم الخطوة]
```

---

## 2. Tier Classification

**هذا الـ packet: Tier 1**

المبررات (من `docs/operations/REVIEW_INTENSITY.md`):
- ✅ يمس database migration موجودة (V9 — permissions INSERT)
- ✅ يمس permission grants (`identity_role_permission`)
- ✅ يكتب booking data (customer PII: fullName, phone, nationality, email)

**الإجراء اللازم:** independent Tier 1 review قبل أي commit.

---

## 3. المهام — بالترتيب الصارم

> **قاعدة الـ progress tracking:**
> بعد إنهاء كل step بنجاح، حدِّث `WEGO012_PROGRESS.md` فوراً قبل الانتقال للتالي.
> غيِّر الحالة من `🔴 لم يبدأ` إلى `✅ مكتمل` في جدول الـ Steps،
> وأضف entry في "سجل التقدم" بالشكل المحدد في §1.5.
> وكيل جديد سيبدأ من هذا الملف ويعرف أين يكمل.

### Step 1 — أضف tours-operator في build.gradle.kts

**الملف:** `platform/application/build.gradle.kts`

أضف السطر التالي داخل `kotlin.sourceSets.named("main") { kotlin.srcDirs(...) }`:

```kotlin
"../../products/tours-operator/src/main/kotlin",
```

بعد السطر الخاص بـ `travel-marketplace`. الترتيب مهم للـ readability فقط.

**تحقق فوري:**
```bash
./gradlew :platform:application:jooqCodegen
# يجب أن يُنشئ ملفات مثل:
# build/generated-src/jooq/main/com/wego/generated/jooq/tables/ToursOperatorTour.java
# build/generated-src/jooq/main/com/wego/generated/jooq/tables/ToursOperatorBooking.java
# build/generated-src/jooq/main/com/wego/generated/jooq/tables/ToursOperatorTourSlot.java
# build/generated-src/jooq/main/com/wego/generated/jooq/tables/ToursOperatorBookingReferenceSeq.java
# build/generated-src/jooq/main/com/wego/generated/jooq/tables/ToursOperatorBookingAuditEvent.java
```

**لا تكمل للـ Step 2 قبل التحقق من وجود الملفات.**

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md:**
```markdown
## Step 1 — build.gradle.kts sourceSets ✅
- **منتهي في:** 2026-XX-XX HH:MM
- **الدليل:** jooqCodegen أنشأ X files جديدة تحتوي ToursOperatorTour/Slot/Booking
- **ملاحظات:** [أي ملاحظة]
- **التالي:** Step 2 — JooqTourRepository
```

---

### Step 2 — JooqTourRepository.kt

**الملف الجديد:**
```
products/tours-operator/src/main/kotlin/com/wego/toursoperator/infrastructure/JooqTourRepository.kt
```

**المتطلبات من `TourRepository` interface:**
```kotlin
fun findById(id: TourId): Tour?
fun findByIdForUpdate(id: TourId): Tour?   // SELECT ... FOR UPDATE
fun findBySlug(slug: String): Tour?
fun existsBySlug(slug: String): Boolean
fun findAll(category: TourCategory?, activeOnly: Boolean, limit: Int, offset: Int): List<Tour>
fun save(tour: Tour)
```

**قواعد التنفيذ:**
- استخدم `TOURS_OPERATOR_TOUR` من jOOQ generated types (الاسم الدقيق سيظهر بعد jooqCodegen)
- `findAll` يُرتَّب بـ `sort_order ASC, id ASC` — tie-breaker ضروري لـ pagination stability
- `save` هو UPSERT — `insertInto(...).set(...).onConflict(ID).doUpdate()`
- `availableTimeSlots` مخزن كـ comma-separated text في DB — حوّله لـ `Set<TimeSlot>` في domain
- `createdByUserId` اختياري (null-safe)
- Pattern: `@Repository` annotation، `@Transactional(readOnly = true)` على القراءة، `@Transactional` على الكتابة

**Reference الأقرب:** `JooqOfferingRepository.kt` في products/divers

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 2 → مكتمل، التالي Step 3)

---

### Step 3 — JooqTourSlotRepository.kt

**الملف الجديد:**
```
products/tours-operator/src/main/kotlin/com/wego/toursoperator/infrastructure/JooqTourSlotRepository.kt
```

**المتطلبات من `TourSlotRepository` interface:**
```kotlin
fun findById(id: TourSlotId): TourSlot?
fun findByIdForUpdate(id: TourSlotId): TourSlot?   // نقطة serialization للـ capacity check
fun findByTourAndDate(tourId: TourId, date: LocalDate): List<TourSlot>
fun findAvailable(tourId: TourId, from: LocalDate, to: LocalDate): List<TourSlot>
fun save(slot: TourSlot)
```

**قواعد التنفيذ:**
- `findByIdForUpdate` هو **أهم method** — يُستخدم في `CreateBookingService` لمنع overselling
- `findAvailable` يُفلتر بـ `is_blocked = false AND booked_count < capacity`
- `save` هو UPSERT بنفس pattern الـ Tour
- ترتيب `findByTourAndDate`: `time_slot ASC`

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 3 → مكتمل، التالي Step 4)

---

### Step 4 — JooqBookingRepository.kt

**الملف الجديد:**
```
products/tours-operator/src/main/kotlin/com/wego/toursoperator/infrastructure/JooqBookingRepository.kt
```

**المتطلبات من `BookingRepository` interface:**
```kotlin
fun findById(id: BookingId): Booking?
fun findByIdForUpdate(id: BookingId): Booking?
fun findByReference(reference: String): Booking?
fun findByReferenceAndPhone(reference: String, phone: String): Booking?
fun findAll(tourId: TourId?, status: BookingStatus?, date: LocalDate?, limit: Int, offset: Int): List<Booking>
fun countBySlotId(slotId: TourSlotId): Int
fun nextReferenceSequence(year: Int): Long   // ⚠️ critical — انظر التفاصيل أسفله
fun save(booking: Booking)
```

**⚠️ `nextReferenceSequence` — تفاصيل حرجة:**

هذه الـ method تُولّد الـ STR-YYYY-N reference number. يجب أن:
1. تُشغَّل داخل transaction مع slot lock قائم (يحدث هذا بالفعل في `CreateBookingService`)
2. تستخدم `SELECT ... FOR UPDATE` على صف السنة في `tours_operator_booking_reference_seq`
3. تعمل UPSERT للسنة لو لم توجد، ثم تزيد الـ next_val
4. ترجع القيمة قبل الزيادة (القيمة المُدّعى بها)

```sql
-- النمط الصحيح (parameterized، ليس string concat)
INSERT INTO wego.tours_operator_booking_reference_seq (year, next_val)
VALUES (?, 2)
ON CONFLICT (year) DO UPDATE
  SET next_val = tours_operator_booking_reference_seq.next_val + 1
RETURNING next_val - 1
```

أو بشكل مكافئ: `SELECT FOR UPDATE` ثم `UPDATE` في خطوتين داخل نفس الـ transaction.

**تحذير:** لا تستخدم SEQUENCE مباشرة — الـ schema يستخدم جدول عادي per-year عن قصد.

**`findAll` ordering:** `created_at DESC, id DESC` — tie-breaker إلزامي

**Customer fields mapping:**
```
DB column              → Domain field
customer_full_name     → customer.fullName
customer_phone         → customer.phone
customer_nationality   → customer.nationality
customer_email         → customer.email (nullable)
```

**Status mapping:** `status` مخزن كـ String في DB — `BookingStatus.valueOf(record.status)`

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 4 → مكتمل، التالي Step 5)

---

### Step 5 — أضف الـ 3 repositories في BeanConfiguration

**الملف:** `ToursOperatorBeanConfiguration.kt`

أضف:
```kotlin
@Bean
fun jooqTourRepository(dsl: DSLContext): TourRepository =
    JooqTourRepository(dsl)

@Bean
fun jooqTourSlotRepository(dsl: DSLContext): TourSlotRepository =
    JooqTourSlotRepository(dsl)

@Bean
fun jooqBookingRepository(dsl: DSLContext): BookingRepository =
    JooqBookingRepository(dsl)
```

تأكد أن `createBookingService` وبقية الـ services تحصل على هذه الـ beans.

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 5 → مكتمل، التالي Step 6)

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 5 → مكتمل، التالي Step 6)

---

### Step 6 — ToursOperatorExceptionHandler.kt

**الملف الجديد:**
```
products/tours-operator/src/main/kotlin/com/wego/toursoperator/api/ToursOperatorExceptionHandler.kt
```

**النمط:** انسخ بالضبط من `DiversExceptionHandler.kt` وعدّل:
- `basePackages = ["com.wego.toursoperator.api"]`
- نفس الـ 6 exception handlers: `IllegalArgumentException`, `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`
- نفس الـ `DataIntegrityViolationException` handler — يُرجع 409 `conflict`
- أي raw 500 هو bug — هذا الـ handler يمنعه

**مهم:** الـ package الصحيح يضمن أن الـ handler لا يؤثر على `com.wego.divers.api` أو `com.wego.identity.api`.

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 6 → مكتمل، التالي Step 7)

---

### Step 7 — Unit Tests

**الملف الجديد:**
```
products/tours-operator/src/test/kotlin/com/wego/toursoperator/domain/TourSlotTest.kt
products/tours-operator/src/test/kotlin/com/wego/toursoperator/domain/BookingTest.kt
```

**`TourSlotTest` — المطلوب:**
- `book()` يُنقص الـ available بمقدار 1 ويرجع `true`
- `book()` على slot مكتمل الـ capacity يرجع `false` دون تغيير
- `book()` على blocked slot محمي بـ domain check (أو الـ service يتحقق قبله — اثبت السلوك الفعلي)
- لا يمكن أن يتجاوز `bookedCount` الـ `capacity`

**`BookingTest` — المطلوب:**
- `confirm()` تحوّل `NEW → CONFIRMED` وتضع `confirmedAt`
- `confirm()` على `CONFIRMED` booking يرمي exception
- `cancel()` تحوّل `NEW → CANCELLED` و `CONFIRMED → CANCELLED`
- `cancel()` تتطلب reason غير فارغ
- `cancel()` على `COMPLETED` booking يرمي exception
- `expire()` تحوّل `NEW → EXPIRED`
- `expire()` على `CONFIRMED` booking يرمي exception
- Reference format validation: `STR-YYYY-N` يُقبل، أي شيء آخر يُرفض

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 7 → مكتمل، التالي Step 8)

---

### Step 8 — Integration Tests

**الملف الجديد:**
```
platform/application/src/test/kotlin/com/wego/ToursOperatorHttpTest.kt
```

**البيئة:** Testcontainers PostgreSQL 18.4 — نفس pattern `DiversHttpTest.kt`

**الـ test cases الإلزامية (P0 — لا build بدونها):**

```
[1] Staff Login → GET /api/v1/tours-operator/tours → 401 without token
[2] POST /api/v1/tours-operator/tours/{tourId}/slots/{slotId}/book (public) → 201 Created, reference matches STR-YYYY-N
[3] POST /api/v1/tours-operator/bookings/{id}/confirm → 200 (payment-update permission)
[4] POST /api/v1/tours-operator/bookings/{id}/cancel → 200 (cancel permission)
[5] POST /api/v1/tours-operator/bookings/{id}/complete → 200 (complete permission)
[6] Concurrent booking: 3 threads على نفس slot بـ capacity=1 → exactly 1 succeeds (201), 2 يفشلوا (409 slot_fully_booked)
[7] confirm على CANCELLED booking → 409 cannot_confirm
[8] cancel على COMPLETED booking → 409 cannot_cancel
[9] GET /api/v1/tours-operator/bookings → 403 بدون booking:view permission
[10] Public lookup: GET /api/v1/tours-operator/bookings/lookup?reference=STR-YYYY-1&phone=... → 200
```

**Test case [6] حرج جداً** — هو الدليل الفعلي على أن `findByIdForUpdate` يمنع overselling.
اثبت الـ test يفشل قبل الإصلاح وينجح بعده (نفس منهجية WEGO-011).

**Setup لكل test:** أنشئ tour + slot + staff user بـ `platform-admin` role مباشرة في DB (nفس نمط seed.mjs).

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 8 → مكتمل، اكتب عدد الـ tests الكلي، التالي Step 9)

---

### Step 9 — توسيع e2e seed

**الملف:** `e2e/seed.mjs`

أضف tours-operator test data بعد الـ divers seed:

```javascript
// 1. Create a test tour
const tourId = randomUUID();
await client.query(`
  INSERT INTO wego.tours_operator_tour
    (id, slug, category, duration_text, price_adult_cents, price_child_cents,
     capacity, available_time_slots, sort_order, is_active, created_by_user_id, created_at)
  VALUES ($1, 'e2e-desert-quad', 'DESERT', '4 hours', 3500, 1750, 10,
          'MORNING,SUNSET', 1, true, $2, now())
  ON CONFLICT (slug) DO NOTHING
`, [tourId, resolvedUserId]);

// 2. Create a test slot (next Monday morning)
const nextMonday = ... // calculate next Monday
await client.query(`
  INSERT INTO wego.tours_operator_tour_slot
    (id, tour_id, date, time_slot, capacity, booked_count, is_blocked, created_at)
  VALUES ($1, $2, $3, 'MORNING', 10, 0, false, now())
  ON CONFLICT (tour_id, date, time_slot) DO NOTHING
`, [randomUUID(), tourId, nextMonday]);
```

**تأكيد:** `ON CONFLICT DO NOTHING` على كل INSERT — idempotent.

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md** (Step 9 → مكتمل، التالي Step 10)

---

### Step 10 — التحقق النهائي قبل التسليم للـ review

```bash
# 1. jooq generation نظيف
./gradlew :platform:application:jooqCodegen
# التحقق: ls build/generated-src/jooq/main/com/wego/generated/jooq/tables/ | grep -i tours

# 2. build كامل
./gradlew :platform:application:build
# المطلوب: BUILD SUCCESSFUL

# 3. test suite
./gradlew :platform:application:check --rerun-tasks
# المطلوب: عدد الـ tests يزيد عن 221 (الـ baseline من WEGO-011)
# المطلوب: 0 failures, 0 skipped (Docker متاح)

# 4. foundry validation
cd foundry && node scripts/validate-manifests.mjs
# المطلوب: zero errors

# 5. web tests (للتأكد لم نكسر شيء)
cd web && pnpm run check
# المطلوب: 27 tests pass (safari-tours-sharm-erp) + كل الـ packages

# 6. تشغيل فعلي
cd infrastructure/compose
docker compose up -d postgres
# انتظر healthy ثم:
WEGO_FLYWAY_ENABLED=true WEGO_DB_PASSWORD=wego-local-postgres-only \
  WEGO_DB_USERNAME=wego_app \
  ./gradlew :platform:application:bootRun

# 7. تحقق live
curl -s http://localhost:8080/actuator/health | grep '"status":"UP"'
curl -s http://localhost:8080/api/v1/tours-operator/tours      # يجب: 200 []
curl -s -X POST http://localhost:8080/api/v1/identity/sessions \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"..."}' | grep token

# بعد الـ token:
curl -s -H "Authorization: Bearer TOKEN" \
  http://localhost:8080/api/v1/tours-operator/bookings         # يجب: 200 []
```

**✅ بعد النجاح — حدّث WEGO012_PROGRESS.md:**
```markdown
## Step 10 — التحقق النهائي ✅
- **منتهي في:** 2026-XX-XX HH:MM
- **الدليل:** BUILD SUCCESSFUL — X tests passed, 0 failed, 0 skipped. Live: tours 200 [], bookings 200 []
- **الحالة الكلية:** 🟡 جاهز للـ Tier 1 Review
- **التالي:** Tier 1 independent review (Codex CLI) قبل أي commit
```

---

## 4. حدود هذا الـ packet — ما لا يُبنى هنا

| المنع | السبب |
|-------|--------|
| Paymob webhook integration | Phase 3 منفصل — يحتاج signed payload verification |
| `ExpireBookingService` scheduler | يحتاج Spring `@Scheduled` + production ops decision |
| Tours CRUD (POST/PUT/DELETE) | Staff-only CRUD → packet منفصل؛ Dashboard يستخدم list/get فقط |
| Slot creation/blocking API | نفس السبب |
| Tour localization fields (name, description per locale) | Phase 5 — schema extension لم يُقرر بعد |
| إضافة OpenAPI schema | يصير في packet منفصل بعد live verification |
| Production deployment | بعد Tier 1 review + owner authorization |

---

## 5. قواعد التنفيذ — الثوابت

من `docs/ENGINEERING_CONSTITUTION.md` + الدروس المستفادة من WEGO-011:

```
❌ لا تضع credentials في الكود — env vars فقط
❌ لا تستخدم findById حيث تحتاج findByIdForUpdate — هذا كان السبب في 6 bugs في WEGO-011
❌ لا تفترض أن green build يعني أنك حميت من race conditions
❌ لا تكتب BLOCKING finding كـ "resolved" قبل ما تثبت الـ test يفشل بدون الـ fix
❌ لا تُشغّل jOOQ code in memory (H2) — PostgreSQL فقط عبر Testcontainers
✅ كل method تعدّل state تستخدم SELECT ... FOR UPDATE على الـ aggregate root
✅ اكتب الـ diagnostic test أولاً (يفشل بدون الـ fix) ثم طبّق الـ fix
✅ أضف execution board entry قبل تطلب الـ review
✅ uuid booking IDs لا تظهر في public responses — استخدم reference فقط
✅ totalEur = priceAdult × adultsCount + priceChild × childrenCount — DB constraint يتحقق هذا
```

---

## 6. Execution Board Entry — الشكل المطلوب

عند الانتهاء، أضف entry في:
```
/home/wego/wego-platform/docs/execution/WEGO_EXECUTION_BOARD.md
```

بالشكل التالي (مثال):

```markdown
## WEGO-012 — Tours Operator: infrastructure wiring

### 2026-XX-XX — WEGO-012 Phase 1: repositories + wiring

- **Status:** ACTIVE
- **Objective:** Wire the three missing jOOQ repositories, add tours-operator to the
  build sourceSets, and prove the full booking lifecycle over real HTTP and real PostgreSQL.
- **Tier:** 1 (database migration permissions + customer PII)
- **Scope:** `build.gradle.kts` sourceSets; `JooqTourRepository`, `JooqTourSlotRepository`,
  `JooqBookingRepository`; `ToursOperatorExceptionHandler`; `TourSlotTest`, `BookingTest`
  (domain); `ToursOperatorHttpTest` (integration, 10 cases including concurrent-booking proof).
- **Out of scope:** Paymob webhook, ExpireBookingService scheduler, Tours CRUD,
  Slot management, tour localization, production deployment.
- **Risks:** ...
- **Acceptance criteria:** ...
- **Tests:** ...
- **Live end-to-end evidence:** ...
- **NEXT PACKET:** ...
```

---

## 7. بعد هذا الـ packet — الخطوة التالية

بعد اكتمال هذا الـ packet وتمرير Tier 1 review:

```
WEGO-012-B (اختياري) — Tours CRUD API + Slot management
  POST /api/v1/tours-operator/tours             ← tour:manage permission
  PUT  /api/v1/tours-operator/tours/{id}
  POST /api/v1/tours-operator/tours/{id}/slots  ← slot:manage permission
  PUT  /api/v1/tours-operator/tours/{id}/slots/{slotId}/block

WEGO-012-C — ExpireBookingService scheduler
  @Scheduled(fixedDelay = 60_000) — فحص NEW bookings أكبر من 30 دقيقة
  يتطلب owner decision على production ops

WEGO-012-D — Paymob webhook
  POST /api/v1/tours-operator/webhooks/paymob
  يتطلب signed payload verification + sandbox Paymob account
```

---

## 8. الملفات المتأثرة — ملخص

```
MODIFIED:
  platform/application/build.gradle.kts
  products/tours-operator/src/main/kotlin/.../infrastructure/ToursOperatorBeanConfiguration.kt
  e2e/seed.mjs

NEW:
  products/tours-operator/src/main/kotlin/.../infrastructure/JooqTourRepository.kt
  products/tours-operator/src/main/kotlin/.../infrastructure/JooqTourSlotRepository.kt
  products/tours-operator/src/main/kotlin/.../infrastructure/JooqBookingRepository.kt
  products/tours-operator/src/main/kotlin/.../api/ToursOperatorExceptionHandler.kt
  products/tours-operator/src/test/kotlin/.../domain/TourSlotTest.kt
  products/tours-operator/src/test/kotlin/.../domain/BookingTest.kt
  platform/application/src/test/kotlin/com/wego/ToursOperatorHttpTest.kt
  docs/execution/WEGO_EXECUTION_BOARD.md (entry appended)
```

**الملفات التي لا تُمس:**
- كل domain + application + api files (مكتملة)
- V9 migration (موجودة)
- web/apps/safari-tours-sharm-erp (جاهز)

---

*آخر تحديث: 2026-09-27 — Safari Tours Sharm Phase 2 Handoff*
