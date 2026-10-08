# Safari Tours Sharm — release readiness manifest

> **Superseded preparation template:** production was deployed on 2026-10-08.
> Actual source/image IDs/CI/live TLS/backup/owner login and residual risks are
> recorded in [the deployed handoff](../handoff/2026-10-08_PRODUCTION_DEPLOYED_HANDOFF_AR.md).
> Do not interpret the old empty cells or GO warnings below as current state.

Prepared 2026-10-07 by Claude (WEGO-016-OPS2-G). The remaining sections preserve
the pre-deployment template and historical checks, not a current launch verdict.

## 1. Source

| Field | Value |
|---|---|
| Final source SHA | `<final SHA>` |
| HEAD when this package was written | `32f6a1c` (OPS2-F follow-ups); OPS2-F re-check fixes F1–F5/F7 landed in `7660ca9`; package committed as `f82c92b` — CI green on `f82c92b` (run 37645085034); review follow-ups in the next commit |
| Branch | `wego-016-safari-hardening` |
| Release ID | `<RELEASE_ID>` (suggested `str-YYYY.MM.DD-<short-sha>`) |
| Board packet | WEGO-016-OPS2-G (ACTIVE) |
| `release.plan.json` digests | releaseLock `0cec7fbf…4255a`, releaseProfiles `b26d153e…21f5` (re-check on final SHA) |
| Booking mode at launch | `ENQUIRY_ONLY`, `TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=false`, Paymob settings empty (fresh install) |

## 2. Images (fill from runbook §5 on the build machine)

| Image | Tag | Image ID (`docker image inspect .Id`) | Arch | Built from |
|---|---|---|---|---|
| backend | `safari-tours-sharm-backend:<RID>` | `<sha256:…>` | `<amd64>` | `safari-backend.Dockerfile` (or `-from-jar` + `JAR_SHA256` only with reviewer acceptance) |
| ERP | `safari-tours-sharm-erp:<RID>` | `<sha256:…>` | | `safari-erp.Dockerfile` |
| site | `safari-tours-sharm-site:<RID>` | `<sha256:…>` | | `safari-site.Dockerfile` |
| postgres | `postgres:18.4-alpine@sha256:9a8afca5…de15` | pinned digest | | public.ecr.aws |
| edge | `nginx:1.30.4-alpine@sha256:97d490c1…5b46` | pinned digest | | public.ecr.aws |
| ops helper | `alpine:3.20` (backup/verify scripts; tag, not digest) | | | public.ecr.aws |

SBOM / image scan: `<tool + result, or "not available — open risk">`.

## 3. Migrations (Flyway, applied in this order on an empty DB)

| Version | File | Purpose |
|---|---|---|
| V1 | `platform/application/…/V1__platform_foundation.sql` | `wego` schema, outbox/event foundation |
| V2 | `…/V2__identity_foundation.sql` | identity users, password hash, lockout counters |
| V3 | `platform/apps/safari-tours-sharm/…/V3__identity_administration.sql` | Safari-only roles/permissions/admin model (generic identity permissions) |
| V14 | `…/V14__tours_operator_foundation.sql` | tours, slots/availability, bookings, `STR-YYYY-NNNN` references |
| V16 | `…/V16__tours_operator_catalog_content.sql` | multilingual names, tour type, image, cancellation policy columns |
| V17 | `…/data/V17__tours_operator_catalog_seed.sql` | **data:** 30 owner-approved tours (approved 2026-09-28) |
| V18 | `…/V18__tours_operator_payment.sql` | payment aggregate (one per booking), provider idempotency |
| V19 | `…/V19__tours_operator_payment_hardening.sql` | provider state hardening, callback audit snapshot |
| V20 | `…/V20__tours_operator_payment_revenue_recognition.sql` | persisted revenue recognition of a capture |
| V21 | `…/V21__tours_operator_payment_audit.sql` | append-only payment status history |
| V22 | `…/V22__tours_operator_notification.sql` | transactional customer notifications outbox |
| V23 | `…/V23__tours_operator_tour_content.sql` | localized DRAFT/PUBLISHED tour content, facts, media |
| V24 | `…/V24__tours_operator_unit_pricing.sql` | per-unit pricing and seat-accurate capacity |
| V25 | `…/data/V25__tours_operator_catalog_revision_and_seats.sql` | **data:** owner catalog revision 2026-09-30, seat counts, unit price options |
| V26 | `…/V26__tours_operator_sales_control.sql` | emergency pause for online bookings/payments |
| V27 | `…/V27__tours_operator_captured_payment_review.sql` | REVIEW_REQUIRED for captured payments (partial refunds) |
| V28 | `…/V28__tours_operator_payment_refund_callback.sql` | durable identity for provider refund callbacks |
| V29 | `…/V29__tours_operator_asset_registry.sql` | managed media asset registry + category media |
| V30 | `…/V30__tours_operator_office_booking.sql` | office (staff-created) bookings, cash collection, FX rates |
| V31 | `…/V31__tours_operator_office_documents.sql` | append-only print register (vouchers, receipts, manifests) |
| V32 | `…/V32__tours_operator_ops_registry.sql` | suppliers, drivers, vehicles, daily departure assignment |
| V33 | `…/V33__tours_operator_costs_settlements.sql` | costs, payables, settlements, cash box, office refunds (append-only, reversal rows) |

Expected `flyway_schema_history`: 22 successful rows `1,2,3,14,16…33`, 0 failed.
Isolation proof source: `ProductIsolationIntegrationTest` + `release.plan.json` `migrationVersions`.

## 4. Deploy-package validation done locally (2026-10-07, no server)

| Check | How | Result |
|---|---|---|
| Resolved Compose base+overlay | `docker compose --env-file deployment/.env.production.example -f …compose.yaml -f …production.yaml config --quiet` + JSON port scan | `[x]` OK; project `safari-tours-sharm-prod`; only `edge 127.0.0.1:58080→8080`; postgres ports reset (base alone publishes 127.0.0.1:55432); no `build:`; volumes `safari-tours-sharm-prod-{postgres-data,media}`; limits + log rotation on all 5 services |
| Required vars fail closed | same, with `TOURS_OPERATOR_BOOKING_MODE` / `SAFARI_BACKEND_IMAGE` removed | `[x]` interpolation error, refuses to render |
| Edge config syntax | `nginx -t` in `nginx:1.30.4-alpine` | `[x]` OK |
| X-Forwarded-Proto at edge | disposable edge container + echo upstream | `[x]` none→`http`; `https` from trusted hop→`https`; `http`/junk→`http`; untrusted peer + `https`→`http`; XFF `203.0.113.7` from trusted hop → upstream sees it |
| Gateway templates | `nginx -t` of full / maintenance / ACME-bootstrap variants (self-signed test cert) | `[x]` OK ×3 |
| Two-proxy chain (gateway → edge → echo) | disposable docker network, TLS gateway, real edge config | `[x]` spoofed `X-Forwarded-For`/`-Proto`/`Forwarded` discarded, apps see `https` + gateway peer IP; www→apex 301 keeps path+query; HTTP→HTTPS 301; ACME file served on :80; staff `X-Robots-Tag: noindex, nofollow`; public 2 MB → 413, staff 13 MB → 413, 11.5 MB reaches edge; maintenance → 503 + `Retry-After` + noindex + `no-store` |
| Full-stack E2E on this exact tree | — | `[ ]` **not rerun** (local disk ~3 GB free; no image builds) |

## 5. Quality gates on the final SHA (Mohamed / Claude fills)

| Gate | Command | Exit | Duration | Date | SHA |
|---|---|---|---|---|---|
| Backend (Safari isolated app) | `./gradlew :platform:apps:safari-tours-sharm:check` | | | | |
| Backend (platform application) | `./gradlew :platform:application:test` | | | | |
| Web lint/typecheck/test/build | `cd web && pnpm run check` | | | | |
| Foundry | `cd foundry && pnpm run validate` | | | | |
| Repository | `bash scripts/repository-check.sh` | | | | |
| Safari gate (all above + log privacy + legacy snapshot) | `bash scripts/safari-tours-sharm-check.sh` | | | | |
| Dependency audit | `pnpm --dir web audit --prod --audit-level high` (with documented ignores) | | | | |
| Safari enquiry E2E (fresh Compose) | CI job / `e2e/compose.safari-enquiry.yaml` | | | | |
| Safari checkout/media E2E | CI job / `e2e/compose.safari-checkout.yaml` | | | | |
| Independent review (OPS2-F re-check, OPS2-G package) | reviewer + verdict | | | | |
| `git diff --check` | | | | | |

## 6. Permissions shipped (identity + tours-operator)

`identity:administer`, `identity:role-manage`, `identity:role-view`,
`identity:user-manage`, `identity:user-view` (V3) ·
`tours-operator.tour:view|manage`, `slot:manage`, `booking:view|cancel|complete|payment-update` (V14) ·
`payment:view|refund` (V18) · `notification:manage` (V22) · `content:publish` (V23) ·
`media:upload` (V29) · `booking:create-office|collect-cash|reverse-collection`, `fx-rate:manage` (V30) ·
`document:print|print-ops` (V31) · `supplier:manage`, `fleet:manage`, `assignment:manage` (V32) ·
`cost:manage`, `settlement:approve|pay`, `cash-box:close|confirm`, `booking:refund-office` (V33).
Total: 5 identity + 27 tours-operator. Role → permission assignment is done by the
admin in the ERP after bootstrap; Mohamed approves who gets money permissions `[!]`.

## 7. Open risks (must be visible at GO)

1. **Dependency exemptions** — `node-forge` (GHSA-86w9-cpqp-85rv) and `braces`
   (GHSA-vfj7-8cjw-p6xm) ignored in `pnpm.auditConfig`; no upstream fix; owner
   acknowledgement per exemption **not yet recorded** (`handoff/DEPENDENCY_AUDIT_EXEMPTIONS.md`). `[!]`
2. **VPS unverified** — nobody has inspected the server: gateway type (host nginx
   vs container), listen syntax, resources, Docker version, free ports. Templates
   assume host nginx with `http2 on;` (nginx ≥ 1.25.1).
3. **Local E2E not rerun on the final tree** — this box has ~3 GB free; full
   Compose E2E must run in CI (or a roomier machine) on `<final SHA>`.
4. **F6 residual (accepted follow-up)** — CHARGE adjustments capped per charge
   (5000 EGP), not cumulatively per party per day.
5. **Edge forwarded-proto change is new** (this package): small, tested in
   disposable containers; needs reviewer sign-off before deploy. Sharm To Go /
   Sharm Divers edges still use `$scheme` (out of scope).
6. **Encrypted backup drill** needs the private key, which must not be on the
   server — so the drill runs **off-server** (runbook §14.1) and its passing
   `bundle-drill-*.json` is copied back for `health-check.sh`; the machine and
   key custody are an owner decision. `[!]`
7. **alpine:3.20** used by backup/verify scripts is tag-pinned, not digest-pinned.
8. **Certificate renewal reload** — `--deploy-hook 'nginx -t && systemctl reload nginx'`
   stored only in Safari's renewal conf; a graceful reload of the shared gateway
   on each Safari renewal. Mandatory GO item; no cert without it. `[!]`
9. ~~Open document/finance decisions~~ — **resolved by the owner 2026-10-07**:
   refund basis = % of the amount collected; 48/24 h bands use departure hours
   sunrise 04:00 / morning 07:00 / afternoon 13:00 / sunset 15:00; the 4 voucher
   instruction lines are approved.
10a. **IPv6 / HSTS defaults** — gateway `listen [::]` lines ship commented out
   (enable only if El Kheima already serves on `[::]`); HSTS is sent without
   `includeSubDomains` (edge + gateway) until the owner confirms no HTTP-only
   subdomain (mail/webmail/cpanel) exists. `[!]`
10b. **CDN** — per-visitor rate limits assume DNS points straight at the VPS; a
   proxied/CDN record requires a trusted-ranges decision first.
10. **Customer e-mail** off (`NOTIFICATIONS_ENABLED=false`) until SMTP is supplied.

## 8. Owner items `[!]` (Mohamed, not secrets in chat)

- [!] GO for: release ID, ENQUIRY_ONLY mode, new Safari-only vhost + graceful reload(s) of the shared gateway.
- [!] Safe deploy access for Codex + El Kheima domain name for the baseline.
- [!] Accept/reject each dependency exemption.
- [!] Certificate e-mail; off-server backup destination; backup GPG public key (private key kept by owner); alert channel.
- [!] Who types the first admin password (bootstrap-admin, interactive).
- [!] Approval for one test enquiry (checklist B12) and for the maintenance page variant, if used.
- [!] Owner-data flags from `import_costs.py` dry run.
- [!] GO for the Safari-only certificate renewal deploy hook (shared gateway reload on renewal).
- [!] Confirm no HTTP-only subdomain of safaritourssharm.com before HSTS `includeSubDomains` is ever enabled.
- [!] Machine + key custody for the weekly off-server restore drill.

## 9. Reviewer verdict

| Reviewer | Scope | Verdict | Date |
|---|---|---|---|
| Opus independent review | deploy package + edge proto | ACCEPT-WITH-FOLLOWUPS (findings 1–8 fixed in the `docs(safari-ops2-g): deploy package review follow-ups` commit) | 2026-10-07 |

**First step for Codex:** review this file + `DEPLOY_RUNBOOK_AR.md`, then run
runbook §1 (read-only preflight) and report topology before asking for GO.
