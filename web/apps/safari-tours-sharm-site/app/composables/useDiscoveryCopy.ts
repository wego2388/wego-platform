import { computed } from "vue";
import { discoveryCopy } from "../content/discovery";
import { useSiteLocale } from "./useSiteLocale";

/** Interface copy for the current locale. */
export function useDiscoveryCopy() {
  const locale = useSiteLocale();
  return computed(() => discoveryCopy[locale.value]);
}
