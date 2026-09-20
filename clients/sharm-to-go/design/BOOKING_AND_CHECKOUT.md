# Booking and checkout

## Customer flow

```text
Service detail
  → date and time
  → language, guests and add-ons
  → pickup and customer contact
  → price/policy review
  → payment method
  → provider-hosted payment where applicable
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

| Mode | Customer wording | Payment timing |
|---|---|---|
| Instant | `Instant confirmation` | Full payment or approved deposit at checkout |
| Manual | `Confirmation usually within …` | Payment link after confirmation by default |
| On request | `We will contact you with availability` | No charge until an offer is accepted |

## Minimum customer fields

Name, one reachable contact, locale, party, selected option/date/time, pickup
information required by that service, and explicit policy acceptance. Passport,
date of birth or health data is absent unless a later service-specific legal and
retention decision justifies it.

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
