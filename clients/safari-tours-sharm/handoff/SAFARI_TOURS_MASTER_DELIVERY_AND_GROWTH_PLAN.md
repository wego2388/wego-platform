# Safari Tours Sharm — Master Delivery & Growth Plan

- **آخر تحديث للحالة الحية:** 2026-10-05 — Africa/Cairo
- **الحالة التنفيذية:** `SAFE LOCAL CHECKPOINT / NO-GO FOR PRODUCTION`
- **الحزمة الوحيدة النشطة:** `WEGO-016-MEDIA` — محرر الكتالوج ورفع الصور
- **الإطلاق / Push / Deploy:** غير منفذ وغير مصرح به تلقائيًا
- **المصدر الرسمي لحالة الحزم:** `docs/execution/WEGO_EXECUTION_BOARD.md`

> **أحدث تسليم:** `2026-10-05_CATALOG_MEDIA_SAFE_CHECKPOINT_AR.md`. نفذ محررا
> الرحلات/الفئات ورفع الصور واختبرا؛ قبول MEDIA وDB+media restore وبوابات
> الإصدار لم تكتمل. تفاصيل checkpoints ونِسب سبتمبر أدناه تاريخية؛ لا تعِد
> فتح E أو WEGO-017 ولا تعتبر الموردين/النماذج منفذة بسبب وجود خطة لهما.

هذه هي خريطة العمل الرئيسية التي يستخدمها أي Agent لاحقًا. هي لا تستبدل
Execution Board، ولا تفتح حزمة جديدة تلقائيًا، ولا تمنح إذنًا للنشر. هدفها أن
تجعل المتبقي واضحًا وقابلًا للتسليم بين Codex وClaude دون تخمين.

## 1. معنى العلامات

| العلامة | المعنى | متى تستخدم؟ |
|---|---|---|
| `[x]` | مكتمل ومثبت | يوجد كود ودليل تحقق قابل لإعادة التشغيل |
| `[~]` | جارٍ أو منفذ محليًا لكن بوابته لم تغلق | لا يجوز اعتباره جاهز إنتاج |
| `[ ]` | لم يبدأ | لا يوجد تنفيذ معتمد |
| `[!]` | محجوب | يحتاج مراجعة مستقلة أو قرارًا/بيانات/صلاحيات بشرية |
| `[s]` | متخطى بقصد | يوجد سبب مكتوب ومقبول |

قواعد العلامات:

- لا تتحول المهمة إلى `[x]` لأن الملف موجود فقط؛ يجب أن يمر معيار الخروج.
- وجود build أخضر لا يساوي إطلاقًا تجاريًا.
- أي حقيقة تجارية ناقصة تظل `[!]` ولا تُستكمل بنص مولد أو افتراض.
- أي مراجعة Tier 1 يجب أن تكون مستقلة عن المنفذ الأخير للمناطق الحساسة.

## 2. ترتيب مصادر الحقيقة

عند التعارض يُستخدم الترتيب الآتي:

1. الكود والـmigrations والاختبارات الحالية في Git.
2. `docs/execution/WEGO_EXECUTION_BOARD.md` لحالة الـpacket.
3. هذا الملف وأحدث checkpoint حي.
4. `content-research/approved-catalog.json` للأسعار والحالة والسياسة المعتمدة.
5. `legacy-wordpress-export.json` كمصدر بحثي تاريخي فقط.

الـWordPress القديم ليس runtime source of truth. يحتوي snapshot على 13 صفحة،
30 tour و437 media record، لكن النصوص كلها `UNVERIFIED_SOURCE` وحقوق الصور
`UNVERIFIED`. اعتماد الكتالوج في 2026-09-28 يغطي حقائق الكتالوج المحددة في
`approved-catalog.json`، ولا يحوّل كل وصف أو صورة أو testimonial قديم إلى مادة
صالحة للنشر تلقائيًا.

## 3. القرار التنفيذي الحالي

| المسار | الحالة | القرار |
|---|---|---|
| Domain / booking / payment foundation | `[x]` | E مكتمل محليًا؛ Tier 1 بصفر موانع |
| Compose mock checkout | `[x]` | 13/13 Playwright على stack نظيف؛ Guest Checkout مثبت |
| Migration V17 layout | `[x]` | migration فعلية واحدة؛ runtime يقرأ `data/` وjOOQ لا ينفذ DML |
| Checkout/PII hardening | `[~]` | الإصلاحات مثبتة محليًا؛ Tier 1 مستقل متبقٍ |
| ERP finance | `[!]` | كود أولي موجود لكنه غير صحيح كمصدر إيراد نهائي |
| Notifications / deployment / UAT | `[ ]` | لم تبدأ رسميًا |
| Content migration / international SEO | `[~]` | audit وتجهيز حقائق فقط؛ التنفيذ الكبير ينتظر غلق WEGO-016 |
| Production launch | `[!]` | ممنوع قبل Tier 1 + Paymob sandbox + restore drill + UAT |

**الحكم:** المشروع في نقطة محلية آمنة ومستقرة للتسليم والمراجعة، لكنه ليس
جاهزًا للنشر التجاري بعد.

## 4. ما تم في checkpoint الحالي

### استقرار WEGO-016-E

- [x] إثبات أن V17 موجودة فعليًا مرة واحدة تحت `db/migration/data/`.
- [x] تشغيل jOOQ codegen واختبار migration الحقيقي بنجاح.
- [x] إضافة Compose override يعرض Safari ERP الصحيح بدل ERP العام.
- [x] إضافة Dockerfile معزول لـSafari ERP وبناء non-root.
- [x] فصل أصول Nuxt العامة تحت `/_safari/` لمنع تعارضها مع ERP `/_nuxt/`.
- [x] إصلاح login في Safari ERP لاستخدام `/identity/login` و`/identity/me`.
- [x] جعل Playwright checkout serial لأن E1–E9 سلسلة حالة واحدة مقصودة.
- [x] إضافة bearer token لاختبار lookup المحمي بعد تسجيل الدخول.
- [x] تشغيل flow كامل: create → pay → webhook → PAID/CONFIRMED → site → ERP.
- [x] 13/13 Playwright tests خضراء على Compose project disposable، ومنها
      browser Guest Checkout كامل بلا login للعميل.
- [x] إصلاح ملكية `/`: الموقع العام على `/` والـERP على `/login` ومساراته.
- [x] إصلاح contrast للـheader قبل scroll على Home والصفحات الداخلية.
- [x] فحص logs: لا HMAC أو customer PII أو raw webhook body أو payment token.
- [x] إبقاء mock Paymob opt-in صريحًا وغير مفعل افتراضيًا.

### حماية البيانات والحقيقة التجارية

- [x] تحويل public booking lookup من query-string GET إلى JSON-body POST.
- [x] إضافة validation للعقد وOpenAPI/generated TypeScript وتغطية HTTP/UI.
- [x] إضافة Nginx per-IP rate limit مع `429` و`Retry-After`.
- [x] إزالة query args من access-log format حتى لا تتسرب HMAC/PII.
- [x] إزالة ادعاءات غير مثبتة: 4.9 rating، 500+ guests، 24/7، since 2010،
      hotel pickup العام، وسائل دفع غير مثبتة وعبارة 100% secure.
- [x] تطبيق سياسة الإلغاء المعتمدة نفسها في صفحات EN/AR/RU/IT:
      `>=48h full / 24–48h 50% / <24h no refund`.
- [x] منع tour detail من اختراع pickup/inclusions/restrictions غير موجودة.
- [x] إزالة structured data غير المثبتة والاحتفاظ بـOrganization minimal صادق.
- [x] إزالة مراجع social/favicon assets المفقودة وإضافة favicon حقيقي.
- [x] إضافة اختبارات تمنع رجوع testimonials/ratings/scarcity والحقائق الوهمية.

### البنية والتحقق

- [x] Cache mounts لبناء pnpm داخل Docker.
- [x] Safari site وSafari ERP بُنيا بنجاح من Dockerfiles الأصلية.
- [x] Backend Docker image بُني من Dockerfile الأصلي باستخدام Gradle image
      المثبتة، بلا اعتماد ثانٍ على تنزيل wrapper من GitHub.
- [x] full backend test gate: **390 tests / BUILD SUCCESSFUL**.
- [x] web contract/lint/typecheck/tests/builds: جميعها خضراء؛ Safari site
      11/11 وSafari ERP 56/56 ضمن البوابة.
- [x] Foundry manifests/OpenAPI/YAML validation خضراء.
- [x] `repository-check.sh` و`git diff --check` خضران بعد إخراج artifacts
      المؤقتة من repository.
- [x] Independent Tier 1 review — الجولة الأولى — سجل 19 finding مانعًا.
- [x] كل findings عولجت؛ Tier 1 النهائي: `READY — ZERO BLOCKING findings`.
      E مكتمل محليًا في `8a5e643`، ولا يوجد إذن إنتاج.

## 5. البرنامج الأول — إكمال المنتج التشغيلي WEGO-016

لا يبدأ sub-packet تالٍ قبل إغلاق السابق وتحديث Board.

### المرحلة E — Checkout hardening and independent review — P0

- [x] E-01 migration discovery/layout proof.
- [x] E-02 live Compose + browser checkout proof.
- [x] E-03 public lookup no longer places phone/reference in URL.
- [x] E-04 rate limiting and sanitized access logs.
- [x] E-05 correct Safari ERP image/routing/login.
- [x] E-06 CI runs foundation ERP test, then Safari override checkout.
- [x] E-07 independent fresh-context Tier 1 review — Round 1: 19 blockers.
- [x] E-08 remediate every blocking review finding and rerun affected evidence.
- [x] E-09 close E on Board after zero blocking findings.

**Exit gate:** 13/13 E2E + full gate + zero blocking Tier 1 findings + honest
Board/handoff. لا يلزم deploy لإغلاق implementation، لكن لا deploy قبل H/I.

### المرحلة F — ERP operations, roles and finance — P0

الحالة: `[x] COMPLETE` — commit `28e80f5` (2026-09-30)، Tier 1 READY. البنود
المفتوحة F-08 وإدارة الصلاحيات انتقلت للمرحلة 1 (F2) في `../ROADMAP_AR.md`.

- [x] F-01 تفعيل F رسميًا بعد إغلاق E.
- [x] F-02 جعل revenue مبنيًا على payment ledger: `PAID - REFUNDED`، لا على
      `CONFIRMED/COMPLETED booking status`.
- [x] F-03 فصل pending/failed/mismatch/reconciled عن الإيراد (V20
      `revenue_recognised_at`: refund لـ review capture لا يصبح إيرادًا).
- [x] F-04 basis زمني: `revenueRecognisedAt/refundedAt` بتوقيت القاهرة.
- [x] F-05 منع الخلط بين العملات صراحة (خطأ تحميل ظاهر).
- [x] F-06 صلاحية مالية مستقلة `tours-operator.payment:view`.
- [x] F-07 server-side date range + keyset pagination بلا تكرار.
- [ ] F-08 audit trail لأوامر confirm/cancel/complete/refund → المرحلة 1.
- [x] F-09 إزالة تضارب Nuxt auto-import للأسماء `Money` و`formatMoney`.
- [x] F-10 اختبارات ledger/refund/date/permissions/large totals (D9a–D9h + spec).
- [x] F-11 Tier 1 independent review — round 2 READY.

**Exit gate:** الأرقام قابلة للمصالحة مع payment records، صلاحيات مستقلة، لا
PII زائد، full gate وTier 1 خضران.

### المرحلة G — Notifications and transactional outbox — P1

- [ ] G-01 تفعيل packet بعد F فقط.
- [ ] G-02 email/WhatsApp notifications من outbox بعد transaction commit.
- [ ] G-03 idempotency، retries، dead-letter وoperator visibility.
- [ ] G-04 consent/template/locale/versioning لكل رسالة.
- [ ] G-05 عدم وضع PII في logs أو analytics أو URLs.
- [ ] G-06 review request بعد `COMPLETED` فقط، بلا incentive أو review gating.
- [ ] G-07 Tier 1 review.

### المرحلة H — Deployment, observability and recovery — P0

- [ ] H-01 isolated production Compose/deployment contract.
- [ ] H-02 TLS، domain routing، HSTS، CSP، CORS وsecret rotation plan.
- [ ] H-03 Paymob sandbox الحقيقي بما فيه payment-key API.
- [ ] H-04 happy/declined/timeout/duplicate/late/refund sandbox scenarios.
- [ ] H-05 metrics/logs/alerts بدون PII أو raw payment payloads.
- [ ] H-06 backup + restore drill على قاعدة disposable مع زمن استعادة موثق.
- [ ] H-07 rollback/kill-switch/runbooks.
- [ ] H-08 Lighthouse/browser baseline للصفحات الرئيسية والحجز.
- [ ] H-09 Tier 1 infrastructure/security review.

### المرحلة I — UAT and controlled launch — P0

- [ ] I-01 UAT matrix: locale × device × payment state × booking lifecycle.
- [ ] I-02 business owner signs catalog/policies/contact/company facts.
- [ ] I-03 legal/privacy/consent sign-off.
- [ ] I-04 DNS/certificates/provider IDs/credentials by owner-controlled action.
- [ ] I-05 canary or controlled launch with monitoring and rollback window.
- [ ] I-06 final Tier 1 review and WEGO-016 closure.

## 6. البرنامج الثاني — Proposed WEGO-017 Marketing & Growth Platform

الحالة كلها `[ ] PROPOSED / NOT ACTIVE / NOT AUTHORIZED`. لا تُضاف كـACTIVE
أثناء WEGO-016-E. يفضل تقسيمها إلى packets صغيرة بالترتيب التالي.

### M0 — Content truth and publication readiness — P0

- [ ] تعريف content schema قابل للنسخ EN/AR/RU/IT: value proposition،
      highlights، itinerary، included/excluded، pickup، departure، languages،
      what-to-bring، restrictions، safety، weather، FAQs، related tours.
- [ ] content readiness status: `DRAFT / VERIFIED / APPROVED / PUBLISHED`.
- [ ] owner approval provenance لكل حقل تجاري.
- [ ] media rights/consent/deduplication/alt-text workflow.
- [ ] إبقاء الحقل الناقص ظاهرًا كـreadiness issue، لا تخمينه.

إذا تطلب هذا migration أو auth/PII فهو Tier 1 packet مستقل.

### M1 — SSR multilingual and international SEO foundation — P0

- [ ] locale URLs مثل `/en/...`, `/ar/...`, `/ru/...`, `/it/...`.
- [ ] SSR للمحتوى الحرج بدل `onMounted` للـtour/category data.
- [ ] `html lang/dir` صحيح من أول response.
- [ ] canonical وreciprocal hreflang و`x-default`.
- [ ] localized title/description/OG/Twitter metadata.
- [ ] sitemap index/localized sitemaps وrobots.txt.
- [ ] noindex للحجز/confirmation/payment-result/my-booking/staff.
- [ ] breadcrumb navigation وcrawlable internal links.
- [ ] اختبارات duplicate titles/canonical/hreflang/sitemap/robots/localized routes.

### M2 — Legacy migration and redirects — P0

- [ ] inventory كامل للـ13 page URL و30 tour URL.
- [ ] one-to-one 301 للصفحات المتكافئة مع الحفاظ على slugs عالية القيمة.
- [ ] 404/410 موثق للصفحات بلا بديل؛ لا blanket redirect للـhomepage.
- [ ] map صفحات category القديمة إلى equivalents الجديدة.
- [ ] redirect tests وloop/chain detection.
- [ ] لا تحويل `private-boat` إلى bookable طالما inactive/request-only.

### M3 — Content pages and CRO — P1

- [ ] Home، Tours index، Category، Tour detail data-driven.
- [ ] About/Why Us، Contact، Privacy، Terms، Cancellation/Refund pages.
- [ ] factual CTAs: book/select date/WhatsApp assistance.
- [ ] availability urgency من inventory فقط؛ لا fake scarcity.
- [ ] price/currency من catalog/quote فقط.
- [ ] related tours وdecision-support comparisons.
- [ ] accessible forms/errors/focus/keyboard/reduced-motion.

### M4 — Truthful structured data and reviews — P1

- [ ] Organization/WebSite/WebPage/BreadcrumbList.
- [ ] LocalBusiness فقط بعد إثبات business facts.
- [ ] Tour/Trip/Service semantics فقط عندما تطابق الصفحة.
- [ ] no AggregateRating/Review بدون provenance حقيقي.
- [ ] typed review contract؛ صفر review UI عندما لا توجد reviews موثقة.
- [ ] JSON-LD validation tests.

### M5 — Analytics, consent and attribution — P1

- [ ] provider-neutral typed analytics layer.
- [ ] events: view/select item/list، begin_checkout، add_payment_info، search،
      tour_view، slot_selected، whatsapp_click، booking_lookup، language_switch.
- [ ] `purchase` فقط من backend-confirmed PAID truth.
- [ ] stable transaction id + browser/server idempotency.
- [ ] GA4/Google Ads/Meta Pixel/CAPI adapters بلا IDs أو secrets داخل الكود.
- [ ] consent-aware loading and privacy controls.
- [ ] UTM standard وحفظ attribution خلال navigation بلا PII.
- [ ] أي persistence في booking/payment يحتاج Tier 1 schema packet منفصل.

### M6 — Content, social and distribution operations — P2

- [ ] EN/AR/RU/IT content calendars حسب clusters المعتمدة.
- [ ] منع thin programmatic/AI pages؛ كل صفحة intent وقيمة محلية واضحة.
- [ ] social pillars: experience، education، trust، destination، offers، comparison.
- [ ] specs لـ9:16 و4:5 و1:1 وStories وShorts/Reels/TikTok.
- [ ] Canva templates وDaVinci workflow؛ لا DAM/editor جديد بلا حاجة.
- [ ] human runbooks لـGoogle Business/Search Console/GA4/Ads/Things to do/
      Tripadvisor/Viator/GetYourGuide/Meta/TikTok/YouTube.
- [ ] Wego يظل commercial/inventory source of truth؛ لا booking authority ثانية.

### M7 — Performance, accessibility and launch SEO — P1

- [ ] Lighthouse mobile baselines وbudgets على Home/Tours/Tour/Booking.
- [ ] LCP/INP/CLS، responsive images، AVIF/WebP، explicit dimensions/lazy load.
- [ ] WCAG contrast/keyboard/focus/labels/reduced motion.
- [ ] marketing scripts لا تكسر Core Web Vitals.
- [ ] Search Console validation، sitemap submission، crawl/index monitoring.
- [ ] post-launch dashboard وrollback criteria.

## 7. نموذج تعاون Codex وClaude

القاعدة: Agent واحد منفذ في worktree الحالي، والثاني reviewer مستقل. لا يكتبان
معًا في نفس الملفات أو نفس Board/OpenAPI/migration.

| الدور | المسؤولية |
|---|---|
| Implementer | يقرأ packet، يعدل، يختبر، يسجل الأدلة ولا يراجع نفسه Tier 1 |
| Independent reviewer | fresh context، read-only أولًا، يعيد المخاطر والأدلة ويخرج findings |
| Owner | القرارات التجارية، credentials، commit/push/deploy/DNS والإطلاق |

تسلسل التسليم:

1. المنفذ يثبت branch/HEAD/status والـpacket الوحيد النشط.
2. ينفذ نطاق packet فقط ويشغل targeted ثم full gates.
3. يحدّث هذا الملف بعلامات الحالة دون تحويل `[!]` إلى `[x]`.
4. يتوقف عن الكتابة ويسلم diff + commands + risks.
5. المراجع يبدأ fresh context ويعيد Tier 1 checks.
6. findings تعود للمنفذ؛ بعد الإصلاح يلزم re-review للمناطق المتغيرة.
7. Board يُغلق فقط بعد zero blocking findings.

## 8. أفعال بشرية لا ينفذها Agent تلقائيًا

- [!] اعتماد company name/legal entity/address/phone/support hours/team history.
- [!] اعتماد أو رفض ادعاءات pickup/languages/safety لكل tour.
- [!] إثبات حقوق الصور وموافقات ظهور الضيوف.
- [!] تزويد reviews حقيقية مع source URL/date/name permission.
- [!] Paymob sandbox ثم production credentials عبر secret manager.
- [!] إنشاء/ربط Google Business، Search Console، GA4، Ads، Meta، Tripadvisor
      وOTA accounts.
- [!] DNS/TLS cutover وproduction deployment.
- [!] UAT وlaunch sign-off.

## 8A. البرنامج الثالث — Proposed Android Customer App

الحالة: `[ ] PROPOSED / NOT ACTIVE`. الخطة التنفيذية الكاملة موجودة في
`clients/safari-tours-sharm/mobile/ANDROID_EXECUTION_PLAN.md` وتشمل المعمارية،
الشاشات، API، الحجز وPaymob، EN/AR/RU/IT، offline boundaries، الأمان، analytics،
notifications، الاختبارات، Play Store والإطلاق المرحلي.

- [x] تدقيق mobile workspace وتأكيد أن التطبيقات الموجودة تخص منتجات أخرى
      ولا يجوز تحويلها أو نسخ حقائقها.
- [x] تحديد KMP module وAndroid shell مستقلين لـSafari Tours Sharm.
- [x] تقسيم التنفيذ إلى A0–A9 مع exit gates وعلامات تسليم واضحة.
- [!] التنفيذ محجوب حتى إغلاق `WEGO-016-E` وتفعيل packet مستقل.
- [!] production release يحتاج Play Console/signing/legal/privacy/credentials
      مملوكة للمالك.

## 9. مخاطر معروفة يجب ألا تضيع في التسليم

- E اكتملت محليًا؛ WEGO-017-A هو النشط لعزل Releases العملاء الثلاثة.
- Paymob Intention + Unified Checkout adapter منفذ، لكن sandbox الحقيقي لم
  يُثبت؛ mock ليس دليل production payment.
- Finance الحالي يحسب من booking status وليس payment ledger.
- محتوى tours التفصيلي والترجمات غير مكتملة وغير قابلة للاختراع.
- صور WordPress لها URLs في catalog لكن حقوقها/استضافتها/alt text لم تُعتمد.
- locale حالي client-only؛ SEO متعدد اللغات غير موجود بعد.
- لا canonical/hreflang/sitemap/robots/redirect map/analytics/consent حتى الآن.
- لا backup/restore drill أو production observability أو UAT.

## 10. تعريف نقطة التسليم الآمنة التالية

تتحول E إلى `[x]` فقط عندما:

- [x] reviewer مستقل سجل zero blocking findings.
- [x] findings تم إصلاحها وإعادة اختبارها.
- [x] `safari-tours-sharm-check.sh` وrepository checks خضراء على diff النهائي.
- [x] Board وcheckpoint يعكسان نفس الحقيقة.
- [x] commit `8a5e643`؛ push/deploy غير منفذين.

المالك غيّر الترتيب صراحةً وفعّل `WEGO-017-A` قبل F. لا يبدأ F أو أي M-packet
بالتوازي.
