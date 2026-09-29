# Safari Tours Sharm — Android Execution Plan

- **آخر تحديث:** 2026-09-29 — Africa/Cairo
- **الحالة:** `[ ] PROPOSED / NOT ACTIVE`
- **المنتج:** تطبيق عميل Android لـSafari Tours Sharm
- **قيد الحوكمة:** لا يبدأ التنفيذ قبل إغلاق `WEGO-016-E` وتفعيل packet مستقل
- **مصدر الحقيقة التجاري:** Wego/PostgreSQL و`tours-operator` فقط

هذه الخطة تنفيذية وليست تصورًا عامًا. أي Agent لاحق يجب أن يحدّث العلامات
والأدلة هنا، لكن لا يغيّر حالة الـExecution Board أو يبدأ packet جديدًا دون
تفويض صريح.

## 1. معنى العلامات

| العلامة | المعنى |
|---|---|
| `[x]` | مكتمل ومثبت باختبار أو artifact قابل لإعادة الإنتاج |
| `[~]` | بدأ أو منفذ محليًا لكن بوابة الخروج لم تكتمل |
| `[ ]` | لم يبدأ |
| `[!]` | محجوب بقرار/بيانات/صلاحيات/مراجعة مستقلة |
| `[s]` | متخطى بقصد مع سبب موثق |

## 2. الوضع الحقيقي الآن

- [x] المستودع يستخدم Kotlin Multiplatform وCompose Multiplatform وفق
      `docs/adr/0007-kmp-mobile.md`.
- [x] يوجد Android shell فعلي تحت `mobile/apps/customer-android`، لكنه خاص
      بـSharm Divers Club، وليس Safari Tours Sharm، ولا يجوز تغيير هويته أو
      محتواه لخدمة منتج آخر.
- [x] يوجد نمط تطبيق مستقل في `mobile/apps/sharm-to-go` و
      `mobile/apps/sharm-to-go-android` يمكن الاقتباس منه معماريًا فقط.
- [x] `mobile/shared` يحتوي primitives مشتركة مقصودة، لكنه يحتوي أيضًا نماذج
      خاصة بمنتجات أخرى لا يعاد استخدامها لمجرد تشابه الأسماء.
- [ ] لا يوجد module أو applicationId أو APK خاص بـSafari Tours Sharm حاليًا.
- [ ] لا يوجد Ktor client أو cache دائم أو secure storage أو push adapter
      منفذ فعليًا في mobile workspace الحالي.
- [ ] لا يوجد checkout أو Paymob mobile flow أو booking lookup في تطبيق Android.
- [!] اسم الحزمة النهائي، Play Console، signing key، privacy/data-safety
      declarations وFirebase project تحتاج قرارات/صلاحيات المالك.

## 3. قرارات المعمارية

### 3.1 الشكل المقترح

ينشأ تطبيق مستقل، ولا يتم تحويل أي تطبيق قائم:

```text
mobile/apps/safari-tours-sharm/          # KMP domain/data/UI
mobile/apps/safari-tours-sharm-android/  # Android application shell
```

ويضافان إلى `settings.gradle.kts`. يظل `mobile/shared` مقتصرًا على ما هو مشترك
فعليًا. لا تنقل إليه models خاصة بـSafari قبل وجود consumer ثانٍ حقيقي.

### 3.2 التقنية

- Kotlin/Compose Multiplatform بنفس نسخ version catalog في المستودع.
- Android SDK baseline يبدأ من baseline المستودع ويُراجع عند release وفق
  متطلبات Play الفعلية، لا وفق رقم قد يصبح قديمًا في هذا المستند.
- unidirectional state flow: `UiState + UiAction + ViewModel/Presenter`.
- Ktor Client + kotlinx.serialization لعقد HTTP typed.
- OpenAPI هو مصدر عقد النقل؛ لا تكتب DTOs يدويًا بشكل ينجرف عن
  `platform/contracts/openapi/v1/wego-api.yaml`.
- cache دائم للقراءة فقط في الإصدار الأول. اختيار Room KMP أو SQLDelight يتم
  بعد spike صغير وADR يثبت migrations والاختبارات والتوافق.
- DataStore للإعدادات غير الحساسة: اللغة، consent، وآخر filters.
- Android Keystore فقط لما يحتاج تخزينًا آمنًا. لا تحفظ الهاتف أو بيانات الدفع
  أو Paymob tokens في preferences أو logs.
- dependency injection خفيف؛ لا تضاف framework كبيرة بلا قيمة مثبتة.

### 3.3 حدود مصدر الحقيقة

- الرحلات، الأسعار، العملات، السعة، المواعيد والحالة تأتي من API.
- لا bundled production catalog كبديل دائم ولا أسعار hardcoded.
- إنشاء الحجز والدفع يتمان في backend؛ التطبيق consumer فقط.
- `purchase` لا يطلق من العودة للتطبيق، بل بعد تأكيد backend أن الدفع `PAID`.
- لا authority ثانية للحجز، ولا reconciliation محلي، ولا تعديل booking state
  مباشرة من الهاتف.
- `Private Boat` يظل `REQUEST_ONLY/inactive` حتى يثبت تغير الحقيقة المعتمدة.

## 4. تجربة المنتج

### 4.1 Navigation الأساسية

1. Home
2. Explore tours
3. Categories / filters
4. Tour detail
5. Availability / slot
6. Party and customer details
7. Review and payment
8. Payment status / confirmation
9. My Booking lookup
10. Saved tours محليًا فقط إن أضيفت بوضوح
11. About / Contact / Policies / Settings

لا حساب عميل إجباري في الإصدار الأول. `My Booking` يستخدم `reference + phone`
عبر JSON-body POST، ولا يضع PII في URL أو deep link أو analytics.

### 4.2 حالات كل شاشة

كل شاشة بيانات يجب أن تملك loading/content/empty/recoverable-error/offline/
blocked states، مع state restoration وRTL وlarge-font وscreen-reader behavior.

### 4.3 تصميم Android

الاتجاه البصري هو **Sinai Afterglow** نفسه في
`DESIGN_UX_FRONTEND_EXCELLENCE_SPEC.md`، لكن بمكونات Android أصلية:

- edge-to-edge مع safe insets؛
- Material 3 semantics مع Ocean/Sand/Sunset tokens؛
- adaptive navigation: bottom bar على الهاتف وrail/pane للمساحات الأكبر؛
- responsive grids بدل نسخة desktop مصغرة؛
- sticky booking CTA لا يغطي الحقول أو IME؛
- predictive back وstate restoration؛
- touch targets لا تقل عن 48dp؛
- font scaling حتى 200% دون قص؛
- TalkBack labels/order/actions؛
- contrast AA وfocus واضح للكيبورد؛
- motion وظيفي مع احترام reduced motion؛
- لا auto-play video أو parallax ثقيل أو shimmer دائم.

مصفوفة QA المرئية: 320dp compact، 360/390dp phones، 600dp tablet،
840dp expanded/foldable، portrait/landscape، light/dark، EN/AR/RU/IT، font
scale 1.0/1.3/2.0.

## 5. تعدد اللغات والمحتوى

- [ ] EN/AR/RU/IT من أول release العام؛ لا fallback صامت لنص تجاري ناقص.
- [ ] Arabic RTL كامل مع عزل الأرقام/reference/money كـLTR عند الحاجة.
- [ ] واجهة التطبيق resource-based أو typed localized contract قابلة للاختبار.
- [ ] محتوى الرحلات من API، مع content readiness؛ لا يختلق التطبيق inclusions
      أو pickup أو restrictions أو safety أو weather.
- [ ] سياسة الإلغاء المعتمدة تظهر من المصدر الرسمي:
      `>=48h full / 24–48h 50% / <24h no refund` ما لم يثبت تغييرها.
- [ ] صور فقط بحقوق مثبتة؛ placeholders لا تبدو كضيوف حقيقيين.

## 6. الشبكة والبيانات

### API paths المطلوبة في الإصدار الأول

- public active tours and tour detail؛
- slots/availability؛
- create booking؛
- initiate payment؛
- payment status polling؛
- booking lookup POST؛
- policy/content endpoints فقط إن أصبحت source-of-truth في backend.

### قواعد التنفيذ

- timeouts وretry للـGET الآمن فقط، مع jitter/backoff وحد أقصى.
- POST create/pay لا يعاد تلقائيًا إلا مع idempotency contract مثبت backend-side.
- responses غير 2xx تتحول إلى typed errors، لا exception خام.
- correlation id تقني بلا PII.
- TLS defaults الآمنة؛ لا trust-all أو pinning هش بلا rotation plan.
- network debug logging في debug فقط ومع redaction.
- cache للcatalog/content والصور؛ availability/payment/booking status لا تعامل
  كحقيقة صالحة طويلًا عند offline.
- يعرض cached content مع وقت تحديث، لكنه يمنع checkout بلا اتصال.

## 7. الحجز والدفع

1. التطبيق يختار tour/slot/party ويطلب validation من backend.
2. backend ينشئ booking ويعيد reference والحالة والسعر الموثوق.
3. backend يبدأ Paymob ويعيد hosted checkout URL.
4. Android يفتح Custom Tab أو browser آمن؛ لا يجمع card data داخل التطبيق.
5. App Link يعود إلى status فقط، ولا يعتبر العودة نجاحًا.
6. التطبيق polls status بحدود واضحة ويعرض pending/paid/failed/expired.
7. confirmation لا تظهر إلا من backend truth؛ refresh/deep-link idempotent.

اختبارات إلزامية: double tap، process death، background/foreground، back أثناء
الدفع، timeout، declined، late success، duplicate callback، stale slot، price
change، sold out، loss of network، locale switch وrotation.

## 8. Analytics، consent والإشعارات

- typed provider-neutral analytics interface مشتركة في المعنى مع الويب.
- events: `view_item_list`, `select_item`, `view_item`, `tour_view`,
  `slot_selected`, `begin_checkout`, `add_payment_info`, `purchase`, `search`,
  `booking_lookup`, `language_switch`, `whatsapp_click`.
- لا phone/email/reference الكامل أو special requests في event properties.
- attribution/UTM عبر App Links مع allowlist وحدود طول.
- provider adapters وIDs عبر config؛ لا production secrets.
- consent قبل marketing analytics وفق privacy design.
- FCM يضاف فقط بعد packet Notifications/outbox؛ الإشعار transactional من
  backend event، لا من polling محلي.
- deep link من الإشعار لا يضع PII في URL.

## 9. الأمان والخصوصية

- Network Security Config يمنع cleartext في release.
- release غير debuggable، وminification/resource shrinking بعد baseline tests.
- signing key خارج Git وفي owner-controlled secret store.
- لا أسرار API داخل APK؛ أي قيمة قابلة للاستخراج منه تعتبر public config.
- screenshots/clipboard policies حسب حساسية الشاشة بدون إفساد UX العام.
- exported activities/providers/receivers أقل ما يمكن ومحددة صراحة.
- App Links تتحقق من domain و`assetlinks.json` في packet النشر.
- dependency/SBOM/license/vulnerability checks داخل CI.
- privacy policy وData Safety يعكسان collection الفعلية، لا template عامة.
- retention/delete/support workflows يجب أن تكون حقيقية قبل التصريح بها.

## 10. الاختبارات وبوابات الجودة

### طبقات الاختبار

- common unit tests: parsing، money، locale، reducers، state machines.
- API contract fixtures: success/error/unknown fields/version drift.
- repository tests: cache/network/stale/clock/retry.
- Compose JVM UI: navigation، كل states، RTL، semantics، no fake copy.
- Android instrumentation: deep links، Custom Tabs، process recreation، IME،
  rotation، permissions، accessibility smoke.
- screenshot/golden tests للمصفوفة الأساسية.
- E2E ضد Compose disposable stack وmock Paymob، وليس UI mocks فقط.
- release APK/AAB build، lint، ktlint، dependency وmanifest checks.
- Macrobenchmark/ProfileInstaller لبدء التشغيل والscroll حيث يفيد.

### معايير غير وظيفية مبدئية

- no crash/ANR في الرحلة الأساسية؛
- لا main-thread network/disk؛
- state restoration بعد process death دون إعادة POST خطرة؛
- لا PII/payment tokens في logcat، analytics، deep links أو crash breadcrumbs؛
- app size/startup/jank baselines تسجل في أول packet ثم تمنع regression بنسب
  مبنية على القياس، لا أرقام مخترعة مسبقًا.

## 11. المراحل التنفيذية المقترحة

كل مرحلة packet مستقلة أو sub-packet معلن، ولا تعمل بالتوازي في نفس worktree.

### A0 — Product contract and scaffolding — P0

- [ ] اعتماد اسم التطبيق، package/applicationId الدائم وPlay ownership.
- [ ] إضافة module KMP وAndroid shell منفصلين إلى Gradle.
- [ ] flavors: `dev`, `staging`, `prod` مع base URL غير سري.
- [ ] theme/tokens، navigation shell، locale framework EN/AR/RU/IT.
- [ ] CI يبني checks وdebug APK؛ لا signing production.
- [ ] manifest hardening وbaseline unit/UI tests.

**Exit:** التطبيق يفتح offline shell، يبني على CI، وكل mobile products القائمة
ما زالت خضراء.

### A1 — Typed API and read-only catalog — P0

- [ ] transport contract من OpenAPI، Ktor client، error model، redacted logging.
- [ ] Home/Tours/Categories/Tour detail من API الحقيقي.
- [ ] image loader/cache وoffline stale catalog.
- [ ] search/filter/sort حسب contract.
- [ ] four-locale rendering وRTL/accessibility tests.

**Exit:** لا catalog/price hardcoding، contract tests خضراء، وoffline لا يدعي
availability حديثة.

### A2 — Availability and booking draft — P0

- [ ] availability/slot picker مع timezone واضح.
- [ ] party/details validation وserver-owned quote.
- [ ] draft state restoration دون تخزين PII طويلًا.
- [ ] stale price/slot/sold-out recovery.
- [ ] create-booking idempotency decision؛ أي backend change Tier 1.

**Exit:** booking `NEW` واحد فقط رغم double tap/retry/process recreation.

### A3 — Paymob hosted checkout and confirmation — P0 / Tier 1

- [ ] Custom Tab flow + verified return link.
- [ ] backend-confirmed status polling and terminal states.
- [ ] payment retry rules دون duplicate charge/booking.
- [ ] confirmation/my-booking بلا PII في URLs.
- [ ] E2E mock ثم Paymob sandbox على أجهزة حقيقية.
- [ ] independent Tier 1 security/payment review.

**Exit:** happy/declined/timeout/duplicate/late callback/process-death scenarios
خضراء، وصفر blocking findings.

### A4 — My Booking, support and legal — P1

- [ ] lookup JSON POST مع rate-limit/error UX.
- [ ] booking details حسب public-safe projection فقط.
- [ ] WhatsApp/contact بدون ادعاء ساعات دعم غير معتمدة.
- [ ] Privacy/Terms/Cancellation/About pages versioned.

### A5 — Analytics, consent and attribution — P1 / Tier 1 if persisted

- [ ] typed event contract وconsent state.
- [ ] provider adapters بدون IDs حقيقية في Git.
- [ ] purchase idempotency من PAID truth.
- [ ] App Link UTM allowlist وسلامة navigation.
- [ ] أي attribution persistence يقترح backend packet منفصل.

### A6 — Notifications — P1 / depends on WEGO-016-G

- [ ] FCM registration/rotation/revocation.
- [ ] consent/preferences وlocale-aware templates من backend.
- [ ] booking state deep links آمنة.
- [ ] retry/dedup/outbox observability.
- [ ] لا review request إلا بعد `COMPLETED` وبلا incentive/gating.

### A7 — UX polish, performance and device matrix — P1

- [ ] final assets بعد rights approval.
- [ ] adaptive tablet/foldable UI وdark mode.
- [ ] motion/reduced-motion وsystem bars/IME/insets.
- [ ] TalkBack/large font/contrast/accessibility audit.
- [ ] Macrobenchmark، Baseline Profile، image/memory/network budgets.
- [ ] physical-device matrix على Android versions المستهدفة.

### A8 — Release engineering and closed testing — P0

- [ ] owner-controlled Play Console، package ownership وPlay App Signing.
- [ ] production API/TLS/App Links/assetlinks verification.
- [ ] privacy policy، Data Safety، content rating، store listing EN/AR/RU/IT.
- [ ] screenshots/feature graphic من build الحقيقي، لا fake reviews/claims.
- [ ] staged internal ثم closed testing؛ crash/ANR/feedback triage.
- [ ] release candidate AAB reproducible + SBOM + checksum.

### A9 — Controlled production release — P0

- [ ] owner UAT لكل locale/device/payment state.
- [ ] production config عبر secret store وCI protected environment.
- [ ] phased rollout مع monitoring وstop criteria.
- [ ] support/runbook/incident ownership.
- [ ] post-launch verification للحجز/الدفع/الإشعارات/analytics.
- [ ] Tier 1 release review وowner sign-off.

## 12. الملفات المتوقعة

```text
settings.gradle.kts
gradle/libs.versions.toml
.github/workflows/ci.yml
mobile/README.md
mobile/apps/safari-tours-sharm/build.gradle.kts
mobile/apps/safari-tours-sharm/src/commonMain/kotlin/.../
  api/ data/ domain/ analytics/ design/ navigation/ ui/
mobile/apps/safari-tours-sharm/src/commonTest/kotlin/.../
mobile/apps/safari-tours-sharm/src/jvmTest/kotlin/.../
mobile/apps/safari-tours-sharm-android/build.gradle.kts
mobile/apps/safari-tours-sharm-android/src/main/AndroidManifest.xml
mobile/apps/safari-tours-sharm-android/src/main/kotlin/.../MainActivity.kt
mobile/apps/safari-tours-sharm-android/src/main/res/...
clients/safari-tours-sharm/mobile/
  ANDROID_EXECUTION_PLAN.md
  ANDROID_RELEASE_RUNBOOK.md
  ANDROID_PRIVACY_DATA_MAP.md
  ANDROID_UAT_MATRIX.md
```

لا تُنشأ الملفات الفارغة مقدمًا؛ كل packet يضيف فقط ما يستخدمه ويختبره.

## 13. أوامر التحقق المستهدفة بعد إنشاء modules

```bash
./gradlew \
  :mobile:shared:check \
  :mobile:apps:safari-tours-sharm:check \
  :mobile:apps:safari-tours-sharm-android:check \
  :mobile:apps:safari-tours-sharm-android:assembleDebug

./gradlew :mobile:apps:safari-tours-sharm-android:bundleProdRelease
bash scripts/repository-check.sh
git diff --check
```

`bundleProdRelease` لا يعمل بتوقيع حقيقي على جهاز Agent؛ CI protected release
job والمالك هما المسؤولان عن signing والنشر.

## 14. قرارات المالك المطلوبة قبل A0/A8

- [!] الاسم النهائي الظاهر وapplicationId الدائم.
- [!] ملكية Play Console والـlegal developer name/support contacts.
- [!] سياسة privacy وdata retention/delete channel.
- [!] signing/Play App Signing وFirebase/analytics accounts.
- [!] حقوق logo/icon/photos/video/store screenshots.
- [!] production domain/API environment وApp Links ownership.
- [!] Paymob sandbox/production credentials من secret manager.
- [!] store listing facts وcontent rating answers.

هذه البنود لا تمنع code غير المعتمد على credentials بعد تفعيل packet، لكنها
تمنع release production.

## 15. المخاطر التي يجب ألا تضيع

- نسخ تطبيق `customer` الحالي سيسحب حقائق Sharm Divers Club الخاطئة؛ يستخدم
  كنمط تقني فقط، لا source content.
- applicationId قرار دائم عمليًا بعد النشر؛ لا يعتمد placeholder ثم يُنسى.
- offline checkout خطر duplicate/stale inventory؛ الإصدار الأول يمنعه.
- العودة من Paymob ليست إثبات دفع.
- remote catalog لا يعني أن كل field صالح للنشر؛ content readiness مستمر.
- analytics/push SDKs قد تضر الخصوصية والأداء؛ تضاف بعد measurement وconsent.
- دعم أربعة locales يضاعف QA الفعلي، وليس مجرد ترجمة strings.
- أي تعديل idempotency/schema/auth/payment في backend يعاد كـTier 1 packet، ولا
  يخبأ داخل mobile implementation.

## 16. تعريف النضج

لا يوصف التطبيق بأنه production-ready حتى تصبح A0–A9 `[x]`، مع:

- zero blocking Tier 1 findings للحجز/الدفع/release؛
- API/catalog/policy truth من backend؛
- full locale/device/payment UAT؛
- signed closed-test evidence ثم phased production rollout؛
- monitoring/support/rollback واضح؛
- لا أسرار أو PII أو ادعاءات أو reviews مختلقة.

