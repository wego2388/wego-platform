# Screen catalog — Sharm Divers Club public site

`P0` is required for the first controlled launch, `P1` follows real usage, and
`P2` is roadmap. Every P0 screen needs desktop/mobile and ar/en states. A
screen is not handed off until it also defines loading, empty, error, long
Arabic copy, and narrow-mobile behavior — not only the ideal happy path
(same completion rule `clients/sharm-to-go/design/SCREEN_CATALOG.md` uses).

## Public site

| Priority | Screen | Required states | Status |
|---|---|---|---|
| P0 | Home (`/`) | default, locale switch (en/ar), reduced motion | Built |
| P0 | Discover / categories (`/discover`) | default, category filter, real-priced offering cards (18 offers: 12 diving + 6 water sports, GOV-003) | Built |
| P0 | Offering detail page (`/discover/[code]`) | real approved price, duration/dive-count meta, related-offerings list, WhatsApp inquiry CTA, `Product`/`Offer` JSON-LD | Built — a real page per offering, not a single exemplar (the former `/offering-preview` is retired) |
| P0 | About / trust (`/about`) | default, approved-facts-only | Built |
| P0 | Contact (`/contact`) | WhatsApp, phone, email, location, hours — all from `approved-facts.json` | Built |
| P0 | Living design system (`/design-system`) | tokens, type, controls, operational states, `noindex` | Built |
| P0 | FAQ (`/faq`) | two-tier accordion: answerable-now facts vs. WhatsApp-confirmed items (ODR-003/004/005/013) | Built |
| P0 | Privacy (`/privacy`) | accurate to actual site behavior — no cookies/analytics/forms | Built |
| P0 | Terms (`/terms`) | inquiry-only framing, prices real but subject to WhatsApp confirmation | Built |
| P0 | Custom error page (`app/error.vue`) | branded 404/error state, locale-aware, WhatsApp + home CTA | Built |
| P0 | Mobile navigation | hamburger menu in `SiteHeader`, all nav links reachable below `md` | Built |
| P1 | Live offering availability | available, closed, capacity-limited — depends on a future public read API this packet does not add; today's offering pages show a real price with no live availability claim | Not built |
| P2 | Booking request (self-service) | depends on a future public booking-request endpoint — out of scope until that backend capability exists | Not built |

## Explicitly not attempted in this foundation

- A working checkout/payment screen — no public booking-creation capability
  exists in the backend yet (see `BRAND_AND_ASSETS.md`).
- Reviews/ratings display — no approved, rights-cleared review feed exists
  yet; fabricating star ratings would violate the no-fabrication rule this
  whole platform already enforces.
- Any language beyond en/ar — see `BRAND_AND_ASSETS.md`.
