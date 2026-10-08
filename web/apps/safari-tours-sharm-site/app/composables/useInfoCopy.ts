import { computed } from "vue";
import { infoForSales } from "../content/salesAwareCopy";
import { useSiteLocale } from "./useSiteLocale";
import { useSalesStatus } from "./useSalesStatus";

export function useInfoCopy() {
  const locale = useSiteLocale();
  const { data: sales } = useSalesStatus();
  return computed(() => infoForSales(locale.value, sales.value));
}
