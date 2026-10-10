import { beforeEach, vi } from "vitest";
import { ref } from "vue";

// Nitro's real auto-import — stubbed as identity so server/api/*.ts route
// handlers can be imported and called directly in a plain Vitest test (see
// sharm-divers-club-site's test/setup.ts for the same convention). Set at
// module scope (not inside beforeEach): a route module calls this at
// import time, which happens before any beforeEach hook would run.
vi.stubGlobal("defineEventHandler", <T>(handler: T) => handler);

beforeEach(() => {
  const state = new Map<string, ReturnType<typeof ref>>();
  vi.stubGlobal("useState", (key: string, init: () => unknown) => {
    if (!state.has(key)) state.set(key, ref(init()));
    return state.get(key);
  });
  vi.stubGlobal("useHead", () => {});
  vi.stubGlobal("useRoute", () => ({ params: {}, query: {} }));
  vi.stubGlobal("useRouter", () => ({ push: () => {}, replace: () => {} }));
  vi.stubGlobal("useRuntimeConfig", () => ({ travelMarketplaceApiBase: "http://localhost:8081" }));
  vi.stubGlobal("getQuery", (event: { query?: Record<string, unknown> }) => event.query ?? {});
  vi.stubGlobal("getRouterParam", (event: { params?: Record<string, string> }, name: string) => event.params?.[name]);
  vi.stubGlobal("setResponseStatus", () => {});
  vi.stubGlobal("setResponseHeader", () => {});
  vi.stubGlobal("getHeader", (event: { headers?: Record<string, string> }, name: string) => event.headers?.[name]);
  vi.stubGlobal("readBody", async (event: { body?: unknown }) => event.body);
  vi.stubGlobal("createError", (input: { statusCode?: number; statusMessage?: string }) => {
    const error = new Error(input.statusMessage ?? "Error");
    return Object.assign(error, input);
  });
  // useSiteLocale persists the chosen locale to localStorage by design — real
  // behavior across page loads for a real visitor, but it must not leak from
  // one test to the next.
  localStorage.clear();
});
