import { ref } from "vue";
import { beforeEach, vi } from "vitest";

// Nitro's real auto-import — stubbed as identity so server/api/*.ts route
// handlers can be imported and called directly in a plain Vitest test. Set
// at module scope (not inside beforeEach): a route module calls this at
// import time, which happens before any beforeEach hook would run.
vi.stubGlobal("defineEventHandler", <T>(handler: T) => handler);

beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  vi.stubGlobal("useState", <T>(_key: string, init?: () => T) => ref(init ? init() : undefined));
  vi.stubGlobal("useRoute", () => ({ params: {} }));
  vi.stubGlobal("createError", (input: { statusCode?: number; statusMessage?: string }) => {
    const error = new Error(input.statusMessage ?? "Error");
    return Object.assign(error, input);
  });
});
