# Design system

## Character

Sharm To Go should feel calm, useful and locally confident. Sea teal provides
trust and orientation; sand and sun add warmth without turning the experience
into a crowded discount marketplace. White space is functional: it keeps dates,
prices, pickup rules and payment state legible.

The current wave-and-sun SVG mark is an approved foundation placeholder, not a
final trademark decision. It can be replaced without changing the component or
layout contracts.

## Typography

- English and numbers: Inter Variable.
- Arabic: Noto Sans Arabic Variable.
- Money uses tabular figures where alignment matters.
- Headings are sentence case. Do not use all-caps Arabic.
- Mixed values such as booking codes, phone numbers and currency remain
  isolated LTR spans inside RTL content.

## Semantic color

Components consume roles from `tokens.json`, never a guessed hex value. Brand
colors do not replace status colors: a failed payment is always danger, a
pending payment warning, and a completed payment success.

## Component inventory

| Group | Required components |
|---|---|
| Navigation | Header, locale switch, currency display, mobile drawer, breadcrumbs |
| Discovery | Search intent, category tile, service card, filters, sort, empty state |
| Detail | Gallery, facts, provider label, included/excluded, policy accordion, map/pickup |
| Booking | Calendar, slot pills, language pills, guest stepper, add-on row, pickup field |
| Checkout | Stepper, customer form, order summary, payment choice, policy consent |
| Feedback | Inline validation, toast, alert, skeleton, retry, success/failure result |
| Account | Booking card, status timeline, voucher, cancellation/refund request |
| Operations | Data table, filters, status chip, form drawer, audit timeline, confirmation dialog |

## Interaction rules

- One primary action per panel.
- Disabled controls explain why; unavailable dates are not only low contrast.
- Price changes announce through an `aria-live` region.
- A date selection never submits a booking by itself.
- Destructive and money actions require a clear summary and explicit confirm.
- Provider, price basis, cancellation terms and confirmation type appear before
  the final customer action.

## Status vocabulary

Public labels remain simple: `Awaiting confirmation`, `Confirmed`, `Awaiting
payment`, `Paid`, `Completed`, `Cancelled`, `Refund in progress`, `Refunded`.
Internal states may be more precise but cannot leak confusing provider or
gateway codes directly to customers.
