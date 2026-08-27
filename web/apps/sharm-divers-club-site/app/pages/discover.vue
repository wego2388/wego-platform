<script setup lang="ts">
import { computed, ref } from "vue";
import OfferingCard from "../components/OfferingCard.vue";
import SiteFooter from "../components/SiteFooter.vue";
import SiteHeader from "../components/SiteHeader.vue";
import { useScrollReveal } from "../composables/useScrollReveal";
import { useSiteLocale } from "../composables/useSiteLocale";
import { directionFor, siteCopy, whatsappUrl } from "../content/locales";
import { offerings } from "../content/offerings";

const locale = useSiteLocale();
const copy = computed(() => siteCopy[locale.value]);
const direction = computed(() => directionFor(locale.value));
const activeFilter = ref<string | null>(null);

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

const filteredCategories = computed(() =>
  activeFilter.value ? copy.value.categories.filter(category => category.id === activeFilter.value) : copy.value.categories,
);

const filteredOfferings = computed(() =>
  activeFilter.value ? offerings.filter(offering => offering.categoryId === activeFilter.value) : offerings,
);

const gridReveal = useScrollReveal();
const offeringsReveal = useScrollReveal();
</script>

<template>
  <main :dir="direction" :lang="locale" class="min-h-screen bg-sdc-canvas text-sdc-ink">
    <SiteHeader
      variant="solid"
      :locale="locale"
      :direction="direction"
      :home-label="copy.nav.home"
      :discover-label="copy.nav.discover"
      :about-label="copy.nav.about"
      :contact-label="copy.nav.contact"
      :language-name="copy.languageName"
      :whatsapp-label="copy.whatsappFab"
      @toggle-locale="toggleLocale"
    />

    <section class="px-6 pt-10 pb-4 lg:px-10">
      <div class="mx-auto max-w-3xl rounded-[2rem] border border-sdc-border bg-white p-8 text-center shadow-sm sm:p-12">
        <span class="inline-flex rounded-full bg-sdc-sand-soft px-4 py-2 text-xs font-bold tracking-[0.13em] text-sdc-deep uppercase">
          {{ copy.discover.pricingNotice }}
        </span>
        <h1 class="mt-6 font-display text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.discover.heading }}</h1>
        <p class="mx-auto mt-5 max-w-2xl leading-8 text-sdc-muted">{{ copy.discover.body }}</p>
        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <a :href="whatsappUrl" target="_blank" rel="noopener" class="inline-flex rounded-full bg-sdc-deep px-6 py-3 font-semibold text-white transition-transform hover:-translate-y-0.5">
            {{ copy.discover.whatsapp }}
          </a>
          <NuxtLink to="/offering-preview" class="inline-flex rounded-full border border-sdc-border bg-white px-6 py-3 font-semibold text-sdc-deep-bright">
            {{ copy.discover.offeringPreview }}
          </NuxtLink>
          <NuxtLink to="/design-system" class="inline-flex rounded-full border border-sdc-border bg-white px-6 py-3 font-semibold text-sdc-deep-bright">
            {{ copy.discover.viewSystem }}
          </NuxtLink>
        </div>
      </div>
    </section>

    <section
      :ref="(node) => (gridReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto max-w-6xl px-6 pt-8 lg:px-10"
      :class="{ 'reveal-hidden': !gridReveal.visible.value }"
    >
      <div class="flex flex-wrap justify-center gap-2" role="group" aria-label="Filter categories">
        <button
          type="button"
          class="min-h-11 rounded-full border px-4 text-sm font-semibold transition-colors"
          :class="activeFilter === null ? 'border-sdc-deep bg-sdc-deep text-white' : 'border-sdc-border bg-white text-sdc-deep-bright'"
          :aria-pressed="activeFilter === null"
          @click="activeFilter = null"
        >
          {{ copy.discover.filterAll }}
        </button>
        <button
          v-for="category in copy.categories"
          :key="category.id"
          type="button"
          class="min-h-11 rounded-full border px-4 text-sm font-semibold transition-colors"
          :class="activeFilter === category.id ? 'border-sdc-deep bg-sdc-deep text-white' : 'border-sdc-border bg-white text-sdc-deep-bright'"
          :aria-pressed="activeFilter === category.id"
          @click="activeFilter = category.id"
        >
          {{ category.title }}
        </button>
      </div>

      <div class="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <article
          v-for="(category, index) in filteredCategories"
          :key="category.id"
          class="hover-lift rounded-[1.75rem] border border-sdc-border bg-white p-6 shadow-sm"
          :style="{ transitionDelay: gridReveal.visible.value ? `${index * 60}ms` : '0ms' }"
        >
          <p class="text-xs font-bold tracking-[0.15em] text-sdc-deep-bright uppercase">{{ category.eyebrow }}</p>
          <h2 class="mt-2 text-xl font-semibold">{{ category.title }}</h2>
          <p class="mt-3 text-sm leading-6 text-sdc-muted">{{ category.description }}</p>
        </article>
      </div>
    </section>

    <section
      :ref="(node) => (offeringsReveal.el.value = node as HTMLElement | null)"
      class="reveal-transition mx-auto max-w-6xl px-6 py-16 lg:px-10"
      :class="{ 'reveal-hidden': !offeringsReveal.visible.value }"
    >
      <div class="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <OfferingCard
          v-for="offering in filteredOfferings"
          :key="offering.code"
          :offering="offering"
          :locale="locale"
          :pricing-notice-label="copy.discover.pricingNotice"
          :ask-label="copy.discover.whatsapp"
        />
      </div>
    </section>

    <SiteFooter :locale="locale" :copy="copy.footer" />
  </main>
</template>
