import { onMounted, ref } from "vue";

export interface Conditions {
  fetchedAt: string;
  air: { tempC: number; windKph: number; weatherCode: number } | null;
  sea: { tempC: number; waveHeightM: number } | null;
}

type ConditionsStatus = "loading" | "ready" | "error";

/**
 * Fetches live conditions client-side after mount, never during SSR — a
 * flaky third-party API must never slow down or fail a server-rendered
 * page. Falls back to an honest error state rather than a fabricated value.
 */
export function useConditions() {
  const status = ref<ConditionsStatus>("loading");
  const data = ref<Conditions | null>(null);

  onMounted(async () => {
    try {
      const response = await fetch("/api/conditions");
      if (!response.ok) throw new Error(`conditions request failed: ${response.status}`);
      data.value = (await response.json()) as Conditions;
      status.value = "ready";
    } catch {
      status.value = "error";
    }
  });

  return { status, data };
}
