# Safari Tours Sharm — فحص VPS فعلي قبل النشر

> 8 أكتوبر 2026 بتوقيت القاهرة. المنفذ: Codex.  
> تفويض محمد: «نفذ الديبلوي بنجاح علي الفي بي اس ولك كل الاكسيس والصلاحيات».  
> **النتيجة: وصول ناجح وفحص قراءة فقط؛ لا نشر ولا تغيير حي.** شرط حماية
> الخيمة السابق يظل قائمًا؛ التغيير المحدد لبوابتها يحتاج اعتمادًا مستقلًا.

## المصدر والتنسيق

- SHA المقروء: `198959efdd3754fffc9c357e555f25a014569b46`، فرع
  `wego-016-safari-hardening`؛ الشجرة كانت نظيفة قبل إضافة هذا السجل.
- جلسة Claude جديدة مرئية بعنوان `Safari Deploy — Wagdio` بدأت في مجلد
  المشروع واستلمت التكليف وبيانات الأدمن؛ قرأت المراجع وعرضت بنود المالك.
  سجلها يذكر أنها لم تدخل السيرفر، ولم تتلق GO منفصلًا في جلستها عند الفحص.
- لا تنفذ عمليتي نشر بالتوازي: عيّن منفذًا واحدًا بعد اعتماد خطة البوابة.
- CI للإصدار `198959e`: run `37685772250` كان **in_progress** وقت القراءة؛
  backend/infrastructure لم يكتملَا. نجاح `54b65a7` السابق ليس ادعاء نجاح هذا run.

## أدلة القراءة الفعلية

SSH عبر alias `resort-os-vps-next`، BatchMode، بدون TTY، مع
`StrictHostKeyChecking=yes`. لا تعطيل للتحقق من المفتاح ولا كشف credentials.

| البند | النتيجة |
|---|---|
| المضيف/العنوان/المستخدم | `srv2006897` / `187.6.167.233` / `resortos` |
| معمارية / CPU | x86_64 / 2 CPUs |
| RAM | total 7935 MiB، available 5629 MiB؛ swap 2047 MiB غير مستخدمة |
| القرص | `/` و`/srv`: نحو 83G free، استخدام 14% |
| Docker / Compose | 29.8.1 / v5.5.1 |
| تثبيت Safari الإنتاجي | لم تظهر حاويات باسم project `safari-tours-sharm-prod` ولا volumes في فلتر `safari-tours-sharm`؛ راجع المسارات أيضًا قبل أي تثبيت |
| 80/443 | منشورة بواسطة **resort-os-prod-nginx-1** على IPv4 وIPv6 |
| host nginx | `command -v nginx` لم يجد executable؛ هذا سبب exit 1 للأمر المجمع الأول، وليس فشل SSH |
| حاوية البوابة | readonly root، شبكة واحدة `resort-os-prod_default` |
| config mounts | `default.conf` و`owner.conf` مركبان كملفين منفردين `:ro` من release الخيمة؛ مجلدا `/etc/letsencrypt` و`/var/www/certbot` مركبان `:ro` |
| active gateway hosts | `elkheima.com` / `www.elkheima.com` / `app.elkheima.com` / `owner.elkheima.com`؛ لا vhost Safari ظاهر في فلتر أسماء السيرفر |
| baseline الخيمة الخارجي | طلب HTTPS صارم إلى `elkheima.com` على IP الهدف: **200، ssl_verify_result=0** |

مصدر mounts/config labels المقروء:
`/opt/resort-os-releases/4983a7b0d53bfbcf905775b8278ee994900f1be3`.
لم نعدل ملفات ذلك الإصدار ولم نطبع `.env` أو مفاتيح أو كامل configs.

Baseline مختصر للـStartedAt/RestartCount:

| الخدمة | StartedAt | RestartCount |
|---|---|---|
| nginx | 2026-10-07T14:42:00.713795811Z | 0 |
| backend | 2026-10-07T14:38:18.559321357Z | 0 |
| db_postgres | 2026-09-25T16:14:02.690269088Z | 0 |

لم تُنفذ baseline الكاملة للأسماء الأربعة أو fingerprint certificate بعد؛
هذا الفحص الأول لا يحل محل checklist C قبل/بعد أي تغيير معتمد.

## قرار التوقف المحدد

- [x] الوصول وهوية الخدمات والطوبولوجيا تأكدت قراءةً؛ الموارد تبدو مناسبة
  مبدئيًا، وليست إثبات تحميل/أداء أو resource budget نهائيًا.
- [!] بوابة HTTPS **حاوية الخيمة** لا host nginx؛ أوامر host runbook لا تنطبق.
- [!] خيار الشبكة المخصصة + mount إضافي يحتاج تعديل تعريف تشغيل بوابة
  الخيمة وإعادة إنشاء حاوية nginx فقط. قد يقطع الخدمة مؤقتًا؛ ليس reload
  ولا مضمونًا أنه بلا انقطاع. لا ينفذ بموجب موافقة عامة مع شرط عدم المساس بالخيمة.
- [ ] المطلوب من محمد الآن: قبول/رفض هذا التغيير المحدد ونافذة تنفيذه بعد
  اختبار config وrollback؛ لا تعديل لقاعدة/تطبيقات/شهادة الخيمة.
- [ ] قبل النشر أيضًا: CI النهائي، إصدار/صور متطابقة، إقرارات audit،
  bootstrap الآمن، certificate/renewal plan وbackup/drill وفق ملف الجاهزية.
- [x] لا network/volume/container/ملف/شهادة/cron أنشئ أو عدل على VPS؛ لا
  restart/recreate/reload، ولا رفع images ولا تشغيل Safari في هذه الجلسة.

**NEXT:** انتظار اعتماد topology change. إذا رفض محمد أي تغيير لبوابة
الخيمة، لا نرتجل على خدماتها؛ نعرض حل نشر مستقل بديل وتكلفته قبل التنفيذ.
OPS2-G تظل ACTIVE؛ لا COMPLETE ولا إعلان نجاح deploy.
