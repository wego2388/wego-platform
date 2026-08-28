<script setup lang="ts">
import { computed } from "vue";
import SiteFooter from "../components/SiteFooter.vue";
import SiteHeader from "../components/SiteHeader.vue";
import { useScrollReveal } from "../composables/useScrollReveal";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy } from "../content/locales";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "من نحن · Sharm Divers Club" : "About · Sharm Divers Club",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
  meta: [{ name: "description", content: copy.value.about.body }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const factsReveal = useScrollReveal();
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

    <section class="mx-auto mt-14 max-w-3xl px-6 text-center lg:px-10">
      <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.about.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.about.body }}</p>
    </section>

    <section
      :ref="(node) => (factsReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto mt-12 max-w-4xl px-6 lg:px-10"
      :class="{ 'reveal-hidden': !factsReveal.visible.value }"
    >
      <div class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-8">
        <h2 class="text-xl font-semibold">{{ copy.about.factsHeading }}</h2>
        <dl class="mt-6 grid gap-5 sm:grid-cols-2">
          <div v-for="fact in copy.about.facts" :key="fact.label">
            <dt class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ fact.label }}</dt>
            <dd class="mt-1 font-semibold">{{ fact.value }}</dd>
          </div>
        </dl>
      </div>

      <div class="mt-8 rounded-[1.75rem] border border-sdc-border bg-sdc-turquoise-soft p-8">
        <h2 class="text-xl font-semibold text-sdc-deep">{{ copy.about.languagesHeading }}</h2>
        <p class="mt-3 leading-7 text-sdc-deep">{{ copy.about.languagesBody }}</p>
      </div>
    </section>

    <div class="h-16" />

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
