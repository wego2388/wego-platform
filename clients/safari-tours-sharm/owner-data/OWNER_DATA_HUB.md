# Owner data hub — Safari Tours Sharm

`safari-tours-owner-data-hub.xlsx` is the **single source of owner and audited
business data** for this client: company facts, external accounts, domain and
services, the tour catalogue, per-unit options, media, translations, open
questions, quick replies, FAQs, marketing foundations and the current
readiness dashboard. The owner (Mohamed) edits it; agents read it.

`build_owner_hub.py` is now a **bootstrap-only** generator for a brand-new
workbook from the preserved catalogue sources. It deliberately refuses to
overwrite the live workbook unless `--force-bootstrap` is supplied. Do not use
that flag on the owner's working copy: the generated bootstrap does not include
later owner answers or authenticated account-audit results.

## Rules for agents

- Read rows by the `الكود (key)` column (or `الكود (slug)` / `كود الرحلة`).
  Never key on row numbers or Arabic labels.
- Cell colours: yellow = owner to fill, green = filled, grey = system value,
  red = needs a decision. Status values: `ناقص` (missing), `اتملى` (filled),
  `محتاج مراجعة` (needs review), `مش محتاجينه` (not needed). The visible marks
  are `[✓]` done, `[!]` needs review, `[x]` missing and `[-]` not needed.
- **Secrets never live here.** Passwords, API keys, Paymob secret/HMAC keys,
  SMTP passwords go to the server environment only. If you find one in the
  workbook, stop, tell the owner, and do not copy it anywhere.
- A value only becomes live through the normal path: catalogue/price changes
  → a guarded data migration (see V25) + Tier-1 review; tour text → staff
  content API with revision-checked publishing; platform links → site config.
- Record what you applied in the `سجل التغييرات` sheet's source (rebuild) and
  on the execution board.

## Sheets

| Sheet | Key column | Contents |
|---|---|---|
| اقرأني | — | How to use the file (owner-facing). |
| بيانات الشركة | key | legal name, register/tax/licence numbers, address, phones, emails, support hours/languages, cancellation policy, payment methods. |
| المنصات والسوشيال | key | Google Business/Search Console/GA4/Ads/review link, TripAdvisor, Meta Business/Facebook/Instagram/WhatsApp/Pixel, TikTok, YouTube, Yandex, VK, Telegram, Bing, Apple Business, Viator, GetYourGuide, Trustpilot — account exists?, URL/handle, login e-mail (no password), what is needed, priority. |
| الدومين والسيرفر والخدمات | key | domain, registrar, DNS access, old hosting, new server, SMTP provider, sending domain, Paymob account/merchant id/EUR, backups, Cloudflare, media Drive link. |
| الرحلات | slug | Owner's tour sheet with the approved revisions applied, plus `طريقة التسعير` (per person / per unit). |
| خيارات الوحدات | كود الرحلة + كود الوحدة | per-unit options: seats per unit and € per unit (matches V25). |
| الصور والميديا | key | logo, colours, hero video, per-tour photos with rights confirmation. |
| الترجمة | key | locale, plan, reviewer. |
| أسئلة مفتوحة | key | every open question with the owner's reply and status. |
| سجل التغييرات | — | dated change log. |
| مراجعة الحسابات | key | authenticated, non-secret account facts and the exact next action. |
| الردود السريعة | key | approved/draft Arabic and English support replies with readiness status. |
| الأسئلة الشائعة | key | customer FAQs; unverified commercial answers remain marked for review. |
| أساس التسويق | key | positioning, audiences, SEO clusters, CTAs, attribution and trust rules. |
| لوحة المتابعة | — | live counts and P0/P1 owner actions calculated from the other sheets. |

## Account audit snapshot — 2026-10-01

- Google Business Profile exists and is verified. Its phone, 24/7 hours,
  legacy activities and old/misspelled Instagram link still need owner review.
- Meta Business portfolio `7845733532195095` is still named
  `International dive college`, while its primary Page and submitted legal
  verification identify Safari Tours Sharm. The current `safari tours` user
  has full control; Mohamed Wagdy has active partial/basic access. Business
  verification is `In review`.
- The Facebook Page (`104180979683761`) and Instagram
  `@safari_tours_sharm` are connected with one full-access and one
  partial-access person and no partners. The Instagram account showed 843
  followers in this snapshot; the linked administrative user still needs a
  passkey.
- WhatsApp Business account `1431818925553858` is approved, but the business
  review is pending and `+20 11 11292690` is not verified. No person is
  assigned directly to the WABA yet.
- Two empty duplicate catalogs (`1308046411195594` and `903762161371500`)
  were permanently deleted after confirming that WhatsApp and Commerce were
  not using them. A clean catalog, `Safari Tours Sharm | Tours & Experiences`
  (`2505660239941576`), was created and linked to WhatsApp. Cart ordering and
  the customer-facing catalog icon remain off so Meta does not become a second
  booking authority before valid products are accepted.
- A 26-item Meta feed was prepared from active, owner-approved tours with
  approved prices and images. It was deliberately not uploaded: the public
  domain currently fails with TLS/502 and the feed's image and canonical tour
  URLs are therefore not fetchable. Three inactive tours and the airport
  transfer with no approved image are excluded.
- Dataset `SAFARITOURS` (`1433667068811688`) appeared in the catalog creation
  flow, despite not appearing in Business Settings. It was not linked pending
  provenance, events and consent review. No Meta domain is registered yet.
- Meta requires 2FA for everyone and reports it complete for all three people.
  Passkeys are still required for two of three people, and the Security Center
  warns that a public email domain is in use.
- Search Console has no access for the current Google admin account, and no GA4
  property was found. Both require setup after the owner confirms the plan.
- Hostinger exposes DNS management for the externally registered domain and
  shows a time-sensitive hosting-renewal warning. The domain also failed a
  live TLS/HTTP check during this audit. No payment, renewal or DNS change is
  authorized by this workbook.

The next human-assisted sequence is: repair or replace the public hosting/TLS,
verify the dedicated WhatsApp number with the owner entering the OTP, assign
one least-privilege WhatsApp operator, decide the final portfolio/WABA names,
then upload the 26-item feed and enable the catalog icon only after Meta accepts
the products. Search Console, GA4 and domain-email follow after the deployment
and consent plan are fixed.
