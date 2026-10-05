import { computed } from "vue";
import type { PublicCategoryCover, TourCategory } from "@wego/api-contract";
import { apiFetch } from "./useCatalog";
import { useSiteLocale } from "./useSiteLocale";

/** SSR metadata only. Image bytes always re-check approval at the backend. */
export function useCategoryCovers() {
  const locale = useSiteLocale();
  const result = useAsyncData(() => `category-covers-${locale.value}`, () =>
    apiFetch<PublicCategoryCover[]>(`/api/v1/tours-operator/categories/media?locale=${locale.value}`), {
    watch: [locale],
    default: () => [] as PublicCategoryCover[],
  });
  const covers = computed(() => Object.fromEntries((result.data.value ?? []).map((item) => [item.category, item])) as Partial<Record<TourCategory, PublicCategoryCover>>);
  return { ...result, covers };
}
