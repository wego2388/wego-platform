# Phase 3 — marketing website and conversion

## Dependency gate

- [x] Phase 1 public request API exists.
- [x] Phase 2 confirms the staff team can receive and operate submitted requests.

## Shared site foundation

- [x] Brand positioning and owner-approved story are live in code.
- [x] About, FAQ, Contact, Privacy and Terms routes exist.
- [x] Real contact channels and global WhatsApp action exist.
- [x] Public catalog list and detail routes exist.
- [x] Sitemap includes static trust pages and dynamic service URLs.
- [ ] Extract one shared responsive header/footer/locale state across every route.
- [ ] Add mobile navigation and persistent locale choice.
- [ ] Add a consistent skip link and one main landmark per page.

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

- [x] Replace `/booking-preview` as the customer CTA with service-bound steps.
  Removed the misleading primary-styled "Preview booking" link from the
  experiences index; the service detail page now has a real "Request this
  experience" primary CTA linking to `/experiences/:id/request`.
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

## Analytics and SEO

- [ ] Owner approves analytics provider and consent requirements.
- [ ] Define events: service view, request start, step completion, submit,
  WhatsApp handoff and confirmed outcome.
- [ ] Never send customer PII in URLs or analytics payloads.
- [ ] Add robots/sitemap/canonical smoke tests and structured-data validation.

## Required evidence

- [x] Current marketing-site tests, lint, typecheck and production build pass.
  49/49 site tests pass (16 new from 3A, plus 6 new from 3B covering the
  real search box and query forwarding through catalog → detail →
  request), 50/50 ERP tests pass. Root `pnpm run check` (lint across
  every app) and `nuxt typecheck` both clean; a real production
  `nuxt build` of the site succeeds.
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
