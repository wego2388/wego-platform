import tailwindcss from "@tailwindcss/vite";

export default defineNuxtConfig({
  compatibilityDate: "2026-08-09",
  css: ["@wego/design-tokens/tokens.css", "~/assets/css/main.css"],
  devtools: { enabled: false },
  modules: ["@nuxt/eslint"],
  app: {
    // No default title/titleTemplate here — each page sets its own full,
    // literal title via useHead (see index.vue/login.vue) rather than a
    // templated suffix, since Nuxt config's app.head only accepts a plain
    // string template, and a plain "%s · Wego Platform" would double up
    // whenever a page's own title chunk is already "Wego Platform".
    head: {
      meta: [{ name: "theme-color", content: "#087f74" }],
      link: [{ rel: "icon", type: "image/svg+xml", href: "/favicon.svg" }],
      script: [
        {
          // Runs before Vue mounts, so the very first paint already has
          // the right theme — without this, a stored/system dark
          // preference would render light for one frame (or longer,
          // waiting on hydration) and then visibly flip. Deliberately
          // duplicates useTheme.ts's own read/resolve logic rather than
          // importing it: this has to run as a synchronous, dependency-
          // free script tag in <head>, before any bundle executes.
          innerHTML: `(function () {
            try {
              var stored = window.localStorage.getItem("wego-erp-theme");
              var preference = stored === "light" || stored === "dark" ? stored : "system";
              var dark = preference === "dark" || (preference === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
              if (dark) {
                document.documentElement.setAttribute("data-theme", "dark");
                var meta = document.querySelector('meta[name="theme-color"]');
                if (meta) meta.setAttribute("content", "#35d6bd");
              }
            } catch (e) {}
          })();`,
        },
      ],
    },
  },
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
