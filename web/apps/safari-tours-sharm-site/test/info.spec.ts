import { describe, expect, it } from "vitest";
import type { Tour } from "@wego/api-contract";
import { toCatalogTour } from "../app/composables/useCatalog";
import { infoCopy } from "../app/content/info";
import { answersFromQuery, answersToQuery, pricePerGuest, suggestTours } from "../app/utils/tripFinder";

function tour(overrides: Partial<Tour>): Tour {
  return {
    id: overrides.slug ?? "id", slug: "tour", category: "DESERT", durationText: "2–3 hours",
    priceAdult: { amount: "35.00", currencyCode: "EUR" }, priceChild: null, capacity: 20, availableTimeSlots: ["MORNING"],
    sortOrder: 0, isActive: true, nameEn: null, tourType: "TOUR", imageUrl: null, cancellationPolicy: "STANDARD",
    pricingNote: null, localized: null, priceBasis: "PER_PERSON", priceOptions: [], ...overrides,
  } as Tour;
}

const catalog = [
  tour({ slug: "quad", sortOrder: 1, durationText: "2–3 hours" }),
  tour({ slug: "safari", sortOrder: 2, durationText: "5–6 hours", priceAdult: { amount: "45.00", currencyCode: "EUR" } }),
  tour({ slug: "buggy", sortOrder: 3, priceBasis: "PER_UNIT", priceAdult: { amount: "30.00", currencyCode: "EUR" },
    priceOptions: [{ code: "buggy", label: "Two-seat buggy", seatsPerUnit: 2, price: { amount: "30.00", currencyCode: "EUR" } }] }),
  tour({ slug: "ras", category: "SEA", sortOrder: 4, durationText: "8–9 hours", priceAdult: { amount: "40.00", currencyCode: "EUR" } }),
  tour({ slug: "cairo", category: "CULTURAL", sortOrder: 5, durationText: "20–24 hours", priceAdult: { amount: "75.00", currencyCode: "EUR" } }),
  tour({ slug: "course", category: "SEA", sortOrder: 6, durationText: "2–3 days", priceAdult: { amount: "400.00", currencyCode: "EUR" } }),
  tour({ slug: "transfer", category: "TRANSFERS", sortOrder: 7, tourType: "TRANSFER" }),
].map(toCatalogTour);

const slugs = (query: Record<string, string>) => suggestTours(catalog, answersFromQuery(query)).map((s) => s.entry.tour.slug);

describe("trip finder", () => {
  it("reads only known answers from the URL and writes a short query", () => {
    expect(answersFromQuery({ like: "sea", who: "family", time: "half", budget: "50" })).toEqual({ like: "SEA", who: "family", time: "half", budget: "50" });
    expect(answersFromQuery({ like: "space", time: "forever", budget: "7" })).toEqual({ like: null, who: null, time: "any", budget: "any" });
    expect(answersToQuery({ like: "DESERT", who: null, time: "any", budget: "any" })).toEqual({ like: "desert" });
  });

  it("never suggests transfers, keeps the chosen experience and respects time and budget", () => {
    expect(slugs({})).not.toContain("transfer");
    expect(slugs({ like: "desert" })).toEqual(["quad", "safari", "buggy"]);
    expect(slugs({ like: "desert", time: "short" })).toEqual(["quad", "buggy"]);
    expect(slugs({ budget: "25" })).toEqual(["buggy"]);
    expect(slugs({ time: "full" })).not.toContain("course");
  });

  it("shares a unit's price between its seats when comparing with a budget", () => {
    expect(pricePerGuest(catalog[2]!)).toBe(15);
  });

  it("keeps families away from very long trips and explains every suggestion", () => {
    expect(slugs({ who: "family" })).not.toContain("cairo");
    const [first] = suggestTours(catalog, answersFromQuery({ like: "desert", who: "friends" }));
    expect(first?.reasons).toContain("category");
    const buggy = suggestTours(catalog, answersFromQuery({ like: "desert", who: "friends" })).find((s) => s.entry.tour.slug === "buggy");
    expect(buggy?.reasons).toContain("group");
  });
});

describe("information copy", () => {
  it("has the same shape in every language", () => {
    const shape = (value: unknown): unknown =>
      Array.isArray(value) ? value.map(shape) : value && typeof value === "object"
        ? Object.fromEntries(Object.entries(value).map(([k, v]) => [k, shape(v)]))
        : typeof value;
    for (const locale of ["ar", "ru", "it"] as const) expect(shape(infoCopy[locale])).toEqual(shape(infoCopy.en));
  });

  it("states the approved cancellation policy in the terms", () => {
    const section = infoCopy.en.terms.sections.find((s) => s.heading.startsWith("Cancellation"))!;
    expect(section.body.join(" ")).toMatch(/48 hours.*full refund.*24 and 48 hours.*50%.*Less than 24 hours.*no refund/);
  });

  it("does not claim tracking cookies", () => {
    for (const locale of ["en", "ar", "ru", "it"] as const) {
      expect(infoCopy[locale].privacy.sections.length).toBe(infoCopy.en.privacy.sections.length);
    }
    expect(infoCopy.en.privacy.sections.find((s) => s.heading === "Cookies")!.body[0]).toMatch(/do not use advertising or tracking cookies/);
  });
});

describe("trip finder edge cases", () => {
  it("never lets a per-unit tour without options pass a budget", () => {
    const broken = toCatalogTour(tour({ slug: "broken", priceBasis: "PER_UNIT", priceOptions: [] }));
    expect(pricePerGuest(broken)).toBe(Number.POSITIVE_INFINITY);
    expect(suggestTours([broken], answersFromQuery({ budget: "100" }))).toEqual([]);
  });
});
