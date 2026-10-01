<script setup lang="ts">
import SiteSubHeader from "../components/SiteSubHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
const { locale, copy, direction, toggleLocale } = useSiteLocale();
useHead(() => ({ title: locale.value === "ar" ? "سياسة الخصوصية · Sharm To Go" : "Privacy · Sharm To Go", htmlAttrs: { dir: direction.value, lang: locale.value } }));
</script>
<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <article class="mx-auto max-w-4xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
      <h1 class="font-display mt-14 text-4xl font-semibold">{{ copy.legalPages.privacy.heading }}</h1>
      <p class="mt-3 text-sm text-sharm-muted">{{ copy.legalPages.updated }}</p>
      <section v-for="section in copy.legalPages.privacy.sections" :key="section.title" class="mt-9">
        <h2 class="text-xl font-bold">{{ section.title }}</h2><p class="mt-3 leading-8 text-sharm-muted">{{ section.body }}</p>
      </section>
      <NuxtLink to="/" class="my-12 inline-flex font-bold text-sharm-sea underline">{{ copy.legalPages.back }}</NuxtLink>
    </article>
    <SiteFooter :locale="locale" />
  </main>
</template>
