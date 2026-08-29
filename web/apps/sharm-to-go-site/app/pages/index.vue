<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";

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
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink">
    <div class="sharm-hero border-b border-black/5">
      <header class="mx-auto flex max-w-7xl items-center justify-between gap-6 px-6 py-6 lg:px-10">
        <NuxtLink to="/" class="flex items-center gap-3 font-semibold" aria-label="Sharm To Go home">
          <span class="grid size-11 place-items-center rounded-2xl bg-sharm-sea text-lg font-black text-white">S</span>
          <span>Sharm To Go</span>
        </NuxtLink>
        <nav class="hidden items-center gap-7 text-sm font-semibold md:flex" aria-label="Primary navigation">
          <NuxtLink to="/experiences" class="hover:text-sharm-sea-bright">{{ copy.nav.experiences }}</NuxtLink>
          <a href="#how" class="hover:text-sharm-sea-bright">{{ copy.nav.howItWorks }}</a>
          <a href="#trust" class="hover:text-sharm-sea-bright">{{ copy.nav.trust }}</a>
        </nav>
        <button
          type="button"
          class="rounded-full border border-sharm-sea/20 bg-white/80 px-4 py-2 text-sm font-semibold text-sharm-sea"
          @click="toggleLocale"
        >
          {{ copy.languageName }}
        </button>
      </header>

      <section class="mx-auto grid max-w-7xl gap-12 px-6 pt-16 pb-20 lg:grid-cols-[1.15fr_0.85fr] lg:px-10 lg:pt-24">
        <div>
          <span class="inline-flex rounded-full bg-white/75 px-4 py-2 text-xs font-bold tracking-[0.13em] text-sharm-sea uppercase">
            {{ copy.preview }}
          </span>
          <p class="mt-8 text-sm font-bold tracking-[0.18em] text-sharm-sea uppercase">{{ copy.hero.eyebrow }}</p>
          <h1 class="mt-4 max-w-3xl text-4xl leading-tight font-semibold tracking-[-0.035em] sm:text-6xl">
            {{ copy.hero.title }}
          </h1>
          <p class="mt-6 max-w-2xl text-lg leading-8 text-sharm-muted">{{ copy.hero.body }}</p>
          <div class="mt-9 flex flex-wrap gap-3">
            <NuxtLink to="/experiences" class="rounded-full bg-sharm-sea px-6 py-3 font-semibold text-white shadow-lg shadow-sharm-sea/15">
              {{ copy.hero.browse }}
            </NuxtLink>
            <a href="#how" class="rounded-full border border-sharm-sea/20 bg-white px-6 py-3 font-semibold text-sharm-sea">
              {{ copy.hero.plan }}
            </a>
          </div>
        </div>

        <div class="self-end rounded-[2rem] border border-white/80 bg-white/90 p-5 shadow-2xl shadow-sharm-sea/10 backdrop-blur">
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

    <section class="mx-auto max-w-7xl px-6 py-20 lg:px-10">
      <div class="max-w-2xl">
        <h2 class="text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.categoriesHeading }}</h2>
        <p class="mt-4 leading-7 text-sharm-muted">{{ copy.categoriesBody }}</p>
      </div>
      <div class="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
        <article v-for="(category, index) in copy.categories" :key="category.title" class="rounded-[1.75rem] border border-black/5 bg-white p-6 shadow-sm">
          <div class="grid size-12 place-items-center rounded-2xl" :class="index % 2 === 0 ? 'bg-sharm-lagoon text-sharm-sea' : 'bg-sharm-sand text-sharm-ink'">
            {{ String(index + 1).padStart(2, "0") }}
          </div>
          <p class="mt-6 text-xs font-bold tracking-[0.15em] text-sharm-sea uppercase">{{ category.eyebrow }}</p>
          <h3 class="mt-2 text-xl font-semibold">{{ category.title }}</h3>
          <p class="mt-3 text-sm leading-6 text-sharm-muted">{{ category.description }}</p>
        </article>
      </div>
    </section>

    <section id="how" class="bg-sharm-sea px-6 py-20 text-white lg:px-10">
      <div class="mx-auto max-w-7xl">
        <h2 class="text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.how.heading }}</h2>
        <div class="mt-10 grid gap-5 md:grid-cols-3">
          <article v-for="step in copy.how.steps" :key="step.title" class="rounded-[1.75rem] border border-white/15 bg-white/8 p-6">
            <h3 class="text-xl font-semibold">{{ step.title }}</h3>
            <p class="mt-3 leading-7 text-white/75">{{ step.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <section id="trust" class="mx-auto grid max-w-7xl gap-10 px-6 py-20 lg:grid-cols-2 lg:px-10">
      <div>
        <h2 class="text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.trust.heading }}</h2>
        <p class="mt-5 max-w-xl leading-7 text-sharm-muted">{{ copy.trust.body }}</p>
      </div>
      <ul class="grid gap-3 sm:grid-cols-2">
        <li v-for="point in copy.trust.points" :key="point" class="rounded-2xl border border-black/5 bg-white p-5 font-semibold">
          <span class="text-sharm-sea">✓</span> {{ point }}
        </li>
      </ul>
    </section>

    <footer class="border-t border-black/5 px-6 py-8 text-center text-sm text-sharm-muted lg:px-10">
      {{ copy.footer }}
    </footer>
  </main>
</template>
