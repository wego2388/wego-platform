/**
 * Same-origin proxy to one public service — see ../categories.get.ts for
 * why this exists. Passes the backend's 404 straight through: an unknown
 * id and a real, not-currently-published service are indistinguishable to
 * a public caller, by design (see PublicCatalogController).
 */
export default defineEventHandler(async (event) => {
  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const id = getRouterParam(event, "id");

  const response = await fetch(`${base}/api/v1/travel-marketplace/public/services/${encodeURIComponent(String(id))}`);
  if (response.status === 404) {
    setResponseStatus(event, 404);
    return null;
  }
  if (!response.ok) {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the catalog." });
  }
  return response.json();
});
