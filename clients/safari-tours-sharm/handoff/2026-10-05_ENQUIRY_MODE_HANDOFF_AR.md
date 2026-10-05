# Safari Tours Sharm — تسليم وضع الطلب المؤقت عبر واتساب

> التاريخ: 5 أكتوبر 2026. الحزمة: `WEGO-016-ENQUIRY`.  
> الحالة: **COMPLETE محليًا** بعد البوابات النهائية ومراجعة Tier 1 مستقلة READY.  
> مكان العمل: `/home/wego/wego-safari-hardening`، فرع `wego-016-safari-hardening`.  
> هذا تسليم مرحلة محلية، وليس إطلاقًا تجاريًا أو رفعًا على VPS.

## النتيجة بالنسبة لمحمد

المشروع يستطيع استقبال طلب الرحلة عبر واتساب لحين تجهيز Paymob. العميل
يتصفح السعر والمعلومات المعتمدة، يختار اليوم والأعداد ويرسل الطلب للمكتب
بدون حساب. المكتب يؤكد السعة والسعر النهائي وترتيبات الدفع. الضغط لا يحجز
أماكن ولا ينشئ `NEW` أو `CONFIRMED` أو `PAID`، ولا يصدر إيصال تحصيل.

الموقع باللغات EN/AR/RU/IT يوضح ذلك، والداشبورد EN/AR يوضح وضع التشغيل
الفعلي. إعدادات إيقاف المبيعات الموجودة لا تستطيع فتح الدفع الإلكتروني
داخل تثبيت يعمل بوضع الاستفسار.

## العلامات والحالة

`[x]` منفذ ومختبر · `[~]` تحت القبول · `[ ]` لم ينفذ · `[!]` اعتماد/بيانات مطلوبة.

- [x] وضع صريح `ONLINE_PAYMENT` افتراضي، ووضع `ENQUIRY_ONLY` مؤقت.
- [x] الخادم يرفض إنشاء حجز/بدء دفع/استئناف دفع في وضع الاستفسار قبل الكتابة
  أو الاتصال بالمزود؛ إخفاء الزر ليس وسيلة الحماية الوحيدة.
- [x] تثبيت جديد بلا سجل دفع يبدأ بدون مفاتيح Paymob وبـadapter غير وهمي.
- [x] وضع غير معروف، إعداد جزئي، أو mock في وضع الاستفسار يمنع بدء الخادم.
- [x] أي سجل دفع، مهما كانت حالته، يمنع إزالة إعدادات Paymob الكاملة.
  فشل قراءة التاريخ يمنع البدء أيضًا؛ لا حذف للتاريخ لتجاوز الحماية.
- [x] الإعداد الكامل يحتفظ بالـadapter الحقيقي لاستقبال callbacks موقعة
  والمدفوعات/الاستردادات التاريخية حتى داخل وضع الاستفسار.
- [x] المزود المعطل لا يعترف بنجاح callback: يرجع 503 قابلًا لإعادة المحاولة.
- [x] العقد وواجهة المبيعات العامة والداشبورد يعرضون الوضع والحالة الفعلية.
- [x] الرسالة تستخدم اختيار الرحلة واليوم والفترة والأعداد وخيار التسعير
  الموجود في الكتالوج فقط؛ لا اسم عميل/هاتف/إيميل/فندق/token في الرابط.
- [x] `whatsapp_click` يعمل بموافقة التتبع؛ لا `purchase` أو `begin_checkout`
  نتيجة إرسال طلب. اختبار المتصفح اعترض الاتصالات الخارجية قبل خروجها.
- [x] عرض SSR باللغات الأربع، إخفاء نموذج الدفع، وnoindex لمسار checkout.
- [x] إصلاح ازدحام الهيدر الروسي على التابلت؛ القائمة الفعلية تعمل تحت
  1024px، مع فتح وإغلاق وتركيز لوحة مفاتيح وروابط مترجمة.
- [x] المراجعة المستقلة النهائية READY بصفر findings مفتوحة، وقبول الحزمة
  COMPLETE على Board. الحزمة النشطة التالية هي `WEGO-016-MEDIA`؛ تدقيقها
  ومواصفتها بدآ، دون endpoint رفع أو schema جديد بعد.
- [ ] تسجيل حجز المكتب والتحصيل اليدوي داخل ERP؛ حزمة تالية مستقلة.
- [ ] محرر محتوى الرحلة ورفع الصور من الجهاز وصور الفئات؛ لم ينفذ بعد.

## الاختبارات والأدلة

| البوابة | النتيجة الفعلية | دليل محلي |
|---|---|---|
| Backend checks | 223 Safari + 327 generic، صفر فشل/skipped | `/tmp/safari-enquiry-backend-final.log` وJUnit XML |
| Web كامل على المصدر النهائي | 655 اختبارًا، contracts/lint/typecheck و6 builds | `/tmp/safari-enquiry-web-final-strict.log` |
| Compose استفسارات فعلي | 5 خدمات healthy، بدون mock أو مفاتيح مزود | `/tmp/safari-enquiry-header-up.log` و`/tmp/safari-enquiry-final-erp-up.log` |
| المتصفح بوضع الاستفسار | 24/24؛ 4 لغات × 4 مقاسات، صفر overflow/axe في الصفحات المفحوصة | `/tmp/safari-enquiry-final-24.log` |
| قائمة التابلت | ضمن الـ24؛ 8 حالات لغة/مقاس، روابط وEscape/focus | نفس السجل النهائي؛ ودليل مستقل `/tmp/safari-enquiry-tablet-menu.log` |
| مسار الدفع السابق على المصدر النهائي | 29/29 ضد Compose جديد مستقل بـmock الاختبارات | `/tmp/safari-online-final-fresh-browser.log` |
| ERP الحالي | 122/122، EN/AR وأربع مقاسات، retries=0 | `/tmp/safari-enquiry-erp-acceptance.log` |
| Guard تاريخ دفع فعلي | backend المبني رفض البدء مع PENDING؛ تنظيف السجلين الصناعيين فقط | `/tmp/safari-enquiry-history-startup-final.log` |
| رفض بدء الإعداد الخاطئ | 4 عمليات فعلية خرجت 1 بالسبب الصحيح | `/tmp/safari-enquiry-*-startup.log` |
| Foundry/العقود/YAML | validation ناجح؛ Board وdiff checks ناجحة | `/tmp/safari-enquiry-handoff-validation.log` |

الـ24 اختبارًا تشمل tour card وmobile sheet واختيار يوم فعلي ثم checkout
وواجهة الموظف ورفض HTTP503 وقائمة التابلت. إعادة مجموعة
24 الأولى كشف فيها locator ملتبس بين زر اللغة وزر القائمة؛ صحح locator
دون تغيير assertions، ونجح اختبار القائمة. أصل مشكلة RU768 كان overflow
حقيقيًا، وعولج بالـbreakpoint؛ لم نخْفِ عرض الصفحة بـoverflow hidden.

اختبارات backend التاريخية تستخدم PostgreSQL وإعدادات صناعية كاملة وHMAC
حقيقيًا للتحقق من PAID/refund/replay وتحرير السعة؛ لا تمثل sandbox الحساب
التجاري. الاختبارات لم تتصل بواتساب أو Google أو Paymob الحقيقي.

إعادة lifecycle الدفع مرتين على نفس fixture ملأ السعة 10/10؛ الخادم رفض
المزيد بـ409 واختفى اليوم الممتلئ من التقويم كما ينبغي. المراجع أكد السبب
بقراءة API. لم نحذف الحجوزات/المدفوعات؛ أنشأنا fixture جديدًا منفصلًا، فنجح
الـ29 على الصورة النهائية. فشل DNS في SSR fixture الداشبورد صحح بـloopback
مع Host وcookie اللغة الفعليين، بدون تخفيف assertions.

المراجع المستقل `/root/enquiry_tier1_review` تحقق من الأدلة والصورة النهائية
وأصدر READY. ما زال البناء العادي للـbackend والدفع التجاري بوابتين منفصلتين.

بعد الاختبارات أغلقت حاويات مشروعي ONLINE الصناعيين فقط واحتفظت بالـvolumes
وسجلاتهما. نسخة الاستفسارات غير الوهمية ما زالت healthy على
`http://127.0.0.1:58087/en` والداشبورد على `http://staff.localhost:58087/login`؛
هما بيئة اختبار محلية، وليسا الدومين التجاري أو حسابات موظفي الإنتاج.

## ملفات التنفيذ

كل المسارات التالية من جذر المستودع؛ بقية تغييرات worktree تشمل مراحل
SEC/OPS2 السابقة. لا يعادل كامل `git diff` نطاق هذه الحزمة وحدها.

- `products/tours-operator/.../application/BookingMode.kt`،
  `CreateBookingService.kt`، `InitiatePaymentService.kt`، `PaymentRepository.kt`،
  `PaymobClient.kt`، `HandlePaymobWebhookService.kt`.
- `products/tours-operator/.../infrastructure/PaymobClientFactory.kt`،
  `DisabledPaymobClient.kt`، `JooqPaymentRepository.kt`،
  `ToursOperatorBeanConfiguration.kt`.
- `products/tours-operator/.../api/BookingController.kt`، `PaymentController.kt`،
  `PaymobWebhookController.kt`، `SalesControlController.kt`.
- `platform/apps/safari-tours-sharm/src/main/resources/application.yml`،
  `platform/contracts/openapi/v1/wego-api.yaml`،
  `web/packages/api-contract/src/generated.ts`.
- `web/apps/safari-tours-sharm-site/app/content/enquiry.ts`،
  `utils/enquiry.ts`، `composables/useSalesStatus.ts`،
  `composables/usePublicToursApi.ts`، `components/site/SiteBookingNotice.vue`،
  `components/tour/TourBookingCard.vue`، `components/SiteHeader.vue`،
  `layouts/default.vue`، `pages/booking/[slotId].vue`، `pages/tour/[slug].vue`.
- `web/apps/safari-tours-sharm-erp/app/utils/erpLocale.ts`،
  `composables/useToursApi.ts`، `layouts/default.vue`، `pages/sales.vue`.
- `.env.safari-tours-sharm.example`،
  `infrastructure/compose/safari-tours-sharm.compose.yaml`،
  `e2e/compose.safari-enquiry.yaml`، `.github/workflows/ci.yml`.
- الاختبارات: `EnquiryModeTest.kt`، `PaymobClientFactoryTest.kt`،
  `ToursOperatorEnquiryHttpTest.kt`، `ToursOperatorEnquiryHistoryTest.kt`،
  `web/apps/safari-tours-sharm-site/test/enquiry.spec.ts`،
  `e2e/tests/safari-enquiry.spec.ts`،
  `e2e/verify-safari-enquiry-history-startup.mjs`.
- Board وخرائط المالك وخطة التشغيل وVPS و
  `docs/operations/SAFARI_TOURS_SHARM_OPERATIONS.md` تحدّثت بالحالة والحدود.

## إعداد التشغيل والرجوع

في إعداد السيرفر الخاص لاحقًا: `TOURS_OPERATOR_BOOKING_MODE=ENQUIRY_ONLY`،
الـmock مغلق، وكل إعدادات Paymob غائبة فقط في تثبيت جديد خالٍ من المدفوعات.
لو يوجد سجل دفع، احتفظ بالإعدادات الحقيقية الكاملة. لا تنقل حسابات fixtures
أو passwords الافتراضية أو override الاختبارات إلى VPS.

الرجوع للدفع الإلكتروني: إعداد sandbox آمن كامل → تجربة الدفع والرفض
والـcallbacks والاسترداد والتكرار → اعتماد المالك → تغيير الوضع. لا تغيّر
تصنيف طلبات واتساب القديمة إلى حجوزات مدفوعة. عند الرجوع للاستفسارات، لا
تمسح أسرار callbacks أو سجل الدفع.

الـhealth script الحالي ينبه WARN مع flags مغلقة؛ هذه متوقعة في الاستفسارات.
راجع `bookingMode` قبل اعتبارها إيقافًا طارئًا. باقي تنبيهات disk/backup/TLS
تظل حقيقية ولا تعفى بسبب وضع التشغيل.

## الحدود المتبقية للإطلاق

- [!] تحذيران HIGH في web dependency audit: node-forge وbraces؛ بوابة الإصدار
  ما زالت مغلقة، بلا تخفيض threshold أو إعفاء.
- [!] البناء العادي لصورة backend تعطل على تنزيل Gradle بسبب TLS/DNS.
  صورة التشخيص بنت المصدر داخل صورة Gradle/JDK المثبتة من كاش artifacts
  فقط، ثم شغلت صورة JRE الإنتاج. هذا دليل runtime، وليس نجاح بناء إصدار
  عادي من الشبكة. صورتا الواجهة بنيتا بالطريقة العادية ونجحتا.
- [!] root filesystem بقي قريبًا من الامتلاء: آخر قياس **3.6 GiB متاح على
  `/` و8.3 GiB على `/home`**. حذف كاش Safari جديد قابل لإعادة البناء ونسخة
  site اختبار قديمة غير مستخدمة من هذه الجولة؛ يمكن إعادة بنائها من المصدر.
  لم تحذف DB volumes أو صور المالك أو ملفات/حاويات مشاريعه الأخرى. كاش apt
  حجمه 427 MiB يحتاج sudo؛ محاولة التنظيف غير التفاعلية رفضت ولم تمسه.
- [!] UAT المالك، صور بحقوق واضحة، حقائق قانونية/سلامة/pickup ناقصة، مسؤول
  الرد وساعات الخدمة وطريقة التحصيل المؤقتة تحتاج اعتمادًا.
- [!] VPS/الدومين وHTTPS وstaff origin وbackup خارج السيرفر وmonitoring
  واختبار restore على الهدف؛ النشر آخر خطوة ولم ينفذ.
- [ ] Lighthouse/Core Web Vitals على إصدار الإطلاق النهائي؛ الدليل هنا
  browser/axe/responsive، ولا يدّعي أرقام Lighthouse جديدة.

## التالي وفق الخطة

1. [x] قبول هذه الحزمة محليًا بعد المراجعة وتسجيل النتائج النهائية.
2. [~] `WEGO-016-MEDIA`: بدأ تدقيق ومواصفة محرر الكتالوج ورفع الصور؛ مصدر
   التنفيذ `CATALOG_MEDIA_IMPLEMENTATION_SPEC_AR.md`. الرفع نفسه لم ينفذ بعد.
3. `OPS2-C`: تسجيل حجز المكتب من أسعار وسعة Wego وبصلاحيات وسجل تدقيق؛
   تحديد دورة تأكيد/تحصيل صادقة قبل بناء receipt أو علامة PAID يدوية.
4. نماذج التشغيل والطباعة، ثم الموردون والسائقون والمركبات والتكاليف والتسويات.
5. UAT وبوابات إصدار مستقلة، ثم تفويض النشر على VPS.

حزمة واحدة فقط ACTIVE. لا commit/push/deploy/DNS أو تعديل حسابات خارجية
في هذه المرحلة، ولا تعديل أو restart لمشروعات العملاء الأخرى.
