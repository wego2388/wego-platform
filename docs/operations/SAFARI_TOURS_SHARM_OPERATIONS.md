# Safari Tours Sharm — operations runbook

Client-specific procedures on top of `ISOLATED_CLIENT_RELEASES.md`. Every
command targets one Compose project; set it once per shell:

```bash
export SAFARI_COMPOSE_PROJECT=wego-safari-tours-sharm
export SAFARI_BACKUP_DIR=/var/backups/safari-tours-sharm
export SAFARI_HEALTH_URL=http://127.0.0.1:58085   # the edge's loopback port
```

The scripts live in `scripts/safari-ops/`. They find containers by Compose
labels and read the database name/user from the container itself, so no
password is typed, stored or logged. None of them prints customer data.

## 1. Emergency stop for online sales

ERP → **Online sales** (managers, permission `tours-operator.tour:manage`):

- **Pause new online bookings** — the website refuses new bookings
  (`503 bookings_paused`) and points visitors to WhatsApp.
- **Pause online payments** — no customer is sent to the card page
  (`503 payments_paused`), including resuming an earlier checkout.
- **Pause everything now** — both at once.

Not affected, on purpose: Paymob webhooks (money already moving is recorded),
expiry of unpaid bookings (places are released), and all staff work. Every
staff page shows a red banner while anything is paused, and the health check
reports it as `WARN`. Use it when: payments fail or double, prices are wrong,
the site misbehaves, or before maintenance. Resume from the same page.

From a shell, if the ERP is unreachable (same effect; record who and why):

```bash
docker exec -i "$(docker ps -q --filter label=com.docker.compose.project=$SAFARI_COMPOSE_PROJECT --filter label=com.docker.compose.service=postgres)" \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
INSERT INTO wego.tours_operator_sales_control (id, bookings_paused, payments_paused, reason, updated_at)
VALUES (1, true, true, 'shell pause', now())
ON CONFLICT (id) DO UPDATE SET bookings_paused = true, payments_paused = true,
  reason = 'shell pause', updated_by_user_id = NULL, updated_at = now();
SQL
```

## 2. Backups

`scripts/safari-ops/backup.sh` — `pg_dump` custom format, verified with
`pg_restore --list` before it is kept, plus a `.json` with SHA-256, Flyway
version and per-table row counts. Set `SAFARI_BACKUP_GPG_RECIPIENT` to a
public key whose private half is **not** on the server, so a stolen server
does not expose the backups; without it the script refuses to run unless
`SAFARI_ALLOW_PLAIN_BACKUP=1` is set (local drills only). Retention: `SAFARI_BACKUP_KEEP_DAYS` (default 14).

Schedule (crontab of the deploy user):

```cron
15 2 * * *  cd /srv/safari && scripts/safari-ops/backup.sh >> /var/log/safari-backup.log 2>&1
40 3 * * 0  cd /srv/safari && scripts/safari-ops/restore-drill.sh >> /var/log/safari-drill.log 2>&1
*/5 * * * * cd /srv/safari && scripts/safari-ops/health-check.sh > /dev/null
```

Copy the backup directory off the server daily (provider snapshot or object
storage) — a backup on the same disk does not survive losing that disk.
**Owner decision still needed:** where the off-server copy goes.

## 3. Restore drill (proof that backups work)

`scripts/safari-ops/restore-drill.sh [file]` restores the newest (or the given)
backup into a **new throw-away container** with no network and no volume,
then checks the checksum, `pg_restore` success, the Flyway version, that no
migration failed, and that every table has at least the rows counted at
backup time. It writes `drill-<time>.json` (with seconds taken) next to the
backups and always removes the container. It never touches the live database.

Evidence on 2026-10-01 (local stack, small data set): backup 1 s, drill 6 s
end to end, 15 tables checked; a damaged file was refused (checksum), a
missing-rows case failed the drill, and the encrypted (GPG) round trip
passed. Repeat on the real server after go-live and record its timing here.

## 4. Real restore (incident)

1. Pause online sales (section 1).
2. Take one more backup of the current state, even if it is damaged.
3. Run the drill on the chosen backup first; continue only if it passes.
4. `docker compose ... stop backend web safari-site` — the database stays up.
5. Keep the broken database: `ALTER DATABASE <db> RENAME TO <db>_broken_<date>`
   (from the `postgres` maintenance database), then `CREATE DATABASE <db>`.
6. `pg_restore --no-owner --exit-on-error -d <db>` from the chosen backup.
7. Check Flyway history and row counts (the drill's checks), start the
   services with `--wait`, run the smoke checks (section 6), resume sales.
8. Reconcile payments made between the backup time and the incident against
   the Paymob dashboard before telling customers anything.

## 5. Upgrade and rollback

Upgrades that add migrations (V24/V25/V26 so far) are **stop-then-start**,
never rolling: an older backend must not run beside a newer schema.

1. Build and test the exact release (`release.plan.json` digest).
2. Pause online sales; wait until no checkout is in progress
   (ERP → Finance shows no PENDING payment from the last 30 minutes).
3. `backup.sh`, then `restore-drill.sh` on that backup.
4. `docker compose ... stop backend` → start the new release with `--wait`
   (Flyway migrates on start) → smoke checks → resume sales.

Rollback:

- **Same schema** (no new migration): start the previous image tag.
- **New migration already applied**: Flyway is forward-only. An older backend
  is only safe if the new migration is purely additive and ignored by it —
  true for V26 (new table only). For V24/V25 (changed tour pricing and
  places), restore the pre-upgrade backup instead (section 4), into a new
  database first, and reconcile any payments taken since.

## 6. Smoke checks after any change

```bash
scripts/safari-ops/health-check.sh
curl -fsS "$SAFARI_HEALTH_URL/en/tours" | grep -c 'href="/en/tour/'
curl -fsS "$SAFARI_HEALTH_URL/api/v1/tours-operator/sales-status"
```

Then in a browser: one tour page shows its calendar, the ERP login works, and
the Online sales page shows "open".

## 7. Edge and TLS terminator

The edge container listens only on a loopback port; the host's TLS
terminator (e.g. Caddy) forwards to it. The terminator **must overwrite**
`X-Forwarded-For` with the real client address (not append to a
client-supplied one): the edge trusts that header from private/loopback
hops for its per-visitor rate limits. After the first deploy, check from two
different networks that each gets its own limit. The customer origin only
proxies the API the public site uses; staff login and staff APIs exist only
on the staff origin. HSTS is sent on every response — serve both origins
over HTTPS only.

## 8. Monitoring

`scripts/safari-ops/health-check.sh` prints `OK/WARN/FAIL` per check and exits
1 on any `FAIL`: edge `/healthz`, every container running/healthy and not
restart-looping, online sales paused (WARN), latest backup age (≤ 26 h), last
drill passed (≤ 8 days), disk use (< 85 %), and TLS expiry (≥ 14 days, once
`SAFARI_PUBLIC_HOST` is set). `SAFARI_ALERT_COMMAND` receives the WARN/FAIL
lines on stdin — e.g. a mail or Telegram bot script. **Owner decision still
needed:** which channel receives alerts.

---

## ملخص بالعربي لمحمد

- **زرار الطوارئ:** من الـERP → «Online sales». تقدر توقف الحجز من الموقع، أو
  الدفع، أو الاتنين بضغطة. الفلوس اللي في الطريق بتتسجل عادي، والموظفين
  شغالين. وطول ما الزرار مفعّل فيه شريط أحمر في كل صفحات الـERP.
- **النسخة الاحتياطية:** كل يوم الساعة 2:15 بالليل، والملف بيتأكد إنه سليم
  قبل ما يتحفظ. ممكن يتشفّر بمفتاح مش موجود على السيرفر.
- **تجربة الاسترجاع:** كل أسبوع بنرجّع النسخة في قاعدة مؤقتة، ونتأكد إن كل
  الجداول والأرقام موجودة، وبنسجّل الوقت. النهارده محليًا: 6 ثواني.
- **المراقبة:** كل 5 دقايق بيتفحص الموقع والسيرفر والنسخة والمساحة والشهادة.
  لو في مشكلة بيبعت تنبيه.
- **محتاجين منك:** (1) فين نحفظ نسخة برّه السيرفر، (2) التنبيهات توصلك
  إزاي (إيميل/تليجرام/واتساب).
