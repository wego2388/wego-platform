import { ref } from "vue";
import { beforeEach, vi } from "vitest";

beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  vi.stubGlobal("useState", <T>(_key: string, init?: () => T) => ref(init ? init() : undefined));
  vi.stubGlobal("useRoute", () => ({ params: {} }));
  vi.stubGlobal("createError", (input: { statusCode?: number; statusMessage?: string }) => {
    const error = new Error(input.statusMessage ?? "Error");
    return Object.assign(error, input);
  });
});
