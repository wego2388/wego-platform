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

## Homepage conversion

- [ ] Replace the visual-only category/date/guest box with working controls.
- [ ] Route search selections into catalog/request state.
- [ ] Add real featured/popular selections controlled by ERP; no invented
  popularity.
- [ ] Add real offer placements only after validity/terms are modeled.
- [ ] Add owner-approved social proof only when its source and wording are
  recorded.

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
  43/43 site tests pass (incl. 16 new: `RequestFlow.spec.ts` ×6,
  `RequestsProxy.spec.ts` ×6, `Track.spec.ts` ×4), 50/50 ERP tests pass.
- [x] Component/page tests for the real request flow. `RequestFlow.spec.ts`
  covers instant confirm, staff-review outcome, capacity block, contact
  validation, 409 error mapping, unknown-service 404.
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
