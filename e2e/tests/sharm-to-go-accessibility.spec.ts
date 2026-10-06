import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Independent of the shared playwright.config.ts `baseURL` (that default
// targets the other app's compose stack on :58080) — this suite runs
// against whichever Sharm To Go site instance WEGO_STG_E2E_BASE_URL points
// at, local or otherwise. A real published service id is optional: pass
// one via WEGO_STG_E2E_SERVICE_ID to also cover the experience detail page;
// without it, that one check is skipped rather than failing on a catalog
// that happens to have nothing published yet.
const baseURL = process.env.WEGO_STG_E2E_BASE_URL ?? "http://127.0.0.1:4031";
const serviceId = process.env.WEGO_STG_E2E_SERVICE_ID;

test.use({ baseURL });

const PAGES: Array<{ name: string; path: string }> = [
  { name: "home", path: "/" },
  { name: "experiences", path: "/experiences" },
  { name: "find-my-trip", path: "/find-my-trip" },
  { name: "about", path: "/about" },
  { name: "faq", path: "/faq" },
  { name: "contact", path: "/contact" },
  { name: "privacy", path: "/privacy" },
  { name: "terms", path: "/terms" },
  { name: "track", path: "/track" },
];

if (serviceId) {
  PAGES.push({ name: "experience-detail", path: `/experiences/${serviceId}` });
  PAGES.push({ name: "experience-request", path: `/experiences/${serviceId}/request` });
}

// WCAG 2.1 AA, per the 2026-10-02 handoff's own UX-8 scope — axe's own
// default ruleset already targets this; naming the tags explicitly makes
// that assertion resilient to a future axe-core default-ruleset change.
const WCAG_TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"];

for (const { name, path } of PAGES) {
  for (const colorScheme of ["light", "dark"] as const) {
    test(`${name} has zero WCAG 2.1 AA violations (${colorScheme}, en)`, async ({ page }) => {
      await page.emulateMedia({ colorScheme });
      await page.goto(path, { waitUntil: "networkidle" });
      const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
      expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
    });
  }

  test(`${name} has zero WCAG 2.1 AA violations (light, ar/RTL)`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: "light" });
    await page.goto(path, { waitUntil: "networkidle" });
    // Same toggle a real visitor uses — every page carries this control via
    // SiteSubHeader/the homepage header, not a URL param (see
    // useSiteLocale.ts: locale is a per-viewer localStorage convenience).
    const toggle = page.getByRole("button", { name: "العربية" });
    if (await toggle.count()) {
      await toggle.click();
      await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
    }
    const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze();
    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
  });
}
