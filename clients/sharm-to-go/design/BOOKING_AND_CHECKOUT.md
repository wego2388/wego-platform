# Booking and checkout

## Customer flow

```text
Service detail
  → date and time
  → language, guests and add-ons
  → pickup and customer contact
  → price/policy review
  → payment method (only after its Phase 3 adapter is activated)
  → provider-hosted payment where applicable; never an owned card form
  → verified result
```

The desktop booking summary remains visible beside the option form. On mobile it
becomes a compact sticky footer that opens a full price sheet. The primary action
always includes the amount or clearly says that payment follows confirmation.

## Pricing presentation

- Show the basis: per person, per vehicle, per group or flat.
- Separate adults, children and add-ons.
- Display currency next to every total; formatting alone is insufficient.
- A crossed-out price requires an approved previous-price fact and validity.
- Taxes/fees cannot appear for the first time after the final confirmation.
- Revalidate price and capacity on the server before accepting the booking.

## Confirmation modes

| Mode | Launch status | Customer wording | Payment timing |
|---|---|---|---|
| `INSTANT` | Supported after Phase 2 | `Instant confirmation` | No online collection before Phase 3; then full payment or approved deposit at checkout |
| `STAFF_REVIEW` | Supported after Phase 2 | `Confirmation usually within …` | No online collection before Phase 3; payment link after confirmation by default |
| `ON_REQUEST` | Deferred | `We will contact you with availability` | Requires a separate quote/acceptance state model before implementation |

`ON_REQUEST` must not be represented as an informal use of `NEW`. It becomes a
separate packet only when quote versioning, expiry, acceptance, price changes
and capacity policy are designed.

## Minimum customer fields

Name, one reachable contact, locale, party, selected option/date/time, pickup
information required by that service, and explicit policy acceptance. Passport,
date of birth or health data is absent unless a later service-specific legal and
retention decision justifies it.

## Manage-booking authorization

The public booking reference is safe to display but grants no access. Guest
management uses a high-entropy capability delivered once and stored only as a
hash by the backend. It must not appear in logs, analytics, referrers, a URL path
or a query string. The web client consumes it from a URL fragment and removes it
from the visible URL immediately; native clients use platform secure storage.

There is no reference-plus-contact lookup in the launch contract. Recovery
requires a separately designed, generic-response, rate-limited OTP or equivalent
verified channel.

## Prototype contract

`/booking-preview` is local design evidence. It may use labelled sample values to
prove calculations and responsive behavior, but it cannot call an API, create a
booking, redirect to a gateway, promise availability or be linked as a live
commercial offering.

## Owner decisions (2026-09-20)

Given directly by the owner; they replace the open questions above and steer
the future booking/payment packet.

- **Real customer channels:** WhatsApp / direct call `+20 10 0141 3469`
  (`wa.me/201001413469`) and email `info@sharmtogo.com`. Already live on the
  site (service detail, footer) and the mobile app's service detail.
- **No customer accounts.** Booking is anonymous and as simple as possible:
  name, one reachable contact, the selected option/date/party, policy
  acceptance — nothing else. No sign-up, no login for customers.
- **Instant confirmation** for every service, as long as it is available for
  the customer's chosen date (server-side availability/capacity check decides).
  The `Manual` and `On request` modes above are not launch targets. The
  current homepage "A request first, a confirmation second" copy describes the
  pre-booking era and must be rewritten when the booking packet ships.
- **Email is the confirmation/notification channel** (sender
  `info@sharmtogo.com`). WhatsApp templates are a later, separate decision.
- **Payment provider details** (Paymob / FawryPay / CIB contracts and
  credentials) are still pending from the owner — the booking packet cannot
  go live before they exist, but its domain model, availability check and
  email flow can be built and tested against a sandbox first.
