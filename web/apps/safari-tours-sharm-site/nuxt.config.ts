import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-25",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  app: {
    head: {
      meta: [
        { name: "theme-color", content: "#F7F4F0", media: "(prefers-color-scheme: light)" },
        { name: "theme-color", content: "#0A2342", media: "(prefers-color-scheme: dark)" },
        {
          name: "description",
          content:
            "Safari Tours Sharm — Desert safaris, Red Sea boat trips, cultural tours & diving in Sharm El Sheikh. Hotel pickup included. Book online.",
        },
        { property: "og:type", content: "website" },
        { property: "og:site_name", content: "Safari Tours Sharm" },
        { property: "og:title", content: "Safari Tours Sharm — Explore the Red Sea & Sinai Desert" },
        {
          property: "og:description",
          content:
            "30+ tours in Sharm El Sheikh — desert safari, boat trips, diving, Cairo excursions. Hotel pickup included. Free cancellation. Book securely online.",
        },
        { property: "og:image", content: "https://safaritourssharm.com/og-image.jpg" },
        { property: "og:image:width", content: "1200" },
        { property: "og:image:height", content: "630" },
        { name: "twitter:card", content: "summary_large_image" },
      ],
      link: [
        { rel: "icon", type: "image/svg+xml", href: "/favicon.svg" },
        { rel: "apple-touch-icon", sizes: "180x180", href: "/apple-touch-icon.png" },
        { rel: "manifest", href: "/manifest.json" },
      ],
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
