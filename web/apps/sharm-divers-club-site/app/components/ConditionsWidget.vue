<script setup lang="ts">
import { computed } from "vue";
import { useConditions } from "../composables/useConditions";
import type { SdcLocale } from "../content/locales";

const props = defineProps<{
  locale: SdcLocale;
  heading: string;
  loadingLabel: string;
  unavailableLabel: string;
  airLabel: string;
  seaLabel: string;
  windLabel: string;
  waveLabel: string;
}>();

const { status, data } = useConditions();

const weatherLabels: Record<number, Record<SdcLocale, string>> = {
  0: { en: "Clear sky", ar: "سماء صافية" },
  1: { en: "Mostly clear", ar: "صافية غالبًا" },
  2: { en: "Partly cloudy", ar: "غائم جزئيًا" },
  3: { en: "Overcast", ar: "غائم" },
  45: { en: "Fog", ar: "ضباب" },
  48: { en: "Fog", ar: "ضباب" },
  51: { en: "Light drizzle", ar: "رذاذ خفيف" },
  53: { en: "Drizzle", ar: "رذاذ" },
  55: { en: "Heavy drizzle", ar: "رذاذ كثيف" },
  61: { en: "Light rain", ar: "مطر خفيف" },
  63: { en: "Rain", ar: "مطر" },
  65: { en: "Heavy rain", ar: "مطر غزير" },
  80: { en: "Rain showers", ar: "زخات مطر" },
  81: { en: "Rain showers", ar: "زخات مطر" },
  82: { en: "Heavy rain showers", ar: "زخات مطر غزيرة" },
  95: { en: "Thunderstorm", ar: "عاصفة رعدية" },
};

const weatherLabel = computed(() => {
  const code = data.value?.air?.weatherCode;
  if (code === undefined) return null;
  return weatherLabels[code]?.[props.locale] ?? null;
});
</script>

<template>
  <div class="rounded-[1.5rem] border border-sdc-border bg-sdc-surface p-6 shadow-sm">
    <h2 class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ heading }}</h2>

    <p v-if="status === 'loading'" class="mt-4 text-sm text-sdc-muted">{{ loadingLabel }}</p>
    <p v-else-if="status === 'error' || (!data?.air && !data?.sea)" class="mt-4 text-sm text-sdc-muted">{{ unavailableLabel }}</p>
    <div v-else class="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
      <div v-if="data?.air">
        <p class="text-[0.7rem] font-semibold text-sdc-muted uppercase">{{ airLabel }}</p>
        <p class="money mt-1 text-xl font-semibold text-sdc-ink">{{ Math.round(data.air.tempC) }}°C</p>
        <p v-if="weatherLabel" class="mt-0.5 text-xs text-sdc-muted">{{ weatherLabel }}</p>
      </div>
      <div v-if="data?.sea">
        <p class="text-[0.7rem] font-semibold text-sdc-muted uppercase">{{ seaLabel }}</p>
        <p class="money mt-1 text-xl font-semibold text-sdc-ink">{{ Math.round(data.sea.tempC) }}°C</p>
      </div>
      <div v-if="data?.air">
        <p class="text-[0.7rem] font-semibold text-sdc-muted uppercase">{{ windLabel }}</p>
        <p class="money mt-1 text-xl font-semibold text-sdc-ink">{{ Math.round(data.air.windKph) }} km/h</p>
      </div>
      <div v-if="data?.sea">
        <p class="text-[0.7rem] font-semibold text-sdc-muted uppercase">{{ waveLabel }}</p>
        <p class="money mt-1 text-xl font-semibold text-sdc-ink">{{ data.sea.waveHeightM.toFixed(1) }} m</p>
      </div>
    </div>
  </div>
</template>
