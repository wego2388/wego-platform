# Safari Tours Sharm — design source

Entry point for `web/apps/safari-tours-sharm-site` (to be created in Phase 3).
Follows the same structure established in `clients/sharm-to-go/design/`.

## Read in this order

1. `tokens.json` — colour/type/space/radius/shadow tokens
2. [Brand and assets](BRAND_AND_ASSETS.md)
3. [Screen catalog](SCREEN_CATALOG.md)
4. [Booking and checkout](BOOKING_AND_CHECKOUT.md)
5. [Dashboard](DASHBOARD.md)

## Token rationale

Colors derived from the physical environment of Sharm El Sheikh:

- `ocean` (#0A2342) — Red Sea depth at 20m, used for headers, hero overlays, footer
- `oceanBright` (#1B4F8A) — lighter navy for interactive states and links
- `coral` (#FF6B6B) — reef coral, primary CTA color, high-energy actions
- `sand` (#C8A97E) — Sinai desert sand, warm accent, secondary interactions
- `sunset` (#FF8C42) — Sharm sunset over Tiran Island, urgency and highlights
- `palm` (#2D6A4F) — palm green, eco/nature badge, calm secondary actions
- Canvas (#F7F4F0) — warm off-white, not pure white — feels like warm sand paper

Semantic status colors are shared across the platform (same as sharm-divers-club
and sharm-to-go) — do not override them for brand reasons.

## Typography

- `Playfair Display` — serif display font for tour names, hero headlines, H1/H2.
  Conveys premium, historical, aspirational. Used at display sizes only.
- `Inter Variable` — clean humanist sans for all UI, body copy, buttons, forms.
- `Cairo` — best modern Arabic variable font. Used for ALL Arabic text regardless
  of semantic role (body and display). Fallback: Tajawal.

Arabic requires `line-height` of at least 1.8 and `letter-spacing: 0` always.

## RTL support

Arabic (`ar`) is the only RTL locale. Apply `dir="rtl"` on `<html>` when the
active locale is `ar`. Use Tailwind `rtl:` modifier variants. Mirror
directional icons (arrows, chevrons) but do not mirror logos or emblems.

## Truth boundary

- Screens in `SCREEN_CATALOG.md` labelled `NOT_BUILT` are roadmap only.
- Sample prices are design data and must always be labelled as such.
- Photos, ratings, availability claims cannot become publishable until
  approved in `projects/clients/safari-tours-sharm/data/approved-facts.json`.
- Tour content uses `approved-facts.json` as the single source of truth —
  never invent data or copy from competitor sites.
- Card numbers and CVVs never enter Wego forms or logs; Paymob hosted page
  owns sensitive card entry.
