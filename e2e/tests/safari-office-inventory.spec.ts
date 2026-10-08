/** Real UI + API on an explicitly opted-in disposable Safari database. Never production. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";

const SITE = process.env.WEGO_STS_SITE_BASE_URL ?? "http://127.0.0.1:58080";
const STAFF = process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58080";
let auth: { token: string; email: string; roles: string[]; permissions: string[] };
const day = "2028-02-29";
test.beforeAll(async ({ request }) => {
  if (process.env.WEGO_SAFARI_OFFICE_E2E_CONFIRM !== "yes-this-is-a-disposable-office-stack" || !["127.0.0.1", "localhost"].includes(new URL(SITE).hostname) || new URL(STAFF).hostname !== "staff.localhost") throw new Error("Refusing office inventory mutations without explicit disposable localhost confirmation");
  const response = await request.post(`${SITE}/api/v1/identity/login`, { headers: { Host: new URL(STAFF).host }, data: { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD } });
  expect(response.status()).toBe(200);
  const login = await response.json();
  const me = await request.get(`${SITE}/api/v1/identity/me`, { headers: { Host: new URL(STAFF).host, Authorization: `Bearer ${login.token}` } });
  expect(me.status()).toBe(200); auth = { ...(await me.json()), token: login.token };
  expect(auth.permissions).toContain("tours-operator.slot:manage");
});

async function date(page: Page, id: string, iso: string) {
  await page.locator(`#${id}-day`).selectOption(String(Number(iso.slice(8))));
  await page.locator(`#${id}-month`).selectOption(String(Number(iso.slice(5, 7))));
  await page.locator(`#${id}-year`).fill(iso.slice(0, 4));
}
async function accessible(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
}

for (const locale of ["en", "ar"] as const) for (const width of [360, 1440]) {
  test(`${locale} ${width}px: inactive tour → confirmed departure → unpaid office booking → block/reopen`, async ({ page, request }) => {
    test.setTimeout(90_000);
    const label = (en: string, ar: string) => locale === "ar" ? ar : en;
    const slug = `e2e-office-${randomUUID()}`;
    const headers = { Host: new URL(STAFF).host, Authorization: `Bearer ${auth.token}` };
    let tourId = "";
    let bookingId = "";
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("dialog", (dialog) => dialog.accept());
    await page.setViewportSize({ width, height: 1000 });
    await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: STAFF, sameSite: "Lax" }]);
    await page.addInitScript((session) => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), auth);
    try {
      await page.goto(`${STAFF}/tours/new`);
      await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
      await page.locator("#tour-name").fill(`E2E office fixture ${slug}`);
      await page.locator("#tour-slug").fill(slug);
      await page.locator("#tour-duration").fill("2 hours (synthetic test)");
      await page.locator("#tour-capacity").fill("5");
      await page.locator("#tour-adult").fill("35.05"); await page.locator("#tour-child").fill("12.05");
      await page.getByLabel(label("Morning", "الصباح"), { exact: true }).check();
      await page.locator("#tour-confirm").check(); await accessible(page);
      const tourResponse = page.waitForResponse((r) => r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/staff/tours"));
      await page.getByRole("button", { name: label("Save inactive tour", "حفظ رحلة غير مفعلة"), exact: true }).click();
      const createdTour = await (await tourResponse).json(); tourId = createdTour.id;
      expect(createdTour.isActive).toBe(false); expect(createdTour.priceAdult).toEqual({ amount: "35.05", currencyCode: "EUR" });
      expect((await request.get(`${SITE}/api/v1/tours-operator/tours/${tourId}/slots/by-date?date=${day}`)).status()).toBe(200);
      expect(await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tourId}/slots/by-date?date=${day}`)).json()).toEqual([]);
      expect((await request.patch(`${SITE}/api/v1/tours-operator/staff/tours/${tourId}/activate`, { headers })).status()).toBe(204);

      await page.goto(`${STAFF}/bookings/new?tourId=${tourId}&date=${day}`);
      await expect(page.locator("#office-date-day")).toHaveValue("29");
      await expect(page.locator("#office-date-month")).toHaveValue("2");
      await expect(page.locator("#office-date-year")).toHaveValue("2028");
      await expect(page.getByText(label("No open departures for this tour on that date.", "لا توجد مواعيد متاحة لهذه الرحلة في هذا التاريخ."), { exact: true })).toBeVisible();
      await page.locator("summary").click();
      await page.locator("#departure-time").selectOption("MORNING"); await page.locator("#departure-capacity").fill("5");
      await page.getByLabel(label("I have confirmed this departure and its capacity with the operator.", "أكدت هذا الموعد وعدد الأماكن مع منفذ الرحلة."), { exact: true }).check();
      await accessible(page);
      const departureResponse = page.waitForResponse((r) => r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/slots"));
      await page.getByRole("button", { name: label("Save departure", "حفظ الموعد"), exact: true }).click();
      const departure = await (await departureResponse).json();
      expect(departure.date).toBe(day); expect(departure.available).toBe(5);
      await expect(page.locator("#office-slot")).toHaveValue(departure.id);
      await page.locator("#office-adults").fill("2"); await page.locator("#office-children").fill("1");
      await page.locator("#office-name").fill("E2EOfficeFixture"); await page.locator("#office-phone").fill("+201099888777"); await page.locator("#office-hotel").fill("E2E fixture hotel");
      const bookingResponse = page.waitForResponse((r) => r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/staff/bookings"));
      await page.locator('button[type="submit"]').click();
      const booking = await (await bookingResponse).json(); bookingId = booking.id;
      expect(booking.id).toBeTruthy(); expect(booking.status).toBe("CONFIRMED"); expect(booking.officePayment.state).toBe("UNPAID");
      expect(booking.totalPrice).toEqual({ amount: "82.15", currencyCode: "EUR" });
      await expect(page.locator("main")).toContainText(booking.reference);
      expect(page.url()).not.toContain("E2EOfficeFixture"); expect(page.url()).not.toContain("201099888777");
      await accessible(page);
      // The existing server idempotency contract must replay without taking places twice.
      const original = (await bookingResponse).request().postDataJSON();
      const replay = await request.post(`${SITE}/api/v1/tours-operator/staff/bookings`, { headers, data: original });
      expect([200, 201]).toContain(replay.status()); expect((await replay.json()).id).toBe(booking.id);
      const dates = await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tourId}/slots/by-date?date=${day}`)).json();
      expect(dates[0].bookedCount).toBe(3); expect(dates[0].available).toBe(2);

      await page.goto(`${STAFF}/tours/${tourId}/slots`);
      await date(page, "calendar-jump", day);
      await expect(page.getByRole("table")).toContainText(locale === "ar" ? "٢/٥" : "2/5");
      await page.getByRole("button", { name: label("Stop new bookings", "إيقاف الحجوزات الجديدة"), exact: true }).click();
      await expect(page.getByRole("button", { name: label("Reopen bookings", "فتح الحجز مجددًا"), exact: true })).toBeVisible();
      await page.reload(); await date(page, "calendar-jump", day);
      await expect(page.getByRole("button", { name: label("Reopen bookings", "فتح الحجز مجددًا"), exact: true })).toBeVisible();
      await page.getByRole("button", { name: label("Reopen bookings", "فتح الحجز مجددًا"), exact: true }).click();
      await expect(page.getByRole("table")).toContainText(locale === "ar" ? "٢/٥" : "2/5");
      await accessible(page);
      await page.screenshot({ path: test.info().outputPath(`office-calendar-${locale}-${width}.png`), fullPage: true });
      expect(errors).toEqual([]);
    } finally {
      if (bookingId) expect((await request.post(`${SITE}/api/v1/tours-operator/bookings/${bookingId}/cancel`, { headers, data: { reason: "Synthetic office E2E cleanup" } })).status()).toBe(200);
      if (tourId) expect((await request.patch(`${SITE}/api/v1/tours-operator/staff/tours/${tourId}/deactivate`, { headers })).status()).toBe(204);
    }
  });
}
