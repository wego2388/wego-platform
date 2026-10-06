import { afterEach, describe, expect, it, vi } from "vitest";

import { useDevOnlyPage } from "../app/composables/useDevOnlyPage";

afterEach(() => {
  vi.unstubAllGlobals();
  import.meta.env.PROD = false;
});

describe("useDevOnlyPage", () => {
  it("does nothing outside a production build (dev, and this test suite)", () => {
    import.meta.env.PROD = false;
    expect(() => useDevOnlyPage()).not.toThrow();
  });

  it("throws a 404 in a real production build, so the route is unreachable", () => {
    import.meta.env.PROD = true;
    vi.stubGlobal("createError", (input: { statusCode?: number; statusMessage?: string }) => {
      const error = new Error(input.statusMessage ?? "Error");
      return Object.assign(error, input);
    });

    expect(() => useDevOnlyPage()).toThrow("Page not found");
  });
});
