# Safari social catalog — verified review source

> **25 October 2026 — owner correction follow-up:** the October20 XML files
> are historical imported sources, not current-price update files once the new
> catalog revision is deployed. Review
> [the owner corrections and STQ handoff](../../handoff/2026-10-25_OWNER_CORRECTIONS_AND_BOOKING_LOOKUP_AR.md).
> After the corrected backend is verified, re-export its public API and review
> the result before updating the same Meta source; do not re-import old prices
> or camel inclusions, create a third catalog, or claim WhatsApp sync from code.

Latest follow-up: the owner's phone screenshots and a fresh WhatsApp Manager
reload confirm customer2690/account1452927319985281 now connects to this
catalog2505660239941576. Cart remains OFF and catalog icon ON after reload.
**Reviewed publication update completed20 October22:03 Egypt time:**
27 updated/added,0 removed/failed/issues. Browser table confirms27 eligible,
0 hidden. Source renamed `Safari Tours Sharm — Wego reviewed tours — EUR`.
WhatsApp visibly displays the five sets and at least20 distinct tours with
prices/images; actual display of all27 is **not yet verified**. Transfer detail
retains EUR15/unit, correct UUID/canonical link and no add-to-cart control.
Do not claim complete WhatsApp synchronization from Meta eligibility alone;
do not disconnect the correct link or confuse it with the historical app source.
`2026-10-20_meta-reviewed-visible.xml` is the imported publication source.
Generator tests3/3 and exporter tests4/4 pass; fresh public API
recheck matched all27 prices/bases/options/pricing notes/cancellation policies.

Use `--all-visible-reviewed` only after review and owner publication authority.
It changes visibility to published but retains available-for-order, request-only
copy, images, IDs and prices. Hidden is still the default. Do not re-import the
old hidden file after publication unless deliberate rollback is intended.
Availability in Meta/WhatsApp must still be verified; never substitute fake stock.

Earlier hidden-stage notes below are historical, superseded by this paragraph.

Snapshot: [20 October 2026](2026-10-20T13-08-20.726Z.json).

- 27 active tours from the production public API, four independently returned
  locales each (EN/AR/RU/IT), and at least one HTTP-verified published image each.
- Three per-unit tours; price options and price basis are retained, not silently
  presented as per-person prices. Cancellation enums are retained, including
  NON_REFUNDABLE exceptions. Empty exclusions do not mean all-inclusive.
- No inactive tours, assumed stock, invented discounts/reviews, client PII,
  secrets, tracking IDs or customer messages. Static capacity is NOT availability.
- Stable production tour UUID is the future content ID, matching app item_id;
  do not generate unrelated IDs during a later import.

The JSON is a dated review export, not a live sync or a second commercial
source of truth. Its original `NOT_IMPORTED` marker records export time; the
confirmed provider outcomes below are newer. Refresh before publication; prices may change.
Image availability does not add new image rights: use the existing approved media
provenance manifests. Runtime approved media wins over curated site fallbacks;
no historical WordPress imageUrl is used by this exporter.

## Browser destinations observed

- Meta catalog `2505660239941576`, Safari Tours Sharm | Tours & Experiences:
  initially empty; **27 hidden items successfully imported and inspected**.
  [Open catalog](https://business.facebook.com/commerce/catalogs/2505660239941576/products?business_id=7845733532195095).
- Existing manual source `1808922023592263`, named
  `Safari Tours Sharm — Wego catalog — hidden review`: sample import reported
  one added/updated, zero removed, zero failed, zero issues. Product
  `39173359935640833` retains production UUID
  `a0000001-0000-0000-0000-000000000001`, approved visible image, canonical tour
  link and **EUR35 per person**. Visibility is hidden. Meta displays its
  `available for order` source value as unavailable for immediate purchase;
  do not change it to `in stock` to manufacture tour availability.
- `2026-10-20_meta-all-hidden.xml` contains27 unique production IDs, including
  the sample. Prices, units/options and cancellation flags were rechecked
  against the live public API before generation. **Full import completed** after
  owner manual upload,20 October16:55 Egypt time:27 added/updated,0 removed,
  0 failed,0 import issues. Products page confirms27 total, not28; sample UUID
  was updated rather than duplicated. All27 visible table records were checked
  for stable UUID, EUR price, per-person/per-unit title and hidden status.
  Airport transfer editor confirms sedan15/SUV20/Hiace35 EUR per vehicle,
  canonical URL and loaded image; exited without saving provider overrides.
  No item has been published and no ad created. Meta's two Actions entries
  are recommendations for sets/dataset, not import failures.
- WhatsApp catalog `2174874126401460`: four partners listed in owner settings;
  Commerce opening failed with `ERR_TOO_MANY_REDIRECTS`. Contents and public
  WhatsApp visibility are unverified. Do not delete it or create a third catalog.
- Linked WhatsApp customer number2690, account2284087695723571, connected.

## Next execution sequence

1. Keep public publication gated on eligibility and truthful availability.
   Full hidden review import and field checks completed; image fetch source
   verified27/27, with sample and transfer images inspected in Meta. Do not
   claim every Meta thumbnail has been individually visually reviewed.
2. Open authenticated WhatsApp Business Web for number2690 (owner scans QR).
   Inspect original catalog/source before uploading anything.
3. Determine whether the app or Commerce owns editing. Correct the original
   source; do not assume a new Commerce catalog syncs back to the phone.
4. Validate platform eligibility and actual WhatsApp destination separately;
   Meta hidden import does not establish phone catalog sync or public approval.
5. Keep the website as booking intake and Wego as confirmation/inventory truth.
   No social cart/order automatically confirms a tour booking.

Google Activities is separate: its current editor forces EGP prices. The owner
approved deleting eight stale EGP activities; empty setup verified. Do not put
the website's EUR amount in an EGP field or convert currencies without approval.

## Refresh and verify locally (public reads only)

```bash
node --test scripts/safari-content/export-social-catalog.test.mjs
node scripts/safari-content/export-social-catalog.mjs --write
```

Creates a new dated review snapshot; no provider API, import, deployment,
booking write or staff credential is used. No output overwrite is allowed.
Quick replies are in [Arabic/English copy](../QUICK_REPLIES_AR_EN_2026-10-20.md).

## Saved dynamic category sets

Existing All Products set1377363887815725 preserved. Five new bilingual sets
use the imported `product_type` values (Meta displays these lowercase).
Auto-recommended products are OFF; counts match the production snapshot:

| Set | Product type | Count | Meta set ID |
|---|---|---:|---|
| Desert Safari / سفاري الصحراء | desert |5|1736851590738131|
| Red Sea Adventures / رحلات البحر | sea |12|1669602724514266|
| Culture & Day Trips / الثقافة والرحلات اليومية | cultural |6|1502880008303824|
| Shows & Experiences / العروض والتجارب | shows |3|1669367998055360|
| Airport Transfers / انتقالات المطار | transfers |1|1384335200356398|

Saved sets list verified after creation. These are internal product sets, not
proof of a public Shop, advertised delivery or WhatsApp collections.
