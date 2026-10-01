/**
 * WEGO-016-E — Playwright E2E: Safari Tours checkout flow (mock Paymob)
 *
 * Coverage:
 *   E0 — Public root/assets and staff virtual-host isolation.
 *   E1 — Create booking via public API → booking is NEW, slot reserved.
 *   E2/E2b — Initiate payment → PENDING, then idempotently resume it.
 *   E3/E4 — Valid webhook confirms once; a duplicate is idempotent.
 *   E5 — Invalid HMAC returns 400 without changing paid truth.
 *   E6/E7 — Payment status and staff booking reflect PAID/CONFIRMED truth.
 *   E8/E8b — Confirmation requires backend PAID truth and rejects mutable browser state.
 *   E9 — Staff ERP loads and backend logout revokes the session.
 *   E10 — A guest completes the browser checkout without an account/login.
 *
 * Mock Paymob strategy:
 *   The dedicated E2E override enables MockPaymobClient, which returns a
 *   local mock-provider URL shaped like the Unified Checkout hand-off.
 *   API-level scenarios POST callbacks with the known E2E HMAC; the guest
 *   browser scenario visits that provider page and returns through the real
 *   payment-result polling/confirmation flow. Base Compose never enables the
 *   mock, and this suite does not claim proof against Paymob's sandbox.
 *
 * The test uses page.request (same Playwright context) for all API calls
 * so cookie/session state is shared if needed, and failures show in Playwright
 * traces.
 *
 * Backend base URL: WEGO_E2E_BASE_URL (default http://127.0.0.1:58080)
 * Site base URL:    WEGO_STS_SITE_BASE_URL (default same as WEGO_E2E_BASE_URL,
 *                   set to the Nuxt safari-tours-sharm-site port if running
 *                   the site separately).
 */
import { expect, test } from "@playwright/test";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";

// ── Configuration ─────────────────────────────────────────────────────────────

/** Backend API base — shared with the ERP test suite's baseURL. */
const API_BASE = process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58080";

/**
 * Public site base URL — where safari-tours-sharm-site is served.
 * Defaults to the same nginx edge as the backend so we can verify
 * the confirmation page HTML even without a separately running Nuxt server.
 *
 * In CI this should be set to the actual Nuxt site port.
 */
const SITE_BASE = process.env.WEGO_STS_SITE_BASE_URL ?? API_BASE;
const STAFF_BASE = process.env.WEGO_STS_STAFF_BASE_URL ?? (() => {
  const url = new URL(API_BASE);
  url.hostname = "staff.localhost";
  return url.origin;
})();
const STAFF_HOST_HEADER = new URL(STAFF_BASE).host;

/**
 * The HMAC value the mock PaymobClient accepts as valid.
 * Matches the mock in ToursOperatorPaymentTest: inv.arguments[1] === "valid-hmac"
 */
const VALID_MOCK_HMAC = "valid-hmac";

/** Slug of the E2E tour seeded by seed.mjs */
const E2E_TOUR_SLUG = "e2e-desert-quad-safari";

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Build a valid-looking HMAC webhook body for the mock client. */
function webhookBody(
  orderId: string,
  transactionId: string,
  amountCents: number,
  opts: { isRefund?: boolean; success?: boolean; pending?: boolean } = {},
): string {
  const success = opts.success ?? true;
  const pending = opts.pending ?? false;
  const isRefund = opts.isRefund ?? false;
  return JSON.stringify({
    obj: {
      id: transactionId,
      success,
      pending,
      is_refunded: isRefund,
      error_occured: false,
      has_parent_transaction: false,
      is_3d_secure: true,
      is_auth: false,
      is_capture: false,
      is_standalone_payment: true,
      is_voided: false,
      owner: "100001",
      amount_cents: amountCents,
      currency: "EUR",
      created_at: "2026-09-28T10:00:00Z",
      integration_id: "100001",
      order: { id: orderId },
      source_data: { pan: "1234", sub_type: "MasterCard", type: "card" },
      data: {},
    },
  });
}

// ── Test suite ────────────────────────────────────────────────────────────────

test.describe("Safari Tours checkout flow — mock Paymob", () => {
  // The numbered cases are one lifecycle and intentionally share the booking
  // created by E1. Explicit serial mode makes that contract visible to
  // Playwright and prevents a future fullyParallel change from racing steps.
  test.describe.configure({ mode: "serial" });

  // Shared state across steps within a single test
  let bookingId: string;
  let bookingReference: string;
  let bookingConfirmation: Record<string, unknown>;
  let paymobOrderId: string;
  let slotId: string;
  let tourId: string;
  let slotDate: string;

  // ── Setup: resolve the seeded slot ID ─────────────────────────────────────

  test.beforeAll(async ({ playwright }) => {
    // Resolve the seeded e2e-desert-quad-safari tour and its slot.
    // We use a raw API request (not page) so this is just setup plumbing.
    const ctx = await playwright.request.newContext({ baseURL: API_BASE });
    try {
      const toursRes = await ctx.get(
        "/api/v1/tours-operator/tours?activeOnly=true&size=50",
      );
      if (!toursRes.ok()) {
        throw new Error(`Tours list failed: ${toursRes.status()}`);
      }
      const tours: Array<{ id: string; slug: string }> = await toursRes.json();
      const tour = tours.find((t) => t.slug === E2E_TOUR_SLUG);
      if (!tour) {
        throw new Error(
          `Seeded tour '${E2E_TOUR_SLUG}' not found — run 'pnpm run seed' first`,
        );
      }
      tourId = tour.id;

      // Find the next-Monday slot seeded by seed.mjs
      const today = new Date();
      const daysUntilMonday = (1 - today.getUTCDay() + 7) % 7 || 7;
      const nextMonday = new Date(today);
      nextMonday.setUTCDate(today.getUTCDate() + daysUntilMonday);
      slotDate = nextMonday.toISOString().slice(0, 10);
      const slotTo = new Date(nextMonday);
      slotTo.setUTCDate(slotTo.getUTCDate() + 1);

      const slotsRes = await ctx.get(
        `/api/v1/tours-operator/tours/${tour.id}/slots?from=${slotDate}&to=${slotTo.toISOString().slice(0, 10)}`,
      );
      if (!slotsRes.ok()) {
        throw new Error(`Slots list failed: ${slotsRes.status()}`);
      }
      const slots: Array<{ id: string; timeSlot: string; bookedCount: number; capacity: number }> =
        await slotsRes.json();
      const morning = slots.find(
        (s) => s.timeSlot === "MORNING" && s.capacity - s.bookedCount >= 2,
      );
      if (!morning) {
        throw new Error(
          `No available MORNING slot found for tour '${E2E_TOUR_SLUG}' on ${slotDate}`,
        );
      }
      slotId = morning.id;
    } finally {
      await ctx.dispose();
    }
  });

  // ── E0: Public edge ownership ─────────────────────────────────────────────

  test("E0 — bare domain root serves the public site and its isolated assets", async ({
    page,
    request,
  }) => {
    await page.goto(`${SITE_BASE}/`);
    await expect(
      page.getByRole("heading", {
        level: 1,
        name: "Sharm El Sheikh Tours & Excursions — Book Direct",
      }),
    ).toBeVisible();

    const favicon = await request.get(`${SITE_BASE}/favicon.svg`);
    expect(favicon.status()).toBe(200);
    expect(favicon.headers()["content-type"]).toContain("image/svg+xml");
  });

  // ── E1: Create booking ─────────────────────────────────────────────────────

  test("E1 — create booking returns 201 with NEW status", async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/tours-operator/bookings`, {
      data: {
        slotId,
        adultsCount: 2,
        childrenCount: 0,
        customer: {
          fullName: "E2E Test Customer",
          phone: "+20100000099",
          nationality: "EG",
          email: "e2e-safari@example.com",
        },
        hotelName: "E2E Hotel",
        specialRequests: null,
        locale: "en",
      },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.id).toBeTruthy();
    expect(body.reference).toBeTruthy();
    expect(body.status).toBe("NEW");

    bookingId = body.id;
    bookingReference = body.reference;
    bookingConfirmation = body;
  });

  // ── E2: Initiate payment ───────────────────────────────────────────────────

  test("E2 — initiate payment returns PENDING with a checkout URL", async ({ request }) => {
    const res = await request.post(
      `${API_BASE}/api/v1/tours-operator/bookings/${bookingId}/pay`,
      { data: {} },
    );

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.status).toBe("PENDING");
    expect(body.checkoutUrl).toBeTruthy();
    // The mock stub always uses ORDER-TEST-123 in unit tests,
    // but in integration the mock bean returns a dynamic order ID.
    expect(body.checkoutUrl).toContain("clientSecret");

    // The E2E-only adapter encodes its order in its opaque mock client secret.
    const url = new URL(body.checkoutUrl);
    const token = url.searchParams.get("clientSecret") ?? "";
    paymobOrderId = token.split("_").slice(1).join("_") || token;
    expect(paymobOrderId).toBeTruthy();
  });

  // ── E2b: Idempotent initiate ───────────────────────────────────────────────

  test("E2b — second pay call is idempotent (200)", async ({ request }) => {
    const res = await request.post(
      `${API_BASE}/api/v1/tours-operator/bookings/${bookingId}/pay`,
      { data: {} },
    );
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe("PENDING");
    expect(body.checkoutUrl).toBeTruthy();
  });

  // ── E3: Valid webhook confirms booking ─────────────────────────────────────

  test("E3 — valid success webhook confirms booking and marks payment PAID", async ({ request }) => {
    const txnId = `E2E-TXN-${Date.now()}`;
    const res = await request.post(
      `${API_BASE}/api/v1/tours-operator/payments/paymob-callback?hmac=${VALID_MOCK_HMAC}`,
      {
        headers: { "Content-Type": "application/json" },
        data: webhookBody(paymobOrderId, txnId, 7000 /* 2 adults × 3500 cents */),
      },
    );

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe("confirmed");
  });

  // ── E4: Duplicate webhook is idempotent ───────────────────────────────────

  test("E4 — duplicate webhook returns already_processed", async ({ request }) => {
    const txnId = `E2E-TXN-DUP-${Date.now()}`;
    const res = await request.post(
      `${API_BASE}/api/v1/tours-operator/payments/paymob-callback?hmac=${VALID_MOCK_HMAC}`,
      {
        headers: { "Content-Type": "application/json" },
        data: webhookBody(paymobOrderId, txnId, 7000),
      },
    );

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe("already_processed");
  });

  // ── E5: Invalid HMAC is rejected ──────────────────────────────────────────

  test("E5 — invalid HMAC returns 400 invalid_signature", async ({ request }) => {
    const res = await request.post(
      `${API_BASE}/api/v1/tours-operator/payments/paymob-callback?hmac=WRONG`,
      {
        headers: { "Content-Type": "application/json" },
        data: webhookBody(paymobOrderId, `E2E-TXN-BAD-${Date.now()}`, 7000),
      },
    );

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe("invalid_signature");
  });

  // ── E6: Payment status endpoint reflects PAID ─────────────────────────────

  test("E6 — payment status endpoint returns PAID", async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/tours-operator/bookings/${bookingId}/payment-status`,
    );

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe("PAID");
    expect(body.paidAt).toBeTruthy();
  });

  // ── E7: Booking status is CONFIRMED ───────────────────────────────────────

  test("E7 — booking is CONFIRMED after successful webhook", async ({ request }) => {
    // Use the staff session to look up the booking status via ERP API
    // First authenticate as staff
    const loginRes = await request.post(`${API_BASE}/api/v1/identity/login`, {
      headers: { Host: STAFF_HOST_HEADER },
      data: { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD },
    });
    expect(loginRes.status()).toBe(200);
    const { token } = (await loginRes.json()) as { token: string };
    expect(token).toBeTruthy();

    // Identity uses an explicit bearer token rather than a cookie. Get the
    // staff-only booking detail with the same contract the ERP uses.
    const bookingRes = await request.get(
      `${API_BASE}/api/v1/tours-operator/bookings/${bookingId}`,
      { headers: { Authorization: `Bearer ${token}`, Host: STAFF_HOST_HEADER } },
    );
    expect(bookingRes.status()).toBe(200);
    const booking = await bookingRes.json();
    expect(booking.status).toBe("CONFIRMED");
    expect(booking.confirmedAt).toBeTruthy();
  });

  // ── E8: /booking/confirmation page shows the reference ────────────────────

  test("E8 — /booking/confirmation page renders the booking reference", async ({ page }) => {
    // Reproduce the real post-checkout handoff: the booking stays in this
    // tab's session storage, never in the URL/history/Referer.
    await page.goto(`${SITE_BASE}/`, { waitUntil: "networkidle" });
    await page.evaluate((booking) => {
      sessionStorage.setItem("sts.booking-confirmation.latest", JSON.stringify(booking));
    }, bookingConfirmation);
    await page.goto(`${SITE_BASE}/booking/confirmation`, { waitUntil: "networkidle" });
    expect(new URL(page.url()).search).toBe("");

    await expect(page.getByText(bookingReference, { exact: true }).first()).toBeVisible();

    // The confirmation heading should be present
    await expect(
      page.getByText("Booking Confirmed", { exact: false }),
    ).toBeVisible();
  });

  test("E8b — a NEW booking in mutable session storage cannot forge confirmation", async ({ page, request }) => {
    const createResponse = await request.post(`${API_BASE}/api/v1/tours-operator/bookings`, {
      data: {
        slotId,
        adultsCount: 1,
        childrenCount: 0,
        customer: {
          fullName: "E2E Unpaid Customer",
          phone: "+20100000198",
          nationality: "EG",
          email: null,
        },
        hotelName: "E2E Unpaid Hotel",
        specialRequests: null,
        locale: "en",
      },
    });
    expect(createResponse.status()).toBe(201);
    const unpaidBooking = await createResponse.json();
    expect(unpaidBooking.status).toBe("NEW");

    await page.goto(`${SITE_BASE}/`, { waitUntil: "networkidle" });
    await page.evaluate((booking) => {
      sessionStorage.setItem("sts.booking-confirmation.latest", JSON.stringify(booking));
    }, unpaidBooking);
    await page.goto(`${SITE_BASE}/booking/confirmation`, { waitUntil: "networkidle" });

    await expect(page.getByRole("heading", { name: "Payment is not confirmed" })).toBeVisible();
    await expect(page.getByText("Booking Confirmed", { exact: false })).toHaveCount(0);
  });

  // ── E9: ERP bookings page shows the confirmed booking ─────────────────────

  test("E9 — ERP bookings page shows the confirmed booking with CONFIRMED status", async ({ page }) => {
    // Login to ERP
    await page.goto(`${STAFF_BASE}/login`, { waitUntil: "networkidle" });
    await page.locator("#email").fill(E2E_STAFF_EMAIL);
    await page.locator("#password").fill(E2E_STAFF_PASSWORD);
    const signInButton = page.getByRole("button", { name: "Sign in" });
    await expect(signInButton).toBeVisible();
    expect(
      await signInButton.evaluate((button) => getComputedStyle(button).backgroundColor),
    ).not.toBe("rgba(0, 0, 0, 0)");
    await signInButton.click();
    await expect(page).toHaveURL(`${STAFF_BASE}/`);
    await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
    await expect(page.getByText(E2E_STAFF_EMAIL)).toBeVisible();

    // Navigate to Safari Tours ERP bookings
    // The ERP is served on the same base URL in this config
    await page.goto(`${STAFF_BASE}/bookings`, { waitUntil: "networkidle" });

    // Verify the booking appears with CONFIRMED status
    // The booking row is identified by the reference
    const bookingRow = page.locator("tbody tr", {
      hasText: bookingReference,
    }).first();
    await expect(bookingRow).toBeVisible();
    await expect(bookingRow.getByText(/CONFIRMED/i)).toBeVisible();

    await page.goto(`${STAFF_BASE}/`, { waitUntil: "networkidle" });
    const issuedToken = await page.evaluate(() => {
      const raw = sessionStorage.getItem("wego_auth_session");
      return raw ? String((JSON.parse(raw) as { token?: string }).token ?? "") : "";
    });
    expect(issuedToken).toBeTruthy();
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL(`${STAFF_BASE}/login`);
    const revoked = await page.request.get(`${API_BASE}/api/v1/identity/me`, {
      headers: { Authorization: `Bearer ${issuedToken}`, Host: STAFF_HOST_HEADER },
    });
    expect(revoked.status()).toBe(401);
  });

  test("E10 — browser checkout reaches backend-confirmed confirmation", async ({ page }) => {
    let callbackFailure = "";
    await page.route("https://mock.paymob.test/**", async (route) => {
      const checkoutUrl = new URL(route.request().url());
      const token = checkoutUrl.searchParams.get("clientSecret") ?? "";
      const orderId = token.split("_").slice(1).join("_");
      if (!orderId) {
        callbackFailure = "Mock checkout did not contain an order id";
      } else {
        const callback = await page.request.post(
          `${API_BASE}/api/v1/tours-operator/payments/paymob-callback?hmac=${VALID_MOCK_HMAC}`,
          {
            headers: { "Content-Type": "application/json" },
            data: webhookBody(orderId, `E2E-UI-TXN-${Date.now()}`, 3500),
          },
        );
        if (!callback.ok()) callbackFailure = `Webhook failed with ${callback.status()}`;
      }
      await route.fulfill({
        status: 302,
        headers: { Location: `${SITE_BASE}/booking/payment-result?provider=mock` },
        body: "",
      });
    });

    const bookingUrl = new URL(`${SITE_BASE}/booking/${slotId}`);
    bookingUrl.searchParams.set("adults", "1");
    bookingUrl.searchParams.set("children", "0");
    bookingUrl.searchParams.set("tourId", tourId);
    bookingUrl.searchParams.set("date", slotDate);
    bookingUrl.searchParams.set("timeSlot", "MORNING");
    await page.goto(bookingUrl.toString(), { waitUntil: "networkidle" });
    await page.locator("#fullName").fill("E2E Browser Customer");
    await page.locator("#phone").fill("+20100000123");
    await page.locator("#nationality").selectOption("EG");
    await page.locator("#email").fill("browser-checkout@example.com");
    await page.locator("#hotelName").fill("E2E Browser Hotel");
    await page.getByRole("button", { name: /Continue/ }).click();
    await page.getByRole("checkbox").check();
    await page.getByRole("button", { name: /Confirm & Pay/ }).click();

    // Public pages live under a locale prefix; the language-less return URL redirects to it.
    await expect(page).toHaveURL(new RegExp(`^${SITE_BASE}/(en|ar|ru|it)/booking/confirmation$`), { timeout: 20_000 });
    expect(callbackFailure).toBe("");
    await expect(page.getByText("Booking Confirmed", { exact: false })).toBeVisible();
    expect(new URL(page.url()).search).toBe("");
  });
});
