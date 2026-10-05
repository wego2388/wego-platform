# Safari Tours Sharm — handoff index

## Agent start here

> **الحالة الحية — 5 أكتوبر 2026:** ابدأ من
> [تسليم الكتالوج والصور](2026-10-05_CATALOG_MEDIA_SAFE_CHECKPOINT_AR.md)، ثم
> Board. مكان التنفيذ `/home/wego/wego-safari-hardening`، والحزمة الوحيدة
> النشطة `WEGO-016-MEDIA`. الكود والمحرران منفذان؛ قبول الحزمة وDB+media
> restore والإصدار لم يكتملوا. الملاحظات المؤرخة بسبتمبر أدناه سجل تاريخي،
> وليست تعليمات لفتح WEGO-017 أو إعادة WEGO-016-E.

> **بداية الحسابات السابقة — سجل سبتمبر التاريخي:**
> `2026-09-29_NEW-CHATGPT-ACCOUNT_START-HERE.md` بعد قراءة
> `CHATGPT_MULTI_ACCOUNT_WORKFLOW.md`. الملفان يوثقان checkpoint سبتمبر
> وحماية العمل بين حسابَي ChatGPT؛ أي ترتيب قديم أدناه تاريخي عند التعارض.
>
> **snapshot سبتمبر المجمد المقبول آنذاك:**
> `2026-09-29_WEGO-016-E_HARDENED_SAFE_CHECKPOINT.md`.

اقرأ بالترتيب قبل أي تعديل:

1. `../OWNER_PROJECT_MAP_AR.md` لخريطة المالك المبسطة.
2. `AGENTS.md` و`docs/ENGINEERING_CONSTITUTION.md`.
3. `docs/execution/WEGO_EXECUTION_BOARD.md` لمعرفة الـpacket الوحيد المصرح به.
4. `SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md` للحالة والنواقص وبوابات الخروج.
5. `SAFARI_TOURS_MASTER_DELIVERY_AND_GROWTH_PLAN.md` للخطة الموحدة والعلامات.
6. `../marketing/CURRENT_STATE_AUDIT_2026-09-29.md` لتدقيق P0/P1/P2.
7. `../design/DESIGN_UX_FRONTEND_EXCELLENCE_SPEC.md` لخطة UI/UX والـmotion.
8. `../mobile/ANDROID_EXECUTION_PLAN.md` لخطة تطبيق Android التنفيذية.
9. `../content-research/README.md` قبل لمس محتوى الرحلات أو الصور أو الأسعار.

للمراجعة الحالية اقرأ تسليم الكتالوج والصور أعلاه، ومواصفة
`CATALOG_MEDIA_IMPLEMENTATION_SPEC_AR.md` و
`2026-10-05_MEDIA_TIER1_CHECKPOINT_REVIEW.md`؛ قبول checkpoint لا يغلق MEDIA.
`WEGO016_B_TIER1_REVIEW_HANDOFF.md` سجل تاريخي لمراجعة B، وليس نطاق العمل
الحالي ولا تصريحًا بالـcommit أو الـdeploy.

## العلامات

| العلامة | معناها |
|---|---|
| `[x]` | مكتمل بدليل مسجل |
| `[~]` | قيد التنفيذ داخل الـpacket الحالي |
| `[ ]` | لم يبدأ أو ما زال مفتوحًا |
| `[!]` | ينتظر قرارًا أو تفويضًا من المالك |
| `[s]` | متخطى بقصد وسبب موثق |

## الحالة التاريخية — 2026-09-29 (ليست الحالة الحية)

- `[x]` `WEGO-016-A`: مكتمل محليًا مع أدلة الجودة.
- `[x]` `WEGO-016-B`: مكتمل، Tier 1 review بلا blocking findings، ومرفوع في
  commit `ae09026` بتفويض المالك.
- `[x]` `WEGO-016-C` + `WEGO-016-D` الأساسيان: committed `0029492`، pushed إلى
  `origin/wego-016-safari-tours-baseline` بتفويض المالك.
  - Backend: 380 tests، 0 failures/errors/skips على PostgreSQL حقيقي.
  - Full web `pnpm run check`: lint + typecheck + 290 tests + 6 builds ✅.
  - `bash scripts/safari-tours-sharm-check.sh` ✅.
- `[x]` `WEGO-016-E`: **COMPLETE LOCALLY** في `8a5e643`. Fresh Compose
  نجح **13/13** ويشمل guest checkout كاملًا بلا login، root routing، عزل
  Safari ERP، وlogout حقيقي. مراجعة Tier 1 النهائية أعادت الاختبارات الحساسة
  وحكمت `READY — ZERO BLOCKING findings`. لا push/deploy بلا تفويض منفصل.
- `[~]` `WEGO-017-A`: **ACTIVE** لعزل Safari وSharm To Go وSharm Divers Club
  كـReleases تنفيذية مستقلة.
- `[!]` `WEGO-016-F`: implementation في `12259a2` لكنه مؤجل، والمالية
  تحتاج التحويل من booking-status totals إلى PAID-minus-REFUNDED ledger.
- `[!]` **Paymob credentials:** لم تُعتمد credentials sandbox/production بعد؛
  لا تستخدم production config ولا تعتبر mock دليل Paymob الحقيقي.

## ترتيب الأولويات التاريخي — سبتمبر (لا تنفذه بدل Board الحي)

1. اقرأ `AGENTS.md` و`docs/ENGINEERING_CONSTITUTION.md`.
2. اقرأ `docs/execution/WEGO_EXECUTION_BOARD.md` — الـpacket الوحيد المصرح به.
3. اقرأ هذا الملف و`SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`.
4. نفّذ `WEGO-017-A` فقط وفق Board، ولا تعدّل Safari F بالتوازي.
5. بعد Foundry وبقرار جديد: أصلح وراجع `WEGO-016-F` وفق payment ledger الحقيقي.
6. لا تبدأ G، ولا تفترض أن وصول credentials مجرد إضافة `.env`؛ التكامل
   الحقيقي يحتاج sandbox E2E واعتماد provider contract الحالي.

## محتوى الموقع القديم — سجل سبتمبر؛ الحالة الحالية في تسليم MEDIA

- `[x]` تم حفظ 13 صفحة، 30 رحلة، 34 booking choices، و437 media metadata
  records في `../content-research/legacy-wordpress-export.json`.
- `[x]` كل record موسوم كغير معتمد للنشر.
- `[x]` المالك اعتمد مصدر الكتالوج والأسعار والصور وسياسة الإلغاء لـC في
  2026-09-28، مع إبقاء Private Boat كـ`REQUEST_ONLY` وغير نشط.
- `[x]` seed التشغيلي المعتمد موجود؛ نقل V17 لمسار DML ما زال uncommitted
  ويحتاج إثبات Flyway/jOOQ قبل إغلاق E.

## ملفات تاريخية وليست مصدر حالة

الملفات التالية تحتفظ بسياق تنفيذ سابق، لكنها لا تمنح تفويضًا ولا تحدد
الـNEXT packet ولا تتغلب على ملف النضج الرسمي:

- `SAFARI_TOURS_GAPS_AND_ROADMAP.md`
- `WEGO012_PHASE2_AGENT_BRIEF.md`
- `WEGO012_PROGRESS.md`
- `PHASE4_AGENT_BRIEF.md`
- `2026-09-28_WEGO-016-EF-AGENT-BRIEF.md` — نُفذت أجزاء منه؛ ليس أمر البدء
  الحالي.

عند التعارض: Execution Board يحكم التفويض، وملف Production Maturity يحكم
حالة Safari Tours، وContent Research يحكم صلاحية المحتوى للنشر.
