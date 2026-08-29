# SEO and analytics foundation

## Indexing

Only approved public discovery/category/service/help pages may be indexed.
Checkout, booking management, dashboard, design system, prototypes, search
parameter variants and payment results are `noindex`. Sitemap entries come from
published canonical records only.

Each published service needs localized title/description, canonical URL,
OpenGraph values, image rights/alt text and structured facts. Structured data
cannot claim ratings, price availability or offer validity absent from the
system of record.

## URL rules

- Locale strategy is decided before adding languages; do not mix locale query
  parameters and locale path prefixes accidentally.
- Slugs are stable, lowercase and redirect through an explicit history when
  changed.
- Booking/customer references and contact data never appear in analytics URLs.
- Campaign parameters are allowlisted and stripped from canonical URLs.

## Minimal event plan

| Event | Safe properties |
|---|---|
| `discovery_searched` | category, date-presence, party-size band, locale |
| `service_viewed` | opaque service ID, category, locale |
| `booking_step_viewed` | opaque service ID, step, confirmation mode |
| `booking_validation_failed` | field code, step; never the entered value |
| `payment_method_selected` | method family; no card/wallet identifier |
| `booking_result_viewed` | result class, payment-state class |

Names, email, phone, hotel, free text, booking reference, provider secrets,
gateway payloads and payment instruments are excluded. Consent and retention
must be implemented before production analytics is enabled.

## Performance budgets for P0 public pages

- No client dependency is added only for decoration.
- Responsive media is sized to its rendered slot and lazy-loaded below the fold.
- Fonts are self-hosted and subsets are reviewed against real Arabic/Latin copy.
- Booking completion remains usable when analytics, maps or nonessential media
  fail.
