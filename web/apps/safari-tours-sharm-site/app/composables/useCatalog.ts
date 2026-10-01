import { computed, onMounted } from "vue";
import type { Tour, TourCategory } from "@wego/api-contract";
import { useSiteLocale } from "./useSiteLocale";

/** A tour as the discovery pages show it: published localized text when it exists. */
export interface CatalogTour {
  tour: Tour;
  name: string;
  summary: string | null;
  /** Locale the text is really in (requested or English fallback); null = catalog name only. */
  textLocale: string | null;
}

export const CATEGORY_ORDER: TourCategory[] = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"];

/**
 * Fetches from the backend: on the server over the internal network (so pages
 * render with real tours for visitors and crawlers), in the browser via the
 * same-origin /api proxy.
 */
export function apiFetch<T>(path: string): Promise<T> {
  if (import.meta.server) {
    const base = useRuntimeConfig().apiInternalBase as string;
    // Bounded: a slow backend must not hold the page render for long.
    return $fetch<T>(`${base}${path}`, { timeout: 8000, retry: 1, retryDelay: 300 }) as Promise<T>;
  }
  // Typed by hand: these are backend API paths, not this app's Nitro routes.
  return $fetch<T>(path) as Promise<T>;
}

// The public catalogue changes rarely; the server keeps each answer for a
// short time so busy pages do not ask the backend on every view. Pages
// themselves are not cached (they carry per-visitor theme and language).
const SERVER_CACHE_MS = 60_000;
const serverCache = new Map<string, { expires: number; value: Promise<unknown> }>();

function cachedApiFetch<T>(path: string): Promise<T> {
  if (!import.meta.server) return apiFetch<T>(path);
  const now = Date.now();
  const hit = serverCache.get(path);
  if (hit && hit.expires > now) return hit.value as Promise<T>;
  const value = apiFetch<T>(path);
  serverCache.set(path, { expires: now + SERVER_CACHE_MS, value });
  value.catch(() => {
    if (serverCache.get(path)?.value === value) serverCache.delete(path);
  });
  return value;
}

/**
 * Fills fields an older backend may not send yet (price basis/options were
 * added with per-unit pricing), so pages never crash on a missing array.
 */
export function normalizeTour(tour: Tour): Tour {
  return { ...tour, priceBasis: tour.priceBasis ?? "PER_PERSON", priceOptions: tour.priceOptions ?? [] };
}

export function toCatalogTour(raw: Tour): CatalogTour {
  const tour = normalizeTour(raw);
  return {
    tour,
    name: tour.localized?.name ?? tour.nameEn ?? tour.slug,
    summary: tour.localized?.shortDescription ?? null,
    textLocale: tour.localized?.locale ?? null,
  };
}

async function fetchAllActiveTours(locale: string): Promise<CatalogTour[]> {
  const tours: Tour[] = [];
  for (let page = 0; page < 20; page++) {
    const batch = await cachedApiFetch<Tour[]>(`/api/v1/tours-operator/tours?locale=${locale}&page=${page}&size=100`);
    tours.push(...batch);
    if (batch.length < 100) break;
  }
  return tours.map(toCatalogTour).sort((a, b) => a.tour.sortOrder - b.tour.sortOrder);
}

/** The whole active catalog in the current language, rendered on the server. */
export function useCatalog() {
  const locale = useSiteLocale();
  const result = useAsyncData(() => `catalog-${locale.value}`, () => fetchAllActiveTours(locale.value), {
    watch: [locale],
    default: () => [] as CatalogTour[],
  });
  // If the server could not reach the backend (e.g. it was still starting),
  // the browser tries once more instead of showing an empty catalogue.
  onMounted(() => {
    if (result.error.value || (result.status.value === "success" && !(result.data.value ?? []).length)) void result.refresh();
  });
  const countsByCategory = computed(() => {
    const counts = Object.fromEntries(CATEGORY_ORDER.map((c) => [c, 0])) as Record<TourCategory, number>;
    for (const item of result.data.value ?? []) counts[item.tour.category]++;
    return counts;
  });
  return { ...result, countsByCategory };
}
