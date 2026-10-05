/** Pure sitemap builder (tested): every page in every language, with hreflang alternates. */
const SITE_LOCALES = ["en", "ar", "ru", "it"] as const;
export const STATIC_PATHS = ["", "/tours", "/trip-finder", "/faq", "/about", "/contact", "/terms", "/privacy"];
export const CATEGORY_SLUGS = ["desert", "sea", "cultural", "shows", "transfers"];

function escapeXml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export function buildSitemap(baseUrl: string, tourSlugs: string[]): string {
  const base = baseUrl.replace(/\/+$/, "");
  const paths = [
    ...STATIC_PATHS,
    ...CATEGORY_SLUGS.map((slug) => `/category/${slug}`),
    ...tourSlugs.filter((slug) => /^[a-z0-9][a-z0-9-]*$/.test(slug)).map((slug) => `/tour/${slug}`),
  ];
  const entries = paths.flatMap((path) =>
    SITE_LOCALES.map((locale) => {
      const alternates = SITE_LOCALES.map(
        (alt) => `    <xhtml:link rel="alternate" hreflang="${alt}" href="${escapeXml(`${base}/${alt}${path}`)}"/>`,
      );
      alternates.push(`    <xhtml:link rel="alternate" hreflang="x-default" href="${escapeXml(`${base}/en${path}`)}"/>`);
      return [`  <url>`, `    <loc>${escapeXml(`${base}/${locale}${path}`)}</loc>`, ...alternates, `  </url>`].join("\n");
    }),
  );
  return [
    `<?xml version="1.0" encoding="UTF-8"?>`,
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">`,
    ...entries,
    `</urlset>`,
    "",
  ].join("\n");
}

export function buildRobots(baseUrl: string): string {
  const base = baseUrl.replace(/\/+$/, "");
  return [
    "User-agent: *",
    "Allow: /",
    // Checkout, booking results and lookups are personal and not for search.
    ...SITE_LOCALES.flatMap((l) => [`Disallow: /${l}/booking/`, `Disallow: /${l}/my-booking`, `Disallow: /${l}/design-system`]),
    "Disallow: /api/",
    "",
    `Sitemap: ${base}/sitemap.xml`,
    "",
  ].join("\n");
}
