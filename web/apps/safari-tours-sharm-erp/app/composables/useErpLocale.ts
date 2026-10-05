import { computed } from "vue";
import {
  ERP_LOCALE_COOKIE, erpMessage, formatErpCount, formatErpDate, formatErpMoney, formatErpInstant, formatErpSignedMoney,
  resolveErpLocale, type ErpLocale, type ErpMessageKey,
} from "../utils/erpLocale";
import type { Money } from "@wego/api-contract";

/** Cookie preference is available to SSR; request-scoped useState keeps every screen in sync. */
export function useErpLocale() {
  const remembered = useCookie<ErpLocale>(ERP_LOCALE_COOKIE, {
    default: () => "en", sameSite: "lax", maxAge: 60 * 60 * 24 * 365, path: "/",
  });
  const locale = useState<ErpLocale>("sts:erp-locale", () => resolveErpLocale(remembered.value));
  const direction = computed(() => locale.value === "ar" ? "rtl" : "ltr");

  function setLocale(next: ErpLocale) {
    locale.value = resolveErpLocale(next);
    remembered.value = locale.value;
  }

  return {
    locale, direction, setLocale,
    t: (key: ErpMessageKey, params?: Record<string, string | number>) => erpMessage(locale.value, key, params),
    count: (value: number) => formatErpCount(value, locale.value),
    money: (value: Money) => formatErpMoney(value, locale.value),
    signedMoney: (value: Money) => formatErpSignedMoney(value, locale.value),
    dateLabel: (day: string, long = false) => formatErpDate(day, locale.value, long),
    instantLabel: (instant: string) => formatErpInstant(instant, locale.value),
  };
}
