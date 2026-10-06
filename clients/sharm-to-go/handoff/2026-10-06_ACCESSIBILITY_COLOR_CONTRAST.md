# Site accessibility — WCAG 2.1 AA color-contrast, evidence record

**Status:** zero axe-core violations, verified across every public page, in
light mode, dark mode, and Arabic/RTL. This is the first real audit this
site has had — the 2026-10-02 handoff listed "axe WCAG 2.1 AA, 0 issues,
light+dark" as UX-8, not yet started; it found 33 real failures on the
first run, all traced to a genuine class of bugs, not noise.

## What this delivers

- `e2e/tests/sharm-to-go-accessibility.spec.ts` — a new, reusable axe-core
  (`@axe-core/playwright`) suite covering all 9 public pages (home,
  experiences, find-my-trip, about, faq, contact, privacy, terms, track,
  plus the experience detail/request pages when a published service id is
  given), each in light, dark (`page.emulateMedia`), and Arabic/RTL (via
  the real locale-toggle button a visitor uses). Runs against any Sharm To
  Go site instance via `WEGO_STG_E2E_BASE_URL` — independent of the
  shared `playwright.config.ts` default, which targets a different app's
  compose stack. Not yet wired into CI (see "What's left" below).
- Real color-token and markup fixes across 16 files, every one verified by
  re-running the suite to green, not just reasoned about.

## The real bugs found (not false positives)

1. **One color token was doing two incompatible jobs.** `--stg-color-brand-
   sea` was brightened for dark mode because it's used as *text* (nav
   links, prices) — correct for that role. But the same token is also used
   as *solid button backgrounds* with white text (every primary CTA on the
   site), and a color cannot simultaneously be "light enough to read as
   text on a dark page" and "dark enough to host white button text."
   Result: every primary button had 2.11:1 contrast in dark mode (needs
   4.5:1). Fixed with a new, fixed (mode-invariant) `--stg-color-action`
   token for the button role, leaving `--stg-color-brand-sea`'s text role
   untouched. ~20 button/badge backgrounds across the site now use it.
2. **Three of the four category accent colors (sun, sky, terracotta) were
   never contrast-checked as text at all.** Sun specifically: 1.99:1
   against white — barely two-fifths of the required ratio. Sky and
   terracotta were marginal failures in light mode (4.48, 4.24) and real
   failures against the dark surface (3.01, 3.17), since neither had a
   dark-mode value. Fixed with real, computed (not eyeballed) darker
   light-mode / lighter dark-mode pairs — see `main.css`'s own comment for
   the exact sRGB relative-luminance math.
3. **Sun used as a solid background always paired with white text** —
   mathematically can't pass (sun is too light for white text to ever
   clear 4.5:1 against it). This is also the one case the design spec
   itself already documented as a rule ("sun must carry dark text, never
   light") that the implementation simply hadn't followed yet. Fixed by
   making `CategoryAccent.onSolidText` a real per-accent property instead
   of hardcoding `text-white`, and introducing a fixed `--stg-color-on-sun`
   dark-ink token for it — catching a second bug along the way: the
   naive fix (reusing the page's own mode-reactive ink color) inverts to
   near-white in dark mode, which against sun's own unchanged background
   is *worse* (1.92:1) than the original problem.
4. **Lagoon/sand badge backgrounds paired with mode-reactive text colors.**
   Both backgrounds are deliberately fixed-light in both themes (existing,
   correct design decision), but several places paired them with
   `text-sharm-sea` — fine in light mode, but brand-sea brightens for dark
   mode (bug #1's token), so dark mode put light cyan text on a light
   pastel chip (1.78–1.79:1). Found in 9 places across 6 files, including
   the trip finder's own new "Not sure? Let us help" button introduced
   earlier in this same session — caught by this audit before shipping
   unverified, not after. All switched to the fixed `--stg-color-action`
   or `--stg-color-on-sun` tokens as appropriate.
5. **`role="tablist"` on the category filter buttons** (`aria-
   required-children`, critical impact) — a real ARIA misuse, not a color
   issue: `tablist` requires `role="tab"` children and tabpanel-switching
   behavior, neither of which this (a plain filter button group) has.
   Fixed by using `role="group"` instead, which carries no such
   requirement and accurately describes what the control actually is.

## Verification performed (2026-10-06)

Against a fresh, throwaway local stack (real Postgres, real backend jar,
real admin bootstrap, the real 41-service catalog import, one service
taken through a real `publish` call so the detail/request pages had real
data to render) and the site's actual **production build** (`nuxt build`
+ `node .output/server/index.mjs`), not the dev server:

1. First run: 33 failures (every page × light/dark, since the button-
   background bug alone touched something on every single page).
2. After the token/markup fixes: down to 3, all on pages using a category
   accent color the first pass's fix set hadn't reached yet (the "sun"
   text token's own dark-mode value was accidentally left unset — caught
   by the very re-verification this discipline exists for).
3. Final run: **33/33 passed. Zero violations.** Also re-ran the full
   Vitest suite (93/93), `nuxt typecheck` (clean), and `eslint` (clean)
   to confirm none of the 16 file changes broke anything else.

Test containers and processes were stopped and removed after
verification; nothing was left running.

## What's left

- **CI wiring.** The suite runs for real locally but isn't yet part of
  `.github/workflows/ci.yml` — that job currently stands up a *different*
  app's compose stack entirely (port 58080, a different Postgres). Wiring
  Sharm To Go's own stack into CI (or a second job) needs its own careful
  setup this session didn't attempt blind, since a broken shared CI
  workflow affects every contributor's pipeline and couldn't be verified
  without an actual GitHub Actions run.
- **Lighthouse / real-device performance** — not started.
- **Keyboard-only and screen-reader manual passes** — axe catches a large,
  real class of issues (this session found 5 genuine bug classes with it)
  but not everything; a manual pass is still worth doing before launch.
