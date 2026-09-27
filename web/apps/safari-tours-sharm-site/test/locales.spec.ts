import { describe, it, expect } from "vitest";
import { siteCopy, directionFor, localeNames, rtlLocales } from "../app/content/locales";
import type { StsLocale } from "../app/content/locales";

const LOCALES: StsLocale[] = ["en", "ru", "ar", "it"];

describe("locales", () => {
  it("has copy for all four locales", () => {
    for (const locale of LOCALES) {
      expect(siteCopy[locale]).toBeDefined();
    }
  });

  it("every locale has required keys", () => {
    for (const locale of LOCALES) {
      const c = siteCopy[locale];
      expect(c.languageName).toBeTruthy();
      expect(c.hero.title).toBeTruthy();
      expect(c.hero.cta).toBeTruthy();
      expect(c.trustItems.length).toBeGreaterThan(0);
      expect(c.howSteps.length).toBe(4);
      expect(Object.keys(c.categories).length).toBe(5);
    }
  });

  it("Arabic is the only RTL locale", () => {
    expect(directionFor("ar")).toBe("rtl");
    expect(directionFor("en")).toBe("ltr");
    expect(directionFor("ru")).toBe("ltr");
    expect(directionFor("it")).toBe("ltr");
    expect(rtlLocales).toEqual(["ar"]);
  });

  it("locale names are defined for all locales", () => {
    for (const locale of LOCALES) {
      expect(localeNames[locale]).toBeTruthy();
    }
  });

  it("all category descriptions are non-empty strings", () => {
    const cats = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"] as const;
    for (const locale of LOCALES) {
      for (const cat of cats) {
        expect(siteCopy[locale].categories[cat].name).toBeTruthy();
        expect(siteCopy[locale].categories[cat].description).toBeTruthy();
      }
    }
  });
});
