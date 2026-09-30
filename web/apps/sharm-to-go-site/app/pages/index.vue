<script setup lang="ts">
import { computed, ref } from "vue";
import MockPhoto from "../components/MockPhoto.vue";
import { accentForIndex, toneForIndex } from "../content/categoryAccents";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";
import { contact, emailLink, whatsappLink } from "../content/contact";
import { vReveal } from "../composables/useScrollReveal";

const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "اكتشف شرم بوضوح · Sharm To Go" : "Sharm To Go · Discover Sharm clearly",
  htmlAttrs: {
    dir: direction.value,
    lang: locale.value,
  },
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}

const stepAccents = ["bg-sharm-sun text-sharm-ink", "bg-white text-sharm-sea", "bg-sharm-sun text-sharm-ink"];
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink">
    <div class="sharm-hero relative overflow-hidden border-b border-black/5">
      <div
        class="sharm-hero-orb pointer-events-none absolute -top-16 -right-10 size-72 rounded-full bg-sharm-sun/20 blur-3xl"
        aria-hidden="true"
      />
      <div
        class="sharm-hero-orb-delay pointer-events-none absolute -bottom-24 left-[-4rem] size-80 rounded-full bg-sharm-sea-bright/15 blur-3xl"
        aria-hidden="true"
      />

      <header class="relative mx-auto flex max-w-7xl items-center justify-between gap-6 px-6 py-6 lg:px-10">
        <NuxtLink to="/" class="flex items-center gap-3 font-semibold" aria-label="Sharm To Go home">
          <img src="/icon-192.png" alt="" width="44" height="44" class="size-11" aria-hidden="true">
          <span class="font-display text-lg">Sharm To Go</span>
        </NuxtLink>
        <nav class="hidden items-center gap-7 text-sm font-semibold md:flex" aria-label="Primary navigation">
          <NuxtLink to="/experiences" class="transition-colors hover:text-sharm-sea-bright">{{ copy.nav.experiences }}</NuxtLink>
          <a href="#how" class="transition-colors hover:text-sharm-sea-bright">{{ copy.nav.howItWorks }}</a>
          <NuxtLink to="/about" class="transition-colors hover:text-sharm-sea-bright">{{ copy.nav.about }}</NuxtLink>
          <NuxtLink to="/faq" class="transition-colors hover:text-sharm-sea-bright">{{ copy.nav.faq }}</NuxtLink>
          <NuxtLink to="/contact" class="transition-colors hover:text-sharm-sea-bright">{{ copy.nav.contact }}</NuxtLink>
        </nav>
        <button
          type="button"
          class="rounded-full border border-sharm-sea/20 bg-sharm-surface/80 px-4 py-2 text-sm font-semibold text-sharm-sea transition-transform hover:scale-105"
          @click="toggleLocale"
        >
          {{ copy.languageName }}
        </button>
      </header>

      <section class="relative mx-auto grid max-w-7xl gap-12 px-6 pt-16 pb-20 lg:grid-cols-[1.15fr_0.85fr] lg:px-10 lg:pt-24">
        <div>
          <span
            class="inline-flex items-center gap-2 rounded-full bg-sharm-surface/80 px-4 py-2 text-xs font-bold tracking-[0.13em] text-sharm-sea uppercase shadow-sm"
          >
            <span class="size-1.5 rounded-full bg-sharm-sun" aria-hidden="true" />
            {{ copy.preview }}
          </span>
          <p class="mt-8 text-sm font-bold tracking-[0.18em] text-sharm-sea uppercase">{{ copy.hero.eyebrow }}</p>
          <h1 class="font-display mt-4 max-w-3xl text-4xl leading-tight font-semibold tracking-[-0.02em] sm:text-6xl">
            {{ copy.hero.title }}
          </h1>
          <p class="mt-6 max-w-2xl text-lg leading-8 text-sharm-muted">{{ copy.hero.body }}</p>
          <div class="mt-9 flex flex-wrap gap-3">
            <NuxtLink
              to="/experiences"
              class="rounded-full bg-sharm-sea px-6 py-3 font-semibold text-white shadow-lg shadow-sharm-sea/20 transition-transform hover:-translate-y-0.5 hover:shadow-xl"
            >
              {{ copy.hero.browse }}
            </NuxtLink>
            <a
              href="#how"
              class="rounded-full border border-sharm-sea/20 bg-sharm-surface px-6 py-3 font-semibold text-sharm-sea transition-transform hover:-translate-y-0.5"
            >
              {{ copy.hero.plan }}
            </a>
          </div>
        </div>

        <div class="self-end rounded-[2rem] border border-sharm-surface/80 bg-sharm-surface/90 p-5 shadow-2xl shadow-sharm-sea/10 backdrop-blur">
          <div class="grid gap-3">
            <div class="rounded-2xl border border-black/5 p-4">
              <p class="text-xs font-bold tracking-[0.12em] text-sharm-muted uppercase">{{ copy.search.category }}</p>
              <p class="mt-2 font-semibold">{{ copy.search.anyCategory }}</p>
            </div>
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="rounded-2xl border border-black/5 p-4">
                <p class="text-xs font-bold tracking-[0.12em] text-sharm-muted uppercase">{{ copy.search.date }}</p>
                <p class="mt-2 font-semibold">{{ copy.search.flexible }}</p>
              </div>
              <div class="rounded-2xl border border-black/5 p-4">
                <p class="text-xs font-bold tracking-[0.12em] text-sharm-muted uppercase">{{ copy.search.guests }}</p>
                <p class="mt-2 font-semibold">{{ copy.search.people }}</p>
              </div>
            </div>
          </div>
          <div class="mt-5 rounded-2xl bg-sharm-lagoon p-4 text-sm leading-6 text-sharm-sea">
            {{ copy.marketplaceNotice }}
          </div>
        </div>
      </section>
    </div>

    <section class="border-b border-black/5 bg-sharm-surface px-6 py-14 lg:px-10">
      <div class="mx-auto grid max-w-7xl gap-10 lg:grid-cols-[0.8fr_1.2fr] lg:items-end">
        <div v-reveal class="sharm-reveal">
          <h2 class="font-display text-3xl font-semibold tracking-tight">{{ copy.proof.heading }}</h2>
          <p class="mt-4 leading-7 text-sharm-muted">{{ copy.proof.body }}</p>
          <NuxtLink to="/about" class="mt-6 inline-flex font-bold text-sharm-sea underline decoration-sharm-sun decoration-2 underline-offset-4">
            {{ copy.nav.about }}
          </NuxtLink>
        </div>
        <dl class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div v-for="fact in copy.proof.facts" :key="fact.value" class="rounded-2xl bg-sharm-lagoon p-5 text-center text-sharm-sea">
            <dt class="font-display text-2xl font-bold sm:text-3xl">{{ fact.value }}</dt>
            <dd class="mt-2 text-xs font-semibold leading-5">{{ fact.label }}</dd>
          </div>
        </dl>
      </div>
    </section>

    <section class="mx-auto max-w-7xl px-6 py-20 lg:px-10">
      <div v-reveal class="sharm-reveal max-w-2xl">
        <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.categoriesHeading }}</h2>
        <p class="mt-4 leading-7 text-sharm-muted">{{ copy.categoriesBody }}</p>
      </div>
      <div class="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
        <article
          v-for="(category, index) in copy.categories"
          :key="category.title"
          v-reveal
          class="sharm-reveal sharm-card-lift overflow-hidden rounded-[1.75rem] border bg-sharm-surface shadow-sm"
          :class="accentForIndex(index).ring"
          :style="{ transitionDelay: `${index * 70}ms` }"
        >
          <MockPhoto :tone="toneForIndex(index)" :label="category.title" class="aspect-[4/3] w-full" />
          <div class="p-6">
            <p class="text-xs font-bold tracking-[0.15em] uppercase" :class="accentForIndex(index).text">{{ category.eyebrow }}</p>
            <h3 class="mt-2 text-xl font-semibold">{{ category.title }}</h3>
            <p class="mt-3 text-sm leading-6 text-sharm-muted">{{ category.description }}</p>
          </div>
        </article>
      </div>
    </section>

    <section id="how" class="relative overflow-hidden bg-sharm-sea px-6 py-20 text-white lg:px-10">
      <div class="pointer-events-none absolute top-0 right-0 size-96 rounded-full bg-sharm-sun/10 blur-3xl" aria-hidden="true" />
      <div class="relative mx-auto max-w-7xl">
        <h2 v-reveal class="sharm-reveal font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.how.heading }}</h2>
        <div class="mt-10 grid gap-5 md:grid-cols-3">
          <article
            v-for="(step, index) in copy.how.steps"
            :key="step.title"
            v-reveal
            class="sharm-reveal rounded-[1.75rem] border border-white/15 bg-white/8 p-6 transition-colors hover:bg-white/12"
            :style="{ transitionDelay: `${index * 90}ms` }"
          >
            <div class="grid size-9 place-items-center rounded-full text-sm font-black" :class="stepAccents[index]">{{ index + 1 }}</div>
            <h3 class="mt-4 text-xl font-semibold">{{ step.title }}</h3>
            <p class="mt-3 leading-7 text-white/75">{{ step.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <section id="trust" class="mx-auto grid max-w-7xl gap-10 px-6 py-20 lg:grid-cols-2 lg:px-10">
      <div v-reveal class="sharm-reveal">
        <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.trust.heading }}</h2>
        <p class="mt-5 max-w-xl leading-7 text-sharm-muted">{{ copy.trust.body }}</p>
      </div>
      <ul class="grid gap-3 sm:grid-cols-2">
        <li
          v-for="(point, index) in copy.trust.points"
          :key="point"
          v-reveal
          class="sharm-reveal sharm-card-lift flex items-start gap-3 rounded-2xl border border-black/5 bg-sharm-surface p-5 font-semibold"
          :style="{ transitionDelay: `${index * 60}ms` }"
        >
          <span class="grid size-6 shrink-0 place-items-center rounded-full bg-sharm-lagoon text-sharm-sea-bright">✓</span>
          {{ point }}
        </li>
      </ul>
    </section>

    <footer class="border-t border-black/5 bg-sharm-surface px-6 py-12 text-sm text-sharm-muted lg:px-10">
      <div class="mx-auto grid max-w-7xl gap-10 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <NuxtLink to="/" class="flex items-center gap-3 text-sharm-ink">
            <img src="/icon-192.png" alt="" width="44" height="44" class="size-11" aria-hidden="true">
            <span class="font-display text-lg font-semibold">Sharm To Go</span>
          </NuxtLink>
          <p class="mt-4 max-w-sm leading-6">{{ copy.footerFull.tagline }}</p>
        </div>
        <div>
          <h2 class="font-bold text-sharm-ink">{{ copy.footerFull.exploreHeading }}</h2>
          <ul class="mt-4 space-y-3">
            <li><NuxtLink to="/experiences" class="hover:text-sharm-sea">{{ copy.footerFull.exploreLinks.experiences }}</NuxtLink></li>
            <li><NuxtLink to="/track" class="hover:text-sharm-sea">{{ copy.footerFull.exploreLinks.trackRequest }}</NuxtLink></li>
            <li><NuxtLink to="/booking-preview" class="hover:text-sharm-sea">{{ copy.footerFull.exploreLinks.bookingPreview }}</NuxtLink></li>
          </ul>
        </div>
        <div>
          <h2 class="font-bold text-sharm-ink">{{ copy.footerFull.companyHeading }}</h2>
          <ul class="mt-4 space-y-3">
            <li><NuxtLink to="/about" class="hover:text-sharm-sea">{{ copy.nav.about }}</NuxtLink></li>
            <li><NuxtLink to="/faq" class="hover:text-sharm-sea">{{ copy.footerFull.companyLinks.faq }}</NuxtLink></li>
            <li><NuxtLink to="/contact" class="hover:text-sharm-sea">{{ copy.footerFull.companyLinks.contact }}</NuxtLink></li>
            <li><NuxtLink to="/privacy" class="hover:text-sharm-sea">{{ copy.footerFull.legal.privacy }}</NuxtLink></li>
            <li><NuxtLink to="/terms" class="hover:text-sharm-sea">{{ copy.footerFull.legal.terms }}</NuxtLink></li>
          </ul>
        </div>
        <div>
          <h2 class="font-bold text-sharm-ink">{{ copy.footerFull.contactHeading }}</h2>
          <p class="money mt-4 space-y-3">
            <a :href="whatsappLink(locale === 'ar' ? 'مرحبًا Sharm To Go' : 'Hello Sharm To Go')" target="_blank" rel="noopener" class="block font-semibold text-sharm-sea hover:underline">WhatsApp {{ contact.whatsappDisplay }}</a>
            <a :href="emailLink('Sharm To Go')" class="block font-semibold text-sharm-sea hover:underline">{{ contact.email }}</a>
          </p>
        </div>
      </div>
      <div class="mx-auto mt-10 flex max-w-7xl flex-wrap justify-between gap-3 border-t border-sharm-border pt-6">
        <p>{{ copy.footerFull.rights }}</p><p class="font-semibold text-sharm-sea">{{ copy.footer }}</p>
      </div>
    </footer>
  </main>
</template>
