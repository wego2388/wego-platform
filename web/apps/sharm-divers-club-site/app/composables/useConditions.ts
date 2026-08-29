import { onMounted, ref } from "vue";

export interface Conditions {
  fetchedAt: string;
  air: { tempC: number; windKph: number; weatherCode: number } | null;
  sea: { tempC: number; waveHeightM: number } | null;
}

type ConditionsStatus = "loading" | "ready" | "error";

const FETCH_TIMEOUT_MS = 8000;

/**
 * Fetches live conditions client-side after mount, never during SSR — a
 * flaky third-party API must never slow down or fail a server-rendered
 * page. Falls back to an honest error state rather than a fabricated value.
 * A bounded timeout matters here specifically: a stalled (not merely
 * failed) connection would otherwise leave the widget on "loading" forever
 * instead of ever reaching that honest error state.
 */
export function useConditions() {
  const status = ref<ConditionsStatus>("loading");
  const data = ref<Conditions | null>(null);

  onMounted(async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
    try {
      const response = await fetch("/api/conditions", { signal: controller.signal });
      if (!response.ok) throw new Error(`conditions request failed: ${response.status}`);
      data.value = (await response.json()) as Conditions;
      status.value = "ready";
    } catch {
      status.value = "error";
    } finally {
      clearTimeout(timeout);
    }
  });

  return { status, data };
}
