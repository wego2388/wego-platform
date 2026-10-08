# تسليم سفاري بعد النشر الفعلي — 8 أكتوبر 2026

النشر على VPS نجح بالفعل، وليس مجرد حزمة جاهزة. الموقع والداشبورد يعملان
بـHTTPS مستقل بجوار الخيمة. Codex هو منفذ النشر؛ لا تشغيل نشر موازٍ.
OPS2-G يبقى ACTIVE لاستكمال قبول المالك وتجربة التشغيل، دون تفعيل باكيت جديد.
علامة `[x]` تعني مثبتًا بالدليل، و`[ ]` تعني متبقيًا، لا وعدًا بالاكتمال.

## الروابط والحسابات

- [x] الموقع: <https://safaritourssharm.com/ar>، والواجهات EN/AR/RU/IT تعمل.
- [x] الداشبورد: <https://staff.safaritourssharm.com/login>، عربي وإنجليزي.
- [x] حساب المالك `mohamedwagdy2323@gmail.com` نشط، بدور `platform-admin`
  و٣٢ صلاحية. المالك أدخل كلمة المرور بنفسه؛ تسجيل دخول ناجح مثبت.
- [x] الحساب الثاني `safaritourssharm@gmail.com` أنشأه المالك بكلمة مرور خاصة؛
  قائمة الموظفين الفعلية تؤكد ACTIVE/platform-admin. الحسابان نشطان، والأول محفوظ.
- [x] لا كلمة مرور افتراضية أو كلمة مرور في الشات/Git/ملفات env.

## ما نُشر بالضبط

| البند | القيمة المثبتة |
|---|---|
| VPS | `187.6.167.233`، SSH alias الموثوق `resort-os-vps-next` |
| Safari branch | `wego-016-safari-hardening`، لا دمج main |
| مصدر بنية سفاري | `61b34cfbef7e2972b229251202de2d7a888cfc13` |
| مصدر التطبيقات داخل الصور | `198959efdd3754fffc9c357e555f25a014569b46`؛ platform/products/web مطابقة لـ61b34cf |
| إصدار السيرفر | `/srv/safari-tours-sharm/releases/str-2026.10.08-61b34cf`؛ `current` يشير إليه |
| Compose project | `safari-tours-sharm-prod`، خمس خدمات healthy |
| بوابة سفاري الخاصة | `127.0.0.1:58080`؛ لا منافذ عامة للـDB/backend/site/ERP |
| مصدر عقد بوابة الخيمة | `b4317b796d98a3a0b6c72cb594a0f3abfe353524`، فرع `codex/shared-gateway-20261008` |
| تطبيق الخيمة الذي ظل يعمل | `4983a7b0d53bfbcf905775b8278ee994900f1be3`؛ لم تُستبدل صوره بهذا النشر |

صور التطبيقات الثلاثة تحمل tag `str-2026.10.08-198959e`:

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
- [ ] تحديث UX الطلب والصور التعبيرية تحت التحقق: preferred date بلا حد60 يوم،
  no slot/capacity hold، والتأكيد للمكتب. حتى توثيق نشر جديد، صورة الموقع
  القديمة وCI القديمة أعلاه لا يُنسب لهما نجاح كود UX الجاري.
- [ ] إدخال الموردين والسائقين والمركبات والمواعيد والتكاليف وسعر الصرف الحقيقي
  من الداشبورد؛ الوحدات موجودة، سجلات التشغيل التجارية لم تُختلق.
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
فحص Lighthouse على مسار خاطئ `/en/tours/...` عاد404؛ ليس دليل نجاح صفحة الرحلة.

Runbook الحاكم: [CONTAINER_GATEWAY_RUNBOOK_AR.md](../deployment/CONTAINER_GATEWAY_RUNBOOK_AR.md).
لا تشغيل hostNginx أو certbotstandalone، ولا إعادة first-install/seed/bootstrap
فوق الإدارة الحالية. كل عملية مستقبلية للخيمة تستخدم `/usr/local/sbin/resort-compose`؛
وكيل الخيمة يضم عقد البوابة في فرع إنتاجه قبل أي نشر، بلا main merge تلقائي.
رجوع سفاري بعد التركيب: maintenance503 لسفاري أو صور الإصدار السابق مع
edge recreate؛ حافظ على شبكة ومثبتات البوابة والخيمة. لا DB restore لمجرد رجوع كود،
ولا down-v أو prune عام أو حذف مفتاح/نسخ أو تشغيل شغل كلودي الآخر.

التسليم يثبت إطلاقًا مستقرًا للاستفسارات، لا اكتمال كل البيانات التجارية أو
دفع إلكتروني جاهز. قبول تجربة المالك التشغيلية متبقٍ بوضوح، لذلك لا إغلاق
صامت لـOPS2-G أو بدء توسعات جديدة.
