<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { directionFor, type SharmLocale, siteCopy } from "../../content/locales";
import { getPublicService, type PublicService } from "../../composables/usePublicCatalog";

const route = useRoute();
const serviceId = String(route.params.id);

const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

const service = ref<PublicService | null>(null);
const state = ref<"loading" | "loaded" | "not-found" | "error">("loading");

useHead(() => ({
  title:
    state.value === "loaded" && service.value
      ? `${service.value.name[locale.value]} · Sharm To Go`
      : locale.value === "ar"
        ? "التجربة · Sharm To Go"
        : "Experience · Sharm To Go",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

function priceBasisLabel(basis: string): string {
  if (basis === "PER_GROUP") return copy.value.browse.perGroup;
  if (basis === "PER_VEHICLE") return copy.value.browse.perVehicle;
  return copy.value.browse.perPerson;
}

onMounted(async () => {
  try {
    const result = await getPublicService(serviceId);
    if (result === null) {
      state.value = "not-found";
      return;
    }
    service.value = result;
    state.value = "loaded";
  } catch {
    state.value = "error";
  }
});
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <header class="mx-auto flex max-w-4xl items-center justify-between">
      <NuxtLink to="/" class="font-semibold text-sharm-sea">← Sharm To Go</NuxtLink>
      <button type="button" class="rounded-full border border-sharm-sea/20 bg-white px-4 py-2 text-sm font-semibold" @click="toggleLocale">
        {{ copy.languageName }}
      </button>
    </header>

    <section class="mx-auto mt-10 max-w-4xl">
      <p v-if="state === 'loading'" class="text-sharm-muted">{{ copy.browse.loading }}</p>

      <div v-else-if="state === 'error'" role="alert" class="rounded-2xl border border-red-200 bg-red-50 p-6 text-red-700">
        {{ copy.browse.loadError }}
      </div>

      <div v-else-if="state === 'not-found'" class="rounded-[2rem] border border-black/5 bg-white p-8 text-center shadow-sm sm:p-12">
        <h1 class="text-2xl font-semibold">{{ copy.detail.notFoundHeading }}</h1>
        <p class="mx-auto mt-3 max-w-xl leading-7 text-sharm-muted">{{ copy.detail.notFoundBody }}</p>
        <NuxtLink to="/experiences" class="mt-6 inline-flex rounded-full bg-sharm-sea px-6 py-3 font-semibold text-white">
          {{ copy.detail.back }}
        </NuxtLink>
      </div>

      <article v-else-if="service" class="rounded-[2rem] border border-black/5 bg-white p-8 shadow-sm sm:p-12">
        <h1 class="text-3xl font-semibold tracking-tight">{{ service.name[locale] }}</h1>
        <p v-if="service.operatedBy" class="mt-2 text-sm text-sharm-muted">{{ copy.browse.operatedBy }}: {{ service.operatedBy }}</p>
        <p class="mt-5 leading-8 text-sharm-muted">{{ service.description[locale] }}</p>

        <section class="mt-8">
          <h2 class="text-lg font-semibold">{{ copy.detail.optionsHeading }}</h2>
          <ul class="mt-3 space-y-3">
            <li v-for="(option, index) in service.options" :key="index" class="flex flex-wrap items-baseline justify-between gap-2 rounded-xl border border-sharm-border p-4">
              <span class="font-medium">{{ option.label[locale] }}</span>
              <span class="text-sharm-sea">
                {{ option.priceCurrency }} {{ option.priceAmount }}
                <span class="text-xs text-sharm-muted">{{ priceBasisLabel(option.priceBasis) }}</span>
              </span>
            </li>
          </ul>
        </section>

        <section class="mt-8">
          <h2 class="text-lg font-semibold">{{ copy.detail.cancellationHeading }}</h2>
          <p class="mt-2 leading-7 text-sharm-muted">{{ service.cancellationPolicy[locale] }}</p>
        </section>

        <section v-if="service.pickupInfo" class="mt-8">
          <h2 class="text-lg font-semibold">{{ copy.detail.pickupHeading }}</h2>
          <p class="mt-2 leading-7 text-sharm-muted">{{ service.pickupInfo[locale] }}</p>
        </section>

        <section v-if="service.inclusions" class="mt-8">
          <h2 class="text-lg font-semibold">{{ copy.detail.inclusionsHeading }}</h2>
          <p class="mt-2 leading-7 text-sharm-muted">{{ service.inclusions[locale] }}</p>
        </section>

        <section v-if="service.exclusions" class="mt-8">
          <h2 class="text-lg font-semibold">{{ copy.detail.exclusionsHeading }}</h2>
          <p class="mt-2 leading-7 text-sharm-muted">{{ service.exclusions[locale] }}</p>
        </section>

        <p v-if="service.media.length > 0" class="mt-8 text-sm text-sharm-muted">{{ copy.browse.photoCount(service.media.length) }}</p>

        <section class="mt-10 rounded-2xl bg-sharm-lagoon p-6">
          <h2 class="text-lg font-semibold">{{ copy.detail.contactHeading }}</h2>
          <p class="mt-2 leading-7 text-sharm-muted">{{ copy.detail.contactBody }}</p>
        </section>

        <NuxtLink to="/experiences" class="mt-8 inline-flex rounded-full border border-sharm-border bg-white px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.detail.back }}
        </NuxtLink>
      </article>
    </section>
  </main>
</template>
