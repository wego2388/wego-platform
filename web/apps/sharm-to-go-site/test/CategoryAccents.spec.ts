import { describe, expect, it } from "vitest";
import { accentForIndex, categoryAccents } from "../app/content/categoryAccents";

describe("category accent cycling", () => {
  it("assigns each of the four homepage categories its own accent", () => {
    const seen = new Set(categoryAccents.map((accent) => accent.solid));
    expect(seen.size).toBe(categoryAccents.length);
  });

  it("cycles back to the first accent once the category count exceeds four", () => {
    expect(accentForIndex(0)).toBe(accentForIndex(4));
    expect(accentForIndex(1)).toBe(accentForIndex(5));
  });

  it("never throws or returns undefined for an unmatched category (index -1)", () => {
    expect(accentForIndex(-1)).toBeDefined();
    expect(accentForIndex(-1)).toBe(categoryAccents[categoryAccents.length - 1]);
  });
});
