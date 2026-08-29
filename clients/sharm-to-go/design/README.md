# Sharm To Go design source

This directory is the repository-owned design handoff for the public site and
operations dashboard. A Figma file may mirror it, but it cannot silently become
the only source of product states, accessibility rules, content meaning or
payment behavior.

## Read in this order

1. [Design system](DESIGN_SYSTEM.md)
2. [Information architecture](INFORMATION_ARCHITECTURE.md)
3. [Screen catalog](SCREEN_CATALOG.md)
4. [Booking and checkout](BOOKING_AND_CHECKOUT.md)
5. [Payment foundation](PAYMENT_FOUNDATION.md)
6. [Dashboard](DASHBOARD.md)
7. [Responsive and accessibility](RESPONSIVE_AND_ACCESSIBILITY.md)
8. [Brand and asset register](BRAND_AND_ASSETS.md)
9. [Service content template](SERVICE_CONTENT_TEMPLATE.md)
10. [SEO and analytics](SEO_AND_ANALYTICS.md)
11. [Handoff and release checklist](HANDOFF_AND_RELEASE.md)

`tokens.json` is the machine-readable semantic-token contract. The Nuxt apps
consume matching CSS custom properties and tests detect drift in the critical
brand/status colors.

## Truth boundary

- Screens labelled `PROTOTYPE` contain interaction examples, not inventory.
- Sample money is design data and must always be labelled as such.
- Photos, ratings, discounts, availability and provider claims cannot become
  publishable merely because they appear in a design.
- Card numbers and CVVs never enter Wego forms, logs or databases. A certified
  hosted provider surface owns sensitive card entry.
- Arabic and English are the only launch-ready locale targets. Other languages
  remain a roadmap until content parity and human approval exist.

## Figma page mirror

If a Figma file is created, use the exact page order below so design and code
reviews share stable names:

```text
00 Cover & decisions
01 Foundations
02 Components
03 Public discovery
04 Service detail
05 Booking & checkout
06 Customer booking
07 Operations dashboard
08 Responsive & RTL
09 Prototype flows
10 Delivery archive
```

Every frame name uses `surface / screen / viewport / locale / state`, for
example `site / booking / mobile-390 / ar / date-selected`.
