# Safari Tours Sharm — legacy WordPress URL decisions

Source: preserved `content-research/legacy-wordpress-export.json` (13 pages),
checked against the current Nuxt routes and owner-approved catalog. The
runtime table for the eight `301` rows lives in
`web/apps/safari-tours-sharm-site/server/utils/legacyWordpressRedirect.ts`.
Targets are fixed English canonicals; a visitor can switch language there.
The gallery mapping was added 15 October after the owner approved the photo
library and the four-language gallery page was implemented. The old query
string is discarded, so obsolete parameters and possible PII
cannot leak into a permanent redirect. This table does not approve old page
copy, images or commercial claims for publication.

| Old path | Decision / new canonical | Reason |
|---|---|---|
| `/` | Existing localized home, no new 301 | Current root already serves the public home and locale selection. |
| `/cancellation-policy/` | `301` → `/en/terms#section-4` | Approved cancellation terms appear in the existing Terms section; no separate legal page exists. |
| `/safari-desert-adventures/` | `301` → `/en/category/desert` | Equivalent current category. |
| `/sea-water-activities/` | `301` → `/en/category/sea` | Equivalent current category. |
| `/cultural-historical/` | `301` → `/en/category/cultural` | Equivalent current category. |
| `/shows-relax-trips/` | `301` → `/en/category/shows` | Equivalent current category. |
| `/about-us/` | `301` → `/en/about` | Equivalent current About page. |
| `/contact/` | `301` → `/en/contact` | Equivalent current Contact page. |
| `/gallery/` | `301` → `/en/gallery` in the pending site release | Owner-approved gallery now ties curated photos to current active tours; verify the public 301 after release. |
| `/booking/` | No redirect; current 404 | Old generic WhatsApp form is not a checkout. Direct customers to actual tour pages via navigation; do not redirect to non-existent `/en/booking`. |
| `/booking-room-rent-car/` | No redirect; current 404 | Room/rental services are not verified in the tour catalog. |
| `/qr-code/` | No redirect; current 404 | No verified replacement. Consider 410 after owner confirms retirement. |
| `/elementor-9/` | No redirect; current 404 | Elementor draft page, no verified one-to-one replacement; consider 410 after validation. |

The old `/tour/{slug}/` paths already pass through the locale-prefix route and
reach `/{locale}/tour/{slug}`. They remain temporary `302`, not part of this
static `301` table: runtime active status can change (notably inactive
`private-boat`), and a permanent rule must be derived from the current
catalog before promotion. The public catalog and sitemap, not the old export,
decide whether a tour is bookable/indexable.

Before treating this as a production SEO completion, verify real domain
responses for each row, crawlability and Search Console coverage after a
separately authorized deployment. No El Kheima gateway rule is required:
the Safari public host's catch-all already routes to this Nuxt app.
