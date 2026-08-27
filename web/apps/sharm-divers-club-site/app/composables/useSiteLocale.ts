import type { SdcLocale } from "../content/locales";

export function useSiteLocale() {
  return useState<SdcLocale>("sdc-locale", () => "en");
}
