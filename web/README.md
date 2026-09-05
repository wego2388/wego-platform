# Wego web workspace

Four Nuxt applications and two shared packages with executable responsibilities:

- `apps/erp`: authenticated ERP for Sharm Divers Club — full RBAC/HR/accounting
  business functionality (WEGO-011/WEGO-012/WEGO-013) across 17 authenticated
  routes: a dashboard, Diving Operations (Offerings, Bookings, Divers,
  Equipment, Boat Charters, Course Enrollments), People (Employees,
  Attendance, Leave Requests), Finance (Chart of Accounts, Journal Entries,
  Payroll, Reports), and Administration (Staff Accounts, Roles &
  Permissions). Every page runs inside a real navigation shell (permanent
  desktop sidebar / off-canvas mobile drawer, one DOM, one `<main>`
  landmark, a skip-to-content link) with System/Light/Dark theming
  (WEGO-014). Login/logout, session handling, and every permission gate
  remain exactly as WEGO-001/WEGO-011 built them — this packet only ever
  changed presentation, never authorization or business logic.
- `apps/sharm-to-go-erp` / `apps/sharm-to-go-site`: scaffolding for the
  second Wego Platform client (Sharm To Go), following the
  `TECHNICAL_EXECUTION_PLAN.md` drafted for it. Not yet built out with real
  business screens.
- `apps/sharm-divers-club-site`: Arabic/English public site for the
  independent Sharm Divers Club client (`products/divers`, WEGO-002). Home,
  Discover (18 real, GOV-003-approved prices — 12 diving + 6 water sports —
  filterable, each with its own `/discover/[code]` detail page), About, FAQ,
  Contact, Privacy, Terms, and a living design system, with a mobile nav menu
  and locale persisted per-visitor. Every fact traces to
  `status=approved`/`publishable=true` entries in the client's
  `approved-facts.json`/`catalog.dive-core.v1.json`. Diving + water sports by
  the owner's explicit scope decisions — the same catalog's other non-diving
  offers (desert safari, sightseeing, snorkeling, transfers) stay unused.
  `products/divers` has no public booking-creation endpoint, so every
  inquiry action routes to the real approved WhatsApp channel instead of a
  fake checkout.
- `packages/design-tokens`: framework-neutral Wego visual tokens — color
  (light + dark palettes), radius, z-index, motion (duration/easing), and
  control-size scales, mapped into real Tailwind utilities (`bg-wego-*`,
  `text-wego-*`, `rounded-wego-*`, `z-wego-*`) via each consuming app's own
  `@theme` block. Every color pairing is verified at WCAG AA (4.5:1 text /
  3:1 UI) by a real relative-luminance test, not eyeballed
  (`packages/design-tokens/test/design-tokens.spec.ts`).
- `packages/ui`: reusable Vue presentation components — `WegoButton`,
  `WegoInput`, `WegoSelect`, `WegoTextarea`, `WegoCheckbox`, `WegoAlert`,
  `WegoBadge`, `WegoPanel`, `WegoPageHeader`, `WegoDialog` (built on the
  native `<dialog>` element for real focus-trap/Escape/restore behavior),
  `WegoEmptyState`, `WegoPagination`. No client behavior — presentation
  only, each with its own Vitest suite.

Use Node 24.19.0 and pnpm 10.34.4:

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm run check
```

Do not place authorization decisions in this workspace. UI visibility is a
usability concern; the API remains the enforcement boundary. Add another app or
package only when it owns an executable responsibility.

## Trying it locally

`apps/erp`'s live demo route, `/design-system` (gated behind sign-in,
`noindex,nofollow`), renders every shared component in both themes with a
working theme toggle — the fastest way to see the whole design system at
once without navigating the real business pages.

## Theming

Dark mode is opt-in per consuming app, not a shared default — three other
Wego web apps in this workspace have never built or verified dark
rendering, so `design-tokens/src/tokens.css`'s dark palette only activates
under an explicit `:root[data-theme="dark"]` selector. `apps/erp` is
currently the only app that applies it, via `useTheme()`
(System/Light/Dark, persisted in `localStorage`, live-reactive to OS
changes while "System" is selected) plus an early inline `<head>` script
that resolves the theme before Vue mounts, avoiding a flash of the wrong
theme.

## Accessibility

`apps/erp` ships a skip-to-content link, a single stable `<main>` landmark
per page, keyboard-operable navigation (Escape closes the mobile drawer
and returns focus to its toggle), and a `prefers-reduced-motion` guard
covering both CSS transitions and the two JS-driven scroll calls that
would otherwise bypass it. Verified with a real `@axe-core/playwright`
sweep across all 17 authenticated routes, 3 widths (390/768/1440px), and
both themes against populated (not empty) data — see WEGO-014 Phase 8 in
`docs/execution/WEGO_EXECUTION_BOARD.md` for the full evidence trail.

**Explicitly deferred, not forgotten:** a designed logo (the current
favicon is a placeholder monogram, not a finished mark), and the public
website (`sharm-divers-club-site`) and mobile app UX/UI redesigns —
deferred to a future packet by the owner's own sequencing call, not part
of WEGO-014.
