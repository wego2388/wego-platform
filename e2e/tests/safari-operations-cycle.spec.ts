/** Owner-authorized rehearsal. Synthetic records stay in an opted-in local DB. */
import { expect, request as requests, test, type APIRequestContext } from "@playwright/test";
import { AxeBuilder } from "@axe-core/playwright";
import { randomUUID } from "node:crypto";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";

const SITE = process.env.WEGO_STS_SITE_BASE_URL ?? "http://127.0.0.1:58080";
const STAFF = process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58080";
const root = "/api/v1/tours-operator";

test("synthetic full cycle: partners → booking → collection → documents → completed service → settlements → two-person cash close", async ({ page, request }) => {
  test.setTimeout(120_000);
  if (process.env.WEGO_SAFARI_OFFICE_E2E_CONFIRM !== "yes-this-is-a-disposable-office-stack" || !["127.0.0.1", "localhost"].includes(new URL(SITE).hostname) || new URL(STAFF).hostname !== "staff.localhost") throw new Error("Refusing operations mutations outside an explicitly confirmed disposable localhost stack");
  page.setDefaultTimeout(15_000);
  const day = new Intl.DateTimeFormat("sv-SE", { timeZone: "Africa/Cairo" }).format(new Date());
  const unique = randomUUID();
  async function api(ctx: APIRequestContext, method: string, path: string, token?: string, data?: unknown) {
    const response = await ctx.fetch(`${SITE}${path}`, { method, timeout: 15_000, headers: { Host: new URL(STAFF).host, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(data === undefined ? {} : { data }) });
    expect(response.status(), `${method} ${path}: HTTP ${response.status()}`).toBeGreaterThanOrEqual(200);
    expect(response.status(), `${method} ${path}: HTTP ${response.status()}`).toBeLessThan(300);
    return response.status() === 204 ? null : response.json();
  }
  const login = await api(request, "POST", "/api/v1/identity/login", undefined, { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD });
  const auth = { ...(await api(request, "GET", "/api/v1/identity/me", login.token)), token: login.token };
  const cashierPassword = `Test-only-${randomUUID()}`;
  const cashierEmail = `e2e-cashier-${unique}@example.invalid`;
  let cashierId = "";
  let tourId = "";
  let closed = false;
  try {
    // A second actual identity is required: never bypass the four-eyes rules.
    const cashier = await api(request, "POST", "/api/v1/identity/users", auth.token, { email: cashierEmail, password: cashierPassword, roleCodes: ["platform-admin"] });
    cashierId = cashier.id;
    const second = await api(request, "POST", "/api/v1/identity/login", undefined, { email: cashierEmail, password: cashierPassword });
    const tour = await api(request, "POST", `${root}/staff/tours`, auth.token, { slug: `e2e-cycle-${unique}`, nameEn: `TEST ONLY — Complete operations cycle ${unique.slice(0,8)}`, category: "DESERT", durationText: "2 hours — test only", priceAdultCents: 3500, priceChildCents: 1750, capacity: 8, availableTimeSlots: ["MORNING"], sortOrder: 9999, cancellationPolicy: "STANDARD", tourType: "TOUR" });
    tourId = tour.id;
    // Exercise the native numeric form, not only the API: Vue coerces 24 into
    // a number and the old .trim() handler hung before sending any request.
    await page.context().addCookies([{ name: "sts_staff_locale", value: "en", url: STAFF, sameSite: "Lax" }]);
    await page.addInitScript(session => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), auth);
    await page.goto(`${STAFF}/suppliers`);
    await expect(page.locator("main").getByRole("status")).toHaveCount(0);
    await page.getByRole("button", { name: "Add", exact: true }).click();
    const supplierCode = `TEST-${unique.slice(0,8).toUpperCase()}`;
    await page.getByLabel("Code", { exact: true }).fill(supplierCode);
    await page.getByLabel("Name", { exact: true }).fill("TEST ONLY — Supplier");
    await page.getByRole("combobox", { name: /^Service type/ }).selectOption("QUAD_BUGGY_SAFARI");
    await page.getByLabel("Notice (hours)", { exact: true }).fill("24");
    await page.getByRole("combobox", { name: /^Currency/ }).selectOption("EUR");
    await page.getByRole("combobox", { name: /^Pricing basis/ }).selectOption("PER_PERSON");
    await page.locator(`input[type="checkbox"][value="${tour.id}"]`).check();
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.locator("main").getByRole("status").filter({ hasText: /^Saved\.$/ })).toHaveText("Saved.");
    const supplier = (await api(request, "GET", `${root}/staff/suppliers`, auth.token)).find((row: { code: string }) => row.code === supplierCode);
    expect(supplier.noticeHours).toBe(24); expect(supplier.tourIds).toEqual([tour.id]);
    expect(supplier.active).toBe(true);
    // Another native numeric model: keep a saved zero distinct from unknown.
    await page.goto(`${STAFF}/tours/${tour.id}/content`);
    const sharedFacts = page.getByRole("region", { name: "Shared tour facts", exact: true });
    await page.locator("#facts-age").fill("0");
    await sharedFacts.getByRole("button", { name: "Save draft", exact: true }).click();
    await expect(page.locator("main").getByRole("status")).toHaveText("Draft saved and refreshed from the server.");
    const savedFacts = await api(request, "GET", `${root}/staff/tours/${tour.id}/content`, auth.token);
    expect(savedFacts.facts.find((fact: { stage: string }) => fact.stage === "DRAFT").document.minimumAge).toBe(0);
    const driver = await api(request, "POST", `${root}/staff/drivers`, auth.token, { name: "TEST ONLY — Driver, not a licensed real person", engagementType: "PER_TRIP", licenceValidUntil: `${Number(day.slice(0,4))+2}-12-31`, active: true });
    const vehicle = await api(request, "POST", `${root}/staff/vehicles`, auth.token, { label: "TEST ONLY — Synthetic 8-seat van", vehicleType: "MINIBUS", seats: 8, ownership: "OWNED", active: true });
    await api(request, "PATCH", `${root}/staff/tours/${tour.id}/activate`, auth.token);
    const slot = await api(request, "POST", `${root}/staff/tours/${tour.id}/slots`, auth.token, { date: day, timeSlot: "MORNING", capacity: 8 });
    await api(request, "PUT", `${root}/staff/slots/${slot.id}/assignment`, auth.token, { driverId: driver.id, vehicleId: vehicle.id, supplierIds: [supplier.id], expectedRevision: 0, supplierNote: "Synthetic rehearsal only — no real service order" });
    await api(request, "POST", `${root}/staff/costs`, auth.token, { clientRequestId: randomUUID(), tourId: tour.id, supplierId: supplier.id, category: "SUPPLIER", label: "TEST ONLY — supplier cost", basis: "PER_PERSON", currency: "EUR", amount: 10, childAmount: 5, validFrom: day, validUntil: day, note: "Temporary test amounts, never a supplier agreement" });
    await api(request, "POST", `${root}/staff/costs`, auth.token, { clientRequestId: randomUUID(), driverId: driver.id, category: "DRIVER", label: "TEST ONLY — driver cost", basis: "PER_DEPARTURE", currency: "EUR", amount: 5, validFrom: day, validUntil: day });
    const booking = await api(request, "POST", `${root}/staff/bookings`, auth.token, { clientRequestId: randomUUID(), slotId: slot.id, adultsCount: 2, childrenCount: 0, customer: { fullName: "Synthetic operations guest", phone: "+200000000000", nationality: "EG" }, hotelName: "TEST ONLY — Fixture hotel", locale: "ar" });
    expect(booking.status).toBe("CONFIRMED"); expect(booking.officePayment.state).toBe("UNPAID");
    expect(booking.totalPrice).toEqual({ amount: "70.00", currencyCode: "EUR" });
    const collectionBody = { clientRequestId: randomUUID(), method: "CASH_AT_OFFICE", currency: "EUR", amount: 70 };
    const collection = await api(request, "POST", `${root}/staff/bookings/${booking.id}/collections`, second.token, collectionBody);
    expect(collection.officePayment.state).toBe("PAID");
    expect((await api(request, "POST", `${root}/staff/bookings/${booking.id}/collections`, second.token, collectionBody)).entry.id).toBe(collection.entry.id);
    const documents = [
      [`bookings/${booking.id}/voucher`, { language: "ar" }],
      [`collections/${collection.entry.id}/receipt`, { language: "ar" }],
      ["run-sheet", { date: day, language: "ar" }],
      [`slots/${slot.id}/driver-sheet`, { language: "ar" }],
      [`slots/${slot.id}/suppliers/${supplier.id}/supplier-order`, { language: "ar" }],
    ] as const;
    for (const [path, body] of documents) expect((await api(request, "POST", `${root}/documents/${path}`, auth.token, body)).document.number).toBeTruthy();
    const driverSheet = await api(request, "POST", `${root}/documents/slots/${slot.id}/driver-sheet`, auth.token, { language: "en" });
    expect(JSON.stringify(driverSheet.data)).not.toContain("+200000000000");
    expect((await api(request, "POST", `${root}/bookings/${booking.id}/complete`, auth.token)).status).toBe("COMPLETED");
    const profit = await api(request, "GET", `${root}/staff/finance/profitability?from=${day}&to=${day}&groupBy=BOOKING`, auth.token);
    const actual = profit.groups.find((group: { bookingId: string }) => group.bookingId === booking.id);
    expect(actual.totals.officeRevenue).toEqual({ amount: "70.00", currencyCode: "EUR" });
    expect(actual.totals.cost).toEqual({ amount: "25.00", currencyCode: "EUR" });
    expect(actual.totals.profit).toEqual({ amount: "45.00", currencyCode: "EUR" });
    for (const [party, id, amount] of [["suppliers", supplier.id, 20], ["drivers", driver.id, 5]] as const) {
      const statementPath = `${root}/staff/payables/${party}/${id}`;
      const before = await api(request, "GET", `${statementPath}/statement?from=${day}&to=${day}`, auth.token);
      expect(before.balances.find((balance: { currency: string }) => balance.currency === "EUR").closing.amount).toBe(`${amount}.00`);
      const approval = await api(request, "POST", `${statementPath}/approvals`, auth.token, { clientRequestId: randomUUID(), currency: "EUR", amount, note: "Synthetic four-eyes settlement rehearsal" });
      const paymentBody = { clientRequestId: randomUUID(), method: "CASH", currency: "EUR", amount, approvalId: approval.id, note: "TEST ONLY" };
      const paid = await api(request, "POST", `${statementPath}/payments`, second.token, paymentBody);
      expect((await api(request, "POST", `${statementPath}/payments`, second.token, paymentBody)).id).toBe(paid.id);
      const after = await api(request, "GET", `${statementPath}/statement?from=${day}&to=${day}`, auth.token);
      expect(after.balances.find((balance: { currency: string }) => balance.currency === "EUR").closing.amount).toBe("0.00");
      expect((await api(request, "POST", `${root}/documents/payables/${party}/${id}/statement`, auth.token, { from: day, to: day, language: "ar" })).document.number).toBeTruthy();
    }
    const cash = await api(request, "GET", `${root}/staff/cash-box?date=${day}&currency=EUR`, auth.token);
    // Other completed fixtures may exist locally. Count the actual aggregate,
    // not the current booking's 45 EUR as though it were the entire drawer.
    expect((await api(request, "POST", `${root}/staff/cash-box/${day}/EUR/count`, second.token, { counted: Number(cash.expected.amount), note: "Synthetic local rehearsal" })).state).toBe("COUNTED");
    const forbidden = await request.post(`${SITE}${root}/staff/cash-box/${day}/EUR/confirm`, { headers: { Host: new URL(STAFF).host, Authorization: `Bearer ${second.token}` } });
    expect(forbidden.status()).toBe(403);
    expect((await api(request, "POST", `${root}/staff/cash-box/${day}/EUR/confirm`, auth.token)).state).toBe("CLOSED");
    closed = true;
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.context().addCookies([{ name: "sts_staff_locale", value: "ar", url: STAFF, sameSite: "Lax" }]);
    await page.addInitScript(session => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), auth);
    await page.goto(`${STAFF}/bookings/${booking.id}`);
    await expect(page.locator("main")).toContainText(booking.reference);
    expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
    await page.screenshot({ path: test.info().outputPath("completed-operations-booking-ar.png"), fullPage: true });
    await page.goto(`${STAFF}/cash-box`);
    await expect(page.locator("main")).toContainText("مقفول");
    await page.screenshot({ path: test.info().outputPath("two-person-cash-close-ar.png"), fullPage: true });
  } finally {
    // Always attempt every cleanup, including disabling the synthetic account,
    // even if a previous cleanup fails. Never delete append-only money history.
    // The test fixture request may already be disposed on test timeout. Use a
    // separately-owned, bounded context so cleanup still has a working channel.
    const cleanupRequest = await requests.newContext();
    try {
      const cleanups = await Promise.allSettled([
        closed ? api(cleanupRequest, "POST", `${root}/staff/cash-box/${day}/EUR/reopen`, auth.token, { reason: "End disposable test: reopen to allow subsequent local fixtures" }) : Promise.resolve(),
        tourId ? api(cleanupRequest, "PATCH", `${root}/staff/tours/${tourId}/deactivate`, auth.token) : Promise.resolve(),
        cashierId ? api(cleanupRequest, "POST", `/api/v1/identity/users/${cashierId}/disable`, auth.token) : Promise.resolve(),
      ]);
      expect(cleanups.map(result => result.status), "Reopen, deactivate and disable cleanup results").toEqual(["fulfilled", "fulfilled", "fulfilled"]);
    } finally { await cleanupRequest.dispose(); }
  }
});
