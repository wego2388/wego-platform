import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));

export const repositoryRoot = path.resolve(scriptDirectory, "../..");

export const paths = Object.freeze({
  client: path.join(repositoryRoot, "clients/sharm-divers-club/client.manifest.json"),
  lock: path.join(repositoryRoot, "clients/sharm-divers-club/release.lock.json"),
  moduleCatalog: path.join(repositoryRoot, "foundry/catalog/modules.json"),
  product: path.join(repositoryRoot, "products/divers/product.manifest.json"),
});

export async function readJson(filePath) {
  return JSON.parse(await readFile(filePath, "utf8"));
}

export function canonicalJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalJson).join(",")}]`;
  }

  if (value !== null && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
      .join(",")}}`;
  }

  return JSON.stringify(value);
}

export function sha256(value) {
  return createHash("sha256").update(canonicalJson(value)).digest("hex");
}

export async function loadReleaseInputs() {
  const [client, product, moduleCatalog] = await Promise.all([
    readJson(paths.client),
    readJson(paths.product),
    readJson(paths.moduleCatalog),
  ]);

  return { client, product, moduleCatalog };
}

export function buildReleaseLock({ client, product, moduleCatalog }) {
  const modulesById = new Map(moduleCatalog.modules.map((module) => [module.id, module]));
  const modules = product.requiredModules
    .map((moduleId) => {
      const module = modulesById.get(moduleId);
      if (!module) {
        throw new Error(`Product references unknown module: ${moduleId}`);
      }
      return { id: module.id, version: module.version };
    })
    .sort((left, right) => left.id.localeCompare(right.id));

  return {
    schemaVersion: 1,
    clientId: client.clientId,
    platformVersion: product.platformVersion,
    product: {
      id: product.productId,
      version: product.version,
    },
    contractVersions: {
      httpApi: "v1",
    },
    modules,
    manifestDigests: {
      client: sha256(client),
      product: sha256(product),
      moduleCatalog: sha256(moduleCatalog),
    },
  };
}
