# Sharm To Go — website, mobile and ERP conversion delivery plan

This plan turns the approved marketing direction into one executable customer
journey across all three applications. It complements the engineering detail in
`TECHNICAL_EXECUTION_PLAN.md`; it does not authorize payment or production
deployment by itself.

## One contract, three surfaces

| Surface | Primary job | Must not become |
|---|---|---|
| Public website | Acquire search/social traffic, explain value, show services and create an enquiry/request | A static brochure or a fake instant checkout |
| Mobile app | Retain customers, browse the same catalog, create and track the same requests | A second catalog with copied prices and divergent state |
| Operations ERP | Publish content, receive requests, confirm operational facts and follow each customer | A disconnected content editor with no customer workflow |

The backend is the authority for service, option, price snapshot, request,
confirmation and status. Website and mobile consume the same public contracts;
ERP consumes the corresponding staff contracts.

## Next implementation packet — enquiry and booking-request foundation

### Domain

- `TravelRequest`: public reference, service/option snapshot, requested date,
  party counts, hotel/pickup text, customer name plus at least one reachable
  contact, notes, locale, source channel and lifecycle.
- Lifecycle: `NEW -> IN_REVIEW -> CONFIRMED -> COMPLETED`, with explicit
  `CANCELLED` and `EXPIRED` transitions.
- `CONFIRMED` requires a staff actor or a separately approved instant rule,
  current capacity evidence and an immutable visible price snapshot.
- An enquiry/request does not create a payment and WhatsApp delivery does not
  make it confirmed.
- Idempotency and row locking are mandatory before accepting public writes.

### Website

- Replace the design-only booking preview with a service-bound request flow.
- Fields: option, date, adults/children, hotel/pickup, name, phone/email, notes
  and consent.
- Result: reference, honest status and shareable summary; contextual WhatsApp
  remains available before and after submission.
- Service pages get a sticky `Request / book` CTA and `Ask on WhatsApp` CTA.

### Mobile

- Introduce the first shared HTTP client only when the request API exists.
- Consume the same catalog/request schemas and status vocabulary as web.
- Add request form, result/reference and `My requests` lookup; no card form or
  local-only booking state.
- Keep release identity, networking, secure storage and offline/error behavior
  explicitly tested per platform.

### ERP

- Request queue with filters for new/in-review/confirmed/cancelled/completed.
- Detail view with customer contact, service snapshot, date, party, pickup,
  notes, source and audit timeline.
- Permission-separated claim/review/confirm/cancel/complete actions; typed
  reasons for destructive transitions.
- Dashboard metrics come from real request queries, not hard-coded cards.

### Notifications and summaries

- Generate a stable HTML/print summary from stored facts and public reference.
- A WhatsApp link may carry the reference and compact summary, but the stored
  request remains the system of record.
- Automated outbound delivery remains deferred until consent, retry, outbox and
  audit foundations are explicitly authorized.

## Following verticals

After the request loop works end to end:

1. Dining — venue discovery and reservation/information request.
2. Car rental — dated vehicle request and delivery requirements.
3. Accommodation — arrival/departure, rooms, occupancy and availability-aware
   request; not a generic `Service` with fake fields.
4. Offers — governed promotion attached to real inventory, validity and terms.

Each vertical joins the same acquisition -> detail -> request -> operations
loop while keeping its own genuine domain invariants.

## Acceptance evidence

- Real PostgreSQL migration and concurrency tests.
- OpenAPI request/response and error contracts.
- Website and ERP unit/accessibility tests.
- Mobile JVM/Android checks and UI tests.
- One synthetic end-to-end journey in an isolated environment: discover a
  published service -> submit request -> see it in ERP -> confirm -> retrieve
  the same public reference/status on web and mobile.
- Full Sharm Divers Club regression gate to prove client isolation.
