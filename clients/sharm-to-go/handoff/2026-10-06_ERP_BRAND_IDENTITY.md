# ERP brand identity — UX-6 (partial), evidence record

**Status:** the ERP's color/font identity now matches Sharm To Go's
owner-approved design tokens instead of the generic shared Wego palette.
This is the concrete fix for the owner's stated complaint ("مش عايجبني
ديزاين الداش بورد") — the dashboard no longer looks like a generic Wego
admin panel. Structural pieces of UX-6 (a dedicated "Today" run sheet page,
a sales-paused banner) are not part of this change — see "What's left"
below.

## What was actually wrong

The ERP (`web/apps/sharm-to-go-erp`) imports two stylesheets in order:
`@wego/design-tokens/tokens.css` (the shared platform default — teal
accent `#087f74`, Inter-only, no brand identity, used by every Wego admin
panel) then its own `assets/css/main.css`. The site
(`web/apps/sharm-to-go-site`) already had Sharm To Go's full brand palette
in its own `main.css` (sea/sun/lagoon/terracotta, Fredoka display font,
dark mode) — the ERP never did. Confirmed by direct inspection before
changing anything: the ERP's `main.css` only *mapped* `--wego-color-*`
variables into Tailwind's `@theme`, it never *set* brand values for them,
so the generic file's values silently won.

## What changed

`web/apps/sharm-to-go-erp/app/assets/css/main.css`: added a `:root` block
(and its `:root[data-theme="dark"]` counterpart, matching this app's own
existing dark-mode toggle in `useTheme.ts`) that overrides every identity
token — canvas, surface, ink, border, accent, focus, radius, display font
— with the exact values from `clients/sharm-to-go/design/tokens.json`,
the same source the site already uses. Added the Fredoka font import and
applied it to `h1`/`h2`/`h3`. Also added four `--wego-category-*` custom
properties (sea/desert/transfers/city) for future per-category accent use
on stat cards, per the design spec's `categoryAccent` map.

Semantic status colors (success/warning/danger/info) were deliberately
**left on the generic system's values** — tokens.json's light-mode
success/danger already match them exactly, the owner's design spec never
asked to change these, and the generic file's dark-mode values are the
only ones in this codebase with a documented WCAG-AA verification. Full
accessibility re-verification across both themes is UX-8, a later packet
— reusing already-verified values here is the conservative choice, not
a shortcut.

## Verification performed (2026-10-06)

No deployed/shared environment was touched. Built and ran a fresh
throwaway stack: real Postgres 16, the real backend jar, a real admin
bootstrap, the real 41-service catalog import (via
`import_catalog.py`, task #66), then the ERP's actual Nuxt dev server.

Browser automation (Claude in Chrome) wasn't available in this session,
so verification used Playwright directly instead of being skipped:
installed the already-present `@playwright/test` dependency's Chromium,
logged in for real, and screenshotted the live rendered dashboard and
services list. Confirmed visually: sea-teal accent and active-nav
highlight, Fredoka headings, 20px-rounded cards, light sea-tinted canvas,
and all 41 imported services listed with correct names/categories/status.
Also ran the ERP's own test suite (`vitest run`: 74/74 passed) and
`nuxt typecheck` is unaffected (CSS-only change, no script/type edits).
`eslint` was run too; it reports 7 pre-existing `vue/no-multiple-template-root`
errors in page files this change never touched — confirmed via
`git status` before committing that only `main.css` was modified, so
these are not introduced by this packet and are left alone (out of this
packet's scope).

Test containers and processes were stopped and removed after
verification; nothing was left running.

## What's left for UX-6 (not done in this packet)

- A dedicated "Today" run-sheet page for daily operations (per the
  handoff's own UX-6 scope: "صفحة «النهارده» للتشغيل اليومي").
- A sales-paused alert banner.
- Applying the `--wego-category-*` accent colors to the dashboard's stat
  cards (left/top colored bar per category, as planned, not yet wired up
  — the tokens exist, the visual treatment doesn't yet).

## What's left for the overall UX packet (UX-1 through UX-8)

UX-1/UX-2 (tokens, components, catalog/home pages) already appear
substantially done on the **site** — it already has the full brand
palette, dark mode, hero gradients, card-lift and reveal-stagger motion,
and `prefers-reduced-motion` support, confirmed by reading its `main.css`
directly. Not yet verified in this packet: the trip detail page's booking
calendar (UX-3), the request/confirmation flow (UX-4), "ساعدني أختار" /
trip finder and info pages (UX-5), SEO/analytics (UX-7), and the full
axe + Lighthouse + Playwright-in-CI pass (UX-8). Those need their own
dedicated review before this phase can be called complete — flagged
honestly rather than assumed.
