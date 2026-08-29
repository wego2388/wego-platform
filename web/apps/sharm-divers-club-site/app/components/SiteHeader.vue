<script setup lang="ts">
import { ref } from "vue";
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
  faqLabel: string;
  contactLabel: string;
  languageName: string;
  whatsappLabel: string;
  menuLabel: string;
}>();

const emit = defineEmits<{ "toggle-locale": [] }>();

const { scrolled } = useScrolled();
const menuOpen = ref(false);

function closeMenu() {
  menuOpen.value = false;
}
</script>

<template>
  <div
    class="sdc-header sticky top-0 z-40"
    :class="props.variant === 'hero'
      ? (scrolled ? 'is-scrolled text-white' : 'bg-sdc-deep text-white')
      : 'border-b border-sdc-border bg-sdc-surface/95 backdrop-blur'"
  >
    <header class="mx-auto flex max-w-7xl items-center justify-between gap-4 px-5 py-4 lg:px-10">
      <NuxtLink to="/" class="flex items-center gap-3 font-semibold" :class="props.variant !== 'hero' && 'text-sdc-ink'" :aria-label="homeLabel" @click="closeMenu">
        <span class="grid size-10 place-items-center rounded-2xl bg-sdc-sand font-display text-base font-black text-sdc-ink">SDC</span>
        <span class="hidden sm:inline">Sharm Divers Club</span>
      </NuxtLink>
      <nav class="hidden items-center gap-6 text-sm font-semibold md:flex" :class="props.variant !== 'hero' && 'text-sdc-ink'" aria-label="Primary navigation">
        <NuxtLink to="/discover" class="transition-colors hover:text-sdc-sand">{{ discoverLabel }}</NuxtLink>
        <NuxtLink to="/about" class="transition-colors hover:text-sdc-sand">{{ aboutLabel }}</NuxtLink>
        <NuxtLink to="/faq" class="transition-colors hover:text-sdc-sand">{{ faqLabel }}</NuxtLink>
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
          class="hidden min-h-10 rounded-full border px-4 text-sm font-semibold backdrop-blur transition-colors md:inline-flex"
          :class="props.variant === 'hero' ? 'border-white/25 bg-white/10 hover:bg-white/20' : 'border-sdc-border bg-sdc-surface text-sdc-ink hover:bg-sdc-canvas'"
          @click="emit('toggle-locale')"
        >
          {{ languageName }}
        </button>
        <button
          type="button"
          class="grid min-h-10 min-w-10 place-items-center rounded-full border transition-colors md:hidden"
          :class="props.variant === 'hero' ? 'border-white/25 bg-white/10 hover:bg-white/20' : 'border-sdc-border bg-sdc-surface text-sdc-ink hover:bg-sdc-canvas'"
          :aria-expanded="menuOpen"
          aria-controls="sdc-mobile-menu"
          :aria-label="menuLabel"
          @click="menuOpen = !menuOpen"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
            <path v-if="!menuOpen" d="M4 6h16M4 12h16M4 18h16" />
            <path v-else d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </div>
    </header>

    <nav
      v-if="menuOpen"
      id="sdc-mobile-menu"
      class="border-t px-5 py-4 md:hidden"
      :class="props.variant === 'hero' ? 'border-white/15 bg-sdc-deep' : 'border-sdc-border bg-sdc-surface'"
      aria-label="Mobile navigation"
    >
      <div class="grid gap-1 text-sm font-semibold" :class="props.variant !== 'hero' && 'text-sdc-ink'">
        <NuxtLink to="/discover" class="rounded-xl px-3 py-3 transition-colors hover:bg-white/10" @click="closeMenu">{{ discoverLabel }}</NuxtLink>
        <NuxtLink to="/about" class="rounded-xl px-3 py-3 transition-colors hover:bg-white/10" @click="closeMenu">{{ aboutLabel }}</NuxtLink>
        <NuxtLink to="/faq" class="rounded-xl px-3 py-3 transition-colors hover:bg-white/10" @click="closeMenu">{{ faqLabel }}</NuxtLink>
        <NuxtLink to="/contact" class="rounded-xl px-3 py-3 transition-colors hover:bg-white/10" @click="closeMenu">{{ contactLabel }}</NuxtLink>
      </div>
      <div class="mt-4 flex gap-2">
        <a :href="whatsappUrl" target="_blank" rel="noopener" class="flex-1 rounded-full bg-sdc-turquoise px-4 py-3 text-center text-sm font-semibold text-white">
          {{ whatsappLabel }}
        </a>
        <button
          type="button"
          class="rounded-full border px-4 py-3 text-sm font-semibold"
          :class="props.variant === 'hero' ? 'border-white/25 text-white' : 'border-sdc-border text-sdc-ink'"
          @click="emit('toggle-locale')"
        >
          {{ languageName }}
        </button>
      </div>
    </nav>
  </div>
</template>
