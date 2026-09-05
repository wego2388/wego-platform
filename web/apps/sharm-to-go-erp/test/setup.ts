import { beforeEach, vi } from "vitest";

// See web/apps/erp/test/setup.ts's own copy of this comment for the full
// rationale — same no-Nuxt-runtime environment, same need to stub useHead
// and reset sessionStorage between tests now that login.vue persists a
// session there.
beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  // definePageMeta (the new layout: "app-shell" declarations) is also a
  // Nuxt build-time macro, unavailable here — same reasoning as useHead
  // above. Tests mount pages directly, not through Nuxt's own
  // layout-selection mechanism; that's covered by mounting app-shell.vue
  // itself and by a real production build/serve check.
  vi.stubGlobal("definePageMeta", () => {});
  sessionStorage.clear();
});
