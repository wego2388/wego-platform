# Safari Tours Sharm — بداية حساب ChatGPT الجديد

**التاريخ:** 2026-09-29 — Africa/Cairo  
**المالك:** Mohamed Wagdy  
**الحالة:** `SUPERSEDED CHECKPOINT — WEGO-016-E COMPLETE / WEGO-017-A ACTIVE`  
**Repository:** `/home/wego/wego-platform`  
**Branch:** `wego-016-safari-tours-baseline`  
**HEAD عند إنشاء التسليم:** `f04a0fa`  
**Origin baseline:** `2cc25db`؛ الفرع المحلي ahead بـ5 commits  
**Production:** لم يُنشر هذا الفرع.

> **تحديث 2026-09-29:** نفذت تعليمات هذا الملف وأُغلق E محليًا في `8a5e643`
> بعد 13/13 E2E وTier 1 بصفر موانع. الحالة الحية الآن في Execution Board؛
> `WEGO-017-A` هو الـpacket النشط. بقية الملف سجل نقطة البداية القديمة. اقرأ
> بروتوكول `CHATGPT_MULTI_ACCOUNT_WORKFLOW.md` أولًا، ثم نفّذ preflight ولا
> تكتب إذا كان حساب آخر ما زال يعمل في نفس worktree.

## 1. المشروع في دقيقة

Safari Tours Sharm عميل `ISOLATED_INSTANCE` فوق منتج
`products/tours-operator` داخل Wego Platform، وليس fork مستقلًا:

```text
Wego Platform
└── product: tours-operator
    └── client: safari-tours-sharm
        ├── public Nuxt website
        ├── staff Nuxt ERP
        ├── Kotlin/Spring modular-monolith module
        └── isolated client deployment/database/secrets
```

المسارات الأساسية:

- backend: `products/tours-operator/`
- migrations/runtime composition: `platform/application/`
- OpenAPI: `platform/contracts/openapi/v1/wego-api.yaml`
- website: `web/apps/safari-tours-sharm-site/`
- ERP: `web/apps/safari-tours-sharm-erp/`
- client manifest/design/release docs: `clients/safari-tours-sharm/`
- marketing facts خارج Git runtime:
  `/home/wego/projects/clients/safari-tours-sharm/`

لا تربط هذا المشروع بـResort OS، ولا تنقل كود platform إلى marketing
workspace أو repo العميل المنفصل. حدود المنصة ثابتة:

```text
Platform → Product → Client Configuration → Isolated Client Deployment
```

## 2. ترتيب القراءة الإلزامي

- [ ] `AGENTS.md`
- [ ] `docs/ENGINEERING_CONSTITUTION.md`
- [ ] `docs/operations/REVIEW_INTENSITY.md`
- [ ] `docs/operations/AGENT_COLLABORATION.md`
- [ ] `clients/safari-tours-sharm/handoff/CHATGPT_MULTI_ACCOUNT_WORKFLOW.md`
- [ ] هذا الملف كاملًا
- [ ] قسم `WEGO-016` في `docs/execution/WEGO_EXECUTION_BOARD.md`
- [ ] `clients/safari-tours-sharm/handoff/SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`
- [ ] `clients/safari-tours-sharm/content-research/README.md` قبل أي content
- [ ] ملفات الكود والاختبارات التي ستتغير، لا الملخصات فقط

عند تعارض نص قديم: Git الفعلي ثم Execution Board ثم هذا checkpoint الحديث
يحكمون. ملف maturity مهم للفجوات، لكن عدة checkboxes داخله متأخرة عن commits
E/F؛ لا تعدّلها بالجملة من غير مطابقة الكود والدليل.

## 3. الحقيقة الحالية — ما اكتمل وما لم يكتمل

### مرفوع على origin

- [x] A: baseline rescue.
- [x] B: OpenAPI/generated types وإزالة تعارض money/customer.
- [x] C: catalog/CRUD/slots/30 approved records في `0029492`.
- [x] D الأساسي: payment aggregate/Paymob webhook/expiry في `0029492`.
- [x] docs/briefs حتى `2cc25db`.

### commits محلية غير مرفوعة

1. `41b4bfa` — Paymob HMAC fields، توثيق checkout stub، وتحريك V17 DML.
2. `5174f25` — mock Paymob + Safari site container/Nginx + Playwright spec.
3. `3cc54a8` — تسجيل E على Board.
4. `12259a2` — ERP finance aggregation + tests.
5. `f04a0fa` — تسجيل F على Board.

لا تعِد A–D ولا تسحق هذه commits. راجعها كحزمة متسلسلة، ثم أصلح findings في
commits جديدة واضحة.

### حالة القرص السابقة لهذا onboarding ويجب الحفاظ عليها

- modified:
  `clients/safari-tours-sharm/handoff/SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`
  — تحديث progress table غير مكتمل بعد.
- deleted:
  `platform/application/src/main/resources/db/migration/V17__tours_operator_catalog_seed.sql`
  — حذف مقصود ظاهريًا لإكمال نقل DML إلى
  `db/migration/data/V17__tours_operator_catalog_seed.sql` الذي أضافه
  `41b4bfa`. لا تسترجعه ولا تعتمد الحذف قبل إثبات أن Flyway يرى V17 مرة
  واحدة وأن jOOQ لا ينفذ DML.

أي تغييرات docs أخرى تراها في status هي حزمة onboarding الحالية. لا تنظفها
ولا تستخدم `git add .`.

## 4. لماذا E هو الـpacket النشط

كود E موجود في `5174f25`، لكن acceptance غير مكتمل: ملف
`e2e/tests/safari-checkout.spec.ts` لم يُشغّل ضد Compose حقيقي. وجود spec
وgreen unit/build لا يثبت booking → payment → webhook → confirmation ولا
يثبت Nginx routing أو أن mock لا يتسرب إلى production.

F نُفذ مبكرًا في `12259a2`، لكنه **محجوب خلف بوابة E** ولا يبرر بدء G.
الحساب الجديد يغلق E أولًا، ثم يفتح F للمراجعة/الإصلاح.

## 5. WEGO-016-E — المطلوب الآن بالترتيب

### E-0 — preflight والحالة

- [ ] سجل status/branch/HEAD/origin/worktrees والعمليات الجارية.
- [ ] تأكد أن لا حساب آخر يكتب في نفس worktree.
- [ ] راجع diff نقل V17 وmaturity doc؛ لا reset/restore تلقائي.
- [ ] أصلح/ثبت Execution Board invariants قبل ادعاء gate أخضر.

### E-1 — migration layout

- [ ] يجب أن توجد migration V17 فعالة **مرة واحدة فقط** في Flyway.
- [ ] أثبت fresh migration على PostgreSQL disposable وأن V14→V18 تطبق.
- [ ] أثبت وجود 30 tour وأن Private Boat request-only/inactive.
- [ ] أثبت `jooqCodegen`/build لا يحاول تنفيذ catalog DML ولا يرى duplicate V17.
- [ ] سجّل لماذا المسار `db/migration/data/` يُكتشف في runtime ولا يدخل DDL
  codegen، ولا تعتمد على التعليق فقط.

### E-2 — Compose E2E حقيقي

ابدأ من runbook ولا تستخدم production DB:

```bash
docker compose --env-file .env.example \
  -f infrastructure/compose/compose.yaml \
  up --build --wait
curl --fail http://127.0.0.1:58080/healthz
```

ثم seed المحمي على قاعدة disposable فقط:

```bash
WEGO_E2E_SEED_CONFIRM=yes-this-is-a-disposable-e2e-database \
  pnpm --dir e2e run seed
```

وشغّل Safari spec من مساره الحقيقي:

```bash
WEGO_E2E_BASE_URL=http://127.0.0.1:58080 \
WEGO_STS_SITE_BASE_URL=http://127.0.0.1:58080 \
  pnpm --dir e2e exec playwright test tests/safari-checkout.spec.ts
```

- [ ] كل E1–E9 تمر فعليًا.
- [ ] booking capacity، payment PENDING→PAID، duplicate webhook، bad HMAC،
  confirmation page وERP visibility مثبتة.
- [ ] mock يحتاج opt-in صريح محليًا، وproduction config لا يفعله.
- [ ] افحص logs لغياب PII/secrets وraw webhook leakage.
- [ ] أوقف stack بدون حذف volume تلقائي؛ لا تستخدم `down --volumes` إلا بعد
  تحديد project/volume والتأكد أنها disposable.

### E-3 — Tier 1 review مستقل

راجع fresh-context وبأدلة تنفيذية:

- HMAC field extraction/canonical ordering وfailure behavior.
- server-side amount/currency/order matching، idempotency وlate/duplicate event.
- expiry/confirmation race وسعة المقعد.
- mock isolation وenvironment default.
- Nginx route precedence وpublic/staff boundaries وCORS/CSP.
- PII في URL/session/log/test artifacts.
- V17 discovery ومخاطر restore/upgrade.

كل BLOCKING finding يُصلحه المنفذ ثم يعاد review. لا self-certify.

### E-4 — بوابة الإغلاق

- [ ] targeted payment/migration tests خضراء.
- [ ] Playwright Safari spec أخضر على stack حقيقي.
- [ ] `bash scripts/safari-tours-sharm-check.sh` أخضر بأرقام حديثة.
- [ ] `bash scripts/repository-check.sh` و`git diff --check` أخضران.
- [ ] Tier 1 verdict: zero blocking findings.
- [ ] Board/maturity/handoff محدثة بصدق.
- [ ] لا push أو deploy بلا تفويض Mohamed الصريح.

```text
E status: ACTIVE
Implementer:
Reviewer:
Migration evidence:
Compose/Playwright evidence:
Full gate:
Commit(s): 41b4bfa + 5174f25 + follow-ups
Push: NOT AUTHORIZED / NOT PUSHED
Deploy: NOT DEPLOYED
```

## 6. WEGO-016-F — بعد إقفال E فقط

`12259a2` بداية جيدة للواجهة والاختبارات، لكنه ليس finance ledger نهائيًا.
المراجعة الحالية وجدت فجوة محاسبية جوهرية:

- `useFinanceAggregation.ts` يعتبر `CONFIRMED` و`COMPLETED` إيرادًا ويجمع
  `Booking.totalPrice`.
- هذا لا يثبت أن Payment أصبحت `PAID`، ولا يطرح `REFUNDED`، وقد يضم تأكيدًا
  يدويًا أو يخفي mismatch مع Paymob.

لذلك عند تفعيل F:

- [ ] الإيراد من payment ledger/status الموثق: PAID ناقص REFUNDED، لا booking
  status وحده.
- [ ] pending/failed/mismatch/reconciled تظهر منفصلة ولا تدخل revenue.
- [ ] date basis واضح: paid/refunded timestamp، لا tour date فقط.
- [ ] صلاحية finance مستقلة أو قرار موثق؛ `booking:view` وحدها لا تصبح تلقائيًا
  إذنًا لرؤية المال/PII.
- [ ] server-side date filtering/pagination للبيانات الكبيرة، أو حد صريح قبل
  التحميل الكامل.
- [ ] اختبارات paid/refunded/partial-date/mixed-currency/large totals وتجنب
  تحويل `bigint` إلى `Number` في نسب قد تتجاوز safe integer.
- [ ] راجع booking actions والـdetail وPII minimization/audit.
- [ ] Tier 1 review مستقل ثم full web/Safari gates.

لا تبدأ G حتى تصبح F صحيحة ماليًا ومغلقة رسميًا. Staff roles ليست “ميزة
تجميلية”: أقل صلاحية، onboarding/offboarding وfinance/cancel/refund authority
لازم تُحسم قبل التشغيل.

## 7. ما بعد F

- G: notifications عبر transactional outbox، consent، retry/dead-letter؛ لا
  رسالة قبل commit ولا تكرار عند replay.
- H: isolated deployment، TLS/CORS/secrets، observability، backup/restore،
  rollback وPaymob sandbox الحقيقي.
- I: UAT لكل locale/device/payment state ثم controlled launch وkill switches.

هذه roadmap وليست authorization. كل packet يُفعل على Board بعد إغلاق السابق
وتفويض المالك.

## 8. حقائق وموانع لا تُكسر

- لا client-supplied amount يؤكد دفعًا؛ السعر snapshot من السيرفر.
- return URL ليس إثبات دفع؛ webhook صحيح ومطابق فقط يؤكد.
- duplicate/late webhook وnetwork retry لا ينتجان أثرًا ماليًا ثانيًا.
- لا active tour بلا price/policy/content/availability معتمدة.
- لا صورة من WordPress بلا rights evidence؛ snapshot بحثي فقط.
- Paymob credentials منفصلة لهذا العميل ولا تدخل Git أو logs.
- no production mock، no production seed، no real-customer test data.
- Flyway forward-fix؛ لا تعدّل migration صدرت دون قرار/خطة واضحة.
- commit، push وdeploy ثلاث موافقات منفصلة.

## 9. تسليم نهاية الجلسة

حدّث checklist وسجل:

```text
Account / role:
Packet:
Worktree / branch / start-end SHA:
Pre-existing files preserved:
Files changed:
Flyway/OpenAPI changes:
Targeted tests:
Full gates:
Tier 1 reviewer verdict:
Disposable DB/Compose state:
Commit / push / deploy:
Last complete item:
Next exact item:
Blocker / owner input:
```

## 10. رسالة جاهزة للحساب الجديد

> افتح `/home/wego/wego-platform` واقرأ `AGENTS.md` ثم الملفات بالترتيب في
> `clients/safari-tours-sharm/handoff/CHATGPT_MULTI_ACCOUNT_WORKFLOW.md`.
> عقد البداية الحي هو
> `clients/safari-tours-sharm/handoff/2026-09-29_NEW-CHATGPT-ACCOUNT_START-HERE.md`.
> ابدأ بإقفال WEGO-016-E على Compose حقيقي ومراجعة Tier 1؛ لا تبدأ G ولا
> deploy. حافظ على نقل V17 وتعديل maturity handoff الموجودين قبل جلستك، ولا
> تكتب بالتوازي على نفس worktree مع حساب ChatGPT آخر.
