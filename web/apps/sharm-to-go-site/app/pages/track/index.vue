<script setup lang="ts">
import { ref } from "vue";
import SiteSubHeader from "../../components/SiteSubHeader.vue";
import SiteFooter from "../../components/SiteFooter.vue";
import { useSiteLocale } from "../../composables/useSiteLocale";

const { locale, copy, direction, toggleLocale } = useSiteLocale();

useHead(() => ({
  title: locale.value === "ar" ? "تابع طلبك · Sharm To Go" : "Track your request · Sharm To Go",
  htmlAttrs: { dir: direction.value, lang: locale.value },
}));

const reference = ref("");
const router = useRouter();

function search() {
  const trimmed = reference.value.trim();
  if (trimmed) router.push(`/track/${trimmed}`);
}
</script>

<template>
  <main id="main-content" :dir="direction" :lang="locale" class="min-h-screen bg-sharm-canvas px-6 py-8 text-sharm-ink lg:px-10">
    <div class="mx-auto max-w-xl">
      <SiteSubHeader back-label="Sharm To Go" back-to="/" :direction="direction" :locale-label="copy.languageName" @toggle-locale="toggleLocale" />
    </div>

    <section class="mx-auto mt-10 max-w-xl rounded-[2rem] border border-black/5 bg-sharm-surface p-8 shadow-sm">
      <h1 class="font-display text-2xl font-semibold tracking-tight">{{ copy.track.heading }}</h1>
      <p class="mt-3 leading-7 text-sharm-muted">{{ copy.track.body }}</p>

      <form class="mt-6" @submit.prevent="search">
        <label for="reference" class="block text-sm font-semibold text-sharm-muted">{{ copy.track.inputLabel }}</label>
        <input
          id="reference"
          v-model="reference"
          type="text"
          dir="ltr"
          required
          :placeholder="copy.track.inputPlaceholder"
          class="mt-2 w-full min-h-12 rounded-xl border border-sharm-border px-4 font-normal"
        >
        <button type="submit" class="mt-4 w-full min-h-12 rounded-full bg-sharm-sea font-semibold text-white">
          {{ copy.track.searchButton }}
        </button>
      </form>
    </section>
    <SiteFooter :locale="locale" />
  </main>
</template>
