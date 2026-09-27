# Phase 3 — marketing website and conversion

## Dependency gate

- [ ] Phase 1 public request API exists.
- [ ] Phase 2 confirms the staff team can receive and operate submitted requests.

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

- [ ] Replace `/booking-preview` as the customer CTA with service-bound steps.
- [ ] Option/date/party step.
- [ ] Hotel/pickup and special-request step.
- [ ] Name/contact/consent step.
- [ ] Review step that distinguishes request from confirmation.
- [ ] Result page with reference, status and shareable summary.
- [ ] Recovery for validation, duplicate, unavailable and server-error outcomes.

## Analytics and SEO

- [ ] Owner approves analytics provider and consent requirements.
- [ ] Define events: service view, request start, step completion, submit,
  WhatsApp handoff and confirmed outcome.
- [ ] Never send customer PII in URLs or analytics payloads.
- [ ] Add robots/sitemap/canonical smoke tests and structured-data validation.

## Required evidence

- [x] Current marketing-site tests, lint, typecheck and production build pass.
- [ ] Component/page tests for the real request flow.
- [ ] Mobile/desktop, Arabic/English, light/dark visual verification.
- [ ] Keyboard and automated accessibility sweep.
- [ ] End-to-end request visible in ERP under the same reference.

## Exit gate

- [ ] A visitor can arrive from a campaign, understand the offer, submit a real
  request or ask contextually on WhatsApp, and receive an honest result.
