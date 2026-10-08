/** Read-only real SSR/product identity and decorative-background verification. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { brandArtworkCopy } from "../../web/apps/safari-tours-sharm-site/app/content/brandArtwork";
const SITE = (process.env.WEGO_STS_SITE_BASE_URL ?? "http://127.0.0.1:58080").replace(/\/+$/, "");
test.use({ reducedMotion: "reduce" });
for (const locale of ["en", "ar", "ru", "it"] as const) for (const width of [360, 1440]) {
  test(`${locale} ${width}px: brand-first titles and truthful destination backgrounds across pages`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    const errors: string[] = [], writes: string[] = [];
    page.on("pageerror", error => errors.push(error.message));
    page.on("request", request => { if (new URL(request.url()).pathname.startsWith("/api/") && request.method() !== "GET") writes.push(request.url()); });
    for (const route of ["tours", "category/desert", "about", "contact", "faq", "privacy", "terms", "trip-finder"]) {
      await page.goto(`${SITE}/${locale}/${route}`, { waitUntil: "networkidle" });
      await expect(page).toHaveTitle(locale === "ar" ? /^سفاري تورز شرم — / : /^Safari Tours Sharm — /);
      expect(await page.title()).not.toMatch(/kheima|beats|الخيمة|الخيمه|بيتس/i);
      await expect(page.locator('meta[name="application-name"]')).toHaveAttribute("content", "Safari Tours Sharm");
      await expect(page.locator("html")).toHaveAttribute("lang", locale);
      await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      const banner = page.locator("[data-page-art]");
      await expect(banner).toContainText(brandArtworkCopy[locale]);
      await expect(banner.locator("img")).toHaveAttribute("alt", "");
      await expect(banner.locator("img")).toHaveAttribute("aria-hidden", "true");
      await expect.poll(() => banner.locator("img").evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      expect((await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze()).violations).toEqual([]);
      if (["tours", "contact"].includes(route)) await page.screenshot({ path: testInfo.outputPath(`${locale}-${width}-${route}.png`), fullPage: true });
    }
    expect(errors).toEqual([]); expect(writes).toEqual([]);
  });
}
