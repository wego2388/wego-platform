import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

interface TokenDocument {
  color: {
    brand: Record<string, string>;
    surface: Record<string, string>;
    text: Record<string, string>;
    border: Record<string, string>;
    status: Record<string, string>;
  };
  layout: { touchTargetMinPx: number };
  version: string;
}

describe("Sharm To Go design-token contract", () => {
  it("keeps the repo design source aligned with executable semantic CSS", () => {
    const tokens = JSON.parse(readFileSync(resolve(process.cwd(), "../../../clients/sharm-to-go/design/tokens.json"), "utf8")) as TokenDocument;
    const css = readFileSync(resolve(process.cwd(), "app/assets/css/main.css"), "utf8");
    const criticalTokens: Array<[string, string]> = [
      ["brand-sea", tokens.color.brand.sea],
      ["brand-lagoon", tokens.color.brand.lagoon],
      ["brand-sand", tokens.color.brand.sand],
      ["brand-sun", tokens.color.brand.sun],
      ["surface-canvas", tokens.color.surface.canvas],
      ["text-primary", tokens.color.text.primary],
      ["text-secondary", tokens.color.text.secondary],
      ["border-default", tokens.color.border.default],
      ["status-success", tokens.color.status.success],
      ["status-warning", tokens.color.status.warning],
      ["status-danger", tokens.color.status.danger],
    ];

    expect(tokens.version).toBe("0.1.0");
    expect(tokens.layout.touchTargetMinPx).toBeGreaterThanOrEqual(44);
    for (const [name, value] of criticalTokens) {
      expect(css).toContain(`--stg-color-${name}: ${value};`);
    }
  });

  it("keeps the executable public mark aligned with the registered design asset", () => {
    const registeredMark = readFileSync(resolve(process.cwd(), "../../../clients/sharm-to-go/design/assets/sharm-to-go-mark.svg"), "utf8");
    const publicMark = readFileSync(resolve(process.cwd(), "public/favicon.svg"), "utf8");

    expect(publicMark).toBe(registeredMark);
  });
});
