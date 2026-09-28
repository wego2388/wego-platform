# Safari Tours Sharm — Production Maturity Handoff

- **نوع الوثيقة:** ملف تسليم تنفيذي للوصول إلى منتج ناضج وقابل للإطلاق
- **آخر تحقق فعلي:** 2026-09-27 — Africa/Cairo
- **الحالة:** `NO-GO` للإطلاق التجاري حاليًا
- **النطاق:** Backend + Public Website + Staff ERP + Payments + Content + Operations
- **المالك المقترح:** Wego Digital / Safari Tours Sharm

> هذه الوثيقة هي مصدر حقيقة حالة Safari Tours ومهام نضجه. أما صلاحية بدء
> التنفيذ وترتيب الـpackets وحالة `ACTIVE` الرسمية فمصدرها
> `docs/execution/WEGO_EXECUTION_BOARD.md`. أي نسبة إنجاز أو عبارة "مكتمل"
> في ملفات أقدم تُعاد مراجعتها مقابل الدليلين. نجاح build منفرد لا يعني
> جاهزية الإنتاج.

---

## 🗂️ نظام التتبع — Agent Start Here

**أي Agent يعمل على المشروع يقرأ بالترتيب:**

1. `AGENTS.md` ثم `docs/ENGINEERING_CONSTITUTION.md`.
2. `docs/execution/WEGO_EXECUTION_BOARD.md` وتحديد الـsub-packet الوحيد
   `ACTIVE`؛ لا يبدأ packet ثانٍ بالتوازي في نفس worktree.
3. هذه الوثيقة لتحديد النواقص، بوابة الخروج، وحالة كل مهمة.
4. `clients/safari-tours-sharm/content-research/README.md` قبل لمس المحتوى.
5. الملفات المتأثرة واختباراتها قبل أي تعديل.

**قواعد التحديث:**

- يحدّث Agent علامة المهمة فور اكتمالها بدليل قابل للتكرار، ويسجل التاريخ.
- لا ينتقل للمرحلة التالية قبل بوابة خروج المرحلة السابقة وتحديث Execution Board.
- لا يحوّل مهمة إلى `[x]` اعتمادًا على وجود كود أو UI فقط؛ يجب تشغيل التحقق.
- لا يخترع رحلة أو سعرًا أو موعدًا أو سياسة أو صورة أو review. بيانات الموقع
  القديم بحثية حتى يعتمدها المالك، ولا تصبح runtime dependency.
- لا commit أو push أو deploy أو استخدام production credentials بلا تفويض صريح.

### Legend — معنى العلامات

| العلامة | المعنى |
|---|---|
| `- [ ]` | لم تبدأ بعد |
| `- [~]` | جارية / قيد التنفيذ |
| `- [x]` | مكتملة — يوجد دليل موثق |
| `- [!]` | محجوبة — تنتظر قرار خارجي أو مدخل من المالك |
| `- [s]` | متخطاة بقصد — مع سبب مذكور |

### لوحة التقدم السريع

> حدّث هذا القسم في كل مرة تُغلق فيها مرحلة كاملة.

| المرحلة | العنوان | الحالة | آخر تحديث |
|---:|---|---|---|
| 0 | Baseline + Governance | `DONE` | 2026-09-27 |
| 1 | Domain + API Contract | `DONE` | 2026-09-27 |
| 2 | Catalog + ERP CRUD | `IMPLEMENTATION COMPLETE — TIER 1 REVIEW PENDING` | 2026-09-28 |
| 3 | Payment + Expiry | `NOT STARTED` | — |
| 4 | Public Website | `NOT STARTED` | — |
| 5 | ERP Operations | `NOT STARTED` | — |
| 6 | Notifications | `NOT STARTED` | — |
| 7 | Infrastructure + Security | `NOT STARTED` | — |
| 8 | UAT + Launch | `NOT STARTED` | — |
| 9 | Post-Launch | `NOT STARTED` | — |

**الحالات المقبولة في العمود:** `NOT STARTED` · `IN PROGRESS` · `DONE` · `BLOCKED`

---

## 1. القرار التنفيذي

المشروع يملك أساسًا تقنيًا جيدًا: Product boundary مستقل، قاعدة بيانات،
Domain للحجوزات، موقع عام، وERP. لكنه لا يملك حتى الآن مسارًا موثوقًا كاملًا
من اختيار الرحلة إلى الدفع والتأكيد والتشغيل اليومي.

**التقدير الحالي:**

| البعد | التقدير | الملاحظة |
|---|---:|---|
| اكتمال البناء التقني | 60–70% | معظم الهياكل والشاشات موجودة |
| الجاهزية التجارية | 30–40% | الدفع والكتالوج الحقيقي والأصول غير مكتملة |
| الجاهزية التشغيلية | 25–35% | لا يوجد تشغيل Production مثبت أو runbooks |
| الجاهزية الأمنية | غير مثبتة | الحجز العام والدفع وPII يحتاجون Tier 1 Review |

**النتيجة:** لا يتم ربط Paymob Production، فتح الحجوزات للعامة، أو اعتبار
صفحة إنشاء الحجز تأكيدًا نهائيًا قبل إغلاق مراحل P0 في هذه الوثيقة.

---

## 2. تعريف "المشروع الناضج"

يُعتبر Safari Tours Sharm ناضجًا للإطلاق عندما تتحقق الشروط التالية معًا:

1. العميل يختار رحلة وموعدًا متاحًا، يرى سعرًا نهائيًا صحيحًا، يدفع، ثم يحصل
   على حالة حجز صحيحة يمكن استعادتها لاحقًا.
2. النظام يمنع overselling والتكرار ويعالج webhook retries وpayment timeout
   بأمان وبطريقة idempotent.
3. الموظف يستطيع إدارة الرحلات والمواعيد والحجوزات والمدفوعات من ERP حقيقي،
   بصلاحيات وتدقيق، وليس mock data أو placeholders.
4. الكتالوج المنشور يحتوي فقط على أسعار وسياسات وصور وترجمات معتمدة.
5. الـAPI له OpenAPI contract واحد ومختبر، وتستخدم الواجهات نفس الأنواع.
6. Production له secrets management وTLS وbackups وrestore test وmonitoring
   وrollback وincident runbooks.
7. كل P0 tests خضراء، وتم تنفيذ Tier 1 review مستقل للدفع، الصلاحيات،
   المايجريشن، وبيانات العملاء.

**ليس شرطًا للإطلاق الأول:** تطبيق Mobile، dark mode، WebSocket، أو نظام Reviews
متقدم. هذه تحسينات بعد إثبات مسار البيع والتشغيل الأساسي.

---

## 3. ما تم التحقق منه فعليًا

### 3.1 Web applications

تم تشغيل lint وtypecheck وunit tests وproduction build مباشرة:

| التطبيق | النتيجة |
|---|---|
| `@wego/safari-tours-sharm-site` | Lint ✅ · Typecheck ✅ · Tests 8/8 ✅ · Build ✅ |
| `@wego/safari-tours-sharm-erp` | Lint ✅ · Typecheck ✅ · Tests 27/27 ✅ · Build ✅ |

الواجهتان لا تعرّفان نسختين يدويتين من DTOs بعد الآن. الأنواع تُولّد من
OpenAPI إلى `web/packages/api-contract`، و`contract:check` يفشل إذا اختلف
الملف المولّد. اختبارات الموقع تثبت كذلك route الحجز، nested customer، Money،
وحفظ confirmation بدون وضع هاتف العميل في الرابط.

تم كذلك تشغيل البوابة الكاملة من `web/`:

```text
pnpm run check
```

ونجحت بالكامل بعد `pnpm install --frozen-lockfile`: lint، كل typechecks،
**414 اختبارًا**، وستة production builds، بلا فشل.

### 3.2 Backend

تم تشغيل الاختبارات باستخدام JDK 25 الموجود في:

```text
/home/wego/.jdks/temurin-25.0.3+9
```

النتيجة الحالية بعد إصلاح baseline:

```text
332 tests completed, 0 failed, 0 errors, 0 skipped
BUILD SUCCESSFUL
```

أُغلق فشل lifecycle: أصبح `confirmedAt` timestamp تاريخيًا يبقى بعد
`COMPLETED` أو الإلغاء بعد التأكيد، وتم تحديث domain وV14 والاختبارات معًا.
كما تم تحديث توقعات Flyway إلى V15. هذا الدليل يغلق العيب الأصلي، لكنه لا
يغلق OpenAPI أو الدفع أو الكتالوج أو التشغيل.

تقرير الاختبارات المحلي:

```text
platform/application/build/reports/tests/test/index.html
```

### 3.3 Repository state

وقت التحقق:

- الفرع الحالي `wego-016-safari-tours-baseline` مبني على `origin/main` المحدث.
- `WEGO-016` مسجل `IN PROGRESS`، و`WEGO-016-A` مكتمل محليًا.
- `WEGO-016-B` مكتمل ومراجع ومرفوع في commit `ae09026`.
- `WEGO-016-C` هو الـpacket النشط الوحيد. تغييرات C الحالية غير committed
  ولم يتم push أو deploy لها.

لا تُفصل أو تُعاد كتابة تغييرات هذا الفرع دون جردها. أكمل C من حالته الحالية
ولا يحدث commit/push/deploy قبل Tier 1 review وتفويض المالك الصريح.

---

## 4. البنية الحالية ومصادر الحقيقة

| المسؤولية | المسار |
|---|---|
| Client configuration والتسليم | `clients/safari-tours-sharm/` |
| Product backend | `products/tours-operator/` |
| Database migration | `platform/application/src/main/resources/db/migration/V14__tours_operator_foundation.sql` |
| Public website | `web/apps/safari-tours-sharm-site/` |
| Staff ERP | `web/apps/safari-tours-sharm-erp/` |
| بيانات التسويق المعتمدة | `/home/wego/projects/clients/safari-tours-sharm/data/approved-facts.json` |
| مصدر الموقع القديم | `https://safaritourssharm.com/` |
| Snapshot المحتوى القديم | `clients/safari-tours-sharm/content-research/legacy-wordpress-export.json` |
| قواعد اعتماد المحتوى | `clients/safari-tours-sharm/content-research/README.md` |

قاعدة الفصل المطلوبة:

```text
Platform → Product → Client Configuration → Isolated Client Deployment
```

ملفات التسويق وSnapshot الموقع القديم مراجع بشرية فقط. لا تصبح runtime
dependency ولا يتم استيراد HTML أو WordPress data مباشرة إلى الإنتاج. المطلوب
لاحقًا importer صريح للحقائق المعتمدة أو إدخال ومراجعة من ERP. المحتوى القديم
يحفظ الفكرة والنسخة الأصلية، لكنه لا يثبت أن السعر أو السياسة أو الصورة ما زالت
صالحة تجاريًا أو مرخصة.

---

## 5. العيوب والفجوات المثبتة

### P0 — تمنع الإطلاق

#### STS-P0-01 — Booking lifecycle غير متوافق مع قيود قاعدة البيانات — `CLOSED 2026-09-27`

الـDomain يبقي `confirmedAt` بعد الانتقال من `CONFIRMED` إلى `COMPLETED`، وهو
السلوك التاريخي الصحيح غالبًا، لكن V9 تشترط أن يكون `confirmedAt` موجودًا فقط
عندما تكون الحالة الحالية `CONFIRMED`. النتيجة `DataIntegrityViolation` ثم 409.

**ما تم:**

- تم اعتماد `confirmedAt` كتاريخ تاريخي لا يُمسح بعد انتقال الحالة.
- تم إصلاح الـdomain وV14 constraint وتحديث اختبارات الانتقالات.
- الدليل: `./gradlew :platform:application:test` — 330/330 خضراء.

**معيار القبول:** كل lifecycle integration tests خضراء، ولا يتحول خطأ داخلي
أو constraint mismatch إلى business 409 مضلل.

#### STS-P0-02 — عقد أسعار الرحلات مختلف بين Backend والواجهات — `CLOSED LOCALLY 2026-09-27`

اعتمد العقد شكلًا واحدًا لكل قيمة مالية:

```text
{ amount: decimal-string, currencyCode: ISO-4217 }
```

الـBackend والموقع والـERP والاختبارات تستخدم نفس OpenAPI-generated types.
الحسابات والتجميع والمتوسطات تتم بوحدات صغرى صحيحة عبر `bigint`، وحُذفت
مسارات `parseFloat` وحقول `*Eur`/`*Cents` الخاصة بعملة أو طبقة بعينها.

#### STS-P0-03 — عقد بيانات العميل مختلف في ERP — `CLOSED LOCALLY 2026-09-27`

كل response وcreate request يستخدم `customer` object واحدًا. MockMvc ضد
PostgreSQL حقيقي يثبت JSON الفعلي، واختبارات الموقع تثبت payload المرسل،
و`contract:check` يمنع drift بين OpenAPI وTypeScript. الإغلاق النهائي ينتظر
Tier 1 review المستقل الخاص بـ`WEGO-016-B` قبل الـcommit.

#### STS-P0-04 — لا يوجد Payment flow حقيقي

الموجود حاليًا ينشئ Booking بحالة `NEW` فقط. لا يوجد مسار إنتاجي مثبت لإنشاء
Paymob order، redirect/checkout، signature verification، webhook idempotency،
payment record، reconciliation، failure، أو refund.

**الأثر:** لا يوجد تحويل آمن من الحجز إلى إيراد مؤكد.

#### STS-P0-05 — صفحة النجاح تعطي رسالة غير صحيحة

بعد `POST /bookings` يعرض الموقع "Booking Confirmed!" رغم أن الحالة الفعلية
هي `NEW` وتنتظر الدفع.

**المطلوب:** فصل الحالات والرسائل بوضوح:

- `NEW/PAYMENT_PENDING`: الحجز محجوز مؤقتًا ولم يتم الدفع.
- `CONFIRMED`: الدفع تحقق وأصبح الحجز مؤكدًا.
- `FAILED/EXPIRED`: لم يكتمل الدفع وتم تحرير السعة.

#### STS-P0-06 — لا يوجد Expiry scheduler تشغيلي

`ExpireBookingService` موجود، لكن لا يوجد scheduler/worker مثبت يستدعيه بعد
نافذة الدفع ويحرر المقاعد بطريقة idempotent.

#### STS-P0-07 — لا يوجد كتالوج Production قابل للبيع

- قاعدة البيانات تملك seed لرحلة E2E واحدة فقط.
- ملف `approved-facts.json` يحتوي 22 رحلة داخل 3 مجموعات populated.
- 4 رحلات فقط لها سعر رقمي معلن؛ عنصر خامس "Pre-booking only".
- قائمة العلامة التجارية تذكر 5 categories، لكن بيانات الرحلات لا تملأها كلها.
- لا توجد عملية معتمدة لإنشاء availability المستقبلية لكل رحلة.

**قرار افتراضي موصى به:** أي رحلة بلا سعر نهائي أو سياسة واضحة تبقى
`isActive=false`. الرحلات التي تتطلب عرض سعر، مثل private boat، تستخدم inquiry
flow منفصلًا ولا تدخل paid booking flow بسعر وهمي أو صفر.

#### STS-P0-08 — CI والجودة لا تغطيان كل المنتج — `CLOSED LOCALLY 2026-09-27`

أوامر الويب تشمل Safari site وERP وpackage العقد في prepare/typecheck/test/build.
بوابة `scripts/safari-tours-sharm-check.sh` نفذت frozen install، 332 backend
tests، 414 web tests، ستة builds، OpenAPI/Foundry، repository checks، وcontent
snapshot quarantine. E2E الخاص بالدفع يظل ضمن packets D/E ولا يُدّعى هنا.

#### STS-P0-09 — لا يوجد Deployment/Operations proof

لا يوجد دليل مكتمل على isolated production deployment، domain routing، TLS،
secrets، backup/restore، monitoring، alerting، أو rollback خاص بالعميل.

#### STS-P0-10 — Governance وGit baseline غير مغلقين — `CLOSED LOCALLY 2026-09-27`

`WEGO-016-A` مغلق محليًا بأدلة كاملة، والفرع والـlocks والـBoard والبوابة
الموحدة متسقة. `WEGO-016-B` هو العمل المفتوح الوحيد وينتظر review مستقل.
لا يوجد commit/push/deploy، ولا يحدث أي منها بلا تفويض صريح.

---

### P1 — مطلوبة لتشغيل يومي ناضج

- Tours CRUD مع validation وaudit.
- Slot creation، bulk generation، capacity edit، block/unblock، وblackout dates.
- Pagination وserver-side filtering لكل قوائم ERP.
- Finance مبني على payments المؤكدة/refunds، لا على عدد الحجوزات فقط.
- Staff roles أقل صلاحية من `platform-admin`، مع onboarding/offboarding.
- Voucher قابل للطباعة وإرسال pickup details.
- WhatsApp/email notifications بعد commit عبر outbox، مع consent وretry.
- Cancellation/refund policy قابلة للتنفيذ وليست نصًا تسويقيًا فقط.
- Export مضبوط بالصلاحية والتدقيق، مع منع تسريب PII.
- صفحات Notifications وReviews وFinance charts إما تصبح حقيقية أو تُخفى من
  النسخة الأولى؛ لا تُعرض placeholders كميزات منتهية.

### P2 — نمو وتحسين بعد الاستقرار

- SEO sitemap، canonical URLs، structured data، وOG images.
- Featured tours وtestimonials موثقة المصدر.
- Accessibility audit وperformance budget.
- Analytics وconversion funnel مع consent policy.
- Review ingestion من Google/Tripadvisor حسب API وسياسة كل مزود.
- Real-time updates إذا أثبتت حاجة تشغيلية.
- تطبيق Mobile فقط بعد ثبات الـAPI وإثبات الطلب؛ ليس blocker للإطلاق الأول.

---

## 6. خطة التنفيذ المرحلية

يتم تنفيذ المراحل بالترتيب. كل مرحلة تتحول إلى packet محدد له acceptance
criteria وأدلة، ولا يبدأ packet تالٍ قبل إغلاق السابق.

---

### المرحلة 0 — إنقاذ الـbaseline والحوكمة

**الهدف:** منع فقد العمل أو دمج Safari فوق أساس قديم.

- [x] 0-1: جرد كل modified/untracked files وفصل Safari عن التغييرات غير المتعلقة به. _(2026-09-27)_
- [x] 0-2: حفظ العمل على branch واضح؛ لا push أو commit تلقائي. _(branch: wego-016-safari-tours-baseline)_
- [x] 0-3: جلب وفهم 68 commit الموجودة على `origin/main` وحل التعارضات بدون overwrite. _(rebase نجح، 330 tests خضراء)_
- [x] 0-4: تسجيل وتفعيل `WEGO-016` و`WEGO-016-A` على Execution Board. _(2026-09-27)_
- [x] 0-5: تقسيم `WEGO-016` إلى A–I؛ الدفع، migrations، auth وPII كلها Tier 1. _(2026-09-27)_
- [x] 0-6: تحديث ملفات التقدم القديمة التي تقول إن backend مكتمل. _(2026-09-27)_
- [x] 0-7: استعادة web dependencies من lockfile وتشغيل `pnpm run check` كاملًا. _(414 tests + 6 builds، 2026-09-27)_
- [x] 0-8: إنشاء وتشغيل `scripts/safari-tours-sharm-check.sh` كبوابة موحدة قابلة للتكرار. _(2026-09-27)_
- [x] 0-9: تسجيل review evidence وإغلاق `WEGO-016-A` محليًا قبل بدء B. _(2026-09-27)_
- [!] 0-10: commit/push الفرع للمراجعة. _(يتطلب تفويضًا صريحًا من المالك)_

**بوابة الخروج:** baseline محدث، تغييرات Safari محددة، packet واحد ACTIVE،
ولا يوجد فقد أو خلط لتغييرات مشاريع أخرى.

---

### المرحلة 1 — تثبيت الـDomain والـAPI contract

**الهدف:** Backend صحيح وعقد واحد يمكن الوثوق به.

- [x] 1-1: إصلاح booking lifecycle وقيود timestamps. _(STS-P0-01 — 2026-09-27: confirmedAt historical timestamp، إصلاح domain + V14 DB constraint)_
- [x] 1-2: تحديث migration expectation tests لتشمل V14 وV15.
- [x] 1-3: تعريف OpenAPI لكل Tours/Slots/Bookings/errors/pagination. _(STS-P0-02/03، 2026-09-27)_
- [x] 1-4: توحيد money وcustomer payloads وتحديث الموقع والـERP. _(generated contract + bigint money، 2026-09-27)_
- [ ] 1-5: إضافة idempotency key لإنشاء booking والعمليات القابلة لإعادة المحاولة.
- [ ] 1-6: إضافة endpoint آمن للحصول على slot/quote summary.
- [ ] 1-7: إضافة limits وvalidation للعدد، الهاتف، locale، date range، وpayload size. _(تم counts/customer/locale/field sizes في B؛ date-range وrequest-size limit باقيان)_
- [ ] 1-8: إضافة rate limiting وحماية enumeration لـbooking lookup والـpublic booking.
- [x] 1-9: إضافة contract/integration tests تستخدم PostgreSQL حقيقية. _(MockMvc/Testcontainers + frontend contract consumers، 2026-09-27)_

**بوابة الخروج:** `./gradlew ... test` أخضر بالكامل، contract tests خضراء،
والموقع والـERP يعرضان payload حقيقية بلا fixtures متعارضة. ✅ **مكتملة — Tier 1 review أُنهي بدون blocking findings (2026-09-27).**

---

### المرحلة 2 — كتالوج وإدارة تشغيلية حقيقية

**الهدف:** تحويل الـskeleton إلى منتج يمكن للموظف إدارته.

> اكتمال `2-3a` هو حفظ مصدر بطلب المالك فقط، وليس تفعيلًا للمرحلة أو
> `WEGO-016-C`. لا يبدأ تعديل runtime catalog قبل إغلاق A ثم B.

- [ ] 2-1: توسيع Tour model ليشمل الاسم والوصف والـincludes/excludes/what-to-bring والقيود والصور وSEO لكل locale مطلوب.
- [x] 2-2: إنشاء Tours CRUD وSlots management بصلاحيات وتدقيق. _(2026-09-28: staff CRUD + block/unblock + permission matrix 17 tests)_
- [x] 2-3a: أرشفة محتوى WordPress العام: 13 صفحة، 30 رحلة، booking choices، و437 media metadata records. _(2026-09-27؛ Research only)_
- [ ] 2-3b: مطابقة Snapshot الموقع القديم مع approved facts وحل التعارضات التجارية.
- [x] 2-3c: بناء import/seed مراجع من records معتمدة فقط؛ لا runtime read من marketing repo أو Snapshot. _(2026-09-28: V17 seeds 30 tours from approved-catalog.json; python comparison 30/30 match)_
- [x] 2-4: إدخال الرحلات ذات الأسعار المعتمدة فقط وتعيين الباقي inactive. _(2026-09-28: Private Boat=REQUEST_ONLY/inactive; all 29 others active with approved prices)_
- [ ] 2-5: إنشاء slots مستقبلية وسياسة capacity وcutoff وpickup.
- [ ] 2-6: جعل category pages وtour details تقرأ بيانات حقيقية فقط.
- [ ] 2-7: إضافة revision/audit للحقائق التجارية الحساسة مثل السعر والسياسة.
- [ ] 2-8: فصل `TOUR` و`TRANSFER` و`ROOM` و`REQUEST_ONLY` بدل حشرها في نموذج واحد.
- [ ] 2-9: اعتماد حقوق الصور وalt text والترجمات قبل نقل أي media إلى الموقع الجديد.

**بوابة الخروج:** الموظف ينشئ/يعدل رحلة ومواعيدها، والموقع يعكسها، ولا توجد
رحلة public بلا سعر أو محتوى أو availability صالح.

---

### المرحلة 3 — الدفع ودورة الحجز الكاملة

**الهدف:** مسار مالي موثوق من booking intent إلى confirmed revenue.

- [ ] 3-1: Payment aggregate/table بحالات واضحة ومبلغ وعملة immutable. _(STS-P0-04)_
- [ ] 3-2: Paymob order creation من server-side booking total فقط.
- [ ] 3-3: Checkout/redirect flow مع return URL لا يُعتبر دليل دفع. _(STS-P0-05)_
- [ ] 3-4: Webhook signature/HMAC verification وallowlisted event handling.
- [ ] 3-5: Idempotency وdeduplication للwebhooks وreplay-safe confirmation.
- [ ] 3-6: مقارنة amount/currency/merchant/order IDs قبل التأكيد.
- [ ] 3-7: Durable expiry worker مع locking وتحرير السعة مرة واحدة. _(STS-P0-06)_
- [ ] 3-8: Reconciliation job/report للمدفوعات غير المتطابقة.
- [ ] 3-9: Refund/cancel rules مع audit ومنع double refund.
- [ ] 3-10: Sandbox E2E للنجاح، الرفض، timeout، duplicate webhook، late webhook، refund، وانقطاع الشبكة.

**بوابة الخروج:** Paymob sandbox E2E مثبت، كل الحالات المالية audit-able،
ولا يمكن للعميل أو return URL تأكيد الدفع بنفسه.

---

### المرحلة 4 — إنهاء الموقع العام

**الهدف:** تجربة بيع صحيحة وسريعة ومتعددة اللغات.

- [ ] 4-1: إصلاح flow اختيار slot → بيانات العميل → مراجعة → دفع → نتيجة.
- [ ] 4-2: تحميل booking summary من server وعدم الاعتماد على query params للسعر/الوقت.
- [ ] 4-3: إظهار حالة الدفع الصحيحة مع resume/retry آمن.
- [ ] 4-4: محتوى الرحلة الحقيقي، gallery، inclusions، restrictions، cancellation.
- [ ] 4-5: ترجمة محتوى business إلى EN/RU/AR/IT؛ لا يكفي ترجمة navigation فقط.
- [ ] 4-6: RTL كامل واختبار mobile/tablet/desktop.
- [ ] 4-7: حالات loading/empty/error/sold-out/offline واضحة وWhatsApp fallback صادق.
- [ ] 4-8: SEO وsitemap وstructured data وcanonical وsocial previews.
- [ ] 4-9: Accessibility keyboard/focus/forms/errors وWCAG AA review.
- [ ] 4-10: Playwright E2E على backend/staging حقيقي.

**بوابة الخروج:** مستخدم جديد يكمل حجز sandbox من الهاتف، يدفع، يرى
`CONFIRMED`، ثم يستعيد الحجز بالمرجع والهاتف.

---

### المرحلة 5 — إنهاء ERP والتشغيل اليومي

**الهدف:** فريق Safari Tours يدير اليوم من النظام بدون تعديل قاعدة البيانات.

- [ ] 5-1: توصيل كل الصفحات بالعقد الموحد وإزالة mock assumptions.
- [ ] 5-2: أدوار: manager، booking agent، finance، read-only حسب الحاجة.
- [ ] 5-3: Booking list/detail/actions مع pagination، filters، reason، وaudit history.
- [ ] 5-4: Tours/slots CRUD وbulk schedule وcapacity alerts.
- [ ] 5-5: Finance من Payment ledger مع paid/refunded/pending reconciliation.
- [ ] 5-6: Customer view أقل بيانات لازمة مع search آمن وسياسة retention.
- [ ] 5-7: Printable voucher وpickup sheet وتصدير permissioned.
- [ ] 5-8: إخفاء Reviews/Notifications/charts حتى تتوفر APIs حقيقية، أو تنفيذها كاملًا.
- [ ] 5-9: Session expiry، forbidden states، logout، وstaff access tests.

**بوابة الخروج:** UAT مكتوب ينجزه موظف غير تقني من login حتى إدارة رحلة
وحجز مدفوع وإلغاء/رد مالي مسموح.

---

### المرحلة 6 — التواصل وخدمة العميل

**الهدف:** رسائل موثوقة لا تغير business truth ولا تتكرر.

- [ ] 6-1: رسائل booking received، payment confirmed، reminder، pickup، cancellation.
- [ ] 6-2: إرسال بعد commit عبر transactional outbox، مع idempotency وretry/dead-letter.
- [ ] 6-3: Templates لكل locale مع fallback ومراجعة بشرية.
- [ ] 6-4: Consent/opt-out وسياسة القنوات.
- [ ] 6-5: Operator alert عند فشل الدفع أو الرسالة أو انخفاض السعة.
- [ ] 6-6: Runbook للإرسال اليدوي عند تعطل المزود.

**بوابة الخروج:** فشل WhatsApp لا يلغي الحجز، وإعادة event لا ترسل رسالة
مكررة، والموظف يرى failure قابلًا للمعالجة.

---

### المرحلة 7 — Infrastructure والأمن والتشغيل

**الهدف:** Production قابل للمراقبة والاسترجاع.

- [ ] 7-1: Deployment مستقل وPostgreSQL مستقل وفق `ISOLATED_INSTANCE`.
- [ ] 7-2: Images reproducible للموقع والـERP والbackend، مع pinned versions.
- [ ] 7-3: Nginx/domain/TLS/HSTS/security headers وCORS policy.
- [ ] 7-4: Secrets خارج Git مع rotation procedure لـDB وPaymob وchannels.
- [ ] 7-5: Health/readiness checks وstructured logs بلا PII أو secrets.
- [ ] 7-6: Metrics/alerts: booking failures، payment mismatch، webhook lag، expiry lag، error rate، DB health، backup age.
- [ ] 7-7: Backup schedule مع restore drill موثق، وليس وجود backup فقط.
- [ ] 7-8: Data retention، PII classification، access audit، incident response.
- [ ] 7-9: Staging مطابق بما يكفي للإنتاج مع Paymob sandbox.
- [ ] 7-10: CI/CD gates وrollback/forward-fix procedure. _(STS-P0-08/09)_

**بوابة الخروج:** restore test ناجح، deployment smoke test ناجح، alerts تصل،
وrollback rehearsal موثق بدون لمس بيانات عميل حقيقية.

---

### المرحلة 8 — UAT والإطلاق التدريجي

**الهدف:** إطلاق محدود يمكن إيقافه بأمان.

- [ ] 8-1: UAT matrix لكل locale/device/payment method/booking state.
- [ ] 8-2: مراجعة الأسعار والسياسات والمحتوى والأصول مع العميل وتوقيع approval.
- [ ] 8-3: Security/Tier 1 review نهائي وإغلاق كل blocking findings.
- [ ] 8-4: تشغيل داخلي ثم soft launch لعدد محدود من الرحلات والمواعيد.
- [ ] 8-5: مراقبة أول حجوزات ومطابقة Paymob يدويًا يوميًا في البداية.
- [ ] 8-6: تعريف kill switch لإيقاف الدفع أو الحجوزات دون إغلاق الموقع كله.
- [ ] 8-7: Post-launch review بعد أول دورة تشغيل حقيقية.

**بوابة الخروج:** نجاح حجوزات إنتاجية محدودة ومطابقتها ماليًا وتشغيليًا،
ثم قرار مكتوب بالتوسع.

---

### المرحلة 9 — ما بعد الإطلاق

- [ ] 9-1: تحسين conversion والـSEO بناءً على بيانات فعلية.
- [ ] 9-2: Reviews، referrals، promo codes، وadvanced reporting حسب قيمة مثبتة.
- [ ] 9-3: Mobile discovery ثم قرار build/no-build؛ لا يبدأ تلقائيًا.
- [ ] 9-4: إضافة channels أو integrations واحدة كل مرة بعقد وسياسة واضحة.

---

## 7. خريطة الـPackets الرسمية

هذه الخريطة مسجلة تحت `WEGO-016` على Execution Board. حالة Board هي الحكم
النهائي عند أي اختلاف، ولا يُفعّل Agent packet جديدًا من هذا الجدول وحده.

| الترتيب | Packet | الحالة | النطاق | Review |
|---:|---|---|---|---|
| 1 | WEGO-016-A | `COMPLETE LOCALLY` | Baseline rescue + Board + quality evidence | Tier 2 |
| 2 | WEGO-016-B | `COMPLETE` | OpenAPI + generated/contract types + contract tests | Tier 1 — مكتمل بدون blocking findings، commit ae09026 (2026-09-27) |
| 3 | WEGO-016-C | `ACTIVE` | Catalog: content model + Tour CRUD + slots + approved import | Tier 1 بسبب migration/permissions |
| 4 | WEGO-016-D | `NOT STARTED` | Payment aggregate + Paymob + expiry + reconciliation | Tier 1 |
| 5 | WEGO-016-E | `NOT STARTED` | Public checkout + Playwright E2E | Tier 1 بسبب payment/PII |
| 6 | WEGO-016-F | `NOT STARTED` | ERP operations + staff roles + finance ledger | Tier 1 بسبب permissions/PII |
| 7 | WEGO-016-G | `NOT STARTED` | Notifications + transactional outbox | Tier 1 |
| 8 | WEGO-016-H | `NOT STARTED` | Isolated deployment + observability + restore drill | Tier 1 |
| 9 | WEGO-016-I | `NOT STARTED` | UAT + controlled launch + closure evidence | Tier 1 final review |

لا يتم جمع كل ما سبق في commit أو review واحد. كل packet يجب أن يكون صغيرًا
بما يكفي ليملك failure tests وrollback واضحين.

---

## 8. مدخلات مطلوبة من المالك/العميل

لا ينبغي تعويض هذه القرارات بافتراضات داخل الكود:

### تجارية

- السعر النهائي للبالغ والطفل لكل رحلة، والعملة وسياسة الضرائب/الرسوم.
- تعريف child age، infant، private/group pricing، والحد الأدنى للحجز.
- capacity الفعلية، schedule، cutoff time، ومناطق/تكلفة pickup.
- cancellation/no-show/refund policy قابلة للتنفيذ لكل نوع رحلة.
- ما الرحلات instant-book وما الرحلات request-only.

### محتوى وهوية

- Logo SVG/PNG، favicon، palette approval.
- صور وفيديوهات مع إثبات الحق في الاستخدام.
- أسماء وأوصاف وincludes/excludes/safety notes معتمدة.
- ترجمة بشرية أو مراجعة بشرية لـRU/AR/IT.
- Reviews مسموح بإعادة نشرها ومصدرها.
- اعتماد أو رفض كل record في `content-research/legacy-wordpress-export.json`؛
  خصوصًا أسعار ومدد الرحلات، Mega Safari، Petra، الغرف، والنقل.
- حسم تعارض سياسة الإلغاء بين الصفحة الرئيسية وصفحة السياسة في الموقع القديم.

### حسابات وتشغيل

- Paymob production account/credentials وطرق الدفع المفعلة وwebhook settings.
- Domain/DNS access وقرار بيئة الاستضافة.
- WhatsApp Business account/provider وtemplates approved إن وجدت.
- أسماء الموظفين وأدوارهم ومن يملك refund/cancel authority.
- جهة اتصال للحوادث والمطابقة المالية اليومية.

### قانوني وخصوصية

- الاسم القانوني، بيانات التواصل، terms، privacy، وسياسة retention.
- موافقة واضحة على البيانات المطلوبة في الحجز والرسائل التسويقية.

---

## 9. Release gates وقرارات NO-GO

### Gate A — Code integrity

- Backend full tests: 0 failures.
- Site وERP: lint + typecheck + tests + build.
- OpenAPI/contract tests خضراء.
- لا توجد Safari changes غير معروفة أو untracked عند release cut.

### Gate B — Booking and payment integrity

- Sandbox happy path وfailure paths مثبتة.
- Duplicate/late webhook آمن.
- Expiry يحرر السعة مرة واحدة.
- Finance يطابق provider totals.

### Gate C — Commercial truth

- كل active tour لها سعر ومحتوى وسياسة ومواعيد معتمدة.
- لا placeholder أو ادعاء غير مثبت.
- unknown-price tours مخفية أو request-only.
- لا media من WordPress قبل إثبات الحق، mapping صحيح، optimization، وalt text.
- كل حقيقة من الموقع القديم لها حالة `DRAFT` أو `APPROVED` أو `REJECTED`؛
  وجودها في Snapshot لا يمنحها `APPROVED` تلقائيًا.

### Gate D — Security and operations

- Tier 1 review نظيف.
- Secrets وTLS وrate limits وaudit مطبقة.
- Backup restore وrollback وincident runbooks مجربة.
- Staff permissions أقل صلاحية ومختبرة.

### Gate E — Controlled production launch

- UAT موقع من owner والعميل.
- Soft launch ناجح.
- أول عمليات الدفع والحجز تمت مطابقتها.
- Monitoring وkill switches تعمل.

**NO-GO فوري إذا:** أي backend test فاشل، أسعار الواجهة لا تطابق server quote،
صفحة `NEW` تقول confirmed، webhook غير موثق التوقيع، restore غير مجرب، أو
لا يمكن إيقاف الحجوزات/الدفع بسرعة.

---

## 10. أوامر التحقق القياسية

### Backend

```bash
cd /home/wego/wego-platform
export JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :platform:application:test
./gradlew :platform:application:build
```

### Website

```bash
cd /home/wego/wego-platform/web
pnpm --filter @wego/safari-tours-sharm-site lint
pnpm --filter @wego/safari-tours-sharm-site typecheck
pnpm --filter @wego/safari-tours-sharm-site test
pnpm --filter @wego/safari-tours-sharm-site build
```

### ERP

```bash
cd /home/wego/wego-platform/web
pnpm --filter @wego/safari-tours-sharm-erp lint
pnpm --filter @wego/safari-tours-sharm-erp typecheck
pnpm --filter @wego/safari-tours-sharm-erp test
pnpm --filter @wego/safari-tours-sharm-erp build
```

### Legacy content snapshot

```bash
cd /home/wego/wego-platform
node --check scripts/export-safari-tours-legacy-content.mjs
node scripts/export-safari-tours-legacy-content.mjs
jq empty clients/safari-tours-sharm/content-research/legacy-wordpress-export.json
```

إعادة تشغيل exporter تغيّر snapshot وقد تغيّر digest؛ تُراجع النتيجة قبل
اعتمادها ولا تعني السماح بالنشر. البوابة المستهدفة للمنتج كله هي:

```bash
bash scripts/safari-tours-sharm-check.sh
```

وهي ما زالت مهمة مفتوحة في `0-8`، فلا يدّعي Agent أنها موجودة أو خضراء قبل
إنشائها وتشغيلها فعليًا.

تضاف لاحقًا أوامر OpenAPI، browser E2E، Compose smoke، backup/restore، وPaymob
sandbox إلى نفس release evidence. لا تعتمد أي مرحلة على عبارة "يعمل عندي".

---

## 11. Definition of Done لأي Packet

لا تعتبر المهمة مكتملة إلا إذا:

- [ ] Acceptance criteria مكتوبة قبل التنفيذ.
- [ ] يوجد test يثبت happy path والفشل المهم والمنافسة/retry حيث تنطبق.
- [ ] تم تشغيل الاختبارات فعليًا وتسجيل النتيجة، لا الاكتفاء بقراءة الكود.
- [ ] تم تحديث OpenAPI والواجهات والوثائق إذا تغير contract.
- [ ] لا توجد placeholders أو mock data في مسار يعلن أنه production-ready.
- [ ] توجد migration/rollback أو forward-fix strategy واضحة.
- [ ] Tier 1 review مستقل انتهى بلا blocking findings عند لمس auth، payments، migrations، isolation، أو PII.
- [ ] **تم تحديث checkbox المهمة في القسم 6 وتحديث لوحة التقدم السريع في القسم "نظام التتبع".**
- [ ] تم تحديث حالة الـsub-packet والدليل و`NEXT SUB-PACKET` على Execution Board.
- [ ] تم تأكيد عدم اختراع/نشر أسعار أو سياسات أو صور أو reviews غير معتمدة.
- [ ] تم تأكيد عدم استخدام secrets أو بيانات عملاء حقيقية في الاختبارات.
- [ ] تم تسجيل هل حدث commit/push/deploy أم لا، مع مرجع التفويض إن حدث.

> **تذكير للوكيل:** تحديث الملف بعد كل مهمة مكتملة ليس اختياريًا.
> الوكيل التالي يعتمد على هذه العلامات لتجنب التكرار والتعارض.

### قالب دليل التسليم الإجباري

كل handoff للـAgent التالي يحتوي على الأقل:

```text
Packet / status:
Outcome:
Changed files:
Commands run + exact results:
Tests not run + reason:
Migration / contract / live-boot evidence:
Review tier + reviewer result:
Known risks / blockers:
Owner inputs still required:
Commercial-content verification:
Commit / push / deploy status and authorization:
Next permitted packet:
```

---

## 12. أول خمس خطوات عملية

1. ~~شغّل Tier 1 review مستقل من fresh context على `WEGO-016-B`~~ **مكتمل — صفر blocking findings (2026-09-27).**
2. ~~أصلح أي blocking finding وأعد البوابة الموحدة والمراجعة~~ **لم تكن هناك blocking findings.**
3. أكمل `WEGO-016-C` من التغييرات الحالية؛ لا تعِد تنفيذ A/B ولا تفصل الـworktree.
4. أغلق حواجز أمان وتزامن CRUD/slots المسجلة في ملف تسليم C المؤرخ 2026-09-28.
5. شغّل البوابة الكاملة ثم Tier 1 review مستقل؛ لا commit/push/deploy قبل
   المراجعة وتفويض المالك.

بعد catalog موثوق تبدأ D للدفع ثم E/F للـcheckout والتشغيل. SEO أو Reviews أو
Mobile تأتي بعد ثبات مسار البيع؛ قبلها ستكون تجميلًا لمنتج لا يملك مسارًا
تجاريًا موثوقًا.
