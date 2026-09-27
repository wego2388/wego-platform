<script setup lang="ts">
import { computed, ref } from "vue";
import SiteSubHeader from "../components/SiteSubHeader.vue";
import { directionFor, type SharmLocale, siteCopy } from "../content/locales";
const locale = ref<SharmLocale>("en");
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));
useHead(() => ({ title: locale.value === "ar" ? "شروط الاستخدام · Sharm To Go" : "Terms · Sharm To Go", htmlAttrs: { dir: direction.value, lang: locale.value } }));
function toggleLocale() { locale.value = locale.value === "en" ? "ar" : "en"; }
</script>
<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <article class="mx-auto max-w-4xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
      <h1 class="font-display mt-14 text-4xl font-semibold">{{ copy.legalPages.terms.heading }}</h1>
      <p class="mt-3 text-sm text-sharm-muted">{{ copy.legalPages.updated }}</p>
      <section v-for="section in copy.legalPages.terms.sections" :key="section.title" class="mt-9">
        <h2 class="text-xl font-bold">{{ section.title }}</h2><p class="mt-3 leading-8 text-sharm-muted">{{ section.body }}</p>
      </section>
      <NuxtLink to="/" class="my-12 inline-flex font-bold text-sharm-sea underline">{{ copy.legalPages.back }}</NuxtLink>
    </article>
  </main>
</template>
