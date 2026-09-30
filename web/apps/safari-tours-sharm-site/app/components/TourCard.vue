<script setup lang="ts">
import { computed } from "vue";
import { formatMoney } from "@wego/api-contract";
import type { CatalogTour } from "../composables/useCatalog";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { siteCopy } from "../content/locales";
import { CATEGORY_VISUAL } from "../utils/categoryVisual";
import { formatDuration } from "../utils/tourDuration";

/**
 * One tour in a list. The picture carries a view-transition name so it
 * morphs into the tour page hero. Only approved media is ever shown; until
 * then the branded category placeholder stands in.
 */
const props = withDefaults(defineProps<{ entry: CatalogTour; priority?: boolean; headingLevel?: "h2" | "h3" }>(), {
  priority: false,
  headingLevel: "h3",
});

const copy = useDiscoveryCopy();
const locale = useSiteLocale();
const tour = computed(() => props.entry.tour);
const visual = computed(() => CATEGORY_VISUAL[tour.value.category]);
const categoryName = computed(() => siteCopy[locale.value].categories[tour.value.category].name);
const duration = computed(() => formatDuration(tour.value.durationText, locale.value));
const isTransfer = computed(() => tour.value.tourType === "TRANSFER");
const onRequest = computed(() => tour.value.tourType === "REQUEST_ONLY");
</script>

<template>
  <article class="group relative flex h-full flex-col overflow-hidden rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface shadow-sts-base transition-[transform,box-shadow] duration-[var(--sts-dur-base)] ease-[var(--sts-ease)] hover:-translate-y-1 hover:shadow-sts-raised focus-within:-translate-y-1 focus-within:shadow-sts-raised">
    <div class="overflow-hidden" :style="{ viewTransitionName: `tour-media-${tour.slug}` }">
      <BrandTourMedia
        alt=""
        :category="tour.category"
        :priority="priority"
        ratio="4 / 3"
        class="!rounded-none transition-transform duration-[var(--sts-dur-reveal)] ease-[var(--sts-ease)] group-hover:scale-[1.03]"
      />
    </div>
    <div class="flex flex-1 flex-col gap-3 p-5">
      <div class="flex flex-wrap items-center gap-2 text-xs font-semibold">
        <UiBadge :tone="visual.tone" :icon="visual.icon">{{ categoryName }}</UiBadge>
        <span class="inline-flex items-center gap-1 text-sts-muted">
          <Icon name="lucide:clock" class="size-3.5" aria-hidden="true" />{{ duration }}
        </span>
      </div>
      <component :is="headingLevel" class="text-lg font-semibold leading-snug" :lang="entry.textLocale ?? 'en'">
        <NuxtLinkLocale
          :to="`/tour/${tour.slug}`"
          class="after:absolute after:inset-0 after:content-[''] focus-visible:outline-none"
        >
          {{ entry.name }}
        </NuxtLinkLocale>
      </component>
      <p v-if="entry.summary" class="line-clamp-2 text-sm leading-6 text-sts-muted" :lang="entry.textLocale ?? 'en'">
        {{ entry.summary }}
      </p>
      <div class="mt-auto flex items-end justify-between gap-3 pt-2">
        <p v-if="onRequest" class="text-sm font-semibold text-sts-ink">{{ copy.card.onRequest }}</p>
        <p v-else class="flex flex-col">
          <span class="text-xs text-sts-muted">{{ copy.card.from }}</span>
          <span class="text-2xl font-bold tabular-nums text-sts-ocean-bright">
            {{ formatMoney(tour.priceAdult) }}
            <span v-if="!isTransfer" class="text-xs font-medium text-sts-muted">{{ copy.card.perPerson }}</span>
          </span>
        </p>
        <span class="inline-flex items-center gap-1 text-sm font-semibold text-sts-ocean-bright" aria-hidden="true">
          {{ copy.card.details }}
          <Icon name="lucide:arrow-right" class="size-4 transition-transform group-hover:translate-x-0.5 rtl:-scale-x-100 rtl:group-hover:-translate-x-0.5" />
        </span>
      </div>
    </div>
  </article>
</template>
