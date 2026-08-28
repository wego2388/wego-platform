<script setup lang="ts">
import { computed } from "vue";
import SiteFooter from "../components/SiteFooter.vue";
import SiteHeader from "../components/SiteHeader.vue";
import { useCountUp } from "../composables/useCountUp";
import { useScrollReveal } from "../composables/useScrollReveal";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../content/locales";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

const pageTitle = computed(() => locale.value === "ar" ? "Sharm Divers Club · ثقة البحر الأحمر" : "Sharm Divers Club · Red Sea confidence, personally guided");
const pageDescription = computed(() => copy.value.hero.body);

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
    { property: "og:type", content: "website" },
  ],
  script: [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "LocalBusiness",
        name: "Sharm Divers Club",
        description: "PADI 5 Star Dive Center in Sharm El Sheikh",
        url: "https://sharmdiversclub.com/",
        telephone: "+201066461010",
        address: {
          "@type": "PostalAddress",
          streetAddress: "Royal Grand Sharm Hotel, Hadabet Um Sid",
          addressLocality: "Sharm El Sheikh",
          addressCountry: "EG",
        },
        openingHoursSpecification: {
          "@type": "OpeningHoursSpecification",
          dayOfWeek: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
          opens: "08:00",
          closes: "20:00",
        },
      }),
    },
  ],
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const categoriesCount = useCountUp(6);
const languagesCount = useCountUp(5);
const categoriesReveal = useScrollReveal();
const personasReveal = useScrollReveal();
const whyReveal = useScrollReveal();
const howReveal = useScrollReveal();
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
    <SiteHeader
      variant="hero"
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

    <div class="sdc-hero border-b border-black/10 text-white">
      <section class="relative mx-auto grid max-w-7xl gap-12 px-6 pt-10 pb-20 lg:grid-cols-[1.15fr_0.85fr] lg:px-10 lg:pt-16">
        <div>
          <span class="inline-flex rounded-full bg-white/10 px-4 py-2 text-xs font-bold tracking-[0.13em] uppercase backdrop-blur">
            {{ copy.preview }}
          </span>
          <p class="mt-8 text-sm font-bold tracking-[0.18em] text-sdc-sand uppercase">{{ copy.hero.eyebrow }}</p>
          <h1 class="mt-4 max-w-3xl font-display text-4xl leading-tight font-semibold tracking-[-0.02em] sm:text-6xl">
            {{ copy.hero.title }}
          </h1>
          <p class="mt-6 max-w-2xl text-lg leading-8 text-white/85">{{ copy.hero.body }}</p>
          <div class="mt-9 flex flex-wrap gap-3">
            <NuxtLink to="/discover" class="rounded-full bg-sdc-sand px-6 py-3 font-semibold text-sdc-deep shadow-lg transition-transform hover:-translate-y-0.5">
              {{ copy.hero.browse }}
            </NuxtLink>
            <a :href="whatsappUrl" target="_blank" rel="noopener" class="rounded-full border border-white/30 bg-white/5 px-6 py-3 font-semibold backdrop-blur transition-transform hover:-translate-y-0.5">
              {{ copy.hero.whatsapp }}
            </a>
          </div>

          <ul class="mt-10 grid gap-3 sm:grid-cols-2">
            <li v-for="guarantee in copy.guarantees" :key="guarantee" class="flex items-start gap-2 text-sm font-semibold text-white/90">
              <span class="mt-1 inline-block size-1.5 shrink-0 rounded-full bg-sdc-sand" aria-hidden="true" />
              {{ guarantee }}
            </li>
          </ul>
        </div>

        <div class="self-end rounded-[2rem] border border-white/15 bg-white/10 p-6 shadow-2xl backdrop-blur">
          <div class="grid grid-cols-3 gap-4 text-center">
            <div :ref="(node) => (categoriesCount.el.value = node as HTMLElement | null)">
              <p class="font-display text-4xl font-semibold text-sdc-sand">{{ categoriesCount.value.value }}</p>
              <p class="mt-1 text-xs font-semibold text-white/75">{{ copy.stats.categories }}</p>
            </div>
            <div>
              <p class="font-display text-4xl font-semibold text-sdc-sand">5★</p>
              <p class="mt-1 text-xs font-semibold text-white/75">{{ copy.stats.padi }}</p>
            </div>
            <div :ref="(node) => (languagesCount.el.value = node as HTMLElement | null)">
              <p class="font-display text-4xl font-semibold text-sdc-sand">{{ languagesCount.value.value }}</p>
              <p class="mt-1 text-xs font-semibold text-white/75">{{ copy.stats.languages }}</p>
            </div>
          </div>
          <ul class="mt-6 grid gap-3 border-t border-white/15 pt-5">
            <li v-for="point in copy.trustStrip" :key="point" class="rounded-2xl bg-white/10 p-4 text-sm font-semibold">
              {{ point }}
            </li>
          </ul>
        </div>
      </section>
    </div>

    <section
      :ref="(node) => (categoriesReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto max-w-7xl px-6 py-20 lg:px-10"
      :class="{ 'reveal-hidden': !categoriesReveal.visible.value }"
    >
      <div class="max-w-2xl">
        <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.categoriesHeading }}</h2>
        <p class="mt-4 leading-7 text-sdc-muted">{{ copy.categoriesBody }}</p>
      </div>
      <div class="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="(category, index) in copy.categories"
          :key="category.title"
          class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm"
          :style="{ transitionDelay: categoriesReveal.visible.value ? `${index * 70}ms` : '0ms' }"
        >
          <div class="grid size-12 place-items-center rounded-2xl" :class="index % 2 === 0 ? 'bg-sdc-turquoise-soft text-sdc-deep' : 'bg-sdc-sand-soft text-sdc-deep'">
            {{ String(index + 1).padStart(2, "0") }}
          </div>
          <p class="mt-6 text-xs font-bold tracking-[0.15em] text-sdc-deep-bright uppercase">{{ category.eyebrow }}</p>
          <h3 class="mt-2 text-xl font-semibold">{{ category.title }}</h3>
          <p class="mt-3 text-sm leading-6 text-sdc-muted">{{ category.description }}</p>
        </article>
      </div>
    </section>

    <section
      :ref="(node) => (whyReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition bg-sdc-turquoise-soft px-6 py-20 lg:px-10"
      :class="{ 'reveal-hidden': !whyReveal.visible.value }"
    >
      <div class="mx-auto max-w-7xl">
        <h2 class="font-display text-3xl font-semibold tracking-tight text-sdc-deep sm:text-4xl">{{ copy.whyHeading }}</h2>
        <p class="mt-3 max-w-2xl text-sdc-deep/80">{{ copy.whyBody }}</p>
        <div class="mt-10 grid gap-5 md:grid-cols-3">
          <article v-for="item in copy.why" :key="item.title" class="hover-lift rounded-[1.75rem] border border-white bg-white/70 p-6">
            <h3 class="text-xl font-semibold text-sdc-deep">{{ item.title }}</h3>
            <p class="mt-3 leading-7 text-sdc-deep/80">{{ item.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <section
      :ref="(node) => (personasReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition bg-sdc-deep px-6 py-20 text-white lg:px-10"
      :class="{ 'reveal-hidden': !personasReveal.visible.value }"
    >
      <div class="mx-auto max-w-7xl">
        <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.personasHeading }}</h2>
        <p class="mt-3 max-w-2xl text-white/75">{{ copy.personasBody }}</p>
        <div class="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          <article
            v-for="(persona, index) in copy.personas"
            :key="persona.name"
            class="hover-lift rounded-[1.75rem] border border-white/15 bg-white/8 p-6"
            :style="{ transitionDelay: personasReveal.visible.value ? `${index * 70}ms` : '0ms' }"
          >
            <h3 class="text-xl font-semibold text-sdc-sand">{{ persona.name }}</h3>
            <p class="mt-3 leading-7 text-white/80">{{ persona.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <section
      id="how"
      :ref="(node) => (howReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto max-w-7xl px-6 py-20 lg:px-10"
      :class="{ 'reveal-hidden': !howReveal.visible.value }"
    >
      <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.how.heading }}</h2>
      <p class="mt-4 max-w-2xl leading-7 text-sdc-muted">{{ copy.how.body }}</p>
      <div class="mt-10 grid gap-5 md:grid-cols-3">
        <article v-for="step in copy.how.steps" :key="step.title" class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6">
          <h3 class="text-xl font-semibold">{{ step.title }}</h3>
          <p class="mt-3 leading-7 text-sdc-muted">{{ step.body }}</p>
        </article>
      </div>
      <p class="mt-8 rounded-2xl bg-sdc-turquoise-soft p-5 text-sm leading-6 text-sdc-deep">{{ copy.bookingNotice }}</p>
      <a :href="whatsappUrl" target="_blank" rel="noopener" class="mt-6 inline-flex rounded-full bg-sdc-deep px-6 py-3 font-semibold text-white transition-transform hover:-translate-y-0.5">
        {{ copy.hero.whatsapp }}
      </a>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
