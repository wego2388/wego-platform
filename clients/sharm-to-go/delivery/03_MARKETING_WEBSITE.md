# Phase 3 — marketing website and conversion

## Dependency gate

- [x] Phase 1 public request API exists.
- [x] Phase 2 confirms the staff team can receive and operate submitted requests.

## Shared site foundation — 3C, core done 2026-10-01

- [x] Brand positioning and owner-approved story are live in code.
- [x] About, FAQ, Contact, Privacy and Terms routes exist.
- [x] Real contact channels and global WhatsApp action exist.
- [x] Public catalog list and detail routes exist.
- [x] Sitemap includes static trust pages and dynamic service URLs.
- [x] Extract one shared responsive header/footer/locale state across every
  route. The header side of this was already ~92% done before 3C — every
  page but the homepage already used the shared `SiteSubHeader`; the
  homepage keeps its own distinct full-nav header on purpose (a landing
  page's primary nav is a different thing from a sub-page's "back" header,
  not duplication worth collapsing into one component). The real,
  previously-undocumented gap was the **footer**: only the homepage had
  one at all — about/contact/faq/privacy/terms/experiences list and
  detail/the request flow/both track pages had none, so a visitor on any
  of those pages had no way to reach the privacy policy, terms, or
  contact info without going back to the homepage first. New
  `SiteFooter.vue` (the homepage's own footer markup, extracted, parametrized
  by locale) is now on every one of those pages. New `useSiteLocale()`
  composable replaces the locale-ref/copy/direction/toggle block that 12
  pages each duplicated inline. One deliberate exception: the request
  flow (`/experiences/:id/request`) uses `useSiteLocale()` for its own
  locale state but does **not** get `SiteFooter` — a footer full of exit
  links at the bottom of an in-progress request form invites drop-off in
  exactly the flow this whole phase exists to protect; every other real
  page gets it.
- [x] Add persistent locale choice. `useSiteLocale()` persists the chosen
  locale to `localStorage` on toggle and adopts a stored choice on the
  next page's mount — starting "en" on first synchronous render always,
  so server and client markup match (no hydration mismatch), then
  switching post-mount if a stored choice exists. A storage failure
  (private browsing, blocked site data) falls back to "en" silently,
  exactly like every page already did before this existed.
- [ ] Add mobile navigation. Not done this round — `SiteSubHeader`'s nav
  links are already responsive-safe (there are few enough to fit), but a
  true hamburger/mobile-drawer pattern for the homepage's fuller nav was
  not built.
- [ ] Add a consistent skip link and one main landmark per page. Not done
  this round — every page already has exactly one `<main>` landmark (true
  before and after 3C), but no skip-to-content link exists anywhere. Real
  gap, not claimed.
- **Deliberately excluded from this refactor:** `booking-preview.vue` and
  `design-system.vue`. Both are self-contained internal/prototype tooling
  pages with their own separate inline copy objects (not `siteCopy`), not
  part of the real customer journey this shared-foundation work is about —
  forcing them onto `useSiteLocale`/`SiteFooter` would mean either
  restructuring their own copy model or giving a "prototype, contains no
  live business data" page the same trust-building footer as a real page,
  neither of which is the right call for pages already explicitly marked
  as non-production.

## Homepage conversion — 3B, core done 2026-10-01

- [x] Replace the visual-only category/date/guest box with working controls.
  Real category `<select>` populated from the live catalog, a real date
  input (`min` = tomorrow, matching the request form's own rule), and two
  real `GuestStepper` controls (the same component the request form
  uses) for adults/children.
- [x] Route search selections into catalog/request state. Submitting the
  homepage box navigates to `/experiences?category=…&date=…&adults=…
  &children=…`. The catalog page reads `category` to pre-select its own
  filter (also closing a real pre-existing gap: selecting a category
  pill there now updates the URL too, so a deep link to a filtered
  catalog view works for the first time). `date`/`adults`/`children` are
  not real catalog filters (the backend only filters services by
  category — there is no date/capacity filter at the browse level), so
  they are carried forward as-is through the catalog page's own service
  links → the detail page's request CTA → the request form, which
  pre-fills its party/date step from them. The existing capacity check
  on that step still runs unchanged — a carried-over party larger than
  an option's `maxParticipants` is blocked exactly like one typed in
  directly, proven by a dedicated test.
- [ ] Add real featured/popular selections controlled by ERP; no invented
  popularity. Not started — needs an ERP-side popularity/feature model
  first, out of this sub-packet's scope.
- [ ] Add real offer placements only after validity/terms are modeled. Not
  started, correctly blocked on its own stated dependency.
- [ ] Add owner-approved social proof only when its source and wording are
  recorded. Not started — needs the owner's own input first.

## Service landing pages

- [ ] Real media gallery with rights-cleared assets and localized alt text.
- [ ] Persuasive summary, duration, capacity/party rules and confirmation type.
- [ ] Structured itinerary/what to expect where supplied.
- [ ] Clear price basis, inclusions, exclusions, pickup and cancellation.
- [ ] Sticky `Request / book` CTA and contextual `Ask on WhatsApp` CTA.
- [ ] Related services driven by category/configuration, not hard-coded names.
- [ ] Service-level SEO title, description, canonical, Open Graph and JSON-LD.

## Real request flow

- **Correction, 2026-10-01 (found by an owner-requested Codex professional
  review, independently re-verified by Claude before accepting it):** the
  "done" claims below were true of the Vue component in isolation but not
  of the real website — `/experiences/:id/request` was unreachable through
  actual navigation. `experiences/[id].vue` had no `<NuxtPage />` outlet,
  so Nuxt treated `[id]/request.vue` as that page's child route with
  nowhere to render; visiting the request URL silently rendered the detail
  page instead. Every passing `RequestFlow.spec.ts` test mounted
  `request.vue` directly, bypassing Nuxt's router entirely, so this was
  never caught. Fixed by moving the detail page to `experiences/
  [id]/index.vue` (a sibling of `request.vue`, not its implicit parent);
  confirmed via `entry.mjs`'s generated route manifest and by booting the
  rebuilt site and diffing the real HTML at the request URL before and
  after. A new `RouteStructure.spec.ts` scans every page file for this
  exact antipattern (a `foo.vue` next to a `foo/` directory) so it cannot
  silently return. See the WEGO-010-A board entry dated 2026-10-01 ("Codex
  review fix round") for the full account.
- [x] Replace `/booking-preview` as the customer CTA with service-bound steps.
  Removed the misleading primary-styled "Preview booking" link from the
  experiences index; the service detail page now has a real "Request this
  experience" primary CTA linking to `/experiences/:id/request` — now
  genuinely reachable, see the correction above.
- [x] Option/date/party step.
- [x] Hotel/pickup and special-request step. Collected on the contact step
  (not a separate step) — same two fields (`hotelOrPickup`, `notes`), one
  fewer screen. Deliberate simplification, not a missing field.
- [~] Name/contact/consent step. Name + at least one of phone/email are
  collected and validated. **Real gap, not done:** there is no explicit
  consent checkbox or inline link to the privacy policy on this step.
- [x] Review step that distinguishes request from confirmation. Shows the
  snapshotted price/date/party and, for `STAFF_REVIEW` services, the
  honest "Request received" (not "Confirmed!") heading — proven by a
  dedicated test.
- [x] Result page with reference, status and shareable summary (reference,
  copy-to-clipboard, WhatsApp handoff to STG's own number, track link,
  start-over).
- [x] Recovery for validation, unavailable and server-error outcomes.
  `service_not_found`, `option_not_found`, `party_size_exceeds_capacity`
  each get a specific honest message; every other error code (including a
  duplicate/idempotency conflict or an unreachable backend) falls back to
  one generic honest message rather than a raw error — deliberate, not
  per-case messaging for every possible backend error code.
- **Correction, 2026-10-01 (same Codex review):** a retry after a failed
  submit was silently *not* protected by the backend's idempotency
  mechanism — `useTravelRequests.ts` minted a brand-new `Idempotency-Key`
  inside every call, so "the backend wrote the request but the response
  was lost, the visitor retries" could create a second request. Fixed:
  the key is now generated once per arrival at the review step (`request.vue`)
  and reused across retries of that same attempt; going back to change
  party/contact details and returning to review mints a new key, correctly
  treating that as a new attempt. Two new tests
  (`RequestFlow.spec.ts`) prove both halves of this directly by capturing
  the real header sent on each submit.

## Analytics and SEO

- [ ] Owner approves analytics provider and consent requirements.
- [ ] Define events: service view, request start, step completion, submit,
  WhatsApp handoff and confirmed outcome.
- [ ] Never send customer PII in URLs or analytics payloads.
- [ ] Add robots/sitemap/canonical smoke tests and structured-data validation.

## Required evidence

- [x] Current marketing-site tests, lint, typecheck and production build pass.
  60/60 site tests pass (16 new from 3A, 6 new from 3B covering the real
  search box and query forwarding through catalog → detail → request, 7
  new from 3C covering `SiteFooter` in both locales, `useSiteLocale`'s
  SSR-safe-then-persisted locale behavior including a storage-failure
  fallback, and a footer-presence assertion added to every page spec
  that previously had none; 4 new from the 2026-10-01 Codex-review fix
  round — a route-structure regression test and two idempotency-key
  retry tests), 52/52 ERP tests pass (2 new from the same fix round).
  Root `pnpm run check` (lint across every app) and `nuxt typecheck`
  both clean; a real production `nuxt build` of the site succeeds.
- [x] Component/page tests for the real request flow. `RequestFlow.spec.ts`
  covers instant confirm, staff-review outcome, capacity block, contact
  validation, 409 error mapping, unknown-service 404, and (from 3B) a
  carried-over date/party pre-fill plus a past-date-in-the-URL rejection.
- [ ] Mobile/desktop, Arabic/English, light/dark visual verification. Not
  done — no connected browser available this round; real gap, not a
  deliberate scope cut.
- [ ] Keyboard and automated accessibility sweep. Not started.
- [x] End-to-end request visible through the real backend, not mocked.
  Verified 2026-10-01 against a fresh throwaway stack (fresh Postgres,
  real Spring Boot backend on a scratch port, the site's actual built
  Nitro server hitting it through its own same-origin proxy routes —
  no test doubles): created a real category + `INSTANT` service, confirmed
  the public catalog endpoint now returns the option's real id (the
  Phase 3 backend fix), then submitted a real request through the site's
  own `/api/requests` proxy and got back reference `STG-Y2PCLJKX` with
  `status: CONFIRMED`; `GET /api/requests/STG-Y2PCLJKX` returned the same
  record with no customer name/phone/email in the body; an unknown
  reference correctly proxied through as a 404. Infrastructure torn down
  cleanly after. Not separately re-checked in the ERP UI this round —
  the ERP reads the same `TravelRequestController` staff endpoint already
  proven against real requests in the Phase 2A verification round, so
  this is a reasonable inference, not a re-observed fact.

## Exit gate

- [ ] A visitor can arrive from a campaign, understand the offer, submit a real
  request or ask contextually on WhatsApp, and receive an honest result.
