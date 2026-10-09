# إصلاح رابط الموظفين — تسليم Codex إلى Claude، 9 أكتوبر 2026

## النتيجة الحالية

- [x] عطل `NET::ERR_CERT_COMMON_NAME_INVALID` أُصلح فعليًا نحو00:41UTC
  (03:41 صباحًا بتوقيت القاهرة). رابط الموظفين:
  <https://staff.safaritourssharm.com/login>.
- [x] الموقع EN/AR والدخول200/TLS0، وwww301 إلى الدومين الأساسي بشهادة سليمة.
  صفحة دخول سفاري ظهرت في Chrome المالك دون تجاوز تحذير أمان؛ لم تُدخل كلمة مرور.
- [x] الأربعة `elkheima.com`/`www`/`app`/`owner` تعمل200/TLS0 بشهادة الخيمة
  الأصلية نفسها. الإصدار الأحدث للخيمة محفوظ، لا rollback للتطبيقات أو البيانات.
- [x] ثماني خدمات الخيمة غير nginx وخمس خدمات سفاري لم تتغير:13 container
  IDs/images/started-at/restart-counts/health متطابقة قبل الإصلاح وبعده.
- [x] فحص الصحة الفعلي `safari-health.service` نجح00:43UTC، ومؤقتا الصحة
  والنسخ نشطان. بوابات API الخاصة محفوظة: public identity/staff404، staff401
  بدون دخول، وpublic `/login`404.
- [ ] منع التكرار **في منتج إصدار الخيمة نفسه** لم يكتمل: يلزم تبني العقد
  المراجع في الفرع الذي ينتج إصدار الخيمة القادم، وليس فقط وجوده على VPS.

## السبب المثبت — ليس DNS ولا نقص بيانات الرحلات

بعد تسليم8 أكتوبر، نُشر إصدار الخيمة الأحدث
`dc266f8ec604d8effab32e382a03e4d4143856df`، وأُعيد إنشاء nginx
في19:41:44UTC. الحاوية الجديدة فقدت مجلد سفاري وloader والشبكة الخارجية؛
أصبحت تقدم شهادة `elkheima.com` لاسم `staff.safaritourssharm.com`.
ثبت الخطأ من جهاز المالك ومن VPS نفسه بفحص TLS صارم. DNS كان بالفعل يشير
إلى187.6.167.233؛ شهادتا المشروعين صالحتان وedge سفاري سليم.

الـwrapper والـguard والـoverlay والـmarker المثبتة على المضيف لم تتغير.
لكن bundle الخيمة الجديد لا يحتوي `scripts/resort_compose.sh` أو
`scripts/verify_shared_gateway.py`، وrelease verifier فيه لا يطبق gate العقد.
إذن سير الإصدار الجديد لم يتبنَّ العقد؛ لا نزعم أن wrapper اختُرق أو أن مجرد
إعادة الربط تمنع استخدام raw Compose لاحقًا.

## ما نفذه Codex فعلًا

1. حفظ هوية الإصدار والبوابة والشهادة وbaseline الخدمات13؛ لا env dump.
2. مراجعة Tier-1 مستقلة `shared_gateway_tier1_review`: READY، صفر blockers.
3. `resort-compose --release <CURRENT-dc266> config --quiet` ثم حاوية nginx
   مؤقتة بالصورة الحالية والإعداد الكامل: `run --rm --no-deps --pull never
   nginx nginx -t` نجح؛ لا service-ports أو استبدال تطبيقات أثناء الاختبار.
4. قفل إصلاح محدود وCAS لهوية current/nginx/baseline، ثم الأمر التالي **nginx فقط**:

```bash
sudo /usr/local/sbin/resort-compose \
  --release /opt/resort-os-releases/dc266f8ec604d8effab32e382a03e4d4143856df \
  up -d --no-deps --no-build --pull never --force-recreate nginx
```

5. تحقق nginx-t والمجلدين read-only والشبكتين، TLS صارم لكل الأسماء، مطابقة
   الخدمات المحمية، عرض Chrome، والصحة الفعلية. لا claim zero-downtime؛ بوابة
   single-instance استُبدلت لفترة قصيرة. القفل لا يلزم منفذًا يتجاهله.

المراجعة المستقلة بعد التنفيذ: **Tier-1 ACCEPT، صفر blockers للإصلاح**،
مع فحص حي مستقل لأسماء الطرفين ومطابقة13 خدمة. الاعتماد في منتج الإصدار
القادم يبقى finding منفصلًا OPEN، لا شرطًا أُنجز ضمن هذا الإصلاح.

الحاوية الجديدة `dee0779398da89a3f0587b85fbeccbd004e57ba8a84faeffc5f52faccb6a765f`،
بنفس صورة nginx:
`sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c`.
أصل config الخيمة هو **dc266**، لا4983 القديم. network membership:
`resort-os-prod_default` + `safari-gateway`؛ على الشبكة المشتركة nginx وSafari edge فقط.

Safari current بقي `str-2026.10.08-0004800`؛ مصدر الواجهتين0004800،
backend198959e. لا بناء صور أو نشر تطبيق أو migration أو تعديل auth/payment/
booking أو DNS أو إصدار شهادة أو إدخال بيانات تشغيلية في هذا الإصلاح.

## المطلوب من Claude قبل أي نشر جديد للخيمة

- [ ] اقرأ تسليم العقد في worktree
  `/home/wego/projects/resort-os-shared-gateway/docs/agent-workflow/handoffs/2026-10-08_SHARED-GATEWAY_codex-to-claude.md`.
  commit العقد المحدد `7fe00609` على `codex/shared-gateway-20261008` مرجع
  للمراجعة؛ لا دمج الفرع كله أو رجوع تطبيقات الخيمة إلى4983 تلقائيًا.
- [ ] تبنَّ scoped deployment files في **فرع تنفيذ الخيمة الحالي** مع الحفاظ
  على عمله الأحدث: wrapper/guard/tests، bundle builder/verifier، ومسار
  `DEPLOYMENT.md` الذي يستعمل wrapper. مستقلة عن تغييرات JWT أو fixtures الأخرى.
- [ ] اجعل وجود marker **أو** wrapper **أو** overlay يُلزم preservation gate؛
  missing overlay يجب أن يوقف release acceptance، لا fallback إلى raw Compose.
- [ ] أثبت أن bundle الناتج نفسه يشحن العقد وأن verifier يطبقه **قبل** قبول
  الصور، وأن المنفذ الفعلي يستعمل `/usr/local/sbin/resort-compose --release ...`.
  اختبارات العقود/CI والمراجعة المستقلة مطلوبة، لا مجرد تعديل النص.
- [ ] كل upgrade/rollback للخيمة يحفظ bindings والشبكة وشهادتي المشروعين.
  بعده افحص3 Safari +4 Resort origins وشهاداتها، بجانب صحة التطبيق الجاري تحديثه.

الأمر المسجل أعلاه دليل لحادث محدد وليس إذنًا لإعادة نشر dc266 بعد تقدم current.
لأي عملية مستقبلية احسم هوية `/opt/resort-os-current` وصورة nginx وconfig
وباقي الخدمات أولًا. لا first-install أو raw Compose أو host nginx أو حذف
network/overlay/cert أو `down -v` أو DB restore. إذا فشل preflight لا تمس الحي؛
بعد mutation أعد اختبار نفس الإعداد المراجع/إصلاح nginx وحده تحت القفل، ولا
توسّع الرجوع إلى تطبيق أو قاعدة أحد المشروعين.

## الأدلة والحالة الإدارية

الأدلة الخاصة المحلية:
`/home/wego/safari-gateway-recovery-20261009.AZGiPm/`، mode0700:
`current-release-nginx-preflight.log`، `restoration.log`،
`protected-before.txt`/`protected-after.txt`، `certificates-after.txt`،
`strict-tls-and-boundaries.txt`، `health-after.txt`.
baseline المطابقSHA256:
`5bdd517c0d92f2fa2bf5636d636cec2f7c0dc2a61a231fcf4fa6a638968785b1`.
لا مفاتيح خاصة أو passwords أو production env في التسليم أو الأدلة المعروضة.

OPS2-G هو الباكيت ACTIVE الوحيد؛ هذا إصلاح حادث تشغيل ضمنه، لا باكيت جديد.
بوابة repository و`git diff --check` نجحتا؛ لا full app build/CI جديد لأن
صور التطبيقات ومصادرها لم تتغير. تغييرات التوثيق المحددة فقط على
`wego-016-safari-hardening`، ضمن إذن المالك للحفظ والرفع؛ لا main merge أو
تعديل worktree الخيمة الجاري. هوية صور التشغيل لا تتبع commit التوثيق.
اعتماد منتج إصدار الخيمة يبقى OPEN بمالك تنفيذ Claude حتى يثبت على مصدره
وحزمته ومنفذه؛ لا نعلن منع كل تعارض مستقبلًا بمجرد إصلاح رابط اليوم.
