import { computed } from "vue";

export type ConsentChoice = "granted" | "denied";

/**
 * Analytics consent. Nothing that tracks is loaded unless an analytics ID is
 * configured for the deployment AND the visitor chose "granted". The choice
 * is kept for six months in a first-party cookie.
 */
export function useConsent() {
  const config = useRuntimeConfig().public;
  const secure = import.meta.client ? window.location.protocol === "https:" : useRequestURL().protocol === "https:";
  const choice = useCookie<ConsentChoice | null>("sts_consent", { maxAge: 60 * 60 * 24 * 182, sameSite: "lax", secure, default: () => null });
  const configured = computed(() => Boolean(config.ga4Id || config.metaPixelId));
  return {
    configured,
    choice,
    granted: computed(() => configured.value && choice.value === "granted"),
    needsAnswer: computed(() => configured.value && choice.value !== "granted" && choice.value !== "denied"),
    set(value: ConsentChoice) {
      choice.value = value;
      if (value === "denied") clearTrackingCookies();
    },
    reset() {
      choice.value = null;
      clearTrackingCookies();
    },
  };
}

/** Remove first-party identifiers Google Analytics and Meta Pixel may have set. */
export function clearTrackingCookies() {
  if (!import.meta.client) return;
  const names = document.cookie
    .split(";")
    .map((part) => part.split("=")[0]!.trim())
    .filter((name) => name.startsWith("_ga") || name === "_fbp" || name === "_fbc" || name === "_gid");
  const host = window.location.hostname;
  const domains = [host, `.${host}`, `.${host.split(".").slice(-2).join(".")}`];
  for (const name of names) {
    document.cookie = `${name}=; Max-Age=0; path=/`;
    for (const domain of domains) document.cookie = `${name}=; Max-Age=0; path=/; domain=${domain}`;
  }
}
