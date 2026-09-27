# Brand and asset register — Safari Tours Sharm

## Source of truth

Brand voice, positioning, and tour data come from the owner-maintained marketing
workspace at `/home/wego/projects/clients/safari-tours-sharm/`. That workspace
is not a code dependency — nothing in `web/apps/safari-tours-sharm-site` imports
from it directly. Facts used here must be copied in by hand, one at a time, each
traceable to a `status: "approved"` entry in
`projects/clients/safari-tours-sharm/data/approved-facts.json`.

## Foundation mark

`assets/` will hold the official Safari Tours Sharm logo once provided by the
client. Until then, use the text wordmark "Safari Tours Sharm" only.

Current asset status: **AWAITING_CLIENT**

Required from client before launch:
- [ ] Logo file (SVG + PNG, transparent background)
- [ ] High-resolution hero video (Red Sea + Sinai desert, 15–30 seconds, no audio)
- [ ] Minimum 3 photos per tour (30 tours = 90 photos minimum)
- [ ] Owner consent for any identifiable people in photos

## Media register (required for every published asset)

| Field | Meaning |
|---|---|
| Asset ID | Stable internal identity, not the filename alone |
| Source owner | Photographer / Safari Tours Sharm / Wego Digital |
| Rights evidence | Agreement, invoice or owned-creation record |
| Allowed channels | Site, social, ads — specify subset |
| Territory/expiry | Geographic or date limits |
| People consent | Required when identifiable people are present |
| Alt text (en/ru/ar/it) | Meaningful description per locale |
| Focal point | Crop-safe x/y for responsive card thumbnails |
| Review status | draft → rights-verified → approved → expired |

No asset from a competitor site, screenshot, or search result is
publishable merely because it is accessible online.

## What is safe to publish today

Cross-referenced against `projects/clients/safari-tours-sharm/data/approved-facts.json`:

- Business name: "Safari Tours Sharm" (use exactly this spelling)
- Website: https://safaritourssharm.com
- WhatsApp: https://wa.me/201111292690
- Phone: +201111292690
- Email: safaritourssharm@gmail.com
- Location: Sharm El Sheikh, Egypt

Tour prices and full tour content: pending approval gate per tour.
Do not publish prices or tour details until each tour entry is
`status: "approved"` and `publishable: true` in `approved-facts.json`.

## File rules

- Source photos retain a protected original; delivery variants are AVIF/WebP
  plus JPEG fallback.
- Filenames contain no customer personal data.
- SVG files are sanitized — no scripts, remote references, or embedded credentials.
- Hero video: MP4 + WebM formats, max 5MB each, 1920×1080, no audio track,
  poster image required (shown before video loads).
