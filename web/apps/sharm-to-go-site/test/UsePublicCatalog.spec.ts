import { describe, expect, it } from "vitest";

import { approximateUsdPrice } from "../app/composables/usePublicCatalog";

describe("approximateUsdPrice", () => {
  it("rounds to the nearest whole dollar, never showing false precision for an approximation", () => {
    expect(approximateUsdPrice("550.00")).toBe("10");
    expect(approximateUsdPrice("55.00")).toBe("1");
    expect(approximateUsdPrice("0.00")).toBe("0");
  });

  it("is a pure function of the EGP amount — same input always gives the same output", () => {
    expect(approximateUsdPrice("6000.00")).toBe(approximateUsdPrice("6000.00"));
  });
});
