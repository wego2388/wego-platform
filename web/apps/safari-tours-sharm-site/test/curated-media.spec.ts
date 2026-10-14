import { readFileSync, statSync } from "node:fs";
import { describe, expect, it } from "vitest";
import images from "../app/content/curatedMedia.json";
import { curatedCategoryCovers, curatedCover, curatedSrcset, curatedTourMedia, resolveTourMedia } from "../app/utils/curatedMedia";

describe("owner-approved bundled photos", () => {
  it("provides one cover per curated active tour, four captions and bounded actual WebP files", () => {
    const tours = [...new Set(images.map(image => image.tour))];
    expect(tours).toHaveLength(27);
    expect(images.length).toBe(47);
    expect(new Set(images.map(image => image.id)).size).toBe(images.length);
    let total = 0;
    for (const slug of tours) expect(images.filter(image => image.tour === slug && image.isCover)).toHaveLength(1);
    for (const image of images) {
      expect(Object.keys(image.alt).sort()).toEqual(["ar", "en", "it", "ru"]);
      for (const caption of Object.values(image.alt)) expect(caption.length).toBeGreaterThan(10);
      expect(image.sourceSha256).toMatch(/^[a-f0-9]{64}$/);
      for (const variant of image.variants) {
        expect(variant.path).toMatch(/^\/images\/catalog\/[a-z0-9-]+\/[a-z0-9-]+\.webp$/);
        const file = `${process.cwd()}/public${variant.path}`;
        expect(readFileSync(file).subarray(8, 12).toString()).toBe("WEBP");
        const bytes = statSync(file).size;
        expect(bytes).toBeLessThanOrEqual(420_000);
        total += bytes;
        expect(variant.width).toBeLessThanOrEqual(image.width);
        expect(variant.height).toBeLessThanOrEqual(image.height);
      }
      expect(curatedSrcset(image.path)).toContain(`${image.path} ${image.width}w`);
    }
    expect(total).toBeLessThan(12_000_000);
  });
  it("keeps published dashboard media first, fills single-cover galleries, and replaces defaults for a full managed gallery", () => {
    const managed = [{ path: "/media/tours/test/approved.jpg", width: 500, height: 400, alt: "Approved owner photo", isCover: true }];
    const augmented = resolveTourMedia("sunset-quad-bike", "en", managed);
    expect(augmented[0]).toBe(managed[0]);
    expect(augmented.slice(1)).toHaveLength(2);
    expect(augmented.slice(1).every(image => !image.isCover)).toBe(true);
    const fullGallery = [...managed, { ...managed[0]!, path: "/media/tours/test/second.jpg", isCover: false }];
    expect(resolveTourMedia("sunset-quad-bike", "en", fullGallery)).toBe(fullGallery);
    expect(resolveTourMedia("sunset-quad-bike", "ar", [])).toHaveLength(2);
    expect(curatedCover("sunset-quad-bike", "ar")?.alt).toMatch(/[\u0600-\u06ff]/);
    expect(curatedTourMedia("unknown", "en")).toEqual([]);
    for (const inactive of ["private-boat", "intro-diving", "crocodile-show"]) expect(curatedTourMedia(inactive, "en")).toEqual([]);
    expect(curatedSrcset(managed[0]!.path)).toBeUndefined();
  });
  it("has category defaults without creating catalog records or external image URLs", () => {
    for (const locale of ["en", "ar", "ru", "it"] as const) {
      const covers = curatedCategoryCovers(locale);
      expect(Object.keys(covers).sort()).toEqual(["CULTURAL", "DESERT", "SEA", "SHOWS", "TRANSFERS"]);
      for (const cover of Object.values(covers)) expect(cover.path).toMatch(/^\/images\/catalog\//);
    }
  });
});
