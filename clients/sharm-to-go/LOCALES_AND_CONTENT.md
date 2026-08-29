# Locales and content governance

## Executable foundation

| Surface | Published now | Direction | Status |
|---|---|---|---|
| Public foundation | English, Arabic | LTR / RTL | Original reviewed foundation copy |
| Operations dashboard | English, Arabic | LTR / RTL | Readiness-only copy, no live data |
| Product/service content | None | — | Awaiting approved catalog facts |

The client manifest intentionally lists only `ar` and `en` today. Advertising a
locale in the manifest means the deployed client can support it end to end; it
is not a wish list.

## Recommended public expansion order

After English and Arabic content parity is measured, add one locale at a time:

1. Russian (`ru`)
2. Italian (`it`)
3. German (`de`)
4. French (`fr`)
5. Polish (`pl`)
6. Spanish (`es`)

This order is a product recommendation, not a claim about current demand. The
owner should confirm priority from real visitor/provider data before activation.

## Translation lifecycle

```text
DRAFT → MACHINE_ASSISTED → HUMAN_REVIEWED → APPROVED → PUBLISHED
                                             └──────→ STALE
```

- AI may draft a translation but cannot publish it.
- Money, dates, duration, pickup, policy and safety terms use structured fields,
  not free translation alone.
- A source-content change marks dependent translations stale.
- Fallback language is visible; the UI never silently mixes languages as if a
  page were fully translated.
- Arabic receives real RTL layout and bidirectional-value testing.

## Currency

`EGP` is the current organizational-currency assumption in the client manifest.
Multi-currency display (for example EGP/EUR/USD/GBP) is deferred until the owner
approves source rates, rounding, validity windows, settlement currency and the
legal wording that the charged amount may differ from a display conversion.
