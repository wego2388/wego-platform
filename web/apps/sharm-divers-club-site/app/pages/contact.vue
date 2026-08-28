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
  title: locale.value === "ar" ? "تواصل · Sharm Divers Club" : "Contact · Sharm Divers Club",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
  meta: [{ name: "description", content: copy.value.contact.body }],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const cardsReveal = useScrollReveal();
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
      <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.contact.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.contact.body }}</p>
    </section>

    <section
      :ref="(node) => (cardsReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto mt-12 max-w-4xl px-6 lg:px-10"
      :class="{ 'reveal-hidden': !cardsReveal.visible.value }"
    >
      <div class="grid gap-5 sm:grid-cols-2">
        <a :href="whatsappUrl" target="_blank" rel="noopener" class="hover-lift rounded-[1.75rem] border border-sdc-border bg-sdc-deep p-6 text-white shadow-sm sm:col-span-2">
          <p class="text-xs font-bold tracking-[0.12em] text-sdc-sand uppercase">{{ copy.contact.whatsappLabel }}</p>
          <p class="mt-2 reference text-lg font-semibold" dir="ltr">+20 10 6646 1010</p>
          <p class="mt-2 text-sm text-white/80">{{ copy.contact.whatsappBody }}</p>
        </a>
        <div class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
          <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.phoneLabel }}</p>
          <p class="reference mt-2 text-lg font-semibold" dir="ltr">+20 10 6646 1010</p>
        </div>
        <div class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
          <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.emailLabel }}</p>
          <p class="reference mt-2 text-lg font-semibold" dir="ltr">Sales@sharmdiversclub.com</p>
        </div>
        <div class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
          <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.locationLabel }}</p>
          <p class="mt-2 font-semibold">Royal Grand Sharm Hotel, Hadabet Um Sid, Sharm El Sheikh, Egypt</p>
        </div>
        <div class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
          <p class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ copy.contact.hoursLabel }}</p>
          <p class="reference mt-2 font-semibold" dir="ltr">Daily 08:00–20:00</p>
        </div>
      </div>
    </section>

    <div class="h-16" />

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
