# Codex professional review — findings backlog

Source: `2026-10-01_CODEX_PROFESSIONAL_REVIEW_BRIEF.md` (same directory) —
the owner-requested advisory review of the ERP and public website's current
state. Full report: `codex exec` session `01a0f732-b104-73e1-a0d7-ab5ab4e65596`
(not archived in-repo; this file is the durable record of its findings).

**How this file was built:** Claude read the full review, independently
re-verified severe findings against the real code and a real running
build/live database before accepting any of them (see each entry's
"Verified" note), and fixed what that verification confirmed across four
rounds (2026-10-01 through 2026-10-02). All 24 of Codex's original
findings are now fixed; the fourth round's 7 items were tracked as
individual tasks per the owner's instruction to carry the project through
to completion rather than stop at a partial sweep. Severity labels are
Codex's own; prioritization and verification notes are Claude's.

## Fixed in the 2026-10-01 review-fix round (first pass)

1. **[High] Request page unreachable via real navigation.** `experiences/
   [id].vue` had no `<NuxtPage />`, so `/experiences/:id/request` silently
   rendered the detail page. **Verified independently** by booting the
   built site and diffing real HTML at the URL before/after the fix, and
   by reading the generated route manifest. Fixed by moving the detail
   page to `experiences/[id]/index.vue`. New regression test:
   `test/RouteStructure.spec.ts`.
2. **[High] Idempotency key regenerated on every submit call.** Defeated
   the backend's retry protection. **Verified independently** by reading
   `useTravelRequests.ts`. Fixed: one key per arrival at the review step,
   reused across retries, regenerated on going back to change details.
   New tests: two cases in `test/RequestFlow.spec.ts`.
3. **[High] ERP customer-facing summary mislabels cancelled/expired
   requests as "Awaiting confirmation."** **Verified independently** by
   reading `requests/[id].vue:135`. Fixed: an exhaustive, localized
   (English/Arabic, by the request's own recorded locale) status-label
   map. New tests: two cases in `erp/test/RequestDetail.spec.ts`.
4. **[Honest gap validation] The Phase 3A "done" record exceeded the real
   evidence.** A direct consequence of finding 1 — resolved by fixing
   finding 1 and correcting `ROADMAP_AR.md`'s Phase 3A entry in place
   (dated correction, not a silent edit).

## Fixed in the 2026-10-01 review-fix round (second pass)

5. **[High] Phone "validation" was presence-only.** `"abc"` passed as a
   sole contact method. Fixed with a real E.164-shaped plausibility check
   — `TravelRequestCustomer.isPlausiblePhoneNumber` (domain), a matching
   `@Pattern` on the DTO, a mirrored client-side check on the request
   form before it ever calls the backend, and a defensive fix in the
   ERP's WhatsApp link builder so an older, pre-validation row with no
   real digits doesn't render a broken `wa.me` link with no destination.
   New tests at all three layers.
6. **[High] Privacy/terms copy described an earlier, pre-request-flow
   product.** Rewrote both English and Arabic privacy-page sections to
   describe what the real request form actually collects and who sees
   it; rewrote the terms page's "how a request works" section to
   distinguish instant confirmation from the few services that need
   staff review (it previously implied every request needs manual
   verification); replaced the homepage's "24/7 continuous support"
   stat (which directly contradicted the contact page's own honest "no
   published support-hours commitment yet") with a claim the product
   actually keeps. New tests assert the corrected copy and the absence
   of the old claims.
7. **[High] Tracking references needed stronger privacy handling.**
   Both Nitro proxy routes (`/api/requests`, `/api/requests/[reference]`)
   now set `Cache-Control: no-store`; the `/track/[reference]` page now
   sets `noindex,nofollow`. The nginx edge config now masks the
   reference out of both the access log's request line and any logged
   referrer via a `map` block — **verified live** by running the real
   nginx image and confirming a request to `/track/STG-SECRET12` logs
   `/track/[redacted]`, not the real reference. **Honestly recorded
   residual gap:** nginx's own built-in `error_log` (upstream failures)
   uses a fixed format this repo cannot rewrite and still logs the raw
   reference on an actual backend error — verified live too, lower
   probability (only on real failures) but real.
8. **[High] The 8-thread concurrency test's own evidence was weaker than
   its claim.** Rewrote it to capture every worker's `Future` and call
   `.get()` on each (a worker exception now fails the test loudly
   instead of vanishing), assert all 8 completed, and independently
   verify via `findByIdempotencyKey` that exactly one row exists in the
   real database — not just infer it from an in-memory collection. **This
   stricter test immediately caught a real bug** (see next item) that the
   original weak test had been masking.
9. **[High] Concurrent requests with the same idempotency key did not
   reliably replay the original response** — a losing concurrent INSERT
   hit the real unique constraint and the exception propagated as an
   unhandled 500 instead of the contract's promised replay. Fixed:
   `CreateTravelRequestService.create()` now catches
   `DataIntegrityViolationException`, re-queries `findByIdempotencyKey`
   in a fresh transaction (the failed one is already aborted), and
   returns `AlreadyExists` with the real winning row. **Verified**
   against real PostgreSQL via Testcontainers, re-run 3 times to rule
   out a lucky pass.
10. **[High] Price/policy could differ between what the customer
    reviewed and what got confirmed.** Added a required `expectedPrice`
    to request creation (frontend sends what the review screen actually
    displayed); the backend now rejects a mismatch with `409
    price_changed` and the real current price, instead of silently
    confirming at a price the customer never saw. The frontend refetches
    the service on this error so the review screen shows the corrected
    price before the visitor retries. OpenAPI contract, backend, and
    frontend all updated with tests at each layer.
11. **[Medium, found opportunistically] API/contract BigDecimal
    serialization drift** — `TravelRequestDtos.kt`'s price fields were
    unannotated, defaulting to JSON numbers while OpenAPI and both
    frontends expect strings. Fixed by adding
    `@JsonFormat(shape = JsonFormat.Shape.STRING)` to every `BigDecimal`
    field in that file (matching the convention `ServiceDtos.kt` already
    used) while adding the new price-check fields anyway. Also closed
    part of the companion gap: the error schema now documents
    `validation_failed` (with its real `message` field) and
    `price_changed` alongside the existing domain error codes, instead
    of omitting response shapes the API actually returns.

## Fixed in the 2026-10-01 review-fix round (third pass — medium-priority sweep)

12. **Integer overflow in `adults + children` bypassed the party-capacity
    check.** Fixed at both layers: a `@Max(100)` on the API DTO fields
    (rejects before the domain is ever touched) and a matching
    `require(... <= MAX_PARTY_COMPONENT)` in `TravelRequest`'s own `init`
    block, so the domain object enforces its own invariant regardless of
    which caller constructs it. New tests at both layers, including one
    that reproduces the exact overflow (`Int.MAX_VALUE` adults + 1 child).
13. **`FLAT` price basis was mislabeled "per person"** in the catalog
    list, detail and request pages (three copy-pasted, identical, all
    stale implementations); **the tracking page dropped price basis
    entirely.** Fixed by deleting all three duplicates in favor of one
    shared `priceBasisLabel()` (`usePublicCatalog.ts`) used everywhere,
    including the tracking page, which never had it.
14. **The tracking page showed raw internal status strings** (e.g.
    `IN_REVIEW`) even on the Arabic page, and **the request flow's own
    shareable WhatsApp/copy summary had the identical bug.** Fixed with
    one shared, bilingual `travelRequestStatusText()` used by both.
15. **The tracking page's date could show the wrong calendar day** —
    `new Date("2026-10-15")` parses a date-only string as UTC midnight,
    then `toLocaleDateString()` rendered it in the visitor's own
    timezone, which rolls back a day for anyone west of UTC. Fixed by
    forcing `timeZone: "UTC"` on the display formatting (the value is a
    calendar date, not an instant, so it has no real timezone of its
    own). **Verified** by computing the actual formatted string under
    `America/Los_Angeles` before and after the fix (`31 Dec 2098` →
    `1 Jan 2099`) and asserting both in a dedicated test.
16. **The ERP dashboard's request counts (a first-50-rows sample) looked
    exhaustive** — the limitation was only in a code comment, never on
    screen. Fixed with a visible caveat shown exactly when the page cap
    is actually hit (`travelRequests.length >= 50`), not shown otherwise
    (confirmed real counts need no caveat).
17. **`SiteFooter.vue` still linked `/booking-preview`** — a prototype
    with sample prices and payment options — from every real customer
    page. Removed from real navigation; the prototype page itself still
    exists as an internal design reference, per Codex's own suggested
    fix.
18. **The `GuestStepper` guest-count controls had misleading accessible
    names** ("Back Adults" / "Continue Adults" instead of "Decrease/
    Increase Adults") on both the homepage search box and the request
    form. Fixed with two new `decreaseGuestLabel`/`increaseGuestLabel`
    copy keys, used in both places (the already-correct prototype page
    was left as the reference for what the fix should look like).
19. **A successful ERP action followed by a failed audit-timeline
    refresh was shown to staff as if the action itself had failed**, and
    **a rejected action left the stale pre-action record on screen**
    instead of showing what actually happened (e.g. another staff
    member having already cancelled the request first). Fixed by
    separating the two failure modes: a failed mutation now reloads the
    real current record (best-effort) instead of leaving it stale; a
    successful mutation whose *subsequent* audit-refresh fails shows a
    distinct, narrower warning instead of the action-failed message.
    Three new tests cover the rejection-reloads-real-state path, the
    success-with-failed-refresh path, and the already-passing rejection
    path.

## Fixed in the 2026-10-02 review-fix round (fourth pass — remaining medium-priority items)

All seven items previously listed as "not yet fixed" below have now been
fixed and independently verified against real infrastructure (a real
Postgres via Testcontainers for the backend items, a real nginx container
for the rate-limit item). This closes the backlog opened by the 2026-10-01
review — nothing from that review remains open. This round proceeded as a
set of tracked tasks per the owner's instruction to work through the
project "كامل" (end to end), including fixing problems discovered along
the way, not just the originally listed Codex findings.

20. **The catalog snapshot (`JooqServiceRepository.kt`) could read
    mixed-generation data under a concurrent edit.** `findById`/`findAll`/
    `findAllPublished`/`findPublishedById` each make 3 separate SELECTs
    (service row, options, media); under the default READ COMMITTED
    isolation, a concurrent `save()` landing between them (it deletes and
    reinserts options/media) could return old service fields paired with
    new options, or vice versa. Fixed by running these four read paths at
    `REPEATABLE_READ` instead, which gives every statement in the
    transaction one consistent snapshot. Safe to apply broadly: these are
    read-only transactions, so none of Postgres's REPEATABLE_READ
    write-write serialization-failure behavior applies. **Verified**: full
    backend suite green against real PostgreSQL.
21. **The expiry sweep crashed and stopped the whole batch early if a
    request was confirmed between candidate-selection and per-row
    locking** (`ExpireTravelRequestsService.kt`) — the guard checked
    `status.isTerminal`, but `CONFIRMED` is not terminal, so a request
    confirmed in that narrow window reached `expire()`'s own precondition
    check, which threw and aborted every request still queued behind it
    in the same sweep. Fixed by matching `expire()`'s actual precondition
    exactly (`status != NEW && status != IN_REVIEW` → skip, don't throw).
    **Not given a new timing-based test** — reproducing the exact race
    would need either a flaky thread-interleaving test or production
    instrumentation hooks this code has no reason to carry; relied
    instead on the guard provably matching `expire()`'s own precondition,
    plus the full regression suite passing.
22. **The site's own request-creation and reference-lookup proxies let a
    true backend connection failure escape uncontrolled** — `fetch()`
    itself throws on a real connection failure (DNS, refused, timeout),
    distinct from the backend responding with a real HTTP error status,
    and the old code only handled the latter. Fixed by wrapping the
    `fetch()` call specifically (not the whole handler, so a real
    passthrough status like `409 price_changed` still reaches the caller
    unchanged) in both `server/api/requests/index.post.ts` and
    `[reference].get.ts`, turning a true connection failure into a clean
    `502`. New tests mock `fetch` to throw and assert the clean 502 for
    both routes.
23. **The ERP service editor had no optimistic-concurrency check, and
    silently dropped incomplete option/media rows.** Two staff editing
    the same service at once meant the second save silently discarded
    the first's changes with no error to either of them; a form row left
    half-filled (one field blank) was silently stripped from the saved
    service, which could delete real existing content rather than just
    an untouched placeholder row. Fixed with:
    - A new `version` column (Flyway `V6__travel_service_optimistic_locking.sql`)
      on `travel_service`, bumped by `Service.withUpdatedDetails()` on
      every content edit.
    - `UpdateServiceService.update()` now compares the editor's
      `expectedVersion` against the real current version — inside the
      same `findByIdForUpdate`-locked transaction that reads it, so the
      comparison is race-free — and rejects a stale save with a new
      `409 version_conflict` (carrying the real current version) instead
      of overwriting it.
    - The ERP's `services.vue` now rejects a half-filled option/media row
      with a clear validation error before ever calling the API,
      distinguishing it from a genuinely untouched blank row (both
      fields empty), which is still silently dropped as before.
    - OpenAPI contract, backend, and ERP frontend all updated; new tests
      at every layer, including an HTTP-level test that reproduces the
      exact scenario (staff A saves, staff B's stale save is rejected,
      staff B reloads and retries successfully) against real PostgreSQL.
      (Opportunistically also added `would_invalidate_published_content`
      to the OpenAPI error enum — it was already a real response the API
      returns, just never documented.)
24. **The public catalog proxy and the sitemap both stopped at the
    backend's first page (50 services)** — anything published beyond
    that was invisible to search and to the "browse all" experience.
    Fixed by walking every backend page (200 per page, the backend's own
    max) in `server/api/catalog/services.get.ts` and `sitemap.xml.ts`
    until a short page signals the end, preserving the `categoryId`
    filter across every page walked. New tests cover the multi-page walk,
    the common single-page case, filter preservation across pages, and a
    connection failure mid-walk. (Opportunistically closed the same
    missing-`fetch()`-try/catch gap in `categories.get.ts` and
    `services/[id].get.ts` while in this file.)
25. **No correlation ID connected a customer-visible failure to its
    backend audit trail end to end.** The backend already had a proven
    `CorrelationContext`/`CorrelationIdFilter` convention used by every
    other product (`divers`, `accounting`, `hr`, `payroll`) — travel-
    marketplace was the one product still generating a throwaway
    `UUID.randomUUID()` (public create) or passing `null` (all four staff
    actions on `TravelRequestController`) instead of using it. Fixed by
    switching both controllers to `CorrelationContext.currentCorrelationId()`.
    On the site, Nitro's own `fetch()` to the backend bypasses nginx
    entirely, so nginx's edge request id could never reach the backend's
    filter on its own — fixed by having the two public proxy routes
    (`requests/index.post.ts`, `requests/[reference].get.ts`) forward an
    incoming `X-Correlation-Id` if the caller sent one, generate a fresh
    one otherwise, pass it to the backend, and echo it back to the
    browser. nginx's existing log format already references
    `$sent_http_x_correlation_id` — it was just always empty before this
    fix gave it a real value to log. New tests cover both the
    generate-when-absent and forward-when-present cases for both routes.
26. **Anonymous request-creation traffic shared the edge's general
    20 req/s browsing rate limit** rather than a tighter, purpose-specific
    one. Fixed with a dedicated `request_create_rate` zone (10 requests/
    minute, burst 10) applied only to `/api/requests`, ahead of the
    general `location /` block. **Verified live**: booted a real
    `nginx:1.30.4-alpine` container and hammered both paths — `/api/requests`
    hit `429` after 11 requests while `/` kept passing through at the
    general limit within the same window.

## Not findings — explicitly already-acknowledged gaps Codex confirmed, not new

Payments, shared availability pooling, mobile API integration, queue
search/filter limits, no scheduler running the expiry sweep, the missing
consent checkbox, and no real-browser visual verification remain exactly
as documented elsewhere — Codex's review confirms these are still true,
not that they are newly discovered.
