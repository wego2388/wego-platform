/**
 * Actual fresh, disposable Safari MEDIA stack only. No API/provider/UI mocks.
 * Run this file alone with --workers=1 --retries=0 after the synthetic seed.
 * Never run against an owner preview or production; fixture/category preflight
 * refuses previously uploaded photos instead of deleting/resetting history.
 */
import { AxeBuilder } from "@axe-core/playwright";
import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import type { CategoryMedia, StaffTourContent, Tour } from "../../web/packages/api-contract/src/index";

// Same public synthetic fixture as seed.mjs, without importing its executable main.
const E2E_STAFF_EMAIL = "e2e-staff@example.com";
const E2E_STAFF_PASSWORD = "e2e-synthetic-password-123";

const API = (process.env.WEGO_E2E_BASE_URL ?? "http://127.0.0.1:58088").replace(/\/+$/, "");
const SITE = (process.env.WEGO_STS_SITE_BASE_URL ?? API).replace(/\/+$/, "");
const STAFF = (process.env.WEGO_STS_STAFF_BASE_URL ?? (() => { const url = new URL(API); url.hostname = "staff.localhost"; return url.origin; })()).replace(/\/+$/, "");
const STAFF_HOST = new URL(STAFF).host;
const SLUG = "e2e-desert-quad-safari";
const LOCALES = ["en", "ar", "ru", "it"] as const;
const CATEGORIES = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"] as const;
type Locale = typeof LOCALES[number];
type StaffLocale = "en" | "ar";
type Photo = StaffTourContent["media"][number];
type Alt = Record<Locale, string>;
let api: APIRequestContext;
let auth: { token: string; email: string; roles: string[]; permissions: string[] };
let tour: Tour;
let commercialBefore: string;
let jpeg: Buffer;
let smallJpeg: Buffer;
let photo: Photo;
let cover: CategoryMedia;
let nextUploadAt = 0;
const tourAlt: Alt = { en: "Synthetic E2E tour JPEG EN", ar: "صورة اختبار اصطناعية للرحلة", ru: "Синтетическое фото тура", it: "Foto sintetica del tour" };
const categoryAlt: Alt = { en: "Synthetic E2E desert cover EN", ar: "غلاف اختبار اصطناعي للصحراء", ru: "Синтетическая обложка пустыни", it: "Copertina sintetica del deserto" };
const headers = () => ({ Host: STAFF_HOST, Authorization: `Bearer ${auth.token}` });
const tourBase = () => `/api/v1/tours-operator/staff/tours/${tour.id}`;

function assertDisposable() {
  expect(process.env.WEGO_SAFARI_MEDIA_E2E_CONFIRM, "Explicit disposable-stack confirmation is required").toBe("yes-this-is-a-disposable-media-stack");
  for (const origin of [API, SITE, STAFF]) {
    const url = new URL(origin);
    expect(["127.0.0.1", "localhost", "[::1]", "staff.localhost"]).toContain(url.hostname);
    expect(url.username + url.password).toBe("");
  }
}

async function staff<T>(path: string, method = "GET", data?: unknown): Promise<T> {
  const response = await api.fetch(path, { method, headers: headers(), ...(data === undefined ? {} : { data }) });
  expect(response.ok(), `${method} ${path}: HTTP ${response.status()}`).toBe(true);
  const text = await response.text(); return (text ? JSON.parse(text) : undefined) as T;
}
const content = () => staff<StaffTourContent>(`${tourBase()}/content`);
const category = () => staff<CategoryMedia>("/api/v1/tours-operator/staff/categories/DESERT/media");
async function selectStaff(page: Page, locale: StaffLocale) {
  await page.context().addCookies([{ name: "sts_staff_locale", value: locale, url: STAFF, sameSite: "Lax" }]);
  await page.addInitScript(({ session, staffHostname }) => {
    if (location.hostname === staffHostname) sessionStorage.setItem("wego_auth_session", JSON.stringify(session));
  }, { session: auth, staffHostname: new URL(STAFF).hostname });
}
async function decoded(page: Page, selector: string) {
  const image = page.locator(selector).first(); await expect(image).toBeVisible();
  await image.scrollIntoViewIfNeeded();
  await expect.poll(() => image.evaluate(element => (element as HTMLImageElement).complete && (element as HTMLImageElement).naturalWidth > 0)).toBe(true);
  return image;
}
async function notice(page: Page, text: string) {
  await expect(page.getByRole("status").filter({ hasText: text })).toHaveCount(1);
}
async function accessible(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const result = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(result.violations.map(item => ({ id: item.id, targets: item.nodes.map(node => node.target) }))).toEqual([]);
}
async function paceUpload() {
  // Respect the actual 10/minute intake limit; do not retry a 429/file POST.
  const delay = nextUploadAt - Date.now(); if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay));
  nextUploadAt = Date.now() + 6200;
}
async function photoBytes(path: string, expected: 200 | 404) {
  const response = await api.get(`${SITE}${path}`); expect(response.status()).toBe(expected);
  expect(response.headers()["cache-control"]).toContain("no-store");
  if (expected === 200) {
    expect(response.headers()["content-type"]).toMatch(/^image\/jpeg/);
    const bytes = await response.body(); expect([...bytes.subarray(0, 2)]).toEqual([255, 216]); expect(bytes.length).toBeGreaterThan(100);
  } else expect(response.headers()["content-type"] ?? "").not.toMatch(/^image\//);
}
async function optimizerDenied(path: string) {
  for (const optimizer of [`/_ipx/w_360${path}`, `/_ipx/w_360/${encodeURIComponent(path)}`]) {
    const response = await api.get(`${SITE}${optimizer}`); expect(response.status()).toBe(404); expect(response.headers()["content-type"] ?? "").not.toMatch(/^image\//);
  }
}
function assetId(path: string) { return path.substring(path.lastIndexOf("/") + 1).split(".")[0]!; }
async function privateBytes(id: string) {
  const response = await api.get(`/api/v1/tours-operator/staff/media/preview/${id}?variant=base`, { headers: headers() });
  expect(response.status()).toBe(200); expect(response.headers()["cache-control"]).toContain("private"); expect(response.headers()["cache-control"]).toContain("no-store");
  expect([...((await response.body()).subarray(0, 2))]).toEqual([255, 216]);
}
async function rights(page: Page, kind: "tour" | "category", locale: StaffLocale = "en") {
  const label = kind === "tour" ? (locale === "ar" ? "مراجعة حقوق الصورة" : "Review photo rights") : (locale === "ar" ? "مراجعة حقوق صورة الفئة" : "Review category photo rights");
  await page.getByRole("button", { name: label, exact: true }).click();
  const dialog = page.getByRole("dialog"); await decoded(page, "[role=dialog] img[src^='blob:']");
  const confirm = dialog.getByRole("button", { name: locale === "ar" ? "تأكيد الحقوق والاعتماد" : "Confirm rights and approve", exact: true });
  await expect(confirm).toBeDisabled(); await dialog.locator("input[type=checkbox]").check(); await expect(confirm).toBeEnabled(); await confirm.click(); await expect(dialog).toHaveCount(0);
}
async function uploadApi(requestId: string, buffer: Buffer, alt: Alt) {
  await paceUpload();
  return api.post(`${tourBase()}/media/upload`, { headers: headers(), multipart: { requestId, file: { name: "synthetic-e2e-media.jpg", mimeType: "image/jpeg", buffer }, altEn: alt.en, altAr: alt.ar, altRu: alt.ru, altIt: alt.it } });
}
async function syntheticJpeg(page: Page, width: number, color: string) {
  const data = await page.evaluate(({ width, color }) => {
    const canvas = document.createElement("canvas"); canvas.width = width; canvas.height = Math.round(width * 2 / 3);
    const context = canvas.getContext("2d")!; context.fillStyle = color; context.fillRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = "#0b3954"; context.fillRect(0, canvas.height / 2, canvas.width, canvas.height / 2);
    context.fillStyle = "white"; context.font = `${Math.round(width / 28)}px sans-serif`; context.fillText("SYNTHETIC E2E PHOTO — NOT OWNER CONTENT", 16, canvas.height / 2);
    return canvas.toDataURL("image/jpeg", 0.85).split(",")[1]!;
  }, { width, color }); return Buffer.from(data, "base64");
}

test.describe("Safari MEDIA — real isolated backend/files/ERP/public bytes", () => {
  test.describe.configure({ mode: "serial", retries: 0, timeout: 120_000 });
  test.skip(process.env.WEGO_SAFARI_MEDIA_E2E_CONFIRM !== "yes-this-is-a-disposable-media-stack", "Opt-in only: requires a newly seeded, disposable MEDIA stack; never mutate another product or owner preview.");
  test.beforeAll(async ({ playwright, browser }) => {
    assertDisposable(); api = await playwright.request.newContext({ baseURL: API });
    const login = await api.post("/api/v1/identity/login", { headers: { Host: STAFF_HOST }, data: { email: E2E_STAFF_EMAIL, password: E2E_STAFF_PASSWORD } });
    expect(login.status()).toBe(200); const issued = await login.json();
    const me = await api.get("/api/v1/identity/me", { headers: { Host: STAFF_HOST, Authorization: `Bearer ${issued.token}` } }); expect(me.status()).toBe(200);
    auth = { token: issued.token, ...await me.json() };
    for (const permission of ["tours-operator.tour:view", "tours-operator.tour:manage", "tours-operator.content:publish", "tours-operator.media:upload"]) expect(auth.permissions).toContain(permission);
    const status = await (await api.get(`${SITE}/api/v1/tours-operator/sales-status`)).json(); expect(status.bookingMode).toBe("ENQUIRY_ONLY");
    const resolved = await api.get(`${SITE}/api/v1/tours-operator/tours/by-slug?slug=${SLUG}`); expect(resolved.status()).toBe(200); tour = await resolved.json(); expect(tour.slug).toBe(SLUG);
    commercialBefore = JSON.stringify({ priceAdult: tour.priceAdult, priceChild: tour.priceChild, priceBasis: tour.priceBasis, priceOptions: tour.priceOptions, capacity: tour.capacity, cancellationPolicy: tour.cancellationPolicy });
    expect((await content()).media, "Requires a fresh synthetic media fixture; never clear existing files").toEqual([]);
    const categories = await staff<CategoryMedia[]>("/api/v1/tours-operator/staff/categories/media"); expect(categories.map(row => row.category).sort()).toEqual([...CATEGORIES].sort());
    expect(categories.every(row => row.assetId === null && row.rightsStatus === "DRAFT"), "Category cover fixtures must be fresh; no owner image may be replaced").toBe(true);
    for (const locale of LOCALES) {
      const document = { name: `Synthetic E2E media tour ${locale}`, shortDescription: "Disposable acceptance fixture — not operator content.", description: "Synthetic local media test. No invented operator facts, prices or reviews.", includes: [], excludes: [], knowBeforeYouGo: [], meetingPoint: null, stops: [] };
      await staff(`${tourBase()}/content/${locale}`, "PUT", document);
      const saved = (await content()).content[locale]!.find(row => row.stage === "DRAFT")!;
      await staff(`${tourBase()}/content/${locale}/publish`, "POST", { revision: saved.revision });
    }
    const fixturePage = await browser.newPage(); try { jpeg = await syntheticJpeg(fixturePage, 960, "#f97316"); smallJpeg = await syntheticJpeg(fixturePage, 600, "#15803d"); } finally { await fixturePage.close(); }
  });
  test.afterAll(async () => {
    if (!api) return;
    try {
      if (auth && tour && commercialBefore) {
        const current = await staff<Tour>(tourBase());
        expect(JSON.stringify({ priceAdult: current.priceAdult, priceChild: current.priceChild, priceBasis: current.priceBasis, priceOptions: current.priceOptions, capacity: current.capacity, cancellationPolicy: current.cancellationPolicy })).toBe(commercialBefore);
      }
    } finally {
      try { if (auth) await api.post("/api/v1/identity/logout", { headers: headers() }); } finally { await api.dispose(); }
    }
  });

  for (const width of [360, 768, 1024, 1440]) {
    test(`${width}px: EN/AR real content/category UI, preserved locale drafts, keyboard and accessibility`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      for (const locale of ["en", "ar"] as const) {
        await selectStaff(page, locale); await page.goto(`${STAFF}/tours/${tour.id}/content`);
        await expect(page.getByRole("heading", { level: 1 })).toHaveText(locale === "ar" ? "المحتوى والصور" : "Content & photos");
        await expect(page.locator("html")).toHaveAttribute("dir", locale === "ar" ? "rtl" : "ltr");
        await expect(page.locator("#content-name")).toHaveValue("Synthetic E2E media tour en");
        const localTitle = `Synthetic unsaved ${width} ${locale}`; await page.locator("#content-name").fill(localTitle);
        const languages = page.getByRole("group", { name: locale === "ar" ? "لغة المحتوى" : "Content language" });
        await languages.getByRole("button", { name: /^العربية/ }).click(); await expect(page.locator("#content-name")).toHaveAttribute("dir", "rtl");
        await page.locator("#content-name").fill(`عنوان اختبار محلي ${width}`); await languages.getByRole("button", { name: /^English/ }).click();
        await expect(page.locator("#content-name")).toHaveValue(localTitle);
        await page.getByRole("button", { name: locale === "ar" ? "تحديث الحالة المحفوظة" : "Refresh saved state", exact: true }).click(); await expect(page.locator("#content-name")).toHaveValue(localTitle);
        await accessible(page);
        page.once("dialog", dialog => dialog.accept()); await page.locator("main").getByRole("link", { name: locale === "ar" ? "الرحلات" : "Tours", exact: true }).click();
        const categoriesLink = page.locator("main").getByRole("link", { name: locale === "ar" ? "أغلفة الفئات" : "Category covers", exact: true }); await categoriesLink.focus(); await page.keyboard.press("Enter");
        await expect(page).toHaveURL(`${STAFF}/categories`); await expect(page.getByRole("heading", { level: 1 })).toHaveText(locale === "ar" ? "أغلفة الفئات" : "Category covers");
        const choices = page.getByRole("group", { name: locale === "ar" ? "اختيار الفئة" : "Choose a category" });
        await expect(choices.locator("button[aria-pressed]")).toHaveCount(5); await page.locator("#category-alt-ar").fill(`وصف فئة محلي ${width}`);
        await choices.getByRole("button", { name: locale === "ar" ? /^البحر/ : /^Sea/ }).click(); await choices.getByRole("button", { name: locale === "ar" ? /^الصحراء/ : /^Desert/ }).click();
        await expect(page.locator("#category-alt-ar")).toHaveValue(`وصف فئة محلي ${width}`); await accessible(page);
        if (locale === "ar" || width === 1440) await page.screenshot({ path: test.info().outputPath(`media-categories-${locale}-${width}.png`), fullPage: true });
        page.once("dialog", dialog => dialog.accept()); await page.locator("main").getByRole("link", { name: locale === "ar" ? "الرحلات" : "Tours", exact: true }).click();
      }
    });
  }

  test("tour actual file picker creates only DRAFT; authorized blob decodes, guessed/private/optimizer access does not", async ({ page }) => {
    await selectStaff(page, "en"); await page.goto(`${STAFF}/tours/${tour.id}/content`); await expect(page.locator("#photo-file")).toBeVisible();
    for (const locale of LOCALES) await page.locator(`#upload-alt-${locale}`).fill(tourAlt[locale]);
    await page.locator("#photo-file").setInputFiles({ name: "synthetic-e2e-tour.jpg", mimeType: "image/jpeg", buffer: jpeg }); await paceUpload();
    await page.getByRole("button", { name: "Upload photo", exact: true }).click(); await notice(page, "Photo uploaded as a draft");
    await decoded(page, "img[src^='blob:']"); await page.screenshot({ path: test.info().outputPath("media-private-tour-draft.png"), fullPage: true }); const saved = await content(); expect(saved.media).toHaveLength(1); photo = saved.media[0]!; expect(photo.rightsStatus).toBe("DRAFT"); expect(photo.width).toBe(960); expect(photo.height).toBe(640);
    await privateBytes(assetId(photo.path)); await photoBytes(photo.path, 404); await photoBytes(`${photo.path}?v=w768`, 404); await optimizerDenied(photo.path);
    const denied = await api.get(`/api/v1/tours-operator/staff/media/preview/${assetId(photo.path)}?variant=base`, { headers: { Host: STAFF_HOST } }); expect(denied.status()).toBe(401);
    const html = await page.content(); expect(html).not.toContain(auth.token); expect(await page.locator("img[src^='blob:']").first().getAttribute("src")).toMatch(/^blob:/);
  });

  test("category actual file picker uploads all localized alt as DRAFT and uses authorized private bytes", async ({ page }) => {
    await selectStaff(page, "en"); await page.goto(`${STAFF}/categories`); await expect(page.locator("#category-file")).toBeVisible();
    for (const locale of LOCALES) await page.locator(`#category-alt-${locale}`).fill(categoryAlt[locale]);
    await page.locator("#category-file").setInputFiles({ name: "synthetic-e2e-category.jpg", mimeType: "image/jpeg", buffer: jpeg }); await paceUpload();
    await page.getByRole("button", { name: "Upload photo", exact: true }).click(); await notice(page, "Category cover uploaded as an unpublished draft");
    await decoded(page, "img[src^='blob:']"); await page.screenshot({ path: test.info().outputPath("media-private-category-draft.png"), fullPage: true }); cover = await category(); expect(cover.rightsStatus).toBe("DRAFT"); expect(cover.alt).toEqual(categoryAlt);
    await privateBytes(cover.assetId!); await photoBytes(cover.path!, 404); await optimizerDenied(cover.path!);
    const publicList = await (await api.get(`${SITE}/api/v1/tours-operator/categories/media?locale=en`)).json(); expect(publicList.some((row: { category: string }) => row.category === "DESERT")).toBe(false);
  });

  test("explicit rights reviews publish real tour/category pixels in EN/AR/RU/IT without IPX or bearer URLs", async ({ page }) => {
    await selectStaff(page, "en"); await page.goto(`${STAFF}/tours/${tour.id}/content`); await decoded(page, "img[src^='blob:']"); await rights(page, "tour");
    photo = (await content()).media[0]!; expect(photo.rightsStatus).toBe("APPROVED"); await photoBytes(photo.path, 200);
    await page.goto(`${STAFF}/categories`); await decoded(page, "img[src^='blob:']"); await rights(page, "category"); cover = await category(); expect(cover.rightsStatus).toBe("APPROVED"); await photoBytes(cover.path!, 200);
    for (const locale of LOCALES) {
      await page.goto(`${SITE}/${locale}/tour/${SLUG}`); const picture = page.getByRole("img", { name: tourAlt[locale], exact: true }).first(); await expect(picture).toBeVisible();
      await expect.poll(() => picture.evaluate(element => (element as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
      expect(await picture.getAttribute("src")).toBe(photo.path); expect(await picture.getAttribute("srcset")).not.toContain("_ipx"); expect(await picture.getAttribute("srcset")).not.toContain("w1440");
      if (locale === "en" || locale === "ar") await page.screenshot({ path: test.info().outputPath(`media-public-tour-${locale}.png`), fullPage: true });
      await page.goto(`${SITE}/${locale}/`); const categoryImage = page.locator("section[aria-labelledby='categories-heading']").getByRole("img", { name: categoryAlt[locale], exact: true }); await categoryImage.scrollIntoViewIfNeeded(); await expect(categoryImage).toBeVisible();
      await expect.poll(() => categoryImage.evaluate(element => (element as HTMLImageElement).naturalWidth)).toBeGreaterThan(0); expect(await categoryImage.getAttribute("src")).toBe(cover.path);
      const urls = await page.locator("a[href], img[src]").evaluateAll(elements => elements.map(element => element.getAttribute("href") || element.getAttribute("src") || "")); expect(urls.some(url => url.includes(auth.token))).toBe(false);
    }
  });

  test("alt change immediately revokes warmed public bytes; approved pixels are also denied while fixture tour inactive", async ({ page }) => {
    await selectStaff(page, "en"); await page.goto(`${STAFF}/tours/${tour.id}/content`); await decoded(page, "img[src^='blob:']"); await photoBytes(photo.path, 200);
    await page.locator(`#photo-alt-${photo.id}-en`).fill("Synthetic changed tour alt EN"); await page.getByRole("button", { name: "Save photo metadata", exact: true }).click();
    await notice(page, "Photo metadata saved and verified"); photo = (await content()).media[0]!; expect(photo.rightsStatus).toBe("DRAFT"); await photoBytes(photo.path, 404); await privateBytes(assetId(photo.path));
    await rights(page, "tour"); await photoBytes(photo.path, 200);
    await staff(`${tourBase()}/deactivate`, "PATCH"); try { await photoBytes(photo.path, 404); } finally { await staff(`${tourBase()}/activate`, "PATCH"); }
    await photoBytes(photo.path, 200);
  });

  test("replacement is a new private asset; old approved bytes and a subsequently unlinked image remain publicly revoked", async ({ page }) => {
    const oldPath = photo.path; await selectStaff(page, "en"); await page.goto(`${STAFF}/tours/${tour.id}/content`); await page.locator("#photo-replacement").selectOption(photo.id);
    await page.locator("#photo-file").setInputFiles({ name: "synthetic-replacement-small.jpg", mimeType: "image/jpeg", buffer: smallJpeg }); await paceUpload(); await page.getByRole("button", { name: "Upload photo", exact: true }).click();
    await notice(page, "Photo uploaded as a draft"); const preview = await decoded(page, "img[src^='blob:']"); expect(await preview.evaluate(element => (element as HTMLImageElement).naturalWidth)).toBe(600);
    photo = (await content()).media[0]!; expect(photo.path).not.toBe(oldPath); expect(photo.rightsStatus).toBe("DRAFT"); await photoBytes(oldPath, 404); await photoBytes(photo.path, 404); await privateBytes(assetId(photo.path));
    await rights(page, "tour"); await photoBytes(photo.path, 200); await page.getByRole("button", { name: "Unlink photo", exact: true }).click(); await page.getByRole("button", { name: "Save photo metadata", exact: true }).click();
    await notice(page, "Photo metadata saved and verified"); expect((await content()).media).toEqual([]); await photoBytes(photo.path, 404); await privateBytes(assetId(photo.path)); await optimizerDenied(photo.path);
  });

  test("category alt/replacement revoke earlier public pixels and small cover previews use base rather than absent derivatives", async ({ page }) => {
    await selectStaff(page, "ar"); await page.goto(`${STAFF}/categories`); await decoded(page, "img[src^='blob:']"); const original = cover.path!; await photoBytes(original, 200);
    await page.locator("#category-alt-en").fill("Synthetic updated category cover EN"); await page.getByRole("button", { name: "حفظ النص البديل", exact: true }).click(); await notice(page, "تم حفظ النص البديل للفئة");
    expect((await category()).rightsStatus).toBe("DRAFT"); await photoBytes(original, 404); await rights(page, "category", "ar"); await photoBytes(original, 200);
    await page.locator("#category-file").setInputFiles({ name: "synthetic-category-small.jpg", mimeType: "image/jpeg", buffer: smallJpeg }); await paceUpload(); await page.getByRole("button", { name: "رفع الصورة", exact: true }).click();
    await notice(page, "تم رفع غلاف الفئة كمسودة"); const preview = await decoded(page, "img[src^='blob:']"); expect(await preview.evaluate(element => (element as HTMLImageElement).naturalWidth)).toBe(600);
    cover = await category(); expect(cover.path).not.toBe(original); expect(cover.rightsStatus).toBe("DRAFT"); await photoBytes(original, 404); await photoBytes(cover.path!, 404); await optimizerDenied(cover.path!);
    await rights(page, "category", "ar"); await photoBytes(cover.path!, 200);
  });

  test("real edge/auth rejects unauthorized, spoofed and oversized files without linked writes or optimizer disclosure", async () => {
    const before = await content(); const denied = await api.get(`${tourBase()}/content`, { headers: { Host: STAFF_HOST } }); expect(denied.status()).toBe(401);
    const publicStaff = await api.get(`${SITE}${tourBase()}/content`); expect(publicStaff.status()).toBe(404);
    await paceUpload(); const spoof = await api.post(`${tourBase()}/media/upload`, { headers: headers(), multipart: { requestId: randomUUID(), file: { name: "fake.jpg", mimeType: "image/jpeg", buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>not an image</script></svg>') } } }); expect(spoof.status()).toBe(400);
    await paceUpload(); const oversized = await api.post(`${tourBase()}/media/upload`, { headers: headers(), multipart: { requestId: randomUUID(), file: { name: "too-big.jpg", mimeType: "image/jpeg", buffer: Buffer.alloc(10 * 1024 * 1024 + 1) } } }); expect(oversized.status()).toBe(413);
    expect((await content()).media).toEqual(before.media); expect((await content()).mediaRevision).toBe(before.mediaRevision); await optimizerDenied(cover.path!);
  });

  test("idempotent upload replay preserves approval; real concurrent upload makes stale ERP list write conflict instead of unlinking new data", async ({ page }) => {
    const requestId = randomUUID(); const first = await uploadApi(requestId, smallJpeg, tourAlt); expect(first.status()).toBe(200); const created = await first.json();
    let current = await content(); const initial = current.media[0]!; await staff(`${tourBase()}/media/${initial.id}/approve`, "POST", { revision: initial.revision });
    const replay = await uploadApi(requestId, smallJpeg, tourAlt); expect(replay.status()).toBe(200); expect(await replay.json()).toEqual(created); current = await content(); expect(current.media).toHaveLength(1); expect(current.media[0]!.rightsStatus).toBe("APPROVED");
    const misuse = await uploadApi(requestId, smallJpeg, { ...tourAlt, en: "Different input on same nonce" }); expect(misuse.status()).toBe(400); expect((await content()).media[0]!.rightsStatus).toBe("APPROVED");
    await selectStaff(page, "en"); await page.goto(`${STAFF}/tours/${tour.id}/content`); await decoded(page, "img[src^='blob:']"); await page.locator(`#photo-alt-${initial.id}-en`).fill("Preserve unsaved local alt across real conflict");
    const competing = await uploadApi(randomUUID(), jpeg, categoryAlt); expect(competing.status()).toBe(200); expect((await content()).media).toHaveLength(2);
    await page.getByRole("button", { name: "Save photo metadata", exact: true }).click(); await expect(page.getByRole("alert").filter({ hasText: "saved photo list changed" })).toHaveCount(1);
    await expect(page.locator(`#photo-alt-${initial.id}-en`)).toHaveValue("Preserve unsaved local alt across real conflict"); expect((await content()).media).toHaveLength(2); await expect(page.getByRole("button", { name: "Save photo metadata", exact: true })).toBeDisabled();
    await page.getByRole("button", { name: "Discard local photo edits & reload list", exact: true }).click(); await expect(page.getByRole("button", { name: "Unlink photo", exact: true })).toHaveCount(2);
    const linked = (await content()).media; await page.getByRole("button", { name: "Unlink photo", exact: true }).first().click(); await page.getByRole("button", { name: "Unlink photo", exact: true }).click(); await page.getByRole("button", { name: "Save photo metadata", exact: true }).click();
    await notice(page, "Photo metadata saved and verified"); expect((await content()).media).toEqual([]); for (const item of linked) { await photoBytes(item.path, 404); await privateBytes(assetId(item.path)); }
  });
});
