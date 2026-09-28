# WEGO-016 — Critical fixes + E-mock + F
# Agent start brief — 2026-09-28 (Africa/Cairo)
# Branch: wego-016-safari-tours-baseline
# Last commit: fc39bd6

## إلزامي قبل أي شيء

1. اقرأ `AGENTS.md` و`docs/ENGINEERING_CONSTITUTION.md`.
2. اقرأ `docs/execution/WEGO_EXECUTION_BOARD.md` — الـpacket الوحيد المصرح به.
3. اقرأ `clients/safari-tours-sharm/handoff/README.md` ثم
   `clients/safari-tours-sharm/handoff/SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`.
4. شغّل `git status --short` — يجب أن يكون نظيفاً.
5. شغّل `bash scripts/safari-tours-sharm-check.sh` — يجب أن يمر قبل أي تعديل.

---

## المهمة — ثلاث مراحل بالترتيب

### المرحلة 1 — Critical fixes (BLOCKING — افعلها أولاً)

ثلاث مشاكل حرجة في الكود الحالي لازم تتصلح قبل أي packet جديد:

#### Fix 1 — buildCheckoutUrl stub واضح وخطير
الملف: `products/tours-operator/src/main/kotlin/com/wego/toursoperator/infrastructure/PaymobHttpClient.kt`

المشكلة: `buildCheckoutUrl` بترجع URL وهمي مش Paymob API حقيقي.
لو شغّل على production هيدي customers رابط غلط تماماً.

المطلوب:
- احتفظ بالـstub كما هو (credentials لسه ما وصلتش)
- لكن أضف comment واضح وصريح يمنع الـdeploy:

```kotlin
/**
 * STUB — NOT PRODUCTION READY.
 *
 * Real Paymob checkout requires a separate payment-key API call:
 *   POST /acceptance/payment_keys  { auth_token, amount_cents, currency,
 *                                    order_id, billing_data, integration_id,
 *                                    lock_order_when_paid }
 * Returns a payment_token used as: https://accept.paymob.com/api/acceptance/iframes/{iframeId}?payment_token={token}
 *
 * Replace this stub with the real implementation when Paymob sandbox
 * credentials are available. Do NOT deploy with this stub active.
 *
 * @see https://developers.paymob.com/egypt/accept/step-by-step-integration
 */
```

أضف كمان annotation:
```kotlin
@Suppress("FunctionOnlyReturningConstant")
```

#### Fix 2 — HMAC fields فاضية تكسر webhook verification
الملف: `products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/HandlePaymobWebhookService.kt`

المشكلة: `buildSignatureFields` بتحط `"created_at" to ""` و`"integration_id" to ""`
ده يعني كل HMAC verification هتفشل على webhook حقيقي لأن الـfields مش صح.

المطلوب: اجعل `buildSignatureFields` تأخذ القيم من الـpayload الفعلي:

```kotlin
private fun buildSignatureFields(p: PaymobWebhookPayload): Map<String, String> =
    mapOf(
        "amount_cents"             to p.amountCents.toString(),
        "created_at"               to p.createdAt,        // add createdAt to PaymobWebhookPayload
        "currency"                 to p.currencyCode,
        "error_occured"            to "false",
        "has_parent_transaction"   to "false",
        "id"                       to p.transactionId,
        "integration_id"           to p.integrationId,   // add integrationId to PaymobWebhookPayload
        "is_3d_secure"             to "false",
        "is_auth"                  to "false",
        "is_capture"               to "false",
        "is_refunded"              to p.isRefund,
        "is_standalone_payment"    to "true",
        "is_voided"                to "false",
        "order"                    to p.orderId,
        "owner"                    to "",
        "pending"                  to p.pending,
        "source_data.pan"          to p.sourceDataPan,
        "source_data.sub_type"     to p.sourceDataSubType,
        "source_data.type"         to p.sourceDataType,
        "success"                  to p.success,
    )
```

أضف الـfields الجديدة لـ`PaymobWebhookPayload`:
```kotlin
val createdAt: String,
val integrationId: String,
val sourceDataPan: String,
val sourceDataSubType: String,
val sourceDataType: String,
```

وعدّل `PaymobWebhookController.parsePayload()` لتملأها من الـbody:
```kotlin
createdAt       = obj["created_at"]?.toString() ?: "",
integrationId   = obj["integration_id"]?.toString() ?: "",
sourceDataPan   = sourceData["pan"]?.toString() ?: "",
sourceDataSubType = sourceData["sub_type"]?.toString() ?: "",
sourceDataType  = sourceData["type"]?.toString() ?: "",
```

بعد التعديل: حدّث `ToursOperatorPaymentTest` لتمرير الـfields الجديدة في `webhookBody()`.

#### Fix 3 — V17 seed بدون ON CONFLICT خطر على replay
الملف: `platform/application/src/main/resources/db/migration/V17__tours_operator_catalog_seed.sql`

المشكلة: لو Flyway أعاد التشغيل على schema موجود (مثلاً بعد restore من backup)
الـINSERT هيفشل بـunique constraint violation لأن مافيش `ON CONFLICT`.

المطلوب: أضف `ON CONFLICT (slug) DO NOTHING` لكل الـ30 INSERT.

الشكل الصح:
```sql
INSERT INTO wego.tours_operator_tour (
    id, slug, category, ...
) VALUES
    (...),
    (...),
    (...)
ON CONFLICT (slug) DO NOTHING;
```

ملاحظة: الـcomment الحالي يقول "jOOQ DDLDatabase (H2) cannot simulate ON CONFLICT" —
ده صح للـDDL migrations، لكن V17 هو DML-only ومش بيمر على H2 codegen.
تحقق من ذلك قبل التعديل بتشغيل `./gradlew jooqCodegen` بعد التغيير.

---

### بوابة بين Fix و المرحلة 2

بعد الـfixes الثلاثة:

```bash
JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 \
  ./gradlew :platform:application:test --rerun-tasks
# يجب: BUILD SUCCESSFUL — 380+ tests, 0 failures

pnpm --dir web run check
# يجب: contract:check + lint + typecheck + tests + 6 builds — كلها خضراء

bash scripts/safari-tours-sharm-check.sh
# يجب: PASSED
```

لو كل البوابات خضراء: commit الـfixes بـmessage واضح:
```
fix(wego-016-d): critical payment fixes — HMAC fields, checkout stub doc, V17 conflict guard
```

---

### المرحلة 2 — WEGO-016-E: Playwright E2E (mock Paymob)

**الهدف:** اثبت الـflow كامل (booking → pay → webhook → confirm) بدون Paymob credentials حقيقية.

**النطاق:**
- أضف E2E test في `e2e/tests/` يغطي:
  1. عميل يفتح صفحة tour، يختار slot، يملأ بياناته
  2. يضغط "Proceed to Payment"
  3. السيرفر يرجع checkout URL (mock)
  4. test يعمل POST مباشرة على `/api/v1/tours-operator/payments/paymob-callback`
     بـvalid mock HMAC وـamount صح
  5. يتحقق إن الـbooking أصبح CONFIRMED
  6. يتحقق إن صفحة `/booking/confirmation` تعرض الـreference الصحيح

- الـmock Paymob يكون via `MockPaymobClient` (نفس pattern الـunit tests)
  في `e2e/seed.mjs` أو via Spring test profile منفصل.

- اتبع نفس pattern الـE2E في `e2e/tests/erp-lifecycle.spec.ts`.

**خارج النطاق (لا تبدأه):**
- Paymob iframe الحقيقي
- SMS/WhatsApp notifications
- ERP finance screens

**بوابة الخروج:**
```bash
pnpm --filter e2e exec playwright test e2e/tests/safari-checkout.spec.ts
# يجب: passed

bash scripts/safari-tours-sharm-check.sh
# يجب: PASSED
```

Commit بعد الـE2E:
```
feat(wego-016-e): Playwright E2E checkout flow with mock Paymob
```

---

### المرحلة 3 — WEGO-016-F: ERP finance + staff management

**الهدف:** اكمل الـERP بحيث الموظف يقدر يدير الحجوزات والإيرادات من لوحة التحكم.

**النطاق:**
- `web/apps/safari-tours-sharm-erp/app/pages/finance.vue`: اربط الـAPI الحقيقي
  (حالياً mock data)
- `web/apps/safari-tours-sharm-erp/app/pages/bookings.vue`: تأكد إن كل actions
  (confirm، cancel، complete) تشتغل بـpermission gating صحيح
- `web/apps/safari-tours-sharm-erp/app/pages/bookings/[id].vue`: تفاصيل الحجز
  الكاملة
- أضف ERP composables لـfinance aggregation (revenue by period، booking counts)
- أضف vitest tests لكل composable جديد

**خارج النطاق:**
- Paymob refund من ERP (ينتظر credentials)
- Analytics dashboard متقدم
- Staff roles management UI (مؤجل للـG)

**بوابة الخروج:**
```bash
pnpm --dir web run check
# يجب: كل tests خضراء، 6 builds

bash scripts/safari-tours-sharm-check.sh
# يجب: PASSED
```

Commit بعد F:
```
feat(wego-016-f): ERP finance aggregation + complete booking management
```

---

## قواعد ثابتة طول الوقت

- لا commit/push/deploy بدون تفويض صريح من المالك.
- لا تبدأ packet ثانٍ قبل بوابة الأول.
- لا تلمس Paymob credentials أو production config.
- لا تعدل `.env` files أو secrets.
- كل تعديل على migration (V*) يمر على `./gradlew jooqCodegen` أولاً.
- اتبع نفس code style وpatterns الموجودة — لا تخترع abstractions جديدة.
- عدّل `SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md` و`WEGO_EXECUTION_BOARD.md`
  بعد كل مرحلة مكتملة بأدلة حقيقية.
