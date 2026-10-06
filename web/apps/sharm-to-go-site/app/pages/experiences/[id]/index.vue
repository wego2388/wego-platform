<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import MockPhoto from "../../../components/MockPhoto.vue";
import SiteSubHeader from "../../../components/SiteSubHeader.vue";
import SiteFooter from "../../../components/SiteFooter.vue";
import { contact, emailLink, whatsappLink } from "../../../content/contact";
import { toneForIndex } from "../../../content/categoryAccents";
import { useSiteLocale } from "../../../composables/useSiteLocale";
import {
  approximateUsdPrice,
  getPublicService,
  listPublicCategories,
  priceBasisLabel,
  type PublicCategory,
  type PublicService,
} from "../../../composables/usePublicCatalog";

const route = useRoute();
const serviceId = String(route.params.id);

// Forwarded straight through from the catalog page (itself forwarded from
// the homepage search box, if the visitor used it) — see experiences/index.vue.
const forwardedQuery = computed<Record<string, string>>(() => {
  const query: Record<string, string> = {};
  if (typeof route.query.date === "string") query.date = route.query.date;
  if (typeof route.query.adults === "string") query.adults = route.query.adults;
  if (typeof route.query.children === "string") query.children = route.query.children;
  return query;
});

const { locale, copy, direction, toggleLocale } = useSiteLocale();

const service = ref<PublicService | null>(null);
const categories = ref<PublicCategory[]>([]);
const state = ref<"loading" | "loaded" | "not-found" | "error">("loading");

// Same accentForIndex/toneForIndex position-cycling as experiences/index.vue,
// so the same category shows the same MockPhoto tone on both the list and
// this detail page — fetched here too (not derived from just the id) after
// an earlier version's id-hash approach visibly disagreed with the list
// page's own index-based tone for the same category, caught by comparing
// real screenshots of both pages side by side.
const categoryIndex = computed(() => categories.value.findIndex((item) => item.id === service.value?.categoryId));

// No per-service description existed before this — every service's
// shared link looked identical in search/social previews, inheriting
// app.vue's one fixed, generic og:description regardless of which
// experience was actually being shared.
const pageTitle = computed(() =>
  state.value === "loaded" && service.value
    ? `${service.value.name[locale.value]} · Sharm To Go`
    : locale.value === "ar"
      ? "التجربة · Sharm To Go"
      : "Experience · Sharm To Go",
);

const pageDescription = computed(() => {
  if (state.value !== "loaded" || !service.value) {
    return locale.value === "ar"
      ? "تجارب شرم الشيخ الحقيقية اللي شرم تو جو بتشغّلها وتنسّقها مباشرة."
      : "Real Sharm El Sheikh experiences that Sharm To Go operates and coordinates directly.";
  }
  // A search snippet/social preview truncates anyway — cut at a word
  // boundary near the conventional ~155-character description length
  // rather than mid-word.
  const full = service.value.description[locale.value];
  if (full.length <= 155) return full;
  return `${full.slice(0, 155).replace(/\s+\S*$/, "")}…`;
});

useHead(() => ({
  title: pageTitle.value,
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
  meta: [
    { name: "description", content: pageDescription.value },
    { property: "og:title", content: pageTitle.value },
    { property: "og:description", content: pageDescription.value },
  ],
}));


onMounted(async () => {
  try {
    categories.value = await listPublicCategories();
  } catch {
    categories.value = [];
  }
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
  <main id="main-content" :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <div class="mx-auto max-w-4xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
    </div>

    <section class="mx-auto mt-10 max-w-4xl">
      <p v-if="state === 'loading'" class="text-sharm-muted">{{ copy.browse.loading }}</p>

      <div v-else-if="state === 'error'" role="alert" class="rounded-2xl border border-sharm-danger/30 bg-sharm-danger-soft p-6 text-sharm-danger">
        {{ copy.browse.loadError }}
      </div>

      <div v-else-if="state === 'not-found'" class="rounded-[2rem] border border-black/5 bg-sharm-surface p-8 text-center shadow-sm sm:p-12">
        <h1 class="text-2xl font-semibold">{{ copy.detail.notFoundHeading }}</h1>
        <p class="mx-auto mt-3 max-w-xl leading-7 text-sharm-muted">{{ copy.detail.notFoundBody }}</p>
        <NuxtLink to="/experiences" class="mt-6 inline-flex rounded-full bg-sharm-action px-6 py-3 font-semibold text-white">
          {{ copy.detail.back }}
        </NuxtLink>
      </div>

      <article v-else-if="service" class="overflow-hidden rounded-[2rem] border border-black/5 bg-sharm-surface p-8 shadow-sm sm:p-12">
        <MockPhoto
          :tone="toneForIndex(categoryIndex)"
          :label="service.name[locale]"
          class="-mx-8 -mt-8 mb-8 aspect-[21/9] w-[calc(100%+4rem)] sm:-mx-12 sm:-mt-12 sm:mb-10 sm:w-[calc(100%+6rem)]"
        />
        <h1 class="font-display text-3xl font-semibold tracking-tight">{{ service.name[locale] }}</h1>
        <p class="mt-2 text-sm text-sharm-muted">{{ copy.browse.operatedBy }}: {{ service.operatedBy ?? "Sharm To Go" }}</p>
        <p class="mt-5 leading-8 text-sharm-muted">{{ service.description[locale] }}</p>

        <section class="mt-8">
          <h2 class="text-lg font-semibold">{{ copy.detail.optionsHeading }}</h2>
          <ul class="mt-3 space-y-3">
            <li v-for="(option, index) in service.options" :key="index" class="flex flex-wrap items-baseline justify-between gap-2 rounded-xl border border-sharm-border p-4">
              <span class="font-medium">{{ option.label[locale] }}</span>
              <span class="text-sharm-sea">
                {{ option.priceCurrency }} {{ option.priceAmount }}
                <span class="text-xs text-sharm-muted">
                  {{ copy.browse.approxUsd(approximateUsdPrice(option.priceAmount)) }}
                  {{ priceBasisLabel(option.priceBasis, copy.browse) }}
                </span>
              </span>
            </li>
          </ul>
          <NuxtLink
            :to="{ path: `/experiences/${service.id}/request`, query: forwardedQuery }"
            class="mt-5 inline-flex w-full items-center justify-center rounded-full bg-sharm-action px-6 py-3.5 text-base font-semibold text-white shadow-lg shadow-sharm-sea/20 transition-transform hover:-translate-y-0.5 hover:shadow-xl sm:w-auto"
          >
            {{ copy.detail.requestCta }}
          </NuxtLink>
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

        <section class="mt-10 rounded-2xl border border-sharm-sea/25 bg-sharm-surface p-6">
          <h2 class="text-lg font-semibold">{{ copy.detail.contactHeading }}</h2>
          <p class="mt-2 leading-7 text-sharm-muted">{{ copy.detail.contactBody }}</p>
          <div class="mt-4 flex flex-wrap gap-3">
            <a
              :href="whatsappLink(copy.detail.whatsappMessage(service.name[locale]))"
              target="_blank"
              rel="noopener"
              class="inline-flex rounded-full bg-sharm-action px-5 py-2.5 text-sm font-semibold text-white"
            >
              {{ copy.detail.whatsappCta }}
            </a>
            <a
              :href="emailLink(copy.detail.emailSubject(service.name[locale]))"
              class="inline-flex rounded-full border border-sharm-sea bg-sharm-surface px-5 py-2.5 text-sm font-semibold text-sharm-sea"
            >
              {{ copy.detail.emailCta }}
            </a>
          </div>
          <p class="money mt-3 text-xs text-sharm-muted">{{ contact.whatsappDisplay }} · {{ contact.email }}</p>
        </section>

        <NuxtLink
          :to="{ path: '/experiences', query: forwardedQuery }"
          class="mt-8 inline-flex rounded-full border border-sharm-border bg-sharm-surface px-6 py-3 font-semibold text-sharm-sea"
        >
          {{ copy.detail.back }}
        </NuxtLink>
      </article>
    </section>
    <SiteFooter :locale="locale" />
  </main>
</template>
