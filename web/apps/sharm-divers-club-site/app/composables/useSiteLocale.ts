import { onMounted, watch } from "vue";
import type { SdcLocale } from "../content/locales";

const STORAGE_KEY = "sdc-locale";

export function useSiteLocale() {
  const locale = useState<SdcLocale>("sdc-locale", () => "en");

  onMounted(() => {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored === "en" || stored === "ar") locale.value = stored;
    } catch {
      // localStorage unavailable (private mode, blocked, etc.) — keep the default.
    }
  });

  watch(locale, (value) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, value);
    } catch {
      // ignore — per-viewer convenience only, not load-bearing.
    }
  });

  return locale;
}
