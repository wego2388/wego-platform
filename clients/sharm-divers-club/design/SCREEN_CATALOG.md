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
| P0 | Discover / categories (`/discover`) | default, category filter, real-priced offering cards (12 diving offers, GOV-003) | Built |
| P0 | Offering detail page (`/offering-preview`) | real approved price, duration/dive-count meta, WhatsApp inquiry CTA, `noindex` | Built — one exemplar offering (PC04), not a full per-offering catalog router (that's P1, below) |
| P0 | About / trust (`/about`) | default, approved-facts-only | Built |
| P0 | Contact (`/contact`) | WhatsApp, phone, email, location, hours — all from `approved-facts.json` | Built |
| P0 | Living design system (`/design-system`) | tokens, type, controls, operational states, `noindex` | Built |
| P1 | Offering detail (live), full catalog browse | available, closed, capacity-limited — depends on a future public read API this packet does not add | Not built |
| P2 | Booking request (self-service) | depends on a future public booking-request endpoint — out of scope until that backend capability exists | Not built |

## Explicitly not attempted in this foundation

- A working checkout/payment screen — no public booking-creation capability
  exists in the backend yet (see `BRAND_AND_ASSETS.md`).
- Reviews/ratings display — no approved, rights-cleared review feed exists
  yet; fabricating star ratings would violate the no-fabrication rule this
  whole platform already enforces.
- Any language beyond en/ar — see `BRAND_AND_ASSETS.md`.
