<script setup lang="ts">
import { computed } from "vue";
import SiteFooter from "../components/SiteFooter.vue";
import SiteHeader from "../components/SiteHeader.vue";
import { useScrollReveal } from "../composables/useScrollReveal";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../content/locales";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "الأسئلة الشائعة · Sharm Divers Club" : "FAQ · Sharm Divers Club",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: copy.value.faq.body }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const knownReveal = useScrollReveal();
const unknownReveal = useScrollReveal();
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
      <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.faq.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.faq.body }}</p>
    </section>

    <section
      :ref="(node) => (knownReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto mt-12 max-w-3xl px-6 lg:px-10"
      :class="{ 'reveal-hidden': !knownReveal.visible.value }"
    >
      <h2 class="text-xs font-bold tracking-[0.15em] text-sdc-deep-bright uppercase">{{ copy.faq.knownHeading }}</h2>
      <div class="mt-4 grid gap-3">
        <details v-for="item in copy.faq.known" :key="item.q" class="group rounded-2xl border border-sdc-border bg-white p-5 open:shadow-sm">
          <summary class="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold">
            {{ item.q }}
            <span class="shrink-0 text-sdc-deep-bright transition-transform group-open:rotate-45" aria-hidden="true">+</span>
          </summary>
          <p class="mt-3 leading-6 text-sdc-muted">{{ item.a }}</p>
        </details>
      </div>
    </section>

    <section
      :ref="(node) => (unknownReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto mt-10 mb-16 max-w-3xl px-6 lg:px-10"
      :class="{ 'reveal-hidden': !unknownReveal.visible.value }"
    >
      <h2 class="text-xs font-bold tracking-[0.15em] text-sdc-deep-bright uppercase">{{ copy.faq.unknownHeading }}</h2>
      <p class="mt-2 text-sm leading-6 text-sdc-muted">{{ copy.faq.unknownIntro }}</p>
      <div class="mt-4 grid gap-3">
        <details v-for="item in copy.faq.unknown" :key="item.q" class="group rounded-2xl border border-sdc-border bg-sdc-turquoise-soft p-5 open:shadow-sm">
          <summary class="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold text-sdc-deep">
            {{ item.q }}
            <span class="shrink-0 text-sdc-deep transition-transform group-open:rotate-45" aria-hidden="true">+</span>
          </summary>
          <p class="mt-3 leading-6 text-sdc-deep">{{ item.a }}</p>
        </details>
      </div>
      <a :href="whatsappUrl" target="_blank" rel="noopener" class="mt-6 inline-flex rounded-full bg-sdc-deep px-6 py-3 font-semibold text-white transition-transform hover:-translate-y-0.5">
        {{ copy.faq.whatsapp }}
      </a>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
