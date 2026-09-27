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

## الحالة الآن — 2026-09-27

- `[x]` `WEGO-016-A`: مكتمل محليًا مع أدلة الجودة.
- `[~]` `WEGO-016-B`: التنفيذ والبوابات مكتملة؛ ينتظر Tier 1 review مستقل
  من fresh context قبل الإغلاق أو الـcommit.
- `[x]` Backend: 332 tests، 0 failures/errors/skips على PostgreSQL حقيقي.
- `[x]` Full web `pnpm run check`: lint + typecheck + 414 tests + 6 builds.
- `[x]` Unified `scripts/safari-tours-sharm-check.sh` بكل مراحله.
- `[!]` Commit/push: لا يحدثان بلا تفويض صريح من المالك.
- `[ ]` `WEGO-016-C`: لا يبدأ قبل Tier 1 closure لـB.

## محتوى الموقع القديم

- `[x]` تم حفظ 13 صفحة، 30 رحلة، 34 booking choices، و437 media metadata
  records في `../content-research/legacy-wordpress-export.json`.
- `[x]` كل record موسوم كغير معتمد للنشر.
- `[ ]` اعتماد الأسعار والسياسات والمحتوى وحقوق الصور من المالك.
- `[ ]` الاستيراد التشغيلي يتم فقط داخل `WEGO-016-C` بعد إغلاق A ثم B.

## ملفات تاريخية وليست مصدر حالة

الملفات التالية تحتفظ بسياق تنفيذ سابق، لكنها لا تمنح تفويضًا ولا تحدد
الـNEXT packet ولا تتغلب على ملف النضج الرسمي:

- `SAFARI_TOURS_GAPS_AND_ROADMAP.md`
- `WEGO012_PHASE2_AGENT_BRIEF.md`
- `WEGO012_PROGRESS.md`
- `PHASE4_AGENT_BRIEF.md`

عند التعارض: Execution Board يحكم التفويض، وملف Production Maturity يحكم
حالة Safari Tours، وContent Research يحكم صلاحية المحتوى للنشر.
