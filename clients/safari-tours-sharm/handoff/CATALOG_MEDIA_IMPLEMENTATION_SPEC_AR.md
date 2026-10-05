# Safari Tours Sharm — تنفيذ محرر الكتالوج ورفع الصور

> 5 أكتوبر 2026 — `WEGO-016-MEDIA` هي الحزمة النشطة الوحيدة بعد قبول ENQUIRY.  
> Tier 1. مواصفة وتدقيق البداية أدناه تاريخيان؛ نفذ الرفع والمحرر والفئات
> في الكود لاحقًا، لكن قبول الحزمة وDB+media restore لم يكتمل. اقرأ
> `2026-10-05_CATALOG_MEDIA_SAFE_CHECKPOINT_AR.md` للحالة والأدلة الحالية.  
> صلاحية محمد تغطي التنفيذ المحلي؛ النشر والحسابات الخارجية لهما بوابة منفصلة.

## لماذا هذه المرحلة الآن؟

محمد يريد رفع صور الرحلات والخدمات والفئات بنفسه من الداشبورد، والمشروع
يملك كتالوجًا ومحتوى منشورًا وصلاحيات مراجعة بالفعل. نكمل الموجود بدل إنشاء
كتالوج جديد أو نسخ أسعار في الواجهة. تسجيل تحصيل المكتب يحتاج طريقة وحقائق
تجارية معتمدة؛ تجهيز الصور والمحتوى يمكن أن يتقدم مستقلًا عن هذا القرار.

## ما ثبت من الكود وقت تدقيق البداية — قبل التنفيذ

- [x] `TourContentController.kt` يملك قراءة الموظف للمحتوى، حفظ المسودة
  EN/AR/RU/IT، نشر/إلغاء نشر النص والحقائق، استبدال قائمة media واعتماد حقوقها.
- [x] القراءة تحتاج `tours-operator.tour:view`، التعديل `tour:manage`، والنشر
  واعتماد الحقوق `content:publish`. نعيد استخدام هذه الحدود؛ إظهار زر لا يمنح صلاحية.
- [x] `TourContentService.kt` يفصل DRAFT وPUBLISHED، ويمنع نشر revision
  تغيّر بعد المراجعة. ملف أو alt جديد يعيد media إلى DRAFT؛ العدد الأقصى 30.
- [x] `TourContent.kt` يملك path/width/height/cover/order وalt لأربع لغات
  وحقوق/مراجع مراجعة. اعتماده يحتاج English alt على الأقل.
- [x] `PublicTourContentQuery.kt` يعرض فقط رحلات نشطة وmedia APPROVED،
  مع fallback لغة معلن. هذا فلتر metadata، وليس ACL لخدمة bytes الرفع المستقبلية.
- [x] صفحة Tours في ERP إدارة قائمة/نشاط/مواعيد؛ لا محرر محتوى ولا file upload
  في `useToursApi.ts` أو الصفحات الحالية.
- [x] `CategoryTile.vue` يستخدم category enum وألوانًا وأيقونات؛ لا مخزن
  صور فئات موجود. تغيير صورة الفئة لا ينشئ فئة أو خدمة أو سعرًا جديدًا.
- [x] الموقع يستخدم `@nuxt/image` ويفترض media محلية في شجرة الموقع الحالية.
  لا يجوز افتراض أن IPX سيقرأ ملفًا جديدًا في volume خاص بالـbackend.
- [x] Compose backend read-only؛ لا media volume موجود. `backup.sh` و
  `restore-drill.sh` خاصان بقاعدة البيانات؛ لم يثبت استرجاع ملفات مرفوعة.

المصادر من جذر الريبو:

- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/api/TourContentController.kt`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/TourContentService.kt`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/application/PublicTourContentQuery.kt`
- `products/tours-operator/src/main/kotlin/com/wego/toursoperator/domain/TourContent.kt`
- `web/apps/safari-tours-sharm-erp/app/composables/useToursApi.ts`
- `web/apps/safari-tours-sharm-site/app/components/CategoryTile.vue`
- `web/apps/safari-tours-sharm-site/nuxt.config.ts`
- `infrastructure/compose/safari-tours-sharm.compose.yaml`
- `infrastructure/nginx/nginx.conf`
- `scripts/safari-ops/backup.sh` و`restore-drill.sh`.

## النواقص وترتيب الأهمية

| المستوى | الفجوة | العمل المطلوب |
|---|---|---|
| P0 | لا استقبال/تحقق/تخزين للملف | intake مصرح وحدود bytes/pixels/decode ومسارات immutable |
| P0 | metadata approval لا يحمي bytes مستقبلية | preview خاص بالموظف؛ public يتحقق من الارتباط والحقوق والنشاط |
| P1 | لا asset registry أو اتفاق واضح للـvariants | تصميم سجل وربط مضبوط بالرحلة/الفئة، وإعداد Flyway/jOOQ قبل التنفيذ إن لزم |
| P1 | لا محرر ERP | tabs محتوى بأربع لغات، مسودات وrevision ومعاينة، EN/AR وRTL |
| P1 | backup قاعدة البيانات فقط | manifest/checksum للملفات وتجربة restore متوافقة مع DB |
| P1 | خدمة الصور الحالية تفترض ملفات build ثابتة | مسار ديناميكي مضبوط واختبار optimizer/cache دون كشف DRAFT |
| P1 | لا صور فئات محفوظة | كيان غلاف للفئات الحالية فقط، حقوق وalt وتحديث الموقع |
| P2 | AVIF/WebP والترميز الأصلي يحتاجان دليل مكتبة/بيئة | لا وعد بتحويل غير مختبر ولا منصة DAM جديدة |

## قرارات التنفيذ الفنية وقت المواصفة — الحالة الحية في ملف التسليم

- [ ] المرحلة الأولى تقبل JPEG وPNG فقط من file picker واضح. حد أولي
  **10 MiB** للملف و**24 مليون pixel**؛ تثبت الحدود في العقد والواجهة والخادم
  والاختبارات. لا SVG/HTML/GIF أو archive أو ملف تنفيذ أو remote URL ingestion.
- [ ] قراءة magic/header واكتشاف format وأبعاد فعلية قبل allocation/decode؛
  رفض تالِف/truncated/animation وبُعد يتجاوز الميزانية. لا الثقة في filename
  أو Content-Type أو عرض/ارتفاع يرسلهما المتصفح.
- [ ] decode ثم تطبيق اتجاه الصورة عند وجوده وإعادة ترميز صالحة بدون
  EXIF/GPS/تعليقات غير لازمة؛ اختبار صور الهاتف المدورة. bytes
  الأصلية لا تنشر ولا تحفظ افتراضيًا؛ أصل صور محمد على جهازه يظل محفوظًا.
- [ ] مسارات UUID/اسم آمن ينشئه الخادم، مرتبطة بكيان معلوم في نفس التثبيت؛
  لا original filename أو customer identifiers ولا path traversal/symlinks.
- [ ] اشتقاقات bounded للغلاف/card/gallery، width/height حقيقيان وحجم معقول
  للموبايل. JPEG/PNG مبدئيًا؛ AVIF/WebP يضافان فقط بعد إثبات encoder آمن
  ومتوافق مع صورة التشغيل، دون تغيير تقني واسع بحجة صورة.
- [ ] كتابة staging ثم atomic move؛ فشل DB أو filesystem لا ينتج asset
  منشورًا ناقصًا. retries لها هوية طلب محدودة، وعمليات الرفع المتزامنة محدودة.
- [ ] metadata asset في Wego، والـbytes في volume Safari خاص دائم خارج
  image/container layer. الجزء الباقي من filesystem يظل read-only.
- [ ] حقوق asset لا تعتمد تلقائيًا بعد الرفع. معاينة خاصة تحتاج صلاحية؛
  public لا يقرأ DRAFT أو صورة رحلة غير نشطة بمجرد تخمين path.
- [ ] ربط managed asset بmedia يتأكد من مالك الرحلة والأبعاد المشتقة من
  الخادم. لا تسمح كتابة path/dimensions يدويًا بتجاوز سجل assets الجديد.
  حافظ على قراءة static approved media الموجودة؛ لا bulk rewrite للكتالوج.
- [ ] فحص حق النشر عبر كل طرق تقديم الصورة، بما فيها optimizer؛ لا mount
  للـprivate volume كله تحت مجلد Nuxt public أو alias عام في Nginx.
- [ ] قواعد cache/revocation واضحة ومختبرة؛ تغيير الحقوق أو فك الارتباط
  لا يبقي preview خاصًا قابلًا للوصول من cache عامة. لا تدّعي القدرة على
  محو نسخة سبق تنزيلها بصورة مشروعة من جهاز الزائر.
- [ ] حذف مرجعي آمن: unlink قبل delete، منع حذف asset ما زال منشورًا،
  tombstone/فترة استرجاع عند الحاجة. orphan cleanup تقرير أولًا؛ لا حذف شامل.

هذه حدود فنية مقترحة للحزمة، وليست حقائق أسعار أو خدمات أو حقوق تجارية.
إذا استدعى التصميم migration أو صلاحية جديدة، ثبّت القرار وراجعه داخل
نفس Tier 1 قبل التنفيذ، مع تعريف إصدار Safari المعزول الموافق.

## الواجهة المطلوبة وقت المواصفة — لا تعد هذه العلامات تقرير التنفيذ الحالي

- [ ] من قائمة الرحلات إلى «Content & photos / المحتوى والصور» لرحلة محددة.
- [ ] EN/AR لواجهة المكتب؛ تبويبات تحرير EN/AR/RU/IT تعرض اللغة وحالة
  المسودة/النشر بدقة. switching لا يمسح تعديلات غير محفوظة.
- [ ] النص والوصف وما يشمله/لا يشمله وما يجب معرفته ونقطة اللقاء والحقائق
  الحالية. لا إنشاء معلومات pickup/safety/languages/age/سعر غير معتمدة.
- [ ] اختيار الملف، حالة progress/retry/failure، ترتيب keyboard-friendly،
  cover/gallery وalt متعدد اللغات، حقوق ثم publish/revision confirmation.
- [ ] معاينة قبل النشر لا تغير الموقع العام. لا toast نجاح عند فشل غامض،
  ولا فقد التعديل عند response متأخر أو draft_changed.
- [ ] صور الفئات الخمس الحالية؛ «خدمة» ممثلة برحلة/transfer تستخدم كيانها
  الموجود. أي نوع خدمة جديد يحتاج scope خاصًا بدل تخزين بيانات مبهمة.
- [ ] responsive 360/768/1024/1440 وfocus/contrast/reduced-motion، ومقاسات
  الصور ثابتة لتقليل CLS وlazy loading لما تحت fold.

## خطوات التنفيذ وبوابات القبول

1. [x] تدقيق المصدر وتحديد الفجوات الحالية دون تغيير schema أو نشر صور.
2. [ ] تثبيت asset/storage/serving contract وبيانات الفئات، ومراجعة cache/
   optimizer/حقوق المسار والتزامن. تسجيل الملفات والمخاطر على Board.
3. [ ] تنفيذ port/application ثم infrastructure/API/OpenAPI؛ Flyway+jOOQ
   وتعريف release إذا لزمت جداول جديدة. لا أسرار أو تخزين metadata في JSON محلي
   يصير مصدر حقيقة ثانيًا.
4. [ ] ERP editor/upload ثم تقديم approved assets والصور المشتقة في الموقع.
5. [ ] media-inclusive backup/restore لقاعدة وملفات من snapshot متوافق،
   checksums وقياس زمن، مع رفض missing/corrupt assets بدل نجاح زائف.
6. [ ] اختبارات 401/403، النوع المزيف/الحجم/pixels/الملف التالف/traversal/
   فشل الكتابة وDB/التكرار/التزامن/الحقوق والـrevision/الحذف المرجعي، و
   guessed public/private/optimizer URLs. تحقق محتوى bytes لا مجرد status200.
7. [ ] بوابات backend/contracts/web وCompose مع filesystem دائم حقيقي،
   ثم E2E على EN/AR وأربع لغات public وفحص الشاشات والأداء.
8. [ ] fresh independent Tier 1 READY وصفر موانع قبل قبول الحزمة.

لا عمل تعديل دفع/تحصيل/حجز أو مزامنة OTA داخل هذه الحزمة. تحذيرا HIGH
والبناء العادي للـbackend وUAT/VPS ما زالوا بوابات إصدار منفصلة. تستمر
طلبات واتساب المقبولة محليًا، ولا نحولها إلى PAID أثناء تطوير الصور.
