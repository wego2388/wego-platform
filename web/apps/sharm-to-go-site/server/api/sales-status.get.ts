export default defineEventHandler(async (event) => {
  setResponseHeader(event, "cache-control", "no-store");
  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  try {
    const response = await fetch(`${base}/api/v1/travel-marketplace/public/sales-status`, { cache: "no-store" });
    if (!response.ok) throw new Error("sales_status_unavailable");
    const body: unknown = await response.json();
    if (!body || typeof body !== "object" || !("requestsOpen" in body) || typeof body.requestsOpen !== "boolean") {
      throw new Error("invalid_sales_status");
    }
    // Explicit projection protects public callers even if upstream changes.
    return { requestsOpen: body.requestsOpen };
  } catch {
    throw createError({ statusCode: 502, statusMessage: "Could not verify request availability." });
  }
});
