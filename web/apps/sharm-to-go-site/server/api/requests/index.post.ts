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

  // This proxy's own fetch() to the backend bypasses nginx entirely, so
  // nginx's edge request id never reaches CorrelationIdFilter on its own —
  // generate one here (or keep one the browser already sent) and echo it
  // back, so a customer-visible failure can be traced into the backend
  // audit trail.
  const correlationId = getHeader(event, "x-correlation-id") ?? crypto.randomUUID();
  setResponseHeader(event, "x-correlation-id", correlationId);

  // fetch() itself throws on a true connection failure (DNS, refused,
  // timeout) — distinct from the backend responding with a real HTTP error
  // status, which is deliberately passed through unchanged below so a
  // specific error code like price_changed or party_size_exceeds_capacity
  // reaches the caller intact.
  let response: Response;
  try {
    response = await fetch(`${base}/api/v1/travel-marketplace/public/requests`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
        "X-Correlation-Id": correlationId,
      },
      body: JSON.stringify(body),
    });
  } catch {
    throw createError({ statusCode: 502, statusMessage: "Could not reach the request service." });
  }

  const payload = await response.json().catch(() => null);
  setResponseStatus(event, response.status);
  return payload;
});
