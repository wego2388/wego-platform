import { buildSitemap } from "../utils/sitemap";

/** XML sitemap of all public pages in four languages; tours come from the live catalogue. */
// Cached for an hour, so crawlers or a flood of requests cost at most a few
// backend calls per hour instead of up to 20 per request.
export default defineCachedEventHandler(async (event) => {
  const config = useRuntimeConfig(event);
  const baseUrl = (config.public.i18n as { baseUrl?: string } | undefined)?.baseUrl ?? "http://localhost:3000";
  const slugs: string[] = [];
  try {
    // The public list allows at most 100 per page.
    for (let page = 0; page < 20; page++) {
      const batch = await $fetch<{ slug: string }[]>(`${config.apiInternalBase}/api/v1/tours-operator/tours?page=${page}&size=100`);
      slugs.push(...batch.map((t) => t.slug));
      if (batch.length < 100) break;
    }
  } catch {
    // The sitemap still lists every static and category page if the catalogue is unreachable.
  }
  setResponseHeader(event, "Content-Type", "application/xml; charset=utf-8");
  setResponseHeader(event, "Cache-Control", "public, max-age=3600");
  return buildSitemap(baseUrl, slugs);
}, { maxAge: 3600, name: "sitemap", getKey: () => "all" });
