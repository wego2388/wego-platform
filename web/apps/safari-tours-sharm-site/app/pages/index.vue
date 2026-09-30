<script setup lang="ts">
import { computed } from "vue";
import { CATEGORY_ORDER, useCatalog, type CatalogTour } from "../composables/useCatalog";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useSiteLocale } from "../composables/useSiteLocale";
import { siteCopy, siteEmail, siteWebsite, whatsappPhone, whatsappUrl } from "../content/locales";
import { CATEGORY_VISUAL } from "../utils/categoryVisual";

const copy = useDiscoveryCopy();
const locale = useSiteLocale();
const { data: catalog, countsByCategory } = useCatalog();

/** One tour from each experience first, then the rest in catalogue order. */
const featured = computed<CatalogTour[]>(() => {
  const tours = (catalog.value ?? []).filter((entry) => entry.tour.tourType !== "TRANSFER");
  const picks: CatalogTour[] = [];
  for (const category of CATEGORY_ORDER) {
    const first = tours.find((entry) => entry.tour.category === category);
    if (first) picks.push(first);
  }
  for (const entry of tours) {
    if (picks.length >= 6) break;
    if (!picks.includes(entry)) picks.push(entry);
  }
  return picks.slice(0, 6);
});

const faqItems = computed(() => copy.value.faq.items.map((item, index) => ({ value: `faq-${index}`, ...item })));

useSeoMeta({
  title: () => `${copy.value.hero.title} — Safari Tours Sharm`,
  description: () => copy.value.hero.body,
  ogTitle: () => copy.value.hero.title,
  ogDescription: () => copy.value.hero.body,
});
useHead(() => ({
  script: [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "TravelAgency",
        name: "Safari Tours Sharm",
        url: siteWebsite,
        email: siteEmail,
        telephone: whatsappPhone,
        areaServed: "Sharm El Sheikh, Egypt",
        currenciesAccepted: "EUR",
      }),
    },
  ],
}));
</script>

<template>
  <main id="main-content" tabindex="-1">
    <section class="sts-hero relative isolate overflow-hidden" aria-labelledby="hero-heading">
      <div class="relative z-10 mx-auto max-w-7xl px-4 pt-14 pb-20 sm:px-6 lg:px-10 lg:pt-24 lg:pb-28">
        <div class="max-w-3xl">
          <p class="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-1.5 text-xs font-bold tracking-[0.12em] text-white/90 uppercase ring-1 ring-white/20 backdrop-blur">
            <Icon name="lucide:map-pin" class="size-3.5" aria-hidden="true" />{{ copy.hero.eyebrow }}
          </p>
          <h1 id="hero-heading" class="mt-6 font-display text-4xl leading-tight font-semibold tracking-tight text-balance text-white sm:text-5xl lg:text-6xl">
            {{ copy.hero.title }}
          </h1>
          <p class="mt-5 max-w-2xl text-lg leading-8 text-white/85">{{ copy.hero.body }}</p>
          <div class="mt-8 flex flex-wrap gap-3">
            <UiButton to="/tours" size="lg" icon-end="lucide:arrow-right">{{ copy.hero.primary }}</UiButton>
            <UiButton :href="whatsappUrl" variant="inverse" size="lg" icon="lucide:message-circle">{{ copy.hero.secondary }}</UiButton>
          </div>
        </div>

        <nav class="mt-12 max-w-4xl" :aria-label="copy.explore.label">
          <p class="text-sm font-semibold text-white/80">{{ copy.explore.label }}</p>
          <ul class="mt-3 flex flex-wrap gap-2">
            <li v-for="category in CATEGORY_ORDER" :key="category">
              <NuxtLinkLocale
                :to="{ path: '/tours', query: { cat: category.toLowerCase() } }"
                class="inline-flex min-h-11 items-center gap-2 rounded-full bg-white/12 px-4 text-sm font-semibold text-white ring-1 ring-white/25 backdrop-blur transition-colors hover:bg-white/20"
              >
                <Icon :name="CATEGORY_VISUAL[category].icon" class="size-4" aria-hidden="true" />
                {{ siteCopy[locale].categories[category].name }}
              </NuxtLinkLocale>
            </li>
          </ul>
        </nav>
      </div>
    </section>

    <section class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-10 lg:py-20" aria-labelledby="categories-heading">
      <div class="max-w-2xl">
        <h2 id="categories-heading" class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.categories.heading }}</h2>
        <p class="mt-3 leading-7 text-sts-muted">{{ copy.categories.body }}</p>
      </div>
      <ul class="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <li v-for="category in CATEGORY_ORDER" :key="category">
          <CategoryTile :category="category" :count="countsByCategory[category]" />
        </li>
      </ul>
    </section>

    <section v-if="featured.length" class="bg-sts-sand-soft py-16 lg:py-20" aria-labelledby="featured-heading">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-10">
        <div class="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 id="featured-heading" class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.featured.heading }}</h2>
            <p class="mt-2 text-sts-muted">{{ copy.featured.body }}</p>
          </div>
          <UiButton to="/tours" variant="secondary" icon-end="lucide:arrow-right">{{ copy.featured.all }}</UiButton>
        </div>
        <ul class="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <li v-for="(entry, index) in featured" :key="entry.tour.id">
            <TourCard :entry="entry" :priority="index < 3" />
          </li>
        </ul>
      </div>
    </section>

    <section class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-10 lg:py-20" aria-labelledby="why-heading">
      <h2 id="why-heading" class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.why.heading }}</h2>
      <ul class="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <li v-for="item in copy.why.items" :key="item.title" class="rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6">
          <span class="grid size-11 place-items-center rounded-xl bg-sts-ocean text-sts-sand" aria-hidden="true">
            <Icon :name="item.icon" class="size-5" />
          </span>
          <h3 class="mt-5 font-semibold">{{ item.title }}</h3>
          <p class="mt-2 text-sm leading-6 text-sts-muted">{{ item.body }}</p>
        </li>
      </ul>
    </section>

    <section class="mx-auto max-w-3xl px-4 pb-16 sm:px-6 lg:pb-20" aria-labelledby="faq-heading">
      <h2 id="faq-heading" class="font-display text-3xl font-semibold tracking-tight">{{ copy.faq.heading }}</h2>
      <UiAccordion class="mt-6" :items="faqItems" />
    </section>

    <section class="bg-sts-ocean px-4 py-16 text-white sm:px-6 lg:px-10" aria-labelledby="cta-heading">
      <div class="mx-auto max-w-3xl text-center">
        <h2 id="cta-heading" class="font-display text-3xl font-semibold">{{ copy.cta.title }}</h2>
        <p class="mt-3 leading-7 text-white/80">{{ copy.cta.body }}</p>
        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <UiButton to="/tours" size="lg">{{ copy.cta.primary }}</UiButton>
          <UiButton :href="whatsappUrl" variant="inverse" size="lg" icon="lucide:message-circle">{{ copy.cta.secondary }}</UiButton>
        </div>
      </div>
    </section>
  </main>
</template>
