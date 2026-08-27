import { ref } from "vue";
import { beforeEach, vi } from "vitest";

beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  vi.stubGlobal("useState", <T>(_key: string, init?: () => T) => ref(init ? init() : undefined));
});
