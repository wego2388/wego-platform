# Codex professional review — findings backlog

Source: `2026-10-01_CODEX_PROFESSIONAL_REVIEW_BRIEF.md` (same directory) —
the owner-requested advisory review of the ERP and public website's current
state. Full report: `codex exec` session `01a0f732-b104-73e1-a0d7-ab5ab4e65596`
(not archived in-repo; this file is the durable record of its findings).

**How this file was built:** Claude read the full review, independently
re-verified the most severe findings against the real code and a real
running build before accepting any of them (see each entry's "Verified"
note), fixed the three that were both confirmed and clearly the most
severe, and recorded the rest here rather than fixing all 24 in one pass.
Severity labels are Codex's own; prioritization and verification notes are
Claude's.

## Fixed in the 2026-10-01 review-fix round

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

## Not yet fixed — not independently re-verified, reported here as Codex found them

### High priority (money/trust/legal-adjacent — do next)

- **Price/policy can differ between what the customer reviewed and what
  gets confirmed.** The review screen shows a previously-fetched price;
  `CreateTravelRequestService.kt:83-105` re-reads current commercial
  facts at creation and can confirm them immediately. A catalog edit
  between review and submit changes what the customer actually gets.
  Suggested fix: submit a server-issued quote/version; if it changed,
  show the new terms and require fresh acceptance.
- **Phone "validation" is presence-only.** `TravelRequestCustomer.kt:17-21`
  accepts any non-blank string — `"abc"` passes as a sole contact method,
  and the ERP's WhatsApp link-builder then strips it to an empty
  destination. Suggested fix: parse/normalize plausible international
  phone numbers; stop describing presence-only validation as "reachable
  contact" in `delivery/01_REQUEST_AND_BOOKING.md`.
- **Privacy/terms copy describes an earlier, pre-request-flow product.**
  Both English and Arabic privacy pages still say booking is a future
  feature and WhatsApp/email are the only contact channels — inaccurate
  now that the real request form collects personal data. The homepage's
  "24/7 continuous support" claim also contradicts the contact page's own
  "no published support hours" line. This is separate from the already-
  documented missing consent checkbox.
- **Tracking references need stronger privacy handling.** The public
  response includes unrestricted pickup text (can contain an address or
  room number) behind an anonymously-guessable-enough reference; Nginx
  logs full paths/referrers; the lookup proxy doesn't set `no-store`; the
  track page has no `noindex`. Suggested fix: treat the reference as a
  bearer secret end to end.
- **The 8-thread concurrency test's own evidence is weaker than the
  "safe and auditable" claim it backs.**
  `TravelRequestServiceTest.kt:263-291` discards submitted futures, never
  retrieves worker exceptions, and doesn't assert 8 successful outcomes
  sharing one row — a pass does not actually prove what
  `delivery/01_REQUEST_AND_BOOKING.md:132` claims it proves. Fix the test
  itself before trusting the claim further.

### Medium priority

- Concurrent requests using the *same* idempotency key don't reliably
  replay the original success response (the unique-constraint conflict
  path doesn't resolve into the OpenAPI contract's promised replay body).
- The catalog snapshot (`JooqServiceRepository.kt:76-83`) reads
  publication/options/media across separate unlocked queries — a
  concurrent catalog edit can produce a mixed-generation snapshot.
- Integer overflow in `adults + children` (`CreateTravelRequestService.kt:89`)
  bypasses the party-capacity check — needs realistic upper bounds.
- The expiry sweep throws and stops early if a request is confirmed
  between candidate-selection and per-row locking (`ExpireTravelRequestsService.kt:26-35`).
- A successful ERP action followed by a failed audit-timeline refresh is
  shown to staff as if the action itself failed
  (`erp/requests/[id].vue:81-91`); the booking proxy also lets connection
  failures escape uncontrolled.
- `TravelRequestDtos.kt`'s unannotated `BigDecimal` fields serialize as
  JSON numbers; OpenAPI and both frontends expect strings — a real wire-
  format mismatch, not just a lint gap. The error schema is also missing
  real response shapes the API actually returns.
- The ERP service editor can silently overwrite another staff member's
  concurrent edit (no optimistic version check) and can silently delete
  an option/media row that was merely left incomplete in the form.
- The public catalog proxy and the sitemap both stop at the backend's
  first page (50 services) — anything published beyond that is invisible
  to search and to the "browse all" experience.
- No correlation ID connects a customer-visible failure to its backend
  audit trail end to end (Nitro doesn't propagate one; staff actions pass
  `null`).
- Anonymous request-creation traffic shares the edge's general
  20 req/s browsing rate limit rather than a tighter, purpose-specific one.
- `FLAT` price basis is mislabeled "per person" in the detail/list/request
  formatters; the tracking page drops price-basis display entirely.
- The tracking page shows raw internal status strings (e.g. `IN_REVIEW`)
  even in the Arabic UI, and parses a date-only value as UTC-midnight
  then displays it in the visitor's local timezone — can show the wrong
  calendar day.
- The ERP dashboard's request counts are a first-50-rows sample
  (acknowledged in a code comment) but presented on screen with no such
  qualification, reading as an exhaustive total.
- `SiteFooter.vue` still links `/booking-preview` from real customer
  pages — a prototype with sample prices and payment options, confusing
  next to the real request flow.
- The party-size `GuestStepper` controls pass "Back"/"Continue" as their
  decrease/increase accessible-name labels, so a screen reader announces
  "Back Adults" / "Continue Adults" instead of "Decrease/Increase Adults."

## Not findings — explicitly already-acknowledged gaps Codex confirmed, not new

Payments, shared availability pooling, mobile API integration, queue
search/filter limits, no scheduler running the expiry sweep, the missing
consent checkbox, and no real-browser visual verification remain exactly
as documented elsewhere — Codex's review confirms these are still true,
not that they are newly discovered.
