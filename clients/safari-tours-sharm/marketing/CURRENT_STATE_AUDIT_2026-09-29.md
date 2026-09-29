# Safari Tours Sharm — Current-State Marketing & Product Audit

- **Audit date:** 2026-09-29 — Africa/Cairo
- **Scope:** legacy WordPress snapshot, current Nuxt site/ERP, catalog, checkout,
  SEO, content, analytics, privacy, conversion and distribution readiness
- **Release verdict:** `NO-GO FOR PRODUCTION`
- **Implementation constraint:** `WEGO-016-E` is complete locally; `WEGO-017-A`
  is ACTIVE. This audit does not activate a marketing packet or bypass Foundry.

## Status and priority legend

- `[x]` present and verified
- `[~]` partial or locally remediated but not release-cleared
- `[ ]` missing
- `[!]` requires owner credentials, approval or independent review
- `P0` blocks safe launch or correct commercial truth
- `P1` materially affects acquisition/conversion/operations
- `P2` optimization after the foundation is correct

## 1. Executive summary

Safari Tours Sharm now has a technically credible catalog, booking lifecycle,
payment aggregate, public checkout and staff ERP. The final local Compose run
proved the mock payment flow in a real browser, and this checkpoint removed
several PII and false-claim risks. The public marketing layer is still a thin,
client-locale Nuxt application, not yet an international SEO platform.

The launch blockers are not “more design.” They are: real Paymob sandbox
evidence, payment-ledger finance, verified tour
content and media rights, SSR localized routing/SEO, deployment/recovery and UAT.

## 2. Public domain versus new application

| Item | Finding | Priority |
|---|---|---|
| Public domain | Owner identifies `https://safaritourssharm.com/` as the old WordPress site. The repository preserves a 2026-09-27 snapshot; the new branch is not deployed. | P0 |
| New public app | Nuxt app at `web/apps/safari-tours-sharm-site/`; 11 public/transactional route shapes exist. | P0 |
| New staff app | Dedicated Safari ERP now has its own image/Compose override and correct login flow. | P0 |
| Root ownership | `[x]` Bare `/` now serves the public site; `/login` serves the staff ERP. Browser regression coverage was added. | P0 |
| Cutover | No DNS, TLS or production deployment change was performed. | P0 |
| Source of truth | Wego/PostgreSQL is the intended commercial authority; WordPress is research/migration input only. | P0 |

## 3. Legacy inventory

Repository snapshot facts:

- [x] 13 WordPress pages captured.
- [x] 30 published legacy tour records captured.
- [x] 34 legacy booking choices captured.
- [x] 437 media metadata records captured.
- [x] SHA-256 digest validated by the unified quality gate.
- [!] every legacy page/tour text remains `UNVERIFIED_SOURCE`.
- [!] every media record remains `rightsStatus: UNVERIFIED` and
      `migrationStatus: NOT_SELECTED`.

Legacy pages requiring an explicit decision/map:

| Old path | Intended treatment | Status |
|---|---|---|
| `/` | localized Home canonical | `[ ]` |
| `/cancellation-policy/` | Cancellation/Refund policy page | `[ ]` |
| `/safari-desert-adventures/` | Desert category | `[ ]` |
| `/sea-water-activities/` | Sea category | `[ ]` |
| `/cultural-historical/` | Cultural category | `[ ]` |
| `/shows-relax-trips/` | Shows category | `[ ]` |
| `/about-us/` | About/Why Us | `[ ]` |
| `/contact/` | Contact equivalent | `[ ]` |
| `/gallery/` | Keep only if rights-approved media supports it | `[!]` |
| `/booking/` | Do not redirect blindly; map only to a valid booking entry | `[ ]` |
| `/booking-room-rent-car/` | Separate inventory/intent; no valid Tour equivalent yet | `[!]` |
| `/qr-code/` | 410 or replacement decision required | `[!]` |
| `/elementor-9/` | likely 410 after validation; never homepage blanket redirect | `[!]` |

All 30 old `/tour/{slug}/` URLs have a matching slug in the approved catalog.
Twenty-nine can target equivalent active tour pages after localized routing.
`/tour/private-boat/` must not become a bookable active page: it is
`REQUEST_ONLY` and inactive.

## 4. Approved production catalog

| Fact | Verified state |
|---|---|
| Total records | 30 |
| Active | 29 |
| Inactive | 1 (`private-boat`) |
| Types | 28 TOUR, 1 TRANSFER, 1 REQUEST_ONLY |
| Categories | SEA 14, CULTURAL 6, DESERT 5, SHOWS 4, TRANSFERS 1 |
| Cancellation | `>=48h full`, `24–48h 50%`, `<24h none` |

The approved catalog supplies slug, English name, category, type, duration,
adult/child price where applicable, capacity, allowed time slots, sort order,
active status and image URL. It does not supply a publish-ready content model
for highlights, itinerary, inclusions, pickup, restrictions, safety, weather,
FAQs or translations.

### Content readiness by field

| Field | Coverage | Finding |
|---|---|---|
| slug/name EN/category/type | `[x]` | catalog-approved |
| adult/child price | `[~]` | approved values/nulls exist; page must not infer absent child price |
| duration/capacity/slots | `[x]` | approved catalog facts |
| primary image URL | `[~]` | URL exists, but rights/hosting/alt text remain unapproved |
| short value proposition | `[ ]` | missing per tour |
| localized title/body | `[ ]` | no catalog fields for AR/RU/IT |
| pickup/departure details | `[ ]` | not safely publishable per tour |
| highlights/itinerary | `[ ]` | missing verified model |
| included/excluded | `[ ]` | missing verified model |
| what to bring | `[ ]` | missing |
| age/participation restrictions | `[ ]` | missing |
| safety/weather notes | `[ ]` | missing |
| languages | `[ ]` | missing per tour |
| FAQs/related tours | `[ ]` | missing data/curation |

## 5. Current Nuxt route inventory

Routes found:

- `/`
- `/tours`
- `/category/[slug]`
- `/tour/[slug]`
- `/booking/[slotId]`
- `/booking/confirmation`
- `/booking/payment-result`
- `/my-booking`
- `/contact`
- `/privacy`
- `/terms`

Missing or incomplete information architecture:

- `[ ]` `/about` or `/why-us` with verified facts.
- `[ ]` dedicated `/cancellation-refund-policy` canonical page.
- `[ ]` locale-specific route tree.
- `[ ]` content/guides architecture.
- `[ ]` crawlable breadcrumb component.
- `[ ]` verified 404/410 rules for legacy URLs.

## 6. Content and conversion audit

### Remediated in this checkpoint

- [x] Removed fake/unverified 4.9 rating and 500+ guests counters.
- [x] Removed unverified 24/7 support and since-2010 claims.
- [x] Removed universal hotel pickup and invented inclusion language.
- [x] Removed unverified Vodafone Cash/Fawry/“100% secure” claims.
- [x] Removed incorrect automatic driver/pickup promise on confirmation.
- [x] Tour detail now reads real catalog name/image/type/price.
- [x] Missing commercial detail is disclosed rather than invented.
- [x] Exact approved cancellation tiers appear consistently in four locales.
- [x] No placeholder testimonial component is published.

### Remaining conversion gaps

- [P0] `[ ]` verified per-tour content is too thin for a confident purchase.
- [P0] `[ ]` media rights and locally optimized assets are not ready.
- [P1] `[ ]` related tours/comparison/FAQ decision support.
- [P1] `[ ]` About/Why Us trust content with owner-approved facts.
- [P1] `[ ]` contact facts/hours/legal business identity approval.
- [P1] `[ ]` clear distinction between instant booking and request-only.
- [P1] `[ ]` transactional pages need explicit noindex and privacy review.
- [P2] `[ ]` content experiments require analytics/consent first.

## 7. International SEO audit

| Requirement | State | Priority |
|---|---|---|
| Four UI dictionaries EN/AR/RU/IT | `[~]` present, but not full tour translations | P0 |
| Locale-specific URLs | `[ ]` | P0 |
| SSR `html lang/dir` | `[ ]` defaults EN, switches client-side from localStorage | P0 |
| SSR critical tour/category content | `[ ]` loaded in `onMounted` | P0 |
| Canonical URL | `[ ]` | P0 |
| reciprocal hreflang | `[ ]` | P0 |
| x-default | `[ ]` | P0 |
| localized title/description | `[~]` UI copy exists; URL/data architecture does not | P0 |
| OpenGraph/Twitter | `[~]` global text only; no approved share image/per-tour metadata | P1 |
| XML sitemap | `[ ]` | P0 |
| robots.txt | `[ ]` | P0 |
| explicit noindex rules | `[~]` privacy/terms/error only; checkout/account routes incomplete | P0 |
| breadcrumbs | `[ ]` | P1 |
| internal links | `[~]` navigation exists; localized crawl graph does not | P1 |
| 404/410 | `[~]` Nuxt error noindex; no legacy retirement policy | P1 |

Privacy and terms currently use `noindex`; that may be undesirable because
legal pages can be useful trust/crawl targets. This needs an explicit SEO/legal
decision rather than copying the same rule to every non-marketing page.

## 8. Structured data audit

- [x] Homepage now emits only a minimal truthful `Organization` name/url.
- [x] Unverified address/contact/business-type data was removed.
- [ ] WebSite/WebPage/BreadcrumbList.
- [ ] tour/service semantics backed by complete factual fields.
- [!] LocalBusiness only after legal name/address/phone/hours are verified.
- [!] AggregateRating/Review prohibited until real attributable reviews exist.
- [ ] automated JSON-LD validation.

## 9. Booking funnel, privacy and security

- [x] Catalog/slot/booking/payment status APIs use server-owned commercial data.
- [x] Phone/reference lookup now uses POST body, not browser query string.
- [x] Nginx per-IP lookup rate limit returns JSON `429` and `Retry-After`.
- [x] Nginx access logs omit query args, protecting callback HMAC.
- [x] purchase confirmation is based on backend PAID status, not landing alone.
- [x] 13/13 checkout/edge Playwright flow passed in clean Compose، ويشمل
  guest browser checkout كاملًا بلا حساب أو login.
- [x] duplicate and invalid webhook behavior covered.
- [x] independent Tier 1 review returned zero blocking findings.
- [!] real Paymob payment-key/sandbox flow not yet proven.
- [ ] consent design and cookie/tracker control.
- [ ] explicit retention/deletion/support runbooks for customer PII.
- [ ] rate-limit/distributed-abuse design beyond single Nginx instance.

## 10. Analytics and attribution audit

- [ ] no provider-neutral analytics contract.
- [ ] no GA4/Google Ads/Meta adapters.
- [ ] no consent-aware script loading.
- [ ] no typed event tests.
- [ ] no purchase idempotency layer for analytics.
- [ ] no UTM standard/persistence.
- [x] no production IDs/secrets embedded.

Any booking/payment attribution persistence that changes schema/auth/PII must
be a proposed Tier 1 packet, not an opportunistic field addition.

## 11. Reviews and social proof

- [x] legacy placeholder testimonials are not reused.
- [ ] no verified review runtime dataset exists.
- [ ] typed review contract/component is absent.
- [!] Google/Tripadvisor review URLs require owner-controlled accounts.
- [ ] post-COMPLETED request workflow is documentation-only future work.

Correct empty state: publish no testimonial block until provenance exists.

## 12. Performance and accessibility

- [x] Nuxt production builds pass.
- [x] public/ERP asset namespaces no longer collide behind one edge.
- [x] missing icon/manifest/social assets are no longer requested globally.
- [~] font payload is material and should be measured per locale.
- [~] remote WordPress thumbnails are not a production image pipeline.
- [ ] no Lighthouse evidence recorded for the final marketing architecture.
- [ ] no explicit LCP/INP/CLS budgets.
- [ ] no responsive image/AVIF/WebP/width-height policy for tour media.
- [ ] accessibility browser audit on the complete funnel.

## 13. Distribution and external accounts

All following actions require a human account owner and are not automated:

- [!] Google Business Profile
- [!] Google Search Console
- [!] GA4 and Google Ads
- [!] Google Things to do
- [!] Tripadvisor / Viator / GetYourGuide
- [!] Instagram / Facebook / TikTok / YouTube

No OTA or Google integration may create an independent booking authority or
permit double booking. Wego remains inventory and commercial source of truth.

## 14. Prioritized findings

### P0 — must close before production launch

1. `[!]` independent Tier 1 review for active WEGO-016-E.
2. `[!]` real Paymob sandbox/payment-key and failure/refund evidence.
3. `[ ]` payment-ledger finance and least-privilege finance permission.
4. `[ ]` verified, versioned tour content model and translation workflow.
5. `[!]` rights-approved media pipeline.
6. `[ ]` SSR locale routes, canonical, hreflang, sitemap and robots.
7. `[ ]` redirect map and 404/410 tests before WordPress cutover.
8. `[ ]` consent/privacy design before analytics scripts.
9. `[ ]` deployment security, observability, backup/restore and rollback.
10. `[!]` UAT and owner sign-off.

### P1 — materially improves trust, conversion and operations

1. About/Why Us and dedicated cancellation page.
2. Complete tour details, FAQs, related tours and comparisons.
3. Truthful structured data and verified review contract.
4. Typed analytics, attribution and purchase idempotency.
5. Accessibility/browser test matrix.
6. Core Web Vitals and image optimization.
7. Content/social/distribution operating model.

### P2 — optimize after a correct launch foundation

1. Experimentation and CRO testing.
2. Long-form local guides by distinct user intent.
3. richer social creative library and publishing cadence.
4. OTA/feed automation only after inventory authority safeguards.

## 15. Files expected in future implementation

Exact paths should be finalized per packet, but the likely surfaces are:

- `web/apps/safari-tours-sharm-site/nuxt.config.ts`
- `web/apps/safari-tours-sharm-site/app/pages/**`
- `web/apps/safari-tours-sharm-site/app/components/**`
- `web/apps/safari-tours-sharm-site/app/composables/**`
- `web/apps/safari-tours-sharm-site/app/content/**`
- `web/apps/safari-tours-sharm-site/server/routes/{robots.txt,sitemap.xml}.ts`
- `web/apps/safari-tours-sharm-site/server/middleware/**` for redirects
- `web/apps/safari-tours-sharm-site/test/**`
- `clients/safari-tours-sharm/content-research/**`
- `clients/safari-tours-sharm/marketing/**`
- `platform/contracts/openapi/v1/wego-api.yaml`
- `products/tours-operator/**` only for an explicitly activated content or
  attribution packet with the required review intensity.

## 16. Audit conclusion

The product is no longer a brochure prototype: its checkout path is executable
and locally proven. It is also not yet a production marketing platform. The
correct next action is an independent E review, then F/H/I product maturity,
followed by a separately activated multilingual marketing program. Skipping
that sequence would turn SEO traffic into an unreviewed payment/operations
system, which is the wrong launch risk.
