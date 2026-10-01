# Owner data hub — Safari Tours Sharm

`safari-tours-owner-data-hub.xlsx` is the **single source of owner-provided
data** for this client: company facts, every external platform, domain and
service accounts, the tour catalogue, per-unit options, media, translation
reviewers, open questions and a change log. The owner (Mohamed) edits it;
agents read it.

Rebuild from the latest sources: `python3 build_owner_hub.py` (reads
`../content-research/drafts/owner-edited-2026-09-30.xlsx` and
`../content-research/approved-catalog.json`). Rebuilding overwrites owner
edits — read the owner's copy first and fold answers back into the sources.

## Rules for agents

- Read rows by the `الكود (key)` column (or `الكود (slug)` / `كود الرحلة`).
  Never key on row numbers or Arabic labels.
- Cell colours: yellow = owner to fill, green = filled, grey = system value,
  red = needs a decision. Status values: `ناقص` (missing), `اتملى` (filled),
  `محتاج مراجعة` (needs review), `مش محتاجينه` (not needed).
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
