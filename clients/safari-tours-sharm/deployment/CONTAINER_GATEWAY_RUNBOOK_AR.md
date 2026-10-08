# سفاري — مسار النشر الفعلي على بوابة Docker المشتركة

8 أكتوبر 2026 · OPS2-G ACTIVE · المنفذ الوحيد Codex.
تم تنفيذ هذا المسار بنجاح؛ الدليل والحالة الحالية في
[تسليم الإنتاج](../handoff/2026-10-08_PRODUCTION_DEPLOYED_HANDOFF_AR.md).
الخطوات التالية مرجع للنشر/الرجوع، لا إذن بإعادة first-install على الإنتاج.
محمد أجاز نشر سفاري والتعديل الضروري للبوابة المشتركة، وحفظ ورفع التغييرات
المحددة على الفروع الحالية فقط. لا main merge ولا تعديل تطبيق أو بيانات الخيمة.

## العقد الثابت

- بوابة 80/443: `resort-os-prod-nginx-1`؛ لا Nginx على host.
- الخيمة تظل على `resort-os-prod_default` بكل إعداداتها وشهادتها وصورها.
- شبكة خارجية `safari-gateway` تضم nginx المشترك وedge سفاري فقط.
- قاعدة وbackend وERP وموقع سفاري لا تنضم إلى شبكة البوابة.
- سفاري: `safari-tours-sharm-prod`؛ الحجوم الإنتاجية مثبتة، لا منافذ DB/app.
- edge على loopback 58080 وعلى الشبكة باسم `safari-edge:8080`؛ DNS ديناميكي
  داخل nginx المشترك يمنع توقف الخيمة إذا غاب سفاري أو تغير عنوان edge.
- إعداد سفاري `/srv/safari-tours-sharm/shared/gateway` mounted read-only
  كمجلد؛ ملف loader منفصل ثابت. تبديل `safari.conf` داخل المجلد atomic.
- HSTS بدون includeSubDomains، لا AAAA أو IPv6 server إضافي بلا تحقق.

## قبل أي تعديل إنتاج

### تحديث الموقع وحده بعد الإطلاق

تحديث8 أكتوبر النهائي استخدم صورة الموقعcd18068 فقط؛ backend/ERP من198959e
ظلا كما هما. current الآن`str-2026.10.08-cd18068`. الإصدار الجديد يحفظ
RELEASE_MANIFEST المحدث وCI_SOURCE_PROOF وCORE_RELEASE_MANIFEST التاريخي.
تفاصيل الصور والاختبارات في التسليم؛ لا relabel الصور بمصدر commit توثيق لاحق.

1. خذ backup مشفرًا واسحبه خارج VPS واختبر الاستعادة المعزولة قبل التحديث.
2. ابنِ صورة من المصدر المحدد الذي اجتاز CI كاملًا، وافحص immutable image
   عبر Compose/Playwright. انقل checksum/imageID/source/output hashes ودليل CI.
3. جهّز release جديدًا، لا تعدّل القديم. نسخة env احتياطية خاصة mode0600؛
   بدّل SAFARI_SITE_IMAGE فقط، وتحقق أن كل الأسطر الأخرى لم تتغير.
4. اعرض resolved Compose للـverifier عبر stdin، لا تطبعه أو تحفظ أسراره في logs.
5. استخدم base+production+gateway و`-p safari-tours-sharm-prod` صراحةً:
   `up -d --no-deps --no-build --wait safari-site` فقط.
6. بعد healthy: `docker exec safari-tours-sharm-prod-edge-1 nginx -t`، ثم
   `docker exec safari-tours-sharm-prod-edge-1 nginx -s reload` ليحل عنوان
   الموقع الجديد. لا recreate للـedge أو nginx الخيمة لتغيير صورة الموقع وحدها.
7. تحقق على الدومين الحقيقي، واحتفاظ باقي container IDs/start/images/restarts
   بحالتها، ثم بدّل current atomically. خذ backup جديدًا واختبره أيضًا.
8. عند الفشل أعد SAFARI_SITE_IMAGE السابق، وأعد up للموقع فقط وedge reload؛
   اختبره قبل current switch. لا DB restore ولا down-v لرجوع كود الموقع.

النشر الحالي single-instance؛ استبدال الموقع قد يسبب فجوة قصيرة، ولا ادعاء
zero-downtime دون قياس. لا تكرر خطوات first-install أدناه على قاعدة المالك.

1. تثبيت هوية VPS وDNS A للأسماء الثلاثة، عدم CDN/AAAA غير مخطط.
2. تسجيل الأربع HTTPS للخيمة بـTLS صارم وبصمة الشهادة، current release، صور
   وتوقيت/restart/ID كل حاويات الخيمة، checksum ملفات nginx الحالية.
3. حفظ rollback لإعداد البوابة وCompose وصورتها، دون نشر أو نسخ الأسرار إلى Git.
4. اجتياز CI على commit الإصلاحات، و24 E2E على صور الإصدار، ومراجعة Tier-1
   بلا blockers. قبول owner للاستثناءين لا يلغي runtime verification.
5. غياب أي تثبيت سفاري سابق مثبت؛ لو ظهرت بيانات يصبح Upgrade مع backup أولًا.

## إعداد إصدار سفاري

انقل حزمة حد أدنى من commit مُراجع إلى
`/srv/safari-tours-sharm/releases/<release-id>` وصور runtime المبنية خارج VPS.
لا تنقل ملفات owner-data أو قاعدة E2E أو `node_modules` البناء أو مفتاح النسخ الخاص.
قارن checksum الحزمة وimage IDs والـlabels بعد `docker load`.

ملف الأسرار `/srv/safari-tours-sharm/shared/.env.production` mode0600، كلمة DB
مولدة على السيرفر مستقلة. ENQUIRY_ONLY، mock=false، notifications=false،
لا Paymob/SMTP/analytics تجريبية في الإنتاج.

Compose سفاري دائمًا base ثم production ثم gateway، بالـenv الإنتاجي:

```bash
DC() { docker compose -p safari-tours-sharm-prod --env-file "$ENVF" \
  -f "$REL/infrastructure/compose/safari-tours-sharm.compose.yaml" \
  -f "$REL/infrastructure/compose/safari-tours-sharm.production.yaml" \
  -f "$REL/infrastructure/compose/safari-tours-sharm.gateway.yaml" "$@"; }
DC config --quiet
DC up -d --no-build --wait --wait-timeout 180
```

افحص resolved JSON داخليًا فقط: لا build، لا منافذ سوى edge loopback، فقط
edge على الشبكة الخارجية، الحجوم الصحيحة، صورة كل service pinned كما في manifest.
لا طباعة JSON أو env. لا `down -v` ولا system/volume prune.

قبل تعريض الموقع: health UP، 22 migrations ناجحة/صفر فشل، 30 tour معتمدة
منها27 نشطة وفق V25؛ Private Boat وIntro Diving REQUEST_ONLY/inactive،
Crocodile Show inactive؛32 permission، لا مستخدم/رحلة E2E، ولا جداول منتجات أخرى.
صور sharp/IPX فعليًا، canonicals على النطاق العام، ERP noindex، لا تشغيل checkout.

## إضافة البوابة مع الحفاظ على الخيمة

1. أنشئ gateway directory/loader/proxy/maintenance. render باستخدام
   `scripts/safari-ops/render-container-gateway.sh bootstrap|proxy`، وليس
   نسخ template host مباشرة. استخدم permissions قابلة للقراءة من nginx.
2. ثبت overlay `gateway/resort-gateway.compose.yaml` root-owned في
   `/etc/resort-os/safari-gateway.compose.yaml`.
3. من فرع الخيمة `codex/shared-gateway-20261008` ثبت root-owned:
   `scripts/resort_compose.sh` → `/usr/local/sbin/resort-compose`،
   `scripts/verify_shared_gateway.py` → `/usr/local/lib/resort-os/verify-shared-gateway.py`.
   ثبت كذلك marker مستقل root-owned mode0644 في
   `/etc/resort-os/shared-gateway.required`. وجود marker أو wrapper يفرض gate
   حتى إذا فُقد overlay؛ لا fallback إلى Compose القديم عند فقد ملف الربط.
4. الـwrapper يقرأ صور release المعتمدة من current، ومفتاح DB في الذاكرة،
   ويتحقق من المجلد والشبكة والمثبتات الدقيقة قبل كل عملية. لا تشغيل application
   source الجديد من فرع البوابة؛ صور الإنتاج الحالية تبقى كما هي.
5. شغّل nginx-t في حاوية مؤقتة بالصورة الحالية نفسها ومثبتاتها مع overlay؛
   لا تغيير حاوية البوابة قبل النجاح.
6. `sudo /usr/local/sbin/resort-compose config --quiet` يجب ينجح.
7. أول تركيب فقط يحتاج إعادة إنشاء nginx وحده:
   `sudo /usr/local/sbin/resort-compose up -d --no-deps --no-build nginx`.
   راقب HTTPS للخيمة فورًا، وبقاء IDs/start/restarts الخدمات الثماني الأخرى.
   لا `up` لكل مشروع الخيمة، ولا تغيير DB/Redis/guest أو certificate الخيمة.

## HTTPS مستقل ثم تفعيل سفاري

Webroot ACME هو الموجود `/var/www/certbot`، والشهادات مركبة أصلًا read-only.
بعد نجاح challenge عبر HTTP لكل اسم، استخدم certbot webroot فقط مع
certificate name `safaritourssharm.com` وأسماء apex/www/staff وبريد
`mohamedwagdy2323@gmail.com`. لا `--nginx`/`--standalone` ولا تغيير lineage الخيمة.

الـrenewal service الموجود بالفعل لديه deploy-hook
`/usr/local/sbin/reload-resort-os-nginx` الذي يعمل `docker exec nginx -t`
ثم `nginx -s reload`. تحقق منه قبل الاعتماد؛ لا host systemctl nginx ولا
timer عام جديد يكرر أو يبدل خدمة التجديد. نفذ renewal dry-run لشهادة سفاري.

render live إلى ملف مؤقت في directory البوابة، تحقق syntax في الحاوية المؤقتة
قبل استبداله، ثم atomic rename + nginx-t في البوابة + reload فقط. HTTP→HTTPS،
www→apex مع path/query، staff noindex، EN/AR/RU/IT وAPI boundaries وIPX بـTLS
صارم؛ لا curl -k. قارن الأربع origins للخيمة وبصمة شهادتها وإعداداتها.

## الإدارة والنسخ والمراقبة

أول admin: `mohamedwagdy2323@gmail.com`؛ password يكتبه محمد تفاعليًا عبر
bootstrap-admin، لا chat/env/args/default. لا يوجد display-name في bootstrap
الحالي، فلا migration لإدخال Wagdio متسللة في النشر.

المفتاح الخاص على جهاز محمد فقط، public key على VPS، النسخ مشفرة وتشمل DB+media.
أول bundle يجب سحبه إلى `/home/wego/Backups/safari-tours-sharm` وفحص hashes
واستعادته في DB مؤقتة network-none بلا منافذ/حجوم live. اقرأ scripts قبل التنفيذ؛
off-server drill يحتاج حاوية Postgres مرجعية محلية بالـproject الصحيح، لا قاعدة production.
انسخ التقرير JSON فقط إلى VPS لإثبات freshness؛ لا المفتاح أو البيانات المفكوكة.

جدولة backup/health مستقلة لسفاري؛ لا تبديل timers الخيمة. Receiver للتنبيهات
الخارجية لم يُختَر، ونسخ الجهاز المحلي لا تُسحب وهو مطفأ؛ وضّح ذلك في التسليم.

## الترقيات المستقبلية والرجوع

كل نشر للخيمة على هذا host يجب يستخدم `resort-compose --release <immutable-dir>`
والـbundle verifier الجديد. دمج عقد البوابة في مصدر النشر المستقبلي مهمة صريحة
لوكيل الخيمة، لا merge تلقائي لـmain. حذف overlay أو network أو mount يوقف gate.
الـwrapper لا يحمي ممن يتجاهله عمدًا ويستعمل raw Compose؛ هذا ممنوع في runbook.

عند ترقية تطبيقات سفاري، أعد إنشاء edge بعدها لأن upstreams الداخلية ثابتة
وقت تحميل إعداد edge. البوابة الخارجية تحل IP edge ديناميكيًا دون restart الخيمة.

Rollback أول تركيب: استعد ملفات/Compose البوابة الثلاثة الأصلية وصورتها من
baseline، وأعد إنشاء nginx وحده، verify الأربع HTTPS. سجّل أن سفاري سيصبح غير
متاح؛ لا DB restore ولا down-volumes. بعد نجاح التركيب استخدم maintenance503
لسفاري أو إصدار الصور السابق + edge recreate؛ حافظ على mount والشبكة في nginx.
