import { beforeEach, vi } from "vitest";

// See web/apps/erp/test/setup.ts's own copy of this comment for the full
// rationale — same no-Nuxt-runtime environment, same need to stub useHead
// and reset sessionStorage between tests now that login.vue persists a
// session there.
beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  sessionStorage.clear();
});
