/**
 * Same-origin proxy to the real Sharm To Go backend's public catalog
 * (see products/travel-marketplace's PublicCatalogController). A direct
 * browser fetch to the backend's own origin is blocked by CORS — caught
 * live in a real headless browser, not a Node-based test — and calling it
 * from here also lets SSR reach it without exposing the backend's host to
 * client bundles.
 */
export default defineEventHandler(async () => {
  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const response = await fetch(`${base}/api/v1/travel-marketplace/public/categories`);
  if (!response.ok) {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the catalog." });
  }
  return response.json();
});
