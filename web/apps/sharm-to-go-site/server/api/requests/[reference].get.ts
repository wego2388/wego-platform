/**
 * Same-origin proxy to the real backend's public reference lookup — see
 * ../catalog/categories.get.ts for why this exists. Passes the backend's
 * 404 straight through: an unknown reference and a typo are
 * indistinguishable to a public caller, by design.
 */
export default defineEventHandler(async (event) => {
  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const reference = getRouterParam(event, "reference");

  const response = await fetch(`${base}/api/v1/travel-marketplace/public/requests/${encodeURIComponent(String(reference))}`);
  if (response.status === 404) {
    setResponseStatus(event, 404);
    return null;
  }
  if (!response.ok) {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the request service." });
  }
  return response.json();
});
