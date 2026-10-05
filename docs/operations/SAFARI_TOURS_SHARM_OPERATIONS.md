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

> **V29 / WEGO-016-MEDIA (5 October 2026):** from V29 on, the managed image
> volume is part of the data. The database-only `backup.sh` /
> `restore-drill.sh` deliberately refuse V29 schemas and bundles; do not
> disable those guards and do not schedule them. Use the bundle scripts below.
> The earlier evidence in this section belongs to the pre-MEDIA stack.

`scripts/safari-ops/bundle-backup.sh` writes `<stamp>.bundle/` containing
`db.dump` (`pg_dump` custom format, checked with `pg_restore --list`),
`media.tar.gz` (the named media volume, mounted read-only, tar exit status
checked) and `manifest.json`. The manifest records SHA-256 and size of both
files, SHA-256 and size of **every file inside the media archive**, the
DB-referenced storage keys, Flyway version and per-table row counts. The
backup aborts if any `tours_operator_asset` / `_variant` key is missing from
the archive; a fresh V29 database with zero assets is valid, a failed query
is not. Files are mode 0600. Set `SAFARI_BACKUP_GPG_RECIPIENT` to a public
key whose private half is **not** on the server, so a stolen server does not
expose the backups (the media volume holds DRAFT images that are private);
without it the script refuses to run unless `SAFARI_ALLOW_PLAIN_BACKUP=1` is
set (local drills only). Retention: `SAFARI_BACKUP_KEEP_DAYS` (default 14).

Schedule (crontab of the deploy user):

```cron
15 2 * * *  cd /srv/safari && scripts/safari-ops/bundle-backup.sh >> /var/log/safari-backup.log 2>&1
40 3 * * 0  cd /srv/safari && scripts/safari-ops/bundle-restore-drill.sh >> /var/log/safari-drill.log 2>&1
*/5 * * * * cd /srv/safari && scripts/safari-ops/health-check.sh > /dev/null
```

Copy the backup directory off the server daily (provider snapshot or object
storage) — a backup on the same disk does not survive losing that disk.
**Owner decision still needed:** where the off-server copy goes.

## 3. Restore drill (proof that backups work)

`scripts/safari-ops/bundle-restore-drill.sh [bundle]` restores the newest (or
the given) bundle's database into a **new throw-away container** with no
network and no volume, then checks the bundle checksums, `pg_restore`
success, the Flyway version, that no migration failed, and that every table
has at least the rows counted at backup time. The expected media keys are
read from the **restored database** (assets and variants), not from the
manifest: every one must be in the archive, every archived file must match
the SHA-256 and size recorded in the manifest, originals must match
`tours_operator_asset.sha256`, variants must match
`tours_operator_asset_variant.file_size_bytes`, and the archive may hold no
file the manifest does not list. It writes `bundle-drill-<time>.json` next
to the backups and always removes the container. It never touches the live
database or the live media volume.

Evidence on 2026-10-01 (database-only scripts, local stack, small data set): backup 1 s, drill 6 s
end to end, 15 tables checked; a damaged file was refused (checksum), a
missing-rows case failed the drill, and the encrypted (GPG) round trip
passed. Repeat on the real server after go-live and record its timing here.

## 4. Real restore (incident)

1. Pause online sales (section 1).
2. Take one more bundle of the current state, even if it is damaged.
3. Run `bundle-restore-drill.sh` on the chosen bundle first; continue only if
   it passes. If the bundle is GPG-encrypted, decrypt `db.dump.gpg` and
   `media.tar.gz.gpg` to a private directory (mode 0700) first.
4. `docker compose ... stop backend web safari-site` — the database stays up.
5. Keep the broken database: `ALTER DATABASE <db> RENAME TO <db>_broken_<date>`
   (from the `postgres` maintenance database), then `CREATE DATABASE <db>`.
   Keep the broken media volume too: do not delete it; restore into a new one
   (or, if reusing the name, first copy the old volume aside).
6. Restore the database: `pg_restore --no-owner --exit-on-error -d <db>`.
7. Restore media **after** the database, into the named media volume
   (`<project>-media`), as root with numeric ownership so the backend user
   (10001:10001) can read it and nobody else can. DRAFT images are private
   and stay private only if modes are kept (directories 0700, files 0600):

   ```sh
   docker run --rm --network none \
     -v <project>-media:/data/media \
     -v /private/restore-dir:/in:ro \
     public.ecr.aws/docker/library/alpine:3.20 \
     sh -c 'cd /data/media && tar -xzf /in/media.tar.gz --numeric-owner'
   ```

   As root, tar keeps owner and permissions by default (the alpine/busybox
   tar has no `--same-owner`; with GNU tar add `--same-owner
   --same-permissions`). Then verify: `find /data/media -type d ! -perm 700` and
   `find /data/media -type f ! -perm 600` print nothing, and
   `find /data/media \( ! -user 10001 -o ! -group 10001 \)` prints nothing (run it
   in a throw-away container with the volume mounted `:ro`). Never extract as
   a non-root user (ownership is lost) or with a world-readable umask.
8. Verify with the drill's checks: Flyway history and row counts, and that
   every `tours_operator_asset` / `_variant` key exists in the volume with the
   recorded size and SHA-256: run `scripts/safari-ops/verify-live-media.sh`
   (read-only; also checks owner 10001:10001 and modes 0700/0600). Start the services with
   `--wait`, run the smoke checks (section 6), resume sales.
9. Reconcile payments made between the backup time and the incident against
   the Paymob dashboard before telling customers anything.

## 5. Upgrade and rollback

Upgrades that add migrations (V24/V25/V26/V27/V28 so far) are **stop-then-start**,
never rolling: an older backend must not run beside a newer schema.

1. Build and test the exact release (`release.plan.json` digest).
2. Pause online sales; wait until no checkout is in progress
   (ERP → Finance shows no PENDING payment from the last 30 minutes).
3. `bundle-backup.sh`, then `bundle-restore-drill.sh` on that bundle.
4. `docker compose ... stop backend` → start the new release with `--wait`
   (Flyway migrates on start) → smoke checks → resume sales.

Rollback:

- **Same schema** (no new migration): start the previous image tag.
- **New migration already applied**: Flyway is forward-only. An older backend
  is only safe if the new migration is purely additive and ignored by it —
  true for V26 (new table only). V27 changes a payment constraint and V28 adds
  a refund-identity table used by the matching handler; use the matching
  backend for both and do not run an older image against them. For V24/V25
  (changed tour pricing and places), V27, or V28, restore the pre-upgrade
  backup instead (section 4), into a new database first, and reconcile any
  payments taken since.

## 6. Smoke checks after any change

```bash
scripts/safari-ops/health-check.sh
curl -fsS "$SAFARI_HEALTH_URL/en/tours" | grep -c 'href="/en/tour/'
curl -fsS "$SAFARI_HEALTH_URL/api/v1/tours-operator/sales-status"
```

Then in a browser: one tour page shows its calendar, the ERP login works, and
the Online sales page agrees with the deployment mode. `ONLINE_PAYMENT` with
unpaused switches reports both effective flags open; `ENQUIRY_ONLY` reports
both closed and explains that the office confirms the request. A stored open
switch cannot enable checkout in an enquiry deployment.

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
restart-looping, online sales closed (WARN), latest backup age (≤ 26 h), last
drill passed (≤ 8 days), disk use (< 85 %), and TLS expiry (≥ 14 days, once
`SAFARI_PUBLIC_HOST` is set). `SAFARI_ALERT_COMMAND` receives the WARN/FAIL
lines on stdin — e.g. a mail or Telegram bot script. **Owner decision still
needed:** which channel receives alerts.

In `ENQUIRY_ONLY`, the current health script's sales WARN is expected: the
effective online flags are deliberately closed. Check `bookingMode` in the
public status response before treating this as an emergency pause. Other
FAIL/WARN checks (backup, disk, restart loop, TLS) still need attention.

### 8.1 Temporary launch with enquiries

Set `TOURS_OPERATOR_BOOKING_MODE=ENQUIRY_ONLY` in the private deployment env
file. Keep `TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=false`; the test override is never
a launch configuration. Entirely absent provider settings are accepted only
for an installation with zero payment records. Partial credentials or callback
URLs, unknown modes, failed history reads and any payment history without
complete real settings refuse startup. Never delete history to make it start.

With complete real Paymob configuration, the real adapter remains active for
signed historical payment/refund callbacks even in enquiry mode. New booking,
payment and resume calls return unavailable before writes/provider requests.
A disabled provider returns retryable callback 503, never a success response.

Verify the four language tour cards and booking page show an office-confirmed
WhatsApp request, with factual tour/date/slot/party selection and no customer
name, phone, email, hotel or recovery token in the link or analytics. A request
does not hold seats or create a confirmed/paid booking. Office confirmation
must check Wego availability; manual booking/collection has its own follow-up
packet and must not become a second inventory authority.

To activate online payment later, supply complete reviewed real settings,
complete the sandbox checkout/callback/refund gate and owner acceptance, then
change the mode. Old enquiries are never reclassified as paid bookings. A
rollback to enquiry mode preserves all keys required by historical payments.

## 9. Cancellation and refund procedure

Cancellation and refund are deliberately two distinct money/operations steps:

- **Cancellation only:** staff may cancel a live booking in the ERP with a
  reason. This releases its places and sends the cancellation notification,
  but it never claims that money moved. If the payment is `PAID`, the finance
  owner must still complete the provider step below.
- **Full refund:** an authorised finance owner issues the refund in Paymob's
  dashboard using the exact provider transaction and full captured amount.
  Paymob's signed webhook is the only event that changes the Wego payment to
  `REFUNDED`. If the booking is still `NEW` or `CONFIRMED`, Wego cancels it,
  releases its places, records the reason, and queues the customer message in
  the same database transaction. A repeated webhook does nothing twice.
- **Partial refund:** do not treat it as a full refund. Wego acknowledges the
  signed webhook once, keeps the original captured sale visible, changes the
  payment to `REVIEW_REQUIRED`, and writes a durable reconciliation outbox event
  with expected/received minor units. There is no dispatcher for this event yet:
  it does not automatically notify staff by email, Telegram, or a queue. Staff
  must check review-required payments in Finance and reconcile against Paymob.
  The finance owner records the case and
  reconciles it against Paymob; the current ledger does not yet model several
  partial-refund lines. If a new partial refund arrives after a full refund,
  the payment remains truthfully `REFUNDED` but Wego records a distinct
  `PARTIAL_REFUND_AFTER_REFUND` anomaly and writes the same outbox event; it is never
  silently treated as a replay. Replays are deduplicated by the provider
  transaction identity.

Before refunding, verify booking reference, customer, provider transaction,
currency, amount, and the approved cancellation tier. Never copy a card number
or customer contact into notes, URLs, screenshots, or chat. After Paymob shows
success, wait for the ERP payment state; do not manually edit PostgreSQL. If no
webhook arrives, keep the booking/payment under review and reconcile against
Paymob before retrying, to avoid a double refund.

---

## ملخص بالعربي لمحمد

- **زرار الطوارئ:** من الـERP → «Online sales». تقدر توقف الحجز من الموقع، أو
  الدفع، أو الاتنين بضغطة. الفلوس اللي في الطريق بتتسجل عادي، والموظفين
  شغالين. وطول ما الزرار مفعّل فيه شريط أحمر في كل صفحات الـERP.
- **النسخة الاحتياطية:** كل يوم الساعة 2:15 بالليل بنعمل «باندل»: قاعدة
  البيانات + مجلد الصور (`bundle-backup.sh`)، ومعاها بصمة SHA-256 وحجم لكل
  ملف صورة. النسخة بتتلغي لو أي صورة مسجلة في القاعدة ناقصة. ممكن تتشفّر
  بمفتاح مش موجود على السيرفر، والصور المسودة تفضل خاصة.
- **تجربة الاسترجاع:** كل أسبوع (`bundle-restore-drill.sh`) بنرجّع القاعدة في
  حاوية مؤقتة، ونطلع قائمة الصور المطلوبة من القاعدة المسترجعة نفسها (مش من
  الـmanifest)، ونتأكد إن كل ملف موجود وبصمته وحجمه سليمين، وإن مفيش ملف
  اتغيّر أو اتشال. استرجاع الحوادث: القاعدة الأول ثم الصور بصلاحيات
  10001:10001 (مجلدات 0700، ملفات 0600).
- **المراقبة:** كل 5 دقايق بيتفحص الموقع والسيرفر والنسخة والمساحة والشهادة.
  لو في مشكلة بيبعت تنبيه.
- **الإلغاء والاسترداد:** إلغاء الموظف يحرر الأماكن لكنه لا يدّعي أن الفلوس
  رجعت. مسؤول المالية يعمل الاسترداد من Paymob بالعملية والمبلغ الصحيحين؛
  الـwebhook الموقّع هو الذي يسجل الاسترداد. الاسترداد الكامل يلغي الحجز الحي
  أوتوماتيك ويحرر السعة، والجزئي يظهر `REVIEW_REQUIRED` للتسوية ولا يتكرر عند
  إعادة نفس الإشعار.
- **محتاجين منك:** (1) فين نحفظ نسخة برّه السيرفر، (2) التنبيهات توصلك
  إزاي (إيميل/تليجرام/واتساب).
