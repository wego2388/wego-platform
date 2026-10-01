import { watch } from "vue";
import type { AnalyticsEvent } from "../composables/useAnalytics";
import { useConsent } from "../composables/useConsent";

type Params = Record<string, string | number | undefined>;
interface Fbq {
  (...args: unknown[]): void;
  push?: Fbq;
  queue?: unknown[];
  loaded?: boolean;
  version?: string;
  callMethod?: (...args: unknown[]) => void;
}
type Win = Window & { dataLayer?: unknown[]; gtag?: (...args: unknown[]) => void; fbq?: Fbq };

/** Meta standard events for our GA4 event names. */
const META_EVENT: Partial<Record<AnalyticsEvent, string>> = {
  view_item: "ViewContent",
  begin_checkout: "InitiateCheckout",
  purchase: "Purchase",
  whatsapp_click: "Contact",
  search: "Search",
};

function loadScript(src: string) {
  const script = document.createElement("script");
  script.async = true;
  script.src = src;
  document.head.appendChild(script);
}

/**
 * Loads GA4 / Meta Pixel only after consent, and only those that are
 * configured. Page views are sent on every client navigation.
 */
export default defineNuxtPlugin((nuxtApp) => {
  const config = useRuntimeConfig().public;
  const consent = useConsent();
  const router = useRouter();
  const w = window as Win;
  let ready = false;

  function start() {
    if (ready || !consent.granted.value) return;
    ready = true;
    if (config.ga4Id) {
      w.dataLayer = w.dataLayer ?? [];
      w.gtag = function gtag() {
        // eslint-disable-next-line prefer-rest-params
        w.dataLayer!.push(arguments);
      };
      w.gtag("js", new Date());
      // Page views are sent by hand on navigation; turn off GA's own SPA
      // "history change" page views in the GA admin to avoid duplicates.
      w.gtag("config", config.ga4Id, { send_page_view: false });
      loadScript(`https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(String(config.ga4Id))}`);
    }
    if (config.metaPixelId) {
      // Standard Meta stub: queue calls until fbevents.js takes over (callMethod).
      const queue: unknown[] = [];
      const fbq: Fbq = Object.assign(
        (...args: unknown[]) => {
          if (fbq.callMethod) fbq.callMethod(...args);
          else queue.push(args);
        },
        { queue, loaded: true, version: "2.0" },
      );
      fbq.push = fbq;
      w.fbq = fbq;
      loadScript("https://connect.facebook.net/en_US/fbevents.js");
      // No automatic advanced matching or auto-collected button/form data:
      // the checkout has names, phones and emails that must never reach Meta.
      w.fbq("set", "autoConfig", false, String(config.metaPixelId));
      w.fbq("init", String(config.metaPixelId));
    }
    void router.isReady().then(pageView);
  }

  /** Path only — query strings and fragments never leave the site. */
  function location() {
    const path = router.currentRoute.value.path;
    return { page_path: path, page_location: window.location.origin + path, page_referrer: "" };
  }

  function pageView() {
    if (!ready) return;
    w.gtag?.("event", "page_view", location());
    w.fbq?.("track", "PageView");
  }

  function track(event: AnalyticsEvent, params: Params) {
    if (!ready) return;
    w.gtag?.("event", event, { ...params, ...location() });
    const metaEvent = META_EVENT[event];
    if (metaEvent) {
      const { item_id: itemId, value, currency } = params;
      w.fbq?.("track", metaEvent, { content_ids: itemId ? [itemId] : undefined, content_type: "product", value, currency });
    }
  }

  watch(consent.granted, (granted) => granted && start(), { immediate: true });
  router.afterEach((to, from) => {
    if (to.path !== from.path) pageView();
  });

  nuxtApp.provide("stsTrack", track);
});
