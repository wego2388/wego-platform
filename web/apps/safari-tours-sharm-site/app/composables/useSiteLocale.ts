import { onMounted, watch } from "vue";
import type { StsLocale } from "../content/locales";
import { rtlLocales } from "../content/locales";

const STORAGE_KEY = "sts-locale";
const SUPPORTED: StsLocale[] = ["en", "ru", "ar", "it"];

function isSupported(v: string): v is StsLocale {
  return (SUPPORTED as string[]).includes(v);
}

/**
 * Persists locale in localStorage and syncs <html dir> and <html lang>
 * on every change. Defaults to "en" on SSR; reads from localStorage on mount.
 */
export function useSiteLocale() {
  const locale = useState<StsLocale>("sts-locale", () => "en");

  onMounted(() => {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored && isSupported(stored)) {
        locale.value = stored;
        return;
      }
      // respect browser language on first visit
      const lang = navigator.language.slice(0, 2);
      if (isSupported(lang)) locale.value = lang;
    } catch {
      // localStorage blocked (private mode) — keep default
    }
  });

  watch(locale, (val) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, val);
    } catch {
      // ignore
    }
    // sync <html> attributes
    if (typeof document !== "undefined") {
      document.documentElement.lang = val;
      document.documentElement.dir = rtlLocales.includes(val) ? "rtl" : "ltr";
    }
  });

  return locale;
}
