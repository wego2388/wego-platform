import { beforeEach, vi } from "vitest";

// Nitro's real auto-import — stubbed as identity so server/api/*.ts route
// handlers can be imported and called directly in a plain Vitest test (see
// sharm-divers-club-site's test/setup.ts for the same convention). Set at
// module scope (not inside beforeEach): a route module calls this at
// import time, which happens before any beforeEach hook would run.
vi.stubGlobal("defineEventHandler", <T>(handler: T) => handler);

beforeEach(() => {
  vi.stubGlobal("useHead", () => {});
  vi.stubGlobal("useRoute", () => ({ params: {} }));
  vi.stubGlobal("useRuntimeConfig", () => ({ travelMarketplaceApiBase: "http://localhost:8081" }));
  vi.stubGlobal("getQuery", (event: { query?: Record<string, unknown> }) => event.query ?? {});
  vi.stubGlobal("getRouterParam", (event: { params?: Record<string, string> }, name: string) => event.params?.[name]);
  vi.stubGlobal("setResponseStatus", () => {});
  vi.stubGlobal("createError", (input: { statusCode?: number; statusMessage?: string }) => {
    const error = new Error(input.statusMessage ?? "Error");
    return Object.assign(error, input);
  });
});
