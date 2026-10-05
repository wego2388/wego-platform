# Safari Tours Sharm — تسليم إصلاحات الأمان والدفع

> **التاريخ:** 3 أكتوبر 2026  
> **مكان العمل:** `/home/wego/wego-safari-hardening`  
> **الفرع:** `wego-016-safari-hardening` — التغييرات محلية وغير committed  
> **الحزمة:** `WEGO-016-SEC`  
> **الحالة:** `[x]` COMPLETE محليًا بعد Tier 1 مستقلة READY ومتابعة تعريف
> حزمة الإصدار READY؛ هذه ليست موافقة إطلاق production.

## ما اتعمل واتجرّب

- [x] توحيد البريد قبل فحص طوله يمنع تجاوز throttle بمسافات طويلة.
- [x] الطلب المرفوض لا يصل لفحص كلمة المرور أو استعلام المستخدم أو كتابة audit.
- [x] جلسة موظف صحيحة تستمر أثناء قفل سببه التخمين؛ تعطيل إداري يمنع الوصول.
- [x] إعادة تعيين موثوقة تغيّر كلمة المرور وتلغي الجلسات ذريًا؛ تمسح throttle
  بعد نجاح المعاملة فقط.
- [x] استرداد كامل مؤكد وموقّع يلغي الحجز الحي ويحرر سعته في نفس المعاملة.
- [x] استرداد جزئي يُقبل للمراجعة، ويحفظ الإيراد المقبوض حتى التسوية البشرية.
- [x] تكرار A ثم B ثم A لا يعيد إنشاء حدث المراجعة؛ هوية callback محفوظة في V28.
- [x] callback جزئي جديد بعد استرداد كامل يسجل anomaly ويحافظ على `REFUNDED`.
- [x] V27/V28 في تطبيق Safari فقط؛ اختبار العزل يثبت مجموعة migrations الدقيقة.
- [x] إصلاح نسخة Finance التي كانت توحي باستبعاد كل دفعات المراجعة من الإيراد.
- [x] تثبيت js-yaml على 4.3.2 وإصلاح تضارب auto-import في utils الموقع.
- [x] تشغيل فحص Foundry أثبت وجوب إضافة V27/V28 لتعريف الإصدار؛ تمت الإضافة
  وإعادة توليد الخطط بالسكريبت الرسمي، والتحقق الكامل نجح.

## أدلة القبول

| البوابة | النتيجة |
|---|---|
| `:platform:application:cleanTest :platform:application:check` | ناجح، 327 اختبارًا، 0 skipped/failures/errors، 4m13s |
| `:platform:apps:safari-tours-sharm:cleanTest :platform:apps:safari-tours-sharm:check` | ناجح، 209 اختبارات، 0 skipped/failures/errors، 1m35s |
| `web/pnpm run check` | ناجح: contract/lint/typecheck/tests/build لجميع workspaces |
| ERP Vitest في هذه المراجعة | 68/68 |
| Safari site Vitest في هذه المراجعة | 118/118 |
| المراجعة المستقلة | READY بصفر موانع؛ اختبارات auth/payment/isolation أُعيدت مستقلًا |
| `foundry/pnpm run validate` | ناجح: manifests/locks/plans/OpenAPI/repository YAML |
| `bash scripts/repository-check.sh` | ناجح |

نتائج Gradle الحالية في `platform/application/build/test-results/test/` و
`platform/apps/safari-tours-sharm/build/test-results/test/`. هذه ملفات بناء
يمكن إعادة توليدها. الأرقام السابقة 325/208 كانت لمراجعة أقدم.

محاولة تشغيل Gradle مع مراجعة أخرى بالتزامن فشلت على ملف نتائج binary مفقود.
أُعيدت البوابتان منفردتين بعد `cleanTest` ونجحتا بالكامل؛ لا نسجل المحاولة
المتداخلة كنجاح ولا كعيب تجاري في المشروع.

## حدود النضج الحالية

- [ ] إرسال تنبيه refund-review تلقائيًا: حدث outbox موجود بلا dispatcher.
  الإجراء الحالي هو مراجعة Finance وPaymob يدويًا حسب runbook؛ لا يوجد وعد
  بوصول بريد أو Telegram لهذا الحدث.
- [ ] ledger كامل لبنود الاسترداد الجزئي المتعددة: الوضع الحالي تحفظي
  `REVIEW_REQUIRED`، وليس حسابًا نهائيًا لصافي هذه الحالات.
- [ ] LOW follow-ups معتمدة بالمراجعة: method-pinned public matchers، مراجع حجز
  غير متسلسلة، وnonce CSP. ليست موانع لهذه الحزمة.
- [!] محاولات التخمين قد تؤخر login جديدًا خلال نافذة throttle. الاستعادة
  الموثوقة/انتظار Retry-After هو المسار؛ لا نسمح بفحص كلمة المرور أثناء الرفض.
- [!] Paymob sandbox، UAT، SMTP الحقيقي، VPS/DNS وحقوق أصول الإطلاق ما زالت
  بوابات تجارية وتشغيلية مستقلة. نجاح هذه الحزمة لا يعني إطلاق production.

## حزمة الإصدار والعزل

مجموعة Safari هي:
`1, 2, 3, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28`.
لا نضيف migrations منتجات أخرى لقاعدة Safari.

تعريف Foundry يؤثر على digest الكتالوج الكامل. إعادة توليد الخطط غيّرت في
Sharm To Go وSharm Divers Club هذا digest فقط؛ لا artifact أو module أو
migration أو هوية تجارية لهما تغيرت. كل ذلك داخل الـworktree المعزول.

## تنظيف الجهاز

- [x] `docker builder prune -af`: حذف 3.197 GB من كاش البناء فقط؛ يمكن إعادة
  توليده. الحاويات والـvolumes وقواعد البيانات لم تُحذف.
- [x] فحص المساحة: Docker على قسم `/`؛ `/home` منفصل وباقٍ فيه نحو 8.1 GB.
  لم تُحذف ملفات المشروع أو ملفات حسابات المتصفح أو شغل Agent آخر.

## ما بعد هذه الحزمة

الترتيب التفصيلي في `SAFARI_OPERATIONS_EXPANSION_PLAN_AR.md`:
`OPS2-A` أساس اللغة وأربع شاشات، ثم `OPS2-B` بقية الترجمة، ثم الحجز اليدوي،
مركز الطباعة، الموردون/السائقون/المركبات، التكاليف والتسويات، وUAT التشغيل.

لم يُنفذ commit أو push أو deploy أو تغيير DNS أو استخدام أسرار حية.
