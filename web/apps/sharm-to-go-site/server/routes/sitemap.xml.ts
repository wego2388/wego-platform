// Static pages plus every service the real backend has published. If the
// backend is unreachable the sitemap still serves the static pages rather
// than failing — a crawler must never get a 5xx for a catalog outage.
export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig();
  const siteUrl = String(config.public.siteUrl).replace(/\/$/, "");
  const paths = ["/", "/experiences"];

  try {
    const response = await fetch(`${config.travelMarketplaceApiBase}/api/v1/travel-marketplace/public/services`);
    if (response.ok) {
      const services = (await response.json()) as Array<{ id: string }>;
      for (const service of services) paths.push(`/experiences/${encodeURIComponent(service.id)}`);
    }
  } catch {
    // static pages only
  }

  const urls = paths.map((path) => `  <url><loc>${siteUrl}${path}</loc></url>`).join("\n");
  setResponseHeader(event, "content-type", "application/xml; charset=utf-8");
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`;
});
