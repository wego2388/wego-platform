import { readFileSync, statSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { artworkSource, brandArtwork, brandArtworkCopy, categoryArtwork } from "../app/content/brandArtwork";

describe("truthful, bounded decorative brand imagery", () => {
  it("has optimized real files for every responsive source", () => {
    for (const kind of ["hero", "desert", "sea"] as const) {
      expect(brandArtwork[kind].width).toBeGreaterThan(0);
      expect(brandArtwork[kind].height).toBeGreaterThan(0);
      for (const width of [480, 960, 1600] as const) {
        const path = `${process.cwd()}/public${artworkSource(kind, width)}`;
        expect(statSync(path).size).toBeLessThan(360_000);
        expect(readFileSync(path).subarray(8, 12).toString()).toBe("WEBP");
      }
    }
  });
  it("discloses artwork in every locale without implying real guests, assets or facilities", () => {
    expect(Object.keys(brandArtworkCopy).sort()).toEqual(["ar", "en", "it", "ru"]);
    for (const text of Object.values(brandArtworkCopy)) expect(text.length).toBeGreaterThan(10);
    expect(categoryArtwork("DESERT")).toBe("desert");
    expect(categoryArtwork("SEA")).toBe("sea");
    for (const category of ["CULTURAL", "SHOWS", "TRANSFERS"] as const) expect(categoryArtwork(category)).toBeNull();
    const tile = readFileSync(`${process.cwd()}/app/components/CategoryTile.vue`, "utf8");
    expect(tile).toContain('v-if="cover"');
    expect(tile).toContain('v-else-if="artwork"');
    // Actual tour detail and gallery keep their approved ERP media contract.
    expect(readFileSync(`${process.cwd()}/app/pages/tour/[slug].vue`, "utf8")).not.toContain("BrandArtwork");
  });
});
