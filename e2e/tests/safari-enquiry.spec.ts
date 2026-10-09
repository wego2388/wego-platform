/** Actual disposable ENQUIRY_ONLY Compose stack. No mocked backend/provider. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";
import { enquiryCopy } from "../../web/apps/safari-tours-sharm-site/app/content/enquiry";
import { onlineRequestCopy } from "../../web/apps/safari-tours-sharm-site/app/content/onlineRequest";
import { brandArtworkCopy } from "../../web/apps/safari-tours-sharm-site/app/content/brandArtwork";
import { tripadvisorUrl } from "../../web/apps/safari-tours-sharm-site/app/content/locales";
import { officeCopy } from "../../web/apps/safari-tours-sharm-site/app/content/salesAwareCopy";

const SITE = (process.env.WEGO_STS_SITE_BASE_URL ?? "http://127.0.0.1:58087").replace(/\/+$/, "");
const STAFF = process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58087";
const STAFF_HOST = new URL(STAFF).host;
const SLUG = "e2e-desert-quad-safari";
let tour: { id: string; priceAdult: { amount: string; currencyCode: string } };
let slot: { id: string; date: string; timeSlot: string; bookedCount: number; available: number };
let unscheduledTour: { id: string; slug: string };

test.beforeAll(async ({ request }) => {
  // The catalog wait below can take up to 70 s (60 s SSR cache); the default
  // 30 s hook timeout would cut it short.
  test.setTimeout(120_000);
  const status = await (await request.get(`${SITE}/api/v1/tours-operator/sales-status`)).json();
  expect(status).toEqual({ bookingMode: "ENQUIRY_ONLY", bookingsOpen: false, paymentsOpen: false });
  tour = await (await request.get(`${SITE}/api/v1/tours-operator/tours/by-slug?slug=${SLUG}`)).json();
  const from = new Date().toISOString().slice(0, 10);
  const to = new Date(Date.now() + 60 * 86400000).toISOString().slice(0, 10);
  const slots = await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tour.id}/slots?from=${from}&to=${to}`)).json();
  slot = slots.find((s: typeof slot) => s.timeSlot === "MORNING");
  expect(slot).toBeTruthy();
  // Existing production catalog fixture, deliberately without synthetic slots.
  unscheduledTour = await (await request.get(`${SITE}/api/v1/tours-operator/tours/by-slug?slug=super-safari-adventure`)).json();
  expect(unscheduledTour.id).toBeTruthy();
  expect(await (await request.get(`${SITE}/api/v1/tours-operator/tours/${unscheduledTour.id}/slots?from=${from}&to=${to}`)).json()).toEqual([]);
  // Health checks can warm the existing 60s SSR catalog cache before the
  // synthetic fixture is seeded. Wait for that bounded cache, not for a UI
  // mutation or a retry that could disguise a booking defect.
  await expect(async () => {
    const html = await (await request.get(`${SITE}/en/tours`)).text();
    expect(html).toContain(`/en/tour/${SLUG}`);
  }).toPass({ timeout: 70_000, intervals: [1000, 5000] });
});

function bookingPath(locale: string) {
  return `${SITE}/${locale}/booking/${slot.id}?${new URLSearchParams({ tourId: tour.id, date: slot.date, timeSlot: slot.timeSlot, adults: "2", children: "1" })}`;
}

async function accessibility(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(results.violations.map((v) => ({ id: v.id, target: v.nodes.map((n) => n.target) }))).toEqual([]);
}

async function preventExternalTracking(page: Page) {
  // Synthetic runtime GA4 ID + actual consent exercise the real adapter without
  // sending any data to Google, WhatsApp or other external accounts.
  await page.context().addCookies([{ name: "sts_consent", value: "granted", url: SITE, sameSite: "Lax" }]);
  await page.context().route(/https:\/\/(www\.googletagmanager\.com|www\.google-analytics\.com|wa\.me)\//, (route) => route.fulfill({ status: 200, body: "" }));
}

for (const locale of ["en", "ar", "ru", "it"] as const) {
  for (const width of [360, 1440]) {
    test(`${locale} ${width}px: responsive brand art loads with truthful disclosure and no third-party widget`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await preventExternalTracking(page);
      const errors: string[] = [], widgets: string[] = [];
      page.on("pageerror", (error) => errors.push(error.message));
      page.on("request", (r) => { if (/jscache|tacdn/.test(r.url())) widgets.push(r.url()); });
      await page.goto(`${SITE}/${locale}`, { waitUntil: "networkidle" });
      await expect(page.locator("main")).toContainText(officeCopy[locale].hero);
      await expect(page.locator("main")).not.toContainText("Paymob");
      const hero = page.locator('[data-brand-art="hero"]');
      await expect(hero.locator("img")).toHaveAttribute("alt", ""); // Decorative, not a documentary tour photo.
      await expect(hero.locator("img")).toHaveAttribute("aria-hidden", "true");
      await expect(hero.locator("img")).toHaveAttribute("fetchpriority", "high");
      await expect(hero.locator("figcaption")).toHaveText(brandArtworkCopy[locale]);
      for (const kind of ["hero", "desert", "sea"]) {
        const image = page.locator(`[data-brand-art="${kind}"] img`);
        await image.scrollIntoViewIfNeeded();
        await expect.poll(() => image.evaluate((element) => {
          const img = element as HTMLImageElement;
          return img.complete && img.naturalWidth > 0 && /\/images\/brand\/.*\.webp$/.test(img.currentSrc);
        })).toBe(true);
        await expect(image).toHaveAttribute("width", /^\d+$/);
        await expect(image).toHaveAttribute("height", /^\d+$/);
      }
      await expect(page.locator("[data-tripadvisor-link]")).toHaveAttribute("href", tripadvisorUrl);
      await accessibility(page);
      expect(errors).toEqual([]);
      expect(widgets).toEqual([]);
      await page.evaluate(() => scrollTo(0, 0));
      if (locale === "en" || locale === "ar") await page.screenshot({ path: test.info().outputPath(`home-${locale}-${width}.png`) });
    });
    test(`${locale} ${width}px: a real tour without slots accepts a preferred date beyond 60 days, not a reservation`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await preventExternalTracking(page);
      const apiRequests: { method: string; path: string }[] = [];
      page.on("request", (r) => {
        if (r.url().includes("/api/v1/tours-operator/")) apiRequests.push({ method: r.method(), path: new URL(r.url()).pathname });
      });
      await page.goto(`${SITE}/${locale}/tour/${unscheduledTour.slug}`, { waitUntil: "networkidle" });
      if (width < 1024) {
        await expect(page.locator("[data-mobile-booking]")).toHaveText(enquiryCopy[locale].title);
        await page.locator("[data-mobile-booking]").click();
      }
      const button = page.locator("[data-trip-enquiry]").first();
      await expect(button).toBeDisabled();
      const scope = width < 1024 ? page.getByRole("dialog") : page.locator("aside");
      const date = scope.locator("[data-preferred-date]");
      await expect(date).toHaveAttribute("max", /^\d{4}-\d{2}-\d{2}$/);
      const futureDate = new Date(Date.now() + 120 * 86400000).toISOString().slice(0, 10);
      await date.fill(futureDate);
      await expect(button).toBeEnabled();
      await expect(button).not.toHaveAttribute("href");
      await expect(scope.locator("[data-online-request-form]")).toBeVisible();
      // Choosing an unscheduled preferred day enables intake, not a seat claim.
      await expect(scope.locator('[name="fullName"]')).toBeVisible();
      await expect(scope.locator("table")).toHaveCount(0);
      await expect(scope.getByText(/60/)).toHaveCount(0);
      await accessibility(page);
      await date.fill("2020-01-01");
      await expect(button).toBeDisabled();
      await expect(date).toHaveAttribute("aria-invalid", "true");
      await expect(scope.getByRole("alert")).toHaveText(enquiryCopy[locale].dateInvalid);
      expect(apiRequests.filter((r) => r.method !== "GET" || /\/slots$/.test(r.path))).toEqual([]);
    });
  }
  test(`${locale}: SSR information and FAQ structured data describe office confirmation, not disabled online payment`, async ({ request }) => {
    for (const path of ["about", "faq", "terms", "privacy"]) {
      const response = await request.get(`${SITE}/${locale}/${path}`);
      expect(response.status()).toBe(200);
      const html = await response.text();
      if (path === "privacy") expect(html).toContain(officeCopy[locale].privacy);
      else if (path === "about") expect(html).toContain(officeCopy[locale].availability);
      else expect(html).toContain(officeCopy[locale].booking);
      if (path === "faq") {
        const scripts = [...html.matchAll(/<script[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)].map((match) => JSON.parse(match[1]!));
        const faq = scripts.find((schema) => schema["@type"] === "FAQPage");
        expect(faq.mainEntity[1].acceptedAnswer.text).toBe(officeCopy[locale].booking);
        expect(faq.mainEntity[4].acceptedAnswer.text).toBe(officeCopy[locale].confirmation);
      }
    }
  });
  test(`${locale}: SSR renders truthful enquiry notice and no checkout form`, async ({ request }) => {
    const response = await request.get(bookingPath(locale));
    expect(response.status()).toBe(200);
    const html = await response.text();
    expect(html).toContain("data-enquiry-notice");
    expect(html).toContain("data-trip-enquiry");
    expect(html).not.toContain('id="fullName"');
    expect(html).toMatch(/name="robots" content="noindex"/);
    expect(html).toMatch(new RegExp(`lang="${locale}"`));
    expect(html).toMatch(new RegExp(`dir="${locale === "ar" ? "rtl" : "ltr"}"`));
  });

  for (const width of [360, 768, 1024, 1440]) {
    test(`${locale} ${width}px: actual tour card/mobile sheet and checkout are enquiry-only without holds or purchase`, async ({ page, request }) => {
      test.setTimeout(60_000);
      await page.setViewportSize({ width, height: 900 });
      await preventExternalTracking(page);
      const mutations: string[] = [];
      page.on("request", (r) => { if (r.url().includes("/api/v1/tours-operator/") && r.method() !== "GET") mutations.push(r.url()); });
      await page.goto(`${SITE}/${locale}/tour/${SLUG}`, { waitUntil: "networkidle" });
      await expect(page.locator("[data-booking-notice]")).toContainText(enquiryCopy[locale].notice);
      // Card is deliberately inserted after hydration; mobile opens the sheet.
      if (width < 1024) await page.locator("[data-mobile-booking]").click();
      const card = page.locator("[data-trip-enquiry]").first();
      await expect(card).toBeVisible();
      await expect(card).toContainText(onlineRequestCopy[locale].send);
      const scope = width < 1024 ? page.getByRole("dialog") : page.locator("aside").filter({ has: card });
      await expect(scope.locator("table")).toHaveCount(0);
      await expect(scope.getByText(/60/)).toHaveCount(0);
      await expect(scope.locator("[data-preferred-date]")).toBeVisible();
      await scope.locator("[data-preferred-date]").fill(slot.date);
      await scope.locator("[data-preferred-time]").selectOption("MORNING");
      await expect(card).not.toHaveAttribute("href");
      await expect(scope.locator("[data-online-request-form]")).toBeVisible();
      await expect(scope.locator('[name="fullName"]')).toBeVisible();
      await accessibility(page);
      if (locale === "ar" && (width === 360 || width === 1440)) await page.screenshot({ path: test.info().outputPath(`tour-${locale}-${width}.png`), fullPage: true });
      await page.goto(bookingPath(locale), { waitUntil: "networkidle" });
      await expect(page.locator("main [data-enquiry-notice]")).toContainText(enquiryCopy[locale].notice);
      await expect(page.locator("main form")).toHaveCount(0);
      await expect(page.locator("main input")).toHaveCount(0);
      const enquiry = page.locator("main [data-trip-enquiry]");
      await expect(enquiry).toHaveAttribute("href", `/${locale}/tour/${SLUG}`);
      await enquiry.focus();
      await expect(enquiry).toBeFocused();
      // This fallback is internal: no WhatsApp popup is part of booking.
      const events = await page.evaluate(() => Array.from((window as Window & { dataLayer?: IArguments[] }).dataLayer ?? []).map((args) => Array.from(args)));
      expect(events.some((args) => args[0] === "event" && args[1] === "whatsapp_click")).toBe(false);
      expect(events.some((args) => args[0] === "event" && ["purchase", "begin_checkout"].includes(String(args[1])))).toBe(false);
      await accessibility(page);
      expect(mutations).toEqual([]);
      const slots = await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tour.id}/slots?from=${slot.date}&to=${slot.date}`)).json();
      expect(slots.find((s: typeof slot) => s.id === slot.id)?.bookedCount).toBe(slot.bookedCount);
    });
  }
}

test("actual API refuses stale booking/pay calls and neither creates a hold nor acknowledges disabled callback", async ({ request }) => {
  const response = await request.post(`${SITE}/api/v1/tours-operator/bookings`, { data: {
    slotId: slot.id, adultsCount: 2, childrenCount: 0, customer: { fullName: "Synthetic E2E Guest", phone: "+201000000000", nationality: "EG" }, hotelName: "Synthetic hotel", locale: "en",
  } });
  expect(response.status()).toBe(503);
  expect(await response.json()).toEqual({ error: "online_booking_unavailable" });
  const payment = await request.post(`${SITE}/api/v1/tours-operator/bookings/00000000-0000-0000-0000-000000000001/pay`, { data: {} });
  expect(payment.status()).toBe(503);
  expect(await payment.json()).toEqual({ error: "online_payment_unavailable" });
  const callback = await request.post(`${SITE}/api/v1/tours-operator/payments/paymob-callback?hmac=synthetic`, { data: { obj: {
    id: "synthetic", order: { id: "synthetic" }, amount_cents: 100, currency: "EUR", integration_id: "123", owner: "456", success: true,
  } } });
  expect(callback.status()).toBe(503);
  expect(await callback.json()).toEqual({ error: "payment_provider_unavailable" });
});

test("tablet header keeps all localized navigation accessible without overflow", async ({ page }) => {
  await preventExternalTracking(page);
  for (const locale of ["en", "ar", "ru", "it"]) {
    for (const width of [768, 1023]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(`${SITE}/${locale}/tour/${SLUG}`, { waitUntil: "networkidle" });
      const menu = page.locator("header button[aria-expanded]:not([aria-haspopup])");
      await expect(menu).toBeVisible();
      await expect(menu).toHaveAttribute("aria-expanded", "false");
      await menu.click();
      const dialog = page.getByRole("dialog");
      await expect(dialog).toBeVisible();
      for (const path of ["tours", "my-booking", "contact"]) {
        await expect(dialog.locator(`nav a[href="/${locale}/${path}"]`)).toBeVisible();
      }
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      await page.keyboard.press("Escape");
      await expect(dialog).toBeHidden();
      await expect(menu).toBeFocused();
    }
  }
});

for (const locale of ["en", "ar"] as const) {
  test(`${locale}: actual staff switches cannot enable deployment checkout`, async ({ page, request }) => {
    // Browser *.localhost mapping is explicit in Chromium config. Node's
    // APIRequestContext instead uses loopback + the actual staff Host header.
    const login = await request.post(`${SITE}/api/v1/identity/login`, { headers: { Host: STAFF_HOST }, data: { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD } });
    expect(login.status()).toBe(200);
    const auth = await login.json();
    const saved = await request.put(`${SITE}/api/v1/tours-operator/staff/sales-control`, { headers: { Host: STAFF_HOST, Authorization: `Bearer ${auth.token}` }, data: { bookingsPaused: false, paymentsPaused: false, reason: null } });
    expect(saved.status()).toBe(200);
    expect((await saved.json()).bookingMode).toBe("ENQUIRY_ONLY");
    expect(await (await request.get(`${SITE}/api/v1/tours-operator/sales-status`)).json()).toEqual({ bookingMode: "ENQUIRY_ONLY", bookingsOpen: false, paymentsOpen: false });
    await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: STAFF, sameSite: "Lax" }]);
    await page.addInitScript((session) => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), auth);
    await page.goto(`${STAFF}/sales`, { waitUntil: "networkidle" });
    await expect(page.locator("[data-enquiry-mode]")).toBeVisible();
    await expect(page.locator("main")).not.toContainText(locale === "ar" ? "البيع عبر الموقع متاح." : "Online sales are open.");
    await page.setViewportSize({ width: 360, height: 900 });
    await accessibility(page);
    const logout = await request.post(`${SITE}/api/v1/identity/logout`, { headers: { Host: STAFF_HOST, Authorization: `Bearer ${auth.token}` } });
    expect(logout.status()).toBe(204);
  });
}
