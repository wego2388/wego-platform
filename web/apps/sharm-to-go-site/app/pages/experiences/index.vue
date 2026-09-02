<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { directionFor, type SharmLocale, siteCopy } from "../../content/locales";
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
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <header class="mx-auto flex max-w-6xl items-center justify-between">
      <NuxtLink to="/" class="font-semibold text-sharm-sea">← Sharm To Go</NuxtLink>
      <button type="button" class="rounded-full border border-sharm-sea/20 bg-white px-4 py-2 text-sm font-semibold" @click="toggleLocale">
        {{ copy.languageName }}
      </button>
    </header>

    <section class="mx-auto mt-10 max-w-6xl">
      <span class="inline-flex rounded-full bg-sharm-lagoon px-4 py-2 text-xs font-bold tracking-[0.13em] text-sharm-sea uppercase">
        {{ copy.preview }}
      </span>
      <h1 class="mt-6 text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.catalog.heading }}</h1>

      <div class="mt-8 flex flex-wrap gap-2" role="tablist" :aria-label="copy.browse.allCategories">
        <button
          type="button"
          class="rounded-full border px-4 py-2 text-sm font-semibold"
          :class="selectedCategoryId === '' ? 'border-sharm-sea bg-sharm-sea text-white' : 'border-sharm-border bg-white text-sharm-sea'"
          @click="selectCategory('')"
        >
          {{ copy.browse.allCategories }}
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="rounded-full border px-4 py-2 text-sm font-semibold"
          :class="selectedCategoryId === category.id ? 'border-sharm-sea bg-sharm-sea text-white' : 'border-sharm-border bg-white text-sharm-sea'"
          @click="selectCategory(category.id)"
        >
          {{ category.name[locale] }}
        </button>
      </div>

      <p v-if="state === 'loading'" class="mt-10 text-sharm-muted">{{ copy.browse.loading }}</p>

      <div v-else-if="state === 'error'" role="alert" class="mt-10 rounded-2xl border border-red-200 bg-red-50 p-6 text-red-700">
        {{ copy.browse.loadError }}
      </div>

      <div v-else-if="services.length === 0" class="mt-10 rounded-[2rem] border border-black/5 bg-white p-8 text-center shadow-sm sm:p-12">
        <h2 class="text-xl font-semibold">{{ copy.browse.empty.heading }}</h2>
        <p class="mx-auto mt-3 max-w-xl leading-7 text-sharm-muted">{{ copy.browse.empty.body }}</p>
      </div>

      <div v-else class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <article v-for="service in services" :key="service.id" class="flex flex-col rounded-2xl border border-black/5 bg-white p-6 shadow-sm">
          <p v-if="categoryName(service.categoryId)" class="text-xs font-bold tracking-[0.1em] text-sharm-sea uppercase">
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
            class="mt-4 inline-flex justify-center rounded-full border border-sharm-sea bg-white px-5 py-2.5 text-sm font-semibold text-sharm-sea"
          >
            {{ copy.browse.viewDetails }}
          </NuxtLink>
        </article>
      </div>

      <div class="mt-10 flex flex-wrap gap-3">
        <NuxtLink to="/booking-preview" class="inline-flex rounded-full bg-sharm-sea px-6 py-3 font-semibold text-white">
          {{ copy.catalog.previewBooking }}
        </NuxtLink>
        <NuxtLink to="/design-system" class="inline-flex rounded-full border border-sharm-border bg-white px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.catalog.viewSystem }}
        </NuxtLink>
        <NuxtLink to="/" class="inline-flex rounded-full border border-sharm-border bg-white px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.catalog.back }}
        </NuxtLink>
      </div>
    </section>
  </main>
</template>
