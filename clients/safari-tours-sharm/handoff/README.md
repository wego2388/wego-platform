# Safari Tours Sharm — handoff index

## Agent start here

اقرأ بالترتيب قبل أي تعديل:

1. `AGENTS.md` و`docs/ENGINEERING_CONSTITUTION.md`.
2. `docs/execution/WEGO_EXECUTION_BOARD.md` لمعرفة الـpacket الوحيد المصرح به.
3. `SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md` للحالة والنواقص وبوابات الخروج.
4. `../content-research/README.md` قبل لمس محتوى الرحلات أو الصور أو الأسعار.

للمراجعة الحالية اقرأ أيضًا `WEGO016_B_TIER1_REVIEW_HANDOFF.md`؛ هو نطاق
المراجع المستقل وإثبات التنفيذ، وليس تصريحًا بالـcommit أو الـdeploy.

## العلامات

| العلامة | معناها |
|---|---|
| `[x]` | مكتمل بدليل مسجل |
| `[~]` | قيد التنفيذ داخل الـpacket الحالي |
| `[ ]` | لم يبدأ أو ما زال مفتوحًا |
| `[!]` | ينتظر قرارًا أو تفويضًا من المالك |
| `[s]` | متخطى بقصد وسبب موثق |

## الحالة الآن — 2026-09-28

- `[x]` `WEGO-016-A`: مكتمل محليًا مع أدلة الجودة.
- `[x]` `WEGO-016-B`: مكتمل، Tier 1 review بلا blocking findings، ومرفوع في
  commit `ae09026` بتفويض المالك.
- `[x]` `WEGO-016-C` + `WEGO-016-D`: مكتملان، committed `0029492`، pushed إلى
  `origin/wego-016-safari-tours-baseline` بتفويض المالك.
  - Backend: 380 tests، 0 failures/errors/skips على PostgreSQL حقيقي.
  - Full web `pnpm run check`: lint + typecheck + 290 tests + 6 builds ✅.
  - `bash scripts/safari-tours-sharm-check.sh` ✅.
- `[!]` **Paymob credentials:** المالك ينتظر رد من Paymob مصر. الكود جاهز،
  فقط يحتاج config values. لا تعدّل `PaymobHttpClient` قبل وصول credentials.
  راجع ملف `2026-09-28_WEGO-016-C-COMPLETE.md` قسم "ملحوظة للـAgent القادم".
- `[~]` `WEGO-016-E`: الـpacket التالي — Playwright E2E بـmock Paymob (بدون
  credentials حقيقية). ابدأ من هنا.
- `[ ]` `WEGO-016-F`: ERP finance screens + staff booking management — بعد E.

## ترتيب الأولويات للـAgent القادم

1. اقرأ `AGENTS.md` و`docs/ENGINEERING_CONSTITUTION.md`.
2. اقرأ `docs/execution/WEGO_EXECUTION_BOARD.md` — الـpacket الوحيد المصرح به.
3. اقرأ هذا الملف و`SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`.
4. ابدأ `WEGO-016-E`: Playwright E2E بـmock Paymob — booking → pay → webhook
   → confirm — بدون Paymob credentials حقيقية.
5. بعد E: `WEGO-016-F` — ERP finance + staff management.
6. لما تيجي credentials من المالك: أضف config values فقط في `.env`،
   لا تعيد كتابة `PaymobHttpClient`.

## محتوى الموقع القديم

- `[x]` تم حفظ 13 صفحة، 30 رحلة، 34 booking choices، و437 media metadata
  records في `../content-research/legacy-wordpress-export.json`.
- `[x]` كل record موسوم كغير معتمد للنشر.
- `[x]` المالك اعتمد مصدر الكتالوج والأسعار والصور وسياسة الإلغاء لـC في
  2026-09-28، مع إبقاء Private Boat كـ`REQUEST_ONLY` وغير نشط.
- `[~]` الاستيراد التشغيلي قيد التنفيذ داخل `WEGO-016-C` فقط.

## ملفات تاريخية وليست مصدر حالة

الملفات التالية تحتفظ بسياق تنفيذ سابق، لكنها لا تمنح تفويضًا ولا تحدد
الـNEXT packet ولا تتغلب على ملف النضج الرسمي:

- `SAFARI_TOURS_GAPS_AND_ROADMAP.md`
- `WEGO012_PHASE2_AGENT_BRIEF.md`
- `WEGO012_PROGRESS.md`
- `PHASE4_AGENT_BRIEF.md`

عند التعارض: Execution Board يحكم التفويض، وملف Production Maturity يحكم
حالة Safari Tours، وContent Research يحكم صلاحية المحتوى للنشر.
