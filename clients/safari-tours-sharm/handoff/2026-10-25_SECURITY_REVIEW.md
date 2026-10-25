# Safari Tours Sharm — independent pre-production security review (2026-10-25)

- **Revision:** HEAD `5f5cc4a` (uncommitted finance-report files ignored).
- **Method:** read-only source review plus a few harmless GET/HEAD requests to
  `safaritourssharm.com` and `staff.safaritourssharm.com` to check headers and
  route exposure. No writes, no POSTs, no load and no attacks on production.
- **Baseline:** WEGO-016-SEC (2026-10-01..03, READY). This round covers what changed after it.
- **Verdict:** **no CRITICAL findings.** There is 1 HIGH, a configuration gap at the edge
  that is easy to fix. Fix it before relying on the edge rate limits.

## HIGH

### H1 — The staff origin is an unthrottled second path to the public write endpoints
- **Where:** `infrastructure/nginx/nginx.conf:539-558`. On the staff server,
  `location /api/` proxies every API path to the backend. Only `bookings/lookup`
  is blocked there (`:528-537`).
- **Effect:** the public-origin limits do not apply on the staff host:
  - `booking_create_rate` (`:204-213`) for `POST /api/v1/tours-operator/bookings` and `/booking-requests`
  - `payment_start_rate` (`:227-236`) for `POST /bookings/{id}/pay`
- **Live check:** `GET https://staff.safaritourssharm.com/api/v1/tours-operator/sales-status`
  returns 200. This shows that public routes are served on the staff host.
- **Exploit:** a script posts to `staff.safaritourssharm.com/api/v1/tours-operator/bookings`
  with no rate limit. Each booking can hold up to 100 places (`ToursOperatorDtos.kt:187-190`)
  for 30 minutes. This can keep every departure "fully booked". The same path can also:
  - flood the staff request queue (`/booking-requests`, whose only spam check is a honeypot);
  - open unlimited Paymob orders (`/pay`).
- **Fix:**
  - On the staff server, return 404 for the public prefixes
    (`bookings$`, `booking-requests$`, `bookings/*/pay`, `payment-status`, `paymob-callback`,
    `tours…`, `sales-status`, `categories/media`), or allow-list `staff/**`, `identity/**`
    and `documents/**` only.
  - Add a CI probe like the existing lookup-404 assertion.

## MEDIUM

### M1 — Inventory can be held even through the throttled public origin
- **Where:** `nginx.conf:109,205` (20 requests/min, burst 20, per IPv4) and `CreateBookingService` (30-minute hold).
- **Effect:** one IP can hold about 2,000 places in 30 minutes. A few IPs can block every slot.
- **Fix:** add an app-side cap on unpaid NEW places per slot (for example, at most X% of capacity held unpaid),
  or a per-phone/per-IP limit on concurrent unpaid bookings. Also consider a lower adult cap for unpaid holds.

### M2 — Meta Pixel can send Paymob return query data to Meta
- **Where:** `web/apps/safari-tours-sharm-site/app/plugins/analytics.client.ts:89,95,107`.
- **Background:** GA4 is correctly given a path-only `page_location`. But `fbevents.js` reads
  `document.location.href` itself (its `dl` parameter).
- **Effect:** when a consenting visitor returns from Paymob, the first `PageView` on
  `/booking/payment-result?...` can include Paymob's fields: transaction id, order id, amount,
  masked PAN, and `hmac` when present.
- **Fix:** on `payment-result`, run `history.replaceState` to drop the query before the plugin
  sends the first PageView, or skip the Meta PageView on `/booking/*`.

### M3 — Nuxt advisories in the production ERP and site are not covered by the exemption file
- **Where:** `pnpm --dir web audit` reports 13 issues: 3 low, 9 moderate, and 1 high that is ignored.
  The 2 exempted GHSAs (node-forge, braces) are still the only ignores.
- **Not covered by** `DEPENDENCY_AUDIT_EXEMPTIONS.md`, all on `nuxt@4.5.2` used by
  `safari-tours-sharm-erp` and `-site` (fixed in 4.6.2):
  - GHSA-vjh5-4x84-xrqp (route rules skipped on case variants)
  - GHSA-xfq5-w38q-pc2x (`useHeadSafe` in server islands)
  - GHSA-jjm6-p2m9-9w4x (`javascript:` URL filter)
- **Exposure today is low:** the site's routeRules only set cache headers, and no islands or
  `useHeadSafe` were found.
- **Fix:** upgrade to Nuxt ≥4.6.2, or record these as exemptions. The other advisories
  (vitest, postcss, serialize-javascript) are dev-only or in the foundation ERP.

## LOW
- **L1 — Plaintext dump on disk before encryption.** `scripts/safari-ops/backup.sh:43,51` and
  `bundle-backup.sh:66,123` write the plaintext pg_dump to the backup directory (mode 600) and then
  encrypt it. It is removed on exit, but a crash or ENOSPC can leave customer PII on disk.
  **Fix:** stream `pg_dump | gpg`, or write to a tmpfs, and verify with `pg_restore --list` after decryption in the drill.
- **L2 — Containers are not fully hardened.** `infrastructure/compose/safari-tours-sharm*.yaml` sets no
  `cap_drop: [ALL]` and no `security_opt: [no-new-privileges:true]`. `read_only` and the non-root
  backend (`USER 10001`) are already in place. **Fix:** add both to backend, web, safari-site and edge.
- **L3 — Booking references are still sequential** (`CreateBookingService.kt:187`, `STR-<year>-<seq>`).
  Lookup is phone-gated and limited to 10/min per IP, and the previous review accepted this. But
  someone who knows a victim's phone number can walk the small reference space from many IPs and
  learn the tour date and hotel. Keep it on the follow-up list (random suffix).
- **L4 — STQ reference is the client-chosen `clientRequestId`** (`OnlineRequestService.kt:73`). The
  site uses `crypto.randomUUID()`, but the server accepts any UUID, so a weak client weakens only its
  own lookup secret. **Fix:** generate the reference token on the server.
- **L5 — Public lookup returns `cancellationReason`** (`BookingController.kt:330`), which is free text
  typed by staff. Make sure staff never put internal notes there, or map it to a customer-safe code.
- **L6 — No nonce-based CSP.** `script-src 'unsafe-inline'` remains on the site and the ERP; the ERP
  bearer token is in sessionStorage. This was accepted earlier and is still open.
- **L7 — Separation of duties depends on how roles are assigned.** A user who holds both `cost:manage`
  and `settlement:pay` can raise a cost rate and then pay up to 5000 EGP per party per day in cash
  without approval. `charge_recorder_cannot_pay` covers adjustments only. **Fix:** never grant both
  permissions to one non-owner role, or extend the recorder check to cost-rule changes.

## Verified sound
- **Default deny:** `SecurityConfiguration` ends in `anyRequest().denyAll()`. Every staff, finance,
  document, ops, media and notification endpoint I enumerated has a `@PreAuthorize`. The un-annotated
  endpoints are exactly the intended public set.
- **Lookups:** booking lookup needs reference + phone in one query. STQ lookup checks reference and
  phone, and the linked booking re-checks the phone. All misses give the same 404 + `no-store`.
  Lookup is blocked on the staff host.
- **Request submit:** validates, rejects control characters, ignores the honeypot, does not send email,
  binds idempotency to a payload hash, and returns no PII.
- **Two-person rules:** cash-box confirm, collection/refund reversal, settlement payment vs approval
  and adjustment reversal all reject the same actor.
- **SQL:** no jOOQ plain-SQL concatenation. The advisory-lock keys and the `like` use bound parameters.
- **Media:** bytes are re-encoded, pixel/dimension and PNG/JPEG structure are checked, storage paths are
  normalised, every public read checks ownership and approval, served with `nosniff`, and the edge
  regex is restrictive.
- **Emails and logs:** emails are plain text with no customer name or PII in the URL. Logs carry only
  ids and exception class names. The gateway and edge log `$uri` without the query.
- **Edge and gateway:** the gateway overwrites `X-Forwarded-For`/`Proto` and clears `Forwarded`.
  Real-IP is trusted only from private hops. Postgres is not published in production and the edge is
  bound to loopback.
- **Headers (checked live):** HSTS, nosniff, X-Frame-Options (DENY on staff, SAMEORIGIN on the site),
  CSP, Permissions-Policy, and `X-Robots-Tag` on staff. No CORS headers are returned to a foreign Origin.
- **Public site:** JSON-LD is escaped with `<`→`<` everywhere, FAQ included. Analytics loads only
  after consent, with the Meta autoConfig turned off. The locale redirect cannot become protocol-relative.
- **Bootstrap admin and backups:** bootstrap admin needs a TTY, works only before the first user exists,
  and does not use CLI or environment credentials. Backups refuse to run unencrypted by default, and
  the private key is kept off the VPS.
- **Secrets:** a grep found no live secrets in the repository. The only match is a local e2e
  Postgres password.
