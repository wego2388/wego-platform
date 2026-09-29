# Safari Tours Sharm — WEGO-016-E Hardened Safe Checkpoint

- **وقت التجميد:** 2026-09-29 06:00 Africa/Cairo
- **الحالة:** `COMPLETE LOCALLY / TIER-1 READY / COMMITTED / NOT PUSHED`
- **الحزمة النشطة التالية:** `WEGO-017-A` لعزل Foundry التنفيذي
- **Branch:** `wego-016-safari-tours-baseline`
- **Base HEAD:** `f04a0fa`
- **Commit:** `8a5e643` · **Push / deploy / DNS:** لم يحدث
- **Production readiness:** `NO-GO` حتى Paymob sandbox وUAT وبوابات التشغيل

هذا الملف هو نقطة استئناف تنفيذية، وليس تصريح نشر. عند التعارض يحكم
`docs/execution/WEGO_EXECUTION_BOARD.md` حالة الـpacket والتفويض.

## معنى العلامات

| العلامة | المعنى |
|---|---|
| `[x]` | مكتمل بدليل منفذ |
| `[~]` | منفذ محليًا لكن البوابة الخارجية/المراجعة لم تغلق |
| `[ ]` | لم يبدأ |
| `[!]` | ينتظر حقيقة تجارية أو credential أو قرار مالك |

## الحكم المختصر

- [x] العميل يحجز كـGuest من الموقع **بلا حساب وبلا login**.
- [x] دخول الموظف فقط على origin مستقل للداشبورد.
- [x] مسار booking → payment → webhook → backend-confirmed confirmation يعمل
  داخل Compose في متصفح حقيقي.
- [x] الموقع والداشبورد والـBackend وPostgreSQL وRedis والـedge جميعها healthy.
- [x] جولة Tier 1 الأولى كشفت 19 مانعًا وعولجت؛ إعادة الفحص كشفت 6 موانع
  أدق، وكلها عولجت في working tree الحالي مع اختبارات رجوع.
- [x] إعادة المراجعة المستقلة انتهت: `READY — ZERO BLOCKING findings`.
- [!] تكامل Paymob Intention/Unified Checkout منفذ، لكن Paymob sandbox الحقيقي
  غير مثبت لعدم وجود credentials معتمدة.
- [!] لا نشر تجاري قبل sandbox وUAT وbackup/restore وقرار المالك.

## البيئة المحلية الجاهزة للتجربة

| السطح | الرابط / البيانات |
|---|---|
| الموقع العام | `http://127.0.0.1:58084/` |
| لوحة الموظفين | `http://staff.localhost:58084/login` |
| staff email التجريبي | `e2e-staff@example.com` |
| staff password التجريبي | `e2e-synthetic-password-123` |

هذه credentials صناعية لبيئة E2E المحلية فقط. لا تمثل حسابًا أو secret
للإنتاج. الموقع العام لا يعرض login للعملاء، و`/login` على الـpublic origin
يعيد 404.

## ما أُثبت فعليًا

### البوابة الموحدة

- [x] `bash scripts/safari-tours-sharm-check.sh` — **PASS**.
- [x] Backend: **390 tests**، `BUILD SUCCESSFUL`؛ 0 failures/errors/skips.
- [x] OpenAPI generated contract مطابق.
- [x] ESLint بلا warnings، وكل typechecks خضراء.
- [x] **446 Vitest tests** خضراء عبر workspace:
  `6 + 88 + 38 + 134 + 24 + 37 + 52 + 11 + 56`.
- [x] ستة Nuxt production builds خضراء، ومنها Safari site وSafari ERP.
- [x] Foundry manifests وOpenAPI وrepository YAML خضراء.
- [x] content snapshot checksum/quarantine guards خضراء.
- [x] repository invariants و`git diff --check` خضراء.

### بوابة المتصفح والـCompose

- [x] `safari-checkout.spec.ts`: **13/13 Chromium passed** في 12.6s على
  قاعدة جديدة داخل `wego-safari-e2e-final`.
- [x] E0: public root/assets وعزل staff host.
- [x] E1–E7: إنشاء الحجز، payment idempotency، HMAC صحيح/مكرر/خاطئ،
  وPAID/CONFIRMED truth.
- [x] E8: confirmation لا تُمنح من URL reference أو Session Storage وحدهما؛
  E8b يثبت أن حجز `NEW` مزروع في المتصفح لا يظهر كمؤكد.
- [x] E9: حجز الموظف ظاهر، وlogout يلغي token في الـBackend.
- [x] E10: Guest Checkout كامل من المتصفح بلا حساب أو login.
- [x] Public `/` = 200، Staff `/login` = 200، Public `/login` = 404.
- [x] lookup limiter أعاد JSON 429 مع `Retry-After` في الدليل الحي.
- [x] Compose log scan لم يجد HMAC أو customer PII أو payment secret أو raw
  callback؛ callback path فقط ظل كإشارة تشغيلية.

### migrations والدفع

- [x] V17 نُقل إلى مسار data من دون تغيير bytes المنشورة؛ SHA-256:
  `5c537d0d5c5c6138038666bd68621d698add970675167c73cae039861acb734a`.
- [x] V19 يضيف stable provider reference وpayment constraints/indexes وredacted
  audit وcheckout token و`RECONCILIATION_REQUIRED` للحالات الملتبسة.
- [x] Upgrade test حقيقي من V18 إلى V19 يزرع provider IDs مكررة، يعزلها في
  quarantine، ثم يثبت نجاح V19 والـunique indexes بدل تعطيل startup.
- [x] Initiation يحفظ هوية محاولة ثابتة قبل الاتصال الخارجي، ولا يعيد محاولة
  دفع ملتبسة قد تنشئ تحصيلًا ثانيًا؛ النجاح المتأخر يبقى قابلًا للمصالحة.
- [x] HMAC canonical fields وهوية integration/account/owner والمبلغ والعملة
  تُتحقق قبل تغيير الحقيقة التجارية.
- [x] initiation/expiry/webhook تستخدم locks وترتيبًا ثابتًا وتغطي retry/races.
- [x] staff manual-confirm أزيل؛ confirmation يتطلب `PAID` من الـBackend.
- [x] Paymob mock لا يعمل في base Compose؛ override الـE2E وحده يفعله.

## حالة المراجعة المستقلة

- [x] Round 1: 19 `BLOCKING` findings موثقة في Execution Board وعولجت.
- [x] Re-review أول كشف 6 `BLOCKING` إضافية: upgrade V19، crash-idempotency،
  هوية المحاولة القديمة، confirmation truth، staff lookup، وCI routing.
- [x] remediation محلي للستة ولـ3 advisories مع اختبارات وأدلة حية.
- [x] المراجع المستقل أعاد 22 اختبارًا حساسًا وراجع Compose والمسارات
  والـmigrations والسجلات: `READY — ZERO BLOCKING findings`.
- [x] المالك قبل الـcheckpoint وفوّض الـcommit المحلي؛ E أُغلق في `8a5e643`.
- [~] `WEGO-017-A` هو الـpacket النشط؛ F لم يُفعّل تلقائيًا.

## حالة مساحة الجهاز

- [x] قسم `/` خرج من 100%، والمتاح وقت آخر فحص **6.1 GiB** بعد صور البناء.
- [x] قسم `/home` عند **83%**، والمتاح **16 GiB**.
- [x] أزيلت فقط حاويات/صور مشروعي E2E قديمين وDocker build cache.
- [x] لم تُحذف أي Docker volume أو قاعدة بيانات للمشروع الحالي.
- [x] stack النهائي `wego-safari-e2e-final` يعمل على قاعدة نظيفة، والـstack
  السابق محفوظ كذلك من دون حذف volume.

تجنب Docker rebuild غير الضروري قبل المراجعة؛ build cache أصبح صفرًا وأي build
جديد سيستهلك مساحة `/` مرة أخرى.

## ما لم يتم عمدًا

- [ ] لا commit ولا push.
- [ ] لا deploy ولا DNS/TLS cutover.
- [ ] لا production credentials أو Paymob sandbox credentials.
- [ ] لا تفعيل packet جديدة؛ F يظل خلف بوابة E.
- [ ] لا ادعاء بأن mock يثبت Paymob production.
- [ ] لا fake reviews/ratings/scarcity ولا حقائق تجارية مخترعة.

## الخطوة التالية الدقيقة

1. لا push أو deploy لـSafari بدون تفويض منفصل.
2. نفّذ `WEGO-017-A` في worktree مستقل لإنتاج Releases منفصلة للعملاء الثلاثة.
3. لا تُفعّل Safari F بالتوازي؛ يعود بعد بوابة Foundry بقرار مالك جديد.
