<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import SiteSubHeader from "../../components/SiteSubHeader.vue";
import { accentForIndex } from "../../content/categoryAccents";
import { directionFor, type SharmLocale, siteCopy } from "../../content/locales";
import { vReveal } from "../../composables/useScrollReveal";
import {
  listPublicCategories,
  listPublicServices,
  type PublicCategory,
  type PublicService,
  startingPrice,
} from "../../composables/usePublicCatalog";

const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "التجارب · Sharm To Go" : "Experiences · Sharm To Go",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const categories = ref<PublicCategory[]>([]);
const services = ref<PublicService[]>([]);
const selectedCategoryId = ref<string>("");
const state = ref<"loading" | "loaded" | "error">("loading");

function categoryName(categoryId: string): string {
  const category = categories.value.find((item) => item.id === categoryId);
  if (!category) return "";
  return category.name[locale.value];
}

function categoryIndex(categoryId: string): number {
  return categories.value.findIndex((item) => item.id === categoryId);
}

async function loadServices() {
  try {
    services.value = await listPublicServices(selectedCategoryId.value || undefined);
    state.value = "loaded";
  } catch {
    state.value = "error";
  }
}

async function selectCategory(categoryId: string) {
  selectedCategoryId.value = categoryId;
  state.value = "loading";
  await loadServices();
}

function priceBasisLabel(basis: string): string {
  if (basis === "PER_GROUP") return copy.value.browse.perGroup;
  if (basis === "PER_VEHICLE") return copy.value.browse.perVehicle;
  return copy.value.browse.perPerson;
}

onMounted(async () => {
  try {
    categories.value = await listPublicCategories();
  } catch {
    categories.value = [];
  }
  await loadServices();
});
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink">
    <div class="sharm-hero border-b border-black/5 px-6 py-8 lg:px-10">
      <div class="mx-auto max-w-6xl">
        <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
      </div>

      <section class="mx-auto mt-10 max-w-6xl">
        <span class="inline-flex rounded-full bg-sharm-surface/80 px-4 py-2 text-xs font-bold tracking-[0.13em] text-sharm-sea uppercase shadow-sm">
          {{ copy.preview }}
        </span>
        <h1 class="font-display mt-6 text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.catalog.heading }}</h1>

        <div class="mt-8 flex flex-wrap gap-2" role="tablist" :aria-label="copy.browse.allCategories">
          <button
            type="button"
            class="rounded-full border px-4 py-2 text-sm font-semibold transition-transform hover:-translate-y-0.5"
            :class="selectedCategoryId === '' ? 'border-sharm-sea bg-sharm-sea text-white' : 'border-sharm-border bg-sharm-surface text-sharm-sea'"
            @click="selectCategory('')"
          >
            {{ copy.browse.allCategories }}
          </button>
          <button
            v-for="(category, index) in categories"
            :key="category.id"
            type="button"
            class="rounded-full border px-4 py-2 text-sm font-semibold transition-transform hover:-translate-y-0.5"
            :class="selectedCategoryId === category.id ? `${accentForIndex(index).solid} border-transparent text-white` : `${accentForIndex(index).ring} bg-sharm-surface ${accentForIndex(index).text}`"
            @click="selectCategory(category.id)"
          >
            {{ category.name[locale] }}
          </button>
        </div>
      </section>
    </div>

    <section class="mx-auto max-w-6xl px-6 py-10 lg:px-10">
      <p v-if="state === 'loading'" class="text-sharm-muted">{{ copy.browse.loading }}</p>

      <div v-else-if="state === 'error'" role="alert" class="rounded-2xl border border-sharm-danger/30 bg-sharm-danger-soft p-6 text-sharm-danger">
        {{ copy.browse.loadError }}
      </div>

      <div v-else-if="services.length === 0" class="rounded-[2rem] border border-black/5 bg-sharm-surface p-8 text-center shadow-sm sm:p-12">
        <h2 class="text-xl font-semibold">{{ copy.browse.empty.heading }}</h2>
        <p class="mx-auto mt-3 max-w-xl leading-7 text-sharm-muted">{{ copy.browse.empty.body }}</p>
      </div>

      <div v-else class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <article
          v-for="service in services"
          :key="service.id"
          v-reveal
          class="sharm-reveal sharm-card-lift flex flex-col rounded-2xl border border-black/5 bg-sharm-surface p-6 shadow-sm"
        >
          <p v-if="categoryName(service.categoryId)" class="text-xs font-bold tracking-[0.1em] uppercase" :class="accentForIndex(categoryIndex(service.categoryId)).text">
            {{ categoryName(service.categoryId) }}
          </p>
          <h3 class="mt-2 text-lg font-semibold">{{ service.name[locale] }}</h3>
          <p class="mt-2 line-clamp-3 flex-1 text-sm leading-6 text-sharm-muted">{{ service.description[locale] }}</p>
          <p v-if="service.operatedBy" class="mt-3 text-xs text-sharm-muted">{{ copy.browse.operatedBy }}: {{ service.operatedBy }}</p>
          <p v-if="service.media.length > 0" class="mt-1 text-xs text-sharm-muted">{{ copy.browse.photoCount(service.media.length) }}</p>
          <p v-if="startingPrice(service)" class="mt-4 text-base font-semibold text-sharm-sea">
            {{ copy.browse.fromPrice }} {{ startingPrice(service)?.priceCurrency }} {{ startingPrice(service)?.priceAmount }}
            <span class="text-xs font-normal text-sharm-muted">{{ priceBasisLabel(startingPrice(service)?.priceBasis ?? "PER_PERSON") }}</span>
          </p>
          <NuxtLink
            :to="`/experiences/${service.id}`"
            class="mt-4 inline-flex justify-center rounded-full border border-sharm-sea bg-sharm-surface px-5 py-2.5 text-sm font-semibold text-sharm-sea"
          >
            {{ copy.browse.viewDetails }}
          </NuxtLink>
        </article>
      </div>

      <div class="mt-10 flex flex-wrap gap-3">
        <NuxtLink to="/booking-preview" class="inline-flex rounded-full bg-sharm-sea px-6 py-3 font-semibold text-white">
          {{ copy.catalog.previewBooking }}
        </NuxtLink>
        <NuxtLink to="/design-system" class="inline-flex rounded-full border border-sharm-border bg-sharm-surface px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.catalog.viewSystem }}
        </NuxtLink>
        <NuxtLink to="/" class="inline-flex rounded-full border border-sharm-border bg-sharm-surface px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.catalog.back }}
        </NuxtLink>
      </div>
    </section>
  </main>
</template>
