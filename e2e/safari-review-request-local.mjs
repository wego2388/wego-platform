// Local, read-only browser gate for owner-supplied review destinations.
// Booking lookup is intercepted with a synthetic fixture; no customer data or production write.
import assert from "node:assert/strict";
import { chromium } from "@playwright/test";

const base = process.env.SAFARI_REVIEW_BASE_URL ?? "http://127.0.0.1:58121";
assert.ok(["127.0.0.1", "localhost"].includes(new URL(base).hostname), "This gate must use a loopback preview");
const googleUrl = "https://g.page/r/Cd4uOnKD1ldBEBM/review";
const tripadvisorUrl = "https://www.tripadvisor.com.eg/UserReviewEdit-d34123701?m=68676";

const browser = await chromium.launch();
try {
  for (const locale of ["en", "ar", "ru", "it"]) {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const response = await page.goto(`${base}/${locale}/contact`, { waitUntil: "networkidle" });
    assert.equal(response?.status(), 200, `${locale} contact`);
    const request = page.locator("[data-review-request]");
    await request.waitFor();
    assert.equal(await request.locator("[data-google-review-link]").getAttribute("href"), googleUrl);
    assert.equal(await request.locator("[data-tripadvisor-review-link]").getAttribute("href"), tripadvisorUrl);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `${locale} overflow`);
    await page.close();
  }

  for (const status of ["COMPLETED", "CONFIRMED"]) {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await page.route("**/api/v1/tours-operator/bookings/lookup", route => route.fulfill({ json: {
      reference: "STR-LOCAL-TEST", tourDate: "2026-10-10", timeSlot: "MORNING",
      adultsCount: 2, childrenCount: 0, totalPrice: { amount: "70.00", currencyCode: "EUR" },
      hotelName: "Local test hotel", status, cancellationReason: null, unit: null,
    } }));
    const response = await page.goto(`${base}/ar/my-booking`);
    assert.equal(response?.status(), 200, `my-booking ${status}`);
    await page.locator("#ref").fill("STR-LOCAL-TEST");
    await page.locator("#lookup-phone").fill("+201000000000");
    await page.locator('button[type="submit"]').click();
    await page.locator("#result-heading").waitFor();
    assert.equal(await page.locator("[data-review-request]").count(), status === "COMPLETED" ? 1 : 0);
    assert.ok(!page.url().includes("201000000000"), "Phone must not appear in the URL");
    await page.close();
  }
  console.log("Safari review request local browser gate passed (4 locales, COMPLETED, CONFIRMED).");
} finally {
  await browser.close();
}
