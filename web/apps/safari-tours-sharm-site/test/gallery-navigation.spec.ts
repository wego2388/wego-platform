import { describe, expect, it } from "vitest";
import { galleryStep, gallerySwipe } from "../app/utils/galleryNavigation";
describe("multi-photo gallery keyboard and touch navigation", () => {
  it("wraps both directions and handles single/empty galleries without NaN", () => {
    expect(galleryStep(0, -1, 3)).toBe(2); expect(galleryStep(2, 1, 3)).toBe(0);
    expect(galleryStep(0, 1, 1)).toBe(0); expect(galleryStep(3, 1, 0)).toBe(0);
  });
  it("respects RTL swiping and leaves normal vertical scrolling alone", () => {
    expect(gallerySwipe(-80, 10, false)).toBe(1); expect(gallerySwipe(-80, 10, true)).toBe(-1);
    expect(gallerySwipe(80, 10, false)).toBe(-1);
    expect(gallerySwipe(-20, 0, false)).toBe(0); expect(gallerySwipe(-80, 100, false)).toBe(0);
  });
});
