<script setup lang="ts">
import { computed } from "vue";
import type { TourCategory } from "@wego/api-contract";
import { CATEGORY_ORDER, useCatalog } from "../../composables/useCatalog";
import { useCategoryCovers } from "../../composables/useCategoryCovers";
import { useDiscoveryCopy } from "../../composables/useDiscoveryCopy";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { categoryMeta, siteCopy, whatsappUrl } from "../../content/locales";
import { CATEGORY_VISUAL } from "../../utils/categoryVisual";
import { categoryArtwork } from "../../content/brandArtwork";

const route = useRoute();
const copy = useDiscoveryCopy();
const locale = useSiteLocale();

definePageMeta({
  // Unknown category slugs are a real 404, on the server and on client navigation.
  validate: (to) => Object.values(categoryMeta).some((meta) => meta.slug === String(to.params.slug)),
});

const category = computed<TourCategory | undefined>(() =>
  CATEGORY_ORDER.find((c) => categoryMeta[c].slug === String(route.params.slug)),
);

const { data: catalog, status, refresh, countsByCategory } = useCatalog();
const { covers } = useCategoryCovers();
const tours = computed(() => (catalog.value ?? []).filter((entry) => entry.tour.category === category.value));
const text = computed(() => siteCopy[locale.value].categories[category.value!]);
const visual = computed(() => CATEGORY_VISUAL[category.value!]);
const others = computed(() => CATEGORY_ORDER.filter((c) => c !== category.value));

useSeoMeta({
  title: () => `${text.value.name} — Safari Tours Sharm`,
  description: () => text.value.description,
  ogTitle: () => text.value.name,
  ogDescription: () => text.value.description,
});
</script>

<template>
  <main id="main-content" tabindex="-1">
    <BrandPageBanner :kind="categoryArtwork(category!) ?? 'hero'" :style="{ '--tile': `var(${visual.colorVar})` }">
      <div class="mx-auto max-w-7xl">
        <NuxtLinkLocale to="/tours" class="inline-flex items-center gap-1 text-sm font-semibold text-white/75 hover:text-white">
          <Icon name="lucide:arrow-left" class="size-4 rtl:-scale-x-100" aria-hidden="true" />{{ copy.category.all }}
        </NuxtLinkLocale>
        <div class="mt-5 flex items-center gap-4">
          <span class="grid size-14 place-items-center rounded-2xl bg-[var(--tile)] text-white" aria-hidden="true">
            <Icon :name="visual.icon" class="size-7" />
          </span>
          <h1 class="font-display text-3xl font-semibold sm:text-4xl">{{ text.name }}</h1>
        </div>
        <p class="mt-4 max-w-2xl text-white/80">{{ text.description }}</p>
      </div>
    </BrandPageBanner>

    <section class="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-10" :aria-label="text.name">
      <p class="text-sm font-semibold text-sts-muted">{{ copy.tours.results(tours.length) }}</p>

      <ul v-if="status === 'pending' && !catalog?.length" class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
        <li v-for="i in 3" :key="i"><UiSkeleton class="aspect-[4/5] w-full" /></li>
      </ul>
      <div v-else-if="status === 'error'" class="mt-6">
        <UiEmptyState icon="lucide:cloud-off" :title="copy.errors.load">
          <UiButton variant="secondary" icon="lucide:refresh-cw" @click="refresh()">{{ copy.errors.retry }}</UiButton>
        </UiEmptyState>
      </div>
      <div v-else-if="tours.length === 0" class="mt-6">
        <UiEmptyState :icon="visual.icon" :title="copy.tours.emptyTitle" :body="copy.tours.emptyBody">
          <UiButton :href="whatsappUrl" icon="lucide:message-circle">{{ copy.tours.askWhatsapp }}</UiButton>
        </UiEmptyState>
      </div>
      <ul v-else class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <li v-for="(entry, index) in tours" :key="entry.tour.id">
          <TourCard :entry="entry" :priority="index < 3" heading-level="h2" />
        </li>
      </ul>
    </section>

    <section class="relative isolate overflow-hidden bg-sts-sand-soft px-4 py-14 sm:px-6 lg:px-10" aria-labelledby="other-categories">
      <BrandSectionBackdrop />
      <div class="relative z-10 mx-auto max-w-7xl">
        <h2 id="other-categories" class="font-display text-2xl font-semibold">{{ copy.category.back }}</h2>
        <ul class="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <li v-for="other in others" :key="other"><CategoryTile :category="other" :count="countsByCategory[other]" :cover="covers[other]" /></li>
        </ul>
      </div>
    </section>
  </main>
</template>
