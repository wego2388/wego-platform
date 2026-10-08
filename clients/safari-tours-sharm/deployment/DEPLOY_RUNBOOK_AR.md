# Safari Tours Sharm — دليل النشر على VPS المشترك (لـCodex)

> **تصحيح حاكم بتاريخ 8 أكتوبر:** هذا دليل الـhost-Nginx القديم، وليس وصف
> طوبولوجيا الـVPS الفعلية. اتبع [دليل البوابة داخل Docker](CONTAINER_GATEWAY_RUNBOOK_AR.md)
> بدل أوامر `sudo nginx` / `systemctl reload nginx` ومثبتات host هنا. احتفظ
> بباقي ضوابط الأسرار والعزل؛ لا تنفذ المسارين معًا.

> الإصدار: WEGO-016-OPS2-G، أُعد في 7 أكتوبر 2026 من Claude.  
> **هذا الملف خطة وأوامر؛ لم يُنفَّذ منه شيء على أي سيرفر.** لا SSH ولا DNS
> ولا deploy تم من جهة Claude. المنفذ: Codex، بعد مراجعة GO وموافقة محمد.  
> المرجع الحاكم: `../handoff/2026-10-07_CLAUDE_PREPARE_CODEX_DEPLOY_VPS_HANDOFF_AR.md`
> (§4 حماية الخيمة، §7 الترتيب، §9 شروط STOP).

العلامات: `[x]` منفذ ومثبت محليًا · `[~]` جزئي · `[ ]` لم ينفذ (كل خطوات السيرفر) · `[!]` يحتاج محمد.

## 0. ثوابت وأسماء

| البند | القيمة |
|---|---|
| VPS | `187.6.167.233` (يعاد التأكد من الهوية قبل أي أمر) |
| Compose project | `safari-tours-sharm-prod` (مثبت في overlay) |
| الحجوم | `safari-tours-sharm-prod-postgres-data`، `safari-tours-sharm-prod-media` |
| الشبكة | `safari-tours-sharm-prod_internal` (bridge خاصة) |
| المنفذ الوحيد المنشور | `127.0.0.1:${SAFARI_EDGE_PORT}` (مرشح 58080، يختاره Codex بعد preflight) |
| الأسماء | `safaritourssharm.com` · `www.` (301 → apex) · `staff.` (ERP + staff APIs) |
| المجلد | `/srv/safari-tours-sharm/{releases,shared,backups}` |
| الوضع | `TOURS_OPERATOR_BOOKING_MODE=ENQUIRY_ONLY`، `PAYMOB_MOCK=false` |
| Migrations | V1, V2, V3, V14, V16–V33 (22 ملف، آخرها V33) — انظر `RELEASE_READINESS.md` |

متغيرات الصدفة المستخدمة في الأوامر (placeholders):

```bash
export RID='<RELEASE_ID>'            # مثال: str-2026.10.07-<short-sha>
export SAFARI_ROOT=/srv/safari-tours-sharm
export REL="$SAFARI_ROOT/releases/$RID"
export ENVF="$SAFARI_ROOT/shared/.env.production"
export EDGE_PORT=58080               # بعد التأكد أنه حر
DC() { docker compose --env-file "$ENVF" \
  -f "$REL/infrastructure/compose/safari-tours-sharm.compose.yaml" \
  -f "$REL/infrastructure/compose/safari-tours-sharm.production.yaml" "$@"; }
```

**ممنوع طوال التنفيذ:** أي أمر Compose بلا `-f` للملفين أعلاه، `docker system prune`،
`docker volume prune`، `down -v` على المشروع الإنتاجي، restart لـDocker daemon،
`pkill`، أي أمر على حاويات/ملفات/شهادة الخيمة، `certbot --nginx` أو `--standalone`،
`curl -k`، طباعة `.env` أو `docker compose config` كاملًا في سجل مشترك.

---

## 1. Preflight — قراءة فقط

- [ ] هوية الجهاز والموارد:

```bash
hostname; hostname -I; uname -m; cat /etc/os-release | head -3
nproc; free -m; df -h / /var/lib/docker /srv 2>/dev/null
docker version --format '{{.Server.Version}}'; docker compose version
```

  `uname -m` يجب أن يطابق معمارية الصور (`amd64`/`x86_64` متوقع). عدم التطابق = STOP.

- [ ] من يملك 80/443 وطوبولوجيا البوابة:

```bash
sudo ss -ltnp | grep -E ':(80|443|58080)\b'
ps -eo pid,user,cmd | grep -E '[n]ginx|[c]addy|[t]raefik|[h]aproxy'
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}'
```

  - nginx على المضيف (systemd) ← القوالب في `nginx/` تنطبق كما هي.
  - البوابة داخل container، أو Caddy/Traefik ← **STOP**: `127.0.0.1` داخل
    الحاوية ليس المضيف؛ Codex يقترح البديل ويأخذ موافقة قبل أي تغيير.

- [ ] قراءة إعداد البوابة دون تعديل:

```bash
sudo nginx -V 2>&1 | head -2
sudo nginx -T 2>/dev/null | grep -nE 'server_name|listen|default_server|include' | head -80
ls -l /etc/nginx/conf.d /etc/nginx/sites-enabled 2>/dev/null
sudo certbot certificates 2>/dev/null | grep -E 'Certificate Name|Domains|Expiry'
```

  حدد: (1) أي server يعمل `default_server` على 443 ويقدّم الخيمة لأسماء Safari الآن،
  (2) صيغة `listen 443` المستخدمة (`http2 on;` أم `listen ... http2`)، (3) مكان ملفات
  vhost، (4) هل يوجد `server_name` يلتقط `*.safaritourssharm.com` أو `_` بطريقة تتعارض.
  لا تطبع مفاتيح أو ملفات env.

- [ ] IPv6 (مراجعة، finding 1): أسطر `listen [::]:…` في قوالب Safari **معطلة افتراضيًا**.

```bash
sudo nginx -T 2>/dev/null | grep -n '\[::\]'           # هل البوابة/الخيمة تستمع على IPv6؟
dig +short AAAA <ELKHEIMA_DOMAIN>; dig +short AAAA safaritourssharm.com
dig +short AAAA www.safaritourssharm.com; dig +short AAAA staff.safaritourssharm.com
```

  فعّل `listen [::]:…` في ملف Safari **فقط** إذا الخيمة تستمع فعلًا على `[::]` بسيرفراتها
  الخاصة (وdefault_server الخاص بها على `[::]` موجود)؛ وإلا يصبح أول server لسفاري هو
  default على IPv6 ويرد على زوار الخيمة. لا AAAA لسفاري + لا `[::]` في الخيمة ← اتركها معطلة.
  حالة غير واضحة ← STOP.

- [ ] DNS غير ممرر عبر CDN/proxy (finding 6) — وإلا كل الزوار يظهرون بعناوين الـCDN
  وتنهار حدود الطلبات لكل زائر:

```bash
for h in safaritourssharm.com www.safaritourssharm.com staff.safaritourssharm.com; do
  echo "$h A=$(dig +short A $h | paste -sd,)"; done          # يجب 187.6.167.233 فقط
dig +short NS safaritourssharm.com
```

  أي A غير IP الـVPS (مثلاً نطاقات Cloudflare) ← STOP/escalate: يلزم قرار trusted CDN ranges قبل النشر.

- [ ] هل يوجد تثبيت Safari سابق؟

```bash
docker ps -a --filter label=com.docker.compose.project=safari-tours-sharm-prod --format '{{.Names}}'
docker volume ls --filter name=safari-tours-sharm --format '{{.Name}}'
ls -la /srv/safari-tours-sharm 2>/dev/null
```

  أي حجم/بيانات موجودة ← **STOP**: هذه ترقية لا تثبيت أول؛ خطة ترقية + bundle backup أولًا.

- [ ] المنفذ المرشح حر: `sudo ss -ltn '( sport = :58080 )'` لا يرجع listener.

## 2. Baseline الخيمة (قبل) — بدون أسرار

```bash
B=$SAFARI_ROOT/shared/evidence/elkheima-baseline-before-$(date -u +%Y%m%dT%H%M%SZ).txt
sudo mkdir -p "$(dirname "$B")"
{
  date -u
  docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.RunningFor}}' | grep -vi safari
  docker inspect --format '{{.Name}} started={{.State.StartedAt}} restarts={{.RestartCount}}' \
    $(docker ps -q) | grep -vi safari
  curl -sS -o /dev/null -w 'elkheima %{http_code} %{ssl_verify_result} %{time_total}\n' \
    https://<ELKHEIMA_DOMAIN>/
  # إذا للخيمة سجل AAAA: نفس الفحص عبر IPv6
  [ -n "$(dig +short AAAA <ELKHEIMA_DOMAIN>)" ] && curl -6 -sS -o /dev/null \
    -w 'elkheima-v6 %{http_code} %{ssl_verify_result}\n' https://<ELKHEIMA_DOMAIN>/
  echo | openssl s_client -connect <ELKHEIMA_DOMAIN>:443 -servername <ELKHEIMA_DOMAIN> 2>/dev/null \
    | openssl x509 -noout -subject -enddate -fingerprint -sha256
  sudo nginx -T 2>/dev/null | sha256sum
} | sudo tee "$B" >/dev/null
```

`<ELKHEIMA_DOMAIN>` يعطيه محمد `[!]`. نفس الأوامر تعاد بعد كل مرحلة (§12) وتقارن.

## 3. GO مكتوب قبل أي تغيير `[!]`

خطة التغيير التي يوافق عليها محمد صراحة: release ID، الوضع ENQUIRY_ONLY،
المنفذ، ملفات vhost الجديدة فقط، **graceful reload للبوابة المشتركة** (مرة لـACME،
مرة للتفعيل)، **reload تلقائي عند تجديد شهادة Safari** (deploy hook — §10.2، بند GO إلزامي)،
تأكيد عدم وجود subdomain يعمل HTTP فقط قبل أي `includeSubDomains` لـHSTS، بريد الشهادة، مكان backup خارج السيرفر، قناة التنبيه، وقبول
استثناءات dependency audit. بدون GO مكتوب: لا شيء بعد هذا السطر.

## 4. المجلدات والصلاحيات

```bash
sudo install -d -m 0750 -o <DEPLOY_USER> -g <DEPLOY_USER> \
  $SAFARI_ROOT $SAFARI_ROOT/releases $SAFARI_ROOT/shared
sudo install -d -m 0700 -o <DEPLOY_USER> -g <DEPLOY_USER> \
  $SAFARI_ROOT/backups $SAFARI_ROOT/shared/evidence $SAFARI_ROOT/shared/logs
sudo install -d -m 0755 $SAFARI_ROOT/shared/maintenance
sudo install -d -m 0755 /var/www/safari-tours-sharm-acme
```

`<DEPLOY_USER>` مستخدم ينتمي لمجموعة docker، ليس root ولا مستخدم الخيمة.

## 5. نقل الإصدار والصور (بلا build على VPS)

على جهاز البناء (ليس VPS)، من نفس SHA المسجل في `RELEASE_READINESS.md`:

```bash
git checkout <FINAL_SHA> && git status --porcelain   # يجب أن يكون فارغًا
docker build -f infrastructure/docker/safari-backend.Dockerfile -t safari-tours-sharm-backend:$RID .
docker build -f infrastructure/docker/safari-erp.Dockerfile     -t safari-tours-sharm-erp:$RID .
docker build -f infrastructure/docker/safari-site.Dockerfile    -t safari-tours-sharm-site:$RID .
docker image inspect --format '{{.RepoTags}} {{.Id}} {{.Architecture}}' \
  safari-tours-sharm-backend:$RID safari-tours-sharm-erp:$RID safari-tours-sharm-site:$RID \
  | tee images-$RID.txt
docker save safari-tours-sharm-backend:$RID safari-tours-sharm-erp:$RID \
  safari-tours-sharm-site:$RID | gzip > safari-tours-sharm-images-$RID.tar.gz
git archive --format=tar.gz -o safari-tours-sharm-release-$RID.tar.gz <FINAL_SHA> \
  infrastructure/compose/safari-tours-sharm.compose.yaml \
  infrastructure/compose/safari-tours-sharm.production.yaml \
  infrastructure/nginx/nginx.conf scripts/safari-ops clients/safari-tours-sharm/deployment
sha256sum safari-tours-sharm-*-$RID.tar.gz images-$RID.txt > SHA256SUMS-$RID
```

مسار backend البديل `safari-backend-from-jar.Dockerfile` مقبول فقط بقرار reviewer
مسجل (provenance + نفس SHA + `JAR_SHA256`). بديل registry: push ثم استخدم
`name@sha256:<digest>` في `SAFARI_*_IMAGE`.

على الـVPS:

```bash
install -d -m 0750 "$REL"
# انقل الملفات الأربعة إلى $REL (scp/rsync بالوصول المعتمد)
cd "$REL" && sha256sum -c SHA256SUMS-$RID
tar -xzf safari-tours-sharm-release-$RID.tar.gz -C "$REL"
gunzip -c safari-tours-sharm-images-$RID.tar.gz | docker load
docker image inspect --format '{{.RepoTags}} {{.Id}} {{.Architecture}}' \
  safari-tours-sharm-backend:$RID safari-tours-sharm-erp:$RID safari-tours-sharm-site:$RID \
  | diff - images-$RID.txt && echo IMAGE_IDS_MATCH
# صور طرف ثالث مثبتة بالـdigest (postgres/nginx) + alpine لسكربتات النسخ:
docker pull public.ecr.aws/docker/library/postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15
docker pull public.ecr.aws/docker/library/nginx:1.30.4-alpine@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46
docker pull public.ecr.aws/docker/library/alpine:3.20
```

قبل `docker load` تحقق أن مساحة `/var/lib/docker` تكفي (الصور ~1–1.5GB). لا
تحذف صورًا أخرى لتوفير مساحة بدون موافقة.

## 6. ملف البيئة الخاص

```bash
install -m 0600 "$REL/clients/safari-tours-sharm/deployment/.env.production.example" "$ENVF"
${EDITOR:-vi} "$ENVF"     # املأ كل CHANGE_ME_* على السيرفر نفسه
grep -n 'CHANGE_ME' "$ENVF" && echo 'STOP: placeholders remain' || echo ENV_FILLED
```

- كلمة سر PostgreSQL تولد على السيرفر (`openssl rand -base64 36 | tr -d '/+=' | cut -c1-40`).
- `SAFARI_*_IMAGE` = الوسوم المحملة بالـ`$RID`؛ `SAFARI_EDGE_PORT` = المنفذ المختار.
- Paymob كله فارغ (تثبيت جديد بلا مدفوعات)؛ `NOTIFICATIONS_ENABLED=false` حتى SMTP معتمد.
- حدود CPU/RAM: Codex يضبطها من `free -m`/`nproc` بحيث يبقى للخيمة هامشها الحالي
  (الافتراضي ~2.5GB RAM لكل Safari). لا تغيّر حدود الخيمة.

تحقق المنافذ المحلولة **بدون طباعة الأسرار**:

```bash
DC config --quiet && echo CONFIG_OK
DC config --format json | python3 -c '
import json,sys; c=json.load(sys.stdin)
assert c["name"]=="safari-tours-sharm-prod", c["name"]
bad=[]
for n,s in c["services"].items():
    for p in s.get("ports",[]):
        print(n, p.get("host_ip"), p.get("published"), p.get("target"))
        if p.get("host_ip")!="127.0.0.1" or n!="edge": bad.append(n)
    assert "build" not in s, n
print("volumes", sorted(v["name"] for v in c["volumes"].values()))
sys.exit("NON-LOOPBACK OR UNEXPECTED PORT: %s"%bad if bad else 0)'
```

المتوقع: سطر واحد `edge 127.0.0.1 58080 8080` فقط، والحجمان بالأسماء أعلاه.
(تم إثبات ذلك محليًا بقيم placeholder — `RELEASE_READINESS.md` §4.)

## 7. التشغيل على loopback فقط

```bash
DC up -d --wait postgres
DC up -d --wait backend          # Flyway يطبق V1..V33 على قاعدة فارغة عند البدء
DC logs --no-color backend | grep -E 'Successfully applied|Migrating schema|ERROR' | tail -30
```

تحقق تاريخ Flyway (أعداد فقط):

```bash
docker exec -i "$(docker ps -q --filter label=com.docker.compose.project=safari-tours-sharm-prod \
  --filter label=com.docker.compose.service=postgres)" \
  sh -c 'psql -X -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT string_agg(version, ',' ORDER BY installed_rank) FROM public.flyway_schema_history WHERE success;
SELECT count(*) FILTER (WHERE NOT success) AS failed FROM public.flyway_schema_history;
SQL
```

المتوقع: `1,2,3,14,16,17,…,33` (22 إصدارًا) و`failed=0`. أي اختلاف = STOP.

### 7.1 أول مدير (bootstrap-admin) — مرة واحدة

```bash
DC run --rm -it --no-deps backend --spring.profiles.active=bootstrap-admin
```

- يحتاج TTY تفاعليًا؛ البريد وكلمة السر يكتبهما محمد (أو من يفوضه) مباشرة في
  الطرفية — لا argument ولا env ولا chat. الحساب الاصطناعي `e2e-staff@example.com` ممنوع.
- البروفايل لا يعمل إلا بهذا الأمر الصريح (`web-application-type: none`، يخرج بعد
  الإنشاء)، ويرفض التشغيل مرة ثانية إذا وجد أي مستخدم. **التعطيل بعده** = لا تمرر
  البروفايل أبدًا؛ تحقق: `docker inspect --format '{{.Args}}' $(DC ps -q backend)` لا
  يحتوي `bootstrap-admin`، ولا يوجد `SPRING_PROFILES_ACTIVE` في `$ENVF`.
- بقية الموظفين والأدوار تنشأ من ERP (`identity:user-manage`/`role-manage`).

### 7.2 بقية الخدمات

```bash
DC up -d --wait
DC ps --format '{{.Service}}\t{{.Status}}\t{{.Ports}}'
sudo ss -ltnp | grep ":$EDGE_PORT"     # يجب 127.0.0.1:$EDGE_PORT فقط
```

### 7.3 وضع الاستفسار

```bash
curl -fsS http://127.0.0.1:$EDGE_PORT/api/v1/tours-operator/sales-status \
  -H 'Host: safaritourssharm.com'
```

المتوقع `bookingMode=ENQUIRY_ONLY` والأعلام الفعلية للحجز/الدفع مغلقة. لاحقًا
`health-check.sh` سيعطي WARN للمبيعات المغلقة — متوقع في هذا الوضع.

## 8. بيانات الكتالوج المعتمدة (لا قاعدة اختبار)

- [x] الكتالوج المعتمد من المالك (30 رحلة، أسعار، مراجعة 30 سبتمبر وخيارات الوحدات)
  يدخل تلقائيًا عبر migrations البيانات `V17` و`V25` من
  `content-research/approved-catalog.json` — لا استيراد يدوي ولا نسخ DB.
- [ ] المحتوى المترجم والصور: الموظف يحرر DRAFT في ERP ثم ينشر (`content:publish`)،
  والصور ترفع عبر ERP (`media:upload`) **فقط** لما حقوقه موثقة. الأصول القديمة في
  `legacy-wordpress-export.json` معلمة `UNVERIFIED` / `NOT_SELECTED` ولا تنقل.
- [ ] التكاليف/السائقين: `clients/safari-tours-sharm/owner-data/import_costs.py` يعمل
  dry-run افتراضيًا ثم `--apply` عبر staff API فقط، بحساب موظف له
  `tours-operator.cost:manage`، والبيانات من env (`SAFARI_API_BASE=https://staff.safaritourssharm.com`
  بعد التفعيل). البنود المعلمة flagged تنتظر محمد `[!]`.
- ممنوع: dump من بيئة محلية/E2E، صور اصطناعية، حسابات `example.com`.

## 9. Smoke داخلي على loopback (قبل أي زائر)

```bash
H=http://127.0.0.1:$EDGE_PORT
curl -fsS $H/healthz
for l in en ar ru it; do
  printf '%s ' $l; curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: safaritourssharm.com' $H/$l/tours
done
curl -s -H 'Host: safaritourssharm.com' $H/robots.txt | head -5        # Sitemap: https://safaritourssharm.com/...
curl -s -H 'Host: safaritourssharm.com' $H/en/tours | grep -o 'rel="canonical" href="[^"]*"' | head -1
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: staff.safaritourssharm.com' $H/login       # 200
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: staff.safaritourssharm.com' \
  $H/api/v1/tours-operator/staff/tours                                              # 401
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: safaritourssharm.com' $H/api/v1/identity/me # 404
```

القائمة الكاملة والأدلة: `VERIFICATION_CHECKLIST.md` §A.

> **تنبيه 7 أكتوبر:** قراءة إعداد الخيمة محليًا تشير إلى أن بوابة 80/443 **حاوية
> nginx الخاصة بالخيمة** (`resort-os-prod`، `read_only`)، لا nginx على المضيف.
> لو أكد الـpreflight ذلك فهذا القسم لا ينطبق كما هو: اتبع الخيار (أ) في
> `../handoff/2026-10-07_CLAUDE_DEPLOY_AGENT_BRIEF_AR.md` §3 بعد GO محمد.

## 10. البوابة: ACME ثم الشهادة ثم التفعيل (reload مشترك — بعد GO)

نسخة احتياطية من ملفات البوابة (قبل أي ملف جديد):

```bash
sudo tar -czf $SAFARI_ROOT/backups/gateway-nginx-before-$(date -u +%Y%m%dT%H%M%SZ).tgz /etc/nginx
```

### 10.1 ملف ACME المؤقت

```bash
N=$REL/clients/safari-tours-sharm/deployment/nginx
sudo install -m 0644 $N/safari-tours-sharm-proxy.inc /etc/nginx/snippets/safari-tours-sharm-proxy.inc
sudo install -m 0644 $N/safaritourssharm.com.acme-bootstrap.conf /etc/nginx/conf.d/safaritourssharm.com.conf
# (أو sites-available + symlink حسب تخطيط البوابة المكتشف في §1)
sudo nginx -t && sudo systemctl reload nginx      # graceful reload فقط، ليس restart
```

هذا الملف لا يغيّر ما يراه الزائر (نفس التحويل لـHTTPS)، فقط يخدم مسار التحدي.

### 10.2 الشهادة (webroot، بدون إيقاف 80، بدون installer)

```bash
A=/var/www/safari-tours-sharm-acme/.well-known/acme-challenge
sudo install -d -m 0755 "$A"
echo ok | sudo tee "$A/probe" >/dev/null
for h in safaritourssharm.com www.safaritourssharm.com staff.safaritourssharm.com; do
  curl -fsS http://$h/.well-known/acme-challenge/probe; done            # ok ×3
sudo rm "$A/probe"
sudo certbot certonly --webroot -w /var/www/safari-tours-sharm-acme \
  --cert-name safaritourssharm.com \
  -d safaritourssharm.com -d www.safaritourssharm.com -d staff.safaritourssharm.com \
  --email <OWNER_CERT_EMAIL> --agree-tos --no-eff-email \
  --deploy-hook 'nginx -t && systemctl reload nginx' --dry-run
# بعد نجاح dry-run، نفس الأمر بدون --dry-run
sudo certbot certificates | grep -A3 'Certificate Name: safaritourssharm.com'
```

- شهادة مستقلة باسم `safaritourssharm.com`؛ لا `--expand` لشهادة الخيمة ولا `--nginx`.
- التجديد: مؤقت certbot الموجود يجدد بنفس webroot. الـ`--deploy-hook` أعلاه يحفظ
  `renew_hook = nginx -t && systemctl reload nginx` **في
  `/etc/letsencrypt/renewal/safaritourssharm.com.conf` فقط** — يعمل فقط عند تجديد شهادة
  Safari فعليًا (كل ~60 يومًا)، graceful reload للبوابة المشتركة لا restart. هذا **بند GO
  إلزامي** (§3): بدون موافقة محمد لا تصدر الشهادة. لا تعدل `cli.ini` العام ولا renewal conf الخيمة.
  تحقق: `sudo grep -n renew_hook /etc/letsencrypt/renewal/safaritourssharm.com.conf` ثم
  `sudo certbot renew --dry-run --cert-name safaritourssharm.com` (dry-run لا ينفذ الـhook).

### 10.3 التفعيل الكامل

```bash
sudo install -m 0644 $N/safaritourssharm.com.conf /etc/nginx/conf.d/safaritourssharm.com.conf
# عدّل: منفذ upstream لو ≠ 58080، وصيغة listen 443 لتطابق البوابة (انظر رأس الملف)
sudo nginx -t && sudo systemctl reload nginx
```

بديل الصيانة (503/noindex/Retry-After، لا يكشف التطبيق) =
`safaritourssharm.com.maintenance.conf` + `maintenance/index.html` في
`$SAFARI_ROOT/shared/maintenance/` — **بموافقة منفصلة** فقط. لا تفعل ملفين لنفس الأسماء.

## 11. تحقق خارجي (بشهادة سليمة، بدون `-k`)

من شبكة خارج الـVPS:

```bash
for h in safaritourssharm.com www.safaritourssharm.com staff.safaritourssharm.com; do
  echo | openssl s_client -connect $h:443 -servername $h 2>/dev/null | openssl x509 -noout -subject -ext subjectAltName -enddate
done
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' 'https://www.safaritourssharm.com/ar/tours?x=1'  # 301 → apex/ar/tours?x=1
curl -sS -o /dev/null -w '%{http_code} %{redirect_url}\n' http://safaritourssharm.com/en/tours            # 301 → https
curl -sSI https://staff.safaritourssharm.com/login | grep -i x-robots-tag                                  # noindex
```

ثم `VERIFICATION_CHECKLIST.md` §B كاملة. اختبار rate-limit لكل زائر: طلبات
login خاطئة من شبكتين مختلفتين — كل شبكة لها حدها (5/دقيقة) ولا تتشاركان.

## 12. Baseline الخيمة (بعد) — بعد كل reload

أعد أوامر §2 إلى `elkheima-baseline-after-<time>.txt` وقارن: نفس الحاويات، نفس
`StartedAt`/`RestartCount`، نفس بصمة الشهادة، 200 على دومينها. **الفرق الوحيد
المقبول**: hash `nginx -T` (ملف Safari أضيف). أي تأثر للخيمة ← §15 فورًا.

## 13. الجدولة: backup + health

```bash
gpg --import <OWNER_BACKUP_PUBLIC_KEY.asc>     # المفتاح العام فقط؛ الخاص عند المالك
crontab -e    # للمستخدم <DEPLOY_USER>
```

```cron
SAFARI_COMPOSE_PROJECT=safari-tours-sharm-prod
SAFARI_BACKUP_DIR=/srv/safari-tours-sharm/backups
SAFARI_HEALTH_URL=http://127.0.0.1:58080
SAFARI_PUBLIC_HOST=safaritourssharm.com
SAFARI_BACKUP_GPG_RECIPIENT=<OWNER_BACKUP_KEY_ID>
SAFARI_ALERT_COMMAND=<OWNER_APPROVED_ALERT_COMMAND>
15 2 * * *   cd /srv/safari-tours-sharm/current && scripts/safari-ops/bundle-backup.sh >> /srv/safari-tours-sharm/shared/logs/backup.log 2>&1
*/5 * * * *  cd /srv/safari-tours-sharm/current && scripts/safari-ops/health-check.sh > /dev/null
```

لا cron لـ`bundle-restore-drill.sh` على الـVPS: النسخ مشفرة لمفتاح خاص **غير موجود** على
السيرفر، فالـdrill هناك سيفشل دائمًا. الـdrill الأسبوعي يتم خارج السيرفر (§14).

`current` = `ln -sfn "$REL" $SAFARI_ROOT/current` (للسكربتات فقط؛ الـsymlink لا
يبدّل صورة — Compose يشغّل الوسم المكتوب في `$ENVF`). نسخة خارج السيرفر يوميًا
إلى الوجهة التي يحددها محمد `[!]`. دوّر سجلات `shared/logs` (logrotate أسبوعي).

## 14. أول restore drill (إلزامي قبل إعلان الإطلاق)

```bash
cd $SAFARI_ROOT/current
SAFARI_COMPOSE_PROJECT=safari-tours-sharm-prod SAFARI_BACKUP_DIR=$SAFARI_ROOT/backups \
  SAFARI_BACKUP_GPG_RECIPIENT=<OWNER_BACKUP_KEY_ID> scripts/safari-ops/bundle-backup.sh
SAFARI_COMPOSE_PROJECT=safari-tours-sharm-prod scripts/safari-ops/verify-live-media.sh
SAFARI_COMPOSE_PROJECT=safari-tours-sharm-prod SAFARI_HEALTH_URL=http://127.0.0.1:$EDGE_PORT \
  SAFARI_BACKUP_DIR=$SAFARI_ROOT/backups SAFARI_PUBLIC_HOST=safaritourssharm.com \
  scripts/safari-ops/health-check.sh
```

### 14.1 الـdrill خارج السيرفر (أول مرة ثم أسبوعيًا)

المكان: جهاز يملكه/يعتمده محمد عليه Docker وgpg والمفتاح الخاص (`[!]` اختيار الجهاز).
لا يُنسخ المفتاح الخاص إلى الـVPS أبدًا.

```bash
# على الجهاز الآمن
BUNDLE=<STAMP>.bundle
mkdir -m 0700 -p ~/safari-drill/backups && cd ~/safari-drill
rsync -a <DEPLOY_USER>@187.6.167.233:/srv/safari-tours-sharm/backups/$BUNDLE backups/
#   (أو من نسخة off-server اليومية نفسها — أفضل: يثبت أن النسخة الخارجية سليمة)
git -C <repo> archive <FINAL_SHA> scripts/safari-ops infrastructure/compose/safari-tours-sharm.compose.yaml | tar -x
# الـdrill يأخذ صورة postgres من حاوية postgres جارية لنفس اسم المشروع:
printf 'WEGO_POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 16)" > drill.env
docker compose -p safari-drill-offsite --env-file drill.env \
  -f infrastructure/compose/safari-tours-sharm.compose.yaml up -d --wait postgres
SAFARI_COMPOSE_PROJECT=safari-drill-offsite SAFARI_BACKUP_DIR=$PWD/backups \
  scripts/safari-ops/bundle-restore-drill.sh backups/$BUNDLE     # يفك التشفير بمفتاح المالك (gpg-agent)
docker compose -p safari-drill-offsite --env-file drill.env \
  -f infrastructure/compose/safari-tours-sharm.compose.yaml down -v   # مشروع الـdrill المحلي فقط
```

الناتج `backups/bundle-drill-<UTC>.json` وفيه `"ok": true`. **ربطه بالمراقبة:**
`health-check.sh` يقرأ أحدث `bundle-drill-*.json` (أو `drill-*.json`) داخل `SAFARI_BACKUP_DIR`
على الـVPS، ويتحقق من `"ok": true` ومن عمر الملف (mtime) ≤ `SAFARI_DRILL_MAX_AGE_DAYS` (8).
بعد نجاح الـdrill فقط انسخ التقرير (بدون `-p` حتى يكون mtime وقت النسخ، أي وقت الإثبات):

```bash
scp backups/bundle-drill-<UTC>.json <DEPLOY_USER>@187.6.167.233:/srv/safari-tours-sharm/backups/
ssh <DEPLOY_USER>@187.6.167.233 chmod 600 /srv/safari-tours-sharm/backups/bundle-drill-<UTC>.json
```

drill فاشل ← لا تنسخ تقريرًا ناجحًا؛ انسخ تقرير الفشل (`"ok": false`) حتى يعطي health-check
FAIL وينبه. بدون drill لمدة > 8 أيام ← WARN متوقع ويجب أن يصل للتنبيه.
- اختبار الاسترجاع الكامل (DB **وحجم جديدين** + تشغيل الإصدار فوقهما + فحص روابط
  approved وخصوصية DRAFT): الأفضل خارج الـVPS. لو على الـVPS فبمشروع منفصل
  (`-p safari-restore-test`، منفذ loopback مختلف، حجوم بأسماء مختلفة) بعد فحص الموارد،
  وإزالته بأمر **مقيد بذلك المشروع فقط**. سجل المدة والنتيجة في `RELEASE_READINESS.md`.

## 15. Rollback

**تثبيت أول (لا بيانات عملاء بعد):**

1. أعد البوابة لما قبل Safari: احذف/عطّل ملف `safaritourssharm.com.conf` فقط →
   `sudo nginx -t && sudo systemctl reload nginx` → baseline الخيمة (§12).
2. `DC stop` (لا `down -v`). الحجوم تبقى حتى قرار محمد.

**بعد وجود بيانات (ترقيات لاحقة)** — حسب `docs/operations/SAFARI_TOURS_SHARM_OPERATIONS.md` §4–§5:

- لا migration جديدة → شغّل الوسم السابق (`SAFARI_*_IMAGE`) و`DC up -d --wait`.
- migration جديدة طُبقت → Flyway للأمام فقط: **forward-fix** بإصدار جديد، أو
  استرجاع bundle ما قبل الترقية إلى DB/حجم جديدين بعد drill ناجح، مع تسوية أي
  تحصيل/مدفوعات تمت بعد النسخة. لا تشغّل backend قديمًا على schema أحدث. لا تحذف القاعدة الجديدة.
- الصيانة أثناء ذلك: ملف maintenance (بموافقة) بدل كشف تطبيق نصف مرقّى.

## 16. شروط STOP (قف وبلغ محمد)

- أي خطوة تتطلب تعديل/إيقاف/إعادة تشغيل الخيمة أو شهادتها أو ملفاتها، أو restart لـDocker/nginx (reload فقط مسموح بعد GO).
- هوية السيرفر/معماريته غير مؤكدة؛ البوابة داخل container أو ليست nginx؛ مالك 80/443 غير واضح.
- الموارد (RAM/disk) لا تكفي بحدود Safari مع هامش الخيمة.
- SHA أو image IDs لا تطابق `RELEASE_READINESS.md`؛ BLOCKING review مفتوح.
- منفذ منشور غير `127.0.0.1:$EDGE_PORT` في `docker ps`/`ss`.
- منفذ الـedge غير متطابق في الأماكن الأربعة: `SAFARI_EDGE_PORT` في `$ENVF`، `SAFARI_HEALTH_URL`
  في crontab، upstream البوابة (`server 127.0.0.1:<port>`)، ونتيجة `ss`.
- البوابة/الخيمة لا تستمع على `[::]` بسيرفراتها بينما يُطلب تفعيل IPv6 لسفاري، أو حالة IPv6 غير واضحة.
- DNS لأسماء Safari يمر عبر CDN/proxy (A ≠ IP الـVPS) بدون قرار trusted ranges.
- Flyway history ≠ القائمة المتوقعة أو `failed>0`؛ تثبيت Safari سابق موجود.
- `nginx -t` يفشل؛ شهادة لا تطابق الأسماء الثلاثة؛ الحاجة لـ`-k`.
- backup/drill (خارج السيرفر) يفشل؛ لا GO لـdeploy hook التجديد؛ placeholders باقية؛ استثناءات audit بلا إقرار محمد.
- أي تغير في baseline الخيمة.

## 17. ما يسلمه Codex بعد النشر

release ID + image IDs، مسار ملفات البوابة المضافة، خلاصة baseline قبل/بعد، نتائج
`VERIFICATION_CHECKLIST.md` (evidence index)، توقيت أول backup/drill، وموعد انتهاء الشهادة.
