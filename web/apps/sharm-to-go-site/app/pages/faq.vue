<script setup lang="ts">
import { computed, ref } from "vue";
import SiteSubHeader from "../components/SiteSubHeader.vue";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";

const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));
useHead(() => ({ title: locale.value === "ar" ? "الأسئلة الشائعة · Sharm To Go" : "FAQ · Sharm To Go", htmlAttrs: { dir: direction.value, lang: locale.value } }));
function toggleLocale() { locale.value = locale.value === "en" ? "ar" : "en"; }
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <div class="mx-auto max-w-4xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
      <header class="py-14">
        <h1 class="font-display text-4xl font-semibold sm:text-5xl">{{ copy.faqPage.heading }}</h1>
        <p class="mt-5 max-w-2xl text-lg leading-8 text-sharm-muted">{{ copy.faqPage.body }}</p>
      </header>
      <section>
        <h2 class="font-display text-2xl font-semibold">{{ copy.faqPage.knownHeading }}</h2>
        <div class="mt-6 space-y-3">
          <details v-for="item in copy.faqPage.items" :key="item.q" class="group rounded-2xl border border-sharm-border bg-sharm-surface p-5">
            <summary class="cursor-pointer list-none font-bold">{{ item.q }}</summary>
            <p class="mt-4 leading-7 text-sharm-muted">{{ item.a }}</p>
          </details>
        </div>
      </section>
      <section class="my-12 rounded-[2rem] bg-sharm-lagoon p-7 text-sharm-sea">
        <h2 class="font-display text-2xl font-semibold">{{ copy.faqPage.unknownHeading }}</h2>
        <p class="mt-3 leading-7">{{ copy.faqPage.unknownBody }}</p>
        <ul class="mt-5 list-disc space-y-2 ps-5">
          <li v-for="item in copy.faqPage.unknownItems" :key="item">{{ item }}</li>
        </ul>
      </section>
    </div>
  </main>
</template>
