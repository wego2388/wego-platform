import { computed } from "vue";
import type { TimeSlot, TourCategory } from "@wego/api-contract";
import type { CatalogTour } from "./useCatalog";
import { CATEGORY_ORDER } from "./useCatalog";
import { buildSearchIndex, searchIndex } from "../utils/tourSearch";
import { durationBucket, durationHours, type DurationBucket } from "../utils/tourDuration";
import { siteCopy } from "../content/locales";

export const MAX_QUERY_LENGTH = 80;
export const DURATION_BUCKETS: DurationBucket[] = ["short", "half", "full", "multi"];
export const TIME_SLOTS: TimeSlot[] = ["SUNRISE", "MORNING", "AFTERNOON", "SUNSET"];
export const PRICE_CAPS = [25, 50, 100] as const;
export const SORTS = ["recommended", "priceAsc", "priceDesc", "duration"] as const;
const FILTER_KEYS = ["q", "cat", "dur", "time", "max", "sort"];
export type TourSort = (typeof SORTS)[number];

export interface TourFilters {
  q: string;
  categories: TourCategory[];
  durations: DurationBucket[];
  slots: TimeSlot[];
  maxPrice: number | null;
  sort: TourSort;
}

function list(value: unknown): string[] {
  const raw = Array.isArray(value) ? value.join(",") : typeof value === "string" ? value : "";
  return raw.split(",").map((part) => part.trim()).filter(Boolean);
}

/** Reads filters from a URL query; unknown values are ignored, never trusted. */
export function filtersFromQuery(query: Record<string, unknown>): TourFilters {
  const q = typeof query.q === "string" ? query.q.slice(0, MAX_QUERY_LENGTH) : "";
  const categories = list(query.cat)
    .map((c) => c.toUpperCase())
    .filter((c): c is TourCategory => (CATEGORY_ORDER as string[]).includes(c));
  const durations = list(query.dur).filter((d): d is DurationBucket => (DURATION_BUCKETS as string[]).includes(d));
  const slots = list(query.time)
    .map((s) => s.toUpperCase())
    .filter((s): s is TimeSlot => (TIME_SLOTS as string[]).includes(s));
  const max = Number(typeof query.max === "string" ? query.max : NaN);
  const sort = (SORTS as readonly string[]).includes(String(query.sort)) ? (query.sort as TourSort) : "recommended";
  return {
    q,
    categories: [...new Set(categories)],
    durations: [...new Set(durations)],
    slots: [...new Set(slots)],
    maxPrice: (PRICE_CAPS as readonly number[]).includes(max) ? max : null,
    sort,
  };
}

/** The canonical query for a filter set: defaults are left out so URLs stay short. */
export function filtersToQuery(filters: TourFilters): Record<string, string> {
  const query: Record<string, string> = {};
  if (filters.q.trim()) query.q = filters.q.trim().slice(0, MAX_QUERY_LENGTH);
  if (filters.categories.length) query.cat = filters.categories.map((c) => c.toLowerCase()).join(",");
  if (filters.durations.length) query.dur = filters.durations.join(",");
  if (filters.slots.length) query.time = filters.slots.map((s) => s.toLowerCase()).join(",");
  if (filters.maxPrice !== null) query.max = String(filters.maxPrice);
  if (filters.sort !== "recommended") query.sort = filters.sort;
  return query;
}

export function activeFilterCount(filters: TourFilters): number {
  return filters.categories.length + filters.durations.length + filters.slots.length + (filters.maxPrice !== null ? 1 : 0);
}

/**
 * Applies filters to the catalogue. Search looks at the tour's text in the
 * current language, its English name and the category names in all four
 * languages, so a visitor can type in whatever language comes to mind.
 */
export function applyTourFilters(catalog: CatalogTour[], filters: TourFilters): CatalogTour[] {
  const index = buildSearchIndex(catalog, (entry) => [
    entry.name,
    entry.summary,
    entry.tour.nameEn,
    entry.tour.slug.replace(/-/g, " "),
    ...Object.values(siteCopy).map((copy) => copy.categories[entry.tour.category].name),
  ]);
  let result = searchIndex(index, filters.q);
  if (filters.categories.length) result = result.filter((e) => filters.categories.includes(e.tour.category));
  if (filters.durations.length) {
    result = result.filter((e) => {
      const bucket = durationBucket(e.tour.durationText);
      return bucket !== null && filters.durations.includes(bucket);
    });
  }
  if (filters.slots.length) result = result.filter((e) => e.tour.availableTimeSlots.some((s) => filters.slots.includes(s)));
  if (filters.maxPrice !== null) {
    const cap = filters.maxPrice;
    result = result.filter((e) => e.tour.tourType !== "REQUEST_ONLY" && Number(e.tour.priceAdult.amount) <= cap);
  }
  // Request-only tours have no public price: they go last in both price orders.
  const priced = (e: CatalogTour) => e.tour.tourType !== "REQUEST_ONLY";
  const price = (e: CatalogTour) => Number(e.tour.priceAdult.amount);
  const sorted = [...result];
  if (filters.sort === "priceAsc" || filters.sort === "priceDesc") {
    const direction = filters.sort === "priceAsc" ? 1 : -1;
    sorted.sort((a, b) => Number(priced(b)) - Number(priced(a)) || direction * (price(a) - price(b)));
  }
  else if (filters.sort === "duration") {
    sorted.sort((a, b) => (durationHours(a.tour.durationText) ?? Infinity) - (durationHours(b.tour.durationText) ?? Infinity));
  }
  return sorted;
}

/** Filters bound to the current URL: reading parses the query, writing replaces it. */
export function useTourFilters() {
  const route = useRoute();
  const router = useRouter();
  const filters = computed(() => filtersFromQuery(route.query));
  function update(patch: Partial<TourFilters>) {
    const next = { ...filters.value, ...patch };
    // Keep unrelated parameters (utm_*, etc.); rewrite only the filter keys.
    const kept = Object.fromEntries(Object.entries(route.query).filter(([key]) => !FILTER_KEYS.includes(key)));
    void router.replace({ query: { ...kept, ...filtersToQuery(next) } });
  }
  function toggle<K extends "categories" | "durations" | "slots">(key: K, value: TourFilters[K][number]) {
    const current = filters.value[key] as string[];
    const next = current.includes(value) ? current.filter((v) => v !== value) : [...current, value];
    update({ [key]: next } as Partial<TourFilters>);
  }
  function clear() {
    update({ categories: [], durations: [], slots: [], maxPrice: null, q: "" });
  }
  return { filters, update, toggle, clear };
}
