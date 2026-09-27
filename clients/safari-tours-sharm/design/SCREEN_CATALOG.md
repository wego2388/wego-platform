# Screen catalog — Safari Tours Sharm

Priority levels: `P0` required for first controlled launch, `P1` follows real
usage data, `P2` is roadmap. Every P0 screen needs desktop and mobile states,
plus all four locale variants (en/ru/ar/it), including the Arabic RTL layout.

A screen is not handed off until it defines: loading state, empty state, error
state, long Arabic copy behavior, and narrow-mobile (390px) behavior — not only
the ideal happy path.

---

## Public site

| Priority | Screen | Route | Required states | Status |
|---|---|---|---|---|
| P0 | Homepage | `/` | default, locale switch, reduced motion | NOT_BUILT |
| P0 | All tours | `/tours` | default, filtered, no-results | NOT_BUILT |
| P0 | Category page | `/category/:slug` | all 5 categories, filter active, empty | NOT_BUILT |
| P0 | Tour detail | `/tour/:slug` | default, all 4 tab states, sticky booking card | NOT_BUILT |
| P0 | Booking — details | `/book/:slug` step 1 | form validation, all field errors | NOT_BUILT |
| P0 | Booking — review | `/book/:slug` step 2 | price summary, terms unchecked error | NOT_BUILT |
| P0 | Booking — payment | `/book/:slug` step 3 | redirect to Paymob, failure state | NOT_BUILT |
| P0 | Booking — confirmed | `/booking/:id/confirmation` | success animation, all details | NOT_BUILT |
| P0 | My booking lookup | `/booking/:id` | found, not found, cancellation window | NOT_BUILT |
| P0 | Contact | `/contact` | WhatsApp primary, map, inquiry form | NOT_BUILT |
| P0 | Custom error | `error.vue` | branded 404, 500, locale-aware, WhatsApp CTA | NOT_BUILT |
| P1 | About | `/about` | team, trust signals, story | NOT_BUILT |
| P1 | FAQ | `/faq` | accordion, WhatsApp CTA | NOT_BUILT |
| P1 | Privacy | `/privacy` | — | NOT_BUILT |
| P1 | Terms | `/terms` | — | NOT_BUILT |
| P2 | Live availability widget | embedded in tour detail | real-time slot count | NOT_BUILT |
| P2 | Reviews page | `/reviews` | verified reviews only | NOT_BUILT |

---

## Staff dashboard (ERP)

| Priority | Screen | Notes | Status |
|---|---|---|---|
| P0 | Overview / KPIs | today's bookings, revenue, charts | NOT_BUILT |
| P0 | Tours list | table with search + filter | NOT_BUILT |
| P0 | Tour editor | create / edit with 4-language tabs | NOT_BUILT |
| P0 | Bookings list | filter by date / status / tour | NOT_BUILT |
| P0 | Booking detail drawer | full info + actions | NOT_BUILT |
| P0 | Finance — revenue | charts + export | NOT_BUILT |
| P1 | Customers | derived from bookings | NOT_BUILT |
| P1 | Reviews management | publish / request review | NOT_BUILT |
| P1 | Notifications center | real-time feed | NOT_BUILT |
| P1 | Settings | company info, Paymob, WhatsApp, policy | NOT_BUILT |

---

## Explicitly out of scope for Phase 1

- Mobile app screens — Phase 5 (Kotlin Multiplatform, separate packet)
- Provider onboarding — Safari Tours is a single operator, no providers
- Multi-currency display — EUR only at launch
- Social login — no customer accounts; booking lookup by reference + phone
