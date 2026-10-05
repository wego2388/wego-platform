/** Explicit fixtures only: display/query evidence, not real inventory or a payment test. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const ERP = (process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58080").replace(/\/+$/, "");
const TOUR = {
  id: "fixture-tour", slug: "fixture-tour", nameEn: "Original catalog label <b>not HTML</b>",
  category: "DESERT", durationText: "Original duration", tourType: "GROUP",
  priceAdult: { amount: "35.05", currencyCode: "EUR" },
  priceChild: { amount: "12.05", currencyCode: "EUR" }, capacity: 20,
  availableTimeSlots: ["MORNING"], sortOrder: 1, isActive: true,
  priceBasis: "PER_PERSON", priceOptions: [],
};
const UNIT = {
  ...TOUR, id: "fixture-unit", slug: "fixture-unit", nameEn: "Original unit label", isActive: false,
  priceBasis: "PER_UNIT", priceOptions: [{ code: "original-unit", label: "Original option", price: { amount: "90.05", currencyCode: "EUR" }, seatsPerUnit: 2 }],
};
const REQUEST = { ...TOUR, id: "fixture-request", slug: "fixture-request", nameEn: "Fixture request-only tour", tourType: "REQUEST_ONLY", isActive: false };
type Request = { method: string; path: string; search: string; body: unknown };
type Options = { permissions?: string[]; listError?: number; slotError?: number; empty?: boolean; actionError?: boolean; paginated?: boolean; slowFirstSlots?: boolean; slowFirstList?: boolean; inactive?: boolean };

async function fixtures(page: Page, locale: "en" | "ar", options: Options = {}) {
  await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: ERP, sameSite: "Lax" }]);
  await page.addInitScript((session) => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), {
    token: "tour-fixture-only", email: "fixture@example.com", roles: ["fixture"],
    permissions: options.permissions ?? ["tours-operator.tour:view", "tours-operator.tour:manage"],
  });
  const requests: Request[] = [];
  let active = true;
  let slotLoads = 0;
  let listLoads = 0;
  let releaseFirst!: () => void;
  const firstGate = new Promise<void>((resolve) => { releaseFirst = resolve; });
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    requests.push({ method: request.method(), path, search: url.search, body: request.postData() ? request.postDataJSON() : null });
    if (path.endsWith("/sales-status")) return route.fulfill({ json: { bookingsOpen: true, paymentsOpen: true } });
    if (path.endsWith("/staff/tours")) {
      const first = ++listLoads === 1;
      if (options.slowFirstList && first) await firstGate;
      if (options.listError) return route.fulfill({ status: options.listError, json: { error: "fixture_failure" } });
      const tours = options.empty ? [] : options.slowFirstList
        ? [{ ...TOUR, nameEn: first ? "Stale catalog result" : "Current catalog result" }]
        : options.paginated && url.searchParams.get("page") === "0"
          ? Array.from({ length: 50 }, (_, index) => ({ ...TOUR, id: `fixture-${index}`, slug: `fixture-${index}`, nameEn: `Fixture ${index}` }))
          : [{ ...TOUR, isActive: active }, UNIT, REQUEST];
      return route.fulfill({ json: tours });
    }
    if (path.endsWith(`/staff/tours/${TOUR.id}`)) return route.fulfill({ json: { ...TOUR, isActive: !options.inactive } });
    if (path.endsWith(`/tours/${TOUR.id}/slots`)) {
      const first = ++slotLoads === 1;
      if (options.slowFirstSlots && first) await firstGate;
      if (options.slotError) return route.fulfill({ status: options.slotError, json: { error: "fixture_failure" } });
      const date = url.searchParams.get("from");
      return route.fulfill({ json: options.empty ? [] : [
        { id: "fixture-slot", tourId: TOUR.id, date, timeSlot: "MORNING", capacity: 20, available: options.slowFirstSlots && !first ? 8 : 3, isBlocked: false },
      ] });
    }
    if (path.endsWith("/activate") || path.endsWith("/deactivate")) {
      if (options.actionError) return route.fulfill({ status: 409, json: { error: "original_conflict" } });
      active = path.endsWith("/activate");
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ status: 404, json: { error: "unexpected_fixture_request" } });
  });
  return { requests, releaseFirst };
}

async function accessible(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
}
const slotRequests = (requests: Request[]) => requests.filter(({ path }) => path.endsWith("/slots"));

test.describe("Safari ERP tours and slots EN/AR", () => {
  test.use({ timezoneId: "Africa/Cairo" });
  for (const locale of ["en", "ar"] as const) {
    for (const width of [360, 768, 1024, 1440]) {
      test(`${locale} ${width}px: prices, original content, keyboard and calendar accessibility`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.clock.setFixedTime(new Date("2026-10-05T12:00:00Z"));
        const { requests } = await fixtures(page, locale);
        await page.goto(`${ERP}/tours`);
        await expect(page.getByText(TOUR.nameEn, { exact: true })).toBeVisible();
        await expect(page.getByRole("status").filter({ hasText: "هذه الصفحة متاحة بالإنجليزية" })).toHaveCount(0);
        await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
        await expect(page.locator("main").locator("xpath=..")).toHaveAttribute("lang", locale);
        await expect(page.locator(".money").first()).toContainText(locale === "ar" ? "٣٥٫٠٥" : "35.05");
        const unitRow = page.getByRole("row").filter({ hasText: UNIT.nameEn });
        await expect(unitRow).toContainText(locale === "ar" ? "٩٠٫٠٥" : "90.05");
        await expect(unitRow).toContainText("Original option");
        await expect(page.getByRole("row").filter({ hasText: REQUEST.nameEn }).getByRole("button")).toHaveCount(0);
        await accessible(page);
        const link = page.getByRole("row").filter({ hasText: TOUR.nameEn }).getByRole("link", { name: locale === "ar" ? "المواعيد" : "Slots", exact: true });
        await link.focus();
        await page.keyboard.press("Enter");
        const table = page.getByRole("table", { name: locale === "ar" ? "توافر المواعيد الأسبوعية" : "Weekly slot availability" });
        await expect(table).toBeVisible();
        await expect(page.locator("main").locator("xpath=..")).toHaveAttribute("lang", locale);
        await expect(table).toContainText(locale === "ar" ? "الصباح" : "Morning");
        await expect(table).toContainText(locale === "ar" ? "٣/٢٠" : "3/20");
        await expect(page.getByText(locale === "ar"
          ? "يعرض التقويم المواعيد المتاحة للحجز من مصدر التوافر العام، وليس كشف التشغيل الكامل. المواعيد الممتلئة والمحظورة لا ترجع؛ علامة الشرطة لا تعني عدم وجود رحلة."
          : "This calendar shows bookable slots from public availability, not the full operating schedule. Full/blocked slots are not returned; a dash does not mean no departure exists.", { exact: true })).toBeVisible();
        expect(requests.some(({ path }) => path === `/api/v1/tours-operator/tours/${TOUR.id}`)).toBe(false);
        expect(requests.some(({ path }) => path === `/api/v1/tours-operator/staff/tours/${TOUR.id}`)).toBe(true);
        expect(Object.fromEntries(new URLSearchParams(slotRequests(requests).at(-1)!.search))).toEqual({ from: "2026-10-05", to: "2026-10-11" });
        await accessible(page);
        expect(requests.filter(({ method }) => method !== "GET")).toHaveLength(0);
        await page.screenshot({ path: test.info().outputPath(`calendar-${locale}-${width}.png`), fullPage: true });
      });
    }

    test(`${locale}: filter values survive language switch, pagination and activation payload stay unchanged`, async ({ page }) => {
      const { requests } = await fixtures(page, locale, { paginated: true });
      await page.goto(`${ERP}/tours`);
      await expect(page.getByText("Fixture 0", { exact: true })).toBeVisible();
      await page.getByLabel(locale === "ar" ? "تصفية حسب الفئة" : "Filter by category").selectOption("SEA");
      await page.getByLabel(locale === "ar" ? "تصفية حسب حالة الرحلة" : "Filter by tour status").selectOption("false");
      const before = requests.length;
      await page.getByRole("button", { name: locale === "ar" ? "EN" : "العربية", exact: true }).click();
      const ar = locale === "en";
      await expect(page.getByLabel(ar ? "تصفية حسب الفئة" : "Filter by category")).toHaveValue("SEA");
      await expect(page.getByLabel(ar ? "تصفية حسب حالة الرحلة" : "Filter by tour status")).toHaveValue("false");
      expect(requests).toHaveLength(before);
      await page.getByRole("button", { name: ar ? "تطبيق الفلاتر" : "Filter", exact: true }).click();
      await expect.poll(() => requests.filter(({ path }) => path.endsWith("/staff/tours")).at(-1)?.search).toContain("activeOnly=false");
      await page.getByRole("button", { name: ar ? "التالي" : "Next", exact: true }).click();
      await expect(page.getByText(TOUR.nameEn, { exact: true })).toBeVisible();
      expect(Object.fromEntries(new URLSearchParams(requests.filter(({ path }) => path.endsWith("/staff/tours")).at(-1)!.search))).toEqual({ category: "SEA", activeOnly: "false", page: "1", size: "50" });
      await page.getByRole("row").filter({ hasText: TOUR.nameEn }).getByRole("button", { name: ar ? "إيقاف" : "Deactivate", exact: true }).click();
      await expect(page.getByRole("row").filter({ hasText: TOUR.nameEn }).getByRole("button", { name: ar ? "تفعيل" : "Activate", exact: true })).toBeVisible();
      expect(requests.filter(({ method }) => method !== "GET")).toEqual([{ method: "PATCH", path: `/api/v1/tours-operator/staff/tours/${TOUR.id}/deactivate`, search: "", body: null }]);
    });

    test(`${locale}: action error follows locale without repeat mutation`, async ({ page }) => {
      const { requests } = await fixtures(page, locale, { actionError: true });
      await page.goto(`${ERP}/tours`);
      await page.getByRole("row").filter({ hasText: TOUR.nameEn }).getByRole("button", { name: locale === "ar" ? "إيقاف" : "Deactivate", exact: true }).click();
      await expect(page.getByRole("alert")).toContainText("original_conflict");
      await page.getByRole("button", { name: locale === "ar" ? "EN" : "العربية", exact: true }).click();
      await expect(page.getByRole("alert")).toContainText(locale === "en" ? "تعذر تنفيذ الطلب" : "Request failed");
      expect(requests.filter(({ method }) => method !== "GET")).toHaveLength(1);
    });
  }

  test("view-only tours has no activation buttons; slots never add new mutations", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { permissions: ["tours-operator.tour:view"] });
    await page.goto(`${ERP}/tours`);
    await expect(page.getByText(TOUR.nameEn, { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "إيقاف", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "تفعيل", exact: true })).toHaveCount(0);
    expect(requests.filter(({ method }) => method !== "GET")).toHaveLength(0);
  });

  for (const status of [401, 403, 404, 500]) {
    test(`slots HTTP ${status} shows truthful failure without an empty availability calendar`, async ({ page }) => {
      await fixtures(page, "ar", { slotError: status });
      await page.goto(`${ERP}/tours/${TOUR.id}/slots`);
      if (status === 401) {
        await expect(page).toHaveURL(`${ERP}/login`);
        expect(await page.evaluate(() => sessionStorage.getItem("wego_auth_session"))).toBeNull();
      } else {
        await expect(page.getByRole("alert")).toContainText(status === 403 ? "ليس لديك صلاحية" : status === 404 ? "الرحلة غير موجودة" : "fixture_failure");
        await expect(page.getByRole("table")).toHaveCount(0);
        await page.getByRole("button", { name: "EN", exact: true }).click();
        await expect(page.getByRole("alert")).toContainText(status === 403 ? "permission" : status === 404 ? "Tour not found" : "Request failed");
      }
    });
  }

  test("empty inventory is explicit, not a failed request disguised as sold out", async ({ page }) => {
    await fixtures(page, "ar", { empty: true });
    await page.goto(`${ERP}/tours`);
    await expect(page.getByText("لا توجد رحلات.", { exact: true })).toBeVisible();
    await page.goto(`${ERP}/tours/${TOUR.id}/slots`);
    await expect(page.getByText("لم ترجع مواعيد متاحة للحجز هذا الأسبوع. قد توجد مواعيد ممتلئة أو محظورة.", { exact: true })).toBeVisible();
    await expect(page.getByRole("alert")).toHaveCount(0);
  });

  test("inactive tour details use the authorized staff reader, not public active-only detail", async ({ page }) => {
    const { requests } = await fixtures(page, "ar", { inactive: true });
    await page.goto(`${ERP}/tours/${TOUR.id}/slots`);
    await expect(page.getByRole("table")).toBeVisible();
    await expect(page.getByRole("heading", { level: 1 })).toContainText("غير نشط");
    expect(requests.filter(({ path }) => path.endsWith(`/tours/${TOUR.id}`)).map(({ path }) => path)).toEqual([`/api/v1/tours-operator/staff/tours/${TOUR.id}`]);
  });

  test("late week result cannot overwrite the currently selected calendar", async ({ page }) => {
    await page.clock.setFixedTime(new Date("2026-10-05T12:00:00Z"));
    const { requests, releaseFirst } = await fixtures(page, "en", { slowFirstSlots: true });
    await page.goto(`${ERP}/tours/${TOUR.id}/slots`);
    await expect.poll(() => slotRequests(requests).length).toBe(1);
    await expect(page.getByRole("status").filter({ hasText: "Loading" })).toBeVisible();
    await page.getByRole("button", { name: "Next week", exact: true }).click();
    await expect(page.getByRole("table")).toContainText("8/20");
    const finished = page.waitForResponse((response) => response.url().includes("from=2026-10-05"));
    releaseFirst();
    await finished;
    await expect(page.getByRole("table")).toContainText("8/20");
    await expect(page.getByRole("table")).not.toContainText("3/20");
  });

  test("late tour filter result cannot overwrite the latest list", async ({ page }) => {
    const { requests, releaseFirst } = await fixtures(page, "en", { slowFirstList: true });
    await page.goto(`${ERP}/tours`);
    await expect.poll(() => requests.filter(({ path }) => path.endsWith("/staff/tours")).length).toBe(1);
    await page.getByLabel("Filter by category").selectOption("SEA");
    await page.getByRole("button", { name: "Filter", exact: true }).click();
    await expect(page.getByText("Current catalog result", { exact: true })).toBeVisible();
    const finished = page.waitForResponse((response) => response.url().endsWith("/staff/tours?page=0&size=50"));
    releaseFirst();
    await finished;
    await expect(page.getByText("Current catalog result", { exact: true })).toBeVisible();
    await expect(page.getByText("Stale catalog result", { exact: true })).toHaveCount(0);
  });
});

for (const scenario of [
  { zone: "Africa/Cairo", instant: "2026-09-28T00:30:00+03:00", monday: "2026-09-28", sunday: "2026-10-04" },
  { zone: "UTC", instant: "2026-09-28T00:30:00Z", monday: "2026-09-28", sunday: "2026-10-04" },
  { zone: "America/Los_Angeles", instant: "2026-10-04T22:30:00-07:00", monday: "2026-09-28", sunday: "2026-10-04" },
  { zone: "Asia/Tokyo", instant: "2026-12-31T00:30:00+09:00", monday: "2026-12-28", sunday: "2027-01-03" },
  { zone: "Africa/Cairo", instant: "2026-10-29T23:30:00+03:00", monday: "2026-10-26", sunday: "2026-11-01" },
]) {
  test.describe(`calendar ${scenario.zone} ${scenario.instant}`, () => {
    test.use({ timezoneId: scenario.zone });
    test("query is the local Monday–Sunday date range, including midnight/DST/year boundaries", async ({ page }) => {
      await page.clock.setFixedTime(new Date(scenario.instant));
      const { requests } = await fixtures(page, "en");
      await page.goto(`${ERP}/tours/${TOUR.id}/slots`);
      await expect(page.getByRole("table")).toBeVisible();
      expect(Object.fromEntries(new URLSearchParams(slotRequests(requests).at(-1)!.search))).toEqual({ from: scenario.monday, to: scenario.sunday });
      await page.getByRole("button", { name: "Next week", exact: true }).click();
      await expect.poll(() => slotRequests(requests).length).toBe(2);
      await page.getByRole("button", { name: "Previous week", exact: true }).click();
      await expect.poll(() => slotRequests(requests).length).toBe(3);
      expect(Object.fromEntries(new URLSearchParams(slotRequests(requests).at(-1)!.search))).toEqual({ from: scenario.monday, to: scenario.sunday });
    });
  });
}
