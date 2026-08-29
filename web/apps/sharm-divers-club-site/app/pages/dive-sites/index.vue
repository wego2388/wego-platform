<script setup lang="ts">
import { computed } from "vue";
import ConditionsWidget from "../../components/ConditionsWidget.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import SiteHeader from "../../components/SiteHeader.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";
import { diveSites, offeringsForSite } from "../../content/diveSites";
import { directionFor, siteCopy } from "../../content/locales";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "مواقع الغوص · Sharm Divers Club" : "Dive Sites · Sharm Divers Club",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: copy.value.diveSites.body }],
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

    <section class="px-6 pt-10 pb-4 lg:px-10">
      <div class="mx-auto max-w-3xl rounded-[2rem] border border-sdc-border bg-sdc-surface p-8 text-center shadow-sm sm:p-12">
        <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.diveSites.heading }}</h1>
        <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.diveSites.body }}</p>
      </div>
    </section>

    <section class="mx-auto max-w-4xl px-6 pt-8 lg:px-10">
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
    </section>

    <section class="mx-auto max-w-6xl px-6 py-12 lg:px-10">
      <div class="grid gap-5 md:grid-cols-2">
        <NuxtLink
          v-for="site in diveSites"
          :key="site.slug"
          :to="`/dive-sites/${site.slug}`"
          class="hover-lift block rounded-[1.75rem] border border-sdc-border bg-sdc-surface p-6 shadow-sm"
        >
          <h2 class="text-xl font-semibold">{{ site.name[locale] }}</h2>
          <p class="mt-3 text-sm leading-6 text-sdc-muted">{{ site.blurb[locale] }}</p>
          <p class="mt-4 text-xs font-semibold text-sdc-deep-bright">
            {{ offeringsForSite(site).length }} · {{ copy.diveSites.offeringsHeading }}
          </p>
        </NuxtLink>
      </div>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
