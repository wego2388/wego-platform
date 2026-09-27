<script setup lang="ts">
import { computed } from "vue";
import SiteHeader from "../components/SiteHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { useCountUp } from "../composables/useCountUp";
import { useScrollReveal } from "../composables/useScrollReveal";
import { useSiteLocale } from "../composables/useSiteLocale";
import {
  directionFor,
  siteCopy,
  whatsappUrl,
  categoryMeta,
} from "../content/locales";
import type { StsLocale, TourCategory } from "../content/locales";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: "Safari Tours Sharm — Explore the Red Sea & Sinai Desert",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [
    { name: "description", content: copy.value.hero.body },
    { property: "og:title", content: "Safari Tours Sharm — " + copy.value.hero.title },
    { property: "og:description", content: copy.value.hero.body },
  ],
  script: [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "TouristInformationCenter",
        name: "Safari Tours Sharm",
        url: "https://safaritourssharm.com",
        telephone: "+201111292690",
        email: "safaritourssharm@gmail.com",
        address: {
          "@type": "PostalAddress",
          addressLocality: "Sharm El Sheikh",
          addressCountry: "EG",
        },
      }),
    },
  ],
}));

// count-up stats
const travellersCount = useCountUp(500, 1200);
const toursCount = useCountUp(30, 900);

// scroll reveals
const trustReveal = useScrollReveal();
const categoriesReveal = useScrollReveal();
const howReveal = useScrollReveal();
const cancellationReveal = useScrollReveal();

const CATEGORIES: TourCategory[] = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"];
</script>

<template>
  <div :dir="direction" :lang="locale" class="min-h-screen bg-sts-canvas text-sts-ink">
    <SiteHeader
      :locale="locale"
      :direction="direction"
      :nav="copy.nav"
      :whatsapp-label="copy.whatsappFab"
      :current-locales="ALL_LOCALES"
      @set-locale="(l) => (locale = l)"
    />

    <!-- ── HERO ──────────────────────────────────────────────────────── -->
    <section class="sts-hero relative" aria-labelledby="hero-heading">
      <div class="relative z-10 mx-auto max-w-7xl px-6 pb-24 pt-16 lg:px-10 lg:pt-24">
        <div class="max-w-3xl">
          <span
            class="inline-flex rounded-full border border-white/20 bg-white/10 px-4 py-1.5 text-xs font-bold tracking-[0.14em] text-white/90 uppercase backdrop-blur"
          >
            {{ copy.hero.eyebrow }}
          </span>

          <h1
            id="hero-heading"
            class="mt-6 font-display text-4xl font-semibold leading-tight tracking-tight text-white sm:text-5xl lg:text-6xl"
          >
            {{ copy.hero.title }}
          </h1>

          <p class="mt-5 max-w-2xl text-lg leading-8 text-white/80">
            {{ copy.hero.body }}
          </p>

          <div class="mt-8 flex flex-wrap gap-3">
            <NuxtLink
              to="/tours"
              class="rounded-full bg-sts-coral px-7 py-3.5 font-semibold text-white shadow-lg transition-transform hover:-translate-y-0.5"
            >
              {{ copy.hero.cta }}
            </NuxtLink>
            <a
              :href="whatsappUrl"
              target="_blank"
              rel="noopener"
              class="rounded-full border border-white/30 bg-white/10 px-7 py-3.5 font-semibold text-white backdrop-blur transition-transform hover:-translate-y-0.5"
            >
              {{ copy.hero.whatsapp }}
            </a>
          </div>
        </div>

        <!-- Stats row -->
        <div class="mt-14 flex flex-wrap gap-8">
          <div
            :ref="(n) => (travellersCount.el.value = n as HTMLElement | null)"
            class="text-center"
          >
            <p class="font-display text-4xl font-semibold text-sts-sand">
              {{ travellersCount.value.value }}+
            </p>
            <p class="mt-1 text-xs font-semibold uppercase tracking-wide text-white/60">
              Happy Travelers
            </p>
          </div>
          <div class="text-center">
            <p class="font-display text-4xl font-semibold text-sts-sand">4.9★</p>
            <p class="mt-1 text-xs font-semibold uppercase tracking-wide text-white/60">
              Google Rating
            </p>
          </div>
          <div
            :ref="(n) => (toursCount.el.value = n as HTMLElement | null)"
            class="text-center"
          >
            <p class="font-display text-4xl font-semibold text-sts-sand">
              {{ toursCount.value.value }}+
            </p>
            <p class="mt-1 text-xs font-semibold uppercase tracking-wide text-white/60">
              Tours Available
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- ── TRUST BAR ──────────────────────────────────────────────────── -->
    <section
      :ref="(n) => (trustReveal.el.value = n as HTMLElement | null)"
      class="reveal-transition border-b border-sts-border bg-sts-surface"
      :class="{ 'reveal-hidden': !trustReveal.visible.value }"
      aria-label="Trust signals"
    >
      <ul
        class="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-x-8 gap-y-3 px-6 py-5 lg:px-10"
      >
        <li
          v-for="item in copy.trustItems"
          :key="item.label"
          class="text-sm font-semibold text-sts-ink"
        >
          {{ item.label }}
        </li>
      </ul>
    </section>

    <!-- ── CATEGORIES ─────────────────────────────────────────────────── -->
    <section
      :ref="(n) => (categoriesReveal.el.value = n as HTMLElement | null)"
      class="reveal-transition mx-auto max-w-7xl px-6 py-20 lg:px-10"
      :class="{ 'reveal-hidden': !categoriesReveal.visible.value }"
      aria-labelledby="categories-heading"
    >
      <div class="max-w-2xl">
        <h2
          id="categories-heading"
          class="font-display text-3xl font-semibold tracking-tight sm:text-4xl"
        >
          {{ copy.categoriesHeading }}
        </h2>
        <p class="mt-4 leading-7 text-sts-muted">{{ copy.categoriesBody }}</p>
      </div>

      <div class="mt-10 grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        <NuxtLink
          v-for="(cat, index) in CATEGORIES"
          :key="cat"
          :to="`/category/${categoryMeta[cat].slug}`"
          class="hover-lift group rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-6 shadow-sm"
          :style="{ transitionDelay: categoriesReveal.visible.value ? `${index * 60}ms` : '0ms' }"
        >
          <div
            class="grid size-12 place-items-center rounded-2xl text-2xl"
            :class="categoryMeta[cat].colorClass"
            aria-hidden="true"
          >
            {{ categoryMeta[cat].icon }}
          </div>
          <h3 class="mt-5 text-lg font-semibold group-hover:text-sts-coral transition-colors">
            {{ copy.categories[cat].name }}
          </h3>
          <p class="mt-2 text-sm leading-6 text-sts-muted">
            {{ copy.categories[cat].description }}
          </p>
        </NuxtLink>
      </div>

      <div class="mt-8 text-center">
        <NuxtLink
          to="/tours"
          class="inline-flex rounded-full border border-sts-ocean px-7 py-3 font-semibold text-sts-ocean transition-transform hover:-translate-y-0.5 hover:bg-sts-ocean hover:text-white"
        >
          {{ copy.nav.tours }} →
        </NuxtLink>
      </div>
    </section>

    <!-- ── HOW IT WORKS ───────────────────────────────────────────────── -->
    <section
      :ref="(n) => (howReveal.el.value = n as HTMLElement | null)"
      class="reveal-transition bg-sts-sand-soft px-6 py-20 lg:px-10"
      :class="{ 'reveal-hidden': !howReveal.visible.value }"
      aria-labelledby="how-heading"
    >
      <div class="mx-auto max-w-7xl">
        <h2
          id="how-heading"
          class="font-display text-3xl font-semibold tracking-tight text-sts-ocean sm:text-4xl"
        >
          {{ copy.howHeading }}
        </h2>
        <div class="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <article
            v-for="(step, index) in copy.howSteps"
            :key="step.title"
            class="hover-lift rounded-[var(--sts-radius-card)] border border-white bg-white/80 p-6 shadow-sm backdrop-blur"
            :style="{ transitionDelay: howReveal.visible.value ? `${index * 80}ms` : '0ms' }"
          >
            <div
              class="grid size-10 place-items-center rounded-xl bg-sts-ocean font-display text-sm font-bold text-sts-sand"
              aria-hidden="true"
            >
              {{ index + 1 }}
            </div>
            <h3 class="mt-5 font-semibold text-sts-ocean">{{ step.title }}</h3>
            <p class="mt-2 text-sm leading-6 text-sts-muted">{{ step.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <!-- ── FREE CANCELLATION ─────────────────────────────────────────── -->
    <section
      :ref="(n) => (cancellationReveal.el.value = n as HTMLElement | null)"
      class="reveal-transition bg-sts-ocean px-6 py-16 text-white lg:px-10"
      :class="{ 'reveal-hidden': !cancellationReveal.visible.value }"
    >
      <div class="mx-auto max-w-3xl text-center">
        <p class="text-4xl" aria-hidden="true">✅</p>
        <h2 class="mt-4 font-display text-2xl font-semibold sm:text-3xl">
          {{ copy.cancellationHeading }}
        </h2>
        <p class="mt-4 leading-7 text-white/75">{{ copy.cancellationBody }}</p>
        <div class="mt-8 flex flex-wrap justify-center gap-4">
          <NuxtLink
            to="/tours"
            class="rounded-full bg-sts-coral px-7 py-3.5 font-semibold text-white shadow-lg transition-transform hover:-translate-y-0.5"
          >
            {{ copy.hero.cta }}
          </NuxtLink>
          <a
            :href="whatsappUrl"
            target="_blank"
            rel="noopener"
            class="rounded-full border border-white/30 px-7 py-3.5 font-semibold text-white transition-transform hover:-translate-y-0.5"
          >
            {{ copy.hero.whatsapp }}
          </a>
        </div>
      </div>
    </section>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
