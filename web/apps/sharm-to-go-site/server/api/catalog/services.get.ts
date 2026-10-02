/**
 * Same-origin proxy to the real backend's public services list — see
 * categories.get.ts for why this exists. The backend paginates (default
 * page size 50, max 200 per page) — this proxy walks every page and
 * returns the full merged list, so a catalog with more than one page of
 * published services is not silently truncated for the "browse all"
 * experience. The backend's own page size cap (200) bounds how many
 * round trips this can ever take for a catalog of any realistic size.
 */
export default defineEventHandler(async (event) => {
  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const query = getQuery(event);
  const categoryId = typeof query.categoryId === "string" ? query.categoryId : undefined;
  const categoryParam = categoryId ? `&categoryId=${encodeURIComponent(categoryId)}` : "";
  const pageSize = 200;

  const all: unknown[] = [];
  for (let page = 0; ; page += 1) {
    let response: Response;
    try {
      response = await fetch(`${base}/api/v1/travel-marketplace/public/services?page=${page}&size=${pageSize}${categoryParam}`);
    } catch {
      throw createError({ statusCode: 502, statusMessage: "Could not reach the catalog." });
    }
    if (!response.ok) {
      throw createError({ statusCode: 502, statusMessage: "Could not reach the catalog." });
    }
    const pageItems = (await response.json()) as unknown[];
    all.push(...pageItems);
    if (pageItems.length < pageSize) break; // last page
  }
  return all;
});
