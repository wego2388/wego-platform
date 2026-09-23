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
      link: [
        // Real logo (owner-supplied 2026-09-23, registered at
        // clients/sharm-to-go/design/assets/sharm-to-go-badge.png) — a
        // rendered medallion, not a vector mark, so favicons are raster
        // PNGs rather than the site's earlier favicon.svg.
        { rel: "icon", type: "image/png", sizes: "32x32", href: "/favicon-32.png" },
        { rel: "icon", type: "image/png", sizes: "192x192", href: "/icon-192.png" },
        { rel: "apple-touch-icon", href: "/apple-touch-icon.png" },
        { rel: "manifest", href: "/manifest.webmanifest" },
      ],
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
    public: {
      // The deployed origin, used for absolute og:image/canonical/sitemap
      // URLs (share-preview crawlers reject relative ones). Set
      // NUXT_PUBLIC_SITE_URL to the real domain at deploy time.
      siteUrl: "http://localhost:4001",
    },
  },
});
