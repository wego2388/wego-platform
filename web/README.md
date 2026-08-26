# Wego web workspace

The foundation contains two Nuxt applications and only two shared packages
with executable responsibilities:

- `apps/erp`: authenticated-application shell boundary. Login/logout (WEGO-001)
  is real and functional; further business screens remain deferred.
- `apps/sharm-divers-club-site`: Arabic/English public foundation for the
  independent Sharm Divers Club client (`products/divers`, WEGO-002). Every
  fact shown traces to `status=approved`/`publishable=true` entries in that
  client's `approved-facts.json`; catalog prices are owner-review-pending and
  always shown as preview. `products/divers` has no public booking-creation
  endpoint, so every booking action routes to the real approved WhatsApp
  channel instead of a fake checkout.
- `packages/design-tokens`: framework-neutral Wego visual tokens.
- `packages/ui`: reusable Vue presentation components; no client behavior.

Use Node 24.19.0 and pnpm 10.34.4:

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm run check
```

Do not place authorization decisions in this workspace. UI visibility is a
usability concern; the API remains the enforcement boundary. Add another app or
package only when it owns an executable responsibility.

## Design tokens and shared UI

`packages/design-tokens` defines color, radius, and font tokens, mapped into
real Tailwind utilities (`bg-wego-*`, `text-wego-*`, `rounded-wego-*`) via the
`@theme` block in `apps/erp/app/assets/css/main.css`:

- **Color**: `canvas`/`surface`/`ink`/`muted`/`border` (neutrals), `accent`/
  `accent-soft` (brand teal), `focus` (accessibility focus ring), and
  `success`/`warning`/`danger` with `-soft` variants (all verified at WCAG AA
  4.5:1 against both `surface` and `canvas`).
- **Radius**: `card` (1.25rem, large surfaces) and `control` (0.75rem, inputs
  and buttons) — one soft-geometry family at two sizes.
- **Font**: Inter, self-hosted via `@fontsource-variable/inter` (no CDN
  dependency).

`packages/ui` exports `WegoFoundationCard`, `WegoButton`, `WegoInput`, and
`WegoAlert` — the shared building blocks `apps/erp`'s pages are built from,
instead of each page hand-rolling its own input/button/alert markup.

**Explicitly deferred, not forgotten:** dark mode, any page/nav/dashboard
beyond what already exists, animation beyond a loading spinner and simple
`prefers-reduced-motion`-respecting transitions, a custom spacing or type
scale (Tailwind's defaults are coherent at this size), and a designed logo
(the current favicon is a placeholder monogram, not a finished mark).
