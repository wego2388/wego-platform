# سفاري تورز شرم — تسليم Claude إلى Codex: الحالة الحية والمتبقي

> 25 أكتوبر 2026 (آخر تحديث بعد commit `158b1f0`) · من Claude إلى Codex بطلب المالك.
> **من لحظة هذا التسليم: Codex هو منفّذ النشر الوحيد.** Claude لن ينشر شيئًا حتى يُعاد له
> التنفيذ صراحةً ويُسجَّل ذلك في Board. `WEGO-016-OPS2-G` ما زالت الحزمة الوحيدة ACTIVE.
> هذا الملف لقطة حالة موثقة وليس تفويضًا جديدًا؛ الأولوية: طلب المالك الأحدث ← Board ← هذا الملف.
>
> العلامات: `[x]` منشور ومتحقَّق منه حيًا · `[c]` committed ومختبَر وغير منشور · `[ ]` متبقٍ · `[!]` يحتاج المالك.

## 0. الصورة في دقيقة

| | الحالة |
|---|---|
| الإنتاج | VPS `187.6.167.233`، مشروع `safari-tours-sharm-prod`. backend/site/ERP = `str-2026.10.25-a728327-fin`؛ edge من `e3559bc` (`current → releases/str-2026.10.25-e3559bc-edge`). Flyway **36**. `ENQUIRY_ONLY`. بريد Gmail مفعّل. GA4 `G-3JZT9JZRQE` مفعّل بالموافقة. Meta Pixel مقفول. |
| الخيمة | `resort-os-prod` لم تُلمس في أي نشر؛ هوية حاوياتها قورنت قبل/بعد كل نشر ← متطابقة. |
| جاهز للنشر | `str-2026.10.25-df846c1-sec` — صور محمّلة على VPS + `deploy.sh`/`rollback.sh` جاهزين في `/srv/safari-tours-sharm/incoming/str-2026.10.25-df846c1-sec/`. E2E معزول **أخضر بالكامل**. (§3) |
| جاهز بعد تأكيد المالك | V37 بيانات الكتالوج (`0c5680f` + `158b1f0`) — Tier1 READY-WITH-FOLLOWUPS والمتابعات أُصلحت؛ ينتظر تأكيدات المالك + تجربة على نسخة إنتاج. (§4) |
| الفرع | `wego-016-safari-hardening`، HEAD `158b1f0` مرفوع. ملفات غير محفوظة ليست من Claude: `handoff/README.md` (معدّل) و`handoff/2026-10-25_CANVA_TOUR_DESIGNS_EXECUTION_HANDOFF_AR.md` (جديد) — لا تُدمج في commits أخرى. |

## 1. ما نُشر اليوم `[x]`

| الإصدار | المحتوى | المراجعة | الدليل |
|---|---|---|---|
| `str-2026.10.25-b98aa55` | V36 أسعار/صور المالك + «حجزي» بمرجع STQ + ترقيعات تبعيات | Tier1 READY + V36 على pg_dump إنتاج | `deployment/2026-10-25_CATALOG_STQ_RELEASE_MANIFEST.json` |
| `str-2026.10.25-50e35ea-mail` | بريد إلغاء لحجوزات OFFICE التي وصلها تأكيد | Opus Tier1 READY | Board |
| GA4 (env فقط) | تفعيل المعرّف وإعادة إنشاء site/edge | متصفح: صفر طلبات قبل الموافقة/بعد الرفض، السحب يوقف فورًا | Board |
| `str-2026.10.25-a728327-fin` | SAF-04 Slice A ضوابط مالية + SEO/كاش الموقع | Opus Tier1 READY | `deployment/2026-10-25_FINANCE_SEO_RELEASE_MANIFEST.json` |
| `str-2026.10.25-e3559bc-edge` | H1: نطاق staff لا يمرّر نقاط الكتابة العامة | مراجعة أمنية مستقلة | `handoff/2026-10-25_SECURITY_REVIEW.md` |

حي الآن: 27 رحلة؛ كواد 15، بجي 35/وحدة، عشاء بدوي 25، بحرية مسائية 30، دهب 30، هاي إس 30،
سيدان 15، **SUV 20 ما زال موجودًا حتى V37**، غواصة 35، زجاجي 15. staff `POST` العامة ← 404.

## 2. طريقة النشر المثبتة (اتبعها حرفيًا)

1. **CI:** وظائف الكود يجب أن تكون خضراء. وظائف `infrastructure` و`safari-container-gateway` تفشل حاليًا
   بسبب حصة public ECR (`toomanyrequests: Data limit exceeded`) — ليست الكود. التعويض: E2E معزول (3).
2. **البناء خارج VPS:** backend: `bootJar` → `infrastructure/docker/safari-backend-from-jar.Dockerfile`.
   site/ERP: `pnpm build` على المضيف → `safari-nuxt-from-output.Dockerfile` (npm داخل Docker يتعطل هنا).
3. **E2E معزول إلزامي على نفس الصور** + override يضيف `cap_drop`/`no-new-privileges` لمحاكاة الإنتاج:
   - `ci-safari-enquiry` (58087): `safari-enquiry.spec.ts` (44) + `e2e/verify-safari-enquiry-history-startup.mjs` + `safari-online-requests.spec.ts` (8).
   - `safari-ops-e2e` (58090، مع `e2e/compose.safari-checkout.yaml`): checkout 13، site 11، launch 5، office-inventory 5، operations-cycle 1، document-responsive 12 + فحص PII في السجلات.
   - **درس اليوم:** Vue مزدوج بعد ترقية Nuxt — build وunit نجحا وSSR أعطى 500. E2E كشفه. الحارس الآن: `web/apps/safari-tours-sharm-site/test/vue-single-version.spec.ts`.
4. **النقل:** `docker save|gzip` → `rsync --partial --append-verify` إلى `incoming/<rid>/` → `sha256sum -c` → `docker load` → قارن image IDs.
5. **نسخة قبل النشر:** `sudo systemctl start safari-backup.service`، ثم انسخ الـ bundle إلى `/home/wego/Backups/safari-tours-sharm/` وطابق sha256 مع manifest.
6. **النشر:** مجلد إصدار جديد = نسخة من `current` + ملفات المصدر من `source*.tar.gz`، ثم `deploy.sh <rid>`:
   قفل `shared/.deploy.lock`، حفظ `.env.production.before-<rid>`، تبديل `SAFARI_*_IMAGE`، `DC up --no-deps --wait` خدمة خدمة، edge `--force-recreate`، مقارنة حاويات الخيمة. `DC` = base + production + gateway compose.
7. **بعد النشر:** `current` رابط مطلق، `safari-health.service`، فحوص حية، manifest في `deployment/` + Board.
8. **الرجوع:** `rollback.sh <rid>` (env السابق + صور الإصدار السابق). لا تُرجع backend أقدم من V35.

ممنوعات: لا `prune` عام، لا `down -v` على الإنتاج، لا لمس الخيمة، لا Paymob ولا Meta Pixel، لا أسرار في git/الشات.

## 3. الإصدار الجاهز: `str-2026.10.25-df846c1-sec` `[c]`

| البند | commit | المراجعة |
|---|---|---|
| SAF-04 Slice B تقارير قراءة فقط + CSV (انعكاسات، فروق الخزنة، تحويل الطلبات، فلوس الرحلات) | `70c5534` | Tier2 — 6 اختبارات PostgreSQL + 10 ERP |
| L4 مرجع STQ من الخادم، L5 حذف سبب الإلغاء من البحث العام، M2 لا تحليلات على `/booking/*` | `7c29e01` | Opus Tier1 READY |
| L1 بث `pg_dump | gpg` | `5fb5ff3` | READY |
| L2 `no-new-privileges` + `cap_drop: ALL` (postgres: CHOWN/DAC_OVERRIDE/FOWNER/SETGID/SETUID) | `9a0d267` | مثبت محليًا |
| M3 Nuxt 4.6.2 + Vue واحد 3.5.44 | `1834479`, `a546ad8` | web check 997 |

صور: backend `sha256:30598f02…`، site `sha256:67365a8d…`، ERP `sha256:aec2e495…`؛ jar `6c8db5da…`.
E2E معزول مع hardening: enquiry 44، حارس الدفع PASS، requests 8، checkout 13، site 11، launch 5، office 5، cycle 1، documents 12؛ السجلات بلا PII.

**خطوات Codex للنشر:**
- [ ] أنشئ `releases/str-2026.10.25-df846c1-sec` (نسخة من `current` ثم فك `source5.tar.gz` فوقها، احذف `evidence/` و`RELEASE_MANIFEST.json` المنسوخين)، وانقل `deploy.sh`/`rollback.sh` من `incoming/` إليه وراجعهما.
- [ ] `deploy.sh` **يعيد إنشاء postgres أولًا** (L2) ثم backend/site/web/edge؛ `rollback.sh` يعيد postgres أيضًا بإعدادات `e3559bc-edge`.
- [ ] نسخة قبل النشر (§2.5) ثم النشر ثم الفحوص الحية (ومنها: تقارير ERP الجديدة تفتح بالصلاحية، `cancellationReason` غائب من `bookings/lookup`).
- [ ] **تفعيل L1 فعليًا (منفصل):** `safari-backup.service` له drop-in يشغّل `/srv/safari-tours-sharm/shared/ops-backup-inventory-6db0d43/bundle-backup.sh` — نشر الإصدار لا يفعّل البث المشفر. انسخ `bundle-backup.sh`+`lib.sh` الجديدين لمجلد shared جديد، عدّل الـ drop-in، `daemon-reload`، شغّل الخدمة مرة وتحقق من bundle صالح.

## 4. V37 بيانات الكتالوج من إجابات المالك `[c]` — `0c5680f` + `158b1f0`

المصدر الوحيد للحقائق: فقرات 25 أكتوبر في نهاية `owner-data/PENDING_OWNER_ANSWERS.md`.
السجل: `marketing/owner-corrections/2026-10-25_catalog-revision-part2.json` (216 تعديل محتوى، 26 حقيقة).

يطبق: لغات المرشد en/it/ru لكل الرحلات النشطة (عدا نقل المطار)؛ «تعمل يوميًا»؛ حجز قبلها بيوم لـ12 رحلة بحرية
(ومنها وصف السبيد بوت)؛ الدولفين 14:30؛ ملاحظة ميعاد الاستلام على واتساب لنقل المطار؛ غير شامل:
«الصور والفيديو (تُباع منفصلة)»، الشال، النظارة الشمسية؛ القاهرة بالطيارة «مرشد إنجليزي أو إيطالي أو روسي»؛
**حذف خيار SUV** (لا يوجد علم تعطيل؛ الترحيل يُجهض إن وُجد طلب مفتوح أو حجز قادم يستخدمه؛ اللقطات التاريخية سليمة).
وجبة سانت كاترين + دهب **مؤجلة** لحين تأكيد المالك (`mealsPending` في السجل).

مراجعة: Opus Tier1 READY-WITH-FOLLOWUPS → المتابعات الخمس أُصلحت في `158b1f0` (481 اختبار backend، foundry، repository-check).

**قبل نشره:**
- [!] تأكيدات المالك في §6 (سانت كاترين، الغطس، الدولفين، نقل المطار). أي تغيير ينتج عنها يُعدَّل في V37 نفسه طالما لم يُنشر.
- [ ] تجربة V36→V37 على pg_dump إنتاج يُسحب للقراءة ويُستعاد في حاوية `--internal` ثم يُحذف (طريقة V36 في Board).
- [ ] انشر في وقت هادئ (الجداول غير مقفولة ضد طلب جديد بالـ SUV أثناء الترحيل).
- [ ] بعد النشر: أعد توليد تغذية Meta (`scripts/safari-content/export-social-catalog.mjs --write` ثم `build-meta-catalog-feed.mjs --all-visible-reviewed`)؛ `owner-data/build_owner_hub.py` ما زال يذكر SUV؛ تسميات SUV في `web/apps/safari-tours-sharm-site/app/content/tourPage.ts` غير ضارة.

## 5. المتبقي بالأولوية

1. [ ] نشر `df846c1-sec` + تفعيل L1 (§3).
2. [ ] V37 بعد تأكيدات المالك (§4) ثم تغذية Meta.
3. [ ] **سياسة الأطفال (Tier1 مال + migration):** قرار المالك: «من 1 إلى 4.5 سنة مجاني، من 5 إلى 9 نص سعر الفرد، كل الرحلات». الحالي: `price_child_cents` فارغ في كل الرحلات، الحجز فيه `adults_count/children_count` فقط، الطفل حاليًا مجاني (`COALESCE(price_child,0)`)، قيد `total_matches_pricing` في القاعدة. المطلوب: فئات عمر في نموذج الطلب/الحجز، قاعدة نصف السعر مشتقة من سعر البالغ مع التقريب، أعمدة جديدة وتعديل القيد، قرار هل الرضيع يأخذ مقعدًا، ERP/البريد/المستندات/الموقع بأربع لغات، قاعدة للحجوزات القديمة. حزمة Tier1 مستقلة بمراجعة وتجربة على نسخة إنتاج.
4. [ ] SAF-04 Slice C تثبيت التكلفة (Tier1 + migration) — ينتظر قرارات المالك في `handoff/2026-10-25_SAF04_FINANCE_DECISION_MEMO.md`.
5. [ ] أمان متبقٍ: M1 سقف المقاعد المعلّقة (قبل Paymob فقط)، L3 أرقام STR تسلسلية، L6 CSP `unsafe-inline`، L7 فصل `cost:manage` عن `settlement:pay`، تتبع vite (peer ^8.3.4).
6. [ ] أداء من `marketing/SITE_AUDIT_2026-10-25.md`: غلاف super-safari PNG 2.2MB بـ `no-store` (أصل ERP)، كاش `/media/**`، TBT.
7. [ ] المالك وافق «انشرها» على صور المكتبة المختارة في سجل وسائط ERP: تحتاج جلسة موظف مصرّحة؛ الموقع يعرضها بالفعل من حزمة الإصدار.
8. [ ] SAF-03.06/SAF-10.08 قبول تشغيلي على حجز حقيقي — لا تُغلق OPS2-G قبله.

## 6. المطلوب من المالك `[!]`

- سانت كاترين + دهب: غدا أم عشاء؟
- كورس الغطس: يُعامل كرحلة بحرية (حجز قبلها بيوم)؟ ومستوى الشهادة/الفحص الطبي/الأيام والغطسات؟
- عرض الدولفين: عرض واحد يوميًا 14:30 فقط؟
- نقل المطار: يضاف له «الصور/الشال/النظارة غير شاملة»؟ ووقت الانتظار المجاني؟
- بقشيش كابتن السبيد بوت على مين؟
- سن 4.5 بالضبط والرضيع تحت سنة.
- رفع تغذية Meta، مفتاح فك تشفير النسخ (drill مشفر)، إذن حجز تجريبي لاختبار بريد الإلغاء، مراجعة GA4 Realtime، قرارات مذكرة SAF-04.
- القيم المالية الافتراضية بانتظار رأيه: سقف 5000 ج/يوم/طرف، انتهاء الموافقة 7 أيام، من سجّل مصروفًا لا يدفع للطرف نفسه 7 أيام، الربح على المحصَّل نقدًا.

## 7. مراجع

Board `docs/execution/WEGO_EXECUTION_BOARD.md` (فقرات 25 أكتوبر) · الخطة `handoff/2026-10-23_SAFARI_MASTER_EXECUTION_HANDOFF_AR.md` ·
المراجعات `handoff/2026-10-25_SECURITY_REVIEW.md`، `handoff/2026-10-25_SAF04_FINANCE_DECISION_MEMO.md`، `marketing/SITE_AUDIT_2026-10-25.md`،
`marketing/CATALOG_READINESS_2026-10-25_AR.md` · النسخ خارج VPS `/home/wego/Backups/safari-tours-sharm/` (آخرها `20261025T074256Z.bundle`) ·
دليل الموظف `operations/ADMIN_OPERATING_GUIDE_AR.md` و PDF `/home/wego/output/pdf/Safari_Tours_Sharm_Admin_Guide_AR_2026-10-25.pdf`.

**منفّذ نشر واحد:** Codex من الآن. أي عودة لـ Claude تُسجَّل في Board أولًا.
