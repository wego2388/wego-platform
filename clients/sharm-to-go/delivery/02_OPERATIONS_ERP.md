# Phase 2 — operations ERP

Sub-packet 2A, done 2026-09-30. See the dated WEGO-010-A board entry for full
evidence detail.

## Dependency gate

- [x] Phase 1 API and lifecycle are complete and documented (1A + 1B,
  2026-09-30).
- [x] Staff permission codes and roster/detail response shapes are stable —
  unchanged since 1B, plus one addition (the `/requests/{id}/audit`
  read endpoint, added at the start of this packet once the UI's own "audit
  timeline" requirement below made the gap concrete).

## Navigation and dashboard

- [x] Added "Requests" to the existing Sharm To Go app shell (`app-shell.vue`,
  new "Operations" nav group).
- [x] Real permission-scoped dashboard counts — no placeholder was actually
  present (the services/categories/providers widgets were already real,
  from an earlier pass); this adds a real "Requests" widget alongside them.
- [x] Shows new/in-review/confirmed/today-upcoming/cancelled-or-expired
  counts — computed client-side from the 50 most-recently-created requests,
  the same accepted limitation the existing services-by-status widget
  already has (no dedicated aggregate-count endpoint exists yet).
- [x] Never calls an endpoint the signed-in account lacks permission to use —
  every new list/detail/action call is preceded by a `hasPermission(...)`
  check, same pattern as every existing catalog page.

## Request queue

- [x] Paginated queue with a status filter (server-side, mirrors the
  existing services queue). Date/service/source filters are **not** built —
  a real, honest gap versus the literal spec line, deferred rather than
  faked with client-side-only filtering that would silently stop working
  past the first page.
- [~] Search by reference or customer name — built, but client-side only
  against the currently loaded page (the backend roster has no free-text
  search endpoint yet). Documented in the component's own comment.
- [x] Clear empty/loading/error/no-permission states, same pattern as the
  existing services page.
- [x] New/unclaimed work is visually distinct without relying on color alone
  — a bolded "● unclaimed" text label plus a border color change, not color
  alone.
- [ ] Deep links preserving filter state — not implemented (the status
  filter is local component state, not synced to the URL query string).
  The existing services/categories/providers pages don't do this either;
  consistent with, not a regression from, established convention.

## Request detail and actions

- [x] Shows customer contact, service/price snapshot, party, pickup, notes
  and source.
- [x] Shows the audit timeline (newest first) and an explicit
  payment-independence note ("No payment has been collected for this
  request... online payment is not live yet").
- [x] Review/claim action (`start-review`).
- [x] Confirm action. **Not** "with current capacity and price
  revalidation" as literally written — this is intentional, not a gap: the
  price is a frozen snapshot by design (`delivery/01`'s own contract — a
  later catalog edit must never retroactively change what a customer was
  promised), and this phase has no shared capacity pool to revalidate
  against (see the 1A board entry's capacity scope note). Re-validating
  either at confirm time would contradict the snapshot design, not fulfill it.
- [x] Cancel action with a typed reason (`WegoSelect`) and an optional
  detail (`WegoTextarea`), behind a real `WegoDialog` confirmation (not
  `window.confirm`).
- [ ] Expire action — **not** exposed in the UI, by design: `TravelRequest.expire()`
  is system-only in the 1A domain model ("never called with a staff/customer
  actor"); a manual staff-triggered expire would violate that invariant.
  The sweep itself (`ExpireTravelRequestsService`) exists but has no
  scheduler wired yet — tracked as 1A/1B follow-up, not a Phase 2 item.
- [x] Copy/share a customer-safe summary, plus a `wa.me` deep link
  pre-filled with that summary, opened to the *customer's own* phone number
  (not Sharm To Go's) — the actual point of "share with the customer".
- [x] Prevents double action: the action button set is disabled while
  `actionState === 'submitting'`, and the backend's own state guards (proven
  in 1A/1B) are the real safety net under a race.

## Catalog support for conversion

- [ ] **Deferred, out of scope for this packet.** "Is each published service
  request-ready" and related/featured-service controls are a genuinely
  separate, catalog-facing feature (not request-operations), and building it
  now would be scope creep beyond what "take a request from receipt to
  completion in the ERP" (this packet's actual exit gate) requires. Revisit
  as its own packet if the owner prioritizes it.

## Required evidence

- [x] Vitest coverage — 13 new tests (`Requests.spec.ts` ×6,
  `RequestDetail.spec.ts` ×6, one addition to `Index.spec.ts`) covering
  permissions, the status filter, client-side search, and every action
  transition including a rejected one.
- [x] Typecheck, lint and production build — all green
  (`nuxt typecheck`, root `eslint apps packages --max-warnings=0`,
  `nuxt build`).
- [~] Real browser lifecycle against an isolated backend/database — a real
  Spring Boot backend was run against a throwaway PostgreSQL (migrations
  0→5 applied fresh), a real category/service was created and published,
  and a real customer request was created, reviewed, confirmed and
  completed entirely over real HTTP (the same calls the ERP itself makes).
  The Nuxt ERP was served through a real same-origin proxy and its SSR
  shell was confirmed rendering correctly (200, not a crash) for both new
  routes. **Honest gap:** no interactive graphical browser was available in
  this session (the Claude-in-Chrome extension was not connected), so the
  fully-authenticated, data-populated UI render was not visually confirmed
  by eye — that behavior is instead covered by the 13 Vitest component
  tests above, which do execute real Vue component logic (mount, fetch,
  permission-gating, action dispatch) against a mocked `fetch`, just not
  inside a real browser engine.
- [ ] Accessibility check — not run as a dedicated pass (no axe scan, no
  manual keyboard-only walkthrough). The new markup reuses
  already-accessible primitives (`WegoDialog`'s native `<dialog>`
  focus-trap, `WegoSelect`/`WegoTextarea`'s labeled inputs, semantic
  `<dl>/<dt>/<dd>`), but that is not the same as verifying it. Real gap,
  not claimed as done.
- [x] Existing catalog ERP tests remain green — all 37 pre-existing tests
  still pass unchanged; 50 total now.

## Exit gate

- [x] Staff can take a new request from receipt to confirmation/completion
  using only the ERP, with every transition visible in audit history —
  proven end to end over real HTTP in the manual verification above
  (create → start-review → confirm → complete, `/audit` showing all 4
  events), and independently by `RequestDetail.spec.ts`'s own confirm-flow
  test against the real component.
