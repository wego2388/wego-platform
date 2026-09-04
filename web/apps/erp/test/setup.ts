import { beforeEach, vi } from "vitest";

// Nuxt auto-imports `useHead` at build time; this plain @vue/test-utils
// environment has no Nuxt runtime, so page components that call it (setting
// the tab title/meta) would otherwise throw "useHead is not defined" on
// mount. Tests here assert rendered content, not document head state, so a
// no-op stub is sufficient. Re-stubbed before every test, not just once at
// startup: individual spec files call `vi.unstubAllGlobals()` in their own
// `afterEach` to reset their own `fetch` stubs, which would otherwise wipe
// this one out too after the first test in the file.
beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  // Same reason as useHead above: definePageMeta (WEGO-014's layout: "app-shell"
  // declarations) is also a Nuxt build-time macro, unavailable here. Tests
  // mount pages directly and don't exercise Nuxt's own layout-selection
  // mechanism — that's covered separately by mounting app-shell.vue itself
  // (see AppShell.spec.ts) and by the real E2E suite against the built app.
  vi.stubGlobal("definePageMeta", () => {});
  // login.vue now persists the session to sessionStorage; without this,
  // a test that logs in successfully would leak that session into the
  // next test in the same file (happy-dom's storage isn't reset per test).
  sessionStorage.clear();
});
