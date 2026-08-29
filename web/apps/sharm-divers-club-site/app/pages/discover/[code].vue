<script setup lang="ts">
import { computed } from "vue";
import SiteFooter from "../../components/SiteFooter.vue";
import SiteHeader from "../../components/SiteHeader.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../../content/locales";
import { audienceLabel, categoryIcon, diveCountLabel, durationLabel, formatEur, imageForOffering, offerings } from "../../content/offerings";

const route = useRoute();
const code = String(route.params.code).toUpperCase();
const offering = offerings.find(item => item.code === code);

if (!offering) {
  throw createError({ statusCode: 404, statusMessage: "Offering not found" });
}

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));
const category = computed(() => copy.value.categories.find(item => item.id === offering.categoryId));
const label = computed(() => offering.name[locale.value]);
const duration = computed(() => durationLabel(locale.value, offering.durationMinutes));
const dives = computed(() => diveCountLabel(locale.value, offering.diveCount));
const audience = computed(() => audienceLabel(locale.value, offering.audience));
const price = computed(() => formatEur(offering.priceEur));
const icon = computed(() => categoryIcon(offering.categoryId));
const photo = computed(() => imageForOffering(offering, locale.value));
const related = computed(() => offerings.filter(item => item.categoryId === offering.categoryId && item.code !== offering.code));

const askUrl = computed(() => {
  const text = locale.value === "ar"
    ? `مرحبًا، عايز أسأل عن: ${label.value} (${offering.code})`
    : `Hi, I'd like to ask about: ${label.value} (${offering.code})`;
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});

useHead(() => ({
  title: `${label.value} · Sharm Divers Club`,
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: `${label.value} — ${audience.value}. ${copy.value.discover.pricingNotice}.` }],
  script: [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "Product",
        name: label.value,
        sku: offering.code,
        category: category.value?.title,
        offers: {
          "@type": "Offer",
          price: offering.priceEur,
          priceCurrency: "EUR",
          availability: "https://schema.org/InStock",
          url: `https://sharmdiversclub.com/discover/${offering.code}`,
        },
      }),
    },
  ],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}
</script>

<template>
  <main id="main-content" tabindex="-1" :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
    <SiteHeader
      variant="solid"
      :locale="locale"
      :direction="direction"
      :home-label="copy.nav.home"
      :discover-label="copy.nav.discover"
      :about-label="copy.nav.about"
      :faq-label="copy.nav.faq"
      :contact-label="copy.nav.contact"
      :language-name="copy.languageName"
      :whatsapp-label="copy.whatsappFab"
      :menu-label="copy.nav.menu"
      @toggle-locale="toggleLocale"
    />

    <section class="mx-auto max-w-5xl px-5 py-10 lg:px-10">
      <NuxtLink to="/discover" class="inline-flex items-center gap-2 font-semibold text-sdc-deep-bright">
        <span aria-hidden="true">{{ direction === "rtl" ? "→" : "←" }}</span> {{ copy.discover.back }}
      </NuxtLink>

      <div class="mt-6 grid gap-8 lg:grid-cols-[1fr_20rem]">
        <div class="overflow-hidden rounded-[1.75rem] border border-sdc-border bg-sdc-surface">
          <img
            v-if="photo"
            :src="photo.url"
            :alt="photo.alt"
            width="1200"
            height="800"
            class="aspect-[3/2] w-full object-cover sm:aspect-[16/9]"
          >
          <div class="p-6 sm:p-8">
          <div class="grid size-14 place-items-center rounded-2xl bg-sdc-turquoise-soft text-sdc-deep">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path :d="icon" />
            </svg>
          </div>
          <p class="mt-5 text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ category?.title }}</p>
          <h1 class="mt-2 font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ label }}</h1>
          <p class="mt-3 leading-7 text-sdc-muted">{{ audience }}</p>

          <div v-if="duration || dives" class="mt-6 flex flex-wrap gap-2 text-xs font-semibold text-sdc-deep-bright">
            <span v-if="duration" class="rounded-full bg-sdc-canvas px-3 py-1">{{ duration }}</span>
            <span v-if="dives" class="rounded-full bg-sdc-canvas px-3 py-1">{{ dives }}</span>
          </div>

          <div class="mt-8 border-t border-sdc-border pt-6">
            <p class="money text-3xl font-semibold text-sdc-ink">{{ price }}</p>
            <p class="mt-2 text-xs font-semibold text-sdc-deep-bright">{{ copy.discover.pricingNotice }}</p>
          </div>
          </div>
        </div>

        <aside class="h-fit rounded-[1.75rem] border border-sdc-border bg-sdc-surface p-6 shadow-sm">
          <p class="reference text-sm leading-6 text-sdc-muted" dir="ltr">catalog.dive-core.v1.json · {{ offering.code }}</p>
          <a :href="askUrl" target="_blank" rel="noopener" class="mt-5 flex min-h-11 items-center justify-center rounded-full bg-sdc-deep px-5 text-sm font-semibold text-white">
            {{ copy.discover.whatsapp }}
          </a>

          <template v-if="related.length">
            <h2 class="mt-8 text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.discover.moreInCategory }}</h2>
            <ul class="mt-3 grid gap-2">
              <li v-for="item in related" :key="item.code">
                <NuxtLink :to="`/discover/${item.code}`" class="flex items-center justify-between gap-3 rounded-xl bg-sdc-canvas px-3 py-2 text-sm font-semibold hover:bg-sdc-turquoise-soft">
                  <span>{{ item.name[locale] }}</span>
                  <span class="money text-sdc-deep-bright">{{ formatEur(item.priceEur) }}</span>
                </NuxtLink>
              </li>
            </ul>
          </template>
        </aside>
      </div>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
