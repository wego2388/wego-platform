# Safari Tours Sharm — تكليف Claude بتنفيذ النشر (Deploy Agent Brief)

> 7 أكتوبر 2026. كاتبه: جلسة Claude التي بنت المشروع. القارئ: **جلسة Claude
> جديدة ستتولى النشر الفعلي** على VPS الإنتاج بطلب محمد.
> هذا الملف يحل محل «Codex ينفذ النشر» في تسليمات اليوم: **المنفذ الآن أنت (Claude)**؛
> كل الأدلة والقيود في ملفات Codex تظل سارية عليك بالحرف.

---

## 0. اقرأ هذا أولًا — في 60 ثانية

- **المالك:** محمد وجدي — ليس مبرمجًا. **كلّمه بالعربي المصري دائمًا**، باختصار
  ووضوح. لو احتجت منه يكتب أمرًا: أوامر قصيرة (< 70 حرف) سطرًا سطرًا، يكتبها
  بـ`! <command>` في Claude Code — الطرفية عنده تكسر الأسطر الطويلة.
- **المشروع جاهز كودًا:** كل الحزم مكتملة ومراجَعة، CI أخضر على `54b65a7`
  (run `37645085034` على `f82c92b` + run أخضر على `54b65a7`). فرع
  `wego-016-safari-hardening`، worktree `/home/wego/wego-safari-hardening`.
- **النشر ليس مفوّضًا بعد.** هذا الملف يجهزك؛ **لا تلمس السيرفر قبل أن يقول
  محمد صراحةً «GO للنشر» في جلستك**، ولا تفترض أن موافقة سابقة تنتقل لك.
- **أخطر شيء في المهمة: السيرفر نفسه عليه الخيمة (El Kheima / resort-os) في
  الإنتاج.** نجاح سفاري مع أي أذى للخيمة = فشل. القسم 3 يشرح لماذا هذا
  ليس نظريًا.

## 1. ترتيب القراءة (لا تتخطاه)

1. هذا الملف كاملًا.
2. [`2026-10-07_RELEASE_READY_CODEX_DEPLOY_HANDOFF_AR.md`](2026-10-07_RELEASE_READY_CODEX_DEPLOY_HANDOFF_AR.md) — ما اتنفذ، الأدلة، بنود المالك.
3. [`../deployment/RELEASE_READINESS.md`](../deployment/RELEASE_READINESS.md) — migrations/صور/صلاحيات/مخاطر.
4. [`../deployment/DEPLOY_RUNBOOK_AR.md`](../deployment/DEPLOY_RUNBOOK_AR.md) — الأوامر. **انتبه: مكتوب على افتراض nginx على المضيف — راجع القسم 3 هنا قبل تنفيذ §10 منه.**
5. [`../deployment/VERIFICATION_CHECKLIST.md`](../deployment/VERIFICATION_CHECKLIST.md).
6. [`2026-10-07_CLAUDE_PREPARE_CODEX_DEPLOY_VPS_HANDOFF_AR.md`](2026-10-07_CLAUDE_PREPARE_CODEX_DEPLOY_VPS_HANDOFF_AR.md) — قيود حماية الخيمة (§4) وشروط STOP (§9). **ملزمة لك.**
7. `docs/operations/SAFARI_TOURS_SHARM_OPERATIONS.md` (backup/restore/monitoring).
8. ذاكرة المستخدم: `reference_resort_os_vps.md` و`project_resort_os_governance.md`
   و`reference_docker_registry_mirror.md` (تحت `~/.claude/projects/-home-wego/memory/`).

## 2. الحقائق — ما ثبت وما لم يثبت

| | الحقيقة | المصدر |
|---|---|---|
| `[x]` | VPS الإنتاج `187.6.167.233`، SSH alias `resort-os-vps-next`، user `resortos`، sudo بدون باسورد | ذاكرة deploy حقيقي 2026-09-29 |
| `[x]` | DNS `safaritourssharm.com` و`www` و`staff` → `187.6.167.233` (لقطة 7 أكتوبر)؛ حاليًا يُخدم بشهادة/موقع الخيمة | فحص Codex |
| `[x]` | الخيمة: compose project `resort-os-prod` (9 حاويات)، releases في `/opt/resort-os-releases/<sha>/` و`/opt/resort-os-current` | ذاكرة |
| `[x]` | الرفع من جهاز التطوير بطيء **~180 KB/s** | ذاكرة deploy حقيقي |
| `[x]` | `docker.io` لا يعمل من جهاز التطوير — استخدم `public.ecr.aws/docker/library/*` | ذاكرة |
| `[~]` | **بوابة HTTPS = حاوية nginx الخاصة بالخيمة** تحجز 80/443 (`read_only: true`، conf ملفات مفردة مركّبة `:ro`، الشهادات من `/etc/letsencrypt` المضيف في overlay الدومين) | قراءة `~/projects/resort-os/docker-compose.prod*.yml` و`deploy/nginx/edge-domain.conf` محليًا — **تحقق على السيرفر** |
| `[ ]` | الموارد الحرة (RAM/CPU/disk) على VPS، نسخة Docker، وجود IPv6/AAAA | لم يُفحص |

## 3. الاكتشاف الحاسم — البوابة حاوية وليست nginx على المضيف

حزمة النشر وrunbook يفترضان nginx على المضيف (`/etc/nginx/conf.d` +
`systemctl reload nginx`) ويقولان **STOP لو البوابة في container**. الدليل
المحلي يقول إنها في container. إذن المسار المتوقع هو **STOP → عرض الخيارات
على محمد → تنفيذ المعتمد فقط**. لا ترتجل.

**لماذا لا يكفي «أضف ملف conf»:** الحاوية `read_only` وملفاتها مركّبة فرديًا؛
إضافة vhost تعني تعديل تعريف خدمة nginx للخيمة (mount إضافي) **وإعادة إنشاء
الحاوية = انقطاع ثوانٍ لكل مواقع الخيمة**. و`127.0.0.1:58080` على المضيف
**غير مرئي من داخل الحاوية**.

### الخيارات (اعرضها على محمد بالعربي البسيط مع توصيتك)

**(أ) الموصى به — سفاري خلف nginx الخيمة عبر شبكة Docker مخصصة:**
1. شبكة خارجية `safari-gateway` (`docker network create`).
2. edge سفاري ينضم لها (override صغير لـ`safari-tours-sharm.production.yaml`)،
   ويظل منفذه على المضيف `127.0.0.1` فقط للفحص الداخلي.
3. ملف compose override **منفصل** بجوار release الخيمة الحالي (لا تعدّل ملفات
   الخيمة الأصلية في git): يضيف لخدمة `nginx` الشبكة `safari-gateway` + mount
   `:ro` لملف `safari.conf` من `/srv/safari-tours-sharm/gateway/`.
4. `safari.conf` = قوالب `deployment/nginx/` بعد تعديل: `proxy_pass` إلى
   `http://<safari-edge-container>:8080` بدل `127.0.0.1:58080`، ومسار ACME
   والشهادات بنفس mounts الخيمة (`/var/www/certbot`، `/etc/letsencrypt`) —
   **lineage منفصل** `safaritourssharm.com`، لا تلمس شهادة `elkheima.com`.
5. `nginx -t` **داخل حاوية مؤقتة بنفس الصورة ونفس mounts** قبل أي تغيير حي.
6. إعادة إنشاء خدمة nginx للخيمة فقط (`up -d --no-deps nginx` بنفس ملفات
   الخيمة + override) في وقت هادئ بموافقة محمد؛ تحقق فورًا من الخيمة.
   - ثقة edge سفاري: الـpeer سيكون IP حاوية nginx على `safari-gateway` (خاص) → `X-Forwarded-Proto/For` موثوق — تحقق بالفعل.
   - **لاحظ حوكمة resort-os:** Claude مالك resort-os (ذاكرة governance). أي تغيير في طريقة تشغيل nginx الخيمة يُسجَّل في وثائق resort-os (DEPLOYMENT.md/override) حتى لا يمحوه deploy قادم للخيمة. نسّق: deploy الخيمة القادم يجب أن يحمل الـoverride وإلا يختفي سفاري.

**(ب) بديل — بوابة على المضيف أمام المشروعين:** أنظف معماريًا لكنه يغيّر
مسار كل زيارات الخيمة (نقل 80/443 من حاويتها) → انقطاع أطول ومخاطرة أعلى.
لا يُنفذ في نفس يوم إطلاق سفاري.

**(ج) مؤقت للتجربة — سفاري على IP:منفذ بدون دومين:** للـUAT فقط، بلا HTTPS
حقيقي؛ تذكّر درس `upgrade-insecure-requests` في ذاكرة VPS (CSP يكسر الأصول
على HTTP). ليس إطلاقًا.

## 4. خطة التنفيذ — بنقاط توقف إلزامية

كل ⛔ = توقف، اكتب لمحمد ملخصًا عربيًا قصيرًا، وانتظر رده.

1. **محلي:** تأكد أن `git status` نظيف، HEAD = SHA الإصدار، وCI أخضر عليه (`gh run list --branch wego-016-safari-hardening`). سجل `<final SHA>` في `RELEASE_READINESS.md`.
2. **⛔ اطلب من محمد** (القسم 6) GO + القرارات. لا أسرار في الشات.
3. **preflight read-only على VPS** (runbook §1 + هذا): `docker ps`، `docker compose -p resort-os-prod ps`، `docker inspect` لحاوية nginx (mounts/networks)، `nproc`/`free -h`/`df -h`، `ss -ltnp`، `docker version`، AAAA/IPv6، هل DNS عبر CDN. **لا تطبع env/أسرار.**
4. **baseline الخيمة** (runbook §2 + VERIFICATION_CHECKLIST قسم C): HTTP codes + الشهادة + هوية الحاويات وأوقات بدئها لـ`elkheima.com`، `www`، `app`، `owner`.
5. **⛔ تقرير preflight لمحمد + الخيار الموصى به (عادةً أ).**
6. **بناء الصور محليًا** من SHA الإصدار (backend عبر `safari-backend-from-jar.Dockerfile` بعد `bootJar` — بناء Gradle داخل Docker يفشل شبكيًا هنا؛ سجّل `JAR_SHA256`)، ERP وsite عبر Dockerfiles الخاصة. tags `safari-tours-sharm-{backend,erp,site}:<RID>`.
7. **النقل:** `docker save | gzip` لكل صورة، ثم `rsync --partial --append-verify` في حلقة إعادة محاولة (لا `scp` بمهلة) — توقع ساعة تقريبًا لـ~930 MB خام. تحقق `sha256sum` على الطرفين، ثم `docker load`.
8. **تشغيل سفاري على loopback** (runbook §4–§9): `/srv/safari-tours-sharm/{releases,shared,backups}`، env بأسرار جديدة يولّدها السيرفر (`openssl rand`)، postgres ثم backend (Flyway V1–V33 → 22 صف، 0 failed)، **bootstrap-admin تفاعلي: محمد يكتب الإيميل والباسورد بنفسه** (أعطه الأمر القصير بـ`!`، أو شغّله أنت عبر TTY وهو يكتب)، ثم بقية الخدمات، smoke قسم A (runbook §9).
9. **⛔ GO لتعديل بوابة الخيمة** (الخيار المعتمد) + موعد.
10. **البوابة والشهادة:** conf بوضع ACME-bootstrap أولًا → certbot webroot لـ`safaritourssharm.com,www,staff` (تحقق كيف تدير الخيمة certbot على السيرفر: حاوية؟ timer؟ واتبع نفس الأسلوب بـlineage منفصل + reload hook لحاوية nginx: `docker exec <nginx> nginx -s reload` بعد `nginx -t`) → conf الكامل → `nginx -t` → reload.
11. **تحقق خارجي بلا `-k`** (قسم B) + **الخيمة بعد = قبل** (قسم C). أي اختلاف في الخيمة → ارجع تغيير البوابة فورًا (override السابق + recreate) وأبلغ محمد.
12. **التشغيل الدوري:** cron لـ`bundle-backup.sh` و`health-check.sh` (المسارات في runbook §13)؛ أول drill **خارج السيرفر** (runbook §14.1)؛ off-server copy حسب قرار محمد.
13. **التسليم:** املأ `RELEASE_READINESS.md` (SHA، image IDs، جدول البوابات، verdicts)، سجّل في Board تحت OPS2-G (Status يبقى ACTIVE حتى قبول محمد ثم COMPLETE)، commit + push للفرع، وحدّث الذاكرة (`project_wego_platform.md` + reference VPS بقسم سفاري). تقرير عربي نهائي لمحمد بالروابط.

## 5. حدود لا تتجاوزها أبدًا

- لا `docker system/image/volume prune`، لا `down -v` لأي مشروع، لا restart لـDocker daemon.
- لا أي أمر compose على project `resort-os-prod` غير `ps`/`config`/`inspect` — **إلا** خطوة 6 من الخيار (أ) بعد GO وبنطاق `--no-deps nginx`.
- لا تعديل في git الخيمة على السيرفر، ولا قاعدة بياناتها، ولا شهادة `elkheima.com`، ولا نسخ أسرارها.
- لا `certbot --nginx`/`--standalone`، لا build ثقيل على السيرفر.
- لا تفعيل `ONLINE_PAYMENT` ولا Paymob؛ الإطلاق `ENQUIRY_ONLY` و`TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=false`.
- لا merge إلى `main`، لا DNS، لا إرسال أي شيء خارجي (إيميل/واتساب/enquiry تجريبي) بدون موافقة.
- لا أسرار في git أو الشات أو الذاكرة. الحساب التجريبي المحلي `owner@safari.local` **لا يُستخدم في الإنتاج**.
- قاعدة الفشل: لو خطوة فشلت مرتين بنفس الشكل، **قف واشرح**، لا تجرّب حلولًا عشوائية على سيرفر إنتاج.

## 6. ما تطلبه من محمد في البداية (رسالة واحدة منظمة)

1. GO للنشر + نافذة زمنية هادئة لإعادة إنشاء nginx الخيمة (ثوانٍ).
2. الموافقة على الخيار (أ) أو غيره بعد تقرير preflight.
3. إيميل شهادة Let's Encrypt.
4. قبول/رفض استثناءي التبعيات (`DEPENDENCY_AUDIT_EXEMPTIONS.md`: node-forge، braces) — اشرحهما بجملة لكل واحد.
5. مكان النسخ الاحتياطي خارج السيرفر + مفتاح GPG العام (الخاص يبقى معه).
6. قناة التنبيهات (إيميل/تليجرام/واتساب).
7. SMTP + إيميل الحجوزات — أو الإطلاق والإشعارات مقفولة (`TOURS_OPERATOR_NOTIFICATIONS_ENABLED=false`).
8. هل توجد subdomains قديمة HTTP فقط (mail/webmail/cpanel)؟ (HSTS بدون includeSubDomains حتى يرد).
9. هو من يكتب باسورد الأدمن الأول.
10. موافقة على enquiry تجريبي واحد بعد النشر (اختياري).

البنود 3–8 ليست كلها مانعة: 3 مانع للـHTTPS؛ 4 مانع (قاعدة الإصدار)؛ 5–6
يمكن الإطلاق بدونهما **فقط** لو وافق محمد كتابةً على مخاطرة «نسخ على نفس
السيرفر مؤقتًا»؛ 7 غير مانع.

## 7. تعريف النجاح

- `https://safaritourssharm.com` (و`/ar`، `/en`، `/ru`، `/it`) بشهادة سفاري الصحيحة؛ `www` → 301 للـapex؛ `https://staff.safaritourssharm.com` يفتح دخول الـERP وnoindex.
- محمد دخل الـERP بحسابه، وأنشأ سعر صرف اليوم، والكتالوج (30 رحلة) ظاهر.
- الخيمة: نفس الأكواد والشهادات والحاويات قبل/بعد (عدا nginx المعاد إنشاؤه في الخيار أ).
- backup أول ناجح + health-check أخضر (WARN الدفع المتوقع في ENQUIRY_ONLY فقط).
- `RELEASE_READINESS.md` مملوء ومرفوع، Board محدث، تقرير عربي لمحمد.

## 8. ما بعد النشر (اذكره لمحمد، لا تنفذه بدون طلب)

إنشاء الموظفين والأدوار؛ مواعيد الرحلات الحقيقية؛ استيراد الموردين/السواقين/
التكاليف من `owner-data/` بعد مراجعته للخانات المعلّمة (`import_costs.py`
dry-run افتراضيًا)؛ بيانات العربيات؛ Paymob لاحقًا بحزمة ومراجعة منفصلة؛
تحسين ملفات Viator/Tripadvisor بعد الإطلاق.
