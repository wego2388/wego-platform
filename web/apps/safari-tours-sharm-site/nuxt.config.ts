import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-25",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  app: {
    // The local/CI edge serves both the staff ERP and this public site. A
    // client-specific asset prefix prevents both Nuxt apps competing for the
    // default `/_nuxt/**` route and accidentally hydrating with the other's
    // JavaScript bundle.
    buildAssetsDir: "/_safari/",
    head: {
      meta: [
        { name: "theme-color", content: "#F7F4F0", media: "(prefers-color-scheme: light)" },
        { name: "theme-color", content: "#0A2342", media: "(prefers-color-scheme: dark)" },
        {
          name: "description",
          content:
            "Browse desert safaris, Red Sea activities and day trips in Sharm El Sheikh. See catalog prices, choose available dates and book direct online.",
        },
        { property: "og:type", content: "website" },
        { property: "og:site_name", content: "Safari Tours Sharm" },
        { property: "og:title", content: "Sharm El Sheikh Tours & Excursions — Book Direct" },
        {
          property: "og:description",
          content:
            "Desert safaris, Red Sea activities and day trips with catalog prices, live availability and local support in Sharm El Sheikh.",
        },
        { name: "twitter:card", content: "summary_large_image" },
      ],
      link: [{ rel: "icon", type: "image/svg+xml", href: "/favicon.svg" }],
    },
  },
  typescript: { strict: true, typeCheck: true },
  vite: {
    plugins: [tailwindcss()],
    server: {
      proxy: {
        // In development, proxy /api/* → Spring Boot backend on :8080
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  },
});
