import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-25",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  app: {
    head: {
      meta: [
        { name: "theme-color", content: "#075f67" },
        { name: "description", content: "Discover Sharm El Sheikh experiences with clear local coordination." },
      ],
      link: [{ rel: "icon", type: "image/svg+xml", href: "/favicon.svg" }],
    },
  },
  typescript: { strict: true, typeCheck: true },
  vite: { plugins: [tailwindcss()] },
  runtimeConfig: {
    // Server-only (not under `public`) — the browser never talks to this
    // backend directly; it goes through server/api/catalog/* (same-origin,
    // no CORS). See WEGO-010-A Packet 0R for why this is a separate
    // app/port from every other client. Override with
    // NUXT_TRAVEL_MARKETPLACE_API_BASE in real deployments.
    travelMarketplaceApiBase: "http://localhost:8081",
  },
});
