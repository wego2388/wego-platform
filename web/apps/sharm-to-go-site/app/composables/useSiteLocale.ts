// The locale-ref/copy/direction/toggle pattern every page used to
// duplicate inline. Starts "en" on every page (server and first client
// render must match, or Vue warns on hydration) and, once mounted in the
// browser, adopts a previously-chosen locale from localStorage — a
// per-viewer convenience, not shared state between pages or visitors, so a
// storage failure (private browsing, blocked site data) just means the
// page falls back to "en" exactly like it always did before this existed.
import { computed, onMounted, ref } from "vue";
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
  const locale = ref<SharmLocale>("en");
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
