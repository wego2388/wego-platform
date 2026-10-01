/**
 * Analytics events (owner hub list). A no-op unless consent was granted and
 * an ID is configured; never pass personal data (names, phones, emails).
 */
export type AnalyticsEvent = "view_item" | "begin_checkout" | "purchase" | "whatsapp_click" | "search" | "language_switch" | "booking_lookup";

export function useAnalytics() {
  const nuxtApp = useNuxtApp();
  return {
    track(event: AnalyticsEvent, params: Record<string, string | number | undefined> = {}) {
      const track = nuxtApp.$stsTrack as ((e: AnalyticsEvent, p: Record<string, string | number | undefined>) => void) | undefined;
      track?.(event, params);
    },
  };
}
