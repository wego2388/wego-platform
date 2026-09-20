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

## Not covered here (owner/ops decisions still open)

Automated Postgres backups and restore drills, monitoring/alerting, log
shipping, off-box secret storage, and payment-provider secrets (no payment
integration exists yet — see `clients/sharm-to-go/design/PAYMENT_FOUNDATION.md`).
