# Safari Tours Sharm — تسليم مرحلة الكتالوج والصور

> 5 أكتوبر 2026 — تنفيذ محلي، لا نشر ولا إعلان Production-ready.  
> مكان العمل: `/home/wego/wego-safari-hardening`؛ الفرع `wego-016-safari-hardening`؛
> HEAD قبل هذا العمل `8948315`. التغييرات الحالية غير committed.  
> الحزمة الوحيدة النشطة: **WEGO-016-MEDIA / Tier 1**. لم تُغلق؛ النسخ
> الاحتياطي للصور مع قاعدة البيانات وبوابات الإصدار وقبول الحزمة ما زالت مطلوبة.

## 1. الخلاصة لمحمد

أصبح في الكود محرر فعلي لمحتوى الرحلات وصورها وصور الفئات من الداشبورد،
بالعربية والإنجليزية. تحرير المحتوى وalt يدعم EN/AR/RU/IT. ترفع الصورة
كمسودة خاصة؛ الموظف يشاهدها ثم يراجع حقوق استخدامها ويعتمد النسخة التي
شاهدها. الموقع يأخذ الصور المعتمدة من الخادم، وليس أسعارًا أو صورًا تجارية
مخترعة في الواجهة. لم ننقل صورك بالجملة ولم نحذف الأصول القديمة.

الأساس السابق للحجز والمالية والإشعارات موجود. الحجز المكتبي والورقيات
والموردون والسائقون والتكاليف والتسويات **ليست منفذة في هذه المرحلة**؛
لكل منها نطاق لاحق واضح في القسم 8. المشروع ليس جاهزًا للرفع بعد.

## 2. علامات التسليم

| العلامة | معناها |
|---|---|
| `[x]` | نفّذ واختبر بالدليل المذكور |
| `[~]` | جزء منفذ، لكن بوابة القبول أو التشغيل لم تكتمل |
| `[ ]` | عمل مفتوح؛ لا تعتبره موجودًا |
| `[!]` | بيانات/قرار مالك أو بوابة إصدار تحتاج معالجة مستقلة |

وجود الكود لا يساوي قبول الحزمة، وقبول اختبار محلي لا يساوي تفويض النشر.
Board هو المرجع الحي؛ ملفات سبتمبر ومحاضر الأرقام القديمة تاريخية عند التعارض.

## 3. ما اتنفذ في هذه المرحلة

- [x] إصلاح V29 بدون `INSERT OR IGNORE` الخاص بـSQLite: إنشاء السجل
  والاشتقاقات وصفوف الفئات الخمس، وseed عادي يعمل مع PostgreSQL وjOOQ.
  اختيار V29 داخل إصدار Safari فقط؛ لا إدخال جداول الصور في إصدار Divers.
- [x] استقبال JPEG/PNG فقط؛ 10 MiB و24 مليون pixel وحد أقصى 16384 للمحور.
  فحص magic/الأبعاد قبل decode، رفض الملفات المقطوعة والـPNG المتحرك
  والتضخم وفشل stream/CRC، واتجاهات EXIF الثمانية، ثم إعادة الترميز بدون GPS/EXIF.
- [x] أسماء UUID ينشئها الخادم، ملفات immutable خاصة، منع traversal/
  symlinks/overwrite، نشر ذري بدون clobber وfsync للملف والدليل.
  لا مسار أصلي من المستخدم ولا جلب صور من URL خارجي.
- [x] رفع الرحلة يحتاج **media:upload AND tour:manage**؛ المعاينة تحتاج
  tour:view؛ النشر/اعتماد الحقوق يحتاج content:publish. اختبار كل صلاحية
  منفردة أثبت الرفض، وليس مجرد فحص زر مخفي في الشاشة.
- [x] رفع الصورة لا يعتمد حقوقها. إعادة نفس requestId بنفس البيانات
  ترجع نفس المرجع؛ إعادة استخدامه ببيانات مختلفة ترفض. اختبار تزامن حقيقي.
- [x] حماية revision للرفع البديل وقائمة الصور والفئات. الأبعاد والملكية
  تؤخذ من سجل الخادم؛ لا ربط صورة رحلة بمالك آخر أو تزوير أبعادها.
- [x] فشل الكتابة الجزئية/rollback ينظف الملفات الجديدة غير المحفوظة.
  إذا حفظت قاعدة البيانات ثم ضاع الرد، نحتفظ بالملف؛ فشل إعادة القراءة
  لا يؤدي إلى حذف صورة قد تكون مرجعية. لا نعلن نجاحًا غامضًا في الواجهة.
- [x] محرر الرحلة `/tours/{id}/content`: مسودات الأربع لغات، النص والحقائق
  ومحطات الرحلة، حفظ/مراجعة/نشر/إلغاء نشر، alt/order/cover/unlink ورفع/استبدال.
  unknown ليس false؛ تبديل اللغة والتحديث لا يمسحان التعديلات بصمت.
- [x] شاشة `/categories`: DESERT/SEA/CULTURAL/SHOWS/TRANSFERS فقط؛ أربع
  لغات للـalt ومعاينة خاصة وحقوق وrevision. ليست شاشة إنشاء فئة تجارية جديدة.
- [x] المعاينة بتحميل bearer إلى blob محلي، لا token داخل URL؛ إلغاء
  object URLs ومعالجة الصور الصغيرة التي لا تملك اشتقاق w768.
- [x] الموقع يقرأ approved tour covers وapproved category covers فقط؛
  English fallback للـalt. تفاصيل الرحلة والـgallery/cards والفئات تستخدم
  الصور الديناميكية مع الأبعاد الحقيقية واشتقاقات أصغر فقط، دون upscale.
- [x] bytes العامة تتحقق كل مرة من المرجع والمالك والاعتماد ونشاط الرحلة؛
  guessed DRAFT/owner/path/variant يعيد 404. `no-store, private` حتى لا تستمر
  صلاحية صورة أُلغي اعتمادها في cache عامة. لا يمكن محو نسخة حملها زائر سابقًا.
- [x] حجب managed media عن IPX، بما فيها المسارات المشفرة ومصدر URL
  واشتقاق يحمل query. لا private volume داخل Nuxt public أو Nginx alias.
- [x] volume Safari خاص في Compose، runtime UID10001 وread-only خارج volume؛
  Nginx له حد 11 MiB للمultipart وحد معدل رفع ومسار managed منفصل.
- [x] إصلاح عيب ظهر في اختبار الرفع الحقيقي: الملف الأكبر من 10 MiB يرجع
  `413 file_too_large`، لا `401` ولا قطع اتصال. اختبار HTTP على منفذ حقيقي
  يغطي حد الملف وحد طلب multipart؛ لا تخفيف auth أو فتح `/error` للعامة.
- [x] تحديث OpenAPI والعقد TypeScript المولد وrelease plans، بدون أسرار.
- [x] جرد المصدر المحلي القديم قراءة فقط: 1770 ملفًا، 417 أصلًا غير thumbnail،
  و29/30 صورة غلاف كتالوج لها أصل محلي. التقرير يحفظ المطابقة والحجم والhash
  والمفقود، ولا يدعي أن metadata تثبت الحقوق.
- [x] إيقاف backup/restore القديمين عند وجود V29 حتى لو جدول الصور فارغ؛
  نسخة DB فقط ليست نسخة كاملة للمشروع.

## 4. الأدلة المثبتة

| البوابة | النتيجة الحالية | الدليل المحلي |
|---|---|---|
| backend النهائي: Safari check/test + generic check | 289 Safari / 327 generic؛ صفر فشل/خطأ/skip | `/tmp/safari-media-backend-final-full.log` وJUnit XML |
| MEDIA HTTP بPostgreSQL وHTTP server حقيقي | 15/15؛ الصلاحيات والتزامن والحقوق وفشل I/O/commit وحدود multipart | `ToursOperatorMediaHttpTest.kt`؛ `/tmp/safari-media-transport-test.log` |
| الموقع: lint/typecheck/tests/build | 132/132، build ناجح | `/tmp/safari-media-site-build.log` وscoped commands |
| ERP: lint/typecheck/unit | 217/217 في 22 suite | ERP tests والبوابة الشاملة |
| البوابة الشاملة للويب | 730/730 + lint/typecheck وستة builds | `/tmp/safari-media-web-full.log` |
| بوابة المشروع الإلزامية | PASS: backend عام، web، contracts، legacy، log privacy، Foundry وrepository | `/tmp/safari-media-quality-gate.log`؛ Safari check النهائي منفصل أعلاه |
| Playwright MEDIA على Compose فعلي، بلا API mocks | 12/12؛ worker واحد، retries=0، صفر fail/skip/flaky | `/tmp/safari-media-e2e-final.json`؛ `e2e/test-results` |
| انحدار واجهة ERP EN/AR | 122/122؛ retries=0، صفر fail/skip/flaky؛ **API fixtures للواجهة** | `/tmp/safari-erp-regression-final.json`؛ `/tmp/safari-erp-regression-final-artifacts` |
| بقاء الصورة بعد إعادة إنشاء backend ثم edge | PASS؛ bytes وSHA متطابقان مع DB قبل/بعد؛ نفس volume خاص وroot readonly | `/tmp/safari-media-persistence-final.log`؛ `e2e/verify-safari-media-persistence.mjs` |
| مراجعة Tier 1 مستقلة للـcheckpoint | لا مانع كود مُعاد إنتاجه باقٍ؛ **الحزمة كاملة NOT READY** | `2026-10-05_MEDIA_TIER1_CHECKPOINT_REVIEW.md`؛ reviewer قارن الصورة العامة أيضًا قبل/بعد recreation |
| Docker site وERP بالمسار العادي | PASS للمصدر الحالي | `/tmp/safari-media-ordinary-site-build.log`؛ `/tmp/safari-media-ordinary-erp-build.log` |
| Docker backend بالمسار العادي (source build) | **FAILED**: DNS تنزيل Gradle plugins داخل Docker؛ قيد بيئة وليس خطأ كود | `/tmp/safari-media-ordinary-backend-build.log` |
| Docker backend من jar محلي محقق (safari-backend-from-jar.Dockerfile) | **PASS**؛ SHA256 محقق داخل build step؛ runtime image مطابق — UID10001/drwx------/entrypoint | `/tmp/safari-media-bundle-gate.log` |
| Bundle DB+media (bundle-backup.sh) | **PASS**: 15 ملف، sha cross-check، Flyway 29، 26 جدول | `/tmp/safari-media-bundle-gate.log`؛ `scripts/safari-ops/bundle-backup.sh` |
| Bundle restore drill (bundle-restore-drill.sh) | **PASS**: checksum+pg_restore+row counts+media cross-check+sha256 originals؛ رفض bundle ناقص | `/tmp/safari-media-bundle-gate.log`؛ `scripts/safari-ops/bundle-restore-drill.sh` |
| Dependency audit HIGH | **2 HIGH بدون patch متاح**: node-forge@1.4.0 وbraces@3.0.3 — لا تصل لـruntime code؛ exemption موثق | `clients/safari-tours-sharm/handoff/DEPENDENCY_AUDIT_EXEMPTIONS.md` |
| Foundry/OpenAPI/YAML | PASS؛ أعيد بعد تحديث التسليم | `/tmp/safari-media-foundry-validation-final.log` |
| repository / shell syntax / whitespace | PASS | `/tmp/safari-media-repository-check.log`، `bash -n`، `git diff --check` |
| current storage على JRE Alpine الفعلي readonly UID10001 | PASS، write/read/immutable/fsync/cleanup | `/tmp/safari-media-runtime-proof-ywa2k2/RESULTS.md` |
| رفض backup/restore ناقص الصور | PASS كاختبار رفض، **ليس backup كاملًا**؛ restore يكتشف V29 من schema حتى لو metadata حذفته | `/tmp/safari-media-backup-refusal.log`؛ `/tmp/safari-media-restore-refusal.log` |

اختبار Alpine وحده استعمل tmpfs؛ فحص Compose المنفصل أثبت بقاء bytes بعد
استبدال الحاوية، لكنه لا يثبت مقاومة فقدان الطاقة ولا استرجاع disaster backup.
اختبارات MEDIA الـ12 رفعت JPEG من file picker، عرضت DRAFT بمعاينة bearer
خاصة، اعتمدت الحقوق، شاهدت الصورة في الأربع لغات، ثم تحققت من سحبها بعد
إلغاء الاعتماد؛ تشمل المقاسات 360/768/1024/1440 وkeyboard وaxe وrevision/
idempotency، ورفض spoof وoversize. أرقام ERP الـ122 تخص UI-fixture regression
ولا يصح جمعها وتسميتها 134 اختبارًا على API حقيقي.

المراجعة المستقلة كشفت خطر حذف bytes عند commit غامض، ومعاينة الصورة
الصغيرة وقصور تصنيف managed optimizer؛ أصلحت وأعيد اختبارها. قصور التصنيف
لم يكن دليلًا على تسريب bytes. ملاحظة غير مانعة: مسار خطأ Nitro للـIPX
المحجوب يرجع 404 بلا صورة، لكنه يعيد `Cache-Control: no-cache` بدل
`no-store` الذي وضعته middleware؛ هذه ليست صلاحية وصول للصورة.

المراجعة المستقلة تفصل قبول checkpoint عن قبول MEDIA الكاملة؛ مرجعها:
`2026-10-05_MEDIA_TIER1_CHECKPOINT_REVIEW.md`. لا تنسب أرقام ENQUIRY القديمة
إلى هذا المصدر. ملفات `/tmp` أدلة جلسة محلية وليست أرشيفًا دائمًا للإصدار؛
احفظ الأدلة المقبولة في مخزن الفريق المعتمد قبل تنظيف الجهاز أو release.

### التشغيل المحلي الذي اختبرناه

- المشروع التجريبي المعزول: `wego-safari-media-final`؛ خمس حاويات healthy.
- الموقع: `http://127.0.0.1:58088/`؛ الداشبورد:
  `http://staff.localhost:58088/`. حدد staff virtual host عند استعمال API.
- الوضع `ENQUIRY_ONLY` وPaymob mock معطل، ومعرّفات analytics فارغة؛ المحتوى
  المرفوع **صور اصطناعية للاختبار، وليس محتوى المالك النهائي**. لا تنشر هذه القاعدة.
- backend تشغيلي diagnostic من bootJar المبني محليًا + JRE pinned، لا بديل
  عن gate بناء Docker المعتاد. SHA256 للـjar داخل الحاوية:
  `04c18ee1765d0506fec02d8ef6f3639881430e92023d940cdea540d7ca41c1ad`.
- ملفات التشغيل: Compose الأساسي + `e2e/compose.safari-media.yaml`؛ tags:
  `wego-safari-media-{backend,site,erp}:local`. media volume:
  `wego-safari-media-final-media`؛ لا تحذفه ولا تستخدم `down -v`.
- preview ENQUIRY السابق 58087 وعمليات العملاء الآخرين لم تُمس. أُزيلت
  حاويات fixture MEDIA السابق فقط، مع الاحتفاظ بvolumes بياناته.

إعادة الفحوص من جذر worktree، واحدة تلو الأخرى للـGradle:

```bash
JAVA_HOME=/home/wego/.jdks/temurin-25.0.3+9 ./gradlew :platform:apps:safari-tours-sharm:check :platform:apps:safari-tours-sharm:test --rerun :platform:application:check --no-configuration-cache
bash scripts/safari-tours-sharm-check.sh
pnpm --dir web audit --prod --audit-level high
```

اختبار `e2e/tests/safari-media.spec.ts` opt-in فقط، ويتطلب fixture **جديدًا**
مع seed الاصطناعي والحراس الموثقة داخل الاختبار. fixture الحالي يحتوي نتائج
اختبار؛ لا تمسح الصور لإعادته، ولا توجهه إلى كتالوج المالك. persistence probe
له guard لمشروع الاختبار/DB/ports المحددة ويستبدل backend/edge فقط.

## 5. النواقص P0/P1/P2

| الأولوية | الحالة | العمل المتبقي وشرط إنهائه |
|---|---|---|
| P0 | `[x]` | bundle متوافق DB+media، manifest/checksums، restore جديد يرفض المفقود/التالف ودليل وقت الاسترجاع؛ **bundle-backup.sh + bundle-restore-drill.sh منفذان ومختبران** |
| P0 runtime | `[x]` | Compose فعلي وE2E صور وبقاء bytes بعد recreate مثبتة؛ حدود diagnostic backend موضحة أعلاه |
| P0 | `[~]` | fresh Tier 1 **قبول الحزمة كاملة**؛ مراجعة الإصلاحات وحدها لا تغلق MEDIA |
| P0 إصدار | `[~]` | dependency audit HIGH: ثغرتان بدون patch متاح من upstream؛ مخاطر موثقة في DEPENDENCY_AUDIT_EXEMPTIONS.md؛ **يلزم إقرار المالك قبل النشر** |
| P0 إصدار | `[x]` | ordinary backend image من jar محلي محقق عبر safari-backend-from-jar.Dockerfile؛ مطابق للـruntime image الحالي؛ source Dockerfile يفشل بسبب DNS isolation (قيد بيئة) |
| P1 | `[ ]` | استيراد صور قديمة مختارة بعد إثبات المطابقة والحقوق، كـDRAFT أولًا؛ لا bulk approval |
| P1 | `[!]` | بيانات pickup/languages/ages/safety/weather وغيرها غير الموجودة؛ لا اختراع commercial facts |
| P1 واجهة | `[x]` | responsive/keyboard/axe آلية باللغتين على 360/768/1024/1440؛ ليست اعتمادًا شاملًا لكل إعاقة |
| P1 قبول/أداء | `[ ]` | UAT محمد، reduced-motion وفحص بشري، Lighthouse/CWV وميزانية الأداء؛ لا أرقام LCP/INP/CLS مثبتة هنا |
| P1 ERP سابق | `[ ]` | كشف staff كامل للموعد الممتلئ/المحظور، وسجل جهات اتصال يتجاوز ملخص حتى 200 حجز؛ ليسا منفذين |
| P1 | `[ ]` | تقرير orphan/retention قابل للمراجعة؛ لا asset delete endpoint حاليًا ولا حذف مرجعي تلقائي |
| P2 | `[ ]` | encoder WebP/AVIF مدروس لاحقًا؛ أول نسخة رفع JPEG/PNG فقط، حتى لو المصدر القديم WebP |

لا توجد reviews وهمية ولا تغيير أسعار/سعة/سياسة إلغاء. `STANDARD` المعتمد:
≥48 ساعة رد كامل؛ 24–48 ساعة 50%؛ أقل من 24 ساعة بدون رد، ما لم يثبت تغيير معتمد.

## 6. ملفات التنفيذ الرئيسية

- migration/release: `platform/application/src/main/resources/db/migration/V29__tours_operator_asset_registry.sql`؛
  `platform/apps/safari-tours-sharm/build.gradle.kts`؛ `platform/application/build.gradle.kts`؛
  `foundry/catalog/release-profiles.json` و`clients/*/release.plan.json` المولدة.
- domain/application: `products/tours-operator/src/main/kotlin/com/wego/toursoperator/`
  → `domain/AssetDomain.kt`، `application/AssetPorts.kt`، `CategoryMediaRepository.kt`،
  `MediaUploadService.kt` وملفات `TourContentService/Repository/PublicTourContentQuery`.
- infrastructure/API: `JdkImageProcessor.kt`، `FilesystemAssetStorage.kt`،
  `JooqAssetRepository.kt`، `JooqTourContentRepository.kt`، `ToursOperatorBeanConfiguration.kt`،
  `MediaController.kt`، `MediaTransportExceptionHandler.kt`، `TourContentController.kt`،
  `TourController.kt`، `ToursOperatorDtos.kt`.
- tests: `platform/application/src/test/kotlin/com/wego/toursoperator/ToursOperatorMediaHttpTest.kt`؛
  `products/tours-operator/src/test/kotlin/com/wego/toursoperator/` image/storage/category tests؛
  Safari `ProductIsolationIntegrationTest.kt`.
- ERP: `web/apps/safari-tours-sharm-erp/app/` → content/categories pages،
  `useTourContentApi/Editor`، `useTourMediaApi`، `useCategoryMediaApi/CoverEditor`،
  preview/media/facts components وmessages؛ اختبارات `test/*Content*/*Media*/*category*/*Photo*`.
- site: `web/apps/safari-tours-sharm-site/app/` → `brand/SafeTourImage.vue`،
  `brand/TourMedia.vue`، cards/gallery/category pages، `useCategoryCovers.ts`،
  `utils/managedMedia.ts` وNitro middleware واختبارات الصور.
- contract: `platform/contracts/openapi/v1/wego-api.yaml`؛
  `web/packages/api-contract/src/{generated,index}.ts`.
- runtime: Safari Dockerfile/application config/Compose، `infrastructure/nginx/nginx.conf`؛
  `e2e/compose.safari-media.yaml` و`tests/safari-media.spec.ts` و
  `verify-safari-media-persistence.mjs`؛ `scripts/safari-ops/{backup,restore-drill}.sh`.

الـworktree يحتوي تغييرات SEC/ENQUIRY/ERP سابقة؛ `git diff --stat` الكلي
ليس حجم شغلي وحده. لا `git add .`، reset، restore، squash أو حذف untracked.

**ملخص diff الخاص بالمرحلة:** V29 وسجل صور/اشتقاقات + upload/private preview/
public bytes/revision + محررا الرحلة والفئات + صور الموقع الديناميكية +
العقود المولدة + runtime/private volume + منع backup ناقص + اختبارات/تسليم.
تغييرات release plans للعملاء الثلاثة مولدة من تعديل release profile المشترك؛
فحص العزل أثبت عدم إدخال V29 إلى Divers. ليست ربطًا تجاريًا بين العملاء.
لقطة diff للـtracked worktree كله عند التسليم: 106 ملفات، 3787 additions/
1594 deletions؛ تتضمن أعمالًا سابقة وتستثني الملفات الجديدة untracked.
لا تستخدم الرقم كقياس لنطاق MEDIA وحده أو كقائمة جاهزة للـcommit.

## 7. أول تعليمات للإيجنت المستلم

1. [ ] اقرأ AGENTS، Constitution، Board، review/collaboration protocol،
   هذا الملف، مواصفة MEDIA وجرد الأصول. تأكد من worktree والbranch والحالة.
2. [ ] اعمل read-only preflight للعمليات/الأقراص/containers/diff. لا توقف
   Resort OS أو عميل آخر أو preview سابق، ولا تعمل prune عامًا.
3. [ ] استكمل **MEDIA نفسها**: consistent bundle+restore ثم ordinary backend
   image/audit/UAT/performance الناقصة. لا تفتح OPS2 بالتوازي داخل نفس worktree.
   صمم consistency للـDB والملفات قبل التنفيذ؛ لا تزل حارس رفض DB-only.
   restore إلى بيئة جديدة يجب أن يفشل إذا غاب ملف مرجعي أو اختلف checksum،
   ثم يثبت ظهور الصور المعتمدة وبقاء المسودات خاصة وحقيقة الحجوزات/المدفوعات.
4. [ ] أعد backend/web/contracts/repository gates المطلوبة على المصدر النهائي؛
   وثق أي بوابة فاشلة كما هي. لا تتجاوز storage guard أو permissions/revision.
5. [ ] fresh reviewer ينفذ اختبارات حساسة ويصدر verdict؛ append الدليل إلى
   Board. لا تضع COMPLETE لمجرد نجاح unit tests أو قرب نهاية الجلسة.
6. [ ] بعد قبول MEDIA وتفويض المرحلة التالية: OPS2-C ثم D ثم E ثم F/G.
   commit/push/deploy/DNS/accounts/live credentials تحتاج تفويضًا صريحًا منفصلًا.

## 8. باقي المشروع بالترتيب — الموردون والمستندات فين؟

| المرحلة | الحالة | مخرجاتها |
|---|---|---|
| الأساس والحجز والدفع/المالية والإشعارات | `[x]` محليًا سابقًا | راجع Board وتسليم ENQUIRY؛ real Paymob ما زال بوابة مستقلة |
| ERP EN/AR الأساسي | `[x]` محليًا سابقًا | 11 صفحة مترجمة، مع حدود customers/availability موثقة |
| MEDIA | `[~]` ACTIVE | محرر ورفع وصور فئات؛ استكمل بوابات هذا الملف أولًا |
| OPS2-C حجز المكتب | `[ ]` | إنشاء حجز من السعة والسعر الحقيقيين؛ قناة المصدر؛ طريقة التحصيل المعتمدة، دون PAID مزيف |
| OPS2-D الورقيات | `[ ]` | voucher/booking form، receipt للحقيقة المالية فقط، run sheet، pickup/driver/supplier sheets، رقم نسخة ومن طبع |
| OPS2-E الموردون والتعيين | `[ ]` | suppliers/drivers/vehicles، بيانات ومهام اليوم، منع تعارض الموارد، صلاحيات وPII محدودة |
| OPS2-F التكاليف والتسويات | `[ ]` | تكلفة تشغيل/عمولة/مستحقات وربحية، اعتماد وتسوية وسجل تدقيق؛ لا خلط مال المورد بمدفوعات العميل |
| OPS2-G/UAT/إصدار | `[ ]` | restore كامل، monitoring/alerts، اختبارات تشغيل، UAT، rollback ونسخة مستقلة للـVPS |
| Android | `[ ]` | خطة `mobile/ANDROID_EXECUTION_PLAN.md` موجودة؛ التطبيق نفسه لم ينفذ |
| التسويق الخارجي | `[!]` | Google/Meta/Tripadvisor/OTA والمراجعات الحقيقية تحتاج حسابات واعتماد المالك؛ Wego يظل مصدر السعة والأسعار |

التفاصيل: `SAFARI_OPERATIONS_EXPANSION_PLAN_AR.md`، `../ROADMAP_AR.md`،
`../OWNER_PROJECT_MAP_AR.md`. Safari مشروع/عميل مستقل عن Sharm To Go وDivers وResort OS.

## 9. المطلوب من محمد — ليس قبل استكمال بوابات الكود

- [!] صور أصلية ومصدر/حقوق واضحة؛ اختيارات الأغلفة والـalt الواقعي لكل رحلة وفئة.
- [!] الحقائق الناقصة للرحلات وبيانات الشركة القانونية/الدعم واعتماد السياسات.
- [!] قواعد الموردين والتكلفة والعملة والعمولة والتسوية، وطريقة تحصيل المكتب.
- [!] Paymob sandbox ثم production عندما يتجهز؛ لا مفاتيح داخل Git/chat/logs.
- [!] مقصد off-server backups، صلاحية مفاتيح التشفير وخطة alert delivery.
- [!] UAT وتفويض النشر النهائي وHTTPS/secrets/بيانات VPS. IP المذكور
  `187.6.167.233` معلومة من المالك، وليس سيرفرًا اختبرناه أو نشرنا عليه.

## 10. حدود الأمان والتسليم

لا commit/push/deploy/DNS، لا استخدام حسابات أو أسرار live، لا حذف صور أو
volumes أصلية، لا تغيير booking/payment/auth خارج MEDIA. وضع واتساب المؤقت
ENQUIRY_ONLY لا يحجز سعة ولا يجعل استفسارًا حجزًا مؤكدًا، ولا يتطلب login
للعميل. أي تثبيت به payment history يحتفظ بالمفاتيح/adapter المطلوبين للتاريخ.

هذه نقطة تسليم للمرحلة المنفذة، لا ادعاء أن كل خطة المشروع أُنجزت.

تنظيف المساحة في هذه الجلسة اقتصر على cache مرحلتي build الموقع/ERP الجديدتين
المحددتين والمتحقق منهما (~1.2 GB)، دون global prune أو حذف images/volumes/
صور المالك. القياس الأخير: `/` نحو 2.2 GiB free (96%) و`/home` نحو
8.2 GiB free (91%)؛ مشكلة السعة لم تُحل جذريًا، ولا تبدأ builds كبيرة بلا preflight.
