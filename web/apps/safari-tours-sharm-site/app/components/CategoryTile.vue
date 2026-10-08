<script setup lang="ts">
import { computed } from "vue";
import type { PublicCategoryCover, TourCategory } from "@wego/api-contract";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { categoryMeta, siteCopy } from "../content/locales";
import { CATEGORY_VISUAL } from "../utils/categoryVisual";
import { categoryArtwork } from "../content/brandArtwork";

const props = withDefaults(defineProps<{ category: TourCategory; count?: number | null; cover?: PublicCategoryCover }>(), { count: null, cover: undefined });
const copy = useDiscoveryCopy();
const locale = useSiteLocale();
const visual = computed(() => CATEGORY_VISUAL[props.category]);
const text = computed(() => siteCopy[locale.value].categories[props.category]);
const artwork = computed(() => categoryArtwork(props.category));
</script>

<template>
  <NuxtLinkLocale
    :to="`/category/${categoryMeta[category].slug}`"
    class="group relative flex h-full flex-col overflow-hidden rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6 shadow-sts-base transition-[transform,box-shadow] duration-[var(--sts-dur-base)] hover:-translate-y-1 hover:shadow-sts-raised"
    :style="{ '--tile': `var(${visual.colorVar})` }"
  >
    <BrandTourMedia v-if="cover" :src="cover.path" :alt="cover.alt" :width="cover.width" :height="cover.height" :category="category" ratio="16 / 9" class="mb-5" />
    <BrandArtwork v-else-if="artwork" :kind="artwork" />
    <span class="pointer-events-none absolute -end-10 -top-10 size-36 rounded-full bg-[var(--tile)] opacity-10 transition-transform duration-[var(--sts-dur-reveal)] group-hover:scale-125" aria-hidden="true" />
    <span class="grid size-12 place-items-center rounded-2xl bg-[var(--tile)] text-white" aria-hidden="true">
      <Icon :name="visual.icon" class="size-6" />
    </span>
    <span class="mt-5 text-lg font-semibold">{{ text.name }}</span>
    <span class="mt-2 flex-1 text-sm leading-6 text-sts-muted">{{ text.description }}</span>
    <span class="mt-4 flex items-center justify-between text-sm font-semibold">
      <span v-if="count" class="text-sts-muted">{{ copy.categories.count(count) }}</span>
      <Icon name="lucide:arrow-right" class="ms-auto size-4 text-sts-ocean-bright transition-transform group-hover:translate-x-0.5 rtl:-scale-x-100" aria-hidden="true" />
    </span>
  </NuxtLinkLocale>
</template>
