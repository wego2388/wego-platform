import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { brandTitle } from "../app/utils/brandTitle";
const source = (path: string) => readFileSync(`${process.cwd()}/app/${path}`, "utf8");
describe("Safari identity and lightweight destination backgrounds", () => {
  it("puts the correct brand first without duplicating it or losing page intent", () => {
    expect(brandTitle("Tours — Safari Tours Sharm", "en")).toBe("Safari Tours Sharm — Tours");
    expect(brandTitle("الرحلات — Safari Tours Sharm", "ar")).toBe("سفاري تورز شرم — الرحلات");
    expect(brandTitle(undefined, "ru")).toBe("Safari Tours Sharm");
    expect(brandTitle("Safari Tours Sharm", "it")).toBe("Safari Tours Sharm");
    expect(brandTitle("Sea & desert", "en")).toBe("Safari Tours Sharm — Sea & desert");
  });
  it("shares optimized decorative art on every marketing and legal header, not tour evidence", () => {
    for (const path of ["pages/tours.vue", "pages/category/[slug].vue", "pages/contact.vue", "pages/faq.vue", "pages/trip-finder.vue", "components/info/InfoDocument.vue"]) expect(source(path)).toContain("BrandPageBanner");
    const banner = source("components/brand/PageBanner.vue");
    expect(banner).toContain('alt="" aria-hidden="true"'); expect(banner).toContain("brandArtworkCopy[locale]");
    expect(banner).toContain(":srcset="); expect(banner).toContain(":width="); expect(banner).toContain(":height=");
    expect(source("components/brand/SectionBackdrop.vue")).toContain('aria-hidden="true"');
    expect(source("components/brand/SectionBackdrop.vue")).not.toMatch(/<img|animation:|<script src=/);
    expect(source("pages/tour/[slug].vue")).not.toContain("BrandPageBanner");
    expect(source("app.vue")).toContain('name: "application-name", content: "Safari Tours Sharm"');
  });
});
