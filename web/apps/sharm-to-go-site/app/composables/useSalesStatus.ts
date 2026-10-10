import { onBeforeUnmount, onMounted } from "vue";

export const salesCopy = {
  en: {
    paused: "New experience requests are temporarily paused. You can still browse and track your existing request.",
    unknown: "We could not verify whether new requests are available. Please try again.",
    refresh: "Check availability again",
    contact: "Ask our team on WhatsApp",
  },
  ar: {
    paused: "استقبال طلبات تجارب جديدة متوقف مؤقتًا. تقدر تتصفح وتتابع طلبك الحالي.",
    unknown: "تعذر التأكد من إمكانية استقبال طلبات جديدة. حاول مرة أخرى.",
    refresh: "تحقق من إمكانية الطلب",
    contact: "تواصل مع فريقنا على واتساب",
  },
};

/** Nuxt state is scoped to each SSR request and shared by this visitor's components. */
export function useSalesStatus(poll = false) {
  const requestsOpen = useState<boolean | null>("stg-sales-open", () => null);
  const loading = useState<boolean>("stg-sales-loading", () => false);
  const generation = useState<number>("stg-sales-generation", () => 0);
  let interval: ReturnType<typeof setInterval> | undefined;

  // A rejected request is newer evidence than any availability read that
  // was already in flight. Invalidate those reads before showing the pause.
  function markPaused() {
    ++generation.value;
    requestsOpen.value = false;
    loading.value = false;
  }

  async function refresh() {
    const own = ++generation.value;
    loading.value = true;
    try {
      const response = await fetch("/api/sales-status", { cache: "no-store" });
      if (!response.ok) throw new Error("sales_status_unavailable");
      const body: unknown = await response.json();
      if (!body || typeof body !== "object" || !("requestsOpen" in body) || typeof body.requestsOpen !== "boolean") {
        throw new Error("invalid_sales_status");
      }
      if (own === generation.value) requestsOpen.value = body.requestsOpen;
    } catch {
      if (own === generation.value) requestsOpen.value = null;
    } finally {
      if (own === generation.value) loading.value = false;
    }
  }

  function onFocus() { void refresh(); }
  onMounted(() => {
    if (!loading.value) void refresh();
    if (poll) {
      interval = setInterval(onFocus, 30_000);
      window.addEventListener("focus", onFocus);
    }
  });
  onBeforeUnmount(() => {
    if (interval) clearInterval(interval);
    if (poll) window.removeEventListener("focus", onFocus);
  });
  return { requestsOpen, loading, refresh, markPaused };
}
