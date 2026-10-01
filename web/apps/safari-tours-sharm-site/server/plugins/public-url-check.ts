/**
 * Canonical links, hreflang, the sitemap and share images all use the public
 * origin. A production server still pointing at a local address would publish
 * wrong URLs to search engines, so say so loudly at start-up.
 */
export default defineNitroPlugin(() => {
  const baseUrl = String((useRuntimeConfig().public.i18n as { baseUrl?: string } | undefined)?.baseUrl ?? "");
  if (process.env.NODE_ENV === "production" && /^https?:\/\/(localhost|127\.|0\.0\.0\.0)/.test(baseUrl)) {
    console.warn(`[safari-site] NUXT_PUBLIC_I18N_BASE_URL is "${baseUrl}" — set WEGO_SITE_PUBLIC_URL to the public https origin before going live.`);
  }
});
