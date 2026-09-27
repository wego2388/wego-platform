<script setup lang="ts">
import { computed, ref } from "vue";
import SiteSubHeader from "../components/SiteSubHeader.vue";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";

const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "من نحن · Sharm To Go" : "About · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

function toggleLocale() {
  locale.value = locale.value === "en" ? "ar" : "en";
}
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink">
    <div class="sharm-hero border-b border-black/5 px-6 py-8 lg:px-10">
      <div class="mx-auto max-w-6xl">
        <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
        <section class="max-w-4xl py-16 sm:py-24">
          <p class="text-sm font-bold tracking-[0.16em] text-sharm-sea uppercase">{{ copy.aboutPage.eyebrow }}</p>
          <h1 class="font-display mt-4 text-4xl font-semibold tracking-tight sm:text-6xl">{{ copy.aboutPage.heading }}</h1>
          <p class="mt-7 max-w-3xl text-xl leading-9 text-sharm-muted">{{ copy.aboutPage.lead }}</p>
        </section>
      </div>
    </div>

    <section class="mx-auto grid max-w-6xl gap-10 px-6 py-16 lg:grid-cols-[1.1fr_0.9fr] lg:px-10 lg:py-24">
      <div class="space-y-6 text-lg leading-8 text-sharm-muted">
        <p v-for="paragraph in copy.aboutPage.story" :key="paragraph">{{ paragraph }}</p>
        <p class="font-display text-2xl font-semibold text-sharm-sea">Sharm To Go. Where you must go.</p>
      </div>
      <aside class="rounded-[2rem] bg-sharm-sea p-8 text-white shadow-xl shadow-sharm-sea/15">
        <h2 class="font-display text-2xl font-semibold">{{ copy.aboutPage.promiseHeading }}</h2>
        <ul class="mt-6 space-y-4">
          <li v-for="promise in copy.aboutPage.promises" :key="promise" class="flex gap-3">
            <span class="grid size-6 shrink-0 place-items-center rounded-full bg-sharm-sun font-bold text-sharm-ink">✓</span>
            <span>{{ promise }}</span>
          </li>
        </ul>
        <NuxtLink to="/contact" class="mt-8 inline-flex rounded-full bg-sharm-sun px-6 py-3 font-bold text-sharm-ink">
          {{ copy.aboutPage.cta }}
        </NuxtLink>
      </aside>
    </section>
  </main>
</template>
