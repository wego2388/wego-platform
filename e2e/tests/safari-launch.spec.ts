/**
 * WEGO-016-QA — launch-matrix journeys not covered by safari-checkout/site:
 *
 *   L1 — Arabic on a phone: RTL, no sideways scroll, failed card payment
 *        shows "payment not completed" and never confirms the booking.
 *   L2 — Russian and Italian checkout forms render in their language.
 *   L3 — Emergency sales switch: the site warns, the API refuses, resume works.
 *   L4 — Staff cancellation is visible to the customer on "My booking".
 *   L5 — A party larger than the places left is refused.
 *
 * Runs after safari-checkout.spec.ts against the same compose stack with the
 * mock Paymob client. It always resumes online sales, even on failure.
 */
import { devices, expect, test, type APIRequestContext } from "@playwright/test";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";

const API_BASE = process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58080";
const SITE = (process.env.WEGO_STS_SITE_BASE_URL ?? API_BASE).replace(/\/+$/, "");
const VALID_MOCK_HMAC = "valid-hmac";
const E2E_TOUR_SLUG = "e2e-desert-quad-safari";

let tourId: string;
let slotId: string;
let slotDate: string;

function bookingPath(locale: string, adults = 1): string {
  const url = new URL(`${SITE}/${locale}/booking/${slotId}`);
  url.searchParams.set("adults", String(adults));
  url.searchParams.set("children", "0");
  url.searchParams.set("tourId", tourId);
  url.searchParams.set("date", slotDate);
  url.searchParams.set("timeSlot", "MORNING");
  return url.toString();
}

function bookingBody(adults: number, phone: string) {
  return {
    slotId,
    adultsCount: adults,
    childrenCount: 0,
    customer: { fullName: "E2E Launch Customer", phone, nationality: "EG", email: "launch@example.com" },
    hotelName: "E2E Launch Hotel",
    locale: "en",
  };
}

async function staffToken(request: APIRequestContext): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/identity/login`, {
    data: { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD },
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).token as string;
}

async function setSales(request: APIRequestContext, token: string, paused: boolean) {
  const res = await request.put(`${API_BASE}/api/v1/tours-operator/staff/sales-control`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { bookingsPaused: paused, paymentsPaused: paused, reason: paused ? "E2E launch drill" : null },
  });
  expect(res.status()).toBe(200);
}

test.describe.configure({ mode: "serial" });

test.describe("Safari launch matrix", () => {
  test.beforeAll(async ({ request }) => {
    const tours: Array<{ id: string; slug: string }> = await (
      await request.get(`${API_BASE}/api/v1/tours-operator/tours?activeOnly=true&size=100`)
    ).json();
    const tour = tours.find((t) => t.slug === E2E_TOUR_SLUG);
    if (!tour) throw new Error(`Seeded tour '${E2E_TOUR_SLUG}' not found — run 'pnpm run seed' first`);
    tourId = tour.id;

    const today = new Date();
    const nextMonday = new Date(today);
    nextMonday.setUTCDate(today.getUTCDate() + ((1 - today.getUTCDay() + 7) % 7 || 7));
    slotDate = nextMonday.toISOString().slice(0, 10);
    const to = new Date(nextMonday);
    to.setUTCDate(to.getUTCDate() + 1);
    const slots: Array<{ id: string; timeSlot: string }> = await (
      await request.get(`${API_BASE}/api/v1/tours-operator/tours/${tourId}/slots?from=${slotDate}&to=${to.toISOString().slice(0, 10)}`)
    ).json();
    const morning = slots.find((s) => s.timeSlot === "MORNING");
    if (!morning) throw new Error("Seeded MORNING slot not found");
    slotId = morning.id;
  });

  test.describe("on a phone", () => {
    // Phone viewport, touch and user agent on the same Chromium worker.
    const { viewport, userAgent, deviceScaleFactor, isMobile, hasTouch } = devices["iPhone 13"];
    test.use({ viewport, userAgent, deviceScaleFactor, isMobile, hasTouch });

    test("L1 — Arabic checkout is RTL without sideways scroll, and a failed payment is never confirmed", async ({ page }) => {
      let bookingId = "";
      await page.route("https://mock.paymob.test/**", async (route) => {
        const token = new URL(route.request().url()).searchParams.get("clientSecret") ?? "";
        const orderId = token.split("_").slice(1).join("_");
        await page.request.post(`${API_BASE}/api/v1/tours-operator/payments/paymob-callback?hmac=${VALID_MOCK_HMAC}`, {
          headers: { "Content-Type": "application/json" },
          data: JSON.stringify({
            obj: {
              id: `E2E-DECLINED-${Date.now()}`, success: false, pending: false, is_refunded: false,
              error_occured: false, has_parent_transaction: false, is_3d_secure: true, is_auth: false,
              is_capture: false, is_standalone_payment: true, is_voided: false, owner: "100001",
              amount_cents: 3500, currency: "EUR", created_at: "2026-09-28T10:00:00Z",
              integration_id: "100001", order: { id: orderId }, data: {},
            },
          }),
        });
        await route.fulfill({ status: 302, headers: { Location: `${SITE}/booking/payment-result?provider=mock` }, body: "" });
      });
      page.on("response", async (res) => {
        if (res.request().method() === "POST" && /\/api\/v1\/tours-operator\/bookings$/.test(res.url()) && res.status() === 201) {
          bookingId = (await res.json()).id;
        }
      });

      await page.goto(bookingPath("ar"), { waitUntil: "networkidle" });
      await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow).toBeLessThanOrEqual(1);

      await page.locator("#fullName").fill("عميل اختبار");
      await page.locator("#phone").fill("+20100000124");
      await page.locator("#nationality").selectOption("EG");
      await page.locator("#hotelName").fill("فندق الاختبار");
      await page.getByRole("button", { name: "متابعة" }).click();
      await page.getByRole("checkbox").check();
      await page.getByRole("button", { name: "تأكيد والدفع" }).click();

      await expect(page).toHaveURL(/\/ar\/booking\/payment-result/, { timeout: 20_000 });
      await expect(page.getByRole("heading", { name: "لم يكتمل الدفع" })).toBeVisible({ timeout: 30_000 });

      expect(bookingId).not.toBe("");
      const status = await (await page.request.get(`${API_BASE}/api/v1/tours-operator/bookings/${bookingId}/payment-status`)).json();
      expect(status.status).toBe("FAILED");
    });
  });

  test("L2 — Russian and Italian checkout forms are in their language", async ({ page }) => {
    for (const [locale, heading] of [["ru", /данные/i], ["it", /dati/i]] as const) {
      await page.goto(bookingPath(locale), { waitUntil: "networkidle" });
      await expect(page.locator("html")).toHaveAttribute("lang", new RegExp(`^${locale}`));
      await expect(page.locator("#details-heading")).toHaveText(heading);
    }
  });

  test("L3 — the emergency switch pauses online sales and resumes them", async ({ page, request }) => {
    const token = await staffToken(request);
    try {
      await setSales(request, token, true);
      expect(await (await request.get(`${API_BASE}/api/v1/tours-operator/sales-status`)).json())
        .toEqual({ bookingsOpen: false, paymentsOpen: false });

      await page.goto(bookingPath("en"), { waitUntil: "networkidle" });
      await expect(page.getByRole("alert").filter({ hasText: "Online booking is paused" })).toBeVisible();
      await expect(page.locator("#fullName")).toHaveCount(0);

      const refused = await request.post(`${API_BASE}/api/v1/tours-operator/bookings`, { data: bookingBody(1, "+20100000125") });
      expect(refused.status()).toBe(503);
      expect((await refused.json()).error).toBe("bookings_paused");
    } finally {
      await setSales(request, token, false);
    }
    await page.goto(bookingPath("en"), { waitUntil: "networkidle" });
    await expect(page.locator("#fullName")).toBeVisible();
  });

  test("L4 — a staff cancellation is visible to the customer on My booking", async ({ page, request }) => {
    const phone = "+20100000126";
    const created = await request.post(`${API_BASE}/api/v1/tours-operator/bookings`, { data: bookingBody(1, phone) });
    expect(created.status()).toBe(201);
    const booking = await created.json();

    const token = await staffToken(request);
    const cancelled = await request.post(`${API_BASE}/api/v1/tours-operator/bookings/${booking.id}/cancel`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { reason: "E2E launch drill" },
    });
    expect(cancelled.ok()).toBeTruthy();

    await page.goto(`${SITE}/en/my-booking`, { waitUntil: "networkidle" });
    await page.locator("#ref").fill(booking.reference);
    await page.locator("#lookup-phone").fill(phone);
    await page.locator("#lookup-phone").press("Enter");
    await expect(page.getByText("Cancelled", { exact: true })).toBeVisible({ timeout: 15_000 });
  });

  test("L5 — a party larger than the places left is refused", async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/tours-operator/bookings`, { data: bookingBody(50, "+20100000127") });
    expect(res.status()).toBe(409);
    expect((await res.json()).error).toBe("slot_fully_booked");
  });
});
