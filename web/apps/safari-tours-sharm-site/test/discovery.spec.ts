import { describe, expect, it } from "vitest";
import type { Tour } from "@wego/api-contract";
import { toCatalogTour } from "../app/composables/useCatalog";
import { activeFilterCount, applyTourFilters, filtersFromQuery, filtersToQuery } from "../app/composables/useTourFilters";
import { discoveryCopy } from "../app/content/discovery";
import { durationBucket, formatDuration, parseDuration } from "../app/utils/tourDuration";
import { foldSearchText } from "../app/utils/tourSearch";

function tour(overrides: Partial<Tour>): Tour {
  return {
    id: overrides.slug ?? "id",
    slug: "tour",
    category: "DESERT",
    durationText: "2–3 hours",
    priceAdult: { amount: "35.00", currencyCode: "EUR" },
    priceChild: null,
    capacity: 20,
    availableTimeSlots: ["MORNING"],
    sortOrder: 0,
    isActive: true,
    nameEn: null,
    tourType: "TOUR",
    imageUrl: null,
    cancellationPolicy: "STANDARD",
    pricingNote: null,
    localized: null,
    ...overrides,
  } as Tour;
}

const catalog = [
  tour({ slug: "sunset-quad-bike", nameEn: "Sunset Quad Bike", sortOrder: 1, durationText: "2–3 hours", availableTimeSlots: ["SUNSET"] }),
  tour({
    slug: "ras-mohamed-boat",
    nameEn: "Ras Mohamed Boat Trip",
    category: "SEA",
    sortOrder: 2,
    durationText: "8–9 hours",
    priceAdult: { amount: "40.00", currencyCode: "EUR" },
    localized: { locale: "ar", name: "رحلة رأس محمد بالمركب", shortDescription: "سنوركل في أجمل شعاب البحر الأحمر" },
  }),
  tour({ slug: "diving-course", nameEn: "Diving Course", category: "SEA", sortOrder: 3, durationText: "2–3 days", priceAdult: { amount: "400.00", currencyCode: "EUR" } }),
  tour({ slug: "cairo-by-bus", nameEn: "Cairo by Bus", category: "CULTURAL", sortOrder: 4, durationText: "20–24 hours", priceAdult: { amount: "75.00", currencyCode: "EUR" } }),
].map(toCatalogTour);

describe("tour durations", () => {
  it("reads ranges, units and 'about'", () => {
    expect(parseDuration("2–3 hours")).toEqual({ min: 2, max: 3, unit: "hours", approximate: false });
    expect(parseDuration("About 4 hours (18:00–22:00)")).toEqual({ min: 4, max: 4, unit: "hours", approximate: true });
    expect(parseDuration("10–15 minutes")?.unit).toBe("minutes");
    expect(parseDuration("Full day")).toBeNull();
  });

  it("buckets by the longest stated length", () => {
    expect(durationBucket("1 hour")).toBe("short");
    expect(durationBucket("2–3 hours")).toBe("short");
    expect(durationBucket("5–6 hours")).toBe("half");
    expect(durationBucket("8–9 hours")).toBe("full");
    expect(durationBucket("20–24 hours")).toBe("full");
    expect(durationBucket("2–3 days")).toBe("multi");
    expect(durationBucket("unknown")).toBeNull();
  });

  it("keeps English text and translates the unit elsewhere", () => {
    expect(formatDuration("About 4 hours (18:00–22:00)", "en")).toBe("About 4 hours (18:00–22:00)");
    expect(formatDuration("2–3 hours", "it")).toBe("2–3 ore");
    expect(formatDuration("About 4 hours (18:00–22:00)", "ru")).toBe("Около 4 ч");
    expect(formatDuration("2–3 days", "ar")).toBe("٢–٣ أيام");
    expect(formatDuration("Full day", "ar")).toBe("Full day");
  });
});

describe("tour search and filters", () => {
  it("folds Arabic letter forms and Latin accents", () => {
    expect(foldSearchText("رحلة إلى")).toBe("رحله الي");
    expect(foldSearchText("Città")).toBe("citta");
  });

  it("parses only known values from the URL and writes a short canonical query", () => {
    const filters = filtersFromQuery({ cat: "sea,DESERT,bogus", dur: "full,forever", time: "sunset", max: "50", sort: "priceAsc", q: "boat" });
    expect(filters).toEqual({ q: "boat", categories: ["SEA", "DESERT"], durations: ["full"], slots: ["SUNSET"], maxPrice: 50, sort: "priceAsc" });
    expect(filtersToQuery(filters)).toEqual({ q: "boat", cat: "sea,desert", dur: "full", time: "sunset", max: "50", sort: "priceAsc" });
    expect(filtersFromQuery({ max: "37", sort: "evil" })).toMatchObject({ maxPrice: null, sort: "recommended" });
    expect(filtersToQuery(filtersFromQuery({}))).toEqual({});
    expect(activeFilterCount(filters)).toBe(5);
  });

  it("finds tours by localized text, English name or category name in any language", () => {
    const find = (q: string) => applyTourFilters(catalog, filtersFromQuery({ q })).map((e) => e.tour.slug);
    expect(find("quad")).toEqual(["sunset-quad-bike"]);
    expect(find("راس محمد")).toEqual(["ras-mohamed-boat"]);
    expect(find("snorkel")).toEqual([]);
    expect(find("سنوركل")).toEqual(["ras-mohamed-boat"]);
    expect(find("ras boat")).toEqual(["ras-mohamed-boat"]);
    expect(find("")).toHaveLength(4);
  });

  it("combines category, duration, time and price filters and sorts", () => {
    const slugs = (query: Record<string, string>) => applyTourFilters(catalog, filtersFromQuery(query)).map((e) => e.tour.slug);
    expect(slugs({ cat: "sea" })).toEqual(["ras-mohamed-boat", "diving-course"]);
    expect(slugs({ dur: "multi" })).toEqual(["diving-course"]);
    expect(slugs({ time: "sunset" })).toEqual(["sunset-quad-bike"]);
    expect(slugs({ max: "50" })).toEqual(["sunset-quad-bike", "ras-mohamed-boat"]);
    expect(slugs({ sort: "priceDesc" })[0]).toBe("diving-course");
    expect(slugs({ sort: "duration" })).toEqual(["sunset-quad-bike", "ras-mohamed-boat", "cairo-by-bus", "diving-course"]);
  });

  it("uses published localized text, falling back to the catalogue name", () => {
    expect(catalog[1]).toMatchObject({ name: "رحلة رأس محمد بالمركب", textLocale: "ar" });
    expect(catalog[0]).toMatchObject({ name: "Sunset Quad Bike", summary: null, textLocale: null });
  });
});

describe("discovery copy", () => {
  it("has the same shape in every language", () => {
    const shape = (value: unknown): unknown =>
      typeof value === "function" ? "fn" : Array.isArray(value) ? value.map(shape) : value && typeof value === "object"
        ? Object.fromEntries(Object.entries(value).map(([k, v]) => [k, shape(v)]))
        : typeof value;
    const reference = shape(discoveryCopy.en);
    for (const locale of ["ar", "ru", "it"] as const) expect(shape(discoveryCopy[locale])).toEqual(reference);
  });

  it("pluralises counts", () => {
    expect(discoveryCopy.ru.tours.results(1)).toBe("1 тур");
    expect(discoveryCopy.ru.tours.results(3)).toBe("3 тура");
    expect(discoveryCopy.ru.tours.results(11)).toBe("11 туров");
    expect(discoveryCopy.ar.tours.results(2)).toBe("رحلتان");
    expect(discoveryCopy.ar.tours.results(30)).toBe("30 رحلة");
  });

  it("keeps the E0 headline on the English home page", () => {
    expect(discoveryCopy.en.hero.title).toBe("Sharm El Sheikh Tours & Excursions — Book Direct");
  });
});

describe("request-only tours", () => {
  it("sort after priced tours in both price orders and never pass a price cap", () => {
    const withRequest = [
      ...catalog,
      toCatalogTour(tour({ slug: "private-boat", tourType: "REQUEST_ONLY", sortOrder: 9, priceAdult: { amount: "0.00", currencyCode: "EUR" } })),
    ];
    const slugs = (query: Record<string, string>) => applyTourFilters(withRequest, filtersFromQuery(query)).map((e) => e.tour.slug);
    expect(slugs({ sort: "priceAsc" }).at(-1)).toBe("private-boat");
    expect(slugs({ sort: "priceDesc" }).at(-1)).toBe("private-boat");
    expect(slugs({ max: "25" })).not.toContain("private-boat");
  });

  it("caps the search text written to the URL", () => {
    expect(filtersToQuery({ ...filtersFromQuery({}), q: "x".repeat(200) }).q).toHaveLength(80);
  });
});

describe("tours from an older backend", () => {
  it("get an empty price-option list instead of crashing pages", () => {
    const legacy = { ...tour({ slug: "legacy" }) } as Record<string, unknown>;
    delete legacy.priceBasis;
    delete legacy.priceOptions;
    const entry = toCatalogTour(legacy as unknown as Tour);
    expect(entry.tour.priceBasis).toBe("PER_PERSON");
    expect(entry.tour.priceOptions).toEqual([]);
  });
});
