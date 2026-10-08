# سفاري — إصلاحات ما قبل النشر ودليلها، 8 أكتوبر 2026

الحالة: تجهيز واختبارات محلية؛ ليست إثبات نشر. OPS2-G هو الباكيت الوحيد ACTIVE.
محمد أجاز تعديل البوابة اللازمة للمشروعين، نشر سفاري، حفظ ورفع التغييرات
المحددة على فروعها فقط، استثناءَي الاعتماديات، وبريد الشهادة ومكان النسخ.
Codex هو منفذ النشر الوحيد. لا تعديل بيانات/تطبيق الخيمة ولا دمج main.

## إصلاحات مثبتة

- [x] تكييف البوابة مع Nginx الحقيقي داخل Docker بدل افتراض host Nginx.
- [x] ربط البوابة وedge فقط بشبكة مستقلة؛ لا مشاركة قواعد أو خدمات التطبيقات.
- [x] DNS ديناميكي: بداية البوابة دون سفاري، ثم تغيير عنوان edge دون reload.
- [x] ملفات إعدادات خارج release الخيمة، mounted read-only عبر directory.
- [x] سجلات سفاري لا تسجل query/referrer؛ أخطاء الطلبات معطلة لكل server
  لأن حتى مستوى crit يضيف الرابط الكامل. سجل access الآمن يحتفظ بالحالة
  والمسار ومعرف الطلب، وأخطاء إقلاع Nginx تبقى في stderr.
- [x] wrapper إجباري لنشر الخيمة + guard للمجلد والشبكة والمثبتات الدقيقة؛
  يرفض حذف override أو إدخال ملفات/مشروع بديل، down، وconfig يعرض الأسرار.
- [x] تصحيح تقييم استثناءات node-forge/braces: node_modules الخاصة بالبناء
  لا تُشحن كاملة؛ فحص الصورتين لم يجد directories للمكتبتين.
- [x] بناء صور الواجهة من .output حديثة مع materialization للـsymlinks
  وفحص hash داخل الصورة. Debian/glibc مثبت بالـdigest ليتوافق مع sharp
  الخاص ببناء الجهاز، لا نسخ binaries glibc إلى Alpine/musl.
- [x] اختبار فعلي لتحويل صورة إلى WebP داخل صورة الموقع عبر sharp.
- [x] فحص صحة Node بدل wget للواجهات، صالح للصورتين Alpine/Debian.
- [x] إصلاح SMTP health: الإعداد الفارغ أنشأ mail indicator يحاول localhost
  وأبقى الباك إند DOWN رغم تعطيل الإشعارات. عُزل البريد الاختياري عن readiness؛
  بعد الإصلاح وصلت الخدمات الخمس في نسخة الاختبار إلى healthy.
- [x] تحسين health لتدقيق hostname/chain للشهادة، لا تاريخ صلاحيتها فقط؛
  timeout محدود. ENQUIRY_ONLY المعتمد ليس تحذير توقف مبيعات كاذبًا.

## المصدر والبناء

Application source: `198959efdd3754fffc9c357e555f25a014569b46`، CI
[37685772250](https://github.com/wego2388/wego-platform/actions/runs/37685772250)
SUCCESS. لا diff في platform/products/web أثناء البناء. تغييرات البنية
والتوثيق هنا مستقلة وتحتاج commit/CI جديد قبل النشر؛ لا تُنسب إلى CI القديم.

| العنصر | SHA-256 / دليل |
|---|---|
| bootJar | `51af6683ae3ad70bb50fdca2eadf12343fb80329f401f30f8cdeb5ad58da9230` |
| site output بعد materialization | `f7b6b6d65878af55b9cc18cc2d43945de45a9b0728693de6d5d213b2fab3fb03` |
| ERP output بعد materialization | `cf13cfc16be35cb506fd8ce3d8366dcd6cbcc35c22f595db7016a079fff7c1c6` |
| Node runtime amd64 | `51b1100cc2a83d370c6a60952e3f2989c8a43159d0e38586e090f3b3326efefd` |
| build staging | `/home/wego/safari-release-20261008.85V6vQ` — محلي، لا يحتوي أسرار VPS |

## بوابات نفذت فعليًا

- [x] bootJar وSafari isolated check: exit 0؛ Gradle أعاد استخدام المهام
  غير المتغيرة. ليست إعادة تشغيل forced لكل اختبارات Java.
- [x] lint/typecheck للموقع والـERP؛ production build حديث لكليهما: exit 0.
- [x] اختبارات الموقع 132/132، ERP 329/329: exit 0.
- [x] Foundry/OpenAPI/YAML + repository invariants وdiff check: exit 0.
- [x] اختبار gateway: غياب سفاري، بقاء الخيمة، تبديل IP بلا reload، spoofed
  forwarding headers، فشل buffering حرج دون تسرب sentinel، syntax للأنواع الثلاثة.
- [x] guard اختبارات 7؛ resolved Compose تجريبي من الملفات الفعلية اجتاز.
- [x] إعادة E2E 24/24 على صور الإصدار بعد إصلاح SMTP: exit 0 في 1.8 دقيقة،
  أربع لغات وأربعة مقاسات، رفض APIs القديمة وحماية إعداد ERP. المحاولة الأولى
  فشلت ECONNREFUSED قبل جاهزية edge؛ محفوظة ولا تُحسب نجاحًا.
- [x] endpoint الصور الفعلي `/_ipx/w_96/brand/logo.webp`: 200 image/webp.
- [x] اختبارات verifier الخيمة 14/14، إضافة لاختبارات guard السبعة. Marker
  مستقل يمنع fallback للنشر القديم إذا فُقد overlay بعد التركيب.
- [x] التسجيل الأخير دون تغييرات شبكة:24/24 في1.8 دقيقة/exit0، بنفس
  الصور/assertions/retries. التسجيل السابق23/24 محفوظ؛ trace يثبت
  ERR_NETWORK_CHANGED لملفات hydration بالتزامن مع تغيير Dockerbridges المحلية.
- [x] مراجعة Tier-1 `shared_gateway_tier1_review`: ACCEPT/zero blocking
  للـcommit/push. تحقق مستقل من trace28networkerrors ومن تطابق مصدر الاختبار
  وretries0 ونجاح24 النهائي و14verifier؛ النشر مشروط بـCI وبوابات السيرفر.
- [ ] CI على commit الإصلاحات؛ نقل الصور؛ strict TLS وHTTPS للمشروعين؛ backup/restore.

## نسخ احتياطي وإدارة

بموافقة محمد أُنشئ مفتاح النسخ على جهازه في directory 0700؛ لا توجد نسخة
خاصة على VPS. حفظ النسخ خارج VPS في `/home/wego/Backups/safari-tours-sharm`.
المفتاح المحلي غير محمي بعبارة مرور لكي يعمل سحب/اختبار النسخ الآلي؛ يجب
حماية حساب الجهاز والقرص، وحفظ نسخة recovery للمفتاح في مكان آمن منفصل.
لا تتضمن هذه الوثيقة المفتاح أو أي كلمة مرور. لم يُفعّل جدول نسخ بعد.

أول أدمن `mohamedwagdy2323@gmail.com` يحتاج إدخال كلمة المرور تفاعليًا
بواسطة محمد. الاسم Wagdio لا يوجد له حقل في bootstrap الحالي؛ لا migration
متسللة ولا اسم وهمي في هوية المستخدم. لا SMTP/Paymob/analytics حية حتى إعدادها.

## مكان الدليل المحلي الفعلي

`/home/wego/safari-release-20261008.85V6vQ/`: `gateway-test.log`،
`resort-guard-tests.log` (7+12 قبل marker)، `resort-release-verifier.log`
(14 بعد marker)، `resort-resolved-config-guard.log`، `enquiry-e2e.log` (23/24
مع أخطاء شبكة)، `e2e-network-change-failure/` (trace/screenshots)،
`enquiry-e2e-final.log` (مكتمل:24passed/exit0). ملفات الدليل المحلي لا تُشحن
كبيانات إنتاج؛ يُحفظ ملخصها/hash في سجل النشر. agent-check checkout الخيمة
الصغير أظهر غياب .venv/node_modules، لا فشل app؛ targetedverifier استخدم venv
الموجودة وفحص14حالة فعلًا. لا ادعاء تشغيل fullResortsuite في هذا التغيير.

SHA256 النهائي للسجلات: enquiry final
`9adecd79dbc52f582578c83e3a1fd25834b610ec1b511b284c9f1a8e6598d688`؛
gateway `e594f54724e7508610531e049b956d850c3b8e65b33bc3a94ae7c624bb0f1b26`؛
verifier14 `9dba01232013cdce61d4d9070a28005597cfcbbaaa3277fea41ab0f5a93f37d9`.
