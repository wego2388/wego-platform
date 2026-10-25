# Safari Tours Sharm — live site audit (SAF-05 / SAF-06), 2026-10-25

Scope: https://safaritourssharm.com (en/ar/ru/it) and the staff origin login page. Read-only: GET requests only, no forms, no booking lookups, no login. Evidence scripts: `/home/wego/.claude/jobs/88cbdad6/tmp/audit/` (`seo.mjs`, `resp.mjs`, Lighthouse JSON).

## Summary

No P1 defects found. SEO fundamentals pass on all 164 sitemap URLs. Remaining issues are performance (P2) and structured-data coverage (P2).

## Passed (verified)

- **Sitemap:** 164 URLs = 14 static/category pages x4 + 27 tours x4. The 27 tour slugs equal the public API list exactly. Inactive `private-boat` is absent and `/en/tour/private-boat` returns 404. No booking/my-booking/design-system URLs. Every entry has 5 alternates including x-default.
- **Per page (all 164, SSR HTML):** status 200, exactly one `<h1>`, `<title>` and description present, canonical equals the page's own URL (same locale, trailing-slash variants canonicalise to the slash-less URL), `og:url` matches, HTML hreflang set equals the sitemap set (reciprocal, with x-default -> /en), `html lang` matches the locale, `dir=rtl` only on ar. All OG/Twitter tags present (title, description, image, url, type, locale, `twitter:card`, `twitter:image`). All 28 distinct `og:image` URLs return 200.
- **robots.txt:** disallows booking, my-booking, design-system and `/api/` for all 4 locales, and links the sitemap. `/en/booking/*` and `/my-booking` are also `<meta robots noindex>`, so protection does not rely on robots alone. `/en/design-system` returns 404.
- **Staff origin:** header `x-robots-tag: noindex, nofollow` on all responses, meta noindex on login, `/sitemap.xml` 404, API 401 unauthenticated. `staff.../robots.txt` is a 404 JSON; this is fine, since a Disallow would stop crawlers seeing the noindex.
- **Legacy redirects:** all 8 rows in `legacyWordpressRedirect.ts` return 301 to the documented target (query string dropped, with and without a trailing slash). `/booking/`, `/booking-room-rent-car/`, `/qr-code/`, `/elementor-9/` and `/wp-admin/` return 404. `/tour/{slug}/` returns 302 -> `/en/tour/{slug}/`, as documented.
- **404s:** real 404 status for unknown pages/tours. The HTML 404 page has `noindex`.
- **JSON-LD:** valid JSON on every page. No `AggregateRating` or `Review` anywhere.
- **Responsive:** home, tours, 2 tours (sunset-quad-bike, sharm-airport-transfer), my-booking and contact. en at 360/768/1024/1440; ar/ru/it at 360/1440. 60 combinations: no horizontal overflow, no console errors, no failed requests, no broken images, no `<img>` without alt. Three transient `ERR_NETWORK_CHANGED` errors came from the local network; they did not recur on rerun.
- **Lighthouse 13.5 (mobile, simulated throttling, local Chromium):** accessibility, best-practices and SEO score 100 on both pages. Performance is below.

| Page | Perf | FCP | LCP | TBT | CLS | Weight | TTFB |
|---|---|---|---|---|---|---|---|
| /en | 74 | 2.4 s | 3.9 s | 430 ms | 0.001 | 1.5 MB | 170 ms |
| /en/tour/sunset-quad-bike | 73 | 1.9 s | 2.7 s | 950 ms | 0.001 | 1.3 MB | 160 ms |

Lighthouse is a simulation; INP and field data are not measured.

## Findings

### P2

1. **Heavy, uncacheable cover image.**
   - URL: `/media/tours/super-safari-adventure/18e4d7d4-ac38-4f28-ae07-82a361d3d8c6.png`.
   - Evidence: the original is a 2.2 MB PNG and is also the `og:image` for the tour. The 768 px variant is 641-655 KB and is the largest image on /en and the quad-bike page. Response header is `cache-control: no-store, private`.
   - Fix: re-upload as a ~1600 px WebP/JPEG under 300 KB (WhatsApp ignores big previews). Serve `/media/**` with `public, max-age`, with a versioned `?v=` URL or an ETag. Check the uploaded-media handler and the nginx location for `/media/`.
   - Source: the Safari nginx config and the media route; the catalog/media upload tool.
2. **No cache headers on static images.**
   - URL: `/images/catalog/*`, `/brand/logo-full.webp` (118 KB for a logo), `/og-default.png`.
   - Evidence: no `Cache-Control`. Lighthouse cache insight: 533 KiB estimated savings on /en. `/_safari/*.js` is already immutable.
   - Fix: add `Cache-Control: public, max-age=2592000` for `/images/`, `/brand/` and `/og-default.png`. Shrink the logo (use SVG or a smaller WebP).
   - Source: nginx for the Safari host; `web/apps/safari-tours-sharm-site/public/brand/`.
3. **Main-thread cost and LCP.**
   - Evidence: TBT 430 ms (home) and 950 ms (tour); mainthread-work 3.6 s and 2.9 s; home LCP 3.9 s. Image-delivery savings of about 730 KiB are flagged on both pages. Consent and analytics scripts are unverified.
   - Fix: send correctly sized images (`sizes`/`srcset`) and use `fetchpriority=high` on the hero, no `loading=lazy` on it. Defer non-critical hydration (Nuxt lazy hydration for below-the-fold sections). Profile the `_safari/*.js` bundles.
   - Source: `web/apps/safari-tours-sharm-site/app/pages/index.vue`, `app/pages/tour/`, the hero and cards components.
4. **Structured data covers only 2 page types.**
   - Evidence: `TravelAgency` on the 4 home pages only (no logo, `sameAs`, address or `image`) and `FAQPage` on faq. All 27x4 tour pages, tours, category, about and contact pages have none. The handoff §12 expects Organization/WebSite/WebPage/Breadcrumb.
   - Fix: add `BreadcrumbList` on category/tour pages, `WebSite` + `Organization` (with the real logo) on home, and optionally `TouristTrip` or `Product` + `Offer` using the real catalog price/currency. Do not add ratings. Rich-result eligibility is not guaranteed.
   - Source: `app/composables/` SEO head helpers (where `useSchemaOrg`/ld+json is built) and `app/pages/tour/[slug].vue`.

### P3

5. **Generic description.** `/{en,ar,ru,it}/terms` and `/privacy` share one identical description (8 URLs). Give each a distinct one (i18n locale files: `terms` and `privacy` SEO keys).
6. **Very short descriptions** (39-70 characters): faq, category pages and some ar tours (e.g. `/ar/tour/diving-course`). Lengthen to 110-155 characters. `/ru/about` is 220 characters and will be truncated; `/ru/tour/ras-mohamed-white-island-boat` has a 66-character title. Source: locale files.
7. **Title patterns.** Tour titles read "Safari Tours Sharm — {name}" and the `en` and `it` titles for super-safari-adventure are identical. Consider `{name} in Sharm El Sheikh | Safari Tours Sharm`, with a localised name for `it`. `og:title` omits the brand, which is acceptable.
8. **Trailing-slash duplicates return 200** (`/en/tours/`, `/en/tour/x/`) rather than 301. Canonical is correct, so impact is low. Optionally 301 to the slash-less form in nginx or Nitro.
9. **Legacy tour redirects are 302** by design. Promote to 301 only after deriving the table from the active catalog (see LEGACY_URL_DECISIONS).
10. **Open items outside this audit:** Search Console state (SAF-06.05) and field INP/CrUX were not checked, as they need owner access. The four WordPress 404 rows may become 410 once the owner confirms retirement.
