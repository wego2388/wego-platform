/** Same-origin proxy to the real backend's public services list — see categories.get.ts for why this exists. */
export default defineEventHandler(async (event) => {
  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const query = getQuery(event);
  const categoryId = typeof query.categoryId === "string" ? query.categoryId : undefined;
  const search = categoryId ? `?categoryId=${encodeURIComponent(categoryId)}` : "";

  const response = await fetch(`${base}/api/v1/travel-marketplace/public/services${search}`);
  if (!response.ok) {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the catalog." });
  }
  return response.json();
});
