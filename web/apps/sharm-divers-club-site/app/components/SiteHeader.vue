<script setup lang="ts">
import { useScrolled } from "../composables/useScrolled";
import { whatsappUrl } from "../content/locales";
import type { SdcLocale } from "../content/locales";

const props = defineProps<{
  locale: SdcLocale;
  direction: "ltr" | "rtl";
  variant?: "hero" | "solid";
  homeLabel: string;
  discoverLabel: string;
  aboutLabel: string;
  contactLabel: string;
  languageName: string;
  whatsappLabel: string;
}>();

const emit = defineEmits<{ "toggle-locale": [] }>();

const { scrolled } = useScrolled();
</script>

<template>
  <div
    class="sdc-header sticky top-0 z-40"
    :class="props.variant === 'hero'
      ? (scrolled ? 'is-scrolled text-white' : 'bg-sdc-deep text-white')
      : 'border-b border-sdc-border bg-white/95 backdrop-blur'"
  >
    <header class="mx-auto flex max-w-7xl items-center justify-between gap-4 px-5 py-4 lg:px-10">
      <NuxtLink to="/" class="flex items-center gap-3 font-semibold" :class="props.variant !== 'hero' && 'text-sdc-deep'" :aria-label="homeLabel">
        <span class="grid size-10 place-items-center rounded-2xl bg-sdc-sand font-display text-base font-black text-sdc-deep">SDC</span>
        <span class="hidden sm:inline">Sharm Divers Club</span>
      </NuxtLink>
      <nav class="hidden items-center gap-6 text-sm font-semibold md:flex" :class="props.variant !== 'hero' && 'text-sdc-deep'" aria-label="Primary navigation">
        <NuxtLink to="/discover" class="transition-colors hover:text-sdc-sand">{{ discoverLabel }}</NuxtLink>
        <NuxtLink to="/about" class="transition-colors hover:text-sdc-sand">{{ aboutLabel }}</NuxtLink>
        <NuxtLink to="/contact" class="transition-colors hover:text-sdc-sand">{{ contactLabel }}</NuxtLink>
      </nav>
      <div class="flex items-center gap-2">
        <a
          :href="whatsappUrl"
          target="_blank"
          rel="noopener"
          class="hidden rounded-full bg-sdc-turquoise px-4 py-2 text-sm font-semibold text-white transition-transform hover:-translate-y-0.5 sm:inline-flex"
        >
          {{ whatsappLabel }}
        </a>
        <button
          type="button"
          class="min-h-10 rounded-full border px-4 text-sm font-semibold backdrop-blur transition-colors"
          :class="props.variant === 'hero' ? 'border-white/25 bg-white/10 hover:bg-white/20' : 'border-sdc-border bg-white text-sdc-deep hover:bg-sdc-canvas'"
          @click="emit('toggle-locale')"
        >
          {{ languageName }}
        </button>
      </div>
    </header>
  </div>
</template>
