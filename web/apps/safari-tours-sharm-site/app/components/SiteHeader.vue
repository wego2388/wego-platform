<script setup lang="ts">
import { ref } from "vue";
import { useScrolled } from "../composables/useScrolled";
import { whatsappUrl, localeNames } from "../content/locales";
import type { StsLocale } from "../content/locales";

const props = defineProps<{
  locale: StsLocale;
  direction: "ltr" | "rtl";
  nav: { home: string; tours: string; about: string; contact: string; menu: string };
  whatsappLabel: string;
  currentLocales: StsLocale[];
}>();

const emit = defineEmits<{ "set-locale": [locale: StsLocale] }>();

const { scrolled } = useScrolled();
const menuOpen = ref(false);

function closeMenu() {
  menuOpen.value = false;
}
</script>

<template>
  <div
    class="sts-header sticky top-0 z-40 transition-all duration-300"
    :class="
      scrolled
        ? 'border-b border-sts-border bg-sts-surface/95 shadow-sm backdrop-blur'
        : 'bg-sts-ocean'
    "
  >
    <header
      class="mx-auto flex max-w-7xl items-center justify-between gap-4 px-5 py-4 lg:px-10"
      :class="scrolled ? 'text-sts-ink' : 'text-white'"
    >
      <!-- Logo -->
      <NuxtLink
        to="/"
        class="flex items-center gap-3 font-semibold"
        :aria-label="props.nav.home"
        @click="closeMenu"
      >
        <span
          class="grid size-10 place-items-center rounded-xl font-display text-sm font-black"
          :class="scrolled ? 'bg-sts-ocean text-white' : 'bg-white/20 backdrop-blur'"
        >
          STS
        </span>
        <span class="hidden font-semibold sm:inline">Safari Tours Sharm</span>
      </NuxtLink>

      <!-- Desktop nav -->
      <nav
        class="hidden items-center gap-6 text-sm font-semibold md:flex"
        aria-label="Primary navigation"
      >
        <NuxtLink
          to="/tours"
          class="transition-colors hover:text-sts-coral"
          :class="scrolled ? 'text-sts-ink' : 'text-white/90'"
        >
          {{ props.nav.tours }}
        </NuxtLink>
        <NuxtLink
          to="/contact"
          class="transition-colors hover:text-sts-coral"
          :class="scrolled ? 'text-sts-ink' : 'text-white/90'"
        >
          {{ props.nav.contact }}
        </NuxtLink>
      </nav>

      <!-- Actions -->
      <div class="flex items-center gap-2">
        <!-- WhatsApp CTA -->
        <a
          :href="whatsappUrl"
          target="_blank"
          rel="noopener"
          class="hidden rounded-full px-4 py-2 text-sm font-semibold transition-transform hover:-translate-y-0.5 sm:inline-flex"
          :class="
            scrolled
              ? 'bg-sts-coral text-white'
              : 'border border-white/30 bg-white/10 text-white backdrop-blur'
          "
        >
          {{ props.whatsappLabel }}
        </a>

        <!-- Language switcher -->
        <div class="flex gap-1">
          <button
            v-for="lang in props.currentLocales"
            :key="lang"
            type="button"
            class="min-h-9 rounded-full border px-3 text-xs font-bold transition-colors"
            :class="
              lang === props.locale
                ? scrolled
                  ? 'border-sts-ocean bg-sts-ocean text-white'
                  : 'border-white bg-white/20 text-white backdrop-blur'
                : scrolled
                  ? 'border-sts-border text-sts-muted hover:border-sts-ocean hover:text-sts-ink'
                  : 'border-white/20 text-white/60 hover:border-white/50 hover:text-white'
            "
            :aria-pressed="lang === props.locale"
            :aria-label="`Switch to ${lang}`"
            @click="emit('set-locale', lang)"
          >
            {{ localeNames[lang] }}
          </button>
        </div>

        <!-- Mobile hamburger -->
        <button
          type="button"
          class="grid min-h-10 min-w-10 place-items-center rounded-full border transition-colors md:hidden"
          :class="
            scrolled
              ? 'border-sts-border bg-sts-surface text-sts-ink hover:bg-sts-canvas'
              : 'border-white/25 bg-white/10 text-white hover:bg-white/20'
          "
          :aria-expanded="menuOpen"
          aria-controls="sts-mobile-menu"
          :aria-label="props.nav.menu"
          @click="menuOpen = !menuOpen"
        >
          <svg
            viewBox="0 0 24 24"
            width="20"
            height="20"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            aria-hidden="true"
          >
            <path v-if="!menuOpen" d="M4 6h16M4 12h16M4 18h16" />
            <path v-else d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </div>
    </header>

    <!-- Mobile menu -->
    <nav
      v-if="menuOpen"
      id="sts-mobile-menu"
      class="border-t px-5 py-4 md:hidden"
      :class="
        scrolled
          ? 'border-sts-border bg-sts-surface'
          : 'border-white/15 bg-sts-ocean'
      "
      aria-label="Mobile navigation"
    >
      <div class="grid gap-1 text-sm font-semibold text-white">
        <NuxtLink
          to="/tours"
          class="rounded-xl px-3 py-3 transition-colors hover:bg-white/10"
          :class="scrolled ? 'text-sts-ink hover:bg-sts-canvas' : ''"
          @click="closeMenu"
        >
          {{ props.nav.tours }}
        </NuxtLink>
        <NuxtLink
          to="/contact"
          class="rounded-xl px-3 py-3 transition-colors hover:bg-white/10"
          :class="scrolled ? 'text-sts-ink hover:bg-sts-canvas' : ''"
          @click="closeMenu"
        >
          {{ props.nav.contact }}
        </NuxtLink>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <a
          :href="whatsappUrl"
          target="_blank"
          rel="noopener"
          class="flex-1 rounded-full bg-sts-coral px-4 py-3 text-center text-sm font-semibold text-white"
          @click="closeMenu"
        >
          {{ props.whatsappLabel }}
        </a>
        <div class="flex gap-1">
          <button
            v-for="lang in props.currentLocales"
            :key="lang"
            type="button"
            class="rounded-full border px-3 py-3 text-xs font-bold"
            :class="
              lang === props.locale
                ? 'border-sts-coral bg-sts-coral text-white'
                : 'border-white/30 text-white/70'
            "
            :aria-pressed="lang === props.locale"
            @click="emit('set-locale', lang); closeMenu()"
          >
            {{ localeNames[lang] }}
          </button>
        </div>
      </div>
    </nav>
  </div>
</template>
