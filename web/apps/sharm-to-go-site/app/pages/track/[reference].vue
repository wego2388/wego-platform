<script setup lang="ts">
import { onMounted, ref } from "vue";
import SiteSubHeader from "../../components/SiteSubHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { approximateUsdPrice, priceBasisLabel } from "../../composables/usePublicCatalog";
import { getTravelRequestByReference, travelRequestStatusText, type TravelRequestPublicResponse } from "../../composables/useTravelRequests";

const route = useRoute();
const reference = String(route.params.reference);

const { locale, copy, direction, toggleLocale } = useSiteLocale();

useHead(() => ({
  title: locale.value === "ar" ? "تابع طلبك · Sharm To Go" : "Track your request · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  // The reference in this URL is a bearer secret (see the proxy routes'
  // own comments) — never let a search engine index or cache this page.
  meta: [{ name: "robots", content: "noindex,nofollow" }],
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

// requestedDate is a calendar date (e.g. "2026-10-15"), not an instant — the
// native Date constructor parses that as UTC midnight, and formatting it in
// the viewer's own local timezone can then show the day before for anyone
// west of UTC. Force UTC on the display too, so it always reads back the
// same calendar date the string names, regardless of the viewer's timezone.
function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(locale.value === "ar" ? "ar-EG" : "en-GB", {
    year: "numeric",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}
</script>

<template>
  <main id="main-content" :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
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
          <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.track.statusLabel }}</dt><dd class="font-semibold">{{ travelRequestStatusText(result.status, locale) }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewDate }}</dt><dd class="font-semibold">{{ formatDate(result.requestedDate) }}</dd></div>
          <div class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewParty }}</dt><dd class="font-semibold">{{ result.adults + result.children }}</dd></div>
          <div v-if="result.hotelOrPickup" class="flex justify-between gap-3"><dt class="text-sharm-muted">{{ copy.request.reviewPickup }}</dt><dd class="font-semibold">{{ result.hotelOrPickup }}</dd></div>
          <div class="flex justify-between gap-3">
            <dt class="text-sharm-muted">{{ copy.request.reviewPrice }}</dt>
            <dd class="font-semibold text-sharm-sea">
              {{ result.priceCurrency }} {{ result.priceAmount }}
              <span class="text-xs font-normal text-sharm-muted">
                {{ copy.browse.approxUsd(approximateUsdPrice(result.priceAmount)) }}
                {{ priceBasisLabel(result.priceBasis, copy.browse) }}
              </span>
            </dd>
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
