<script setup lang="ts">
import { ref, watch } from "vue";
import { useDiscoveryCopy } from "../composables/useDiscoveryCopy";
import { useScrolled } from "../composables/useScrolled";
import { whatsappUrl } from "../content/locales";

/**
 * Site header. Over the ocean band at the top of the page it is light-on-dark;
 * once the page scrolls it becomes a solid surface bar.
 */
const copy = useDiscoveryCopy();
const { scrolled } = useScrolled();
const menuOpen = ref(false);
const route = useRoute();
watch(() => route.fullPath, () => (menuOpen.value = false));
</script>

<template>
  <div
    class="sts-header sticky top-0 z-[var(--sts-z-sticky)] transition-[background-color,box-shadow] duration-[var(--sts-dur-base)]"
    :class="scrolled ? 'border-b border-sts-border bg-sts-surface/95 shadow-sts-base backdrop-blur' : 'bg-sts-ocean'"
  >
    <header class="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6 lg:px-10">
      <NuxtLinkLocale to="/" class="flex shrink-0 items-center rounded-md" aria-label="Safari Tours Sharm">
        <BrandLogo :inverse="!scrolled" :height="36" />
      </NuxtLinkLocale>

      <nav class="hidden items-center gap-1 text-sm font-semibold md:flex" :aria-label="copy.nav.main">
        <NuxtLinkLocale
          v-for="item in [
            { to: '/tours', label: copy.nav.tours },
            { to: '/my-booking', label: copy.nav.myBooking },
            { to: '/contact', label: copy.nav.contact },
          ]"
          :key="item.to"
          :to="item.to"
          class="rounded-full px-3.5 py-2 transition-colors"
          :class="scrolled ? 'text-sts-ink hover:bg-sts-sand-soft' : 'text-white/90 hover:bg-white/12 hover:text-white'"
          active-class="sts-nav-active"
        >
          {{ item.label }}
        </NuxtLinkLocale>
      </nav>

      <div class="flex items-center gap-1">
        <SiteLocaleSwitcher :label="copy.nav.language" :tone="scrolled ? 'dark' : 'light'" />
        <SiteThemeToggle :labels="copy.nav.theme" :tone="scrolled ? 'dark' : 'light'" />
        <span class="ms-1 hidden sm:block"><UiButton to="/tours" size="sm">{{ copy.nav.book }}</UiButton></span>
        <button
          type="button"
          class="grid size-10 place-items-center rounded-full md:hidden"
          :class="scrolled ? 'text-sts-ink hover:bg-sts-sand-soft' : 'text-white hover:bg-white/12'"
          :aria-label="copy.nav.menu"
          :aria-expanded="menuOpen"
          @click="menuOpen = true"
        >
          <Icon name="lucide:menu" class="size-5" aria-hidden="true" />
        </button>
      </div>
    </header>

    <UiSheet v-model:open="menuOpen" :title="copy.nav.menu" :close-label="copy.nav.closeMenu" side="end">
      <nav class="grid gap-1 text-base font-semibold" :aria-label="copy.nav.menu">
        <NuxtLinkLocale to="/tours" class="rounded-[var(--sts-radius-control)] px-3 py-3 hover:bg-sts-sand-soft">{{ copy.nav.tours }}</NuxtLinkLocale>
        <NuxtLinkLocale to="/my-booking" class="rounded-[var(--sts-radius-control)] px-3 py-3 hover:bg-sts-sand-soft">{{ copy.nav.myBooking }}</NuxtLinkLocale>
        <NuxtLinkLocale to="/contact" class="rounded-[var(--sts-radius-control)] px-3 py-3 hover:bg-sts-sand-soft">{{ copy.nav.contact }}</NuxtLinkLocale>
      </nav>
      <div class="mt-6 grid gap-3">
        <UiButton to="/tours" block>{{ copy.nav.book }}</UiButton>
        <UiButton :href="whatsappUrl" variant="secondary" icon="lucide:message-circle" block>{{ copy.nav.whatsapp }}</UiButton>
      </div>
    </UiSheet>
  </div>
</template>
