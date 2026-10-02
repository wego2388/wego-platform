/**
 * Same-origin proxy to the real backend's public reference lookup — see
 * ../catalog/categories.get.ts for why this exists. Passes the backend's
 * 404 straight through: an unknown reference and a typo are
 * indistinguishable to a public caller, by design.
 */
export default defineEventHandler(async (event) => {
  // A request reference is effectively a bearer secret — anyone who has it
  // (not just the customer) can read this record. Never let it sit in a
  // shared cache, a proxy, or the browser's own disk cache.
  setResponseHeader(event, "cache-control", "no-store");

  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const reference = getRouterParam(event, "reference");

  // This proxy's own fetch() to the backend bypasses nginx entirely, so
  // nginx's edge request id never reaches CorrelationIdFilter on its own —
  // generate one here (or keep one the browser already sent) and echo it
  // back, so a customer-visible failure can be traced into the backend
  // audit trail.
  const correlationId = getHeader(event, "x-correlation-id") ?? crypto.randomUUID();
  setResponseHeader(event, "x-correlation-id", correlationId);

  // fetch() itself throws on a true connection failure (DNS, refused,
  // timeout) — the !response.ok branch below only ever sees a real HTTP
  // response, so it cannot catch this case on its own.
  let response: Response;
  try {
    response = await fetch(`${base}/api/v1/travel-marketplace/public/requests/${encodeURIComponent(String(reference))}`, {
      headers: { "X-Correlation-Id": correlationId },
    });
  } catch {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the request service." });
  }
  if (response.status === 404) {
    setResponseStatus(event, 404);
    return null;
  }
  if (!response.ok) {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the request service." });
  }
  return response.json();
});
