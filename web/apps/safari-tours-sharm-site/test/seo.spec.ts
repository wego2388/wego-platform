import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { buildRobots, buildSitemap } from "../server/utils/sitemap";

describe("fallback metadata", () => {
  it("does not promise online checkout or live seats when the sales mode is enquiry-only", () => {
    const config = readFileSync(`${process.cwd()}/nuxt.config.ts`, "utf8");
    expect(config).toContain("Browse tour details, catalog prices and local travel information.");
    expect(config).not.toContain("book direct online");
    expect(config).not.toContain("live availability");
  });
});

describe("sitemap", () => {
  const xml = buildSitemap("https://safaritourssharm.com/", ["sunset-quad-bike", "bad slug<script>"]);

  it("lists every page in every language with hreflang alternates and x-default", () => {
    expect(xml).toContain("<loc>https://safaritourssharm.com/ar/tour/sunset-quad-bike</loc>");
    expect(xml).toContain('hreflang="ru" href="https://safaritourssharm.com/ru/category/desert"');
    expect(xml).toContain('hreflang="x-default" href="https://safaritourssharm.com/en/trip-finder"');
    expect(xml).toContain('hreflang="ar" href="https://safaritourssharm.com/ar/gallery"');
    expect(xml).toContain("<loc>https://safaritourssharm.com/it</loc>");
  });

  it("drops anything that is not a clean slug and never lists private pages", () => {
    expect(xml).not.toContain("script");
    expect(xml).not.toMatch(/booking|my-booking|design-system/);
    expect((xml.match(/<url>/g) ?? []).length).toBe((9 + 5 + 1) * 4);
  });
});

describe("robots.txt", () => {
  it("points to the sitemap and keeps personal pages out of search", () => {
    const robots = buildRobots("https://safaritourssharm.com");
    expect(robots).toContain("Sitemap: https://safaritourssharm.com/sitemap.xml");
    expect(robots).toContain("Disallow: /en/booking/");
    expect(robots).toContain("Disallow: /ar/my-booking");
    expect(robots).toContain("Disallow: /api/");
  });
});
