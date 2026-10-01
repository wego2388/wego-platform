import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-25",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint", "@nuxtjs/i18n", "@nuxt/image", "@nuxt/icon", "@vueuse/nuxt"],
  // Locale-prefixed URLs (/en, /ar, /ru, /it) so every language is its own
  // indexable page and <html lang/dir> is correct in the server response.
  // Copy stays in the typed dictionaries in app/content; the module owns
  // routing, the active locale and SEO head tags.
  i18n: {
    strategy: "prefix",
    defaultLocale: "en",
    locales: [
      { code: "en", language: "en", dir: "ltr", name: "English" },
      { code: "ar", language: "ar", dir: "rtl", name: "العربية" },
      { code: "ru", language: "ru", dir: "ltr", name: "Русский" },
      { code: "it", language: "it", dir: "ltr", name: "Italiano" },
    ],
    detectBrowserLanguage: {
      useCookie: true,
      cookieKey: "sts_locale",
      redirectOn: "root",
      alwaysRedirect: false,
      fallbackLocale: "en",
    },
    // Absolute origin for canonical/hreflang. Overridable at runtime with
    // NUXT_PUBLIC_I18N_BASE_URL (set per deployment in compose), so one image
    // serves any domain without a rebuild.
    baseUrl: process.env.NUXT_PUBLIC_I18N_BASE_URL ?? "http://localhost:3000",
  },
  image: {
    // Tour media is served from this site's own /media tree (see UX-0 media paths).
    format: ["avif", "webp"],
    quality: 72,
  },
  icon: {
    // Inline SVG so Tailwind size classes (size-5, size-14…) set the icon size.
    mode: "svg",
    serverBundle: { collections: ["lucide"] },
    clientBundle: { scan: true },
  },
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
  runtimeConfig: {
    // Server-side rendering reads the catalog from the backend directly on the
    // internal network (NUXT_API_INTERNAL_BASE); the browser uses same-origin /api.
    apiInternalBase: "http://127.0.0.1:8080",
    public: {
      // Analytics IDs come from deployment config only (NUXT_PUBLIC_GA4_ID,
      // NUXT_PUBLIC_META_PIXEL_ID). Empty = no consent banner, no tags at all.
      ga4Id: "",
      metaPixelId: "",
      // Internal component showcase (/{locale}/design-system); off in production.
      designSystem: process.env.NUXT_PUBLIC_DESIGN_SYSTEM === "true",
    },
  },
  // Same-document View Transitions between pages (tour card picture → tour
  // page hero). Browsers without support, and visitors who prefer reduced
  // motion, get a normal navigation.
  experimental: { viewTransition: true },
  // Pre-compressed (gzip/brotli) copies of the client bundle, served by the
  // Nitro node server to browsers that accept them.
  nitro: { compressPublicAssets: true },
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
