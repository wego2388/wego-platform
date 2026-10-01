<script setup lang="ts">
import { onMounted, ref } from "vue";
import SiteSubHeader from "../../components/SiteSubHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { getTravelRequestByReference, type TravelRequestPublicResponse } from "../../composables/useTravelRequests";

const route = useRoute();
const reference = String(route.params.reference);

const { locale, copy, direction, toggleLocale } = useSiteLocale();

useHead(() => ({
  title: locale.value === "ar" ? "تابع طلبك · Sharm To Go" : "Track your request · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

const result = ref<TravelRequestPublicResponse | null>(null);
const state = ref<"loading" | "loaded" | "not-found" | "error">("loading");

async function load() {
  state.value = "loading";
  try {
    const found = await getTravelRequestByReference(reference);
    if (found === null) {
      state.value = "not-found";
      return;
    }
    result.value = found;
    state.value = "loaded";
  } catch {
    state.value = "error";
  }
}

onMounted(load);

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(locale.value === "ar" ? "ar-EG" : "en-GB", { year: "numeric", month: "short", day: "numeric" });
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <div class="mx-auto max-w-xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
    </div>

    <section class="mx-auto mt-10 max-w-xl">
      <h1 class="font-display text-2xl font-semibold tracking-tight">{{ copy.track.heading }}</h1>

      <p v-if="state === 'loading'" class="mt-4 text-sharm-muted">{{ copy.track.searching }}</p>

      <div v-else-if="state === 'error'" role="alert" class="mt-6 rounded-2xl border border-sharm-danger/30 bg-sharm-danger-soft p-6 text-sharm-danger">
        {{ copy.browse.loadError }}
      </div>

      <div v-else-if="state === 'not-found'" class="mt-6 rounded-[2rem] border border-black/5 bg-sharm-surface p-8 text-center shadow-sm">
        <p class="text-sharm-muted">{{ copy.track.notFound }}</p>
      </div>

      <article v-else-if="result" class="mt-6 rounded-[2rem] border border-black/5 bg-sharm-surface p-8 shadow-sm">
        <p class="reference text-xs font-bold tracking-[0.12em] text-sharm-sea uppercase">{{ reference }}</p>
        <h2 class="mt-2 text-xl font-semibold">{{ result.serviceName[locale] }} — {{ result.optionLabel[locale] }}</h2>

        <dl class="mt-6 grid gap-3 text-sm">
          <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.track.statusLabel }}</dt><dd class="font-semibold">{{ result.status }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewDate }}</dt><dd class="font-semibold">{{ formatDate(result.requestedDate) }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewParty }}</dt><dd class="font-semibold">{{ result.adults + result.children }}</dd></div>
          <div v-if="result.hotelOrPickup" class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewPickup }}</dt><dd class="font-semibold">{{ result.hotelOrPickup }}</dd></div>
          <div class="flex justify-between gap-3">
            <dt class="text-sharm-muted">{{ copy.request.reviewPrice }}</dt>
            <dd class="font-semibold text-sharm-sea">{{ result.priceCurrency }} {{ result.priceAmount }}</dd>
          </div>
        </dl>

        <p class="mt-6 text-sm leading-6 text-sharm-muted">{{ result.cancellationPolicy[locale] }}</p>
      </article>

      <form v-if="state !== 'loading'" class="mt-8" @submit.prevent="$router.push(`/track/${reference}`)">
        <label for="reference-input" class="block text-sm font-semibold text-sharm-muted">{{ copy.track.inputLabel }}</label>
        <div class="mt-2 flex gap-2">
          <input
            id="reference-input"
            :value="reference"
            type="text"
            dir="ltr"
            :placeholder="copy.track.inputPlaceholder"
            class="w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal"
            @change="(event) => $router.push(`/track/${(event.target as HTMLInputElement).value.trim()}`)"
          >
        </div>
      </form>
    </section>
    <SiteFooter :locale="locale" />
  </main>
</template>
