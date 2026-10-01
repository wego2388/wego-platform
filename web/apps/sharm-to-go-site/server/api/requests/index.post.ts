/**
 * Same-origin proxy to the real backend's public create-request endpoint —
 * see ../catalog/categories.get.ts for why this exists. Forwards the
 * client's Idempotency-Key header through unchanged: the backend, not this
 * proxy, is the real idempotency boundary (a unique DB constraint), so the
 * key must survive the hop intact.
 */
export default defineEventHandler(async (event) => {
  // The response carries a fresh request reference — a bearer secret, see
  // [reference].get.ts's own comment — never cache it.
  setResponseHeader(event, "cache-control", "no-store");

  const base = useRuntimeConfig().travelMarketplaceApiBase as string;
  const idempotencyKey = getHeader(event, "idempotency-key");
  if (!idempotencyKey) {
    throw createError({ statusCode: 400, statusMessage: "Idempotency-Key header is required." });
  }
  const body = await readBody(event);

  const response = await fetch(`${base}/api/v1/travel-marketplace/public/requests`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => null);
  setResponseStatus(event, response.status);
  return payload;
});
