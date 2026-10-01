import type { PublicTourContent, Tour } from "@wego/api-contract";
import { apiFetch, normalizeTour } from "./useCatalog";
import { useSiteLocale } from "./useSiteLocale";

export type { PublicTourContent };

export interface TourPageData {
  tour: Tour;
  content: PublicTourContent | null;
}

/**
 * A tour and its published content in the current language, rendered on the
 * server. An unknown or inactive tour is a real 404. Content that fails to
 * load does not take the page down: the page falls back to catalogue facts.
 */
export function useTourPage(slug: () => string) {
  const locale = useSiteLocale();
  return useAsyncData(
    () => `tour-${slug()}-${locale.value}`,
    async (): Promise<TourPageData> => {
      const query = `slug=${encodeURIComponent(slug())}&locale=${locale.value}`;
      const tourRequest = apiFetch<Tour>(`/api/v1/tours-operator/tours/by-slug?${query}`).catch((error: { statusCode?: number }) => {
        if (error?.statusCode === 404 || error?.statusCode === 400) {
          throw createError({ statusCode: 404, statusMessage: "Tour not found", fatal: true });
        }
        throw error;
      });
      const contentRequest = apiFetch<PublicTourContent>(`/api/v1/tours-operator/tours/by-slug/content?${query}`).catch(() => null);
      const [tour, content] = await Promise.all([tourRequest, contentRequest]);
      return { tour: normalizeTour(tour), content };
    },
    { watch: [locale] },
  );
}
