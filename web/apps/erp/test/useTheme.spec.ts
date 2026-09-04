import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { applyTheme, effectiveTheme, readThemePreference, writeThemePreference } from "../app/composables/useTheme";

function stubMatchMedia(prefersDark: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: query === "(prefers-color-scheme: dark)" ? prefersDark : false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

describe("useTheme", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute("data-theme");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("defaults to system with nothing stored", () => {
    expect(readThemePreference()).toBe("system");
  });

  it("round-trips an explicit preference through storage", () => {
    writeThemePreference("dark");
    expect(readThemePreference()).toBe("dark");
    writeThemePreference("light");
    expect(readThemePreference()).toBe("light");
  });

  it("writing system clears the stored key rather than storing the literal string", () => {
    writeThemePreference("dark");
    writeThemePreference("system");
    expect(window.localStorage.getItem("wego-erp-theme")).toBeNull();
    expect(readThemePreference()).toBe("system");
  });

  it("ignores a corrupt stored value and falls back to system", () => {
    window.localStorage.setItem("wego-erp-theme", "not-a-real-theme");
    expect(readThemePreference()).toBe("system");
  });

  it("system preference resolves against the real OS signal", () => {
    stubMatchMedia(true);
    expect(effectiveTheme("system")).toBe("dark");
    stubMatchMedia(false);
    expect(effectiveTheme("system")).toBe("light");
  });

  it("an explicit preference overrides the OS signal either direction", () => {
    stubMatchMedia(true);
    expect(effectiveTheme("light")).toBe("light");
    stubMatchMedia(false);
    expect(effectiveTheme("dark")).toBe("dark");
  });

  it("applyTheme sets data-theme=dark only when the effective theme is dark", () => {
    stubMatchMedia(false);
    applyTheme("dark");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });

  it("applyTheme removes the attribute entirely for light, never sets data-theme=light", () => {
    document.documentElement.setAttribute("data-theme", "dark");
    stubMatchMedia(false);
    applyTheme("light");
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
  });

  it("applyTheme(system) follows the live OS signal", () => {
    stubMatchMedia(true);
    applyTheme("system");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");

    stubMatchMedia(false);
    applyTheme("system");
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
  });
});
