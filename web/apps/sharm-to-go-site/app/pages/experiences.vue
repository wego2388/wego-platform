<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";

const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "التجارب · Sharm To Go" : "Experiences · Sharm To Go",
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
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <header class="mx-auto flex max-w-6xl items-center justify-between">
      <NuxtLink to="/" class="font-semibold text-sharm-sea">← Sharm To Go</NuxtLink>
      <button type="button" class="rounded-full border border-sharm-sea/20 bg-white px-4 py-2 text-sm font-semibold" @click="toggleLocale">
        {{ copy.languageName }}
      </button>
    </header>
    <section class="mx-auto mt-20 max-w-3xl rounded-[2rem] border border-black/5 bg-white p-8 text-center shadow-sm sm:p-12">
      <span class="inline-flex rounded-full bg-sharm-lagoon px-4 py-2 text-xs font-bold tracking-[0.13em] text-sharm-sea uppercase">
        {{ copy.preview }}
      </span>
      <h1 class="mt-6 text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.catalog.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sharm-muted">{{ copy.catalog.body }}</p>
      <div class="mt-8 flex flex-wrap justify-center gap-3">
        <NuxtLink to="/booking-preview" class="inline-flex rounded-full bg-sharm-sea px-6 py-3 font-semibold text-white">
          {{ copy.catalog.previewBooking }}
        </NuxtLink>
        <NuxtLink to="/design-system" class="inline-flex rounded-full border border-sharm-border bg-white px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.catalog.viewSystem }}
        </NuxtLink>
        <NuxtLink to="/" class="inline-flex rounded-full border border-sharm-border bg-white px-6 py-3 font-semibold text-sharm-sea">
          {{ copy.catalog.back }}
        </NuxtLink>
      </div>
    </section>
  </main>
</template>
