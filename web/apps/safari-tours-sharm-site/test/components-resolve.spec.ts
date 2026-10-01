import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Nuxt auto-imports components by folder-prefixed name (components/info/InfoDocument.vue →
 * <InfoDocument>). A wrong name renders nothing and no linter or type check notices, so
 * every PascalCase tag in a template must be a registered component, a Nuxt/Vue built-in
 * or something the file imports itself.
 */
const BUILT_INS = new Set([
  "NuxtLink", "NuxtLinkLocale", "NuxtLayout", "NuxtPage", "NuxtImg", "NuxtPicture", "ClientOnly", "Icon",
  "Transition", "TransitionGroup", "KeepAlive", "Teleport", "Suspense", "NuxtRouteAnnouncer",
]);

function vueFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? vueFiles(path) : path.endsWith(".vue") ? [path] : [];
  });
}

function registeredComponents(): Set<string> {
  const names = new Set<string>();
  const root = join(process.cwd(), "app/components");
  for (const file of vueFiles(root)) {
    const parts = file.slice(root.length + 1, -".vue".length).split("/");
    const base = parts.pop()!;
    const prefix = parts.map((p) => p[0]!.toUpperCase() + p.slice(1)).join("");
    // Nuxt drops a folder prefix the file name already starts with.
    names.add(base.startsWith(prefix) ? base : prefix + base);
  }
  return names;
}

describe("templates only use components that exist", () => {
  const registered = registeredComponents();
  for (const file of vueFiles(join(process.cwd(), "app"))) {
    it(file.slice(process.cwd().length + 1), () => {
      const source = readFileSync(file, "utf8");
      const template = source.slice(source.indexOf("<template>"));
      const imported = new Set([...source.matchAll(/import\s*\{([^}]*)\}/g)].flatMap((m) => m[1]!.split(",").map((s) => s.trim().split(/\s+as\s+/).pop()!)));
      for (const m of source.matchAll(/import\s+([A-Z]\w*)\s+from/g)) imported.add(m[1]!);
      const tags = new Set([...template.matchAll(/<([A-Z][A-Za-z0-9]*)/g)].map((m) => m[1]!));
      const unknown = [...tags].filter((tag) => !registered.has(tag) && !BUILT_INS.has(tag) && !imported.has(tag));
      expect(unknown).toEqual([]);
    });
  }
});
