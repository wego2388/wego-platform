<script setup lang="ts">
import { computed } from "vue";
import { useSiteLocale } from "./composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "./content/locales";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: copy.value.notFound.title + " — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "robots", content: "noindex" }],
}));
</script>

<template>
  <main
    id="main-content"
    tabindex="-1"
    :dir="direction"
    :lang="locale"
    class="flex min-h-screen flex-col items-center justify-center gap-6 bg-sts-canvas px-6 py-20 text-center text-sts-ink"
  >
    <span class="text-6xl" aria-hidden="true">🏜️</span>
    <h1 class="font-display text-3xl font-semibold">{{ copy.notFound.title }}</h1>
    <p class="max-w-sm text-sts-muted">{{ copy.notFound.body }}</p>
    <div class="flex flex-wrap justify-center gap-3">
      <NuxtLink
        to="/"
        class="rounded-full bg-sts-ocean px-6 py-3 font-semibold text-white transition-transform hover:-translate-y-0.5"
      >
        {{ copy.notFound.cta }}
      </NuxtLink>
      <a
        :href="whatsappUrl"
        target="_blank"
        rel="noopener"
        class="rounded-full border border-sts-border bg-sts-surface px-6 py-3 font-semibold transition-transform hover:-translate-y-0.5"
      >
        WhatsApp
      </a>
    </div>
  </main>
</template>
