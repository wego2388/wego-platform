<script setup lang="ts">
import { computed, ref } from "vue";
import { directionFor, type SdcLocale, siteCopy, whatsappUrl } from "../content/locales";

const locale = ref<SdcLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: locale.value === "ar" ? "استكشف · Sharm Divers Club" : "Discover · Sharm Divers Club",
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

    <section class="mx-auto mt-14 max-w-3xl rounded-[2rem] border border-sdc-border bg-white p-8 text-center shadow-sm sm:p-12">
      <span class="inline-flex rounded-full bg-sdc-sand-soft px-4 py-2 text-xs font-bold tracking-[0.13em] text-sdc-deep uppercase">
        {{ copy.discover.pricingNotice }}
      </span>
      <h1 class="mt-6 font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.discover.heading }}</h1>
      <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.discover.body }}</p>
      <div class="mt-8 flex flex-wrap justify-center gap-3">
        <a :href="whatsappUrl" target="_blank" rel="noopener" class="inline-flex rounded-full bg-sdc-deep px-6 py-3 font-semibold text-white">
          {{ copy.discover.whatsapp }}
        </a>
        <NuxtLink to="/offering-preview" class="inline-flex rounded-full border border-sdc-border bg-white px-6 py-3 font-semibold text-sdc-deep-bright">
          {{ copy.discover.offeringPreview }}
        </NuxtLink>
        <NuxtLink to="/design-system" class="inline-flex rounded-full border border-sdc-border bg-white px-6 py-3 font-semibold text-sdc-deep-bright">
          {{ copy.discover.viewSystem }}
        </NuxtLink>
        <NuxtLink to="/" class="inline-flex rounded-full border border-sdc-border bg-white px-6 py-3 font-semibold text-sdc-deep-bright">
          {{ copy.discover.back }}
        </NuxtLink>
      </div>
    </section>

    <section class="mx-auto mt-12 grid max-w-6xl gap-5 pb-16 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="category in copy.categories" :key="category.title" class="rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm">
        <p class="text-xs font-bold tracking-[0.15em] text-sdc-deep-bright uppercase">{{ category.eyebrow }}</p>
        <h2 class="mt-2 text-xl font-semibold">{{ category.title }}</h2>
        <p class="mt-3 text-sm leading-6 text-sdc-muted">{{ category.description }}</p>
        <p class="mt-4 rounded-xl bg-sdc-turquoise-soft px-3 py-2 text-xs font-semibold text-sdc-deep">{{ copy.discover.pricingNotice }}</p>
      </article>
    </section>
  </main>
</template>
