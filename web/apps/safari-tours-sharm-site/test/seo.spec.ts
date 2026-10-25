import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { buildRobots, buildSitemap } from "../server/utils/sitemap";
import { breadcrumbList, homeGraph, jsonLd, touristTrip } from "../app/utils/structuredData";
import { facebookUrl, instagramUrl, tripadvisorUrl } from "../app/content/locales";
import { infoCopy } from "../app/content/info";
import { faqDescription, privacyDescription, termsDescription } from "../app/utils/seoDescriptions";

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

describe("static asset cache rules", () => {
  const config = readFileSync(`${process.cwd()}/nuxt.config.ts`, "utf8");
  it("gives unhashed public assets a 7-day, non-immutable cache", () => {
    for (const route of ["/images/**", "/brand/**", "/og-default.png", "/favicon.svg"]) {
      expect(config).toContain(`"${route}": { headers: { "cache-control": "public, max-age=604800" } }`);
    }
    expect(config).not.toMatch(/max-age=604800[^}]*immutable/);
    expect(config).toContain('buildAssetsDir: "/_safari/"');
  });
});

describe("structured data", () => {
  const parse = (s: string) => JSON.parse(s);
  it("home graph has logo, official sameAs and a WebSite node, and no ratings", () => {
    const raw = jsonLd(homeGraph("it"));
    const graph = parse(raw)["@graph"];
    const org = graph.find((n: { "@type": string }) => n["@type"] === "TravelAgency");
    expect(org.logo).toBe("https://safaritourssharm.com/brand/logo-full.webp");
    expect(org.sameAs).toEqual([facebookUrl, instagramUrl, tripadvisorUrl]);
    const site = graph.find((n: { "@type": string }) => n["@type"] === "WebSite");
    expect(site.inLanguage).toBe("it");
    expect(raw).not.toMatch(/aggregateRating|"review"|availability/i);
  });

  it("builds absolute breadcrumbs per locale", () => {
    const crumbs = parse(jsonLd(breadcrumbList([
      { name: "الرئيسية", path: "/ar" },
      { name: "كل الرحلات", path: "/ar/tours" },
    ])));
    expect(crumbs["@type"]).toBe("BreadcrumbList");
    expect(crumbs.itemListElement.map((i: { item: string }) => i.item)).toEqual([
      "https://safaritourssharm.com/ar",
      "https://safaritourssharm.com/ar/tours",
    ]);
  });

  it("TouristTrip uses the canonical url, EUR offer, and omits availability/ratings", () => {
    const raw = jsonLd(touristTrip({
      name: "Quad <b>", description: "d", inLanguage: "ru", touristType: "Desert",
      path: "/ru/tour/sunset-quad-bike", imagePath: "/media/x.webp", price: { amount: "30.00", currencyCode: "EUR" },
    }));
    expect(raw).not.toContain("<");
    const trip = parse(raw);
    expect(trip["@type"]).toBe("TouristTrip");
    expect(trip.url).toBe("https://safaritourssharm.com/ru/tour/sunset-quad-bike");
    expect(trip.offers).toMatchObject({ price: "30.00", priceCurrency: "EUR", url: trip.url });
    expect(trip.provider.name).toBe("Safari Tours Sharm");
    expect(raw).not.toMatch(/aggregateRating|"review"|availability/i);
    const onRequest = touristTrip({ name: "n", inLanguage: "en", touristType: "t", path: "/en/tour/a" });
    expect(onRequest.offers).toBeUndefined();
  });
});

describe("terms / privacy / faq descriptions", () => {
  for (const locale of Object.keys(infoCopy) as (keyof typeof infoCopy)[]) {
    it(`${locale}: terms and privacy differ and are long enough`, () => {
      const info = infoCopy[locale];
      const terms = termsDescription(info.terms);
      const privacy = privacyDescription(info.privacy);
      expect(terms).not.toBe(privacy);
      expect(terms.length).toBeGreaterThan(100);
      expect(privacy.length).toBeGreaterThan(100);
      expect(faqDescription(info.faq, info.contact).length).toBeGreaterThan(80);
    });
  }
});
