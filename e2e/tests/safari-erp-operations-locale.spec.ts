/** OPS2-B: explicit browser fixtures, never real identities, emails or money. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const ERP = (process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58080").replace(/\/+$/, "");
type Locale = "en" | "ar";
type Request = { method: string; path: string; search: string; body: unknown };
const ALL_PERMISSIONS = ["tours-operator.booking:view", "tours-operator.notification:manage", "tours-operator.payment:view", "tours-operator.tour:view", "identity:user-view", "identity:user-manage", "identity:role-view", "identity:role-manage"];
const USER = { id: "fixture-user", email: "original-fixture@example.com", status: "ACTIVE", roles: ["fixture-role"], createdAt: "2026-10-01T10:00:00Z" };
const ROLE = { code: "fixture-role", description: "Original server description <b>not HTML</b>", permissions: ["tours-operator.booking:view"] };
const PERMISSIONS = [
  { code: "tours-operator.booking:view", description: "Original view description" },
  { code: "tours-operator.booking:cancel", description: "Original cancel description" },
  { code: "identity:user-view", description: "Original staff description" },
];
const TOUR = { id: "fixture-tour", slug: "original-tour-slug", nameEn: "Original tour", category: "DESERT", durationText: "Original duration", priceAdult: { amount: "35.05", currencyCode: "EUR" }, priceChild: null, capacity: 20, availableTimeSlots: ["MORNING"], sortOrder: 1, isActive: true, priceBasis: "PER_PERSON", priceOptions: [] };
const BOOKING = { id: "fixture-booking", reference: "FIXTURE-001", tourId: TOUR.id, slotId: "fixture-slot", tourDate: "2026-10-05", timeSlot: "MORNING", adultsCount: 1, childrenCount: 0, totalPrice: { amount: "35.05", currencyCode: "EUR" }, customer: { fullName: "Original fixture guest", nationality: "GB", phone: "+200000000000", email: "guest-fixture@example.com" }, hotelName: "Fixture hotel", hotelRoom: null, specialRequests: null, status: "NEW", createdAt: "2026-10-01T10:00:00Z" };
const LEDGER = { paymentId: "fixture-paid", bookingId: BOOKING.id, tourId: TOUR.id, adultsCount: 1, childrenCount: 0, amount: { amount: "35.05", currencyCode: "EUR" }, status: "PAID", createdAt: "2026-10-05T10:00:00Z", paidAt: "2026-10-05T10:00:00Z", revenueRecognisedAt: "2026-10-05T10:00:00Z", refundedAt: null, failedAt: null };
const NOTIFICATION = { id: "fixture-sent", bookingId: BOOKING.id, bookingReference: BOOKING.reference, kind: "BOOKING_CONFIRMED", status: "SENT", attemptCount: 1, resendCount: 2, lastError: null, createdAt: "2026-10-01T10:00:00Z", availableAt: "2026-10-01T10:00:00Z", sentAt: "2026-10-01T10:01:00Z" };
type Options = { permissions?: string[]; errorPath?: string; status?: number; errorCode?: string; reasonCode?: string; empty?: boolean; settingsData?: boolean; slowMessages?: boolean; slowFinance?: boolean; currencyMix?: boolean; paginatedLedger?: boolean };

async function fixtures(page: Page, locale: Locale, options: Options = {}) {
  await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: ERP, sameSite: "Lax" }]);
  await page.addInitScript((permissions) => sessionStorage.setItem("wego_auth_session", JSON.stringify({ token: "operations-fixture-token", email: "fixture@example.com", roles: ["fixture"], permissions })), options.permissions ?? ALL_PERMISSIONS);
  await page.clock.setFixedTime(new Date("2026-10-05T12:00:00Z"));
  const requests: Request[] = [];
  let sales = { bookingsPaused: false, paymentsPaused: false, reason: null as string | null, updatedAt: "2026-10-05T10:00:00Z" };
  let messageLoads = 0;
  let financeLoads = 0;
  let releaseFirst!: () => void;
  const firstGate = new Promise<void>((resolve) => { releaseFirst = resolve; });
  await page.route("**/api/**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    requests.push({ method: req.method(), path, search: url.search, body: req.postData() ? req.postDataJSON() : null });
    if (path.endsWith("/sales-status")) return route.fulfill({ json: { bookingsOpen: true, paymentsOpen: true } });
    if (options.errorPath && `${req.method()} ${path}`.includes(options.errorPath)) return route.fulfill({ status: options.status ?? 500, json: { error: options.errorCode ?? "fixture_failure" } });
    if (path.endsWith("/staff/notifications")) {
      const first = ++messageLoads === 1;
      if (options.slowMessages && first) await firstGate;
      const items = [NOTIFICATION, { ...NOTIFICATION, id: "fixture-failed", kind: "BOOKING_CANCELLED", status: "FAILED", lastError: options.reasonCode ?? "no_customer_email", sentAt: null }, { ...NOTIFICATION, id: "fixture-pending", kind: "REVIEW_REQUEST", status: "PENDING", sentAt: null }];
      return route.fulfill({ json: options.empty ? [] : options.slowMessages ? [{ ...NOTIFICATION, bookingReference: first ? "STALE-RESULT" : "CURRENT-RESULT" }] : items.filter((item) => !url.searchParams.get("status") || item.status === url.searchParams.get("status")) });
    }
    if (path.endsWith("/resend")) return route.fulfill({ status: 204 });
    if (path.endsWith("/staff/sales-control")) {
      if (req.method() === "PUT") sales = { ...sales, ...req.postDataJSON() };
      return route.fulfill({ json: sales });
    }
    if (path.endsWith("/settings")) return options.settingsData ? route.fulfill({ json: { companyName: "Original business label", companyPhone: "+200000000000", companyEmail: "fixture@example.com", companyAddress: "Original address", currency: "EUR", timezone: "Africa/Cairo", bookingExpiryMinutes: 30, whatsappNumber: "+200000000000" } }) : route.fulfill({ status: 404, json: { error: "not_found" } });
    if (path.endsWith("/bookings")) return route.fulfill({ json: options.empty ? [] : [BOOKING, { ...BOOKING, id: "fixture-cancelled", status: "CANCELLED", totalPrice: { amount: "0.05", currencyCode: "EUR" } }, { ...BOOKING, id: "fixture-egp", totalPrice: { amount: "200.05", currencyCode: "EGP" } }] });
    if (path.endsWith("/staff/tours")) return route.fulfill({ json: [TOUR] });
    if (path.endsWith("/staff/finance/office-summary")) return route.fulfill({ json: { from: url.searchParams.get("from"), to: url.searchParams.get("to"), collected: { amount: "0.00", currencyCode: "EUR" }, collectionsReversed: { amount: "0.00", currencyCode: "EUR" }, refunded: { amount: "0.00", currencyCode: "EUR" }, refundsReversed: { amount: "0.00", currencyCode: "EUR" }, net: { amount: "0.00", currencyCode: "EUR" }, collectionCount: 0, refundCount: 0 } });
    if (path.endsWith("/staff/payments")) {
      const first = ++financeLoads === 1;
      if (options.slowFinance && first) await firstGate;
      if (options.paginatedLedger && !url.searchParams.has("after")) return route.fulfill({ json: Array.from({ length: 50 }, (_, i) => ({ ...LEDGER, paymentId: `fixture-page-${i}` })) });
      return route.fulfill({ json: options.empty ? [] : options.currencyMix ? [LEDGER, { ...LEDGER, paymentId: "fixture-mixed", amount: { amount: "200.05", currencyCode: "EGP" } }] : [LEDGER, { ...LEDGER, paymentId: "fixture-refund", status: "REFUNDED", amount: { amount: "70.05", currencyCode: "EUR" }, revenueRecognisedAt: "2026-09-01T10:00:00Z", refundedAt: "2026-10-05T10:00:00Z" }] });
    }
    if (path.endsWith("/identity/users")) return route.fulfill({ json: req.method() === "POST" ? { ...USER, id: "new-fixture-user", email: req.postDataJSON().email, roles: req.postDataJSON().roleCodes } : options.empty ? [] : [USER] });
    if (path.endsWith("/identity/roles")) return route.fulfill({ json: req.method() === "POST" ? { ...ROLE, ...req.postDataJSON(), permissions: req.postDataJSON().permissionCodes } : [ROLE] });
    if (path.endsWith("/identity/permissions")) return route.fulfill({ json: PERMISSIONS });
    if (path.endsWith("/roles")) return route.fulfill({ json: { ...USER, roles: req.postDataJSON().roleCodes } });
    if (path.endsWith("/permissions")) return route.fulfill({ json: { ...ROLE, permissions: req.postDataJSON().permissionCodes } });
    if (path.endsWith("/disable") || path.endsWith("/enable") || path.endsWith("/reset-password")) return route.fulfill({ json: { ...USER, status: path.endsWith("/disable") ? "DISABLED" : "ACTIVE" } });
    return route.fulfill({ status: 404, json: { error: "unexpected_fixture_request" } });
  });
  return { requests, releaseFirst };
}
const writes = (requests: Request[]) => requests.filter((request) => request.method !== "GET");
async function accessible(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const violations = (await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations;
  expect(violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => ({ target: n.target, summary: n.failureSummary })) }))).toEqual([]);
}
const title = (locale: Locale, en: string, ar: string) => locale === "en" ? en : ar;
async function switchLanguage(page: Page, locale: Locale) { await page.getByRole("button", { name: title(locale, "العربية", "EN"), exact: true }).click(); }

test.describe("Safari ERP remaining operations EN/AR", () => {
  test.use({ timezoneId: "Africa/Cairo" });
  for (const locale of ["en", "ar"] as const) {
    for (const width of [360, 768, 1024, 1440]) {
      test(`${locale} ${width}px: seven pages localized, responsive, honest and accessible`, async ({ page }) => {
        test.setTimeout(90_000);
        await page.setViewportSize({ width, height: 900 });
        const { requests } = await fixtures(page, locale);
        for (const [route, en, ar] of [["notifications", "Customer messages", "رسائل العملاء"], ["reviews", "Reviews", "التقييمات"], ["sales", "Online sales", "البيع عبر الموقع"], ["settings", "Settings", "الإعدادات"], ["customers", "Customers", "العملاء"], ["finance", "Finance", "المالية"], ["staff", "Staff", "الموظفون"]]) {
          await page.goto(`${ERP}/${route}`, { waitUntil: "networkidle" });
          await expect(page.getByRole("heading", { level: 1, name: title(locale, en!, ar!), exact: true })).toBeVisible();
          await expect(page.locator("main").locator("xpath=..")).toHaveAttribute("lang", locale);
          await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
          await expect(page.getByRole("status").filter({ hasText: "هذه الصفحة متاحة بالإنجليزية" })).toHaveCount(0);
          if (route === "customers") {
            await expect(page.getByText(BOOKING.customer.fullName, { exact: true })).toBeVisible();
            await expect(page.locator(".money").first()).toContainText(title(locale, "35.10", "٣٥٫١٠"));
            await expect(page.locator(".money").nth(1)).toContainText(title(locale, "200.05", "٢٠٠٫٠٥"));
          }
          if (route === "finance") await expect(page.locator(".money").first()).toContainText(title(locale, "-€35.00", "-‏٣٥٫٠٠"));
          if (route === "reviews") { await expect(page.locator("main select")).toHaveCount(0); await expect(page.locator("main")).not.toContainText("Phase 5"); }
          await accessible(page);
          if (locale === "ar" && (width === 360 || width === 1440) && ["staff", "finance"].includes(route!)) await page.screenshot({ path: test.info().outputPath(`${route}-ar-${width}.png`), fullPage: true });
        }
        expect(writes(requests)).toEqual([]);
        expect(requests.filter((r) => r.path.endsWith("/bookings"))).toHaveLength(1);
        expect(requests.find((r) => r.path.endsWith("/bookings"))?.search).toBe("?page=0&size=200");
      });
    }
    test(`${locale}: sent resend requires confirmation; language preserves dialog and filter`, async ({ page }) => {
      const { requests } = await fixtures(page, locale);
      await page.goto(`${ERP}/notifications`);
      await page.locator("#status-filter").selectOption("SENT");
      await page.locator("main").getByRole("button", { name: title(locale, "Send again", "إعادة الإرسال"), exact: true }).click();
      const dialog = page.getByRole("dialog");
      await expect(dialog).toContainText(BOOKING.reference);
      await accessible(page);
      const readsBefore = requests.length;
      await dialog.getByRole("button", { name: title(locale, "العربية", "EN"), exact: true }).click();
      await expect(dialog).toContainText(title(locale, "وصلت هذه الرسالة", "The customer already received"));
      await expect(page.locator("#status-filter")).toHaveValue("SENT");
      expect(requests).toHaveLength(readsBefore);
      await dialog.getByRole("button", { name: title(locale, "إلغاء", "Cancel"), exact: true }).focus();
      await page.keyboard.press("Escape");
      await expect(dialog).not.toBeVisible();
      expect(writes(requests)).toEqual([]);
      await page.locator("main").getByRole("button", { name: title(locale, "إعادة الإرسال", "Send again"), exact: true }).click();
      await dialog.getByRole("button", { name: title(locale, "إعادة الإرسال", "Send again"), exact: true }).click();
      await expect(dialog).not.toBeVisible();
      expect(writes(requests)).toEqual([{ method: "POST", path: "/api/v1/tours-operator/staff/notifications/fixture-sent/resend", search: "", body: null }]);
    });
    test(`${locale}: unsaved sales form survives language switch with original PUT payload`, async ({ page }) => {
      const { requests } = await fixtures(page, locale);
      await page.goto(`${ERP}/sales`);
      await page.locator("#pause-payments").check();
      await page.getByRole("textbox").fill("  Original staff-only note  ");
      const before = requests.length;
      await switchLanguage(page, locale);
      await expect(page.locator("#pause-payments")).toBeChecked();
      await expect(page.getByRole("textbox")).toHaveValue("  Original staff-only note  ");
      expect(requests).toHaveLength(before);
      await page.getByRole("button", { name: title(locale, "حفظ", "Save"), exact: true }).click();
      await expect(page.getByRole("status").filter({ hasText: title(locale, "تم الحفظ", "Saved") })).toBeVisible();
      expect(writes(requests)).toEqual([{ method: "PUT", path: "/api/v1/tours-operator/staff/sales-control", search: "", body: { bookingsPaused: false, paymentsPaused: true, reason: "Original staff-only note" } }]);
    });
    test(`${locale}: staff forms/reset retain state across language switches; password cleared on Escape`, async ({ page }) => {
      const { requests } = await fixtures(page, locale);
      await page.goto(`${ERP}/staff`);
      await page.locator("#newUserEmail").fill("new-fixture@example.com");
      await page.locator("#newUserPassword").fill("Fixture-only-password");
      await page.locator("#newRoleCode").fill("original-code");
      await page.locator("#newRoleDescription").fill("Original description");
      await switchLanguage(page, locale);
      await expect(page.locator("#newUserEmail")).toHaveValue("new-fixture@example.com");
      await expect(page.locator("#newUserPassword")).toHaveValue("Fixture-only-password");
      await expect(page.locator("#newRoleCode")).toHaveValue("original-code");
      await page.getByRole("button", { name: title(locale, "إعادة تعيين كلمة المرور", "Reset password"), exact: true }).click();
      const dialog = page.getByRole("dialog");
      await dialog.locator("#resetPassword").fill("Reset-fixture-password");
      await dialog.getByRole("button", { name: title(locale, "EN", "العربية"), exact: true }).click();
      await expect(dialog.locator("#resetPassword")).toHaveValue("Reset-fixture-password");
      await accessible(page);
      await page.keyboard.press("Escape");
      await page.getByRole("button", { name: title(locale, "Reset password", "إعادة تعيين كلمة المرور"), exact: true }).click();
      await expect(dialog.locator("#resetPassword")).toHaveValue("");
      expect(writes(requests)).toEqual([]);
    });
    test(`${locale}: customer search and finance date edits survive language without refetch`, async ({ page }) => {
      const { requests } = await fixtures(page, locale);
      await page.goto(`${ERP}/customers`);
      await page.getByRole("searchbox").fill("  Original  ");
      await page.getByRole("combobox").selectOption("GB");
      const before = requests.length;
      await switchLanguage(page, locale);
      await expect(page.getByRole("searchbox")).toHaveValue("  Original  ");
      await expect(page.getByRole("combobox")).toHaveValue("GB");
      expect(requests).toHaveLength(before);
      await page.goto(`${ERP}/finance`);
      await expect(page.locator(".money").first()).toBeVisible();
      await page.locator("#fin-from").fill("2026-09-01");
      const ledgerBefore = requests.filter((r) => r.path.endsWith("/staff/payments")).length;
      await switchLanguage(page, locale === "en" ? "ar" : "en");
      await expect(page.locator("#fin-from")).toHaveValue("2026-09-01");
      expect(requests.filter((r) => r.path.endsWith("/staff/payments"))).toHaveLength(ledgerBefore);
      await page.locator("#fin-to").fill("2026-08-01");
      await page.getByRole("button", { name: title(locale, "Apply", "تطبيق"), exact: true }).click();
      await expect(page.getByRole("alert")).toContainText(title(locale, "valid dates", "تاريخين صحيحين"));
      expect(requests.filter((r) => r.path.endsWith("/staff/payments"))).toHaveLength(ledgerBefore);
    });
    test(`${locale}: empty message/customer/finance/staff states do not fabricate records`, async ({ page }) => {
      const { requests } = await fixtures(page, locale, { empty: true });
      for (const [path, en, ar] of [["notifications", "No customer messages returned", "لم ترجع رسائل"], ["customers", "No contacts found", "لا توجد جهات اتصال"], ["finance", "No data for this period", "لا توجد بيانات لهذه الفترة"], ["staff", "No staff accounts yet", "لا توجد حسابات موظفين"]]) {
        await page.goto(`${ERP}/${path}`);
        await expect(page.getByText(title(locale, en!, ar!), { exact: false })).toBeVisible();
      }
      expect(writes(requests)).toEqual([]);
    });
    test(`${locale}: identity create/reset/role commands keep exact existing contract`, async ({ page }) => {
      const { requests } = await fixtures(page, locale);
      await page.goto(`${ERP}/staff`);
      await page.locator("#newUserEmail").fill("new-fixture@example.com");
      await page.locator("#newUserPassword").fill("Fixture-only-password");
      await page.getByRole("button", { name: title(locale, "Create account", "إنشاء حساب"), exact: true }).click();
      await expect(page.locator("#newUserPassword")).toHaveValue("");
      await page.locator("#newRoleCode").fill("new-fixture-role");
      await page.locator("#newRoleDescription").fill("Original fixture description");
      await page.getByRole("button", { name: title(locale, "Create role", "إنشاء دور"), exact: true }).click();
      await expect(page.locator("#newRoleCode")).toHaveValue("");
      // Identity records keep unique IDs, as the real server contract requires.
      const card = page.locator("li").filter({ has: page.getByText(USER.email, { exact: true }) });
      await card.getByRole("button", { name: title(locale, "Change roles", "تغيير الأدوار"), exact: true }).click();
      await card.getByRole("checkbox", { name: ROLE.code, exact: true }).uncheck();
      await card.getByRole("button", { name: title(locale, "Save roles", "حفظ الأدوار"), exact: true }).click();
      await expect(card.getByRole("button", { name: title(locale, "Save roles", "حفظ الأدوار"), exact: true })).toHaveCount(0);
      await card.getByRole("button", { name: title(locale, "Reset password", "إعادة تعيين كلمة المرور"), exact: true }).click();
      const dialog = page.getByRole("dialog");
      await dialog.locator("#resetPassword").fill("Reset-fixture-password");
      await dialog.getByRole("button", { name: title(locale, "Reset password", "إعادة تعيين كلمة المرور"), exact: true }).click();
      await expect(dialog).not.toBeVisible();
      const commands = writes(requests).map(({ method, path, body }) => ({ method, path, body }));
      expect(commands).toEqual([
        { method: "POST", path: "/api/v1/identity/users", body: { email: "new-fixture@example.com", password: "Fixture-only-password", roleCodes: [] } },
        { method: "POST", path: "/api/v1/identity/roles", body: { code: "new-fixture-role", description: "Original fixture description", permissionCodes: [] } },
        { method: "PUT", path: `/api/v1/identity/users/${USER.id}/roles`, body: { roleCodes: [] } },
        { method: "POST", path: `/api/v1/identity/users/${USER.id}/reset-password`, body: { newPassword: "Reset-fixture-password" } },
      ]);
      expect(requests.some((r) => r.search.includes("@") || r.search.includes("password"))).toBe(false);
    });
    test(`${locale}: role permission selection survives language and submits original permission codes`, async ({ page }) => {
      const { requests } = await fixtures(page, locale);
      await page.goto(`${ERP}/staff`);
      const card = page.locator("li").filter({ has: page.getByText(ROLE.code, { exact: true }) });
      await card.getByRole("button", { name: title(locale, "Edit permissions", "تعديل الصلاحيات"), exact: true }).click();
      await card.locator('input[value="tours-operator.booking:cancel"]').check();
      await switchLanguage(page, locale);
      await expect(card.locator('input[value="tours-operator.booking:cancel"]')).toBeChecked();
      await card.getByRole("button", { name: title(locale, "حفظ الصلاحيات", "Save permissions"), exact: true }).click();
      await expect(card.getByRole("button", { name: title(locale, "حفظ الصلاحيات", "Save permissions"), exact: true })).toHaveCount(0);
      expect(writes(requests)).toEqual([{ method: "PUT", path: `/api/v1/identity/roles/${ROLE.code}/permissions`, search: "", body: { permissionCodes: ["tours-operator.booking:view", "tours-operator.booking:cancel"] } }]);
    });
  }

  for (const status of [401, 403, 404, 500]) {
    for (const [route, errorPath] of [["notifications", "GET /api/v1/tours-operator/staff/notifications"], ["staff", "GET /api/v1/identity/users"], ["sales", "GET /api/v1/tours-operator/staff/sales-control"], ["settings", "GET /api/v1/tours-operator/settings"], ["customers", "GET /api/v1/tours-operator/bookings"], ["finance", "GET /api/v1/tours-operator/staff/payments"]]) {
      test(`${route} HTTP ${status}: truthful localized failure`, async ({ page }) => {
        await fixtures(page, "ar", { errorPath, status });
        await page.goto(`${ERP}/${route}`);
        if (status === 401) { await expect(page).toHaveURL(`${ERP}/login`); expect(await page.evaluate(() => sessionStorage.getItem("wego_auth_session"))).toBeNull(); }
        else if (route === "settings" && status === 404) await expect(page.getByText(/تعديل إعدادات الشركة غير منفذ/)).toBeVisible();
        else {
          await expect(page.getByRole("alert").last()).toContainText(status === 403 ? "ليس لديك صلاحية" : status === 404 && route === "staff" ? "العنصر غير موجود" : "fixture_failure");
          await switchLanguage(page, "ar");
          await expect(page.getByRole("alert").last()).toContainText(status === 403 ? "permission" : status === 404 && route === "staff" ? "Not found" : "Request failed");
        }
      });
    }
  }
  test("view-only notification and identity scopes expose no mutations or extra PII endpoints", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { permissions: ["tours-operator.booking:view", "identity:role-view"] });
    await page.goto(`${ERP}/notifications`);
    await expect(page.getByText("تأكيد الحجز", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "إعادة الإرسال", exact: true })).toHaveCount(0);
    await page.goto(`${ERP}/staff`);
    await expect(page.getByText(ROLE.description, { exact: true })).toBeVisible();
    await expect(page.locator("main form")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "تعديل الصلاحيات", exact: true })).toHaveCount(0);
    expect(requests.some((r) => r.path.endsWith("/identity/users"))).toBe(false);
    expect(writes(requests)).toEqual([]);
  });
  test("payment-only finance remains independent of tour access and handles mixed currencies honestly", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { permissions: ["tours-operator.payment:view"], currencyMix: true });
    await page.goto(`${ERP}/finance`);
    await expect(page.getByRole("alert")).toContainText("عملات مختلفة");
    await expect(page.locator(".money")).toHaveCount(0);
    expect(requests.some((r) => r.path.endsWith("/staff/tours"))).toBe(false);
    await switchLanguage(page, "ar");
    await expect(page.getByRole("alert")).toContainText("different currencies");
  });
  test("ambiguous sales write reports unknown outcome and does not automatically retry", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { errorPath: "PUT /api/v1/tours-operator/staff/sales-control" });
    await page.goto(`${ERP}/sales`);
    await page.locator("#pause-payments").check();
    await page.getByRole("button", { name: "حفظ", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("تعذر التأكد من حفظ");
    await switchLanguage(page, "ar");
    await expect(page.getByRole("alert")).toContainText("Could not verify");
    expect(writes(requests)).toHaveLength(1);
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.locator("#pause-payments")).not.toBeChecked();
    expect(writes(requests)).toHaveLength(1);
  });
  test("stale message-filter result never overwrites current selection", async ({ page }) => {
    const { releaseFirst } = await fixtures(page, "ar", { slowMessages: true });
    await page.goto(`${ERP}/notifications`);
    await page.locator("#status-filter").selectOption("SENT");
    await expect(page.getByText("CURRENT-RESULT", { exact: true })).toBeVisible();
    const staleResponse = page.waitForResponse((r) => r.url().includes("/staff/notifications?") && !new URL(r.url()).searchParams.has("status"));
    releaseFirst();
    await staleResponse;
    await expect(page.getByText("STALE-RESULT", { exact: true })).toHaveCount(0);
    await expect(page.getByText("CURRENT-RESULT", { exact: true })).toBeVisible();
  });
  test("settings values are readonly original server facts with localized labels", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { settingsData: true });
    await page.goto(`${ERP}/settings`);
    await expect(page.getByText("Original business label", { exact: true })).toBeVisible();
    await expect(page.getByText("Africa/Cairo", { exact: true })).toBeVisible();
    await expect(page.getByText("٣٠ دقيقة", { exact: true })).toBeVisible();
    await expect(page.locator("main input, main select")).toHaveCount(0);
    expect(writes(requests)).toEqual([]);
  });
  test("finance fetches all original keyset pages without duplicate recognition", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { paginatedLedger: true });
    await page.goto(`${ERP}/finance`);
    await expect(page.getByText("مدفوعات محصّلة: ٥١", { exact: true })).toBeVisible();
    const pages = requests.filter((r) => r.path.endsWith("/staff/payments"));
    expect(pages).toHaveLength(2);
    expect(pages[1]?.search).toContain("after=fixture-page-49");
  });
  test("stale finance result cannot overwrite the applied range", async ({ page }) => {
    const { releaseFirst } = await fixtures(page, "en", { slowFinance: true });
    await page.goto(`${ERP}/finance`);
    await page.locator("#fin-from").fill("2026-09-01");
    await page.getByRole("button", { name: "Apply", exact: true }).click();
    await expect(page.locator(".money").first()).toContainText("€35.05");
    const staleResponse = page.waitForResponse((r) => r.url().includes("/staff/payments?") && new URL(r.url()).searchParams.get("from") === "2026-10-01");
    releaseFirst();
    await staleResponse;
    await expect(page.locator(".money").first()).toContainText("€35.05");
  });
  test("dispatcher unknown reason is escaped and ambiguous resend never claims success", async ({ page }) => {
    const code = "<img src=x onerror=alert(1)>";
    const { requests } = await fixtures(page, "ar", { errorPath: "POST /api/v1/tours-operator/staff/notifications/fixture-failed/resend", status: 500, reasonCode: code });
    await page.goto(`${ERP}/notifications`);
    const card = page.locator("li").filter({ hasText: code });
    await expect(card).toContainText(code);
    await expect(card.locator("img")).toHaveCount(0);
    await card.getByRole("button", { name: "إعادة الإرسال", exact: true }).click();
    await expect(card.getByRole("alert")).toContainText("تعذر التأكد من إدراج");
    await switchLanguage(page, "ar");
    await expect(card.getByRole("alert")).toContainText("Could not verify");
    expect(writes(requests)).toHaveLength(1);
  });
});
