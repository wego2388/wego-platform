# سفاري تورز شرم — تسليم Claude إلى Codex: الحالة الحية والمتبقي

> 25 أكتوبر 2026 · من Claude (المنفّذ الوحيد للنشر في هذه الجولة) إلى Codex.
> `WEGO-016-OPS2-G` ما زالت الحزمة الوحيدة ACTIVE. هذا الملف **لقطة حالة موثقة**
> وليس تفويضًا جديدًا. عند التعارض: طلب المالك الأحدث ← Board ← هذا الملف.
> العلامات: `[x]` منشور ومتحقَّق منه حيًا، `[c]` committed ومختبَر لكن غير منشور،
> `[~]` جارٍ محليًا، `[ ]` متبقٍ، `[!]` يحتاج المالك.

## 0. اقرأ هذا في دقيقة

- **الإنتاج الآن (VPS `187.6.167.233`، مشروع `safari-tours-sharm-prod`):**
  backend/site/ERP = `str-2026.10.25-a728327-fin`، edge = إعداد `e3559bc`
  (`current → releases/str-2026.10.25-e3559bc-edge`)، Flyway **36**، `ENQUIRY_ONLY`،
  الإشعارات مفعّلة (Gmail)، GA4 `G-3JZT9JZRQE` مفعّل بالموافقة، Meta Pixel **مقفول**.
- **الخيمة (resort-os-prod):** لم تُلمس في أي نشر؛ كل نشر قارن هوية حاوياتها قبل/بعد ← متطابقة.
- **إصدار جاهز لم يُنشر:** `str-2026.10.25-df846c1-sec` (تقارير مالية Slice B + إصلاحات أمان
  L1/L2/L4/L5/M2/M3 + توحيد Vue). الصور محمّلة على VPS في `incoming/`، وE2E المعزول عليها
  **نجح بالكامل** (§3). Claude سيتابع نشره ما لم يُسلَّم النشر لـ Codex؛ راجع Board لمعرفة آخر حالة.
- **جارٍ محليًا بلا commit:** V37 بيانات الكتالوج من إجابات المالك (§4). لا تنشره قبل
  مراجعة Tier1 واختبار على نسخة إنتاج مستعادة.
- **الفرع:** `wego-016-safari-hardening` في `/home/wego/wego-safari-hardening`، HEAD `02fa67f`
  (مرفوع). الشجرة قد تحتوي تغييرات V37 غير محفوظة من agent — لا تستخدم reset/clean.

## 1. ما نُشر اليوم بالترتيب (كله `[x]` ومتحقَّق منه حيًا)

| الإصدار | المحتوى | المراجعة | الدليل |
|---|---|---|---|
| `str-2026.10.25-b98aa55` | V36 أسعار/صور المالك + بحث «حجزي» بمرجع STQ + ترقيعات تبعيات | Tier1 READY (سابق) + V36 على نسخة pg_dump إنتاج | `deployment/2026-10-25_CATALOG_STQ_RELEASE_MANIFEST.json` |
| `str-2026.10.25-50e35ea-mail` | بريد إلغاء لحجوزات OFFICE التي وصلها تأكيد | Opus Tier1 READY (N1–N4) | Board 25 أكتوبر |
| GA4 (إعداد فقط) | `WEGO_SITE_GA4_ID` + إعادة إنشاء site/edge | اختبار متصفح: لا طلبات قبل الموافقة/بعد الرفض، السحب يوقف فورًا | Board |
| `str-2026.10.25-a728327-fin` | SAF-04 Slice A (ضوابط مالية) + SEO/كاش الموقع | Opus Tier1 READY (F1–F5) | `deployment/2026-10-25_FINANCE_SEO_RELEASE_MANIFEST.json` |
| `str-2026.10.25-e3559bc-edge` | إصلاح أمني H1: نطاق staff لا يمرّر نقاط الكتابة العامة | نتيجة مراجعة أمنية مستقلة | Board + `handoff/2026-10-25_SECURITY_REVIEW.md` |

نتائج حية مهمة: 27 رحلة نشطة؛ الأسعار مطابقة (كواد 15، بجي 35/وحدة، عشاء بدوي 25،
بحرية مسائية 30، دهب 30، هاي إس 30، سيدان 15، SUV 20 **ما زال موجودًا**، غواصة 35،
زجاجي 15)؛ `POST` العامة على staff ← 404؛ health-check OK ما عدا WARN عمر الـ drill.

## 2. كيف ننشر هنا (الطريقة المثبتة اليوم — التزم بها)

1. **CI:** وظائف الكود (repository/contracts/backend/web/mobile/secrets) يجب أن تكون خضراء.
   وظائف `infrastructure` و`safari-container-gateway` **تفشل حاليًا بسبب حصة public ECR**
   (`toomanyrequests: Data limit exceeded`) وليس الكود. التعويض المعتمد: E2E معزول محليًا على
   **نفس الصور** (§2.3). لا تغيّر مصدر الصور في CI لتمرير الفحص دون مراجعة.
2. **البناء خارج VPS:** backend عبر `bootJar` ثم `infrastructure/docker/safari-backend-from-jar.Dockerfile`؛
   site/ERP عبر `pnpm build` على المضيف ثم `safari-nuxt-from-output.Dockerfile`
   (npm داخل Docker يتعطل شبكيًا على هذا الجهاز). سجّل `JAR_SHA256` و`OUTPUT_SHA256`.
3. **E2E المعزول (إلزامي قبل أي نشر):** ملف override محلي يشير للصور الجديدة + ملف hardening
   (cap_drop/no-new-privileges) ثم:
   - مشروع `ci-safari-enquiry` (منفذ 58087): `safari-enquiry.spec.ts` (44) + حارس سجل الدفع
     `e2e/verify-safari-enquiry-history-startup.mjs` + `safari-online-requests.spec.ts` (8).
   - مشروع `safari-ops-e2e` (منفذ 58090، `compose.safari-checkout.yaml`): checkout 13، site 11،
     launch 5، office-inventory 5، operations-cycle 1، document-responsive 12، وفحص خلو السجلات من PII.
   - **درس اليوم:** ترقية Nuxt جلبت Vue مزدوج (3.5.43/3.5.44) — البناء واختبارات الوحدة نجحت
     لكن SSR أعطى 500 في كل صفحة. E2E المعزول هو من كشفه. أضيف اختبار
     `web/apps/safari-tours-sharm-site/test/vue-single-version.spec.ts`. لا تتخطَّ E2E أبدًا.
4. **النقل:** `docker save | gzip` → `rsync --partial --append-verify` إلى
   `/srv/safari-tours-sharm/incoming/<rid>/` → `sha256sum -c` → `docker load` → قارن image IDs.
5. **نسخة قبل النشر:** `sudo systemctl start safari-backup.service` ثم انسخ أحدث bundle إلى
   `/home/wego/Backups/safari-tours-sharm/` وتحقق من sha256 في manifest.
6. **النشر:** مجلد إصدار جديد (نسخة من الحالي + ملفات الإصدار)، `deploy.sh <rid>` داخل المجلد:
   يأخذ قفل `shared/.deploy.lock`، يحفظ `.env.production.before-<rid>`، يبدّل
   `SAFARI_*_IMAGE`، ثم `DC up --no-deps --wait` خدمة خدمة، ثم edge `--force-recreate`، ويقارن
   حاويات الخيمة قبل/بعد. `DC` = base + production + gateway compose بنفس ملف env.
7. **بعد النشر:** `current` رابط **مطلق** للإصدار الجديد، `safari-health.service`، فحوص حية
   (أسعار API، الصفحات الأربع، staff 404/401، الخيمة 200)، manifest في `deployment/` + Board.
8. **الرجوع:** `rollback.sh <rid>` في مجلد الإصدار: يعيد env السابق وصور الإصدار السابق.
   لا migration جديدة منذ V36 (V36 بيانات فقط والباك إند القديم يتجاهل الهجرات المستقبلية).
   **لا تُرجع backend أقدم من V35.**

ممنوعات ثابتة: لا `docker prune` عام، لا `down -v` على الإنتاج، لا لمس حاويات/ملفات الخيمة،
لا تفعيل Paymob أو Meta Pixel، لا أسرار في git/الشات.

## 3. الإصدار الجاهز غير المنشور: `str-2026.10.25-df846c1-sec` `[c]`

**المصدر:** HEAD عند البناء `df846c1` (الكود = `a546ad8`؛ بعده commits وثائق فقط).
**الصور على VPS (incoming):** backend `sha256:30598f02…`، site `sha256:67365a8d…`، ERP `sha256:aec2e495…`؛ jar `6c8db5da…`.

| البند | الحالة |
|---|---|
| SAF-04 Slice B تقارير قراءة فقط + CSV (`70c5534`) | Tier2، 6 اختبارات PostgreSQL + 10 ERP |
| L4 مرجع STQ يولّده الخادم، L5 حذف سبب الإلغاء من البحث العام، M2 لا تحليلات على `/booking/*` (`7c29e01`) | Opus Tier1 READY |
| L1 بث `pg_dump | gpg` بلا ملف نصي (`5fb5ff3`) | READY — **انظر ملاحظة التفعيل أدناه** |
| L2 `no-new-privileges` + `cap_drop: ALL` (postgres يحتفظ بـ CHOWN/DAC_OVERRIDE/FOWNER/SETGID/SETUID) (`9a0d267`) | مثبت محليًا: postgres init+restart، والـ stack كله صحي |
| M3 Nuxt 4.6.2 + توحيد Vue 3.5.44 (`1834479`, `a546ad8`) | web check 997 اختبارًا |
| بوابات | Gradle 480/0، web 997، repository-check |

**قبل نشره:**
- [x] E2E المعزول على صور `df846c1-sec` مع ملف hardening: enquiry 44، حارس الدفع PASS، requests 8،
      checkout 13، site 11، launch 5، office 5، cycle 1، documents 12؛ السجلات بلا PII. (المحاولة الأولى
      على `7c29e01` فشلت بسبب Vue المزدوج وحُذفت صورها من VPS.)
- [ ] `deploy.sh` لهذا الإصدار **يعيد إنشاء postgres أولًا** (L2) ثم backend/site/web/edge.
      يوجد `deploy5.sh`/`rollback5.sh` محليًا في `$CLAUDE_JOB_DIR/tmp/rel/` — راجعهما أو أعد كتابتهما
      وفق §2.6؛ rollback يعيد postgres أيضًا بإعدادات الإصدار السابق.
- [ ] **تفعيل L1 فعليًا:** خدمة `safari-backup.service` لها drop-in يشغّل
      `/srv/safari-tours-sharm/shared/ops-backup-inventory-6db0d43/bundle-backup.sh` وليس نسخة `current`.
      نشر الإصدار وحده **لا يفعّل** البث المشفر. المطلوب: نسخ `bundle-backup.sh` + `lib.sh`
      الجديدين إلى مجلد shared جديد، تعديل الـ drop-in، `daemon-reload`، تشغيل الخدمة مرة
      والتحقق من bundle صالح. سجّل ذلك كتغيير تشغيلي منفصل.

## 4. V37 بيانات الكتالوج من إجابات المالك `[c]` — commit `0c5680f`، غير منشور، مراجعة Tier1 جارية

> تحديث: V37 committed (481 اختبار backend أخضر). نقاط تحتاج تأكيد المالك قبل النشر: سانت كاترين + دهب
> صار «عشاء» بينما الوصف يقول غدا في دهب؛ القاهرة بالطيارة ما زال نصها «مرشد يتحدث الإنجليزية»
> بينما لغات المرشد en/it/ru؛ نقل المطار استُثني من بنود «كل الرحلات» (لا مرشد/تصوير). خيار SUV
> يُحذف من الجدول (لا يوجد علم تعطيل) ويُجهض الترحيل إن وُجد طلب مفتوح أو حجز قادم يستخدمه.

المصدر الوحيد للحقائق: آخر ثلاث فقرات بتاريخ 25 أكتوبر في
`clients/safari-tours-sharm/owner-data/PENDING_OWNER_ANSWERS.md`.

مطبَّق في V37 (agent يعمل عليه، غير committed وقت كتابة الملف):
- غير شامل في كل الرحلات: التصوير، الشال، النظارة الشمسية.
- وجبات: سوبر سفاري عشاء؛ العشاء البدوي عشاء؛ كانيون دهب غدا؛ البحرية المسائية عشاء؛
  القاهرة أتوبيس غدا؛ القاهرة طيران غدا؛ سانت كاترين + دهب عشاء.
- مرشد بالإنجليزية والإيطالية والروسية في كل الرحلات.
- تشغيل يومي لـ 19 رحلة؛ الحجز قبلها بيوم للسبيد بوت وكل الرحلات البحرية؛ عرض الدولفين 14:30؛
  ميعاد الاستلام يُبلَّغ على واتساب (بلا أوقات مخترعة).
- **إزالة خيار SUV** من نقل المطار (تعطيل/حذف مشروط بعدم وجود مرجع في حجوزات/طلبات).

المطلوب قبل نشره: commit منفصل، مراجعة Tier1 مستقلة (ترحيل بيانات تجارية)، اختبار V36→V37 على
pg_dump إنتاج مستعاد في حاوية معزولة (الطريقة في Board/manifest V36)، تحديث تغذية Meta بعد النشر.

**غير منفَّذ عمدًا — سياسة الأطفال (Tier1 مال):** المالك قرر لكل الرحلات: من 1 إلى 4.5 سنة مجاني،
ومن 5 إلى 9 نص سعر الفرد. النظام الحالي لا يحتوي سعر طفل (`price_child_cents` فارغ) ولا فئات عمر؛
التنفيذ يحتاج نموذج فئات عمر في نموذج الطلب/الحجز، حساب السعر، ERP، البريد، المستندات، وربما
migration. تقرير التحليل سيرد في تقرير agent V37؛ نفّذه كحزمة Tier1 مستقلة.

## 5. المتبقي من الخطة (مرتب بالأولوية)

1. [ ] نشر `df846c1-sec` (§3) ثم تفعيل L1.
2. [ ] V37 + سياسة الأطفال (§4) + إعادة توليد تغذية Meta.
3. [ ] SAF-04 Slice C — تثبيت تكلفة الرحلة/الشهر (Tier1 + migration) — ينتظر قرارات المالك في
      `handoff/2026-10-25_SAF04_FINANCE_DECISION_MEMO.md` (من يقفل اليوم/الشهر، أساس الربح…).
4. [ ] متابعات أمنية: M1 سقف المقاعد المعلّقة (لازم قبل Paymob فقط)، L3 أرقام STR تسلسلية،
      L6 CSP `unsafe-inline`، L7 فصل `cost:manage` عن `settlement:pay` في الأدوار، تتبع vite (peer warning).
5. [ ] ملاحظات الأداء من `marketing/SITE_AUDIT_2026-10-25.md`: غلاف super-safari PNG 2.2MB بـ `no-store`
      (أصل من ERP — إعادة رفع JPEG/WebP)، كاش `/media/**`، TBT.
6. [ ] SAF-03.06/SAF-10.08 قبول تشغيلي على حجز حقيقي مع المالك/موظفه — لا تُغلق OPS2-G قبله.
7. [ ] نشر صور المكتبة المختارة في سجل وسائط ERP (المالك وافق «انشرها»): يحتاج جلسة موظف
      مصرّحة في الداشبورد؛ الموقع يعرضها بالفعل من حزمة الإصدار، والفائدة أساسًا للـ API/Meta.

## 6. المطلوب من المالك `[!]`

- الغطس: مستوى الشهادة، الفحص الطبي، عدد الأيام/الغطسات؛ بقشيش كابتن السبيد بوت؛ وقت الانتظار
  المجاني لنقل المطار؛ تأكيد أن استلام القاهرة بالطيارة «يُبلَّغ على واتساب».
- سن 4.5: هل الطفل 4 سنوات ونصف تمامًا مجاني أم نصف؟ وهل الرضيع تحت سنة مجاني؟
- رفع ملف Meta `marketing/social-catalog/2026-10-25_meta-reviewed-visible.xml` (وبعد V37 ملف أحدث).
- مفتاح فك تشفير النسخ (الخاص) لتشغيل drill مشفر حقيقي — غير موجود على جهاز التطوير.
- إذن حجز تجريبي على بريده لاختبار بريد الإلغاء حيًا.
- مراجعة GA4 Realtime من حسابه، وقرارات مذكرة SAF-04.
- القيم الافتراضية المالية التي وضعها Claude بانتظار رأيه: سقف 5000 ج/يوم/طرف، انتهاء الموافقة 7 أيام،
  منع من سجّل مصروفًا من دفعه للطرف نفسه 7 أيام، الربح على أساس المحصَّل نقدًا.

## 7. مراجع سريعة

- Board: `docs/execution/WEGO_EXECUTION_BOARD.md` (أعلى 8 فقرات بتاريخ 25 أكتوبر).
- الخطة: `handoff/2026-10-23_SAFARI_MASTER_EXECUTION_HANDOFF_AR.md` + `2026-10-25_OWNER_CORRECTIONS_AND_BOOKING_LOOKUP_AR.md`.
- المراجعات: `2026-10-25_SECURITY_REVIEW.md`، `2026-10-25_SAF04_FINANCE_DECISION_MEMO.md`،
  `marketing/SITE_AUDIT_2026-10-25.md`، `marketing/CATALOG_READINESS_2026-10-25_AR.md`.
- النسخ خارج VPS: `/home/wego/Backups/safari-tours-sharm/` (آخرها `20261025T074256Z.bundle`).
- دليل الموظف المحدّث: `operations/ADMIN_OPERATING_GUIDE_AR.md` و PDF في `/home/wego/output/pdf/Safari_Tours_Sharm_Admin_Guide_AR_2026-10-25.pdf`.

**قاعدة واحدة فوق الكل:** منفّذ نشر واحد في كل لحظة. لو بدأت أنت النشر، Claude يتوقف عن النشر حتى
يُسلَّم له صراحة، والعكس — وسجّل التسليم في Board.
