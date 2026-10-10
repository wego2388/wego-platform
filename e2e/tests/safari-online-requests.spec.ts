/** Real disposable ENQUIRY_ONLY stack: public form -> PostgreSQL -> ERP -> confirmed unpaid booking. */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import { E2E_STAFF_EMAIL, E2E_STAFF_PASSWORD } from "../seed.mjs";
import { onlineRequestCopy } from "../../web/apps/safari-tours-sharm-site/app/content/onlineRequest";
import { infoCopy } from "../../web/apps/safari-tours-sharm-site/app/content/info";
import { requestMessages } from "../../web/apps/safari-tours-sharm-erp/app/utils/onlineRequestMessages";

const SITE = (process.env.WEGO_STS_SITE_BASE_URL ?? "http://127.0.0.1:58087").replace(/\/+$/, "");
const STAFF = (process.env.WEGO_STS_STAFF_BASE_URL ?? "http://staff.localhost:58087").replace(/\/+$/, "");
const STAFF_HOST = new URL(STAFF).host;
const SLUG = "e2e-desert-quad-safari";
let auth: { token: string; email: string; roles: string[]; permissions: string[] } | undefined;
test.beforeAll(async ({ request }) => {
  if (process.env.WEGO_SAFARI_REQUEST_E2E_CONFIRM !== "yes-this-is-a-disposable-request-stack"
    || new URL(SITE).hostname !== "127.0.0.1" || new URL(STAFF).hostname !== "staff.localhost") {
    throw new Error("Online request E2E may mutate only an explicitly confirmed disposable local stack.");
  }
  // One synthetic staff session for this serial suite, not eight rapid logins.
  // Respect the real edge's 5/minute +burst6 login limit on fast CI runners.
  const login = await request.post(`${SITE}/api/v1/identity/login`, { headers: { Host: STAFF_HOST }, data: { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD } });
  expect(login.status()).toBe(200); const session = await login.json();
  const me = await request.get(`${SITE}/api/v1/identity/me`, { headers: { Host: STAFF_HOST, Authorization: `Bearer ${session.token}` } });
  expect(me.status()).toBe(200); auth = { ...await me.json(), token: session.token };
  expect(auth!.permissions).toContain("tours-operator.booking:view");
  expect(auth!.permissions).toContain("tours-operator.booking:create-office");
});
test.afterAll(async ({ request }) => {
  if (auth) {
    const logout = await request.post(`${SITE}/api/v1/identity/logout`, { headers: { Host: STAFF_HOST, Authorization: `Bearer ${auth.token}` } });
    expect(logout.status()).toBe(204);
  }
});

for (const [localeIndex, locale] of (["en", "ar", "ru", "it"] as const).entries()) {
  for (const [widthIndex, width] of [360, 1440].entries()) {
    test(`${locale} ${width}px: guest request appears in bilingual ERP and confirms once, unpaid`, async ({ page, request }) => {
      test.setTimeout(90_000);
      await page.setViewportSize({ width, height: 900 });
      if (!auth) throw new Error("Synthetic staff session was not established");
      const staffHeaders = { Host: STAFF_HOST, Authorization: `Bearer ${auth.token}` };
      const tour = await (await request.get(`${SITE}/api/v1/tours-operator/tours/by-slug?slug=${SLUG}`)).json();
      expect(tour.id).toBeTruthy();
      const date = new Date(Date.now() + (90 + localeIndex * 2 + widthIndex) * 86400000).toISOString().slice(0, 10);
      const departure = await request.post(`${SITE}/api/v1/tours-operator/staff/tours/${tour.id}/slots`, { headers: staffHeaders, data: { date, timeSlot: "MORNING", capacity: 20 } });
      expect([201, 409]).toContain(departure.status());
      const slots = await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tour.id}/slots/by-date?date=${date}`)).json();
      const slot = slots.find((s: { timeSlot: string }) => s.timeSlot === "MORNING"); expect(slot).toBeTruthy();
      const captured: unknown[] = []; const navigations: string[] = [];
      page.on("request", req => {
        if (req.method() === "POST" && req.url().endsWith("/booking-requests")) captured.push(req.postDataJSON());
        if (req.isNavigationRequest()) navigations.push(req.url());
      });
      await page.goto(`${SITE}/${locale}/tour/${SLUG}`, { waitUntil: "networkidle" });
      await page.getByRole("button", { name: infoCopy[locale].consent.decline, exact: true }).click();
      if (width < 1024) await page.locator("[data-mobile-booking]").click();
      const scope = width < 1024 ? page.getByRole("dialog") : page.locator("aside").filter({ has: page.locator("[data-preferred-date]") });
      await scope.locator("[data-preferred-date]").fill(date);
      await scope.locator("[data-preferred-time]").selectOption("MORNING");
      const form = scope.locator("[data-online-request-form]");
      const name = `Request E2E ${locale} ${width}`;
      for (const [field, value] of Object.entries({ fullName: name, phone: "+201000000002", nationality: "EG", hotelName: "Synthetic request hotel", email: "synthetic-request@example.com" })) {
        const control = form.locator(`[name="${field}"]`);
        if (field === "nationality") await control.selectOption(value); else await control.fill(value);
      }
      await form.locator('[name="privacy"]').check();
      await expect(form.getByRole("button", { name: onlineRequestCopy[locale].send })).not.toHaveAttribute("href");
      const received = page.waitForResponse(res => res.url().endsWith("/booking-requests") && res.request().method() === "POST");
      await form.getByRole("button", { name: onlineRequestCopy[locale].send }).click();
      const response = await received; expect(response.status()).toBe(201);
      const ack = await response.json(); expect(Object.keys(ack)).toEqual(["reference"]);
      await expect(scope.locator("[data-request-reference]")).toHaveText(ack.reference);
      await expect(scope.locator("[data-online-request-success]")).toContainText(onlineRequestCopy[locale].next);
      expect(captured).toHaveLength(1); const payload = captured[0] as { clientRequestId: string };
      const replay = await request.post(`${SITE}/api/v1/tours-operator/booking-requests`, { data: payload });
      expect(replay.status()).toBe(200); expect(await replay.json()).toEqual(ack);
      const unchanged = await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tour.id}/slots/by-date?date=${date}`)).json();
      expect(unchanged.find((s: { id: string }) => s.id === slot.id).bookedCount).toBe(slot.bookedCount);
      expect(navigations.join(" ")).not.toMatch(/wa\.me|Request%20E2E|synthetic-request|201000000002/);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

      const erpLocale = locale === "ar" || locale === "ru" ? "ar" : "en";
      await page.context().addCookies([{ name: "sts_staff_locale", value: erpLocale, url: STAFF, sameSite: "Lax" }]);
      await page.addInitScript(session => sessionStorage.setItem("wego_auth_session", JSON.stringify(session)), auth);
      await page.goto(`${STAFF}/requests`, { waitUntil: "networkidle" });
      await expect(page.locator("main")).toContainText(ack.reference);
      await page.locator("main article").filter({ hasText: ack.reference }).getByRole("link", { name: requestMessages[erpLocale].open }).click();
      await expect(page.locator("main")).toContainText(name);
      await expect(page.locator("main")).toContainText("Synthetic request hotel");
      await page.getByRole("button", { name: requestMessages[erpLocale].start, exact: true }).click();
      await expect(page.locator("main")).toContainText(requestMessages[erpLocale].IN_PROGRESS);
      await page.locator("main select").filter({ has: page.locator(`option[value="${slot.id}"]`) }).selectOption(slot.id);
      await page.getByRole("checkbox", { name: requestMessages[erpLocale].agree }).check();
      const conversion = page.waitForResponse(res => res.url().endsWith("/convert") && res.request().method() === "POST");
      await page.getByRole("button", { name: requestMessages[erpLocale].convert, exact: true }).click();
      const converted = await conversion; expect(converted.status()).toBe(200); const saved = await converted.json();
      expect(saved.status).toBe("CONVERTED"); expect(saved.bookingId).toBeTruthy();
      await expect(page.getByRole("link", { name: requestMessages[erpLocale].booking })).toBeVisible();
      const confirmed = await (await request.get(`${SITE}/api/v1/tours-operator/bookings/${saved.bookingId}`, { headers: staffHeaders })).json();
      expect(confirmed.status).toBe("CONFIRMED"); expect(confirmed.channel).toBe("OFFICE"); expect(confirmed.officePayment.state).toBe("UNPAID"); expect(confirmed.customer.fullName).toBe(name);
      const repeat = await request.post(`${SITE}/api/v1/tours-operator/staff/booking-requests/${payload.clientRequestId}/convert`, { headers: staffHeaders, data: converted.request().postDataJSON() });
      expect(repeat.status()).toBe(200); expect((await repeat.json()).bookingId).toBe(saved.bookingId);
      const finalSlots = await (await request.get(`${SITE}/api/v1/tours-operator/tours/${tour.id}/slots/by-date?date=${date}`)).json();
      expect(finalSlots.find((s: { id: string }) => s.id === slot.id).bookedCount).toBe(slot.bookedCount + confirmed.adultsCount + confirmed.childrenCount);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
      expect(axe.violations.map(v => ({ id: v.id, target: v.nodes.map(n => n.target) }))).toEqual([]);
      await page.screenshot({ path: test.info().outputPath(`request-${locale}-${width}.png`), fullPage: true });
    });
  }
}
