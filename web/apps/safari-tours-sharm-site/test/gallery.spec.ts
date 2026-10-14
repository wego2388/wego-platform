import { describe, expect, it } from "vitest";
import type { CatalogTour } from "../app/composables/useCatalog";
import { galleryCopy } from "../app/content/gallery";
import { gallerySections } from "../app/utils/gallery";

function item(slug: string, category: CatalogTour["tour"]["category"], active = true, cover?: CatalogTour["tour"]["cover"]): CatalogTour {
  return {
    name: slug,
    summary: null,
    textLocale: "en",
    tour: { id: slug, slug, category, isActive: active, cover } as CatalogTour["tour"],
  };
}

describe("photo-led gallery", () => {
  it("shows only current active API tours that have approved media, capped per category", () => {
    const catalog = [
      item("sunset-quad-bike", "DESERT"),
      item("inactive-private", "DESERT", false, { path: "/media/private.jpg", alt: "Private", width: 400, height: 300, isCover: true }),
      item("no-photo-test", "SEA"),
      ...Array.from({ length: 7 }, (_, index) => item(`active-${index}`, "SEA", true, { path: `/media/${index}.jpg`, alt: `Sea ${index}`, width: 400, height: 300, isCover: true })),
    ];
    const sections = gallerySections(catalog, "en");
    expect(sections.map(section => section.category)).toEqual(["DESERT", "SEA"]);
    expect(sections[0]?.items.map(entry => entry.entry.tour.slug)).toEqual(["sunset-quad-bike"]);
    expect(sections[1]?.items).toHaveLength(4);
    expect(sections.flatMap(section => section.items).some(entry => entry.entry.tour.slug === "inactive-private")).toBe(false);
    expect(sections.flatMap(section => section.items).some(entry => entry.entry.tour.slug === "no-photo-test")).toBe(false);
  });

  it("prefers a published managed cover and localizes the bundled fallback", () => {
    const managed = { path: "/media/current.jpg", alt: "Published", width: 800, height: 600, isCover: true };
    const catalog = [item("sunset-quad-bike", "DESERT", true, managed), item("banana-boat", "SEA")];
    expect(gallerySections(catalog, "ar")[0]?.items[0]?.cover.path).toBe("/media/current.jpg");
    expect(gallerySections(catalog, "ar")[1]?.items[0]?.cover.alt).toMatch(/[\u0600-\u06ff]/);
  });

  it("has original human-readable copy for every launch language", () => {
    for (const locale of ["en", "ar", "ru", "it"] as const) {
      expect(galleryCopy[locale].title.length).toBeGreaterThan(8);
      expect(galleryCopy[locale].note.length).toBeGreaterThan(20);
    }
  });
});
