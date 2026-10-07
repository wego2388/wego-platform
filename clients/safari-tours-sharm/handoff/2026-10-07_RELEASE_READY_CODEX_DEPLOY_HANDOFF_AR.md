# Safari Tours Sharm — تسليم Claude إلى Codex حتى النشر

> 7 أكتوبر 2026 — تنفيذ محلي كامل، **لا نشر ولا دخول سيرفر ولا DNS ولا أسرار**.
> مكان العمل: `/home/wego/wego-safari-hardening`؛ الفرع `wego-016-safari-hardening`
> (مرفوع على `origin`). SHA التسليم: آخر commit على الفرع بعد هذا الملف
> (انظر §3). الحزمة النشطة الوحيدة في Board: **WEGO-016-OPS2-G**.
> هذا الملف يرد على طلب Codex في
> [`2026-10-07_CLAUDE_PREPARE_CODEX_DEPLOY_VPS_HANDOFF_AR.md`](2026-10-07_CLAUDE_PREPARE_CODEX_DEPLOY_VPS_HANDOFF_AR.md)
> ويحل محل وصف الحالة في تسليمات 5 أكتوبر عند التعارض.

## 1. الخلاصة لمحمد

كل الحزم المطلوبة قبل الإطلاق اتنفذت واتراجعت مراجعة مستقلة واتصلحت
ملاحظاتها: الحجز من المكتب والتحصيل بسعر صرف يومي، مستندات المكتب
(فاوتشر/إيصال/كشف تشغيل/كشف استلام/نموذج إلغاء)، الموردون والسواقين
والعربيات وتوزيع الرحلات، التكاليف والأرباح وحسابات الموردين والسواقين
بموافقة المالك، الخزنة اليومية، ورد الفلوس من المكتب. وجهزنا **حزمة نشر
كاملة** لسفاري لوحده على نفس السيرفر من غير ما نلمس الخيمة.

النشر نفسه **مش هيحصل غير لما تقول GO** وتدّي Codex المطلوب في §8.

## 2. علامات التسليم

| العلامة | معناها |
|---|---|
| `[x]` | نفّذ واختبر بالدليل المذكور |
| `[~]` | جزء منفذ؛ بوابة قبول أو تشغيل ناقصة |
| `[ ]` | مفتوح؛ لا تعتبره موجودًا |
| `[!]` | قرار/بيانات مالك أو بوابة إصدار |

## 3. المصدر والأدلة

| البند | القيمة |
|---|---|
| Branch | `wego-016-safari-hardening` على `origin` |
| آخر CI أخضر كامل | `f82c92b` — foundation-ci run `37645085034` (19 دقيقة، كل الـjobs بما فيها E2E enquiry/checkout/ERP locale) |
| commits بعده | follow-ups حزمة النشر (docs/config فقط) + هذا الملف؛ CI يعاد عليها تلقائيًا — Codex يتأكد إن آخر run على SHA الإصدار **success** قبل GO |
| Migrations | `1,2,3,14,16…33` (22 صف) — القائمة والغرض في [`RELEASE_READINESS.md`](../deployment/RELEASE_READINESS.md) §3 |
| وضع الإطلاق | `ENQUIRY_ONLY` + الحجز من المكتب؛ `TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=false`؛ Paymob فاضي |

### بوابات محلية على الشجرة النهائية للكود (7 أكتوبر)

- [x] `./gradlew ktlintFormat :platform:apps:safari-tours-sharm:check :platform:application:check ktlintCheck` — BUILD SUCCESSFUL؛ Safari 425 اختبار، application 327، صفر فشل/تخطي.
- [x] `cd web && pnpm run check` — exit 0 (ERP Vitest 329).
- [x] `bash scripts/safari-tours-sharm-check.sh` — exit 0 (يشمل foundry validate + repository-check + log privacy).
- [x] `bash scripts/repository-check.sh` + `git diff --check` — نظيف.
- [x] ERP operations locale E2E محليًا 54/54 بعد إصلاح fixture المكتب.
- [~] E2E الكامل على Compose جديد **لم يُعد محليًا** (المساحة ~3 GB)؛ يعتمد على CI أخضر على SHA الإصدار.

## 4. ما اتنفذ منذ تسليم 5 أكتوبر (كل حزمة Tier 1 بمراجعة Opus مستقلة)

| الحزمة | المحتوى | المراجعة |
|---|---|---|
| MEDIA | محرر المحتوى/الصور/الفئات، bundle backup + restore drill للقاعدة والصور | ACCEPT بعد follow-ups — COMPLETE |
| OPS2-C (V30) | حجز مكتبي بتأكيد فوري، تحصيل كاش/محفظة/فيزا/InstaPay/فوري، عربون، سعر صرف يومي يحدده المدير، عكس تحصيل | ACCEPT-WITH-FOLLOWUPS → مصلحة — COMPLETE |
| OPS2-D (V31) | فاوتشر، إيصال، كشف تشغيل، كشف استلام، نموذج إلغاء/رد؛ سجل طباعة append-only؛ حساب ساعات الإلغاء من ساعة القيام المعتمدة | مصلحة — COMPLETE |
| OPS2-E (V32) | موردون، سواقين، عربيات (فاضية لحد البيانات)، توزيع يومي بفحص التعارض، شيت السواق وأمر المورد | مصلحة — COMPLETE |
| OPS2-F (V33) | تكاليف لكل رحلة، ربحية، مستحقات موردين/سواقين، حد يومي 5000 ج للمدير لكل طرف وما فوقه بموافقة المالك، الموافق لا يدفع، الخزنة اليومية (قفل/تأكيد)، رد فلوس المكتب FIFO بأسعار التحصيل | مراجعتين: ACCEPT-WITH-FOLLOWUPS ثم re-check؛ M1–M3, L1–L2, F1–F5, F7 مصلحة؛ F6 residual مقبول |
| OPS2-G | حزمة النشر (§5)، تصحيح `X-Forwarded-Proto` في edge، هذا التسليم | مراجعة Opus مستقلة: ACCEPT-WITH-FOLLOWUPS؛ الملاحظات 1–8 مصلحة |
| الهوية | لوجو المالك (بدون سطر التليفونات) في الموقع والـERP والأيقونات | e2e axe/360px أخضر |

**Residuals مقبولة ومسجلة في Board:** F6 (سقف CHARGE لكل قيد 5000 ج وليس
تراكمي يومي لكل طرف — الكاش الخارج نفسه محدود 5000/يوم/طرف)؛ L3–L7 من مراجعة OPS2-F
(عرض: خزنة متوقعة بالسالب تظهر وقت العد، رصيد سالب عند الدفع الزائد،
الربحية تستخدم آخر سعر صرف لو يوم القيام بدون سعر — كلها ظاهرة ومعلّمة).

## 5. حزمة النشر — `clients/safari-tours-sharm/deployment/`

| الملف | الغرض |
|---|---|
| [`README.md`](../deployment/README.md) | فهرس |
| [`RELEASE_READINESS.md`](../deployment/RELEASE_READINESS.md) | manifest: SHA/الصور/migrations/الصلاحيات/جدول البوابات/المخاطر/بنود المالك |
| [`DEPLOY_RUNBOOK_AR.md`](../deployment/DEPLOY_RUNBOOK_AR.md) | الخطوات بالأوامر: preflight read-only → baseline الخيمة → الصور → env → تشغيل loopback → bootstrap admin → vhost/ACME/شهادة → تحقق خارجي → cron → drill → rollback → STOP |
| [`VERIFICATION_CHECKLIST.md`](../deployment/VERIFICATION_CHECKLIST.md) | smoke بدون تعديل بيانات + الخيمة قبل/بعد + فهرس الأدلة |
| [`.env.production.example`](../deployment/.env.production.example) | كل المتغيرات بقيم `CHANGE_ME_…` |
| [`nginx/`](../deployment/nginx/) | vhost لأسماء سفاري فقط (apex/www→301/staff noindex)، ACME bootstrap، صفحة صيانة 503 |
| `infrastructure/compose/safari-tours-sharm.production.yaml` | overlay: project `safari-tours-sharm-prod`، edge على `127.0.0.1:58080` فقط، بلا ports للقاعدة/التطبيقات، صور جاهزة بلا build، حدود موارد، log rotation |

**تم التحقق محليًا (بدون سيرفر):** compose config المحلول (المنفذ الوحيد
edge على loopback)؛ رفض البدء بدون الوضع والصور؛ `nginx -t` لكل القوالب؛
سلسلة gateway→edge→app في حاويات مؤقتة: رؤوس الزائر المزورة تُحذف، www→apex
301 بالمسار، ACME يشتغل، staff noindex، حدود الحجم 413، الصيانة 503.

**قرارات آمنة افتراضية بعد المراجعة:** أسطر IPv6 `[::]` معطلة إلا لو الخيمة
بالفعل تسمع على IPv6؛ HSTS بدون `includeSubDomains` لحد تأكيد المالك؛ drill
الاسترجاع المشفر **خارج السيرفر** (المفتاح الخاص مش على VPS)؛ reload الشهادة
بعد التجديد إلزامي وفي renewal conf الخاص بسفاري فقط؛ heap الجافا 60%.

## 6. ترتيب Codex الملزم (تفاصيله في الـrunbook)

1. [ ] راجع هذا الملف و`RELEASE_READINESS.md` والـrunbook؛ تأكد CI أخضر على SHA الإصدار؛ verdict **GO/NO-GO**.
2. [ ] preflight **read-only** على VPS (`187.6.167.233`): نوع البوابة (nginx على المضيف؟ ≥1.25.1)، IPv6، CDN، المنافذ الحرة، الموارد، Docker. لو البوابة في container أو Caddy/Traefik → **STOP** واسأل.
3. [ ] baseline الخيمة (HTTP/TLS/الحاويات) قبل أي تغيير.
4. [ ] بناء الصور خارج السيرفر من SHA الإصدار، نقلها (save/load أو registry)، تسجيل image IDs في الـmanifest.
5. [ ] `/srv/safari-tours-sharm/` + env بأسرار جديدة على السيرفر فقط؛ `up -d` على loopback؛ Flyway يطبق V1–V33 على قاعدة فاضية؛ bootstrap-admin مرة واحدة ثم إزالته.
6. [ ] smoke داخلي على loopback (VERIFICATION_CHECKLIST قسم A).
7. [ ] بعد GO المالك للتغيير المشترك: vhost سفاري + `nginx -t` + `systemctl reload nginx` (ليس restart)، certbot webroot للأسماء الثلاثة مع deploy-hook.
8. [ ] تحقق خارجي **بدون `curl -k`**؛ الخيمة قبل/بعد مطابقة؛ أي تأثير على الخيمة → ارجع إضافة سفاري فقط وبلّغ محمد.
9. [ ] cron للـbundle-backup وhealth-check؛ أول drill خارج السيرفر؛ تسليم release ID/digests/الأدلة.

**ممنوع:** prune عام، `down -v`، restart لـDocker أو للبوابة، `certbot --nginx`،
لمس ملفات/قاعدة/حاويات الخيمة، نسخ أسرارها، تفعيل الدفع الإلكتروني.

## 7. بيانات التشغيل الأولى بعد النشر (من الـERP، مش SQL)

- [x] الكتالوج: 30 رحلة معتمدة + مراجعة أسعار 30 سبتمبر داخل migrations (V17/V25).
- [ ] إنشاء الأدوار والموظفين؛ صلاحيات المال (`settlement:approve` للمالك فقط، `settlement:pay`، `cash-box:confirm`) بقرار محمد `[!]`.
- [ ] سعر صرف اليوم قبل أول تحصيل.
- [ ] الموردون/السواقين من `owner-data/safari-tours-owner-data-hub.xlsx`؛ التكاليف بـ`import_costs.py` (dry-run افتراضيًا: 57 تكلفة جاهزة، 19 خانة معلّمة للمراجعة) — يشغَّل على API الإنتاج فقط بعد مراجعة محمد للخانات المعلّمة `[!]`.
- [ ] العربيات: الهيكل جاهز، البيانات عند محمد.

## 8. المطلوب من محمد قبل/عند النشر — مش أسرار في الشات

- [!] **GO صريح** للنشر، وللتغيير المشترك المحدود في البوابة (vhost جديد + reload).
- [!] وصول آمن لـCodex على VPS + اسم دومين الخيمة للـbaseline.
- [!] قبول/رفض استثناءي التبعيات (`DEPENDENCY_AUDIT_EXEMPTIONS.md`: node-forge وbraces).
- [!] إيميل الشهادة؛ مكان backup خارج السيرفر؛ مفتاح GPG العام (الخاص يفضل معاك)؛ قناة التنبيهات.
- [!] SMTP وإيميل الحجوزات (الإشعارات مقفولة لحد ما يتوفر).
- [!] هل في subdomains قديمة http فقط (mail/webmail/cpanel)؟ لتحديد HSTS.
- [!] مين يكتب باسورد الأدمن الأول.
- [!] موافقة على enquiry تجريبي واحد بعد النشر.
- [!] مراجعة الافتراضات المعلّمة «محتاج مراجعة» في الإكسيل (قواعد التسوية، العمولات، الخزنة)، و19 خانة تكاليف، وربط سعر المورد بكل رحلة، وبيانات العربيات.
- [!] Paymob لاحقًا: حساب حقيقي + sandbox + مراجعة منفصلة قبل التحويل من ENQUIRY_ONLY.

## 9. STOP / NO-GO

أي واحدة من دول توقف النشر ويُسأل محمد: تغيير/وقف الخيمة مطلوب؛ البوابة
مش nginx على المضيف؛ هوية السيرفر أو 80/443 غير مؤكدة؛ الموارد لا تكفي؛
SHA الإصدار ≠ الصور/CI؛ CI مش أخضر؛ backup/drill فشل؛ الشهادة لا تطابق؛
استثناء تبعيات بلا إقرار؛ أسرار ناقصة؛ drift في migrations؛ DNS عبر CDN.

## 10. المرجع

- Board: `docs/execution/WEGO_EXECUTION_BOARD.md` (WEGO-016 → OPS2-G).
- التشغيل: `docs/operations/SAFARI_TOURS_SHARM_OPERATIONS.md` (backup/restore/upgrade/monitoring/§9 الإلغاء والرد).
- السكربتات: `scripts/safari-ops/` (`bundle-backup.sh`، `bundle-restore-drill.sh`، `verify-live-media.sh`، `health-check.sh`). القديمة `backup.sh`/`restore-drill.sh` ترفض V29+.
- بيانات المالك: `clients/safari-tours-sharm/owner-data/` (`PENDING_OWNER_ANSWERS.md` سجل القرارات).

**هذه الجلسة لم تنشر شيئًا.** Claude أنهى التجهيز؛ Codex يملك التنفيذ بعد GO.
