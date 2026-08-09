import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-09",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  typescript: {
    strict: true,
    typeCheck: true,
  },
  vite: {
    plugins: [tailwindcss()],
    server: {
      // Production serves the web app and the API from the same Nginx
      // origin (see docs/architecture/WEGO_ARCHITECTURE.md's runtime
      // shape), so app code always calls relative `/api/**` paths. This
      // proxy reproduces that same-origin shape in local dev instead of
      // opening CORS on the backend.
      proxy: {
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  },
});
