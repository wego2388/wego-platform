// Regression test for a real, previously-shipped bug (found by an external
// review, confirmed by booting the built site): a page file named
// `foo.vue` sitting next to a directory `foo/` makes Nuxt treat `foo.vue`
// as the parent route of everything inside `foo/`, which is only ever
// rendered if `foo.vue` contains a `<NuxtPage />` outlet. `experiences/
// [id].vue` had no such outlet, so `/experiences/:id/request` silently
// rendered the detail page instead of the request form — unreachable in
// the real app despite every component-level test (which mounts
// request.vue directly, bypassing Nuxt's router) passing. Fixed by moving
// the detail page to `experiences/[id]/index.vue`, a sibling of
// `request.vue` rather than its implicit parent.
//
// This test asserts the fix structurally, directly on the filesystem, so
// the antipattern cannot silently return — a mount-level test cannot
// catch this class of bug at all, since Nuxt's route nesting is resolved
// by its build step, not by @vue/test-utils.
import { readdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { describe, expect, it } from "vitest";

const pagesRoot = resolve(__dirname, "../app/pages");

function collectVueFiles(dir: string): string[] {
  const entries = readdirSync(dir);
  return entries.flatMap((entry) => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return collectVueFiles(full);
    return entry.endsWith(".vue") ? [full] : [];
  });
}

describe("page route structure", () => {
  it("has no page file that shares its base name with a sibling directory (the Nuxt implicit-parent-route trap)", () => {
    const files = collectVueFiles(pagesRoot);
    const directories = new Set(files.map((file) => dirname(file)));

    const offenders = files.filter((file) => {
      if (!file.endsWith(".vue") || file.endsWith("index.vue")) return false;
      const baseName = file.slice(0, -4); // strip ".vue"
      return directories.has(baseName);
    });

    expect(offenders).toEqual([]);
  });

  it("keeps the real request form reachable as a true sibling of the detail page", () => {
    const files = collectVueFiles(pagesRoot).map((file) => file.replace(pagesRoot, ""));

    expect(files).toContain("/experiences/[id]/index.vue");
    expect(files).toContain("/experiences/[id]/request.vue");
    expect(files).not.toContain("/experiences/[id].vue");
  });
});
