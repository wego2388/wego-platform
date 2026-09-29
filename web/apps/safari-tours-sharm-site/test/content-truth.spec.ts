import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

import { siteCopy } from "../app/content/locales";

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

    const terms = source("app/pages/terms.vue");
    expect(terms).toContain("at least 48 hours");
    expect(terms).toContain("24–48 hours");
    expect(terms).toContain("50% refund");
    expect(terms).toContain("no-shows are non-refundable");
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
    ].join("\n");

    expect(runtimeContent).not.toMatch(/4\.9|500\+|since 2010|24\/7|100% secure/i);
    expect(runtimeContent).not.toMatch(/Vodafone Cash|Fawry|Hotel pickup included/i);
    expect(runtimeContent).not.toMatch(/Free cancellation until|TouristInformationCenter/i);
  });
});
