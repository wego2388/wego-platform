# Sharm Divers Club — design foundation

Entry point for `web/apps/sharm-divers-club-site`. Mirrors the structure
`clients/sharm-to-go/design/` already established for this platform, scoped
down to what this packet actually built rather than claiming full parity.

- `tokens.json` — colour/type/space/radius/shadow tokens, sourced from the
  real, approved brand direction in `brand/BRAND_CORE.md` (deep ocean blue +
  turquoise + warm sand), not invented.
- `BRAND_AND_ASSETS.md` — what is and is not safe to publish today, and why
  the booking flow routes to WhatsApp instead of a fake checkout.
- `SCREEN_CATALOG.md` — required screens and states for this foundation.

`products/divers` (WEGO-002, COMPLETE) is the real backend this brand belongs
to. This is a **new web app inside the existing Divers product**, not a new
product boundary — unlike `products/travel-marketplace`, Sharm Divers Club is
a single-operator business whose data already lives in `products/divers`, so
no new product/client composition risk applies here.
