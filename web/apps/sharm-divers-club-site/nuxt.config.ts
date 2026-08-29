import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-25",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  app: {
    head: {
      meta: [
        { name: "theme-color", content: "#faf6ee", media: "(prefers-color-scheme: light)" },
        { name: "theme-color", content: "#071b22", media: "(prefers-color-scheme: dark)" },
        { name: "description", content: "Sharm Divers Club — PADI 5 Star diving in Sharm El Sheikh, personally guided." },
        { property: "og:type", content: "website" },
        { property: "og:site_name", content: "Sharm Divers Club" },
        { property: "og:title", content: "Sharm Divers Club — Red Sea confidence, personally guided." },
        {
          property: "og:description",
          content: "PADI 5 Star diving, courses and water sports in Sharm El Sheikh — real 2026 prices, one WhatsApp inquiry away.",
        },
        { property: "og:image", content: "https://sharmdiversclub.com/og-image.jpg" },
        { property: "og:image:width", content: "1200" },
        { property: "og:image:height", content: "630" },
        { name: "twitter:card", content: "summary_large_image" },
        { name: "twitter:title", content: "Sharm Divers Club — Red Sea confidence, personally guided." },
        {
          name: "twitter:description",
          content: "PADI 5 Star diving, courses and water sports in Sharm El Sheikh — real 2026 prices, one WhatsApp inquiry away.",
        },
        { name: "twitter:image", content: "https://sharmdiversclub.com/og-image.jpg" },
      ],
      link: [
        { rel: "icon", type: "image/svg+xml", href: "/favicon.svg" },
        { rel: "apple-touch-icon", sizes: "180x180", href: "/apple-touch-icon.png" },
        { rel: "manifest", href: "/manifest.json" },
      ],
    },
  },
  typescript: { strict: true, typeCheck: true },
  vite: { plugins: [tailwindcss()] },
});
