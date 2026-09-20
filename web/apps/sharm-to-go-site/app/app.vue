<script setup lang="ts">
const siteUrl = String(useRuntimeConfig().public.siteUrl).replace(/\/$/, "");
const route = useRoute();

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
  <NuxtPage />
</template>
