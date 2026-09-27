<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import SiteHeader from "../../components/SiteHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl, categoryMeta } from "../../content/locales";
import type { StsLocale, TourCategory } from "../../content/locales";
import { formatPrice, listToursPublic, type Tour } from "../../composables/usePublicToursApi";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const route = useRoute();

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

// Resolve category from slug
const SLUG_TO_CAT: Record<string, TourCategory> = {
  desert:    "DESERT",
  sea:       "SEA",
  cultural:  "CULTURAL",
  shows:     "SHOWS",
  transfers: "TRANSFERS",
};

const slug = computed(() => route.params.slug as string);
const category = computed(() => SLUG_TO_CAT[slug.value] as TourCategory | undefined);
const categoryName = computed(() =>
  category.value ? copy.value.categories[category.value].name : slug.value,
);
const categoryDescription = computed(() =>
  category.value ? copy.value.categories[category.value].description : "",
);
const meta = computed(() => (category.value ? categoryMeta[category.value] : null));

// ── Tours from API ─────────────────────────────────────────────────────────
const tours = ref<Tour[]>([]);
const loadState = ref<"loading" | "loaded" | "error">("loading");

async function loadTours() {
  loadState.value = "loading";
  try {
    tours.value = await listToursPublic({ category: category.value });
    loadState.value = "loaded";
  } catch {
    loadState.value = "error";
  }
}

watch(category, loadTours, { immediate: true });
onMounted(loadTours);

useHead(() => ({
  title: categoryName.value + " — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: categoryDescription.value }],
}));
</script>

<template>
  <div :dir="direction" :lang="locale" class="min-h-screen bg-sts-canvas text-sts-ink">
    <SiteHeader
      :locale="locale"
      :direction="direction"
      :nav="copy.nav"
      :whatsapp-label="copy.whatsappFab"
      :current-locales="ALL_LOCALES"
      @set-locale="(l) => (locale = l)"
    />

    <main id="main-content" tabindex="-1">
      <!-- Category hero -->
      <div class="bg-sts-ocean px-6 py-16 text-white lg:px-10">
        <div class="mx-auto max-w-7xl">
          <NuxtLink
            to="/tours"
            class="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-white/60 hover:text-white"
          >
            ← {{ copy.nav.tours }}
          </NuxtLink>
          <div class="flex items-center gap-4">
            <span
              v-if="meta"
              class="grid size-14 place-items-center rounded-2xl text-3xl"
              :class="meta.colorClass"
              aria-hidden="true"
            >
              {{ meta.icon }}
            </span>
            <h1 class="font-display text-3xl font-semibold sm:text-4xl">
              {{ categoryName }}
            </h1>
          </div>
          <p class="mt-3 max-w-xl text-white/75">{{ categoryDescription }}</p>
        </div>
      </div>

      <!-- Tours grid -->
      <section class="mx-auto max-w-7xl px-6 py-14 lg:px-10">
        <!-- Loading skeleton -->
        <div v-if="loadState === 'loading'" class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          <div v-for="i in 6" :key="i" class="h-64 animate-pulse rounded-2xl bg-sts-border" />
        </div>

        <!-- Error / empty -->
        <div
          v-else-if="loadState === 'error' || (loadState === 'loaded' && tours.length === 0)"
          class="rounded-2xl border border-dashed border-sts-border bg-sts-surface p-12 text-center"
        >
          <p class="text-4xl" aria-hidden="true">{{ meta?.icon ?? "🗺️" }}</p>
          <p class="mt-4 font-semibold text-sts-muted">
            {{ loadState === 'error' ? 'Could not load tours.' : 'No tours in this category yet.' }}
          </p>
          <a
            :href="whatsappUrl"
            target="_blank"
            rel="noopener"
            class="mt-6 inline-flex rounded-full bg-sts-coral px-7 py-3 font-semibold text-white transition-transform hover:-translate-y-0.5"
          >
            {{ copy.whatsappFab }}
          </a>
        </div>

        <!-- Tours -->
        <div v-else class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          <NuxtLink
            v-for="tour in tours"
            :key="tour.id"
            :to="`/tour/${tour.slug}`"
            class="hover-lift group flex flex-col rounded-2xl border border-sts-border bg-sts-surface shadow-sm overflow-hidden"
          >
            <div class="flex h-44 items-center justify-center bg-sts-ocean/10">
              <span class="text-5xl" aria-hidden="true">{{ meta?.icon ?? "🗺️" }}</span>
            </div>
            <div class="flex flex-1 flex-col p-5">
              <h2 class="font-semibold capitalize group-hover:text-sts-coral transition-colors">
                {{ tour.slug.replace(/-/g, " ") }}
              </h2>
              <p class="mt-1 text-xs text-sts-muted">⏱ {{ tour.durationText }}</p>
              <div class="mt-auto flex items-center justify-between pt-4">
                <span class="text-sm text-sts-muted">From</span>
                <span class="font-display text-xl font-bold text-sts-ocean">
                  {{ formatPrice(tour.priceAdult) }}
                </span>
              </div>
            </div>
          </NuxtLink>
        </div>
      </section>
    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
