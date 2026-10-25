import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

// Two resolved Vue runtimes (e.g. vue 3.5.44 with an older pinned
// @vue/server-renderer) build and unit-test fine but break SSR at runtime
// ("Cannot read properties of null (reading 'ce')"). Keep exactly one.
describe("workspace Vue runtime", () => {
  it("resolves a single version of vue and its runtime packages", () => {
    const lock = readFileSync(resolve(process.cwd(), "../../pnpm-lock.yaml"), "utf8");
    for (const pkg of ["vue", "@vue/server-renderer", "@vue/runtime-core", "@vue/shared"]) {
      const escaped = pkg.replace(/[/@]/g, (c) => `\\${c}`);
      const versions = new Set([...lock.matchAll(new RegExp(`^  '?${escaped}@(\\d+\\.\\d+\\.\\d+)`, "gm"))].map((m) => m[1]));
      expect([...versions], pkg).toHaveLength(1);
    }
  });
});
