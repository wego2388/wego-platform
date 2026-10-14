<script setup lang="ts">
import { computed } from "vue";
import { useCatalog } from "../composables/useCatalog";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { galleryCopy } from "../content/gallery";
import { categoryMeta, siteCopy } from "../content/locales";
import { gallerySections } from "../utils/gallery";
import { illustrativePhotoLabel, isIllustrativeTourPhoto } from "../utils/tourPhotoDisclosure";

const locale = useSiteLocale();
const discovery = useDiscoveryCopy();
const copy = computed(() => galleryCopy[locale.value]);
const { data: catalog, status, refresh } = useCatalog();
const sections = computed(() => gallerySections(catalog.value ?? [], locale.value));

useSeoMeta({
  title: () => `${copy.value.title} — Safari Tours Sharm`,
  description: () => copy.value.intro,
  ogTitle: () => copy.value.title,
  ogDescription: () => copy.value.intro,
});
</script>

<template>
  <main id="main-content" tabindex="-1">
    <BrandPageBanner>
      <div class="mx-auto max-w-7xl">
        <p class="text-sm font-semibold tracking-wide text-sts-sand uppercase">Safari Tours Sharm</p>
        <h1 class="mt-3 max-w-3xl font-display text-4xl font-semibold sm:text-5xl">{{ copy.title }}</h1>
        <p class="mt-5 max-w-2xl leading-8 text-white/85">{{ copy.intro }}</p>
      </div>
    </BrandPageBanner>

    <div class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-10">
      <p class="max-w-3xl rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-sand-soft p-5 leading-7 text-sts-muted">{{ copy.note }}</p>

      <div v-if="status === 'pending' && !catalog?.length" class="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-4" aria-hidden="true">
        <UiSkeleton v-for="i in 8" :key="i" class="aspect-[4/5] w-full" />
      </div>
      <UiEmptyState v-else-if="status === 'error'" icon="lucide:cloud-off" :title="copy.empty" class="mt-10">
        <UiButton variant="secondary" icon="lucide:refresh-cw" @click="refresh()">{{ discovery.errors.retry }}</UiButton>
      </UiEmptyState>
      <UiEmptyState v-else-if="!sections.length" icon="lucide:image" :title="copy.empty" class="mt-10">
        <UiButton to="/tours" variant="secondary">{{ copy.all }}</UiButton>
      </UiEmptyState>
      <template v-else>
        <section v-for="section in sections" :key="section.category" class="mt-14" :aria-labelledby="`gallery-${section.category.toLowerCase()}`">
          <div class="flex flex-wrap items-end justify-between gap-4">
            <h2 :id="`gallery-${section.category.toLowerCase()}`" class="font-display text-2xl font-semibold sm:text-3xl">{{ siteCopy[locale].categories[section.category].name }}</h2>
            <NuxtLinkLocale :to="`/category/${categoryMeta[section.category].slug}`" class="font-semibold text-sts-ocean-bright underline underline-offset-4">{{ copy.category }}</NuxtLinkLocale>
          </div>
          <ul class="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <li v-for="item in section.items" :key="item.entry.tour.id">
              <article class="group relative h-full overflow-hidden rounded-[var(--sts-radius-card)] bg-sts-ocean shadow-sts-base transition-shadow hover:shadow-sts-raised">
                <BrandTourMedia :src="item.cover.path" :alt="item.cover.alt" :width="item.cover.width" :height="item.cover.height" :category="section.category" ratio="4 / 5" sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw" class="!rounded-none" />
                <div class="pointer-events-none absolute inset-0 bg-gradient-to-t from-sts-ocean/95 via-sts-ocean/15 to-transparent" aria-hidden="true" />
                <div class="absolute inset-x-0 bottom-0 p-5 text-white">
                  <span v-if="isIllustrativeTourPhoto(item.cover.alt)" class="mb-2 inline-block rounded-full bg-sts-ocean/90 px-2 py-0.5 text-xs">{{ illustrativePhotoLabel[locale] }}</span>
                  <h3 class="text-lg font-semibold leading-snug" :lang="item.entry.textLocale ?? 'en'">
                    <NuxtLinkLocale :to="`/tour/${item.entry.tour.slug}`" class="after:absolute after:inset-0 after:content-[''] focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-sts-sunset">{{ item.entry.name }}</NuxtLinkLocale>
                  </h3>
                  <p class="mt-2 text-sm text-white/80">{{ copy.tour }}</p>
                </div>
              </article>
            </li>
          </ul>
        </section>
        <div class="mt-14 flex justify-center"><UiButton to="/tours" size="lg" icon-end="lucide:arrow-right">{{ copy.all }}</UiButton></div>
      </template>
    </div>
  </main>
</template>
