# تسليم سفاري بعد النشر الفعلي — 8 أكتوبر 2026

النشر على VPS نجح بالفعل، وليس مجرد حزمة جاهزة. الموقع والداشبورد يعملان
بـHTTPS مستقل بجوار الخيمة. Codex هو منفذ النشر؛ لا تشغيل نشر موازٍ.
OPS2-G يبقى ACTIVE لاستكمال قبول المالك وتجربة التشغيل، دون تفعيل باكيت جديد.
علامة `[x]` تعني مثبتًا بالدليل، و`[ ]` تعني متبقيًا، لا وعدًا بالاكتمال.

> تحديث9 أكتوبر03:41 القاهرة: أُصلح عطل شهادة رابط الموظفين بعد أن أسقط
> نشر الخيمة الأحدث ربط سفاري من nginx. إصدار سفاري0004800 والخيمةdc266
> محفوظان، وتبدلت البوابة وحدها؛13 خدمة محمية لم تتغير. تفاصيل الحادث
> والمطلوب من منتج إصدار الخيمة لمنع التكرار في
> [تسليم إصلاح رابط الموظفين](2026-10-09_STAFF_TLS_RECOVERY_CLAUDE_HANDOFF_AR.md).

> **دليل وفحص تشغيل9 أكتوبر:** أُعيد إصدار PDF الأدمن16 صفحة، مع إصلاح
> محلي مثبت لقص جداول المستندات على الموبايل. لم يُنشر هذا الإصلاح الجديد بعد؛
> الحالة والبوابات في [تسليم الدليل والمستندات](2026-10-09_ADMIN_GUIDE_AND_DOCUMENTS_QA_HANDOFF_AR.md).
> أدلة8 أكتوبر أدناه تاريخية لتحديث الواجهات، لا baseline إصدار الخيمة الأحدث.

## الروابط والحسابات

- [x] الموقع: <https://safaritourssharm.com/ar>، والواجهات EN/AR/RU/IT تعمل.
- [x] الداشبورد: <https://staff.safaritourssharm.com/login>، عربي وإنجليزي.
- [x] حساب المالك `mohamedwagdy2323@gmail.com` نشط، بدور `platform-admin`
  و٣٢ صلاحية. المالك أدخل كلمة المرور بنفسه؛ تسجيل دخول ناجح مثبت.
- [x] الحساب الثاني `safaritourssharm@gmail.com` أنشأه المالك بكلمة مرور خاصة؛
  قائمة الموظفين الفعلية تؤكد ACTIVE/platform-admin. الحسابان نشطان، والأول محفوظ.
- [x] لا كلمة مرور افتراضية أو كلمة مرور في الشات/Git/ملفات env.

## الحالة الحالية — تحديث الموقع والداشبورد والتشغيل

نُشر الإصدار الجديد فعليًا نحو18:55UTC، واعتمد `current` بعد اختبارات الدومين
والداشبورد نحو19:00UTC في8 أكتوبر2026. **هذا القسم هو الحالة الحالية**؛
الأقسام التالية تحفظ تاريخ الإصدارات السابقة ولا تلغي ما اكتمل هنا.

- [x] الإصدار `/srv/safari-tours-sharm/releases/str-2026.10.08-0004800`.
  مصدر الموقع وERP هو `00048009d570f42348c3f0c2ffe22eff9c211d34`،
  محفوظ ومرفوع على الفرع الحالي فقط. backend بقي من198959e؛ لا migration
  أو تغيير auth/payment/booking mode أو DNS أو شهادة.
- [x] [CI37825115838](https://github.com/wego2388/wego-platform/actions/runs/37825115838)
  SUCCESS على المصدر نفسه: audit/contracts/web/gateway/infrastructure/backend/
  repository/mobile. شرطا dependency-review وdependency-submission SKIPPED
  وفق workflow push، وليس نجاحًا منسوبًا إليهما.
- [x] ERP375 unit tests، الموقع154، وSafari repository gate كامل PASS،
  بما فيه اختبارات backend الحقيقية وlint/typecheck/builds/Foundry.
  متصفح المرشح183PASS، delta9PASS، ومعرض صورتين حقيقيتين4PASS؛
  **صور الإصدار immutable النظيفة نفسها** اجتازت61PASS(6.7min).
- [x] مراجعة Tier-1 مستقلة `office_inventory_tier1_review`: READY، صفر blockers.
  فحوص المورد24 ساعة والعمر0 والتعافي من أخطاء الإدخال مثبتة، لا inspect فقط.
- [x] الداشبورد EN/AR بخمس مجموعات وصلاحيات العرض الحالية، بحث عن الصفحات،
  بطاقات دورة العمل، قائمة موبايل وطباعة نظيفة. فُتحت18 صفحة تنقل بحساب المالك
  على الإنتاج؛ العناوين صحيحة ولا overflow أو alert مرئي أثناء المراجعة.
  هذا فحص عرض، لا إنشاء معاملات مالية تجريبية على الإنتاج.
- [x] إضافة رحلة جديدة تبدأ غير نشطة، بسعر الفرد المعتمد باليورو، وتأكيد
  بيانات المالك. سعر الطفل الفارغ ليس صفرًا؛ REQUEST_ONLY لا يُفعّل.
- [x] تاريخ الحجز المكتبي يوم/شهر/سنة واضح، بما فيه تاريخ بعد60 يومًا.
  «إضافة موعد مؤكد» من نفس الحجز أو التقويم، بفترة وسعة حقيقية وتأكيد الموظف؛
  يُختار الموعد الجديد مباشرة. التقويم يعرض الممتلئ والموقوف ويدعم إيقاف/فتح
  الموعد. لا مواعيد تلقائية وهمية أو تجاوز للسعة.
- [x] عند ضياع رد إنشاء الحجز، تبقى نفس البيانات ومفتاح المحاولة في التبويب
  الحالي؛ إعادة المحاولة لا تخصم السعة مرتين. بعد refresh/انتهاء الجلسة يلزم
  البحث عن الحجز أولًا. لا وعد باستمرار المفتاح بعد إغلاق التبويب.
- [x] رفع الصورة ثم اعتمادها في مسار واحد بعد اختيار إقرار الحقوق، مع تحقق
  المسودة والمعاينة والنسخة المحفوظة وحالة APPROVED. عدم التأكد من الرد يمنع
  تكرار الرفع الأعمى. الصلاحيات لم تتوسع وDRAFT بقي افتراضي backend.
- [x] معرض الرحلة حتى30 صورة معتمدة، تُرفع واحدة واحدة، مع صور مصغّرة وعدّاد
  وأسهم وسحب للهاتف واتجاه العربية وEscape/عودة التركيز. لا batch upload حاليًا.
  اعتُمدت صورة المالك الموجودة لرحلةSuper Safari دون تغيير غلافها؛ لا اعتماد
  جماعي لملفات مجهولة الحقوق. صور الرحلات الأخرى يستكملها المالك.
- [x] عنوان الموقع يبدأ بعلامة سفاري، وخلفيات/لافتات الصفحات والأقسام متجاوبة
  من صور الهوية التعبيرية المعلنة والرسومات الزخرفية؛ ليست دليل تجربة حقيقية.
- [x] دورة تشغيل معزولة كاملة:70EUR تحصيل،25 تكلفة،45 ربح، المستندات الفعلية،
  المورد والسائق والمركبة، التسويات والإقفال بمراجعة شخصين، وإعادة المحاولة
  بلا تكرار مال. لا بيانات هذه الدورة أو الحسابات التجريبية في VPS.
- [x] إصلاح عطل فعلي علّق حفظ المورد عند إدخال الإخطار كرقم. أُدخلت بيانات
  الموردين الخمسةS01–S05 من ملف المالك عبر شاشة الإنتاج، ثم أُعيد تحميلها
  والتحقق: كلها **غير نشطة** وبدون تعيين رحلات أو أسعار مخترعة.24 ساعة محفوظة؛
  S02 بدون موعد محاسبة غير متوفر، وشروط الإلغاء غير المكتملة بقيت فارغة.
- [x] الموقع العام:34 page checks، أربع لغات×360/1440 وERP login EN/AR؛
  AXE0/page-errors0، canonical/hreflang/RTL/no-overflow، وطلب بعد120 يومًا.
 108 محتويات معتمدة مطابقة،27 رحلة نشطة، sitemap160/alternates800 وwww301.
  API private404/staff401، inactive/unknown404؛ writes0/tracking0 في فحص الجمهور.
- [x] تبدلت حاويتا الموقع وERP فقط؛ خمس خدمات سفاري healthy. backend/DB/edge
  وحاويات الخيمة التسع احتفظت بنفسID/image/start/restart:12 خدمة محمية لم تتغير.
  nginx-t ثم reload للـedge فقط، وأربع origins للخيمة200/TLS0. لا ادعاء نشر
  zero-downtime؛ الواجهتان single-instance وقد توجد فجوة قصيرة أثناء الاستبدال.
- [x] backup قبل التحديث `20261008T182322Z.bundle`:43tables/414rows/Flyway33؛
  وبعد الموردين `20261008T190355Z.bundle`:43tables/419rows/Flyway33.
  كلاهما مشفر وخارج VPS واستعادة network-none حقيقية،20 مفاتيح و20 ملف media
  بكل hashes سليمة. تقرير ما بعد التحديث `bundle-drill-20261008T191012Z.json`
  نُسخ للسيرفر mode0600. المفتاح الخاص لم يُرفع.
- [x] health الفعلي بعد التحديث PASS، disk VPS15%، TLS89 يومًا، النسخ/health
  timers فعالة. device-offsite يعمل فقط حين يكون جهاز المالك متاحًا.
- [x] تنظيف محلي محدد:10 صور مرشحين غير مستخدمة، صورة أدواتGradle غير مستخدمة،
  وقاعدتاE2E قديمتان باسمCodex غير مرتبطتين بحاويات. كاش بناء سفاري القديم فقط
  أُزيل بثلاثةIDs محددة(582.1+616.2+431.4MB)، لا prune عام أو حجوم الإنتاج.
  المؤقتات قابلة لإعادة البناء/seed، وسجل التجارب القديمة لا يعود إلا من أدلته.
  المصادر والأدلة وصور الإصدار الحالي/السابق والنسخ والمفاتيح محفوظة.
  النظام المحلي نحو2GB متاحة بعد التنظيف؛ لا إعادة توزيع قسم Docker.

| صورة الواجهة المنشورة | Image ID | output hash |
|---|---|---|
| ERP `str-2026.10.08-0004800` | `sha256:9be9d5fa5985cdbb99694ba14e1ddcccbade662a430c68b1a03513dcf707782a` | `783d69fa7f0a1377bc15f4553e5b18ba9372c8978ab75b081df82a89179885cf` |
| Site `str-2026.10.08-0004800` | `sha256:74a8b94b68fc447c600ee645986ac3686ad5409d80454d0cf65ef1946a020496` | `1ef74a4fe9ae504225812df102408feed16d93069b18f0c524ef91da1fc7d63b` |

لا candidate/dirty labels؛ non-root10001:10001/amd64. archiveSHA
`a17184e647a659fa5a348f3bce085a34083127ff0a275e4e021b3d722fb289fd`؛
manifestSHA `bd79154636abb2137c8c9390e845724bb2f6989bb7625c6bf5674a0be7a5b026`.
أي commit توثيق لاحق لا يغيّر مصدر الصور0004800 أو نسبتها إلىCI أعلاه.

### المتبقي الحقيقي — لا بيانات تخمينية

- [ ] **P1 أداء الموبايل:** Lighthouse13.5 بعد النشر: HomeEN75(AR57 أثناء
  قياس سابق)، Tours74، Tour53؛ Accessibility/Best-practices/SEO100 للموقع العام.
  LCP بالترتيبEN2.90s/3.19s/6.85s، وTBT706/695/1021ms.
  صفحة الدخولPerformance95/Accessibility100/Best-practices100، SEO54 بسبب
  عدم الفهرسة المقصود للموظفين. ليست مؤشرات field-INP أو اجتيازCWV حقيقي.
  غلاف الرحلةPNG768 نحو656KB أحد أسباب البطء. متابعة ضغط المشتقات يجب أن
  تحفظ فحص اعتماد الحقوق **عند كل طلب**؛ لا تمرير managed media عبرIPX cache
  يتجاوز سحب الاعتماد. لا تغيير backend/schema/صيغة الوسائط صامتًا لتحسين score.
  أول محاولتي القياس فشلتا لضيق/tmp وطولUnix socket، ومحاولةTour أخرى سجلت
  Chrome interstitial عابرًا؛ الفشل محفوظ، curl TLS صارم200 وإعادة القياس نجحا.
- [ ] صور المالك لباقي الرحلات والتصنيفات، وربط كل مورد بالرحلات والاتفاق الصحيح.
- [ ] تواريخ الرخص الكاملة للسائقين، مركبات وسعات حقيقية، أسعار التكاليف مع
  المستحق/العملة/أساس الحساب/تاريخ السريان، وسعر الصرف المعتمد. لا افتراضات مالية.
- [ ] Paymob/SMTP الحقيقيان واختبار جاهزية مستقل قبل تفعيلهما؛ التشغيل الحالي
  ENQUIRY_ONLY ولم يتغير. طلب واتساب لا ينشئ حجز ERP تلقائيًا.
- [ ] قبول المالك للدورة اليومية، مقصد نسخ دائم التشغيل وتنبيه خارجي؛ مخاطر
  F6 والمكتبات المعتمدة وغيرها أدناه باقية. مجلداnode-forge/braces غائبان عن
  ملفات.output في صور الواجهتين النهائية؛ لا ادعاء whole-image/SBOM scan نظيف.

الأدلة المحلية الجديدة داخل المجلد الخاص المذكور أدناه:
`CI_OWNER_SOURCE_PROOF.json`، `ERP_RELEASE_MANIFEST.json`،
`owner-maturity-all-gates.log`، `numeric-full-browser.log`،
`immutable-final-browser.log`، `site-erp-owner-upgrade.log`،
`live-owner-0004800-{browser,boundaries,content}.log`،
`owner-release-{current-switch,final-health,post-offsite-restore}.log`،
`lighthouse-owner-0004800-*.json` وصور`live-owner-0004800-*`.
`OWNER_UI_OBSERVATIONS.json` يوثق ملاحظاتCUA المنفذ دون بيانات اتصال، وليس
فحصًا آليًا مستقلًا. المراجع طابق الأدلة والوثائق وأعاد ACCEPT/صفر blockers.
آخر فحص`owner-release-protected-final-canonical.log` PASS بعد تصحيح افتراض
فحص خاطئ: جذر سفاري يعيد302 إلى/en طبيعيًا، والـcanonical200/TLS0؛ محاولة
الفحص الأولى الفاشلة محفوظة وليست محسوبة نجاحًا.12 خدمة محمية بقيت دون تغيير.
stage السيرفر الخاص `/home/resortos/safari-erp-update-20261008.V5ATyBvZ`
يحفظenv السابق والـmanifest وCI والدليلينbefore/acceptance؛ لا تنشر محتوياته.
للرجوع: استعد **SAFARI_ERP_IMAGE وSAFARI_SITE_IMAGE السابقين معًا** منenv
الخاص، `up --no-deps --no-build --wait web safari-site`، ثم nginx-t/edge reload
والاختبار، ثمcurrent القديمcd18068. لا restore للـDB أو إعادة بوابة الخيمة.

## تحديث سابق — طلب الحجز والهوية والمحتوى (مرجع تاريخي)

اكتمل التحديث النهائي واختبار الدومين الحقيقي في 8 أكتوبر2026 نحو03:56UTC.
هذه حالة الإصدار السابقcd18068؛ الحالة الحالية0004800 موثقة أعلى الملف.

- [x] الإصدار حينها: `/srv/safari-tours-sharm/releases/str-2026.10.08-cd18068`،
  وكان`current` يشير إليه. مصدر الموقع `cd180682d3c3b1c7f1b94504916c4cdc723dc6a8`.
- [x] [CI37723501226](https://github.com/wego2388/wego-platform/actions/runs/37723501226)
  SUCCESS على المصدر نفسه: backend/web/mobile/contracts/repository/audit/gateway؛
  checkout13، public-site11، launch5، ERP bilingual122، enquiry44 PASS.
  dependency-review وmain dependency-submission SKIPPED وفق شروط push، لا PASS.
- [x] صورة الموقع `safari-tours-sharm-site:str-2026.10.08-cd18068`، ID
  `sha256:d6d4d9cc51a24cddc182f50154b673b0062f39849cc8897d0fc606336645a062`؛
  output hash `6c8789d78b30f6e0b14dc50b63d0c68a906dd0862be71efa8b00fdb93733cb35`.
  لا candidate/dirty labels. صورة immutable نفسها اجتازت44 E2E محليًا أيضًا.
- [x] الزائر يختار تاريخًا مطلوبًا بلا حد60 يومًا، ووقتًا مفضلًا اختياريًا من
  أكواد الرحلة المعتمدة، وعدد الأشخاص/الوحدات، ثم يرسل طلب واتساب؛ بدون حساب.
  التاريخ ليس إتاحة مؤكدة، والطلب ليس hold أو PAID أو إنشاءً تلقائيًا لحجز ERP.
  إجمالي الكتالوج تقديري يحتاج تأكيد المكتب. الدفع الإلكتروني ما زال غير مفعّل.
- [x] لا تقويم60 يومًا أو استدعاء availability في وضع ENQUIRY_ONLY. مسار
  المواعيد والدفع المستقبلي محفوظ، واجتاز checkout الحقيقي في CI باختبار mock
  معزول؛ لا mock أو بيانات تجريبية في الإنتاج.
- [x] تفاصيل27 رحلة ×4 لغات منشورة. فحص جديد بعد تحديث الصورة يثبت108
  تطابقات كاملة للمحتوى العام، وservedLocale الصحيح دون fallback.
- [x] صور هوية محلية محسّنة WebP للهوم والصحراء والبحر، بأحجام480/960/1600،
  disclosure مرئي بأنها تعبيرية مولدة. ليست صورًا توثيقية لرحلات أو ملكية مركبات.
  صور أقسام ERP المعتمدة لها الأولوية؛ صور تفاصيل الرحلات بقيت للمالك.
  [المصادر والبرومبتات النهائية](../content-research/brand-art/README.md).
- [x] Home/About/FAQ/Terms/Privacy وFAQ JSON-LD تصف تأكيد المكتب في هذا
  الوضع؛ لا وعد «تأكيد في دقائق» أو Paymob فعّال أو مواعيد حية غير موجودة.
  سياسة48/24 ساعة وأسعار الكتالوج لم تتغير.
- [x] Google review link الصحيحBEBM وTripadvisor34123701؛ روابط مباشرة فقط،
  دون jscache/tacdn/widget scripts أو rating/reviews مختلقة.
- [x] الدليل الدقيق هو34 page checks على الدومين الحقيقي، أربع لغات
  على360/1440، Home/Tours/Detail/FAQ ودخول ERP EN/AR؛ AXE0/page-errors0،
  canonical/hreflang/RTL/no-overflow وطلب بعد120 يومًا PASS؛ API writes0/tracking0.
- [x] sitemap160/alternates800، robots وwww301 مع path/query؛ حدود private
  API404/staff401، inactive/unknown tours404، صورة brand WebP200.
- [x] خمس خدمات سفاري healthy. تغيرت حاوية الموقع وحدها، وedge عمل reload
  فقط بعد nginx-t. حاويات backend/ERP/DB/edge الأربع و**الخيمة التسع كلها**
  احتفظت بـID/image/start/restart؛ أربعة origins للخيمة200/TLS0.
  لا إعادة إنشاء بوابة الخيمة في هذا التحديث، ولا تغيير DNS/شهادة/schema.
- [x] backup قبل التحديث `20261008T033909Z.bundle` وبعده
  `20261008T035702Z.bundle`، كلاهما مشفر وخارج VPS واستعادة network-none
  حقيقية43tables/380rows/Flyway33/media0. التقرير الأخير
  `bundle-drill-20261008T035917Z.json` محفوظ محليًا ونُسخ للسيرفر mode0600؛
  أدلة التحديث الخاصة في `shared/evidence/site-cd18068` mode0700/0600.
- [x] دليل الأدمن المبسط [ADMIN_OPERATING_GUIDE_AR.md](../operations/ADMIN_OPERATING_GUIDE_AR.md)
  وPDF11 صفحة، راجع بصريًا، يشرح حدود التشغيل الفعلية لا أزرارًا غير موجودة.
- [x] Lighthouse13.5 mobile quiet: Home71، Tour82، وكل من accessibility/
  best-practices/SEO100؛ HomeLCP3.53s/CLS0.000781/TBT687ms،
  TourLCP2.38s/CLS0.000695/TBT508ms. أول قياس بالتوازي مع browser gate
 70/78 محفوظ؛ لا انتقاء صامت ولا ادعاء field-INP/CWV. أداء الموبايل متابعةP1.
- [x] إزالة3 صور candidate محلية ثبت أنها غير مستخدمة؛ محفوظة مصادرها
  وlogs وآثار الاختبارات. لا حذف صور الإصدار السابق/الحالي أو backups/keys/data،
  ولا global prune أو تنظيف شغل كلودي الآخر.

المتبقي وقت هذا الإصدار كان صور المالك وبيانات التشغيل، وتقويم ERP عرض/حجز
من موعد موجود فقط. **نموذج إنشاء/إيقاف المواعيد أُنجز ونُشر في0004800**،
وسُجل الموردون الخمسة؛ استكمال الحقائق التجارية وحده لا يُختلق ولاseed له.
Paymob/SMTP والتنبيهات الخارجية وalways-online offsite/UAT المالك متابعة مستقلة.
OPS2-G ACTIVE للقبول والمتبقي المحدد، لا إغلاق صامت أو توسع جديد.

## إصدار الإطلاق الأول — مرجع تاريخي لا الحالة الحالية

| البند | القيمة المثبتة |
|---|---|
| VPS | `187.6.167.233`، SSH alias الموثوق `resort-os-vps-next` |
| Safari branch | `wego-016-safari-hardening`، لا دمج main |
| مصدر بنية سفاري | `61b34cfbef7e2972b229251202de2d7a888cfc13` |
| مصدر التطبيقات داخل الصور | `198959efdd3754fffc9c357e555f25a014569b46`؛ platform/products/web مطابقة لـ61b34cf |
| إصدار الإطلاق الأول | `/srv/safari-tours-sharm/releases/str-2026.10.08-61b34cf`؛ السابق المحفوظ للرجوع، وليس current الآن |
| Compose project | `safari-tours-sharm-prod`، خمس خدمات healthy |
| بوابة سفاري الخاصة | `127.0.0.1:58080`؛ لا منافذ عامة للـDB/backend/site/ERP |
| مصدر عقد بوابة الخيمة | `b4317b796d98a3a0b6c72cb594a0f3abfe353524`، فرع `codex/shared-gateway-20261008` |
| تطبيق الخيمة الذي ظل يعمل | `4983a7b0d53bfbcf905775b8278ee994900f1be3`؛ لم تُستبدل صوره بهذا النشر |

عند الإطلاق الأول حملت الصور الثلاث tag `str-2026.10.08-198959e`؛ backend/ERP
كانت صورهما هذه عند تحديثcd18068؛ backend فقط ما زال بها الآن، وصورتا
الموقع وERP الحاليتان موثقتان في أول الملف:

| الصورة | Image ID المتحقق منه بعد load على VPS |
|---|---|
| backend | `sha256:d50534cfe26406c043063fcd6cda48a7939c77105cd4551573ba2e5c18d5934c` |
| site | `sha256:17f2db69e5cd78081ea9c270a60e256577203eb78987235659e9a9310a83ed08` |
| ERP | `sha256:781ee7cd8c03a90b73a40c139e2165345025018838a3127270db80d30dd3c172` |

أي commit توثيق لاحق لا يغيّر مصدر الصور أو الإصدار المثبت أعلاه.

## أدلة التنفيذ [x]

- [x] [CI سفاري37714374585](https://github.com/wego2388/wego-platform/actions/runs/37714374585)
  SUCCESS على61b34cf: backend/web/mobile/contracts/repository/dependency gates
  والبوابة الحقيقية وCompose/Playwright. الاستثناءان HIGH المعتمدان باقِيان موثقين.
- [x] [CI عقد الخيمة37716063910](https://github.com/wego2388/Resort-OS/actions/runs/37716063910)
  SUCCESS علىb4317b7. فشلا CI السابقان محفوظان ولا يُنسب لهما نجاح.
- [x] مراجعة Tier-1 مستقلة للعقد والإصلاحات: ACCEPT، صفر blockers؛
  guard7/verifier14 وfixture الحقيقي، وenquiry E2E24/24 على صور الإصدار.
- [x] HTTPS للأسماء apex/www/staff، شهادة مستقلة تنتهي
  `2027-01-06 01:27:59 GMT`، تحقق السلسلة والاسم دون تعطيل TLS.
- [x] HTTP→HTTPS وwww→apex301 مع حفظ المسار/query، staff noindex.
- [x] renewal dry-run لشهادة سفاري ناجح مع hook تجديد Docker الموجود؛
  timers الخيمة الثلاثة ظلت ACTIVE، ولا timer Certbot عام مكرر.
- [x] قاعدة مستقلة:22 migrations ناجحة، آخرها33، صفر فشل؛30 رحلة،27 نشطة،
  32 permission. لا جداول منتجات أخرى أو بيانات E2E أو قاعدة محلية منقولة.
  Intro Diving وPrivate Boat REQUEST_ONLY/inactive وCrocodile Show inactive
  وفق V25 المعتمد؛ لا إعادة تفعيل اصطناعية للوصول إلى توقع29 القديم.
- [x] ENQUIRY_ONLY وmock=false؛ الموقع يرسل طلب واتساب يحتاج تأكيد المكتب،
  وليس دفعًا ناجحًا أو حجز سعة. لا Paymob/SMTP/tracking IDs تجريبية في الإنتاج.
- [x] حدود API: موظفو/هوية الموقع العام404، staff API دون جلسة401؛
  تفاصيل رحلة غير موجودة أو Private Boat404، وصورة IPX حقيقيةWebP200.
- [x] فحص متصفح read-only26 صفحة: Home/Tours/Detail ×4 لغات ×360/1440،
  ودخول ERP EN/AR على360. Canonical/hreflang/RTL/no-overflow/AXE صفر
  مخالفات/page-errors صفر، دون mutations API أو تتبع تسويقي.
- [x] sitemap160 عنوانًا canonical مع5 alternates لكل عنوان، دون localhost/
  test/private؛ robots صحيح. query/referrer sentinel غائب من سجلي البوابة وedge.
- [x] الخيمة4 origins200 مع TLS صارم؛ بصمة شهادتها وملفا vhost لم يتغيروا.
  ثماني خدمات الخيمة غير nginx احتفظت بالـIDs/images/start/restarts نفسها.
  إعادة إنشاء nginx وحده سببت فشل اتصال عابرًا مرصودًا قبل التعافي؛ لا ادعاء
  انعدام انقطاع تمامًا. لا تعديل قاعدة/مال/QR/تطبيق الخيمة.
- [x] شبكة `safari-gateway` تضم nginx وedge سفاري فقط؛ DNS ديناميكي،
  mounts read-only. wrapper/guard/overlay/marker مثبتة root-owned على المضيف.

## النسخ والمراقبة

- [x] النسخ تشمل DB+media ومشفرة بالمفتاح العام؛ الخاص على جهاز المالك فقط
  في `/home/wego/.local/share/safari-backup-keys-20261008` mode0700، لم يُرفع للـVPS.
- [x] النسخ خارج السيرفر في `/home/wego/Backups/safari-tours-sharm` mode0700.
  استعادة معزولة حقيقية network-none، دون منافذ أو حجوم الإنتاج:
  أول نسخة105 صفوف؛ نسخة بعد إنشاء الأدمن107 صفوف،43 جدولًا وآخرFlyway33.
  بعد الحساب الثاني ونشر المحتوى: نسخة `20261008T030251Z.bundle` استُعيدت
  محليًا بنجاح،43 جدولًا/380 صفًا/Flyway33، وصفر مفاتيح media مفقودة.
  التقرير الأخير `bundle-drill-20261008T030401Z.json` نُسخ إلى VPS.
  صفر ملفات media صحيح لأن المالك لم يرفع صورًا بعد؛ ليس إثبات صور مفقودة.
- [x] daily encrypted backup ACTIVE، health كل5 دقائق ACTIVE، سحب خارجي
  كل ساعة عبر user timer ACTIVE. آخر health: كل البنود OK، disk16%، TLS89 يومًا.
- [ ] جهاز المالك المطفأ لا يسحب نسخًا؛ يلزم لاحقًا مقصد خارج السيرفر دائم التشغيل.
- [ ] اختيار receiver تنبيه خارجي؛ الحالي journal فقط، ليس تنبيه WhatsApp/email.
- [ ] جدولة restore أسبوعي آلي ونسخة آمنة منفصلة لمفتاح الاستعادة؛ الاختبار الحالي
  حقيقي لكنه يدوي، والمفتاح المحلي غير محمي بعبارة مرور لتشغيل غير تفاعلي.

## الجودة والمتبقي الحقيقي

- [x] Lighthouse13.5.0 mobile Home بتاريخ02:35UTC: Accessibility100،
  Best practices100، SEO100، Performance67؛ LCP2.85s، CLS0.000884، TBT694ms.
  هذا قياس مختبري واحد، لا شهادة INP/CWV من مستخدمين حقيقيين.
- [x] صفحة الرحلة canonical نفسها: Performance82 وAccessibility/Best-practices/
  SEO100، LCP2.28s/CLS0.000260/TBT518ms، دون runtime errors/warnings.
- [ ] تحسين أداء الموبايل/التحميل والتنفيذ بعد الإطلاق وفق هذا القياس؛
  لا نغيّر صور الإصدار أثناء تسليم الإدارة دون دورة تحقق جديدة.
- [x] نشر108 وثائق للرحلات الـ27 النشطة، EN/AR/RU/IT، عبر importer
  WEGO-016-CNT المعتمد وواجهات ERP revision/publish؛ فحص saved documents
  يثبت تطابقًا كاملًا مع المصادر المعتمدة. الحسابات/الأسعار/المواعيد والصور
  والرحلات غير النشطة لم تتغير. كلمة المرور أدخلها المالك محليًا خارج الشات.
- [ ] صور تفاصيل الرحلات الحقيقية وحقوقها يرفعها المالك عبر ERP؛ ليس مطلوبًا
  منه كتابة التفاصيل من البداية. صور الهوية المولدة منفصلة عن وسائط الرحلات.
- [x] تحديث UX الطلب والصور التعبيرية نُشر بعد CI خاص بالمصدرcd18068؛
  أدلته في قسم آخر تحديث حي أعلاه، وليست منسوبة لـCI وصورة الإطلاق القديمة.
- [ ] استكمال ربط الموردين الخمسة المسجلين والسائقين والمركبات والمواعيد
  والتكاليف وسعر الصرف الحقيقي؛ السجلات غير المكتملة لم تُختلق.
- [ ] Paymob وSMTP الحقيقيان، ثم gate مستقل قبل تفعيل الدفع أو رسائل البريد.
- [ ] verified reviews/analytics/GBP/OTA بمعلومات وصلاحيات المالك الحقيقية.
- [ ] F6 المقبول: حد CHARGE5000EGP لكل تعديل، لا حد تراكمي يومي لكل طرف.
- [ ] متابعة node-forge/bracesHIGH المعتمدين: directories غائبة عن صور runtime
  المفحوصة؛ لا ادعاء SBOM/whole-image vulnerability scan نظيف.
- [ ] helper `alpine:3.20` للنسخ tag-pinned لا digest-pinned، كما في runbook.
- [ ] PyJWT2.15.0 أُصلح واختُبر في مصدر الخيمة فقط؛ صور تطبيق الخيمة الحالية
  لم تُرقَّ بهذا النشر. يلزم تحديث تطبيق مستقل مع backup/قبول وكيل الخيمة.

## خريطة الأدلة والرجوع — للوكيل القادم

الأدلة الخاصة في `/home/wego/safari-release-20261008.85V6vQ`؛ لا تنشر محتويات
env أو قاعدة أو private keys. أهم السجلات:
`vps-start.log`، `vps-gateway-recreate.log`، `vps-certificate-renewal-drill.log`،
`vps-admin-health-final.log`، `vps-final-gates.log`، `live-browser-smoke.log`،
`production-admin-offsite-restore.log`، `lighthouse-mobile-home.json`.
آخر أدلة التحديث: `CI_SOURCE_PROOF.json` و`ci-cd18068-complete.log`،
`immutable-site-e2e.log`، `site-only-upgrade.log`، `live-postlaunch-smoke.log`،
`live-final-public-content.log`، `live-final-boundaries-drained.log`،
`live-final-current-health.log`، `post-site-upgrade-offsite-restore.log`،
`lighthouse-final-quiet-mobile-{home,tour}.json` وصور `live-final-*`.
حزمة السيرفر الجديدة تحفظCORE_RELEASE_MANIFEST التاريخي، وRELEASE_MANIFEST
المحدث وCI_SOURCE_PROOF؛ تطبيقات backend/ERP من198959e وليست relabel باسمcd18068.
فحص Lighthouse على مسار خاطئ `/en/tours/...` عاد404؛ ليس دليل نجاح صفحة الرحلة.

Runbook الحاكم: [CONTAINER_GATEWAY_RUNBOOK_AR.md](../deployment/CONTAINER_GATEWAY_RUNBOOK_AR.md).
لا تشغيل hostNginx أو certbotstandalone، ولا إعادة first-install/seed/bootstrap
فوق الإدارة الحالية. كل عملية مستقبلية للخيمة تستخدم `/usr/local/sbin/resort-compose`؛
وكيل الخيمة يضم عقد البوابة في فرع إنتاجه قبل أي نشر، بلا main merge تلقائي.
رجوع تحديث الموقع فقط: أعد SAFARI_SITE_IMAGE السابق من النسخة الخاصة للـenv،
ثم Compose المشروع الصحيح `up -d --no-deps --no-build --wait safari-site`،
وبعد nginx-t اعمل edge reload لتحديث عنوان upstream، ثم تحقق قبل current switch.
لا edge recreate أو بوابة الخيمة لتغيير الموقع وحده؛ حافظ على مثبتات البوابة.
لا DB restore لمجرد رجوع كود،
ولا down-v أو prune عام أو حذف مفتاح/نسخ أو تشغيل شغل كلودي الآخر.

التسليم يثبت إطلاقًا مستقرًا للاستفسارات، لا اكتمال كل البيانات التجارية أو
دفع إلكتروني جاهز. قبول تجربة المالك التشغيلية متبقٍ بوضوح، لذلك لا إغلاق
صامت لـOPS2-G أو بدء توسعات جديدة.
