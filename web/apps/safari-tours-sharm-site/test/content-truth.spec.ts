import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

import { siteCopy } from "../app/content/locales";
import { infoCopy } from "../app/content/info";

const appRoot = process.cwd();

function source(relativePath: string): string {
  return readFileSync(`${appRoot}/${relativePath}`, "utf8");
}

describe("commercial content truth", () => {
  it("keeps the approved cancellation tiers consistent in every locale and the terms page", () => {
    for (const copy of Object.values(siteCopy)) {
      expect(copy.cancellationBody).toContain("48");
      expect(copy.cancellationBody).toContain("24");
      expect(copy.cancellationBody).toContain("50");
    }

    // The terms page text lives in app/content/info.ts (four languages).
    for (const copy of Object.values(infoCopy)) {
      const cancellation = copy.terms.sections[3]!.body.join(" ");
      expect(cancellation).toContain("48");
      expect(cancellation).toContain("24");
      expect(cancellation).toContain("50%");
    }
    expect(infoCopy.en.terms.sections[3]!.body.join(" ")).toMatch(/At least 48 hours.*full refund.*24 and 48 hours.*50% refund.*do not show up: no refund/);
  });

  it("does not publish placeholder social proof, payment methods, or universal inclusions", () => {
    const runtimeContent = [
      source("nuxt.config.ts"),
      source("app/content/locales.ts"),
      source("app/pages/index.vue"),
      source("app/pages/tour/[slug].vue"),
      source("app/pages/booking/[slotId].vue"),
      source("app/pages/booking/confirmation.vue"),
      source("app/pages/terms.vue"),
      source("app/content/info.ts"),
    ].join("\n");

    expect(runtimeContent).not.toMatch(/4\.9|500\+|since 2010|24\/7|100% secure/i);
    expect(runtimeContent).not.toMatch(/Vodafone Cash|Fawry|Hotel pickup included/i);
    expect(runtimeContent).not.toMatch(/Free cancellation until|TouristInformationCenter/i);
  });
});
