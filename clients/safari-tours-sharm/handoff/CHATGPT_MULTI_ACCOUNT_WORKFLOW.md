# Safari Tours Sharm — بروتوكول حسابَي ChatGPT

**التاريخ:** 2026-09-29  
**المالك:** Mohamed Wagdy  
**النطاق:** `WEGO-016` داخل `/home/wego/wego-platform` فقط.

هذا البروتوكول يكمّل `AGENTS.md` و`docs/operations/AGENT_COLLABORATION.md`
ولا يستبدلهما. زيادة الكوتة لا تسمح بتشغيل حسابين يكتبان في نفس worktree أو
بتجاوز Tier 1 review أو تفويض commit/push/deploy.

## 1. مصادر الحقيقة

- كود المنتج موجود في Git داخل `/home/wego/wego-platform`، والـremote الوحيد
  لهذا الكود هو `wego2388/wego-platform`.
- `/home/wego/projects/clients/safari-tours-sharm` مساحة marketing وحقائق
  عميل بشرية، وليست build/runtime repo ولا مكانًا لنسخ كود المنصة.
- Git + Execution Board + أحدث handoff حي هي الذاكرة المشتركة. محادثة أي
  حساب ليست مصدر حقيقة.
- `clients/safari-tours-sharm/content-research/legacy-wordpress-export.json`
  مصدر بحثي محجوز، لا يثبت حق صورة أو صحة سعر أو قابلية النشر.

## 2. ملكية العمل

1. حساب واحد فقط هو **مالك التنفيذ** للـpacket النشط والـworktree الحالي.
2. الحساب الآخر يكون متوقفًا، أو reviewer قراءةً فقط، أو يعمل في worktree
   مستقل على packet مصرح مستقلًا ولا يلمس الملفات نفسها.
3. ممنوع الكتابة المتزامنة على `/home/wego/wego-platform`.
4. لا يُسمح لحساب نفّذ Tier 1 packet أن يصف مراجعته لنفسه بأنها مستقلة.
   الحساب الآخر يبدأ fresh context، يعيد الأدلة المهمة فعليًا، ثم يسلم
   findings للمنفذ. لو أصلح reviewer الكود يصبح منفذًا لتلك الجولة، ويلزم
   re-review مستقل جديد للمناطق Tier 1 المتغيرة.
5. Flyway migrations وExecution Board وOpenAPI يملكها حساب واحد في اللحظة
   نفسها حتى لا تظهر heads/versions أو contracts متعارضة.

## 3. بداية كل جلسة

نفّذ أولًا:

```bash
cd /home/wego/wego-platform
git status --short --branch
git branch --show-current
git rev-parse --short HEAD
git log --oneline --decorate -n 10
git worktree list
```

ثم اقرأ بالترتيب:

1. `AGENTS.md`
2. `docs/ENGINEERING_CONSTITUTION.md`
3. `docs/operations/REVIEW_INTENSITY.md`
4. `docs/operations/AGENT_COLLABORATION.md`
5. هذا الملف
6. `2026-09-29_NEW-CHATGPT-ACCOUNT_START-HERE.md`
7. قسم `WEGO-016` كاملًا في `docs/execution/WEGO_EXECUTION_BOARD.md`
8. `SAFARI_TOURS_PRODUCTION_MATURITY_HANDOFF.md`
9. `../content-research/README.md` قبل أي محتوى/سعر/صورة

لا تعمل `pull` أو rebase أو reset فوق worktree غير نظيف. افحص الفرق بين HEAD
وorigin أولًا، واعرف من يملك كل ملف متغير.

## 4. تبديل الحساب النشط

قبل أن يسلم الحساب A للحساب B:

- يوقف test/watch/Compose jobs التي بدأها أو يذكرها صراحةً إن ستظل قائمة.
- يشغّل targeted gates و`git diff --check` ويسجل النتائج الحقيقية.
- يحدّث العلامات `[x]`/`[~]`/`[ ]`/`[!]` في handoff الحي.
- يسجل worktree، branch، start/end SHA، الملفات، migration/contract، قاعدة
  الاختبار، الاختبارات، آخر بند ناجح، والبند التالي فقط.
- يوضح commit/push/deploy status؛ الثلاثة عمليات منفصلة وتحتاج تفويضًا.
- يعلن أنه توقف عن الكتابة. بعدها فقط يبدأ B.

إذا انتهت كوتة A فجأة، يبدأ B قراءةً فقط: يفحص Git والعمليات والـdiff، ولا
يفترض أن آخر رسالة دردشة تساوي آخر حالة على القرص.

## 5. العمل المتوازي عند الضرورة

- worktree وفرع مستقلان من exact integration SHA لكل حزمة.
- packet واحد `ACTIVE` في كل implementation worktree، وفق الدستور.
- لا migrations متوازية من نفس baseline، ولا تعديل متوازٍ للـBoard/OpenAPI.
- يحدد مسبقًا مالك الدمج. بعد الدمج يعيد اختبارات التداخل والبوابة الموحدة.
- لا نسخ ملفات يدويًا بين worktrees ولا `git add .`.

## 6. قواعد Tier 1 وبيانات التشغيل

- Auth/permissions، payment، PII، Flyway، client isolation كلها Tier 1.
- evidence يعني أمرًا شُغّل وخرج بنتيجة، لا قراءة كود أو قول “المفروض”.
- اختبارات migration/concurrency/E2E على PostgreSQL/Compose disposable فقط.
- لا Paymob production credentials أو بيانات عميل حقيقية في الاختبارات.
- لا secrets في docs/chat/logs/commits، ولا تفعيل mock Paymob في production.
- أي فشل migration، HMAC، reconciliation، E2E، restore أو health = توقف وتسليم
  دقيق، لا استمرار بالتخمين.

## 7. قالب checkpoint

```text
Active account / role:
Packet / review tier:
Worktree + branch:
Start SHA -> end SHA:
Modified/untracked files and owner:
Migration / OpenAPI changes:
Commands run + exact results:
Tests not run + reason:
Disposable DB/Compose project used:
Tier 1 reviewer / verdict:
Commit / push / deploy:
Last completed checklist item:
Next exact item:
Do not repeat:
Blocker / owner input:
```

