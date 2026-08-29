<script setup lang="ts">
import { computed } from "vue";
import { whatsappUrl } from "../content/locales";
import type { SdcLocale } from "../content/locales";
import { audienceLabel, categoryIcon, diveCountLabel, durationLabel, formatEur, imageForOffering } from "../content/offerings";
import type { OfferingSummary } from "../content/offerings";

const props = defineProps<{
  offering: OfferingSummary;
  locale: SdcLocale;
  pricingNoticeLabel: string;
  askLabel: string;
}>();

const label = computed(() => props.offering.name[props.locale]);
const duration = computed(() => durationLabel(props.locale, props.offering.durationMinutes));
const dives = computed(() => diveCountLabel(props.locale, props.offering.diveCount));
const audience = computed(() => audienceLabel(props.locale, props.offering.audience));
const price = computed(() => formatEur(props.offering.priceEur));
const icon = computed(() => categoryIcon(props.offering.categoryId));
const photo = computed(() => imageForOffering(props.offering, props.locale));
const askUrl = computed(() => {
  const text = props.locale === "ar"
    ? `مرحبًا، عايز أسأل عن: ${label.value} (${props.offering.code})`
    : `Hi, I'd like to ask about: ${label.value} (${props.offering.code})`;
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});
</script>

<template>
  <article class="hover-lift flex flex-col overflow-hidden rounded-[1.75rem] border border-sdc-border bg-sdc-surface shadow-sm">
    <img
      v-if="photo"
      :src="photo.url"
      :alt="photo.alt"
      width="960"
      height="640"
      loading="lazy"
      class="aspect-[3/2] w-full object-cover"
    >
    <div class="flex flex-1 flex-col p-6">
      <div class="grid size-12 place-items-center rounded-2xl bg-sdc-turquoise-soft text-sdc-deep">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path :d="icon" />
        </svg>
      </div>
      <p class="reference mt-5 text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase" dir="ltr">{{ offering.code }}</p>
      <NuxtLink :to="`/discover/${offering.code}`" class="mt-1 block text-lg font-semibold hover:text-sdc-deep-bright">{{ label }}</NuxtLink>
      <p class="mt-2 text-sm text-sdc-muted">{{ audience }}</p>
      <div v-if="duration || dives" class="mt-3 flex flex-wrap gap-2 text-xs font-semibold text-sdc-deep-bright">
        <span v-if="duration" class="rounded-full bg-sdc-canvas px-3 py-1">{{ duration }}</span>
        <span v-if="dives" class="rounded-full bg-sdc-canvas px-3 py-1">{{ dives }}</span>
      </div>
      <div class="mt-5 flex flex-1 items-end justify-between gap-3">
        <div>
          <p class="money text-2xl font-semibold text-sdc-ink">{{ price }}</p>
          <p class="mt-1 text-[0.7rem] text-sdc-muted">{{ pricingNoticeLabel }}</p>
        </div>
        <a :href="askUrl" target="_blank" rel="noopener" class="min-h-10 shrink-0 rounded-full bg-sdc-deep px-4 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 flex items-center">
          {{ askLabel }}
        </a>
      </div>
    </div>
  </article>
</template>
