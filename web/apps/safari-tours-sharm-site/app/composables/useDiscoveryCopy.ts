import { computed } from "vue";
import { discoveryForSales } from "../content/salesAwareCopy";
import { useSiteLocale } from "./useSiteLocale";
import { useSalesStatus } from "./useSalesStatus";

/** Interface copy for the current locale. */
export function useDiscoveryCopy() {
  const locale = useSiteLocale();
  const { data: sales } = useSalesStatus();
  return computed(() => discoveryForSales(locale.value, sales.value));
}
