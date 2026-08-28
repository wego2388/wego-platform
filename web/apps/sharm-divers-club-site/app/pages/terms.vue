<script setup lang="ts">
import { computed } from "vue";
import SiteFooter from "../components/SiteFooter.vue";
import SiteHeader from "../components/SiteHeader.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy } from "../content/locales";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));
const page = computed(() => copy.value.legalPages.terms);

useHead(() => ({
  title: locale.value === "ar" ? "شروط الاستخدام · Sharm Divers Club" : "Terms of Use · Sharm Divers Club",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: page.value.sections[0]?.body ?? page.value.heading }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
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

    <section class="mx-auto mt-14 mb-16 max-w-2xl px-6 lg:px-10">
      <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ page.heading }}</h1>
      <p class="mt-2 text-sm text-sdc-muted">{{ copy.legalPages.updated }}</p>
      <div class="mt-8 grid gap-8">
        <div v-for="section in page.sections" :key="section.title">
          <h2 class="text-lg font-semibold">{{ section.title }}</h2>
          <p class="mt-2 leading-7 text-sdc-muted">{{ section.body }}</p>
        </div>
      </div>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
