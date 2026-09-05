# WEGO-015 Phase 1 — website UX and regression contract

The frozen baseline for the Sharm Divers Club public website half of
WEGO-015. Built by direct inspection of `app/pages/`, `app/components/`,
and every file in `test/` — every selector and fact below is grep output
against the real test suite (51 Vitest cases as of this writing), not an
estimate. Unlike the ERP (WEGO-014), this site has **no dedicated
Playwright E2E suite** — its only automated coverage is this Vitest +
`@vue/test-utils` suite, so the regression surface here is component-level
text/attribute assertions, not browser-driven locators.

## Route inventory (13 routes + 1 internal reference page)

| Route | Purpose |
|---|---|
| `/` | Home — hero, category grid, trust/persona sections, booking-flow explainer |
| `/discover` | Category-filterable offering directory |
| `/discover/[code]` | Offering detail (diving or water sports) |
| `/dive-sites` | Named dive site directory |
| `/dive-sites/[slug]` | Dive site detail with its real linked trips |
| `/about` | Company facts, real office photo, accreditation |
| `/contact` | Real contact channels (WhatsApp, email, phone, address) |
| `/faq` | Known + not-yet-confirmed question groups |
| `/package-builder` | Build-your-own package with a running total |
| `/privacy` | Privacy policy |
| `/terms` | Terms of use |
| `/design-system` | Internal reference (not in main nav) |
| `error.vue` | 404/error boundary |

## The one rule every test file enforces: never a fabricated fact

Every single spec file in `test/` asserts on **real, approved content** —
real prices (`€350`, `€50`, `€30`...), the real WhatsApp number
(`+20 10 6646 1010` / `https://wa.me/201066461010`), the real email
(`Sales@sharmdiversclub.com`), the real address (`Royal Grand Sharm
Hotel, Hadabet Um Sid, Sharm El Sheikh`), real dive site names (Ras
Mohammed, Tiran, SS Thistlegorm, Dahab Blue Hole & Canyon), real
accreditation (`PADI 5 Star Dive Center`, `CDWS #100601`). A redesign may
restructure how any of this is presented; it must never alter what is
actually said. This is the single most important invariant in this
packet — more important than any selector below.

## Frozen structural selectors

### The landmark pattern (repeated on About/Contact/DiveSites/DiveSiteDetail/Discover/Faq/Home/Privacy/Terms)

```
wrapper.get("main").attributes("id") === "main-content"
wrapper.get("main").attributes("tabindex") === "-1"
```

One real `<main>` landmark per page, keeping the skip link's target
focusable. Any redesign that introduces a different top-level wrapper
must preserve this id/tabindex pair on the actual landmark element.

### `aria-pressed` filter toggles

`DiscoverIndex.spec.ts` and `PackageBuilder.spec.ts` both find a specific
category/offering button by its visible text and assert
`attributes("aria-pressed") === "true"` after clicking it, and assert
the *other* categories' content becomes absent (`not.toContain`) — a
real filter-state contract, not just presentational.

### RTL/locale switching (near-universal)

Almost every page test (`About`, `Contact`, `Faq`, `Home`, `Privacy`,
`Terms`) has a case titled "switches between English LTR and Arabic
RTL" — driven through `useSiteLocale`. Whatever markup changes, the
`dir`/`lang` attribute pair this composable sets must keep working
identically in both directions.

### `WhatsAppFab.spec.ts` / `SiteFooter.spec.ts` / `SiteHeader.spec.ts`

Assert the real `wa.me` URL, `target="_blank"`, an `aria-label` on the
floating WhatsApp button, and (`SiteHeader`) `aria-expanded`/`aria-label`
on the mobile menu toggle plus a "closes the mobile menu when a nav link
is clicked" behavior test. These are real keyboard/AT-accessibility
contracts, not decoration.

### `test/DesignTokens.spec.ts` — the existing (partial) token-drift guard

Already asserts 10 critical colors + `layout.touchTargetMinPx` from
`clients/sharm-divers-club/design/tokens.json` appear byte-identical in
`app/assets/css/main.css`, plus that `public/favicon.svg` matches the
registered mark asset exactly. **Phase 2 extends this check's coverage —
it must never regress what it already verifies.**

## High-risk patterns for later phases

- **`useCountUp` and `useScrollReveal`** (`app/composables/`) are both
  now correctly `prefers-reduced-motion`-aware (the former fixed in this
  same phase — see the WEGO-015 board entry for the bug and fix). Any
  new animated/revealed content must follow the same pattern: check
  reduced-motion *before* setting up an `IntersectionObserver`, never
  only inside the animation callback.
- **`ConditionsWidget.spec.ts`** tests a real external API call
  (`/api/conditions`) with a loading state (`"Checking live
  conditions…"`) and a failure state (`"Live conditions unavailable"`) —
  real network-dependent UI, not static content. A redesign touching
  this component must preserve both states' exact test-asserted text.
- **No Playwright E2E suite exists for this site at all.** If a future
  phase decides real click-path browser testing is worth adding (mirroring
  the ERP's `erp-lifecycle.spec.ts`), that is new test infrastructure to
  build, not something to assume already exists.

## What Phase 1 does not include

No visual changes in this phase. This document is the baseline every
later phase in WEGO-015's website track is checked against.
