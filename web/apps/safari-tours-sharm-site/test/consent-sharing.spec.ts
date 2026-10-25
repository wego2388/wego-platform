import { effectScope, ref, watch } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useConsent } from "../app/composables/useConsent";

afterEach(() => vi.unstubAllGlobals());

describe("consent shared across app callers but not SSR requests", () => {
  it("UI reset reaches plugin synchronously even if useCookie would create isolated refs", () => {
    const app = {};
    const cookie = vi.fn(() => ref<"granted" | "denied" | null>("granted"));
    vi.stubGlobal("useNuxtApp", () => app);
    vi.stubGlobal("useRuntimeConfig", () => ({ public: { ga4Id: "G-TEST" } }));
    vi.stubGlobal("useRequestURL", () => new URL("https://safaritourssharm.com"));
    vi.stubGlobal("useCookie", cookie);
    const scope = effectScope();
    let granted = true;
    scope.run(() => {
      const pluginConsent = useConsent();
      watch(pluginConsent.granted, value => { granted = value; }, { flush: "sync" });
      const footerConsent = useConsent();
      expect(footerConsent.choice).toBe(pluginConsent.choice);
      expect(cookie).toHaveBeenCalledTimes(1);
      footerConsent.reset();
      expect(granted).toBe(false);
      footerConsent.set("granted");
      expect(granted).toBe(true);
      footerConsent.set("denied");
      expect(granted).toBe(false);
    });
    scope.stop();
  });

  it("never shares visitor choice across Nuxt app/request instances", () => {
    let app = {};
    vi.stubGlobal("useNuxtApp", () => app);
    vi.stubGlobal("useRuntimeConfig", () => ({ public: { ga4Id: "G-TEST" } }));
    vi.stubGlobal("useRequestURL", () => new URL("https://safaritourssharm.com"));
    vi.stubGlobal("useCookie", () => ref(null));
    const first = useConsent();
    first.set("granted");
    app = {};
    const second = useConsent();
    expect(second.choice).not.toBe(first.choice);
    expect(second.choice.value).toBeNull();
    expect(second.granted.value).toBe(false);
    expect(first.granted.value).toBe(true);
  });
});
