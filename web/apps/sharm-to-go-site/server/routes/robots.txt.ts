// Generated (not a static file) so the Sitemap line carries the real
// deployed origin from NUXT_PUBLIC_SITE_URL instead of a hardcoded domain.
export default defineEventHandler((event) => {
  const siteUrl = String(useRuntimeConfig().public.siteUrl).replace(/\/$/, "");
  setResponseHeader(event, "content-type", "text/plain; charset=utf-8");
  return [
    "User-agent: *",
    "Allow: /",
    "Disallow: /booking-preview",
    "Disallow: /design-system",
    "Disallow: /api/",
    "",
    `Sitemap: ${siteUrl}/sitemap.xml`,
    "",
  ].join("\n");
});
