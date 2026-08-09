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
});
