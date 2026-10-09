/** Presentation regressions only: every API request is intercepted; no real prints or financial writes. */
import { expect, test } from "@playwright/test";

const STAFF = process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58080";
const ID = "11111111-1111-4111-8111-111111111111";
const DAY = "2027-06-15";
const lines = Array.from({ length: 8 }, (_, index) => ({
  order: index + 1, reference: `TEST-2027-000${index}`, leadName: "Synthetic guest for responsive paper",
  hotelName: "TEST ONLY — Long hotel name for layout verification", hotelRoom: "312", guests: 2,
  phone: "+200000000000", paymentDue: true,
}));

for (const lang of ["en", "ar"] as const) for (const width of [360, 768, 1440]) for (const kind of ["run-sheet", "pickup"] as const) {
  test(`${kind} ${lang} ${width}px: all columns reachable by keyboard; A4 and reprint preserved`, async ({ page }) => {
    if (new URL(STAFF).hostname !== "staff.localhost") throw new Error("Document fixtures require the local staff host");
    await page.setViewportSize({ width, height: 1000 });
    await page.context().addCookies([{ name: "sts_staff_locale", value: lang, url: STAFF, sameSite: "Lax" }]);
    await page.addInitScript(() => {
      sessionStorage.setItem("wego_auth_session", JSON.stringify({ token: "document-layout-fixture-only", email: "paper-fixture@example.invalid", roles: [], permissions: ["tours-operator.document:print-ops"] }));
      window.print = () => {};
    });
    let posts = 0;
    const doc = {
      document: { type: kind === "pickup" ? "PICKUP_MANIFEST" : "RUN_SHEET", number: "TEST-PAPER-001", version: 1, language: lang, copy: false, revised: false, originalPrintedAt: "2027-06-14T10:00:00Z", printedAt: "2027-06-14T10:00:00Z", printedByEmail: "paper-fixture@example.invalid" },
      data: kind === "pickup" ? { slotId: ID, date: DAY, timeSlot: "MORNING", tourNameEn: "TEST ONLY — Paper layout", tourNameAr: "اختبار شكل الورقة فقط", totalGuests: 16, driver: "Synthetic driver", vehicle: "Synthetic van", lines }
        : { date: DAY, totalGuests: 16, tours: [{ tourId: ID, tourNameEn: "TEST ONLY — Paper layout", tourNameAr: "اختبار شكل الورقة فقط", guests: 16, departures: [{ timeSlot: "MORNING", guests: 16, hotels: [{ hotelName: lines[0]!.hotelName, guests: 16 }], lines, notes: [] }] }] },
    };
    await page.route("**/api/**", async route => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      const expected = kind === "pickup" ? `/api/v1/tours-operator/documents/slots/${ID}/pickup-manifest` : "/api/v1/tours-operator/documents/run-sheet";
      if (request.method() === "POST" && path === expected) {
        posts++;
        expect(request.postDataJSON().language).toBe(lang);
        return route.fulfill({ json: doc });
      }
      return route.fulfill({ status: 404, json: { error: "unexpected_document_fixture_request" } });
    });
    const errors: string[] = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.goto(`${STAFF}/documents/${kind}/${kind === "pickup" ? ID : DAY}?lang=${lang}`);
    await page.getByRole("button", { name: lang === "ar" ? "إنشاء وطباعة" : "Generate and print", exact: true }).click();
    const sheet = page.locator(".doc-sheet");
    await expect(sheet).toBeVisible();
    await expect(sheet).toHaveAttribute("dir", lang === "ar" ? "rtl" : "ltr");
    if (kind === "run-sheet") await expect(sheet).not.toContainText("+200000000000");
    const scroll = page.locator(".doc-table-scroll");
    await expect(scroll).toHaveAttribute("tabindex", "0");
    await expect(scroll).toHaveAttribute("role", "region");
    expect(await scroll.getAttribute("aria-label")).toBeTruthy();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(await scroll.evaluate(el => {
      const outer = el.closest(".doc-sheet")!.getBoundingClientRect(), inner = el.getBoundingClientRect();
      return inner.left >= outer.left && inner.right <= outer.right;
    })).toBe(true);
    const overflow = await scroll.evaluate(el => el.scrollWidth > el.clientWidth);
    if (width === 360) expect(overflow, "Fixture must exercise a genuinely wide table").toBe(true);
    if (overflow) {
      const before = await scroll.evaluate(el => el.scrollLeft);
      await scroll.focus();
      await page.keyboard.press(lang === "ar" ? "ArrowLeft" : "ArrowRight");
      await expect.poll(() => scroll.evaluate(el => el.scrollLeft)).not.toBe(before);
    }
    await page.getByRole("button", { name: lang === "ar" ? "اطبع هذه الصفحة مرة أخرى" : "Print this page again", exact: true }).click();
    expect(posts).toBe(1);
    await page.emulateMedia({ media: "print" });
    expect(await scroll.evaluate(el => getComputedStyle(el).overflowX)).toBe("visible");
    await expect(page.locator(".doc-table-hint")).toBeHidden();
    expect(await sheet.locator("tbody tr").count()).toBe(8);
    expect(errors).toEqual([]);
  });
}
