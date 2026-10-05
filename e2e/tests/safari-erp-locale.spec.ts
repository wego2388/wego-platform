/**
 * OPS2-A display/interaction evidence. All browser API responses below are
 * explicit fixtures; this suite does not log in to or mutate a real account.
 * ERP can run alone via WEGO_STS_STAFF_BASE_URL, or on the CI staff virtual host.
 */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const ERP = (process.env.WEGO_STS_STAFF_BASE_URL ?? (() => {
  const url = new URL(process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58080");
  url.hostname = "staff.localhost";
  return url.origin;
})()).replace(/\/+$/, "");
const DAY = "2026-10-03";
const SESSION = {
  token: "locale-test-fixture-token",
  email: "locale-fixture@example.com",
  roles: ["fixture-operator"],
  permissions: ["tours-operator.booking:view", "tours-operator.tour:view"],
};
const TOUR = {
  id: "fixture-tour", slug: "fixture-tour", nameEn: "Fixture desert tour",
  category: "DESERT", durationText: "Fixture duration", priceAdult: { amount: "35.00", currencyCode: "EUR" },
  priceChild: null, capacity: 20, availableTimeSlots: ["MORNING"], sortOrder: 1,
  isActive: true, priceBasis: "PER_PERSON", priceOptions: [],
};
const BOOKING = {
  id: "fixture-booking", reference: "FIXTURE-001", tourId: TOUR.id, slotId: "fixture-slot",
  tourDate: DAY, timeSlot: "MORNING", adultsCount: 2, childrenCount: 0,
  totalPrice: { amount: "70.00", currencyCode: "EUR" }, unit: null,
  customer: { fullName: "Fixture guest", nationality: "GB", phone: "+200000000000", email: null },
  hotelName: "Fixture hotel", hotelRoom: "12", specialRequests: null,
  status: "CONFIRMED", createdAt: "2026-10-02T10:00:00Z",
};

async function mockApi(page: Page) {
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/identity/login")) return route.fulfill({ json: { token: SESSION.token } });
    if (path.endsWith("/identity/me")) return route.fulfill({ json: SESSION });
    if (path.endsWith("/identity/logout")) return route.fulfill({ status: 204 });
    if (path.endsWith("/sales-status")) return route.fulfill({ json: { bookingsOpen: true, paymentsOpen: true } });
    if (path.endsWith("/staff/tours")) return route.fulfill({ json: [TOUR] });
    if (path.endsWith("/bookings")) return route.fulfill({ json: [BOOKING] });
    if (path.endsWith("/slots/by-date")) return route.fulfill({ json: [{ id: BOOKING.slotId, tourId: TOUR.id, date: DAY, timeSlot: "MORNING", capacity: 20, bookedCount: 2, isBlocked: false }] });
    return route.fulfill({ status: 404, json: { error: "unexpected_fixture_request" } });
  });
}

async function setPreference(page: Page, locale: "en" | "ar", authenticated = false) {
  await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: ERP, sameSite: "Lax" }]);
  if (authenticated) await page.addInitScript((session) => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), SESSION);
  await mockApi(page);
}

async function assertNoOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}

test.describe("Safari ERP EN/AR foundation", () => {
  test("Arabic login is rendered by SSR before JavaScript and invalid preference falls back safely", async ({ page }) => {
    await setPreference(page, "ar");
    // Chromium has an explicit staff.localhost resolver rule; Node's HTTP
    // client does not. Keep the actual virtual host and browser locale cookie
    // when dialing loopback, without changing the system DNS/hosts file.
    const loginUrl = new URL(`${ERP}/login`);
    const staffHost = loginUrl.host;
    if (loginUrl.hostname === "staff.localhost") loginUrl.hostname = "127.0.0.1";
    const readLogin = async () => {
      const preference = (await page.context().cookies(ERP)).find((cookie) => cookie.name === "sts_staff_locale");
      return page.request.get(loginUrl.href, { headers: { Host: staffHost, Cookie: `sts_staff_locale=${encodeURIComponent(preference?.value ?? "")}` } });
    };
    const html = await (await readLogin()).text();
    expect(html).toMatch(/<html[^>]*lang="ar"/);
    expect(html).toMatch(/<html[^>]*dir="rtl"/);
    expect(html).toContain("تسجيل الدخول");
    await page.context().addCookies([{ name: "sts_staff_locale", value: "ru", url: ERP }]);
    const fallback = await (await readLogin()).text();
    expect(fallback).toMatch(/<html[^>]*lang="en"/);
    expect(fallback).toContain("Sign in");
  });

  test("language switch retains form input, locale survives navigation/reload, and auth payload stays unchanged", async ({ page }) => {
    await setPreference(page, "en");
    await page.goto(`${ERP}/login`);
    await page.getByLabel("Email", { exact: true }).fill("fixture@example.com");
    await page.getByLabel("Password", { exact: true }).fill("Fixture-only-password");
    await page.getByRole("button", { name: "العربية", exact: true }).click();
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByLabel("البريد الإلكتروني")).toHaveValue("fixture@example.com");
    await expect(page.getByLabel("كلمة المرور")).toHaveValue("Fixture-only-password");
    const loginRequest = page.waitForRequest((request) => new URL(request.url()).pathname.endsWith("/identity/login"));
    await page.getByRole("button", { name: "تسجيل الدخول", exact: true }).click();
    expect((await loginRequest).postDataJSON()).toEqual({ email: "fixture@example.com", password: "Fixture-only-password" });
    await expect(page.getByRole("heading", { name: "الرئيسية", exact: true })).toBeVisible();
    await page.reload();
    await expect(page.locator("html")).toHaveAttribute("lang", "ar");
    await expect(page.getByRole("heading", { name: "الرئيسية", exact: true })).toBeVisible();
    expect(new URL(page.url()).search).not.toMatch(/phone|email|password|token/);
  });

  test("existing login errors translate immediately without resubmitting credentials", async ({ page }) => {
    await setPreference(page, "en");
    await page.route("**/api/v1/identity/login", (route) => route.fulfill({ status: 429, json: { error: "rate_limited" } }));
    await page.goto(`${ERP}/login`);
    await page.getByLabel("Email", { exact: true }).fill("fixture@example.com");
    await page.getByLabel("Password", { exact: true }).fill("Fixture-only-password");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("Too many attempts");
    await page.getByRole("button", { name: "العربية", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("محاولات كثيرة");
  });

  for (const locale of ["en", "ar"] as const) {
    test(`${locale}: unknown route keeps a real 404 with a localized error screen`, async ({ page }) => {
      await setPreference(page, locale);
      const response = await page.goto(`${ERP}/no-such-erp-page`);
      expect(response?.status()).toBe(404);
      await expect(page.locator("html")).toHaveAttribute("lang", locale);
      await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
      await expect(page.getByRole("heading", { name: locale === "ar" ? "الصفحة غير موجودة" : "Page not found", exact: true })).toBeVisible();
    });

    for (const width of [360, 768, 1024, 1440]) {
      test(`${locale} at ${width}px: login, overview and today's sheet have usable controls without page overflow`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await setPreference(page, locale);
        await page.goto(`${ERP}/login`);
        await expect(page.getByRole("heading", { name: locale === "ar" ? "تسجيل الدخول" : "Sign in", exact: true })).toBeVisible();
        await assertNoOverflow(page);
        await page.evaluate((session) => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), SESSION);
        await page.goto(ERP);
        await expect(page.getByRole("heading", { name: locale === "ar" ? "الرئيسية" : "Overview", exact: true })).toBeVisible();
        await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
        await expect(page.getByRole("link", { name: "FIXTURE-001", exact: true })).toBeVisible();
        const menu = page.getByRole("button", { name: locale === "ar" ? "القائمة" : "Menu", exact: true });
        if (width < 1280) {
          await menu.click();
          await expect(menu).toHaveAttribute("aria-expanded", "true");
        }
        await expect(page.getByRole("link", { name: locale === "ar" ? "المالية" : "Finance", exact: true })).toHaveCount(0);
        await assertNoOverflow(page);
        await page.goto(`${ERP}/today?date=${DAY}`);
        await expect(page.getByText(TOUR.nameEn, { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: locale === "ar" ? "طباعة" : "Print", exact: true })).toBeVisible();
        await assertNoOverflow(page);
        await page.screenshot({ path: test.info().outputPath(`today-${locale}-${width}.png`), fullPage: true });
      });
    }

    test(`${locale}: keyboard language control and WCAG AA on login/overview/today`, async ({ page }) => {
      await setPreference(page, locale);
      await page.goto(`${ERP}/login`);
      await page.evaluate((session) => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), SESSION);
      for (const path of ["/", `/today?date=${DAY}`]) {
        await page.goto(`${ERP}${path}`);
        await expect(page.getByText(BOOKING.customer.fullName, { exact: true }).filter({ visible: true }).first()).toBeVisible();
        const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
        expect(result.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(" ")).join(", ")}`)).toEqual([]);
      }
      await page.evaluate(() => sessionStorage.removeItem("wego_auth_session"));
      await page.goto(`${ERP}/login`, { waitUntil: "networkidle" });
      const switchButton = page.getByRole("button", { name: locale === "ar" ? "EN" : "العربية", exact: true });
      await switchButton.focus();
      await page.keyboard.press("Enter");
      await expect(switchButton).toHaveAttribute("aria-pressed", "true");
      const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
      expect(result.violations.map((violation) => violation.id)).toEqual([]);
    });
  }

  test("translated settings has Arabic content direction and print keeps Arabic run-sheet direction", async ({ page }) => {
    await setPreference(page, "ar", true);
    await page.goto(`${ERP}/settings`);
    await expect(page.getByRole("heading", { name: "الإعدادات", exact: true })).toBeVisible();
    await expect(page.getByRole("status").filter({ hasText: "هذه الصفحة متاحة بالإنجليزية" })).toHaveCount(0);
    await expect(page.locator('main').first().locator('xpath=..')).toHaveAttribute("lang", "ar");
    await expect(page.locator('main').first().locator('xpath=..')).toHaveAttribute("dir", "rtl");
    await page.goto(`${ERP}/today?date=${DAY}`);
    await expect(page.getByText(TOUR.nameEn, { exact: true })).toBeVisible();
    await page.emulateMedia({ media: "print" });
    await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    await expect(page.getByRole("button", { name: "طباعة", exact: true })).toBeHidden();
    await expect(page.getByRole("columnheader", { name: "الضيف", exact: true })).toBeVisible();
    await page.screenshot({ path: test.info().outputPath("today-ar-print.png"), fullPage: true });
  });
});
