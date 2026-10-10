<script setup lang="ts">
import WhatsAppFab from "./components/WhatsAppFab.vue";
import SalesStatusBanner from "./components/SalesStatusBanner.vue";
import { useSiteLocale } from "./composables/useSiteLocale";

const siteUrl = String(useRuntimeConfig().public.siteUrl).replace(/\/$/, "");
const route = useRoute();
const { copy } = useSiteLocale();

useHead({
  link: [{ rel: "canonical", href: () => `${siteUrl}${route.path}` }],
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
