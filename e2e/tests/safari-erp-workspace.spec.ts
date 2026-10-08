/** Presentation-only fixtures: no real account, booking or financial write. */
import { expect, test } from "@playwright/test";
import { AxeBuilder } from "@axe-core/playwright";
const ERP = process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58080";
const session = { token: "workspace-test-fixture", email: "workspace@example.invalid", roles: ["fixture"], permissions: ["tours-operator.booking:view", "tours-operator.booking:create-office", "tours-operator.tour:view", "tours-operator.supplier:manage", "tours-operator.fleet:manage", "tours-operator.payment:view", "identity:user-view"] };
for (const locale of ["en", "ar"] as const) for (const width of [360, 1440]) {
  test(`${locale} ${width}px: grouped searchable workspace, current page, mobile escape and print`, async ({ page }) => {
    const writes: string[] = [];
    await page.route("**/api/**", async route => {
      if (route.request().method() !== "GET") writes.push(route.request().method());
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith("/sales-status")) return route.fulfill({ json: { bookingMode: "ENQUIRY_ONLY", bookingsOpen: false, paymentsOpen: false } });
      if (path.endsWith("/bookings") || path.endsWith("/staff/tours")) return route.fulfill({ json: [] });
      return route.fulfill({ status: 404, json: { error: "unexpected_workspace_fixture_request" } });
    });
    await page.setViewportSize({ width, height: 1000 });
    await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: ERP, sameSite: "Lax" }]);
    await page.addInitScript(auth => sessionStorage.setItem("wego_auth_session", JSON.stringify(auth)), session);
    await page.goto(ERP);
    const names = locale === "ar" ? ["التشغيل اليومي", "الرحلات والمحتوى", "المالية والحسابات", "الأشخاص والشركاء", "الإدارة"] : ["Daily operations", "Tours & content", "Finance & accounts", "People & partners", "Administration"];
    const menu = page.getByRole("button", { name: locale === "ar" ? "القائمة" : "Menu", exact: true });
    if (width < 1280) await menu.click();
    const nav = page.getByRole("navigation", { name: locale === "ar" ? "التنقل الرئيسي" : "Main navigation", exact: true });
    for (const name of names) await expect(nav.getByRole("heading", { name, exact: true })).toBeVisible();
    await expect(nav.locator('a[aria-current="page"]')).toHaveAttribute("href", "/");
    const search = nav.getByRole("searchbox");
    await search.fill(locale === "ar" ? "المالية" : "Finance");
    await expect(nav.getByRole("link")).toHaveCount(1);
    expect(new URL(page.url()).search).toBe("");
    await search.fill("not-a-dashboard-page"); await expect(nav.getByRole("status")).toBeVisible();
    await nav.getByRole("button", { name: locale === "ar" ? "مسح بحث الشاشات" : "Clear page search" }).click();
    await expect(nav.getByRole("link", { name: locale === "ar" ? "صور التصنيفات" : "Category photos", exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
    // Escape still dismisses the menu even if focus has returned to body.
    if (width < 1280) { await search.evaluate(element => (element as HTMLInputElement).blur()); await page.keyboard.press("Escape"); await expect(menu).toHaveAttribute("aria-expanded", "false"); await expect(menu).toBeFocused(); }
    await page.screenshot({ path: test.info().outputPath(`workspace-${locale}-${width}.png`), fullPage: true });
    await page.emulateMedia({ media: "print" });
    await expect(page.getByRole("navigation")).toHaveCount(0);
    expect(await page.locator(".staff-frame").evaluate(element => getComputedStyle(element).display)).toBe("block");
    expect(writes).toEqual([]);
  });
}
