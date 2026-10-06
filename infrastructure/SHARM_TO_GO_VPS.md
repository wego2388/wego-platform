# Sharm To Go — single-VPS deployment runbook

Topology (`compose/sharm-to-go.compose.yaml`): PostgreSQL → `sharm-to-go`
backend → customer site + staff ERP → Nginx edge (rate limits, CSP,
hostname-split ports) → Caddy (automatic HTTPS, `tls` profile).

| Public hostname | Caddy → edge port | Serves |
|---|---|---|
| `STG_SITE_DOMAIN` (and `www.`) | 8080 | Customer site; the staff API is **not** reachable here |
| `STG_ADMIN_DOMAIN` | 8081 | Staff ERP + `/api/`, login rate-limited, `noindex` |

## Before the first start

1. A VPS with Docker Engine + the Compose plugin, ports 80/443 open.
2. Two DNS `A` records (site apex and admin subdomain, plus `www`) pointing at it.
3. `cp .env.sharm-to-go.example .env.sharm-to-go`, then set a generated
   `STG_POSTGRES_PASSWORD` and the two real domains. The file is git-ignored;
   never commit it.

## Start

```bash
docker compose --env-file .env.sharm-to-go \
  -f infrastructure/compose/sharm-to-go.compose.yaml \
  --profile tls up --build --wait -d
```

Without `--profile tls` the stack runs on loopback only
(`127.0.0.1:58180` = site, `127.0.0.1:58181` = admin) — useful for checking a
build before DNS exists.

## Create the first staff admin (once)

The bootstrap deliberately needs an interactive terminal; the password is never
read from an argument or environment variable:

```bash
docker compose --env-file .env.sharm-to-go \
  -f infrastructure/compose/sharm-to-go.compose.yaml \
  run --rm -it backend --spring.profiles.active=bootstrap-admin
```

It refuses to run again once any user exists.

## Verify

```bash
curl -fsS https://<site domain>/robots.txt          # Sitemap line shows the real domain
curl -fsS https://<site domain>/sitemap.xml
curl -sI https://<admin domain>/login               # 200, CSP + X-Robots-Tag: noindex
curl -s -o /dev/null -w '%{http_code}\n' https://<site domain>/api/v1/identity/me   # 404
```

## Update / roll back

```bash
git pull && docker compose ... up --build --wait -d      # migrations apply on start
```

Flyway migrations are forward-only: roll back application code by checking out
the previous release and rebuilding; do not edit an applied migration.

## Backup and restore drill

```bash
scripts/sharm-to-go-backup.sh
```

Dumps the real `postgres` service (via `docker compose exec`) to a local,
timestamped, `pg_dump --format=custom` file under `backups/sharm-to-go/`
(override with `STG_BACKUP_DIR`), keeping the most recent 14 by default
(`STG_BACKUP_RETENTION`). **Local only** — this VPS has nowhere else to put
it yet; see "Not covered here" below. A backup nobody has ever restored is
not a real backup, so rehearse it against a throwaway container, never the
live database:

```bash
scripts/sharm-to-go-restore-drill.sh backups/sharm-to-go/sharm-to-go-<timestamp>.pgdump
```

**Encryption (opt-in, `STG_BACKUP_PASSPHRASE`):** a dump contains customer
PII (names, phone numbers, emails). Set `STG_BACKUP_PASSPHRASE` before
running the backup script and every dump is symmetrically encrypted
(GPG, AES256) to `sharm-to-go-<timestamp>.pgdump.gpg`, with the plaintext
removed immediately after. Store the passphrase in a password manager, not
on this VPS — losing it makes every encrypted backup permanently
unreadable. The restore drill decrypts automatically when given a `.gpg`
file and the same `STG_BACKUP_PASSPHRASE`:

```bash
STG_BACKUP_PASSPHRASE='...' scripts/sharm-to-go-restore-drill.sh backups/sharm-to-go/sharm-to-go-<timestamp>.pgdump.gpg
```

Boots a throwaway `postgres:18.4-alpine` container, restores the file into
it, confirms the `wego` schema actually has tables, then tears the
container down. **Verified for real** (not just read): run end to end
against a real Postgres container with a seeded table and rows, confirming
both the table and the exact row contents survive the dump/restore
round-trip, and that the throwaway container is removed afterward either
way (success or failure).

Neither script is wired to a schedule (cron/systemd timer) yet — see below.

## Health check

```bash
scripts/sharm-to-go-health.sh <site-domain> <admin-domain>
```

Runs the same four checks this runbook's own "Verify" section above curls
by hand, as one repeatable, scriptable command: the public site's
`robots.txt` and `sitemap.xml`, the backend (reached indirectly through the
site's own catalog proxy — a 502 there means the backend is unreachable,
not just that the site itself is up), and the staff ERP's login page.
Prints `OK`/`FAIL` per check and exits non-zero if anything failed, so it
can be wired into cron or any alerting channel later — it sends no alert
itself; see "Not covered here" below. **Verified for real**: run against a
real local HTTP server for all three failure modes (unreachable host, wrong
response content, and a mixed pass/fail run), plus the full success path.

## Not covered here (owner/ops decisions still open)

Off-box backup storage (S3, rsync to a second box, etc. — the backup
script above produces a local file only), a backup/health-check schedule
(cron/systemd timer), an actual alerting channel for the health check to
notify (Slack/email/etc.), log shipping, off-box secret storage, and
payment-provider secrets (no payment integration exists yet — see
`clients/sharm-to-go/design/PAYMENT_FOUNDATION.md`).
