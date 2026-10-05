# Safari Tours Sharm — جرد المصدر المحلي القديم

> 5 أكتوبر 2026 — جرد للقراءة فقط ضمن `WEGO-016-MEDIA`.
> مصدر صور ومراجعة محتوى، **ليس اعتماد حقوق ولا استيرادًا إلى قاعدة التشغيل**.

## الخلاصة لمحمد ولأي إيجنت

المجلد الذي أرسله محمد مفيد جدًا: أصول أغلفة **29 من 30 رحلة** الموجودة
في الكتالوج المعتمد متاحة محليًا، دون تنزيل من الإنترنت. رحلة نقل المطار
ليس لها `imageUrl` في الكتالوج المعتمد؛ لا نخترع صورة أو نربطها تلقائيًا.

لكن المجلد يحتوي ملفات WordPress وملفات الصور، **وليس تصديرًا كاملًا لمحتوى
قاعدة البيانات**. مصدر نصوص الصفحات والرحلات يظل snapshot الريبو المحفوظ؛
لا ننفذ PHP القديم ولا نعيد استخدام نظام حجزه أو إعداداته أو أسراره.

## المصادر والحدود

- المصدر المحلي: `/media/wego/Projects/safari-tours-sharm-extracted`.
- قاعدة مسارات الصور في الجدول أدناه:
  `/media/wego/Projects/safari-tours-sharm-extracted/wp-content/uploads/`.
- ربط الرحلة بالصورة ثبت من `imageUrl` في
  `clients/safari-tours-sharm/content-research/approved-catalog.json`، وليس
  بتخمين معنى اسم الصورة. عند وجود thumbnail اخترنا ملف العائلة الأكبر
  المطابق بعد إزالة لاحقة `-WIDTHxHEIGHT`؛ تحققنا من وجوده فعليًا.
- مرجع المحتوى المحفوظ:
  `clients/safari-tours-sharm/content-research/legacy-wordpress-export.json`،
  ملتقط 27 سبتمبر 2026؛ يحتوي 30 رحلة و13 صفحة و437 سجل media.
- الأبعاد في جدول الأغلفة قرئت من headers JPEG/PNG/WebP المحلي، والأحجام
  من bytes الملف الفعلية، وSHA-256 محسوب على كامل الملف. هذا **ليس** إثبات
  سلامة decode أو اتجاه EXIF أو حقوق الصورة؛ uploader يظل مسؤولًا عن ذلك.
- لم تفتح ملفات `wp-config.php` أو `.private/config.json`، ولم تنفذ ملفات
  WordPress أو shell القديمة. لم تنسخ أي صورة أو قاعدة بيانات أو أسرار إلى
  الريبو، ولم تغير أو تحذف الأصل أو تشغل خدمة قديمة.

## نتائج الجرد

| البند | النتيجة |
|---|---:|
| ملفات uploads كلها | 1,770 |
| صور داخل مسارات سنة/شهر | 1,759 |
| صور تستبعدها قاعدة thumbnail `-WIDTHxHEIGHT` | 1,342 |
| مرشحات أصل/نسخة كاملة بعد الاستبعاد | 417 |
| حجم هذه المرشحات الفعلي | 76,424,414 bytes؛ نحو 72.9 MiB |
| ملفات مختلفة byte-for-byte بين المرشحات | 400 |
| مجموعات ازدواج SHA-256 | 17 مجموعة؛ كل مجموعة ملفان |
| حجم uploads على القرص (`du -sh`) | 177 MiB تقريبًا |
| JPEG بين المرشحات | 389؛ 386 jpg و3 jpeg |
| WebP / PNG / AVIF بين المرشحات | 21 / 5 / 2 |
| أغلفة approved catalog لها أصل محلي مطابق | 29 |
| أغلفة المطابقة JPEG/PNG / WebP | 24 / 5 |
| عدد الرحلات في approved catalog | 30؛ 27 ACTIVE و3 INACTIVE |

«مرشحات الأصل» اصطلاح جرد لا إثبات ملف كاميرا أصلي: بعض الأسماء تشمل
`scaled` أو تعديلات أو حجمًا داخل الاسم دون لاحقة thumbnail النهائية.
استبعدنا CSS وElementor screenshots وملفات plugins/themes/icons من صور
الرحلات. الـ417 ليست 417 صورة مختارة للنشر.

من 437 سجل media في snapshot، **401** له `media_details.file` موجود فعليًا
في المصدر المحلي، **29** له مسار ملف غير موجود، و**7** بلا حقل file صالح
للربط المحلي. أغلب الملفات ذات المسارات المفقودة من `2026/03` و`2026/06`؛
المصدر المحلي المصور يمتد هنا إلى `2026/01`. لا ندعي أن النسخة المحلية أحدث
أو أكمل من snapshot الشبكة. جميع سجلات snapshot الـ437 بلا `alt_text` مفيد
وموسومة `rightsStatus: UNVERIFIED` و`migrationStatus: NOT_SELECTED`.

## خريطة الأغلفة المثبتة

الحالة أدناه من ملف الكتالوج المعتمد، **وليست قراءة لحالة قاعدة بيانات
التشغيل**. لا تغير حالة رحلة أثناء استيراد صورتها. الرحلات غير النشطة هي
`intro-diving` و`private-boat` و`crocodile-show`؛ القارب الخاص يظل request-only
ولا يحصل على سعر أو تفعيل بسبب توفر صورة.

| Tour slug | الحالة | الأصل النسبي إلى uploads | الأبعاد | bytes | SHA-256 |
|---|---|---|---|---:|---|
| `super-safari-adventure` | ACTIVE | `2025/11/IMG-20240419-WA0015.jpg` | 640×480 | 47,224 | `95bbc4924212b05a70f898e2713af8fe894e89a532df14f3fdc8c91601097484` |
| `sunset-quad-bike` | ACTIVE | `2026/01/quad-biking-tour-in-sharm-el-sheikh-desert-2436382.webp` | 1500×1001 | 199,622 | `9ed6cf07f4ca1171325aeb13c23e9e690956a32d4382a9e06111a5285118cfca` |
| `double-buggy-camel-ride` | ACTIVE | `2025/11/IMG-20240419-WA0073.jpg` | 1280×854 | 116,045 | `975f709d9fac6bcc27193dff99f68a5e757ff31c518270c3d1808ffe474a31a4` |
| `bedouin-dinner-camel-ride` | ACTIVE | `2025/11/IMG-20240419-WA0027.jpg` | 2048×1536 | 361,313 | `8485364b738e84e7a842ec6ab82feb6f418de7cefc0df3d484951d04bcba5b93` |
| `dahab-colored-canyon` | ACTIVE | `2025/11/FB_IMG_1731410704834.jpg` | 828×1125 | 150,489 | `b035b6033f3863d905ac7ce3bed8c548436f095b5d663af435af4bc3ccdccc42` |
| `camel-horse-ride` | ACTIVE | `2026/01/83.jpg` | 669×446 | 103,519 | `8433b4d0e36564ae16890deea5da3725222d55b6f1f7ff2ebd68a722b250a539` |
| `ras-mohamed-white-island-boat` | ACTIVE | `2025/11/IMG-20240419-WA0156.jpg` | 1368×770 | 128,116 | `dba226a8048e2ed078742849ff2df68f387ce12a02a3f910554803c2f793332f` |
| `evening-cruise` | ACTIVE | `2025/11/IMG-20240419-WA0166.jpg` | 720×554 | 28,044 | `bb80ae1dd14e7ee0c30d5a78686ef55cad59fe03175172b623797c435094b8c2` |
| `glass-bottom-boat` | ACTIVE | `2025/11/IMG-20240419-WA0069.jpg` | 856×1280 | 111,137 | `762b9ee0bd779d14e3dc8dac0e5e5f66dd34a646c146a9216534699329995890` |
| `diving-course` | ACTIVE | `2026/01/PPB1-TaeMHJYU-1024x576-1.jpg` | 1024×576 | 55,868 | `b210a52fa58959ec823e3a625b804c9833d3ce6df27b0e025a3885c76153e7f9` |
| `intro-diving` | INACTIVE | `2025/11/IMG-20240419-WA0165.jpg` | 1600×1200 | 199,678 | `c812fd221aef79bd02466d91d4ce2cd31e757c013cee6df38df73065ee8df2ee` |
| `private-boat` | INACTIVE | `2025/11/IMG-20240419-WA0190.jpg` | 1600×1200 | 160,670 | `cf8005d2eb768b64629a2115316d4fdefa24a3002539eda387046a562e998834` |
| `sinai-dream-sailboat` | ACTIVE | `2026/01/pirates-boat-cruise.jpg` | 600×333 | 43,239 | `e76246db4c2ba7f7eaf7a784ab40a51b043c1f4c69d2ee19baf29a3032751d94` |
| `ras-mohamed-by-bus` | ACTIVE | `2025/11/IMG-20240419-WA01671.jpg` | 1200×1600 | 127,813 | `91347d631cc0cf57b544e22c36e4e0a0cad9529c3e01c8b98929083af218505e` |
| `banana-boat` | ACTIVE | `2025/11/IMG-20240419-WA0130.jpg` | 1000×664 | 87,855 | `33144f2f509b1dd1ed7709d22b8f376c0acedc388dff542a5fae3cfa8fe5e8c7` |
| `parasailing-adventure` | ACTIVE | `2025/11/IMG-20240419-WA0102.jpg` | 3696×2448 | 895,857 | `0c0bef76df86872ece0ec106dec67c91c430219ff294c61c66df73ea4cf30dc0` |
| `speed-boat-adventure` | ACTIVE | `2026/01/sharm-el-sheikh-2-hours-private-speed-boat-to-tiran-island-740580.jpg` | 680×452 | 59,043 | `d1d91330622da3ebce9e00c0b13481becff15a806cb580956d6f863c0aacde57` |
| `submarine` | ACTIVE | `2026/01/sharm-el-sheikh-seascope-subma17373741644.webp` | 1000×609 | 35,476 | `60cbb14230930cacb5a82c373c2e786febbc631b8904233bd246d14fff9fe0a1` |
| `tiran-island-boat-trip` | ACTIVE | `2026/01/2000x2000-0-70-8da5c8a9db726301dd34107cb7334fee.jpg` | 2000×1314 | 436,452 | `21e1dc5810fa469644eff94e403f8ec58797da0942d3a4357eaf26db90168be5` |
| `tube-boat` | ACTIVE | `2026/01/31ca45b4-8e30-4d89-9f1d-fe4d6612e8fb_sea-fun-glass-bottom-boat-tube-ride-parasailing-sharm-el-sheikh.png` | 720×480 | 417,502 | `67cf8b5835518d34fbd29e65a3639cec69a3ddfd130dfb5066424afe40ebb40e` |
| `cairo-by-bus` | ACTIVE | `2026/01/61.jpg` | 669×446 | 65,491 | `f44e0723e8d6d8d4f89ed855463ff023b93be59c98ab7011b6982942d48882c2` |
| `cairo-by-plane` | ACTIVE | `2026/01/Cairo-Trip-by-Plane-Sharm-El-Sheikh-e1669435434944.webp` | 1200×675 | 159,586 | `f74be2cba947d5db64e095ff9885fde1f111494b749bc4c5a0ec9d7e01bd885d` |
| `mount-sinai-st-catherine` | ACTIVE | `2026/01/FB_IMG_1754255597368.jpg` | 720×960 | 91,931 | `343fd1779ccabfce0722b37674b43737a645a27e0c634c4e250397bbeb795eb9` |
| `luxor-by-plane` | ACTIVE | `2026/01/Excursions-from-Sharm-J.webp` | 1600×1600 | 234,816 | `469de9dbf9c55b424a34530578668776070e2e0508f3bb7324f1e9d1fcd98bed` |
| `st-catherine-dahab` | ACTIVE | `2026/01/saint-catherine-s-monastery.jpg` | 2000×1333 | 890,014 | `4954f6b1bd725911b76e809931b5d7a239b2218731d5e96210d460491443771f` |
| `turkish-bath` | ACTIVE | `2026/01/42.jpg` | 669×446 | 83,356 | `7382bc8a2768bb92674a10f38e72f11922a0e1c01ea9977701721a75ac7ee52e` |
| `dolphin-show` | ACTIVE | `2026/01/2016130396531.jpg` | 1000×667 | 121,409 | `be8bb1a4f0dd706f67a5338ceca43978f9f24949c0a02b82aaa415e139bfd87f` |
| `swimming-with-dolphins` | ACTIVE | `2026/01/swim-with-dolphins-activity-sharm.webp` | 960×637 | 29,146 | `e688186e531e11e1fc8ba4ebd64b884f57eb567533bd46fbcc4ca7b441cbcc91` |
| `crocodile-show` | INACTIVE | `2026/01/crocodile-snake-sharm-trip-day-show-34.jpg` | 800×600 | 508,960 | `3f72f6f520a4ccda2b43f938164c1e56c7852b597a16570803f0fd929d622220` |
| `sharm-airport-transfer` | ACTIVE | لا غلاف في approved catalog | — | — | — |

## ازدواج الصور والجودة

- 14 صورة باسم `FB_IMG_*` موجودة مرتين byte-for-byte في `2025/11` و`2026/01`.
  لا حاجة إلى رفع النسختين ولا حذف الأصل من قرص محمد.
- `2025/11/IMG-20240419-WA0167.jpg` و`IMG-20240419-WA01671.jpg` متطابقتان.
- `2026/01/FB_IMG_1759106104480-1.jpg` و`FB_IMG_1759106104480.jpg` متطابقتان.
- ملفا `2026/01/Picsart_24-03-23_04-04-07-861-1-1024x1024-1.webp`
  و`Picsart_24-03-23_04-04-07-861-1-1024x1024-2.webp` متطابقان.
- بعض الأغلفة 600–720 px؛ وجود أصل محلي لا يضمن صورة مناسبة لـhero كبير.
  يفضل استخدامها في cards مناسبة مع أبعاد ثابتة؛ صورة أكبر حقيقية مطلوبة
  عند الحاجة، ولا نكبرها أو نولد مشهدًا يوحي بأنه تجربة موثقة.
- أسماء مثل `FB_IMG_*` أو أسماء مواقع/مزودين أو watermark لا تثبت ملكية
  Safari. لا تستنتج حقوقًا أو موافقة أشخاص من filename أو تاريخ سابق.

## المحتوى المفيد وحدود إعادة استخدامه

- snapshot المحفوظ يحتوي Home، Cancellation Policy، أربع صفحات مجموعات،
  Gallery، About، Contact، Booking، Room/Car، QR Code وصفحة Elementor إضافية.
  إعادة الكتابة من بيانات معتمدة أفضل من نسخ HTML/CSS/scripts القديمة.
- الـ30 رحلة لها نص تاريخي في snapshot؛ **21 فقط** تتطابق slugs حرفيًا مع
  approved catalog. التسع الأخرى تحتاج خريطة legacy → canonical قائمة على
  سجل/URL/مطابقة مثبتة، لا دمجًا على الاسم المتقارب وحده.
- الأسعار والحالات والسياسات الحالية من Wego والكتالوج المعتمد، لا من اسم
  الصورة أو صفحة WordPress. سياسة الإلغاء المعتمدة: ≥48 ساعة كامل؛ 24–48
  ساعة 50%؛ أقل من24 ساعة لا رد. التضارب في صفحة Home القديمة لا يعاد نشره.
- testimonials القديمة، أرقام العملاء، أسماء الفريق وتاريخ الشركة لا
  يعتمدها هذا الجرد. لا استيراد reviews أو حقائق جديدة بلا مصدر معتمد.
- Room/Car والاختيارات غير المنشورة في نموذج WordPress ليست رحلات تشغيل
  جديدة ولا أسعارًا جاهزة للاستيراد.

## المهام المتبقية القابلة للتنفيذ

- [x] تحقق وجود المصدر دون تشغيل WordPress أو قراءة أسرار.
- [x] جرد الحجم والأنواع والـthumbnail families والازدواج الفعلي.
- [x] ربط 29 غلافًا بمرجع approved catalog وحساب الأبعاد/bytes/hash.
- [x] توثيق نقص صورة نقل المطار وفرق المصدر المحلي عن snapshot.
- [~] uploader والمحرران واختبارات الحقوق/المعاينة نفذت في الكود؛ قبول
  MEDIA والـDB+media backup/restore لم يكتمل. انظر
  `2026-10-05_CATALOG_MEDIA_SAFE_CHECKPOINT_AR.md` للأدلة الحالية.
- [ ] مراجعة مصدر وحقوق كل صورة مختارة وموافقة الأشخاص الظاهرين عند اللزوم.
- [ ] اختيار صور galleries فقط عند وجود ارتباط tour موثق؛ لا كل الصور دفعة.
- [ ] كتابة alt فعلي EN/AR/RU/IT، ثم مراجعة revision واعتماد حقوق منفصل.
- [ ] رفع JPEG/PNG المختار عبر pipeline المعتمد؛ خمسة أغلفة WebP تحتاج
  تحويلًا آمنًا مثبتًا إلى JPEG/PNG أولًا إذا ظل intake لا يقبل WebP.
- [ ] اختبار الصورة المرفوعة في preview خاص ثم public بعد الاعتماد، مع
  revocation/optimizer/cache وغياب EXIF/GPS وتوافق backup/restore.
- [ ] جلب أصل/صورة حقيقية معتمدة لنقل المطار أو إبقاء fallback واضح غير مضلل.

لا نسخ 177 MiB إلى Git أو public volume؛ لا نشر جماعي أو حذف source originals.
موافقة محمد على الاستفادة من المصدر تسمح بالتجهيز الفني، لكنها لا تملأ
سجل حقوق غير موجود تلقائيًا ولا تغير المسودة إلى APPROVED.
