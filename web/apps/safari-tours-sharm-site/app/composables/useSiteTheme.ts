import { computed, type WritableComputedRef } from "vue";

export type SiteTheme = "system" | "light" | "dark";
const THEMES: SiteTheme[] = ["system", "light", "dark"];

/**
 * Visitor's colour theme, stored in a cookie so the server renders
 * <html data-theme> directly — no flash of the wrong theme on load.
 * "system" leaves the attribute off and follows prefers-color-scheme.
 */
export function useSiteTheme(): WritableComputedRef<SiteTheme> {
  const cookie = useCookie<string | null>("sts_theme", { maxAge: 60 * 60 * 24 * 365, sameSite: "lax" });
  return computed({
    get: () => (THEMES.includes(cookie.value as SiteTheme) ? (cookie.value as SiteTheme) : "system"),
    set: (next: SiteTheme) => {
      cookie.value = next;
    },
  });
}
