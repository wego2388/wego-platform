# Brand and asset register

## Logo

`assets/sharm-to-go-badge.png` (1024×1024, transparent outside the circular
badge) is the owner-supplied logo, approved 2026-09-23 — status `APPROVED`,
replacing the earlier `FOUNDATION_PLACEHOLDER` wave-and-sun mark. It is used
byte-identical everywhere: the site and ERP favicons/app icons/manifest icons
(derived raster sizes: 32, 180, 192, 512px), the site's header, the site's
`og:image`, and the mobile app's launcher icon (all mipmap densities, plain
raster — the badge is already circular with a transparent ground, so it needs
no separate adaptive-icon foreground/background split).

- Source is a photographic/rendered medallion, not a vector mark — there is no
  SVG source; regenerate derived sizes from the 1024px master if it ever
  changes (Pillow, `LANCZOS` resampling, a soft circular alpha mask).
- Minimum digital size: 32×32px (the ring text stops being legible below
  roughly 64px, but the dolphin/badge silhouette stays recognizable).
- Clear space: at least one quarter of the badge diameter.
- Do not stretch, recolor, crop into a non-circular shape, or place on a
  background so dark or busy the gold ring loses contrast.
- The public wordmark remains the text `Sharm To Go` alongside the badge, not
  a replacement for it — the badge's own ring text is too small to read at
  most in-product sizes.

## Media register required for every service asset

| Field | Meaning |
|---|---|
| Asset ID | Stable internal identity, not the filename alone |
| Source owner | Photographer/provider/Sharm To Go |
| Rights evidence | Agreement, invoice or owned-creation record |
| Allowed channels | Site, social, ads, print or specified subset |
| Territory/expiry | Limits that can suspend publication automatically |
| People consent | Required when identifiable people are present |
| Alt text per locale | Meaning for customers who cannot see the image |
| Focal point | Crop-safe x/y point for responsive cards |
| Review status | Draft, rights verified, approved, expired, blocked |

No asset from a reference website, screenshot, social post or search result is
publishable merely because it is accessible online.

## File rules

- Source photos retain a protected original; delivery variants use AVIF/WebP
  plus a compatible fallback where required.
- Filenames contain no customer/provider personal data.
- SVG is sanitized; script, remote references and embedded credentials fail
  intake.
- Hero images need mobile and desktop crop review, not separate uncontrolled
  content copies.
