import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

interface ColorCategories {
  brand: Record<string, string>;
  surface: Record<string, string>;
  text: Record<string, string>;
  border: Record<string, string>;
  status: Record<string, string>;
}

interface TokenDocument {
  color: ColorCategories;
  colorDark: Partial<ColorCategories> & { _comment?: string };
  layout: { touchTargetMinPx: number };
  version: string;
}

// kebab-case, matching the --sdc-color-<category>-<key> naming main.css uses.
function toKebab(word: string): string {
  return word.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
}

function flattenColors(categories: Partial<ColorCategories>): Array<[string, string]> {
  const entries: Array<[string, string]> = [];
  for (const [category, values] of Object.entries(categories)) {
    // colorDark carries a sibling "_comment" string explaining the override
    // convention — not a color category, so it must not be iterated as one.
    if (!values || typeof values !== "object") continue;
    for (const [key, value] of Object.entries(values)) {
      entries.push([`${category}-${toKebab(key)}`, value]);
    }
  }
  return entries;
}

describe("Sharm Divers Club design-token contract", () => {
  const tokens = JSON.parse(
    readFileSync(resolve(process.cwd(), "../../../clients/sharm-divers-club/design/tokens.json"), "utf8"),
  ) as TokenDocument;
  const css = readFileSync(resolve(process.cwd(), "app/assets/css/main.css"), "utf8");

  it("keeps every light-mode color tokens.json defines aligned with main.css — not just a curated subset", () => {
    expect(tokens.version).toBe("0.2.0");
    expect(tokens.layout.touchTargetMinPx).toBeGreaterThanOrEqual(44);

    // surface.raised, surface.inverse, text.inverse, text.link, border.strong
    // are real tokens.json entries with no CSS custom property yet — nothing
    // in the site consumes them (confirmed by grep before writing this test).
    // Documented as reserved, not silently skipped: this list is the
    // complete set of exceptions, not a shrinking net.
    const notYetWiredIntoCss = new Set(["surface-raised", "surface-inverse", "text-inverse", "text-link", "border-strong"]);

    for (const [name, value] of flattenColors(tokens.color)) {
      if (notYetWiredIntoCss.has(name)) continue;
      expect(css, `--sdc-color-${name} should equal tokens.json's ${name}`).toContain(`--sdc-color-${name}: ${value};`);
    }
  });

  it("keeps the dark-mode overrides aligned with main.css's prefers-color-scheme block", () => {
    const darkBlockMatch = css.match(/@media \(prefers-color-scheme: dark\) \{\s*:root \{([\s\S]*?)\n {2}\}\n\}/);
    expect(darkBlockMatch, "expected a @media (prefers-color-scheme: dark) { :root { ... } } block in main.css").toBeTruthy();
    const darkBlock = darkBlockMatch![1]!;

    for (const [name, value] of flattenColors(tokens.colorDark)) {
      expect(darkBlock, `dark --sdc-color-${name} should equal tokens.json colorDark's ${name}`).toContain(
        `--sdc-color-${name}: ${value};`,
      );
    }
  });

  it("keeps the executable public mark aligned with the registered design asset", () => {
    const registeredMark = readFileSync(resolve(process.cwd(), "../../../clients/sharm-divers-club/design/assets/sharm-divers-club-mark.svg"), "utf8");
    const publicMark = readFileSync(resolve(process.cwd(), "public/favicon.svg"), "utf8");

    expect(publicMark).toBe(registeredMark);
  });
});
