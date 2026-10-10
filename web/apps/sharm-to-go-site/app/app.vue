<script setup lang="ts">
import WhatsAppFab from "./components/WhatsAppFab.vue";
import SalesStatusBanner from "./components/SalesStatusBanner.vue";
import { useSiteLocale } from "./composables/useSiteLocale";
// `?url` resolves to the real, content-hashed built asset path at build
// time — these two files are the ones actually needed for the first
// paint every page starts with (English, per useSiteLocale's own
// server/client-must-match design: every page starts "en", Arabic is a
// client-only opt-in after mount). Without this, the browser only
// discovers these fonts after downloading and parsing the CSS bundle
// that contains their @font-face rule, which measurably delayed LCP —
// the hero's own body text is the LCP element on the homepage, and
// `font-display: swap`'s swap-in repaint is what the LCP metric actually
// waits for. Measured directly via a Playwright + CDP-throttled
// PerformanceObserver probe (Slow-4G/4x-CPU, matching Lighthouse's own
// mobile preset), not guessed.
import interLatinFontUrl from "@fontsource-variable/inter/files/inter-latin-wght-normal.woff2?url";
import fredokaLatinFontUrl from "@fontsource-variable/fredoka/files/fredoka-latin-wght-normal.woff2?url";

const siteUrl = String(useRuntimeConfig().public.siteUrl).replace(/\/$/, "");
const route = useRoute();
const { copy } = useSiteLocale();

useHead({
  link: [
    { rel: "canonical", href: () => `${siteUrl}${route.path}` },
    { rel: "preload", as: "font", type: "font/woff2", href: interLatinFontUrl, crossorigin: "anonymous" },
    { rel: "preload", as: "font", type: "font/woff2", href: fredokaLatinFontUrl, crossorigin: "anonymous" },
  ],
  meta: [
    { property: "og:type", content: "website" },
    { property: "og:site_name", content: "Sharm To Go" },
    { property: "og:title", content: "Sharm To Go · Discover Sharm clearly" },
    { property: "og:description", content: "Discover Sharm El Sheikh experiences with clear local coordination." },
    { property: "og:image", content: `${siteUrl}/og-image.png` },
    { property: "og:image:width", content: "1200" },
    { property: "og:image:height", content: "630" },
    { property: "og:url", content: () => `${siteUrl}${route.path}` },
    { name: "twitter:card", content: "summary_large_image" },
    { name: "twitter:image", content: `${siteUrl}/og-image.png` },
  ],
});
</script>

<template>
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-full focus:bg-sharm-action focus:px-5 focus:py-3 focus:font-semibold focus:text-white rtl:focus:right-3 rtl:focus:left-auto"
  >
    {{ copy.skipToContent }}
  </a>
  <SalesStatusBanner />
  <NuxtPage />
  <WhatsAppFab />
</template>
