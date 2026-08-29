# Sharm To Go product blueprint

## Product shape

Sharm To Go is a curated multi-provider marketplace for Sharm El Sheikh. Some
services may be operated directly; others are fulfilled by approved local
providers. The public experience must identify that responsibility before the
customer confirms.

The repository boundary is:

```text
Wego Platform
└── Wego Travel Marketplace (reusable product rules)
    └── Sharm To Go (isolated client configuration and deployment)
```

Sharm Divers Club remains a separate client of Wego Divers. A travel request
cannot read, mutate, or silently become a diving booking.

## Intended experiences

- Public site: discovery, categories, filters, service detail, saved items,
  trip planning, request/quote/confirmation, booking management, locale and
  permitted currency display.
- Operations dashboard: catalog approval, provider onboarding, request queue,
  booking exceptions, content/locales, payments/refunds, commissions and
  settlements, audit and reports.
- Provider workspace: only the provider's assigned products, availability,
  requests, confirmations, documents and settlement statements.

The provider workspace is deferred until provider identity and resource scopes
are designed and reviewed. It must not reuse a broad staff dashboard role.

## Future bounded domains

1. **Catalog and content** — category, service, option, inclusions/exclusions,
   pickup rules, media rights, locale variants and publication approval.
2. **Provider network** — legal/operational profile, contacts, approval status,
   service assignments, documents, commission agreement and scoped users.
3. **Availability and pricing** — date/time slot, capacity, pricing basis,
   participant bands, add-ons, currency source and expiry.
4. **Booking request** — customer intent and contact, selected option, pickup,
   party, idempotency, provider routing and response deadline.
5. **Confirmation** — immutable quote snapshot and explicit customer acceptance;
   a request is never represented as confirmed before provider verification.
6. **Payment and refund** — authorization, capture, refund and reconciliation;
   separate permissions and reasons for every sensitive transition.
7. **Commission and settlement** — gross, fee, provider net, adjustments,
   statement period and payout evidence. Never inferred from the public price.
8. **Verified review** — only a completed eligible booking can create a review;
   imported or anonymous ratings are not displayed as verified.
9. **Planner** — customer preferences and an explainable itinerary assembled
   only from currently published facts. It cannot invent availability.

No shared platform abstraction is created merely because two domain names look
similar to Wego Divers. Promotion to `platform/` requires proven identical
invariants across real products.

## Simple customer booking model

The public and staff experience uses a small state vocabulary:

```text
NEW → CONFIRMED → COMPLETED
 └──────┬──────→ CANCELLED
        └──────→ EXPIRED (only while awaiting confirmation/payment)
```

`CONFIRMED` requires an immutable option/price snapshot, capacity proof and an
explicit actor or approved instant-confirmation rule. Payment has its own small
state model (`PENDING`, `PAID`, `FAILED`, `REFUNDED`) and never changes booking
truth merely because a browser returns from a gateway.

## Isolation and security

- One client deployment and data store per client instance.
- Product routes and permissions use a travel-marketplace namespace; no Divers
  permission authorizes them.
- Provider users are resource-scoped to their provider, never client-wide by
  role name alone.
- Support has no implicit client access.
- Customer PII, passport/identity documents and payment data require explicit
  classification, minimization, retention and audit decisions before storage.
- Public content is untrusted input at publication time and escaped at render.

## Commercial truth

Service availability, price, tax, currency, pickup, duration, provider,
inclusions, cancellation terms, media rights and translation status are durable
owned facts with source and approval. Marketing copy and external websites are
references, never the system of record.
