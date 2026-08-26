<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SdcLocale, siteCopy, whatsappUrl } from "../content/locales";

const locale = ref<SdcLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "Sharm Divers Club · ثقة البحر الأحمر" : "Sharm Divers Club · Red Sea confidence, personally guided",
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
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
    <div class="sdc-hero border-b border-black/10 text-white">
      <header class="mx-auto flex max-w-7xl items-center justify-between gap-6 px-6 py-6 lg:px-10">
        <NuxtLink to="/" class="flex items-center gap-3 font-semibold" aria-label="Sharm Divers Club home">
          <span class="grid size-11 place-items-center rounded-2xl bg-sdc-sand font-display text-lg font-black text-sdc-deep">SDC</span>
          <span>Sharm Divers Club</span>
        </NuxtLink>
        <nav class="hidden items-center gap-7 text-sm font-semibold md:flex" aria-label="Primary navigation">
          <NuxtLink to="/discover" class="hover:text-sdc-sand">{{ copy.nav.discover }}</NuxtLink>
          <NuxtLink to="/about" class="hover:text-sdc-sand">{{ copy.nav.about }}</NuxtLink>
          <NuxtLink to="/contact" class="hover:text-sdc-sand">{{ copy.nav.contact }}</NuxtLink>
        </nav>
        <button
          type="button"
          class="rounded-full border border-white/25 bg-white/10 px-4 py-2 text-sm font-semibold backdrop-blur"
          @click="toggleLocale"
        >
          {{ copy.languageName }}
        </button>
      </header>

      <section class="mx-auto grid max-w-7xl gap-12 px-6 pt-14 pb-20 lg:grid-cols-[1.15fr_0.85fr] lg:px-10 lg:pt-20">
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
            <NuxtLink to="/discover" class="rounded-full bg-sdc-sand px-6 py-3 font-semibold text-sdc-deep shadow-lg">
              {{ copy.hero.browse }}
            </NuxtLink>
            <a :href="whatsappUrl" target="_blank" rel="noopener" class="rounded-full border border-white/30 bg-white/5 px-6 py-3 font-semibold backdrop-blur">
              {{ copy.hero.whatsapp }}
            </a>
          </div>
        </div>

        <div class="self-end rounded-[2rem] border border-white/15 bg-white/10 p-6 shadow-2xl backdrop-blur">
          <ul class="grid gap-3">
            <li v-for="point in copy.trustStrip" :key="point" class="rounded-2xl bg-white/10 p-4 text-sm font-semibold">
              {{ point }}
            </li>
          </ul>
        </div>
      </section>
    </div>

    <section class="mx-auto max-w-7xl px-6 py-20 lg:px-10">
      <div class="max-w-2xl">
        <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.categoriesHeading }}</h2>
        <p class="mt-4 leading-7 text-sdc-muted">{{ copy.categoriesBody }}</p>
      </div>
      <div class="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <article v-for="(category, index) in copy.categories" :key="category.title" class="rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
          <div class="grid size-12 place-items-center rounded-2xl" :class="index % 2 === 0 ? 'bg-sdc-turquoise-soft text-sdc-deep' : 'bg-sdc-sand-soft text-sdc-deep'">
            {{ String(index + 1).padStart(2, "0") }}
          </div>
          <p class="mt-6 text-xs font-bold tracking-[0.15em] text-sdc-deep-bright uppercase">{{ category.eyebrow }}</p>
          <h3 class="mt-2 text-xl font-semibold">{{ category.title }}</h3>
          <p class="mt-3 text-sm leading-6 text-sdc-muted">{{ category.description }}</p>
        </article>
      </div>
    </section>

    <section class="bg-sdc-deep px-6 py-20 text-white lg:px-10">
      <div class="mx-auto max-w-7xl">
        <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.personasHeading }}</h2>
        <p class="mt-3 max-w-2xl text-white/75">{{ copy.personasBody }}</p>
        <div class="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          <article v-for="persona in copy.personas" :key="persona.name" class="rounded-[1.75rem] border border-white/15 bg-white/8 p-6">
            <h3 class="text-xl font-semibold text-sdc-sand">{{ persona.name }}</h3>
            <p class="mt-3 leading-7 text-white/80">{{ persona.body }}</p>
          </article>
        </div>
      </div>
    </section>

    <section id="how" class="mx-auto max-w-7xl px-6 py-20 lg:px-10">
      <h2 class="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{{ copy.how.heading }}</h2>
      <p class="mt-4 max-w-2xl leading-7 text-sdc-muted">{{ copy.how.body }}</p>
      <div class="mt-10 grid gap-5 md:grid-cols-3">
        <article v-for="step in copy.how.steps" :key="step.title" class="rounded-[1.75rem] border border-sdc-border bg-white p-6">
          <h3 class="text-xl font-semibold">{{ step.title }}</h3>
          <p class="mt-3 leading-7 text-sdc-muted">{{ step.body }}</p>
        </article>
      </div>
      <p class="mt-8 rounded-2xl bg-sdc-turquoise-soft p-5 text-sm leading-6 text-sdc-deep">{{ copy.bookingNotice }}</p>
      <a :href="whatsappUrl" target="_blank" rel="noopener" class="mt-6 inline-flex rounded-full bg-sdc-deep px-6 py-3 font-semibold text-white">
        {{ copy.hero.whatsapp }}
      </a>
    </section>

    <footer class="border-t border-sdc-border px-6 py-8 text-center text-sm text-sdc-muted lg:px-10">
      {{ copy.footer }}
    </footer>
  </main>
</template>
