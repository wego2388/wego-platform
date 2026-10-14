<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { curatedCover } from "../../utils/curatedMedia";
import { categoryMeta, siteCopy } from "../../content/locales";
const locale = useSiteLocale();
const moments = computed(() => ([
  { category: "DESERT", slug: "bedouin-dinner-camel-ride" },
  { category: "SEA", slug: "tiran-island-boat-trip" },
  { category: "CULTURAL", slug: "mount-sinai-st-catherine" },
] as const).map(item => ({ ...item, photo: curatedCover(item.slug, locale.value)!, name: siteCopy[locale.value].categories[item.category].name })));
</script>

<template>
  <ul class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
    <li v-for="(moment, index) in moments" :key="moment.slug" :class="{ 'sm:col-span-2 lg:col-span-1': index === 2 }">
      <NuxtLinkLocale :to="`/category/${categoryMeta[moment.category].slug}`" class="group relative isolate flex min-h-64 items-end overflow-hidden rounded-[var(--sts-radius-card)] bg-sts-ocean p-6 text-white focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-sts-ocean">
        <BrandSafeTourImage :src="moment.photo.path" :alt="moment.photo.alt" :width="moment.photo.width" :height="moment.photo.height" sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw" class="absolute inset-0 -z-20 h-full w-full object-cover transition-transform duration-500 motion-safe:group-hover:scale-105" />
        <div class="absolute inset-0 -z-10 bg-gradient-to-t from-sts-ocean/95 via-sts-ocean/30 to-transparent" aria-hidden="true" />
        <span class="flex w-full items-center justify-between gap-3 text-xl font-semibold">{{ moment.name }}<Icon name="lucide:arrow-up-right" class="size-5 rtl:-scale-x-100" aria-hidden="true" /></span>
      </NuxtLinkLocale>
    </li>
  </ul>
</template>
