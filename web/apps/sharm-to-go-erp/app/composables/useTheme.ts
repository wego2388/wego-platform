import { onMounted, onUnmounted, ref } from "vue";

// Ported verbatim from web/apps/erp's own useTheme.ts (WEGO-014 Phase 2) —
// this mechanism is product-neutral, not Divers-specific. Only the storage
// key changes, matching this app's own useAuthSession.ts convention of a
// distinct per-app key.

export type ThemePreference = "system" | "light" | "dark";

const STORAGE_KEY = "wego-stg-erp-theme";

function isThemePreference(value: string | null): value is ThemePreference {
  return value === "system" || value === "light" || value === "dark";
}

export function readThemePreference(): ThemePreference {
  if (typeof window === "undefined") return "system";
  const stored = window.localStorage.getItem(STORAGE_KEY);
  return isThemePreference(stored) ? stored : "system";
}

export function writeThemePreference(preference: ThemePreference): void {
  if (typeof window === "undefined") return;
  if (preference === "system") {
    window.localStorage.removeItem(STORAGE_KEY);
  } else {
    window.localStorage.setItem(STORAGE_KEY, preference);
  }
}

function systemPrefersDark(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function effectiveTheme(preference: ThemePreference): "light" | "dark" {
  return preference === "system" ? (systemPrefersDark() ? "dark" : "light") : preference;
}

export function applyTheme(preference: ThemePreference): void {
  if (typeof document === "undefined") return;
  if (effectiveTheme(preference) === "dark") {
    document.documentElement.setAttribute("data-theme", "dark");
  } else {
    document.documentElement.removeAttribute("data-theme");
  }
}

export function useTheme() {
  const preference = ref<ThemePreference>(readThemePreference());
  let media: MediaQueryList | undefined;

  function setPreference(next: ThemePreference): void {
    preference.value = next;
    writeThemePreference(next);
    applyTheme(next);
  }

  function handleSystemChange(): void {
    if (preference.value === "system") applyTheme("system");
  }

  onMounted(() => {
    preference.value = readThemePreference();
    applyTheme(preference.value);
    media = window.matchMedia("(prefers-color-scheme: dark)");
    media.addEventListener("change", handleSystemChange);
  });

  onUnmounted(() => {
    media?.removeEventListener("change", handleSystemChange);
  });

  return { preference, setPreference };
}
