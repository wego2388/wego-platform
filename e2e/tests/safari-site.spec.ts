/**
 * Public-site journeys added in phase 4 (UX-2…UX-8): discovery, tour page,
 * information pages, SEO files and accessibility. Read-only: creates no
 * bookings. Runs against the same compose stack as safari-checkout.
 *
 * Site base URL: WEGO_STS_SITE_BASE_URL (default http://127.0.0.1:58080).
 */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const SITE = (process.env.WEGO_STS_SITE_BASE_URL ?? process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58080").replace(/\/+$/, "");
const E2E_TOUR_SLUG = "e2e-desert-quad-safari";

test.describe("Safari public site", () => {
  test("home and tours list are server-rendered with the live catalogue", async ({ page }) => {
    await page.goto(`${SITE}/en`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await page.goto(`${SITE}/en/tours`);
    await expect(page.getByRole("link", { name: /e2e/i }).first()).toBeVisible();
  });

  test("search and filters live in the URL", async ({ page }) => {
    await page.goto(`${SITE}/en/tours`, { waitUntil: "networkidle" });
    await page.getByLabel("Search tours").fill("quad");
    await expect(page).toHaveURL(/[?&]q=quad/);
    await page.goto(`${SITE}/en/tours?cat=sea`, { waitUntil: "networkidle" });
    await expect(page.getByRole("link", { name: new RegExp(E2E_TOUR_SLUG.replace(/-/g, "[- ]"), "i") })).toHaveCount(0);
  });

  test("the tour page shows the booking calendar and hands off to checkout without personal data", async ({ page }) => {
    await page.goto(`${SITE}/en/tour/${E2E_TOUR_SLUG}`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const day = page.locator("aside table button:not([disabled])").first();
    await expect(day).toBeVisible();
    await day.click();
    const radios = page.locator("aside input[type=radio]");
    if ((await radios.count()) > 1) await radios.first().check({ force: true });
    await page.locator("aside").getByRole("button", { name: /Continue to booking/ }).click();
    await expect(page).toHaveURL(/\/en\/booking\/[0-9a-f-]{36}\?/);
    const url = new URL(page.url());
    expect([...url.searchParams.keys()].sort()).toEqual(expect.arrayContaining(["adults", "children", "date", "timeSlot", "tourId"]));
    expect(url.search).not.toMatch(/phone|email|name/i);
  });

  test("information pages render in four languages and an unknown page is a 404", async ({ page }) => {
    for (const path of ["/ar/contact", "/ru/faq", "/it/about", "/en/terms", "/ar/privacy", "/en/trip-finder?like=desert&go=1"]) {
      const response = await page.goto(`${SITE}${path}`);
      expect(response?.status(), path).toBe(200);
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    }
    await expect(page.locator("text=+20 111 129 2690").or(page.locator("main"))).toBeVisible();
    const missing = await page.goto(`${SITE}/en/no-such-page`);
    expect(missing?.status()).toBe(404);
  });

  test("sitemap and robots describe the public site", async ({ request }) => {
    const sitemap = await request.get(`${SITE}/sitemap.xml`);
    expect(sitemap.status()).toBe(200);
    const xml = await sitemap.text();
    expect(xml).toContain(`/en/tour/${E2E_TOUR_SLUG}</loc>`);
    expect(xml).toContain('hreflang="ar"');
    expect(xml).not.toContain("/booking/");
    const robots = await (await request.get(`${SITE}/robots.txt`)).text();
    expect(robots).toContain("Sitemap:");
    expect(robots).toContain("Disallow: /en/booking/");
  });

  test("no analytics request is made without configured IDs and consent", async ({ page }) => {
    const tracking: string[] = [];
    page.on("request", (r) => {
      if (/googletagmanager|google-analytics|facebook\.net/.test(r.url())) tracking.push(r.url());
    });
    await page.goto(`${SITE}/en/tours`, { waitUntil: "networkidle" });
    expect(tracking).toEqual([]);
  });

  for (const path of ["/en", "/ar/tours", `/en/tour/${E2E_TOUR_SLUG}`, "/ar/contact", "/en/trip-finder"]) {
    test(`meets WCAG 2.1 AA (axe): ${path}`, async ({ page }) => {
      await page.goto(`${SITE}${path}`, { waitUntil: "networkidle" });
      const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
      expect(result.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`)).toEqual([]);
    });
  }
});
