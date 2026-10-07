# Safari Tours Sharm — deploy verification checklist (non-mutating)

Prepared 2026-10-07 (WEGO-016-OPS2-G) for Codex. Every check below is
**read-only** unless marked `MUTATES` — those need explicit owner approval at
the time and must use owner-approved test data only. Never use `curl -k`;
never paste tokens, passwords, customer data or full env/config into evidence.

Legend: `[ ]` not run · `[x]` passed (link evidence) · `[!]` failed / needs Mohamed.

Shell setup for the loopback section: `H=http://127.0.0.1:<SAFARI_EDGE_PORT>`,
`P='-H Host:safaritourssharm.com'`, `S='-H Host:staff.safaritourssharm.com'`.
For the external section run from a machine **outside** the VPS.

## A. Internal (loopback, before the gateway vhost)

| # | Check | Command (abridged) | Expected |
|---|---|---|---|
| A1 | Only the edge is published, loopback only | `docker ps --filter label=com.docker.compose.project=safari-tours-sharm-prod --format '{{.Names}} {{.Ports}}'`; `ss -ltnp \| grep <port>` | one `127.0.0.1:<port>->8080/tcp`; nothing on 0.0.0.0/::, no 5432/8080/3000/3001 |
| A2 | All 5 containers healthy | `docker compose … ps` | postgres, backend, web, safari-site, edge `healthy` |
| A3 | Flyway history | runbook §7 SQL | `1,2,3,14,16…33` (22 rows), `failed=0` |
| A4 | Edge health | `curl -fsS $H/healthz` | `{"status":"UP"…}` |
| A5 | Enquiry mode | `curl -s $P $H/api/v1/tours-operator/sales-status` | `bookingMode":"ENQUIRY_ONLY"`, `bookingsOpen:false`, `paymentsOpen:false` |
| A6 | Public pages EN/AR/RU/IT | `curl -s -o /dev/null -w '%{http_code}' $P $H/{en,ar,ru,it}/tours` | 200 ×4 |
| A7 | Language-less redirect | `curl -sI $P $H/tours` | 302 to a locale path, `Cache-Control: no-store` |
| A8 | Tour cards present | `curl -s $P $H/en/tours \| grep -c 'href="/en/tour/'` | ≥ number of published tours |
| A9 | robots + sitemap use real origin | `curl -s $P $H/robots.txt`; `curl -s $P $H/sitemap.xml \| head` | `Sitemap: https://safaritourssharm.com/sitemap.xml`; URLs start with `https://safaritourssharm.com/` |
| A10 | Canonical + hreflang | `curl -s $P $H/en/tours \| grep -oE 'rel="(canonical\|alternate)"[^>]*'` | `https://safaritourssharm.com/…`, en/ar/ru/it alternates |
| A11 | Staff login page | `curl -s -o /dev/null -w '%{http_code}' $S $H/login` | 200 |
| A12 | Staff API denied without auth | `curl -s -o /dev/null -w '%{http_code}' $S $H/api/v1/tours-operator/staff/tours` | 401 |
| A13 | Staff/identity API absent on public origin | `curl -s -o /dev/null -w '%{http_code}' $P $H/api/v1/identity/me` and `…/api/v1/tours-operator/staff/tours` | 404 |
| A14 | Staff app absent on public origin | `curl -s -o /dev/null -w '%{http_code}' $P $H/login` and compare body with A11 | public-site 404 page, never the ERP login markup |
| A15 | Security headers | `curl -sI $P $H/en/tours` | CSP, HSTS, X-Frame-Options, nosniff, Referrer-Policy present |
| A16 | Media (only if an approved image exists) | `curl -sI $P $H/media/tours/<slug>/<uuid>.jpg` | 200 image/jpeg; DRAFT image URL → 404 |
| A17 | Health script | `scripts/safari-ops/health-check.sh` (env per runbook §13) | only expected WARNs (sales closed in ENQUIRY_ONLY; backup/drill before first run) |

## B. External (after gateway vhost + certificate)

| # | Check | Command | Expected |
|---|---|---|---|
| B1 | Certificate SANs, chain valid | `openssl s_client -connect <h>:443 -servername <h> \| openssl x509 -noout -subject -ext subjectAltName -enddate` for the 3 names | SAN covers apex/www/staff; issuer Let's Encrypt; not El Kheima's cert |
| B2 | Strict TLS (no -k) | `curl -sS -o /dev/null -w '%{http_code} %{ssl_verify_result}' https://safaritourssharm.com/en/tours` | `200 0` |
| B3 | www → apex, path + query kept | `curl -s -o /dev/null -w '%{http_code} %{redirect_url}' 'https://www.safaritourssharm.com/ar/tours?x=1'` | `301 https://safaritourssharm.com/ar/tours?x=1` |
| B4 | HTTP → HTTPS (3 names) | `curl -s -o /dev/null -w '%{http_code} %{redirect_url}' http://<h>/en/tours` | 301 to `https://` (www → apex) |
| B5 | Staff noindex header | `curl -sI https://staff.safaritourssharm.com/login \| grep -i x-robots-tag` | `noindex, nofollow` |
| B6 | Staff API 401 / public API 404 | as A12/A13 over https | 401 / 404 |
| B7 | Repeat A6–A10, A15 over https | — | same results, canonical/sitemap `https://safaritourssharm.com` |
| B8 | Forwarded proto reaches apps as https | `curl -sI https://safaritourssharm.com/tours` → `Location` | relative or `https://` (never `http://`) |
| B9 | Per-visitor rate limit (`MUTATES` only failed-login counters of a non-existent email) | 7 wrong logins from network A, then 1 from network B with an unused email | A gets 429 after its burst; B is not limited |
| B10 | Spoofed X-Forwarded-For ignored | `curl -s -H 'X-Forwarded-For: 1.2.3.4' …` repeated as in B9 | still limited per real IP |
| B11 | Upload size path (no write) | `curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @13MB.bin https://staff…/api/v1/tours-operator/staff/categories/SEA/media/upload` | 413 (gateway) — no upload created |
| B12 | Enquiry form | **only if Mohamed approves one test enquiry** (`MUTATES` WhatsApp only): tour → date → party → enquiry link | WhatsApp link holds tour/date/slot/party only; no name/phone/email/hotel/token in URL; no seat hold |
| B13 | Browser pass | real browser EN/AR (RTL)/RU/IT on phone + desktop; ERP login with the bootstrap admin | pages render, ERP loads, no console CSP errors |

## C. El Kheima protection (before / after every gateway reload)

| # | Check | Before | After | Same? |
|---|---|---|---|---|
| C1 | El Kheima domain HTTPS status + `ssl_verify_result` | | | |
| C2 | El Kheima certificate SHA-256 fingerprint + expiry | | | |
| C3 | El Kheima containers: names, images, `StartedAt`, `RestartCount` | | | |
| C4 | El Kheima response time (3 samples) | | | |
| C5 | `nginx -T \| sha256sum` | | | changes only by the Safari file |
| C6 | Safari names no longer serve El Kheima content/cert | n/a (was El Kheima) | Safari | — |

Any difference in C1–C4 → stop, remove only the Safari vhost, reload, report (runbook §15).

## D. Evidence index (fill during deploy; keep in the team's evidence store, not /tmp)

| ID | What | File / location | SHA-256 | Time (UTC) | By |
|---|---|---|---|---|---|
| E1 | Preflight (identity, resources, listeners, gateway topology) | | | | |
| E2 | El Kheima baseline before | | | | |
| E3 | Image IDs loaded = release manifest | | | | |
| E4 | Resolved-ports check output (no secrets) | | | | |
| E5 | Flyway history query | | | | |
| E6 | Bootstrap admin created (no credentials, just success line) | | | | |
| E7 | Section A results | | | | |
| E8 | `nginx -t` + reload (ACME bootstrap) | | | | |
| E9 | certbot dry-run + issue + `certbot certificates` | | | | |
| E10 | `nginx -t` + reload (full vhost) | | | | |
| E11 | Section B results | | | | |
| E12 | El Kheima baseline after (each reload) | | | | |
| E13 | First bundle backup + drill JSON | | | | |
| E14 | Crontab (env names, no secrets) | | | | |
| E15 | Owner GO record(s) | | | | |
