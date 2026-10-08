/** OPS2-B display evidence only. Every API request uses declared fixtures; no owner account or money is touched. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const ERP = (process.env.WEGO_STS_STAFF_BASE_URL ?? (() => {
  const url = new URL(process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58080");
  url.hostname = "staff.localhost";
  return url.origin;
})()).replace(/\/+$/, "");
const DAY = "2026-10-03";
const SESSION = {
  token: "booking-locale-fixture-token", email: "operator@example.com", roles: ["fixture-only"],
  permissions: ["tours-operator.booking:view", "tours-operator.booking:cancel", "tours-operator.booking:complete", "tours-operator.tour:view", "tours-operator.payment:view"],
};
const TOUR = {
  id: "fixture-tour", slug: "fixture-tour", nameEn: "Fixture tour — owner fact unchanged",
  category: "DESERT", durationText: "Fixture", priceAdult: { amount: "35.05", currencyCode: "EUR" },
  priceChild: null, capacity: 20, availableTimeSlots: ["MORNING"], sortOrder: 1,
  isActive: true, priceBasis: "PER_PERSON", priceOptions: [],
};
const BOOKING = {
  id: "fixture-booking", reference: "FIXTURE-001", tourId: TOUR.id, slotId: "fixture-slot",
  tourDate: DAY, timeSlot: "MORNING", adultsCount: 2, childrenCount: 1,
  totalPrice: { amount: "1234.05", currencyCode: "EUR" },
  unit: { unitCount: 1, optionLabel: "Original approved unit label", unitPrice: { amount: "1234.05", currencyCode: "EUR" } },
  customer: { fullName: "Fixture guest", nationality: "GB", phone: "+200000000000", email: "long-fixture-address-for-responsive-check@example.com" },
  hotelName: "A long fixture hotel name kept unchanged for mobile layout", hotelRoom: "12",
  specialRequests: "Original request <script>window.__unsafeFixture = true</script>", locale: "ru",
  status: "CONFIRMED", createdAt: "2026-10-02T10:00:00Z", confirmedAt: "2026-10-02T10:05:00Z",
};
const HISTORY = [
  { eventType: "BOOKING_CREATED", occurredAt: "2026-10-02T10:00:00Z" },
  { eventType: "BOOKING_CONFIRMED", actorEmail: "fixture-staff@example.com", occurredAt: "2026-10-02T10:06:00Z", reason: "Original audit note <b>not HTML</b>" },
];
const PAYMENTS = [{ paymentId: "fixture-payment", toStatus: "PAID", providerStatus: "CAPTURED", occurredAt: "2026-10-02T10:05:00Z", recorded: false }];
type RecordedRequest = { method: string; path: string; search: string; body: unknown };
type FixtureOptions = { permissions?: string[]; listStatus?: number; detailStatus?: number; historyStatus?: number; listEmpty?: boolean; paginated?: boolean; actionStatus?: number; actionCode?: string; delay?: number };

async function fixtures(page: Page, locale: "en" | "ar", options: FixtureOptions = {}) {
  const session = { ...SESSION, permissions: options.permissions ?? SESSION.permissions };
  await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: ERP, sameSite: "Lax" }]);
  await page.addInitScript((value) => sessionStorage.setItem("wego_auth_session", JSON.stringify(value)), session);
  const requests: RecordedRequest[] = [];
  let current = { ...BOOKING };
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    requests.push({ method: request.method(), path, search: url.search, body: request.postData() ? request.postDataJSON() : null });
    if (path.endsWith("/sales-status")) return route.fulfill({ json: { bookingsOpen: true, paymentsOpen: true } });
    if (path.endsWith("/staff/tours")) return route.fulfill({ json: [TOUR] });
    if (path.endsWith(`/staff/tours/${TOUR.id}`)) return route.fulfill({ json: TOUR });
    if (path.endsWith(`/tours/${TOUR.id}/slots/by-date`)) return route.fulfill({ json: [] });
    if (path.endsWith("/bookings")) {
      if (options.delay) await new Promise((resolve) => setTimeout(resolve, options.delay));
      if (options.listStatus) return route.fulfill({ status: options.listStatus, json: { error: "fixture_failure" } });
      const data = options.listEmpty ? [] : options.paginated && url.searchParams.get("page") === "0"
        ? Array.from({ length: 50 }, (_, index) => ({ ...current, id: `fixture-${index}`, reference: `FIXTURE-${index}` })) : [current];
      return route.fulfill({ json: data });
    }
    if (path.endsWith("/payment-history")) return route.fulfill({ json: PAYMENTS });
    if (path.endsWith("/history")) return options.historyStatus
      ? route.fulfill({ status: options.historyStatus, json: { error: "history_unavailable" } })
      : route.fulfill({ json: HISTORY });
    if (path.endsWith("/cancel") || path.endsWith("/complete")) {
      if (options.actionStatus) return route.fulfill({ status: options.actionStatus, json: { error: options.actionCode ?? "invalid_transition" } });
      current = { ...current, status: path.endsWith("/cancel") ? "CANCELLED" : "COMPLETED" };
      return route.fulfill({ json: current });
    }
    if (path.endsWith(`/bookings/${BOOKING.id}`)) return options.detailStatus
      ? route.fulfill({ status: options.detailStatus, json: { error: "fixture_failure" } })
      : route.fulfill({ json: current });
    return route.fulfill({ status: 404, json: { error: "unexpected_fixture_request" } });
  });
  return requests;
}

function mutations(requests: RecordedRequest[]) { return requests.filter((request) => request.method !== "GET"); }
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}

test.describe("Safari ERP booking EN/AR presentation", () => {
  for (const locale of ["en", "ar"] as const) {
    for (const width of [360, 768, 1024, 1440]) {
      test(`${locale} ${width}px: booking list/detail, keyboard and accessibility`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        const requests = await fixtures(page, locale);
        await page.goto(`${ERP}/bookings`);
        await expect(page.getByRole("link", { name: BOOKING.reference, exact: true })).toBeVisible();
        await expect(page.getByRole("status").filter({ hasText: "هذه الصفحة متاحة بالإنجليزية" })).toHaveCount(0);
        await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
        await expect(page.locator("main").locator("xpath=..")).toHaveAttribute("lang", locale);
        await expect(page.getByLabel(locale === "ar" ? "تصفية حسب حالة الحجز" : "Filter by booking status")).toBeVisible();
        await expect(page.locator(".money").first()).toContainText(locale === "ar" ? "١٬٢٣٤٫٠٥" : "1,234.05");
        await noOverflow(page);
        expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
        const link = page.getByRole("link", { name: BOOKING.reference, exact: true });
        await link.focus();
        await page.keyboard.press("Enter");
        await expect(page.getByRole("heading", { name: locale === "ar" ? "تفاصيل الحجز" : "Booking details", exact: true })).toBeVisible();
        await expect(page.getByText(locale === "ar" ? "تم تحصيل الدفع" : "Payment captured")).toBeVisible();
        await expect(page.getByText(locale === "ar" ? "(مُعاد بناؤه من الحالة السابقة)" : "(reconstructed)", { exact: true })).toBeVisible();
        await expect(page.getByText(BOOKING.specialRequests, { exact: true })).toBeVisible();
        expect(await page.evaluate(() => "__unsafeFixture" in window)).toBe(false);
        await noOverflow(page);
        await page.getByRole("button", { name: locale === "ar" ? "إلغاء الحجز" : "Cancel booking", exact: true }).click();
        await expect(page.getByLabel(locale === "ar" ? "سبب الإلغاء" : "Cancellation reason", { exact: true })).toBeVisible();
        expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
        await noOverflow(page);
        expect(mutations(requests)).toHaveLength(0);
        await page.screenshot({ path: test.info().outputPath(`booking-detail-${locale}-${width}.png`), fullPage: true });
      });
    }

    test(`${locale}: filters and pagination keep original enum/date/id query values`, async ({ page }) => {
      const requests = await fixtures(page, locale, { paginated: true });
      await page.goto(`${ERP}/bookings`);
      await expect(page.getByRole("link", { name: "FIXTURE-0", exact: true })).toBeVisible();
      await page.getByLabel(locale === "ar" ? "تصفية حسب حالة الحجز" : "Filter by booking status").selectOption("CONFIRMED");
      await page.getByLabel(locale === "ar" ? "تصفية حسب الرحلة" : "Filter by tour", { exact: true }).selectOption(TOUR.id);
      await page.locator("#booking-filter-date-day").selectOption(String(Number(DAY.slice(8))));
      await page.locator("#booking-filter-date-month").selectOption(String(Number(DAY.slice(5, 7))));
      await page.locator("#booking-filter-date-year").fill(DAY.slice(0, 4));
      await expect.poll(() => requests.filter(({ path }) => path.endsWith("/bookings")).at(-1)?.search).toContain(`date=${DAY}`);
      await page.getByRole("button", { name: locale === "ar" ? "التالي" : "Next", exact: true }).click();
      await expect(page.getByRole("link", { name: BOOKING.reference, exact: true })).toBeVisible();
      const query = new URLSearchParams(requests.filter(({ path }) => path.endsWith("/bookings")).at(-1)?.search);
      expect(Object.fromEntries(query)).toEqual({ tourId: TOUR.id, status: "CONFIRMED", date: DAY, page: "1", size: "50" });
      await page.getByRole("button", { name: locale === "ar" ? "مسح الفلاتر" : "Clear filters", exact: true }).click();
      await expect.poll(() => requests.filter(({ path }) => path.endsWith("/bookings")).at(-1)?.search).toBe("?page=0&size=50");
      expect(mutations(requests)).toHaveLength(0);
    });

    test(`${locale}: detail cancellation retains unsaved reason through a switch and sends the same trimmed payload`, async ({ page }) => {
      const requests = await fixtures(page, locale);
      await page.goto(`${ERP}/bookings/${BOOKING.id}`);
      await page.getByRole("button", { name: locale === "ar" ? "إلغاء الحجز" : "Cancel booking", exact: true }).click();
      await page.getByLabel(locale === "ar" ? "سبب الإلغاء" : "Cancellation reason", { exact: true }).fill("  Fixture original reason  ");
      await page.getByRole("button", { name: locale === "ar" ? "EN" : "العربية", exact: true }).click();
      const afterArabic = locale === "en";
      await expect(page.getByLabel(afterArabic ? "سبب الإلغاء" : "Cancellation reason", { exact: true })).toHaveValue("  Fixture original reason  ");
      expect(mutations(requests)).toHaveLength(0);
      await page.getByRole("button", { name: afterArabic ? "تأكيد الإلغاء" : "Confirm cancellation", exact: true }).click();
      await expect(page.getByRole("alert")).toContainText(afterArabic ? "تم تحديث الحجز بنجاح" : "Booking updated successfully");
      expect(mutations(requests)).toEqual([{ method: "POST", path: `/api/v1/tours-operator/bookings/${BOOKING.id}/cancel`, search: "", body: { reason: "Fixture original reason" } }]);
      await expect(page.locator(".badge-CANCELLED")).toContainText(afterArabic ? "ملغي" : "Cancelled");
      expect(page.url()).not.toMatch(/phone|email|reason|token/);
    });

    test(`${locale}: list validation, native confirmation and cancellation preserve original command`, async ({ page }) => {
      const requests = await fixtures(page, locale);
      await page.goto(`${ERP}/bookings`);
      await page.getByRole("button", { name: locale === "ar" ? "إلغاء الحجز" : "Cancel booking", exact: true }).click();
      await expect(page.getByRole("alert")).toContainText(locale === "ar" ? "يجب إدخال سبب الإلغاء" : "A cancellation reason is required");
      expect(mutations(requests)).toHaveLength(0);
      await page.getByLabel(locale === "ar" ? "سبب الإلغاء" : "Cancellation reason", { exact: true }).fill("  Fixture cancel  ");
      page.once("dialog", async (dialog) => {
        expect(dialog.message()).toContain(locale === "ar" ? "هل تريد إلغاء الحجز" : "Cancel booking");
        await dialog.accept();
      });
      await page.getByRole("button", { name: locale === "ar" ? "إلغاء الحجز" : "Cancel booking", exact: true }).click();
      await expect(page.locator(".badge-CANCELLED")).toBeVisible();
      expect(mutations(requests)[0]?.body).toEqual({ reason: "Fixture cancel" });
    });

    test(`${locale}: completion remains a body-free POST and shows backend-confirmed result`, async ({ page }) => {
      const requests = await fixtures(page, locale);
      await page.goto(`${ERP}/bookings/${BOOKING.id}`);
      await page.getByRole("button", { name: locale === "ar" ? "تسجيل كمكتمل" : "Mark completed", exact: true }).click();
      await expect(page.locator(".badge-COMPLETED")).toBeVisible();
      expect(mutations(requests)).toEqual([{ method: "POST", path: `/api/v1/tours-operator/bookings/${BOOKING.id}/complete`, search: "", body: null }]);
    });
  }

  test("an already-visible action error and timeline switch language without re-fetch or resubmit", async ({ page }) => {
    const requests = await fixtures(page, "en", { actionStatus: 409 });
    await page.goto(`${ERP}/bookings/${BOOKING.id}`);
    await page.getByRole("button", { name: "Mark completed", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("That status change isn't allowed");
    const before = requests.length;
    await page.getByRole("button", { name: "العربية", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("لا يمكن إجراء هذا التغيير");
    await expect(page.getByText("تم تحصيل الدفع")).toBeVisible();
    expect(requests).toHaveLength(before);
    expect(mutations(requests)).toHaveLength(1);
  });

  test("completion error remains visible for a complete-only operator without a cancellation row", async ({ page }) => {
    const requests = await fixtures(page, "ar", { permissions: ["tours-operator.booking:view", "tours-operator.booking:complete"], actionStatus: 403 });
    await page.goto(`${ERP}/bookings`);
    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "إكمال", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("ليس لديك صلاحية لتنفيذ هذا الإجراء");
    await expect(page.getByRole("button", { name: "إلغاء الحجز", exact: true })).toHaveCount(0);
    expect(mutations(requests)).toHaveLength(1);
  });

  test("view-only account has no mutation buttons and never fetches payment history", async ({ page }) => {
    const requests = await fixtures(page, "ar", { permissions: ["tours-operator.booking:view"] });
    await page.goto(`${ERP}/bookings/${BOOKING.id}`);
    await expect(page.getByText("عرض أحداث الدفع يحتاج صلاحية الاطلاع على المدفوعات.", { exact: true })).toBeVisible();
    await expect(page.getByText("أُنشئ الحجز", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /إلغاء الحجز|تسجيل كمكتمل/ })).toHaveCount(0);
    expect(requests.some(({ path }) => path.endsWith("/payment-history"))).toBe(false);
    expect(mutations(requests)).toHaveLength(0);
  });

  for (const status of [403, 404, 500]) {
    test(`HTTP ${status} detail error remains readable and follows the locale`, async ({ page }) => {
      const requests = await fixtures(page, "en", { detailStatus: status });
      await page.goto(`${ERP}/bookings/${BOOKING.id}`);
      await expect(page.getByRole("alert")).toBeVisible();
      await page.getByRole("button", { name: "العربية", exact: true }).click();
      await expect(page.getByRole("alert")).toContainText(status === 403 ? "ليس لديك صلاحية" : status === 404 ? "الحجز غير موجود" : "تعذر تنفيذ الطلب (fixture_failure)");
      expect(mutations(requests)).toHaveLength(0);
    });
  }

  test("list loading and empty states are localized", async ({ page }) => {
    await fixtures(page, "ar", { listEmpty: true, delay: 500 });
    await page.goto(`${ERP}/bookings`);
    await expect(page.getByText("جارٍ التحميل…", { exact: true })).toBeVisible();
    await expect(page.getByText("لا توجد حجوزات تطابق الفلاتر الحالية.", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "التالي", exact: true })).toBeDisabled();
  });

  test("a list 401 still clears the existing session and redirects to localized login", async ({ page }) => {
    await fixtures(page, "ar", { listStatus: 401 });
    await page.goto(`${ERP}/bookings`);
    await expect(page.getByRole("heading", { name: "تسجيل الدخول", exact: true })).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem("wego_auth_session"))).toBeNull();
  });

  test("failed history does not hide booking data and its warning translates", async ({ page }) => {
    await fixtures(page, "en", { historyStatus: 500 });
    await page.goto(`${ERP}/bookings/${BOOKING.id}`);
    await expect(page.getByRole("heading", { name: "Booking details", exact: true })).toBeVisible();
    await expect(page.getByRole("alert")).toContainText("History could not be loaded");
    await page.getByRole("button", { name: "العربية", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("تعذر تحميل سجل الأحداث");
  });

  test("existing tour slots route also opens its own page instead of being shadowed by the tour list", async ({ page }) => {
    const requests = await fixtures(page, "en");
    await page.goto(`${ERP}/tours/${TOUR.id}/slots`);
    await expect(page.getByRole("heading", { name: `Slots ${TOUR.slug}` })).toBeVisible();
    await expect(page.getByRole("table", { name: "Weekly slot availability" })).toBeVisible();
    expect(requests.filter(({ path }) => path.endsWith(`/tours/${TOUR.id}/slots/by-date`))).toHaveLength(7);
    expect(mutations(requests)).toHaveLength(0);
    await page.locator("main").getByRole("link", { name: "Tours", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Tours", exact: true })).toBeVisible();
  });
});
