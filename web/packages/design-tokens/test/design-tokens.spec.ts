import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

import { wegoDesignTokens } from "../src/index";

const cssPath = fileURLToPath(new URL("../src/tokens.css", import.meta.url));
const css = readFileSync(cssPath, "utf-8");

// Splits tokens.css into its two real blocks so a value can be looked up
// by custom-property name within the light (":root {") or dark
// (":root[data-theme=\"dark\"] {") block specifically — a plain
// file-wide regex would find whichever block's declaration happens to
// come first, silently comparing the wrong theme.
function blockFor(theme: "light" | "dark"): string {
  const marker = theme === "light" ? ":root {" : ':root[data-theme="dark"] {';
  const start = css.indexOf(marker);
  if (start === -1) throw new Error(`Could not find the ${theme} block in tokens.css`);
  const end = css.indexOf("\n}", start);
  return css.slice(start, end);
}

function cssValue(block: string, property: string): string | undefined {
  const match = block.match(new RegExp(`--${property}:\\s*([^;]+);`));
  return match?.[1]?.trim();
}

// kebab-case, matching the CSS custom-property naming convention.
function toKebab(camel: string): string {
  return camel.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
}

describe("design tokens: TS/CSS parity", () => {
  const lightBlock = blockFor("light");
  const darkBlock = blockFor("dark");

  it.each(Object.entries(wegoDesignTokens.color.light))("light color %s matches tokens.css", (name: string, value: string) => {
    expect(cssValue(lightBlock, `wego-color-${toKebab(name)}`)).toBe(value);
  });

  it.each(Object.entries(wegoDesignTokens.color.dark))("dark color %s matches tokens.css", (name: string, value: string) => {
    expect(cssValue(darkBlock, `wego-color-${toKebab(name)}`)).toBe(value);
  });

  it("light and dark declare exactly the same set of color roles", () => {
    expect(Object.keys(wegoDesignTokens.color.dark).sort()).toEqual(Object.keys(wegoDesignTokens.color.light).sort());
  });

  it.each(Object.entries(wegoDesignTokens.radius))("radius %s matches tokens.css", (name: string, value: string) => {
    expect(cssValue(lightBlock, `wego-radius-${toKebab(name)}`)).toBe(value);
  });

  it.each(Object.entries(wegoDesignTokens.zIndex))("z-index %s matches tokens.css", (name: string, value: number) => {
    expect(cssValue(lightBlock, `wego-z-${toKebab(name)}`)).toBe(String(value));
  });

  it("motion tokens match tokens.css", () => {
    expect(cssValue(lightBlock, "wego-motion-duration-fast")).toBe(wegoDesignTokens.motion.durationFast);
    expect(cssValue(lightBlock, "wego-motion-duration-standard")).toBe(wegoDesignTokens.motion.durationStandard);
    expect(cssValue(lightBlock, "wego-motion-duration-slow")).toBe(wegoDesignTokens.motion.durationSlow);
    expect(cssValue(lightBlock, "wego-motion-easing-standard")).toBe(wegoDesignTokens.motion.easingStandard);
    expect(cssValue(lightBlock, "wego-motion-easing-decelerate")).toBe(wegoDesignTokens.motion.easingDecelerate);
    expect(cssValue(lightBlock, "wego-motion-easing-accelerate")).toBe(wegoDesignTokens.motion.easingAccelerate);
  });

  it("control-min size matches tokens.css", () => {
    expect(cssValue(lightBlock, "wego-size-control-min")).toBe(wegoDesignTokens.size.controlMin);
  });

  it("the dark block is opt-in (:root[data-theme=\"dark\"]), never a bare prefers-color-scheme default", () => {
    // The real hazard this guards: a bare `@media (prefers-color-scheme:
    // dark)` block here would silently reskin every consuming app,
    // including the three that have never built or verified dark-mode
    // rendering. Only the scoped, opt-in selector below is acceptable in
    // this shared file.
    expect(css).toContain(':root[data-theme="dark"] {');
    expect(css).not.toMatch(/@media\s*\(prefers-color-scheme:\s*dark\)/);
  });
});

describe("design tokens: WCAG contrast", () => {
  function srgbToLinear(channel: number): number {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  function relativeLuminance(hex: string): number {
    const n = parseInt(hex.replace("#", ""), 16);
    const r = (n >> 16) & 255;
    const g = (n >> 8) & 255;
    const b = n & 255;
    return 0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b);
  }

  function contrastRatio(hexA: string, hexB: string): number {
    const [high, low] = [relativeLuminance(hexA), relativeLuminance(hexB)].sort((a, b) => b - a);
    return (high + 0.05) / (low + 0.05);
  }

  const AA_TEXT = 4.5;
  const AA_UI = 3;

  for (const [theme, color] of [
    ["light", wegoDesignTokens.color.light],
    ["dark", wegoDesignTokens.color.dark],
  ] as const) {
    describe(`${theme} theme`, () => {
      it("ink is readable on canvas and surface", () => {
        expect(contrastRatio(color.ink, color.canvas)).toBeGreaterThanOrEqual(AA_TEXT);
        expect(contrastRatio(color.ink, color.surface)).toBeGreaterThanOrEqual(AA_TEXT);
      });

      it("muted text is readable on canvas and surface", () => {
        expect(contrastRatio(color.muted, color.canvas)).toBeGreaterThanOrEqual(AA_TEXT);
        expect(contrastRatio(color.muted, color.surface)).toBeGreaterThanOrEqual(AA_TEXT);
      });

      const statusRoles = ["accent", "success", "warning", "danger", "info"] as const;
      type StatusRole = (typeof statusRoles)[number];

      it.each(statusRoles)("%s is readable as text on canvas and surface", (role: StatusRole) => {
        expect(contrastRatio(color[role], color.canvas)).toBeGreaterThanOrEqual(AA_TEXT);
        expect(contrastRatio(color[role], color.surface)).toBeGreaterThanOrEqual(AA_TEXT);
      });

      it.each(statusRoles)("%s is readable as text on its own soft background (the badge pattern)", (role: StatusRole) => {
        const soft = color[`${role}Soft` as keyof typeof color];
        expect(contrastRatio(color[role], soft)).toBeGreaterThanOrEqual(AA_TEXT);
      });

      it("borderStrong meets the non-text UI contrast floor against surface", () => {
        expect(contrastRatio(color.borderStrong, color.surface)).toBeGreaterThanOrEqual(AA_UI);
      });

      it("focus ring is visible against canvas", () => {
        expect(contrastRatio(color.focus, color.canvas)).toBeGreaterThanOrEqual(AA_UI);
      });
    });
  }
});
