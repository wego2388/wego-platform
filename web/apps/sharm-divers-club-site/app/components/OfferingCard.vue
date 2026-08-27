<script setup lang="ts">
import { computed } from "vue";
import { whatsappUrl } from "../content/locales";
import type { SdcLocale } from "../content/locales";
import { audienceLabel, diveCountLabel, durationLabel, formatEur } from "../content/offerings";
import type { OfferingSummary } from "../content/offerings";

const props = defineProps<{
  offering: OfferingSummary;
  locale: SdcLocale;
  pricingNoticeLabel: string;
  askLabel: string;
}>();

const icons: Record<string, string> = {
  "shore-diving": "M2 15c2-2 4-2 6 0s4 2 6 0 4-2 6 0 M2 10c2-2 4-2 6 0s4 2 6 0 4-2 6 0",
  "boat-diving": "M4 15h16l-2 5H6l-2-5Zm8-11v11M8 8l4-4 4 4",
  "multi-day": "M4 5h16v15H4V5Zm0 5h16M8 3v4M16 3v4",
  signature: "M12 3l2.2 6.8H21l-5.6 4.2L17.6 21 12 16.8 6.4 21l2.2-7-5.6-4.2h6.8L12 3Z",
  "world-class": "M12 3v4m0 14v-4m-9-7h4m10 0h4M6.3 6.3l2.8 2.8m5.8 5.8 2.8 2.8m0-11.4-2.8 2.8m-5.8 5.8-2.8 2.8M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z",
  "padi-courses": "M12 3 3 7l9 4 9-4-9-4Zm-6 6v6l6 3 6-3V9M6 13v4M18 13v4",
};

const label = computed(() => props.offering.name[props.locale]);
const duration = computed(() => durationLabel(props.locale, props.offering.durationMinutes));
const dives = computed(() => diveCountLabel(props.locale, props.offering.diveCount));
const audience = computed(() => audienceLabel(props.locale, props.offering.audience));
const price = computed(() => formatEur(props.offering.priceEur));
const askUrl = computed(() => {
  const text = props.locale === "ar"
    ? `مرحبًا، عايز أسأل عن: ${label.value} (${props.offering.code})`
    : `Hi, I'd like to ask about: ${label.value} (${props.offering.code})`;
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});
</script>

<template>
  <article class="hover-lift flex flex-col rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
    <div class="grid size-12 place-items-center rounded-2xl bg-sdc-turquoise-soft text-sdc-deep">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path :d="icons[offering.categoryId]" />
      </svg>
    </div>
    <p class="reference mt-5 text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase" dir="ltr">{{ offering.code }}</p>
    <h3 class="mt-1 text-lg font-semibold">{{ label }}</h3>
    <p class="mt-2 text-sm text-sdc-muted">{{ audience }}</p>
    <div v-if="duration || dives" class="mt-3 flex flex-wrap gap-2 text-xs font-semibold text-sdc-deep-bright">
      <span v-if="duration" class="rounded-full bg-sdc-canvas px-3 py-1">{{ duration }}</span>
      <span v-if="dives" class="rounded-full bg-sdc-canvas px-3 py-1">{{ dives }}</span>
    </div>
    <div class="mt-5 flex flex-1 items-end justify-between gap-3">
      <div>
        <p class="money text-2xl font-semibold text-sdc-deep">{{ price }}</p>
        <p class="mt-1 text-[0.7rem] text-sdc-muted">{{ pricingNoticeLabel }}</p>
      </div>
      <a :href="askUrl" target="_blank" rel="noopener" class="min-h-10 shrink-0 rounded-full bg-sdc-deep px-4 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 flex items-center">
        {{ askLabel }}
      </a>
    </div>
  </article>
</template>
