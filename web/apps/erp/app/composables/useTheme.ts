import { onMounted, onUnmounted, ref } from "vue";

// WEGO-014 Phase 2: the token/mechanism half of theming. The visible
// toggle control lands in Phase 4 with the navigation shell — this file
// is deliberately usable (and tested) before any page calls it, so the
// shell only has to wire a control to what's already correct.

export type ThemePreference = "system" | "light" | "dark";

const STORAGE_KEY = "wego-erp-theme";

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
  // "system" has no stored value at all — an absent key and an explicit
  // "system" entry must behave identically, so there is only one way to
  // represent "no override", not two that could drift apart.
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
    // No attribute at all, not data-theme="light" — tokens.css's dark
    // block is keyed on the attribute's *presence*, and an unstamped
    // root is what every other Wego app (which never calls this
    // composable) already renders as, by construction.
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
