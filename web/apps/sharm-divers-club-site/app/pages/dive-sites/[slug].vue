<script setup lang="ts">
import { computed } from "vue";
import ConditionsWidget from "../../components/ConditionsWidget.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import SiteHeader from "../../components/SiteHeader.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { findDiveSite, offeringsForSite } from "../../content/diveSites";
import { directionFor, siteCopy, whatsappUrl } from "../../content/locales";
import { formatEur } from "../../content/offerings";

const route = useRoute();
const slug = String(route.params.slug);
const site = findDiveSite(slug);

if (!site) {
  throw createError({ statusCode: 404, statusMessage: "Dive site not found" });
}

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));
const name = computed(() => site.name[locale.value]);
const blurb = computed(() => site.blurb[locale.value]);
const siteOfferings = computed(() => offeringsForSite(site));

const askUrl = computed(() => {
  const text = locale.value === "ar"
    ? `مرحبًا، عايز أسأل عن رحلات ${name.value}`
    : `Hi, I'd like to ask about trips to ${name.value}`;
  return `${whatsappUrl}?text=${encodeURIComponent(text)}`;
});

useHead(() => ({
  title: `${name.value} · Sharm Divers Club`,
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: blurb.value }],
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
      <NuxtLink to="/dive-sites" class="inline-flex items-center gap-2 font-semibold text-sdc-deep-bright">
        <span aria-hidden="true">{{ direction === "rtl" ? "→" : "←" }}</span> {{ copy.diveSites.back }}
      </NuxtLink>

      <h1 class="mt-6 font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ name }}</h1>
      <p class="mt-4 max-w-2xl leading-8 text-sdc-muted">{{ blurb }}</p>

      <div class="mt-8 max-w-2xl">
        <ConditionsWidget
          :locale="locale"
          :heading="copy.diveSites.conditionsHeading"
          :loading-label="copy.diveSites.loadingLabel"
          :unavailable-label="copy.diveSites.unavailableLabel"
          :air-label="copy.diveSites.airLabel"
          :sea-label="copy.diveSites.seaLabel"
          :wind-label="copy.diveSites.windLabel"
          :wave-label="copy.diveSites.waveLabel"
        />
      </div>

      <h2 class="mt-10 text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.diveSites.offeringsHeading }}</h2>
      <ul class="mt-4 grid gap-3 sm:grid-cols-2">
        <li v-for="offering in siteOfferings" :key="offering.code">
          <NuxtLink
            :to="`/discover/${offering.code}`"
            class="flex items-center justify-between gap-3 rounded-xl border border-sdc-border bg-sdc-surface px-4 py-3 text-sm font-semibold hover:bg-sdc-turquoise-soft"
          >
            <span>{{ offering.name[locale] }}</span>
            <span class="money text-sdc-deep-bright">{{ formatEur(offering.priceEur) }}</span>
          </NuxtLink>
        </li>
      </ul>

      <div class="mt-8 flex flex-wrap gap-3">
        <a :href="askUrl" target="_blank" rel="noopener" class="inline-flex min-h-11 items-center rounded-full bg-sdc-deep px-6 font-semibold text-white transition-transform hover:-translate-y-0.5">
          {{ copy.diveSites.whatsapp }}
        </a>
        <NuxtLink to="/package-builder" class="inline-flex min-h-11 items-center rounded-full border border-sdc-border bg-sdc-surface px-6 font-semibold text-sdc-deep-bright">
          {{ copy.diveSites.buildPackageCta }}
        </NuxtLink>
      </div>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
