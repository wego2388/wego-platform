<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SdcLocale, siteCopy } from "../content/locales";

const locale = ref<SdcLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "من نحن · Sharm Divers Club" : "About · Sharm Divers Club",
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
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas px-6 py-8 text-sdc-ink lg:px-10">
    <header class="mx-auto flex max-w-6xl items-center justify-between">
      <NuxtLink to="/" class="font-semibold text-sdc-deep-bright">{{ direction === "rtl" ? "→" : "←" }} Sharm Divers Club</NuxtLink>
      <button type="button" class="rounded-full border border-sdc-border bg-white px-4 py-2 text-sm font-semibold" @click="toggleLocale">
        {{ copy.languageName }}
      </button>
    </header>

    <section class="mx-auto mt-14 max-w-3xl text-center">
      <h1 class="font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.about.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.about.body }}</p>
    </section>

    <section class="mx-auto mt-12 max-w-4xl rounded-[1.75rem] border border-sdc-border bg-white p-8">
      <h2 class="text-xl font-semibold">{{ copy.about.factsHeading }}</h2>
      <dl class="mt-6 grid gap-5 sm:grid-cols-2">
        <div v-for="fact in copy.about.facts" :key="fact.label">
          <dt class="text-xs font-bold tracking-[0.12em] text-sdc-deep-bright uppercase">{{ fact.label }}</dt>
          <dd class="mt-1 font-semibold">{{ fact.value }}</dd>
        </div>
      </dl>
    </section>

    <section class="mx-auto mt-8 mb-16 max-w-4xl rounded-[1.75rem] border border-sdc-border bg-sdc-turquoise-soft p-8">
      <h2 class="text-xl font-semibold text-sdc-deep">{{ copy.about.languagesHeading }}</h2>
      <p class="mt-3 leading-7 text-sdc-deep">{{ copy.about.languagesBody }}</p>
    </section>
  </main>
</template>
