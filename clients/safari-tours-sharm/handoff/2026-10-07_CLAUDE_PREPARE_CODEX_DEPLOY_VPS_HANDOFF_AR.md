# Safari Tours Sharm — كلودي يجهّز، Codex يراجع وينفّذ النشر

> التاريخ: 7 أكتوبر 2026. طلب مباشر من محمد.  
> **المطلوب الآن: تحضير وتسليم محلي؛ ليس بدء نشر على السيرفر.**  
> المالك التنفيذي للنشر اللاحق: **Codex**؛ منفذ تجهيز المنتج والأدلة: **Claude**.  
> شرط المالك: **الخيمة شغالة بالفعل على الـVPS ولا نعدّل مشروعها أو نوقفها.**

## 1. الرسالة المباشرة لكلودي

كمّل تجهيز Safari Tours Sharm داخل مشروعه المستقل، وسلّم Codex إصدارًا
واضحًا وقابلًا للنشر، بالأدلة وخطة الرجوع والنسخ والاسترجاع. **لا تنفّذ
النشر بنفسك، ولا تدخل السيرفر أو تغيّر DNS أو إعدادات الخيمة من أجل التجهيز.**
Codex هو الذي يفحص السيرفر، يراجع الإصدار وخطة التغيير، ويجهز إعداد Safari
المستقل ثم ينفذ النشر بعد بوابة GO واعتماد محمد. لا ترسل أسرارًا أو مفاتيح
في ملف التسليم أو المحادثة. هذا الملف لا يعطي صلاحية commit/push تلقائية.

اقرأ أولًا `AGENTS.md` وConstitution وBoard وملف التعاون، ثم هذا الملف.
لا تتعامل مع تسليم 5 أكتوبر على أنه وصف ثابت لكل تقدم لاحق.

## 2. الحالة التي تحقّق منها Codex عند كتابة هذا الملف

| الحالة | ما ثبت / حد الدليل |
|---|---|
| `[x]` | worktree: `/home/wego/wego-safari-hardening`؛ branch: `wego-016-safari-hardening`؛ HEAD المقروء: `982c6ce` |
| `[x]` | Board الحالي: **WEGO-016-OPS2-E** هي الحزمة الوحيدة ذات `Status: ACTIVE`؛ نطاق الموردين والسائقين والمركبات والتعيين |
| `[x]` | Board يسجل إغلاق MEDIA وOPS2-C وOPS2-D، مع follow-ups وبوابات إصدار؛ هذه قراءة للسجل وليست إعادة اختبار/قبول من Codex للتغييرات اللاحقة |
| `[~]` | `bundle-backup.sh` و`bundle-restore-drill.sh` و`verify-live-media.sh` موجودة الآن؛ لا تنقل وصف «غير منفذة» القديم، ولا تعتبر وجود الملفات دليل استرجاع إصدار الإطلاق |
| `[!]` | ملف `DEPENDENCY_AUDIT_EXEMPTIONS.md` و`pnpm.auditConfig.ignoreGhsas` موجودان؛ إقرار المالك الخاص بالنشر لكل استثناء ما زال غير مسجل في الملف عند القراءة |
| `[x]` | DNS authoritative الحالي للأسماء `@` و`www` و`staff` يشير إلى `187.6.167.233`، TTL 14400؛ هذه لقطة فحص، يعاد التحقق عند النشر |
| `[x]` | صورة المالك تعرض الخيمة على `https://safaritourssharm.com/ar`؛ طلب HTTP مباشر للعنوان مع Host سفاري يرجع تحويلًا إلى HTTPS |
| `[x]` | اختبار TLS المباشر فشل لأن شهادة السيرفر لا تطابق `safaritourssharm.com`؛ **لا تعطّل التحقق من الشهادة لتجاوز ذلك** |
| `[!]` | لم يدخل Codex الـVPS؛ البنية الداخلية والمنافذ والسعة ومالك 80/443 واسم دومين الخيمة لم تتحقق من داخل السيرفر |
| `[ ]` | لا نشر Safari ولا إصدار شهادة ولا تغيير proxy/الخيمة تم في هذه الجلسة |

**الاستنتاج، لا حقيقة config مقروءة:** ظهور الخيمة يتفق مع fallback أو
توجيه خاطئ في بوابة السيرفر. لا نحسم شكل الملف الخاطئ قبل فحصه. DNS وحده
ليس نشرًا، وانتظار الانتشار لا يصلح شهادة أو upstream خاطئًا.

يوجد تنفيذ متزامن لكلودي؛ لا reset/restore ولا حذف لتغييرات الآخرين.
هذه الوثيقة تضيف ترتيب تسليم فقط: **لا تغلق OPS2-E ولا تفتح packet نشر
ثانية بسببها**. عند تجهيز الإطلاق، تسجل scope وبوابات النشر في Board بعد
قبول الحزمة النشطة ووفق تفويض المالك؛ OPS2-F/G ليست COMPLETE بمجرد وجود خطة.

## 3. التصميم المطلوب — جهاز مشترك، مشروعان مستقلان

| الدومين / المكون | الوجهة المطلوبة | الملكية والعزل |
|---|---|---|
| دومين الخيمة الحالي | نفس upstream الحالي بلا تغيير | مشروع الخيمة محمي خارج نطاق Safari |
| `safaritourssharm.com` | موقع Safari + واجهات العميل العامة المسموحة | إعداد host محدد لسفاري، HTTPS صحيح |
| `www.safaritourssharm.com` | 301 إلى دومين Safari الأساسي مع حفظ المسار | لا تحويل إلى الخيمة ولا homepage لكل URL |
| `staff.safaritourssharm.com` | ERP + staff/identity APIs الحالية | مصادقة وصلاحيات الخادم، HTTPS، noindex |
| Safari backend/PostgreSQL/media | خدمات وحجوم تخزين خاصة بسفاري | لا اتصال بقاعدة الخيمة ولا نسخ أسرارها |

**المسار المفضل إذا TLS terminator الحالي يعمل على المضيف:**

```text
Internet → بوابة HTTPS الحالية
             ├─ دومين الخيمة → إعدادها الحالي دون تعديل
             └─ دومينات Safari → إعداد Safari جديد محدد بالاسم
                                  → 127.0.0.1:<Safari edge port>
                                  → edge Safari → site / ERP / backend
                                                        → PostgreSQL + media خاصة
```

هذه معمارية مستهدفة وليست ادعاءً بفحص المضيف. لو بوابة HTTPS داخل container،
فـ`127.0.0.1` بداخلها ليس localhost المضيف. **لا تربط شبكات التطبيقات
بالخيمة أو تعيد إنشاء بوابتها لحل ذلك تلقائيًا**؛ Codex يراجع topology أولًا
ويطلب اعتمادًا إذا تطلب الحل تغييرًا أوسع. لا نحجز 80/443 لحاوية Safari
ثانية ولا نثبت proxy آخر ينافس الموجود.

### أسماء ومسارات مقترحة، تثبت بعد فحص Codex

- Compose project: `safari-tours-sharm-prod`، ودليل خاص `/srv/safari-tours-sharm`.
- releases immutable حسب release ID؛ `shared/` للأسرار والإعداد الدائم، لا أسرار في release artifact.
- volume names وفق المشروع: `safari-tours-sharm-prod-postgres-data` و`...-media`؛ شبكة مستقلة.
- edge مربوط بـ`127.0.0.1` على منفذ حر يختاره Codex؛ `58080` مرشح **وليس منفذًا محجوزًا أو مثبتًا**.
- PostgreSQL بلا published port في production؛ backend/site/ERP غير مكشوفة مباشرة.
- backup directory خاص بسفاري؛ حساب DB وكلمات مرور ومفاتيح bootstrap جديدة.

لو وجد Codex تثبيت Safari سابقًا بvolumes أو history، يحافظ على هويته
ويضع خطة ترقية؛ لا يغيّر project/volume names ويعلن نجاح قاعدة فارغة بدل البيانات.
المنافذ loopback يجب التحقق منها في Compose المحلول وDocker الفعلي؛ اسم
شبكة `internal` وحده ليس ضمان عزل. [مرجع Docker](https://docs.docker.com/engine/network/port-publishing/).

## 4. حماية الخيمة — حدود لا تتجاوز

- [ ] لا تعديل source أو `.env` أو DB/volumes أو Compose أو services أو
  certificate/renewal config أو server block/upstream الخاص بالخيمة.
- [ ] لا stop/restart/recreate لحاويات أو خدمات الخيمة، ولا restart لـDocker daemon.
- [ ] لا global prune أو `down -v` أو حذف عام أو تغيير firewall/daemon/network
  settings عامة أو install/upgrade للمضيف يؤثر على تشغيلها.
- [ ] لا build ثقيل على VPS الإنتاج قبل تقييم الموارد؛ الأفضل بناء artifact
  المراجع خارج السيرفر ونقله/سحبه كصور ثابتة.
- [ ] لا نسخ credentials من مشروع الخيمة، حتى لو كانت طريقة النشر مشابهة.
- [ ] سجّل قبل/بعد baseline غير حساس للخيمة: دومينها وTLS/HTTP وحالة/هوية
  حاوياتها وأوقات بدء الخدمات، دون فتح DB أو سرد أسرارها أو بيانات العملاء.

**حد مشترك لابد أن يفهمه محمد:** إضافة Safari إلى نفس بوابة HTTPS قد
تحتاج ملف virtual host جديدًا و**graceful reload للبوابة المشتركة فقط**، بعد
اختبار config. هذا ليس restart لمشروع الخيمة، لكنه تغيير في مكون مشترك؛
يسجله Codex صراحة في خطة التغيير وGO. لا وعد بأن «لن نلمس أي شيء في
السيرفر إطلاقًا». لو شرط المالك يمنع reload للبوابة أيضًا، **قف واسأله**
عن البديل المعتمد، ولا تنفذ خلسة. [سلوك Nginx عند reload](https://nginx.org/en/docs/beginners_guide.html).

## 5. قائمة تجهيز Claude — سلّم بالأدلة لا بالتوقعات

### أ. مصدر الإصدار وجودة المنتج

- [ ] أكمل scope الحزمة النشطة أولًا مع مراجعتها المستقلة؛ سجل follow-ups
  وأي قرارات تجارية ناقصة. لا تعتبر تاريخ اختبار قديم دليلًا على SHA جديد.
- [ ] سلّم source SHA النهائي وBoard state وrelease plan/lock/digests، وقائمة
  migrations الفعلية وترتيبها؛ لا تفترض أن آخر migration ما زال V29.
- [ ] backend check/test على Safari المعزول، وweb lint/typecheck/tests/build،
  العقود/Foundry/repository/legacy/log privacy، مع commands وexit codes وأوقات التشغيل.
- [ ] E2E فعلي على fresh disposable Compose للإصدار النهائي: enquiry/المكتب/
  المستندات والموردين بحسب scope المعتمد، الصور والحقوق، guest flow بلا login،
  staff/public boundaries واللغات والمقاسات. ميّز API mocks عن الاختبار الحقيقي.
- [ ] browser/accessibility/UAT/أداء بالمستوى المطلوب للإطلاق؛ لا تختلق
  Lighthouse أو Core Web Vitals scores، ولا smoke يغيّر بيانات production.
- [ ] خذ أدلة الإعفاءات الأمنية الحالية كمخاطر مفتوحة: أعد تقييم advisories
  والنسخ المصححة وproduction image reachability، واعرض ignores صراحة؛ نجاح
  audit مع ignores لا يعني زوال الثغرات. مراجعة مستقلة وإقرار محمد قبل النشر.

### ب. Artifact نشر خاص بسفاري

- [ ] صور backend/site/ERP مراجعة، immutable tags + digests/checksums، release
  manifest، CPU architecture ومصدر build وSBOM/scan عند توفر أدواته المعتمدة.
- [ ] Compose production overlay لا يفتح DB/8080/3000/3001 ولا ينشر edge
  على `0.0.0.0`. افحص **resolved Compose**: merge قد يترك ports القديمة.
  لا تطبع `docker compose config` بأسرار في logs؛ احتفظ بالنسخة الحساسة خاصًا.
- [ ] `.env.production.example` بلا قيم live: mode/origins/names/paths/ports
  placeholders وإعدادات backup/health/limits. لا تستخدم كلمات سر E2E أو env المحلي.
- [ ] منطق bootstrap staff موجود ومراجع وطريقة تسليم آمنة؛ لا الحساب
  الاصطناعي `e2e-staff@example.com` ولا password داخل التسليم.
- [ ] resource budget موثق للـCPU/RAM/disk/log rotation/DB pool/heap وupload
  peak، مع حدود Safari وحدها؛ codex يحدد الأرقام بعد preflight، لا يستهلك
  الذاكرة المتاحة كلها ولا يغيّر حدود الخيمة.
- [ ] أثبت مسار build المعتمد. `safari-backend-from-jar.Dockerfile` موجود
  الآن كبديل محتمل؛ لا تعلن قبوله لأنه تجاوز مشكلة شبكة. يلزم provenance
  ونفس SHA/نسخ الأدوات والأدلة وقبول reviewer لمسار الإصدار المختار.

### ج. Gateway/HTTPS — تجهيز محلي لا تطبيق حي

- [ ] قالب host config **لسفاري فقط**، بأسماء public/www/staff الدقيقة،
  challenge path وصيانة مستقلة، proxy إلى edge الخاص، timeouts/body limits
  مناسبة لحد رفع الصور؛ لا تعديل global default أو rules الخيمة.
- [ ] Host الصحيح يصل إلى edge حتى يختار staff server؛ لا تدمج واجهات
  staff/identity في public origin ولا bypass لمسارات الحماية الحالية.
- [ ] خطط trusted forwarding للـHTTPS وclient IP: outer proxy يكتب القيم
  بنفسه لا يثق بهيدر الزائر؛ اختبر السلسلة الفعلية عبر proxyين. الكود الحالي
  يكتب `X-Forwarded-Proto $scheme` في edge: لا تفترض أن https سيصل تلقائيًا
  بعد TLS termination خارجي. أي إصلاح أمني يحتاج scope/review قبل نشره.
- [ ] اختبر منع spoofing وعدم جمع الزوار تحت IP واحد في rate limit، cookies/
  redirects/canonical/CORS وHTTPS؛ لا تغيّر trusted networks في production تخمينًا.
- [ ] HTTPS لكافة أسماء Safari، وشهادة مستقلة لا تعيد إصدار شهادة الخيمة.
  جهز webroot challenge خاصًا أو طريقة ACME معتمدة؛ لا `--standalone`
  يستلزم وقف port 80، ولا auto-installer يحرر configs أخرى بلا diff/review.
  خطط التجديد وقياس انتهاء الشهادة دون stop hooks. [مرجع Certbot](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot).
- [ ] `WEGO_SITE_PUBLIC_URL=https://safaritourssharm.com` للروابط SEO؛ تحقق
  من EN/AR/RU/IT/canonical/hreflang/sitemap ومن noindex لصفحات staff/private.

### د. البيانات والدفع والتشغيل

- [ ] seed/import مدروس لكتالوج المالك الموافق عليه، statuses/prices/rights/
  policies وowner-data المحدثة؛ **لا تنقل DB الاختبار وصورها الاصطناعية إلى production**.
- [ ] الاختيار المبدئي المقترح: `ENQUIRY_ONLY` لحين قبول Paymob الحقيقي،
  `TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=false`. راجع طريقة الحجز المكتبي والتحصيل المعتمدة من المصدر الحالي؛
  لا ترجعها إلى «غير منفذة» لأن تسليم 5 أكتوبر قديم.
- [ ] إذا توجد payment history: احتفظ بالadapter والمفاتيح والتسوية المطلوبة؛
  لا تحذف history أو secrets لجعل الخدمة تبدأ. لا تفعيل ONLINE_PAYMENT قبل sandbox/owner GO.
- [ ] قرارات المستندات المفتوحة في Board (refund basis للـdeposit، ساعة
  الانطلاق لأشرطة 48/24 ساعة، نص تعليمات voucher) تُحسم أو تُقيّد الوظيفة
  المتأثرة بوضوح وموافقة المالك؛ لا سياسة إنتاج مالية ضمنية أو حساب refund مخترع.
- [ ] bundle DB+media للإصدار النهائي: manifest/checksums/permissions، اختبار
  concurrent writes وmissing/corrupt media، وفشل واضح بلا backup ناقص.
- [ ] restore **إلى DB وvolume جديدين** وتشغيل الإصدار فوقهما: روابط approved
  تعمل، DRAFT يبقى private، الحجوزات/المدفوعات/المستندات/التعيينات سليمة؛
  hash-check للأرشيف وحده لا يغني عن تجربة استخراج وتشغيل كاملة.
- [ ] encryption/key custody، off-server destination/retention/alerting
  معتمدة، وجدولة خاصة بسفاري. plaintext local drill ليس إعداد production.
- [ ] health/monitoring/log rotation/backup-age/TLS expiry ومسؤول التنبيه،
  مع تفسير WARN المتوقع في ENQUIRY_ONLY وعدم إخفاء بقية FAILs.
- [ ] rollback حسب schema الفعلي: old image لا يضمن توافق migrations؛
  forward-fix أو restore متوافق، مع حفظ البيانات الجديدة وتسوية المال.

## 6. ملفات التسليم التي يجهزها Claude

المسارات الجديدة التالية **مقترحة وغير منفذة بهذه الوثيقة**؛ لا تنشئ
مواصفات بديلة للعقود الحالية ولا تطلق packet إضافية في منتصف OPS2-E.

| المخرج | المسار المقترح / المصدر الحالي |
|---|---|
| readiness manifest: SHA/images/migrations/evidence/risks | `clients/safari-tours-sharm/deployment/RELEASE_READINESS.md` |
| production env schema بلا أسرار | `clients/safari-tours-sharm/deployment/.env.production.example` |
| overlay production مراجع بلا build حي وبلا DB ports | `infrastructure/compose/safari-tours-sharm.production.yaml` |
| host config templates public/www/staff + maintenance/ACME | `clients/safari-tours-sharm/deployment/nginx/` |
| ترتيب التنفيذ وrollback بخطوات scoped ومراجعة | `clients/safari-tours-sharm/deployment/DEPLOY_RUNBOOK_AR.md` |
| smoke non-mutating + baseline protection + evidence index | `clients/safari-tours-sharm/deployment/VERIFICATION_CHECKLIST.md` |
| release artifacts الحالية والعزل | `release.lock.json` و`release.plan.json`، Dockerfiles وCompose Safari الحالي |
| backup/restore/health | `scripts/safari-ops/` و`docs/operations/SAFARI_TOURS_SHARM_OPERATIONS.md`؛ طور الموجود بدل duplicate scripts |

كل ملف تسليم يذكر: ما نفذ `[x]`، ما اختبر فعليًا، المفتوح `[ ]`، المعتمد
جزئيًا `[~]`، ما يحتاج محمد `[!]`، تاريخ/SHA الأدلة، reviewer verdict، وأول
خطوة لـCodex. احفظ evidence في مكان الفريق المعتمد؛ `/tmp` ليست أرشيف إصدار.

## 7. ما سينفذه Codex لاحقًا — ترتيب ملزم

1. [ ] مراجعة التسليم النهائي ومصدره واختباراته والمخاطر؛ verdict **GO أو NO-GO**.
2. [ ] الحصول على وصول آمن محدود وتأكيد VPS identity؛ preflight read-only:
   OS/architecture/resources/Docker version/listeners/proxy topology ومسارات
   Safari والخيمة. لا تطبع كامل env/config/secrets أو بيانات عملاء.
3. [ ] تثبيت baseline الخيمة وخطة diff إضافية فقط؛ اعتماد محمد للتغيير
   الدقيق والإصدار المختار والوضع، ولـreload البوابة المشتركة إن لزم.
4. [ ] إنشاء release Safari وprivate config/volumes فقط، وتشغيله على loopback
   قبل ربط أي زائر؛ لا global compose commands أو build مرهق على VPS.
5. [ ] تثبيت البيانات المعتمدة/bootstrap حسب الخطة، وانتظار health ثم smoke
   داخلية للـpublic/staff/media؛ snapshot/backup مناسب قبل أي ترقية لتثبيت موجود.
6. [ ] إضافة إعداد Safari-only للبوابة؛ إذا المشروع غير جاهز فلا expose
   التطبيق: صفحة صيانة مستقلة 503/noindex/Retry-After فقط **بعد اعتماد منفصل**.
7. [ ] challenge وشهادة Safari صحيحة، اختبار config في سياق البوابة الفعلي،
   ثم graceful reload معتمد فقط؛ لا service restart ولا تعديل TLS الخيمة.
8. [ ] تحقق خارجي بشهادة سليمة **دون `curl -k`** لكل الأسماء وredirects
   وSSR وstaff denial وuploads/private bytes. بالتوازي اختبر الخيمة قبل/بعد؛
   إذا تغيرت أو تأثرت، توقف وارجع **إضافة Safari فقط** وبلغ محمد.
9. [ ] فعّل public Safari فقط بعد GO؛ جدولة backups/renewal/health المعتمدة،
   اختبار restore، ثم تسليم release ID/digests وأدلة URLs والمراقبة وخطة التشغيل.

لا نفترض أن symlink وحده يبدّل container image: Compose يشغّل الصور
المثبتة والنسخة المختارة صراحة. لا تشغّل backend قديمًا وجديدًا مع schema
غير متوافق، ولا تستخدم rollback حذف قاعدة جديدة لاستعادة مظهر الصفحة.

## 8. المطلوب من محمد عند بوابة النشر — ليس أسرارًا في الشات

- [!] وصول deploy آمن لسفاري وتأكيد حساب/هوية VPS، واسم دومين الخيمة
  المستخدم لفحص baseline؛ لا root password/private key داخل Git أو المحادثة.
- [!] اعتماد release ID والوضع (enquiry/office أولًا أو الدفع الحقيقي بعد بواباته)،
  وUAT/بيانات العميل والقرارات المالية المفتوحة ذات الصلة بالإطلاق.
- [!] قبول أو رفض مخاطر dependency exemptions صراحة بعد مراجعتها المحدثة.
- [!] بريد مسؤول الشهادة ومكان backup خارج السيرفر ومسؤول alerts/key custody.
- [!] اعتماد التغيير المشترك المحدود للبوابة، إن لزم. الخيمة وبياناتها خارج نطاق التعديل.

## 9. شروط STOP / NO-GO

قف وابلغ محمد بدل التخمين إذا: الخطة تحتاج تغيير/وقف الخيمة؛ هوية السيرفر
أو ملكية 80/443 أو المنافذ غير مؤكدة؛ الموارد لا تكفي؛ source SHA لا يطابق
artifact/evidence؛ BLOCKING review مفتوح؛ archive/restore يفشل؛ لا certificate
مطابق؛ commercial/legal fact ناقص مؤثر؛ audit exception بلا الإقرار المطلوب؛
production secrets ناقصة؛ migration history drift؛ أو بوابة governance غير مكتملة.

**قبولنا النهائي:** سفاري يعمل على دوميناته وشهادته وبياناته الخاصة، والخيمة
تعمل على دومينها كما كانت، ومراقبة ونسخ واسترجاع وrollback مثبتة. ليس فقط
أن الصفحة فتحت. هذه الجلسة أنشأت الوثيقة والفهرس؛ **لم تنفذ deploy**.
