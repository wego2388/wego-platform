import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-25",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  app: {
    head: {
      meta: [{ name: "theme-color", content: "#102f35" }],
      link: [{ rel: "icon", type: "image/svg+xml", href: "/favicon.svg" }],
    },
  },
  typescript: { strict: true, typeCheck: true },
  vite: {
    plugins: [tailwindcss()],
    server: {
      // Same same-origin-in-production shape as web/apps/erp's own proxy —
      // see that config's comment. Points at the Sharm To Go backend
      // (:platform:apps:sharm-to-go), a separate real application from the
      // Divers backend on :8080 (see WEGO-010-A Packet 0R) — run it locally
      // with `--server.port=8081` so both backends can run side by side.
      proxy: {
        "/api": {
          target: "http://localhost:8081",
          changeOrigin: true,
        },
      },
    },
  },
});
