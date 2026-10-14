// Read-only visual release check. No authentication, forms or production writes.
import { chromium } from "@playwright/test";
import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
const base = process.env.SAFARI_MEDIA_BASE_URL ?? "http://127.0.0.1:58116";
const output = process.env.SAFARI_MEDIA_EVIDENCE_DIR ?? "/home/wego/output/safari-media-20261015";
await mkdir(output, { recursive: true });
const browser = await chromium.launch();
const results = [];
try {
  for (const mobile of [false, true]) {
    const context = await browser.newContext({ viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 }, reducedMotion: mobile ? "reduce" : "no-preference" });
    await context.route("**/*", route => ["GET", "HEAD"].includes(route.request().method()) ? route.continue() : route.abort());
    const page = await context.newPage();
    for (const path of ["/ar", "/en", "/ar/tours", "/en/category/sea", "/ar/tour/sunset-quad-bike", "/en/about", "/ar/contact", "/ar/privacy"]) {
      const errors = [];
      const listener = error => errors.push(error.message);
      page.on("pageerror", listener);
      const response = await page.goto(base + path, { waitUntil: "networkidle" });
      assert.equal(response.status(), 200, path);
      await page.locator("h1").waitFor();
      for (const image of await page.locator("main img").all()) {
        await image.scrollIntoViewIfNeeded();
        await image.evaluate(img => img.decode());
      }
      await page.evaluate(() => window.scrollTo(0, 0));
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `Overflow ${path}`);
      assert.deepEqual(errors, [], `Browser errors ${path}`);
      if (path === "/ar" || path === "/en") {
        if (mobile) assert.equal(await page.locator("video").count(), 0, "Mobile must stay poster-only");
        else {
          await page.locator("video").waitFor();
          await page.waitForFunction(() => { const video = document.querySelector("video"); return video?.readyState >= 2 && video.currentTime > 0; });
          await page.locator(".sts-hero button").click();
          assert.ok(await page.locator("video").evaluate(video => video.paused), "Pause control");
        }
      }
      const name = `${mobile ? "mobile" : "desktop"}${path.replaceAll("/", "-")}`;
      await page.screenshot({ path: `${output}/${name}.png`, fullPage: true });
      results.push({ path, mobile, status: response.status(), images: await page.locator("main img").count(), errors });
      page.off("pageerror", listener);
    }
    await context.close();
  }
  await writeFile(`${output}/results.json`, JSON.stringify({ base, results }, null, 2));
  console.log(JSON.stringify({ passed: results.length, output }));
} finally { await browser.close(); }
