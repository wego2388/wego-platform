# Mobile Lighthouse performance investigation — 2026-10-11

**Status:** one real, verified fix shipped. The design spec's <1s LCP
target on throttled mobile/slow-4G is **not met** after this fix, and a
second hypothesis (animated hero orbs) was tested and ruled out. Honest
finding, not a declared "done."

## What was tested

Against a fresh local stack (real Postgres, real backend, 41-service
catalog imported, 10 services published) and the site's real production
build, served and measured with `lighthouse` under its own default
throttled mobile preset (150ms RTT, ~1.6Mbps, 4x CPU slowdown) — the same
condition the 2026-10-06 audit used.

**Baseline (before this session's fix):** performance 75, LCP 4.4s, FCP
3.4s, TBT 160ms, Speed Index 3.4s.

### Hypothesis 1 — animated hero "orbs" (ruled out)

Two decorative elements on the homepage (`blur-3xl` + an infinite
`transform`/`scale` CSS animation) are a known performance anti-pattern
(continuous repaint through an expensive blur filter). Tested by
temporarily removing them and re-measuring: LCP 4.4s → 4.4s (no change),
Style & Layout main-thread cost 982ms → 820ms (a real but small drop).
**Not the dominant cause.** Reverted immediately; never shipped.

### Hypothesis 2 — late web-font discovery (confirmed contributor, fixed)

Identified the actual LCP element directly (Lighthouse's own
`largest-contentful-paint-element` audit returned empty both times, so
used a Playwright + CDP session with the same network/CPU throttling
profile and a raw `PerformanceObserver` instead): the homepage's hero
sub-headline paragraph (`copy.hero.body`), not an image. Confirmed no
`<link rel="preload">` existed for any font file — the browser only
discovered the Inter/Fredoka woff2 files after downloading and parsing
the CSS bundle containing their `@font-face` rule, and `font-display:
swap`'s swap-in repaint is literally what the LCP metric waits for on a
text candidate.

**Fix:** `web/apps/sharm-to-go-site/app/app.vue` now preloads the two
critical-path font files (Inter + Fredoka, Latin subset — the only ones
needed for first paint, since every page starts in English per
`useSiteLocale`'s own server/client-match design) via Vite's `?url`
import, which resolves to the real content-hashed build path at build
time — no hardcoded, build-breaking filename.

**Measured result:** FCP 3.4s→3.1s, TBT 160ms→110ms, Speed Index
3.4s→3.2s — all real, verified improvements. **LCP itself barely moved**
(4.4s→4.5s on Lighthouse; a separate direct Playwright probe of the exact
LCP timestamp showed 2216ms→2184ms, both well under Lighthouse's own
reported 4.4s — the two tools' methodologies clearly differ in what they
charge to the document-request phase, but neither shows the preload
meaningfully moving the number).

## Why this session stopped here instead of continuing to chase <1s

No further low-risk, high-confidence lead was identified. The remaining
cost is spread across ordinary SSR-hydration + style/layout work for a
real, content-rich page under 4x CPU throttling — not one blocking
resource or an isolatable bug the way the two hypotheses above were.
Closing the rest of the gap would mean either:
- **Structural/content changes** (shortening the hero's visible text so a
  smaller element becomes the LCP candidate, deferring below-the-fold
  sections) — a design tradeoff, not a pure engineering fix, and not this
  session's call to make unilaterally.
- **A deeper rendering-architecture change** (reducing JS payload,
  rethinking hydration strategy) — real, open-ended effort, not a scoped
  quick fix.

**Recommendation, not a decision:** the <1s target itself may be more
aggressive than is realistic for a real Vue/Nuxt SSR page with custom
webfonts under Lighthouse's own slow-4G+4x-CPU preset — many production
sites don't clear it even well-optimized. Worth the owner's input on
whether to invest further here now, or treat the current, measurably
improved state as acceptable until real-world traffic data says
otherwise.

## Verification

101/101 Vitest tests, `nuxt typecheck`, `eslint` all clean after the
change. Test infrastructure (Postgres container, backend process, site
server) stopped and removed after measurement; nothing left running.
