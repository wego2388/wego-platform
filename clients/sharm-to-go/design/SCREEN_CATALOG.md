# Screen catalog

`P0` is required for the first controlled launch, `P1` follows real usage, and
`P2` is roadmap. Every P0 screen needs desktop/mobile and ar/en states.

## Public and customer

| Priority | Screen | Required states |
|---|---|---|
| P0 | Home | default, search intent, locale switch |
| P0 | Experiences | results, filters, empty, unavailable, error |
| P0 | Service detail | available, request-to-confirm, sold out, suspended |
| P0 | Booking options | default, invalid, price update, slot unavailable |
| P0 | Customer details | empty, validation, saved progress |
| P0 | Payment | methods, processing, failed, expired |
| P0 | Result | confirmed, request received, payment failed |
| P0 | Manage booking | found, not found, cancelled, refunded |
| P0 | Help and policies | default, locale fallback disclosed |
| P1 | Cart | empty, populated, stale price/availability |
| P1 | Customer account | guest upgrade, bookings, profile |
| P2 | Saved/compare/planner | empty, populated, stale item |

## Operations

| Priority | Screen | Required states |
|---|---|---|
| P0 | Overview | readiness, live totals only after APIs |
| P0 | Service list/editor | draft, review, published, suspended, validation |
| P0 | Calendar/pricing | day, range edit, conflict, capacity zero |
| P0 | Booking list/detail | new, confirmed, paid, completed, cancelled |
| P0 | Payment detail | pending, paid, failed, refund states, reconciliation issue |
| P0 | Content/locales | missing, draft, reviewed, stale, published |
| P0 | Settings/policies | dirty, validation, saved, permission denied |
| P1 | Customer list/detail | minimized data, restricted fields, retention action |
| P1 | Coupons | inactive, scheduled, limit reached |
| P2 | Provider workspace | separately scoped identity and provider resources |

## Frame completion rule

A screen is not handed off until it includes loading, empty, error, permission,
keyboard focus, long Arabic copy and narrow mobile behavior—not only the ideal
happy path.
