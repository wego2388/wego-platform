<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useCatalog } from "../composables/useCatalog";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { MAX_QUERY_LENGTH, SORTS, activeFilterCount, applyTourFilters, useTourFilters, type TourSort } from "../composables/useTourFilters";
import { whatsappUrl } from "../content/locales";
import { infoCopy } from "../content/info";
import { useSiteLocale } from "../composables/useSiteLocale";

const copy = useDiscoveryCopy();
const siteLocale = useSiteLocale();
const finderTitle = computed(() => infoCopy[siteLocale.value].finder.title);
const { data: catalog, status, refresh } = useCatalog();
const { filters, update, clear } = useTourFilters();

const results = computed(() => applyTourFilters(catalog.value ?? [], filters.value));
const activeCount = computed(() => activeFilterCount(filters.value));
const filtersOpen = ref(false);

// The search box updates the URL as the visitor types (debounced), and
// follows the URL when it changes elsewhere (back button, "clear").
const query = ref(filters.value.q);
watch(() => filters.value.q, (q) => {
  if (q !== query.value.trim()) query.value = q;
});
watchDebounced(query, (q) => {
  if (q.trim() !== filters.value.q) update({ q });
}, { debounce: 180 });

function onSort(event: Event) {
  update({ sort: (event.target as HTMLSelectElement).value as TourSort });
}

useSeoMeta({
  title: () => `${copy.value.tours.title} — Safari Tours Sharm`,
  description: () => copy.value.tours.body,
  ogTitle: () => copy.value.tours.title,
  ogDescription: () => copy.value.tours.body,
});
</script>

<template>
  <main id="main-content" tabindex="-1">
    <div class="bg-sts-ocean px-4 pt-12 pb-10 text-white sm:px-6 lg:px-10">
      <div class="mx-auto max-w-7xl">
        <h1 class="font-display text-3xl font-semibold sm:text-4xl">{{ copy.tours.title }}</h1>
        <p class="mt-3 max-w-2xl text-white/80">{{ copy.tours.body }}</p>
        <NuxtLinkLocale to="/trip-finder" class="mt-3 inline-flex items-center gap-1.5 text-sm font-semibold text-sts-sand hover:text-white">
          <Icon name="lucide:sparkles" class="size-4" aria-hidden="true" />{{ finderTitle }}
        </NuxtLinkLocale>
        <form class="relative mt-8 max-w-2xl" role="search" @submit.prevent="update({ q: query })">
          <label for="tour-search" class="sr-only">{{ copy.tours.searchLabel }}</label>
          <Icon name="lucide:search" class="pointer-events-none absolute inset-y-0 start-4 my-auto size-5 text-sts-muted" aria-hidden="true" />
          <input
            id="tour-search"
            v-model="query"
            type="search"
            enterkeyhint="search"
            autocomplete="off"
            :maxlength="MAX_QUERY_LENGTH"
            :placeholder="copy.tours.searchPlaceholder"
            class="min-h-13 w-full rounded-full border-0 bg-sts-surface ps-12 pe-5 text-base text-sts-ink shadow-sts-raised placeholder:text-sts-muted focus-visible:outline-3 focus-visible:outline-sts-sunset"
          >
        </form>
      </div>
    </div>

    <div class="mx-auto grid max-w-7xl gap-10 px-4 py-10 sm:px-6 lg:grid-cols-[17rem_1fr] lg:px-10">
      <aside class="hidden lg:block" :aria-label="copy.tours.filters">
        <div class="sticky top-24">
          <ToursFilterPanel id-prefix="side" />
          <UiButton v-if="activeCount" variant="ghost" size="sm" class="mt-6" icon="lucide:x" @click="clear">{{ copy.tours.clear }}</UiButton>
        </div>
      </aside>

      <section aria-labelledby="results-heading">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 id="results-heading" class="text-sm font-semibold text-sts-muted" aria-live="polite">
            {{ copy.tours.results(results.length) }}
          </h2>
          <div class="flex items-center gap-2">
            <UiButton variant="secondary" size="sm" icon="lucide:sliders-horizontal" class="lg:hidden" @click="filtersOpen = true">
              {{ copy.tours.filters }}<span v-if="activeCount" class="rounded-full bg-sts-ocean px-2 text-xs text-white">{{ activeCount }}</span>
            </UiButton>
            <label for="tour-sort" class="sr-only">{{ copy.tours.sort }}</label>
            <select
              id="tour-sort"
              :value="filters.sort"
              class="min-h-9 rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface px-3 text-sm font-semibold"
              @change="onSort"
            >
              <option v-for="sort in SORTS" :key="sort" :value="sort">{{ copy.tours.sorts[sort] }}</option>
            </select>
          </div>
        </div>

        <ul v-if="status === 'pending' && !catalog?.length" class="mt-6 grid gap-6 sm:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
          <li v-for="i in 6" :key="i"><UiSkeleton class="aspect-[4/5] w-full" /></li>
        </ul>

        <div v-else-if="status === 'error'" class="mt-6">
          <UiEmptyState icon="lucide:cloud-off" :title="copy.errors.load">
            <UiButton variant="secondary" icon="lucide:refresh-cw" @click="refresh()">{{ copy.errors.retry }}</UiButton>
          </UiEmptyState>
        </div>

        <div v-else-if="results.length === 0" class="mt-6">
          <UiEmptyState icon="lucide:search-x" :title="copy.tours.emptyTitle" :body="copy.tours.emptyBody">
            <div class="flex flex-wrap justify-center gap-3">
              <UiButton variant="secondary" icon="lucide:x" @click="clear">{{ copy.tours.clear }}</UiButton>
              <UiButton :href="whatsappUrl" icon="lucide:message-circle">{{ copy.tours.askWhatsapp }}</UiButton>
            </div>
          </UiEmptyState>
        </div>

        <TransitionGroup v-else tag="ul" name="sts-list" class="relative mt-6 grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          <li v-for="(entry, index) in results" :key="entry.tour.id">
            <TourCard :entry="entry" :priority="index < 3" />
          </li>
        </TransitionGroup>
      </section>
    </div>

    <UiSheet v-model:open="filtersOpen" :title="copy.tours.filters" :close-label="copy.nav.closeMenu">
      <ToursFilterPanel id-prefix="sheet" />
      <template #footer>
        <div class="flex gap-3">
          <UiButton v-if="activeCount" variant="ghost" @click="clear">{{ copy.tours.clear }}</UiButton>
          <UiButton block @click="filtersOpen = false">{{ copy.tours.show(results.length) }}</UiButton>
        </div>
      </template>
    </UiSheet>
  </main>
</template>
