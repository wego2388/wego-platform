// Locale is shared by this visitor's page and global notices through Nuxt's
// request-scoped state. First SSR/hydration render starts EN; storage is read
// after mount, without sharing one visitor's language with another.
import { computed, onMounted } from "vue";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";

const STORAGE_KEY = "sharm-to-go-locale";

function readStoredLocale(): SharmLocale | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === "en" || value === "ar" ? value : null;
  } catch {
    return null;
  }
}

function writeStoredLocale(value: SharmLocale) {
  try {
    localStorage.setItem(STORAGE_KEY, value);
  } catch {
    // Per-viewer convenience only — nothing on the page depends on this succeeding.
  }
}

export function useSiteLocale() {
  const locale = useState<SharmLocale>("stg-site-locale", () => "en");
  const copy = computed(() => siteCopy[locale.value]);
  const direction = computed(() => directionFor(locale.value));

  onMounted(() => {
    const stored = readStoredLocale();
    if (stored) locale.value = stored;
  });

  function toggleLocale() {
    locale.value = locale.value === "en" ? "ar" : "en";
    writeStoredLocale(locale.value);
  }

  return { locale, copy, direction, toggleLocale };
}
