<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import MockPhoto from "../components/MockPhoto.vue";
import SiteSubHeader from "../components/SiteSubHeader.vue";
import SiteFooter from "../components/SiteFooter.vue";
import { accentForIndex, toneForIndex } from "../content/categoryAccents";
import { finderCategories, type FinderCategoryCopy } from "../content/locales";
import { vReveal } from "../composables/useScrollReveal";
import { useSiteLocale } from "../composables/useSiteLocale";
import { listPublicCategories, type PublicCategory } from "../composables/usePublicCatalog";

const { locale, copy, direction, toggleLocale } = useSiteLocale();

useHead(() => ({
  title: locale.value === "ar" ? "مرشد الرحلات · Sharm To Go" : "Trip finder · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
  meta: [{ name: "description", content: copy.value.finderPage.body }],
}));

const categories = ref<PublicCategory[]>([]);
const state = ref<"loading" | "loaded" | "error">("loading");

onMounted(async () => {
  try {
    categories.value = await listPublicCategories();
    state.value = "loaded";
  } catch {
    state.value = "error";
  }
});

// Falls back to the live category's own name/description for a code this
// app doesn't have a written vibe-copy entry for yet — see finderCategories'
// own doc comment in content/locales.ts. Never crashes on a category this
// page wasn't written for.
function vibeFor(category: PublicCategory): FinderCategoryCopy {
  const written = finderCategories[locale.value][category.code];
  if (written) return written;
  return { eyebrow: "", title: category.name[locale.value], description: category.description?.[locale.value] ?? "" };
}

const cards = computed(() => categories.value.map((category) => ({ category, vibe: vibeFor(category) })));
</script>

<template>
  <main id="main-content" :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas text-sharm-ink">
    <div class="sharm-hero border-b border-black/5 px-6 py-8 lg:px-10">
      <div class="mx-auto max-w-6xl">
        <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
      </div>

      <section class="mx-auto mt-10 max-w-6xl">
        <span class="inline-flex rounded-full bg-sharm-surface/80 px-4 py-2 text-xs font-bold tracking-[0.13em] text-sharm-sea uppercase shadow-sm">
          {{ copy.finderPage.eyebrow }}
        </span>
        <h1 class="font-display mt-6 text-3xl font-semibold tracking-tight sm:text-5xl">{{ copy.finderPage.heading }}</h1>
        <p class="mt-5 max-w-2xl leading-8 text-sharm-muted">{{ copy.finderPage.body }}</p>
      </section>
    </div>

    <section class="mx-auto max-w-6xl px-6 py-10 lg:px-10">
      <p v-if="state === 'loading'" class="text-sharm-muted">{{ copy.finderPage.loading }}</p>

      <div v-else-if="state === 'error'" role="alert" class="rounded-2xl border border-sharm-danger/30 bg-sharm-danger-soft p-6 text-sharm-danger">
        {{ copy.finderPage.loadError }}
      </div>

      <div v-else class="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <NuxtLink
          v-for="({ category, vibe }, index) in cards"
          :key="category.id"
          v-reveal
          :to="{ path: '/experiences', query: { category: category.id } }"
          class="sharm-reveal sharm-card-lift overflow-hidden rounded-[1.75rem] border bg-sharm-surface shadow-sm"
          :class="accentForIndex(index).ring"
          :style="{ transitionDelay: `${index * 70}ms` }"
        >
          <MockPhoto :tone="toneForIndex(index)" :label="vibe.title" class="aspect-[4/3] w-full" />
          <div class="p-6">
            <p v-if="vibe.eyebrow" class="text-xs font-bold tracking-[0.15em] uppercase" :class="accentForIndex(index).text">{{ vibe.eyebrow }}</p>
            <h2 class="mt-2 text-xl font-semibold">{{ vibe.title }}</h2>
            <p class="mt-3 text-sm leading-6 text-sharm-muted">{{ vibe.description }}</p>
            <span class="mt-4 inline-flex font-bold text-sharm-sea underline decoration-sharm-sun decoration-2 underline-offset-4">
              {{ copy.finderPage.cta(vibe.title) }}
            </span>
          </div>
        </NuxtLink>
      </div>

      <NuxtLink to="/experiences" class="mt-10 inline-flex rounded-full bg-sharm-action px-6 py-3 font-semibold text-white">
        {{ copy.finderPage.back }}
      </NuxtLink>
    </section>
    <SiteFooter :locale="locale" />
  </main>
</template>
