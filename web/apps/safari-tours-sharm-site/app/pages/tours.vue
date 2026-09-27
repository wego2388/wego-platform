<script setup lang="ts">
import { computed } from "vue";
import SiteHeader from "../components/SiteHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl, categoryMeta } from "../content/locales";
import type { StsLocale, TourCategory } from "../content/locales";

const ALL_LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];
const CATEGORIES: TourCategory[] = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"];

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));

useHead(() => ({
  title: copy.value.nav.tours + " — Safari Tours Sharm",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: copy.value.hero.body }],
}));
</script>

<template>
  <div :dir="direction" :lang="locale" class="min-h-screen bg-sts-canvas text-sts-ink">
    <SiteHeader
      :locale="locale"
      :direction="direction"
      :nav="copy.nav"
      :whatsapp-label="copy.whatsappFab"
      :current-locales="ALL_LOCALES"
      @set-locale="(l) => (locale = l)"
    />

    <main id="main-content" tabindex="-1">
      <!-- Page header -->
      <div class="bg-sts-ocean px-6 py-16 text-white lg:px-10">
        <div class="mx-auto max-w-7xl">
          <h1 class="font-display text-3xl font-semibold sm:text-4xl">
            {{ copy.categoriesHeading }}
          </h1>
          <p class="mt-3 max-w-xl text-white/75">{{ copy.categoriesBody }}</p>
        </div>
      </div>

      <!-- Category grid -->
      <section class="mx-auto max-w-7xl px-6 py-14 lg:px-10">
        <div class="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
          <NuxtLink
            v-for="cat in CATEGORIES"
            :key="cat"
            :to="`/category/${categoryMeta[cat].slug}`"
            class="hover-lift group flex flex-col rounded-[var(--sts-radius-card)] border border-sts-border bg-sts-surface p-7 shadow-sm"
          >
            <div
              class="grid size-14 place-items-center rounded-2xl text-3xl"
              :class="categoryMeta[cat].colorClass"
              aria-hidden="true"
            >
              {{ categoryMeta[cat].icon }}
            </div>
            <h2 class="mt-5 text-xl font-semibold group-hover:text-sts-coral transition-colors">
              {{ copy.categories[cat].name }}
            </h2>
            <p class="mt-2 flex-1 text-sm leading-6 text-sts-muted">
              {{ copy.categories[cat].description }}
            </p>
            <span
              class="mt-5 inline-flex items-center gap-1 text-sm font-semibold text-sts-ocean-bright group-hover:text-sts-coral transition-colors"
            >
              {{ copy.hero.cta }} →
            </span>
          </NuxtLink>
        </div>

        <!-- WhatsApp CTA -->
        <div
          class="mt-14 rounded-2xl bg-sts-sand-soft p-8 text-center"
        >
          <p class="text-lg font-semibold text-sts-ocean">
            {{ copy.hero.whatsapp }}
          </p>
          <a
            :href="whatsappUrl"
            target="_blank"
            rel="noopener"
            class="mt-4 inline-flex rounded-full bg-sts-ocean px-7 py-3 font-semibold text-white transition-transform hover:-translate-y-0.5"
          >
            {{ copy.whatsappFab }}
          </a>
        </div>
      </section>
    </main>

    <SiteFooter
      :tagline="copy.footerTagline"
      :links="copy.footerLinks"
      :rights="copy.footerRights"
    />
  </div>
</template>
